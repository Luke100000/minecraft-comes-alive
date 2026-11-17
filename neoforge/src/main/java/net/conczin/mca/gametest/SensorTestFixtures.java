package net.conczin.mca.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;

import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Own only the blocks changed by these sensor fixtures, including their bed POIs. */
public final class SensorTestFixtures {
    private static final Map<GameTestHelper, Map<BlockPos, BlockState>> ORIGINALS = new IdentityHashMap<>();

    public static void prepare(GameTestHelper helper, BlockPos center, int radius) {
        var original = ORIGINALS.computeIfAbsent(helper, ignored -> new LinkedHashMap<>());
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -1, -radius), center.offset(radius, 2, radius))) {
            helper.assertTrue(helper.getBounds().contains(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5),
                    "sensor fixture terrain must fit inside its allocated arena");
            original.putIfAbsent(pos.immutable(), helper.getLevel().getBlockState(pos));
        }
        GameTestTerrain.prepareFlatArea(helper, center, radius, 3);
    }

    public static void restore(GameTestHelper helper) {
        var original = ORIGINALS.remove(helper);
        if (original != null) original.forEach((pos, state) -> helper.getLevel().setBlock(pos, state, 3));
    }

    public static void restoreAll() {
        List.copyOf(ORIGINALS.keySet()).forEach(SensorTestFixtures::restore);
    }
}
