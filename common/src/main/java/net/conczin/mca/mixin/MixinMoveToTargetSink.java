package net.conczin.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.conczin.mca.entity.ai.navigation.CombatEscapePositionTracker;
import net.conczin.mca.entity.ai.navigation.MultiTargetPositionTracker;
import net.conczin.mca.entity.ai.navigation.PathRequestDiagnostics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

@Mixin(MoveToTargetSink.class)
abstract class MixinMoveToTargetSink {
    @Unique
    private static final String MCA_TRY_COMPUTE_PATH =
            "tryComputePath(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/entity/ai/memory/WalkTarget;J)Z";
    @Unique
    private static final String MCA_REACHED_TARGET =
            "reachedTarget(Lnet/minecraft/world/entity/Mob;Lnet/minecraft/world/entity/ai/memory/WalkTarget;)Z";

    @Inject(method = MCA_TRY_COMPUTE_PATH, at = @At("HEAD"))
    private void mca$clearFailureFromPreviousTarget(
            Mob mob,
            WalkTarget walkTarget,
            long gameTime,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (mob instanceof VillagerEntityMCA villager) {
            WalkTargetFailureMemory.clearIfTargetChanged(
                    villager,
                    walkTarget.getTarget().currentBlockPosition()
            );
        }
    }

    @Inject(method = MCA_TRY_COMPUTE_PATH, at = @At("RETURN"))
    private void mca$rememberFailureTarget(
            Mob mob,
            WalkTarget walkTarget,
            long gameTime,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (mob instanceof VillagerEntityMCA villager) {
            WalkTargetFailureMemory.syncAfterVanillaPathAttempt(
                    villager,
                    walkTarget.getTarget().currentBlockPosition()
            );
        }
    }

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
        PathRequestDiagnostics.beginSinkRequest(mob);
        try {
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
        } finally {
            PathRequestDiagnostics.endSinkRequest();
        }
    }

    @WrapOperation(
            method = MCA_TRY_COMPUTE_PATH,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/ai/util/DefaultRandomPos;getPosTowards(Lnet/minecraft/world/entity/PathfinderMob;IILnet/minecraft/world/phys/Vec3;D)Lnet/minecraft/world/phys/Vec3;"
            )
    )
    @Nullable
    private Vec3 mca$skipRandomFallbackForCombatEscape(
            PathfinderMob pathfinderMob,
            int horizontalRange,
            int verticalRange,
            Vec3 target,
            double maxAngle,
            Operation<Vec3> original,
            Mob mob,
            WalkTarget walkTarget,
            long gameTime
    ) {
        if (walkTarget.getTarget() instanceof CombatEscapePositionTracker) {
            // Combat escape already supplies vetted tactical endpoints. If none can be
            // pathfound this tick, let the producer retry instead of walking to an
            // arbitrary vanilla fallback position that may point back into danger.
            return null;
        }
        return original.call(pathfinderMob, horizontalRange, verticalRange, target, maxAngle);
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
