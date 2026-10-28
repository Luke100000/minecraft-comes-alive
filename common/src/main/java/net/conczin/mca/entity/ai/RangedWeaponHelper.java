package net.conczin.mca.entity.ai;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public final class RangedWeaponHelper {
    private static final double BOW_TARGET_HEIGHT_FRACTION = 0.5D;
    public static final float BOW_PROJECTILE_SPEED = 1.6F;
    public static final float BOW_INACCURACY = 3.0F;
    private static final double ARROW_AIR_DRAG = 0.99D;
    private static final double ARROW_GRAVITY = 0.05D;
    private static final int TRAJECTORY_SOLVER_ITERATIONS = 32;
    private static final int MAX_TRAJECTORY_TICKS = 80;

    private RangedWeaponHelper() {
    }

    public record BowShot(Vec3 direction, double flightTicks) {
    }

    public static Optional<BowShot> solveBowShot(Vec3 launchPosition, Vec3 targetBasePosition,
                                                  double targetHeight, Vec3 observedTargetVelocity) {
        if (!Double.isFinite(targetHeight) || targetHeight <= 0
                || !isFinite(launchPosition) || !isFinite(targetBasePosition) || !isFinite(observedTargetVelocity)) {
            return Optional.empty();
        }
        // Observed motion is deliberately bounded; a teleport must not produce a
        // shot at a fictional interception point far outside combat range.
        Vec3 motion = observedTargetVelocity.lengthSqr() > 0.36
                ? observedTargetVelocity.normalize().scale(0.6) : observedTargetVelocity;
        Vec3 offset = targetBasePosition.add(0, targetHeight * BOW_TARGET_HEIGHT_FRACTION, 0)
                .subtract(launchPosition);
        for (int tick = 1; tick <= MAX_TRAJECTORY_TICKS; tick++) {
            if (requiredLaunchVelocity(offset, motion, tick).lengthSqr() > BOW_PROJECTILE_SPEED * BOW_PROJECTILE_SPEED) {
                continue;
            }
            double lower = tick - 1;
            double upper = tick;
            for (int iteration = 0; iteration < TRAJECTORY_SOLVER_ITERATIONS; iteration++) {
                double middle = (lower + upper) * 0.5;
                if (requiredLaunchVelocity(offset, motion, middle).lengthSqr()
                        > BOW_PROJECTILE_SPEED * BOW_PROJECTILE_SPEED) {
                    lower = middle;
                } else {
                    upper = middle;
                }
            }
            return Optional.of(new BowShot(requiredLaunchVelocity(offset, motion, upper), upper));
        }
        return Optional.empty();
    }

    private static Vec3 requiredLaunchVelocity(Vec3 offset, Vec3 motion, double ticks) {
        int wholeTicks = (int) ticks;
        double fraction = ticks - wholeTicks;
        double dragSum = (1 - Math.pow(ARROW_AIR_DRAG, wholeTicks)) / (1 - ARROW_AIR_DRAG)
                + fraction * Math.pow(ARROW_AIR_DRAG, wholeTicks);
        double gravityDrop = ARROW_GRAVITY * (ticks - dragSum) / (1 - ARROW_AIR_DRAG);
        return offset.add(motion.scale(ticks)).add(0, gravityDrop, 0).scale(1 / dragSum);
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    public static Vec3 observedTargetVelocity(LivingEntity target) {
        return new Vec3(target.getX() - target.xo, target.onGround() ? 0 : target.getDeltaMovement().y,
                target.getZ() - target.zo);
    }

    public static boolean hasClearBowTrajectory(PathfinderMob shooter, Vec3 launchPosition, BowShot shot) {
        Vec3 position = launchPosition;
        Vec3 velocity = shot.direction().normalize().scale(BOW_PROJECTILE_SPEED);
        for (int tick = 0; tick < Math.ceil(shot.flightTicks()); tick++) {
            double fraction = Math.min(1, shot.flightTicks() - tick);
            Vec3 next = position.add(velocity.scale(fraction));
            if (!shooter.level().hasChunkAt(BlockPos.containing(next))
                    || shooter.level().clip(new ClipContext(position, next, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, shooter)).getType() != HitResult.Type.MISS) {
                return false;
            }
            position = next;
            velocity = velocity.scale(ARROW_AIR_DRAG).subtract(0, ARROW_GRAVITY, 0);
        }
        return true;
    }

    public static boolean canShootBowFrom(PathfinderMob shooter, Vec3 feetPosition, LivingEntity target) {
        InteractionHand hand = getBowHoldingHand(shooter);
        if (hand == null) {
            return false;
        }
        ItemStack weapon = shooter.getItemInHand(hand);
        if (!canPredictBowTrajectory(shooter, weapon, shooter.getProjectile(weapon))) {
            return true;
        }
        Vec3 launch = feetPosition.add(0, shooter.getEyeHeight() - 0.1F, 0);
        return solveBowShot(launch, target.position(), target.getBbHeight(), observedTargetVelocity(target))
                .filter(shot -> hasClearBowTrajectory(shooter, launch, shot)).isPresent();
    }

    public static boolean canPredictBowTrajectory(LivingEntity shooter, ItemStack weapon, ItemStack ammunition) {
        // Unknown item factories and water do not promise ordinary air-arrow
        // physics. Keep their existing firing contract at both draw and release.
        return !shooter.isInWater() && weapon.getItem().getClass() == BowItem.class
                && (ammunition.is(Items.ARROW) || ammunition.is(Items.TIPPED_ARROW)
                || ammunition.is(Items.SPECTRAL_ARROW));
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
