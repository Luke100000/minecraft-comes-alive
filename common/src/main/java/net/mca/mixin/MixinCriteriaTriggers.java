package net.mca.mixin;

import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.CriterionTrigger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(CriteriaTriggers.class)
public interface MixinCriteriaTriggers {
    @Invoker("register")
    static <T extends CriterionTrigger<?>> T register(T object) {
        return null;
    }
}
