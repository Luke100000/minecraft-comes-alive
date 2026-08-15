package net.conczin.mca.entity.ai.brain.tasks;

import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;

public class ConditionalTask<E extends LivingEntity> extends Behavior<E> {
    private final BehaviorControl<? super E> task;
    private final Predicate<E> predicate;

    public ConditionalTask(BehaviorControl<? super E> task, Predicate<E> predicate) {
        super(Map.of());

        this.task = task;
        this.predicate = predicate;
    }

    @Override
    protected void start(ServerLevel world, E entity, long time) {
        task.tryStart(world, entity, time);
    }

    @Override
    protected void tick(ServerLevel world, E entity, long time) {
        task.tickOrStop(world, entity, time);
    }

    @Override
    protected void stop(ServerLevel world, E entity, long time) {
        task.doStop(world, entity, time);
    }

    @Override
    protected boolean canStillUse(ServerLevel world, E entity, long time) {
        return predicate.test(entity) && task.getStatus() == Status.RUNNING;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, E entity) {
        return predicate.test(entity);
    }

    @Override
    protected boolean timedOut(long time) {
        return false;
    }
}