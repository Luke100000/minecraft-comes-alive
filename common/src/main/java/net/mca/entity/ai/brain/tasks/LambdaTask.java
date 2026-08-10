package net.mca.entity.ai.brain.tasks;

import net.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import java.util.Map;
import java.util.function.Consumer;

public class LambdaTask<E extends VillagerEntityMCA> extends Behavior<E> {
    private final Consumer<E> lambda;

    public LambdaTask(Consumer<E> lambda) {
        super(Map.of());
        this.lambda = lambda;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, E entity) {
        return true;
    }

    @Override
    protected boolean canStillUse(ServerLevel world, E entity, long time) {
        return false;
    }

    @Override
    protected boolean timedOut(long time) {
        return false;
    }

    @Override
    protected void start(ServerLevel world, E entity, long time) {
        lambda.accept(entity);
    }
}

