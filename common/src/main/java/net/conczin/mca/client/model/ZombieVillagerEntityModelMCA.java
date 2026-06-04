package net.conczin.mca.client.model;

import net.conczin.mca.client.render.MCAHumanoidRenderState;
import net.minecraft.client.model.geom.ModelPart;

public class ZombieVillagerEntityModelMCA extends VillagerEntityModelMCA {
    public ZombieVillagerEntityModelMCA(ModelPart tree) {
        super(tree);
    }

    @Override
    public void setupAnim(MCAHumanoidRenderState renderState) {
        super.setupAnim(renderState);
    }
}
