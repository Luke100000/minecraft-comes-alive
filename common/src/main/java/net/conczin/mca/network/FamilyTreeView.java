package net.conczin.mca.network;

import io.netty.buffer.ByteBuf;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record FamilyTreeView(
        Map<UUID, FamilyTreeNode> nodes,
        Set<Continuation> continuations,
        Set<UUID> unavailable
) {
    private static final StreamCodec<ByteBuf, Direction> DIRECTION_CODEC = ByteBufCodecs.idMapper(
            id -> Direction.values()[id],
            Direction::ordinal
    );
    private static final StreamCodec<FriendlyByteBuf, Continuation> CONTINUATION_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, Continuation::anchor,
            DIRECTION_CODEC, Continuation::direction,
            Continuation::new
    );
    private static final StreamCodec<FriendlyByteBuf, Map<UUID, FamilyTreeNode>> NODES_CODEC = ByteBufCodecs.map(
            HashMap::new,
            UUIDUtil.STREAM_CODEC,
            FamilyTreeNode.STREAM_CODEC
    );
    private static final StreamCodec<FriendlyByteBuf, Set<Continuation>> CONTINUATIONS_CODEC = ByteBufCodecs.collection(
            HashSet::new,
            CONTINUATION_CODEC
    );
    private static final StreamCodec<FriendlyByteBuf, Set<UUID>> UUID_SET_CODEC = ByteBufCodecs.collection(
            HashSet::new,
            UUIDUtil.STREAM_CODEC
    );

    public static final StreamCodec<FriendlyByteBuf, FamilyTreeView> STREAM_CODEC = StreamCodec.composite(
            NODES_CODEC, FamilyTreeView::nodes,
            CONTINUATIONS_CODEC, FamilyTreeView::continuations,
            UUID_SET_CODEC, FamilyTreeView::unavailable,
            FamilyTreeView::new
    );

    public FamilyTreeView {
        nodes = Map.copyOf(nodes);
        continuations = Set.copyOf(continuations);
        unavailable = Set.copyOf(unavailable);
    }

    public static FamilyTreeView empty() {
        return new FamilyTreeView(Map.of(), Set.of(), Set.of());
    }

    public record Continuation(UUID anchor, Direction direction) {
    }

    public enum Direction {
        ANCESTORS,
        DESCENDANTS
    }
}
