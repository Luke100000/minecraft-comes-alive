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
    @Test void heterochromiaCanBeLighterOrDarkerAndSubtleOrPronounced() {
        var light = genes(0, 0, 1, 1);
        var dark = genes(2, 2, 1, 1);
        int lighter = 0, darker = 0, subtle = 0, pronounced = 0;
        RandomSource random = RandomSource.create(263);
        for (int i = 0; i < 1024; i++) {
            var local = EyeGenetics.localExpression(light, random);
            float offset = local.pigmentationOffset();
            assertTrue(Math.abs(offset) >= 0.05F && Math.abs(offset) <= 0.4F);
            assertTrue(Math.abs(local.appearanceOffset()) <= 0.25F);
            if (offset < 0) lighter++; else darker++;
            if (Math.abs(offset) < 0.15F) subtle++;
            if (Math.abs(offset) > 0.3F) pronounced++;
        }
        assertTrue(lighter > 0 && darker > 0, "Local pigment differences must allow both directions");
        assertTrue(subtle > 0 && pronounced > 0, "Local expression must include weak and strong differences");
        assertEquals(EyeGenetics.localExpression(light, RandomSource.create(11)),
                EyeGenetics.localExpression(dark, RandomSource.create(11)),
                "The same local draw must not force opposite directions based on inherited pigment");
    }
    @Test void productionFoundersKeepDiverseAlleleWeightsAndNaturalShadeVariety() {
        int[] pigmentCounts = new int[3], modifierCounts = new int[3];
        var colours = new java.util.HashSet<Integer>();
        RandomSource random = RandomSource.create(557);
        for (int i = 0; i < 100_000; i++) {
            var genotype = EyeGenetics.founder(EyeGenetics.PopulationPreset.DIVERSE, random);
            pigmentCounts[genotype.pigmentation().first()]++;
            pigmentCounts[genotype.pigmentation().second()]++;
            modifierCounts[genotype.modifier().first()]++;
            modifierCounts[genotype.modifier().second()]++;
            colours.add(EyeGenetics.resolveNatural(genotype, null, false));
        }
        double[] pigmentWeights = {0.55, 0.225, 0.225}, modifierWeights = {0.45, 0.18, 0.37};
        for (int i = 0; i < 3; i++) {
            assertEquals(pigmentWeights[i], pigmentCounts[i] / 200_000.0, 0.01);
            assertEquals(modifierWeights[i], modifierCounts[i] / 200_000.0, 0.01);
        }
        assertTrue(colours.size() >= 20, "Founder DNA must resolve to more than a small set of repeated base shades");
        System.out.printf("Diverse production sampler: 100000 founders, %d distinct ordinary RGB shades%n", colours.size());
    }
}
