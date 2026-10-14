package net.conczin.mca.entity.ai;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Game-scale eye inheritance, separate from cosmetics and the generic scalar genes. */
public final class EyeGenetics {
    public static final String KEY = "EyeGenetics";
    public static final int FORMAT = 1;
    private static final String[] ALLELES = {"PigmentA", "PigmentB", "ModifierA", "ModifierB"};
    private static final long INFERENCE_SALT = 0x455945494E464552L;
    public static final long FOUNDER_SALT = 0x455945464F554E44L;
    public static final long LOCAL_SALT = 0x4559454C4F43414CL;
    private static final int GREY = 0xFF81909C, BLUE = 0xFF6699BE;
    private static final int GREEN = 0xFF70975D, HAZEL = 0xFF8E9D4C;
    private static final int AMBER = 0xFFC98A30, BROWN = 0xFF6B442E;
    // Representative iris pixels from the bundled 22-entry textures at f756d7bd20fb26ebbf371f93f9815349e6b1467a.
    // These retain recoverable colour, not pixel-identical shading/custom resource-pack artwork.
    private static final int[][] BAKED = {
            {0xFF562D12, 0xFFEC68A2, 0xFF81431A, 0xFFB46CEC, 0xFF6CD71F, 0xFF2E37E2, 0xFFDC2AD2, 0xFF56CBD9, 0xFFDC322A, 0xFF929292, 0xFFE2D263, 0xFF2C1709, 0xFFD41C6C, 0xFFA45F14, 0xFF831CD4, 0xFF55AB19, 0xFF1920AB, 0xFF881682, 0xFF2594A3, 0xFFDC322A, 0xFF5D5D5D, 0xFFBDAA24},
            {0xFFE2D263, 0xFF562D12, 0xFFEC68A2, 0xFF81431A, 0xFFB46CEC, 0xFF6CD71F, 0xFF2E37E2, 0xFFDC2AD2, 0xFF56CBD9, 0xFFDC322A, 0xFF929292, 0xFFE2D263, 0xFF2C1709, 0xFFD41C6C, 0xFFA45F14, 0xFF55AB19, 0xFF1920AB, 0xFF881682, 0xFF2594A3, 0xFFDC322A, 0xFF1920AB, 0xFF5D5D5D},
            {0xFFDC322A, 0xFFE2D263, 0xFF562D12, 0xFFEC68A2, 0xFF81431A, 0xFFB46CEC, 0xFF6CD71F, 0xFF2E37E2, 0xFFDC2AD2, 0xFF56CBD9, 0xFFDC322A, 0xFFDC322A, 0xFFBDAA24, 0xFF2C1709, 0xFFD41C6C, 0xFF831CD4, 0xFF55AB19, 0xFF1920AB, 0xFF881682, 0xFF2594A3, 0xFF55AB19, 0xFF871C16},
            {0xFF7A4D2B, 0xFFE887B0, 0xFFDB6F20, 0xFFCB90EE, 0xFF99E891, 0xFF90A2EE, 0xFFE887E1, 0xFF91DEE8, 0xFFEA6769, 0xFFC0C0C0, 0xFFF3F0B3, 0xFFF2DFD1, 0xFFE886B0, 0xFFDB6F20, 0xFFCB91EE, 0xFF99E891, 0xFF91A2EE, 0xFFEA4EEA, 0xFF91DEE8, 0xFFEA6769, 0xFFF3F3F3, 0xFFFFFD93},
            {0xFF7A4D2B, 0xFFE887B0, 0xFFEA6769, 0xFFDB6F20, 0xFFCB90EE, 0xFFF3F0B3, 0xFF90A2EE, 0xFFE887E1, 0xFF91DEE8, 0xFFEA6769, 0xFFC0C0C0, 0xFFF2DFD1, 0xFFE886B0, 0xFFEA6769, 0xFFDB6F20, 0xFFCB91EE, 0xFFFFFD93, 0xFF91A2EE, 0xFFEA4EEA, 0xFF91DEE8, 0xFFEA6769, 0xFFF3F3F3},
            {0xFFC0C0C0, 0xFF7A4D2B, 0xFFE887B0, 0xFFE887B0, 0xFFDB6F20, 0xFFCB90EE, 0xFFF3F0B3, 0xFF90A2EE, 0xFFE887E1, 0xFF91DEE8, 0xFFEA6769, 0xFFC0C0C0, 0xFF7A4D2B, 0xFFE886B0, 0xFFE886B0, 0xFFDB6F20, 0xFFCB91EE, 0xFFFFFD93, 0xFF91A2EE, 0xFFEA4EEA, 0xFF91DEE8, 0xFFEA6769}
    };
    private EyeGenetics() {}
    public enum PopulationPreset { DIVERSE }
    public record Locus(int first, int second) {
        public Locus { checkAllele(first); checkAllele(second); }
        int sample(RandomSource random) { return random.nextBoolean() ? first : second; }
    }
    public record Genotype(Locus pigmentation, Locus modifier) {
        public Genotype { Objects.requireNonNull(pigmentation); Objects.requireNonNull(modifier); }
    }
    public record LocalExpression(float pigmentationOffset, float appearanceOffset) {
        public LocalExpression {
            if (!Float.isFinite(pigmentationOffset) || Math.abs(pigmentationOffset) > 0.4F
                    || !Float.isFinite(appearanceOffset) || Math.abs(appearanceOffset) > 0.25F) {
                throw new IllegalArgumentException("EyeGenetics local offsets exceed pigment 0.4 / modifier 0.25 bounds");
            }
        }
    }
    private static void checkAllele(int value) {
        if (value < 0 || value > 2) throw new IllegalArgumentException("EyeGenetics allele must be an integer in [0,2]: " + value);
    }
    public static PopulationPreset parsePreset(String value) {
        if (!"DIVERSE".equals(value)) throw new IllegalArgumentException("Invalid eyePopulationPreset '" + value + "'; supported: DIVERSE. Config was not rewritten.");
        return PopulationPreset.DIVERSE;
    }
    public static long seed(UUID identity, long salt) {
        return identity.getMostSignificantBits() ^ Long.rotateLeft(identity.getLeastSignificantBits(), 21) ^ salt;
    }
    public static Genotype founder(PopulationPreset preset, RandomSource random) {
        Objects.requireNonNull(preset);
        // Diverse founders: pigmentation (55%, 22.5%, 22.5%), modifier (45%, 18%, 37%).
        // Each allele is drawn independently; offspring still inherit actual parental alleles.
        return new Genotype(new Locus(sample(random, 0.55, 0.775), sample(random, 0.55, 0.775)),
                new Locus(sample(random, 0.45, 0.63), sample(random, 0.45, 0.63)));
    }
    private static int sample(RandomSource random, double first, double second) {
        double value = random.nextDouble();
        return value < first ? 0 : value < second ? 1 : 2;
    }
    public static Genotype inherit(Genotype mother, Genotype father, RandomSource random) {
        return new Genotype(new Locus(mother.pigmentation.sample(random), father.pigmentation.sample(random)),
                new Locus(mother.modifier.sample(random), father.modifier.sample(random)));
    }
    public static Genotype inheritWithMissingParent(Genotype mother, Genotype father, PopulationPreset preset, RandomSource random) {
        return inherit(mother == null ? founder(preset, random) : mother,
                father == null ? founder(preset, random) : father, random);
    }
    private static float pigmentation(Genotype genotype) {
        Locus locus = genotype.pigmentation;
        return (0.75F * Math.max(locus.first, locus.second) + 0.25F * Math.min(locus.first, locus.second)) / 2;
    }
    private static float modifier(Genotype genotype) {
        return (genotype.modifier.first + genotype.modifier.second) / 4.0F;
    }
    public static LocalExpression localExpression(Genotype genotype, RandomSource random) {
        Objects.requireNonNull(genotype);
        float direction = random.nextBoolean() ? 1 : -1;
        return new LocalExpression(direction * (0.05F + 0.35F * random.nextFloat()), (random.nextFloat() - 0.5F) * 0.5F);
    }
    public static int resolveNatural(Genotype genotype, LocalExpression local, boolean left) {
        float p = pigmentation(genotype), s = modifier(genotype);
        if (left && local != null) {
            p = Mth.clamp(p + local.pigmentationOffset, 0, 1);
            s = Mth.clamp(s + local.appearanceOffset, 0, 1);
        }
        return 0xFF000000 | (naturalChannel(p, s, 16) << 16)
                | (naturalChannel(p, s, 8) << 8) | naturalChannel(p, s, 0);
    }
    /** Blend expression coordinates, never parental RGB. Round once after blending and brightness. */
    private static int naturalChannel(double p, double s, int shift) {
        double cool = blend(GREY >>> shift & 255, BLUE >>> shift & 255, smooth(0, 0.5, s));
        double middle = blend(GREEN >>> shift & 255, HAZEL >>> shift & 255, smooth(0, 0.75, s));
        double warm = blend(BROWN >>> shift & 255, AMBER >>> shift & 255, smooth(0.65, 1, s));
        double channel = p < 0.5 ? blend(cool, middle, smooth(0, 0.5, p))
                : p < 0.8 ? blend(middle, warm, smooth(0.5, 0.8, p))
                : blend(warm, BROWN >>> shift & 255, smooth(0.8, 1, p));
        return Math.min(255, (int) Math.round(channel * (0.85 + 0.30 * p)));
    }
    private static double blend(double first, double second, double weight) {
        return first + (second - first) * weight;
    }
    private static double smooth(double lower, double upper, double value) {
        double t = Mth.clamp((value - lower) / (upper - lower), 0, 1);
        return t * t * (3 - 2 * t);
    }
    public static Genotype inferLegacy(CompoundTag legacy, UUID identity) {
        return infer(legacy, new CompoundTag(), identity);
    }
    private static Genotype infer(CompoundTag legacy, CompoundTag partial, UUID identity) {
        int target = resolveLegacyNatural(legacy, false, false, false);
        List<Genotype> closest = new ArrayList<>();
        long best = Long.MAX_VALUE;
        // Lexicographic, unphased candidate order; preserve valid partial slot values.
        boolean hasPartial = List.of(ALLELES).stream().anyMatch(key -> validAllele(partial, key));
        for (int a = 0; a <= 2; a++) for (int b = 0; b <= 2; b++)
            for (int c = 0; c <= 2; c++) for (int d = 0; d <= 2; d++) {
                if (!hasPartial && (a > b || c > d)) continue;
                int[] values = {a, b, c, d};
                boolean compatible = true;
                for (int i = 0; i < ALLELES.length; i++) {
                    if (validAllele(partial, ALLELES[i]) && partial.getInt(ALLELES[i]) != values[i]) compatible = false;
                }
                if (!compatible) continue;
                Genotype candidate = new Genotype(new Locus(a, b), new Locus(c, d));
                int color = resolveNatural(candidate, null, false);
                long distance = 0;
                for (int shift : new int[]{0, 8, 16}) {
                    int delta = (color >>> shift & 255) - (target >>> shift & 255);
                    distance += delta * delta;
                }
                if (distance < best) { best = distance; closest.clear(); }
                if (distance == best) closest.add(candidate);
            }
        return closest.get(RandomSource.create(seed(identity, INFERENCE_SALT)).nextInt(closest.size()));
    }
    public static CompoundTag encode(Genotype genotype, LocalExpression local, CompoundTag legacy) {
        CompoundTag payload = new CompoundTag();
        payload.putInt("Format", FORMAT);
        payload.putInt(ALLELES[0], genotype.pigmentation.first);
        payload.putInt(ALLELES[1], genotype.pigmentation.second);
        payload.putInt(ALLELES[2], genotype.modifier.first);
        payload.putInt(ALLELES[3], genotype.modifier.second);
        if (local != null) {
            CompoundTag value = new CompoundTag();
            value.putFloat("Pigment", local.pigmentationOffset);
            value.putFloat("Modifier", local.appearanceOffset);
            payload.put("Local", value);
        }
        if (!legacy.isEmpty()) payload.put("LegacyNatural", legacy.copy());
        return payload;
    }
    public static boolean isSupported(CompoundTag payload) {
        return payload.contains("Format", Tag.TAG_INT) && payload.getInt("Format") == FORMAT;
    }
    private static void requireSupported(CompoundTag payload) {
        if (!isSupported(payload)) throw new IllegalArgumentException("Unsupported EyeGenetics Format: " + payload.get("Format"));
    }
    private static boolean validAllele(CompoundTag payload, String key) {
        return payload.contains(key, Tag.TAG_INT) && payload.getInt(key) >= 0 && payload.getInt(key) <= 2;
    }
    static boolean hasGenotype(CompoundTag payload) {
        if (!isSupported(payload)) return false;
        for (String key : ALLELES) if (!validAllele(payload, key)) return false;
        return true;
    }
    public static Genotype decodeGenotype(CompoundTag payload) {
        requireSupported(payload);
        for (String key : ALLELES) if (!validAllele(payload, key)) throw new IllegalArgumentException("Invalid EyeGenetics " + key);
        return new Genotype(new Locus(payload.getInt(ALLELES[0]), payload.getInt(ALLELES[1])),
                new Locus(payload.getInt(ALLELES[2]), payload.getInt(ALLELES[3])));
    }
    public static LocalExpression decodeLocalExpression(CompoundTag payload) {
        requireSupported(payload);
        if (!payload.contains("Local")) return null;
        if (!payload.contains("Local", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("Invalid EyeGenetics Local compound");
        CompoundTag local = payload.getCompound("Local");
        if (!local.contains("Pigment", Tag.TAG_FLOAT) || !local.contains("Modifier", Tag.TAG_FLOAT)) throw new IllegalArgumentException("Invalid EyeGenetics Local offsets");
        return new LocalExpression(local.getFloat("Pigment"), local.getFloat("Modifier"));
    }
    /** Pure server normalization. Unknown formats are opaque, never reinterpreted as missing. */
    public static CompoundTag initializeSavedPayload(CompoundTag payload, UUID identity, PopulationPreset preset) {
        if (!payload.isEmpty() && !isSupported(payload)) return payload.copy();
        CompoundTag result = payload.copy();
        result.putInt("Format", FORMAT);
        if (!hasGenotype(result)) {
            CompoundTag legacy = result.getCompound("LegacyNatural");
            Genotype genotype = !legacy.isEmpty() ? infer(legacy, result, identity)
                    : founder(preset, RandomSource.create(seed(identity, FOUNDER_SALT)));
            CompoundTag encoded = encode(genotype, null, legacy);
            for (String key : ALLELES) {
                if (!validAllele(result, key)) result.put(key, Objects.requireNonNull(encoded.get(key)).copy());
            }
        }
        try { decodeLocalExpression(result); } catch (IllegalArgumentException invalid) { result.remove("Local"); }
        return result;
    }
    public static CompoundTag finishSavedExpression(CompoundTag payload, boolean heterochromia, long seed) {
        CompoundTag result = payload.copy();
        if (isSupported(result) && heterochromia && !result.contains("LegacyNatural") && !result.contains("Local")) {
            Genotype genotype = decodeGenotype(result);
            result.put("Local", encode(genotype, localExpression(genotype, RandomSource.create(seed ^ LOCAL_SALT)),
                    new CompoundTag()).getCompound("Local").copy());
        }
        return result;
    }
    /** Capture before the old migration removes FACE. Manual dyes are deliberately not captured. */
    public static CompoundTag captureLegacyNatural(CompoundTag raw) {
        CompoundTag legacy = new CompoundTag();
        String key = raw.contains("GeneEyeColor", Tag.TAG_ANY_NUMERIC) ? "GeneEyeColor" : "GeneFace";
        if (raw.contains(key, Tag.TAG_ANY_NUMERIC)) {
            float scalar = raw.getFloat(key);
            if (Float.isFinite(scalar) && scalar >= 0 && scalar <= 1) {
                legacy.putFloat("Scalar", scalar);
                legacy.putString("Source", key);
                legacy.putBoolean("Baked", key.equals("GeneFace") && !raw.contains("EyeColor"));
                legacy.putInt("Gender", raw.getInt("Gender"));
                if (legacy.getBoolean("Baked")) {
                    int index = Mth.clamp((int)(scalar * 22), 0, 21);
                    int row = raw.getInt("Gender") == 2 ? 3 : 0;
                    legacy.putInt("Argb", BAKED[row][index]);
                    legacy.putInt("HeteroArgb", BAKED[row + 1][index]);
                    legacy.putInt("HeteroLeftArgb", BAKED[row + 2][index]);
                }
            }
        }
        return legacy;
    }
    public static int resolveLegacyNatural(CompoundTag legacy, boolean left, boolean heterochromia, boolean albinism) {
        if (albinism) return 0xFFE8A0A0;
        String bakedKey = heterochromia ? (left ? "HeteroLeftArgb" : "HeteroArgb") : "Argb";
        if (legacy.contains(bakedKey, Tag.TAG_INT)) return legacy.getInt(bakedKey);
        float value = Mth.frac((legacy.contains("Scalar", Tag.TAG_FLOAT) ? legacy.getFloat("Scalar") : 0.5F)
                + (left && heterochromia ? 0.43F : 0));
        if (value < 0.35F) return FastColor.ARGB32.lerp(value / 0.35F, 0xFF3A98E8, 0xFF4CB346);
        if (value < 0.70F) return FastColor.ARGB32.lerp((value - 0.35F) / 0.35F, 0xFF4CB346, 0xFFC29B35);
        return FastColor.ARGB32.lerp((value - 0.70F) / 0.30F, 0xFFC29B35, 0xFF7C4825);
    }
    public static float compatibilityScalar(CompoundTag payload) {
        CompoundTag legacy = payload.getCompound("LegacyNatural");
        return legacy.contains("Scalar", Tag.TAG_FLOAT) ? legacy.getFloat("Scalar") : pigmentation(decodeGenotype(payload));
    }
}
