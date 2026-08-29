package net.conczin.mca.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import net.conczin.mca.datafix.McaDataFixers;

/**
 * Bridges the legacy MCA no-aging trait into Minecraft's age-lock state.
 * The trait remains in MCA data; the added field is consumed during entity loading.
 */
public final class NoAgingAgeLockFix extends DataFix {
    private static final String NO_AGING_TRAIT_ID = "mca:no_aging";

    public NoAgingAgeLockFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return fixTypeEverywhereTyped(
                "MCA: migrate no-aging trait to vanilla age lock",
                getInputSchema().getType(McaDataFixers.MCA_DATA),
                typed -> typed.update(DSL.remainderFinder(), NoAgingAgeLockFix::rewrite)
        );
    }

    private static <T> Dynamic<T> rewrite(Dynamic<T> root) {
        if (root.get("Traits").get(NO_AGING_TRAIT_ID).result().isEmpty()) {
            return root;
        }
        return root.set(McaDataFixers.AGE_LOCKED_MIGRATION_KEY, root.createBoolean(true));
    }
}
