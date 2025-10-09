package net.mca.client.render.layer;

import net.mca.client.gui.immersive_library.SkinCache;
import net.mca.client.resources.ColorPalette;
import net.mca.entity.ai.Genetics;
import net.mca.entity.ai.Traits;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;

import static net.mca.client.model.CommonVillagerModel.getVillager;

import com.mojang.blaze3d.vertex.PoseStack;

public class HairLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends VillagerLayer<T, M> {
    public HairLayer(RenderLayerParent<T, M> renderer, M model) {
        super(renderer, model);
    }

    @Override
    public void render(PoseStack transform, MultiBufferSource provider, int light, T villager, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        model.setAllVisible(true);
        this.model.leftLeg.visible = false;
        this.model.rightLeg.visible = false;

        super.render(transform, provider, light, villager, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
    }

    @Override
    public ResourceLocation getSkin(T villager) {
        String identifier = getVillager(villager).getHair();
        if (identifier.startsWith("immersive_library:")) {
            return SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring(18)));
        }
        return cached(identifier, ResourceLocation::new);
    }

    @Override
    protected ResourceLocation getOverlay(T villager) {
        return cached(getVillager(villager).getHair().replace(".png", "_overlay.png"), ResourceLocation::new);
    }

    private float[] getRainbow(LivingEntity entity, float tickDelta) {
        int n = Math.abs(entity.tickCount) / 25 + entity.getId();
        int o = DyeColor.values().length;
        int p = n % o;
        int q = (n + 1) % o;
        float r = ((float)(Math.abs(entity.tickCount) % 25) + tickDelta) / 25.0f;
        float[] fs = Sheep.getColorArray(DyeColor.byId(p));
        float[] gs = Sheep.getColorArray(DyeColor.byId(q));
        return new float[] {
                fs[0] * (1.0f - r) + gs[0] * r,
                fs[1] * (1.0f - r) + gs[1] * r,
                fs[2] * (1.0f - r) + gs[2] * r
        };
    }

    @Override
    public float[] getColor(T villager, float tickDelta) {
        if (getVillager(villager).getTraits().hasTrait(Traits.RAINBOW)) {
            return getRainbow(villager, tickDelta);
        }

        float[] hairDye = getVillager(villager).getHairDye();
        if (hairDye[0] > 0.0f) {
            return hairDye;
        }

        float albinism = getVillager(villager).getTraits().hasTrait(Traits.ALBINISM) ? 0.1f : 1.0f;

        return ColorPalette.HAIR.getColor(
                getVillager(villager).getGenetics().getGene(Genetics.EUMELANIN) * albinism,
                getVillager(villager).getGenetics().getGene(Genetics.PHEOMELANIN) * albinism,
                0
        );
    }
}
