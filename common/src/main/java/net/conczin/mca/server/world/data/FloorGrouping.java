package net.conczin.mca.server.world.data;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

    static List<Band> bands(Collection<Integer> heights) {
        List<Band> bands = new ArrayList<>();
        for (int height : heights.stream().distinct().sorted().toList()) {
            if (bands.isEmpty() || (long) height - bands.getLast().minY() > MAX_HEIGHT_SPAN) {
                bands.add(new Band(height, height));
            } else {
                Band last = bands.getLast();
                bands.set(bands.size() - 1, new Band(last.minY(), height));
            }
        }
        return List.copyOf(bands);
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
        List<Integer> heights = new ArrayList<>(registered.stream().map(StructureFloor::anchorY).toList());
        heights.add(candidateY);
        List<Band> bands = bands(heights);
        Band candidate = bands.stream().filter(band -> band.contains(candidateY)).findFirst().orElseThrow();
        List<Integer> labels = registered.stream().filter(floor -> candidate.contains(floor.anchorY()))
                .filter(floor -> candidateFloor == null || floor.anchorY() == candidateY
                        || !floor.overlapsFootprint(candidateFloor))
                .map(StructureFloor::floorNumber).distinct().toList();
        if (labels.size() > 1) return ambiguous();
        if (labels.size() == 1) {
            int number = labels.getFirst();
            if (number == Integer.MIN_VALUE) return ambiguous();
            int minY = candidateY;
            int maxY = candidateY;
            for (StructureFloor floor : registered) {
                if (floor.floorNumber() != number) continue;
                minY = Math.min(minY, floor.anchorY());
                maxY = Math.max(maxY, floor.anchorY());
            }
            // Reusing a label must not extend its saved group through pairwise chaining.
            return (long) maxY - minY <= MAX_HEIGHT_SPAN ? success(number) : ambiguous();
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
