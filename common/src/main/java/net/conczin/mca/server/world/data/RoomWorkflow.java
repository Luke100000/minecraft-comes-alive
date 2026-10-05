package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.conczin.mca.resources.data.BuildingType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.*;

/** Server-side orchestration for Room scan, type-selection and commit workflows. */
public final class RoomWorkflow {
    private final VillageManager manager;
    private final ServerLevel world;

    public RoomWorkflow(VillageManager manager, ServerLevel world) {
        this.manager = manager;
        this.world = world;
    }

    public Outcome addBuilding(BlockPos source, String selectedType) {
        return addBuilding(source, -1, selectedType);
    }

    public Outcome addBuilding(BlockPos source, int expectedTargetBuildingId, String selectedType) {
        Village village = manager.findNearestVillage(source, Village.MERGE_MARGIN).orElse(null);
        if (village != null) {
            RoomScanPlanner.Analysis analysis = RoomScanPlanner.analyze(village, world, source);
            RoomScanPlan plan = analysis.plan();
            if (analysis.result() != Building.validationResult.SUCCESS) {
                return Outcome.failed(analysis.result(), source, expectedTargetBuildingId);
            }
            if (plan.mode() == Village.RoomScanMode.ADD_ATTACHMENT) {
                if (expectedTargetBuildingId >= 0
                        && plan.targetBuildingId() != expectedTargetBuildingId) {
                    return Outcome.failed(
                            Building.validationResult.NOT_IN_BUILDING, source, expectedTargetBuildingId);
                }
                Outcome outcome = commitAddition(
                        analyzeAttachedRoom(village, analysis, expectedTargetBuildingId), selectedType);
                return withProspectiveFloorNumber(outcome, plan.prospectiveFloorNumber());
            }
            if (expectedTargetBuildingId >= 0) {
                return Outcome.failed(
                        Building.validationResult.NOT_IN_BUILDING, source, expectedTargetBuildingId);
            }
        }
        return commitAddition(analyzeBuildingAddition(source), selectedType);
    }

    public Outcome scanRoom(BlockPos source, int expectedRoomId, String selectedType) {
        Village village = manager.findNearestVillage(source, Village.MERGE_MARGIN).orElse(null);
        if (village == null) {
            return Outcome.failed(Building.validationResult.NOT_IN_BUILDING, source, expectedRoomId);
        }
        RoomScanPlanner.Analysis analysis = RoomScanPlanner.analyze(village, world, source);
        RoomScanPlan plan = analysis.plan();
        if (analysis.result() != Building.validationResult.SUCCESS) {
            return Outcome.failed(analysis.result(), source, expectedRoomId);
        }
        if (expectedRoomId >= 0 && plan.mode() != Village.RoomScanMode.UPDATE_ROOM) {
            return Outcome.failed(Building.validationResult.NOT_IN_BUILDING, source, expectedRoomId);
        }
        if (expectedRoomId < 0 && plan.mode() == Village.RoomScanMode.UPDATE_ROOM) {
            return Outcome.failed(Building.validationResult.IDENTICAL, source, expectedRoomId);
        }
        return switch (plan.mode()) {
            case ADD_ROOM -> withExpectedTargetId(
                    commitAddition(analyzeRoom(village, analysis), selectedType), -1);
            case UPDATE_ROOM -> updateRoom(
                    village, plan.currentRoom().orElse(null), source, expectedRoomId, selectedType);
            default -> Outcome.failed(Building.validationResult.NOT_IN_BUILDING, source, expectedRoomId);
        };
    }

    BuildingScanResult analyzeBuildingAddition(BlockPos source) {
        return analyzeBuildingAddition(source, false);
    }

    BuildingScanResult analyzeReportedBuildingAddition(BlockPos source) {
        return analyzeBuildingAddition(source, true);
    }

    private BuildingScanResult analyzeBuildingAddition(BlockPos source, boolean reportedSource) {
        Village village = manager.findNearestVillage(source, Village.MERGE_MARGIN).orElse(null);
        Collection<Structure> existing = village == null ? List.of() : village.getStructures().values();
        if (village != null && village.getInteractionStructureAt(source).isPresent()) {
            return failedRoom(Building.validationResult.IDENTICAL, source, village);
        }

        StructureScanner.Result structureScan = reportedSource
                ? StructureScanner.scanReportedStructure(world, source, existing)
                : StructureScanner.scanNewStructure(world, source, existing);
        if (structureScan.result() != Building.validationResult.SUCCESS) {
            return failedRoom(structureScan.result(), source, village);
        }

        Structure candidate = structureScan.toStructure(-1);
        StructureFloor floor = candidate.getFloors().getFirst();
        SelectedFloorScanner.Result floorScan = structureScan.scan();
        List<RoomPartitioner.Component> components = BuildingRoomScanner.components(world, floorScan);
        BuildingRoomScanner.Result selectedGeometry = BuildingRoomScanner.materializeSelected(
                structureScan.source(), Config.getInstance().maxBuildingSize, floor.id(),
                floorScan.floor(), components);
        BuildingScanResult selected = roomResultFromGeometry(
                village, candidate, floor, selectedGeometry);
        if (selected.result() != Building.validationResult.SUCCESS) return selected;
        return selected.withSource(source).withPendingStructure(candidate);
    }

