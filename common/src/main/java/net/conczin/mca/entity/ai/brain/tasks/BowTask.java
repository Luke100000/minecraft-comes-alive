package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.RangedWeaponHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.item.BowItem;

public class BowTask<E extends Mob & CrossbowAttackMob> extends Behavior<E> {
    private static final int DRAW_TICKS = 20;
    private static final int LOST_SIGHT_CANCEL_TICKS = 60;
    private static final float LOOK_SPEED = 30.0F;

    private final int fireInterval;
    private final double rangeSquared;
    private LivingEntity lastTarget;
    private int attackCooldown;
    private int lostSightTicks;

    public BowTask(int fireInterval, int range) {
        super(ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT
        ), 1200);
        this.fireInterval = fireInterval;
        this.rangeSquared = range * range;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, E entity) {
        return hasValidTarget(entity, getAttackTarget(entity)) && RangedWeaponHelper.getBowHoldingHand(entity) != null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, E entity, long gameTime) {
        return hasValidTarget(entity, getAttackTarget(entity)) && RangedWeaponHelper.getBowHoldingHand(entity) != null;
    }

    @Override
    protected void start(ServerLevel level, E entity, long gameTime) {
        entity.setAggressive(true);
        this.lastTarget = null;
        this.attackCooldown = 0;
        this.lostSightTicks = 0;
    }

    @Override
    protected void tick(ServerLevel level, E entity, long gameTime) {
        LivingEntity target = getAttackTarget(entity);
        InteractionHand bowHand = RangedWeaponHelper.getBowHoldingHand(entity);
        if (!hasValidTarget(entity, target) || bowHand == null) {
            if (entity.isUsingItem()) {
                logAction(entity, target, false, 0.0, "stop_using", "invalid_target");
                entity.stopUsingItem();
            }
            return;
        }

        if (isEmergencyFleeing(entity)) {
            if (entity.isUsingItem()) {
                logAction(entity, target, false, entity.distanceToSqr(target), "stop_using", "emergency_fleeing");
                entity.stopUsingItem();
            }
            return;
        }

        if (target != this.lastTarget) {
            if (entity.isUsingItem()) {
                logAction(entity, target, false, entity.distanceToSqr(target), "stop_using", "target_changed");
                entity.stopUsingItem();
            }
            this.lastTarget = target;
            this.attackCooldown = 0;
            this.lostSightTicks = 0;
        }

        boolean visible = entity.getSensing().hasLineOfSight(target);
        double distanceSquared = entity.distanceToSqr(target);
        double attackRangeSquared = RangedWeaponHelper.getAttackRangeSquared(entity, bowHand, this.rangeSquared);

        entity.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        entity.getLookControl().setLookAt(target, LOOK_SPEED, LOOK_SPEED);

        if (visible) {
            this.lostSightTicks = 0;
        } else {
            this.lostSightTicks++;
        }

        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }

        if (entity.isUsingItem()) {
            if (!visible && this.lostSightTicks > LOST_SIGHT_CANCEL_TICKS) {
                logAction(entity, target, visible, distanceSquared, "stop_using", "lost_sight");
                entity.stopUsingItem();
                return;
            }

            int pullTime = entity.getTicksUsingItem();
            if (visible && pullTime >= DRAW_TICKS) {
                entity.stopUsingItem();
                entity.performRangedAttack(target, BowItem.getPowerForTime(pullTime));
                this.attackCooldown = this.fireInterval;
                logAction(entity, target, visible, distanceSquared, "release", "draw_complete");
            }
        } else if (visible
                && distanceSquared <= attackRangeSquared
                && this.attackCooldown <= 0) {
            entity.startUsingItem(bowHand);
            logAction(entity, target, visible, distanceSquared, "start_using", "ready");
        }
    }

    @Override
    protected void stop(ServerLevel level, E entity, long gameTime) {
        super.stop(level, entity, gameTime);
        entity.setAggressive(false);
        this.lastTarget = null;
        this.attackCooldown = 0;
        this.lostSightTicks = 0;
        if (entity.isUsingItem()) {
            logAction(entity, getAttackTarget(entity), false, 0.0, "stop_using", "behavior_stop");
            entity.stopUsingItem();
        }
    }

    private void logAction(E entity, LivingEntity target, boolean visible, double distanceSquared, String action, String reason) {
        if (!MCA.platformHelper.isDevelopmentEnvironment()) {
            return;
        }

        MCA.LOGGER.info(
                "[MCA Archer BowTask] entity={} entityName=\"{}\" target={} targetName=\"{}\" action={} reason={} distSqr={} rangeSqr={} los={} lostSightTicks={} attackCooldown={} usingItem={} ticksUsingItem={} mainHand={} offHand={} bowHand={}",
                entity.getStringUUID(),
                entity.getName().getString(),
                target == null ? "none" : target.getStringUUID(),
                target == null ? "none" : target.getName().getString(),
                action,
                reason,
                String.format("%.2f", distanceSquared),
                String.format("%.2f", this.rangeSquared),
                visible,
                this.lostSightTicks,
                this.attackCooldown,
                entity.isUsingItem(),
                entity.getTicksUsingItem(),
                entity.getMainHandItem(),
                entity.getOffhandItem(),
                getBowHoldingHandName(entity)
        );
    }

    private static LivingEntity getAttackTarget(LivingEntity entity) {
        return entity.getBrain().getMemoryInternal(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    private static boolean hasValidTarget(Mob entity, LivingEntity target) {
        return target != null
               && target.isAlive()
               && !target.isRemoved()
               && target.level() == entity.level()
               && entity.canAttack(target);
    }

    private static String getBowHoldingHandName(Mob entity) {
        InteractionHand hand = RangedWeaponHelper.getBowHoldingHand(entity);
        return hand == null ? "none" : hand.name();
    }

    private static boolean isEmergencyFleeing(Mob entity) {
        return entity.getMoveControl() instanceof net.conczin.mca.entity.ai.ArcherMoveControl archerMoveControl && archerMoveControl.isEmergencyFleeing();
    }
}
