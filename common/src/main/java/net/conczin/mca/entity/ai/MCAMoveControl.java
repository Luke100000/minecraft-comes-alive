package net.conczin.mca.entity.ai;

import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Owns movement rules shared by every MCA villager movement mode.
 *
 * <p>Specialised controls such as {@link ArcherMoveControl} delegate here so
 * navigation-owned movement is handled consistently.</p>
 */
class MCAMoveControl extends MoveControl {
    private static final double ADJACENT_RAISED_TARGET_EPSILON = 1.0E-6D;

    MCAMoveControl(Mob mob) {
        super(mob);
    }

    protected final boolean isClimbNavigationActive() {
        return this.mob.getNavigation() instanceof MCAGroundPathNavigation navigation
                && navigation.isControllingClimbableMovement();
    }

    @Override
    public void setWantedPosition(double x, double y, double z, double speedModifier) {
        if (this.mob.onClimbable() && this.operation == Operation.JUMPING) {
            this.operation = Operation.WAIT;
        }
        super.setWantedPosition(x, y, z, speedModifier);
    }

    @Override
    public void tick() {
        if (!isClimbNavigationActive()) {
            boolean adjacentRaisedTargetNeedsJump = shouldJumpAtVanillaAdjacentBoundary();
            super.tick();
            if (adjacentRaisedTargetNeedsJump && this.operation != Operation.JUMPING) {
                this.mob.getJumpControl().jump();
                this.operation = Operation.JUMPING;
            }
            return;
        }

        this.operation = Operation.WAIT;
        this.mob.setSpeed(0.0F);
        this.mob.setXxa(0.0F);
        this.mob.setZza(0.0F);
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
