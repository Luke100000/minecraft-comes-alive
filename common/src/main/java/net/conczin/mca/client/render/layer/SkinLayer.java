package net.conczin.mca.client.render.layer;

import net.conczin.mca.MCA;
import net.conczin.mca.client.render.MCAHumanoidRenderState;
import net.conczin.mca.client.resources.ColorPalette;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.Identifier;

public class SkinLayer<M extends HumanoidModel<MCAHumanoidRenderState>> extends VillagerLayer<M> {
    public SkinLayer(RenderLayerParent<MCAHumanoidRenderState, M> renderer, M model) {
        super(renderer, model);
    }

    @Override
    public Identifier getSkin(MCAHumanoidRenderState renderState) {
        VillagerLike<?> villager = (VillagerLike<?>) renderState.villager;
        if (villager == null)
            return null;
        Genetics genetics = villager.getGenetics();
        int skin = (int) Math.min(4, Math.max(0, genetics.getGene(Genetics.SKIN) * 5));
        return cached("skins/skin/" + genetics.getGender().getDataName() + "/" + skin + ".png", MCA::locate);
    }

    @Override
    public int getColor(MCAHumanoidRenderState renderState, float tickDelta) {
        VillagerLike<?> villager = (VillagerLike<?>) renderState.villager;
        if (villager == null)
            return 0xFFFFFFFF;
        float albinism = villager.getTraits().hasTrait(Traits.ALBINISM) ? 0.1f : 1.0f;

        int c = ColorPalette.SKIN.getColor(
                villager.getGenetics().getGene(Genetics.MELANIN) * albinism,
                villager.getGenetics().getGene(Genetics.HEMOGLOBIN) * albinism,
                villager.getInfectionProgress());
        return c;
    }
}
