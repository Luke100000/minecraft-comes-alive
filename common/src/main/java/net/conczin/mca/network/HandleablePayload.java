package net.conczin.mca.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

public interface HandleablePayload extends CustomPacketPayload {
    default void handle(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            handleServer(serverPlayer);
        }
    }

    default void handleServer(ServerPlayer player) {

    }
}