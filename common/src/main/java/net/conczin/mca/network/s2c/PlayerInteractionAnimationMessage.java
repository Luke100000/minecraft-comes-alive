package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public record PlayerInteractionAnimationMessage(UUID source, UUID target, String action, int durationTicks, float strength) implements HandleablePayload {
    public static final CustomPacketPayload.Type<PlayerInteractionAnimationMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("player_interaction_animation"));
    public static final StreamCodec<FriendlyByteBuf, PlayerInteractionAnimationMessage> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PlayerInteractionAnimationMessage::source,
            UUIDUtil.STREAM_CODEC, PlayerInteractionAnimationMessage::target,
            ByteBufCodecs.STRING_UTF8, PlayerInteractionAnimationMessage::action,
            ByteBufCodecs.INT, PlayerInteractionAnimationMessage::durationTicks,
            ByteBufCodecs.FLOAT, PlayerInteractionAnimationMessage::strength,
            PlayerInteractionAnimationMessage::new
    );

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handlePlayerInteractionAnimation(this);
    }

    @Override
    public CustomPacketPayload.Type<PlayerInteractionAnimationMessage> type() {
        return TYPE;
    }
}
