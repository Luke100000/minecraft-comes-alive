package net.conczin.mca.server.world.data;

import net.conczin.mca.Config;
import net.conczin.mca.resources.data.BuildingType;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;

import java.util.*;

/** Shared room scan planning and server-side structure changes. */
public final class RoomWorkflow {
    static final int MAX_FLOOR_GROUP_HEIGHT_SPAN = 2;

    private final VillageManager manager;
    private final ServerLevel world;

    public RoomWorkflow(VillageManager manager, ServerLevel world) {
        this.manager = manager;
        this.world = world;
    }

    public static Village findTargetVillage(VillageManager manager, BlockPos source) {
        return manager.findNearestVillage(source, Village.MERGE_MARGIN).orElse(null);
    }

    public Outcome addBuilding(BlockPos source, String selectedType) {
        return addBuilding(source, -1, selectedType);
    }

    public Outcome addBuilding(BlockPos source, int expectedTargetBuildingId, String selectedType) {
        SelectedFloorScanner.Observation operation = StructureScanner.newObservation(world);
        Village village = findTargetVillage(manager, source);
        if (village != null) {
            Analysis analysis = analyze(village, world, source, operation);
            Plan plan = analysis.plan();
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
                        materializeAttachedRoom(village, analysis, expectedTargetBuildingId), selectedType);
                return withProspectiveFloorNumber(outcome, plan.prospectiveFloorNumber());
            }
            if (expectedTargetBuildingId >= 0) {
                return Outcome.failed(
                        Building.validationResult.NOT_IN_BUILDING, source, expectedTargetBuildingId);
            }
            if (plan.mode() == Village.RoomScanMode.ADD_ROOM) {
                return commitAddition(analyzeRoom(village, analysis), selectedType);
            }
            if (plan.mode() == Village.RoomScanMode.UPDATE_ROOM) {
                return Outcome.failed(Building.validationResult.IDENTICAL, source, -1);
            }
            if (plan.mode() == Village.RoomScanMode.ADD_BUILDING && analysis.observation() != null
                    && operation.selected(source) == analysis.observation().scan()
                    && village.getInteractionStructureAt(source).isEmpty()) {
                StructureScanner.Result exact = StructureScanner.resultFromObservedFloor(
                        source, analysis.observation().scan(), village.getStructures().values(), -1, -1);
                if (exact.result() == Building.validationResult.SUCCESS) {
                    return commitAddition(prepareAddition(village, plan, exact, analysis.components()), selectedType);
                }
            }
        }
        return commitAddition(analyzeBuildingAddition(source, false, operation), selectedType);
    }

    public Outcome scanRoom(BlockPos source, int expectedRoomId, String selectedType) {
        Village village = findTargetVillage(manager, source);
        if (village == null) {
            return Outcome.failed(expectedRoomId >= 0 ? Building.validationResult.TARGET_CHANGED
                    : Building.validationResult.NOT_IN_BUILDING, source, expectedRoomId);
        }
        Analysis analysis = analyze(village, world, source);
        Plan plan = analysis.plan();
        if (expectedRoomId >= 0 && (plan.mode() != Village.RoomScanMode.UPDATE_ROOM
                || plan.currentRoom().map(Building::getId).orElse(-1) != expectedRoomId)) {
            return Outcome.failed(Building.validationResult.TARGET_CHANGED, source, expectedRoomId);
        }
        if (analysis.result() != Building.validationResult.SUCCESS) {
            return Outcome.failed(analysis.result(), source, expectedRoomId);
        }
        if (expectedRoomId < 0 && plan.mode() == Village.RoomScanMode.UPDATE_ROOM) {
            return Outcome.failed(Building.validationResult.IDENTICAL, source, expectedRoomId);
        }
        return switch (plan.mode()) {
            case ADD_ROOM -> commitAddition(analyzeRoom(village, analysis), selectedType);
            case UPDATE_ROOM -> updateRoom(village, analysis, selectedType);
            default -> Outcome.failed(Building.validationResult.NOT_IN_BUILDING, source, expectedRoomId);
        };
    }

    public Outcome addAttachment(BlockPos source, int expectedBuildingId, String selectedType) {
        if (expectedBuildingId < 0) {
            return Outcome.failed(Building.validationResult.NOT_IN_BUILDING, source, expectedBuildingId);
        }
        Village village = findTargetVillage(manager, source);
        if (village == null) {
            return Outcome.failed(Building.validationResult.NOT_IN_BUILDING, source, expectedBuildingId);
        }
        Analysis analysis = analyze(village, world, source);
        Outcome outcome = commitAddition(
                materializeAttachedRoom(village, analysis, expectedBuildingId), selectedType);
        return withProspectiveFloorNumber(outcome, analysis.plan().prospectiveFloorNumber());
    }

    public Outcome updateInheritance(BlockPos source,
                                     int expectedRoomId,
                                     boolean enabled,
                                     String selectedType) {
        if (world == null) {
            return Outcome.failed(expectedRoomId >= 0 ? Building.validationResult.TARGET_CHANGED
                    : Building.validationResult.NOT_IN_BUILDING, source, expectedRoomId);
        }
        Village village = findTargetVillage(manager, source);
        Building room = village == null ? null : village.resolveInteractionPosition(world, source)
                .map(Village.ResolvedInteraction::position)
                .map(Structure.InteractionPosition::room)
                .orElse(null);
        if (room == null || (expectedRoomId >= 0 && room.getId() != expectedRoomId)) {
            return Outcome.failed(expectedRoomId >= 0 ? Building.validationResult.TARGET_CHANGED
                    : Building.validationResult.NOT_IN_BUILDING, source, expectedRoomId);
        }

        boolean mainRoom = village.isMainRoom(room);
        boolean previousEnabled = mainRoom
                ? village.isBuildingInheritanceEnabled(room)
                : room.contributesToMain();
        if (previousEnabled == enabled && selectedType == null) {
            return Outcome.committed(source, room.getId());
        }
        List<String> matchingTypes = enabled ? List.of() : village.getMatchingRoomTypes(room).stream()
                .map(BuildingType::name)
                .toList();
        if (previousEnabled && !enabled && selectedType == null
                && RoomTypeResolver.requiresTypeChoice(matchingTypes)) {
            return Outcome.requiresTypeSelection(source, matchingTypes, room.getId());
        }
        return committed(village.updateRoomInheritance(room, enabled, selectedType), source, room.getId());
    }

    Addition analyzeBuildingAddition(BlockPos source) {
        return analyzeBuildingAddition(source, false, StructureScanner.newObservation(world));
    }

    Addition analyzeReportedBuildingAddition(BlockPos source) {
        return analyzeBuildingAddition(source, true, null);
    }

    private Addition analyzeBuildingAddition(BlockPos source, boolean reportedSource,
                                             SelectedFloorScanner.Observation operation) {
        Village village = findTargetVillage(manager, source);
        Collection<Structure> existing = village == null ? List.of() : village.getStructures().values();
        if (village != null && village.getInteractionStructureAt(source).isPresent()) {
            return failedAddition(Building.validationResult.IDENTICAL, source, village);
        }

        StructureScanner.Result structureScan = reportedSource
                ? StructureScanner.scanReportedStructure(world, source, existing)
                : StructureScanner.scanNewStructure(world, source, existing, operation);
        return prepareAddition(village, Plan.addBuilding(source), structureScan, null);
    }

    Addition analyzeRoom(BlockPos source) {
        Village village = findTargetVillage(manager, source);
        if (village == null) return failedAddition(Building.validationResult.NOT_IN_BUILDING, source, null);
        Analysis analysis = analyze(village, world, source);
        return analyzeRoom(village, analysis);
    }

    private Addition analyzeRoom(Village village, Analysis analysis) {
        Plan plan = analysis.plan();
        BlockPos source = plan.interactionSource();
        if (analysis.result() != Building.validationResult.SUCCESS) {
            return failedAddition(analysis.result(), source, village);
        }
        if (plan.mode() != Village.RoomScanMode.ADD_ROOM) {
            return failedAddition(plan.mode() == Village.RoomScanMode.UPDATE_ROOM
                    ? Building.validationResult.IDENTICAL
                    : Building.validationResult.NOT_IN_BUILDING, source, village);
        }

        Structure structure = village.getStructure(plan.targetStructureId()).orElse(null);
        StructureFloor floor = structure == null
                ? null : structure.getFloor(plan.targetFloorId()).orElse(null);
        if (structure == null || floor == null) {
            return failedAddition(Building.validationResult.NOT_IN_BUILDING, source, village);
        }
        BlockPos scanSeed = plan.scanSeed();
        StructureScanner.Result fresh = analysis.observation() == null
                ? StructureScanner.scanExistingFloor(world, structure, floor, scanSeed, village.getStructures().values())
                : StructureScanner.resultFromObservedFloor(scanSeed, analysis.observation().scan(),
                        village.getStructures().values(), structure.getId(), structure.getLogicalBuildingId());
        return prepareAddition(village, plan, fresh,
                analysis.observation() == null ? null : analysis.components());
    }

    Addition analyzeAttachedRoom(BlockPos source, int expectedTargetBuildingId) {
        Village village = findTargetVillage(manager, source);
        if (village == null) return failedAddition(Building.validationResult.NOT_IN_BUILDING, source, null);
        Analysis analysis = analyze(village, world, source);
        return materializeAttachedRoom(village, analysis, expectedTargetBuildingId);
    }

    Addition analyzeAttachedRoom(Village village, Analysis captured, int expectedTargetBuildingId) {
        Plan plan = captured == null ? null : captured.plan();
        BlockPos source = plan == null ? BlockPos.ZERO : plan.interactionSource();
        if (village == null || plan == null) {
            return failedAddition(Building.validationResult.NOT_IN_BUILDING, source, village);
        }
        Analysis fresh = analyze(village, world, source);
        Plan current = fresh.plan();
        if (plan.mode() != current.mode() || plan.targetBuildingId() != current.targetBuildingId()
                || plan.prospectiveFloorNumber() != current.prospectiveFloorNumber()
                || captured.observation() == null || fresh.observation() == null
                || !captured.observation().scan().floor()
                        .sameCellPositions(fresh.observation().scan().floor())) {
            return failedAddition(Building.validationResult.NOT_IN_BUILDING, source, village);
        }
        return materializeAttachedRoom(village, fresh, expectedTargetBuildingId);
    }

    private Addition materializeAttachedRoom(Village village,
                                             Analysis analysis,
                                             int expectedTargetBuildingId) {
        Plan plan = analysis.plan();
        BlockPos source = plan.interactionSource();
        if (analysis.result() != Building.validationResult.SUCCESS) {
            return failedAddition(analysis.result(), source, village);
        }
        if (plan.mode() != Village.RoomScanMode.ADD_ATTACHMENT
                || (expectedTargetBuildingId >= 0
                && plan.targetBuildingId() != expectedTargetBuildingId)) {
            return failedAddition(Building.validationResult.NOT_IN_BUILDING, source, village);
        }

        if (analysis.observation() == null) return failedAddition(Building.validationResult.NOT_IN_BUILDING, source, village);
        StructureScanner.Result structureScan = StructureScanner.resultFromObservedFloor(
                plan.scanSeed(), analysis.observation().scan(), village.getStructures().values(), -1, plan.targetBuildingId());
        return prepareAddition(village, plan, structureScan, analysis.components());
    }

    Addition prepareAddition(Village village, Plan plan, StructureScanner.Result scan,
                             List<RoomPartitioner.Component> components) {
        BlockPos source = plan.interactionSource();
        if (scan.result() != Building.validationResult.SUCCESS) return failedAddition(scan.result(), source, village);
        Structure prepared;
        int floorId = 0;
        if (plan.mode() == Village.RoomScanMode.ADD_ROOM) {
            Structure current = village == null ? null : village.getStructure(plan.targetStructureId()).orElse(null);
            if (current == null || current.getFloor(plan.targetFloorId()).isEmpty()) {
                return failedAddition(Building.validationResult.NOT_IN_BUILDING, source, village);
            }
            floorId = plan.targetFloorId();
            prepared = refreshedStructure(current, floorId, scan.scannedFloor());
            if (prepared == null) return failedAddition(Building.validationResult.OVERLAP, source, village);
        } else if (plan.mode() == Village.RoomScanMode.ADD_BUILDING
                || plan.mode() == Village.RoomScanMode.ADD_ATTACHMENT) {
            prepared = scan.toStructure(-1, plan.mode() == Village.RoomScanMode.ADD_ATTACHMENT
                    ? plan.prospectiveFloorNumber() : 0);
            if (plan.mode() == Village.RoomScanMode.ADD_ATTACHMENT) {
                prepared.setLogicalBuildingId(plan.targetBuildingId());
            }
        } else {
            return failedAddition(Building.validationResult.NOT_IN_BUILDING, source, village);
        }
        if (components == null) components = StructureScanner.components(world, scan.scan());
        BlockPos selected = plan.mode() == Village.RoomScanMode.ADD_BUILDING ? scan.source() : plan.scanSeed();
        RoomPartitioner.Component component = RoomPartitioner.select(selected, scan.scannedFloor(), components);
        if (component == null) {
            return failedAddition(Building.validationResult.TOO_SMALL, source, village);
        }
        RoomPoiEvidence.Index evidence = RoomPoiEvidence.index(components);
        Addition addition = materializeRoom(village, prepared, prepared.getFloor(floorId).orElseThrow(),
                selected, evidence, component, plan);
        if (addition.result() == Building.validationResult.SUCCESS && plan.mode() == Village.RoomScanMode.ADD_ROOM) {
            List<Building> registered = village.getRooms()
                    .filter(room -> room.getStructureId() == plan.targetStructureId() && room.getFloorId() == plan.targetFloorId())
                    .toList();
            if (hasRegisteredRoomConflict(scan.scannedFloor(), addition.building(), registered)) {
                return failedAddition(Building.validationResult.OVERLAP, source, village);
            }
        }
        return addition;
    }

    RegisteredFloorUpdate analyzeRegisteredFloorUpdate(Village village, int structureId, int floorId) {
        Structure structure = village == null ? null : village.getStructure(structureId).orElse(null);
        StructureFloor floor = structure == null ? null : structure.getFloor(floorId).orElse(null);
        if (floor == null) {
            return RegisteredFloorUpdate.failure(Building.validationResult.NOT_IN_BUILDING, structureId, floorId);
        }
        List<Building> rooms = village.getRooms()
                .filter(room -> room.getStructureId() == structureId && room.getFloorId() == floorId)
                .sorted(Comparator.comparingInt(Building::getId)).toList();
        if (rooms.isEmpty()) {
            return RegisteredFloorUpdate.failure(Building.validationResult.NOT_IN_BUILDING, structureId, floorId);
        }
        StructureScanner.Result fresh = StructureScanner.scanExistingFloor(world, structure, floor,
                rooms.getFirst().getSourceBlock(), village.getStructures().values());
        if (fresh.result() != Building.validationResult.SUCCESS) {
            return RegisteredFloorUpdate.failure(fresh.result(), structureId, floorId);
        }
        List<RoomPartitioner.Component> components = StructureScanner.components(world, fresh.scan());
        Structure refreshed = refreshedStructure(structure, floorId, fresh.scannedFloor());
        if (refreshed == null) {
            return RegisteredFloorUpdate.failure(Building.validationResult.OVERLAP, structureId, floorId);
        }
        StructureFloor refreshedFloor = refreshed.getFloor(floorId).orElseThrow();
        RoomPoiEvidence.Index evidence = RoomPoiEvidence.index(components);
        boolean[] matched = new boolean[components.size()];
        List<Building> replacements = new ArrayList<>(rooms.size());
        for (Building room : rooms) {
            int match = -1;
            for (int i = 0; i < components.size(); i++) {
                if (fresh.scannedFloor().roomIdentityOverlapCount(
                        room.getFloorCells(), components.get(i).floorCells()) == 0) continue;
                if (match >= 0 || matched[i]) {
                    return RegisteredFloorUpdate.failure(Building.validationResult.OVERLAP, structureId, floorId);
                }
                match = i;
            }
            if (match < 0) {
                return RegisteredFloorUpdate.failure(Building.validationResult.OVERLAP, structureId, floorId);
            }
            matched[match] = true;
            Addition selected = materializeRegisteredRoom(village, refreshed, refreshedFloor,
                    Plan.updateRoom(room, room.getSourceBlock()), evidence, components.get(match));
            if (selected.result() != Building.validationResult.SUCCESS) {
                return RegisteredFloorUpdate.failure(selected.result(), structureId, floorId);
            }
            replacements.add(selected.building());
        }
        List<Building> prospective = village.getRooms()
                .filter(room -> room.getStructureId() != structureId || room.getFloorId() != floorId)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        prospective.addAll(replacements);
        RoomTypeResolver resolver = RoomTypeResolver.create(village, prospective);
        for (Building replacement : replacements) {
            if (replacement.isTypeForced()) continue;
            String type = resolver.resolve(replacement).updatedType(null);
            if (type == null) {
                return RegisteredFloorUpdate.failure(Building.validationResult.INVALID_TYPE, structureId, floorId);
            }
            replacement.setType(type);
        }
        return new RegisteredFloorUpdate(Building.validationResult.SUCCESS, structureId, floorId,
                refreshed, replacements);
    }

    RoomUpdate analyzeRegisteredRoomUpdate(Village village, int roomId, BlockPos source) {
        return analyzeRegisteredRoomUpdate(village, roomId, source, null);
    }

    RoomUpdate analyzeRegisteredRoomUpdate(Village village, int roomId, BlockPos source,
                                           StructureScanner.Result observed) {
        Building expected = village == null ? null : village.getBuilding(roomId).orElse(null);
        if (expected == null || !expected.isFunctionalRoom()) {
            return RoomUpdate.failure(Building.validationResult.NOT_IN_BUILDING, source, village);
        }
        Structure structure = village.getStructureFor(expected).orElse(null);
        if (structure == null) {
            return RoomUpdate.failure(Building.validationResult.NOT_IN_BUILDING, source, village);
        }
        return analyzeRegisteredFloor(village, structure, Plan.updateRoom(expected, source,
                observed == null ? source : observed.source()), observed, null);
    }

    RoomUpdate analyzePlannedRoomUpdate(Village village, Analysis analysis) {
        Plan plan = analysis.plan();
        if (analysis.observation() == null) {
            return analyzeRegisteredRoomUpdate(village, plan.currentRoom().orElseThrow().getId(), plan.interactionSource());
        }
        Structure structure = village.getStructure(plan.targetStructureId()).orElse(null);
        if (structure == null) {
            return RoomUpdate.failure(Building.validationResult.NOT_IN_BUILDING, plan.interactionSource(), village);
        }
        StructureScanner.Result observed = StructureScanner.resultFromObservedFloor(
                plan.scanSeed(), analysis.observation().scan(), village.getStructures().values(),
                structure.getId(), structure.getLogicalBuildingId());
        return analyzeRegisteredFloor(village, structure, plan, observed, analysis.components());
    }

    private RoomUpdate analyzeRegisteredFloor(Village village,
                                              Structure structure,
                                              Plan plan,
                                              StructureScanner.Result observed,
                                              List<RoomPartitioner.Component> observedComponents) {
        Building expected = plan.currentRoom().orElseThrow();
        BlockPos source = plan.interactionSource();
        StructureFloor persistedFloor = structure.getFloor(plan.targetFloorId()).orElse(null);
        if (persistedFloor == null) {
            return RoomUpdate.failure(Building.validationResult.OVERLAP, source, village);
        }

        StructureScanner.Result fresh = observed == null ? StructureScanner.scanExistingFloor(
                world, structure, persistedFloor, plan.scanSeed(), village.getStructures().values()) : observed;
        if (fresh.result() != Building.validationResult.SUCCESS) {
            return RoomUpdate.failure(fresh.result(), source, village);
        }

        List<RoomPartitioner.Component> components = observedComponents == null
                ? StructureScanner.components(world, fresh.scan()) : observedComponents;
        RoomPartitioner.Component selectedComponent = RoomPartitioner.select(
                fresh.source(), fresh.scannedFloor(), components);
        if (selectedComponent == null) {
            return RoomUpdate.failure(Building.validationResult.TOO_SMALL, source, village);
        }

        Structure refreshed = refreshedStructure(structure, persistedFloor.id(), fresh.scannedFloor());
        StructureFloor refreshedFloor = refreshed == null
                ? null : refreshed.getFloor(persistedFloor.id()).orElse(null);
        if (refreshedFloor == null) {
            return RoomUpdate.failure(Building.validationResult.OVERLAP, source, village);
        }

        Addition selected = materializeRegisteredRoom(village, refreshed, refreshedFloor, plan,
                RoomPoiEvidence.index(components), selectedComponent);
        if (selected.result() != Building.validationResult.SUCCESS) {
            return RoomUpdate.failure(selected.result(), source, village);
        }

        Building replacement = selected.building();
        if (fresh.scannedFloor().roomIdentityOverlapCount(
                expected.getFloorCells(), replacement.getFloorCells()) == 0) {
            return RoomUpdate.failure(Building.validationResult.OVERLAP, source, village);
        }

        List<Building> otherRooms = village.getRooms()
                .filter(room -> room.getStructureId() == structure.getId())
                .filter(room -> room.getFloorId() == persistedFloor.id())
                .filter(room -> room.getId() != expected.getId())
                .toList();
        if (hasRegisteredRoomConflict(fresh.scannedFloor(), replacement, otherRooms)) {
            return RoomUpdate.failure(Building.validationResult.OVERLAP, source, village);
        }

        List<String> matchingTypes = village.getMatchingRoomTypes(replacement).stream()
                .map(BuildingType::name)
                .toList();
        return new RoomUpdate(Building.validationResult.SUCCESS, source, village,
                refreshed, expected.getId(), replacement, matchingTypes);
    }

    private Addition materializeRegisteredRoom(Village village, Structure refreshed, StructureFloor floor,
                                               Plan plan, RoomPoiEvidence.Index evidence,
                                               RoomPartitioner.Component component) {
        Building original = plan.currentRoom().orElseThrow();
        Addition selected = materializeRoom(village, refreshed, floor, plan.scanSeed(), evidence, component, plan);
        if (selected.result() != Building.validationResult.SUCCESS) return selected;
        Building replacement = selected.building();
        replacement.setId(original.getId());
        replacement.setType(original.getType());
        replacement.setTypeForced(original.isTypeForced());
        replacement.setContributesToMain(original.contributesToMain());
        return selected;
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

    private Addition materializeRoom(Village village,
                                     Structure structure,
                                     StructureFloor floor,
                                     BlockPos source,
                                     RoomPoiEvidence.Index evidence,
                                     RoomPartitioner.Component component,
                                     Plan plan) {
        Building room = new Building(component.nearestCell(source));
        Building.validationResult result = room.applyRoomComponent(
                world, source, Config.getInstance().maxBuildingSize, evidence, component);
        if (result != Building.validationResult.SUCCESS) {
            return failedAddition(result, plan.interactionSource(), village);
        }
        room.setStructureId(structure.getId());
        room.setFloorId(floor.id());
        if (plan.mode() == Village.RoomScanMode.UPDATE_ROOM) {
            return new Addition(result, room.getSourceBlock(), room, List.of(), village, null, plan);
        }
        List<String> types = village == null
                ? room.getVisibleMatchingTypes().stream().map(BuildingType::name).toList()
                : village.getMatchingRoomTypes(room).stream().map(BuildingType::name).toList();
        return new Addition(result, plan.interactionSource(), room, types, village, structure, plan);
    }

    private static Addition failedAddition(
            Building.validationResult result, BlockPos source, Village village) {
        return new Addition(result, source, new Building(source), List.of(), village, null, Plan.addBuilding(source));
    }

    private Outcome updateRoom(Village village, Analysis analysis, String selectedType) {
        Plan plan = analysis.plan();
        int roomId = plan.currentRoom().orElseThrow().getId();
        RoomUpdate update = analyzePlannedRoomUpdate(village, analysis);
        if (update.result() != Building.validationResult.SUCCESS) {
            return Outcome.failed(update.result(), plan.interactionSource(), roomId);
        }
        if (selectedType == null && update.requiresTypeSelection()) {
            return Outcome.requiresTypeSelection(update.source(), update.matchingTypes(), roomId);
        }
        return committed(manager.commitRegisteredRoomUpdate(update, selectedType), update.source(), roomId);
    }

    Outcome commitAddition(Addition scan, String selectedType) {
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

    private static Outcome committed(Building.validationResult result, BlockPos source, int expectedTargetId) {
        return result == Building.validationResult.SUCCESS
                ? Outcome.committed(source, expectedTargetId)
                : Outcome.failed(result, source, expectedTargetId);
    }

    private static Outcome withProspectiveFloorNumber(Outcome outcome, int prospectiveFloorNumber) {
        return new Outcome(outcome.status(), outcome.result(), outcome.source(),
                outcome.matchingTypes(), outcome.expectedTargetId(), prospectiveFloorNumber);
    }

    // --- Scan planning & floor-number grouping (inlined from RoomScanPlanner / FloorGrouping) ---

    static Plan plan(Village village, Level level, BlockPos pos) {
        return analyze(village, level, pos).plan();
    }

    public static Optional<ScanPreview> preview(Village village, Level level, BlockPos pos) {
        Analysis analysis = analyze(village, level, pos);
        if (analysis.result() != Building.validationResult.SUCCESS) {
            return Optional.empty();
        }
        Plan plan = analysis.plan();
        return switch (plan.mode()) {
            case ADD_BUILDING -> Optional.empty();
            case ADD_ROOM -> Optional.of(new ScanPreview(plan.mode(), -1, 0));
            case UPDATE_ROOM -> Optional.of(new ScanPreview(plan.mode(), plan.currentRoom().orElseThrow().getId(), 0));
            case ADD_ATTACHMENT -> Optional.of(new ScanPreview(
                    plan.mode(), plan.targetBuildingId(), plan.prospectiveFloorNumber()));
        };
    }

    public record ScanPreview(Village.RoomScanMode mode, int expectedTargetId, int floorNumber) {
    }

    static Analysis analyze(Village village, Level level, BlockPos pos) {
        return analyze(village, level, pos, null);
    }

    static Analysis analyze(Village village, Level level, BlockPos pos,
                            SelectedFloorScanner.Observation operation) {
        BlockPos source = pos == null ? BlockPos.ZERO : pos.immutable();
        if (village == null || pos == null) return new Analysis(Plan.addBuilding(source));

        Village.ResolvedInteraction resolved = village.resolveInteractionPosition(level, pos).orElse(null);
        Plan persistedFloorPlan = null;
        if (resolved != null) {
            Building room = resolved.position().room();
            if (room != null) return new Analysis(Plan.updateRoom(room, source));
            persistedFloorPlan = Plan.addRoom(
                    resolved.structure().getId(), resolved.position().floor().id(), source);
        }

        if (level == null) {
            return new Analysis(persistedFloorPlan == null ? Plan.addBuilding(source) : persistedFloorPlan);
        }
        StructureScanner.FloorObservation observation = StructureScanner.observeFloor(
                level, source, village.getStructures().values(),
                operation == null ? StructureScanner.newObservation(level) : operation).orElse(null);
        if (observation == null) {
            return new Analysis(persistedFloorPlan == null ? Plan.addBuilding(source) : persistedFloorPlan);
        }
        Analysis fresh = analyzeFresh(village, level, source, observation);
        List<RoomPartitioner.Component> components = fresh.components();
        Plan freshPlan = fresh.plan();
        Building verticalExitRoom = resolveVerticalExitRoom(village, level, source, observation, components, freshPlan);
        if (verticalExitRoom != null) {
            return new Analysis(Plan.updateRoom(verticalExitRoom, source, observation.seed()),
                    observation, components);
        }
        if (persistedFloorPlan == null) {
            return fresh;
        }
        if (freshPlan.mode() == Village.RoomScanMode.ADD_ROOM
                && freshPlan.targetStructureId() == persistedFloorPlan.targetStructureId()
                && freshPlan.targetFloorId() == persistedFloorPlan.targetFloorId()) {
            return fresh;
        }
        return new Analysis(persistedFloorPlan);
    }

    private static Building resolveVerticalExitRoom(Village village,
                                                    Level level,
                                                    BlockPos source,
                                                    StructureScanner.FloorObservation observation,
                                                    List<RoomPartitioner.Component> components,
                                                    Plan plan) {
        FloorGeometry floor = observation.scan().floor();
        FloorGeometry.Cell exitCell = floor.interactionCellAt(source.getX(), source.getY(), source.getZ())
                .orElse(null);
        if (plan.mode() != Village.RoomScanMode.ADD_ROOM || exitCell == null
                || !StructureConnector.isVerticalTopExit(level, source)) {
            return null;
        }
        RoomPartitioner.Component selected = RoomPartitioner.select(exitCell.feet(), floor, components);
        if (selected == null) return null;
        List<Building> owners = village.getRooms()
                .filter(room -> room.getStructureId() == plan.targetStructureId()
                        && room.getFloorId() == plan.targetFloorId())
                .filter(room -> floor.roomIdentityOverlapCount(room.getFloorCells(), selected.floorCells()) > 0)
                .limit(2)
                .toList();
        return owners.size() == 1 ? owners.getFirst() : null;
    }

    private static Analysis buildAnalysis(Village village, Plan plan,
                                          StructureScanner.FloorObservation observation,
                                          List<RoomPartitioner.Component> components) {
        Building.validationResult result = plan.mode() == Village.RoomScanMode.ADD_ATTACHMENT
                && !plan.hasProspectiveFloor()
                ? prospectiveFloorNumber(village, plan.targetBuildingId(), new StructureFloor(
                        0, 0, observation.scan().anchorY(), observation.scan().floor())).result()
                : Building.validationResult.SUCCESS;
        return new Analysis(plan, observation, components, result);
    }

    static Analysis analyzeFresh(Village village, BlockGetter world, BlockPos source,
                                 StructureScanner.FloorObservation observation) {
        List<RoomPartitioner.Component> components = observation == null ? List.of()
                : StructureScanner.components(world, observation.scan());
        return buildAnalysis(village, planFresh(village, source, observation, components), observation, components);
    }

    private static Plan planFresh(Village village,
                                  BlockPos source,
                                  StructureScanner.FloorObservation observation,
                                  List<RoomPartitioner.Component> components) {
        if (village == null || observation == null) return Plan.addBuilding(source);

        Plan attachment = attachmentPlan(village, source, observation).orElse(null);
        if (attachment != null) return attachment;

        FloorTarget expansion = selectSameFloorTarget(village, observation).orElse(null);
        if (expansion != null && validExpansion(village, observation, expansion)) {
            RoomPartitioner.Component selected = observation.scan().floor() == null || observation.seed() == null
                    ? null : RoomPartitioner.select(observation.seed(), observation.scan().floor(), components);
            if (selected != null) {
                SelectedFloorScanner.Result scan = observation.scan();
                BlockPos supportedSource = scan.supportedSource();
                boolean directSelection = source.equals(supportedSource) || source.above().equals(supportedSource);
                List<Building> owners = village.getRooms()
                        .filter(room -> room.getStructureId() == expansion.structureId()
                                && room.getFloorId() == expansion.floorId())
                        .filter(room -> room.ownsFloorCell(source)
                                || directSelection && room.ownsFloorCell(scan.seed()))
                        .limit(2)
                        .toList();
                if (owners.size() == 1) {
                    return Plan.updateRoom(owners.getFirst(), source, scan.seed());
                }
                if (!directSelection && scan.floor().interactionCellAt(
                        source.getX(), source.getY(), source.getZ()).isEmpty()) {
                    return Plan.addBuilding(source);
                }
                return Plan.addRoom(
                        expansion.structureId(), expansion.floorId(), source, observation.seed());
            }
        }

        return Plan.addBuilding(source);
    }

    private static Optional<Plan> attachmentPlan(Village village,
                                                 BlockPos source,
                                                 StructureScanner.FloorObservation observation) {
        if (village == null || observation == null) return Optional.empty();
        StructureFloor candidateFloor = new StructureFloor(
                0, 0, observation.scan().anchorY(), observation.scan().floor());
        Village.AttachmentTarget target = village.selectAttachmentTarget(
                candidateFloor, observation.explicitConnections(),
                observation.liveContacts()).orElse(null);
        if (target == null) return Optional.empty();

        NumberDecision number = prospectiveFloorNumber(village, target.buildingId(), candidateFloor);
        return Optional.of(Plan.attachment(
                target.buildingId(), number.number().orElse(Integer.MIN_VALUE), source, observation.seed()));
    }

    static NumberDecision prospectiveFloorNumber(Village village, int buildingId,
                                                 StructureFloor candidate) {
        List<StructureFloor> floors = village.getBuildingStructures(buildingId).stream()
                .flatMap(structure -> structure.getFloors().stream()).toList();
        Building main = village.getLogicalBuilding(buildingId)
                .flatMap(logical -> village.getBuilding(logical.mainRoomId())).orElse(null);
        StructureFloor ground = main == null ? null : village.getStructure(main.getStructureId())
                .flatMap(structure -> structure.getFloor(main.getFloorId())).orElse(null);
        return prospectiveFloorNumber(floors, ground, candidate.anchorY(), candidate);
    }

    static NumberDecision prospectiveFloorNumber(Collection<StructureFloor> registered,
                                                 StructureFloor ground,
                                                 int candidateY) {
        return prospectiveFloorNumber(registered, ground, candidateY, null);
    }

    static NumberDecision prospectiveFloorNumber(Collection<StructureFloor> registered,
                                                 StructureFloor ground,
                                                 StructureFloor candidate) {
        return prospectiveFloorNumber(registered, ground, candidate.anchorY(), candidate);
    }

    private static NumberDecision prospectiveFloorNumber(Collection<StructureFloor> registered,
                                                         StructureFloor ground,
                                                         int candidateY,
                                                         StructureFloor candidateFloor) {
        if (ground == null || !registered.contains(ground)) {
            return new NumberDecision(Building.validationResult.NOT_IN_BUILDING, OptionalInt.empty(), Map.of());
        }
        if (ground.floorNumber() != 0) {
            return new NumberDecision(Building.validationResult.AMBIGUOUS_STRUCTURE, OptionalInt.empty(), Map.of());
        }
        record Band(int minY, int maxY) {
            boolean contains(int height) {
                return height >= minY && height <= maxY;
            }
        }
        Map<Integer, Band> groups = new HashMap<>();
        for (StructureFloor floor : registered) {
            groups.merge(floor.floorNumber(), new Band(floor.anchorY(), floor.anchorY()),
                    (first, second) -> new Band(Math.min(first.minY(), second.minY()),
                            Math.max(first.maxY(), second.maxY())));
        }
        List<Integer> nearbyLabels = registered.stream()
                .filter(floor -> Math.abs((long) floor.anchorY() - candidateY) <= MAX_FLOOR_GROUP_HEIGHT_SPAN)
                .filter(floor -> candidateFloor == null || floor.anchorY() == candidateY
                        || !floor.overlapsFootprint(candidateFloor))
                .map(StructureFloor::floorNumber).distinct().toList();
        List<Integer> labels = nearbyLabels.stream().filter(number -> {
            Band group = groups.get(number);
            return (long) Math.max(group.maxY(), candidateY) - Math.min(group.minY(), candidateY)
                    <= MAX_FLOOR_GROUP_HEIGHT_SPAN;
        }).toList();
        for (int number : nearbyLabels) {
            if (number == Integer.MIN_VALUE || groups.get(number).contains(candidateY) && !labels.contains(number)) {
                return new NumberDecision(Building.validationResult.AMBIGUOUS_STRUCTURE, OptionalInt.empty(), Map.of());
            }
        }
        if (labels.size() > 1) {
            return new NumberDecision(Building.validationResult.AMBIGUOUS_STRUCTURE, OptionalInt.empty(), Map.of());
        }
        if (labels.size() == 1) {
            return new NumberDecision(Building.validationResult.SUCCESS, OptionalInt.of(labels.getFirst()), Map.of());
        }

        OptionalInt lowerY = registered.stream().mapToInt(StructureFloor::anchorY)
                .filter(height -> height < candidateY).max();
        OptionalInt upperY = registered.stream().mapToInt(StructureFloor::anchorY)
                .filter(height -> height > candidateY).min();
        List<Integer> lowerLabels = lowerY.isEmpty() ? List.of() : registered.stream()
                .filter(floor -> floor.anchorY() == lowerY.getAsInt()).map(StructureFloor::floorNumber).distinct().toList();
        List<Integer> upperLabels = upperY.isEmpty() ? List.of() : registered.stream()
                .filter(floor -> floor.anchorY() == upperY.getAsInt()).map(StructureFloor::floorNumber).distinct().toList();
        if (lowerLabels.size() > 1 || upperLabels.size() > 1) {
            return new NumberDecision(Building.validationResult.AMBIGUOUS_STRUCTURE, OptionalInt.empty(), Map.of());
        }
        Integer below = lowerLabels.isEmpty() ? null : lowerLabels.getFirst();
        Integer above = upperLabels.isEmpty() ? null : upperLabels.getFirst();
        long proposed = candidateY > ground.anchorY()
                ? below == null ? Long.MAX_VALUE : (long) below + 1
                : above == null ? Long.MIN_VALUE : (long) above - 1;
        if (proposed <= Integer.MIN_VALUE || proposed > Integer.MAX_VALUE
                || below != null && proposed < below || above != null && proposed > above
                || candidateY > ground.anchorY() && proposed <= 0
                || candidateY < ground.anchorY() && proposed >= 0) {
            return new NumberDecision(Building.validationResult.AMBIGUOUS_STRUCTURE, OptionalInt.empty(), Map.of());
        }
        Map<Integer, Integer> shiftedNumbers = new HashMap<>();
        if (groups.containsKey((int) proposed)) {
            int direction = proposed > 0 ? 1 : -1;
            for (int number : groups.keySet()) {
                if (direction > 0 ? number < proposed : number > proposed) continue;
                long shifted = (long) number + direction;
                if (shifted <= Integer.MIN_VALUE || shifted > Integer.MAX_VALUE) {
                    return new NumberDecision(Building.validationResult.AMBIGUOUS_STRUCTURE, OptionalInt.empty(), Map.of());
                }
                shiftedNumbers.put(number, (int) shifted);
            }
        }
        return new NumberDecision(Building.validationResult.SUCCESS, OptionalInt.of((int) proposed), shiftedNumbers);
    }

    private static boolean validExpansion(Village village,
                                          StructureScanner.FloorObservation observation,
                                          FloorTarget target) {
        Structure structure = village.getStructure(target.structureId()).orElseThrow();
        return StructureScanner.validateObservation(
                observation, village.getStructures().values(), target.structureId(), structure.getLogicalBuildingId())
                == Building.validationResult.SUCCESS;
    }

    private static Optional<FloorTarget> selectSameFloorTarget(Village village,
                                                               StructureScanner.FloorObservation observation) {
        if (village == null || observation == null || observation.scan().floor() == null) return Optional.empty();
        StructureFloor freshFloor = new StructureFloor(
                0, 0, observation.scan().anchorY(), observation.scan().floor());
        List<FloorTarget> exact = new ArrayList<>(2);
        List<FloorTarget> nearby = new ArrayList<>(2);
        for (Structure structure : village.getStructures().values()) {
            for (StructureFloor floor : structure.getFloors()) {
                FloorTarget target = new FloorTarget(structure.getId(), floor.id());
                boolean sharesCells = freshFloor.geometry().cells().stream().anyMatch(cell ->
                        freshFloor.geometry().isRoomIdentityCell(cell.feet())
                                && floor.geometry().isRoomIdentityCell(cell.feet())
                                && floor.geometry().cellAt(cell.feet()).isPresent());
                if (sharesCells) {
                    exact.add(target);
                    if (exact.size() > 1) return Optional.empty();
                } else if (nearby.size() < 2 && floor.overlapsNearbyFloorBand(freshFloor)) {
                    nearby.add(target);
                }
            }
        }
        List<FloorTarget> matches = exact.isEmpty() ? nearby : exact;
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private record FloorTarget(int structureId, int floorId) {
    }

    record Plan(Village.RoomScanMode mode,
                Optional<Building> currentRoom,
                int targetBuildingId,
                int targetStructureId,
                int targetFloorId,
                int prospectiveFloorNumber,
                BlockPos interactionSource,
                BlockPos scanSeed) {
        Plan {
            interactionSource = interactionSource == null ? BlockPos.ZERO : interactionSource.immutable();
            scanSeed = scanSeed == null ? interactionSource : scanSeed.immutable();
        }

        static Plan addBuilding(BlockPos source) {
            return new Plan(Village.RoomScanMode.ADD_BUILDING, Optional.empty(),
                    -1, -1, -1, 0, source, source);
        }

        static Plan addRoom(int structureId, int floorId, BlockPos source) {
            return addRoom(structureId, floorId, source, source);
        }

        static Plan addRoom(int structureId, int floorId, BlockPos source, BlockPos scanSeed) {
            return new Plan(Village.RoomScanMode.ADD_ROOM, Optional.empty(),
                    -1, structureId, floorId, Integer.MIN_VALUE, source, scanSeed);
        }

        static Plan updateRoom(Building room, BlockPos source) {
            return updateRoom(room, source, source);
        }

        static Plan updateRoom(Building room, BlockPos source, BlockPos scanSeed) {
            return new Plan(Village.RoomScanMode.UPDATE_ROOM, Optional.of(room),
                    -1, room.getStructureId(), room.getFloorId(), Integer.MIN_VALUE, source, scanSeed);
        }

        static Plan attachment(int buildingId, int prospectiveFloorNumber, BlockPos source, BlockPos scanSeed) {
            return new Plan(Village.RoomScanMode.ADD_ATTACHMENT, Optional.empty(),
                    buildingId, -1, -1, prospectiveFloorNumber, source, scanSeed);
        }

        boolean hasProspectiveFloor() {
            return prospectiveFloorNumber != Integer.MIN_VALUE;
        }
    }

    record Analysis(Plan plan,
                    StructureScanner.FloorObservation observation,
                    List<RoomPartitioner.Component> components,
                    Building.validationResult result) {
        Analysis {
            components = List.copyOf(components);
        }

        Analysis(Plan plan, StructureScanner.FloorObservation observation,
                 List<RoomPartitioner.Component> components) {
            this(plan, observation, components, Building.validationResult.SUCCESS);
        }

        Analysis(Plan plan) {
            this(plan, null, List.of(), Building.validationResult.SUCCESS);
        }
    }

    record Addition(Building.validationResult result,
                    BlockPos source,
                    Building building,
                    List<String> matchingTypes,
                    Village village,
                    Structure pendingStructure,
                    Plan plan) {
        Addition {
            Objects.requireNonNull(plan, "plan");
            source = source == null ? BlockPos.ZERO : source.immutable();
            matchingTypes = matchingTypes == null ? List.of() : List.copyOf(matchingTypes);
        }

        boolean isAmbiguous() {
            return RoomTypeResolver.requiresTypeChoice(matchingTypes);
        }

        boolean matchesType(String type) {
            return RoomTypeResolver.matchesTypeChoice(matchingTypes, type);
        }

        int targetBuildingId() {
            return plan != null && plan.mode() == Village.RoomScanMode.ADD_ATTACHMENT
                    ? plan.targetBuildingId() : -1;
        }
    }

    record RoomUpdate(Building.validationResult result,
                      BlockPos source,
                      Village village,
                      Structure refreshedStructure,
                      int expectedRoomId,
                      Building replacementRoom,
                      List<String> matchingTypes) {
        RoomUpdate {
            source = source == null ? BlockPos.ZERO : source.immutable();
            matchingTypes = matchingTypes == null ? List.of() : List.copyOf(matchingTypes);
        }

        static RoomUpdate failure(Building.validationResult result, BlockPos source, Village village) {
            return new RoomUpdate(result, source, village, null, -1, null, List.of());
        }

        boolean requiresTypeSelection() {
            Building current = village == null ? null : village.getBuilding(expectedRoomId).orElse(null);
            return current != null && !current.isTypeForced()
                    && RoomTypeResolver.requiresTypeChoice(matchingTypes);
        }

        boolean matchesType(String type) {
            return RoomTypeResolver.matchesTypeChoice(matchingTypes, type);
        }
    }

    record RegisteredFloorUpdate(Building.validationResult result, int structureId, int floorId,
                                 Structure refreshedStructure, List<Building> replacementRooms) {
        RegisteredFloorUpdate {
            if (result != Building.validationResult.SUCCESS) {
                refreshedStructure = null;
                replacementRooms = List.of();
            } else {
                replacementRooms = List.copyOf(replacementRooms);
            }
        }

        static RegisteredFloorUpdate failure(Building.validationResult result, int structureId, int floorId) {
            return new RegisteredFloorUpdate(result, structureId, floorId, null, List.of());
        }
    }

    record NumberDecision(Building.validationResult result, OptionalInt number,
                          Map<Integer, Integer> shiftedNumbers) {
        NumberDecision {
            shiftedNumbers = Map.copyOf(shiftedNumbers);
            if ((result == Building.validationResult.SUCCESS) != number.isPresent()) {
                throw new IllegalArgumentException("A successful floor number decision needs a number");
            }
        }
    }

    public enum Status {
        COMMITTED,
        REQUIRES_TYPE_SELECTION,
        FAILED
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
