package net.conczin.mca.server.world.data;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

/** Pure bounded storey grouping; stored labels are inputs, never rewritten here. */
final class FloorGrouping {
    static final int MAX_HEIGHT_SPAN = 2;

    private FloorGrouping() {
    }

    record Band(int minY, int maxY) {
        boolean contains(int height) {
            return height >= minY && height <= maxY;
        }
    }

    record NumberDecision(Building.validationResult result, OptionalInt number) {
        NumberDecision {
            if ((result == Building.validationResult.SUCCESS) != number.isPresent()) {
                throw new IllegalArgumentException("A successful floor number decision needs a number");
            }
        }
    }

    static NumberDecision prospectiveNumber(Collection<StructureFloor> registered,
                                            StructureFloor ground, int candidateY) {
        return prospectiveNumber(registered, ground, candidateY, null);
    }

    static NumberDecision prospectiveNumber(Collection<StructureFloor> registered,
                                            StructureFloor ground, StructureFloor candidate) {
        return prospectiveNumber(registered, ground, candidate.anchorY(), candidate);
    }

    private static NumberDecision prospectiveNumber(Collection<StructureFloor> registered,
                                                    StructureFloor ground, int candidateY,
                                                    StructureFloor candidateFloor) {
        if (ground == null || !registered.contains(ground)) {
            return new NumberDecision(Building.validationResult.NOT_IN_BUILDING, OptionalInt.empty());
        }
        if (ground.floorNumber() != 0) return ambiguous();
        // Saved labels define existing groups. A new candidate must not repartition those
        // groups by changing where a sorted height-band sweep starts.
        Map<Integer, Band> groups = new HashMap<>();
        for (StructureFloor floor : registered) {
            groups.merge(floor.floorNumber(), new Band(floor.anchorY(), floor.anchorY()),
                    (first, second) -> new Band(Math.min(first.minY(), second.minY()),
                            Math.max(first.maxY(), second.maxY())));
        }
        List<Integer> nearbyLabels = registered.stream()
                .filter(floor -> Math.abs((long) floor.anchorY() - candidateY) <= MAX_HEIGHT_SPAN)
                .filter(floor -> candidateFloor == null || floor.anchorY() == candidateY
                        || !floor.overlapsFootprint(candidateFloor))
                .map(StructureFloor::floorNumber).distinct().toList();
        List<Integer> labels = nearbyLabels.stream().filter(number -> {
            Band group = groups.get(number);
            return (long) Math.max(group.maxY(), candidateY) - Math.min(group.minY(), candidateY)
                    <= MAX_HEIGHT_SPAN;
        }).toList();
        for (int number : nearbyLabels) {
            if (number == Integer.MIN_VALUE || groups.get(number).contains(candidateY) && !labels.contains(number)) {
                return ambiguous();
            }
        }
        if (labels.size() > 1) return ambiguous();
        if (labels.size() == 1) {
            int number = labels.getFirst();
            return success(number);
        }

        OptionalInt lowerY = registered.stream().mapToInt(StructureFloor::anchorY)
                .filter(height -> height < candidateY).max();
        OptionalInt upperY = registered.stream().mapToInt(StructureFloor::anchorY)
                .filter(height -> height > candidateY).min();
        List<Integer> lowerLabels = lowerY.isEmpty() ? List.of() : registered.stream()
                .filter(floor -> floor.anchorY() == lowerY.getAsInt()).map(StructureFloor::floorNumber).distinct().toList();
        List<Integer> upperLabels = upperY.isEmpty() ? List.of() : registered.stream()
                .filter(floor -> floor.anchorY() == upperY.getAsInt()).map(StructureFloor::floorNumber).distinct().toList();
        if (lowerLabels.size() > 1 || upperLabels.size() > 1) return ambiguous();
        Integer below = lowerLabels.isEmpty() ? null : lowerLabels.getFirst();
        Integer above = upperLabels.isEmpty() ? null : upperLabels.getFirst();
        long proposed = candidateY > ground.anchorY()
                ? below == null ? Long.MAX_VALUE : (long) below + 1
                : above == null ? Long.MIN_VALUE : (long) above - 1;
        if (proposed <= Integer.MIN_VALUE || proposed > Integer.MAX_VALUE
                || below != null && proposed <= below || above != null && proposed >= above
                || candidateY > ground.anchorY() && proposed <= 0
                || candidateY < ground.anchorY() && proposed >= 0) return ambiguous();
        for (StructureFloor floor : registered) {
            if (floor.floorNumber() == proposed) return ambiguous();
        }
        return success((int) proposed);
    }

    private static NumberDecision success(int number) {
        return new NumberDecision(Building.validationResult.SUCCESS, OptionalInt.of(number));
    }

    private static NumberDecision ambiguous() {
        return new NumberDecision(Building.validationResult.AMBIGUOUS_STRUCTURE, OptionalInt.empty());
    }
}
