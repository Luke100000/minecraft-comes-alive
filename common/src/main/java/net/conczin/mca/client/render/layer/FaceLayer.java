package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.client.render.MCAHumanoidRenderState;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.Identifier;

public class FaceLayer<M extends HumanoidModel<MCAHumanoidRenderState>> extends VillagerLayer<M> {
    private static final int FACE_COUNT = 22;
    private final String variant;

    public FaceLayer(RenderLayerParent<MCAHumanoidRenderState, M> renderer, M model, String variant) {
        super(renderer, model);
        this.variant = variant;
    }

    @Override
    public void adjustVisibility(MCAHumanoidRenderState renderState) {
        model.setAllVisible(false);
        model.head.visible = true;
    }

    @Override
    public Identifier getSkin(MCAHumanoidRenderState renderState) {
        VillagerLike<?> villager = (VillagerLike<?>) renderState.villager;
        if (villager == null)
            return null;
        int index = (int) Math.min(FACE_COUNT - 1,
                Math.max(0, villager.getGenetics().getGene(Genetics.FACE) * FACE_COUNT));
        int time = renderState.villager.tickCount / 2
                + (int) (villager.getGenetics().getGene(Genetics.HEMOGLOBIN) * 65536);
        boolean blink = time % 50 == 1 || time % 57 == 1 || renderState.villager.isSleeping()
                || renderState.villager.isDeadOrDying();
        boolean hasHeterochromia = variant.equals("normal") && villager.getTraits().hasTrait(Traits.HETEROCHROMIA);
        String gender = villager.getGenetics().getGender().getDataName();
        String blinkTexture = blink ? "_blink" : (hasHeterochromia ? "_hetero" : "");
        return cached("skins/face/" + variant + "/" + gender + "/" + index + blinkTexture + ".png", MCA::locate);
    }
}
