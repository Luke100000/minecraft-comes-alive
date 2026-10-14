package net.conczin.mca.entity.ai;

import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Experimental model comparison, not the production genetics implementation. */
class EyeGeneticsModelComparisonTest {
    private static final ProbeModel TWO_PAIR = new ProbeModel(0.25);
    private static final long[] SEEDS = {11, 29, 47, 83, 131, 197, 263, 347, 431, 557};

    @Test
    void randomMatingSelectsTwoDistinctParents() {
        RandomSource random = RandomSource.create(11L);
        for (int i = 0; i < 2_048; i++) {
            ParentIndices parents = parentIndices(32, random);
            assertNotEquals(parents.mother(), parents.father(), "population probe allowed self-parenting");
        }
    }

    @Test
    void scalarControlUsesTheCurrentGradientAndWrappingEndpoint() {
        ProbeModel scalar = new ProbeModel(Kind.SCALAR_MUTATION, 0.25);
        assertEquals(0xFF3A98E8, scalar.express(new ProbeGenotype(List.of(0.0))).argb());
        assertEquals(0xFF4CB346, scalar.express(new ProbeGenotype(List.of((double) 0.35F))).argb());
        assertEquals(0xFFC29B35, scalar.express(new ProbeGenotype(List.of((double) 0.70F))).argb());
        assertEquals(0xFF3A98E8, scalar.express(new ProbeGenotype(List.of(1.0))).argb());
    }

    @Test
    void candidateDarkestEndpointIsBrownRatherThanWrappedBlue() {
        ProbePhenotype darkest = TWO_PAIR.express(new ProbeGenotype(List.of(1.0, 1.0, 0.0, 0.0)));
        assertEquals(Family.BROWN, darkest.family());
        assertEquals(0xFF7B4E35, darkest.argb());
    }

    @Test
    void founderAndGenerationReportsAreReproducible() {
        Snapshot first = simulate(TWO_PAIR, 128, 10, 47L).getLast();
        assertEquals(128L, first.counts().stream().mapToLong(Long::longValue).sum());
        assertEquals(first, simulate(TWO_PAIR, 128, 10, 47L).getLast());
    }

    @Test
    @Tag("experimental")
    void populationComparisonReportsFoundersAndLargeAndFinitePopulations() {
        System.out.println("POP,model,seed,population,generation,brown,blue,hazel,green,grey,amber,pigmentMean,pigmentVariance,rgbVariance,p0,p1,p2,m0,m1,m2");
        for (Kind kind : Kind.values()) {
            ProbeModel model = new ProbeModel(kind, 0.25);
            for (long seed : SEEDS) {
                Snapshot founders = founderSnapshot(model, 100_000, seed);
                assertEquals(100_000L, founders.counts().stream().mapToLong(Long::longValue).sum());
                printSnapshot(kind.name(), seed, 100_000, -1, founders);
                for (int size : new int[]{10_000, 32, 128}) {
                    List<Snapshot> generations = simulate(model, size, 100, seed);
                    assertEquals(4, generations.size());
                    for (int i = 0; i < generations.size(); i++) {
                        int generation = new int[]{0, 10, 50, 100}[i];
                        printSnapshot(kind.name(), seed, size, generation, generations.get(i));
                    }
                }
            }
        }
    }

    @Test
    @Tag("experimental")
    void dominanceParameterSweepReportsRatherThanAssumingTheCoefficient() {
        for (double contribution : new double[]{0.0, 0.25, 0.5}) {
            Snapshot sample = founderSnapshot(new ProbeModel(Kind.TWO_PAIR, contribution), 100_000, 47L);
            assertEquals(100_000L, sample.counts().stream().mapToLong(Long::longValue).sum());
            printSnapshot("SWEEP_" + contribution, 47L, 100_000, -1, sample);
        }
    }

