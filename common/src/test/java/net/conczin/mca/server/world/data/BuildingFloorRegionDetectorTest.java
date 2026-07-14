package net.conczin.mca.server.world.data;

import java.util.*;

public final class BuildingFloorRegionDetectorTest {
    public static void main(String[] args) {
        detectsOpenStaircaseFloors();
        suppressesSmallStairLanding();
        retainsMezzanine();
        keepsDisconnectedBalconiesOnOneFloor();
        suppressesSpiralStairTransitions();
        keepsTallSingleStoryHallOnOneFloor();
        clustersTwoBlockSplitLevelIntoOneSemanticBand();
        detectsOpenBasementAndGroundFloor();
        reportsRejectedTransitionSlices();
        preservesAtriumVoidInSpans();
        suppressesSmallLandingInSmallBuilding();
        retainsMeaningfulLongMezzanine();
        System.out.println("BuildingFloorRegionDetectorTest: PASS");
    }

    private static void detectsOpenStaircaseFloors() {
        assertAnchors("open staircase", cells(
                plane(0, 64, 0, 10, 10),
                lineX(3, 65, 4, 4),
                lineX(3, 66, 4, 4),
                lineX(3, 67, 4, 4),
                plane(0, 68, 0, 9, 9)
        ), 64, 68);
    }

    private static void suppressesSmallStairLanding() {
        assertAnchors("small stair landing", cells(
                plane(0, 64, 0, 9, 9),
                plane(3, 66, 3, 2, 3),
                plane(0, 69, 0, 9, 9)
        ), 64, 69);
    }

    private static void retainsMezzanine() {
        assertAnchors("mezzanine", cells(
                plane(0, 64, 0, 12, 10),
                plane(0, 68, 0, 6, 4)
        ), 64, 68);
    }

    private static void keepsDisconnectedBalconiesOnOneFloor() {
        List<BuildingFloorRegionDetector.DetectedRegion> regions = detect(cells(
                plane(0, 64, 0, 12, 10),
                plane(0, 68, 0, 4, 3),
                plane(8, 68, 0, 4, 3)
        ));

        assertAnchors("disconnected balconies", regions, 64, 68);
        BuildingFloorRegionDetector.DetectedRegion upper = regions.get(1);
        if (upper.components().size() != 2) {
            throw new AssertionError("disconnected balconies expected 2 upper components but got "
                    + upper.components().size());
        }
    }

    private static void suppressesSpiralStairTransitions() {
        assertAnchors("spiral staircase", cells(
                plane(0, 64, 0, 8, 8),
                plane(3, 65, 3, 2, 2),
                plane(4, 66, 3, 2, 2),
                plane(4, 67, 4, 2, 2),
                plane(0, 69, 0, 8, 8)
        ), 64, 69);
    }

    private static void keepsTallSingleStoryHallOnOneFloor() {
        assertAnchors("tall single-story hall", cells(
                plane(0, 64, 0, 14, 14)
        ), 64);
    }

    private static void clustersTwoBlockSplitLevelIntoOneSemanticBand() {
        assertAnchors("split level", cells(
                plane(0, 64, 0, 10, 10),
                plane(5, 66, 0, 5, 5)
        ), 64);
    }

    private static void detectsOpenBasementAndGroundFloor() {
        assertAnchors("open basement", cells(
                plane(0, 60, 0, 8, 8),
                plane(0, 64, 0, 10, 10)
        ), 60, 64);
    }


    private static void reportsRejectedTransitionSlices() {
        BuildingFloorRegionDetector.DetectionResult result = BuildingFloorRegionDetector.analyze(cells(
                plane(0, 64, 0, 10, 10),
                lineX(3, 65, 4, 4),
                lineX(3, 66, 4, 4),
                plane(0, 68, 0, 9, 9)
        ));

        BuildingFloorRegionDetector.SliceDecision transition = result.slices().stream()
                .filter(slice -> slice.y() == 65)
                .findFirst()
                .orElseThrow();
        if (transition.promoted()) {
            throw new AssertionError("stair transition Y=65 should not be promoted");
        }
        if (transition.reason() != BuildingFloorRegionDetector.SliceReason.BELOW_AREA_THRESHOLD) {
            throw new AssertionError("expected BELOW_AREA_THRESHOLD but got " + transition.reason());
        }
    }


