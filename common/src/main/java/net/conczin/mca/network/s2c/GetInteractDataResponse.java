package net.conczin.mca.network.s2c;

import io.netty.buffer.ByteBuf;
import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.entity.interaction.Constraint;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public record GetInteractDataResponse(
        int villagerEntityId,
        UUID interactionId,
        Set<Constraint> constraints,
        Optional<String> father,
        Optional<String> mother,
        Optional<String> spouse,
        RelationshipState marriageState
) implements HandleablePayload {
    public static final CustomPacketPayload.Type<GetInteractDataResponse> TYPE = new CustomPacketPayload.Type<>(MCA.locate("get_interact_data_response"));
    private static final StreamCodec<ByteBuf, Set<Constraint>> CONSTRAINT_CODEC =
            ByteBufCodecs.idMapper(i -> Constraint.values()[i], Constraint::ordinal).apply(set());
    private static final StreamCodec<ByteBuf, Optional<String>> NAME_CODEC = ByteBufCodecs.optional(ByteBufCodecs.STRING_UTF8);
    private static final StreamCodec<ByteBuf, RelationshipState> RELATIONSHIP_CODEC =
            ByteBufCodecs.idMapper(i -> RelationshipState.values()[i], RelationshipState::ordinal);

    public static final StreamCodec<FriendlyByteBuf, GetInteractDataResponse> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public GetInteractDataResponse decode(FriendlyByteBuf buf) {
            int villagerEntityId = ByteBufCodecs.INT.decode(buf);
            UUID interactionId = UUIDUtil.STREAM_CODEC.decode(buf);
            return new GetInteractDataResponse(villagerEntityId, interactionId, CONSTRAINT_CODEC.decode(buf),
                    NAME_CODEC.decode(buf), NAME_CODEC.decode(buf), NAME_CODEC.decode(buf), RELATIONSHIP_CODEC.decode(buf));
        }

        @Override
        public void encode(FriendlyByteBuf buf, GetInteractDataResponse response) {
            ByteBufCodecs.INT.encode(buf, response.villagerEntityId());
            UUIDUtil.STREAM_CODEC.encode(buf, response.interactionId());
            CONSTRAINT_CODEC.encode(buf, response.constraints());
            NAME_CODEC.encode(buf, response.father());
            NAME_CODEC.encode(buf, response.mother());
            NAME_CODEC.encode(buf, response.spouse());
            RELATIONSHIP_CODEC.encode(buf, response.marriageState());
        }
    };

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
