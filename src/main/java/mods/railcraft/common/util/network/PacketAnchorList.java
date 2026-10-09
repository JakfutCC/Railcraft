package mods.railcraft.common.util.network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import mods.railcraft.client.gui.GuiAnchorList;

/** Bounded, server-to-client snapshots. Opening the list never loads a world or cube. */
public final class PacketAnchorList extends RailcraftPacket {

    public static final int PAGE_SIZE = 6;
    private List<NBTTagCompound> entries;
    private int page, pages;
    private boolean all;

    public PacketAnchorList() {}

    public PacketAnchorList(List<NBTTagCompound> entries, int page, int pages, boolean all) {
        this.entries = new ArrayList<>(entries);
        this.page = page;
        this.pages = pages;
        this.all = all;
    }

    @Override
    public int getID() {
        return PacketType.ANCHOR_LIST.ordinal();
    }

    @Override
    public void writeData(DataOutputStream data) throws IOException {
        data.writeInt(page);
        data.writeInt(pages);
        data.writeBoolean(all);
        data.writeInt(entries.size());
        for (NBTTagCompound entry : entries) {
            data.writeUTF(entry.getString("type"));
            data.writeUTF(entry.getString("owner"));
            for (String key : new String[] { "dim", "x", "y", "z", "cost" }) data.writeInt(entry.getInteger(key));
            int[] bounds = entry.getIntArray("bounds");
            for (int i = 0; i < 6; i++) data.writeInt(bounds.length == 6 ? bounds[i] : 0);
            data.writeBoolean(entry.getBoolean("cubic"));
            data.writeBoolean(entry.getBoolean("active"));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void readData(DataInputStream data) throws IOException {
        int page = data.readInt(), pages = data.readInt();
        boolean all = data.readBoolean();
        int count = data.readInt();
        if (count < 0 || count > PAGE_SIZE || page < 0 || pages < 1 || page >= pages)
            throw new IOException("Invalid anchor page");
        List<NBTTagCompound> entries = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setString("type", data.readUTF());
            entry.setString("owner", data.readUTF());
            for (String key : new String[] { "dim", "x", "y", "z", "cost" }) entry.setInteger(key, data.readInt());
            int[] bounds = new int[6];
            for (int j = 0; j < 6; j++) bounds[j] = data.readInt();
            entry.setIntArray("bounds", bounds);
            entry.setBoolean("cubic", data.readBoolean());
            entry.setBoolean("active", data.readBoolean());
            entries.add(entry);
        }
        Minecraft.getMinecraft().func_152344_a(
                () -> Minecraft.getMinecraft().displayGuiScreen(new GuiAnchorList(entries, page, pages, all)));
    }
}
