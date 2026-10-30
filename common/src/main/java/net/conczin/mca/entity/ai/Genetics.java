package net.conczin.mca.entity.ai;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.EyeCatalog;
import net.conczin.mca.resources.EyeStyles;
import net.conczin.mca.util.network.datasync.CDataManager;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.conczin.mca.util.network.datasync.CEnumParameter;
import net.conczin.mca.util.network.datasync.CParameter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;

import java.util.*;

/**
 * Villagerized Genetic Diversity.
 */
public class Genetics implements Iterable<Genetics.Gene> {
    private static final Set<GeneType> GENOMES = new HashSet<>();

    public static final GeneType SIZE = new GeneType("Size");
    public static final GeneType WIDTH = new GeneType("Width");
    public static final GeneType BREAST = new GeneType("Breast");
    public static final GeneType MELANIN = new GeneType("Melanin");
    public static final GeneType HEMOGLOBIN = new GeneType("Hemoglobin");
    public static final GeneType EUMELANIN = new GeneType("Eumelanin");
    public static final GeneType PHEOMELANIN = new GeneType("Pheomelanin");
    public static final GeneType EYE_COLOR = new GeneType("EyeColor");
    public static final GeneType SKIN = new GeneType("Skin");
    public static final GeneType EYE_BRIGHTNESS = new GeneType("EyeBrightness");
    public static final GeneType VOICE = new GeneType("Voice");
    public static final GeneType VOICE_TONE = new GeneType("VoiceTone");

    private static final CEnumParameter<Gender> GENDER = CParameter.create("Gender", Gender.UNASSIGNED);
    private static final CDataParameter<CompoundTag> EYES = new CDataParameter<>(
            EyeGenetics.KEY, EntityDataSerializers.COMPOUND_TAG, new CompoundTag(),
            (nbt, key, registries) -> nbt.getCompound(key).copy(),
            (nbt, key, value, registries) -> nbt.put(key, value.copy())
    ) {
        @Override public CompoundTag getDefault() { return new CompoundTag(); }
    };
    private static final long INHERITANCE_SALT = 0x455945494E484552L;

    private final Map<GeneType, Gene> genes = new HashMap<>();
    private final VillagerLike<?> entity;
    private RandomSource random = RandomSource.create();

    public Genetics(VillagerLike<?> entity) {
        this.entity = entity;
    }

    public CompoundTag getEyePayload() { return entity.getTrackedValue(EYES).copy(); }
    public void setEyePayload(CompoundTag payload) {
        entity.setTrackedValue(EYES, payload.copy());
        if (EyeGenetics.hasGenotype(payload)) {
            entity.setTrackedValue(EYE_COLOR.parameter, EyeGenetics.compatibilityScalar(payload));
        }
    }

    public void initializeEyesIfMissing() {
        if (entity.asEntity().level().isClientSide) return;
        CompoundTag payload = getEyePayload();
        setEyePayload(EyeGenetics.initializeSavedPayload(payload, entity.asEntity().getUUID()));
    }

    public void finishEyeExpression(long seed) {
        if (entity.asEntity().level().isClientSide) return;
        initializeEyesIfMissing();
        CompoundTag payload = getEyePayload();
        setEyePayload(EyeGenetics.finishSavedExpression(payload, entity.getTraits().hasTrait(Traits.HETEROCHROMIA), seed));
    }

    public int getNaturalEyeColor(boolean left) {
        boolean heterochromia = entity.getTraits().hasTrait(Traits.HETEROCHROMIA);
        boolean albinism = entity.getTraits().hasTrait(Traits.ALBINISM);
        if (albinism) return 0xFFE8A0A0;
        CompoundTag payload = entity.getTrackedValue(EYES);
        if (payload.contains("LegacyNatural")) return EyeGenetics.resolveLegacyNatural(payload.getCompound("LegacyNatural"), left, heterochromia, false);
        if (EyeGenetics.hasGenotype(payload)) {
            return EyeGenetics.resolveNatural(EyeGenetics.decodeGenotype(payload),
                    heterochromia ? EyeGenetics.decodeLocalExpression(payload) : null, left);
        }
        // Before the initial server snapshot (or with a future format) this is only a read-only preview.
        CompoundTag preview = new CompoundTag();
        preview.putFloat("Scalar", entity.getTrackedValue(EYE_COLOR.parameter));
        return EyeGenetics.resolveLegacyNatural(preview, left, heterochromia, false);
    }

    public void setNaturalEyeColorFromLegacyScalar(float scalar) {
        if (!Float.isFinite(scalar) || scalar < 0 || scalar > 1) throw new IllegalArgumentException("Eye colour scalar must be finite in [0,1]");
        if (entity.asEntity().level().isClientSide) return;
        CompoundTag payload = getEyePayload();
        if (!payload.isEmpty() && !EyeGenetics.isSupported(payload)) throw new IllegalArgumentException("Cannot edit unsupported EyeGenetics Format " + payload.get("Format"));
        CompoundTag legacy = new CompoundTag(); legacy.putFloat("Scalar", scalar);
        setEyePayload(EyeGenetics.encode(EyeGenetics.inferLegacy(legacy, entity.asEntity().getUUID()), null, new CompoundTag()));
        finishEyeExpression(EyeGenetics.seed(entity.asEntity().getUUID(), 0));
    }

    public static <E extends Entity> CDataManager.Builder<E> createTrackedData(CDataManager.Builder<E> builder) {
        GENOMES.forEach(g -> builder.addAll(g.getParam()));
        return builder.addAll(GENDER, EYES);
    }

    public float getVerticalScaleFactor() {
        return 0.75F + getGene(SIZE) / 2;
    }

