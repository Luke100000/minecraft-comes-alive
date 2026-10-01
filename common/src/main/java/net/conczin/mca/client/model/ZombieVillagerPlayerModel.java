package net.conczin.mca.client.model;

import net.conczin.mca.entity.VillagerLike;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

public final class ZombieVillagerPlayerModel<T extends LivingEntity & VillagerLike<T>> extends VillagerPlayerModel<T> {
    public ZombieVillagerPlayerModel(ModelPart root) {
        super(root);
    }

    @Override
    public void setupAnim(
            T villager,
            float limbAngle,
            float limbDistance,
            float animationProgress,
            float headYaw,
            float headPitch
    ) {
        super.setupAnim(villager, limbAngle, limbDistance, animationProgress, headYaw, headPitch);
        AnimationUtils.animateZombieArms(leftArm, rightArm, false, attackTime, animationProgress);
    }
}
