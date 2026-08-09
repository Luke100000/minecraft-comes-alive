package net.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.enums.BedPart;
import net.minecraft.entity.ai.brain.Brain;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.WalkTarget;
import net.minecraft.entity.ai.brain.task.WanderAroundTask;
import net.minecraft.entity.ai.pathing.EntityNavigation;
import net.minecraft.entity.ai.pathing.Path;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.ai.brain.Activity;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.GlobalPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

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

        return navigation.findPathTo(mca$getBedApproachPositions(target, bedState), 0);
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
        return bedState != null
                && mca$getBedApproachPositions(bedPos, bedState).contains(mob.getBlockPos());
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
