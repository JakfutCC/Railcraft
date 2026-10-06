/*
 * Copyright (c) CovertJaguar, 2014 http://railcraft.info This code is the property of CovertJaguar and may only be used
 * with explicit written permission unless otherwise specified on the license page at
 * http://railcraft.info/wiki/info:license.
 */
package mods.railcraft.common.util.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;

import com.cardinalstar.cubicchunks.server.CubicPlayerManager;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;
import cpw.mods.fml.common.network.internal.FMLProxyPacket;

/**
 *
 * @author CovertJaguar <http://www.railcraft.info/>
 */
public class PacketDispatcher {

    public static void sendToServer(RailcraftPacket packet) {
        PacketHandler.INSTANCE.channel.sendToServer(packet.getPacket());
    }

    public static void sendToPlayer(RailcraftPacket packet, EntityPlayerMP player) {
        PacketHandler.INSTANCE.channel.sendTo(packet.getPacket(), player);
    }

    public static void sendToAll(RailcraftPacket packet) {
        PacketHandler.INSTANCE.channel.sendToAll(packet.getPacket());
    }

    public static void sendToAllAround(RailcraftPacket packet, TargetPoint zone) {
        PacketHandler.INSTANCE.channel.sendToAllAround(packet.getPacket(), zone);
    }

    public static void sendToDimension(RailcraftPacket packet, int dimensionId) {
        PacketHandler.INSTANCE.channel.sendToDimension(packet.getPacket(), dimensionId);
    }

    public static void sendToWatchers(RailcraftPacket packet, WorldServer world, int worldX, int worldZ) {
        sendToWatchers(packet, world, worldX, 0, worldZ, false);
    }

    public static void sendToWatchers(RailcraftPacket packet, WorldServer world, int worldX, int worldY, int worldZ) {
        sendToWatchers(packet, world, worldX, worldY, worldZ, true);
    }

    private static void sendToWatchers(RailcraftPacket packet, WorldServer world, int worldX, int worldY, int worldZ,
            boolean checkHeight) {
        boolean cubic = checkHeight && Loader.isModLoaded("cubicchunks") && CubicWatchers.isCubic(world);
        FMLProxyPacket serializedPacket = null;
        // Query the manager rather than its private vanilla-only watcher storage.
        for (Object candidate : world.playerEntities) {
            if (candidate instanceof EntityPlayerMP player
                    && (cubic ? CubicWatchers.isWatching(world, player, worldX, worldY, worldZ)
                            : world.getPlayerManager().isPlayerWatchingChunk(player, worldX >> 4, worldZ >> 4))) {
                if (serializedPacket == null) serializedPacket = packet.getPacket();
                PacketHandler.INSTANCE.channel.sendTo(serializedPacket, player);
            }
        }
    }

    private static final class CubicWatchers {

        private static boolean isCubic(WorldServer world) {
            return world.getPlayerManager() instanceof CubicPlayerManager;
        }

        private static boolean isWatching(WorldServer world, EntityPlayerMP player, int x, int y, int z) {
            return ((CubicPlayerManager) world.getPlayerManager()).isPlayerWatchingCube(player, x >> 4, y >> 4, z >> 4);
        }
    }
}
