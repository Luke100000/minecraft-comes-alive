package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.VillagerNameResponse;
import net.conczin.mca.resources.Names;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record VillagerNameRequest(Gender gender) implements HandleablePayload {
    public static final CustomPacketPayload.Type<VillagerNameRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("villager_name_request"));
    public static final StreamCodec<FriendlyByteBuf, VillagerNameRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, r -> r.gender().ordinal(),
            i -> new VillagerNameRequest(Gender.byId(i))
    );

    @Override
    public void handleServer(ServerPlayer player) {
        String name = Names.pickCitizenName(gender);
        Network.sendToPlayer(new VillagerNameResponse(name), player);
    }

    @Override
    public CustomPacketPayload.Type<VillagerNameRequest> type() {
        return TYPE;
    }
}
