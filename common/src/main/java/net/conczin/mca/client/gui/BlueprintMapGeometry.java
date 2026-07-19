package net.conczin.mca.client.gui;

import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Structure;
import net.conczin.mca.server.world.data.StructureFloor;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;

import java.util.*;

/** Builds immutable Blueprint geometry from persistent Structures, Floors and registered Rooms. */
final class BlueprintMapGeometry {
    private static final int ALL_FLOORS_KEY = Integer.MIN_VALUE;
    private static final int BUILDING_OUTLINE_WIDTH = 1;
    private static final float ROOM_ICON_MIN_SCALE = 0.90f;
    private static final float ROOM_ICON_MAX_SCALE = 1.35f;
    private static final float ROOM_ICON_AREA_REFERENCE = 6.0f;

    private final Village village;
    private final BlueprintFloorLayout floorLayout;
    private final Map<Integer, MapGeometry> cache = new HashMap<>();

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
        if (village == null) return MapGeometry.empty();
        int key = selectedFloor == null ? ALL_FLOORS_KEY : selectedFloor;
        return cache.computeIfAbsent(key, ignored -> {
            List<MapFootprintLayer> rooms = buildRoomLayers(selectedFloor);
            return new MapGeometry(rooms, buildStructureLayers(selectedFloor, rooms), buildIconLayers(rooms),
                    village.getExternalBuildings().filter(Building::isComplete)
                            .filter(building -> floorLayout.isBuildingVisible(building, selectedFloor))
                            .sorted(Comparator.comparingInt(Building::getId)).map(Building.class::cast).toList());
        });
    }

    private List<MapFootprintLayer> buildRoomLayers(Integer selectedFloor) {
        List<Building> rooms = village.getRooms()
                .sorted(Comparator.comparingInt(Building::getEffectiveStructureId).thenComparingInt(Building::getId))
                .toList();
        List<Integer> floors = selectedFloor == null ? allFloorsPriority() : List.of(selectedFloor);
        List<MapFootprintLayer> layers = new ArrayList<>();
        for (int floor : floors) {
            for (Building room : rooms) {
                if (!floorLayout.isBuildingVisible(room, floor)) continue;
                Set<BlueprintMapFootprint.Cell> cells = roomFootprint(room);
                if (cells.isEmpty()) continue;
                layers.add(new MapFootprintLayer(room, cells, BlueprintMapFootprint.rowSpans(cells),
                        BlueprintMapFootprint.outerEdges(cells), floor));
            }
        }
        return List.copyOf(layers);
    }

    private List<Integer> allFloorsPriority() {
        List<Integer> result = new ArrayList<>();
        if (floorLayout.ordinals().contains(0)) result.add(0);
        floorLayout.ordinals().stream().filter(ordinal -> ordinal != 0)
                .sorted(Comparator.comparingInt((Integer ordinal) -> Math.abs(ordinal)).thenComparingInt(Integer::intValue))
                .forEach(result::add);
        return List.copyOf(result);
    }

    private List<MapStructureLayer> buildStructureLayers(Integer selectedFloor,
                                                          List<MapFootprintLayer> roomLayers) {
        List<MapStructureLayer> layers = new ArrayList<>();
        for (Structure structure : village.getStructures().values().stream()
                .sorted(Comparator.comparingInt(Structure::getId)).toList()) {
            Building rootRoom = village.getBuilding(structure.getRootRoomId()).orElse(null);
            if (rootRoom == null) continue;

            LinkedHashSet<BlueprintMapFootprint.Cell> physicalCells = new LinkedHashSet<>();
            for (StructureFloor floor : structure.getFloors()) {
                OptionalInt ordinal = floorLayout.ordinalForFloor(structure.getId(), floor.id());
                if (selectedFloor != null && (ordinal.isEmpty() || ordinal.getAsInt() != selectedFloor)) {
                    continue;
                }
                physicalCells.addAll(BlueprintMapFootprint.fromFloorRegions(List.of(floor.region())));
            }
            if (physicalCells.isEmpty()) continue;

            LinkedHashSet<BlueprintMapFootprint.Cell> registeredRoomCells = new LinkedHashSet<>();
            for (MapFootprintLayer roomLayer : roomLayers) {
                if (roomLayer.building().getStructureId() == structure.getId()) {
                    registeredRoomCells.addAll(roomLayer.footprintCells());
                }
            }
            StructureShape shape = structureShape(physicalCells, registeredRoomCells);
            layers.add(new MapStructureLayer(
                    rootRoom,
                    shape.shadeCells(),
                    BlueprintMapFootprint.rowSpans(shape.shadeCells()),
                    BlueprintMapFootprint.outerEdges(shape.outlineCells()),
                    null));
        }
        return List.copyOf(layers);
    }

    static StructureShape structureShape(Set<BlueprintMapFootprint.Cell> physicalCells,
                                         Set<BlueprintMapFootprint.Cell> registeredRoomCells) {
        Set<BlueprintMapFootprint.Cell> outlineCells = BlueprintMapFootprint.expand(
                physicalCells, BUILDING_OUTLINE_WIDTH);
        LinkedHashSet<BlueprintMapFootprint.Cell> shadeCells = new LinkedHashSet<>(outlineCells);
        shadeCells.removeAll(registeredRoomCells);
        return new StructureShape(Set.copyOf(outlineCells), Set.copyOf(shadeCells));
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
            Center center = centerInside(cells);
            icons.add(new MapIconLayer(layers.getFirst().building(), layers.getFirst().floorOrdinal(),
                    center.x(), center.z(), iconScale(cells)));
        }
        return List.copyOf(icons);
    }

    private static Set<BlueprintMapFootprint.Cell> roomFootprint(Building building) {
        Set<BlueprintMapFootprint.Cell> cells = BlueprintMapFootprint.fromFloorRegions(building.getFloorRegions());
        if (!cells.isEmpty()) return cells;
        BlockPos min = building.getRawPos0();
        BlockPos max = building.getRawPos1();
        return BlueprintMapFootprint.rectangle(min.getX(), min.getZ(), max.getX(), max.getZ());
    }

    /** Always returns a point inside the actual visible footprint closest to its centroid. */
    private static Center centerInside(Set<BlueprintMapFootprint.Cell> cells) {
        double cx = cells.stream().mapToDouble(cell -> cell.x() + 0.5D).average().orElse(0.0D);
        double cz = cells.stream().mapToDouble(cell -> cell.z() + 0.5D).average().orElse(0.0D);
        BlueprintMapFootprint.Cell best = cells.stream().min(Comparator
                .comparingDouble((BlueprintMapFootprint.Cell cell) -> {
                    double dx = cell.x() + 0.5D - cx;
                    double dz = cell.z() + 0.5D - cz;
                    return dx * dx + dz * dz;
                }).thenComparingInt(BlueprintMapFootprint.Cell::x)
                .thenComparingInt(BlueprintMapFootprint.Cell::z)).orElse(new BlueprintMapFootprint.Cell(0, 0));
        return new Center(best.x() + 0.5D, best.z() + 0.5D);
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

        static MapGeometry empty() { return new MapGeometry(List.of(), List.of(), List.of(), List.of()); }
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
                             List<BlueprintMapFootprint.Edge> borderEdges, Integer floorOrdinal) {
        MapStructureLayer {
            shadeCells = Set.copyOf(shadeCells);
            shadeSpans = List.copyOf(shadeSpans);
            borderEdges = List.copyOf(borderEdges);
        }
    }

    record MapIconLayer(Building building, Integer floorOrdinal, double iconX, double iconZ, float iconScale) {
    }


    record StructureShape(Set<BlueprintMapFootprint.Cell> outlineCells,
                          Set<BlueprintMapFootprint.Cell> shadeCells) {
        StructureShape {
            outlineCells = Set.copyOf(outlineCells);
            shadeCells = Set.copyOf(shadeCells);
        }
    }

    private record Center(double x, double z) {
    }
}
