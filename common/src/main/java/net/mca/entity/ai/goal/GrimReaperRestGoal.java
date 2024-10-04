package net.mca.entity.ai.goal;

import net.mca.entity.GrimReaperEntity;
import net.mca.entity.ReaperAttackState;
import net.mca.entity.ai.TaskUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.Goal;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

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
    public boolean canStart() {
        return reaper.age > lastHeal + COOLDOWN && reaper.getHealth() <= (reaper.getMaxHealth() * (1.0f - (healingCount + 1.0f) / (float)MAX_HEALING_COUNT));
    }

    @Override
    public boolean shouldContinue() {
        return healingTime > 0;
    }

    @Override
    public boolean canStop() {
        return false;
    }

    @Override
    public void start() {
        reaper.requestTeleport(reaper.getX(), reaper.getY() + 8, reaper.getZ());

        healingTime = MAX_HEALING_TIME;
        lastHeal = reaper.age;
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

        reaper.setVelocity(Vec3d.ZERO);

        if (!reaper.getWorld().isClient && healingTime % (10 + healingCount * 5) == 0) {
            reaper.setHealth(reaper.getHealth() + 1);
        }

        if (!reaper.getWorld().isClient && healingTime % 50 == 0) {
            // Let's have a light show.
            int dX = reaper.getRandom().nextInt(16) - 8;
            int dZ = reaper.getRandom().nextInt(16) - 8;
            int y = TaskUtils.getSpawnSafeTopLevel(reaper.getWorld(), (int)reaper.getX() + dX, 256, (int)reaper.getZ() + dZ);

            EntityType.LIGHTNING_BOLT.spawn((ServerWorld)reaper.getWorld(), BlockPos.ofFloored(reaper.getX() + dX, y, reaper.getZ() + dZ), SpawnReason.TRIGGERED);

            if (!reaper.getWorld().isClient && healingTime % 100 == 0) {
                // Also spawn a random enemy
                EntityType<?> m = reaper.getRandom().nextFloat() < 0.5f ? EntityType.ZOMBIE : EntityType.SKELETON;
                Entity e = m.spawn((ServerWorld)reaper.getWorld(), BlockPos.ofFloored(reaper.getX() + dX, y, reaper.getZ() + dZ), SpawnReason.TRIGGERED);

                // Equip them
                if (e != null) {
                    if (m == EntityType.SKELETON) {
                        e.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
                    } else {
                        e.equipStack(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
                    }
                    e.equipStack(EquipmentSlot.HEAD, new ItemStack(Items.IRON_HELMET));
                    e.equipStack(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                    e.equipStack(EquipmentSlot.LEGS, new ItemStack(Items.IRON_LEGGINGS));
                    e.equipStack(EquipmentSlot.FEET, new ItemStack(Items.IRON_BOOTS));
                }
            }
        }
    }
}