    BuildingScanResult analyzeRoom(BlockPos source) {
        Village village = manager.findNearestVillage(source, Village.MERGE_MARGIN).orElse(null);
        if (village == null) return failedRoom(Building.validationResult.NOT_IN_BUILDING, source, null);
        RoomScanPlanner.Analysis analysis = RoomScanPlanner.analyze(village, world, source);
        return analyzeRoom(village, analysis);
    }

    private BuildingScanResult analyzeRoom(Village village, RoomScanPlanner.Analysis analysis) {
        RoomScanPlan plan = analysis.plan();
        BlockPos source = plan.interactionSource();
        if (analysis.result() != Building.validationResult.SUCCESS) {
            return failedRoom(analysis.result(), source, village);
        }
        if (plan.mode() != Village.RoomScanMode.ADD_ROOM
                || plan.targetStructureId() < 0 || plan.targetFloorId() < 0) {
            return failedRoom(plan.mode() == Village.RoomScanMode.UPDATE_ROOM
                    ? Building.validationResult.IDENTICAL
                    : Building.validationResult.NOT_IN_BUILDING, source, village);
        }

        Structure structure = village.getStructure(plan.targetStructureId()).orElse(null);
        StructureFloor floor = structure == null
                ? null : structure.getFloor(plan.targetFloorId()).orElse(null);
        if (structure == null || floor == null) {
            return failedRoom(Building.validationResult.NOT_IN_BUILDING, source, village);
        }
        BlockPos scanSeed = plan.scanSeed();
        StructureScanner.Result fresh = analysis.observation() == null
                ? StructureScanner.scanExistingFloor(world, structure, floor, scanSeed, village.getStructures().values())
                : StructureScanner.resultFromObservedFloor(scanSeed, analysis.observation().scan(),
                        village.getStructures().values(), structure.getId(), structure.getLogicalBuildingId());
        if (fresh.result() != Building.validationResult.SUCCESS) {
            return failedRoom(fresh.result(), source, village);
        }

        Structure refreshed = refreshedStructure(structure, floor.id(), fresh.scannedFloor());
        if (refreshed == null) {
            return failedRoom(Building.validationResult.OVERLAP, source, village);
        }
        StructureFloor refreshedFloor = refreshed.getFloor(floor.id()).orElse(null);
        if (refreshedFloor == null) {
            return failedRoom(Building.validationResult.OVERLAP, source, village);
        }
        List<RoomPartitioner.Component> components = analysis.observation() == null
                ? BuildingRoomScanner.components(world, fresh.scan()) : analysis.components();
        BuildingRoomScanner.Result selected = BuildingRoomScanner.materializeSelected(
                scanSeed, Config.getInstance().maxBuildingSize, floor.id(),
                fresh.scannedFloor(), components);
        BuildingScanResult addition = roomResultFromGeometry(
                village, refreshed, refreshedFloor, selected);
        if (addition.result() != Building.validationResult.SUCCESS) {
            return addition.withSource(source);
        }

        List<Building> existingRooms = village.getRooms()
                .filter(room -> room.getStructureId() == structure.getId())
                .filter(room -> room.getFloorId() == floor.id())
                .toList();
        if (hasRegisteredRoomConflict(
                fresh.scannedFloor(), addition.building(), existingRooms)) {
            return failedRoom(Building.validationResult.OVERLAP, source, village);
        }
        return addition.withSource(source).withPendingStructure(refreshed);
    }

    BuildingScanResult analyzeAttachedRoom(BlockPos source,
                                           int expectedTargetBuildingId) {
        Village village = manager.findNearestVillage(source, Village.MERGE_MARGIN).orElse(null);
        if (village == null) return failedRoom(Building.validationResult.NOT_IN_BUILDING, source, null);
        RoomScanPlanner.Analysis analysis = RoomScanPlanner.analyze(village, world, source);
        return analyzeAttachedRoom(village, analysis, expectedTargetBuildingId);
    }

