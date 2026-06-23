package net.conczin.mca.mixin;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractArrow.class)
abstract class MixinAbstractArrow {
    @Redirect(
            method = "onHitEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Entity;hurtOrSimulate(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
            )
    )
    @SuppressWarnings("deprecation")
    private boolean mca$allowMcaArcherArrowsThroughHurtCooldown(Entity target, DamageSource source, float damage) {
        if (!(target instanceof LivingEntity livingTarget) || !mca$isMcaArcherArrow()) {
            return target.hurtOrSimulate(source, damage);
        }

        if (target.level().isClientSide()) {
            return true;
        }

        livingTarget.invulnerableTime = 0;
        return target.hurtOrSimulate(source, damage);
    }

    private boolean mca$isMcaArcherArrow() {
        Entity owner = ((AbstractArrow) (Object) this).getOwner();
        return owner instanceof VillagerEntityMCA villager && villager.getProfession() == ProfessionsMCA.ARCHER;
    }
}
