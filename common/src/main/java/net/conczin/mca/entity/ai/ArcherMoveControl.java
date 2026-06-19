package net.conczin.mca.entity.ai;

import net.conczin.mca.MCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

public class ArcherMoveControl extends MoveControl {
    private static final double STUCK_SPEED_SQUARED = 1.0E-4;
    private static final int BLOCKED_TICKS_BEFORE_JUMP = 3;

    private LivingEntity faceTarget;
    private LivingEntity retreatTarget;
    private float retreatBackwards;
    private double retreatSpeedModifier;
    private int retreatBlockedTicks;

    public ArcherMoveControl(Mob mob) {
        super(mob);
    }

    public void retreatFrom(LivingEntity target, float backwards, double speedModifier) {
        this.faceTarget = null;
        this.retreatTarget = target;
        this.retreatBackwards = backwards;
        this.retreatSpeedModifier = speedModifier;
    }

    public void face(LivingEntity target) {
        this.faceTarget = target;
    }

    public boolean isRetreatingFrom(LivingEntity target) {
        return this.retreatTarget != null && this.retreatTarget != target && this.retreatTarget.isAlive() && !this.retreatTarget.isRemoved();
    }

    @Override
    public void tick() {
        LivingEntity target = this.retreatTarget;
        this.retreatTarget = null;
        if (target == null || !target.isAlive() || target.isRemoved()) {
            LivingEntity faceTarget = this.faceTarget;
            this.faceTarget = null;
            this.retreatBlockedTicks = 0;
            if (faceTarget != null && faceTarget.isAlive() && !faceTarget.isRemoved()) {
                tickFace(faceTarget);
                return;
            }

            super.tick();
            return;
        }

        this.operation = Operation.WAIT;
        syncRotation(target);
        jumpIfBlockedLikeVanilla(target);
        this.mob.setSpeed((float)(this.retreatSpeedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
        this.mob.setZza(this.retreatBackwards);
        this.mob.setXxa(0.0F);
    }

    private void tickFace(LivingEntity target) {
        this.operation = Operation.WAIT;
        syncRotation(target);
        this.mob.setZza(0.0F);
        this.mob.setXxa(0.0F);
    }

    private void syncRotation(LivingEntity target) {
        float yRot = getYRotTo(this.mob, target);
        this.mob.setYRot(yRot);
        this.mob.yBodyRot = yRot;
        this.mob.yHeadRot = yRot;
    }

    private void jumpIfBlockedLikeVanilla(LivingEntity target) {
        if (!this.mob.onGround()) {
            return;
        }

        BlockPos pos = this.mob.blockPosition();
        BlockState blockState = this.mob.level().getBlockState(pos);
        VoxelShape shape = blockState.getCollisionShape(this.mob.level(), pos);
        if (shouldJumpForCollisionShape(pos, blockState, shape)
                || isRetreatStuck()) {
            logJump(target);
            this.retreatBlockedTicks = 0;
            this.mob.getJumpControl().jump();
            this.operation = Operation.JUMPING;
        }
    }

    private boolean shouldJumpForCollisionShape(BlockPos pos, BlockState blockState, VoxelShape shape) {
        return !shape.isEmpty()
                && this.mob.getY() < shape.max(Direction.Axis.Y) + pos.getY()
                && !blockState.is(BlockTags.DOORS)
                && !blockState.is(BlockTags.FENCES);
    }

    private boolean isRetreatStuck() {
        if (!this.mob.horizontalCollision && !this.mob.minorHorizontalCollision) {
            this.retreatBlockedTicks = 0;
            return false;
        }

        double horizontalSpeedSquared = this.mob.getDeltaMovement().horizontalDistanceSqr();
        if (horizontalSpeedSquared > STUCK_SPEED_SQUARED) {
            this.retreatBlockedTicks = 0;
            return false;
        }

        this.retreatBlockedTicks++;
        return this.retreatBlockedTicks >= BLOCKED_TICKS_BEFORE_JUMP;
    }

    private void logJump(LivingEntity target) {
        if (!MCA.platformHelper.isDevelopmentEnvironment()) {
            return;
        }

        MCA.LOGGER.info(
                "[MCA Archer MoveControl] entity={} entityName=\"{}\" target={} targetName=\"{}\" action=jump_for_retreat blockedTicks={} horizontalCollision={} minorHorizontalCollision={} onGround={} deltaMovement={} pos={} targetPos={}",
                this.mob.getStringUUID(),
                this.mob.getName().getString(),
                target.getStringUUID(),
                target.getName().getString(),
                this.retreatBlockedTicks,
                this.mob.horizontalCollision,
                this.mob.minorHorizontalCollision,
                this.mob.onGround(),
                this.mob.getDeltaMovement(),
                this.mob.blockPosition(),
                target.blockPosition()
        );
    }

    private static float getYRotTo(Mob mob, LivingEntity target) {
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        return Math.abs(dx) <= 1.0E-5 && Math.abs(dz) <= 1.0E-5
                ? mob.getYRot()
                : (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
    }
}
