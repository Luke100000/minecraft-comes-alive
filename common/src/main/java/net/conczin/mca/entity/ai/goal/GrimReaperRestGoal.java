package net.conczin.mca.entity.ai.goal;

import net.conczin.mca.entity.GrimReaperEntity;
import net.conczin.mca.entity.ReaperAttackState;
import net.conczin.mca.entity.ai.TaskUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public class GrimReaperRestGoal extends Goal {
    private static final int COOLDOWN = 1000;
    private final GrimReaperEntity reaper;
    private int lastHeal = -COOLDOWN;
    private int healingCount = 0;
    private static final int MAX_HEALING_COUNT = 5;
    private static final int MAX_HEALING_TIME = 400;
    private int healingTime;

    public GrimReaperRestGoal(GrimReaperEntity reaper) {
        this.reaper = reaper;
    }

    @Override
    public boolean canUse() {
        return reaper.tickCount > lastHeal + COOLDOWN && reaper.getHealth() <= (reaper.getMaxHealth() * (1.0f - (healingCount + 1.0f) / (float)MAX_HEALING_COUNT));
    }

    @Override
    public boolean canContinueToUse() {
        return healingTime > 0;
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public void start() {
        reaper.teleportTo(reaper.getX(), reaper.getY() + 8, reaper.getZ());

        healingTime = MAX_HEALING_TIME;
        lastHeal = reaper.tickCount;
        healingCount++;
    }

    @Override
    public void stop() {
        reaper.setAttackState(ReaperAttackState.IDLE);
    }

    @Override
    public void tick() {
        healingTime--;
        reaper.setAttackState(ReaperAttackState.REST);

        reaper.setDeltaMovement(Vec3.ZERO);

        if (!reaper.level().isClientSide && healingTime % (10 + healingCount * 5) == 0) {
            reaper.setHealth(reaper.getHealth() + 1);
        }

        if (!reaper.level().isClientSide && healingTime % 50 == 0) {
            // Let's have a light show.
            int dX = reaper.getRandom().nextInt(16) - 8;
            int dZ = reaper.getRandom().nextInt(16) - 8;
            int y = TaskUtils.getSpawnSafeTopLevel(reaper.level(), (int)reaper.getX() + dX, 256, (int)reaper.getZ() + dZ);

            EntityType.LIGHTNING_BOLT.spawn((ServerLevel)reaper.level(), BlockPos.containing(reaper.getX() + dX, y, reaper.getZ() + dZ), MobSpawnType.TRIGGERED);

            if (!reaper.level().isClientSide && healingTime % 100 == 0) {
                // Also spawn a random enemy
                EntityType<?> m = reaper.getRandom().nextFloat() < 0.5f ? EntityType.ZOMBIE : EntityType.SKELETON;
                Entity e = m.spawn((ServerLevel)reaper.level(), BlockPos.containing(reaper.getX() + dX, y, reaper.getZ() + dZ), MobSpawnType.TRIGGERED);

                // Equip them
                if (e != null) {
                    if (m == EntityType.SKELETON) {
                        e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                    } else {
                        e.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                    }
                    e.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                    e.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                    e.setItemSlot(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                    e.setItemSlot(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
                }
            }
        }
    }
}
