package net.mca.client.model;

import net.mca.entity.VillagerLike;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

public class ZombieVillagerEntityModelMCA<T extends LivingEntity & VillagerLike<T>> extends VillagerEntityModelMCA<T> {
    public ZombieVillagerEntityModelMCA(ModelPart tree) {
        super(tree);
    }

    @Override
    public void setupAnim(T villager, float limbAngle, float limbDistance, float animationProgress, float headYaw, float headPitch) {
        super.setupAnim(villager, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        AnimationUtils.animateZombieArms(leftArm, rightArm, false, attackTime, animationProgress);
        leftArmwear.copyFrom(leftArm);
        rightArmwear.copyFrom(rightArm);
    }
}
