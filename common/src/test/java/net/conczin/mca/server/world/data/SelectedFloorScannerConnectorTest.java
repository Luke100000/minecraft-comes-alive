package net.conczin.mca.server.world.data;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;

class SelectedFloorScannerConnectorTest {
    @Test
    void equivalentInteriorSourcesKeepCanonicalFloorAndAction() {
        TallRoom world = new TallRoom();
        BlockPos ordinary = new BlockPos(2, 64, 2);
        BlockPos slab = new BlockPos(4, 64, 4);
        BlockPos shelf = new BlockPos(6, 64, 6);
        BlockPos bed = new BlockPos(8, 64, 8);
        world.put(slab.below(), Blocks.STONE_SLAB.defaultBlockState());
        world.put(shelf.above(), Blocks.LADDER.defaultBlockState());
        var bedState = Blocks.WHITE_BED.defaultBlockState()
                .setValue(BedBlock.FACING, net.minecraft.core.Direction.EAST);
        world.put(bed, bedState.setValue(BedBlock.PART, BedPart.FOOT));
        world.put(bed.east(), bedState.setValue(BedBlock.PART, BedPart.HEAD));
        var baseline = SelectedFloorScanner.scan(world, ordinary, 2048, 64);
        assertEquals(Building.validationResult.SUCCESS, baseline.result());
        var roomCells = baseline.floor().cells().stream().map(FloorGeometry.Cell::feet).collect(Collectors.toSet());
        Village registered = new Village(1, null);
        Building room = new Building(ordinary);
        room.setId(42);
        room.setStructureId(10);
        room.setFloorId(0);
        var bounds = FloorGeometry.bounds(baseline.floor().cells(), 0);
        room.setGeometry(bounds.min(), bounds.max(), roomCells);
        registered.registerStructure(new Structure(10, ordinary,
                java.util.List.of(new StructureFloor(0, 0, baseline.anchorY(), baseline.floor()))), room);
        BlockPos[] sources = {ordinary, slab, shelf, bed, bed.above()};
        for (boolean reverse : new boolean[]{false, true}) {
            var observation = new SelectedFloorScanner.Observation(world, 2048, 64);
            for (int i = 0; i < sources.length; i++) {
                BlockPos source = sources[reverse ? sources.length - 1 - i : i];
                var scan = observation.selected(source);
                assertEquals(Building.validationResult.SUCCESS, scan.result(), source.toString());
                assertTrue(baseline.floor().sameExactGeometry(scan.floor()), source.toString());
                assertEquals(baseline.anchorY(), scan.anchorY());
                assertEquals(roomCells, RoomPartitioner.select(scan.seed(), scan.floor(),
                        BuildingRoomScanner.components(world, scan)).floorCells());
                var fresh = new StructureScanner.FloorObservation(scan, Set.of(), Set.of());
                assertEquals(Village.RoomScanMode.ADD_BUILDING,
                        RoomScanPlanner.analyzeFresh(new Village(2, null), source, fresh).plan().mode());
                var plan = RoomScanPlanner.analyzeFresh(registered, source, fresh).plan();
                assertEquals(Village.RoomScanMode.UPDATE_ROOM, plan.mode(), source.toString());
                assertEquals(42, plan.currentRoom().orElseThrow().getId());
                assertEquals(10, plan.targetStructureId());
                assertEquals(0, plan.targetFloorId());
            }
        }
    }

    @Test
    void aBedInATallRoomDoesNotCreateAdditionalSurfaceCells() {
        TallRoom world = new TallRoom();
        BlockPos source = new BlockPos(8, 64, 8);
        var before = SelectedFloorScanner.scan(world, source, 2048, 64);
        assertEquals(Building.validationResult.SUCCESS, before.result());
        var bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, net.minecraft.core.Direction.EAST);
        world.put(source, bed.setValue(BedBlock.PART, BedPart.FOOT));
        world.put(source.east(), bed.setValue(BedBlock.PART, BedPart.HEAD));

