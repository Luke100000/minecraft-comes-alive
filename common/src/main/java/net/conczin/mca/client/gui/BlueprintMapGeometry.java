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
    private static final double COLLISION_RADIUS_PIXELS = 10.0D;
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
            List<MapStructureLayer> structures = buildStructureLayers(selectedFloor, rooms);
            List<MapIconLayer> icons = buildIconLayers(rooms, selectedFloor);
            List<Building> grouped = village.getExternalBuildings().filter(Building::isComplete)
                    .filter(building -> BlueprintMapLayering.isOutdoorVisible(selectedFloor))
                    .sorted(Comparator.comparingInt(Building::getId)).map(Building.class::cast).toList();
            return new MapGeometry(rooms, structures, icons, grouped);
        });
    }

    private List<MapFootprintLayer> buildRoomLayers(Integer selectedFloor) {
        List<Building> rooms = village.getRooms()
                .sorted(Comparator.comparingInt(Building::getEffectiveStructureId).thenComparingInt(Building::getId))
                .toList();
        List<MapFootprintLayer> layers = new ArrayList<>();
        for (Building room : rooms) {
            int floorNum = room.getFloorNumber(village);
            if (selectedFloor != null && floorNum != selectedFloor) continue;
            Set<BlueprintMapFootprint.Cell> footprintCells = roomFootprint(room);
            if (footprintCells.isEmpty()) continue;

            layers.add(new MapFootprintLayer(
                    room,
                    presentationType(room),
                    footprintCells,
                    BlueprintMapFootprint.rowSpans(footprintCells),
                    BlueprintMapFootprint.outerEdges(footprintCells),
                    floorNum));
        }
        return List.copyOf(layers);
    }

    private BuildingType presentationType(Building room) {
        BuildingType resolved = roomTypeResolver == null ? null : roomTypeResolver.presentationType(room);
        return resolved == null ? room.getBuildingType() : resolved;
    }

    private List<MapStructureLayer> buildStructureLayers(Integer selectedFloor,
                                                         List<MapFootprintLayer> roomLayers) {
        List<MapStructureLayer> layers = new ArrayList<>();
        Map<Integer, List<Structure>> groups = village.getStructures().values().stream()
                .collect(java.util.stream.Collectors.groupingBy(Structure::getStructureGroupId));

        for (List<Structure> group : groups.values()) {
            Structure first = group.getFirst();
            Building mainRoom = village.getMainRoom(first).orElse(null);
            Set<Integer> groupStructureIds = group.stream().map(Structure::getId).collect(java.util.stream.Collectors.toSet());

            LinkedHashSet<BlueprintMapFootprint.Cell> outlineBaseCells = new LinkedHashSet<>();
            for (Structure s : group) {
                for (StructureFloor f : s.getFloors()) {
                    if (f.region() != null) {
                        outlineBaseCells.addAll(BlueprintMapFootprint.fromFloorRegions(List.of(f.region())));
                    }
                }
            }
            if (outlineBaseCells.isEmpty()) continue;

            LinkedHashSet<BlueprintMapFootprint.Cell> visibleRoomCells = new LinkedHashSet<>();
            roomLayers.stream()
                    .filter(layer -> groupStructureIds.contains(layer.building().getEffectiveStructureId()))
                    .filter(layer -> layer.building().getFloorNumber(village) >= 0)
                    .forEach(layer -> visibleRoomCells.addAll(layer.footprintCells()));

            outlineBaseCells.addAll(visibleRoomCells);
            if (outlineBaseCells.isEmpty()) continue;

            Set<BlueprintMapFootprint.Cell> filteredBase = outlineBaseWithoutEntranceProtrusions(outlineBaseCells);
            if (filteredBase.isEmpty()) filteredBase = Set.copyOf(outlineBaseCells);

            Set<BlueprintMapFootprint.Cell> outlineCells = BlueprintMapFootprint.expand(
                    filteredBase, BUILDING_OUTLINE_WIDTH);
            LinkedHashSet<BlueprintMapFootprint.Cell> shellCells = new LinkedHashSet<>(outlineCells);
            shellCells.removeAll(visibleRoomCells);

            layers.add(new MapStructureLayer(
                    mainRoom,
                    groupStructureIds,
                    shellCells,
                    BlueprintMapFootprint.rowSpans(shellCells),
                    BlueprintMapFootprint.outerEdges(outlineCells)));
        }
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

    private List<MapIconLayer> buildIconLayers(List<MapFootprintLayer> roomLayers, Integer selectedFloor) {
        Map<Integer, List<MapFootprintLayer>> byGroup = new LinkedHashMap<>();
        for (MapFootprintLayer layer : roomLayers) {
            int structureId = layer.building().getEffectiveStructureId();
            int groupId = village == null ? structureId : village.getStructure(structureId)
                    .map(Structure::getStructureGroupId).orElse(structureId);
            byGroup.computeIfAbsent(groupId, ignored -> new ArrayList<>()).add(layer);
        }

        List<MapIconLayer> icons = new ArrayList<>();
        for (List<MapFootprintLayer> groupLayers : byGroup.values()) {
            MapFootprintLayer mainLayer = groupLayers.stream()
                    .filter(layer -> village != null && village.isMainRoom(layer.building()))
                    .findFirst()
                    .orElse(groupLayers.getFirst());

            if (!mainLayer.presentationType().visible() || !mainLayer.presentationType().hasIcon()) {
                continue;
            }

            LinkedHashSet<BlueprintMapFootprint.Cell> buildingCells = new LinkedHashSet<>();
            groupLayers.forEach(layer -> buildingCells.addAll(layer.footprintCells()));
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

    static boolean shouldShowRoomIcon(Integer selectedFloor, boolean contributesToMain) {
        return selectedFloor != null || !contributesToMain;
    }

    private static List<MapIconLayer> offsetOverlappingIcons(List<MapIconLayer> icons) {
        Map<IconAnchor, List<MapIconLayer>> byAnchor = new LinkedHashMap<>();
        for (MapIconLayer icon : icons) {
            byAnchor.computeIfAbsent(new IconAnchor(icon.iconX(), icon.iconZ()), ignored -> new ArrayList<>())
                    .add(icon);
        }

        List<MapIconLayer> result = new ArrayList<>(icons.size());
        for (List<MapIconLayer> group : byAnchor.values()) {
            group.sort(Comparator
                    .comparingInt((MapIconLayer icon) -> icon.floorOrdinal() == null ? 0 : icon.floorOrdinal())
                    .thenComparingInt(icon -> icon.building().getId()));
            for (int index = 0; index < group.size(); index++) {
                if (group.size() == 1) {
                    result.add(group.get(index));
                    continue;
                }
                double angle = -Math.PI / 2.0D + Math.PI * 2.0D * index / group.size();
                result.add(group.get(index).withScreenOffset(
                        Math.cos(angle) * COLLISION_RADIUS_PIXELS,
                        Math.sin(angle) * COLLISION_RADIUS_PIXELS));
            }
        }
        return List.copyOf(result);
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
                             Integer floorOrdinal) {
        MapFootprintLayer {
            footprintCells = Set.copyOf(footprintCells);
            fillSpans = List.copyOf(fillSpans);
            outlineEdges = List.copyOf(outlineEdges);
        }
    }

    record MapStructureLayer(Building mainRoom, Set<Integer> structureIds,
                             Set<BlueprintMapFootprint.Cell> shellCells,
                             List<BlueprintMapFootprint.RowSpan> shellSpans,
                             List<BlueprintMapFootprint.Edge> borderEdges) {
        MapStructureLayer {
            structureIds = Set.copyOf(structureIds);
            shellCells = Set.copyOf(shellCells);
            shellSpans = List.copyOf(shellSpans);
            borderEdges = List.copyOf(borderEdges);
        }
    }

    record MapIconLayer(Building building, BuildingType presentationType, Integer floorOrdinal,
                        double iconX, double iconZ, float iconScale,
                        double screenOffsetX, double screenOffsetY) {
        MapIconLayer(Building building, BuildingType presentationType, Integer floorOrdinal,
                     double iconX, double iconZ, float iconScale) {
            this(building, presentationType, floorOrdinal, iconX, iconZ, iconScale, 0.0D, 0.0D);
        }

        MapIconLayer withScreenOffset(double x, double y) {
            return new MapIconLayer(building, presentationType, floorOrdinal,
                    iconX, iconZ, iconScale, x, y);
        }
    }
    private record IconAnchor(double x, double z) {
    }

    private record Center(double x, double z) {
    }
}
