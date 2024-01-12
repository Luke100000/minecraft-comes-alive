package net.mca.entity.ai;

import net.mca.Config;
import net.mca.entity.VillagerLike;
import net.mca.entity.ai.relationship.Gender;
import net.mca.util.network.datasync.CDataManager;
import net.mca.util.network.datasync.CDataParameter;
import net.mca.util.network.datasync.CEnumParameter;
import net.mca.util.network.datasync.CParameter;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

import java.util.*;

/**
 * Villagerized Genetic Diversity.
 */
public class Genetics implements Iterable<Genetics.Gene> {
    private static final Set<GeneType> GENOMES = new HashSet<>();

    public static final GeneType SIZE = new GeneType("gene_size");
    public static final GeneType WIDTH = new GeneType("gene_width");
    public static final GeneType BREAST = new GeneType("gene_breast");
    public static final GeneType MELANIN = new GeneType("gene_melanin");
    public static final GeneType HEMOGLOBIN = new GeneType("gene_hemoglobin");
    public static final GeneType EUMELANIN = new GeneType("gene_eumelanin");
    public static final GeneType PHEOMELANIN = new GeneType("gene_pheomelanin");
    public static final GeneType SKIN = new GeneType("gene_skin");
    public static final GeneType FACE = new GeneType("gene_face");
    public static final GeneType VOICE = new GeneType("gene_voice");
    public static final GeneType VOICE_TONE = new GeneType("gene_voice_tone");

    private static final CEnumParameter<Gender> GENDER = CParameter.create("gender", Gender.UNASSIGNED);

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        GENOMES.forEach(g -> builder.addAll(g.getParam()));
        return builder.addAll(GENDER);
    }

    private Random random = Random.create();

    private final Map<GeneType, Gene> genes = new HashMap<>();

    private final VillagerLike<?> entity;

    public Genetics(VillagerLike<?> entity) {
        this.entity = entity;
    }

    public float getVerticalScaleFactor() {
        return 0.75F + getGene(SIZE) / 2;
    }

    public float getHorizontalScaleFactor() {
        return 0.75F + getGene(WIDTH) / 2;
    }

    public void setGender(Gender gender) {
        entity.setTrackedValue(GENDER, gender);
    }

    public Gender getGender() {
        return entity.getTrackedValue(GENDER);
    }

    public float getBreastSize() {
        return getGender() == Gender.FEMALE ? getGene(BREAST) : 0;
    }

    @Override
    public Iterator<Gene> iterator() {
        return genes.values().iterator();
    }

    public void setGene(GeneType type, float value) {
        getGenome(type).set(value);
    }

    public float getGene(GeneType type) {
        return getGenome(type).get();
    }

    public Gene getGenome(GeneType type) {
        return genes.computeIfAbsent(type, Gene::new);
    }

    //initializes the genes with random numbers
    public void randomize() {
        for (GeneType type : GENOMES) {
            getGenome(type).randomize();
        }

        // size is more centered
        setGene(SIZE, centeredRandom());
        setGene(WIDTH, centeredRandom());

        // temperature
        float temp = entity.asEntity().world.getBiome(entity.asEntity().getBlockPos()).value().getTemperature();

        // immigrants
        if (random.nextFloat() < Config.getInstance().geneticImmigrantChance) {
            temp = random.nextFloat() * 2 - 0.5F;
        }

        setGene(MELANIN, temperatureBaseRandom(temp));
        setGene(HEMOGLOBIN, temperatureBaseRandom(temp));

        setGene(EUMELANIN, random.nextFloat());
        setGene(PHEOMELANIN, random.nextFloat());
    }

    /**
     * Produces a float between 0 and 1, weighted at 0.5
     */
    private float centeredRandom() {
        return Math.min(1, Math.max(0, (random.nextFloat() - 0.5F) * (random.nextFloat() - 0.5F) + 0.5F));
    }

    private float temperatureBaseRandom(float temp) {
        return MathHelper.clamp((random.nextFloat() - 0.5F) * 0.5F + temp * 0.4F + 0.1F, 0, 1);
    }

    public void combine(Genetics mother, Genetics father) {
        for (GeneType type : GENOMES) {
            getGenome(type).mutate(mother, father);
        }
    }

    public void combine(Genetics mother, Genetics father, long seed) {
        Random old = random;
        random = Random.create(seed);
        combine(mother, father);
        random = old;
    }

    public class Gene {
        private final GeneType type;

        public Gene(GeneType type) {
            this.type = type;
        }

        public GeneType getType() {
            return type;
        }

        public float get() {
            return entity.getTrackedValue(type.parameter);
        }

        public void set(float value) {
            entity.setTrackedValue(type.parameter, value);
        }

        public void randomize() {
            set(random.nextFloat());
        }

        public void mutate(Genetics mother, Genetics father) {
            float m = mother.getGene(type);
            float f = father.getGene(type);
            float interpolation = random.nextFloat();
            float mutation = (random.nextFloat() - 0.5f) * 0.2f;
            float g = m * interpolation + f * (1.0f - interpolation) + mutation;

            set((float) Math.min(1.0, Math.max(0.0, g)));
        }
    }

    public static class GeneType implements Comparable<GeneType> {
        private final String key;
        private final CDataParameter<Float> parameter;

        public GeneType(String key) {
            this.key = key;
            parameter = CParameter.create(key, 0.5f);
            GENOMES.add(this);
        }

        public String key() {
            return key;
        }

        public String getTranslationKey() {
            return key().replace("_", ".");
        }

        public CDataParameter<Float> getParam() {
            return parameter;
        }

        @Override
        public int compareTo(GeneType o) {
            return key().compareTo(o.key());
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof GeneType geneType && geneType.key().equals(key());
        }
    }
}
