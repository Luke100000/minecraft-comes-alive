package net.conczin.mca.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;

/** Transfers the visible MCA pose through an EMF-interceptable player model. */
public final class PlayerAnimationBridge<T extends LivingEntity> {
    private final PlayerModel<T> source;

    public PlayerAnimationBridge(PlayerModel<T> source) {
        this.source = source;
    }

    public void apply(HumanoidModel<T> target, PoseStack matrices, int light, int overlay) {
        target.copyPropertiesTo(source);
        McaModelAnimationDriver.animate(source.head, matrices, light, overlay);
        source.copyPropertiesTo(target);
    }

    public void applyArm(HumanoidModel<T> target, PoseStack matrices, int light, int overlay, boolean right) {
        target.copyPropertiesTo(source);
        var arm = right ? source.rightArm : source.leftArm;
        arm.xRot = 0.0F;
        McaModelAnimationDriver.animate(arm, matrices, light, overlay);
        source.copyPropertiesTo(target);
    }
}
