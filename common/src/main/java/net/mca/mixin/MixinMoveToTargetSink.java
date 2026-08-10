package net.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.Dismounting;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.ai.brain.task.WanderAroundTask;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.LandPathNodeMaker;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

@Mixin(WanderAroundTask.class)
abstract class MixinMoveToTargetSink {
    @Unique
    private static final String MCA_TRY_COMPUTE_PATH =
            "hasFinishedPath(Lnet/minecraft/entity/mob/MobEntity;Lnet/minecraft/entity/ai/brain/WalkTarget;J)Z";
    @Unique
    private static final String MCA_REACHED_TARGET =
            "hasReached(Lnet/minecraft/entity/mob/MobEntity;Lnet/minecraft/entity/ai/brain/WalkTarget;)Z";

    @WrapOperation(
            method = MCA_TRY_COMPUTE_PATH,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/ai/pathing/EntityNavigation;findPathTo(Lnet/minecraft/util/math/BlockPos;I)Lnet/minecraft/entity/ai/pathing/Path;"
            )
    )
    @Nullable
    private Path mca$createBedApproachPath(
            EntityNavigation navigation,
            BlockPos target,
            int reachRange,
            Operation<Path> original,
            MobEntity mob,
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
        return navigation.findPathTo(approachTargets, 0);
    }

    @ModifyReturnValue(method = MCA_REACHED_TARGET, at = @At("RETURN"))
    private boolean mca$acceptReachedBedApproach(
            boolean original,
            MobEntity mob,
            WalkTarget walkTarget
    ) {
        if (original) {
            return true;
        }

        BlockPos bedPos = walkTarget.getLookTarget().getBlockPos();
        BlockState bedState = mca$getTargetBedState(mob, bedPos);
        if (bedState == null) {
            return false;
        }

        // Mirror vanilla's on-ground node-Y calculation, then use the same resolver
        // that produced the path targets so reachability has one source of truth.
        BlockPos navigationPos = BlockPos.ofFloored(mob.getX(), mob.getY() + 0.5D, mob.getZ());
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
    private static BlockState mca$getTargetBedState(MobEntity mob, BlockPos target) {
        if (!(mob instanceof VillagerEntityMCA villager) || villager.isSleeping()) {
            return null;
        }

        Brain<?> brain = villager.getBrain();
        if (!brain.hasActivity(Activity.REST)) {
            return null;
        }

        Optional<GlobalPos> homeMemory = brain.getOptionalMemory(MemoryModuleType.HOME);
        if (homeMemory.isEmpty()) {
            return null;
        }

        GlobalPos home = homeMemory.get();
        if (home.getDimension() != mob.getWorld().getRegistryKey()
                || !home.getPos().equals(target)) {
            return null;
        }

        BlockState state = mob.getWorld().getBlockState(target);
        return state.isIn(BlockTags.BEDS)
                && state.contains(BedBlock.FACING)
                && state.contains(BedBlock.PART)
                && state.get(BedBlock.PART) == BedPart.HEAD
                ? state
                : null;
    }

    @Unique
    private static Set<BlockPos> mca$getBedApproachPathTargets(MobEntity mob, BlockPos bedPos, BlockState bedState) {
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
    private static BlockPos mca$resolveBedApproachPathTarget(MobEntity mob, BlockPos approach) {
        Vec3d standPosition = mca$findSafeBedApproachPosition(mob, approach);
        if (standPosition == null) {
            BlockState state = mob.getWorld().getBlockState(approach);
            if (state.getCollisionShape(mob.getWorld(), approach).isEmpty()
                    || state.isFullCube(mob.getWorld(), approach)) {
                return null;
            }

            // A top slab/stair occupies the approach block itself, so its walk node is
            // in the block above. Full blocks are deliberately excluded to avoid making
            // bookshelves and similar furniture valid bed approaches.
            standPosition = mca$findSafeBedApproachPosition(mob, approach.up());
        }

        if (standPosition == null) {
            return null;
        }

        BlockPos pathTarget = BlockPos.ofFloored(
                standPosition.x,
                standPosition.y + 0.5D,
                standPosition.z
        );
        PathNodeType pathType = LandPathNodeMaker.getLandNodeType(mob.getWorld(), pathTarget.mutableCopy());
        return pathType != PathNodeType.OPEN && mob.getPathfindingPenalty(pathType) >= 0.0F
                ? pathTarget
                : null;
    }

    @Unique
    @Nullable
    private static Vec3d mca$findSafeBedApproachPosition(MobEntity mob, BlockPos approach) {
        Vec3d standPosition = Dismounting.findRespawnPos(
                mob.getType(), mob.getWorld(), approach, true
        );
        if (standPosition == null) {
            return null;
        }

        Box box = mob.getBoundingBox().offset(
                standPosition.x - mob.getX(),
                standPosition.y - mob.getY(),
                standPosition.z - mob.getZ()
        );
        return mob.getWorld().isSpaceEmpty(mob, box) ? standPosition : null;
    }

    @Unique
    private static Set<BlockPos> mca$getBedApproachPositions(BlockPos bedPos, BlockState bedState) {
        Direction facing = bedState.get(BedBlock.FACING);
        BlockPos footPos = bedPos.offset(facing.getOpposite());

        return Set.of(
                bedPos.offset(facing),
                bedPos.offset(facing.rotateYClockwise()),
                bedPos.offset(facing.rotateYCounterclockwise()),
                footPos.offset(facing.rotateYClockwise()),
                footPos.offset(facing.rotateYCounterclockwise()),
                footPos.offset(facing.getOpposite())
        );
    }
}
