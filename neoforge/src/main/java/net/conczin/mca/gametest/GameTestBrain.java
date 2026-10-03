package net.conczin.mca.gametest;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.attribute.AttributeTypes;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.schedule.Activity;

import java.util.List;
import java.util.Set;

/** 26.x adapters for the small custom Brain fixtures used by MCA GameTests. */
public final class GameTestBrain {
    private GameTestBrain() {
    }

    public static <E extends LivingEntity> void addActivity(
            Brain<E> brain,
            Activity activity,
            int priority,
            List<? extends BehaviorControl<? super E>> behaviors
    ) {
        ImmutableList.Builder<Pair<Integer, BehaviorControl<? super E>>> pairs = ImmutableList.builder();
        for (BehaviorControl<? super E> behavior : behaviors) {
            pairs.add(Pair.of(priority, behavior));
        }
        brain.addActivity(activity, pairs.build(), Set.of(), Set.of());
    }

    public static EnvironmentAttribute<Activity> fixedSchedule(Activity activity) {
        return EnvironmentAttribute.builder(AttributeTypes.ACTIVITY)
                .defaultValue(activity)
                .build();
    }
}
