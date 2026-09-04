package net.conczin.mca.server.world.data;

import java.util.Objects;
import java.util.Set;

/** Temporary semantic-storey wrapper around exact FloorGeometry during the migration. */
record ScannedFloor(FloorGeometry geometry, int semanticCeilingY) {
    ScannedFloor(FloorSurface surface, int semanticCeilingY) {
        this(fromSurface(surface), semanticCeilingY);
    }

    ScannedFloor {
        Objects.requireNonNull(geometry, "geometry");
        int highestFeetY = geometry.cells().stream()
                .mapToInt(cell -> cell.feet().getY())
                .max()
                .orElse(geometry.anchorY());
        if (!geometry.cells().isEmpty() && semanticCeilingY < highestFeetY) {
            throw new IllegalArgumentException("Semantic floor ceiling cannot be below a selected transition cell: "
                    + semanticCeilingY + " < " + highestFeetY);
        }
    }

    static ScannedFloor physical(FloorGeometry geometry) {
        return new ScannedFloor(geometry, geometry.maxPhysicalCeilingY());
    }

    static ScannedFloor physical(FloorSurface surface) {
        return physical(fromSurface(surface));
    }

    int anchorY() {
        return geometry.anchorY();
    }

    BuildingFloorRegion region() {
        return geometry.projection();
    }

    boolean overlapsSameSemanticBand(StructureFloor other) {
        return other != null
                && StructureFloor.sameSemanticBand(anchorY(), other.anchorY())
                && region().intersectionArea(other.region()) > 0;
    }

    StructureFloor persistedFloor() {
        Set<net.minecraft.core.BlockPos> boundaryCells = geometry.cells().stream()
                .map(FloorGeometry.Cell::feet)
                .filter(feet -> feet.getY() == semanticCeilingY)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        BuildingFloorRegion boundary = BuildingFloorRegion.fromFootprint(
                semanticCeilingY, boundaryCells);
        return new StructureFloor(0, anchorY(), semanticCeilingY, 0,
                region(), boundary, geometry.connectorMarkers());
    }

    /** Compatibility bridge for callers removed in the next topology task. */
    FloorSurface surface() {
        Set<FloorSurface.Cell> cells = geometry.cells().stream()
                .map(cell -> new FloorSurface.Cell(cell.feet(), cell.surfaceY(), cell.ceilingY()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new FloorSurface(cells, geometry.connectorTypesByCell());
    }

    private static FloorGeometry fromSurface(FloorSurface surface) {
        Objects.requireNonNull(surface, "surface");
        Set<FloorGeometry.Cell> cells = surface.cells().stream()
                .map(cell -> new FloorGeometry.Cell(cell.feet(), cell.surfaceY(), cell.ceilingY()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new FloorGeometry(cells, surface.connectorTypesByFloorCell());
    }
}
