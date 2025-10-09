package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public record GetFamilyTreeResponse(UUID uuid, Map<UUID, FamilyTreeNode> family) implements HandleablePayload {
    public static final CustomPacketPayload.Type<GetFamilyTreeResponse> TYPE = new CustomPacketPayload.Type<>(MCA.locate("get_family_tree_response"));
    public static final StreamCodec<FriendlyByteBuf, GetFamilyTreeResponse> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, GetFamilyTreeResponse::uuid,
            ByteBufCodecs.map(
                    HashMap::new,
                    UUIDUtil.STREAM_CODEC,
                    FamilyTreeNode.STREAM_CODEC
            ), GetFamilyTreeResponse::family,
            GetFamilyTreeResponse::new
    );

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleFamilyTreeResponse(this);
    }

    @Override
    public CustomPacketPayload.Type<GetFamilyTreeResponse> type() {
        return TYPE;
    }
}
