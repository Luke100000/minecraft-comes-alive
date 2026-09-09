package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Pure planning over one already-observed fresh Floor. */
final class RoomScanPlanner {
    private RoomScanPlanner() {
    }

    static RoomScanPlan plan(Village village, Level level, BlockPos pos) {
        BlockPos source = pos == null ? BlockPos.ZERO : pos.immutable();
        if (village == null || pos == null) return RoomScanPlan.addBuilding(source);

        Village.ResolvedInteraction resolved = village.resolveInteractionPosition(pos).orElse(null);
        if (resolved != null) {
            if (level != null
                    && source.getY() > resolved.position().floor().anchorY() + StructureFloor.BAND_TOLERANCE) {
                StructureScanner.FloorObservation fresh = StructureScanner.observeFloor(
                        level, source, village.getStructures().values()).orElse(null);
                if (fresh != null) {
                    StructureFloor freshFloor = new StructureFloor(0, 0, fresh.scan().floor());
                    if (!StructureFloor.sameSemanticBand(
                            freshFloor.anchorY(), resolved.position().floor().anchorY())) {
                        return attachmentPlan(village, source, fresh)
                                .orElseGet(() -> RoomScanPlan.addBuilding(source));
                    }
                }
            }
            Building room = resolved.position().room();
            if (room != null) return RoomScanPlan.updateRoom(room, source);
            return RoomScanPlan.addRoom(
                    resolved.structure().getId(), resolved.position().floor().id(), source);
        }

        if (level == null) return RoomScanPlan.addBuilding(source);
        StructureScanner.FloorObservation observation = StructureScanner.observeFloor(
                level, source, village.getStructures().values()).orElse(null);
        return planFresh(village, source, observation);
    }

    static RoomScanPlan planFresh(Village village,
                                  BlockPos source,
                                  StructureScanner.FloorObservation observation) {
        if (village == null || observation == null) return RoomScanPlan.addBuilding(source);

        FloorTarget expansion = selectSameStoreyTarget(village, observation.scan().floor()).orElse(null);
        if (expansion != null && validExpansion(village, observation, expansion)) {
            Building existingRoom = registeredRoomForFreshComponent(
                    village, expansion, observation.scan().floor(), observation.scan().transitions(), source)
                    .orElse(null);
            return existingRoom != null
                    ? RoomScanPlan.updateRoom(existingRoom, source)
                    : RoomScanPlan.addRoom(expansion.structureId(), expansion.floorId(), source);
        }

        return attachmentPlan(village, source, observation)
                .orElseGet(() -> RoomScanPlan.addBuilding(source));
    }

    static Optional<RoomScanPlan> attachmentPlan(Village village,
                                                 BlockPos source,
                                                 StructureScanner.FloorObservation observation) {
        if (village == null || observation == null) return Optional.empty();
        StructureFloor candidateFloor = new StructureFloor(0, 0, observation.scan().floor());
        Village.AttachmentTarget target = village.selectAttachmentTarget(
                candidateFloor, observation.verticalConnections(), observation.scan().connectedFloors()).orElse(null);
        if (target == null) return Optional.empty();

        Structure candidate = new Structure(-1, observation.seed(), List.of(candidateFloor));
        int floorNumber = village.prospectiveFloorNumber(target.buildingId(), candidate, candidateFloor);
        if (floorNumber == Integer.MIN_VALUE) return Optional.empty();
        return Optional.of(RoomScanPlan.attachment(
                target.buildingId(), floorNumber, source, observation.seed()));
    }

    private static boolean validExpansion(Village village,
                                          StructureScanner.FloorObservation observation,
                                          FloorTarget target) {
        return StructureScanner.validateObservation(
                observation, village.getStructures().values(), target.structureId(), -1)
                == Building.validationResult.SUCCESS;
    }

    private static Optional<FloorTarget> selectSameStoreyTarget(Village village, FloorGeometry freshFloor) {
        if (village == null || freshFloor == null) return Optional.empty();
        List<FloorTarget> matches = village.getStructures().values().stream()
                .flatMap(structure -> structure.getFloors().stream()
                        .filter(floor -> StructureFloor.sameSemanticBand(freshFloor.anchorY(), floor.anchorY()))
                        .filter(floor -> freshFloor.footprintIntersectionArea(floor.geometry()) > 0)
                        .map(floor -> new FloorTarget(structure.getId(), floor.id())))
                .limit(2)
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private static Optional<Building> registeredRoomForFreshComponent(
            Village village,
            FloorTarget target,
            FloorGeometry floor,
            Collection<SelectedFloorScanner.Transition> transitions,
            BlockPos source) {
        if (village == null || target == null || floor == null || source == null) return Optional.empty();
        List<RoomPartitioner.Component> components = RoomPartitioner.partition(floor, transitions);
        RoomPartitioner.Component selected = RoomPartitioner.select(source, floor, components);
        if (selected == null) return Optional.empty();

        Set<BlockPos> identityCells = selected.floorCells().stream()
                .filter(cell -> {
                    FloorConnector.Type connector = floor.connectorTypesByCell().get(cell);
                    return connector == null || !connector.roomBoundary();
                })
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (identityCells.isEmpty()) return Optional.empty();

        List<Building> matches = village.getRooms()
                .filter(Building::isFunctionalRoom)
                .filter(room -> room.getStructureId() == target.structureId())
                .filter(room -> room.getFloorId() == target.floorId())
                .filter(room -> identityCells.stream().anyMatch(room.getFloorCells()::contains))
                .limit(2)
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private record FloorTarget(int structureId, int floorId) {
    }
}
