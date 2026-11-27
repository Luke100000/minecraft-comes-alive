package net.conczin.mca.entity.ai.brain;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.brain.tasks.WalkTargetRetryGate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class WalkTargetFailureMemory {
    public interface TargetIdentityOwner {
        GlobalPos failureTarget();

        MemoryModuleType<GlobalPos> failureDestinationMemory();
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
        WalkTarget command = currentCommand(villager);
        record(villager, resolveTargetIdentity(villager, target, command), ownerOf(command), since);
    }

    public static void record(VillagerEntityMCA villager, GlobalPos target, long since) {
        WalkTarget command = currentCommand(villager);
        MemoryModuleType<?> owner = command instanceof TargetIdentityOwner owned && !owned.failureTarget().equals(target)
                ? null : ownerOf(command);
        record(villager, target, owner, since);
    }

    public static void recordForProducer(VillagerEntityMCA villager, MemoryModuleType<GlobalPos> memory,
                                         GlobalPos target, long since) {
        record(villager, target, memory, since);
    }

    private static void record(VillagerEntityMCA villager, GlobalPos target,
                               @Nullable MemoryModuleType<?> owner, long since) {
        long failureSince = villager.getVillagerBrain().syncFailureToRetryGates(owner, target, since);
        projectFailure(villager, target, owner, failureSince);
    }

    private static void projectFailure(VillagerEntityMCA villager, GlobalPos target,
                                       @Nullable MemoryModuleType<?> owner, long since) {
        villager.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, since);
        rememberTarget(villager, target, owner);
    }

    private static void rememberTarget(VillagerEntityMCA villager, GlobalPos target,
                                       @Nullable MemoryModuleType<?> owner) {
        villager.getBrain().setMemory(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET, target);
        villager.getVillagerBrain().setFailureProjectionOwner(owner);
    }

    public static void clear(Mob entity) {
        if (entity instanceof VillagerEntityMCA villager) {
            clear(villager);
        } else {
            entity.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        }
    }

    public static void clear(VillagerEntityMCA villager) {
        WalkTarget command = currentCommand(villager);
        if (command instanceof TargetIdentityOwner owned) {
            villager.getVillagerBrain().clearProducerRetryGates(
                    owned.failureDestinationMemory(), owned.failureTarget(), false);
        } else if (command == null) {
            villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET)
                    .ifPresent(villager.getVillagerBrain()::clearProjectedProducerFailure);
        }
        eraseFailureMemories(villager);
    }

    private static void eraseFailureMemories(VillagerEntityMCA villager) {
        villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        clearTarget(villager);
    }

    private static void clearTarget(VillagerEntityMCA villager) {
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET);
        villager.getVillagerBrain().setFailureProjectionOwner(null);
    }

    public static void clearIfTargetChanged(VillagerEntityMCA villager, BlockPos target) {
        WalkTarget command = currentCommand(villager);
        clearIfTargetChanged(villager, resolveTargetIdentity(villager, target, command), ownerOf(command));
    }

    public static void clearIfTargetChanged(VillagerEntityMCA villager, GlobalPos target) {
        clearIfTargetChanged(villager, target, ownerOf(currentCommand(villager)));
    }

    public static void clearIfTargetChanged(VillagerEntityMCA villager, WalkTarget command) {
        clearIfTargetChanged(villager, resolveTargetIdentity(villager, command.getTarget().currentBlockPosition(), command),
                ownerOf(command));
    }

    private static void clearIfTargetChanged(VillagerEntityMCA villager, GlobalPos target,
                                             @Nullable MemoryModuleType<?> owner) {
        if (!villager.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) {
            clearTarget(villager);
            return;
        }

        if (!hasFailureFor(villager, target) || !projectionCompatible(villager, owner)) {
            // Foreign MoveToTargetSink path attempts replace the shared failure slot without
            // resolving any preempted POI producer's own failure episode.
            eraseFailureMemories(villager);
        }
    }

    public static void clearIfTargetMatches(VillagerEntityMCA villager, GlobalPos target) {
        if (hasFailureFor(villager, target)) {
            MemoryModuleType<?> owner = villager.getVillagerBrain().failureProjectionOwner();
            if (owner != null) {
                villager.getVillagerBrain().clearProducerRetryGates(owner, target, false);
            }
            eraseFailureMemories(villager);
        }
    }

    public static void clearForProducer(VillagerEntityMCA villager, MemoryModuleType<GlobalPos> memory,
                                        GlobalPos target) {
        clearProducer(villager, memory, target, true);
    }

    public static void clearFailureForProducer(VillagerEntityMCA villager, MemoryModuleType<GlobalPos> memory,
                                               GlobalPos target) {
        clearProducer(villager, memory, target, false);
    }

    private static void clearProducer(VillagerEntityMCA villager, MemoryModuleType<GlobalPos> memory,
                                      GlobalPos target, boolean endEpisode) {
        villager.getVillagerBrain().clearProducerRetryGates(memory, target, endEpisode);
        if (villager.getVillagerBrain().failureProjectionOwner() == memory
                && villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET)
                .filter(target::equals).isPresent()) {
            eraseFailureMemories(villager);
        }
    }

    public static void resumeTarget(VillagerEntityMCA villager, GlobalPos target,
                                    WalkTargetRetryGate retryGate, long time) {
        MemoryModuleType<GlobalPos> owner = retryGate.destinationMemory();
        clearIfTargetChanged(villager, target, owner);
        long unresolvedSince = villager.getVillagerBrain().resumeFailureFromRetryGates(retryGate, target, time,
                villager.getBrain().getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE));
        if (unresolvedSince != Long.MIN_VALUE) {
            projectFailure(villager, target, owner, unresolvedSince);
        }
    }

    public static boolean hasFailureFor(VillagerEntityMCA villager, BlockPos target) {
        return hasFailureFor(villager, resolveTargetIdentity(villager, target, currentCommand(villager)));
    }

    /** Decision about a specific movement command, rather than a projection query. */
    public static boolean hasFailureForCommand(VillagerEntityMCA villager, WalkTarget command) {
        return hasFailureFor(villager, resolveTargetIdentity(villager, command.getTarget().currentBlockPosition(), command))
                && projectionCompatible(villager, ownerOf(command));
    }

    private static boolean hasFailureFor(VillagerEntityMCA villager, GlobalPos target) {
        return villager.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                && villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET)
                .filter(target::equals)
                .isPresent();
    }

    public static void syncAfterVanillaPathAttempt(VillagerEntityMCA villager, BlockPos target) {
        syncAfterVanillaPathAttempt(villager, target, currentCommand(villager));
    }

    public static void syncAfterVanillaPathAttempt(VillagerEntityMCA villager, WalkTarget command) {
        syncAfterVanillaPathAttempt(villager, command.getTarget().currentBlockPosition(), command);
    }

    private static void syncAfterVanillaPathAttempt(VillagerEntityMCA villager, BlockPos target,
                                                    @Nullable WalkTarget command) {
        Optional<Long> since = villager.getBrain().getMemoryInternal(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        if (since.isPresent()) {
            GlobalPos resolved = resolveTargetIdentity(villager, target, command);
            MemoryModuleType<?> owner = ownerOf(command);
            record(villager, resolved, owner, since.orElseThrow());
        } else {
            clearTarget(villager);
        }
    }

    private static boolean projectionCompatible(VillagerEntityMCA villager, @Nullable MemoryModuleType<?> owner) {
        MemoryModuleType<?> projectedOwner = villager.getVillagerBrain().failureProjectionOwner();
        return projectedOwner == null || owner == null || projectedOwner == owner;
    }

    @Nullable
    private static WalkTarget currentCommand(VillagerEntityMCA villager) {
        return villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
    }

    @Nullable
    private static MemoryModuleType<?> ownerOf(@Nullable WalkTarget command) {
        return command instanceof TargetIdentityOwner owned ? owned.failureDestinationMemory()
                : command == null ? null : MemoryModuleType.WALK_TARGET;
    }

    private static GlobalPos resolveTargetIdentity(VillagerEntityMCA villager, BlockPos target,
                                                   @Nullable WalkTarget command) {
        return command instanceof TargetIdentityOwner owned ? owned.failureTarget()
                : GlobalPos.of(villager.level().dimension(), target);
    }
}
