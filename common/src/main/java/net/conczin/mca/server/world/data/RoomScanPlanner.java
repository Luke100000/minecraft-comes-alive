package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;

/** Pure planning over one already-observed fresh Floor. */
final class RoomScanPlanner {
    private RoomScanPlanner() {
    }

    static RoomScanPlan plan(Village village, Level level, BlockPos pos) {
        BlockPos source = pos == null ? BlockPos.ZERO : pos.immutable();
        if (village == null || pos == null) return RoomScanPlan.addBuilding(source);

        Village.ResolvedInteraction resolved = village.resolveInteractionPosition(pos).orElse(null);
        RoomScanPlan persistedFloorPlan = null;
        if (resolved != null) {
            Building room = resolved.position().room();
            if (room != null) return RoomScanPlan.updateRoom(room, source);
            persistedFloorPlan = RoomScanPlan.addRoom(
                    resolved.structure().getId(), resolved.position().floor().id(), source);
        }

        if (level == null) {
            return persistedFloorPlan == null ? RoomScanPlan.addBuilding(source) : persistedFloorPlan;
        }
        StructureScanner.FloorObservation observation = StructureScanner.observeFloor(
                level, source, village.getStructures().values()).orElse(null);
        if (observation == null) {
            return persistedFloorPlan == null ? RoomScanPlan.addBuilding(source) : persistedFloorPlan;
        }
        List<RoomPartitioner.Component> components = BuildingRoomScanner.components(
                level, observation.scan().floor(), observation.scan().transitions());
        RoomScanPlan freshPlan = planFresh(village, source, observation, components);
        if (persistedFloorPlan == null || freshPlan.mode() == Village.RoomScanMode.UPDATE_ROOM) {
            return freshPlan;
        }
        if (freshPlan.mode() == Village.RoomScanMode.ADD_ROOM
                && freshPlan.targetStructureId() == persistedFloorPlan.targetStructureId()
                && freshPlan.targetFloorId() == persistedFloorPlan.targetFloorId()) {
            return freshPlan;
        }
        return persistedFloorPlan;
    }

    static RoomScanPlan planFresh(Village village,
                                  BlockPos source,
                                  StructureScanner.FloorObservation observation) {
        List<RoomPartitioner.Component> components = observation == null
                ? List.of()
                : BuildingRoomScanner.components(
                null, observation.scan().floor(), observation.scan().transitions());
        return planFresh(village, source, observation, components);
    }

    private static RoomScanPlan planFresh(Village village,
                                          BlockPos source,
                                          StructureScanner.FloorObservation observation,
                                          List<RoomPartitioner.Component> components) {
        if (village == null || observation == null) return RoomScanPlan.addBuilding(source);

        FloorTarget expansion = selectSameStoreyTarget(village, observation.scan().floor()).orElse(null);
        if (expansion != null && validExpansion(village, observation, expansion)) {
            FreshComponentSelection selected = selectFreshComponent(
                    village, expansion, observation.scan().floor(),
                    observation.seed(), components).orElse(null);
            if (selected != null) {
                return selected.existingRoom() != null
                        ? RoomScanPlan.updateRoom(selected.existingRoom(), source)
                        : RoomScanPlan.addRoom(
                        expansion.structureId(), expansion.floorId(), source, selected.scanSeed());
            }
        }

        return attachmentPlan(village, source, observation)
                .orElseGet(() -> RoomScanPlan.addBuilding(source));
    }

    private static Optional<RoomScanPlan> attachmentPlan(Village village,
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
                        .filter(floor -> floor.overlapsSemanticStorey(freshFloor))
                        .map(floor -> new FloorTarget(structure.getId(), floor.id())))
                .limit(2)
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private static Optional<FreshComponentSelection> selectFreshComponent(
            Village village,
            FloorTarget target,
            FloorGeometry floor,
            BlockPos scanSeed,
            List<RoomPartitioner.Component> components) {
        if (village == null || target == null || floor == null
                || scanSeed == null) return Optional.empty();
        RoomPartitioner.Component selected = RoomPartitioner.select(scanSeed, floor, components);
        if (selected == null) return Optional.empty();

        Building existingRoom = registeredRoomForComponent(village, target, floor, selected).orElse(null);
        return Optional.of(new FreshComponentSelection(
                existingRoom, selected.nearestCell(scanSeed)));
    }

    private static Optional<Building> registeredRoomForComponent(
            Village village,
            FloorTarget target,
            FloorGeometry floor,
            RoomPartitioner.Component component) {
        if (component == null) return Optional.empty();

        var componentCells = component.floorCells();

        List<Building> matches = village.getRooms()
                .filter(Building::isFunctionalRoom)
                .filter(room -> room.getStructureId() == target.structureId())
                .filter(room -> room.getFloorId() == target.floorId())
                .filter(room -> floor.roomIdentityOverlapCount(
                        componentCells, room.getFloorCells()) > 0)
                .limit(2)
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    private record FloorTarget(int structureId, int floorId) {
    }

    private record FreshComponentSelection(Building existingRoom, BlockPos scanSeed) {
    }
}