    @Test
    void localExpressionChangesAppearanceWithoutChangingInheritedAlleles() {
        RandomSource random = RandomSource.create(263L);
        int changedFamilies = 0;
        for (int i = 0; i < 10_000; i++) {
            ProbeGenotype genotype = TWO_PAIR.founder(random);
            List<Double> savedAlleles = genotype.alleles();
            ProbePhenotype base = TWO_PAIR.express(genotype);
            LocalExpression local = localExpression(base, random);
            assertTrue(Math.abs(local.pigmentationOffset()) <= 0.4);
            assertTrue(Math.abs(local.modifierOffset()) <= 0.25);
            ProbePhenotype affected = expressLocal(base, local);
            assertTrue(affected.pigmentation() >= 0.0 && affected.pigmentation() <= 1.0);
            assertTrue(affected.modifier() >= 0.0 && affected.modifier() <= 1.0);
            assertEquals(savedAlleles, genotype.alleles());
            if (affected.family() != base.family()) {
                changedFamilies++;
            }
        }
        assertTrue(changedFamilies > 0, "local expression never produces a different colour family");
        System.out.printf(Locale.ROOT, "HETERO,familyChanges,%d,of,10000%n", changedFamilies);
    }

    @Test
    void darkerCarriersCanHaveLightChildrenWithoutAveragingPhenotypes() {
        ProbeGenotype carrier = new ProbeGenotype(List.of(0.0, 1.0, 0.5, 0.5));
        ProbeGenotype dark = new ProbeGenotype(List.of(1.0, 1.0, 0.5, 0.5));
        assertEquals(Family.BROWN, TWO_PAIR.express(carrier).family());
        assertEquals(Family.BROWN, TWO_PAIR.express(dark).family());
        RandomSource random = RandomSource.create(83L);
        int lightChildren = 0;
        for (int i = 0; i < 10_000; i++) {
            if (TWO_PAIR.express(inherit(carrier, carrier, random)).family() == Family.BLUE) {
                lightChildren++;
            }
            assertEquals(Family.BROWN, TWO_PAIR.express(inherit(dark, dark, random)).family());
        }
        assertTrue(lightChildren >= 2_000 && lightChildren <= 3_000,
                "hidden light alleles did not reappear at the expected segregation scale");
    }

    @Test
    void sameVisibleBlueParentsCanCarryDifferentSecondaryFamilyHistory() {
        ProbeGenotype coolCarrier = new ProbeGenotype(List.of(0.0, 0.0, 0.0, 1.0));
        ProbeGenotype warmCarrier = new ProbeGenotype(List.of(0.0, 0.0, 0.5, 1.0));
        ProbeGenotype greyPartner = new ProbeGenotype(List.of(0.0, 0.0, 0.0, 0.0));
        assertEquals(Family.BLUE, TWO_PAIR.express(coolCarrier).family());
        assertEquals(TWO_PAIR.express(coolCarrier).argb(), TWO_PAIR.express(warmCarrier).argb());
        RandomSource random = RandomSource.create(131L);
        int coolGreyChildren = 0;
        int warmGreyChildren = 0;
        for (int i = 0; i < 10_000; i++) {
            if (TWO_PAIR.express(inherit(coolCarrier, greyPartner, random)).family() == Family.GREY) {
                coolGreyChildren++;
            }
            if (TWO_PAIR.express(inherit(warmCarrier, greyPartner, random)).family() == Family.GREY) {
                warmGreyChildren++;
            }
        }
        assertTrue(coolGreyChildren > 4_000 && coolGreyChildren < 6_000);
        assertEquals(0, warmGreyChildren);
    }

    @Test
    void grandparentAllelesCanResurfaceAfterADarkerGeneration() {
        ProbeGenotype lightGrandparent = new ProbeGenotype(List.of(0.0, 0.0, 0.5, 0.5));
        ProbeGenotype darkGrandparent = new ProbeGenotype(List.of(1.0, 1.0, 0.5, 0.5));
        RandomSource random = RandomSource.create(197L);
        ProbeGenotype mother = inherit(lightGrandparent, darkGrandparent, random);
        ProbeGenotype father = inherit(lightGrandparent, darkGrandparent, random);
        assertEquals(List.of(0.0, 1.0, 0.5, 0.5), mother.alleles());
        assertEquals(Family.BROWN, TWO_PAIR.express(mother).family());
        boolean foundLightGrandchild = false;
        for (int i = 0; i < 128; i++) {
            ProbeGenotype child = inherit(mother, father, random);
            if (TWO_PAIR.express(child).family() == Family.BLUE) {
                assertEquals(List.of(0.0, 0.0), child.alleles().subList(0, 2));
                foundLightGrandchild = true;
            }
        }
        assertTrue(foundLightGrandchild);
    }

