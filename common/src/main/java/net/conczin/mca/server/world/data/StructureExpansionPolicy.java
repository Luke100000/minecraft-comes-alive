package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Recovers a persisted same-storey Floor whose canonical footprint is stale. */
final class StructureExpansionPolicy {
    private StructureExpansionPolicy() {
    }

    static Optional<Match> findSameStoreyTarget(Level world,
                                                Collection<Structure> persistedStructures,
                                                BlockPos source) {
        if (world == null || source == null || persistedStructures == null || persistedStructures.isEmpty()) {
            return Optional.empty();
        }
        StructureScanner.Result scan = StructureScanner.scanNewStructure(world, source, List.of());
        if (scan.result() != Building.validationResult.SUCCESS || scan.scannedFloor() == null) {
            return Optional.empty();
        }
        return selectSameStoreyTarget(persistedStructures, scan.scannedFloor())
                .map(target -> new Match(target, scan));
    }

    static Optional<FloorTarget> selectSameStoreyTarget(Collection<Structure> persistedStructures,
                                                        ScannedFloor freshFloor) {
        if (persistedStructures == null || freshFloor == null) return Optional.empty();
        List<FloorTarget> matches = persistedStructures.stream()
                .flatMap(structure -> structure.getFloors().stream()
                        .filter(freshFloor::overlapsSameSemanticBand)
                        .map(floor -> new FloorTarget(structure, floor)))
                .limit(2)
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    static Optional<Building> registeredRoomForFreshComponent(FloorTarget target,
                                                               ScannedFloor floor,
                                                               BlockPos source,
                                                               Collection<Building> rooms) {
        if (target == null || floor == null || source == null || rooms == null) return Optional.empty();
        FloorSurface surface = floor.surface();
        List<FloorSurfacePartitioner.Component> components = FloorSurfacePartitioner.partition(surface);
        FloorSurfacePartitioner.Component selected = FloorSurfacePartitioner.select(source, surface, components);
        if (selected == null) return Optional.empty();

        Set<BlockPos> footprint = BuildingRoomScanner.footprintForComponent(
                selected, floor.anchorY());
        Set<BlockPos> identityFootprint = footprint.stream()
                .filter(cell -> {
                    StructureFloor.ConnectorType connector = surface.connectorTypesByFloorCell().get(cell);
                    return connector == null || !connector.roomBoundary();
                })
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (identityFootprint.isEmpty()) return Optional.empty();
        BuildingFloorRegion componentRegion = BuildingFloorRegion.fromFootprint(
                floor.anchorY(), identityFootprint);
        List<Building> matches = rooms.stream()
                .filter(Building::isFunctionalRoom)
                .filter(room -> room.getStructureId() == target.structure().getId())
                .filter(room -> room.getFloorId() == target.floor().id())
                .filter(room -> room.getFloorRegion()
                        .map(region -> region.intersectionArea(componentRegion) > 0)
                        .orElse(false))
                .limit(2)
                .toList();
        return matches.size() == 1 ? Optional.of(matches.getFirst()) : Optional.empty();
    }

    record FloorTarget(Structure structure, StructureFloor floor) {
    }

    record Match(FloorTarget target, StructureScanner.Result scan) {
    }
}
