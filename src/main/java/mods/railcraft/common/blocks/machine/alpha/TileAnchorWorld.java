/*
 * Copyright (c) CovertJaguar, 2014 http://railcraft.info This code is the property of CovertJaguar and may only be used
 * with explicit written permission unless otherwise specified on the license page at
 * http://railcraft.info/wiki/info:license.
 */
package mods.railcraft.common.blocks.machine.alpha;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.ISidedInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;
import net.minecraftforge.common.ForgeChunkManager.Type;

import org.apache.logging.log4j.Level;

import mods.railcraft.api.core.WorldCoordinate;
import mods.railcraft.api.core.items.IToolCrowbar;
import mods.railcraft.common.blocks.RailcraftTileEntity;
import mods.railcraft.common.blocks.machine.IEnumMachine;
import mods.railcraft.common.blocks.machine.TileMachineItem;
import mods.railcraft.common.blocks.machine.beta.TileSentinel;
import mods.railcraft.common.carts.ItemCartAnchor;
import mods.railcraft.common.core.Railcraft;
import mods.railcraft.common.core.RailcraftConfig;
import mods.railcraft.common.core.RailcraftConstants;
import mods.railcraft.common.gui.EnumGui;
import mods.railcraft.common.gui.GuiHandler;
import mods.railcraft.common.plugins.forge.ChatPlugin;
import mods.railcraft.common.plugins.forge.PlayerPlugin;
import mods.railcraft.common.plugins.forge.PowerPlugin;
import mods.railcraft.common.plugins.forge.WorldPlugin;
import mods.railcraft.common.util.anchors.AnchorLoading;
import mods.railcraft.common.util.anchors.AnchorManager;
import mods.railcraft.common.util.anchors.AnchorVolume;
import mods.railcraft.common.util.collections.ItemMap;
import mods.railcraft.common.util.effects.EffectManager;
import mods.railcraft.common.util.misc.Game;
import mods.railcraft.common.util.misc.IAnchor;

/**
 * @author CovertJaguar <http://www.railcraft.info>
 */
public class TileAnchorWorld extends TileMachineItem implements IAnchor, ISidedInventory {

    private static final int SENTINEL_CHECK = 128;
    private static final byte MAX_CHUNKS = 25;
    private static final int[] SLOTS = new int[] { 0 };
    private static final int[] SLOTS_NO_ACCESS = new int[] {};
    private int xSentinel = -1;
    private int ySentinel = -1;
    private int zSentinel = -1;
    private int prevX, prevY, prevZ;
    private Set<ChunkCoordIntPair> chunks;
    private long fuel;
    private final int[] extents = { 1, 1, 1, 1, 1, 1 };
    private boolean sentinel;
    private String loadingStatus = "Dormant";
    private boolean hasTicket;
    private boolean refreshTicket;
    private boolean powered;
    private int retryDelay;

    public TileAnchorWorld() {
        super(1);
    }

    @Override
    public int getSizeInventory() {
        return this instanceof TileAnchorAdmin ? 0 : 1;
    }

    @Override
    public IEnumMachine getMachineType() {
        return EnumMachineAlpha.WORLD_ANCHOR;
    }

    @Override
    public IIcon getIcon(int side) {
        if (!hasTicket && side < 2) return getMachineType().getTexture(6);
        return getMachineType().getTexture(side);
    }

    @Override
    public boolean blockActivated(EntityPlayer player, int side) {
        if (Game.isHost(worldObj) && !canConfigure(player)) return false;
        ItemStack current = player.getCurrentEquippedItem();
        if (current != null && current.getItem() instanceof IToolCrowbar crowbar) {
            if (crowbar.canWhack(player, current, xCoord, yCoord, zCoord)) {
                if (Game.isHost(worldObj)) {
                    WorldCoordinate target = Railcraft.proxy.getTicketManager().getTarget(player);
                    if (target == null) {
                        Railcraft.proxy.getTicketManager().setTarget(this, player);
                    } else if (worldObj.provider.dimensionId != target.dimension) {
                        ChatPlugin.sendLocalizedChatFromServer(
                                player,
                                "railcraft.gui.anchor.pair.fail.dimension",
                                getLocalizationTag());
                    } else if (new WorldCoordinate(this).equals(target)) {
                        Railcraft.proxy.getTicketManager().removeTarget(player);
                        ChatPlugin.sendLocalizedChatFromServer(
                                player,
                                "railcraft.gui.anchor.pair.cancel",
                                getLocalizationTag());
                    } else setSentinel(player, target);
                    crowbar.onWhack(player, current, xCoord, yCoord, zCoord);
                }
                return true;
            }
        }
        return super.blockActivated(player, side);
    }