        for (BlockPos selected : new BlockPos[]{source, source.above(), source.north()}) {
            var after = SelectedFloorScanner.scan(world, selected, 2048, 64);
            assertEquals(Building.validationResult.SUCCESS, after.result());
            assertTrue(before.floor().sameExactGeometry(after.floor()),
                    "a bed created duplicate cells for the same walking surface");
            var component = RoomPartitioner.select(after.seed(), after.floor(), BuildingRoomScanner.components(world, after));
            assertEquals(256, component.area());
        }
    }

    @Test
    void aFloatingBottomSlabFloorRetainsItsOnlySupportedSurface() {
        TallRoom world = new TallRoom();
        for (int x = 1; x <= 16; x++) {
            for (int z = 1; z <= 16; z++) {
                world.put(new BlockPos(x, 63, z), Blocks.STONE_SLAB.defaultBlockState());
            }
        }
        var scan = SelectedFloorScanner.scan(world, new BlockPos(8, 64, 8), 2048, 64);
        assertEquals(Building.validationResult.SUCCESS, scan.result());
        assertEquals(256, scan.floor().cells().size());
        assertTrue(scan.floor().cells().stream().allMatch(cell -> cell.feet().getY() == 64));
    }

    @Test
    void selectingBelowWallMountedFurnitureRetainsTheEnclosedFloor() {
        TallRoom world = new TallRoom();
        BlockPos source = new BlockPos(8, 64, 8);
        // A thin wall-mounted collision occupies the head block, as with the reported shelf.
        world.put(source.above(), Blocks.LADDER.defaultBlockState());
        var adjacent = SelectedFloorScanner.scan(world, source.east(), 2048, 64);
        assertEquals(Building.validationResult.SUCCESS, adjacent.result());
        assertTrue(adjacent.floor().cellAt(source).isPresent(), "fixture must own the obstructed column");

        var selected = SelectedFloorScanner.scan(world, source, 2048, 64);

        assertEquals(Building.validationResult.SUCCESS, selected.result());
        assertTrue(adjacent.floor().sameExactGeometry(selected.floor()));
        assertEquals(source, selected.seed(), "interaction must stay in the selected floor column");

        var addition = StructureScanner.scanNewStructure(world, source, java.util.List.of(),
                new SelectedFloorScanner.Observation(world, 2048, 64));
        assertEquals(Building.validationResult.SUCCESS, addition.result());
        assertEquals(source, addition.source());
    }

    @Test
    void partialHeadObstructionCannotBorrowTheAdjacentFloorAtAnotherHeight() {
        TallRoom world = new TallRoom();
        BlockPos source = new BlockPos(8, 65, 8);
        world.put(source.below(), Blocks.STONE.defaultBlockState());
        world.put(source.above(), Blocks.LADDER.defaultBlockState());

        assertEquals(Building.validationResult.NOT_IN_BUILDING,
                SelectedFloorScanner.scan(world, source, 2048, 64).result());
    }

    @Test
    void rejectedObstructionDoesNotRepeatedlyDiscoverTheSameAdjacentFloor() {
        BlockPos source = new BlockPos(8, 65, 8);
        TallRoom ordinary = new TallRoom();
        ordinary.put(source.below(), Blocks.STONE.defaultBlockState());
        ordinary.put(source.above(), Blocks.LADDER.defaultBlockState());
        assertEquals(Building.validationResult.SUCCESS,
                SelectedFloorScanner.scan(ordinary, source.east().below(), 2048, 64).result());

        TallRoom rejected = new TallRoom();
        rejected.put(source.below(), Blocks.STONE.defaultBlockState());
        rejected.put(source.above(), Blocks.LADDER.defaultBlockState());
        assertEquals(Building.validationResult.NOT_IN_BUILDING,
                SelectedFloorScanner.scan(rejected, source, 2048, 64).result());
        assertTrue(rejected.totalBlockReads <= ordinary.totalBlockReads * 3 / 2,
                "rejected obstruction repeated floor discovery: ordinary=" + ordinary.totalBlockReads
                        + " rejected=" + rejected.totalBlockReads);
    }

    @Test
    void solidOrFloodedHeadroomCannotBorrowTheAdjacentRoom() {
        for (BlockState obstruction : new BlockState[]{Blocks.STONE.defaultBlockState(), Blocks.WATER.defaultBlockState()}) {
            TallRoom world = new TallRoom();
            BlockPos source = new BlockPos(8, 64, 8);
            world.put(source.above(), obstruction);

            assertEquals(Building.validationResult.NOT_IN_BUILDING,
                    SelectedFloorScanner.scan(world, source, 2048, 64).result());
        }
    }

    @Test
    void buildingFallbackReusesDiscoveryButTheNextOperationReadsFreshBlocks() {
        TallRoom world = new TallRoom();
        BlockPos source = new BlockPos(8, 64, 8);
        var operation = new SelectedFloorScanner.Observation(world, 2048, 64);
        var attachment = StructureScanner.observeFloor(world, source, java.util.List.of(), operation).orElseThrow();
        int readsAfterDiscovery = world.middleLayerReads;

        var addition = StructureScanner.scanNewStructure(world, source, java.util.List.of(), operation);

        assertEquals(Building.validationResult.SUCCESS, addition.result());
        assertSame(attachment.scan(), addition.scan(), "fallback rediscovered the same selected floor");
        assertEquals(readsAfterDiscovery, world.middleLayerReads, "fallback repeated world discovery");
        int feetReadsBeforeChange = world.feetLayerReads;
        world.put(source.above(), Blocks.STONE.defaultBlockState());
        var next = StructureScanner.observeFloor(world, source, java.util.List.of());
        assertTrue(next.isEmpty() || !next.orElseThrow().scan().floor().sameExactGeometry(addition.scannedFloor()),
                "later operation reused the earlier world's geometry");
        assertTrue(world.feetLayerReads > feetReadsBeforeChange, "later operation did not read the world afresh");
    }

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
    void finiteRoofedPlatformDoesNotFloodAirToTheConfiguredRadius() {
        // Repeating stair/door classification, or restoring the strict air flood
        // before finite-surface validation, must fail this budget without changing geometry.
        var source = new BlockPos(3, 64, 3);
        RoofedPlatform nearby = new RoofedPlatform();
        RoofedPlatform wideRadius = new RoofedPlatform();
        var local = SelectedFloorScanner.scan(nearby, source, 8192, 16);
        var configured = SelectedFloorScanner.scan(wideRadius, source, 8192, 320);

        assertEquals(Building.validationResult.SUCCESS, local.result());
        assertEquals(Building.validationResult.SUCCESS, configured.result());
        assertEquals(64, configured.floor().cells().size());
        assertTrue(local.floor().sameExactGeometry(configured.floor()),
                "search radius changed the finite platform's canonical geometry");
        assertTrue(wideRadius.blockReads <= 2_600,
                "finite 64-cell platform repeated classification or flooded exterior air: "
                        + wideRadius.blockReads + " block reads");
        assertTrue(wideRadius.blockReads <= nearby.blockReads + 1_000,
                "larger search radius expanded work on an unchanged finite platform: "
                        + nearby.blockReads + " -> " + wideRadius.blockReads);
        System.out.println("Finite platform block reads: radius16=" + nearby.blockReads
                + " radius320=" + wideRadius.blockReads);
    }

    @Test
    void laterScanObservesNewExteriorOpening() {
        TallRoom world = new TallRoom();
        assertEquals(Building.validationResult.SUCCESS,
                SelectedFloorScanner.scan(world, new BlockPos(8, 64, 8), 2048, 64).result());
        world.put(new BlockPos(0, 64, 8), Blocks.AIR.defaultBlockState());
        world.put(new BlockPos(0, 65, 8), Blocks.AIR.defaultBlockState());

        var openWindow = SelectedFloorScanner.scan(world, new BlockPos(8, 64, 8), 2048, 64);
        assertEquals(Building.validationResult.SUCCESS, openWindow.result());
        assertTrue(openWindow.floor().cellAt(new BlockPos(0, 64, 8)).isPresent(),
                "later observation retained the old solid wall instead of the new opening");
        // Widen the framed entrance to an unframed breach before adding broad terrain.
        // The narrow framed entrance is intentionally valid even beside that terrain.
        for (int z : new int[]{7, 9}) {
            world.put(new BlockPos(0, 64, z), Blocks.AIR.defaultBlockState());
            world.put(new BlockPos(0, 65, z), Blocks.AIR.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(-10, 63, 0, -1, 63, 17)) {
            world.put(pos, Blocks.STONE.defaultBlockState());
        }
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

    private static final class RoofedPlatform implements BlockGetter {
        private int blockReads;

        @Override
        public BlockState getBlockState(BlockPos pos) {
            blockReads++;
            boolean inside = pos.getX() >= 0 && pos.getX() < 8 && pos.getZ() >= 0 && pos.getZ() < 8;
            return inside && (pos.getY() == 63 || pos.getY() == 66)
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }

        @Override
        public int getHeight() { return 128; }

        @Override
        public int getMinY() { return 0; }
    }

    private static final class TallRoom implements BlockGetter {
        private final Map<BlockPos, BlockState> overrides = new HashMap<>();
        private int middleLayerReads;
        private int feetLayerReads;
        private int totalBlockReads;

        private void put(BlockPos pos, BlockState state) {
            overrides.put(pos.immutable(), state);
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            totalBlockReads++;
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
