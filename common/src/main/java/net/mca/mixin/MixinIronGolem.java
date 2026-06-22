package net.mca.mixin;

import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(IronGolemEntity.class)
public abstract class MixinIronGolem extends LivingEntity {
    protected MixinIronGolem(EntityType<? extends LivingEntity> type, World world) {
        super(type, world);
    }

    @Inject(method = "canTarget(Lnet/minecraft/entity/LivingEntity;)Z", at = @At("HEAD"), cancellable = true)
    private void mca$skipGuardTargets(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
        if (target instanceof VillagerEntityMCA) {
            cir.setReturnValue(false);
        }
    }
}
