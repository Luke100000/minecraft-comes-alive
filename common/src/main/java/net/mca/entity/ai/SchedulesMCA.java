package net.mca.entity.ai;

import net.mca.Config;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;

public interface SchedulesMCA {
    //DEFAULT (500 ticks longer awaken)
    Schedule DEFAULT = new ScheduleBuilder(new Schedule())
            .changeActivityAt(10, Activity.IDLE)
            .changeActivityAt(2000, Activity.WORK)
            .changeActivityAt(9000, Activity.MEET)
            .changeActivityAt(11000, Activity.IDLE)
            .changeActivityAt(12500, Activity.REST)
            .build();

    // NIGHT DEFAULT (500 ticks longer awaken)
    Schedule NIGHT_OWL_DEFAULT = new ScheduleBuilder(new Schedule())
            .changeActivityAt(10, Activity.REST)
            .changeActivityAt(12500, Activity.IDLE)
            .changeActivityAt(15000, Activity.WORK)
            .changeActivityAt(18000, Activity.MEET)
            .changeActivityAt(19500, Activity.IDLE)
            .build();

    //DAY GUARD
    Schedule GUARD = new ScheduleBuilder(new Schedule())
            .changeActivityAt(10, Activity.WORK)
            .changeActivityAt(9000, Activity.MEET)
            .changeActivityAt(11000, Activity.WORK)
            .changeActivityAt(14000, Activity.IDLE)
            .changeActivityAt(15000, Activity.REST)
            .build();

    //NIGHT GUARD
    Schedule GUARD_NIGHT = new ScheduleBuilder(new Schedule())
            .changeActivityAt(10, Activity.REST)
            .changeActivityAt(8000, Activity.IDLE)
            .changeActivityAt(9000, Activity.MEET)
            .changeActivityAt(14000, Activity.WORK)
            .build();

    //Schedule for inn guests
    Schedule GUESTS = new ScheduleBuilder(new Schedule())
            .changeActivityAt(10, Activity.IDLE)
            .changeActivityAt(12500, Activity.REST)
            .build();

    static void bootstrap() {
    }

    static Schedule getTypeSchedule(LivingEntity entity, boolean allowNightOwl, Schedule normalSchedule, Schedule nightSchedule) {
        return (allowNightOwl && entity.getRandom().nextFloat() < Config.getInstance().nightOwlChance) ? nightSchedule : normalSchedule;
    }

    static Schedule getTypeSchedule(LivingEntity entity, Schedule normalSchedule, Schedule nightSchedule) {
        return getTypeSchedule(entity, Config.getInstance().allowAnyNightOwl, normalSchedule, nightSchedule);
    }

    static Schedule getTypeSchedule(LivingEntity entity, boolean allowNightOwl) {
        return getTypeSchedule(entity, allowNightOwl, SchedulesMCA.DEFAULT, SchedulesMCA.NIGHT_OWL_DEFAULT);
    }

    static Schedule getTypeSchedule(LivingEntity entity) {
        return getTypeSchedule(entity, Config.getInstance().allowAnyNightOwl);
    }
}
