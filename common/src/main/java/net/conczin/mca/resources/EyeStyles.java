package net.conczin.mca.resources;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Stable eye-style identifiers used for fallbacks and 1.21.1 save migration.
 */
public final class EyeStyles {
    public static final String DEFAULT_VARIANT = "normal";
    public static final ResourceLocation DEFAULT = MCA.locate("skins/face/normal/0.png");

    private static final int LEGACY_FACE_COUNT = 22;
    private static final int LEGACY_SECOND_STYLE_INDEX = 11;

    private EyeStyles() {
    }

    /**
     * The pre-7.7.18 1.21.1 eye set contained two shapes, each authored in
     * eleven colour variants. The new eye system tints those two shapes at
     * runtime, so only legacy indices 0 and 11 remain as shape identifiers.
     */
    public static ResourceLocation fromLegacyFace(float faceGene, Gender gender) {
        int legacyIndex = Mth.clamp((int)(faceGene * LEGACY_FACE_COUNT), 0, LEGACY_FACE_COUNT - 1);
        int styleIndex = legacyIndex < LEGACY_SECOND_STYLE_INDEX ? 0 : LEGACY_SECOND_STYLE_INDEX;
        String genderPath = gender.binary().getDataName();
        return MCA.locate("skins/face/normal/" + genderPath + "/" + styleIndex + ".png");
    }
}
