package net.conczin.mca.client.gui;

import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.RoomTypeResolver;
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
    private final RoomTypeResolver roomTypeResolver;
    private final Map<Integer, MapGeometry> cache = new HashMap<>();

    private BlueprintMapGeometry(Village village, RoomTypeResolver roomTypeResolver) {
        this.village = village;
        this.roomTypeResolver = roomTypeResolver;
    }

    static BlueprintMapGeometry empty() {
        return new BlueprintMapGeometry(null, null);
    }

    static BlueprintMapGeometry build(Village village, RoomTypeResolver roomTypeResolver) {
        return village == null ? empty() : new BlueprintMapGeometry(village, roomTypeResolver);
    }

    MapGeometry get(Integer selectedFloor) {
        if (village == null) return MapGeometry.empty();
        int key = selectedFloor == null ? ALL_FLOORS_KEY : selectedFloor;
        return cache.computeIfAbsent(key, ignored -> {
            List<MapFootprintLayer> rooms = buildRoomLayers(selectedFloor);
            Map<Integer, List<MapFootprintLayer>> roomsByBuilding = groupRoomLayers(rooms);
            List<MapStructureLayer> structures = buildStructureLayers(roomsByBuilding);
            List<MapIconLayer> icons = buildIconLayers(roomsByBuilding, selectedFloor);
            List<Building> grouped = village.getExternalBuildings().filter(Building::isComplete)
                    .filter(building -> BlueprintMapLayering.isOutdoorVisible(selectedFloor))
                    .sorted(Comparator.comparingInt(Building::getId)).map(Building.class::cast).toList();
            return new MapGeometry(rooms, structures, icons, grouped);
        });
    }

    private List<MapFootprintLayer> buildRoomLayers(Integer selectedFloor) {
        List<Building> rooms = village.getRooms()
                .sorted(Comparator.comparingInt((Building room) ->
                                village.getLogicalBuildingId(room.getStructureId()))
                        .thenComparingInt(Building::getId))
                .toList();
        List<MapFootprintLayer> layers = new ArrayList<>();
        for (Building room : rooms) {
            int floorNum = room.getFloorNumber(village);
            if (selectedFloor != null && floorNum != selectedFloor) continue;
            Set<BlueprintMapFootprint.Cell> footprintCells = roomFootprint(room);
            if (footprintCells.isEmpty()) continue;
            BlueprintMapFootprint.Shape shape = BlueprintMapFootprint.shape(footprintCells);
            int anchorY = village.getStructure(room.getStructureId())
                    .flatMap(structure -> structure.getFloor(room.getFloorId()))
                    .map(StructureFloor::anchorY)
                    .orElse(room.getSourceBlock().getY());

            layers.add(new MapFootprintLayer(
                    room,
                    presentationType(room),
                    shape.cells(),
                    shape.spans(),
                    shape.edges(),
                    floorNum,
                    village.getLogicalBuildingId(room.getStructureId()),
                    anchorY));
        }
        layers.sort(Comparator.comparingInt(MapFootprintLayer::anchorY)
                .thenComparingInt(MapFootprintLayer::logicalBuildingId)
                .thenComparingInt(layer -> layer.building().getId()));
        return List.copyOf(layers);
    }

    private BuildingType presentationType(Building room) {
        BuildingType resolved = roomTypeResolver == null ? null : roomTypeResolver.presentationType(room);
        return resolved == null ? room.getBuildingType() : resolved;
    }

    private List<MapStructureLayer> buildStructureLayers(
            Map<Integer, List<MapFootprintLayer>> roomsByBuilding) {
        Map<Integer, List<Structure>> byBuilding = village.getStructures().values().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        structure -> village.getLogicalBuildingId(structure.getId()),
                        TreeMap::new, java.util.stream.Collectors.toList()));
        List<MapStructureLayer> layers = new ArrayList<>();
        for (Map.Entry<Integer, List<Structure>> entry : byBuilding.entrySet()) {
            List<Structure> members = entry.getValue().stream()
                    .sorted(Comparator.comparingInt(Structure::getId))
                    .toList();
            LinkedHashSet<BlueprintMapFootprint.Cell> outlineBaseCells = new LinkedHashSet<>();
            int groundAnchorY = Integer.MAX_VALUE;
            int lowestNonBasementY = Integer.MAX_VALUE;
            for (Structure structure : members) {
                for (StructureFloor floor : structure.getFloors()) {
                    if (floor.floorNumber() >= 0 && floor.region() != null) {
                        outlineBaseCells.addAll(BlueprintMapFootprint.fromFloorRegions(List.of(floor.region())));
                        lowestNonBasementY = Math.min(lowestNonBasementY, floor.anchorY());
                        if (floor.floorNumber() == 0) {
                            groundAnchorY = Math.min(groundAnchorY, floor.anchorY());
                        }
                    }
                }
            }
            if (outlineBaseCells.isEmpty()) continue;
            int anchorY = groundAnchorY != Integer.MAX_VALUE ? groundAnchorY : lowestNonBasementY;

            // Keep the intentional one-cell neutral shell around the canonical physical
            // building footprint. Room geometry remains exact; subtracting visible rooms
            // leaves the padded shade while both paths still use the same Shape conversion.
            Set<BlueprintMapFootprint.Cell> filteredBase =
                    outlineBaseWithoutEntranceProtrusions(outlineBaseCells);
            if (filteredBase.isEmpty()) filteredBase = Set.copyOf(outlineBaseCells);
            Set<BlueprintMapFootprint.Cell> outlineCells = BlueprintMapFootprint.expand(
                    filteredBase, BUILDING_OUTLINE_WIDTH);

            LinkedHashSet<BlueprintMapFootprint.Cell> visibleRoomCells = new LinkedHashSet<>();
            roomsByBuilding.getOrDefault(entry.getKey(), List.of())
                    .forEach(layer -> visibleRoomCells.addAll(layer.footprintCells()));

            LinkedHashSet<BlueprintMapFootprint.Cell> shellCells = new LinkedHashSet<>(outlineCells);
            shellCells.removeAll(visibleRoomCells);
            BlueprintMapFootprint.Shape shellShape = BlueprintMapFootprint.shape(shellCells);
            BlueprintMapFootprint.Shape outlineShape = BlueprintMapFootprint.shape(outlineCells);

            Structure root = village.getStructure(entry.getKey()).orElse(members.getFirst());
            layers.add(new MapStructureLayer(
                    entry.getKey(),
                    village.getMainRoom(root).orElse(null),
                    anchorY,
                    outlineShape.cells(),
                    shellShape.cells(),
                    shellShape.spans(),
                    outlineShape.edges()));
        }
        layers.sort(Comparator.comparingInt(MapStructureLayer::anchorY)
                .thenComparingInt(MapStructureLayer::logicalBuildingId));
        return List.copyOf(layers);
    }

    private static Set<BlueprintMapFootprint.Cell> outlineBaseWithoutEntranceProtrusions(
            Set<BlueprintMapFootprint.Cell> physicalCells) {
        return physicalCells.stream()
                .filter(cell -> cardinalNeighborCount(physicalCells, cell) != 1)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static int cardinalNeighborCount(Set<BlueprintMapFootprint.Cell> cells,
                                             BlueprintMapFootprint.Cell cell) {
        int count = 0;
        if (cells.contains(new BlueprintMapFootprint.Cell(cell.x() + 1, cell.z()))) count++;
        if (cells.contains(new BlueprintMapFootprint.Cell(cell.x() - 1, cell.z()))) count++;
        if (cells.contains(new BlueprintMapFootprint.Cell(cell.x(), cell.z() + 1))) count++;
        if (cells.contains(new BlueprintMapFootprint.Cell(cell.x(), cell.z() - 1))) count++;
        return count;
    }

    private static Map<Integer, List<MapFootprintLayer>> groupRoomLayers(
            List<MapFootprintLayer> roomLayers) {
        Map<Integer, List<MapFootprintLayer>> byBuilding = new LinkedHashMap<>();
        for (MapFootprintLayer layer : roomLayers) {
            byBuilding.computeIfAbsent(layer.logicalBuildingId(), ignored -> new ArrayList<>()).add(layer);
        }
        return byBuilding;
    }

    private List<MapIconLayer> buildIconLayers(
            Map<Integer, List<MapFootprintLayer>> roomsByBuilding,
            Integer selectedFloor) {
        List<MapIconLayer> icons = new ArrayList<>();
        for (List<MapFootprintLayer> structureLayers : roomsByBuilding.values()) {
            MapFootprintLayer mainLayer = structureLayers.stream()
                    .filter(layer -> village.isMainRoom(layer.building()))
                    .findFirst()
                    .orElse(structureLayers.getFirst());
            if (!mainLayer.presentationType().visible() || !mainLayer.presentationType().hasIcon()) continue;

            LinkedHashSet<BlueprintMapFootprint.Cell> buildingCells = new LinkedHashSet<>();
            structureLayers.forEach(layer -> buildingCells.addAll(layer.footprintCells()));
            if (buildingCells.isEmpty()) continue;

            Center center = centerInside(buildingCells);
            icons.add(new MapIconLayer(
                    mainLayer.building(),
                    mainLayer.presentationType(),
                    selectedFloor,
                    center.x(),
                    center.z(),
                    iconScale(buildingCells)));
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

    record MapFootprintLayer(Building building,
                             BuildingType presentationType,
                             Set<BlueprintMapFootprint.Cell> footprintCells,
                             List<BlueprintMapFootprint.RowSpan> fillSpans,
                             List<BlueprintMapFootprint.Edge> outlineEdges,
                             Integer floorOrdinal,
                             int logicalBuildingId,
                             int anchorY) {
        MapFootprintLayer {
            footprintCells = Set.copyOf(footprintCells);
            fillSpans = List.copyOf(fillSpans);
            outlineEdges = List.copyOf(outlineEdges);
        }
    }

    record MapStructureLayer(int logicalBuildingId, Building mainRoom, int anchorY,
                             Set<BlueprintMapFootprint.Cell> outlineCells,
                             Set<BlueprintMapFootprint.Cell> shellCells,
                             List<BlueprintMapFootprint.RowSpan> shellSpans,
                             List<BlueprintMapFootprint.Edge> borderEdges) {
        MapStructureLayer {
            outlineCells = Set.copyOf(outlineCells);
            shellCells = Set.copyOf(shellCells);
            shellSpans = List.copyOf(shellSpans);
            borderEdges = List.copyOf(borderEdges);
        }
    }

    record MapIconLayer(Building building, BuildingType presentationType, Integer floorOrdinal,
                        double iconX, double iconZ, float iconScale) {
    }

    private record Center(double x, double z) {
    }
}
