package mods.railcraft.common.util.anchors;

import java.util.Collection;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.Loader;
import mods.railcraft.common.blocks.machine.alpha.TileAnchorAdmin;
import mods.railcraft.common.blocks.machine.alpha.TileAnchorWorld;
import mods.railcraft.common.core.RailcraftConfig;
import mods.railcraft.common.plugins.forge.PlayerPlugin;
import serverutils.ServerUtilitiesPermissions;
import serverutils.data.ClaimedChunk;
import serverutils.data.ServerUtilitiesTeamData;
import serverutils.lib.data.ForgePlayer;
import serverutils.lib.data.Universe;

public final class AnchorBudgets {

    private AnchorBudgets() {}

    public static boolean permits(TileAnchorWorld anchor, long cost, Collection<TileAnchorWorld> active) {
        if (anchor instanceof TileAnchorAdmin) return true;
        if (RailcraftConfig.anchorRankLimits && Loader.isModLoaded("serverutilities")) {
            return Ranks.permits(anchor, cost, active);
        }
        long used = cost;
        for (TileAnchorWorld other : active) {
            if (other != anchor && other.hasActiveTicket()
                    && !(other instanceof TileAnchorAdmin)
                    && PlayerPlugin.isSamePlayer(anchor.getOwner(), other.getOwner()))
                used += other.getCubeCost();
        }
        return used <= RailcraftConfig.anchorOwnerCubeBudget;
    }

    private static final class Ranks {

        static ForgePlayer player(GameProfile owner) {
            return !Universe.loaded() ? null : Universe.get().getPlayer(owner.getId());
        }

        static boolean permits(TileAnchorWorld anchor, long cost, Collection<TileAnchorWorld> active) {
            ForgePlayer owner = player(anchor.getOwner());
            // Restoration runs on the first tick, after SU loads its universe. Unknown owners fail closed.
            if (owner == null) return false;
            long budget = 0;
            long used = cost;
            if (owner.team.isValid()) {
                for (ForgePlayer member : owner.team.getMembers()) {
                    budget += Math
                            .max(0, member.getRankConfig(ServerUtilitiesPermissions.CHUNKLOADER_MAX_CHUNKS).getInt())
                            * 16L;
                }
                for (ClaimedChunk claimed : ServerUtilitiesTeamData.get(owner.team).getTeamChunks()) {
                    if (claimed.isLoaded() && !claimed.isInvalid()) used += 16;
                }
            } else {
                budget = Math.max(0, owner.getRankConfig(ServerUtilitiesPermissions.CHUNKLOADER_MAX_CHUNKS).getInt())
                        * 16L;
            }
            for (TileAnchorWorld other : active) {
                if (other == anchor || !other.hasActiveTicket() || other instanceof TileAnchorAdmin) continue;
                ForgePlayer otherOwner = player(other.getOwner());
                if (otherOwner != null
                        && (owner == otherOwner || owner.team.isValid() && owner.team == otherOwner.team)) {
                    used += other.getCubeCost();
                }
            }
            return used <= budget;
        }
    }
}
