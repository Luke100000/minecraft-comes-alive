package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.client.model.McaModelAnimationDriver;
import net.conczin.mca.client.resources.ColorPalette;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import static net.conczin.mca.client.model.CommonVillagerModel.getVillager;

public class SkinLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends VillagerLayer<T, M> {
    public SkinLayer(RenderLayerParent<T, M> renderer, M model) {
        super(renderer, model);
    }

    @Override
    protected void prepareParentModel(PoseStack transform, int light) {
        McaModelAnimationDriver.animate(getParentModel().head, transform, light, OverlayTexture.NO_OVERLAY);
    }

    @Override
    public ResourceLocation getSkin(T villager) {
        if (!MCA.isBlankString(getVillager(villager).getSkin())) {
            return cached(getVillager(villager).getSkin(), ResourceLocation::parse);
        }

        Genetics genetics = getVillager(villager).getGenetics();
        int skin = (int) Math.min(4, Math.max(0, genetics.getGene(Genetics.SKIN) * 5));
        return cached("skins/skin/" + genetics.getGender().getDataName() + "/" + skin + ".png", MCA::locate);
    }

    @Override
    public int getColor(T villager, float tickDelta) {
        int skinDye = getVillager(villager).getSkinDye();
        if (skinDye != 0xFF000000) {
            return skinDye;
        }

        float albinism = getVillager(villager).getTraits().hasTrait(Traits.ALBINISM) ? 0.1f : 1.0f;
        return ColorPalette.SKIN.getColor(
                getVillager(villager).getGenetics().getGene(Genetics.MELANIN) * albinism,
                getVillager(villager).getGenetics().getGene(Genetics.HEMOGLOBIN) * albinism,
                getVillager(villager).getInfectionProgress()
        );
    }
}
