package net.conczin.mca.client.gui;

import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;

import java.util.*;

/**
 * Builds and caches the immutable geometry consumed by the Blueprint map renderer.
 * Floor identity remains owned by {@link BlueprintFloorLayout}; this class only turns
 * registered room footprints into fill, outline, structure-shade and icon layers.
 */
final class BlueprintMapGeometry {
    private static final int ALL_FLOORS_KEY = Integer.MIN_VALUE;
    private static final int BUILDING_OUTLINE_WIDTH = 1;
    private static final float ROOM_ICON_MIN_SCALE = 0.90f;
    private static final float ROOM_ICON_MAX_SCALE = 1.35f;
    private static final float ROOM_ICON_AREA_REFERENCE = 6.0f;

    private final Village village;
    private final BlueprintFloorLayout floorLayout;
    private final Map<Integer, MapGeometry> cache = new HashMap<>();
    private List<MapStructureLayer> structureLayers;

    private BlueprintMapGeometry(Village village, BlueprintFloorLayout floorLayout) {
        this.village = village;
        this.floorLayout = floorLayout;
    }

    static BlueprintMapGeometry empty() {
        return new BlueprintMapGeometry(null, BlueprintFloorLayout.empty());
    }

    static BlueprintMapGeometry build(Village village, BlueprintFloorLayout floorLayout) {
        return village == null ? empty() : new BlueprintMapGeometry(village, floorLayout);
    }

    MapGeometry get(Integer selectedFloor) {
        if (village == null) {
            return MapGeometry.empty();
        }
        int key = selectedFloor == null ? ALL_FLOORS_KEY : selectedFloor;
        return cache.computeIfAbsent(key, ignored -> {
            List<MapFootprintLayer> rooms = buildRoomLayers(selectedFloor);
            List<MapIconLayer> icons = buildIconLayers(rooms);
            List<Building> grouped = village.getBuildings().values().stream()
                    .filter(Building::isComplete)
                    .filter(building -> building.getBuildingType().grouped())
                    .filter(building -> floorLayout.isBuildingVisible(building, selectedFloor))
                    .sorted(Comparator.comparingInt(Building::getId))
                    .toList();
            return new MapGeometry(rooms, getStructureLayers(), icons, grouped);
        });
    }

    private List<MapFootprintLayer> buildRoomLayers(Integer selectedFloor) {
        List<Building> rooms = village.getBuildings().values().stream()
                .filter(Building::isComplete)
                .filter(Building::isFunctionalRoom)
                .sorted(Comparator.comparingInt(Building::getEffectiveStructureId)
                        .thenComparingInt(Building::getId))
                .toList();

        List<Integer> floors = selectedFloor == null ? allFloorsPriority() : List.of(selectedFloor);
        List<MapFootprintLayer> layers = new ArrayList<>();
        for (int floor : floors) {
            for (Building room : rooms) {
                if (!floorLayout.isBuildingVisible(room, floor)) {
                    continue;
                }
                Set<BlueprintMapFootprint.Cell> cells = roomFootprint(room);
                if (!cells.isEmpty()) {
                    layers.add(new MapFootprintLayer(
                            room,
                            cells,
                            BlueprintMapFootprint.rowSpans(cells),
                            BlueprintMapFootprint.outerEdges(cells),
                            floor));
                }
            }
        }
        return List.copyOf(layers);
    }

    private List<Integer> allFloorsPriority() {
        List<Integer> floors = new ArrayList<>();
        if (floorLayout.ordinals().contains(0)) {
            floors.add(0);
        }
        floorLayout.ordinals().stream()
                .filter(ordinal -> ordinal != 0)
                .sorted(Comparator.comparingInt((Integer ordinal) -> Math.abs(ordinal))
                        .thenComparingInt(Integer::intValue))
                .forEach(floors::add);
        return List.copyOf(floors);
    }

    private List<MapStructureLayer> getStructureLayers() {
        if (structureLayers == null) {
            structureLayers = buildStructureLayers(buildRoomLayers(0));
        }
        return structureLayers;
    }

    private List<MapStructureLayer> buildStructureLayers(List<MapFootprintLayer> groundRooms) {
        Map<Integer, LinkedHashSet<BlueprintMapFootprint.Cell>> cellsByStructure = new HashMap<>();
        for (MapFootprintLayer room : groundRooms) {
            cellsByStructure
                    .computeIfAbsent(room.building().getEffectiveStructureId(), ignored -> new LinkedHashSet<>())
                    .addAll(room.footprintCells());
        }

        List<MapStructureLayer> layers = new ArrayList<>();
        village.getBuildings().values().stream()
                .filter(Building::isComplete)
                .filter(Building::isStructureRoot)
                .sorted(Comparator.comparingInt(Building::getId))
                .forEach(root -> {
                    Set<BlueprintMapFootprint.Cell> roomCells = cellsByStructure.get(root.getEffectiveStructureId());
                    if (roomCells == null || roomCells.isEmpty()) {
                        return;
                    }
                    Set<BlueprintMapFootprint.Cell> buildingCells =
                            BlueprintMapFootprint.expand(roomCells, BUILDING_OUTLINE_WIDTH);
                    LinkedHashSet<BlueprintMapFootprint.Cell> shade = new LinkedHashSet<>(buildingCells);
                    shade.removeAll(roomCells);
                    layers.add(new MapStructureLayer(
                            root,
                            shade,
                            BlueprintMapFootprint.rowSpans(shade),
                            BlueprintMapFootprint.outerEdges(buildingCells)));
                });
        return List.copyOf(layers);
    }

