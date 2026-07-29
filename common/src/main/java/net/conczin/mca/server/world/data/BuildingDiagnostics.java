package net.conczin.mca.server.world.data;

import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Read-only diagnostics for Structure/Floor/Room lookup and traversal decisions. */
public final class BuildingDiagnostics {
    private static final AtomicLong NEXT_TRACE_ID = new AtomicLong();
    private BuildingDiagnostics() {
    }

    public static Result diagnose(ServerLevel world, BlockPos pos) {
        return diagnose(world, pos, false);
    }

    public static Result diagnose(ServerLevel world, BlockPos pos, boolean verbose) {
        long traceId = NEXT_TRACE_ID.incrementAndGet();
        VillageManager manager = VillageManager.get(world);
        Village village = manager.findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        Village.RoomScanContext context = village == null
                ? new Village.RoomScanContext(Optional.empty(),
                Village.RoomScanMode.ADD_BUILDING, -1, Integer.MIN_VALUE)
                : village.getRoomScanContext(world, pos);
        StructuralPosition position = structuralPosition(context);
        String uiAction = uiAction(context.mode());

        log(traceId, "start position={} dimension={} village={} structuralPosition={} uiAction={} targetBuilding={} verbose={}",
                pos, world.dimension().location(), village == null ? "none" : village.getId(),
                position, uiAction, context.targetBuildingId(), verbose);

        if (village == null) {
            Building.validationResult analysis = manager.analyzeBuildingAddition(pos).result();
            String verdict = "NO_NEARBY_VILLAGE: UI uses " + uiAction + "; initial structure analysis=" + analysis;
            log(traceId, "analysis action={} result={}", uiAction, analysis);
            log(traceId, "verdict={}", verdict);
            return new Result(traceId, position, uiAction, verdict);
        }

        Structure structureAt = village.getExactStructureAt(pos).orElse(null);
        Structure interactionStructure = village.getInteractionStructureAt(world, pos).orElse(null);
        Structure nearestStructure = nearestStructure(village, pos);
        Structure inspected = interactionStructure != null
                ? interactionStructure
                : structureAt != null ? structureAt : nearestStructure;
        Building room = context.functionalRoom().orElse(null);
        RoomTypeResolver roomTypeResolver = RoomTypeResolver.create(village);

        log(traceId, "lookup structureAt={} interactionStructure={} nearestStructure={} lookupBuilding={} lookupBuildingFloor={}",
                id(structureAt), id(interactionStructure), id(nearestStructure),
                room == null ? "none" : room.getId(), room == null ? "none" : room.getFloorId());

        StructureFloor freshPlayerFloor = null;
        if (inspected != null) {
            boolean contains = inspected.containsPos(pos);
            boolean attaches = StructureConnector.attachesToStructure(world, inspected, pos);
            StructureFloor logicalFloor = inspected.resolveFloor(pos.getY()).orElse(null);
            StructureFloor physicalFloor = inspected.physicalFloorAt(pos).orElse(null);
            log(traceId, "structure id={} logicalBuildingId={} source={} bounds={}..{} containsPos={} connectorAttaches={} logicalFloor={} physicalFloor={}",
                    inspected.getId(), inspected.getLogicalBuildingId(), inspected.getSource(),
                    inspected.getRawPos0(), inspected.getRawPos1(),
                    contains, attaches, floor(logicalFloor), floor(physicalFloor));
            log(traceId, "persistentFloors={} surfaceReferenceY={} storedMainRoomId={} storedMainRoomMode={}",
                    floors(inspected.getFloors()),
                    inspected.getSurfaceReferenceY(),
                    inspected.getMainRoomId(), inspected.isMainRoomAutomatic() ? "AUTOMATIC" : "MANUAL");

            if (room != null) {
                StructureFloor roomFloor = inspected.getFloor(room.getFloorId()).orElse(null);
                boolean sameColumn = room.containsFloorColumn(pos.getX(), pos.getZ());
                boolean elevatedWithinBand = roomFloor != null
                        && pos.getY() > roomFloor.anchorY() + BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE
                        && pos.getY() < roomFloor.ceilingY();
                RoomTypeResolver.Context resolved = roomTypeResolver.resolve(room);
                log(traceId, "room id={} directType={} effectiveType={} structureId={} floorId={} floor={} footprintArea={} ownPoi={} effectivePoi={} containsColumn={} elevatedWithinSameFloorBand={}",
                        room.getId(), room.getType(), resolved.effectiveType().name(), room.getStructureId(), room.getFloorId(), floor(roomFloor),
                        room.getFloorFootprintArea(), room.getBlockCount(),
                        resolved.effectivePoi().values().stream().mapToInt(List::size).sum(), sameColumn, elevatedWithinBand);
            }

            StructureScanner.Result scan = StructureScanner.scan(
                    world, inspected.getSource(), village.getStructures().values(), inspected.getId());
            freshPlayerFloor = scan.result() == Building.validationResult.SUCCESS
                    ? floorAt(scan.floors(), pos)
                    : null;
            log(traceId, "freshStructureScan result={} scanSeed={} bounds={}..{} floors={} surfaceReferenceY={} playerFloor={}",
                    scan.result(), scan.source(), scan.min(), scan.max(), floors(scan.floors()),
                    scan.surfaceReferenceY(), floor(freshPlayerFloor));
            logFloorDifference(traceId, inspected.getFloors(), scan.floors(), verbose);
        }

        Building.validationResult analysis = switch (context.mode()) {
            case ADD_BUILDING -> manager.analyzeBuildingAddition(pos).result();
            case ADD_ROOM -> manager.analyzeRoom(pos).result();
            case ADD_FLOOR, ADD_BASEMENT -> manager.analyzeAttachedRoom(
                    pos, context.mode(), context.targetBuildingId()).result();
            case UPDATE_ROOM -> room == null
                    ? Building.validationResult.NOT_IN_BUILDING
                    : manager.analyzeRegisteredRoomUpdate(village, room.getId(), pos).result();
        };
        log(traceId, "analysis action={} result={}", uiAction, analysis);

        String verdict = verdict(position, uiAction, analysis, inspected, room, freshPlayerFloor, pos, world);
        log(traceId, "verdict={}", verdict);
        return new Result(traceId, position, uiAction, verdict);
    }

