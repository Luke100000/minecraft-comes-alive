package net.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.mca.MCA;
import net.mca.MCA;
import net.mca.entity.ai.ArcherMoveControl;
import net.mca.entity.ai.RangedWeaponHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
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
    protected boolean checkExtraStartConditions(ServerLevel world, E entity) {
        return RangedWeaponHelper.isValidAttackTarget(entity, getAttackTarget(entity))
                && RangedWeaponHelper.getBowHoldingHand(entity) != null;
    }

    @Override
    protected boolean canStillUse(ServerLevel world, E entity, long time) {
        return RangedWeaponHelper.isValidAttackTarget(entity, getAttackTarget(entity))
                && RangedWeaponHelper.getBowHoldingHand(entity) != null;
    }

    @Override
    protected void start(ServerLevel world, E entity, long time) {
        entity.setAggressive(true);
        this.lastTarget = null;
        this.attackCooldown = 0;
        this.lostSightTicks = 0;
    }

    @Override
    protected void tick(ServerLevel world, E entity, long time) {
        LivingEntity target = getAttackTarget(entity);
        InteractionHand bowHand = RangedWeaponHelper.getBowHoldingHand(entity);
        //keep distance
        if (!RangedWeaponHelper.isValidAttackTarget(entity, target) || bowHand == null) {
            stopUsing(entity, target, false, 0.0, "invalid_target");
            return;
        }

        if (entity.getMoveControl() instanceof ArcherMoveControl archerMoveControl && archerMoveControl.isEmergencyFleeing()) {
            stopUsing(entity, target, false, entity.distanceToSqr(target), "emergency_fleeing");
            return;
        }

        //strafe
        if (target != this.lastTarget) {
            stopUsing(entity, target, false, entity.distanceToSqr(target), "target_changed");
            this.lastTarget = target;
            this.attackCooldown = 0;
            this.lostSightTicks = 0;
        }

        //shoot
        boolean visible = BehaviorUtils.canSee(entity, target);
        double distanceSquared = entity.distanceToSqr(target);
        double attackRangeSquared = RangedWeaponHelper.getAttackRangeSquared(entity, bowHand, this.rangeSquared);

        entity.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
        entity.lookAt(target, LOOK_SPEED, LOOK_SPEED);

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
                stopUsing(entity, target, visible, distanceSquared, "lost_sight");
                return;
            }

            int pullTime = entity.getTicksUsingItem();
            if (visible && pullTime >= DRAW_TICKS) {
                entity.releaseUsingItem();
                entity.performRangedAttack(target, BowItem.getPowerForTime(pullTime));
                this.attackCooldown = this.fireInterval;
                logAction(entity, target, visible, distanceSquared, "release", "draw_complete");
            }
        } else if (visible && distanceSquared <= attackRangeSquared && this.attackCooldown <= 0) {
            entity.startUsingItem(bowHand);
            logAction(entity, target, visible, distanceSquared, "start_using", "ready");
        }
    }

    @Override
    protected void stop(ServerLevel world, E entity, long time) {
        super.stop(world, entity, time);
        entity.setAggressive(false);
        this.lastTarget = null;
        this.attackCooldown = 0;
        this.lostSightTicks = 0;
        stopUsing(entity, getAttackTarget(entity), false, 0.0, "behavior_stop");
    }

    private void stopUsing(E entity, LivingEntity target, boolean visible, double distanceSquared, String reason) {
        if (entity.isUsingItem()) {
            logAction(entity, target, visible, distanceSquared, "stop_using", reason);
            entity.releaseUsingItem();
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

    private static String getBowHoldingHandName(Mob entity) {
        InteractionHand hand = RangedWeaponHelper.getBowHoldingHand(entity);
        return hand == null ? "none" : hand.name();
    }
}
