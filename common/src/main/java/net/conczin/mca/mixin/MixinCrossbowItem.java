package net.conczin.mca.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.RangedWeaponHelper;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.SpectralArrow;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keep vanilla crossbow firing, but use MCA's ballistic aim for the primary ordinary arrow. */
@Mixin(CrossbowItem.class)
abstract class MixinCrossbowItem {
    @ModifyExpressionValue(
            method = "shootProjectile",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/CrossbowItem;getProjectileShotVector(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/phys/Vec3;F)Lorg/joml/Vector3f;")
    )
    private Vector3f mca$correctArcherArrowAim(Vector3f vanillaDirection, LivingEntity shooter,
                                                Projectile projectile, int projectileIndex, float speed,
                                                float inaccuracy, float sideAngle, @Nullable LivingEntity target) {
        if (!(shooter instanceof VillagerEntityMCA villager)
                || villager.getProfession() != ProfessionsMCA.ARCHER
                || target == null || projectileIndex != 0
                || shooter.isInWater() || projectile.isNoGravity()
                || projectile.getClass() != Arrow.class && projectile.getClass() != SpectralArrow.class) {
            return vanillaDirection;
        }
        InteractionHand hand = RangedWeaponHelper.getCrossbowHoldingHand(villager);
        if (hand == null || !villager.getItemInHand(hand).is(Items.CROSSBOW)) {
            // Modded crossbows may intentionally use a different shot model.
            return vanillaDirection;
        }
        // Aiming at the upper torso leaves clearance beneath low cover while
        // retaining a point inside the target hitbox; center-mass clips the
        // lower edge of one-block head-height firing apertures at range.
        return RangedWeaponHelper.solveArrowShot(projectile.position(),
                        target.position().add(0, target.getBbHeight() * 0.2D, 0),
                        target.getBbHeight(), RangedWeaponHelper.observedTargetVelocity(target), speed)
                .map(shot -> shot.direction().normalize().toVector3f())
                .orElse(vanillaDirection);
    }
}