    @Test
    void secondaryLocusHasAnObservableBenefitBeyondCosmeticRerolling() {
        ProbeGenotype coolCarrier = new ProbeGenotype(List.of(0.0, 0.0, 0.0, 1.0));
        ProbeGenotype warmCarrier = new ProbeGenotype(List.of(0.0, 0.0, 0.5, 1.0));
        ProbeGenotype greyPartner = new ProbeGenotype(List.of(0.0, 0.0, 0.0, 0.0));
        ProbeModel onePair = new ProbeModel(Kind.ONE_PAIR, 0.25);
        ProbeGenotype oneCoolCarrier = new ProbeGenotype(List.of(0.0, 0.0), 0.5);
        ProbeGenotype oneWarmCarrier = new ProbeGenotype(List.of(0.0, 0.0), 0.75);
        ProbeGenotype oneGreyPartner = new ProbeGenotype(List.of(0.0, 0.0), 0.0);
        assertEquals(Family.BLUE, onePair.express(oneCoolCarrier).family());
        assertEquals(TWO_PAIR.express(coolCarrier).argb(), onePair.express(oneCoolCarrier).argb());
        assertEquals(TWO_PAIR.express(warmCarrier).argb(), onePair.express(oneWarmCarrier).argb());
        assertEquals(TWO_PAIR.express(greyPartner).argb(), onePair.express(oneGreyPartner).argb());
        int twoCool = greyChildren(TWO_PAIR, coolCarrier, greyPartner, 347L);
        int twoWarm = greyChildren(TWO_PAIR, warmCarrier, greyPartner, 347L);
        int oneCool = greyChildren(onePair, oneCoolCarrier, oneGreyPartner, 347L);
        int oneWarm = greyChildren(onePair, oneWarmCarrier, oneGreyPartner, 347L);
        assertTrue(twoCool > 4_000 && twoCool < 6_000);
        assertEquals(0, twoWarm);
        assertEquals(oneCool, oneWarm);
        assertTrue(oneCool > 1_500 && oneCool < 2_500);
        System.out.printf(Locale.ROOT, "FAMILY,twoCoolGrey,%d,twoWarmGrey,%d,oneCoolGrey,%d,oneWarmGrey,%d,outOf,10000%n",
                twoCool, twoWarm, oneCool, oneWarm);
    }

    @Test
    void inheritanceCopiesOneAlleleFromEachParentAtEachLocus() {
        ProbeGenotype mother = new ProbeGenotype(List.of(0.0, 0.25, 0.5, 0.75));
        ProbeGenotype father = new ProbeGenotype(List.of(0.75, 1.0, 0.0, 0.25));
        RandomSource random = RandomSource.create(47L);
        Set<Double> maternalPigmentation = new HashSet<>();
        Set<Double> paternalPigmentation = new HashSet<>();
        for (int i = 0; i < 128; i++) {
            ProbeGenotype child = inherit(mother, father, random);
            for (int locus = 0; locus < 2; locus++) {
                int slot = locus * 2;
                assertTrue(mother.alleles().subList(slot, slot + 2).contains(child.alleles().get(slot)),
                        "maternal allele was averaged instead of copied");
                assertTrue(father.alleles().subList(slot, slot + 2).contains(child.alleles().get(slot + 1)),
                        "paternal allele was averaged instead of copied");
            }
            maternalPigmentation.add(child.alleles().getFirst());
            paternalPigmentation.add(child.alleles().get(1));
        }
        assertEquals(Set.of(0.0, 0.25), maternalPigmentation);
        assertEquals(Set.of(0.75, 1.0), paternalPigmentation);
    }

    private static ProbeGenotype inherit(ProbeGenotype mother, ProbeGenotype father, RandomSource random) {
        return new ProbeGenotype(List.of(
                mother.alleles().get(random.nextBoolean() ? 0 : 1),
                father.alleles().get(random.nextBoolean() ? 0 : 1),
                mother.alleles().get(random.nextBoolean() ? 2 : 3),
                father.alleles().get(random.nextBoolean() ? 2 : 3)));
    }

    private record ProbeGenotype(List<Double> alleles, double cosmeticModifier) {
        private ProbeGenotype(List<Double> alleles) {
            this(alleles, 0.0);
        }

        private ProbeGenotype {
            alleles = List.copyOf(alleles);
        }
    }

