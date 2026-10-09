/*
 * Copyright (c) CovertJaguar, 2014 http://railcraft.info This code is the property of CovertJaguar and may only be used
 * with explicit written permission unless otherwise specified on the license page at
 * http://railcraft.info/wiki/info:license.
 */
package mods.railcraft.common.gui.containers;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.ICrafting;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mods.railcraft.api.core.items.IStackFilter;
import mods.railcraft.common.blocks.machine.alpha.TileAnchorWorld;
import mods.railcraft.common.core.RailcraftConfig;
import mods.railcraft.common.core.RailcraftConstants;
import mods.railcraft.common.gui.slots.SlotStackFilter;
import mods.railcraft.common.util.anchors.AnchorLoading;
import mods.railcraft.common.util.misc.IAnchor;
import mods.railcraft.common.util.network.PacketBuilder;

public class ContainerAnchor extends RailcraftContainer {

    public final IAnchor anchor;
    public short minutesRemaining;
    private short prevMinutesRemaining;
    public final int[] extents = new int[6];
    public int cubeCount, maxExtent;
    public boolean active, cubic;
    public String loadingStatus = "";
    private final int[] previousState = new int[10];
    private String previousStatus;

    public ContainerAnchor(InventoryPlayer inventoryplayer, IAnchor a) {
        super(a);
        this.anchor = a;
        boolean block = a instanceof TileAnchorWorld;
        if (anchor.getSizeInventory() > 0) addSlot(new SlotStackFilter(new IStackFilter() {

            @Override
            public boolean matches(ItemStack stack) {
                return anchor.getFuelMap().containsKey(stack);
            }
        }, anchor, 0, block ? 8 : 60, 24));

        for (int i = 0; i < 3; i++) {
            for (int k = 0; k < 9; k++) {
                addSlot(new Slot(inventoryplayer, k + i * 9 + 9, 8 + k * 18, (block ? 158 : 58) + i * 18));
            }
        }

        for (int j = 0; j < 9; j++) {
            addSlot(new Slot(inventoryplayer, j, 8 + j * 18, block ? 216 : 116));
        }
    }

    @Override
    public void addCraftingToCrafters(ICrafting icrafting) {
        super.addCraftingToCrafters(icrafting);
        icrafting.sendProgressBarUpdate(this, 0, getMinutesRemaining(anchor.getAnchorFuel()));
        if (anchor instanceof TileAnchorWorld tile) {
            int[] state = state(tile);
            for (int i = 0; i < state.length; i++) icrafting.sendProgressBarUpdate(this, i + 1, state[i]);
            PacketBuilder.instance().sendGuiStringPacket(icrafting, windowId, 0, tile.getLoadingStatus());
        }
    }

    private short getMinutesRemaining(long fuel) {
        return (short) Math.min(Short.MAX_VALUE, Math.ceil((double) fuel / RailcraftConstants.TICKS_PER_MIN));
    }

    /**
     * Updates crafting matrix; called from onCraftMatrixChanged. Args: none
     */
    @Override
    public void sendUpdateToClient() {
        super.sendUpdateToClient();
        short mins = getMinutesRemaining(anchor.getAnchorFuel());
        int[] state = anchor instanceof TileAnchorWorld tile ? state(tile) : null;
        String status = anchor instanceof TileAnchorWorld tile ? tile.getLoadingStatus() : "";

        for (int var1 = 0; var1 < this.crafters.size(); ++var1) {
            ICrafting var2 = (ICrafting) this.crafters.get(var1);

            if (this.prevMinutesRemaining != mins) var2.sendProgressBarUpdate(this, 0, mins);
            if (state != null) {
                for (int i = 0; i < state.length; i++)
                    if (state[i] != previousState[i]) var2.sendProgressBarUpdate(this, i + 1, state[i]);
                if (!status.equals(previousStatus))
                    PacketBuilder.instance().sendGuiStringPacket(var2, windowId, 0, status);
            }
        }

        this.prevMinutesRemaining = mins;
        if (state != null) System.arraycopy(state, 0, previousState, 0, state.length);
        previousStatus = status;
    }

    private int[] state(TileAnchorWorld tile) {
        return new int[] { tile.getExtent(0), tile.getExtent(1), tile.getExtent(2), tile.getExtent(3),
                tile.getExtent(4), tile.getExtent(5), (int) Math.min(32767, tile.getVolume().size()),
                tile.hasActiveTicket() ? 1 : 0, AnchorLoading.isCubic(tile.getWorldObj()) ? 1 : 0,
                RailcraftConfig.anchorMaxExtent };
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void updateString(byte id, String data) {
        if (id == 0) loadingStatus = data;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void updateProgressBar(int id, int value) {
        if (id == 0) minutesRemaining = (short) value;
        else if (id >= 1 && id <= 6) extents[id - 1] = value;
        else if (id == 7) cubeCount = value;
        else if (id == 8) active = value != 0;
        else if (id == 9) cubic = value != 0;
        else if (id == 10) maxExtent = value;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return super.canInteractWith(player)
                && (!(anchor instanceof TileAnchorWorld tile) || player.worldObj.isRemote || tile.canConfigure(player));
    }

    @Override
    public boolean enchantItem(EntityPlayer player, int button) {
        if (player.worldObj.isRemote || !canInteractWith(player) || !(anchor instanceof TileAnchorWorld tile))
            return false;
        int[] next = new int[6];
        for (int i = 0; i < 6; i++) next[i] = tile.getExtent(i);
        if (button >= 0 && button < 12) {
            int side = button / 2;
            if ((side == 2 || side == 3) && !AnchorLoading.isCubic(tile.getWorldObj())) return false;
            next[side] += button % 2 == 0 ? -1 : 1;
        } else if (button == 12 || button == 13) {
            java.util.Arrays.fill(next, button == 12 ? 0 : 1);
        } else return false;
        tile.setExtents(next);
        return true;
    }
}
