package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

public class FaceLayer<S extends HumanoidRenderState & VillagerStateHolder, M extends HumanoidModel<S>> extends VillagerLayer<S, M> {
    private static final int FACE_COUNT = 22;

    private final String variant;

    public FaceLayer(RenderLayerParent<S, M> renderer, M model, String variant) {
        super(renderer, model);
        this.variant = variant;
        if (model instanceof CommonVillagerModel<?> commonVillagerModel) {
            commonVillagerModel.setRenderMask(CommonVillagerModel.RenderMask.HEAD_ONLY);
        }
    }

    @Override
    protected void prepareModel(S state) {
        setAllVisible(this.model, false);
        this.model.head.visible = true;
    }

    @Override
    protected boolean isTranslucent() {
        return true;
    }

    @Override
    public Identifier getSkin(S state) {
        int index = (int) Math.min(FACE_COUNT - 1, Math.max(0, CommonVillagerModel.getVillager(state).getGenetics().getGene(Genetics.FACE) * FACE_COUNT));
        net.minecraft.world.entity.LivingEntity villager = (net.minecraft.world.entity.LivingEntity) CommonVillagerModel.getVillager(state);
        int time = villager.tickCount / 2 + (int) (CommonVillagerModel.getVillager(state).getGenetics().getGene(Genetics.HEMOGLOBIN) * 65536);
        boolean blink = time % 50 == 1 || time % 57 == 1 || villager.isSleeping() || villager.isDeadOrDying();
        boolean hasHeterochromia = variant.equals("normal") && CommonVillagerModel.getVillager(state).getTraits().hasTrait(Traits.HETEROCHROMIA);
        String gender = CommonVillagerModel.getVillager(state).getGenetics().getGender().getDataName();
        String blinkTexture = blink ? "_blink" : (hasHeterochromia ? "_hetero" : "");

        return cached("skins/face/" + variant + "/" + gender + "/" + index + blinkTexture + ".png", MCA::locate);
    }
}
