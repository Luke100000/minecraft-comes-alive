package net.conczin.mca.entity.ai.brain;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public final class WalkTargetFailureMemory {
    private WalkTargetFailureMemory() {
    }

    public static void record(VillagerEntityMCA villager, BlockPos target, long since) {
        record(villager, GlobalPos.of(villager.level().dimension(), target), since);
    }

    public static void record(VillagerEntityMCA villager, GlobalPos target, long since) {
        villager.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE, since);
        villager.getBrain().setMemory(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET, target);
    }

    private static void rememberTarget(VillagerEntityMCA villager, BlockPos target) {
        rememberTarget(villager, GlobalPos.of(villager.level().dimension(), target));
    }

    private static void rememberTarget(VillagerEntityMCA villager, GlobalPos target) {
        villager.getBrain().setMemory(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET, target);
    }

    public static void clear(VillagerEntityMCA villager) {
        villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET);
    }

    private static void clearTarget(VillagerEntityMCA villager) {
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET);
    }

    public static void clearIfTargetChanged(VillagerEntityMCA villager, BlockPos target) {
        clearIfTargetChanged(villager, GlobalPos.of(villager.level().dimension(), target));
    }

    public static void clearIfTargetChanged(VillagerEntityMCA villager, GlobalPos target) {
        if (!villager.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) {
            clearTarget(villager);
            return;
        }

        if (villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET)
                .filter(target::equals)
                .isEmpty()) {
            clear(villager);
        }
    }

    public static boolean hasFailureFor(VillagerEntityMCA villager, BlockPos target) {
        return hasFailureFor(villager, GlobalPos.of(villager.level().dimension(), target));
    }

    private static boolean hasFailureFor(VillagerEntityMCA villager, GlobalPos target) {
        return villager.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                && villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.CANT_REACH_WALK_TARGET)
                .filter(target::equals)
                .isPresent();
    }

    public static void syncAfterVanillaPathAttempt(VillagerEntityMCA villager, BlockPos target) {
        if (villager.getBrain().hasMemoryValue(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)) {
            rememberTarget(villager, target);
        } else {
            clearTarget(villager);
        }
    }
}
