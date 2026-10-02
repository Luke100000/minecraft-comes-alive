package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoomWorkflowTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void ambiguousAddBuildingDefersCommitAndConfirmationReusesSameWorkflow() throws Exception {
        BlockPos source = new BlockPos(4, 8, 12);
        StubManager manager = new StubManager(new BuildingScanResult(
                Building.validationResult.SUCCESS,
                source,
                new Building(source),
                List.of("library", "music_store"),
                null));

        RoomWorkflow workflow = new RoomWorkflow(manager, null);

        RoomWorkflow.Outcome initial = workflow.commitAddition(manager.scan, null);
        assertEquals(RoomWorkflow.Status.REQUIRES_TYPE_SELECTION, initial.status());
        assertEquals(0, manager.commitCalls);

        RoomWorkflow.Outcome confirmed = workflow.commitAddition(manager.scan, "library");
        assertEquals(RoomWorkflow.Status.COMMITTED, confirmed.status());
        assertEquals(1, manager.commitCalls);
        assertEquals("library", manager.lastForcedType);
    }

    @Test
    void zeroOrOneEligibleTypeCommitsWithoutSelectionRoundTrip() {
        BlockPos source = new BlockPos(4, 8, 12);

        StubManager zero = new StubManager(scan(source, List.of()));
        RoomWorkflow.Outcome zeroOutcome = new RoomWorkflow(zero, null).commitAddition(zero.scan, null);
        assertEquals(RoomWorkflow.Status.COMMITTED, zeroOutcome.status());
        assertEquals(1, zero.commitCalls);

        StubManager one = new StubManager(scan(source, List.of("library")));
        RoomWorkflow.Outcome oneOutcome = new RoomWorkflow(one, null).commitAddition(one.scan, null);
        assertEquals(RoomWorkflow.Status.COMMITTED, oneOutcome.status());
        assertEquals(1, one.commitCalls);
    }

    @Test
    void scanRoomPlansInteractionOnlyOnce() {
        BlockPos source = new BlockPos(4, 8, 12);
        StructureFloor floor = new StructureFloor(3, 0,
                new FloorGeometry(List.of(new FloorGeometry.Cell(source, source.getY() + 3)), Map.of()));
        Structure structure = new Structure(7, source, List.of(floor));
        CountingVillage village = new CountingVillage(structure, floor);
        CountingManager manager = new CountingManager(village);

        RoomWorkflow.Outcome outcome = new RoomWorkflow(manager, null).scanRoom(source, -1, null);

        assertEquals(RoomWorkflow.Status.FAILED, outcome.status());
        assertEquals(1, village.resolveCalls);
    }

    @Test
    void addRoomTypeConfirmationRejectsRoomThatAppearedAfterPrompt() {
        BlockPos source = new BlockPos(4, 8, 12);
        StructureFloor floor = new StructureFloor(3, 0,
                new FloorGeometry(List.of(new FloorGeometry.Cell(source, source.getY() + 3)), Map.of()));
        Structure structure = new Structure(7, source, List.of(floor));
        Building room = new Building(source);
        room.setId(21);
        room.setStructureId(structure.getId());
        room.setFloorId(floor.id());
        StaleRoomVillage village = new StaleRoomVillage(structure, floor, room);

        RoomWorkflow.Outcome outcome = new RoomWorkflow(new CountingManager(village), null)
                .scanRoom(source, -1, "library");

        assertEquals(RoomWorkflow.Status.FAILED, outcome.status());
        assertEquals(Building.validationResult.IDENTICAL, outcome.result());
        assertEquals(-1, outcome.expectedTargetId());
    }

    private static BuildingScanResult scan(BlockPos source, List<String> types) {
        return new BuildingScanResult(
                Building.validationResult.SUCCESS,
                source,
                new Building(source),
                types,
                null);
    }

    private static final class StubManager extends VillageManager {
        private final BuildingScanResult scan;
        private int commitCalls;
        private String lastForcedType;

        private StubManager(BuildingScanResult scan) {
            super(null);
            this.scan = scan;
        }

        @Override
        public Building.validationResult commitRoomAddition(BuildingScanResult scan, String forcedType) {
            commitCalls++;
            lastForcedType = forcedType;
            return Building.validationResult.SUCCESS;
        }
    }

    private static final class CountingManager extends VillageManager {
        private final Village village;

        private CountingManager(Village village) {
            super(null);
            this.village = village;
        }

        @Override
        public Optional<Village> findNearestVillage(BlockPos pos, int margin) {
            return Optional.of(village);
        }
    }

    private static final class CountingVillage extends Village {
        private final Structure structure;
        private final StructureFloor floor;
        private int resolveCalls;

        private CountingVillage(Structure structure, StructureFloor floor) {
            super(1, null);
            this.structure = structure;
            this.floor = floor;
        }

        @Override
        Optional<ResolvedInteraction> resolveInteractionPosition(BlockPos pos) {
            resolveCalls++;
            return Optional.of(new ResolvedInteraction(
                    structure, new Structure.InteractionPosition(floor, null)));
        }
    }

    private static final class StaleRoomVillage extends Village {
        private final Structure structure;
        private final StructureFloor floor;
        private final Building room;

        private StaleRoomVillage(Structure structure, StructureFloor floor, Building room) {
            super(1, null);
            this.structure = structure;
            this.floor = floor;
            this.room = room;
        }

        @Override
        Optional<ResolvedInteraction> resolveInteractionPosition(BlockPos pos) {
            return Optional.of(new ResolvedInteraction(
                    structure, new Structure.InteractionPosition(floor, room)));
        }

        @Override
        public Optional<Building> getBuilding(int id) {
            return room.getId() == id ? Optional.of(room) : Optional.empty();
        }
    }
}
