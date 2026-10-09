package mods.railcraft.common.util.network;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import net.minecraft.entity.player.EntityPlayerMP;

import mods.railcraft.common.util.anchors.AnchorMapRequests;

public final class PacketAnchorMapRequest extends RailcraftPacket {

    private EntityPlayerMP player;
    private int minX, maxX, minZ, maxZ;

    public PacketAnchorMapRequest(EntityPlayerMP player) {
        this.player = player;
    }

    public PacketAnchorMapRequest(int minX, int maxX, int minZ, int maxZ) {
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
    }

    @Override
    public int getID() {
        return PacketType.ANCHOR_MAP_REQUEST.ordinal();
    }

    @Override
    public void writeData(DataOutputStream data) throws IOException {
        data.writeInt(minX);
        data.writeInt(maxX);
        data.writeInt(minZ);
        data.writeInt(maxZ);
    }

    @Override
    public void readData(DataInputStream data) throws IOException {
        int x0 = data.readInt(), x1 = data.readInt(), z0 = data.readInt(), z1 = data.readInt();
        if (x0 > x1 || z0 > z1 || (long) x1 - x0 > 4096 || (long) z1 - z0 > 4096) return;
        AnchorMapRequests.request(player, x0, x1, z0, z1);
    }
}
