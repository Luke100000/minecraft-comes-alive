package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.FamilyTreeSearchEntry;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public record FamilyTreeUUIDResponse(List<FamilyTreeSearchEntry> list) implements HandleablePayload {
    public static final CustomPacketPayload.Type<FamilyTreeUUIDResponse> TYPE = new CustomPacketPayload.Type<>(MCA.locate("family_tree_uuid_response"));
    public static final StreamCodec<FriendlyByteBuf, FamilyTreeUUIDResponse> STREAM_CODEC = StreamCodec.composite(
            FamilyTreeSearchEntry.STREAM_CODEC.apply(ByteBufCodecs.list()), FamilyTreeUUIDResponse::list,
            FamilyTreeUUIDResponse::new
    );

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleFamilyTreeUUIDResponse(this);
    }

    @Override
    public CustomPacketPayload.Type<FamilyTreeUUIDResponse> type() {
        return TYPE;
    }
}
