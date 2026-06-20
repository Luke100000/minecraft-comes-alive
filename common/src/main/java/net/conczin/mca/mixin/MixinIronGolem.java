package net.conczin.mca.mixin;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IronGolem.class)
public abstract class MixinIronGolem extends LivingEntity {
    protected MixinIronGolem(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void mca$skipGuardTargets(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof VillagerEntityMCA) { //villager && villager.isGuard()
            cir.setReturnValue(false);
        }
    }
}
