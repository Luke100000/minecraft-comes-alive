package net.conczin.mca.entity.ai.brain.sensor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.sensing.GolemSensor;
import net.minecraft.world.entity.ai.sensing.NearestBedSensor;
import net.minecraft.world.entity.ai.sensing.NearestItemSensor;
import net.minecraft.world.entity.ai.sensing.SecondaryPoiSensor;
import net.minecraft.world.entity.npc.Villager;

/** MCA-only scan suppression; vanilla countdowns and memory expiry keep running. */
public final class SleepingAwareVanillaSensors {
    private SleepingAwareVanillaSensors() {
    }

    public static class NearestItems extends NearestItemSensor {
        @Override
        protected void doTick(ServerLevel level, Mob entity) {
            if (!entity.isSleeping()) super.doTick(level, entity);
        }
    }

    public static class NearestBed extends NearestBedSensor {
        @Override
        protected void doTick(ServerLevel level, Mob entity) {
            if (!entity.isSleeping()) super.doTick(level, entity);
        }
    }

    public static class SecondaryPois extends SecondaryPoiSensor {
        @Override
        protected void doTick(ServerLevel level, Villager entity) {
            if (!entity.isSleeping()) super.doTick(level, entity);
        }
    }

    public static class GolemDetected extends GolemSensor {
        @Override
        protected void doTick(ServerLevel level, LivingEntity entity) {
            if (!entity.isSleeping()) super.doTick(level, entity);
        }
    }
}
