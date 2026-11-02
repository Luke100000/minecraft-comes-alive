package net.conczin.mca.server.world.data;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SelectedFloorScannerCachingTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void rejectedFlightCachesEveryVisitedCell() throws ReflectiveOperationException {
        OneStepWorld world = new OneStepWorld();
        var observation = new SelectedFloorScanner.Observation(world, 64, 64);
        var lower = observation.surfaceCell(new BlockPos(0, 64, 0)).orElseThrow();
        var upper = observation.surfaceCell(new BlockPos(1, 65, 0)).orElseThrow();

        Field ownershipField = SelectedFloorScanner.Observation.class.getDeclaredField("transitionsOwner");
        ownershipField.setAccessible(true);
        Object ownership = ownershipField.get(observation);
        Method owner = ownership.getClass().getDeclaredMethod("owner", SelectedFloorScanner.SurfaceCell.class);
        owner.setAccessible(true);

        int beforeDiscovery = world.reads;
        assertNull(owner.invoke(ownership, lower));
        int firstDiscoveryReads = world.reads;
        assertTrue(firstDiscoveryReads > beforeDiscovery, "the initial flight proof must inspect the world");

        assertNull(owner.invoke(ownership, upper));
        assertEquals(firstDiscoveryReads, world.reads,
                "a second cell in the same rejected flight must reuse the negative result");
    }

    /** Two supported cells joined by an ordinary one-block step, below a physical roof. */
    private static final class OneStepWorld implements BlockGetter {
        private int reads;

        @Override
        public BlockState getBlockState(BlockPos pos) {
            reads++;
            boolean column = pos.getZ() == 0 && (pos.getX() == 0 || pos.getX() == 1);
            return column && (pos.getY() == 63 + pos.getX() || pos.getY() == 68)
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }

        @Override
        public FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) { return null; }

        @Override
        public int getHeight() { return 128; }

        @Override
        public int getMinBuildHeight() { return 0; }
    }
}