    @Override
    public boolean openGui(EntityPlayer player) {
        if (!Game.isHost(worldObj) || canConfigure(player)) {
            GuiHandler.openGui(EnumGui.WORLD_ANCHOR, player, worldObj, xCoord, yCoord, zCoord);
            return true;
        }
        return false;
    }

    public int getMaxSentinelChunks() {
        Ticket ticket = getTicket();
        if (ticket == null) return MAX_CHUNKS;
        return Math.min(ticket.getMaxChunkListDepth(), MAX_CHUNKS);
    }

    public boolean canConfigure(EntityPlayer player) {
        return PlayerPlugin.isSamePlayer(getOwner(), player.getGameProfile())
                || PlayerPlugin.isPlayerOp(player.getGameProfile());
    }

    public static TileEntity getTargetAt(EntityPlayer player, RailcraftTileEntity searcher, WorldCoordinate coord) {
        if (!WorldPlugin.blockExists(searcher.getWorldObj(), coord.x, coord.y, coord.z)) {
            ChatPlugin.sendLocalizedChatFromServer(
                    player,
                    "railcraft.gui.anchor.pair.fail.unloaded",
                    searcher.getLocalizationTag());
            return null;
        }
        return WorldPlugin.getBlockTile(searcher.getWorldObj(), coord.x, coord.y, coord.z);
    }

    public boolean setSentinel(EntityPlayer player, WorldCoordinate coord) {
        if (!canConfigure(player)) return false;
        TileEntity tile = getTargetAt(player, this, coord);
        if (tile == null) return false;
        if (tile instanceof TileSentinel) {
            int xChunk = xCoord >> 4;
            int zChunk = zCoord >> 4;

            int xSentinelChunk = tile.xCoord >> 4;
            int zSentinelChunk = tile.zCoord >> 4;

            boolean cubic = AnchorLoading.isCubic(worldObj);
            int yChunk = yCoord >> 4;
            int ySentinelChunk = tile.yCoord >> 4;
            int axes = (xChunk != xSentinelChunk ? 1 : 0) + (zChunk != zSentinelChunk ? 1 : 0)
                    + (cubic && yChunk != ySentinelChunk ? 1 : 0);
            if (axes > 1) {
                ChatPlugin.sendLocalizedChatFromServer(
                        player,
                        "railcraft.gui.anchor.pair.fail.alignment",
                        getLocalizationTag(),
                        ((TileSentinel) tile).getLocalizationTag());
                return false;
            }

            int max = getMaxSentinelChunks();
            if (Math.abs((long) xChunk - xSentinelChunk) >= max || Math.abs((long) zChunk - zSentinelChunk) >= max
                    || cubic && Math.abs((long) yChunk - ySentinelChunk) >= max) {
                ChatPlugin.sendLocalizedChatFromServer(
                        player,
                        "railcraft.gui.anchor.pair.fail.distance",
                        getLocalizationTag(),
                        ((TileSentinel) tile).getLocalizationTag());
                return false;
            }

            xSentinel = tile.xCoord;
            ySentinel = tile.yCoord;
            zSentinel = tile.zCoord;
            sentinel = true;

            refreshTicket = true;
            markDirty();
            sendUpdateToClient();
            Railcraft.proxy.getTicketManager().removeTarget(player);
            ChatPlugin.sendLocalizedChatFromServer(player, "railcraft.gui.anchor.pair.success", getLocalizationTag());
            return true;
        }
        ChatPlugin.sendLocalizedChatFromServer(player, "railcraft.gui.anchor.pair.fail.invalid", getLocalizationTag());
        return false;
    }

    public void clearSentinel() {
        if (!hasSentinel()) return;

        xSentinel = -1;
        ySentinel = -1;
        zSentinel = -1;
        sentinel = false;

        refreshTicket = true;
        markDirty();
        sendUpdateToClient();
    }

    public boolean hasSentinel() {
        return sentinel;
    }

    public boolean hasFuel() {
        return fuel > 0;
    }

    @Override
    public ArrayList<ItemStack> getDrops(int fortune) {
        ArrayList<ItemStack> items = new ArrayList<ItemStack>();
        ItemStack drop = getMachineType().getItem();
        NBTTagCompound nbt = new NBTTagCompound();
        nbt.setLong("fuel", fuel / 144);
        nbt.setLong("anchorFuelCubeTicks", fuel);
        nbt.setIntArray("anchorExtents", extents);
        drop.setTagCompound(nbt);
        items.add(drop);
        return items;
    }