    private record ProbeModel(Kind kind, double recessiveContribution) {
        private static final List<Double> VALUES = List.of(0.0, 0.5, 1.0);
        private static final List<List<ProbeGenotype>> COLOUR_BUCKETS = colourBuckets();

        private ProbeModel(double recessiveContribution) {
            this(Kind.TWO_PAIR, recessiveContribution);
        }

        ProbeGenotype founder(RandomSource random) {
            if (kind == Kind.SCALAR_MUTATION || kind == Kind.SCALAR_NO_MUTATION) {
                return new ProbeGenotype(List.of((double) random.nextFloat()));
            }
            if (kind == Kind.TWO_PAIR_COLOUR_FIRST) {
                double draw = random.nextDouble();
                double cumulative = 0.0;
                double[] targets = {0.45, 0.20, 0.15, 0.10, 0.05, 0.05};
                for (int family = 0; family < targets.length; family++) {
                    cumulative += targets[family];
                    if (draw < cumulative || family == targets.length - 1) {
                        List<ProbeGenotype> choices = COLOUR_BUCKETS.get(family);
                        return choices.get(random.nextInt(choices.size()));
                    }
                }
                throw new IllegalStateException("No founder family selected");
            }
            List<Double> pigmentation = List.of(majorAllele(random), majorAllele(random));
            if (kind == Kind.ONE_PAIR) {
                return new ProbeGenotype(pigmentation, (secondaryAllele(random) + secondaryAllele(random)) / 2.0);
            }
            return new ProbeGenotype(List.of(pigmentation.get(0), pigmentation.get(1),
                    secondaryAllele(random), secondaryAllele(random)));
        }

        ProbeGenotype inherit(ProbeGenotype mother, ProbeGenotype father, RandomSource random) {
            if (kind == Kind.SCALAR_MUTATION || kind == Kind.SCALAR_NO_MUTATION) {
                float interpolation = random.nextFloat();
                float mutation = (random.nextFloat() - 0.5F) * 0.2F;
                float scalar = mother.alleles().getFirst().floatValue() * interpolation
                        + father.alleles().getFirst().floatValue() * (1.0F - interpolation)
                        + (kind == Kind.SCALAR_MUTATION ? mutation : 0.0F);
                return new ProbeGenotype(List.of((double) Mth.clamp(scalar, 0.0F, 1.0F)));
            }
            if (kind == Kind.ONE_PAIR) {
                return new ProbeGenotype(List.of(mother.alleles().get(random.nextBoolean() ? 0 : 1),
                        father.alleles().get(random.nextBoolean() ? 0 : 1)),
                        (secondaryAllele(random) + secondaryAllele(random)) / 2.0);
            }
            return EyeGeneticsModelComparisonTest.inherit(mother, father, random);
        }

        ProbePhenotype express(ProbeGenotype genotype) {
            if (kind == Kind.SCALAR_MUTATION || kind == Kind.SCALAR_NO_MUTATION) {
                float scalar = genotype.alleles().getFirst().floatValue();
                float value = Mth.frac(scalar);
                int argb = value < 0.35F
                        ? FastColor.ARGB32.lerp(value / 0.35F, 0xFF3A98E8, 0xFF4CB346)
                        : value < 0.70F
                        ? FastColor.ARGB32.lerp((value - 0.35F) / 0.35F, 0xFF4CB346, 0xFFC29B35)
                        : FastColor.ARGB32.lerp((value - 0.70F) / 0.30F, 0xFFC29B35, 0xFF7C4825);
                // Nearest old scalar anchor, not measured human colour categories.
                Family family = value < 0.175F ? Family.BLUE : value < 0.525F ? Family.GREEN
                        : value < 0.85F ? Family.HAZEL : Family.BROWN;
                return new ProbePhenotype(family, argb, scalar, 0.0);
            }
            double first = genotype.alleles().get(0);
            double second = genotype.alleles().get(1);
            double pigmentation = Math.max(first, second) * (1.0 - recessiveContribution)
                    + Math.min(first, second) * recessiveContribution;
            double modifier = kind == Kind.ONE_PAIR ? genotype.cosmeticModifier()
                    : (genotype.alleles().get(2) + genotype.alleles().get(3)) / 2.0;
            return phenotype(pigmentation, modifier);
        }

