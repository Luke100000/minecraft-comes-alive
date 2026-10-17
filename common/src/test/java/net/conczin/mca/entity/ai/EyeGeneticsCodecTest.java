package net.conczin.mca.entity.ai;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EyeGeneticsCodecTest {
    @Test void genotypeRoundTripsAtEveryBound() {
        for (int a = 0; a <= 2; a++) for (int b = 0; b <= 2; b++) {
            var genotype = EyeGeneticsTest.genes(a, b, b, a);
            var local = new EyeGenetics.LocalExpression(-0.4F, 0.25F);
            var payload = EyeGenetics.encode(genotype, local, new CompoundTag());
            assertEquals(genotype, EyeGenetics.decodeGenotype(payload));
            assertEquals(local, EyeGenetics.decodeLocalExpression(payload));
        }
    }
    @Test void rejectsWrongTypesAndOutOfRangeAlleles() {
        assertThrows(IllegalArgumentException.class, () -> new EyeGenetics.Locus(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new EyeGenetics.Locus(0, 3));
        assertThrows(IllegalArgumentException.class, () -> new EyeGenetics.LocalExpression(Float.NaN, 0));
        CompoundTag payload = EyeGenetics.encode(EyeGeneticsTest.genes(0, 2, 1, 1), null, new CompoundTag());
        payload.putString("PigmentA", "0");
        assertThrows(IllegalArgumentException.class, () -> EyeGenetics.decodeGenotype(payload));
    }
    @Test void unsupportedVersionIsNotMissing() {
        CompoundTag future = new CompoundTag();
        future.putInt("Format", 99);
        assertThrows(IllegalArgumentException.class, () -> EyeGenetics.decodeGenotype(future));
    }
    @Test void codecDoesNotAliasMutableTags() {
        CompoundTag legacy = new CompoundTag();
        legacy.putFloat("Scalar", 0.6F);
        var payload = EyeGenetics.encode(EyeGeneticsTest.genes(0, 2, 1, 1), null, legacy);
        legacy.putFloat("Scalar", 0.1F);
        assertEquals(0.6F, payload.getCompound("LegacyNatural").getFloat("Scalar"));
        assertNull(EyeGenetics.decodeLocalExpression(payload));
    }
}
