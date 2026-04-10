package net.conczin.mca.entity.ai;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

import java.util.HashSet;
import java.util.Set;

public class StatusEffectDangerSet {
    public static final Set<Holder<MobEffect>> IS_DANGER = new HashSet<>();

    static {
        IS_DANGER.add(MobEffects.SLOWNESS);
        IS_DANGER.add(MobEffects.MINING_FATIGUE);
        IS_DANGER.add(MobEffects.INSTANT_DAMAGE);
        IS_DANGER.add(MobEffects.NAUSEA);
        IS_DANGER.add(MobEffects.BLINDNESS);
        IS_DANGER.add(MobEffects.HUNGER);
        IS_DANGER.add(MobEffects.WEAKNESS);
        IS_DANGER.add(MobEffects.POISON);
        IS_DANGER.add(MobEffects.WITHER);
        IS_DANGER.add(MobEffects.LEVITATION);
        IS_DANGER.add(MobEffects.UNLUCK);
        IS_DANGER.add(MobEffects.SPEED);
    }
}
