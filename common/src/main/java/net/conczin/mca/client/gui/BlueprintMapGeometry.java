package net.conczin.mca.client.gui;

import net.conczin.mca.MCA;
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
            List<MapStructureLayer> structures = buildStructureLayers(selectedFloor, rooms);
            List<MapIconLayer> icons = buildIconLayers(rooms);
            List<Building> grouped = village.getExternalBuildings().filter(Building::isComplete)
                    .filter(building -> floorLayout.isBuildingVisible(building, selectedFloor))
                    .sorted(Comparator.comparingInt(Building::getId)).map(Building.class::cast).toList();
            MCA.LOGGER.info("[BlueprintStructureDebug] stage=map-geometry selectedFloor={} roomLayers={} roomSummaries={} structureLayers={} iconRoomIds={} groupedIds={}",
                    selectedFloor, rooms.size(), rooms.stream().map(layer -> "id=" + layer.building().getId()
                            + ":structure=" + layer.building().getStructureId()
                            + ":floorOrdinal=" + layer.floorOrdinal()
                            + ":area=" + layer.footprintCells().size()
                            + ":visible=" + layer.visibleCells().size()
                            + ":bounds=" + bounds(layer.footprintCells())).toList(),
                    structures.size(), icons.stream().map(layer -> layer.building().getId()).toList(),
                    grouped.stream().map(Building::getId).toList());
            return new MapGeometry(rooms, structures, icons, grouped);
        });
    }

    private List<MapFootprintLayer> buildRoomLayers(Integer selectedFloor) {
        List<Building> rooms = village.getRooms()
                .sorted(Comparator.comparingInt(Building::getEffectiveStructureId).thenComparingInt(Building::getId))
                .toList();
        List<Integer> floors = selectedFloor == null
                ? BlueprintMapLayering.floorRenderOrder(floorLayout.ordinals())
                : List.of(selectedFloor);
        List<MapFootprintLayer> layers = new ArrayList<>();
        for (int floor : floors) {
            for (Building room : rooms) {
                if (!floorLayout.isBuildingVisible(room, floor)) continue;
                Set<BlueprintMapFootprint.Cell> footprintCells = roomFootprint(room);
                if (footprintCells.isEmpty()) continue;

                Set<BlueprintMapFootprint.Cell> visibleCells = footprintCells;

                layers.add(new MapFootprintLayer(
                        room,
                        footprintCells,
                        visibleCells,
                        BlueprintMapFootprint.rowSpans(visibleCells),
                        BlueprintMapFootprint.outerEdges(footprintCells),
                        floor));
            }
        }
        return List.copyOf(layers);
    }

    private List<MapStructureLayer> buildStructureLayers(Integer selectedFloor,
                                                          List<MapFootprintLayer> roomLayers) {
        List<MapStructureLayer> layers = new ArrayList<>();
        for (Structure structure : village.getStructures().values().stream()
                .sorted(Comparator.comparingInt(Structure::getId)).toList()) {
            Building rootRoom = village.getBuilding(structure.getRootRoomId()).orElse(null);
            if (rootRoom == null) continue;

            LinkedHashSet<BlueprintMapFootprint.Cell> physicalCells = new LinkedHashSet<>();
            LinkedHashSet<BlueprintMapFootprint.Cell> canonicalOutlineBaseCells = new LinkedHashSet<>();
            for (StructureFloor floor : structure.getFloors()) {
                OptionalInt ordinal = floorLayout.ordinalForFloor(structure.getId(), floor.id());
                Set<BlueprintMapFootprint.Cell> floorCells =
                        BlueprintMapFootprint.fromFloorRegions(List.of(floor.region()));

                // The outer Structure shell is stable across floor selection. Basements never
                // enlarge that shell, but their own physical footprint remains visible separately.
                if (ordinal.isPresent()
                        && BlueprintMapLayering.contributesToAllFloorsOutline(ordinal.getAsInt())) {
                    canonicalOutlineBaseCells.addAll(floorCells);
                }

                if (selectedFloor != null && (ordinal.isEmpty() || ordinal.getAsInt() != selectedFloor)) {
                    continue;
                }
                physicalCells.addAll(floorCells);
            }
            if (physicalCells.isEmpty() || canonicalOutlineBaseCells.isEmpty()) continue;

            LinkedHashSet<BlueprintMapFootprint.Cell> registeredRoomCells = new LinkedHashSet<>();
            for (MapFootprintLayer roomLayer : roomLayers) {
                if (roomLayer.building().getStructureId() == structure.getId()) {
                    registeredRoomCells.addAll(roomLayer.footprintCells());
                }
            }
            StructureShape shape = structureShape(
                    physicalCells, canonicalOutlineBaseCells, registeredRoomCells, selectedFloor == null);
            List<Integer> roomLayerIds = roomLayers.stream()
                    .filter(layer -> layer.building().getStructureId() == structure.getId())
                    .map(layer -> layer.building().getId()).toList();
            MCA.LOGGER.info("[BlueprintStructureDebug] stage=map-structure-layer structureId={} rootRoomId={} selectedFloor={} floors={} physicalCells={} physicalBounds={} canonicalOutlineBaseCells={} canonicalOutlineBaseBounds={} roomLayerIds={} registeredRoomCells={} outlineCells={} shadeCells={} wallShadeCells={}",
                    structure.getId(), structure.getRootRoomId(), selectedFloor,
                    structure.getFloors().stream().map(floor -> "id=" + floor.id() + ":y=" + floor.anchorY()
                            + ":area=" + floor.area()).toList(),
                    physicalCells.size(), bounds(physicalCells), canonicalOutlineBaseCells.size(),
                    bounds(canonicalOutlineBaseCells), roomLayerIds, registeredRoomCells.size(),
                    shape.outlineCells().size(), shape.shadeCells().size(),
                    shape.shadeCells().stream().filter(cell -> !physicalCells.contains(cell)).count());
            layers.add(new MapStructureLayer(
                    rootRoom,
                    shape.shadeCells(),
                    BlueprintMapFootprint.rowSpans(shape.shadeCells()),
                    BlueprintMapFootprint.outerEdges(shape.outlineCells()),
                    selectedFloor));
        }
        return List.copyOf(layers);
    }

    static StructureShape structureShape(Set<BlueprintMapFootprint.Cell> physicalCells,
                                         Set<BlueprintMapFootprint.Cell> outlineBaseCells,
                                         Set<BlueprintMapFootprint.Cell> registeredRoomCells,
                                         boolean allFloors) {
        Set<BlueprintMapFootprint.Cell> outlineCells = BlueprintMapFootprint.expand(
                outlineBaseCells, BUILDING_OUTLINE_WIDTH);
        Set<BlueprintMapFootprint.Cell> shadeCells = BlueprintMapLayering.structureShade(
                physicalCells, outlineBaseCells, outlineCells, registeredRoomCells, allFloors);
        return new StructureShape(Set.copyOf(outlineCells), shadeCells);
    }

    private static List<MapIconLayer> buildIconLayers(List<MapFootprintLayer> roomLayers) {
        TreeMap<Integer, List<MapFootprintLayer>> byRoom = new TreeMap<>();
        for (MapFootprintLayer layer : roomLayers) {
            if (!layer.visibleCells().isEmpty()
                    && layer.building().getBuildingType().visible()
                    && layer.building().getBuildingType().hasIcon()) {
                byRoom.computeIfAbsent(layer.building().getId(), ignored -> new ArrayList<>()).add(layer);
            }
        }
        List<MapIconLayer> icons = new ArrayList<>();
        for (List<MapFootprintLayer> layers : byRoom.values()) {
            LinkedHashSet<BlueprintMapFootprint.Cell> cells = new LinkedHashSet<>();
            layers.forEach(layer -> cells.addAll(layer.visibleCells()));
            if (cells.isEmpty()) continue;
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

    private static String bounds(Set<BlueprintMapFootprint.Cell> cells) {
        if (cells.isEmpty()) return "empty";
        int minX = cells.stream().mapToInt(BlueprintMapFootprint.Cell::x).min().orElse(0);
        int maxX = cells.stream().mapToInt(BlueprintMapFootprint.Cell::x).max().orElse(0);
        int minZ = cells.stream().mapToInt(BlueprintMapFootprint.Cell::z).min().orElse(0);
        int maxZ = cells.stream().mapToInt(BlueprintMapFootprint.Cell::z).max().orElse(0);
        return "[" + minX + "," + minZ + "->" + maxX + "," + maxZ + "]";
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
                             Set<BlueprintMapFootprint.Cell> footprintCells,
                             Set<BlueprintMapFootprint.Cell> visibleCells,
                             List<BlueprintMapFootprint.RowSpan> fillSpans,
                             List<BlueprintMapFootprint.Edge> outlineEdges,
                             Integer floorOrdinal) {
        MapFootprintLayer {
            footprintCells = Set.copyOf(footprintCells);
            visibleCells = Set.copyOf(visibleCells);
            fillSpans = List.copyOf(fillSpans);
            outlineEdges = List.copyOf(outlineEdges);
        }
    }

    record MapStructureLayer(Building root, Set<BlueprintMapFootprint.Cell> shadeCells,
                             List<BlueprintMapFootprint.RowSpan> shadeSpans,
                             List<BlueprintMapFootprint.Edge> borderEdges,
                             Integer floorOrdinal) {
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
