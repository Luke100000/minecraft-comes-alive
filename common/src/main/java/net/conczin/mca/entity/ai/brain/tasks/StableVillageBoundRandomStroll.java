package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.VillageBoundRandomStroll;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

public final class StableVillageBoundRandomStroll {
    private StableVillageBoundRandomStroll() {
    }

    public static OneShot<VillagerEntityMCA> create(float speed) {
        return create(speed, 10, 7);
    }

    public static OneShot<VillagerEntityMCA> create(float speed, int horizontalDistance, int verticalDistance) {
        OneShot<PathfinderMob> vanillaStroll = VillageBoundRandomStroll.create(speed, horizontalDistance, verticalDistance);
        return new OneShot<>() {
            @Override
            public boolean trigger(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
                if (!vanillaStroll.trigger(level, villager, gameTime)) {
                    return false;
                }
                filterUnstableWalkTarget(villager);
                return true;
            }
        };
    }

    static void filterUnstableWalkTarget(VillagerEntityMCA villager) {
        villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                .filter(target -> !villager.getNavigation().isStableDestination(target.getTarget().currentBlockPosition()))
                .ifPresent(target -> villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET));
    }
}
