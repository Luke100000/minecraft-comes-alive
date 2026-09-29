package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

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

        Village.ResolvedInteraction resolved = village.resolveInteractionPosition(pos).orElse(null);
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
        if (persistedFloorPlan == null) {
            return new Analysis(freshPlan, observation, components);
        }
        if (freshPlan.mode() == Village.RoomScanMode.ADD_ROOM
                && freshPlan.targetStructureId() == persistedFloorPlan.targetStructureId()
                && freshPlan.targetFloorId() == persistedFloorPlan.targetFloorId()) {
            return new Analysis(freshPlan, observation, components);
        }
        // A rejected fresh plan cannot supply geometry for the persisted target.
        return new Analysis(persistedFloorPlan);
    }

    record Analysis(RoomScanPlan plan,
                    StructureScanner.FloorObservation observation,
                    List<RoomPartitioner.Component> components) {
        Analysis {
            components = List.copyOf(components);
        }

        Analysis(RoomScanPlan plan) {
            this(plan, null, List.of());
        }
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

        FloorTarget expansion = selectSameFloorTarget(village, observation.scan().floor()).orElse(null);
        if (expansion != null && validExpansion(village, observation, expansion)) {
            RoomPartitioner.Component selected = selectFreshComponent(
                    observation.scan().floor(), observation.seed(), components);
            if (selected != null) {
                return RoomScanPlan.addRoom(
                        expansion.structureId(), expansion.floorId(), source, selected.nearestCell(observation.seed()));
            }
        }

        return RoomScanPlan.addBuilding(source);
    }

    private static Optional<RoomScanPlan> attachmentPlan(Village village,
                                                         BlockPos source,
                                                         StructureScanner.FloorObservation observation) {
        if (village == null || observation == null) return Optional.empty();
        StructureFloor candidateFloor = new StructureFloor(0, 0, observation.scan().floor());
        Village.AttachmentTarget target = village.selectAttachmentTarget(
                candidateFloor, observation.verticalConnections(), observation.scan().adjacentFloorSeeds()).orElse(null);
        if (target == null) return Optional.empty();

        int floorNumber = adjacentFloorNumber(village, target, candidateFloor);
        if (floorNumber == Integer.MIN_VALUE) return Optional.empty();
        return Optional.of(RoomScanPlan.attachment(
                target.buildingId(), floorNumber, source, observation.seed(), candidateFloor));
    }

    private static int adjacentFloorNumber(
            Village village, Village.AttachmentTarget target, StructureFloor candidate) {
        Structure structure = village.getStructure(target.structureId()).orElse(null);
        StructureFloor reference = structure == null
                ? null : structure.getFloor(target.floorId()).orElse(null);
        if (reference == null) return Integer.MIN_VALUE;
        int direction = Integer.compare(candidate.anchorY(), reference.anchorY());
        return direction == 0 ? Integer.MIN_VALUE : reference.floorNumber() + direction;
    }

    private static boolean validExpansion(Village village,
                                          StructureScanner.FloorObservation observation,
                                          FloorTarget target) {
        Structure structure = village.getStructure(target.structureId()).orElseThrow();
        return StructureScanner.validateObservation(
                observation, village.getStructures().values(), target.structureId(), structure.getLogicalBuildingId())
                == Building.validationResult.SUCCESS;
    }

    private static Optional<FloorTarget> selectSameFloorTarget(Village village, FloorGeometry freshFloor) {
        if (village == null || freshFloor == null) return Optional.empty();
        List<FloorTarget> matches = village.getStructures().values().stream()
                .flatMap(structure -> structure.getFloors().stream()
                        .filter(floor -> floor.overlapsSameSemanticBand(freshFloor))
                        .map(floor -> new FloorTarget(structure.getId(), floor.id())))
                .limit(2)
                .toList();
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
