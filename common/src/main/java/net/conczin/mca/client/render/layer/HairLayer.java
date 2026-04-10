package net.conczin.mca.client.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.conczin.mca.client.render.MCAHumanoidRenderState;
import net.conczin.mca.client.resources.ColorPalette;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.minecraft.IdentifierException;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;

public class HairLayer<M extends HumanoidModel<MCAHumanoidRenderState>> extends VillagerLayer<M> {
    public HairLayer(RenderLayerParent<MCAHumanoidRenderState, M> renderer, M model) {
        super(renderer, model);
    }

    private static boolean isMissingIdentifier(String identifier) {
        return identifier == null
                || identifier.isBlank()
                || "mca:missing".equals(identifier)
                || "minecraft:missing".equals(identifier)
                || identifier.endsWith(":missing")
                || identifier.endsWith("/missing")
                || identifier.endsWith("/missing.png");
    }

    private static String defaultIdentifier(VillagerLike<?> villager) {
        String gender = villager.getGenetics().getGender().getDataName();
        return "mca:skins/hair/" + gender + "/0.png";
    }

    private static String normalizeIdentifier(VillagerLike<?> villager, String identifier) {
        if (isMissingIdentifier(identifier)) {
            return defaultIdentifier(villager);
        }

        // Legacy fallback from earlier patches accidentally stored this invalid path pattern.
        if (identifier.contains("/skins/hair/normal/")) {
            return identifier.replace("/skins/hair/normal/", "/skins/hair/");
        }
        if (identifier.contains("/hair/normal/")) {
            return identifier.replace("/hair/normal/", "/hair/");
        }
        return identifier;
    }

    @Override
    public void adjustVisibility(MCAHumanoidRenderState renderState) {
        model.setAllVisible(true);
        this.model.leftLeg.visible = false;
        this.model.rightLeg.visible = false;
    }

    @Override
    public Identifier getSkin(MCAHumanoidRenderState renderState) {
        VillagerLike<?> villager = (VillagerLike<?>) renderState.villager;
        if (villager == null)
            return null;

        String fallbackIdentifier = defaultIdentifier(villager);
        String identifier = normalizeIdentifier(villager, villager.getHair());

        if (identifier.startsWith("immersive_library:")) {
            try {
                return SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring(18)));
            } catch (NumberFormatException ignored) {
                identifier = fallbackIdentifier;
            }
        }

        final String finalIdentifier = identifier;
        final String finalFallbackIdentifier = fallbackIdentifier;
        return cached(finalIdentifier, id -> {
            Identifier resolved;
            try {
                resolved = Identifier.parse(id);
            } catch (IdentifierException ignored) {
                resolved = Identifier.parse(finalFallbackIdentifier);
            }

            if (canUse(resolved)) {
                return resolved;
            }

            Identifier fallback = Identifier.parse(finalFallbackIdentifier);
            return canUse(fallback) ? fallback : null;
        });
    }

    @Override
    protected Identifier getOverlay(MCAHumanoidRenderState renderState) {
        VillagerLike<?> villager = (VillagerLike<?>) renderState.villager;
        if (villager == null)
            return null;

        String hair = normalizeIdentifier(villager, villager.getHair());
        if (hair.startsWith("immersive_library:")) {
            return null;
        }

        String fallbackOverlay = defaultIdentifier(villager).replace(".png", "_overlay.png");
        final String overlayName = hair.replace(".png", "_overlay.png");
        final String finalFallbackOverlay = fallbackOverlay;
        return cached(overlayName, id -> {
            Identifier resolved;
            try {
                resolved = Identifier.parse(id);
            } catch (IdentifierException ignored) {
                resolved = Identifier.parse(finalFallbackOverlay);
            }

            if (canUse(resolved)) {
                return resolved;
            }

            Identifier fallback = Identifier.parse(finalFallbackOverlay);
            return canUse(fallback) ? fallback : null;
        });
    }

    private float[] getRainbow(net.minecraft.world.entity.Entity e, float tickDelta) {
        int n = Math.abs(e.tickCount) / 25 + e.getId();
        int o = DyeColor.values().length;
        int p = n % o;
        int q = (n + 1) % o;
        float r = ((float) (Math.abs(e.tickCount) % 25) + tickDelta) / 25.0f;
        int dp = DyeColor.byId(p).getTextureDiffuseColor();
        int dq = DyeColor.byId(q).getTextureDiffuseColor();
        float[] fs = new float[] { (dp >> 16 & 0xFF) / 255.0f, (dp >> 8 & 0xFF) / 255.0f, (dp & 0xFF) / 255.0f };
        float[] gs = new float[] { (dq >> 16 & 0xFF) / 255.0f, (dq >> 8 & 0xFF) / 255.0f, (dq & 0xFF) / 255.0f };
        return new float[] {
                fs[0] * (1.0f - r) + gs[0] * r,
                fs[1] * (1.0f - r) + gs[1] * r,
                fs[2] * (1.0f - r) + gs[2] * r
        };
    }

    @Override
    public int getColor(MCAHumanoidRenderState renderState, float tickDelta) {
        VillagerLike<?> villager = (VillagerLike<?>) renderState.villager;
        if (villager == null)
            return 0xFFFFFFFF;

        if (villager.getTraits().hasTrait(Traits.RAINBOW)) {
            float[] rC = getRainbow((net.minecraft.world.entity.Entity) villager, tickDelta);
            return 0xFF000000 | ((int) (rC[0] * 255) << 16) | ((int) (rC[1] * 255) << 8) | ((int) (rC[2] * 255));
        }

        int hairDye = villager.getHairDye();
        if ((hairDye & 0xFFFFFF) != 0) {
            return hairDye;
        }

        float albinism = villager.getTraits().hasTrait(Traits.ALBINISM) ? 0.1f : 1.0f;
        return ColorPalette.HAIR.getColor(
                villager.getGenetics().getGene(Genetics.EUMELANIN) * albinism,
                villager.getGenetics().getGene(Genetics.PHEOMELANIN) * albinism,
                0);
    }
}
