package net.mca.datafix;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFixer;
import com.mojang.datafixers.DataFixerBuilder;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.serialization.Dynamic;
import net.mca.datafix.fixes.PersonalityAndTraitsFix;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtOps;

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
    public static NbtCompound update(NbtCompound input) {
        if (getVersion(input) > CURRENT_VERSION) {
            return input.copy();
        }

        NbtCompound updated = updatePayload(input);
        if (!updated.contains(LEGACY_MCA_DATA_KEY, NbtElement.COMPOUND_TYPE)) {
            return updated;
        }

        NbtCompound nested = updated.getCompound(LEGACY_MCA_DATA_KEY);
        NbtCompound migratedNested = updatePayload(nested);
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
    public static void stampCurrentVersion(NbtCompound output) {
        if (getVersion(output) > CURRENT_VERSION) {
            return;
        }

        stampPayload(output);
        if (output.contains(LEGACY_MCA_DATA_KEY, NbtElement.COMPOUND_TYPE)) {
            NbtCompound nested = output.getCompound(LEGACY_MCA_DATA_KEY);
            stampPayload(nested);
            output.put(LEGACY_MCA_DATA_KEY, nested);
        }
    }

    private static NbtCompound updatePayload(NbtCompound input) {
        int sourceVersion = getVersion(input);
        if (sourceVersion >= CURRENT_VERSION) {
            return input;
        }

        NbtCompound copy = input.copy();
        Dynamic<NbtElement> result = FIXER.update(
                MCA_DATA,
                new Dynamic<>(NbtOps.INSTANCE, copy),
                sourceVersion,
                CURRENT_VERSION
        );
        NbtCompound migrated = result.getValue() instanceof NbtCompound compound ? compound : copy;
        migrated.putInt(DATA_VERSION_KEY, CURRENT_VERSION);
        return migrated;
    }

    private static int getVersion(NbtCompound input) {
        return input.contains(DATA_VERSION_KEY, NbtElement.NUMBER_TYPE)
                ? input.getInt(DATA_VERSION_KEY)
                : 0;
    }

    private static void stampPayload(NbtCompound output) {
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
