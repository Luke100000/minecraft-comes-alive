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
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;
import java.util.Set;

@Mixin(MoveToTargetSink.class)
abstract class MixinMoveToTargetSink {
    @Unique
    private static final String MCA_TRY_COMPUTE_PATH =
            "tryComputePath(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/entity/ai/memory/WalkTarget;J)Z";
    @Unique
    private static final String MCA_REACHED_TARGET =
            "reachedTarget(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/entity/ai/memory/WalkTarget;)Z";

    @WrapOperation(
            method = MCA_TRY_COMPUTE_PATH,
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

        Path path = navigation.createPath(mca$getBedApproachPositions(target, bedState), 0);
        return path != null && path.canReach() ? path : null;
    }

    @WrapOperation(
            method = MCA_TRY_COMPUTE_PATH,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/util/DefaultRandomPos;getPosTowards(Lnet/minecraft/world/entity/PathfinderMob;IILnet/minecraft/world/phys/Vec3;D)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    @Nullable
    private Vec3 mca$skipRandomFallbackForBed(
            PathfinderMob pathfinderMob,
            int horizontalRange,
            int verticalRange,
            Vec3 target,
            double angle,
            Operation<Vec3> original,
            Mob mob,
            WalkTarget walkTarget,
            long gameTime
    ) {
        BlockPos walkTargetPos = walkTarget.getTarget().currentBlockPosition();
        if (mca$getTargetBedState(mob, walkTargetPos) != null) {
            return null;
        }

        return original.call(pathfinderMob, horizontalRange, verticalRange, target, angle);
    }

    @ModifyReturnValue(method = MCA_REACHED_TARGET, at = @At("RETURN"))
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
        return bedState != null
                && mca$getBedApproachPositions(bedPos, bedState)
                .contains(mob.blockPosition());
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

        Optional<GlobalPos> homeMemory = brain.getMemory(MemoryModuleType.HOME);
        if (homeMemory.isEmpty()) {
            return null;
        }

        GlobalPos home = homeMemory.get();
        if (!home.dimension().equals(mob.level().dimension())
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
    private static Set<BlockPos> mca$getBedApproachPositions(BlockPos bedPos, BlockState bedState) {
        Direction facing = bedState.getValue(BedBlock.FACING);
        BlockPos footPos = bedPos.relative(facing.getOpposite());

        // These are the five standing blocks inside SleepInBed's strict two-block radius.
        return Set.of(
                bedPos.relative(facing),
                bedPos.relative(facing.getClockWise()),
                bedPos.relative(facing.getCounterClockWise()),
                footPos.relative(facing.getClockWise()),
                footPos.relative(facing.getCounterClockWise())
        );
    }
}
