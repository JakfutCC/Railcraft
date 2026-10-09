package mods.railcraft.common.util.anchors;

import java.util.LinkedHashSet;
import java.util.Set;

import net.minecraft.world.ChunkCoordIntPair;

/** Inclusive cube coordinates. Vanilla volumes contain one layer representing full columns. */
public final class AnchorVolume {

    public final int minX, minY, minZ, maxX, maxY, maxZ;

    public AnchorVolume(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (minX > maxX || minY > maxY || minZ > maxZ) throw new IllegalArgumentException("Empty anchor volume");
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
    }

    public long size() {
        return Math.multiplyExact(
                Math.multiplyExact((long) maxX - minX + 1, (long) maxY - minY + 1),
                (long) maxZ - minZ + 1);
    }

    public Set<ChunkCoordIntPair> columns() {
        Set<ChunkCoordIntPair> result = new LinkedHashSet<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) result.add(new ChunkCoordIntPair(x, z));
        }
        return result;
    }
}
