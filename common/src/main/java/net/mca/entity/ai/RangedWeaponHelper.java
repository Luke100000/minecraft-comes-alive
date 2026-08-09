package net.mca.entity.ai;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.RangedWeaponItem;
import net.minecraft.util.Hand;
import org.jetbrains.annotations.Nullable;

public final class RangedWeaponHelper {
    private RangedWeaponHelper() {
    }

    public static boolean isHoldingSupportedWeapon(LivingEntity entity) {
        return getWeaponHoldingHand(entity) != null;
    }

    @Nullable
    public static Hand getWeaponHoldingHand(LivingEntity entity) {
        Hand crossbowHand = findHoldingHand(entity, CrossbowItem.class);
        return crossbowHand != null ? crossbowHand : findHoldingHand(entity, BowItem.class);
    }

    @Nullable
    public static Hand getBowHoldingHand(LivingEntity entity) {
        return getSelectedHoldingHand(entity, BowItem.class);
    }

    @Nullable
    public static Hand getCrossbowHoldingHand(LivingEntity entity) {
        return getSelectedHoldingHand(entity, CrossbowItem.class);
    }

    public static double getAttackRangeSquared(LivingEntity entity, double maximumRangeSquared) {
        Hand hand = getWeaponHoldingHand(entity);
        return hand == null ? 0.0D : getAttackRangeSquared(entity, hand, maximumRangeSquared);
    }

    public static double getAttackRangeSquared(LivingEntity entity, Hand hand) {
        if (!(entity.getStackInHand(hand).getItem() instanceof RangedWeaponItem weapon)) {
            return 0.0D;
        }

        double range = weapon.getRange();
        return range * range;
    }

    public static double getAttackRangeSquared(LivingEntity entity, Hand hand, double maximumRangeSquared) {
        return Math.min(maximumRangeSquared, getAttackRangeSquared(entity, hand));
    }

    public static boolean isValidAttackTarget(MobEntity entity, @Nullable LivingEntity target) {
        return target != null
                && target.isAlive()
                && !target.isRemoved()
                && target.getWorld() == entity.getWorld()
                && entity.canTarget(target);
    }

    @Nullable
    private static Hand getSelectedHoldingHand(LivingEntity entity, Class<? extends RangedWeaponItem> weaponType) {
        Hand hand = getWeaponHoldingHand(entity);
        return hand != null && weaponType.isInstance(entity.getStackInHand(hand).getItem()) ? hand : null;
    }

    @Nullable
    private static Hand findHoldingHand(LivingEntity entity, Class<? extends RangedWeaponItem> weaponType) {
        if (weaponType.isInstance(entity.getMainHandStack().getItem())) {
            return Hand.MAIN_HAND;
        }
        if (weaponType.isInstance(entity.getOffHandStack().getItem())) {
            return Hand.OFF_HAND;
        }
        return null;
    }
}
