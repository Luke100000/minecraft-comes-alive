package net.conczin.mca.entity.ai;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

import java.util.HashSet;
import java.util.Set;

public class StatusEffectDangerSet {
    public static final Set<Holder<MobEffect>> IS_DANGER = new HashSet<>();

    static {
        IS_DANGER.add(MobEffects.MOVEMENT_SLOWDOWN);
        IS_DANGER.add(MobEffects.DIG_SLOWDOWN);
        IS_DANGER.add(MobEffects.HARM);
        IS_DANGER.add(MobEffects.CONFUSION);
        IS_DANGER.add(MobEffects.BLINDNESS);
        IS_DANGER.add(MobEffects.HUNGER);
        IS_DANGER.add(MobEffects.WEAKNESS);
        IS_DANGER.add(MobEffects.POISON);
        IS_DANGER.add(MobEffects.WITHER);
        IS_DANGER.add(MobEffects.LEVITATION);
        IS_DANGER.add(MobEffects.UNLUCK);
        IS_DANGER.add(MobEffects.MOVEMENT_SPEED);
    }
}
