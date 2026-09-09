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

    public Outcome addRoom(BlockPos source, String selectedType) {
        return commitAddition(analyzeRoom(source), selectedType);
    }

    public Outcome addBuilding(BlockPos source, String selectedType) {
        return commitAddition(analyzeBuildingAddition(source), selectedType);
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
        return scanResolvedRoom(village, candidate, structureScan.source(), -1,
                floor, structureScan.scannedFloor(), structureScan.transitions(), Set.of())
                .withPendingStructure(candidate);
    }

    BuildingScanResult analyzeRoom(BlockPos source) {
        Village village = manager.findNearestVillage(source, Village.MERGE_MARGIN).orElse(null);
        if (village == null) return failedRoom(Building.validationResult.NOT_IN_BUILDING, source, null);
        RoomScanPlan plan = village.getRoomScanPlan(world, source);
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
        StructureScanner.Result fresh = StructureScanner.scanExistingFloor(
                world, structure, floor, source, village.getStructures().values());
        if (fresh.result() != Building.validationResult.SUCCESS) {
            return failedRoom(fresh.result(), source, village);
        }

        Structure refreshed = refreshedStructure(structure, floor.id(), fresh.floor());
        if (refreshed == null) {
            return failedRoom(Building.validationResult.OVERLAP, source, village);
        }
        StructureFloor refreshedFloor = refreshed.getFloor(floor.id()).orElse(null);
        if (refreshedFloor == null) {
            return failedRoom(Building.validationResult.OVERLAP, source, village);
        }
        BuildingScanResult addition = scanResolvedRoom(
                village, refreshed, source, -1, refreshedFloor, fresh.scannedFloor(),
                fresh.transitions(), registeredRoomIdentityCells(
                        village, structure.getId(), floor.id(), fresh.scannedFloor(), -1));
        if (addition.result() != Building.validationResult.SUCCESS) {
            return addition.withSource(source);
        }

        List<Building> freshRooms = new ArrayList<>();
        for (BuildingRoomScanner.Result component : BuildingRoomScanner.partition(
                world, source, Config.getInstance().maxBuildingSize,
                floor.id(), fresh.scannedFloor(), fresh.transitions())) {
            BuildingScanResult componentScan = roomResultFromGeometry(
                    village, refreshed, refreshedFloor, component, -1);
            if (componentScan.result() != Building.validationResult.SUCCESS) {
                return failedRoom(Building.validationResult.OVERLAP, source, village);
            }
            freshRooms.add(componentScan.building());
        }
        List<Building> previousRooms = village.getRooms()
                .filter(room -> room.getStructureId() == structure.getId())
                .filter(room -> room.getFloorId() == floor.id())
                .toList();
        return RegisteredRoomReconciler.reconcileAddition(
                        previousRooms, freshRooms, addition.building(), fresh.scannedFloor())
                .map(existingReplacements -> addition.withSource(source)
                        .withPendingStructure(refreshed)
                        .withPendingFloorRooms(existingReplacements))
                .orElseGet(() -> failedRoom(Building.validationResult.OVERLAP, source, village));
    }

    BuildingScanResult analyzeAttachedRoom(BlockPos source,
                                           Village.RoomScanMode requestedMode,
                                           int expectedTargetBuildingId) {
        if (requestedMode != Village.RoomScanMode.ADD_FLOOR
                && requestedMode != Village.RoomScanMode.ADD_BASEMENT) {
            return failedRoom(Building.validationResult.NOT_IN_BUILDING, source, null);
        }

        Village village = manager.findNearestVillage(source, Village.MERGE_MARGIN).orElse(null);
        if (village == null) return failedRoom(Building.validationResult.NOT_IN_BUILDING, source, null);
        RoomScanPlan plan = village.getRoomScanPlan(world, source);
        return analyzeAttachedRoom(village, plan, requestedMode, expectedTargetBuildingId);
    }

    BuildingScanResult analyzeAttachedRoom(Village village,
                                           RoomScanPlan plan,
                                           Village.RoomScanMode requestedMode,
                                           int expectedTargetBuildingId) {
        BlockPos source = plan == null ? BlockPos.ZERO : plan.interactionSource();
        if (village == null || plan == null) {
            return failedRoom(Building.validationResult.NOT_IN_BUILDING, source, village);
        }
        if (plan.mode() != requestedMode || plan.targetBuildingId() < 0
                || (expectedTargetBuildingId >= 0
                && plan.targetBuildingId() != expectedTargetBuildingId)) {
            return failedRoom(Building.validationResult.NOT_IN_BUILDING, source, village);
        }

        StructureScanner.Result structureScan = StructureScanner.scanPlannedStructure(
                world, plan, village.getStructures().values());
        if (structureScan.result() != Building.validationResult.SUCCESS) {
            return failedRoom(structureScan.result(), source, village);
        }

        StructureFloor scannedFloor = structureScan.floor();
        if (scannedFloor == null) {
            return failedRoom(Building.validationResult.AMBIGUOUS_STRUCTURE, source, village);
        }

        Structure candidate = structureScan.toStructure(-1);
        StructureFloor attachmentFloor = candidate.getFloor(scannedFloor.id()).orElse(null);
        if (attachmentFloor == null || !validAttachment(
                village, candidate, attachmentFloor, structureScan.connectedFloors(),
                plan.targetBuildingId(), requestedMode)) {
            return failedRoom(Building.validationResult.NOT_IN_BUILDING, source, village);
        }

        candidate.setLogicalBuildingId(plan.targetBuildingId());
        return scanResolvedRoom(village, candidate, plan.scanSeed(), -1,
                attachmentFloor, structureScan.scannedFloor(), structureScan.transitions(), Set.of())
                .withSource(source)
                .withPendingStructure(candidate);
    }

    private boolean validAttachment(Village village,
                                    Structure candidate,
                                    StructureFloor playerFloor,
                                    Collection<FloorGeometry> connectedFloors,
                                    int targetBuildingId,
                                    Village.RoomScanMode requestedMode) {
        Village.AttachmentTarget resolved = village.resolveAttachmentTarget(
                world, playerFloor, connectedFloors).orElse(null);
        if (resolved == null || resolved.buildingId() != targetBuildingId) return false;

        int floorNumber = village.prospectiveFloorNumber(targetBuildingId, candidate, playerFloor);
        return requestedMode == Village.RoomScanMode.ADD_BASEMENT
                ? floorNumber < 0
                : floorNumber != Integer.MIN_VALUE && floorNumber >= 0;
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

        List<Building> freshComponents = BuildingRoomScanner.partition(
                        world, source, Config.getInstance().maxBuildingSize,
                        persistedFloor.id(), fresh.scannedFloor(), fresh.transitions()).stream()
                .map(geometry -> roomResultFromGeometry(
                        village, structure, persistedFloor, geometry, -1))
                .filter(scan -> scan.result() == Building.validationResult.SUCCESS)
                .map(BuildingScanResult::building)
                .toList();
        if (freshComponents.isEmpty()) {
            return RegisteredRoomUpdate.failure(Building.validationResult.TOO_SMALL, source, village);
        }

        List<Building> otherRooms = village.getRooms()
                .filter(room -> room.getStructureId() == structure.getId())
                .filter(room -> room.getFloorId() == persistedFloor.id())
                .filter(room -> room.getId() != expected.getId())
                .toList();

        List<Building> lineage = RegisteredRoomReconciler
                .updateLineage(expected, freshComponents, otherRooms).orElse(null);
        if (lineage == null || lineage.isEmpty()) {
            return RegisteredRoomUpdate.failure(Building.validationResult.OVERLAP, source, village);
        }

        int mainRoomId = village.getMainRoom(structure).map(Building::getId).orElse(-1);
        RegisteredRoomReconciler.Result reconciled = RegisteredRoomReconciler.reconcile(
                source, expected.getId(), mainRoomId, List.of(expected), lineage).orElse(null);
        if (reconciled == null) {
            return RegisteredRoomUpdate.failure(Building.validationResult.OVERLAP, source, village);
        }

        Structure refreshed = refreshedStructure(structure, persistedFloor.id(), fresh.floor());
        if (refreshed == null) {
            return RegisteredRoomUpdate.failure(Building.validationResult.OVERLAP, source, village);
        }

        Building playerComponent = reconciled.playerComponent();
        List<String> matchingTypes = village.getMatchingRoomTypes(playerComponent).stream()
                .map(BuildingType::name)
                .toList();
        return new RegisteredRoomUpdate(Building.validationResult.SUCCESS, source, village,
                refreshed, structure.getId(), persistedFloor.id(), expected.getId(),
                reconciled.previousRoomIds(), reconciled.assignments(),
                playerComponent, matchingTypes);
    }

    private static Structure refreshedStructure(Structure structure,
                                                int floorId,
                                                StructureFloor floor) {
        Structure refreshed = structure.copy();
        return refreshed.replaceFloorGeometry(floorId, floor) ? refreshed : null;
    }

    private static Set<BlockPos> registeredRoomIdentityCells(Village village,
                                                             int structureId,
                                                             int floorId,
                                                             FloorGeometry floor,
                                                             int excludedRoomId) {
        if (village == null || floor == null) return Set.of();
        return village.getRooms()
                .filter(room -> room.getId() != excludedRoomId)
                .filter(room -> room.getStructureId() == structureId)
                .filter(room -> room.getFloorId() == floorId)
                .flatMap(room -> room.getFloorCells().stream())
                .filter(cell -> {
                    FloorConnector.Type connector = floor.connectorTypesByCell().get(cell);
                    return connector == null || !connector.roomBoundary();
                })
                .collect(java.util.stream.Collectors.toSet());
    }

    private BuildingScanResult scanResolvedRoom(Village village,
                                                Structure structure,
                                                BlockPos source,
                                                 int existingRoomId,
                                                 StructureFloor floor,
                                                 FloorGeometry scannedFloor,
                                                 Collection<SelectedFloorScanner.Transition> transitions,
                                                 Set<BlockPos> blocked) {
        BuildingRoomScanner.Result geometry = BuildingRoomScanner.scan(
                world, source, blocked, Config.getInstance().maxBuildingSize,
                floor.id(), scannedFloor, transitions);
        return roomResultFromGeometry(village, structure, floor, geometry, existingRoomId);
    }

    private BuildingScanResult roomResultFromGeometry(Village village,
                                                      Structure structure,
                                                      StructureFloor floor,
                                                      BuildingRoomScanner.Result geometry,
                                                      int existingRoomId) {
        Building room = new Building(geometry.seed());
        Building.validationResult result = room.applyRoomScan(world, geometry);
        if (result != Building.validationResult.SUCCESS) return failedRoom(result, geometry.seed(), village);
        room.setStructureId(structure.getId());
        room.setFloorId(floor.id());
        if (existingRoomId >= 0) room.setId(existingRoomId);

        List<String> types = village == null
                ? room.getVisibleMatchingTypes().stream().map(BuildingType::name).toList()
                : village.getMatchingRoomTypes(room).stream().map(BuildingType::name).toList();
        return new BuildingScanResult(Building.validationResult.SUCCESS, room.getSourceBlock(), room,
                types, village);
    }

    private static BuildingScanResult failedRoom(
            Building.validationResult result, BlockPos source, Village village) {
        return new BuildingScanResult(result, source, new Building(source), List.of(), village);
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
        RegisteredRoomUpdate update = analyzeRegisteredRoomUpdate(resolved.village(), room.getId(), source);
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

    private Outcome addAttachedRoom(BlockPos source,
                                    int expectedBuildingId,
                                    String selectedType,
                                    Village.RoomScanMode mode) {
        if (expectedBuildingId < 0) {
            return Outcome.failed(Building.validationResult.NOT_IN_BUILDING, source, expectedBuildingId);
        }
        return commitAddition(analyzeAttachedRoom(source, mode, expectedBuildingId), selectedType);
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
