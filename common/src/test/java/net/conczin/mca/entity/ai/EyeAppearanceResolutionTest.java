package net.conczin.mca.entity.ai;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EyeAppearanceResolutionTest {
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
