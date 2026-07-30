package net.conczin.mca.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.PartPose;

/**
 * Snapshot of the canonical humanoid bones for Minecraft's deferred model renderer.
 */
public record HumanoidModelPose(
        PartPose root,
        PartPose head,
        PartPose body,
        PartPose leftArm,
        PartPose rightArm,
        PartPose leftLeg,
        PartPose rightLeg
) {
    public static HumanoidModelPose capture(HumanoidModel<?> model) {
        return new HumanoidModelPose(
                model.root().storePose(),
                model.head.storePose(),
                model.body.storePose(),
                model.leftArm.storePose(),
                model.rightArm.storePose(),
                model.leftLeg.storePose(),
                model.rightLeg.storePose()
        );
    }

    public void applyTo(HumanoidModel<?> model) {
        model.root().loadPose(root);
        model.head.loadPose(head);
        model.body.loadPose(body);
        model.leftArm.loadPose(leftArm);
        model.rightArm.loadPose(rightArm);
        model.leftLeg.loadPose(leftLeg);
        model.rightLeg.loadPose(rightLeg);
    }
}
