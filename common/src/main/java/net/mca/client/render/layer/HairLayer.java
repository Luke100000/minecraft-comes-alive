package net.mca.client.render.layer;

import net.mca.MCA;
import net.mca.client.gui.immersive_library.SkinCache;
import net.mca.client.resources.ColorPalette;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.Genetics;
import net.mca.entity.ai.Traits;
import net.mca.resources.data.skin.LayeredHair;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.SheepEntity;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;

import static net.mca.client.model.CommonVillagerModel.getVillager;

public class HairLayer<T extends LivingEntity, M extends BipedEntityModel<T>> extends VillagerLayer<T, M> {
    public HairLayer(FeatureRendererContext<T, M> renderer, M model) {
        super(renderer, model);
    }

    @Override
    public void render(MatrixStack transform, VertexConsumerProvider provider, int light, T villager, float limbAngle, float limbDistance, float tickDelta, float animationProgress, float headYaw, float headPitch) {
        model.setVisible(true);
        this.model.leftLeg.visible = false;
        this.model.rightLeg.visible = false;

        super.render(transform, provider, light, villager, limbAngle, limbDistance, tickDelta, animationProgress, headYaw, headPitch);
    }

    @Override
    public void renderFinal(MatrixStack transform, VertexConsumerProvider provider, int light, T villager, float tickDelta, boolean visible, boolean glowing) {
        int overlay = LivingEntityRenderer.getOverlay(villager, 0);
        float[] color = getColor(villager, tickDelta);
        boolean renderedLayeredHair = false;

        for (LayeredHair.Category category : LayeredHair.Category.RENDER_ORDER) {
            String identifier = getVillager(villager).getLayeredHair(category);
            if (MCA.isBlankString(identifier)) {
                continue;
            }

            renderedLayeredHair = true;
            Identifier texture = getTexture(identifier);
            if (canUse(texture)) {
                renderModel(transform, provider, light, model, color[0], color[1], color[2], texture, overlay, visible, glowing);
            }

            Identifier overlayTexture = getOverlayTexture(identifier);
            if (canUse(overlayTexture)) {
                renderModel(transform, provider, light, model, 1, 1, 1, overlayTexture, overlay, visible, glowing);
            }
        }

        if (!renderedLayeredHair) {
            super.renderFinal(transform, provider, light, villager, tickDelta, visible, glowing);
        }
    }

    @Override
    public Identifier getSkin(T villager) {
        return getTexture(getVillager(villager).getHair());
    }

    private Identifier getTexture(String identifier) {
        if (identifier.startsWith("immersive_library:")) {
            return SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring("immersive_library:".length())));
        }
        return cached(identifier, Identifier::new);
    }

    @Override
    protected Identifier getOverlay(T villager) {
        return getOverlayTexture(getVillager(villager).getHair());
    }

    private Identifier getOverlayTexture(String identifier) {
        if (identifier.startsWith("immersive_library:") || !identifier.endsWith(".png")) {
            return null;
        }
        return cached(identifier.replace(".png", "_overlay.png"), Identifier::new);
    }

    private float[] getRainbow(LivingEntity entity, float tickDelta) {
        int n = Math.abs(entity.age) / 25 + entity.getId();
        int o = DyeColor.values().length;
        int p = n % o;
        int q = (n + 1) % o;
        float r = ((float)(Math.abs(entity.age) % 25) + tickDelta) / 25.0f;
        float[] fs = SheepEntity.getRgbColor(DyeColor.byId(p));
        float[] gs = SheepEntity.getRgbColor(DyeColor.byId(q));
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