    @Override
    public void initFromItem(ItemStack stack) {
        super.initFromItem(stack);
        if (needsFuel()) fuel = ItemCartAnchor.getFuel(stack) * 144;
        if (stack.hasTagCompound()) readAnchorSettings(stack.getTagCompound());
    }

    @Override
    public void updateEntity() {
        super.updateEntity();
        if (Game.isNotHost(worldObj)) {
            if (chunks != null) EffectManager.instance.chunkLoaderEffect(worldObj, this, chunks);
            return;
        }

        if (RailcraftConfig.deleteAnchors()) {
            releaseTicket();
            worldObj.setBlock(xCoord, yCoord, zCoord, Blocks.obsidian);
            return;
        }

        if (xCoord != prevX || yCoord != prevY || zCoord != prevZ) {
            releaseTicket();
            prevX = xCoord;
            prevY = yCoord;
            prevZ = zCoord;
        }

        if (hasActiveTicket() && (getTicket().world != worldObj || refreshTicket || powered)) releaseTicket();

        AnchorManager.INSTANCE.track(this);
        if (hasActiveTicket() && needsFuel()) {
            fuel = Math.max(0, fuel - getCubeCost());
            markDirty();
            if (fuel == 0) releaseTicket();
        }

        if (clock % SENTINEL_CHECK == 0 && hasSentinel()) {
            if (worldObj.checkChunksExist(xSentinel, ySentinel, zSentinel, xSentinel, ySentinel, zSentinel)
                    && !(worldObj.getTileEntity(xSentinel, ySentinel, zSentinel) instanceof TileSentinel))
                clearSentinel();
        }

        if (retryDelay > 0) retryDelay--;
        if (!hasActiveTicket() && retryDelay == 0) requestTicket();

        if (RailcraftConfig.printAnchorDebug() && hasActiveTicket()) if (clock % 64 == 0) {
            int numChunks = chunks == null ? 0 : chunks.size();
            ChatPlugin.sendLocalizedChatToAllFromServer(
                    worldObj,
                    "%s has loaded %d chunks and is ticking at <x%d y%d z%d> in dim:%d - logged on tick %d",
                    getName(),
                    numChunks,
                    xCoord,
                    yCoord,
                    zCoord,
                    worldObj.provider.dimensionId,
                    worldObj.getWorldTime());
            Game.log(
                    Level.DEBUG,
                    "{0} has loaded {1} chunks and is ticking at <x{2} y{3} z{4}> in dim:{5} - logged on tick {6}",
                    getName(),
                    numChunks,
                    xCoord,
                    yCoord,
                    zCoord,
                    worldObj.provider.dimensionId,
                    worldObj.getWorldTime());
        }
    }

    @Override
    public void onBlockRemoval() {
        super.onBlockRemoval();
        if (Game.isNotHost(worldObj)) return;
        releaseTicket();
        AnchorManager.INSTANCE.remove(this);
    }

    @Override
    public void invalidate() {
        super.invalidate();
        refreshTicket = true;
    }

    @Override
    public void validate() {
        super.validate();
        refreshTicket = true;
    }

    public void releaseTicket() {
        if (Game.isNotHost(worldObj)) return;
        refreshTicket = false;
        setTicket(null);
    }

    protected void requestTicket() {
        if (hasActiveTicket()) return;
        retryDelay = 20;
        loadingStatus = powered ? "Redstone disabled" : "No fuel or owner offline";
        if (!meetsTicketRequirements()) return;
        if (!volumeAllowed()) {
            loadingStatus = "Area exceeds loading limit";
            return;
        }
        if (AnchorManager.INSTANCE.canAcquire(this)) {
            Ticket chunkTicket = getTicketFromForge();
            if (chunkTicket != null) {
                setTicketData(chunkTicket);
                forceChunkLoading(chunkTicket);
            } else loadingStatus = "Forge ticket limit";
        } else loadingStatus = "Owner/rank budget exceeded";
    }

    public boolean needsFuel() {
        return !getFuelMap().isEmpty();
    }

    @Override
    public ItemMap<Float> getFuelMap() {
        return RailcraftConfig.anchorFuelWorld;
    }

    protected boolean meetsTicketRequirements() {
        return !powered && !isInvalid() && (hasFuel() || !needsFuel() || hasFuelItem());
    }

    protected Ticket getTicketFromForge() {
        return ForgeChunkManager.requestTicket(Railcraft.getMod(), worldObj, Type.NORMAL);
    }

