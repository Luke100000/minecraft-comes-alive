package net.conczin.mca.entity.ai;

import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EyeGeneticsTest {
    static EyeGenetics.Genotype genes(int a, int b, int c, int d) {
        return new EyeGenetics.Genotype(new EyeGenetics.Locus(a, b), new EyeGenetics.Locus(c, d));
    }
    @Test void childAllelesAreParentalContributions() {
        var mother = genes(0, 0, 2, 2);
        var father = genes(2, 2, 0, 0);
        assertEquals(genes(0, 2, 2, 0), EyeGenetics.inherit(mother, father, RandomSource.create(11)));
    }
    @Test void approvedFamilyFixturesKeepHiddenInfluences() {
        var carrier = genes(0, 2, 1, 1);
        var light = genes(0, 0, 1, 1);
        assertNotEquals(EyeGenetics.resolveNatural(carrier, null, false), EyeGenetics.resolveNatural(light, null, false));
        RandomSource random = RandomSource.create(29);
        int lightChildren = 0;
        for (int i = 0; i < 1000; i++) {
            var child = EyeGenetics.inherit(carrier, carrier, random);
            if (child.pigmentation().equals(new EyeGenetics.Locus(0, 0))) lightChildren++;
        }
        assertTrue(lightChildren > 200 && lightChildren < 300, "carriers should expose hidden light alleles");
    }
    @Test void darkestPhenotypeDoesNotWrapToBlue() {
        assertEquals(0xFF7B4E35, EyeGenetics.resolveNatural(genes(2, 2, 0, 0), null, false));
    }
    @Test void productionFoundersFavorVarietyOverBrown() {
        int[] counts = new int[6];
        RandomSource random = RandomSource.create(557);
        for (int i = 0; i < 100_000; i++) {
            var genotype = EyeGenetics.founder(EyeGenetics.PopulationPreset.DIVERSE, random);
            float pigment = (0.75F * Math.max(genotype.pigmentation().first(), genotype.pigmentation().second())
                    + 0.25F * Math.min(genotype.pigmentation().first(), genotype.pigmentation().second())) / 2;
            float shade = (genotype.modifier().first() + genotype.modifier().second()) / 4.0F;
            int family = pigment < 0.30F ? (shade < 0.125F ? 4 : 1)
                    : pigment < 0.65F ? (shade < 0.375F ? 3 : 2) : pigment < 0.90F && shade >= 0.90F ? 5 : 0;
            counts[family]++;
        }
        // Brown, blue, hazel, green, grey, amber. These are founder weights, not offspring quotas.
        double[] expected = {35.163, 24.124, 18.946, 10.866, 6.126, 4.774};
        for (int i = 0; i < counts.length; i++) assertEquals(expected[i], counts[i] / 1000.0, 1.0);
    }
}
