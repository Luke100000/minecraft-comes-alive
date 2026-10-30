package net.conczin.mca.datafix;

import net.conczin.mca.entity.ai.EyeGenetics;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class FamilyEyeGeneticsMigrationTest {
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000000029");
    @BeforeAll static void bootstrapMinecraft() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    @Test void originalSourceCapturedBeforeFaceRemovalForFlatAndNestedVersions() {
        for (int version = 0; version <= 3; version++) for (boolean nested : new boolean[]{false, true}) {
            CompoundTag source = new CompoundTag();
            source.putInt(McaDataFixers.DATA_VERSION_KEY, version);
            source.putFloat(version < 3 ? "GeneFace" : "GeneEyeColor", 0.5F);
            source.putFloat("GeneSize", 0.12F);
            source.putInt("EyeColor", 0xFFFFFFFF);
            CompoundTag input = new CompoundTag();
            if (nested) input.put("MCAData", source); else input = source.copy();
            CompoundTag before = input.copy();
            CompoundTag migrated = McaDataFixers.update(input);
            assertEquals(0.5F, migrated.getCompound(EyeGenetics.KEY).getCompound("LegacyNatural").getFloat("Scalar"));
            assertEquals(version < 3 ? "GeneFace" : "GeneEyeColor",
                    migrated.getCompound(EyeGenetics.KEY).getCompound("LegacyNatural").getString("Source"));
            assertEquals(0.12F, migrated.getFloat("GeneSize"));
            assertEquals(before, input);
            assertEquals(migrated, McaDataFixers.update(migrated));
        }
    }
    @Test void missingScalarIsNotTrackedDefaultAndDyesAreNotGenetics() {
        CompoundTag raw = new CompoundTag(); raw.putInt("EyeColor", 0xFF112233);
        raw.putString("GeneFace", "0.5");
        assertTrue(EyeGenetics.captureLegacyNatural(raw).isEmpty());
        var normalized = EyeGenetics.initializeSavedPayload(McaDataFixers.update(raw).getCompound(EyeGenetics.KEY), ID);
        assertFalse(normalized.contains("LegacyNatural"));
        assertEquals(normalized, EyeGenetics.initializeSavedPayload(normalized, UUID.randomUUID()));
    }
    @Test void partialPayloadKeepsValidAlleles() {
        CompoundTag partial = new CompoundTag(); partial.putInt("Format", 1);
        partial.putInt("PigmentA", 2); partial.putInt("ModifierB", 0); partial.putString("PigmentB", "1");
        CompoundTag result = EyeGenetics.initializeSavedPayload(partial, ID);
        assertEquals(2, EyeGenetics.decodeGenotype(result).pigmentation().first());
        assertEquals(0, EyeGenetics.decodeGenotype(result).modifier().second());
        assertEquals(result, EyeGenetics.initializeSavedPayload(result, ID));
    }
    @Test void partialPayloadWithoutNaturalColourSamplesMissingAllelesFromDiverse() {
        CompoundTag partial = new CompoundTag(); partial.putInt("Format", 1);
        partial.putInt("PigmentA", 0); partial.putInt("ModifierA", 2);
        partial.putString("ModifierB", "corrupt");
        CompoundTag before = partial.copy();
        int[] pigmentCounts = new int[3], modifierCounts = new int[3];
        int population = 4096;
        for (int i = 0; i < population; i++) {
            UUID identity = new UUID(0, i);
            CompoundTag result = EyeGenetics.initializeSavedPayload(partial, identity);
            var genotype = EyeGenetics.decodeGenotype(result);
            assertEquals(0, genotype.pigmentation().first());
            assertEquals(2, genotype.modifier().first());
            pigmentCounts[genotype.pigmentation().second()]++;
            modifierCounts[genotype.modifier().second()]++;
            assertFalse(result.contains("LegacyNatural"));
            assertEquals(result, EyeGenetics.initializeSavedPayload(partial, identity));
            assertEquals(result, EyeGenetics.initializeSavedPayload(result, new UUID(1, i)));
        }
        assertEquals(before, partial);
        // Missing slots follow the founder weights even alongside known alleles.
        double[] pigmentWeights = {0.55, 0.225, 0.225}, modifierWeights = {0.45, 0.18, 0.37};
        for (int allele = 0; allele < 3; allele++) {
            assertEquals(pigmentWeights[allele], pigmentCounts[allele] / (double) population, 0.04,
                    "Missing pigmentation allele must use Diverse, not an invented legacy colour");
            assertEquals(modifierWeights[allele], modifierCounts[allele] / (double) population, 0.04,
                    "Missing modifier allele must use Diverse");
        }
    }
    @Test void partialPayloadWithNaturalColourStillUsesConstrainedInference() {
        CompoundTag partial = new CompoundTag(); partial.putInt("Format", 1);
        partial.putInt("PigmentA", 2); partial.putInt("ModifierB", 0);
        CompoundTag legacy = new CompoundTag(); legacy.putInt("Argb", 0xFF7B4E35);
        partial.put("LegacyNatural", legacy);
        CompoundTag result = EyeGenetics.initializeSavedPayload(partial, ID);
        var genotype = EyeGenetics.decodeGenotype(result);
        assertEquals(2, genotype.pigmentation().first());
        assertEquals(0, genotype.modifier().second());
        assertEquals(0xFF7B4E35, EyeGenetics.resolveNatural(genotype, null, false));
        assertEquals(legacy, result.getCompound("LegacyNatural"));
    }
    @Test void unsupportedPayloadIsRetainedWithoutInitialization() {
        CompoundTag future = new CompoundTag(); future.putInt("Format", 99); future.putString("FutureDna", "keep");
        CompoundTag raw = new CompoundTag(); raw.put(EyeGenetics.KEY, future);
        assertEquals(future, McaDataFixers.update(raw).getCompound(EyeGenetics.KEY));
        assertEquals(future, EyeGenetics.initializeSavedPayload(future, ID));
    }
    @Test void nestedSourceWinsConflictingFlatFields() {
        CompoundTag raw = new CompoundTag(); raw.putFloat("GeneEyeColor", 0.1F);
        CompoundTag nested = new CompoundTag(); nested.putFloat("GeneFace", 0.8F); nested.putInt("EyeColor", -1);
        raw.put("MCAData", nested);
        assertEquals(0.8F, McaDataFixers.update(raw).getCompound(EyeGenetics.KEY).getCompound("LegacyNatural").getFloat("Scalar"));
    }

    @Test void malformedOrEmptyEyePayloadStillRecoversLegacyNatural() {
        for (boolean wrongType : new boolean[]{false, true}) {
            CompoundTag source = new CompoundTag();
            source.putInt(McaDataFixers.DATA_VERSION_KEY, 2);
            source.putFloat("GeneFace", 0.6F);
            source.putInt("EyeColor", -1);
            if (wrongType) source.putString(EyeGenetics.KEY, "corrupt");
            else source.put(EyeGenetics.KEY, new CompoundTag());

            CompoundTag migrated = McaDataFixers.update(source);
            CompoundTag eyes = migrated.getCompound(EyeGenetics.KEY);
            assertEquals(EyeGenetics.FORMAT, eyes.getInt("Format"));
            assertEquals(0.6F, eyes.getCompound("LegacyNatural").getFloat("Scalar"));
            assertEquals("GeneFace", eyes.getCompound("LegacyNatural").getString("Source"));
            assertFalse(migrated.contains("GeneFace"));
            assertEquals(migrated, McaDataFixers.update(migrated));
        }
    }
}