    public float getHorizontalScaleFactor() {
        return 0.75F + getGene(WIDTH) / 2;
    }

    public Gender getGender() {
        return entity.getTrackedValue(GENDER);
    }

    public void setGender(Gender gender) {
        entity.setTrackedValue(GENDER, gender);
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

        EyeCatalog eyes = EyeCatalog.getInstance();
        entity.setEyeTexture(eyes == null ? EyeStyles.DEFAULT : eyes.pick(getGender()));

        // size is more centered
        setGene(SIZE, centeredRandom());
        setGene(WIDTH, centeredRandom());

        // temperature
        float temp = entity.asEntity().level().getBiome(entity.asEntity().blockPosition()).value().getBaseTemperature();

        // immigrants
        if (random.nextFloat() < Config.getInstance().geneticImmigrantChance) {
            temp = random.nextFloat() * 2 - 0.5F;
        }

        Entity owningEntity = entity.asEntity();
        int seaLevel = owningEntity.level() instanceof ServerLevel serverLevel
                ? serverLevel.getChunkSource().getGenerator().getSeaLevel()
                : owningEntity.level().getSeaLevel(); // client only is always 63Y
        float height = owningEntity.blockPosition().getY();
        height -= seaLevel;
        height /= 128;

        setGene(MELANIN, Mth.clamp(temperatureBaseRandom(temp) - height * 0.2f, 0, 1));
        setGene(HEMOGLOBIN, Mth.clamp(temperatureBaseRandom(temp) * 0.5f + height * 0.5f, 0, 1));

        setGene(EUMELANIN, random.nextFloat());
        setGene(PHEOMELANIN, random.nextFloat());
        if (!entity.asEntity().level().isClientSide) {
            initializeEyesIfMissing();
        }
    }

    /**
     * Produces a float between 0 and 1, weighted at 0.5
     */
    private float centeredRandom() {
        return Math.min(1, Math.max(0, (random.nextFloat() - 0.5F) * (random.nextFloat() - 0.5F) + 0.5F));
    }

    private float temperatureBaseRandom(float temp) {
        return (random.nextFloat() - 0.5F) * 0.35F + temp * 0.4F + 0.1F;
    }

    public void combine(Genetics mother, Genetics father) {
        combineEyesAndGenes(mother, father, EyeGenetics.seed(entity.asEntity().getUUID(), INHERITANCE_SALT));
    }

    private void combineEyesAndGenes(Genetics mother, Genetics father, long eyeSeed) {
        // Check parental formats before changing the child. Unsupported DNA must not become a fallback founder.
        EyeGenetics.Genotype maternalEyes = inheritedEyes(mother);
        EyeGenetics.Genotype paternalEyes = inheritedEyes(father);
        for (GeneType type : GENOMES) {
            getGenome(type).mutate(mother, father);
        }

        ResourceLocation inheritedEye = random.nextBoolean()
                ? (mother == null ? EyeStyles.DEFAULT : mother.entity.getEyeTexture())
                : (father == null ? EyeStyles.DEFAULT : father.entity.getEyeTexture());
        EyeCatalog eyes = EyeCatalog.getInstance();
        entity.setEyeTexture(eyes == null ? inheritedEye : eyes.resolve(inheritedEye, getGender()));
        if (!entity.asEntity().level().isClientSide) {
            setEyePayload(EyeGenetics.encode(EyeGenetics.inheritWithMissingParent(maternalEyes, paternalEyes,
                    RandomSource.create(eyeSeed)), null, new CompoundTag()));
        }
    }

    private static EyeGenetics.Genotype inheritedEyes(Genetics parent) {
        if (parent == null) return null;
        parent.initializeEyesIfMissing();
        return EyeGenetics.decodeGenotype(parent.getEyePayload());
    }

    public void combine(Genetics mother, Genetics father, long seed) {
        RandomSource old = random;
        random = RandomSource.create(seed);
        try {
            combineEyesAndGenes(mother, father, seed ^ INHERITANCE_SALT);
        } finally {
            random = old;
        }
    }

    public static class GeneType implements Comparable<GeneType> {
        private final String key;
        private final CDataParameter<Float> parameter;

        public GeneType(String key) {
            this.key = key;
            this.parameter = CParameter.create("Gene" + key, 0.5f);

            GENOMES.add(this);
        }

        public String key() {
            return key;
        }

        public String getTranslationKey() {
            return "gene." + key().toLowerCase(Locale.ROOT);
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

    public class Gene {
        private final GeneType type;

        public Gene(GeneType type) {
            this.type = type;
        }

        public GeneType getType() {
            return type;
        }

        public float get() {
            if (type == EYE_COLOR) {
                CompoundTag payload = entity.getTrackedValue(EYES);
                if (EyeGenetics.hasGenotype(payload)) return EyeGenetics.compatibilityScalar(payload);
            }
            return entity.getTrackedValue(type.parameter);
        }

        public void set(float value) {
            if (type == EYE_COLOR) {
                setNaturalEyeColorFromLegacyScalar(value);
                return;
            }
            entity.setTrackedValue(type.parameter, value);
        }

        public void randomize() {
            entity.setTrackedValue(type.parameter, random.nextFloat());
        }

        public void mutate(Genetics mother, Genetics father) {
            float m = mother == null ? 0.5F : mother.entity.getTrackedValue(type.parameter);
            float f = father == null ? 0.5F : father.entity.getTrackedValue(type.parameter);
            float interpolation = random.nextFloat();
            float mutation = (random.nextFloat() - 0.5f) * 0.2f;
            float g = m * interpolation + f * (1.0f - interpolation) + mutation;

            entity.setTrackedValue(type.parameter, (float) Math.min(1.0, Math.max(0.0, g)));
        }
    }
}
