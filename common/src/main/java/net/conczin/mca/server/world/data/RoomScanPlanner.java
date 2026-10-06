package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Captures fresh Floor evidence once and plans the requested Room operation from it. */
final class RoomScanPlanner {
    private RoomScanPlanner() {
    }

    static RoomScanPlan plan(Village village, Level level, BlockPos pos) {
        return analyze(village, level, pos).plan();
    }

    static Analysis analyze(Village village, Level level, BlockPos pos) {
        BlockPos source = pos == null ? BlockPos.ZERO : pos.immutable();
        if (village == null || pos == null) return new Analysis(RoomScanPlan.addBuilding(source));

        Village.ResolvedInteraction resolved = village.resolveInteractionPosition(level, pos).orElse(null);
        RoomScanPlan persistedFloorPlan = null;
        if (resolved != null) {
            Building room = resolved.position().room();
            if (room != null) return new Analysis(RoomScanPlan.updateRoom(room, source));
            persistedFloorPlan = RoomScanPlan.addRoom(
                    resolved.structure().getId(), resolved.position().floor().id(), source);
        }

        if (level == null) {
            return new Analysis(persistedFloorPlan == null ? RoomScanPlan.addBuilding(source) : persistedFloorPlan);
        }
        StructureScanner.FloorObservation observation = StructureScanner.observeFloor(
                level, source, village.getStructures().values()).orElse(null);
        if (observation == null) {
            return new Analysis(persistedFloorPlan == null ? RoomScanPlan.addBuilding(source) : persistedFloorPlan);
        }
        List<RoomPartitioner.Component> components = BuildingRoomScanner.components(level, observation.scan());
        RoomScanPlan freshPlan = planFresh(village, source, observation, components);
        Building verticalExitRoom = resolveVerticalExitRoom(village, level, source, observation, components, freshPlan);
        if (verticalExitRoom != null) {
            return new Analysis(RoomScanPlan.updateRoom(verticalExitRoom, source, observation.seed()),
                    observation, components);
        }
        if (persistedFloorPlan == null) {
            return analysis(village, freshPlan, observation, components);
        }
        if (freshPlan.mode() == Village.RoomScanMode.ADD_ROOM
                && freshPlan.targetStructureId() == persistedFloorPlan.targetStructureId()
                && freshPlan.targetFloorId() == persistedFloorPlan.targetFloorId()) {
            return analysis(village, freshPlan, observation, components);
        }
        // A rejected fresh plan cannot supply geometry for the persisted target.
        return new Analysis(persistedFloorPlan);
    }

    /** Older scans omitted vertical top exits; recover only the Room owning this exact component. */
    private static Building resolveVerticalExitRoom(Village village,
                                                    Level level,
                                                    BlockPos source,
                                                    StructureScanner.FloorObservation observation,
                                                    List<RoomPartitioner.Component> components,
                                                    RoomScanPlan plan) {
        FloorGeometry floor = observation.scan().floor();
        FloorGeometry.Cell exitCell = floor.interactionCellAt(source.getX(), source.getY(), source.getZ())
                .orElse(null);
        if (plan.mode() != Village.RoomScanMode.ADD_ROOM || exitCell == null
                || !StructureConnector.isVerticalTopExit(level, source)) {
            return null;
        }
        RoomPartitioner.Component selected = selectFreshComponent(floor, exitCell.feet(), components);
        if (selected == null) return null;
        List<Building> owners = village.getRooms()
                .filter(room -> room.getStructureId() == plan.targetStructureId()
                        && room.getFloorId() == plan.targetFloorId())
                .filter(room -> floor.roomIdentityOverlapCount(room.getFloorCells(), selected.floorCells()) > 0)
                .limit(2)
                .toList();
        return owners.size() == 1 ? owners.getFirst() : null;
    }

    record Analysis(RoomScanPlan plan,
                    StructureScanner.FloorObservation observation,
                    List<RoomPartitioner.Component> components,
                    Building.validationResult result) {
        Analysis {
            components = List.copyOf(components);
        }

        Analysis(RoomScanPlan plan, StructureScanner.FloorObservation observation,
                 List<RoomPartitioner.Component> components) {
            this(plan, observation, components, Building.validationResult.SUCCESS);
        }

        Analysis(RoomScanPlan plan) {
            this(plan, null, List.of(), Building.validationResult.SUCCESS);
        }
    }

    private static Analysis analysis(Village village, RoomScanPlan plan,
                                     StructureScanner.FloorObservation observation,
                                     List<RoomPartitioner.Component> components) {
        Building.validationResult result = plan.mode() == Village.RoomScanMode.ADD_ATTACHMENT
                && !plan.hasProspectiveFloor()
                ? prospectiveNumber(village, plan.targetBuildingId(), new StructureFloor(
                        0, 0, observation.scan().anchorY(), observation.scan().floor())).result()
                : Building.validationResult.SUCCESS;
        return new Analysis(plan, observation, components, result);
    }

