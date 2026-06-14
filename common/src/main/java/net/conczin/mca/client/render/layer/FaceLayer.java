package net.conczin.mca.client.render.layer;

import net.conczin.mca.MCA;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.FaceList;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

public class FaceLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends VillagerLayer<S, M> {
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
    public Identifier getSkin(S state) {
        var visuals = VillagerVisuals.require(state);
        boolean blink = visuals.isBlinking();
        boolean hasHeterochromia = variant.equals("normal") && visuals.heterochromia();
        String blinkTexture = blink ? "_blink" : (hasHeterochromia ? "_hetero" : "");
        Gender gender = Gender.byName(visuals.genderDataName());

        FaceList list = FaceList.getInstance();
        if (list == null) {
            int index = (int) Math.min(21, Math.max(0, visuals.faceGene() * 22));
            return cached("skins/face/" + variant + "/" + visuals.genderDataName() + "/" + index + blinkTexture + ".png", MCA::locate);
        }
        return list.pick(variant, gender, visuals.faceGene(), blinkTexture);
    }
}
