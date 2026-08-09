package net.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import dev.architectury.platform.Platform;
import net.mca.MCA;
import net.mca.entity.ai.ArcherMoveControl;
import net.mca.entity.ai.RangedWeaponHelper;
import net.minecraft.entity.CrossbowUser;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.brain.EntityLookTarget;
import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.LookTargetUtil;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.BowItem;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;

public class BowTask<E extends MobEntity & CrossbowUser> extends MultiTickTask<E> {
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
                MemoryModuleType.LOOK_TARGET, MemoryModuleState.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryModuleState.VALUE_PRESENT
        ), 1200);
        this.fireInterval = fireInterval;
        this.rangeSquared = range * range;
    }

    @Override
    protected boolean shouldRun(ServerWorld world, E entity) {
        return RangedWeaponHelper.isValidAttackTarget(entity, getAttackTarget(entity))
                && RangedWeaponHelper.getBowHoldingHand(entity) != null;
    }

    @Override
    protected boolean shouldKeepRunning(ServerWorld world, E entity, long time) {
        return RangedWeaponHelper.isValidAttackTarget(entity, getAttackTarget(entity))
                && RangedWeaponHelper.getBowHoldingHand(entity) != null;
    }

    @Override
    protected void run(ServerWorld world, E entity, long time) {
        entity.setAttacking(true);
        this.lastTarget = null;
        this.attackCooldown = 0;
        this.lostSightTicks = 0;
    }

    @Override
    protected void keepRunning(ServerWorld world, E entity, long time) {
        LivingEntity target = getAttackTarget(entity);
        Hand bowHand = RangedWeaponHelper.getBowHoldingHand(entity);
        //keep distance
        if (!RangedWeaponHelper.isValidAttackTarget(entity, target) || bowHand == null) {
            stopUsing(entity, target, false, 0.0, "invalid_target");
            return;
        }

        if (entity.getMoveControl() instanceof ArcherMoveControl archerMoveControl && archerMoveControl.isEmergencyFleeing()) {
            stopUsing(entity, target, false, entity.squaredDistanceTo(target), "emergency_fleeing");
            return;
        }

        //strafe
        if (target != this.lastTarget) {
            stopUsing(entity, target, false, entity.squaredDistanceTo(target), "target_changed");
            this.lastTarget = target;
            this.attackCooldown = 0;
            this.lostSightTicks = 0;
        }

        //shoot
        boolean visible = LookTargetUtil.isVisibleInMemory(entity, target);
        double distanceSquared = entity.squaredDistanceTo(target);
        double attackRangeSquared = RangedWeaponHelper.getAttackRangeSquared(entity, bowHand, this.rangeSquared);

        entity.getBrain().remember(MemoryModuleType.LOOK_TARGET, new EntityLookTarget(target, true));
        entity.lookAtEntity(target, LOOK_SPEED, LOOK_SPEED);

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

            int pullTime = entity.getItemUseTime();
            if (visible && pullTime >= DRAW_TICKS) {
                entity.stopUsingItem();
                entity.attack(target, BowItem.getPullProgress(pullTime));
                this.attackCooldown = this.fireInterval;
                logAction(entity, target, visible, distanceSquared, "release", "draw_complete");
            }
        } else if (visible && distanceSquared <= attackRangeSquared && this.attackCooldown <= 0) {
            entity.setCurrentHand(bowHand);
            logAction(entity, target, visible, distanceSquared, "start_using", "ready");
        }
    }

    @Override
    protected void finishRunning(ServerWorld world, E entity, long time) {
        super.finishRunning(world, entity, time);
        entity.setAttacking(false);
        this.lastTarget = null;
        this.attackCooldown = 0;
        this.lostSightTicks = 0;
        stopUsing(entity, getAttackTarget(entity), false, 0.0, "behavior_stop");
    }

    private void stopUsing(E entity, LivingEntity target, boolean visible, double distanceSquared, String reason) {
        if (entity.isUsingItem()) {
            logAction(entity, target, visible, distanceSquared, "stop_using", reason);
            entity.stopUsingItem();
        }
    }

    private void logAction(E entity, LivingEntity target, boolean visible, double distanceSquared, String action, String reason) {
        if (!Platform.isDevelopmentEnvironment()) {
            return;
        }

        MCA.LOGGER.info(
                "[MCA Archer BowTask] entity={} entityName=\"{}\" target={} targetName=\"{}\" action={} reason={} distSqr={} rangeSqr={} los={} lostSightTicks={} attackCooldown={} usingItem={} ticksUsingItem={} mainHand={} offHand={} bowHand={}",
                entity.getUuidAsString(),
                entity.getName().getString(),
                target == null ? "none" : target.getUuidAsString(),
                target == null ? "none" : target.getName().getString(),
                action,
                reason,
                String.format("%.2f", distanceSquared),
                String.format("%.2f", this.rangeSquared),
                visible,
                this.lostSightTicks,
                this.attackCooldown,
                entity.isUsingItem(),
                entity.getItemUseTime(),
                entity.getMainHandStack(),
                entity.getOffHandStack(),
                getBowHoldingHandName(entity)
        );
    }

    private static LivingEntity getAttackTarget(LivingEntity entity) {
        return entity.getBrain().getOptionalMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
    }

    private static String getBowHoldingHandName(MobEntity entity) {
        Hand hand = RangedWeaponHelper.getBowHoldingHand(entity);
        return hand == null ? "none" : hand.name();
    }
}
