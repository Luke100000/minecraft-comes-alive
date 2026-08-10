package net.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Chore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class InteractTask extends Behavior<VillagerEntityMCA> {
    private final float speedModifier;

    public InteractTask(float speedModifier) {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), Integer.MAX_VALUE);
        this.speedModifier = speedModifier;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA villager) {
        return shouldRun(villager);
    }

    public static boolean shouldRun(VillagerEntityMCA villager) {
        return villager.isAlive()
                && villager.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).isEmpty()
                && villager.getInteractions().getInteractingPlayer().filter(player -> villager.distanceToSqr(player) <= 25).isPresent()
                && !villager.isInWater()
                && !villager.hurtMarked
                && villager.getVillagerBrain().getCurrentJob() == Chore.NONE;
    }

    @Override
    protected boolean canStillUse(ServerLevel world, VillagerEntityMCA villager, long time) {
        return this.checkExtraStartConditions(world, villager);
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        this.followPlayer(villager);
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        Brain<?> brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        this.followPlayer(villager);
    }

    @Override
    protected boolean timedOut(long time) {
        return false;
    }

    private void followPlayer(VillagerEntityMCA villager) {
        Brain<?> brain = villager.getBrain();

        villager.getInteractions().getInteractingPlayer().ifPresentOrElse(player -> {
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(player, this.speedModifier, 2));
            brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
        }, () -> {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        });
    }
}
