package mods.railcraft.client.gui;

import java.util.List;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.StatCollector;

import mods.railcraft.client.render.AnchorOverlay;

public final class GuiAnchorList extends GuiScreen {

    private final List<NBTTagCompound> entries;
    private final int page, pages;
    private final boolean all;
    private int left, top;

    public GuiAnchorList(List<NBTTagCompound> entries, int page, int pages, boolean all) {
        this.entries = entries;
        this.page = page;
        this.pages = pages;
        this.all = all;
    }

    @Override
    public void initGui() {
        left = (width - 320) / 2;
        top = Math.max(4, (height - 220) / 2);
        for (int i = 0; i < entries.size(); i++) {
            GuiButton button = new GuiButton(i, left + 255, top + 26 + i * 27, 65, 20, "Highlight");
            button.enabled = entries.get(i).getInteger("dim") == mc.thePlayer.dimension;
            buttonList.add(button);
        }
        buttonList.add(new GuiButton(100, left, top + 190, 70, 20, "<"));
        buttonList.add(new GuiButton(101, left + 75, top + 190, 85, 20, "Refresh"));
        buttonList.add(new GuiButton(102, left + 165, top + 190, 70, 20, ">"));
        buttonList.add(new GuiButton(103, left + 240, top + 190, 80, 20, "Done"));
    }

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id < entries.size()) {
            AnchorOverlay.highlight(entries.get(button.id));
            mc.displayGuiScreen(null);
        } else if (button.id == 103) mc.displayGuiScreen(null);
        else {
            int next = Math.max(0, Math.min(pages - 1, page + (button.id == 100 ? -1 : button.id == 102 ? 1 : 0)));
            mc.thePlayer.sendChatMessage("/rcanchors " + (all ? "all " : "") + (next + 1));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partial) {
        drawDefaultBackground();
        drawCenteredString(
                fontRendererObj,
                (all ? "All anchors" : "My anchors") + " - " + (page + 1) + "/" + pages,
                width / 2,
                top + 5,
                0xffffff);
        for (int i = 0; i < entries.size(); i++) {
            NBTTagCompound entry = entries.get(i);
            int y = top + 27 + i * 27;
            String title = StatCollector.translateToLocal(entry.getString("type") + ".name") + " | "
                    + entry.getString("owner");
            fontRendererObj.drawString(
                    fontRendererObj.trimStringToWidth(title, 247),
                    left,
                    y,
                    entry.getBoolean("active") ? 0x80ffa0 : 0xbbbbbb);
            String coords = "D" + entry.getInteger(
                    "dim") + "  " + entry.getInteger("x") + ", " + entry.getInteger("y") + ", " + entry.getInteger("z");
            fontRendererObj.drawString(fontRendererObj.trimStringToWidth(coords, 247), left, y + 11, 0xdddddd);
        }
        super.drawScreen(mouseX, mouseY, partial);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
