package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.List;
import java.util.Optional;

/** Pure planning over one already-observed fresh Floor. */
final class RoomScanPlanner {
    private RoomScanPlanner() {
    }

    static RoomScanPlan planFresh(Village village,
                                  BlockPos source,
                                  StructureScanner.FloorObservation observation) {
        if (village == null || observation == null) return RoomScanPlan.addBuilding(source);

        StructureExpansionPolicy.FloorTarget expansion = StructureExpansionPolicy.selectSameStoreyTarget(
                village.getStructures().values(), observation.floor()).orElse(null);
        if (expansion != null && validExpansion(village, observation, expansion)) {
            Building existingRoom = StructureExpansionPolicy.registeredRoomForFreshComponent(
                    expansion, observation.floor(), source, village.getRooms().toList()).orElse(null);
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
        StructureFloor candidateFloor = new StructureFloor(0, 0, observation.floor());
        Village.AttachmentTarget target = village.selectAttachmentTarget(
                candidateFloor, observation.verticalConnections(), observation.connectedFloors()).orElse(null);
        if (target == null) return Optional.empty();

        Structure candidate = new Structure(-1, observation.seed(), List.of(candidateFloor));
        int floorNumber = village.prospectiveFloorNumber(target.buildingId(), candidate, candidateFloor);
        if (floorNumber == Integer.MIN_VALUE) return Optional.empty();
        return Optional.of(RoomScanPlan.attachment(
                target.buildingId(), floorNumber, source, observation.seed()));
    }

    private static boolean validExpansion(Village village,
                                          StructureScanner.FloorObservation observation,
                                          StructureExpansionPolicy.FloorTarget target) {
        return StructureScanner.validateObservation(
                observation, village.getStructures().values(), target.structureId(), -1)
                == Building.validationResult.SUCCESS;
    }
}