    protected void setTicketData(Ticket chunkTicket) {
        chunkTicket.getModData().setInteger("xCoord", xCoord);
        chunkTicket.getModData().setInteger("yCoord", yCoord);
        chunkTicket.getModData().setInteger("zCoord", zCoord);
        chunkTicket.getModData().setString("type", getMachineType().getTag());
    }

    public boolean hasActiveTicket() {
        return getTicket() != null;
    }

    public Ticket getTicket() {
        return Railcraft.proxy.getTicketManager().tickets.get(getUUID());
    }

    public void setTicket(Ticket t) {
        boolean changed = false;
        Ticket ticket = getTicket();
        if (ticket != t) {
            if (ticket != null) {
                AnchorLoading.release(ticket);
                Railcraft.proxy.getTicketManager().tickets.remove(getUUID());
            }
            changed = true;
        }
        hasTicket = t != null;
        if (hasTicket) Railcraft.proxy.getTicketManager().tickets.put(getUUID(), t);
        if (!hasTicket) chunks = null;
        if (changed) sendUpdateToClient();
    }

    public void forceChunkLoading(Ticket ticket) {
        AnchorManager.INSTANCE.track(this);
        AnchorVolume volume = getVolume();
        int limit = AnchorLoading.ticketLimit(ticket);
        if (!meetsTicketRequirements() || !AnchorManager.INSTANCE.canAcquire(this)
                || limit > 0 && volume.size() > limit) {
            loadingStatus = "Inactive: fuel, owner or loading limit";
            if (getTicket() == ticket) releaseTicket();
            else AnchorLoading.release(ticket);
            return;
        }
        setTicket(ticket);
        try {
            AnchorLoading.force(ticket, volume);
        } catch (RuntimeException error) {
            releaseTicket();
            throw error;
        }
        if (needsFuel() && fuel <= 0) {
            ItemStack stack = getStackInSlot(0);
            fuel = (long) (getFuelMap().get(stack) * RailcraftConstants.TICKS_PER_HOUR * 144D);
            decrStackSize(0, 1);
        }
        refreshTicket = false;
        prevX = xCoord;
        prevY = yCoord;
        prevZ = zCoord;
        loadingStatus = "Active";
        setupChunks();
        markDirty();
    }

    public void setupChunks() {
        if (!hasTicket) chunks = null;
        else chunks = getVolume().columns();
    }

    public boolean hasFuelItem() {
        ItemStack stack = getStackInSlot(0);
        return stack != null && stack.stackSize > 0 && getFuelMap().containsKey(stack) && getFuelMap().get(stack) > 0;
    }

    public boolean shouldRestore() {
        return !powered && !(this instanceof TileAnchorPassive) && (hasFuel() || !needsFuel() || hasFuelItem());
    }

    public void prepareLoading() {
        if (!hasActiveTicket()) requestTicket();
    }

    public int getExtent(int side) {
        return extents[side];
    }

    public void setExtents(int[] values) {
        if (Game.isNotHost(worldObj)) return;
        if (values.length != 6) return;
        for (int value : values) if (value < 0 || value > RailcraftConfig.anchorMaxExtent) return;
        int[] old = extents.clone();
        boolean oldSentinel = sentinel;
        sentinel = false;
        System.arraycopy(values, 0, extents, 0, 6);
        if (!volumeAllowed()) {
            System.arraycopy(old, 0, extents, 0, 6);
            sentinel = oldSentinel;
            loadingStatus = "Requested area exceeds limit";
            return;
        }
        sentinel = false;
        releaseTicket();
        retryDelay = 10;
        loadingStatus = "Applying area";
        markDirty();
        sendUpdateToClient();
    }

