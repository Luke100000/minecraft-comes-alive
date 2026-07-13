package net.mca.entity.ai.brain.tasks;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.entity.ai.brain.task.Task;
import net.minecraft.server.world.ServerWorld;

import java.util.Map;
import java.util.function.Predicate;

public class ConditionalTask<E extends LivingEntity> extends MultiTickTask<E> {
    private final Task<? super E> task;
    private final Predicate<E> predicate;

    public ConditionalTask(Task<? super E> task, Predicate<E> predicate) {
        super(Map.of());

        this.task = task;
        this.predicate = predicate;
    }

    @Override
    protected void run(ServerWorld world, E entity, long time) {
        task.tryStarting(world, entity, time);
    }

    @Override
    protected void keepRunning(ServerWorld world, E entity, long time) {
        task.tick(world, entity, time);
    }

    @Override
    protected void finishRunning(ServerWorld world, E entity, long time) {
        task.stop(world, entity, time);
    }

    @Override
    protected boolean shouldKeepRunning(ServerWorld world, E entity, long time) {
        return predicate.test(entity) && task.getStatus() == Status.RUNNING;
    }

    @Override
    protected boolean shouldRun(ServerWorld world, E entity) {
        return predicate.test(entity);
    }

    @Override
    protected boolean isTimeLimitExceeded(long time) {
        return false;
    }
}