package net.conczin.mca.entity.ai.brain;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.brain.tasks.WalkTargetRetryGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

import java.util.Optional;

public final class WalkTargetFailureMemory {
    public interface TargetIdentityOwner {
        GlobalPos failureTarget();
    }

    private WalkTargetFailureMemory() {
    }

    public static void record(Mob entity, BlockPos target, long since) {
        if (entity instanceof VillagerEntityMCA villager) {
            record(villager, target, since);
        } else {
            entity.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, since);
        }
    }

    public static void record(VillagerEntityMCA villager, BlockPos target, long since) {
        record(villager, resolveTargetIdentity(villager, target), since);
    }

    public static void record(VillagerEntityMCA villager, GlobalPos target, long since) {
        villager.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, since);
        villager.getBrain().setMemory(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET, target);
        villager.getVillagerBrain().syncFailureToRetryGates(target, since);
    }

    private static void rememberTarget(VillagerEntityMCA villager, GlobalPos target) {
        villager.getBrain().setMemory(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET, target);
    }

    public static void clear(Mob entity) {
        if (entity instanceof VillagerEntityMCA villager) {
            clear(villager);
        } else {
            entity.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        }
    }

    public static void clear(VillagerEntityMCA villager) {
        villager.getVillagerBrain().clearFailureOnUnpreemptedRetryGates(null);
        eraseFailureMemories(villager);
    }

    private static void eraseFailureMemories(VillagerEntityMCA villager) {
        villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET);
    }

    private static void clearTarget(VillagerEntityMCA villager) {
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET);
    }

    public static void clearIfTargetChanged(VillagerEntityMCA villager, BlockPos target) {
        clearIfTargetChanged(villager, resolveTargetIdentity(villager, target));
    }

    public static void clearIfTargetChanged(VillagerEntityMCA villager, GlobalPos target) {
        if (!villager.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) {
            clearTarget(villager);
            return;
        }

        if (villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET)
                .filter(target::equals)
                .isEmpty()) {
            // Foreign MoveToTargetSink path attempts replace the shared failure slot without
            // resolving any preempted POI producer's own failure episode.
            eraseFailureMemories(villager);
        }
    }

    public static void clearIfTargetMatches(VillagerEntityMCA villager, GlobalPos target) {
        villager.getVillagerBrain().clearFailureOnUnpreemptedRetryGates(target);
        if (hasFailureFor(villager, target)) {
            eraseFailureMemories(villager);
        }
    }

    public static void resumeTarget(VillagerEntityMCA villager, GlobalPos target,
                                    WalkTargetRetryGate retryGate, long time) {
        clearIfTargetChanged(villager, target);
        long shift = retryGate.consumeFailureShift(time);
        Optional<Long> since = villager.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        if (since.isPresent()) {
            if (shift > 0L) {
                record(villager, target, since.orElseThrow() + shift);
            } else {
                villager.getVillagerBrain().syncFailureToRetryGates(target, since.orElseThrow());
            }
        } else {
            long unresolvedSince = retryGate.unresolvedFailureSince(target.pos());
            if (unresolvedSince != Long.MIN_VALUE) {
                record(villager, target, unresolvedSince);
            }
        }
    }

    public static boolean hasFailureFor(VillagerEntityMCA villager, BlockPos target) {
        return hasFailureFor(villager, resolveTargetIdentity(villager, target));
    }

    private static boolean hasFailureFor(VillagerEntityMCA villager, GlobalPos target) {
        return villager.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                && villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET)
                .filter(target::equals)
                .isPresent();
    }

    public static void syncAfterVanillaPathAttempt(VillagerEntityMCA villager, BlockPos target) {
        Optional<Long> since = villager.getBrain().getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        if (since.isPresent()) {
            GlobalPos resolved = resolveTargetIdentity(villager, target);
            rememberTarget(villager, resolved);
            villager.getVillagerBrain().syncFailureToRetryGates(resolved, since.orElseThrow());
        } else {
            clearTarget(villager);
        }
    }

    private static GlobalPos resolveTargetIdentity(VillagerEntityMCA villager, BlockPos target) {
        return villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                .filter(TargetIdentityOwner.class::isInstance)
                .map(TargetIdentityOwner.class::cast)
                .map(TargetIdentityOwner::failureTarget)
                .orElseGet(() -> GlobalPos.of(villager.level().dimension(), target));
    }
}