    public AnchorVolume getVolume() {
        boolean cubic = AnchorLoading.isCubic(worldObj);
        int x = xCoord >> 4, y = yCoord >> 4, z = zCoord >> 4;
        int minX = x - extents[0], maxX = x + extents[1];
        int minY = y - extents[2], maxY = y + extents[3];
        int minZ = z - extents[4], maxZ = z + extents[5];
        if (sentinel) {
            minX = Math.min(x, xSentinel >> 4);
            maxX = Math.max(x, xSentinel >> 4);
            minY = Math.min(y, ySentinel >> 4);
            maxY = Math.max(y, ySentinel >> 4);
            minZ = Math.min(z, zSentinel >> 4);
            maxZ = Math.max(z, zSentinel >> 4);
        }
        minY = cubic ? Math.max(AnchorLoading.minCube(worldObj), minY) : 0;
        maxY = cubic ? Math.min(AnchorLoading.maxCube(worldObj), maxY) : 0;
        return new AnchorVolume(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public long getCubeCost() {
        return getVolume().size() * (AnchorLoading.isCubic(worldObj) ? 1 : 16);
    }

    public boolean volumeAllowed() {
        if (!sentinel) for (int extent : extents) if (extent > RailcraftConfig.anchorMaxExtent) return false;
        long cost = getCubeCost();
        if (cost > 32768 || !(this instanceof TileAnchorAdmin) && cost > RailcraftConfig.anchorMaxCubes) return false;
        int limit = AnchorLoading.ticketLimit(worldObj);
        return limit <= 0 || getVolume().size() <= limit;
    }

    public String getLoadingStatus() {
        return loadingStatus;
    }

    public boolean isPowered() {
        return powered;
    }

    public void setPowered(boolean power) {
        powered = power;
    }

    @Override
    public void onNeighborBlockChange(Block block) {
        super.onNeighborBlockChange(block);
        if (Game.isNotHost(getWorld())) return;
        boolean newPower = PowerPlugin.isBlockBeingPowered(worldObj, xCoord, yCoord, zCoord);
        if (powered != newPower) powered = newPower;
    }

    @Override
    public void writePacketData(DataOutputStream data) throws IOException {
        super.writePacketData(data);

        data.writeBoolean(hasTicket);

        data.writeInt(xSentinel);
        data.writeInt(ySentinel);
        data.writeInt(zSentinel);
        data.writeBoolean(sentinel);
        for (int value : extents) data.writeInt(value);
    }

    @Override
    public void readPacketData(DataInputStream data) throws IOException {
        super.readPacketData(data);

        boolean tick = data.readBoolean();
        if (hasTicket != tick) {
            hasTicket = tick;
            markBlockForUpdate();
        }

        xSentinel = data.readInt();
        ySentinel = data.readInt();
        zSentinel = data.readInt();
        sentinel = data.readBoolean();
        for (int i = 0; i < 6; i++) extents[i] = data.readInt();

        setupChunks();
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);

        data.setLong("fuel", fuel / 144);
        data.setLong("anchorFuelCubeTicks", fuel);
        data.setIntArray("anchorExtents", extents);
        data.setBoolean("hasSentinel", sentinel);

        data.setBoolean("powered", powered);

        data.setInteger("xSentinel", xSentinel);
        data.setInteger("ySentinel", ySentinel);
        data.setInteger("zSentinel", zSentinel);

        data.setInteger("prevX", prevX);
        data.setInteger("prevY", prevY);
        data.setInteger("prevZ", prevZ);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);

        if (needsFuel()) fuel = Math.max(0, data.getLong("fuel")) * 144;
        readAnchorSettings(data);

        powered = data.getBoolean("powered");

        xSentinel = data.getInteger("xSentinel");
        ySentinel = data.getInteger("ySentinel");
        zSentinel = data.getInteger("zSentinel");
        sentinel = data.hasKey("hasSentinel") ? data.getBoolean("hasSentinel")
                : data.hasKey("ySentinel") && ySentinel != -1;

        prevX = data.getInteger("prevX");
        prevY = data.getInteger("prevY");
        prevZ = data.getInteger("prevZ");
    }

    private void readAnchorSettings(NBTTagCompound data) {
        if (data.hasKey("anchorFuelCubeTicks")) fuel = Math.max(0, data.getLong("anchorFuelCubeTicks"));
        int[] values = data.getIntArray("anchorExtents");
        if (values.length == 6) {
            for (int i = 0; i < 6; i++) extents[i] = Math.max(0, Math.min(128, values[i]));
        }
    }

    @Override
    public float getResistance(Entity exploder) {
        return 60f;
    }

    @Override
    public float getHardness() {
        return 20;
    }

    @Override
    public long getAnchorFuel() {
        return worldObj == null ? fuel / 144 : fuel / Math.max(1, getCubeCost());
    }

    @Override
    public int[] getAccessibleSlotsFromSide(int var1) {
        if (getSizeInventory() > 0 && needsFuel() && RailcraftConfig.anchorsCanInteractWithPipes()) return SLOTS;
        return SLOTS_NO_ACCESS;
    }

    @Override
    public boolean canInsertItem(int i, ItemStack itemstack, int j) {
        return i == 0 && getSizeInventory() > 0
                && needsFuel()
                && RailcraftConfig.anchorsCanInteractWithPipes()
                && getFuelMap().containsKey(itemstack);
    }

    @Override
    public boolean canExtractItem(int i, ItemStack itemstack, int j) {
        return false;
    }
}
