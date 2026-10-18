package net.conczin.mca.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import net.conczin.mca.datafix.McaDataFixers;
import net.conczin.mca.entity.ai.EyeGenetics;

/** Promotes raw pre-chain evidence, without generating DNA or using an entity identity. */
public final class FamilyEyeGeneticsFix extends DataFix {
    public static final String CAPTURE_KEY = "McaLegacyEyeCapture";
    public FamilyEyeGeneticsFix(Schema schema) { super(schema, false); }
    @Override protected TypeRewriteRule makeRule() {
        return fixTypeEverywhereTyped("MCA: preserve natural eyes for family genetics",
                getInputSchema().getType(McaDataFixers.MCA_DATA), typed -> typed.update(DSL.remainderFinder(), root -> {
                    var captured = root.get(CAPTURE_KEY).result();
                    return (root.get(EyeGenetics.KEY).result().isEmpty() && captured.isPresent()
                            ? root.set(EyeGenetics.KEY, captured.get()) : root).remove(CAPTURE_KEY);
                }));
    }
}