    BuildingScanResult analyzeAttachedRoom(Village village,
                                           RoomScanPlan plan,
                                           int expectedTargetBuildingId) {
        BlockPos source = plan == null ? BlockPos.ZERO : plan.interactionSource();
        if (village == null || plan == null) {
            return failedRoom(Building.validationResult.NOT_IN_BUILDING, source, village);
        }
        RoomScanPlanner.Analysis fresh = RoomScanPlanner.analyze(village, world, source);
        RoomScanPlan current = fresh.plan();
        if (plan.mode() != current.mode() || plan.targetBuildingId() != current.targetBuildingId()
                || plan.prospectiveFloorNumber() != current.prospectiveFloorNumber()
                || plan.selectedAttachmentFloor() == null || current.selectedAttachmentFloor() == null
                || !plan.selectedAttachmentFloor().geometry().sameCellPositions(current.selectedAttachmentFloor().geometry())) {
            return failedRoom(Building.validationResult.NOT_IN_BUILDING, source, village);
        }
        return analyzeAttachedRoom(village, fresh, expectedTargetBuildingId);
    }

    private BuildingScanResult analyzeAttachedRoom(Village village,
                                                   RoomScanPlanner.Analysis analysis,
                                                   int expectedTargetBuildingId) {
        RoomScanPlan plan = analysis.plan();
        BlockPos source = plan.interactionSource();
        if (analysis.result() != Building.validationResult.SUCCESS) {
            return failedRoom(analysis.result(), source, village);
        }
        if (plan.mode() != Village.RoomScanMode.ADD_ATTACHMENT || plan.targetBuildingId() < 0
                || (expectedTargetBuildingId >= 0
                && plan.targetBuildingId() != expectedTargetBuildingId)) {
            return failedRoom(Building.validationResult.NOT_IN_BUILDING, source, village);
        }

        StructureScanner.Result structureScan = resolvePlannedAttachmentScan(village, analysis);
        if (structureScan.result() != Building.validationResult.SUCCESS) {
            return failedRoom(structureScan.result(), source, village);
        }

        if (structureScan.scannedFloor() == null) {
            return failedRoom(Building.validationResult.AMBIGUOUS_STRUCTURE, source, village);
        }

        Structure candidate = structureScan.toStructure(-1, plan.prospectiveFloorNumber());
        StructureFloor attachmentFloor = candidate.getFloor(0).orElse(null);
        if (attachmentFloor == null) {
            return failedRoom(Building.validationResult.NOT_IN_BUILDING, source, village);
        }

        candidate.setLogicalBuildingId(plan.targetBuildingId());
        List<RoomPartitioner.Component> components = structureScan.scannedFloor()
                .sameCellPositions(analysis.observation().scan().floor())
                ? analysis.components() : BuildingRoomScanner.components(world, structureScan.scan());
        BuildingRoomScanner.Result geometry = BuildingRoomScanner.materializeSelected(
                plan.scanSeed(), Config.getInstance().maxBuildingSize,
                attachmentFloor.id(), structureScan.scannedFloor(), components);
        return roomResultFromGeometry(village, candidate, attachmentFloor, geometry)
                .withSource(source)
                .withPendingStructure(candidate);
    }

    private StructureScanner.Result resolvePlannedAttachmentScan(Village village, RoomScanPlanner.Analysis analysis) {
        RoomScanPlan plan = analysis.plan();
        StructureFloor plannedFloor = plan.selectedAttachmentFloor();
        if (plannedFloor == null) {
            return StructureScanner.Result.failure(
                    Building.validationResult.NOT_IN_BUILDING, plan.interactionSource());
        }

        Collection<Structure> existing = village.getStructures().values();
        StructureScanner.FloorObservation observation = analysis.observation();
        if (observation == null) {
            return StructureScanner.Result.failure(
                    Building.validationResult.NOT_IN_BUILDING, plan.interactionSource());
        }

        // Planning and materialization consume the same one selected-Floor observation.
        if (!observation.scan().floor().sameCellPositions(plannedFloor.geometry())) {
            return StructureScanner.Result.failure(
                    Building.validationResult.NOT_IN_BUILDING, plan.interactionSource());
        }
        return StructureScanner.resultFromObservedFloor(
                plan.scanSeed(), observation.scan(), existing, -1, plan.targetBuildingId());
    }