    private static void preservesAtriumVoidInSpans() {
        List<BuildingFloorRegionDetector.DetectedRegion> regions = detect(cells(
                plane(0, 64, 0, 10, 10),
                ringPlane(0, 68, 0, 10, 10, 3, 3, 4, 4)
        ));
        assertAnchors("atrium ring", regions, 64, 68);

        BuildingFloorRegionDetector.DetectedComponent upper = regions.get(1).components().getFirst();
        boolean includesVoid = upper.spans().stream()
                .anyMatch(span -> span.z() == 4 && span.minX() <= 4 && span.maxX() >= 4);
        if (includesVoid) {
            throw new AssertionError("atrium void cell (4,4) must not be present in upper floor spans");
        }

        boolean includesBalcony = upper.spans().stream()
                .anyMatch(span -> span.z() == 4 && span.minX() <= 0 && span.maxX() >= 0);
        if (!includesBalcony) {
            throw new AssertionError("atrium balcony cell (0,4) should be preserved");
        }
    }


    private static void suppressesSmallLandingInSmallBuilding() {
        assertAnchors("small-building landing", cells(
                plane(0, 64, 0, 4, 4),
                plane(1, 67, 1, 2, 3)
        ), 64);
    }

    private static void retainsMeaningfulLongMezzanine() {
        assertAnchors("long narrow mezzanine", cells(
                plane(0, 64, 0, 8, 8),
                lineX(0, 68, 0, 8)
        ), 64, 68);
    }

    private static List<BuildingFloorRegionDetector.DetectedRegion> detect(
            Collection<BuildingFloorRegionDetector.SupportedCell> cells) {
        return BuildingFloorRegionDetector.detect(cells);
    }

    private static void assertAnchors(String name,
                                      Collection<BuildingFloorRegionDetector.SupportedCell> cells,
                                      int... expectedAnchors) {
        assertAnchors(name, detect(cells), expectedAnchors);
    }

    private static void assertAnchors(String name,
                                      List<BuildingFloorRegionDetector.DetectedRegion> regions,
                                      int... expectedAnchors) {
        List<Integer> actual = regions.stream()
                .map(BuildingFloorRegionDetector.DetectedRegion::anchorY)
                .toList();
        List<Integer> expected = java.util.Arrays.stream(expectedAnchors).boxed().toList();
        if (!actual.equals(expected)) {
            throw new AssertionError(name + " expected anchors " + expected + " but got " + actual);
        }
    }

    @SafeVarargs
    private static Set<BuildingFloorRegionDetector.SupportedCell> cells(
            Collection<BuildingFloorRegionDetector.SupportedCell>... groups) {
        Set<BuildingFloorRegionDetector.SupportedCell> cells = new LinkedHashSet<>();
        for (Collection<BuildingFloorRegionDetector.SupportedCell> group : groups) {
            cells.addAll(group);
        }
        return cells;
    }

    private static List<BuildingFloorRegionDetector.SupportedCell> plane(
            int startX, int y, int startZ, int width, int depth) {
        List<BuildingFloorRegionDetector.SupportedCell> cells = new ArrayList<>();
        for (int x = startX; x < startX + width; x++) {
            for (int z = startZ; z < startZ + depth; z++) {
                cells.add(new BuildingFloorRegionDetector.SupportedCell(x, y, z));
            }
        }
        return cells;
    }


    private static List<BuildingFloorRegionDetector.SupportedCell> ringPlane(
            int startX, int y, int startZ, int width, int depth,
            int holeX, int holeZ, int holeWidth, int holeDepth) {
        List<BuildingFloorRegionDetector.SupportedCell> cells = new ArrayList<>();
        for (int x = startX; x < startX + width; x++) {
            for (int z = startZ; z < startZ + depth; z++) {
                boolean insideHole = x >= holeX && x < holeX + holeWidth
                        && z >= holeZ && z < holeZ + holeDepth;
                if (!insideHole) {
                    cells.add(new BuildingFloorRegionDetector.SupportedCell(x, y, z));
                }
            }
        }
        return cells;
    }

    private static List<BuildingFloorRegionDetector.SupportedCell> lineX(
            int startX, int y, int z, int length) {
        List<BuildingFloorRegionDetector.SupportedCell> cells = new ArrayList<>();
        for (int x = startX; x < startX + length; x++) {
            cells.add(new BuildingFloorRegionDetector.SupportedCell(x, y, z));
        }
        return cells;
    }
}
