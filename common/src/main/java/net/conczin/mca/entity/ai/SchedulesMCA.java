package net.conczin.mca.entity.ai;

import net.conczin.mca.Config;
import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.schedule.Activity;

public interface SchedulesMCA {
    EnvironmentAttribute<Activity> DEFAULT = EnvironmentAttributes.VILLAGER_ACTIVITY;

    EnvironmentAttribute<Activity> NIGHT_OWL_DEFAULT = EnvironmentAttributes.VILLAGER_ACTIVITY;

    EnvironmentAttribute<Activity> GUARD = EnvironmentAttributes.VILLAGER_ACTIVITY;

    EnvironmentAttribute<Activity> GUARD_NIGHT = EnvironmentAttributes.VILLAGER_ACTIVITY;

    EnvironmentAttribute<Activity> GUESTS = EnvironmentAttributes.VILLAGER_ACTIVITY;

    static void bootstrap() {
    }

    static EnvironmentAttribute<Activity> getTypeSchedule(LivingEntity entity, boolean allowNightOwl, EnvironmentAttribute<Activity> normalSchedule, EnvironmentAttribute<Activity> nightSchedule) {
        return (allowNightOwl && entity.getRandom().nextFloat() < Config.getInstance().nightOwlChance) ? nightSchedule : normalSchedule;
    }

    static EnvironmentAttribute<Activity> getTypeSchedule(LivingEntity entity, EnvironmentAttribute<Activity> normalSchedule, EnvironmentAttribute<Activity> nightSchedule) {
        return getTypeSchedule(entity, Config.getInstance().allowAnyNightOwl, normalSchedule, nightSchedule);
    }

    static EnvironmentAttribute<Activity> getTypeSchedule(LivingEntity entity, boolean allowNightOwl) {
        return getTypeSchedule(entity, allowNightOwl, SchedulesMCA.DEFAULT, SchedulesMCA.NIGHT_OWL_DEFAULT);
    }

    static EnvironmentAttribute<Activity> getTypeSchedule(LivingEntity entity) {
        return getTypeSchedule(entity, Config.getInstance().allowAnyNightOwl);
    }
}
