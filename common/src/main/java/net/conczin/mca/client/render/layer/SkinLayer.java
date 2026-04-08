package net.conczin.mca.client.render.layer;

import net.conczin.mca.MCA;
import net.conczin.mca.client.resources.ColorPalette;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.client.render.VillagerStateHolder;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.resources.Identifier;

import static net.conczin.mca.client.model.CommonVillagerModel.getVillager;

public class SkinLayer<S extends HumanoidRenderState & VillagerStateHolder, M extends HumanoidModel<S>> extends VillagerLayer<S, M> {
    public SkinLayer(RenderLayerParent<S, M> renderer, M model) {
        super(renderer, model);
    }

    @Override
    public Identifier getSkin(S state) {
        Genetics genetics = getVillager(state).getGenetics();
        int skin = (int) Math.min(4, Math.max(0, genetics.getGene(Genetics.SKIN) * 5));
        return cached("skins/skin/" + genetics.getGender().getDataName() + "/" + skin + ".png", MCA::locate);
    }

    @Override
    public int getColor(S state, float tickDelta) {
        float albinism = getVillager(state).getTraits().hasTrait(Traits.ALBINISM) ? 0.1f : 1.0f;

        return ColorPalette.SKIN.getColor(
                getVillager(state).getGenetics().getGene(Genetics.MELANIN) * albinism,
                getVillager(state).getGenetics().getGene(Genetics.HEMOGLOBIN) * albinism,
                getVillager(state).getInfectionProgress()
        );
    }
}
