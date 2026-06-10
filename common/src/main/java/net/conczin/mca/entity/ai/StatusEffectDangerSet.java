package net.conczin.mca.entity.ai;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

import java.util.Set;

public class StatusEffectDangerSet {
    public static final Set<Holder<MobEffect>> IS_DANGER = Set.of(
            MobEffects.SLOWNESS,
            MobEffects.MINING_FATIGUE,
            MobEffects.INSTANT_DAMAGE,
            MobEffects.NAUSEA,
            MobEffects.BLINDNESS,
            MobEffects.HUNGER,
            MobEffects.WEAKNESS,
            MobEffects.POISON,
            MobEffects.WITHER,
            MobEffects.LEVITATION,
            MobEffects.UNLUCK,
            MobEffects.SPEED
    );
}
