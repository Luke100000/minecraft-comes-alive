package net.conczin.mca.entity.ai.relationship;

import net.conczin.mca.MCA;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Extensible villager personality type.
 *
 * <p>Addons should register personalities during mod initialization using a
 * namespaced {@link ResourceLocation}. The identifier is also the stable value
 * used for persistence and client synchronization.</p>
 */
public final class Personality implements Comparable<Personality> {
    private static final Map<ResourceLocation, Personality> REGISTRY = new LinkedHashMap<>();
    private static final List<Personality> LEGACY_VALUES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    //Fallback on error.
    public static final Personality UNASSIGNED = registerBuiltIn("unassigned");

    public static final Personality FRIENDLY = registerBuiltIn("friendly");       // Easier to make friends
    public static final Personality FLIRTY = registerBuiltIn(                     // Likes to chat, flirt and kiss
            "flirty",
            Personality::isOldEnoughToFlirt
    );
    public static final Personality PLAYFUL = registerBuiltIn("playful");         // Loves games and fun activities
    public static final Personality GLOOMY = registerBuiltIn("gloomy");           // Always assuming the worst
    public static final Personality SENSITIVE = registerBuiltIn("sensitive");     // Double heart penalty
    public static final Personality GREEDY = registerBuiltIn("greedy");           // Finds less on chores
    public static final Personality ODD = registerBuiltIn("odd");                 // some interactions are more difficult
    public static final Personality CRABBY = registerBuiltIn("crabby");           // Hard to talk to
    public static final Personality EXTROVERTED = registerBuiltIn("extroverted"); // Enjoys group activities
    public static final Personality INTROVERTED = registerBuiltIn("introverted"); // Prefers solitary activities
    public static final Personality RELAXED = registerBuiltIn("relaxed");         // Calm and unbothered
    public static final Personality ANXIOUS = registerBuiltIn("anxious");         // Easily stressed
    public static final Personality PEACEFUL = registerBuiltIn("peaceful");       // Avoids conflict
    public static final Personality UPBEAT = registerBuiltIn("upbeat");           // Optimistic and cheerful

    private final ResourceLocation id;
    private final Predicate<AgeState> agePredicate;
    private final int compatibilityOrdinal;

    private Personality(ResourceLocation id, Predicate<AgeState> agePredicate, int compatibilityOrdinal) {
        this.id = id;
        this.agePredicate = agePredicate;
        this.compatibilityOrdinal = compatibilityOrdinal;
    }

    /**
     * Registers an unrestricted personality.
     */
    public static Personality register(ResourceLocation id) {
        return register(id, ageState -> true);
    }

    /**
     * Registers a personality with an age eligibility rule.
     */
    public static synchronized Personality register(ResourceLocation id, Predicate<AgeState> agePredicate) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(agePredicate, "agePredicate");
        if (REGISTRY.containsKey(id)) {
            throw new IllegalArgumentException("Duplicate personality id '" + id + "'");
        }

        Personality personality = new Personality(id, agePredicate, REGISTRY.size());
        REGISTRY.put(id, personality);
        return personality;
    }

    public static synchronized Optional<Personality> get(ResourceLocation id) {
        return Optional.ofNullable(REGISTRY.get(id));
    }

    public static Optional<Personality> get(String id) {
        return Optional.ofNullable(parseId(id)).flatMap(Personality::get);
    }

    public static synchronized List<Personality> all() {
        return List.copyOf(REGISTRY.values());
    }

    public static Personality getRandom() {
        return getRandom(AgeState.ADULT);
    }

    public static Personality getRandom(AgeState ageState) {
        List<Personality> valid = new ArrayList<>();
        for (Personality personality : all()) {
            if (personality != UNASSIGNED && personality.isValidFor(ageState)) {
                valid.add(personality);
            }
        }
        return valid.get(RANDOM.nextInt(valid.size()));
    }

    public static Personality byLegacyOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < LEGACY_VALUES.size() ? LEGACY_VALUES.get(ordinal) : UNASSIGNED;
    }

    public ResourceLocation getId() {
        return id;
    }

    public boolean isValidFor(AgeState ageState) {
        return agePredicate.test(ageState);
    }

    public String getDialoguePrefix() {
        return getTranslationSuffix(id);
    }

    public static String getDialoguePrefix(String id) {
        ResourceLocation parsed = parseId(id);
        return getTranslationSuffix(parsed == null ? UNASSIGNED.id : parsed);
    }

    public Component getName() {
        return Component.translatable("personality." + getTranslationSuffix(id));
    }

    public Component getDescription() {
        return Component.translatable("personalityDescription." + getTranslationSuffix(id));
    }

    /**
     * Compatibility shim for code compiled against the former enum API.
     */
    @Deprecated(forRemoval = false)
    public static Personality valueOf(String name) {
        return get(name).orElseThrow(() -> new IllegalArgumentException("Unknown personality '" + name + "'"));
    }

    /**
     * Compatibility shim for code compiled against the former enum API.
     */
    @Deprecated(forRemoval = false)
    public static Personality[] values() {
        return all().toArray(Personality[]::new);
    }

    /**
     * Compatibility shim for code compiled against the former enum API.
     */
    @Deprecated(forRemoval = false)
    public String name() {
        return id.getNamespace().equals(MCA.MOD_ID)
                ? id.getPath().toUpperCase(Locale.ROOT)
                : id.toString();
    }

    /**
     * Compatibility shim for code compiled against the former enum API.
     */
    @Deprecated(forRemoval = false)
    public int ordinal() {
        return compatibilityOrdinal;
    }

    @Override
    public int compareTo(Personality other) {
        return id.compareTo(other.id);
    }

    @Override
    public String toString() {
        return id.toString();
    }

    private static Personality registerBuiltIn(String path) {
        return registerBuiltIn(path, ageState -> true);
    }

    private static Personality registerBuiltIn(String path, Predicate<AgeState> agePredicate) {
        Personality personality = register(MCA.locate(path), agePredicate);
        LEGACY_VALUES.add(personality);
        return personality;
    }

    private static ResourceLocation parseId(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return normalized.indexOf(':') >= 0
                ? ResourceLocation.tryParse(normalized)
                : ResourceLocation.tryBuild(MCA.MOD_ID, normalized);
    }

    private static String getTranslationSuffix(ResourceLocation id) {
        String path = id.getPath().replace('/', '.');
        return id.getNamespace().equals(MCA.MOD_ID) ? path : id.getNamespace() + "." + path;
    }

    private static boolean isOldEnoughToFlirt(AgeState ageState) {
        return ageState != AgeState.BABY && ageState != AgeState.TODDLER && ageState != AgeState.CHILD;
    }
}
