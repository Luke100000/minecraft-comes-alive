package net.conczin.mca.server.world.data;

import java.util.*;

/** Pure spatial grouping and storey renumbering service for Structures. */
public final class StructureGrouping {
    public static final int MAX_VERTICAL_GAP = 4;

    private StructureGrouping() {
    }

    /**
     * Attaches one Structure to every logical group it directly touches.
     *
     * <p>The lowest touched group ID is canonical. Only directly touched groups are merged;
     * unrelated Structures are not swept or rebuilt.</p>
     */
    public static int attachStructureGroup(Village village, Structure candidate) {
        if (candidate == null) return -1;
        if (village == null) {
            candidate.setStructureGroupId(candidate.getId());
            return candidate.getId();
        }

        SortedSet<Integer> touchedGroupIds = touchedGroupIds(village, candidate);
        int canonicalGroupId = touchedGroupIds.isEmpty() ? candidate.getId() : touchedGroupIds.first();

        candidate.setStructureGroupId(canonicalGroupId);
        if (!touchedGroupIds.isEmpty()) {
            village.getStructures().values().stream()
                    .filter(existing -> touchedGroupIds.contains(existing.getStructureGroupId()))
                    .forEach(existing -> existing.setStructureGroupId(canonicalGroupId));
        }
        return canonicalGroupId;
    }

    /** Returns the logical Building group a fresh physical Structure would attach to, without mutating it. */
    public static OptionalInt findAttachedGroupId(Village village, Structure candidate) {
        SortedSet<Integer> touched = touchedGroupIds(village, candidate);
        return touched.isEmpty() ? OptionalInt.empty() : OptionalInt.of(touched.first());
    }

    private static SortedSet<Integer> touchedGroupIds(Village village, Structure candidate) {
        if (village == null || candidate == null) return new TreeSet<>();
        return village.getStructures().values().stream()
                .filter(existing -> existing.getId() != candidate.getId())
                .filter(existing -> sharesGroupProximity(candidate, existing))
                .map(Structure::getStructureGroupId)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
    }

    public static boolean sharesGroupProximity(Structure a, Structure b) {
        if (a == null || b == null) return false;

        // Check horizontal X/Z overlap
        boolean xzOverlap = a.getPos1().getX() >= b.getPos0().getX() && a.getPos0().getX() <= b.getPos1().getX()
                && a.getPos1().getZ() >= b.getPos0().getZ() && a.getPos0().getZ() <= b.getPos1().getZ();
        if (!xzOverlap) return false;

        // Check vertical gap
        int verticalGap = Math.max(0, Math.max(a.getPos0().getY() - b.getPos1().getY(),
                b.getPos0().getY() - a.getPos1().getY()));
        return verticalGap <= MAX_VERTICAL_GAP;
    }

    /** Renumbers all StructureFloors belonging to the same structureGroupId. */
    public static void renumberStructureGroup(Village village, int structureGroupId) {
        if (village == null || structureGroupId < 0) return;

        List<Structure> groupStructures = village.getStructures().values().stream()
                .filter(s -> s.getStructureGroupId() == structureGroupId)
                .sorted(Comparator.comparingInt(Structure::getId))
                .toList();
        if (groupStructures.isEmpty()) return;

        List<StructureFloorRef> allFloors = new ArrayList<>();
        for (Structure s : groupStructures) {
            for (StructureFloor f : s.getFloors()) {
                allFloors.add(new StructureFloorRef(s, f));
            }
        }
        if (allFloors.isEmpty()) return;

        // Cluster physical floors into storey height bands within 2-block Y tolerance
        allFloors.sort(Comparator.comparingInt(ref -> ref.floor.anchorY()));
        List<List<StructureFloorRef>> storeyBands = new ArrayList<>();
        for (StructureFloorRef ref : allFloors) {
            List<StructureFloorRef> lastBand = storeyBands.isEmpty() ? null : storeyBands.getLast();
            if (lastBand == null || ref.floor.anchorY() - lastBand.getFirst().floor.anchorY() > BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE) {
                lastBand = new ArrayList<>();
                storeyBands.add(lastBand);
            }
            lastBand.add(ref);
        }

        // Determine Ground Band index: prefer ground-level floor (anchorY >= surfaceReferenceY - 1)
        int groundIndex = -1;
        int bestDistance = Integer.MAX_VALUE;
        for (int i = 0; i < storeyBands.size(); i++) {
            List<StructureFloorRef> band = storeyBands.get(i);
            int minSurfaceY = band.stream().mapToInt(ref -> ref.structure.getSurfaceReferenceY()).min().orElse(0);
            int anchorY = band.getFirst().floor.anchorY();
            int diff = Math.abs(anchorY - minSurfaceY);
            boolean betterGroundCandidate = diff < bestDistance
                    || (diff == bestDistance && i > groundIndex);
            if (anchorY >= minSurfaceY - 1 && betterGroundCandidate) {
                bestDistance = diff;
                groundIndex = i;
            }
        }

        // If all floors in the group are below ground surface level, set groundIndex above the highest basement floor
        if (groundIndex < 0) {
            List<StructureFloorRef> highestBand = storeyBands.getLast();
            int highestAnchorY = highestBand.getFirst().floor.anchorY();
            int minSurfaceY = highestBand.stream().mapToInt(ref -> ref.structure.getSurfaceReferenceY()).min().orElse(highestAnchorY);
            int subterraneanSteps = Math.max(1, (int) Math.round((minSurfaceY - highestAnchorY) / 4.0));
            groundIndex = (storeyBands.size() - 1) + subterraneanSteps;
        }

        // Assign floor numbers relative to Ground Band index
        for (int i = 0; i < storeyBands.size(); i++) {
            int floorNumber = i - groundIndex;
            for (StructureFloorRef ref : storeyBands.get(i)) {
                StructureFloor updated = ref.floor.withFloorNumber(floorNumber);
                // Update in structure's floor map cleanly via helper in Structure
                ref.structure.updateFloor(updated);
            }
        }
    }

    private record StructureFloorRef(Structure structure, StructureFloor floor) {
    }
}
