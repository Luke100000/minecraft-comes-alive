package net.conczin.mca.entity.ai;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import org.jetbrains.annotations.Nullable;

public final class RangedWeaponHelper {
    private RangedWeaponHelper() {
    }

    private static boolean isSupported(ItemStack stack) {
        return stack.getItem() instanceof BowItem || stack.getItem() instanceof CrossbowItem;
    }

    public static boolean isHoldingSupportedWeapon(LivingEntity entity) {
        return getSupportedWeaponHoldingHand(entity) != null;
    }

    @Nullable
    private static InteractionHand getSupportedWeaponHoldingHand(LivingEntity entity) {
        if (isSupported(entity.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }
        if (isSupported(entity.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }

    @Nullable
    public static InteractionHand getBowHoldingHand(LivingEntity entity) {
        return getHoldingHand(entity, BowItem.class);
    }

    @Nullable
    public static InteractionHand getCrossbowHoldingHand(LivingEntity entity) {
        return getHoldingHand(entity, CrossbowItem.class);
    }

    public static double getAttackRangeSquared(LivingEntity entity, double maximumRangeSquared) {
        InteractionHand hand = getSupportedWeaponHoldingHand(entity);
        return hand == null ? 0.0D : getAttackRangeSquared(entity, hand, maximumRangeSquared);
    }

    public static double getAttackRangeSquared(LivingEntity entity, InteractionHand hand, double maximumRangeSquared) {
        if (!(entity.getItemInHand(hand).getItem() instanceof ProjectileWeaponItem weapon)) {
            return 0.0D;
        }

        double range = weapon.getDefaultProjectileRange();
        return Math.min(maximumRangeSquared, range * range);
    }

    @Nullable
    private static InteractionHand getHoldingHand(LivingEntity entity, Class<? extends ProjectileWeaponItem> weaponType) {
        if (weaponType.isInstance(entity.getMainHandItem().getItem())) {
            return InteractionHand.MAIN_HAND;
        }
        if (weaponType.isInstance(entity.getOffhandItem().getItem())) {
            return InteractionHand.OFF_HAND;
        }
        return null;
    }
}
