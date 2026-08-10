package net.mca.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.mca.ProfessionsMCA;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractArrow.class)
public abstract class MixinAbstractArrow {
    @WrapOperation(
            method = "onHitEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;hurt(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            )
    )
    private boolean mca$allowArcherFollowUpHits(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
        VillagerEntityMCA archer = mca$getMcaArcherOwner((AbstractArrow)(Object)this);
        if (target instanceof LivingEntity livingTarget && archer != null) {
            livingTarget.invulnerableTime = 0;
        }

        boolean hurt = original.call(target, source, amount);
        if (hurt && archer != null) {
            archer.onRangedAttackLanded(target);
        }
        return hurt;
    }

    private VillagerEntityMCA mca$getMcaArcherOwner(AbstractArrow arrow) {
        Entity owner = arrow.getOwner();
        if (owner instanceof VillagerEntityMCA villager && villager.getProfession() == ProfessionsMCA.ARCHER.get()) {
            return villager;
        }
        return null;
    }
}