    private static StructuralPosition structuralPosition(Village.RoomScanContext context) {
        return switch (context.mode()) {
            case UPDATE_ROOM -> StructuralPosition.REGISTERED_ROOM;
            case ADD_ROOM -> StructuralPosition.ATTACHABLE_ROOM;
            case ADD_BUILDING, ADD_FLOOR, ADD_BASEMENT -> StructuralPosition.OUTSIDE;
        };
    }

    private static String verdict(StructuralPosition position,
                                  String uiAction,
                                  Building.validationResult analysis,
                                  Structure structure,
                                  Building room,
                                  StructureFloor freshPlayerFloor,
                                  BlockPos pos,
                                  ServerLevel world) {
        if (structure == null) {
            return "NO_STRUCTURE: no nearby physical Structure was available for this position";
        }
        if (position == StructuralPosition.OUTSIDE) {
            boolean contains = structure.containsPos(pos);
            boolean attaches = StructureConnector.attachesToStructure(world, structure, pos);
            return "NO_INTERACTION_STRUCTURE: UI uses " + uiAction + "; containsPos=" + contains
                    + ", verticalConnectorAttachment=" + attaches + ", analysis=" + analysis;
        }
        if (analysis != Building.validationResult.SUCCESS) {
            return "ANALYSIS_FAILED: " + uiAction + " returned " + analysis;
        }
        if (room != null) {
            StructureFloor persistentRoomFloor = structure.getFloor(room.getFloorId()).orElse(null);
            if (persistentRoomFloor != null && freshPlayerFloor != null
                    && freshPlayerFloor.anchorY() > persistentRoomFloor.anchorY()) {
                return "FRESH_SCAN_SEPARATES_UPPER_FLOOR: persisted Room is Floor " + room.getFloorId()
                        + " @" + persistentRoomFloor.anchorY() + " but fresh scan resolves player to @"
                        + freshPlayerFloor.anchorY();
            }
            if (persistentRoomFloor != null
                    && pos.getY() > persistentRoomFloor.anchorY() + BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE
                    && pos.getY() < persistentRoomFloor.ceilingY()) {
                return "ELEVATED_POSITION_IN_SAME_FLOOR_BAND: no separate StructureFloor anchor currently owns this Y, "
                        + "so Room lookup remains on Floor " + room.getFloorId() + " @" + persistentRoomFloor.anchorY();
            }
        }
        return switch (position) {
            case ATTACHABLE_ROOM -> "ATTACHABLE_ROOM: physical Structure/Floor resolved, but this component is not registered as a Room";
            case REGISTERED_ROOM -> "REGISTERED_ROOM: Structure, Floor and Room lookup all resolved successfully";
            case OUTSIDE -> "OUTSIDE";
        };
    }

