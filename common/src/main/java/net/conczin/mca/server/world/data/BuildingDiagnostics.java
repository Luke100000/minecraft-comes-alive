package net.conczin.mca.server.world.data;

import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/** Read-only diagnostics for Structure/Floor/Room lookup and traversal decisions. */
public final class BuildingDiagnostics {
    private static final AtomicLong NEXT_TRACE_ID = new AtomicLong();
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final int MAX_CONNECTORS_TO_LOG = 128;

    private BuildingDiagnostics() {
    }

    public static Result diagnose(ServerLevel world, BlockPos pos) {
        return diagnose(world, pos, false);
    }

    public static Result diagnose(ServerLevel world, BlockPos pos, boolean verbose) {
        long traceId = NEXT_TRACE_ID.incrementAndGet();
        VillageManager manager = VillageManager.get(world);
        Village village = manager.findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        Village.StructuralLookup lookup = village == null
                ? new Village.StructuralLookup(Village.StructuralPosition.OUTSIDE, Optional.empty())
                : village.getStructuralLookup(world, pos);
        String uiAction = uiAction(lookup.position());

        log(traceId, "start position={} dimension={} village={} structuralPosition={} uiAction={} verbose={}",
                pos, world.dimension().location(), village == null ? "none" : village.getId(),
                lookup.position(), uiAction, verbose);

        if (village == null) {
            Building.validationResult analysis = manager.analyzeInitialStructure(pos).result();
            String verdict = "NO_NEARBY_VILLAGE: UI uses ADD; initial structure analysis=" + analysis;
            log(traceId, "analysis action={} result={}", uiAction, analysis);
            log(traceId, "verdict={}", verdict);
            return new Result(traceId, lookup.position(), uiAction, verdict);
        }

        Structure structureAt = village.getStructureAt(pos).orElse(null);
        Structure interactionStructure = village.getInteractionStructureAt(world, pos).orElse(null);
        Structure nearestStructure = nearestStructure(village, pos);
        Structure inspected = interactionStructure != null
                ? interactionStructure
                : structureAt != null ? structureAt : nearestStructure;
        Building room = lookup.functionalRoom().orElse(null);
        StructureLayout.Layout layout = StructureLayout.build(village);
        RoomTypeResolver roomTypeResolver = RoomTypeResolver.create(village, layout);

        log(traceId, "lookup structureAt={} interactionStructure={} nearestStructure={} lookupBuilding={} lookupBuildingFloor={}",
                id(structureAt), id(interactionStructure), id(nearestStructure),
                room == null ? "none" : room.getId(), room == null ? "none" : room.getFloorId());

        StructureFloor freshPlayerFloor = null;
        if (inspected != null) {
            boolean contains = inspected.containsPos(pos);
            boolean attaches = StructureConnector.attachesToStructure(world, inspected, pos);
            StructureFloor logicalFloor = inspected.resolveFloor(pos.getY()).orElse(null);
            StructureFloor physicalFloor = inspected.physicalFloorAt(pos).orElse(null);
            log(traceId, "structure id={} source={} bounds={}..{} containsPos={} connectorAttaches={} logicalFloor={} physicalFloor={}",
                    inspected.getId(), inspected.getSource(), inspected.getRawPos0(), inspected.getRawPos1(),
                    contains, attaches, floor(logicalFloor), floor(physicalFloor));
            log(traceId, "persistentFloors={} automaticGroundFloor={} groundReferenceY={} groundEntranceCount={} mainRoomId={} mainRoomMode={}",
                    floors(inspected.getFloors()), floor(inspected.getAutomaticGroundFloor().orElse(null)),
                    inspected.getGroundReferenceY(), inspected.getGroundEntranceCount(),
                    inspected.getMainRoomId(), inspected.isMainRoomAutomatic() ? "AUTOMATIC" : "MANUAL");
            layout.buildingFor(inspected.getId()).ifPresent(logical ->
                    log(traceId, "structureLayout={} storeys={} groundStoreyIndex={} mainRoom={}",
                            logical.structureId(), logical.storeys(),
                            logical.groundStoreyIndex(), logical.mainRoomId()));

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

            if (verbose) {
                StructureFloor diagnosticFloor = physicalFloor != null ? physicalFloor : logicalFloor;
                if (diagnosticFloor != null) {
                    logRoomColumnDiagnostic(world, village, inspected, diagnosticFloor, room, pos, traceId);
                }
                logVerticalConnectors(world, inspected, traceId);
            }

            StructureScanner.Result scan = StructureScanner.scan(
                    world, inspected.getSource(), village.getStructures().values(), inspected.getId());
            freshPlayerFloor = scan.result() == Building.validationResult.SUCCESS
                    ? floorAt(scan.floors(), pos)
                    : null;
            StructureFloor freshGroundFloor = scan.floors().stream()
                    .filter(candidate -> candidate.id() == scan.groundFloorId())
                    .findFirst().orElse(null);
            log(traceId, "freshStructureScan result={} scanSeed={} bounds={}..{} floors={} "
                            + "groundFloor={} groundSeed={} groundReferenceY={} groundEntranceCount={} playerFloor={}",
                    scan.result(), scan.source(), scan.min(), scan.max(), floors(scan.floors()),
                    floor(freshGroundFloor), scan.groundSeed(), scan.groundReferenceY(),
                    scan.groundEntranceCount(), floor(freshPlayerFloor));
            logFloorDifference(traceId, inspected.getFloors(), scan.floors(), verbose);
        }

        Building.validationResult analysis = switch (lookup.position()) {
            case OUTSIDE -> manager.analyzeInitialStructure(pos).result();
            case ATTACHABLE_ROOM -> manager.analyzeRoom(pos).result();
            case REGISTERED_ROOM -> room == null
                    ? Building.validationResult.NOT_IN_BUILDING
                    : manager.analyzeRegisteredRoomUpdate(village, room.getId(), pos).result();
        };
        log(traceId, "analysis action={} result={}", uiAction, analysis);

        String verdict = verdict(lookup.position(), analysis, inspected, room, freshPlayerFloor, pos, world);
        log(traceId, "verdict={}", verdict);
        return new Result(traceId, lookup.position(), uiAction, verdict);
    }

