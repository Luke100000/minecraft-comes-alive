package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.Comparator;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Policies for attaching newly scanned Floors to persisted Structures and Rooms. */
final class StructureExpansionPolicy {
    private static final Comparator<Village.AttachmentTarget> ATTACHMENT_TARGET_ORDER = Comparator
            .comparingInt(Village.AttachmentTarget::gap)
            .thenComparingInt(Village.AttachmentTarget::buildingId)
            .thenComparingInt(Village.AttachmentTarget::structureId)
            .thenComparingInt(Village.AttachmentTarget::floorId);

    private StructureExpansionPolicy() {
    }

    static Optional<Village.AttachmentTarget> selectAttachmentTarget(
            StructureFloor candidate,
            Collection<StructureConnector.VerticalConnection> verticalConnections,
            Collection<FloorGeometry> connectedFloors,
            Map<Integer, Structure> structures) {
        if (candidate == null || structures == null) return Optional.empty();
        Set<StructureConnector.VerticalConnection> connections = attachmentConnections(
                candidate, verticalConnections, connectedFloors, structures.values());
        if (hasUnprovenAttachmentOverlap(candidate, connections, structures.values())) return Optional.empty();

        Map<Integer, Village.AttachmentTarget> nearestByBuilding = new HashMap<>();
        for (StructureConnector.VerticalConnection connection : connections) {
            Structure structure = connection.structure();
            StructureFloor floor = connection.floor();
            if (structures.get(structure.getId()) != structure) continue;
            int gap = candidate.attachmentGapTo(floor);
            if (gap < 0) continue;
            Village.AttachmentTarget target = new Village.AttachmentTarget(
                    structure.getLogicalBuildingId(), structure.getId(), floor.id(), gap);
            nearestByBuilding.merge(target.buildingId(), target,
                    (first, second) -> ATTACHMENT_TARGET_ORDER.compare(first, second) <= 0 ? first : second);
        }

        Village.AttachmentTarget nearest = nearestByBuilding.values().stream()
                .min(ATTACHMENT_TARGET_ORDER).orElse(null);
        if (nearest == null) return Optional.empty();
        return nearestByBuilding.values().stream()
                .anyMatch(target -> target.buildingId() != nearest.buildingId()
                        && target.gap() == nearest.gap())
                ? Optional.empty() : Optional.of(nearest);
    }

    private static Set<StructureConnector.VerticalConnection> attachmentConnections(
            StructureFloor candidate,
            Collection<StructureConnector.VerticalConnection> verticalConnections,
            Collection<FloorGeometry> connectedFloors,
            Collection<Structure> structures) {
        LinkedHashSet<StructureConnector.VerticalConnection> connections = new LinkedHashSet<>();
        if (verticalConnections != null) connections.addAll(verticalConnections);
        if (connectedFloors == null) return Set.copyOf(connections);

        for (FloorGeometry band : connectedFloors) {
            if (StructureFloor.sameSemanticBand(candidate.anchorY(), band.anchorY())) continue;
            for (Structure structure : structures) {
                for (StructureFloor floor : structure.getFloors()) {
                    if (StructureFloor.sameSemanticBand(band.anchorY(), floor.anchorY())
                            && band.footprintIntersectionArea(floor.geometry()) > 0) {
                        connections.add(new StructureConnector.VerticalConnection(structure, floor));
                    }
                }
            }
        }
        return Set.copyOf(connections);
    }

    private static boolean hasUnprovenAttachmentOverlap(
            StructureFloor candidate,
            Set<StructureConnector.VerticalConnection> connections,
            Collection<Structure> structures) {
        for (Structure structure : structures) {
            for (StructureFloor floor : structure.getFloors()) {
                if (!candidate.overlapsFootprint(floor) || candidate.verticalGapTo(floor) >= 0) continue;
                if (candidate.sameSemanticBand(floor)
                        || !connections.contains(new StructureConnector.VerticalConnection(structure, floor))) {
                    return true;
                }
            }
        }
        return false;
    }

    static Optional<FloorTarget> selectSameStoreyTarget(Collection<Structure> persistedStructures,
                                                        FloorGeometry freshFloor) {
        if (persistedStructures == null || freshFloor == null) return Optional.empty();
        List<FloorTarget> matches = persistedStructures.stream()
                .flatMap(structure -> structure.getFloors().stream()
                        .filter(floor -> StructureFloor.sameSemanticBand(freshFloor.anchorY(), floor.anchorY()))
                        .filter(floor -> freshFloor.footprintIntersectionArea(floor.geometry()) > 0)
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
        List<RoomPartitioner.Component> components = RoomPartitioner.partition(floor);
        RoomPartitioner.Component selected = RoomPartitioner.select(source, floor, components);
        if (selected == null) return Optional.empty();

        Set<BlockPos> floorCells = selected.floorCells();
        Set<BlockPos> identityCells = floorCells.stream()
                .filter(cell -> {
                    FloorConnector.Type connector = floor.connectorTypesByCell().get(cell);
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
