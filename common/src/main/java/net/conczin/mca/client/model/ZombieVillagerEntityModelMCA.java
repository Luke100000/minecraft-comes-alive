package net.conczin.mca.client.model;

import net.conczin.mca.client.render.VillagerRenderState;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.geom.ModelPart;

public class ZombieVillagerEntityModelMCA extends VillagerEntityModelMCA {
    public ZombieVillagerEntityModelMCA(ModelPart tree) {
        super(tree);
    }

    @Override
    public void setupAnim(VillagerRenderState state) {
        super.setupAnim(state);
        AnimationUtils.animateZombieArms(leftArm, rightArm, false, state);
    }
}
