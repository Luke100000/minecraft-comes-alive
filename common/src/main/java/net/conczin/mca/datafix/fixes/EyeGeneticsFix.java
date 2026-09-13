package net.conczin.mca.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import net.conczin.mca.datafix.McaDataFixers;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.EyeStyles;

/**
 * Splits the legacy FACE gene into the two eye traits that replaced it:
 * a categorical eye-style path and an independently inherited eye-colour gene.
 */
public final class EyeGeneticsFix extends DataFix {
    private static final String FACE_GENE = "GeneFace";
    private static final String EYE_COLOR_GENE = "GeneEyeColor";
    private static final String EYE_TEXTURE = "EyeTexture";
    private static final String GENDER = "Gender";

    public EyeGeneticsFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return fixTypeEverywhereTyped(
                "MCA: migrate inherited eye genetics",
                getInputSchema().getType(McaDataFixers.MCA_DATA),
                typed -> typed.update(DSL.remainderFinder(), EyeGeneticsFix::rewrite)
        );
    }

    private static <T> Dynamic<T> rewrite(Dynamic<T> root) {
        Number face = root.get(FACE_GENE).asNumber().result().orElse(null);
        if (face == null) {
            return root.remove(FACE_GENE);
        }

        Dynamic<T> updated = root;
        if (root.get(EYE_COLOR_GENE).result().isEmpty()) {
            updated = updated.set(EYE_COLOR_GENE, root.createFloat(face.floatValue()));
        }

        if (root.get(EYE_TEXTURE).result().isEmpty()) {
            Gender gender = Gender.byId(root.get(GENDER).asInt(Gender.UNASSIGNED.getId()));
            updated = updated.set(
                    EYE_TEXTURE,
                    root.createString(EyeStyles.fromLegacyFace(face.floatValue(), gender).toString())
            );
        }

        return updated.remove(FACE_GENE);
    }
}
