package net.conczin.mca.server.world.data;

import java.util.Collection;
import java.util.List;

/** Test fixture helpers for intentionally flat synthetic Floors. */
public final class TestStructureFloors {
    private TestStructureFloors() {
    }

    public static StructureFloor create(int id, int floorNumber, FloorGeometry geometry) {
        return new StructureFloor(id, floorNumber, geometry);
    }

    public static StructureFloor create(int id,
                                        int anchorY,
                                        int ceilingY,
                                        TestFloorFootprint region) {
        return create(id, anchorY, ceilingY, 0, region, List.of());
    }

    public static StructureFloor create(int id,
                                        int anchorY,
                                        int ceilingY,
                                        int floorNumber,
                                        TestFloorFootprint region) {
        return create(id, anchorY, ceilingY, floorNumber, region, List.of());
    }

    public static StructureFloor create(int id,
                                        int anchorY,
                                        int ceilingY,
                                        int floorNumber,
                                        TestFloorFootprint region,
                                        Collection<FloorConnector.Marker> markers) {
        List<FloorConnector.Marker> connectors = (markers == null ? List.<FloorConnector.Marker>of() : markers)
                .stream()
                .filter(marker -> region.cells().contains(marker.floorCell()))
                .toList();
        FloorGeometry geometry = new FloorGeometry(region.cells().stream()
                .map(pos -> new FloorGeometry.Cell(pos, ceilingY))
                .toList(), connectors);
        return new StructureFloor(id, floorNumber, geometry);
    }
}
