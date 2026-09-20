/*
 * Copyright (c) CovertJaguar, 2014 http://railcraft.info This code is the property of CovertJaguar and may only be used
 * with explicit written permission unless otherwise specified on the license page at
 * http://railcraft.info/wiki/info:license.
 */
package mods.railcraft.common.util.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.world.WorldServer;

import cpw.mods.fml.common.network.NetworkRegistry.TargetPoint;

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
        // Query the manager rather than its private vanilla-only watcher storage.
        for (Object candidate : world.playerEntities) {
            if (candidate instanceof EntityPlayerMP player
                    && world.getPlayerManager().isPlayerWatchingChunk(player, worldX >> 4, worldZ >> 4)) {
                sendToPlayer(packet, player);
            }
        }
    }
}