    RegisteredRoomUpdate analyzeRegisteredRoomUpdate(Village village, int roomId, BlockPos source) {
        Building expected = village == null ? null : village.getBuilding(roomId).orElse(null);
        if (expected == null || !expected.isFunctionalRoom()) {
            return RegisteredRoomUpdate.failure(Building.validationResult.NOT_IN_BUILDING, source, village);
        }
        Structure structure = village.getStructureFor(expected).orElse(null);
        if (structure == null) {
            return RegisteredRoomUpdate.failure(Building.validationResult.NOT_IN_BUILDING, source, village);
        }
        return analyzeRegisteredFloor(village, structure, expected, source);
    }

    private RegisteredRoomUpdate analyzeRegisteredFloor(Village village,
                                                        Structure structure,
                                                        Building expected,
                                                        BlockPos source) {
        StructureFloor persistedFloor = structure.getFloor(expected.getFloorId()).orElse(null);
        if (persistedFloor == null) {
            return RegisteredRoomUpdate.failure(Building.validationResult.OVERLAP, source, village);
        }

        StructureScanner.Result fresh = StructureScanner.scanExistingFloor(
                world, structure, persistedFloor, source, village.getStructures().values());
        if (fresh.result() != Building.validationResult.SUCCESS) {
            return RegisteredRoomUpdate.failure(fresh.result(), source, village);
        }

        List<RoomPartitioner.Component> components = BuildingRoomScanner.components(world, fresh.scan());
        RoomPartitioner.Component selectedComponent = RoomPartitioner.select(
                fresh.source(), fresh.scannedFloor(), components);
        if (selectedComponent == null) {
            return RegisteredRoomUpdate.failure(Building.validationResult.TOO_SMALL, source, village);
        }

        Structure refreshed = refreshedStructure(structure, persistedFloor.id(), fresh.scannedFloor());
        StructureFloor refreshedFloor = refreshed == null
                ? null : refreshed.getFloor(persistedFloor.id()).orElse(null);
        if (refreshedFloor == null) {
            return RegisteredRoomUpdate.failure(Building.validationResult.OVERLAP, source, village);
        }

        BuildingRoomScanner.Result geometry = BuildingRoomScanner.materialize(
                fresh.source(), Config.getInstance().maxBuildingSize, persistedFloor.id(),
                fresh.scannedFloor(), components, selectedComponent);
        BuildingScanResult selected = materializeRoom(village, refreshed, refreshedFloor, geometry);
        if (selected.result() != Building.validationResult.SUCCESS) {
            return RegisteredRoomUpdate.failure(selected.result(), source, village);
        }

        Building replacement = selected.building();
        if (fresh.scannedFloor().roomIdentityOverlapCount(
                expected.getFloorCells(), replacement.getFloorCells()) == 0) {
            return RegisteredRoomUpdate.failure(Building.validationResult.OVERLAP, source, village);
        }

        List<Building> otherRooms = village.getRooms()
                .filter(room -> room.getStructureId() == structure.getId())
                .filter(room -> room.getFloorId() == persistedFloor.id())
                .filter(room -> room.getId() != expected.getId())
                .toList();
        if (hasRegisteredRoomConflict(fresh.scannedFloor(), replacement, otherRooms)) {
            return RegisteredRoomUpdate.failure(Building.validationResult.OVERLAP, source, village);
        }

        replacement.setId(expected.getId());
        replacement.setStructureId(expected.getStructureId());
        replacement.setFloorId(expected.getFloorId());
        replacement.setType(expected.getType());
        replacement.setTypeForced(expected.isTypeForced());
        replacement.setContributesToMain(expected.contributesToMain());
        List<String> matchingTypes = village.getMatchingRoomTypes(replacement).stream()
                .map(BuildingType::name)
                .toList();
        return new RegisteredRoomUpdate(Building.validationResult.SUCCESS, source, village,
                refreshed, expected.getId(), replacement, matchingTypes);
    }

    private static boolean hasRegisteredRoomConflict(
            FloorGeometry floor, Building candidate, Collection<Building> registeredRooms) {
        return registeredRooms.stream().anyMatch(room ->
                floor.roomIdentityOverlapCount(room.getFloorCells(), candidate.getFloorCells()) > 0)
                || registeredRooms.stream().flatMap(room -> room.getFloorCells().stream())
                .anyMatch(cell -> floor.cellAt(cell).isEmpty());
    }

    private static Structure refreshedStructure(Structure structure,
                                                int floorId,
                                                FloorGeometry floor) {
        Structure refreshed = structure.copy();
        return refreshed.replaceFloorGeometry(floorId, floor) ? refreshed : null;
    }

