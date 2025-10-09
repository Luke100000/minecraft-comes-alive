package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;

import java.util.function.Predicate;

public class SayTask extends Behavior<VillagerEntityMCA> {
    private final String phrase;
    private final int interval;
    private final Predicate<VillagerEntityMCA> condition;

    private long lastShout;

    public SayTask(String phrase) {
        this(phrase, 0, (v) -> true);
    }

    public SayTask(String phrase, int interval, Predicate<VillagerEntityMCA> condition) {
        super(ImmutableMap.of());
        this.phrase = phrase;
        this.interval = interval;
        this.condition = condition;
    }

    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA entity) {
        return entity.level().getGameTime() - lastShout > interval && condition.test(entity);
    }

    protected void start(ServerLevel world, VillagerEntityMCA entity, long time) {
        entity.sendChatToAllAround(phrase);
        lastShout = entity.level().getGameTime();
    }
}
