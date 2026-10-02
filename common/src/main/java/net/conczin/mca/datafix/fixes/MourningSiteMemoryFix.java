package net.conczin.mca.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import net.conczin.mca.datafix.McaDataFixers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;

import java.util.Optional;

/**
 * Migrates the legacy block-position mourning site to the dimension-aware
 * GlobalPos shape. Old mourning sites predate dimension tracking, so they are
 * treated as overworld positions.
 */
public final class MourningSiteMemoryFix extends DataFix {
    private static final String MOURNING_SITE = "mca:mourning_site";

    public MourningSiteMemoryFix(Schema outputSchema) {
        super(outputSchema, false);
    }

    @Override
    protected TypeRewriteRule makeRule() {
        return fixTypeEverywhereTyped(
                "MCA: migrate mourning site to global position",
                getInputSchema().getType(McaDataFixers.MCA_DATA),
                typed -> typed.update(DSL.remainderFinder(), MourningSiteMemoryFix::rewrite)
        );
    }

    private static <T> Dynamic<T> rewrite(Dynamic<T> root) {
        return root.update("Brain", brain -> brain.update(
                "memories",
                MourningSiteMemoryFix::migrateMemories
        ));
    }

    private static <T> Dynamic<T> migrateMemories(Dynamic<T> memories) {
        Optional<Dynamic<T>> memory = memories.get(MOURNING_SITE).result();
        if (memory.isEmpty()) {
            return memories;
        }

        Optional<Dynamic<T>> value = memory.orElseThrow().get("value").result();
        if (value.isEmpty()) {
            return memories.remove(MOURNING_SITE);
        }

        Dynamic<T> position = value.orElseThrow();
        if (GlobalPos.CODEC.parse(position.getOps(), position.getValue()).result().isPresent()) {
            return memories;
        }
        if (BlockPos.CODEC.parse(position.getOps(), position.getValue()).result().isEmpty()) {
            return memories.remove(MOURNING_SITE);
        }

        Dynamic<T> migratedPosition = position.emptyMap()
                .set("dimension", position.createString("minecraft:overworld"))
                .set("pos", position);
        return memories.set(MOURNING_SITE, memory.orElseThrow().set("value", migratedPosition));
    }
}
