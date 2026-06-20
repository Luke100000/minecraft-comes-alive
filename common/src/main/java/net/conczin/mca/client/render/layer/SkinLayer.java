package net.conczin.mca.client.render.layer;

import net.conczin.mca.MCA;
import net.conczin.mca.client.render.VillagerVisuals;
import net.conczin.mca.client.resources.ColorPalette;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

public class SkinLayer<S extends HumanoidRenderState, M extends HumanoidModel<S>> extends VillagerLayer<S, M> {
    public SkinLayer(RenderLayerParent<S, M> renderer, M model) {
        super(renderer, model);
    }

    @Override
    public Identifier getSkin(S state) {
        return getSkin(VillagerVisuals.require(state));
    }

    public Identifier getSkin(VillagerVisuals visuals) {
        if (!MCA.isBlankString(visuals.skin())) {
            return cached(visuals.skin(), Identifier::parse);
        }

        int skin = (int) Math.min(4, Math.max(0, visuals.skinGene() * 5));
        return cached("skins/skin/" + visuals.genderDataName() + "/" + skin + ".png", MCA::locate);
    }

    @Override
    public int getColor(S state, float tickDelta) {
        return getColor(VillagerVisuals.require(state), tickDelta);
    }

    public int getColor(VillagerVisuals visuals, float tickDelta) {
        float albinism = visuals.albinism() ? 0.1f : 1.0f;

        return ColorPalette.SKIN.getColor(
                visuals.melaninGene() * albinism,
                visuals.hemoglobinGene() * albinism,
                visuals.infectionProgress()
        );
    }
}
