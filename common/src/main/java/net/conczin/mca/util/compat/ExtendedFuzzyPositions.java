package net.conczin.mca.util.compat;

import net.minecraft.core.BlockPos;

import java.util.function.Predicate;

public class ExtendedFuzzyPositions {
    public static BlockPos downWhile(BlockPos pos, int minY, Predicate<BlockPos> condition) {
        if (condition.test(pos)) {
            BlockPos blockPos = pos.below();
            while (blockPos.getY() > minY && condition.test(blockPos)) {
                blockPos = blockPos.below();
            }
            return blockPos;
        }
        return pos;
    }
}