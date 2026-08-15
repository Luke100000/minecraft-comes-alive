package net.conczin.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Mixin(MoveToTargetSink.class)
abstract class MixinMoveToTargetSink {
    @WrapOperation(
            method = "tryComputePath(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/entity/ai/memory/WalkTarget;J)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/navigation/PathNavigation;createPath(Lnet/minecraft/core/BlockPos;I)Lnet/minecraft/world/level/pathfinder/Path;"
            )
    )
    @Nullable
    private Path mca$createBedApproachPath(
            PathNavigation navigation,
            BlockPos target,
            int reachRange,
            Operation<Path> original,
            Mob mob,
            WalkTarget walkTarget,
            long gameTime
    ) {
        BlockState bedState = mca$getTargetBedState(mob, target);
        if (bedState == null) {
            return original.call(navigation, target, reachRange);
        }

        Set<BlockPos> approachTargets = mca$getBedApproachPathTargets(mob, target, bedState);
        if (approachTargets.isEmpty()) {
            // Let vanilla WanderAroundTask use its random fallback instead of repeatedly
            // targeting an occupied bed-adjacent block.
            return null;
        }

        // Keep partial paths too. Vanilla WanderAroundTask will follow a partial path and
        // retry later, which lets villagers make progress toward home even when the final
        // standing position is temporarily unreachable.
        return navigation.createPath(approachTargets, 0);
    }

    @ModifyReturnValue(
            method = "reachedTarget(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/entity/ai/memory/WalkTarget;)Z",
            at = @At("RETURN")
    )
    private boolean mca$acceptReachedBedApproach(
            boolean original,
            Mob mob,
            WalkTarget walkTarget
    ) {
        if (original) {
            return true;
        }

        BlockPos bedPos = walkTarget.getTarget().currentBlockPosition();
        BlockState bedState = mca$getTargetBedState(mob, bedPos);
        if (bedState == null) {
            return false;
        }

        // Mirror vanilla's on-ground node-Y calculation, then use the same resolver
        // that produced the path targets so reachability has one source of truth.
        BlockPos navigationPos = BlockPos.containing(mob.getX(), mob.getY() + 0.5D, mob.getZ());
        for (BlockPos approach : mca$getBedApproachPositions(bedPos, bedState)) {
            if (approach.getX() != navigationPos.getX() || approach.getZ() != navigationPos.getZ()) {
                continue;
            }

            return navigationPos.equals(mca$resolveBedApproachPathTarget(mob, approach));
        }
        return false;
    }

    @Unique
    @Nullable
    private static BlockState mca$getTargetBedState(Mob mob, BlockPos target) {
        if (!(mob instanceof VillagerEntityMCA villager) || villager.isSleeping()) {
            return null;
        }

        Brain<?> brain = villager.getBrain();
        if (!brain.isActive(Activity.REST)) {
            return null;
        }

        Optional<GlobalPos> homeMemory = brain.getMemoryInternal(MemoryModuleType.HOME);
        if (homeMemory.isEmpty()) {
            return null;
        }

        GlobalPos home = homeMemory.get();
        if (home.dimension() != mob.level().dimension()
                || !home.pos().equals(target)) {
            return null;
        }

        BlockState state = mob.level().getBlockState(target);
        return state.is(BlockTags.BEDS)
                && state.hasProperty(BedBlock.FACING)
                && state.hasProperty(BedBlock.PART)
                && state.getValue(BedBlock.PART) == BedPart.HEAD
                ? state
                : null;
    }

    @Unique
    private static Set<BlockPos> mca$getBedApproachPathTargets(Mob mob, BlockPos bedPos, BlockState bedState) {
        Set<BlockPos> targets = new HashSet<>();
        for (BlockPos approach : mca$getBedApproachPositions(bedPos, bedState)) {
            BlockPos target = mca$resolveBedApproachPathTarget(mob, approach);
            if (target != null) {
                targets.add(target);
            }
        }
        return targets;
    }

    @Unique
    @Nullable
    private static BlockPos mca$resolveBedApproachPathTarget(Mob mob, BlockPos approach) {
        Vec3 standPosition = mca$findSafeBedApproachPosition(mob, approach);
        if (standPosition == null) {
            BlockState state = mob.level().getBlockState(approach);
            if (state.getCollisionShape(mob.level(), approach).isEmpty()
                    || state.isCollisionShapeFullBlock(mob.level(), approach)) {
                return null;
            }

            // A top slab/stair occupies the approach block itself, so its walk node is
            // in the block above. Full blocks are deliberately excluded to avoid making
            // bookshelves and similar furniture valid bed approaches.
            standPosition = mca$findSafeBedApproachPosition(mob, approach.above());
        }

        if (standPosition == null) {
            return null;
        }

        BlockPos pathTarget = BlockPos.containing(
                standPosition.x,
                standPosition.y + 0.5D,
                standPosition.z
        );
        BlockPathTypes pathType = WalkNodeEvaluator.getBlockPathTypeStatic(mob.level(), pathTarget.mutable());
        return pathType != BlockPathTypes.OPEN && mob.getPathfindingMalus(pathType) >= 0.0F
                ? pathTarget
                : null;
    }

    @Unique
    @Nullable
    private static Vec3 mca$findSafeBedApproachPosition(Mob mob, BlockPos approach) {
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

    @Unique
    private static Set<BlockPos> mca$getBedApproachPositions(BlockPos bedPos, BlockState bedState) {
        Direction facing = bedState.getValue(BedBlock.FACING);
        BlockPos footPos = bedPos.relative(facing.getOpposite());

        return Set.of(
                bedPos.relative(facing),
                bedPos.relative(facing.getClockWise()),
                bedPos.relative(facing.getCounterClockWise()),
                footPos.relative(facing.getClockWise()),
                footPos.relative(facing.getCounterClockWise()),
                footPos.relative(facing.getOpposite())
        );
    }
}
