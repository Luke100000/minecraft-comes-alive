package net.conczin.mca.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractArrow.class)
abstract class MixinAbstractArrow {
    @WrapOperation(
            method = "onHitEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            )
    )
    @SuppressWarnings("deprecation")
    private boolean mca$allowMcaArcherArrowsThroughHurtCooldown(Entity target, DamageSource source, float damage, Operation<Boolean> original) {
        VillagerEntityMCA archer = mca$getMcaArcherOwner();
        if (!(target instanceof LivingEntity livingTarget) || archer == null) {
            return original.call(target, source, damage);
        }

        if (target.level().isClientSide()) {
            return true;
        }

        livingTarget.invulnerableTime = 0;
        boolean hurt = original.call(target, source, damage);
        if (hurt) {
            archer.onRangedAttackLanded(target);
        }
        return hurt;
    }

    @Unique
    private VillagerEntityMCA mca$getMcaArcherOwner() {
        Entity owner = ((AbstractArrow) (Object) this).getOwner();
        if (owner instanceof VillagerEntityMCA villager && villager.getProfession() == ProfessionsMCA.ARCHER) {
            return villager;
        }
        return null;
    }
}