    private static List<MapIconLayer> buildIconLayers(List<MapFootprintLayer> roomLayers) {
        TreeMap<Integer, List<MapFootprintLayer>> byRoom = new TreeMap<>();
        for (MapFootprintLayer layer : roomLayers) {
            if (layer.building().getBuildingType().visible() && layer.building().getBuildingType().hasIcon()) {
                byRoom.computeIfAbsent(layer.building().getId(), ignored -> new ArrayList<>()).add(layer);
            }
        }

        List<MapIconLayer> icons = new ArrayList<>();
        for (List<MapFootprintLayer> layers : byRoom.values()) {
            LinkedHashSet<BlueprintMapFootprint.Cell> cells = new LinkedHashSet<>();
            layers.forEach(layer -> cells.addAll(layer.footprintCells()));
            Center center = center(cells);
            icons.add(new MapIconLayer(
                    layers.getFirst().building(),
                    layers.getFirst().floorOrdinal(),
                    center.x(),
                    center.z(),
                    iconScale(cells)));
        }
        return List.copyOf(icons);
    }

    private static Set<BlueprintMapFootprint.Cell> roomFootprint(Building building) {
        Set<BlueprintMapFootprint.Cell> cells = BlueprintMapFootprint.fromFloorRegions(building.getFloorRegions());
        if (!cells.isEmpty()) {
            return cells;
        }
        BlockPos min = building.getRawPos0();
        BlockPos max = building.getRawPos1();
        return BlueprintMapFootprint.rectangle(min.getX(), min.getZ(), max.getX(), max.getZ());
    }

    private static Center center(Set<BlueprintMapFootprint.Cell> cells) {
        IntSummaryStatistics x = cells.stream().mapToInt(BlueprintMapFootprint.Cell::x).summaryStatistics();
        IntSummaryStatistics z = cells.stream().mapToInt(BlueprintMapFootprint.Cell::z).summaryStatistics();
        return new Center((x.getMin() + x.getMax() + 1) / 2.0D, (z.getMin() + z.getMax() + 1) / 2.0D);
    }

    private static float iconScale(Set<BlueprintMapFootprint.Cell> cells) {
        float scale = (float) Math.sqrt(Math.max(1, cells.size())) / ROOM_ICON_AREA_REFERENCE;
        return Math.max(ROOM_ICON_MIN_SCALE, Math.min(ROOM_ICON_MAX_SCALE, scale));
    }

    record MapGeometry(List<MapFootprintLayer> footprintLayers,
                       List<MapStructureLayer> structureLayers,
                       List<MapIconLayer> iconLayers,
                       List<Building> groupedBuildings) {
        MapGeometry {
            footprintLayers = List.copyOf(footprintLayers);
            structureLayers = List.copyOf(structureLayers);
            iconLayers = List.copyOf(iconLayers);
            groupedBuildings = List.copyOf(groupedBuildings);
        }

        static MapGeometry empty() {
            return new MapGeometry(List.of(), List.of(), List.of(), List.of());
        }
    }

    record MapFootprintLayer(Building building, Set<BlueprintMapFootprint.Cell> footprintCells,
                             List<BlueprintMapFootprint.RowSpan> fillSpans,
                             List<BlueprintMapFootprint.Edge> outlineEdges, Integer floorOrdinal) {
        MapFootprintLayer {
            footprintCells = Set.copyOf(footprintCells);
            fillSpans = List.copyOf(fillSpans);
            outlineEdges = List.copyOf(outlineEdges);
        }
    }

    record MapStructureLayer(Building root, Set<BlueprintMapFootprint.Cell> shadeCells,
                             List<BlueprintMapFootprint.RowSpan> shadeSpans,
                             List<BlueprintMapFootprint.Edge> borderEdges) {
        MapStructureLayer {
            shadeCells = Set.copyOf(shadeCells);
            shadeSpans = List.copyOf(shadeSpans);
            borderEdges = List.copyOf(borderEdges);
        }
    }

    record MapIconLayer(Building building, Integer floorOrdinal,
                        double iconX, double iconZ, float iconScale) {
    }

    private record Center(double x, double z) {
    }
}
