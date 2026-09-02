package net.conczin.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.conczin.mca.entity.ai.navigation.MultiTargetPositionTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

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
    private Path mca$createMultiTargetPath(
            PathNavigation navigation,
            BlockPos target,
            int reachRange,
            Operation<Path> original,
            Mob mob,
            WalkTarget walkTarget,
            long gameTime
    ) {
        if (!(walkTarget.getTarget() instanceof MultiTargetPositionTracker multiTarget)) {
            return original.call(navigation, target, reachRange);
        }

        Set<BlockPos> pathTargets = multiTarget.getPathTargets(mob);
        if (pathTargets.isEmpty()) {
            return null;
        }

        // Preserve vanilla MoveToTargetSink semantics: a non-null partial path is still
        // useful progress, while vanilla tracks CANT_REACH_WALK_TARGET_SINCE separately.
        return navigation.createPath(pathTargets, reachRange);
    }

    @ModifyReturnValue(method = MCA_REACHED_TARGET, at = @At("RETURN"))
    private boolean mca$resolveMultiTargetReached(
            boolean original,
            Mob mob,
            WalkTarget walkTarget
    ) {
        if (walkTarget.getTarget() instanceof MultiTargetPositionTracker multiTarget) {
            return multiTarget.isReached(mob, walkTarget.getCloseEnoughDist());
        }
        return original;
    }
}
