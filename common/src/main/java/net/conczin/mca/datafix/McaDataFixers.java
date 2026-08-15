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
     * Upgrades MCA entity data to the current version.
     *
     * <p>Both the current flat representation and the former nested
     * {@code MCAData} representation are handled. Current payloads are returned
     * unchanged, while payloads from a future MCA version are copied without
     * modification.</p>
     */
    public static CompoundTag update(CompoundTag input) {
        if (getVersion(input) > CURRENT_VERSION) {
            return input.copy();
        }

        CompoundTag updated = updatePayload(input);
        if (!updated.contains(LEGACY_MCA_DATA_KEY, Tag.TAG_COMPOUND)) {
            return updated;
        }

        CompoundTag nested = updated.getCompound(LEGACY_MCA_DATA_KEY);
        CompoundTag migratedNested = updatePayload(nested);
        if (migratedNested == nested) {
            return updated;
        }

        if (updated == input) {
            updated = input.copy();
        }
        updated.put(LEGACY_MCA_DATA_KEY, migratedNested);
        return updated;
    }

    /**
     * Marks newly written data as current without overwriting a future version.
     */
    public static void stampCurrentVersion(CompoundTag output) {
        if (getVersion(output) > CURRENT_VERSION) {
            return;
        }

        stampPayload(output);
        if (output.contains(LEGACY_MCA_DATA_KEY, Tag.TAG_COMPOUND)) {
            CompoundTag nested = output.getCompound(LEGACY_MCA_DATA_KEY);
            stampPayload(nested);
            output.put(LEGACY_MCA_DATA_KEY, nested);
        }
    }

    private static CompoundTag updatePayload(CompoundTag input) {
        int sourceVersion = getVersion(input);
        if (sourceVersion >= CURRENT_VERSION) {
            return input;
        }

        CompoundTag copy = input.copy();
        Dynamic<Tag> result = FIXER.update(
                MCA_DATA,
                new Dynamic<>(NbtOps.INSTANCE, copy),
                sourceVersion,
                CURRENT_VERSION
        );
        CompoundTag migrated = result.getValue() instanceof CompoundTag compound ? compound : copy;
        migrated.putInt(DATA_VERSION_KEY, CURRENT_VERSION);
        return migrated;
    }

    private static int getVersion(CompoundTag input) {
        return input.contains(DATA_VERSION_KEY, Tag.TAG_ANY_NUMERIC)
                ? input.getInt(DATA_VERSION_KEY)
                : 0;
    }

    private static void stampPayload(CompoundTag output) {
        if (getVersion(output) <= CURRENT_VERSION) {
            output.putInt(DATA_VERSION_KEY, CURRENT_VERSION);
        }
    }

    private static DataFixer createFixer() {
        DataFixerBuilder builder = new DataFixerBuilder(CURRENT_VERSION);
        builder.addSchema(0, McaDataSchema::new);
        Schema versionOne = builder.addSchema(1, Schema::new);
        builder.addFixer(new PersonalityAndTraitsFix(versionOne));
        return builder.buildUnoptimized();
    }
}
