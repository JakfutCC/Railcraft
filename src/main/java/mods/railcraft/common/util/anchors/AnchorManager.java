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

import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import mods.railcraft.common.blocks.machine.alpha.EnumMachineAlpha;
import mods.railcraft.common.blocks.machine.alpha.TileAnchorAdmin;
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
    private final Map<Integer, Long> idleSince = new HashMap<>();
    private boolean started;
    private boolean login;
    private long ticks;

    public void reset() {
        anchors.clear();
        pendingTickets.clear();
        pendingWorlds.clear();
        evicting.clear();
        idleSince.clear();
        started = false;
        login = false;
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

    public void remove(TileAnchorWorld anchor) {
        anchors.remove(anchor.getUUID());
        AnchorIndex index = AnchorIndex.get();
        if (index != null) index.remove(anchor.getUUID());
    }

    public boolean canAcquire(TileAnchorWorld anchor) {
        if (evicting.contains(anchor.getWorldObj().provider.dimensionId)) return false;
        long cost = anchor.getCubeCost();
        if (cost > 32768 || !(anchor instanceof TileAnchorAdmin) && cost > RailcraftConfig.anchorMaxCubes) return false;
        return AnchorBudgets.permits(anchor, cost, anchors.values());
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
                        && !PlayerPlugin.isPlayerConnected(PlayerPlugin.readOwnerFromNBT(entry)))
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
        pendingTickets.removeIf(ticket -> ticket.world == event.world);
        anchors.values().removeIf(anchor -> {
            if (anchor.getWorldObj() != event.world) return false;
            Railcraft.proxy.getTicketManager().tickets.remove(anchor.getUUID());
            return true;
        });
    }

    @SubscribeEvent
    public void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        login = true;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        ticks++;
        AnchorIndex index = AnchorIndex.get();
        if (index == null) return;
        boolean startOrLogin = !started || login;
        started = true;
        login = false;

        // Complete all restoration before world ticks and AE2's ServerTick.END tile-ready queue.
        Set<Integer> worlds = new HashSet<>(pendingWorlds);
        pendingWorlds.clear();
        for (NBTTagCompound entry : new ArrayList<>(index.entries.values())) {
            if (!entry.getBoolean("restore")) continue;
            boolean personal = entry.getString("type").equals(EnumMachineAlpha.PERSONAL_ANCHOR.getTag());
            if (personal && !PlayerPlugin.isPlayerConnected(PlayerPlugin.readOwnerFromNBT(entry))) continue;
            int dim = entry.getInteger("dim");
            if (!startOrLogin && !worlds.contains(dim)) continue;
            WorldServer world = DimensionManager.getWorld(dim);
            if (world == null && startOrLogin && DimensionManager.isDimensionRegistered(dim)) {
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
        try {
            // Check saving succeeds before handing off to Forge's ordinary save/event/flush unload path.
            world.saveAllChunks(true, null);
        } catch (net.minecraft.world.MinecraftException error) {
            Game.log(Level.ERROR, "Not evicting dimension {0}: saving failed: {1}", world.provider.dimensionId, error);
            return false;
        }
        evicting.add(world.provider.dimensionId);
        for (TileAnchorWorld anchor : anchors.values()) {
            if (anchor.getWorldObj() == world) anchor.releaseTicket();
        }
        DimensionManager.unloadWorld(world.provider.dimensionId);
        Game.log(Level.INFO, "Queued idle dimension {0} for normal Forge unload", world.provider.dimensionId);
        return true;
    }

    private void checkIdleDimensions() {
        for (WorldServer world : DimensionManager.getWorlds()) {
            int dim = world.provider.dimensionId;
            boolean managed = RailcraftConfig.anchorWatchdog;
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
