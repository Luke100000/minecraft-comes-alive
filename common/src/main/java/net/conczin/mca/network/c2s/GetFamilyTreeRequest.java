package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.FamilyTreeView;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.GetFamilyTreeResponse;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeViewBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public record GetFamilyTreeRequest(UUID uuid, int ancestorDepth, int descendantDepth, long requestId) implements HandleablePayload {
    public static final int MAX_DEPTH = FamilyTreeViewBuilder.MAX_DEPTH;
    public static final int DEFAULT_ANCESTOR_DEPTH = MAX_DEPTH;
    public static final int DEFAULT_DESCENDANT_DEPTH = MAX_DEPTH;

    public static final CustomPacketPayload.Type<GetFamilyTreeRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("get_family_tree_request"));
    public static final StreamCodec<FriendlyByteBuf, GetFamilyTreeRequest> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, GetFamilyTreeRequest::uuid,
            ByteBufCodecs.VAR_INT, GetFamilyTreeRequest::ancestorDepth,
            ByteBufCodecs.VAR_INT, GetFamilyTreeRequest::descendantDepth,
            ByteBufCodecs.VAR_LONG, GetFamilyTreeRequest::requestId,
            GetFamilyTreeRequest::new
    );

    public GetFamilyTreeRequest {
        ancestorDepth = clampDepth(ancestorDepth);
        descendantDepth = clampDepth(descendantDepth);
    }

    private static int clampDepth(int depth) {
        return Math.max(0, Math.min(MAX_DEPTH, depth));
    }

    @Override
    public void handleServer(ServerPlayer player) {
        FamilyTree tree = FamilyTree.get(player.serverLevel());
        FamilyTreeViewBuilder.build(tree, uuid, ancestorDepth, descendantDepth)
                .ifPresentOrElse(
                        view -> Network.sendToPlayer(new GetFamilyTreeResponse(requestId, uuid, true, view), player),
                        () -> Network.sendToPlayer(new GetFamilyTreeResponse(requestId, uuid, false, FamilyTreeView.empty()), player)
                );
    }

    @Override
    public CustomPacketPayload.Type<GetFamilyTreeRequest> type() {
        return TYPE;
    }
}