    private static void logFloorDifference(long traceId,
                                           List<StructureFloor> persistent,
                                           List<StructureFloor> fresh,
                                           boolean verbose) {
        List<Integer> persistentAnchors = persistent.stream().map(StructureFloor::anchorY).toList();
        List<Integer> freshAnchors = fresh.stream().map(StructureFloor::anchorY).toList();
        if (!persistentAnchors.equals(freshAnchors)) {
            log(traceId, "floorMismatch persistentAnchors={} freshAnchors={}", persistentAnchors, freshAnchors);
        }

        boolean geometryMismatch = false;
        for (StructureFloor persistentFloor : persistent) {
            StructureFloor freshFloor = fresh.stream()
                    .filter(candidate -> candidate.anchorY() == persistentFloor.anchorY())
                    .findFirst().orElse(null);
            if (freshFloor == null || persistentFloor.region() == null || freshFloor.region() == null) continue;

            Set<BlockPos> persistentCells = persistentFloor.region().cells();
            Set<BlockPos> freshCells = freshFloor.region().cells();
            if (persistentCells.equals(freshCells)) continue;

            geometryMismatch = true;
            LinkedHashSet<BlockPos> added = new LinkedHashSet<>(freshCells);
            added.removeAll(persistentCells);
            LinkedHashSet<BlockPos> removed = new LinkedHashSet<>(persistentCells);
            removed.removeAll(freshCells);
            if (verbose) {
                log(traceId, "floorGeometryMismatch anchorY={} persistentArea={} freshArea={} "
                                + "addedColumns={} removedColumns={} addedSample={} removedSample={}",
                        persistentFloor.anchorY(), persistentFloor.area(), freshFloor.area(),
                        added.size(), removed.size(), sampleColumns(added), sampleColumns(removed));
            } else {
                log(traceId, "floorGeometryMismatch anchorY={} persistentArea={} freshArea={} "
                                + "addedColumns={} removedColumns={}",
                        persistentFloor.anchorY(), persistentFloor.area(), freshFloor.area(),
                        added.size(), removed.size());
            }
        }
        if (persistentAnchors.equals(freshAnchors) && !geometryMismatch) {
            log(traceId, "floorMismatch none anchors={}", persistentAnchors);
        }
    }

    private static List<BlockPos> sampleColumns(Collection<BlockPos> cells) {
        return cells.stream()
                .sorted(Comparator.comparingInt((BlockPos p) -> p.getX())
                        .thenComparingInt(p -> p.getZ())
                        .thenComparingInt(p -> p.getY()))
                .limit(16)
                .toList();
    }

    private static Structure nearestStructure(Village village, BlockPos pos) {
        return village.getStructures().values().stream()
                .min(Comparator.comparingLong(structure -> distanceSquared(structure.getCenter(), pos)))
                .orElse(null);
    }

    private static long distanceSquared(BlockPos first, BlockPos second) {
        long dx = (long) first.getX() - second.getX();
        long dy = (long) first.getY() - second.getY();
        long dz = (long) first.getZ() - second.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static StructureFloor floorAt(List<StructureFloor> floors, BlockPos pos) {
        return floors.stream()
                .filter(candidate -> candidate.anchorY() <= pos.getY() && pos.getY() < candidate.ceilingY())
                .filter(candidate -> candidate.contains(pos.getX(), pos.getZ()))
                .max(Comparator.comparingInt(StructureFloor::anchorY))
                .orElse(null);
    }

    private static String floors(List<StructureFloor> floors) {
        return floors.stream().map(BuildingDiagnostics::floor).toList().toString();
    }

    private static String floor(StructureFloor floor) {
        return floor == null ? "none"
                : "id=" + floor.id() + " number=" + floor.floorNumber() + " @"
                + floor.anchorY() + ".." + floor.ceilingY() + " area=" + floor.area();
    }

    private static String id(Structure structure) {
        return structure == null ? "none" : Integer.toString(structure.getId());
    }

    private static String uiAction(Village.RoomScanMode mode) {
        return mode.name();
    }

    private static void log(long traceId, String message, Object... args) {
        Object[] prefixed = new Object[args.length + 1];
        prefixed[0] = traceId;
        System.arraycopy(args, 0, prefixed, 1, args.length);
        MCA.LOGGER.info("[MCA-Diagnose][{}] " + message, prefixed);
    }

    public record Result(long traceId,
                         StructuralPosition position,
                         String uiAction,
                         String verdict) {
    }

    public enum StructuralPosition {
        OUTSIDE, REGISTERED_ROOM, ATTACHABLE_ROOM
    }
}
