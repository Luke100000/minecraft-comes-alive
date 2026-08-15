package net.conczin.mca.client.render.layer;

import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.conczin.mca.client.resources.ColorPalette;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;

import static net.conczin.mca.client.model.CommonVillagerModel.getVillager;

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
    public void renderFinal(PoseStack transform, MultiBufferSource provider, int light, T villager, float tickDelta, boolean visible, boolean glowing) {
        int overlay = LivingEntityRenderer.getOverlayCoords(villager, 0);
        float[] color = getColor(villager, tickDelta);
        boolean renderedLayeredHair = false;

        for (LayeredHair.Category category : LayeredHair.Category.RENDER_ORDER) {
            String identifier = getVillager(villager).getLayeredHair(category);
            if (MCA.isBlankString(identifier)) {
                continue;
            }

            renderedLayeredHair = true;
            ResourceLocation texture = getTexture(identifier);
            if (canUse(texture)) {
                renderModel(transform, provider, light, model, color[0], color[1], color[2], texture, overlay, visible, glowing);
            }

            ResourceLocation overlayTexture = getOverlayTexture(identifier);
            if (canUse(overlayTexture)) {
                renderModel(transform, provider, light, model, 1, 1, 1, overlayTexture, overlay, visible, glowing);
            }
        }

        if (!renderedLayeredHair) {
            super.renderFinal(transform, provider, light, villager, tickDelta, visible, glowing);
        }
    }

    @Override
    public ResourceLocation getSkin(T villager) {
        return getTexture(getVillager(villager).getHair());
    }

    private ResourceLocation getTexture(String identifier) {
        if (MCA.isBlankString(identifier)) {
            return null;
        }
        if (identifier.startsWith("immersive_library:")) {
            return SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring("immersive_library:".length())));
        }
        return cached(identifier, ResourceLocation::new);
    }

    @Override
    protected ResourceLocation getOverlay(T villager) {
        return getOverlayTexture(getVillager(villager).getHair());
    }

    private ResourceLocation getOverlayTexture(String identifier) {
        if (identifier.startsWith("immersive_library:") || !identifier.endsWith(".png")) {
            return null;
        }
        return cached(identifier.replace(".png", "_overlay.png"), ResourceLocation::new);
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

        if (getVillager(villager).hasHairDye()) {
            return VillagerLike.unpackHairDyeRgb(getVillager(villager).getHairDye());
        }

        float albinism = getVillager(villager).getTraits().hasTrait(Traits.ALBINISM) ? 0.1f : 1.0f;

        return ColorPalette.HAIR.getColor(
                getVillager(villager).getGenetics().getGene(Genetics.EUMELANIN) * albinism,
                getVillager(villager).getGenetics().getGene(Genetics.PHEOMELANIN) * albinism,
                0
        );
    }

}
