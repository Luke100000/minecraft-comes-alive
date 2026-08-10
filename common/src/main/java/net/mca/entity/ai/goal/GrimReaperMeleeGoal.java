package net.mca.entity.ai.goal;

import net.mca.entity.GrimReaperEntity;
import net.mca.entity.ReaperAttackState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

public class GrimReaperMeleeGoal extends Goal {
    private static final int COOLDOWN = 150;
    private final GrimReaperEntity reaper;

    private int blockDuration;
    private int attackDuration;
    private int retreatDuration;
    private int lastAttack = 0;

    public GrimReaperMeleeGoal(GrimReaperEntity reaper) {
        this.reaper = reaper;
    }

    @Override
    public boolean canUse() {
        LivingEntity entityToAttack = reaper.getTarget();
        return entityToAttack != null
                && reaper.distanceToSqr(entityToAttack) <= 144
                && reaper.tickCount > lastAttack + COOLDOWN
                && reaper.getAttackState() != ReaperAttackState.REST;
    }

    @Override
    public boolean canContinueToUse() {
        return retreatDuration > 0 && reaper.getAttackState() != ReaperAttackState.REST;
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    @Override
    public void start() {
        blockDuration = 50;
        attackDuration = 100;
        retreatDuration = 20;

        lastAttack = reaper.tickCount;
    }

    @Override
    public void stop() {
        super.stop();
        reaper.setAttackState(ReaperAttackState.IDLE);
    }

    //curse the player if he tries to block
    private void curse() {
        LivingEntity entityToAttack = reaper.getTarget();
        if (entityToAttack instanceof Player player) {
            // Check to see if the player's blocking, then teleport behind them.
            // Also, randomly swap their selected item with something else in the hotbar and apply blindness.
            if (player.isBlocking()) {
                double dX = reaper.getX() - player.getX();
                double dZ = reaper.getZ() - player.getZ();

                reaper.teleportTo(player.getX() - (dX * 2), player.getY() + 2, reaper.getZ() - (dZ * 2));

                if (!reaper.level().isClientSide && reaper.getRandom().nextFloat() >= 0.20F) {
                    int currentItem = player.getInventory().selected;
                    int randomItem = reaper.getRandom().nextInt(9);
                    ItemStack currentItemStack = player.getInventory().getItem(currentItem);
                    ItemStack randomItemStack = player.getInventory().getItem(randomItem);

                    player.getInventory().setItem(currentItem, randomItemStack);
                    player.getInventory().setItem(randomItem, currentItemStack);

                    entityToAttack.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 200));
                }
            }
        }
    }

    @Override
    public void tick() {
        //shouldn't be required, yet melee is triggered while resting
        if (reaper.getAttackState() == ReaperAttackState.REST) {
            return;
        }

        LivingEntity entityToAttack = reaper.getTarget();
        if (entityToAttack == null) {
            retreatDuration = 0;
            return;
        }

        if (blockDuration > 0) {
            blockDuration--;
            reaper.setAttackState(ReaperAttackState.BLOCK);

            if (blockDuration == 0) {
                curse();
            }

            // We are blocking, let's retreat if someone comes too close
            if (reaper.distanceToSqr(entityToAttack) <= 4.0D) {
                int rX = reaper.getRandom().nextInt(10);
                int rY = reaper.getRandom().nextInt(6);
                int rZ = reaper.getRandom().nextInt(10);

                reaper.teleportTo(reaper.getX() - 5 + rX, reaper.getY() + rY, reaper.getZ() - 5 + rZ);
                reaper.getNavigation().stop();
            }

            //move up to get a nice overview
            Vec3 deltaMovement = reaper.getDeltaMovement();
            reaper.setDeltaMovement(new Vec3(deltaMovement.x, 0.05, deltaMovement.z));
        } else if (attackDuration > 0) {
            attackDuration--;
            reaper.setAttackState(ReaperAttackState.PRE);

            //charge
            Vec3 dir = entityToAttack.position().subtract(reaper.position()).normalize().scale(0.15);
            reaper.push(dir.x, dir.y, dir.z);

            //attack
            if (reaper.distanceToSqr(entityToAttack) <= 1.0D) {
                reaper.swing(InteractionHand.MAIN_HAND);
                attackDuration = 0;

                entityToAttack.hurt(reaper.level().damageSources().mobAttack(reaper), (float)reaper.getAttributeValue(Attributes.ATTACK_DAMAGE));

                //apply wither effect
                entityToAttack.addEffect(new MobEffectInstance(MobEffects.WITHER, 200));
            }
        } else {
            retreatDuration--;
            reaper.setAttackState(ReaperAttackState.POST);

            //retreat
            Vec3 dir = entityToAttack.position().subtract(reaper.position()).normalize().scale(-0.1);
            reaper.setDeltaMovement(dir.x, dir.y, dir.z);
        }
    }
}
