package net.mca.mixin;

import net.mca.ProfessionsMCA;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PersistentProjectileEntity.class)
public abstract class MixinAbstractArrow {
    @Redirect(
            method = "onEntityHit",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/Entity;damage(Lnet/minecraft/entity/damage/DamageSource;F)Z"
            )
    )
    private boolean mca$allowArcherFollowUpHits(Entity target, DamageSource source, float amount) {
        VillagerEntityMCA archer = mca$getMcaArcherOwner((PersistentProjectileEntity)(Object)this);
        if (target instanceof LivingEntity livingTarget && archer != null) {
            livingTarget.timeUntilRegen = 0;
        }

        boolean hurt = target.damage(source, amount);
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
