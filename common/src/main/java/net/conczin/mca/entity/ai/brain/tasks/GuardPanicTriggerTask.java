package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.brain.sensor.GuardEnemiesSensor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.VillagerPanicTrigger;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

public class GuardPanicTriggerTask extends VillagerPanicTrigger {
    @Override
    protected boolean canStillUse(ServerLevel level, Villager body, long timestamp) {
        return hasPanicStimulus(body);
    }

    @Override
    protected void start(ServerLevel level, Villager body, long timestamp) {
        if (!hasPanicStimulus(body)) {
            return;
        }

        Brain<?> brain = body.getBrain();
        if (!brain.isActive(Activity.PANIC)) {
            brain.eraseMemory(MemoryModuleType.PATH);
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
            brain.eraseMemory(MemoryModuleType.BREED_TARGET);
            brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
        }

        brain.setActiveActivityIfPossible(Activity.PANIC);
    }

    private static boolean hasPanicStimulus(LivingEntity body) {
        Brain<?> brain = body.getBrain();
        return VillagerPanicTrigger.isHurt(body)
                || VillagerPanicTrigger.hasHostile(body)
                || brain.getMemoryInternal(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY)
                        .filter(entity -> GuardEnemiesSensor.isValidGuardEnemy(entity, body))
                        .isPresent();
    }
}