        private static Double majorAllele(RandomSource random) {
            return sampleAllele(random, 0.5, Math.sqrt(0.5) - 0.5);
        }

        private static Double secondaryAllele(RandomSource random) {
            return sampleAllele(random, Math.sqrt(0.2), Math.sqrt(0.2) / 2.0);
        }

        private static Double sampleAllele(RandomSource random, double first, double second) {
            double draw = random.nextDouble();
            return VALUES.get(draw < first ? 0 : draw < first + second ? 1 : 2);
        }

        private static List<List<ProbeGenotype>> colourBuckets() {
            List<List<ProbeGenotype>> buckets = new ArrayList<>();
            for (Family ignored : Family.values()) {
                buckets.add(new ArrayList<>());
            }
            ProbeModel model = new ProbeModel(Kind.TWO_PAIR, 0.25);
            for (double a : VALUES) {
                for (double b : VALUES) {
                    for (double c : VALUES) {
                        for (double d : VALUES) {
                            ProbeGenotype genotype = new ProbeGenotype(List.of(a, b, c, d));
                            buckets.get(model.express(genotype).family().ordinal()).add(genotype);
                        }
                    }
                }
            }
            return buckets.stream().map(List::copyOf).toList();
        }
    }

    private static ProbePhenotype phenotype(double pigmentation, double modifier) {
        Family family;
        if (pigmentation < 0.3) {
            family = modifier < 0.125 ? Family.GREY : Family.BLUE;
        } else if (pigmentation < 0.65) {
            family = modifier < 0.375 ? Family.GREEN : Family.HAZEL;
        } else {
            family = pigmentation < 0.9 && modifier >= 0.9 ? Family.AMBER : Family.BROWN;
        }
        int base = switch (family) {
            case BROWN -> 0xFF6B442E;
            case BLUE -> 0xFF6699BE;
            case HAZEL -> 0xFF8E9D4C;
            case GREEN -> 0xFF70975D;
            case GREY -> 0xFF81909C;
            case AMBER -> 0xFFC98A30;
        };
        double factor = 0.85 + 0.3 * pigmentation;
        int red = Math.min(255, (int) Math.round(((base >>> 16) & 255) * factor));
        int green = Math.min(255, (int) Math.round(((base >>> 8) & 255) * factor));
        int blue = Math.min(255, (int) Math.round((base & 255) * factor));
        return new ProbePhenotype(family, 0xFF000000 | red << 16 | green << 8 | blue, pigmentation, modifier);
    }

    private record ProbePhenotype(Family family, int argb, double pigmentation, double modifier) {
    }

    private enum Family {
        BROWN, BLUE, HAZEL, GREEN, GREY, AMBER
    }

    private enum Kind {
        SCALAR_MUTATION, SCALAR_NO_MUTATION, ONE_PAIR, TWO_PAIR, TWO_PAIR_COLOUR_FIRST
    }

    private record Snapshot(List<Long> counts, double pigmentMean, double pigmentVariance,
                            double rgbVariance, List<Double> alleleFrequencies) {
        private Snapshot {
            counts = List.copyOf(counts);
            alleleFrequencies = List.copyOf(alleleFrequencies);
        }
    }

    private record LocalExpression(double pigmentationOffset, double modifierOffset) {
    }

    private static Snapshot founderSnapshot(ProbeModel model, int size, long seed) {
        return snapshot(model, founders(model, size, RandomSource.create(seed)));
    }

    private static List<Snapshot> simulate(ProbeModel model, int size, int generations, long seed) {
        ProbeGenotype[] population = founders(model, size, RandomSource.create(seed));
        RandomSource mating = RandomSource.create(seed ^ 0x4D415445L);
        RandomSource inheritance = RandomSource.create(seed ^ 0x45594553L);
        List<Snapshot> results = new ArrayList<>();
        results.add(snapshot(model, population));
        for (int generation = 1; generation <= generations; generation++) {
            ProbeGenotype[] children = new ProbeGenotype[size];
            for (int i = 0; i < size; i++) {
                ParentIndices parents = parentIndices(size, mating);
                children[i] = model.inherit(population[parents.mother()],
                        population[parents.father()], inheritance);
            }
            population = children;
            if (generation == 10 || generation == 50 || generation == 100) {
                results.add(snapshot(model, population));
            }
        }
        return List.copyOf(results);
    }

