package net.mca.client.render.layer;

import net.mca.MCA;
import net.mca.client.resources.ColorPalette;
import net.mca.entity.ai.Genetics;
import net.mca.entity.ai.Traits;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;

import static net.mca.client.model.CommonVillagerModel.getVillager;

public class SkinLayer<T extends LivingEntity, M extends BipedEntityModel<T>> extends VillagerLayer<T, M> {
    public SkinLayer(FeatureRendererContext<T, M> renderer, M model) {
        super(renderer, model);
    }

    @Override
    public Identifier getSkin(T villager) {
        if (!MCA.isBlankString(getVillager(villager).getSkin())) {
            return cached(getVillager(villager).getSkin(), Identifier::new);
        }

        Genetics genetics = getVillager(villager).getGenetics();
        int skin = (int) Math.min(4, Math.max(0, genetics.getGene(Genetics.SKIN) * 5));
        return cached("skins/skin/" + genetics.getGender().getDataName() + "/" + skin + ".png", MCA::locate);
    }

    @Override
    public float[] getColor(T villager, float tickDelta) {
        int skinDye = getVillager(villager).getSkinDye();
        if (skinDye != 0xFF000000) {
            return argbToRgb(skinDye);
        }

        float albinism = getVillager(villager).getTraits().hasTrait(Traits.ALBINISM) ? 0.1f : 1.0f;

        return ColorPalette.SKIN.getColor(
                getVillager(villager).getGenetics().getGene(Genetics.MELANIN) * albinism,
                getVillager(villager).getGenetics().getGene(Genetics.HEMOGLOBIN) * albinism,
                getVillager(villager).getInfectionProgress()
        );
    }

    private float[] argbToRgb(int color) {
        return new float[] {
                ((color >> 16) & 0xFF) / 255.0F,
                ((color >> 8) & 0xFF) / 255.0F,
                (color & 0xFF) / 255.0F
        };
    }
}
