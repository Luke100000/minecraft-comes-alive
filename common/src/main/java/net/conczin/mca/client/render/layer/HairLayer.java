package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.MCA;
import net.conczin.mca.MCAClient;
import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.conczin.mca.client.model.VillagerOverlayModel;
import net.conczin.mca.client.resources.ColorPalette;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.resources.data.skin.LayeredHair;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.DyeColor;

public class HairLayer<T extends LivingEntity> extends VillagerLayer<T> {
    public HairLayer(RenderLayerParent<T, PlayerModel<T>> renderer, VillagerOverlayModel<T> model) {
        super(renderer, model);
    }

    @Override
    protected void configureModel(T villager) {
        model.applyMorphology(MCAClient.resolveVillager(villager));
    }

    @Override
    public void renderFinal(PoseStack transform, MultiBufferSource provider, int light, T villager, float tickDelta, boolean visible, boolean glowing) {
        int overlay = LivingEntityRenderer.getOverlayCoords(villager, 0);
        int color = getColor(villager, tickDelta);
        boolean renderedLayeredHair = false;

        for (LayeredHair.Category category : LayeredHair.Category.RENDER_ORDER) {
            String identifier = MCAClient.resolveVillager(villager).getLayeredHair(category);
            if (identifier.isBlank()) {
                continue;
            }

            renderedLayeredHair = true;

            ResourceLocation texture = getTexture(identifier);
            if (canUse(texture)) {
                renderModel(transform, provider, light, model, color, texture, overlay, visible, glowing);
            }

            ResourceLocation overlayTexture = getOverlayTexture(identifier);
            if (canUse(overlayTexture)) {
                renderModel(transform, provider, light, model, 0xFFFFFFFF, overlayTexture, overlay, visible, glowing);
            }
        }

        if (!renderedLayeredHair) {
            super.renderFinal(transform, provider, light, villager, tickDelta, visible, glowing);
        }
    }

    @Override
    public ResourceLocation getSkin(T villager) {
        return getTexture(MCAClient.resolveVillager(villager).getHair());
    }

    private ResourceLocation getTexture(String identifier) {
        if (MCA.isBlankString(identifier)) {
            return null;
        }
        if (identifier.startsWith("immersive_library:")) {
            return SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring("immersive_library:".length())));
        }
        return cached(identifier, ResourceLocation::parse);
    }

    @Override
    protected ResourceLocation getOverlay(T villager) {
        return getOverlayTexture(MCAClient.resolveVillager(villager).getHair());
    }

    private ResourceLocation getOverlayTexture(String identifier) {
        if (identifier.startsWith("immersive_library:") || !identifier.endsWith(".png")) {
            return null;
        }
        return cached(identifier.replace(".png", "_overlay.png"), ResourceLocation::parse);
    }

    private int getRainbow(LivingEntity entity, float tickDelta) {
        int n = Math.abs(entity.tickCount) / 25 + entity.getId();
        int o = DyeColor.values().length;
        int p = n % o;
        int q = (n + 1) % o;
        float r = ((float) (Math.abs(entity.tickCount) % 25) + tickDelta) / 25.0f;
        return FastColor.ARGB32.lerp(r, Sheep.getColor(DyeColor.byId(p)), Sheep.getColor(DyeColor.byId(q)));
    }

    @Override
    public int getColor(T villager, float tickDelta) {
        var villagerData = MCAClient.resolveVillager(villager);
        if (villagerData.getTraits().hasTrait(Traits.RAINBOW)) {
            return getRainbow(villager, tickDelta);
        }

        int hairDye = villagerData.getHairDye();
        if (hairDye != 0xFF000000) {
            return hairDye;
        }

        float albinism = villagerData.getTraits().hasTrait(Traits.ALBINISM) ? 0.1f : 1.0f;

        return ColorPalette.HAIR.getColor(
                villagerData.getGenetics().getGene(Genetics.EUMELANIN) * albinism,
                villagerData.getGenetics().getGene(Genetics.PHEOMELANIN) * albinism,
                0
        );
    }
}
