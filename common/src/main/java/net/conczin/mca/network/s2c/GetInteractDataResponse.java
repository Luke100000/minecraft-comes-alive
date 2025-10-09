package net.conczin.mca.network.s2c;

import io.netty.buffer.ByteBuf;
import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.entity.interaction.Constraint;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public record GetInteractDataResponse(
        Set<Constraint> constraints,
        Optional<String> father,
        Optional<String> mother,
        Optional<String> spouse,
        RelationshipState marriageState
) implements HandleablePayload {
    public static final CustomPacketPayload.Type<GetInteractDataResponse> TYPE = new CustomPacketPayload.Type<>(MCA.locate("get_interact_data_response"));
    public static final StreamCodec<FriendlyByteBuf, GetInteractDataResponse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.idMapper(i -> Constraint.values()[i], Constraint::ordinal).apply(set()), GetInteractDataResponse::constraints,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), GetInteractDataResponse::father,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), GetInteractDataResponse::mother,
            ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8), GetInteractDataResponse::spouse,
            ByteBufCodecs.idMapper(i -> RelationshipState.values()[i], RelationshipState::ordinal), GetInteractDataResponse::marriageState,
            GetInteractDataResponse::new
    );

    static <B extends ByteBuf, V> StreamCodec.CodecOperation<B, V, Set<V>> set() {
        return (codec) -> ByteBufCodecs.collection(HashSet::new, codec);
    }

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleInteractDataResponse(this);
    }

    @Override
    public Type<GetInteractDataResponse> type() {
        return TYPE;
    }
}
