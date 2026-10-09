/*
 * Copyright (c) CovertJaguar, 2014 http://railcraft.info This code is the property of CovertJaguar and may only be used
 * with explicit written permission unless otherwise specified on the license page at
 * http://railcraft.info/wiki/info:license.
 */
package mods.railcraft.client.gui;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.util.StatCollector;

import mods.railcraft.common.blocks.machine.alpha.TileAnchorWorld;
import mods.railcraft.common.core.RailcraftConstants;
import mods.railcraft.common.gui.containers.ContainerAnchor;
import mods.railcraft.common.plugins.forge.LocalizationPlugin;

public class GuiWorldAnchor extends TileGui {

    private final TileAnchorWorld tile;
    private final ContainerAnchor container;

    public GuiWorldAnchor(InventoryPlayer playerInv, TileAnchorWorld anchor) {
        super(
                anchor,
                new ContainerAnchor(playerInv, anchor),
                RailcraftConstants.GUI_TEXTURE_FOLDER + "gui_single_slot.png");
        xSize = 176;
        ySize = 240;
        this.tile = anchor;
        container = (ContainerAnchor) inventorySlots;
    }

    @Override
    public void initGui() {
        super.initGui();
        for (int i = 0; i < 6; i++) {
            int x = guiLeft + 8 + (i % 2) * 84;
            int y = guiTop + 57 + (i / 2) * 23;
            buttonList.add(new GuiButton(i * 2, x, y, 18, 18, "-"));
            buttonList.add(new GuiButton(i * 2 + 1, x + 56, y, 18, 18, "+"));
        }
        buttonList.add(new GuiButton(12, guiLeft + 8, guiTop + 128, 48, 18, "Mini"));
        buttonList.add(new GuiButton(13, guiLeft + 60, guiTop + 128, 48, 18, "Default"));
        buttonList.add(new GuiButton(14, guiLeft + 112, guiTop + 128, 56, 18, "Outline"));
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        for (Object value : buttonList) {
            GuiButton button = (GuiButton) value;
            if (button.id < 12) {
                int side = button.id / 2;
                button.enabled = (container.cubic || side != 2 && side != 3)
                        && (button.id % 2 == 0 ? container.extents[side] > 0
                                : container.extents[side] < container.maxExtent);
            }
        }
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 14) mods.railcraft.client.render.AnchorOverlay.toggle(tile);
        else mc.playerController.sendEnchantPacket(inventorySlots.windowId, button.id);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partial, int mouseX, int mouseY) {
        drawRect(guiLeft, guiTop, guiLeft + xSize, guiTop + ySize, 0xffc6c6c6);
        drawRect(guiLeft + 2, guiTop + 2, guiLeft + xSize - 2, guiTop + ySize - 2, 0xffdddddd);
        for (Object value : inventorySlots.inventorySlots) {
            Slot slot = (Slot) value;
            int x = guiLeft + slot.xDisplayPosition, y = guiTop + slot.yDisplayPosition;
            drawRect(x - 1, y - 1, x + 17, y + 17, 0xff555555);
            drawRect(x, y, x + 16, y + 16, 0xff999999);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        String label = getGuiTitle();
        int sWidth = fontRendererObj.getStringWidth(label);
        int sPos = xSize / 2 - sWidth / 2;
        fontRendererObj.drawString(label, sPos, 6, 0x404040);
        fontRendererObj.drawString(fontRendererObj.trimStringToWidth(container.loadingStatus, 136), 32, 24, 0x404040);
        fontRendererObj.drawString(
                LocalizationPlugin
                        .translate("railcraft.gui.anchor.fuel.remaining", (double) container.minutesRemaining / 60),
                32,
                35,
                0x404040);
        fontRendererObj.drawString(container.cubeCount + (container.cubic ? " cubes" : " chunks"), 8, 46, 0x404040);
        String[] names = { "-X", "+X", "-Y", "+Y", "-Z", "+Z" };
        for (int i = 0; i < 6; i++) {
            fontRendererObj
                    .drawString(names[i] + ":" + container.extents[i], 29 + (i % 2) * 84, 62 + (i / 2) * 23, 0x404040);
        }
        fontRendererObj.drawString(StatCollector.translateToLocal("container.inventory"), 8, 148, 0x404040);
        int x = mouseX - guiLeft, y = mouseY - guiTop;
        if (x >= 32 && x < 168 && y >= 24 && y < 34)
            drawHoveringText(java.util.Collections.singletonList(container.loadingStatus), x, y, fontRendererObj);
    }
}
