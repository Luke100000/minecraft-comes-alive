package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;

/** Server-side orchestration for Room scan, type-selection and commit workflows. */
public final class RoomWorkflow {
    private final VillageManager manager;
    private final ServerLevel world;

    public RoomWorkflow(VillageManager manager, ServerLevel world) {
        this.manager = manager;
        this.world = world;
    }

    public Outcome addRoom(BlockPos source, String selectedType) {
        return commitAddition(manager.analyzeRoom(source), selectedType);
    }

    public Outcome addBuilding(BlockPos source, String selectedType) {
        return commitAddition(manager.analyzeBuildingAddition(source), selectedType);
    }

    public Outcome addFloor(BlockPos source, int expectedBuildingId, String selectedType) {
        return addAttachedRoom(source, expectedBuildingId, selectedType, Village.RoomScanMode.ADD_FLOOR);
    }

    public Outcome addBasement(BlockPos source, int expectedBuildingId, String selectedType) {
        return addAttachedRoom(source, expectedBuildingId, selectedType, Village.RoomScanMode.ADD_BASEMENT);
    }

    public Outcome updateRoom(BlockPos source, int expectedRoomId, String selectedType) {
        ResolvedRoom resolved = resolveRoom(source, expectedRoomId);
        if (resolved == null) {
            return Outcome.failed(Building.validationResult.NOT_IN_BUILDING, source, expectedRoomId);
        }

        Building room = resolved.room();
        RegisteredRoomUpdate update = manager.analyzeRegisteredRoomUpdate(resolved.village(), room.getId(), source);
        if (update.result() != Building.validationResult.SUCCESS) {
            return Outcome.failed(update.result(), source, room.getId());
        }
        if (selectedType == null && update.requiresTypeSelection()) {
            return Outcome.requiresTypeSelection(
                    update.source(), update.playerMatchingTypes(), room.getId());
        }
        return committed(manager.commitRegisteredRoomUpdate(update, selectedType), update.source(), room.getId());
    }

    public Outcome updateInheritance(BlockPos source,
                                     int expectedRoomId,
                                     boolean enabled,
                                     String selectedType) {
        ResolvedRoom resolved = resolveRoom(source, expectedRoomId);
        if (resolved == null) {
            return Outcome.failed(Building.validationResult.NOT_IN_BUILDING, source, expectedRoomId);
        }

        Village village = resolved.village();
        Building room = resolved.room();
        RoomInheritanceUpdate update = village.analyzeRoomInheritanceUpdate(room, enabled);
        if (!update.valid()) {
            return Outcome.failed(Building.validationResult.NOT_IN_BUILDING, source, room.getId());
        }
        if (update.previousEnabled() == enabled && selectedType == null) {
            return Outcome.committed(source, room.getId());
        }
        if (selectedType == null && update.requiresTypeSelection()) {
            return Outcome.requiresTypeSelection(source, update.matchingTypes(), room.getId());
        }
        return committed(village.commitRoomInheritanceUpdate(update, selectedType), source, room.getId());
    }

    private Outcome addAttachedRoom(BlockPos source,
                                    int expectedBuildingId,
                                    String selectedType,
                                    Village.RoomScanMode mode) {
        if (expectedBuildingId < 0) {
            return Outcome.failed(Building.validationResult.NOT_IN_BUILDING, source, expectedBuildingId);
        }
        return commitAddition(
                manager.analyzeAttachedRoom(source, mode, expectedBuildingId), selectedType);
    }

    private Outcome commitAddition(BuildingScanResult scan, String selectedType) {
        if (scan == null) {
            return Outcome.failed(Building.validationResult.TOO_SMALL, BlockPos.ZERO, -1);
        }
        if (scan.result() != Building.validationResult.SUCCESS) {
            return Outcome.failed(scan.result(), scan.source(), scan.targetBuildingId());
        }
        if (selectedType == null && scan.isAmbiguous()) {
            return Outcome.requiresTypeSelection(
                    scan.source(), scan.matchingTypes(), scan.targetBuildingId());
        }
        return committed(
                manager.commitRoomAddition(scan, selectedType), scan.source(), scan.targetBuildingId());
    }

    private ResolvedRoom resolveRoom(BlockPos source, int expectedRoomId) {
        if (world == null) return null;
        Village village = manager.findNearestVillage(source, Village.MERGE_MARGIN).orElse(null);
        RoomScanPlan plan = village == null ? null : village.getRoomScanPlan(world, source);
        Building room = plan == null || plan.mode() != Village.RoomScanMode.UPDATE_ROOM
                ? null : plan.currentRoom().orElse(null);
        if (room == null || expectedRoomId >= 0 && room.getId() != expectedRoomId) return null;
        return new ResolvedRoom(village, room);
    }

    private static Outcome committed(Building.validationResult result, BlockPos source, int expectedTargetId) {
        return result == Building.validationResult.SUCCESS
                ? Outcome.committed(source, expectedTargetId)
                : Outcome.failed(result, source, expectedTargetId);
    }

    public enum Status {
        COMMITTED,
        REQUIRES_TYPE_SELECTION,
        FAILED
    }

    private record ResolvedRoom(Village village, Building room) {
    }

    public record Outcome(Status status,
                          Building.validationResult result,
                          BlockPos source,
                          List<String> matchingTypes,
                          int expectedTargetId) {
        public Outcome {
            source = source == null ? BlockPos.ZERO : source.immutable();
            matchingTypes = matchingTypes == null ? List.of() : List.copyOf(matchingTypes);
        }

        static Outcome committed(BlockPos source, int expectedTargetId) {
            return new Outcome(Status.COMMITTED, Building.validationResult.SUCCESS,
                    source, List.of(), expectedTargetId);
        }

        static Outcome requiresTypeSelection(BlockPos source,
                                             List<String> matchingTypes,
                                             int expectedTargetId) {
            return new Outcome(Status.REQUIRES_TYPE_SELECTION, Building.validationResult.SUCCESS,
                    source, matchingTypes, expectedTargetId);
        }

        static Outcome failed(Building.validationResult result, BlockPos source, int expectedTargetId) {
            return new Outcome(Status.FAILED, result, source, List.of(), expectedTargetId);
        }
    }
}