    private static ProbeGenotype[] founders(ProbeModel model, int size, RandomSource random) {
        ProbeGenotype[] population = new ProbeGenotype[size];
        for (int i = 0; i < size; i++) {
            population[i] = model.founder(random);
        }
        return population;
    }

    private static Snapshot snapshot(ProbeModel model, ProbeGenotype[] population) {
        long[] counts = new long[Family.values().length];
        double sum = 0.0;
        double square = 0.0;
        double[] rgbSum = new double[3];
        double[] rgbSquare = new double[3];
        double[] frequencies = new double[6];
        for (ProbeGenotype genotype : population) {
            ProbePhenotype expressed = model.express(genotype);
            counts[expressed.family().ordinal()]++;
            sum += expressed.pigmentation();
            square += expressed.pigmentation() * expressed.pigmentation();
            for (int channel = 0; channel < 3; channel++) {
                double value = ((expressed.argb() >>> (channel * 8)) & 255) / 255.0;
                rgbSum[channel] += value;
                rgbSquare[channel] += value * value;
            }
            if (model.kind() != Kind.SCALAR_MUTATION && model.kind() != Kind.SCALAR_NO_MUTATION) {
                for (int allele = 0; allele < genotype.alleles().size(); allele++) {
                    int bucket = (allele / 2) * 3 + (int) Math.round(genotype.alleles().get(allele) * 2.0);
                    frequencies[bucket] += 1.0 / (2.0 * population.length);
                }
            }
        }
        double mean = sum / population.length;
        double rgbVariance = 0.0;
        for (int channel = 0; channel < 3; channel++) {
            double channelMean = rgbSum[channel] / population.length;
            rgbVariance += Math.max(0.0, rgbSquare[channel] / population.length - channelMean * channelMean);
        }
        if (model.kind() == Kind.SCALAR_MUTATION || model.kind() == Kind.SCALAR_NO_MUTATION) {
            Arrays.fill(frequencies, Double.NaN);
        } else if (model.kind() == Kind.ONE_PAIR) {
            Arrays.fill(frequencies, 3, 6, Double.NaN);
        }
        return new Snapshot(Arrays.stream(counts).boxed().toList(), mean,
                Math.max(0.0, square / population.length - mean * mean), rgbVariance,
                Arrays.stream(frequencies).boxed().toList());
    }

    private static void printSnapshot(String kind, long seed, int size, int generation, Snapshot snapshot) {
        StringBuilder row = new StringBuilder(String.format(Locale.ROOT, "POP,%s,%d,%d,%d", kind, seed, size, generation));
        for (long count : snapshot.counts()) {
            row.append(',').append(count);
        }
        row.append(String.format(Locale.ROOT, ",%.8f,%.8f,%.8f", snapshot.pigmentMean(),
                snapshot.pigmentVariance(), snapshot.rgbVariance()));
        for (double frequency : snapshot.alleleFrequencies()) {
            row.append(String.format(Locale.ROOT, ",%.8f", frequency));
        }
        System.out.println(row);
    }

    private static LocalExpression localExpression(ProbePhenotype base, RandomSource random) {
        double direction = base.pigmentation() < 0.5 ? 1.0 : -1.0;
        return new LocalExpression(direction * (0.2 + 0.2 * random.nextDouble()),
                (random.nextDouble() - 0.5) * 0.5);
    }

    private static ProbePhenotype expressLocal(ProbePhenotype base, LocalExpression local) {
        return phenotype(Mth.clamp(base.pigmentation() + local.pigmentationOffset(), 0.0, 1.0),
                Mth.clamp(base.modifier() + local.modifierOffset(), 0.0, 1.0));
    }

    private static int greyChildren(ProbeModel model, ProbeGenotype parent, ProbeGenotype partner, long seed) {
        RandomSource random = RandomSource.create(seed);
        int count = 0;
        for (int i = 0; i < 10_000; i++) {
            if (model.express(model.inherit(parent, partner, random)).family() == Family.GREY) {
                count++;
            }
        }
        return count;
    }

    private record ParentIndices(int mother, int father) {
    }

    private static ParentIndices parentIndices(int size, RandomSource random) {
        int mother = random.nextInt(size);
        int father = random.nextInt(size - 1);
        return new ParentIndices(mother, father >= mother ? father + 1 : father);
    }
}