    private BuildingScanResult roomResultFromGeometry(Village village,
                                                      Structure structure,
                                                      StructureFloor floor,
                                                      BuildingRoomScanner.Result geometry) {
        BuildingScanResult materialized = materializeRoom(
                village, structure, floor, geometry);
        if (materialized.result() != Building.validationResult.SUCCESS) return materialized;

        Building room = materialized.building();
        List<String> types = village == null
                ? room.getVisibleMatchingTypes().stream().map(BuildingType::name).toList()
                : village.getMatchingRoomTypes(room).stream().map(BuildingType::name).toList();
        return new BuildingScanResult(Building.validationResult.SUCCESS, room.getSourceBlock(), room,
                types, village);
    }

    private BuildingScanResult materializeRoom(Village village,
                                               Structure structure,
                                               StructureFloor floor,
                                               BuildingRoomScanner.Result geometry) {
        Building room = new Building(geometry.seed());
        Building.validationResult result = room.applyRoomScan(world, geometry);
        if (result != Building.validationResult.SUCCESS) return failedRoom(result, geometry.seed(), village);
        room.setStructureId(structure.getId());
        room.setFloorId(floor.id());
        return new BuildingScanResult(Building.validationResult.SUCCESS, room.getSourceBlock(), room,
                List.of(), village);
    }

    private static BuildingScanResult failedRoom(
            Building.validationResult result, BlockPos source, Village village) {
        return new BuildingScanResult(result, source, new Building(source), List.of(), village);
    }

    public Outcome addAttachment(BlockPos source, int expectedBuildingId, String selectedType) {
        if (expectedBuildingId < 0) {
            return Outcome.failed(Building.validationResult.NOT_IN_BUILDING, source, expectedBuildingId);
        }
        Village village = manager.findNearestVillage(source, Village.MERGE_MARGIN).orElse(null);
        if (village == null) {
            return Outcome.failed(Building.validationResult.NOT_IN_BUILDING, source, expectedBuildingId);
        }
        RoomScanPlanner.Analysis analysis = RoomScanPlanner.analyze(village, world, source);
        Outcome outcome = commitAddition(
                analyzeAttachedRoom(village, analysis, expectedBuildingId), selectedType);
        return withProspectiveFloorNumber(outcome, analysis.plan().prospectiveFloorNumber());
    }

    private Outcome updateRoom(Village village,
                               Building room,
                               BlockPos source,
                               int expectedRoomId,
                               String selectedType) {
        if (room == null || expectedRoomId >= 0 && room.getId() != expectedRoomId) {
            return Outcome.failed(Building.validationResult.NOT_IN_BUILDING, source, expectedRoomId);
        }

        RegisteredRoomUpdate update = analyzeRegisteredRoomUpdate(village, room.getId(), source);
        if (update.result() != Building.validationResult.SUCCESS) {
            return Outcome.failed(update.result(), source, room.getId());
        }
        if (selectedType == null && update.requiresTypeSelection()) {
            return Outcome.requiresTypeSelection(
                    update.source(), update.matchingTypes(), room.getId());
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
        RoomInheritanceUpdate update = RoomInheritanceUpdate.analyze(village, room, enabled);
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

    Outcome commitAddition(BuildingScanResult scan, String selectedType) {
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

    private static Outcome withExpectedTargetId(Outcome outcome, int expectedTargetId) {
        return new Outcome(outcome.status(), outcome.result(), outcome.source(),
                outcome.matchingTypes(), expectedTargetId, outcome.prospectiveFloorNumber());
    }

    private static Outcome withProspectiveFloorNumber(Outcome outcome, int prospectiveFloorNumber) {
        return new Outcome(outcome.status(), outcome.result(), outcome.source(),
                outcome.matchingTypes(), outcome.expectedTargetId(), prospectiveFloorNumber);
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
                          int expectedTargetId,
                          int prospectiveFloorNumber) {
        public Outcome {
            source = source == null ? BlockPos.ZERO : source.immutable();
            matchingTypes = matchingTypes == null ? List.of() : List.copyOf(matchingTypes);
        }

        static Outcome committed(BlockPos source, int expectedTargetId) {
            return new Outcome(Status.COMMITTED, Building.validationResult.SUCCESS,
                    source, List.of(), expectedTargetId, Integer.MIN_VALUE);
        }

        static Outcome requiresTypeSelection(BlockPos source,
                                             List<String> matchingTypes,
                                             int expectedTargetId) {
            return new Outcome(Status.REQUIRES_TYPE_SELECTION, Building.validationResult.SUCCESS,
                    source, matchingTypes, expectedTargetId, Integer.MIN_VALUE);
        }

        static Outcome failed(Building.validationResult result, BlockPos source, int expectedTargetId) {
            return new Outcome(Status.FAILED, result, source, List.of(), expectedTargetId, Integer.MIN_VALUE);
        }
    }
}
