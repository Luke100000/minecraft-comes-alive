package net.conczin.mca.resources;

import net.conczin.mca.resources.data.skin.LayeredHair;
import net.conczin.mca.server.world.data.CustomClothingManager;
import net.minecraft.resources.ResourceLocation;

public final class SkinVisualIds {
    private static final String LEGACY_HAIR_NAMESPACE = "mca";
    private static final String LEGACY_HAIR_PATH_PREFIX = "skins/hair/";

    private SkinVisualIds() {
    }

    public static boolean isBodySkin(String identifier) {
        BodySkinList list = BodySkinList.getInstance();
        return list != null && list.get(identifier) != null;
    }

    public static boolean isClothing(String identifier) {
        ClothingList list = ClothingList.getInstance();
        return isImmersiveLibraryId(identifier)
                || list != null && isKnownClothing(identifier, list);
    }

    public static boolean isHairStyle(String identifier) {
        HairStyleList list = HairStyleList.getInstance();
        return isImmersiveLibraryId(identifier)
                || list != null && list.get(identifier) != null
                || CustomClothingManager.getHair().getEntries().containsKey(identifier);
    }

    public static boolean isHairLayer(String identifier, LayeredHair.Category category) {
        LayeredHairList list = LayeredHairList.getInstance();
        return isImmersiveLibraryId(identifier)
                || list != null && list.containsIdentifier(identifier)
                || category == LayeredHair.Category.BASE && isLegacyHairTexture(identifier)
                || CustomClothingManager.getHair().getEntries().containsKey(identifier);
    }

    public static boolean isLegacyHairTexture(String identifier) {
        ResourceLocation parsed = ResourceLocation.tryParse(identifier);
        return parsed != null
                && LEGACY_HAIR_NAMESPACE.equals(parsed.getNamespace())
                && parsed.getPath().startsWith(LEGACY_HAIR_PATH_PREFIX)
                && parsed.getPath().endsWith(".png");
    }

    private static boolean isImmersiveLibraryId(String identifier) {
        return identifier != null && identifier.startsWith("immersive_library");
    }

    private static boolean isKnownClothing(String identifier, ClothingList list) {
        return list.clothing.containsKey(identifier)
                || CustomClothingManager.getClothing().getEntries().containsKey(identifier);
    }
}
