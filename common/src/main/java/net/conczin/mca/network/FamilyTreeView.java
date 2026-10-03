package net.conczin.mca.network;

import io.netty.buffer.ByteBuf;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record FamilyTreeView(
        Map<UUID, FamilyTreeNode> nodes,
        Set<Continuation> continuations,
        Set<UUID> unavailable,
        Set<UUID> orphans,
        Map<UUID, GlobalPos> graves
) {
    public static final int MAX_NODES = 256;
    public static final int MAX_CONTINUATIONS = MAX_NODES * 2;
    public static final int MAX_UNAVAILABLE = MAX_NODES * 3;

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
            FamilyTreeNode.STREAM_CODEC,
            MAX_NODES
    );
    private static final StreamCodec<FriendlyByteBuf, Set<Continuation>> CONTINUATIONS_CODEC = ByteBufCodecs.collection(
            HashSet::new,
            CONTINUATION_CODEC,
            MAX_CONTINUATIONS
    );
    private static final StreamCodec<FriendlyByteBuf, Set<UUID>> UUID_SET_CODEC = ByteBufCodecs.collection(
            HashSet::new,
            UUIDUtil.STREAM_CODEC,
            MAX_UNAVAILABLE
    );
    private static final StreamCodec<FriendlyByteBuf, Set<UUID>> ORPHAN_SET_CODEC = ByteBufCodecs.collection(
            HashSet::new,
            UUIDUtil.STREAM_CODEC,
            MAX_NODES
    );
    private static final StreamCodec<FriendlyByteBuf, Map<UUID, GlobalPos>> GRAVES_CODEC = ByteBufCodecs.map(
            HashMap::new,
            UUIDUtil.STREAM_CODEC,
            GlobalPos.STREAM_CODEC,
            MAX_NODES
    );

    public static final StreamCodec<FriendlyByteBuf, FamilyTreeView> STREAM_CODEC = StreamCodec.composite(
            NODES_CODEC, FamilyTreeView::nodes,
            CONTINUATIONS_CODEC, FamilyTreeView::continuations,
            UUID_SET_CODEC, FamilyTreeView::unavailable,
            ORPHAN_SET_CODEC, FamilyTreeView::orphans,
            GRAVES_CODEC, FamilyTreeView::graves,
            FamilyTreeView::new
    );

    public FamilyTreeView {
        if (nodes.size() > MAX_NODES) {
            throw new IllegalArgumentException("Family tree view exceeds node budget: " + nodes.size());
        }
        nodes.values().forEach(node -> {
            if (node.children().size() > MAX_NODES) {
                throw new IllegalArgumentException(
                        "Family tree node exceeds relationship budget: " + node.children().size()
                );
            }
        });
        if (continuations.size() > MAX_CONTINUATIONS) {
            throw new IllegalArgumentException("Family tree view exceeds continuation budget: " + continuations.size());
        }
        if (unavailable.size() > MAX_UNAVAILABLE) {
            throw new IllegalArgumentException("Family tree view exceeds unavailable-record budget: " + unavailable.size());
        }
        if (orphans.size() > MAX_NODES) {
            throw new IllegalArgumentException("Family tree view exceeds orphan-status budget: " + orphans.size());
        }
        if (!nodes.keySet().containsAll(orphans)) {
            throw new IllegalArgumentException("Family tree view contains orphan status for node outside the bounded view");
        }
        if (graves.size() > MAX_NODES) {
            throw new IllegalArgumentException("Family tree view exceeds grave budget: " + graves.size());
        }
        if (!nodes.keySet().containsAll(graves.keySet())) {
            throw new IllegalArgumentException("Family tree view contains grave for node outside the bounded view");
        }
        Map<UUID, FamilyTreeNode> ownedNodes = new LinkedHashMap<>(nodes.size());
        nodes.forEach((id, node) -> ownedNodes.put(id, new FamilyTreeNode(null, node.save())));
        nodes = Map.copyOf(ownedNodes);
        continuations = Set.copyOf(continuations);
        unavailable = Set.copyOf(unavailable);
        orphans = Set.copyOf(orphans);
        graves = Map.copyOf(graves);
    }

    public static FamilyTreeView empty() {
        return new FamilyTreeView(Map.of(), Set.of(), Set.of(), Set.of(), Map.of());
    }

    public record Continuation(UUID anchor, Direction direction) {
    }

    public enum Direction {
        ANCESTORS,
        DESCENDANTS
    }
}
