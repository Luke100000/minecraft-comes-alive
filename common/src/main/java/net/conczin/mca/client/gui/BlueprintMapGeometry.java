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
    private List<MapFootprintLayer> allRoomLayers;

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
            List<MapFootprintLayer> allRooms = allRoomLayers();
            List<MapFootprintLayer> visibleRooms = selectedFloor == null
                    ? allRooms
                    : allRooms.stream()
                    .filter(layer -> Objects.equals(layer.floorOrdinal(), selectedFloor))
                    .toList();
            Map<Integer, List<MapFootprintLayer>> visibleRoomsByBuilding = groupRoomLayers(visibleRooms);
            Map<Integer, List<MapFootprintLayer>> outlineRoomsByBuilding = groupRoomLayers(allRooms.stream()
                    .filter(layer -> layer.floorOrdinal() >= 0)
                    .toList());
            List<MapStructureLayer> structures = buildStructureLayers(
                    outlineRoomsByBuilding, visibleRoomsByBuilding);
            List<MapIconLayer> icons = buildIconLayers(visibleRoomsByBuilding, selectedFloor);
            List<MapConnectorLayer> connectors = buildConnectorLayers(selectedFloor);
            List<Building> grouped = village.getExternalBuildings().filter(Building::isComplete)
                    .filter(building -> selectedFloor == null || selectedFloor == 0)
                    .sorted(Comparator.comparingInt(Building::getId)).map(Building.class::cast).toList();
            return new MapGeometry(visibleRooms, structures, icons, connectors, grouped);
        });
    }

    private List<MapFootprintLayer> allRoomLayers() {
        if (allRoomLayers == null) allRoomLayers = buildRoomLayers(null);
        return allRoomLayers;
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

    static BuildingShape buildBuildingShape(
            Collection<? extends Collection<BlueprintMapFootprint.Cell>> roomFootprints) {
        LinkedHashSet<BlueprintMapFootprint.Cell> roomCells = new LinkedHashSet<>();
        roomFootprints.forEach(roomCells::addAll);
        if (roomCells.isEmpty()) {
            BlueprintMapFootprint.Shape empty = BlueprintMapFootprint.shape(Set.of());
            return new BuildingShape(empty, empty);
        }

        Set<BlueprintMapFootprint.Cell> outlineCells =
                BlueprintMapFootprint.expand(roomCells, BUILDING_OUTLINE_WIDTH);
        LinkedHashSet<BlueprintMapFootprint.Cell> shellCells = new LinkedHashSet<>(outlineCells);
        shellCells.removeAll(roomCells);
        return new BuildingShape(
                BlueprintMapFootprint.shape(outlineCells),
                BlueprintMapFootprint.shape(shellCells));
    }

    private List<MapStructureLayer> buildStructureLayers(
            Map<Integer, List<MapFootprintLayer>> outlineRoomsByBuilding,
            Map<Integer, List<MapFootprintLayer>> visibleRoomsByBuilding) {
        List<MapStructureLayer> layers = new ArrayList<>();
        for (Map.Entry<Integer, List<MapFootprintLayer>> entry : outlineRoomsByBuilding.entrySet()) {
            List<MapFootprintLayer> rooms = entry.getValue();
            BuildingShape shape = buildBuildingShape(
                    rooms.stream().map(MapFootprintLayer::footprintCells).toList());
            if (shape.outline().cells().isEmpty()) continue;

            LinkedHashSet<BlueprintMapFootprint.Cell> shellCells =
                    new LinkedHashSet<>(shape.outline().cells());
            visibleRoomsByBuilding.getOrDefault(entry.getKey(), List.of())
                    .forEach(layer -> shellCells.removeAll(layer.footprintCells()));
            BlueprintMapFootprint.Shape shell = BlueprintMapFootprint.shape(shellCells);

            MapFootprintLayer mainLayer = rooms.stream()
                    .filter(layer -> village.isMainRoom(layer.building()))
                    .findFirst()
                    .orElse(rooms.getFirst());
            int anchorY = rooms.stream().mapToInt(MapFootprintLayer::anchorY)
                    .min().orElse(mainLayer.anchorY());
            layers.add(new MapStructureLayer(
                    entry.getKey(),
                    mainLayer.building(),
                    anchorY,
                    shape.outline().cells(),
                    shell.cells(),
                    shell.spans(),
                    shape.outline().edges()));
        }
        layers.sort(Comparator.comparingInt(MapStructureLayer::anchorY)
                .thenComparingInt(MapStructureLayer::logicalBuildingId));
        return List.copyOf(layers);
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

    private List<MapConnectorLayer> buildConnectorLayers(Integer selectedFloor) {
        if (selectedFloor == null) return List.of();

        LinkedHashMap<ConnectorLayerKey, MapConnectorLayer> layers = new LinkedHashMap<>();
        village.getStructures().values().stream()
                .sorted(Comparator.comparingInt(Structure::getId))
                .forEach(structure -> {
                    int logicalBuildingId = village.getLogicalBuildingId(structure.getId());
                    for (StructureFloor floor : structure.getFloors()) {
                        if (floor.floorNumber() != selectedFloor) continue;
                        for (StructureFloor.ConnectorMarker marker : floor.connectors()) {
                            ConnectorLayerKey key = new ConnectorLayerKey(
                                    logicalBuildingId, marker.pos().getX(), marker.pos().getZ(), marker.type());
                            layers.putIfAbsent(key, new MapConnectorLayer(logicalBuildingId, marker));
                        }
                    }
                });
        return List.copyOf(layers.values());
    }

    private static Set<BlueprintMapFootprint.Cell> roomFootprint(Building building) {
        Set<BlueprintMapFootprint.Cell> cells = building.getFloorRegion()
                .map(BlueprintMapFootprint::fromFloorRegion)
                .orElseGet(Set::of);
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
                       List<MapConnectorLayer> connectorLayers,
                       List<Building> groupedBuildings) {
        static MapGeometry empty() {
            return new MapGeometry(List.of(), List.of(), List.of(), List.of(), List.of());
        }
    }

    record MapFootprintLayer(Building building,
                             BuildingType presentationType,
                             Set<BlueprintMapFootprint.Cell> footprintCells,
                             List<BlueprintMapFootprint.RowSpan> fillSpans,
                             List<BlueprintMapFootprint.Edge> outlineEdges,
                             Integer floorOrdinal,
                             int logicalBuildingId,
                             int anchorY) {
    }

    record MapStructureLayer(int logicalBuildingId, Building mainRoom, int anchorY,
                             Set<BlueprintMapFootprint.Cell> outlineCells,
                             Set<BlueprintMapFootprint.Cell> shellCells,
                             List<BlueprintMapFootprint.RowSpan> shellSpans,
                             List<BlueprintMapFootprint.Edge> borderEdges) {
    }

    record MapIconLayer(Building building, BuildingType presentationType, Integer floorOrdinal,
                        double iconX, double iconZ, float iconScale) {
    }

    record MapConnectorLayer(int logicalBuildingId,
                             StructureFloor.ConnectorMarker marker) {
    }

    record BuildingShape(BlueprintMapFootprint.Shape outline,
                         BlueprintMapFootprint.Shape shell) {
    }

    private record Center(double x, double z) {
    }

    private record ConnectorLayerKey(int logicalBuildingId, int x, int z,
                                     StructureFloor.ConnectorType type) {
    }
}
