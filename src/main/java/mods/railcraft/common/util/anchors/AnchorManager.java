package mods.railcraft.common.util.anchors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.event.world.WorldEvent;

import org.apache.logging.log4j.Level;

import com.mojang.authlib.GameProfile;

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import mods.railcraft.common.blocks.machine.alpha.EnumMachineAlpha;
import mods.railcraft.common.blocks.machine.alpha.TileAnchorPassive;
import mods.railcraft.common.blocks.machine.alpha.TileAnchorPersonal;
import mods.railcraft.common.blocks.machine.alpha.TileAnchorWorld;
import mods.railcraft.common.core.Railcraft;
import mods.railcraft.common.core.RailcraftConfig;
import mods.railcraft.common.plugins.forge.PlayerPlugin;
import mods.railcraft.common.util.misc.Game;

/** Server-thread coordinator; Forge tickets and tile NBT remain the source of truth. */
public final class AnchorManager {

    public static final AnchorManager INSTANCE = new AnchorManager();
    private final Map<UUID, TileAnchorWorld> anchors = new LinkedHashMap<>();
    private final List<Ticket> pendingTickets = new ArrayList<>();
    private final Set<Integer> pendingWorlds = new HashSet<>();
    private final Set<Integer> evicting = new HashSet<>();
    private final Map<Integer, WorldServer> requestedEvictions = new HashMap<>();
    private final Set<WorldServer> savedEvictions = new HashSet<>();
    private final Map<Integer, Long> idleSince = new HashMap<>();
    private boolean started;
    private final List<GameProfile> logins = new ArrayList<>();
    private long ticks;

    public void reset() {
        AnchorMapRequests.clear();
        anchors.clear();
        pendingTickets.clear();
        pendingWorlds.clear();
        evicting.clear();
        requestedEvictions.clear();
        savedEvictions.clear();
        idleSince.clear();
        started = false;
        logins.clear();
        ticks = 0;
    }

    public void track(TileAnchorWorld anchor) {
        if (anchor.getWorldObj() == null || anchor.getWorldObj().isRemote) return;
        if (anchors.put(anchor.getUUID(), anchor) != anchor) {
            AnchorIndex index = AnchorIndex.get();
            if (index != null) index.update(anchor);
        }
    }

    public void rememberAll() {
        AnchorIndex index = AnchorIndex.get();
        if (index != null)
            for (TileAnchorWorld anchor : anchors.values()) if (!anchor.isInvalid()) index.update(anchor);
    }

    public boolean isActive(UUID id) {
        TileAnchorWorld anchor = anchors.get(id);
        return anchor != null && !anchor.isInvalid() && anchor.hasActiveTicket();
    }

    public static boolean isOwnerOnline(GameProfile owner) {
        for (Object value : net.minecraft.server.MinecraftServer.getServer()
                .getConfigurationManager().playerEntityList) {
            net.minecraft.entity.player.EntityPlayer player = (net.minecraft.entity.player.EntityPlayer) value;
            if (PlayerPlugin.isSamePlayer(owner, player.getGameProfile())) return true;
        }
        return false;
    }

    public void remove(TileAnchorWorld anchor) {
        if (anchor.getWorldObj().isRemote) return;
        anchors.remove(anchor.getUUID());
        AnchorIndex index = AnchorIndex.get();
        if (index != null) index.remove(anchor.getUUID());
    }

    public boolean canAcquire(TileAnchorWorld anchor) {
        if (evicting.contains(anchor.getWorldObj().provider.dimensionId)) return false;
        return anchor.volumeAllowed() && AnchorBudgets.permits(anchor, anchor.getCubeCost(), anchors.values());
    }

