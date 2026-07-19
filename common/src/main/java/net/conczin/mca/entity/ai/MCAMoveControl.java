package net.conczin.mca.entity.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.MoveControl;

class MCAMoveControl extends MoveControl {
    MCAMoveControl(Mob mob) {
        super(mob);
    }

    @Override
    public void setWantedPosition(double x, double y, double z, double speedModifier) {
        if (this.mob.onClimbable() && this.operation == Operation.JUMPING) {
            this.operation = Operation.WAIT;
        }
        super.setWantedPosition(x, y, z, speedModifier);
    }
}
