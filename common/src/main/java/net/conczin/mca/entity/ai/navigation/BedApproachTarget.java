package net.conczin.mca.entity.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Represents a bed as a semantic interaction target rather than a single navigation
 * coordinate. The logical position remains the bed head while pathfinding may choose
 * any physically valid bedside approach.
 */
public final class BedApproachTarget implements MultiTargetPositionTracker {
    private final BlockPos bedHead;
    private final Vec3 centerPosition;

    private BedApproachTarget(BlockPos bedHead) {
        this.bedHead = bedHead.immutable();
        this.centerPosition = Vec3.atCenterOf(bedHead);
    }

    public static Optional<BedApproachTarget> create(ServerLevel level, BlockPos bedPos) {
        BlockState state = level.getBlockState(bedPos);
        if (!isBedState(state)) {
            return Optional.empty();
        }

        Direction facing = state.getValue(BedBlock.FACING);
        BlockPos head = state.getValue(BedBlock.PART) == BedPart.HEAD
                ? bedPos
                : bedPos.relative(facing);
        BlockState headState = level.getBlockState(head);
        return isBedHead(headState) && headState.getValue(BedBlock.FACING) == facing
                ? Optional.of(new BedApproachTarget(head))
                : Optional.empty();
    }

    @Override
    public Vec3 currentPosition() {
        return centerPosition;
    }

    @Override
    public BlockPos currentBlockPosition() {
        return bedHead;
    }

    @Override
    public boolean isVisibleBy(LivingEntity livingEntity) {
        return true;
    }

    @Override
    public Set<BlockPos> getPathTargets(Mob mob) {
        BlockState bedState = getBedHeadState(mob);
        if (bedState == null) {
            return Set.of();
        }

        Set<BlockPos> targets = new HashSet<>();
        for (BlockPos approach : getApproachPositions(bedState)) {
            BlockPos target = resolvePathTarget(mob, approach);
            if (target != null) {
                targets.add(target);
            }
        }
        return targets;
    }

    @Override
    public boolean isReached(Mob mob, int closeEnoughDistance) {
        BlockState bedState = getBedHeadState(mob);
        if (bedState == null) {
            return false;
        }

        BlockPos navigationPos = navigationPosition(mob);
        for (BlockPos approach : getApproachPositions(bedState)) {
            if (isAtApproach(mob, navigationPos, approach, closeEnoughDistance)) {
                return true;
            }
        }
        return false;
    }

    public boolean isAtBehindFootApproach(Mob mob) {
        BlockState bedState = getBedHeadState(mob);
        if (bedState == null) {
            return false;
        }

        return isAtApproach(mob, navigationPosition(mob), getBehindFootPosition(bedState), 0);
    }

    @Nullable
    private BlockState getBedHeadState(Mob mob) {
        BlockState state = mob.level().getBlockState(bedHead);
        return isBedHead(state) ? state : null;
    }

    private List<BlockPos> getApproachPositions(BlockState bedState) {
        Direction facing = bedState.getValue(BedBlock.FACING);
        BlockPos foot = bedHead.relative(facing.getOpposite());
        return List.of(
                bedHead.relative(facing),
                bedHead.relative(facing.getClockWise()),
                bedHead.relative(facing.getCounterClockWise()),
                foot.relative(facing.getClockWise()),
                foot.relative(facing.getCounterClockWise()),
                getBehindFootPosition(bedState)
        );
    }

    private BlockPos getBehindFootPosition(BlockState bedState) {
        return bedHead.relative(bedState.getValue(BedBlock.FACING).getOpposite(), 2);
    }

    @Nullable
    private static BlockPos resolvePathTarget(Mob mob, BlockPos approach) {
        Vec3 standPosition = findSafeApproachPosition(mob, approach);
        if (standPosition == null) {
            BlockState state = mob.level().getBlockState(approach);
            if (state.getCollisionShape(mob.level(), approach).isEmpty()
                    || state.isCollisionShapeFullBlock(mob.level(), approach)) {
                return null;
            }

            // Partial-height collision geometry may put the physical feet position in
            // the block above. Full blocks are deliberately excluded so furniture such
            // as bookshelves cannot become a valid "stand on top" bed approach.
            standPosition = findSafeApproachPosition(mob, approach.above());
        }

        if (standPosition == null) {
            return null;
        }

        BlockPos pathTarget = BlockPos.containing(
                standPosition.x,
                standPosition.y + 0.5D,
                standPosition.z
        );
        PathType pathType = WalkNodeEvaluator.getPathTypeStatic(mob, pathTarget);
        return pathType != PathType.OPEN && mob.getPathfindingMalus(pathType) >= 0.0F
                ? pathTarget
                : null;
    }

    @Nullable
    private static Vec3 findSafeApproachPosition(Mob mob, BlockPos approach) {
        Vec3 standPosition = DismountHelper.findSafeDismountLocation(
                mob.getType(), mob.level(), approach, true
        );
        if (standPosition == null) {
            return null;
        }

        AABB box = mob.getBoundingBox().move(
                standPosition.x - mob.getX(),
                standPosition.y - mob.getY(),
                standPosition.z - mob.getZ()
        );
        return mob.level().noCollision(mob, box) ? standPosition : null;
    }

    private static BlockPos navigationPosition(Mob mob) {
        return BlockPos.containing(mob.getX(), mob.getY() + 0.5D, mob.getZ());
    }

    private static boolean isBedState(BlockState state) {
        return state.is(BlockTags.BEDS)
                && state.hasProperty(BedBlock.FACING)
                && state.hasProperty(BedBlock.PART);
    }

    private static boolean isBedHead(BlockState state) {
        return isBedState(state) && state.getValue(BedBlock.PART) == BedPart.HEAD;
    }

    private static boolean isAtApproach(Mob mob, BlockPos navigationPos, BlockPos approach, int closeEnoughDistance) {
        int horizontalDistance = Math.abs(approach.getX() - navigationPos.getX())
                + Math.abs(approach.getZ() - navigationPos.getZ());
        if (horizontalDistance > closeEnoughDistance) {
            return false;
        }

        BlockPos pathTarget = resolvePathTarget(mob, approach);
        return pathTarget != null
                && pathTarget.distManhattan(navigationPos) <= closeEnoughDistance;
    }
}
