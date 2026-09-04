package net.conczin.mca.server.world.data;

import java.util.Objects;
import java.util.Set;

/** One semantic storey derived from an exact physical FloorSurface scan. */
record ScannedFloor(FloorSurface surface, int semanticCeilingY) {
    ScannedFloor {
        Objects.requireNonNull(surface, "surface");
        int highestFeetY = surface.cells().stream()
                .mapToInt(cell -> cell.feet().getY())
                .max()
                .orElse(surface.anchorY());
        if (!surface.cells().isEmpty() && semanticCeilingY < highestFeetY) {
            throw new IllegalArgumentException("Semantic floor ceiling cannot be below a selected transition cell: "
                    + semanticCeilingY + " < " + highestFeetY);
        }
    }

    static ScannedFloor physical(FloorSurface surface) {
        return new ScannedFloor(surface, surface.maxCeilingY());
    }

    int anchorY() {
        return surface.anchorY();
    }

    BuildingFloorRegion region() {
        return surface.persistedRegion();
    }

    boolean overlapsSameSemanticBand(StructureFloor other) {
        return other != null
                && StructureFloor.sameSemanticBand(anchorY(), other.anchorY())
                && region().intersectionArea(other.region()) > 0;
    }

    StructureFloor persistedFloor() {
        Set<net.minecraft.core.BlockPos> boundaryCells = surface.cells().stream()
                .map(FloorSurface.Cell::feet)
                .filter(feet -> feet.getY() == semanticCeilingY)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        BuildingFloorRegion boundary = BuildingFloorRegion.fromFootprint(
                semanticCeilingY, boundaryCells);
        return new StructureFloor(0, anchorY(), semanticCeilingY, 0,
                region(), boundary, StructureConnector.floorMarkers(surface));
    }
}
