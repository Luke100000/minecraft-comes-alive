package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.FamilyTreeView;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.GetFamilyTreeResponse;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.conczin.mca.server.world.data.FamilyTreeViewBuilder;
import net.conczin.mca.server.world.data.GraveyardManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;
import java.util.Optional;

public record GetFamilyTreeRequest(UUID uuid, int ancestorDepth, int descendantDepth, long requestId,
                                   boolean oneGeneration, Optional<UUID> after) implements HandleablePayload {
    public static final int MAX_DEPTH = FamilyTreeViewBuilder.MAX_DEPTH;
    public static final int DEFAULT_ANCESTOR_DEPTH = 1;
    public static final int DEFAULT_DESCENDANT_DEPTH = 1;

    public static final CustomPacketPayload.Type<GetFamilyTreeRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("get_family_tree_request"));
    public static final StreamCodec<FriendlyByteBuf, GetFamilyTreeRequest> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, GetFamilyTreeRequest::uuid,
            ByteBufCodecs.VAR_INT, GetFamilyTreeRequest::ancestorDepth,
            ByteBufCodecs.VAR_INT, GetFamilyTreeRequest::descendantDepth,
            ByteBufCodecs.VAR_LONG, GetFamilyTreeRequest::requestId,
            ByteBufCodecs.BOOL, GetFamilyTreeRequest::oneGeneration,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), GetFamilyTreeRequest::after,
            GetFamilyTreeRequest::new
    );

    public GetFamilyTreeRequest(UUID uuid, int ancestorDepth, int descendantDepth, long requestId) {
        this(uuid, ancestorDepth, descendantDepth, requestId, false, Optional.empty());
    }

    public GetFamilyTreeRequest {
        ancestorDepth = clampDepth(ancestorDepth);
        descendantDepth = clampDepth(descendantDepth);
        after = java.util.Objects.requireNonNull(after);
        // A cursor is meaningful only for a page request. An unknown non-nil UUID is
        // allowed because a previously returned relative might have been deleted.
        if (after.isPresent() && (!oneGeneration || !FamilyTreeNode.isValid(after.orElseThrow()))) {
            throw new IllegalArgumentException("Invalid family-tree generation cursor");
        }
    }

    private static int clampDepth(int depth) {
        return Math.max(0, Math.min(MAX_DEPTH, depth));
    }

    @Override
    public void handleServer(ServerPlayer player) {
        FamilyTree tree = FamilyTree.get(player.serverLevel());
        GraveyardManager graveyardManager = GraveyardManager.getGlobal(player.serverLevel());
        if (oneGeneration) {
            FamilyTreeView.Direction direction;
            if (ancestorDepth == 1 && descendantDepth == 0) {
                direction = FamilyTreeView.Direction.ANCESTORS;
            } else if (descendantDepth == 1 && ancestorDepth == 0) {
                direction = FamilyTreeView.Direction.DESCENDANTS;
            } else {
                Network.sendToPlayer(new GetFamilyTreeResponse(requestId, uuid, false,
                        FamilyTreeView.empty()), player);
                return;
            }
            FamilyTreeViewBuilder.buildGenerationPage(tree, uuid, direction, after,
                            graveyardManager::getOccupiedGrave)
                    .ifPresentOrElse(
                            page -> Network.sendToPlayer(new GetFamilyTreeResponse(requestId, uuid,
                                    true, page.view(), page.nextCursor()), player),
                            () -> Network.sendToPlayer(new GetFamilyTreeResponse(requestId, uuid,
                                    false, FamilyTreeView.empty()), player)
                    );
            return;
        }
        FamilyTreeViewBuilder.build(tree, uuid, ancestorDepth, descendantDepth, graveyardManager::getOccupiedGrave)
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
