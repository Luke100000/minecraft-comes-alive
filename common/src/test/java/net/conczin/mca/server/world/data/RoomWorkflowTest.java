package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RoomWorkflowTest {
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

        RoomWorkflow.Outcome initial = workflow.addBuilding(source, null);
        assertEquals(RoomWorkflow.Status.REQUIRES_TYPE_SELECTION, initial.status());
        assertEquals(0, manager.commitCalls);

        RoomWorkflow.Outcome confirmed = workflow.addBuilding(source, "library");
        assertEquals(RoomWorkflow.Status.COMMITTED, confirmed.status());
        assertEquals(1, manager.commitCalls);
        assertEquals("library", manager.lastForcedType);
    }

    @Test
    void zeroOrOneEligibleTypeCommitsWithoutSelectionRoundTrip() {
        BlockPos source = new BlockPos(4, 8, 12);

        StubManager zero = new StubManager(scan(source, List.of()));
        RoomWorkflow.Outcome zeroOutcome = new RoomWorkflow(zero, null).addBuilding(source, null);
        assertEquals(RoomWorkflow.Status.COMMITTED, zeroOutcome.status());
        assertEquals(1, zero.commitCalls);

        StubManager one = new StubManager(scan(source, List.of("library")));
        RoomWorkflow.Outcome oneOutcome = new RoomWorkflow(one, null).addBuilding(source, null);
        assertEquals(RoomWorkflow.Status.COMMITTED, oneOutcome.status());
        assertEquals(1, one.commitCalls);
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
        public BuildingScanResult analyzeBuildingAddition(BlockPos pos) {
            return scan;
        }

        @Override
        public Building.validationResult commitRoomAddition(BuildingScanResult scan, String forcedType) {
            commitCalls++;
            lastForcedType = forcedType;
            return Building.validationResult.SUCCESS;
        }
    }
}
