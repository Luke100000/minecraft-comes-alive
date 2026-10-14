package net.conczin.mca.entity.ai;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EyeAppearanceResolutionTest {
    @Test void modifierProducesBlendedNaturalShadesWithinTheSamePigmentation() {
        // Hand-checked swatches from the approved before/after preview (P=0.5).
        assertEquals(0xFF789959, EyeGenetics.resolveNatural(EyeGeneticsTest.genes(1, 1, 0, 1), null, false));
        assertEquals(0xFF869B50, EyeGenetics.resolveNatural(EyeGeneticsTest.genes(1, 1, 1, 1), null, false));
        assertEquals(0xFF8E9D4C, EyeGenetics.resolveNatural(EyeGeneticsTest.genes(1, 1, 1, 2), null, false));
        assertEquals(EyeGenetics.resolveNatural(EyeGeneticsTest.genes(0, 2, 0, 1), null, false),
                EyeGenetics.resolveNatural(EyeGeneticsTest.genes(2, 0, 1, 0), null, false));
    }
    @Test void localExpressionHasNoAbruptColourJumpsAtFormerFamilyBoundaries() {
        var genotypes = new EyeGenetics.Genotype[]{EyeGeneticsTest.genes(0, 1, 1, 1),
                EyeGeneticsTest.genes(1, 1, 1, 1), EyeGeneticsTest.genes(1, 2, 2, 2)};
        float[] offsets = {-0.075F, 0.15F, 0.025F}; // Cross former P=0.30, 0.65 and 0.90 cutoffs.
        for (int i = 0; i < genotypes.length; i++) {
            int before = EyeGenetics.resolveNatural(genotypes[i], new EyeGenetics.LocalExpression(offsets[i] - 0.001F, 0), true);
            int after = EyeGenetics.resolveNatural(genotypes[i], new EyeGenetics.LocalExpression(offsets[i] + 0.001F, 0), true);
            for (int shift : new int[]{0, 8, 16}) {
                assertTrue(Math.abs((before >>> shift & 255) - (after >>> shift & 255)) <= 2,
                        "A tiny local pigment change must not jump between palette anchors");
            }
        }
    }
    @Test void legacyGradientMatchesOldResolver() {
        for (float scalar : new float[]{0, 0.1F, 0.35F, 0.57F, 0.70F, 0.9F, 1}) for (boolean left : new boolean[]{false, true}) {
            CompoundTag legacy = new CompoundTag(); legacy.putFloat("Scalar", scalar);
            float value = Mth.frac(scalar + (left ? 0.43F : 0));
            int old = value < 0.35F ? FastColor.ARGB32.lerp(value / 0.35F, 0xFF3A98E8, 0xFF4CB346)
                    : value < 0.70F ? FastColor.ARGB32.lerp((value - 0.35F) / 0.35F, 0xFF4CB346, 0xFFC29B35)
                    : FastColor.ARGB32.lerp((value - 0.70F) / 0.30F, 0xFFC29B35, 0xFF7C4825);
            assertEquals(old, EyeGenetics.resolveLegacyNatural(legacy, left, true, false));
            assertEquals(0xFFE8A0A0, EyeGenetics.resolveLegacyNatural(legacy, left, true, true));
        }
    }
    @Test void historicalPinkKeepsNaturalColourButNotPinkAlleles() {
        CompoundTag raw = new CompoundTag(); raw.putFloat("GeneFace", 1.5F / 22); raw.putInt("Gender", 0);
        CompoundTag legacy = EyeGenetics.captureLegacyNatural(raw);
        assertEquals(0xFFEC68A2, EyeGenetics.resolveLegacyNatural(legacy, false, false, false));
        var inferred = EyeGenetics.inferLegacy(legacy, new java.util.UUID(0, 29));
        assertNotEquals(0xFFEC68A2, EyeGenetics.resolveNatural(inferred, null, false));
        assertEquals(inferred, EyeGenetics.inferLegacy(legacy, new java.util.UUID(0, 29)));
    }
    @Test void localExpressionDoesNotBecomeSecondGenotype() {
        var genotype = EyeGeneticsTest.genes(0, 0, 1, 1);
        var local = new EyeGenetics.LocalExpression(0.4F, 0.2F);
        assertNotEquals(EyeGenetics.resolveNatural(genotype, local, true), EyeGenetics.resolveNatural(genotype, local, false));
        assertEquals(genotype, EyeGenetics.decodeGenotype(EyeGenetics.encode(genotype, local, new CompoundTag())));
    }
}
