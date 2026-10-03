package net.conczin.mca.entity.ai;

import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;

/**
 * Owns movement rules shared by every MCA villager movement mode.
 */
public class MCAMoveControl<T extends Mob> extends MoveControl<T> {
    private static final double ADJACENT_RAISED_TARGET_EPSILON = 1.0E-6D;

    public MCAMoveControl(T mob) {
        super(mob);
    }

    public void strafe(float forwards, float right, double speedModifier) {
        super.strafe(forwards, right);
        this.speedModifier = speedModifier;
    }

    public void stopStrafing() {
        if (this.operation == Operation.STRAFE) {
            this.operation = Operation.WAIT;
        }
        // Vanilla WAIT clears forward input only; released lateral input otherwise keeps accelerating the mob.
        this.mob.setXxa(0.0F);
    }

    protected final boolean isClimbNavigationActive() {
        return this.mob.getNavigation() instanceof MCAGroundPathNavigation navigation
                && navigation.isControllingClimbableMovement();
    }

    @Override
    public void setWantedPosition(double x, double y, double z, double speedModifier) {
        if (isClimbNavigationActive() && this.operation == Operation.JUMPING) {
            this.operation = Operation.WAIT;
        }
        super.setWantedPosition(x, y, z, speedModifier);
    }

    @Override
    public void tick() {
        if (!isClimbNavigationActive()) {
            boolean adjacentRaisedTargetNeedsJump = shouldJumpAtVanillaAdjacentBoundary();
            if (this.operation == Operation.MOVE_TO && this.mob.getInBlockState().is(Blocks.SCAFFOLDING)) {
                tickPassThroughScaffoldingMove();
            } else {
                super.tick();
            }
            if (adjacentRaisedTargetNeedsJump && this.operation != Operation.JUMPING) {
                this.mob.getJumpControl().jump();
                this.operation = Operation.JUMPING;
            }
            return;
        }

        if (this.operation == Operation.MOVE_TO) {
            double dx = this.wantedX - this.mob.getX();
            double dz = this.wantedZ - this.mob.getZ();
            if (dx * dx + dz * dz > MIN_SPEED_SQR) {
                float wantedYaw = (float)(Mth.atan2(dz, dx) * 180.0F / (float)Math.PI) - 90.0F;
                this.mob.setYRot(this.rotlerp(this.mob.getYRot(), wantedYaw, MAX_TURN));
            }
        }

        this.operation = Operation.WAIT;
        this.mob.setSpeed(0.0F);
        this.mob.setXxa(0.0F);
        this.mob.setZza(0.0F);
    }

    private void tickPassThroughScaffoldingMove() {
        // Mirror vanilla MOVE_TO, but omit its context-free block-collision jump:
        // scaffolding reports a stable collision shape there even when the entity should pass through it.
        this.operation = Operation.WAIT;
        double dx = this.wantedX - this.mob.getX();
        double dy = this.wantedY - this.mob.getY();
        double dz = this.wantedZ - this.mob.getZ();
        if (dx * dx + dy * dy + dz * dz < MIN_SPEED_SQR) {
            this.mob.setZza(0.0F);
            return;
        }

        float wantedYaw = (float)(Mth.atan2(dz, dx) * 180.0F / (float)Math.PI) - 90.0F;
        this.mob.setYRot(this.rotlerp(this.mob.getYRot(), wantedYaw, MAX_TURN));
        this.mob.setSpeed((float)(this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
        if (dy > this.mob.maxUpStep() && dx * dx + dz * dz < Math.max(1.0F, this.mob.getBbWidth())) {
            this.mob.getJumpControl().jump();
            this.operation = Operation.JUMPING;
        }
    }

    /**
     * Vanilla MoveControl jumps toward a raised target only while horizontal distance squared is strictly below
     * max(1, mob width). Block-centre to adjacent block-centre is exactly 1.0, so an ordinary one-block path rise can
     * sit forever on that excluded boundary. Preserve vanilla behavior everywhere else and include only that edge.
     */
    private boolean shouldJumpAtVanillaAdjacentBoundary() {
        if (this.operation != Operation.MOVE_TO) {
            return false;
        }

        double dx = this.wantedX - this.mob.getX();
        double dz = this.wantedZ - this.mob.getZ();
        double dy = this.wantedY - this.mob.getY();
        if (dy <= this.mob.maxUpStep()) {
            return false;
        }

        double horizontalDistanceSqr = dx * dx + dz * dz;
        double vanillaBoundary = Math.max(1.0F, this.mob.getBbWidth());
        return horizontalDistanceSqr >= vanillaBoundary - ADJACENT_RAISED_TARGET_EPSILON
                && horizontalDistanceSqr <= vanillaBoundary + ADJACENT_RAISED_TARGET_EPSILON;
    }
}
