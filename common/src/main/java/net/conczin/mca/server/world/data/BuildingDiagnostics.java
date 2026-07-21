package net.conczin.mca.server.world.data;

import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
        long traceId = NEXT_TRACE_ID.incrementAndGet();
        VillageManager manager = VillageManager.get(world);
        Village village = manager.findNearestVillage(pos, Village.MERGE_MARGIN).orElse(null);
        Village.StructuralLookup lookup = village == null
                ? new Village.StructuralLookup(Village.StructuralPosition.OUTSIDE, Optional.empty())
                : village.getStructuralLookup(world, pos);
        String uiAction = uiAction(lookup.position());

        log(traceId, "start position={} dimension={} village={} structuralPosition={} uiAction={}",
                pos, world.dimension().location(), village == null ? "none" : village.getId(),
                lookup.position(), uiAction);

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

        log(traceId, "lookup structureAt={} interactionStructure={} nearestStructure={} lookupBuilding={} lookupBuildingFloor={}",
                id(structureAt), id(interactionStructure), id(nearestStructure),
                room == null ? "none" : room.getId(), room == null ? "none" : room.getFloorId());

        FreshScan fresh = null;
        if (inspected != null) {
            boolean contains = inspected.containsPos(pos);
            boolean attaches = StructureConnector.attachesToStructure(world, inspected, pos);
            StructureFloor logicalFloor = inspected.resolveFloor(pos.getY()).orElse(null);
            StructureFloor physicalFloor = inspected.physicalFloorAt(pos).orElse(null);
            log(traceId, "structure id={} source={} bounds={}..{} containsPos={} connectorAttaches={} logicalFloor={} physicalFloor={}",
                    inspected.getId(), inspected.getSource(), inspected.getRawPos0(), inspected.getRawPos1(),
                    contains, attaches, floor(logicalFloor), floor(physicalFloor));
            log(traceId, "persistentFloors={}", floors(inspected.getFloors()));

            if (room != null) {
                StructureFloor roomFloor = inspected.getFloor(room.getFloorId()).orElse(null);
                boolean sameColumn = room.containsFloorColumn(pos.getX(), pos.getZ());
                boolean elevatedWithinBand = roomFloor != null
                        && pos.getY() > roomFloor.anchorY() + BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE
                        && pos.getY() < roomFloor.ceilingY();
                log(traceId, "room id={} type={} structureId={} floorId={} floor={} footprintArea={} containsColumn={} elevatedWithinSameFloorBand={}",
                        room.getId(), room.getType(), room.getStructureId(), room.getFloorId(), floor(roomFloor),
                        room.getFloorFootprintArea(), sameColumn, elevatedWithinBand);
            }

            logVerticalConnectors(world, inspected, traceId);

            StructureScanner.Result scan = StructureScanner.scan(
                    world, inspected.getSource(), village.getStructures().values(), inspected.getId());
            StructureFloor freshPlayerFloor = scan.result() == Building.validationResult.SUCCESS
                    ? floorAt(scan.floors(), pos)
                    : null;
            fresh = new FreshScan(scan.result(), scan.floors(), freshPlayerFloor);
            log(traceId, "freshStructureScan result={} floors={} playerFloor={}",
                    scan.result(), floors(scan.floors()), floor(freshPlayerFloor));
            logFloorDifference(traceId, inspected.getFloors(), scan.floors());
        }

        Building.validationResult analysis = switch (lookup.position()) {
            case OUTSIDE -> manager.analyzeInitialStructure(pos).result();
            case ATTACHABLE_ROOM -> manager.analyzeRoom(pos).result();
            case REGISTERED_ROOM -> room == null
                    ? Building.validationResult.NOT_IN_BUILDING
                    : manager.analyzeRegisteredRoomUpdate(village, room.getId(), pos).result();
        };
        log(traceId, "analysis action={} result={}", uiAction, analysis);

        String verdict = verdict(lookup.position(), analysis, inspected, room, fresh, pos, world);
        log(traceId, "verdict={}", verdict);
        return new Result(traceId, lookup.position(), uiAction, verdict);
    }

    private static String verdict(Village.StructuralPosition position,
                                  Building.validationResult analysis,
                                  Structure structure,
                                  Building room,
                                  FreshScan fresh,
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
            StructureFloor freshPlayerFloor = fresh == null ? null : fresh.playerFloor();
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

    private static void logFloorDifference(long traceId,
                                           List<StructureFloor> persistent,
                                           List<StructureFloor> fresh) {
        List<Integer> persistentAnchors = persistent.stream().map(StructureFloor::anchorY).toList();
        List<Integer> freshAnchors = fresh.stream().map(StructureFloor::anchorY).toList();
        if (!persistentAnchors.equals(freshAnchors)) {
            log(traceId, "floorMismatch persistentAnchors={} freshAnchors={}", persistentAnchors, freshAnchors);
        } else {
            log(traceId, "floorMismatch none anchors={}", persistentAnchors);
        }
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

    private record FreshScan(Building.validationResult result,
                             List<StructureFloor> floors,
                             StructureFloor playerFloor) {
        private FreshScan {
            floors = List.copyOf(floors);
        }
    }

    public record Result(long traceId,
                         Village.StructuralPosition position,
                         String uiAction,
                         String verdict) {
    }
}
