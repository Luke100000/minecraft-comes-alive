package net.conczin.mca.server.world.data;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectedFloorScannerConnectorTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void tallRoomDoesNotRepeatedlySearchBelowEveryConnectorProbe() {
        TallRoom world = new TallRoom();

        var scan = SelectedFloorScanner.scan(world, new BlockPos(8, 64, 8), 2048, 64);

        assertEquals(Building.validationResult.SUCCESS, scan.result());
        assertEquals(256, scan.floor().cells().size());
        assertTrue(scan.floor().connectorMarkers().isEmpty());
        assertTrue(world.middleLayerReads <= 2560,
                "empty upper room layer was repeatedly probed: " + world.middleLayerReads + " block reads");
        System.out.println("Tall room middle-layer block reads: " + world.middleLayerReads);
    }

    @Test
    void overlappingConnectorColumnsDoNotRescanTheSameEmptyHeight() {
        TallRoom world = new TallRoom();

        var scan = SelectedFloorScanner.scan(world, new BlockPos(8, 64, 8), 2048, 64);

        assertEquals(Building.validationResult.SUCCESS, scan.result());
        assertEquals(256, scan.floor().cells().size());
        assertTrue(scan.floor().connectorMarkers().isEmpty());
        // 256 interior columns plus 64 perimeter columns are enough to inspect
        // this layer. Ceiling discovery also reads the 256 interior columns.
        assertTrue(world.middleLayerReads <= 700,
                "overlapping connector bands repeated upper-layer reads: " + world.middleLayerReads);
    }

    @Test
    void roomTopologyDoesNotStoreBothDirectionsOfEveryWalkableEdge() {
        TallRoom world = new TallRoom();

        var scan = SelectedFloorScanner.scan(world, new BlockPos(8, 64, 8), 2048, 64);

        assertEquals(Building.validationResult.SUCCESS, scan.result());
        assertEquals(256, scan.floor().cells().size());
        // A 16x16 grid has 16*15 horizontal and 16*15 vertical room edges.
        assertEquals(480, scan.transitions().size(), "Room topology stored reversed duplicate edges");
        var neighbors = SelectedFloorScanner.Transition.neighborIndex(scan.transitions());
        assertEquals(Set.of(new BlockPos(2, 64, 1), new BlockPos(1, 64, 2)),
                Set.copyOf(neighbors.get(new BlockPos(1, 64, 1))));
        assertEquals(256, neighbors.size());
    }

    @Test
    void repeatedSurfaceDiscoveryDoesNotRereadTheSameFloorCandidates() {
        TallRoom world = new TallRoom();

        var scan = SelectedFloorScanner.scan(world, new BlockPos(8, 64, 8), 2048, 64);

        assertEquals(Building.validationResult.SUCCESS, scan.result());
        assertEquals(256, scan.floor().cells().size());
        assertTrue(world.feetLayerReads <= 7500,
                "surface discovery repeatedly reread floor candidates: " + world.feetLayerReads);
    }

    @Test
    void laterScanObservesNewExteriorOpening() {
        TallRoom world = new TallRoom();
        assertEquals(Building.validationResult.SUCCESS,
                SelectedFloorScanner.scan(world, new BlockPos(8, 64, 8), 2048, 64).result());
        world.put(new BlockPos(0, 64, 8), Blocks.AIR.defaultBlockState());
        world.put(new BlockPos(0, 65, 8), Blocks.AIR.defaultBlockState());

        assertEquals(Building.validationResult.NOT_IN_BUILDING,
                SelectedFloorScanner.scan(world, new BlockPos(8, 64, 8), 2048, 64).result());
    }

    @Test
    void closedRoomExteriorChecksDoNotRepeatLandingDiscovery() {
        TallRoom world = new TallRoom();

        var scan = SelectedFloorScanner.scan(world, new BlockPos(8, 64, 8), 2048, 64);

        assertEquals(Building.validationResult.SUCCESS, scan.result());
        assertEquals(256, scan.floor().cells().size());
        assertTrue(scan.floor().connectorMarkers().isEmpty());
        // The full scan includes geometry and connector reads; the old duplicated
        // exterior landing probes alone push this fixture above 18,000 reads.
        assertTrue(world.feetLayerReads <= 17000,
                "floor landing discovery repeated too many reads: " + world.feetLayerReads);
        System.out.println("Closed room feet-layer block reads: " + world.feetLayerReads);
    }

    @Test
    void connectorCollectionRetainsHighLaddersButRejectsDecorativeTrapdoors() {
        TallRoom world = new TallRoom();
        BlockPos ladder = new BlockPos(0, 78, 8);
        world.put(ladder, Blocks.LADDER.defaultBlockState());
        world.put(ladder.above(), Blocks.OAK_TRAPDOOR.defaultBlockState());
        world.put(new BlockPos(0, 76, 10), Blocks.OAK_TRAPDOOR.defaultBlockState());

        var scan = SelectedFloorScanner.scan(world, new BlockPos(8, 64, 8), 2048, 64);

        assertEquals(Building.validationResult.SUCCESS, scan.result());
        assertEquals(256, scan.floor().cells().size());
        assertEquals(Set.of(ladder), scan.floor().connectorMarkers().stream()
                .map(FloorConnector.Marker::pos).collect(Collectors.toSet()));
    }

    @Test
    void connectorCollectionRetainsShortGapBelowFloorSupportLayer() {
        TallRoom world = new TallRoom();
        BlockPos opening = new BlockPos(4, 64, 8);
        BlockPos ladder = opening.below(2);
        world.put(opening.below(), Blocks.AIR.defaultBlockState());
        world.put(ladder, Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING,
                net.minecraft.core.Direction.EAST));

        var scan = SelectedFloorScanner.scan(world, new BlockPos(8, 64, 8), 2048, 64);

        assertEquals(Building.validationResult.SUCCESS, scan.result());
        assertEquals(256, scan.floor().cells().size());
        assertTrue(scan.floor().cellAt(opening).isPresent());
        assertEquals(Set.of(ladder), scan.floor().connectorMarkers().stream()
                .map(FloorConnector.Marker::pos).collect(Collectors.toSet()));
    }

    private static final class TallRoom implements BlockGetter {
        private final Map<BlockPos, BlockState> overrides = new HashMap<>();
        private int middleLayerReads;
        private int feetLayerReads;

        private void put(BlockPos pos, BlockState state) {
            overrides.put(pos.immutable(), state);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (pos.getY() == 80) middleLayerReads++;
            if (pos.getY() == 64) feetLayerReads++;
            BlockState override = overrides.get(pos);
            if (override != null) return override;
            boolean inBounds = pos.getX() >= 0 && pos.getX() <= 17
                    && pos.getZ() >= 0 && pos.getZ() <= 17;
            boolean wall = pos.getX() == 0 || pos.getX() == 17 || pos.getZ() == 0 || pos.getZ() == 17;
            return inBounds && (pos.getY() == 63 || pos.getY() == 88
                    || wall && pos.getY() >= 64 && pos.getY() < 88)
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public int getHeight() {
            return 128;
        }

        @Override
        public int getMinY() {
            return 0;
        }
    }
}
