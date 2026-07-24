package net.conczin.mca.server.world.data;

import java.util.Comparator;
import java.util.List;

/** Pure deterministic rules used by {@link StructureLayout}. */
final class StructureLayoutRules {
    private StructureLayoutRules() {
    }

    static int selectGroundIndex(List<GroundCandidate> candidates) {
        return candidates.stream().min(Comparator
                        .comparingInt(GroundCandidate::entranceCount).reversed()
                        .thenComparingInt(candidate -> Math.abs(candidate.anchorY() - candidate.referenceY()))
                        .thenComparing(Comparator.comparingInt(GroundCandidate::anchorY).reversed())
                        .thenComparingInt(GroundCandidate::structureId))
                .map(GroundCandidate::storeyIndex)
                .orElse(0);
    }

    static int nearestStoreyIndex(List<Integer> anchors, int y) {
        int best = -1;
        for (int i = 0; i < anchors.size(); i++) {
            if (best < 0
                    || Math.abs(anchors.get(i) - y) < Math.abs(anchors.get(best) - y)
                    || Math.abs(anchors.get(i) - y) == Math.abs(anchors.get(best) - y)
                    && anchors.get(i) < anchors.get(best)) {
                best = i;
            }
        }
        return best;
    }

    static int selectRootRoomId(List<RoomCandidate> rooms, int groundAnchorY) {
        return rooms.stream().min(Comparator
                        .comparingInt((RoomCandidate room) -> Math.abs(room.anchorY() - groundAnchorY))
                        .thenComparingInt(RoomCandidate::anchorY)
                        .thenComparingInt(RoomCandidate::roomId))
                .map(RoomCandidate::roomId)
                .orElse(-1);
    }

    record GroundCandidate(int storeyIndex,
                           int structureId,
                           int anchorY,
                           int referenceY,
                           int entranceCount) {
    }

    record RoomCandidate(int roomId, int anchorY) {
    }
}
