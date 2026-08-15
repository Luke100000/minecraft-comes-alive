package net.conczin.mca.resources;

import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.*;
import net.mca.resources.data.skin.*;
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

    public static boolean isBodySkin(String identifier, Gender gender) {
        BodySkinList list = BodySkinList.getInstance();
        BodySkin skin = list == null ? null : list.get(identifier);
        return skin != null && SkinSelection.matchesGender(skin, gender);
    }

    public static boolean isClothing(String identifier) {
        ClothingList list = ClothingList.getInstance();
        return isImmersiveLibraryId(identifier)
                || getKnownClothing(identifier, list) != null;
    }

    public static boolean isClothing(String identifier, Gender gender) {
        ClothingList list = ClothingList.getInstance();
        Clothing clothing = getKnownClothing(identifier, list);
        return isImmersiveLibraryId(identifier)
                || clothing != null && SkinSelection.matchesGender(clothing, gender);
    }

    public static boolean isHairStyle(String identifier) {
        HairStyleList list = HairStyleList.getInstance();
        return isImmersiveLibraryId(identifier)
                || list != null && list.get(identifier) != null
                || CustomClothingManager.getHair().getEntries().containsKey(identifier);
    }

    public static boolean isHairStyle(String identifier, Gender gender) {
        HairStyleList list = HairStyleList.getInstance();
        HairStyle style = list == null ? null : list.get(identifier);
        Hair customHair = CustomClothingManager.getHair().getEntries().get(identifier);
        return isImmersiveLibraryId(identifier)
                || style != null && SkinSelection.matchesGender(style, gender)
                || customHair != null && SkinSelection.matchesGender(customHair, gender);
    }

    public static boolean isHairLayer(String identifier, LayeredHair.Category category) {
        LayeredHairList list = LayeredHairList.getInstance();
        return isImmersiveLibraryId(identifier)
                || list != null && isKnownLayeredHair(identifier, category, list)
                || category == LayeredHair.Category.BASE && isLegacyHairTexture(identifier)
                || CustomClothingManager.getHair().getEntries().containsKey(identifier);
    }

    public static boolean isHairLayer(String identifier, LayeredHair.Category category, Gender gender) {
        LayeredHairList list = LayeredHairList.getInstance();
        Hair customHair = CustomClothingManager.getHair().getEntries().get(identifier);
        return isImmersiveLibraryId(identifier)
                || list != null && isKnownLayeredHair(identifier, category, gender, list)
                || category == LayeredHair.Category.BASE && isLegacyHairTexture(identifier)
                || category == LayeredHair.Category.BASE && customHair != null && SkinSelection.matchesGender(customHair, gender);
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

    private static Clothing getKnownClothing(String identifier, ClothingList list) {
        Clothing clothing = list == null ? null : list.clothing.get(identifier);
        return clothing == null ? CustomClothingManager.getClothing().getEntries().get(identifier) : clothing;
    }

    private static boolean isKnownLayeredHair(String identifier, LayeredHair.Category category, LayeredHairList list) {
        return list.get(identifier, category) != null;
    }

    private static boolean isKnownLayeredHair(String identifier, LayeredHair.Category category, Gender gender, LayeredHairList list) {
        LayeredHair entry = list.get(identifier, category);
        return entry != null && SkinSelection.matchesGender(entry, gender);
    }
}
