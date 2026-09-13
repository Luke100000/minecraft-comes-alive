package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
                                        BuildingFloorRegion region) {
        return create(id, anchorY, ceilingY, 0, region, List.of());
    }

    public static StructureFloor create(int id,
                                        int anchorY,
                                        int ceilingY,
                                        int floorNumber,
                                        BuildingFloorRegion region) {
        return create(id, anchorY, ceilingY, floorNumber, region, List.of());
    }

    public static StructureFloor create(int id,
                                        int anchorY,
                                        int ceilingY,
                                        int floorNumber,
                                        BuildingFloorRegion region,
                                        Collection<FloorConnector.Marker> markers) {
        Map<BlockPos, FloorConnector.Type> connectors = new LinkedHashMap<>();
        for (FloorConnector.Marker marker : markers == null ? List.<FloorConnector.Marker>of() : markers) {
            if (region.cells().contains(marker.pos())) connectors.put(marker.pos(), marker.type());
        }
        FloorGeometry geometry = new FloorGeometry(region.cells().stream()
                .map(pos -> new FloorGeometry.Cell(pos, ceilingY))
                .toList(), connectors);
        return new StructureFloor(id, floorNumber, geometry);
    }
}