    private static String verdict(Village.StructuralPosition position,
                                  Building.validationResult analysis,
                                  Structure structure,
                                  Building room,
                                  StructureFloor freshPlayerFloor,
                                  BlockPos pos,
                                  ServerLevel world) {
        if (structure == null) {
            return "NO_STRUCTURE: no nearby physical Structure was available for this position";
        }
        if (position == Village.StructuralPosition.OUTSIDE) {
            boolean contains = structure.containsPos(pos);
            boolean attaches = StructureConnector.attachesToStructure(world, structure, pos);
            return "NO_INTERACTION_STRUCTURE: UI uses ADD; containsPos=" + contains
                    + ", verticalConnectorAttachment=" + attaches + ", analysis=" + analysis;
        }
        if (analysis != Building.validationResult.SUCCESS) {
            return "ANALYSIS_FAILED: " + uiAction(position) + " returned " + analysis;
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

    private static void logVerticalConnectors(ServerLevel world, Structure structure, long traceId) {
        BlockPos min = structure.getRawPos0().offset(-1, -2, -1);
        BlockPos max = structure.getRawPos1().offset(1, 2, 1);
        int found = 0;
        for (BlockPos cursor : BlockPos.betweenClosed(min, max)) {
            BlockPos connector = cursor.immutable();
            if (!StructureConnector.isVertical(world.getBlockState(connector))) continue;
            found++;
            if (found > MAX_CONNECTORS_TO_LOG) continue;

            Set<String> candidates = new LinkedHashSet<>();
            for (Direction direction : List.of(Direction.UP, Direction.DOWN)) {
                BlockPos candidate = connector.relative(direction);
                BlockState state = world.getBlockState(candidate);
                String result = StructureConnector.isVertical(state)
                        ? "VERTICAL_CHAIN"
                        : StructureScanner.explainWalkableAnchor(world, candidate);
                candidates.add(candidate + "=" + result);
            }
            for (Direction direction : HORIZONTAL) {
                BlockPos horizontal = connector.relative(direction);
                for (int dy : new int[]{-1, 1}) {
                    BlockPos candidate = horizontal.offset(0, dy, 0);
                    candidates.add(candidate + "=" + StructureScanner.explainWalkableAnchor(world, candidate));
                }
            }
            log(traceId, "verticalConnector pos={} state={} resolvedFloor={} candidates={}",
                    connector, world.getBlockState(connector), floor(structure.resolveFloor(connector.getY()).orElse(null)),
                    candidates);
        }
        log(traceId, "verticalConnectorSummary count={} logged={} truncated={}",
                found, Math.min(found, MAX_CONNECTORS_TO_LOG), found > MAX_CONNECTORS_TO_LOG);
    }

    /**
     * Explains the exact StructureFloor -> BuildingRoomScanner handoff for the queried X/Z column.
     * This is deliberately read-only: it reproduces the current Room passage predicate for each
     * possible base Y in the Floor band so diagnostics can prove whether flattening to anchorY is
     * what turns an otherwise valid interior/furniture column into a Room boundary.
     */
    private static void logRoomColumnDiagnostic(ServerLevel world,
                                                Village village,
                                                Structure structure,
                                                StructureFloor floor,
                                                Building lookupRoom,
                                                BlockPos pos,
                                                long traceId) {
        int x = pos.getX();
        int z = pos.getZ();
        List<Building> sameFloorRooms = village.getRooms()
                .filter(candidate -> candidate.getStructureId() == structure.getId())
                .filter(candidate -> candidate.getFloorId() == floor.id())
                .sorted(Comparator.comparingInt(Building::getId))
                .toList();
        List<Integer> owningRooms = sameFloorRooms.stream()
                .filter(candidate -> candidate.containsFloorColumn(x, z))
                .map(Building::getId)
                .toList();
        List<Integer> poiRooms = sameFloorRooms.stream()
                .filter(candidate -> candidate.getBlocks().values().stream()
                        .flatMap(Collection::stream)
                        .anyMatch(block -> block.getX() == x && block.getZ() == z))
                .map(Building::getId)
                .toList();

        Map<Direction, List<Integer>> adjacentOwners = new LinkedHashMap<>();
        for (Direction direction : HORIZONTAL) {
            int adjacentX = x + direction.getStepX();
            int adjacentZ = z + direction.getStepZ();
            List<Integer> owners = sameFloorRooms.stream()
                    .filter(candidate -> candidate.containsFloorColumn(adjacentX, adjacentZ))
                    .map(Building::getId)
                    .toList();
            if (!owners.isEmpty()) adjacentOwners.put(direction, owners);
        }

        int minY = floor.anchorY() - BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE;
        int maxY = floor.ceilingY() - 1;
        List<String> slices = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            BlockPos probe = new BlockPos(x, y, z);
            BlockState state = world.getBlockState(probe);
            boolean passageCell = StructureConnector.isPassageCell(world, probe);
            var shape = state.getCollisionShape(world, probe);
            String collisionTop = shape.isEmpty()
                    ? "empty"
                    : String.format(Locale.ROOT, "%.3f", shape.max(Direction.Axis.Y));
            slices.add(y + "{state=" + state
                    + ", passage=" + passageCell
                    + ", walkable=" + StructureScanner.explainWalkableAnchor(world, probe)
                    + ", collisionTop=" + collisionTop + "}");
        }

        BlockPos anchorBase = new BlockPos(x, floor.anchorY(), z);
        log(traceId, "columnDiagnostic xz={},{} queryY={} structure={} floor={} floorContainsColumn={} "
                        + "lookupRoom={} owningRooms={} poiRooms={} adjacentRoomOwners={}",
                x, z, pos.getY(), structure.getId(), floor(floor), floor.contains(x, z),
                lookupRoom == null ? "none" : lookupRoom.getId(), owningRooms, poiRooms, adjacentOwners);
        log(traceId, "columnDiagnostic roomScanner anchorBaseY={} anchorDecision={} slices={}",
                floor.anchorY(), BuildingRoomScanner.roomPassageDecision(world, floor, anchorBase), slices);
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
                : floor.id() + "@" + floor.anchorY() + ".." + floor.ceilingY() + " area=" + floor.area();
    }

    private static String id(Structure structure) {
        return structure == null ? "none" : Integer.toString(structure.getId());
    }

    private static String uiAction(Village.StructuralPosition position) {
        return switch (position) {
            case OUTSIDE -> "ADD";
            case ATTACHABLE_ROOM -> "ADD_ROOM";
            case REGISTERED_ROOM -> "UPDATE_ROOM";
        };
    }

    private static void log(long traceId, String message, Object... args) {
        Object[] prefixed = new Object[args.length + 1];
        prefixed[0] = traceId;
        System.arraycopy(args, 0, prefixed, 1, args.length);
        MCA.LOGGER.info("[MCA-Diagnose][{}] " + message, prefixed);
    }

    public record Result(long traceId,
                         Village.StructuralPosition position,
                         String uiAction,
                         String verdict) {
    }
}