    static Analysis analyzeFresh(Village village, BlockPos source,
                                 StructureScanner.FloorObservation observation) {
        List<RoomPartitioner.Component> components = observation == null ? List.of()
                : BuildingRoomScanner.components(null, observation.scan());
        return analysis(village, planFresh(village, source, observation, components), observation, components);
    }

    static RoomScanPlan planFresh(Village village,
                                  BlockPos source,
                                  StructureScanner.FloorObservation observation) {
        List<RoomPartitioner.Component> components = observation == null
                ? List.of()
                : BuildingRoomScanner.components(null, observation.scan());
        return planFresh(village, source, observation, components);
    }

    private static RoomScanPlan planFresh(Village village,
                                          BlockPos source,
                                          StructureScanner.FloorObservation observation,
                                          List<RoomPartitioner.Component> components) {
        if (village == null || observation == null) return RoomScanPlan.addBuilding(source);

        RoomScanPlan attachment = attachmentPlan(village, source, observation).orElse(null);
        if (attachment != null) return attachment;

        FloorTarget expansion = selectSameFloorTarget(village, observation).orElse(null);
        if (expansion != null && validExpansion(village, observation, expansion)) {
            RoomPartitioner.Component selected = selectFreshComponent(
                    observation.scan().floor(), observation.seed(), components);
            if (selected != null) {
                SelectedFloorScanner.Result scan = observation.scan();
                BlockPos supportedSource = scan.supportedSource();
                // A nearby connector handoff discovers a Floor, not ownership of its Room.
                boolean directSelection = source.equals(supportedSource) || source.above().equals(supportedSource);
                List<Building> owners = village.getRooms()
                        .filter(room -> room.getStructureId() == expansion.structureId()
                                && room.getFloorId() == expansion.floorId())
                        .filter(room -> room.ownsFloorCell(source)
                                || directSelection && room.ownsFloorCell(scan.seed()))
                        .limit(2)
                        .toList();
                if (owners.size() == 1) {
                    return RoomScanPlan.updateRoom(owners.getFirst(), source, scan.seed());
                }
                // Discovering a nearby Floor does not make an outside interaction a new Room.
                if (!directSelection && scan.floor().interactionCellAt(
                        source.getX(), source.getY(), source.getZ()).isEmpty()) {
                    return RoomScanPlan.addBuilding(source);
                }
                return RoomScanPlan.addRoom(
                        expansion.structureId(), expansion.floorId(), source, observation.seed());
            }
        }

        return RoomScanPlan.addBuilding(source);
    }

    private static Optional<RoomScanPlan> attachmentPlan(Village village,
                                                         BlockPos source,
                                                         StructureScanner.FloorObservation observation) {
        if (village == null || observation == null) return Optional.empty();
        StructureFloor candidateFloor = new StructureFloor(
                0, 0, observation.scan().anchorY(), observation.scan().floor());
        Village.AttachmentTarget target = village.selectAttachmentTarget(
                candidateFloor, observation.verticalConnections(), observation.scan().adjacentFloorSeeds()).orElse(null);
        if (target == null) return Optional.empty();

        FloorGrouping.NumberDecision number = prospectiveNumber(village, target.buildingId(), candidateFloor);
        return Optional.of(RoomScanPlan.attachment(
                target.buildingId(), number.number().orElse(Integer.MIN_VALUE), source, observation.seed()));
    }

    private static FloorGrouping.NumberDecision prospectiveNumber(Village village, int buildingId,
                                                                  StructureFloor candidate) {
        List<StructureFloor> floors = village.getBuildingStructures(buildingId).stream()
                .flatMap(structure -> structure.getFloors().stream()).toList();
        Building main = village.getLogicalBuilding(buildingId)
                .flatMap(logical -> village.getBuilding(logical.mainRoomId())).orElse(null);
        StructureFloor ground = main == null ? null : village.getStructure(main.getStructureId())
                .flatMap(structure -> structure.getFloor(main.getFloorId())).orElse(null);
        return FloorGrouping.prospectiveNumber(floors, ground, candidate);
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
                // Exact ownership survives changes to a surface's representative height.
                // Projected overlap is only a fallback for changed geometry.
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

    private static RoomPartitioner.Component selectFreshComponent(
            FloorGeometry floor,
            BlockPos scanSeed,
            List<RoomPartitioner.Component> components) {
        if (floor == null || scanSeed == null) return null;
        return RoomPartitioner.select(scanSeed, floor, components);
    }

    private record FloorTarget(int structureId, int floorId) {
    }

}
