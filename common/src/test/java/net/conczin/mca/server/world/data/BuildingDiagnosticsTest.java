package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.conczin.mca.server.world.data.BuildingDiagnostics.StructuralPosition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;

class BuildingDiagnosticsTest {
    @Test
    void compatibilityScanConstructorsLeaveUnknownProvenanceUnknown() {
        BlockPos source = new BlockPos(10, 65, 20);
        BlockPos selected = new BlockPos(10, 64, 20);
        FloorGeometry floor = new FloorGeometry(Set.of(new FloorGeometry.Cell(selected, 68)), List.of());
        SelectedFloorScanner.Result physical = new SelectedFloorScanner.Result(Building.validationResult.SUCCESS,
                floor, selected, source, 64, selected, selected.above(3), Set.of(), Set.of(), Set.of());
        StructureScanner.Result structure = new StructureScanner.Result(Building.validationResult.SUCCESS, source, physical);

        assertNull(physical.traversalSeed());
        assertNull(structure.discoveryInput());
        assertEquals(source, structure.source());
        assertEquals(selected, physical.seed());
    }

    @Test
    void scanProvenanceSnapshotsMutableInputPositions() {
        BlockPos selected = new BlockPos(10, 64, 20);
        BlockPos.MutableBlockPos discovery = selected.mutable();
        BlockPos.MutableBlockPos traversal = new BlockPos.MutableBlockPos(0, 64, 0);
        FloorGeometry floor = new FloorGeometry(Set.of(new FloorGeometry.Cell(selected, 68)), List.of());
        SelectedFloorScanner.Result physical = new SelectedFloorScanner.Result(Building.validationResult.SUCCESS,
                floor, selected, selected, 64, selected, selected.above(3), Set.of(), Set.of(), Set.of(), traversal);
        StructureScanner.Result structure = new StructureScanner.Result(Building.validationResult.SUCCESS,
                selected, physical, discovery);
        discovery.set(99, 99, 99);
        traversal.set(99, 99, 99);

        assertEquals(new BlockPos(10, 64, 20), structure.discoveryInput());
        assertEquals(new BlockPos(0, 64, 0), physical.traversalSeed());
        assertEquals(selected, physical.seed());
    }

    @Test
    void refreshedPersistedFloorAnchorDoesNotProveASeparateUpperFloor() throws Exception {
        BlockPos source = new BlockPos(0, 76, 0);
        FloorGeometry geometry = new FloorGeometry(Set.of(new FloorGeometry.Cell(source.above(), 88)), List.of());
        Structure structure = new Structure(80, source, List.of(new StructureFloor(0, 0, 76, geometry)));
        Building room = new Building(source);
        room.setFloorId(0);
        var verdict = BuildingDiagnostics.class.getDeclaredMethod("verdict", StructuralPosition.class,
                String.class, Building.validationResult.class, Structure.class, java.util.Collection.class,
                Building.class, FloorGeometry.class, BlockPos.class, net.minecraft.server.level.ServerLevel.class);
        verdict.setAccessible(true);

        String message = (String) verdict.invoke(null, StructuralPosition.REGISTERED_ROOM, "UPDATE_ROOM",
                Building.validationResult.SUCCESS, structure, List.of(room), room, geometry, source, null);

        assertFalse(message.contains("SEPARATES_UPPER_FLOOR"), "refresh of the same persisted floor claimed a new storey: " + message);
        assertFalse(message.contains("resolves player"), "persisted-floor refresh was presented as player-floor discovery");
    }

    @Test
    void persistedConnectorLookupUsesPhysicalMarkerPosition() {
        BlockPos floorCell = new BlockPos(10, 64, 20);
        BlockPos ladder = floorCell.east();
        StructureFloor floor = new StructureFloor(3, 0, new FloorGeometry(
                Set.of(new FloorGeometry.Cell(floorCell, 67)),
                List.of(new FloorConnector.Marker(ladder, FloorConnector.Type.LADDER, floorCell))));

        assertEquals(FloorConnector.Type.LADDER,
                BuildingDiagnostics.persistedConnectorType(floor, ladder));
        assertNull(BuildingDiagnostics.persistedConnectorType(floor, floorCell));
    }

    @Test
    void roomPlanProbeCapturesScannerFailureForDiagnostics() {
        IllegalArgumentException failure = new IllegalArgumentException("duplicate floor column");

        BuildingDiagnostics.PlanAttempt attempt = BuildingDiagnostics.planAttempt(() -> {
            throw failure;
        });

        assertNull(attempt.analysis());
        assertEquals(failure, attempt.failure());
    }

    @Test
    void roomPlanProbeReturnsSuccessfulAnalysisUnchanged() {
        RoomScanPlanner.Analysis expected = new RoomScanPlanner.Analysis(
                RoomScanPlan.addBuilding(new BlockPos(1, 64, 2)));

        BuildingDiagnostics.PlanAttempt attempt = BuildingDiagnostics.planAttempt(() -> expected);

        assertEquals(expected, attempt.analysis());
        assertNull(attempt.failure());
    }
}
