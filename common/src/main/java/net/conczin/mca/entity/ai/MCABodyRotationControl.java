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
        // Combat stance owns presentation through both draw and cooldown.
        // Native navigation retains movement yaw, path following and jumping.
        if (this.faceLookDirection.getAsBoolean()) {
            this.mob.yBodyRot = this.mob.yHeadRot;
            return;
        }
        super.clientTick();
    }
}
