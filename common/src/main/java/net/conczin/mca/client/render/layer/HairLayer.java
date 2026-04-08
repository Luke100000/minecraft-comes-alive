package net.conczin.mca.client.render.layer;

import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.client.resources.ColorPalette;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.DyeColor;

public class HairLayer<S extends HumanoidRenderState & VillagerStateHolder, M extends HumanoidModel<S>> extends VillagerLayer<S, M> {
    public HairLayer(RenderLayerParent<S, M> renderer, M model) {
        super(renderer, model);
        if (model instanceof CommonVillagerModel<?> commonVillagerModel) {
            commonVillagerModel.setRenderMask(CommonVillagerModel.RenderMask.NO_LEGS);
        }
    }

    @Override
    protected void prepareModel(S state) {
        setAllVisible(this.model, true);
        this.model.leftLeg.visible = false;
        this.model.rightLeg.visible = false;
    }

    @Override
    public Identifier getSkin(S state) {
        var visuals = CommonVillagerModel.getVisuals(state);
        String identifier = visuals.hair();
        if (identifier.startsWith("immersive_library:")) {
            return SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring(18)));
        }
        return cached(identifier, Identifier::parse);
    }

    @Override
    protected Identifier getOverlay(S state) {
        return cached(CommonVillagerModel.getVisuals(state).hair().replace(".png", "_overlay.png"), Identifier::parse);
    }

    private int getRainbow(int tickCount, int entityId, float tickDelta) {
        int n = Math.abs(tickCount) / 25 + entityId;
        int o = DyeColor.values().length;
        int p = n % o;
        int q = (n + 1) % o;
        float r = ((float) (Math.abs(tickCount) % 25) + tickDelta) / 25.0f;
        return ARGB.srgbLerp(r, DyeColor.byId(p).getTextureDiffuseColor(), DyeColor.byId(q).getTextureDiffuseColor());
    }

    @Override
    public int getColor(S state, float tickDelta) {
        var visuals = CommonVillagerModel.getVisuals(state);
        if (visuals.rainbow()) {
            return getRainbow(visuals.tickCount(), visuals.entityId(), tickDelta);
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
