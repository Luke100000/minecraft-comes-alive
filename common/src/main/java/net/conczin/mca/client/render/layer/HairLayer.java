package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.client.render.RainbowColor;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.client.resources.ColorPalette;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.conczin.mca.util.ImmersiveLibraryIds;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

public class HairLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends VillagerLayer<S, M> {
    public HairLayer(RenderLayerParent<S, M> renderer, M model) {
        super(renderer, model);
    }

    @Override
    protected void prepareModel(S state) {
        setAllVisible(this.model, true);
        hideLegs(this.model);
        hideBreasts(this.model);
    }

    private static void hideBreasts(HumanoidModel<?> model) {
        if (model instanceof CommonVillagerModel<?> villagerModel) {
            villagerModel.getBreastParts().forEach(part -> part.visible = false);
        }
    }

    @Override
    public void renderFinal(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int lightCoords, S state, float tickDelta, boolean visible, boolean glowing) {
        VillagerVisuals visuals = VillagerVisuals.require(state);

        int overlay = LivingEntityRenderer.getOverlayCoords(state, 0.0F);
        int color = getColor(state, tickDelta);
        for (LayeredHair.Category category : LayeredHair.Category.RENDER_ORDER) {
            String identifier = visuals.layeredHair(category);
            if (MCA.isBlankString(identifier)) {
                continue;
            }

            Identifier texture = getTexture(identifier);
            if (canUse(texture)) {
                renderModel(poseStack, submitNodeCollector, lightCoords, this.model, color, texture, overlay, visible, glowing, state);
            }
        }
    }

    private Identifier getTexture(String identifier) {
        var contentId = ImmersiveLibraryIds.contentId(identifier);
        if (contentId.isPresent()) {
            return SkinCache.getTextureIdentifier(contentId.getAsInt());
        }
        return cached(identifier, Identifier::parse);
    }

    @Override
    public int getColor(S state, float tickDelta) {
        var visuals = VillagerVisuals.require(state);
        if (visuals.rainbowHair()) {
            return RainbowColor.sheep(visuals.tickCount() + tickDelta);
        }

        int hairDye = visuals.hairDye();
        if (hairDye != 0xFF000000) {
            return hairDye;
        }

        float albinism = visuals.albinism() ? 0.1f : 1.0f;

        return ColorPalette.HAIR.getColor(
                visuals.eumelaninGene() * albinism,
                visuals.pheomelaninGene() * albinism,
                0
        );
    }
}
