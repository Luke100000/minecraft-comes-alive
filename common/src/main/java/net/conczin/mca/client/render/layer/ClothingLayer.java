package net.conczin.mca.client.render.layer;

import net.conczin.mca.client.gui.immersive_library.SkinCache;
import net.conczin.mca.client.model.VillagerEntityModelMCA;
import net.conczin.mca.client.render.MCAHumanoidRenderState;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.IdentifierException;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.Identifier;

public class ClothingLayer<M extends HumanoidModel<MCAHumanoidRenderState>> extends VillagerLayer<M> {
    private final String variant;

    public ClothingLayer(RenderLayerParent<MCAHumanoidRenderState, M> renderer, M model, String variant) {
        super(renderer, model);
        this.variant = variant;
    }

    @Override
    public void adjustVisibility(MCAHumanoidRenderState renderState) {
        model.setAllVisible(true);

        if (model instanceof VillagerEntityModelMCA villagerModel) {
            villagerModel.breasts.visible = false;
            villagerModel.breastsWear.visible = false;
        }
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
        return "mca:skins/clothing/normal/" + gender + "/none/0.png";
    }

    private static String normalizeIdentifier(VillagerLike<?> villager, String identifier) {
        if (isMissingIdentifier(identifier)) {
            return defaultIdentifier(villager);
        }
        return identifier;
    }

    @Override
    public Identifier getSkin(MCAHumanoidRenderState renderState) {
        VillagerLike<?> villager = (VillagerLike<?>) renderState.villager;
        if (villager == null)
            return null;

        String v = villager.isBurned() ? "burnt" : variant;
        String identifier = normalizeIdentifier(villager, villager.getClothes());
        String fallbackIdentifier = defaultIdentifier(villager);

        if (identifier.startsWith("immersive_library:")) {
            try {
                return SkinCache.getTextureIdentifier(Integer.parseInt(identifier.substring(18)));
            } catch (NumberFormatException ignored) {
                identifier = fallbackIdentifier;
            }
        }

        final String finalIdentifier = identifier;
        final String finalFallbackIdentifier = fallbackIdentifier;
        final String finalVariant = v;
        return cached(finalIdentifier + v, clothes -> {
            Identifier id;
            try {
                id = Identifier.parse(finalIdentifier);
            } catch (IdentifierException ignored) {
                id = Identifier.parse(finalFallbackIdentifier);
            }

            Identifier idNew = Identifier.parse(id.getNamespace() + ":" + id.getPath().replace("normal", finalVariant));
            if (canUse(idNew)) {
                return idNew;
            }
            if (canUse(id)) {
                return id;
            }

            Identifier fallbackVariant = Identifier.parse(finalFallbackIdentifier.replace("/normal/", "/" + finalVariant + "/"));
            if (canUse(fallbackVariant)) {
                return fallbackVariant;
            }

            Identifier fallback = Identifier.parse(finalFallbackIdentifier);
            return canUse(fallback) ? fallback : null;
        });
    }
}
