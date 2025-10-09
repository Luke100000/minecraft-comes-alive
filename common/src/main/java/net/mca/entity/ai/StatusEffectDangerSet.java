package net.mca.entity.ai;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;

public class StatusEffectDangerSet {
    public static final Set<MobEffect> isDanger = new HashSet<>();
    static {
        isDanger.add(MobEffects.MOVEMENT_SLOWDOWN);
        isDanger.add(MobEffects.DIG_SLOWDOWN);
        isDanger.add(MobEffects.HARM);
        isDanger.add(MobEffects.CONFUSION);
        isDanger.add(MobEffects.BLINDNESS);
        isDanger.add(MobEffects.HUNGER);
        isDanger.add(MobEffects.WEAKNESS);
        isDanger.add(MobEffects.POISON);
        isDanger.add(MobEffects.WITHER);
        isDanger.add(MobEffects.LEVITATION);
        isDanger.add(MobEffects.UNLUCK);
        isDanger.add(MobEffects.MOVEMENT_SPEED);
    }
}
