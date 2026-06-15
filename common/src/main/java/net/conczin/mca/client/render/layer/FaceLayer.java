package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.FaceList;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

import java.util.Objects;

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
    public int getColor(S state, float tickDelta) {
        return VillagerVisuals.require(state).eyeDye();
    }

    @Override
    public void renderFinal(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, S state, float tickDelta, boolean visible, boolean glowing) {
        int tint = LivingEntityRenderer.getOverlayCoords(state, 0.0F);

        Identifier skin = getSkin(state);
        if (canUse(skin)) {
            var visuals = VillagerVisuals.require(state);
            Identifier leftSkin = cached(skin.toString().replace(".png", "_left.png"), Identifier::parse);
            Identifier rightSkin = cached(skin.toString().replace(".png", "_right.png"), Identifier::parse);

            if (canUse(leftSkin) && canUse(rightSkin)) {
                int leftColor = visuals.heterochromia() ? visuals.eyeLeftDye() : visuals.eyeDye();
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, leftColor, leftSkin, tint, visible, glowing, state);
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, visuals.eyeDye(), rightSkin, tint, visible, glowing, state);
            } else {
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, visuals.eyeDye(), skin, tint, visible, glowing, state);
            }
        }

        Identifier overlay = getOverlay(state);
        if (!Objects.equals(skin, overlay) && canUse(overlay)) {
            renderModel(poseStack, submitNodeCollector, lightCoords, this.model, 0xFFFFFF, overlay, tint, visible, glowing, state);
        }
    }

    @Override
    public Identifier getSkin(S state) {
        var visuals = VillagerVisuals.require(state);
        boolean blink = visuals.isBlinking();
        Gender gender = Gender.byName(visuals.genderDataName());

        FaceList list = FaceList.getInstance();
        if (list == null) {
            int index = blink ? 2 : (int) Math.min(6, Math.max(0, visuals.faceGene() * 7));
            return cached("skins/face/" + variant + "/" + index + ".png", MCA::locate);
        }
        return list.pick(variant, gender, visuals.faceGene(), blink);
    }
}
