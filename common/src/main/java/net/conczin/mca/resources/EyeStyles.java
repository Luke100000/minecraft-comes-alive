package net.conczin.mca.resources;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Stable eye-style identifiers used for fallbacks and 1.21.1 save migration.
 */
public final class EyeStyles {
    public static final ResourceLocation DEFAULT = MCA.locate("skins/face/normal/0.png");

    private static final int LEGACY_TWELVE_FACE_COUNT = 12;
    private static final int LEGACY_BLINK_INDEX = 11;
    private static final int LEGACY_TWENTY_TWO_FACE_COUNT = 22;
    private static final int LEGACY_SECOND_STYLE_INDEX = 11;

    private EyeStyles() {
    }

    /**
     * The released 1.21.1 normal-eye list contained numbered textures 0..10
     * followed by blink.png. Preserve the numbered texture selected by the old
     * FACE gene; the blink entry is animation state, not a persistent style.
     */
    public static ResourceLocation fromTwelveEntryFace(float faceGene) {
        int legacyIndex = Mth.clamp((int)(faceGene * LEGACY_TWELVE_FACE_COUNT), 0, LEGACY_TWELVE_FACE_COUNT - 1);
        return legacyIndex == LEGACY_BLINK_INDEX
                ? DEFAULT
                : MCA.locate("skins/face/normal/" + legacyIndex + ".png");
    }

    /**
     * Before 7.7.18, the FACE gene selected one of twenty-two gendered eye
     * textures. Indices 0..10 used the first shape and 11..21 the second;
     * colour was baked into the individual texture.
     */
    public static ResourceLocation fromTwentyTwoEntryFace(float faceGene, Gender gender) {
        int legacyIndex = Mth.clamp((int)(faceGene * LEGACY_TWENTY_TWO_FACE_COUNT), 0, LEGACY_TWENTY_TWO_FACE_COUNT - 1);
        int styleIndex = legacyIndex < LEGACY_SECOND_STYLE_INDEX ? 0 : LEGACY_SECOND_STYLE_INDEX;
        return MCA.locate("skins/face/normal/" + gender.binary().getDataName() + "/" + styleIndex + ".png");
    }

}
