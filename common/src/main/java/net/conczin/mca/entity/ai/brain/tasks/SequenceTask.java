package net.conczin.mca.entity.ai.brain.tasks;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

import java.util.List;
import java.util.Map;

public class SequenceTask<E extends LivingEntity> extends Behavior<E> {
    private final List<BehaviorControl<? super E>> tasks;
    int progress = 0;

    public SequenceTask(Map<MemoryModuleType<?>, MemoryStatus> requiredMemoryState, List<BehaviorControl<? super E>> tasks) {
        super(requiredMemoryState);

        this.tasks = tasks;
    }

    private BehaviorControl<? super E> getRunningTask() {
        return tasks.get(progress);
    }

    @Override
    protected void start(ServerLevel world, E entity, long time) {
        getRunningTask().tryStart(world, entity, time);
    }

    @Override
    protected void tick(ServerLevel world, E entity, long time) {
        this.tasks.stream().filter(task -> task.getStatus() == Behavior.Status.RUNNING).forEach(task -> task.tickOrStop(world, entity, time));
    }

    @Override
    protected void stop(ServerLevel world, E entity, long time) {
        progress = 0;
        this.tasks.stream().filter(task -> task.getStatus() == Behavior.Status.RUNNING).forEach(task -> task.doStop(world, entity, time));
    }

    @Override
    protected boolean canStillUse(ServerLevel world, E entity, long time) {
        if (this.tasks.stream().anyMatch(task -> task.getStatus() == Behavior.Status.RUNNING)) {
            return true;
        } else if (progress < tasks.size() - 1) {
            progress++;
            getRunningTask().tryStart(world, entity, time);
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected boolean timedOut(long time) {
        return false;
    }
}
