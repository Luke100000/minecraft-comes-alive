package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Comparator;
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
        RoomScanPlan freshPlan = planFresh(village, level, source, observation, components);
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
        return planFresh(village, null, source, observation, components);
    }

    private static RoomScanPlan planFresh(Village village,
                                          Level level,
                                          BlockPos source,
                                          StructureScanner.FloorObservation observation,
                                          List<RoomPartitioner.Component> components) {
        if (village == null || observation == null) return RoomScanPlan.addBuilding(source);

        RoomScanPlan attachment = attachmentPlan(village, source, observation).orElse(null);
        if (attachment != null) return attachment;

        FloorTarget expansion = selectSameStoreyTarget(village, observation.scan().floor()).orElse(null);
        RoomScanPlan transitionAttachment = connectedTransitionAttachmentPlan(
                village, level, source, observation, expansion).orElse(null);
        if (transitionAttachment != null) return transitionAttachment;

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
                candidateFloor, observation.verticalConnections(), observation.directlyConnectedFloors()).orElse(null);
        if (target == null) return Optional.empty();

        int floorNumber = adjacentFloorNumber(village, target, candidateFloor);
        if (floorNumber == Integer.MIN_VALUE) return Optional.empty();
        return Optional.of(RoomScanPlan.attachment(
                target.buildingId(), floorNumber, source, observation.seed(), candidateFloor));
    }

    /**
     * A stair landing can produce a successful fresh scan in the semantic band of an existing Floor
     * even though the persisted exact Floor does not own the interaction position. In that case the
     * persisted geometry remains authoritative and the already-discovered connected storeys decide
     * whether the interaction is actually entering the next Floor above or below.
     */
    private static Optional<RoomScanPlan> connectedTransitionAttachmentPlan(
            Village village,
            Level level,
            BlockPos source,
            StructureScanner.FloorObservation observation,
            FloorTarget expansion) {
        if (village == null || source == null || observation == null || expansion == null) {
            return Optional.empty();
        }
        Structure referenceStructure = village.getStructure(expansion.structureId()).orElse(null);
        StructureFloor referenceFloor = referenceStructure == null
                ? null : referenceStructure.getFloor(expansion.floorId()).orElse(null);
        if (referenceFloor == null || referenceFloor.geometry().interactionCellAt(
                source.getX(), source.getY(), source.getZ()).isPresent()) {
            return Optional.empty();
        }

        int direction = Integer.compare(observation.scan().floor().anchorY(), referenceFloor.anchorY());
        if (direction == 0) return Optional.empty();

        int buildingId = referenceStructure.getLogicalBuildingId();
        List<ConnectedAttachment> candidates = new ArrayList<>();
        for (FloorGeometry geometry : observation.directlyConnectedFloors()) {
            StructureFloor candidateFloor = new StructureFloor(0, 0, geometry);
            if (Integer.compare(candidateFloor.anchorY(), referenceFloor.anchorY()) != direction) {
                continue;
            }

            Village.AttachmentTarget target = village.selectAttachmentTarget(
                    candidateFloor, List.of(), observation.scan().directlyConnectedFloors(geometry)).orElse(null);
            if (target == null || target.buildingId() != buildingId) continue;

            BlockPos scanSeed = nearestScanSeed(level, geometry, source).orElse(null);
            if (scanSeed == null) continue;
            int floorNumber = adjacentFloorNumber(village, target, candidateFloor);
            if (floorNumber == Integer.MIN_VALUE) continue;
            candidates.add(new ConnectedAttachment(candidateFloor, floorNumber, scanSeed));
        }
        if (candidates.isEmpty()) return Optional.empty();

        int nearestAnchor = candidates.stream()
                .map(ConnectedAttachment::floor)
                .mapToInt(StructureFloor::anchorY)
                .reduce(direction < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE,
                        direction < 0 ? Math::max : Math::min);
        List<ConnectedAttachment> nearest = candidates.stream()
                .filter(candidate -> Math.abs(candidate.floor().anchorY() - nearestAnchor)
                        <= StructureFloor.BAND_TOLERANCE)
                .toList();
        if (nearest.size() != 1) return Optional.empty();
        ConnectedAttachment selected = nearest.getFirst();
        return Optional.of(RoomScanPlan.attachment(
                buildingId, selected.floorNumber(), source, selected.scanSeed(), selected.floor()));
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

    private static Optional<BlockPos> nearestScanSeed(Level level, FloorGeometry geometry, BlockPos source) {
        if (geometry == null || source == null) return Optional.empty();
        FloorCeilingResolver ceilings = level == null ? null : new FloorCeilingResolver(level);
        return geometry.cells().stream()
                .map(FloorGeometry.Cell::feet)
                .sorted(Comparator
                        .comparingInt((BlockPos candidate) -> distance(candidate, source))
                        .thenComparingInt(BlockPos::getY)
                        .thenComparingInt(BlockPos::getX)
                        .thenComparingInt(BlockPos::getZ))
                .filter(candidate -> level == null
                        || SelectedFloorScanner.inspectSurfaceCell(level, candidate, ceilings).isPresent())
                .findFirst();
    }

    private static int distance(BlockPos first, BlockPos second) {
        return Math.abs(first.getX() - second.getX())
                + Math.abs(first.getY() - second.getY())
                + Math.abs(first.getZ() - second.getZ());
    }

    private static boolean validExpansion(Village village,
                                          StructureScanner.FloorObservation observation,
                                          FloorTarget target) {
        Structure structure = village.getStructure(target.structureId()).orElseThrow();
        return StructureScanner.validateObservation(
                observation, village.getStructures().values(), target.structureId(), structure.getLogicalBuildingId())
                == Building.validationResult.SUCCESS;
    }

    private static Optional<FloorTarget> selectSameStoreyTarget(Village village, FloorGeometry freshFloor) {
        if (village == null || freshFloor == null) return Optional.empty();
        List<FloorTarget> matches = village.getStructures().values().stream()
                .flatMap(structure -> structure.getFloors().stream()
                        .filter(floor -> floor.overlapsSemanticStorey(freshFloor))
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

    private record ConnectedAttachment(StructureFloor floor, int floorNumber, BlockPos scanSeed) {
    }
}
