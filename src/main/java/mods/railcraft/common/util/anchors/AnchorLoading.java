package mods.railcraft.common.util.anchors;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.common.ForgeChunkManager.Ticket;

import com.cardinalstar.cubicchunks.api.ICube;
import com.cardinalstar.cubicchunks.mixin.api.ICubicWorldInternal;
import com.cardinalstar.cubicchunks.util.CubePos;
import com.cardinalstar.cubicchunks.util.ITicket;
import com.cardinalstar.cubicchunks.world.ICubicWorld;
import com.cardinalstar.cubicchunks.world.chunkloader.CubicChunkManager;
import com.cardinalstar.cubicchunks.world.core.ICubicTicketInternal;
import com.cardinalstar.cubicchunks.world.cube.Cube;

import cpw.mods.fml.common.Loader;
import mods.railcraft.common.core.Railcraft;

/** Optional CC types stay inside the lazily linked adapter. Never force 2D columns in CC. */
public final class AnchorLoading {

    private AnchorLoading() {}

    public static boolean isCubic(World world) {
        return Loader.isModLoaded("cubicchunks") && Cubic.isCubic(world);
    }

    public static int minCube(World world) {
        return isCubic(world) ? Cubic.min(world) : 0;
    }

    public static int maxCube(World world) {
        return isCubic(world) ? Cubic.max(world) : 0;
    }

    public static int ticketLimit(Ticket ticket) {
        return isCubic(ticket.world) ? Cubic.limit(ticket) : ticket.getMaxChunkListDepth();
    }

    public static int ticketLimit(World world) {
        return isCubic(world) ? Cubic.defaultLimit() : ForgeChunkManager.getMaxChunkDepthFor(Railcraft.MOD_ID);
    }

    public static void loadBlock(World world, int x, int y, int z) {
        if (isCubic(world)) Cubic.load(world, x, y, z);
        else world.getChunkFromChunkCoords(x >> 4, z >> 4);
    }

    public static void force(Ticket ticket, AnchorVolume volume) {
        if (isCubic(ticket.world)) {
            Cubic.force(ticket, volume);
        } else {
            for (ChunkCoordIntPair pos : volume.columns()) {
                ForgeChunkManager.forceChunk(ticket, pos);
                ticket.world.getChunkFromChunkCoords(pos.chunkXPos, pos.chunkZPos);
            }
        }
    }

    public static void release(Ticket ticket) {
        if (isCubic(ticket.world)) Cubic.release(ticket);
        ForgeChunkManager.releaseTicket(ticket);
    }

    public static boolean hasExternalCubes(World world, Set<Ticket> ours) {
        return isCubic(world) && Cubic.hasExternal(world, ours);
    }

    private static final class Cubic {

        static boolean isCubic(World world) {
            return world instanceof ICubicWorld;
        }

        static int min(World world) {
            return ((ICubicWorld) world).getMinHeight() >> 4;
        }

        static int max(World world) {
            return (((ICubicWorld) world).getMaxHeight() - 1) >> 4;
        }

        static int limit(Ticket ticket) {
            return ((ICubicTicketInternal) ticket).getMaxCubeDepth();
        }

        static int defaultLimit() {
            return CubicChunkManager.getCubeDepthFor(Railcraft.MOD_ID);
        }

        static void load(World world, int x, int y, int z) {
            ((ICubicWorld) world).getCubeFromCubeCoords(x >> 4, y >> 4, z >> 4);
        }

        static void force(Ticket ticket, AnchorVolume volume) {
            // Old Forge tickets may contain serialized cube coordinates; replace them with the validated volume.
            release(ticket);
            for (int x = volume.minX; x <= volume.maxX; x++) {
                for (int y = volume.minY; y <= volume.maxY; y++) {
                    for (int z = volume.minZ; z <= volume.maxZ; z++) {
                        CubicChunkManager.forceChunk(ticket, new CubePos(x, y, z));
                    }
                }
            }
        }

        static void release(Ticket ticket) {
            ICubicTicketInternal cubic = (ICubicTicketInternal) ticket;
            for (var entry : new ArrayList<>(cubic.getAllForcedChunkCubes().entrySet())) {
                for (int y : entry.getValue().toIntArray()) {
                    ICube loaded = ((ICubicWorld) ticket.world).getCubeCache()
                            .getLoadedCube(entry.getKey().chunkXPos, y, entry.getKey().chunkZPos);
                    if (loaded instanceof Cube) ((Cube) loaded).getTickets().remove(cubic);
                    cubic.removeRequestedCube(new CubePos(entry.getKey().chunkXPos, y, entry.getKey().chunkZPos));
                }
            }
            cubic.requestedCubes().clear();
        }

        static boolean hasExternal(World world, Set<Ticket> ours) {
            Set<ITicket> excluded = new HashSet<>();
            for (Ticket ticket : ours) excluded.add((ICubicTicketInternal) ticket);
            for (ICube cube : ((ICubicWorldInternal.Server) world).getForcedCubes()) {
                if (((Cube) cube).getTickets().anyMatch(ticket -> !excluded.contains(ticket))) return true;
            }
            return false;
        }
    }
}
