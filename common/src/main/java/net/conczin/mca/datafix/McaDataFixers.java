package net.conczin.mca.datafix;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.DataFixerBuilder;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import net.conczin.mca.datafix.fixes.PersonalityAndTraitsFix;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.NotNull;

/**
 * MCA-owned DataFixerUpper pipeline for persisted MCA entity data.
 *
 * <p>Minecraft's own {@code DataVersion} cannot distinguish two MCA releases
 * targeting the same Minecraft version, so MCA data carries an independent
 * version and is upgraded before tracked values are decoded.</p>
 */
public final class McaDataFixers {
    public static final String DATA_VERSION_KEY = "MCADataVersion";
    public static final int CURRENT_VERSION = 1;
    public static final DSL.TypeReference MCA_DATA = () -> "mca:data";

    private static final String LEGACY_MCA_DATA_KEY = "MCAData";
    private static final DataFixer FIXER = createFixer();

    private McaDataFixers() {
    }

    /**
     * Returns a migrated copy of an MCA entity-data compound.
     *
     * <p>Both the current flat representation and the former nested
     * {@code MCAData} representation are handled. Payloads from a future MCA
     * data version are preserved rather than downgraded.</p>
     */
    public static @NotNull CompoundTag update(@NotNull CompoundTag input) {
        CompoundTag updated = updatePayload(input);
        if (updated.contains(LEGACY_MCA_DATA_KEY, Tag.TAG_COMPOUND)) {
            updated.put(LEGACY_MCA_DATA_KEY, updatePayload(updated.getCompound(LEGACY_MCA_DATA_KEY)));
        }
        return updated;
    }

    /**
     * Marks newly written data as current without overwriting a future version.
     */
    public static void stampCurrentVersion(@NotNull CompoundTag output) {
        stampPayload(output);
        if (output.contains(LEGACY_MCA_DATA_KEY, Tag.TAG_COMPOUND)) {
            stampPayload(output.getCompound(LEGACY_MCA_DATA_KEY));
        }
    }

    private static @NotNull CompoundTag updatePayload(@NotNull CompoundTag input) {
        CompoundTag copy = input.copy();
        int sourceVersion = getVersion(copy);
        if (sourceVersion >= CURRENT_VERSION) {
            return copy;
        }

        Dynamic<Tag> result = FIXER.update(
                MCA_DATA,
                new Dynamic<>(NbtOps.INSTANCE, copy),
                sourceVersion,
                CURRENT_VERSION
        );
        Tag value = result.getValue();
        if (value instanceof CompoundTag migrated) {
            copy = migrated;
        }
        copy.putInt(DATA_VERSION_KEY, CURRENT_VERSION);
        return copy;
    }

    private static int getVersion(CompoundTag input) {
        return input.contains(DATA_VERSION_KEY, Tag.TAG_ANY_NUMERIC)
                ? input.getInt(DATA_VERSION_KEY)
                : 0;
    }

    private static void stampPayload(CompoundTag output) {
        int version = getVersion(output);
        if (version <= CURRENT_VERSION) {
            output.putInt(DATA_VERSION_KEY, CURRENT_VERSION);
        }
    }

    private static DataFixer createFixer() {
        DataFixerBuilder builder = new DataFixerBuilder(CURRENT_VERSION);
        builder.addSchema(0, McaDataSchema::new);
        Schema versionOne = builder.addSchema(1, Schema::new);
        builder.addFixer(new PersonalityAndTraitsFix(versionOne));
        return builder.build().fixer();
    }
}
