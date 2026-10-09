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
import mods.railcraft.common.plugins.forge.PlayerPlugin;
import mods.railcraft.common.util.anchors.AnchorLoading;
import mods.railcraft.common.util.misc.IAnchor;

public class ContainerAnchor extends RailcraftContainer {

    public final IAnchor anchor;
    public short minutesRemaining;
    private short prevMinutesRemaining;
    public final int[] extents = new int[6];
    public int cubeCount, maxExtent;
    public boolean active, cubic;

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

        for (int var1 = 0; var1 < this.crafters.size(); ++var1) {
            ICrafting var2 = (ICrafting) this.crafters.get(var1);

            if (this.prevMinutesRemaining != mins) var2.sendProgressBarUpdate(this, 0, mins);
            if (anchor instanceof TileAnchorWorld tile) {
                for (int i = 0; i < 6; i++) var2.sendProgressBarUpdate(this, i + 1, tile.getExtent(i));
                var2.sendProgressBarUpdate(this, 7, (int) Math.min(32767, tile.getVolume().size()));
                var2.sendProgressBarUpdate(this, 8, tile.hasActiveTicket() ? 1 : 0);
                var2.sendProgressBarUpdate(this, 9, AnchorLoading.isCubic(tile.getWorldObj()) ? 1 : 0);
                var2.sendProgressBarUpdate(this, 10, RailcraftConfig.anchorMaxExtent);
            }
        }

        this.prevMinutesRemaining = mins;
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
        return super.canInteractWith(player) && (!(anchor instanceof TileAnchorWorld tile) || player.worldObj.isRemote
                || PlayerPlugin.isOwnerOrOp(tile.getOwner(), player));
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
