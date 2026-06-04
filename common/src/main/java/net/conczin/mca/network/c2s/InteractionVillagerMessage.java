package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

public record InteractionVillagerMessage(String command, UUID villagerUUID) implements HandleablePayload {
    public static final CustomPacketPayload.Type<InteractionVillagerMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("interaction_villager"));
    public static final StreamCodec<FriendlyByteBuf, InteractionVillagerMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, InteractionVillagerMessage::command,
            UUIDUtil.STREAM_CODEC, InteractionVillagerMessage::villagerUUID,
            InteractionVillagerMessage::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        Entity v = ((ServerLevel) player.level()).getEntity(villagerUUID);
        if (v instanceof VillagerLike<?> villager && villager.getInteractions().handle(player, command)) {
            villager.getInteractions().stopInteracting();
        }
    }

    @Override
    public Type<InteractionVillagerMessage> type() {
        return TYPE;
    }
}
