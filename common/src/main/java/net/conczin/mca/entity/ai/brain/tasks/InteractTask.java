package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.brain.WalkTargetFailureMemory;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;

public class InteractTask extends Behavior<VillagerEntityMCA> {
    private static final long FAILED_PATH_RETRY_TICKS = 20L;
    private final float speedModifier;
    private long nextWalkAttemptTime;

    public InteractTask(float speedModifier) {
        super(ImmutableMap.of(
                MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED
        ), Integer.MAX_VALUE);
        this.speedModifier = speedModifier;
    }

    public static boolean shouldRun(VillagerEntityMCA villager) {
        return villager.isAlive()
               && villager.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty()
               && villager.getInteractions().getInteractingPlayer().filter(player -> villager.distanceToSqr(player) <= 25).isPresent()
               && !villager.isInWater()
               && !villager.hurtMarked
               && villager.getVillagerBrain().getCurrentJob() == Chore.NONE;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA villager) {
        return shouldRun(villager);
    }

    @Override
    protected boolean canStillUse(ServerLevel world, VillagerEntityMCA villager, long time) {
        return this.checkExtraStartConditions(world, villager);
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        nextWalkAttemptTime = time;
        this.followPlayer(villager, time);
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        Brain<?> brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        this.followPlayer(villager, time);
    }

    @Override
    protected boolean timedOut(long time) {
        return false;
    }

    private void followPlayer(VillagerEntityMCA villager, long time) {
        Brain<?> brain = villager.getBrain();

        villager.getInteractions().getInteractingPlayer().ifPresentOrElse(player -> {
            brain.setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true));
            // The sink abandons failed approaches. Keep facing the player without
            // immediately recreating the same exhausted partial path every tick.
            if (!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET)
                    && WalkTargetFailureMemory.hasFailureFor(villager, player.blockPosition())
                    && time < nextWalkAttemptTime) {
                return;
            }
            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(player, this.speedModifier, 2));
            nextWalkAttemptTime = time + FAILED_PATH_RETRY_TICKS;
        }, () -> {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        });
    }
}
