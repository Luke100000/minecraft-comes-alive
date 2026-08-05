package net.conczin.mca.client.model;

import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

public class ZombieVillagerEntityModelMCA<T extends LivingEntity & VillagerLike<T>> extends VillagerEntityModelMCA<T> {
    public ZombieVillagerEntityModelMCA(ModelPart tree) {
        super(tree);
    }

    public ZombieVillagerEntityModelMCA(ModelPart tree, PlayerAnimationBridge<T> animationBridge) {
        super(tree, animationBridge);
    }

    @Override
    public void setupAnim(T villager, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        super.setupAnim(villager, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        AnimationUtils.animateZombieArms(leftArm, rightArm, false, attackTime, animationProgress);
        leftArmwear.copyFrom(leftArm);
        rightArmwear.copyFrom(rightArm);
    }
}
