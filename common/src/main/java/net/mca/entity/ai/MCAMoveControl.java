package net.mca.entity.ai;

import net.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.minecraft.entity.ai.control.MoveControl;
import net.minecraft.entity.mob.MobEntity;

/**
 * Owns movement rules shared by every MCA villager movement mode.
 *
 * <p>Specialised controls such as {@link ArcherMoveControl} delegate here so
 * navigation-owned climbing is handled consistently.</p>
 */
class MCAMoveControl extends MoveControl {
    MCAMoveControl(MobEntity entity) {
        super(entity);
    }

    protected final boolean isClimbNavigationActive() {
        return this.entity.getNavigation() instanceof MCAGroundPathNavigation navigation
                && navigation.isControllingClimbable();
    }

    @Override
    public void moveTo(double x, double y, double z, double speed) {
        if (this.entity.isClimbing() && this.state == State.JUMPING) {
            this.state = State.WAIT;
        }
        super.moveTo(x, y, z, speed);
    }

    @Override
    public void tick() {
        if (!isClimbNavigationActive()) {
            super.tick();
            return;
        }

        this.state = State.WAIT;
        this.entity.setMovementSpeed(0.0F);
        this.entity.setSidewaysSpeed(0.0F);
        this.entity.setForwardSpeed(0.0F);
    }
}
