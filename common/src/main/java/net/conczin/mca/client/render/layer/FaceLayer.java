package net.conczin.mca.client.render.layer;

import net.conczin.mca.MCA;
import net.conczin.mca.client.render.VillagerVisualSnapshot;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.ResourceLocation;

public class FaceLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends VillagerLayer<S, M> {
    private static final int FACE_COUNT = 22;

    private final String variant;

    public FaceLayer(RenderLayerParent<S, M> renderer, M model, String variant) {
        super(renderer, model);
        this.variant = variant;
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
    public ResourceLocation getSkin(S state) {
        VillagerVisualSnapshot visuals = VillagerVisualSnapshot.require(state);
        int index = (int) Math.min(FACE_COUNT - 1, Math.max(0, visuals.faceGene() * FACE_COUNT));
        boolean hasHeterochromia = variant.equals("normal") && visuals.heterochromia();
        String blinkTexture = visuals.isBlinking() ? "_blink" : (hasHeterochromia ? "_hetero" : "");
        return cached("skins/face/" + variant + "/" + visuals.genderDataName() + "/" + index + blinkTexture + ".png", MCA::locate);
    }
}
