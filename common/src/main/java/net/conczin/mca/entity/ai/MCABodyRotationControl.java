package net.conczin.mca.entity.ai;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.BodyRotationControl;

import java.util.function.BooleanSupplier;

/**
 * Preserves vanilla body rotation except while MCA explicitly owns combat-facing presentation.
 */
public class MCABodyRotationControl extends BodyRotationControl {
    private final Mob mob;
    private final BooleanSupplier faceLookDirection;

    public MCABodyRotationControl(Mob mob, BooleanSupplier faceLookDirection) {
        super(mob);
        this.mob = mob;
        this.faceLookDirection = faceLookDirection;
    }

    @Override
    public void clientTick() {
        // Vanilla owns the body facing while walking. Turning the body toward
        // a bow's LOOK_TARGET during escape can rotate it 180 degrees against
        // navigation, making the fleeing archer appear to run backward/spin.
        double dx = this.mob.getX() - this.mob.xo;
        double dz = this.mob.getZ() - this.mob.zo;
        if (this.faceLookDirection.getAsBoolean() && dx * dx + dz * dz <= 2.5000003E-7D) {
            this.mob.yBodyRot = this.mob.yHeadRot;
            return;
        }
        super.clientTick();
    }
}
