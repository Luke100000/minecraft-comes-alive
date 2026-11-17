package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.FamilyTreeView;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;
import java.util.Optional;

public record GetFamilyTreeResponse(long requestId, UUID uuid, boolean found, FamilyTreeView view,
                                    Optional<UUID> nextCursor) implements HandleablePayload {
    public static final CustomPacketPayload.Type<GetFamilyTreeResponse> TYPE = new CustomPacketPayload.Type<>(MCA.locate("get_family_tree_response"));
    public static final StreamCodec<FriendlyByteBuf, GetFamilyTreeResponse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, GetFamilyTreeResponse::requestId,
            UUIDUtil.STREAM_CODEC, GetFamilyTreeResponse::uuid,
            ByteBufCodecs.BOOL, GetFamilyTreeResponse::found,
            FamilyTreeView.STREAM_CODEC, GetFamilyTreeResponse::view,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), GetFamilyTreeResponse::nextCursor,
            GetFamilyTreeResponse::new
    );

    public GetFamilyTreeResponse(long requestId, UUID uuid, boolean found, FamilyTreeView view) {
        this(requestId, uuid, found, view, Optional.empty());
    }

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleFamilyTreeResponse(this);
    }

    @Override
    public CustomPacketPayload.Type<GetFamilyTreeResponse> type() {
        return TYPE;
    }
}
