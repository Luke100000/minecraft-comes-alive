package net.conczin.mca.entity.ai;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class RangedWeaponHelper {
    private static final double BOW_TARGET_HEIGHT_FRACTION = 0.5D;
    private static final double BOW_PROJECTILE_SPEED = 1.6D;
    private static final double ARROW_AIR_DRAG = 0.99D;
    private static final double ARROW_GRAVITY = 0.05D;
    private static final int TRAJECTORY_SOLVER_ITERATIONS = 32;
    private static final int MAX_TRAJECTORY_TICKS = 80;

    private RangedWeaponHelper() {
    }

    public static boolean isHoldingSupportedWeapon(LivingEntity entity) {
        return getWeaponHoldingHand(entity) != null;
    }

    @Nullable
    public static InteractionHand getWeaponHoldingHand(LivingEntity entity) {
        InteractionHand crossbowHand = findHoldingHand(entity, CrossbowItem.class);
        return crossbowHand != null ? crossbowHand : findHoldingHand(entity, BowItem.class);
    }

    @Nullable
    public static InteractionHand getBowHoldingHand(LivingEntity entity) {
        return getSelectedHoldingHand(entity, BowItem.class);
    }

    @Nullable
    public static InteractionHand getCrossbowHoldingHand(LivingEntity entity) {
        return getSelectedHoldingHand(entity, CrossbowItem.class);
    }

    public static double getAttackRangeSquared(LivingEntity entity, double maximumRangeSquared) {
        InteractionHand hand = getWeaponHoldingHand(entity);
        return hand == null ? 0.0D : getAttackRangeSquared(entity, hand, maximumRangeSquared);
    }

    public static double getAttackRangeSquared(LivingEntity entity, InteractionHand hand) {
        if (!(entity.getItemInHand(hand).getItem() instanceof ProjectileWeaponItem weapon)) {
            return 0.0D;
        }

        double range = weapon.getDefaultProjectileRange();
        return range * range;
    }

    public static double getAttackRangeSquared(LivingEntity entity, InteractionHand hand, double maximumRangeSquared) {
        return Math.min(maximumRangeSquared, getAttackRangeSquared(entity, hand));
    }

    public static boolean isValidAttackTarget(Mob entity, @Nullable LivingEntity target) {
        return target != null
               && target.isAlive()
               && target.level() == entity.level()
               && entity.canAttack(target);
    }

    public static Vec3 calculateBowShotVector(
            Vec3 projectilePosition,
            Vec3 targetBasePosition,
            double targetHeight
    ) {
        double x = targetBasePosition.x - projectilePosition.x;
        double z = targetBasePosition.z - projectilePosition.z;
        double horizontalDistance = Math.sqrt(x * x + z * z);
        double targetY = targetBasePosition.y + targetHeight * BOW_TARGET_HEIGHT_FRACTION;
        double verticalDistance = targetY - projectilePosition.y;
        if (horizontalDistance < 1.0E-6D) {
            return new Vec3(x, verticalDistance, z);
        }

        double searchRadius = horizontalDistance * 2.0D + Math.abs(verticalDistance) + 2.0D;
        double lower = verticalDistance - searchRadius;
        double upper = verticalDistance + searchRadius;
        for (int i = 0; i < TRAJECTORY_SOLVER_ITERATIONS; i++) {
            double candidate = (lower + upper) * 0.5D;
            double simulatedHeight = simulateArrowHeightAtDistance(horizontalDistance, candidate);
            if (simulatedHeight < verticalDistance) {
                lower = candidate;
            } else {
                upper = candidate;
            }
        }

        return new Vec3(x, (lower + upper) * 0.5D, z);
    }

    private static double simulateArrowHeightAtDistance(double horizontalDistance, double verticalAim) {
        double vectorLength = Math.sqrt(horizontalDistance * horizontalDistance + verticalAim * verticalAim);
        double horizontalVelocity = BOW_PROJECTILE_SPEED * horizontalDistance / vectorLength;
        double verticalVelocity = BOW_PROJECTILE_SPEED * verticalAim / vectorLength;
        double horizontalPosition = 0.0D;
        double verticalPosition = 0.0D;

        for (int tick = 0; tick < MAX_TRAJECTORY_TICKS; tick++) {
            double nextHorizontalPosition = horizontalPosition + horizontalVelocity;
            double nextVerticalPosition = verticalPosition + verticalVelocity;
            if (nextHorizontalPosition >= horizontalDistance) {
                double progress = (horizontalDistance - horizontalPosition)
                        / (nextHorizontalPosition - horizontalPosition);
                return verticalPosition + (nextVerticalPosition - verticalPosition) * progress;
            }

            horizontalPosition = nextHorizontalPosition;
            verticalPosition = nextVerticalPosition;
            horizontalVelocity *= ARROW_AIR_DRAG;
            verticalVelocity = verticalVelocity * ARROW_AIR_DRAG - ARROW_GRAVITY;
        }

        return Double.NEGATIVE_INFINITY;
    }

    @Nullable
    private static InteractionHand getSelectedHoldingHand(
            LivingEntity entity,
            Class<? extends ProjectileWeaponItem> weaponType
    ) {
        InteractionHand hand = getWeaponHoldingHand(entity);
        return hand != null && weaponType.isInstance(entity.getItemInHand(hand).getItem()) ? hand : null;
    }

    @Nullable
    private static InteractionHand findHoldingHand(LivingEntity entity, Class<? extends ProjectileWeaponItem> weaponType) {
        if (weaponType.isInstance(entity.getMainHandItem().getItem())) {
            return InteractionHand.MAIN_HAND;
        }

        if (weaponType.isInstance(entity.getOffhandItem().getItem())) {
            return InteractionHand.OFF_HAND;
        }

        return null;
    }
}
