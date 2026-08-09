package net.mca.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.mca.ProfessionsMCA;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(PersistentProjectileEntity.class)
public abstract class MixinAbstractArrow {
    @WrapOperation(
            method = "onEntityHit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"
            )
    )
    private boolean mca$allowArcherFollowUpHits(Entity target, DamageSource source, float amount, Operation<Boolean> original) {
        VillagerEntityMCA archer = mca$getMcaArcherOwner((PersistentProjectileEntity)(Object)this);
        if (target instanceof LivingEntity livingTarget && archer != null) {
            livingTarget.timeUntilRegen = 0;
        }

        boolean hurt = original.call(target, source, amount);
        if (hurt && archer != null) {
            archer.onRangedAttackLanded(target);
        }
        return hurt;
    }

    private VillagerEntityMCA mca$getMcaArcherOwner(PersistentProjectileEntity arrow) {
        Entity owner = arrow.getOwner();
        if (owner instanceof VillagerEntityMCA villager && villager.getProfession() == ProfessionsMCA.ARCHER.get()) {
            return villager;
        }
        return null;
    }
}