    public void restore(Ticket ticket) {
        pendingTickets.add(ticket);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void worldLoaded(WorldEvent.Load event) {
        if (event.world.isRemote) return;
        pendingWorlds.add(event.world.provider.dimensionId);
        // On-demand dimensions can be opened during a tick, after ServerTick.START has already run.
        if (started && event.world instanceof WorldServer world) {
            AnchorIndex index = AnchorIndex.get();
            for (NBTTagCompound entry : new ArrayList<>(index.entries.values())) {
                if (entry.getInteger("dim") != world.provider.dimensionId || !entry.getBoolean("restore")) continue;
                if (entry.getString("type").equals(EnumMachineAlpha.PERSONAL_ANCHOR.getTag())
                        && !isOwnerOnline(PlayerPlugin.readOwnerFromNBT(entry)))
                    continue;
                restoreEntry(world, entry, index);
            }
            List<Ticket> tickets = new ArrayList<>();
            pendingTickets.removeIf(ticket -> {
                if (ticket.world != world) return false;
                tickets.add(ticket);
                return true;
            });
            for (Ticket ticket : tickets) restoreTicket(ticket);
        }
    }

    @SubscribeEvent
    public void worldUnloaded(WorldEvent.Unload event) {
        if (event.world.isRemote) return;
        int dim = event.world.provider.dimensionId;
        pendingWorlds.remove(dim);
        idleSince.remove(dim);
        evicting.remove(dim);
        requestedEvictions.remove(dim);
        savedEvictions.remove(event.world);
        pendingTickets.removeIf(ticket -> ticket.world == event.world);
        anchors.values().removeIf(anchor -> {
            if (anchor.getWorldObj() != event.world) return false;
            Railcraft.proxy.getTicketManager().tickets.remove(anchor.getUUID());
            return true;
        });
    }

    @SubscribeEvent
    public void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        logins.add(event.player.getGameProfile());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        ticks++;
        AnchorIndex index = AnchorIndex.get();
        if (index == null) return;
        boolean starting = !started;
        started = true;
        List<GameProfile> entering = new ArrayList<>(logins);
        logins.clear();

        // Complete all restoration before world ticks and AE2's ServerTick.END tile-ready queue.
        Set<Integer> worlds = new HashSet<>(pendingWorlds);
        pendingWorlds.clear();
        if (starting || !entering.isEmpty() || !worlds.isEmpty())
            for (NBTTagCompound entry : new ArrayList<>(index.entries.values())) {
                if (!entry.getBoolean("restore")) continue;
                boolean personal = entry.getString("type").equals(EnumMachineAlpha.PERSONAL_ANCHOR.getTag());
                if (personal && !isOwnerOnline(PlayerPlugin.readOwnerFromNBT(entry))) continue;
                int dim = entry.getInteger("dim");
                boolean ownerEntered = personal && entering.stream()
                        .anyMatch(profile -> PlayerPlugin.isSamePlayer(profile, PlayerPlugin.readOwnerFromNBT(entry)));
                if (!starting && !ownerEntered && !worlds.contains(dim)) continue;
                WorldServer world = DimensionManager.getWorld(dim);
                if (world == null && (starting || ownerEntered) && DimensionManager.isDimensionRegistered(dim)) {
                    DimensionManager.initDimension(dim);
                    world = DimensionManager.getWorld(dim);
                }
                if (world != null) restoreEntry(world, entry, index);
            }
        while (!pendingTickets.isEmpty()) {
            List<Ticket> tickets = new ArrayList<>(pendingTickets);
            pendingTickets.clear();
            for (Ticket ticket : tickets) restoreTicket(ticket);
        }
        for (Iterator<TileAnchorWorld> it = anchors.values().iterator(); it.hasNext();) {
            TileAnchorWorld anchor = it.next();
            if (anchor.isInvalid()) {
                anchor.releaseTicket();
                it.remove();
            }
        }
        if (ticks % 20 == 0) {
            AnchorMapRequests.respond();
            for (TileAnchorWorld anchor : new ArrayList<>(anchors.values())) {
                index.update(anchor);
                if (anchor.hasActiveTicket() && !canAcquire(anchor)) anchor.releaseTicket();
            }
            checkIdleDimensions();
        }
    }

    private void restoreEntry(WorldServer world, NBTTagCompound entry, AnchorIndex index) {
        int x = entry.getInteger("x"), y = entry.getInteger("y"), z = entry.getInteger("z");
        UUID id = UUID.fromString(entry.getString("id"));
        TileAnchorWorld existing = anchors.get(id);
        if (existing != null && existing.hasActiveTicket()) return;
        AnchorLoading.loadBlock(world, x, y, z);
        TileEntity tile = world.getTileEntity(x, y, z);
        if (tile instanceof TileAnchorWorld && ((TileAnchorWorld) tile).getUUID().equals(id)) {
            TileAnchorWorld anchor = (TileAnchorWorld) tile;
            track(anchor);
            anchor.prepareLoading();
            index.update(anchor);
        } else index.remove(id);
    }

