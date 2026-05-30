package net.conczin.mca.entity.ai;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.minecraft.resources.Identifier;
import net.minecraft.world.attribute.AttributeTypes;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.schedule.Activity;

import java.util.HashMap;
import java.util.Map;

public interface SchedulesMCA {
    Map<Identifier, EnvironmentAttribute<Activity>> SCHEDULES = new HashMap<>();

    EnvironmentAttribute<Activity> DEFAULT = schedule("gameplay/default_villager_activity", Activity.IDLE);
    EnvironmentAttribute<Activity> NIGHT_OWL_DEFAULT = schedule("gameplay/night_owl_villager_activity", Activity.REST);
    EnvironmentAttribute<Activity> GUARD = schedule("gameplay/guard_villager_activity", Activity.WORK);
    EnvironmentAttribute<Activity> GUARD_NIGHT = schedule("gameplay/night_guard_villager_activity", Activity.REST);
    EnvironmentAttribute<Activity> GUESTS = schedule("gameplay/guest_villager_activity", Activity.IDLE);

    static EnvironmentAttribute<Activity> schedule(String name, Activity defaultActivity) {
        EnvironmentAttribute<Activity> schedule = EnvironmentAttribute.builder(AttributeTypes.ACTIVITY)
                .defaultValue(defaultActivity)
                .build();
        SCHEDULES.put(MCA.locate(name), schedule);
        return schedule;
    }

    static void bootstrap() {
    }

    static void registerSchedules(MCA.RegisterHelper<EnvironmentAttribute<?>> helper) {
        SCHEDULES.forEach((id, schedule) -> helper.register(id, schedule));
    }

    static EnvironmentAttribute<Activity> getTypeSchedule(
            LivingEntity entity,
            boolean allowNightOwl,
            EnvironmentAttribute<Activity> normalSchedule,
            EnvironmentAttribute<Activity> nightSchedule
    ) {
        return (allowNightOwl && entity.getRandom().nextFloat() < Config.getInstance().nightOwlChance) ? nightSchedule : normalSchedule;
    }

    static EnvironmentAttribute<Activity> getTypeSchedule(
            LivingEntity entity,
            EnvironmentAttribute<Activity> normalSchedule,
            EnvironmentAttribute<Activity> nightSchedule
    ) {
        return getTypeSchedule(entity, Config.getInstance().allowAnyNightOwl, normalSchedule, nightSchedule);
    }

    static EnvironmentAttribute<Activity> getTypeSchedule(LivingEntity entity, boolean allowNightOwl) {
        return getTypeSchedule(entity, allowNightOwl, SchedulesMCA.DEFAULT, SchedulesMCA.NIGHT_OWL_DEFAULT);
    }

    static EnvironmentAttribute<Activity> getTypeSchedule(LivingEntity entity) {
        return getTypeSchedule(entity, Config.getInstance().allowAnyNightOwl);
    }
}
