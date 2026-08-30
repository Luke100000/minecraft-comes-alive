package net.conczin.mca.client.gui;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlueprintMapGeometryTest {
    @Test
    void buildingShapeIsOneCellPaddingAroundRoomsFromEveryFloor() {
        Set<BlueprintMapFootprint.Cell> ground = Set.of(
                new BlueprintMapFootprint.Cell(0, 0),
                new BlueprintMapFootprint.Cell(1, 0),
                new BlueprintMapFootprint.Cell(0, 1));
        Set<BlueprintMapFootprint.Cell> upper = Set.of(
                new BlueprintMapFootprint.Cell(2, 0),
                new BlueprintMapFootprint.Cell(2, 1));
        LinkedHashSet<BlueprintMapFootprint.Cell> union = new LinkedHashSet<>(ground);
        union.addAll(upper);

        BlueprintMapGeometry.BuildingShape shape =
                BlueprintMapGeometry.buildBuildingShape(List.of(ground, upper));
        Set<BlueprintMapFootprint.Cell> expectedOutline = BlueprintMapFootprint.expand(union, 1);
        LinkedHashSet<BlueprintMapFootprint.Cell> expectedShell = new LinkedHashSet<>(expectedOutline);
        expectedShell.removeAll(union);

        assertEquals(expectedOutline, shape.outline().cells());
        assertEquals(expectedShell, shape.shell().cells());
    }

    @Test
    void buildingShapeIsEmptyWithoutRegisteredRooms() {
        BlueprintMapGeometry.BuildingShape shape = BlueprintMapGeometry.buildBuildingShape(List.of());

        assertEquals(Set.of(), shape.outline().cells());
        assertEquals(Set.of(), shape.shell().cells());
    }
}
