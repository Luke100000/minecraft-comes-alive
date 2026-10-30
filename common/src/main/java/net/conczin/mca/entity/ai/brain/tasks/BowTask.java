package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.RangedWeaponHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.BowItem;

public class BowTask<E extends VillagerEntityMCA> extends Behavior<E> {
    private static final int DRAW_TICKS = 20;
    private static final int LOST_SIGHT_CANCEL_TICKS = 20;
    private static final int BLOCKED_RELEASE_GRACE_TICKS = 20;
    private static final float LOOK_SPEED = 30.0F;
    private static final float SHOT_YAW_TOLERANCE = 15.0F;

    private final int fireInterval;
    private final double rangeSquared;
    private LivingEntity lastTarget;
    private int attackCooldown;
    private int lostSightTicks;
    private int blockedReleaseTicks;

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
        return RangedWeaponHelper.isValidAttackTarget(entity, getAttackTarget(entity))
               && RangedWeaponHelper.getBowHoldingHand(entity) != null;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, E entity, long gameTime) {
        return RangedWeaponHelper.isValidAttackTarget(entity, getAttackTarget(entity))
               && RangedWeaponHelper.getBowHoldingHand(entity) != null;
    }

    @Override
    protected void start(ServerLevel level, E entity, long gameTime) {
        entity.setAggressive(true);
        this.lastTarget = null;
        this.attackCooldown = 0;
        this.lostSightTicks = 0;
        this.blockedReleaseTicks = 0;
    }

    @Override
    protected void tick(ServerLevel level, E entity, long gameTime) {
        LivingEntity target = getAttackTarget(entity);
        InteractionHand bowHand = RangedWeaponHelper.getBowHoldingHand(entity);
        if (!RangedWeaponHelper.isValidAttackTarget(entity, target) || bowHand == null) {
            entity.setAggressive(false);
            if (entity.isUsingItem()) {
                logAction(entity, target, false, 0.0, "stop_using", bowHand == null ? "weapon_removed" : "target_lost");
                entity.stopUsingItem();
            }
            this.blockedReleaseTicks = 0;
            return;
        }

        RangedCombatState state = RangedCombatState.current(entity).orElse(RangedCombatState.HOLD);
        boolean aiming = state.permitsAiming(entity) && !entity.getVillagerBrain().isPanicking();
        entity.setAggressive(aiming);
        if (!aiming) {
            if (entity.isUsingItem()) {
                logAction(entity, target, false, entity.distanceToSqr(target), "stop_using",
                        state == RangedCombatState.KITE ? "terrain_escape" : state.name().toLowerCase(java.util.Locale.ROOT));
                entity.stopUsingItem();
            }
            this.blockedReleaseTicks = 0;
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
            this.blockedReleaseTicks = 0;
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
                logAction(entity, target, visible, distanceSquared, "stop_using", "draw_grace_expired");
                entity.stopUsingItem();
                this.blockedReleaseTicks = 0;
                return;
            }

            int pullTime = entity.getTicksUsingItem();
            if (pullTime >= DRAW_TICKS) {
                boolean inRange = distanceSquared <= attackRangeSquared;
                boolean clear = visible && inRange && RangedWeaponHelper.canShootBowFrom(entity, entity.position(), target);
                boolean aligned = isAlignedForMovingShot(entity, target);
                if (clear && aligned && entity.tryPerformBowAttack(target, BowItem.getPowerForTime(pullTime))) {
                    entity.stopUsingItem();
                    this.blockedReleaseTicks = 0;
                    this.attackCooldown = this.fireInterval;
                    logAction(entity, target, visible, distanceSquared, "release", "draw_complete");
                } else {
                    if (this.blockedReleaseTicks++ == 0) {
                        logAction(entity, target, visible, distanceSquared, "hold_draw",
                                !visible ? "eye_los_lost" : !inRange ? "release_out_of_range"
                                        : !aligned ? "aim_settling" : clear ? "release_failed" : "trajectory_blocked");
                    }
                    if (this.blockedReleaseTicks > BLOCKED_RELEASE_GRACE_TICKS) {
                        logAction(entity, target, visible, distanceSquared, "stop_using", "draw_grace_expired");
                        entity.stopUsingItem();
                        this.blockedReleaseTicks = 0;
                    }
                }
            }
        } else if (state.permitsDrawing() && visible
                && distanceSquared <= attackRangeSquared
                && this.attackCooldown <= 0
                && RangedWeaponHelper.canShootBowFrom(entity, entity.position(), target)) {
            entity.startUsingItem(bowHand);
            this.blockedReleaseTicks = 0;
            logAction(entity, target, visible, distanceSquared, "start_using", "ready");
        }
    }

    private static boolean isAlignedForMovingShot(Mob entity, LivingEntity target) {
        if (entity.getNavigation().isDone()) {
            return true;
        }
        double dx = target.getX() - entity.getX();
        double dz = target.getZ() - entity.getZ();
        // Like LookControl, a directly vertical target has no horizontal bearing.
        if (Math.abs(dx) <= 1.0E-5 && Math.abs(dz) <= 1.0E-5) {
            return true;
        }
        float targetYaw = (float) Math.toDegrees(Mth.atan2(dz, dx)) - 90.0F;
        return Math.abs(Mth.wrapDegrees(targetYaw - entity.yHeadRot)) <= SHOT_YAW_TOLERANCE
                && Math.abs(Mth.wrapDegrees(targetYaw - entity.yBodyRot)) <= entity.getMaxHeadYRot();
    }

    @Override
    protected void stop(ServerLevel level, E entity, long gameTime) {
        super.stop(level, entity, gameTime);
        entity.setAggressive(false);
        this.lastTarget = null;
        this.attackCooldown = 0;
        this.lostSightTicks = 0;
        this.blockedReleaseTicks = 0;
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
                "[MCA Archer BowTask] entity={} entityName=\"{}\" target={} targetName=\"{}\" action={} reason={} distSqr={} rangeSqr={} los={} lostSightTicks={} attackCooldown={} usingItem={} ticksUsingItem={} mainHand={} offHand={} bowHand={} movement={} headYaw={} bodyYaw={} navDone={}",
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
                getBowHoldingHandName(entity),
                RangedCombatState.current(entity).orElse(null),
                entity.yHeadRot,
                entity.yBodyRot,
                entity.getNavigation().isDone()
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
