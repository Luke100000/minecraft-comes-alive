package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Recovers the identity of a persisted same-storey Floor whose canonical footprint is stale. */
final class StructureExpansionPolicy {
    private StructureExpansionPolicy() {
    }

    static Optional<FloorTarget> findSameStoreyTarget(Level world,
                                                      Collection<Structure> persistedStructures,
                                                      BlockPos source) {
        if (world == null || source == null || persistedStructures == null || persistedStructures.isEmpty()) {
            return Optional.empty();
        }
        StructureScanner.Result scan = StructureScanner.scanNewStructure(world, source, List.of());
        if (scan.result() != Building.validationResult.SUCCESS || scan.scannedFloor() == null) {
            return Optional.empty();
        }
        return selectSameStoreyTarget(persistedStructures, scan.scannedFloor());
    }

    static Optional<FloorTarget> selectSameStoreyTarget(Collection<Structure> persistedStructures,
                                                        FloorGeometry freshFloor) {
        if (persistedStructures == null || freshFloor == null) return Optional.empty();
        List<FloorTarget> matches = persistedStructures.stream()
                .flatMap(structure -> structure.getFloors().stream()
                        .filter(floor -> StructureFloor.sameSemanticBand(freshFloor.anchorY(), floor.anchorY()))
                        .filter(floor -> freshFloor.projection().intersectionArea(floor.region()) > 0)
                        .map(floor -> new FloorTarget(structure.getId(), floor.id())))
                .limit(2)
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    static Optional<Building> registeredRoomForFreshComponent(FloorTarget target,
                                                               FloorGeometry floor,
                                                               BlockPos source,
                                                               Collection<Building> rooms) {
        if (target == null || floor == null || source == null || rooms == null) return Optional.empty();
        FloorGeometry geometry = floor;
        List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry);
        RoomPartitioner.Component selected = RoomPartitioner.select(source, geometry, components);
        if (selected == null) return Optional.empty();

        Set<BlockPos> floorCells = BuildingRoomScanner.floorCellsForComponent(selected);
        Set<BlockPos> identityCells = floorCells.stream()
                .filter(cell -> {
                    StructureFloor.ConnectorType connector = geometry.connectorTypesByCell().get(cell);
                    return connector == null || !connector.roomBoundary();
                })
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (identityCells.isEmpty()) return Optional.empty();
        List<Building> matches = rooms.stream()
                .filter(Building::isFunctionalRoom)
                .filter(room -> room.getStructureId() == target.structureId())
                .filter(room -> room.getFloorId() == target.floorId())
                .filter(room -> identityCells.stream().anyMatch(room.getFloorCells()::contains))
                .limit(2)
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    record FloorTarget(int structureId, int floorId) {
    }
}
