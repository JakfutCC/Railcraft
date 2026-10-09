package mods.railcraft.common.util.anchors;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;

import mods.railcraft.common.plugins.forge.PlayerPlugin;
import mods.railcraft.common.util.network.PacketAnchorList;
import mods.railcraft.common.util.network.PacketDispatcher;

/** Coalesce network requests; world/index access and permission checks run on the server thread. */
public final class AnchorMapRequests {

    private static final Map<UUID, Request> PENDING = new ConcurrentHashMap<>();

    private AnchorMapRequests() {}

    public static void request(EntityPlayerMP player, int minX, int maxX, int minZ, int maxZ) {
        PENDING.put(player.getUniqueID(), new Request(player, minX, maxX, minZ, maxZ));
    }

    public static void clear() {
        PENDING.clear();
    }

    public static void respond() {
        for (Map.Entry<UUID, Request> entry : PENDING.entrySet()) {
            Request request = entry.getValue();
            if (!PENDING.remove(entry.getKey(), request)) continue;
            EntityPlayerMP player = request.player;
            if (!MinecraftServer.getServer().getConfigurationManager().playerEntityList.contains(player)) continue;
            List<NBTTagCompound> entries = new ArrayList<>();
            boolean truncated = false;
            for (NBTTagCompound anchor : AnchorIndex.get().entries.values()) {
                if (anchor.getInteger("dim") != player.dimension
                        || !PlayerPlugin.isSamePlayer(PlayerPlugin.readOwnerFromNBT(anchor), player.getGameProfile()))
                    continue;
                int[] box = anchor.getIntArray("bounds");
                if (box.length != 6 || box[0] > request.maxX
                        || box[3] < request.minX
                        || box[2] > request.maxZ
                        || box[5] < request.minZ)
                    continue;
                if (entries.size() == PacketAnchorList.MAP_SIZE) {
                    truncated = true;
                    break;
                }
                NBTTagCompound copy = (NBTTagCompound) anchor.copy();
                copy.setBoolean("active", AnchorManager.INSTANCE.isActive(UUID.fromString(anchor.getString("id"))));
                entries.add(copy);
            }
            PacketDispatcher.sendToPlayer(PacketAnchorList.map(entries, truncated), player);
        }
    }

    private static final class Request {

        final EntityPlayerMP player;
        final int minX, maxX, minZ, maxZ;

        Request(EntityPlayerMP player, int minX, int maxX, int minZ, int maxZ) {
            this.player = player;
            this.minX = minX;
            this.maxX = maxX;
            this.minZ = minZ;
            this.maxZ = maxZ;
        }
    }
}
