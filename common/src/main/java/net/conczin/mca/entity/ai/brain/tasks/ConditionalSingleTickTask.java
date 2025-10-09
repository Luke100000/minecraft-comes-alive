package net.conczin.mca.entity.ai.brain.tasks;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.OneShot;

import java.util.function.Predicate;

public class ConditionalSingleTickTask<E extends LivingEntity> extends OneShot<E> {
    private final OneShot<? super E> task;
    private final Predicate<E> predicate;

    public ConditionalSingleTickTask(OneShot<? super E> task, Predicate<E> predicate) {
        super();

        this.task = task;
        this.predicate = predicate;
    }

    @Override
    public boolean trigger(ServerLevel world, E entity, long time) {
        return predicate.test(entity) && task.trigger(world, entity, time);
    }
}