package net.conczin.mca.entity.ai;

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
    private LivingEntity faceTarget;
    private LivingEntity retreatTarget;
    private float retreatBackwards;
    private double retreatSpeedModifier;
    private boolean fleeing;

    public ArcherMoveControl(Mob mob) {
        super(mob);
    }

    public void setFleeing(boolean fleeing) {
        this.fleeing = fleeing;
    }

    public boolean isFleeing() {
        return this.fleeing;
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

    @Override
    public void tick() {
        LivingEntity target = this.retreatTarget;
        this.retreatTarget = null;
        if (target == null || !target.isAlive() || target.isRemoved()) {
            LivingEntity faceTarget = this.faceTarget;
            this.faceTarget = null;
            if (faceTarget != null && faceTarget.isAlive() && !faceTarget.isRemoved()) {
                tickFace(faceTarget);
                return;
            }

            if (this.operation == Operation.STRAFE) {
                checkStepAndJump(this.strafeForwards, this.strafeRight);
            } else {
                this.mob.setXxa(0.0F);
                this.mob.setZza(0.0F);
            }
            super.tick();
            return;
        }

        this.operation = Operation.WAIT;
        syncRotation(target);
        checkStepAndJump(this.retreatBackwards, 0.0F);
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
        float targetYRot = getYRotTo(this.mob, target);
        float yRot = this.rotlerp(this.mob.getYRot(), targetYRot, 30.0F);
        this.mob.setYRot(yRot);
        this.mob.yBodyRot = yRot;
        this.mob.yHeadRot = yRot;
    }

    private void checkStepAndJump(float xa, float za) {
        float sin = Mth.sin(this.mob.getYRot() * (float) (Math.PI / 180.0));
        float cos = Mth.cos(this.mob.getYRot() * (float) (Math.PI / 180.0));
        float dx = za * cos - xa * sin;
        float dz = xa * cos + za * sin;

        double len = Math.sqrt(dx * dx + dz * dz);
        if (len > 1.0E-5) {
            double unitX = dx / len;
            double unitZ = dz / len;
            // Look ahead by the mob's bounding box radius plus a small margin (0.1 blocks)
            double checkX = this.mob.getX() + unitX * (this.mob.getBbWidth() / 2.0D + 0.1D);
            double checkZ = this.mob.getZ() + unitZ * (this.mob.getBbWidth() / 2.0D + 0.1D);
            BlockPos targetPos = BlockPos.containing(checkX, this.mob.getY(), checkZ);
            BlockState targetState = this.mob.level().getBlockState(targetPos);
            VoxelShape targetShape = targetState.getCollisionShape(this.mob.level(), targetPos);

            if (!targetShape.isEmpty()
                    && this.mob.getY() < targetShape.max(Direction.Axis.Y) + targetPos.getY()
                    && !targetState.is(BlockTags.DOORS)
                    && !targetState.is(BlockTags.FENCES)) {
                double heightDiff = (targetShape.max(Direction.Axis.Y) + targetPos.getY()) - this.mob.getY();
                if (heightDiff > this.mob.maxUpStep() && heightDiff <= 1.0D && this.mob.onGround()) {
                    // Check if the 2 blocks above the target step are clear (clearance for a 2-block-tall mob)
                    BlockPos above1 = targetPos.above();
                    BlockPos above2 = targetPos.above(2);
                    boolean clearAbove = this.mob.level().getBlockState(above1).getCollisionShape(this.mob.level(), above1).isEmpty()
                            && this.mob.level().getBlockState(above2).getCollisionShape(this.mob.level(), above2).isEmpty();
                    if (clearAbove) {
                        this.mob.getJumpControl().jump();
                    }
                }
            }
        }
    }

    private static float getYRotTo(Mob mob, LivingEntity target) {
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        return Math.abs(dx) <= 1.0E-5 && Math.abs(dz) <= 1.0E-5
                ? mob.getYRot()
                : (float)(Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
    }
}
