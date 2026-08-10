package net.mca.entity.ai;

import net.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;

/**
 * Owns movement rules shared by every MCA villager movement mode.
 *
 * <p>Specialised controls such as {@link ArcherMoveControl} delegate here so
 * navigation-owned climbing is handled consistently.</p>
 */
class MCAMoveControl extends MoveControl {
    MCAMoveControl(Mob entity) {
        super(entity);
    }

    protected final boolean isClimbNavigationActive() {
        return this.mob.getNavigation() instanceof MCAGroundPathNavigation navigation
                && navigation.isControllingClimbable();
    }

    @Override
    public void setWantedPosition(double x, double y, double z, double speed) {
        if (this.mob.onClimbable() && this.operation == Operation.JUMPING) {
            this.operation = Operation.WAIT;
        }
        super.setWantedPosition(x, y, z, speed);
    }

    @Override
    public void tick() {
        if (!isClimbNavigationActive()) {
            super.tick();
            return;
        }

        this.operation = Operation.WAIT;
        this.mob.setSpeed(0.0F);
        this.mob.setXxa(0.0F);
        this.mob.setZza(0.0F);
    }
}