    private void restoreTicket(Ticket ticket) {
        NBTTagCompound data = ticket.getModData();
        int x = data.getInteger("xCoord"), y = data.getInteger("yCoord"), z = data.getInteger("zCoord");
        AnchorLoading.loadBlock(ticket.world, x, y, z);
        TileEntity tile = ticket.world.getTileEntity(x, y, z);
        if (tile instanceof TileAnchorWorld && !(tile instanceof TileAnchorPassive)) {
            TileAnchorWorld anchor = (TileAnchorWorld) tile;
            track(anchor);
            if (!anchor.hasActiveTicket()) anchor.forceChunkLoading(ticket);
            else if (anchor.getTicket() != ticket) AnchorLoading.release(ticket);
            AnchorIndex.get().update(anchor);
        } else AnchorLoading.release(ticket);
    }

    private Set<Ticket> passiveTickets(World world) {
        Set<Ticket> tickets = new HashSet<>();
        for (TileAnchorWorld anchor : anchors.values()) {
            if (anchor.getWorldObj() == world && anchor instanceof TileAnchorPassive && anchor.hasActiveTicket()) {
                tickets.add(anchor.getTicket());
            }
        }
        return tickets;
    }

    public boolean mayEvict(WorldServer world) {
        int dim = world.provider.dimensionId;
        if (dim == 0 || DimensionManager.shouldLoadSpawn(dim) || !world.playerEntities.isEmpty() || world.levelSaving)
            return false;
        Set<Ticket> passive = passiveTickets(world);
        for (TileAnchorWorld anchor : anchors.values()) {
            if (anchor.getWorldObj() == world && anchor.hasActiveTicket() && !(anchor instanceof TileAnchorPassive))
                return false;
        }
        for (Ticket ticket : ForgeChunkManager.getPersistentChunksFor(world).values()) {
            if (!passive.contains(ticket)) return false;
        }
        return !AnchorLoading.hasExternalCubes(world, passive);
    }

    public boolean evict(WorldServer world) {
        if (!mayEvict(world)) return false;
        requestedEvictions.put(world.provider.dimensionId, world);
        return true;
    }

    public void enqueueEvictions() {
        requestedEvictions.values().removeIf(world -> DimensionManager.getWorld(world.provider.dimensionId) != world);
        for (int dim : requestedEvictions.keySet()) DimensionManager.unloadWorld(dim);
    }

    /** Runs at Forge's per-world unload boundary, after portals, logins and other world ticks. */
    public WorldServer beginEviction(WorldServer world) {
        if (world == null || !requestedEvictions.remove(world.provider.dimensionId, world)) return world;
        if (!mayEvict(world)) return null;
        try {
            world.saveAllChunks(true, null);
        } catch (net.minecraft.world.MinecraftException error) {
            Game.log(Level.ERROR, "Not evicting dimension {0}: saving failed: {1}", world.provider.dimensionId, error);
            return null;
        }
        // Save-event listeners may acquire loaders or move a player. Recheck before releasing our tickets.
        if (!mayEvict(world)) return null;
        evicting.add(world.provider.dimensionId);
        for (TileAnchorWorld anchor : new ArrayList<>(anchors.values())) {
            if (anchor.getWorldObj() == world) anchor.releaseTicket();
        }
        if (!mayEvict(world)) {
            evicting.remove(world.provider.dimensionId);
            return null;
        }
        savedEvictions.add(world);
        Game.log(Level.INFO, "Queued idle dimension {0} for normal Forge unload", world.provider.dimensionId);
        return world;
    }

    public boolean consumeEvictionSave(WorldServer world) {
        return savedEvictions.remove(world);
    }

    private void checkIdleDimensions() {
        Set<Integer> managedDimensions = new HashSet<>();
        AnchorIndex index = AnchorIndex.get();
        if (index != null)
            for (NBTTagCompound entry : index.entries.values()) managedDimensions.add(entry.getInteger("dim"));
        for (WorldServer world : DimensionManager.getWorlds()) {
            int dim = world.provider.dimensionId;
            boolean managed = RailcraftConfig.anchorWatchdog || managedDimensions.contains(dim);
            for (TileAnchorWorld anchor : anchors.values()) {
                if (anchor.getWorldObj() == world
                        && (anchor instanceof TileAnchorPassive || anchor instanceof TileAnchorPersonal))
                    managed = true;
            }
            if (!managed || !mayEvict(world)) {
                idleSince.remove(dim);
                continue;
            }
            long since = idleSince.computeIfAbsent(dim, ignored -> ticks);
            if (ticks - since >= RailcraftConfig.anchorIdleSeconds * 20L) evict(world);
        }
    }
}
