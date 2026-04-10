package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.ServerInteractionManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public record InteractionPlayerMessage(String command, UUID targetUUID) implements HandleablePayload {
    public static final CustomPacketPayload.Type<InteractionPlayerMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("interaction_player"));
    public static final StreamCodec<FriendlyByteBuf, InteractionPlayerMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, InteractionPlayerMessage::command,
            UUIDUtil.STREAM_CODEC, InteractionPlayerMessage::targetUUID,
            InteractionPlayerMessage::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        Entity target = player.level().getPlayerByUUID(targetUUID);
        if (target instanceof ServerPlayer targetPlayer && targetPlayer != player && player.distanceToSqr(targetPlayer) <= 36.0D) {
            switch (command) {
                case "propose" -> ServerInteractionManager.getInstance().sendProposal(player, targetPlayer);
                case "engage" -> ServerInteractionManager.getInstance().engage(player, targetPlayer);
                case "hug" -> ServerInteractionManager.getInstance().hug(player, targetPlayer);
                case "kiss" -> ServerInteractionManager.getInstance().kiss(player, targetPlayer);
                case "marry" -> ServerInteractionManager.getInstance().marry(player, targetPlayer);
                case "procreate" -> ServerInteractionManager.getInstance().procreate(player, targetPlayer);
                default -> {
                }
            }
        }
    }

    @Override
    public CustomPacketPayload.Type<InteractionPlayerMessage> type() {
        return TYPE;
    }
}
