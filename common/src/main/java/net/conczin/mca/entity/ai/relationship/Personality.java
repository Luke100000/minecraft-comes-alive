package net.conczin.mca.entity.ai.relationship;

import net.conczin.mca.MCA;
import net.conczin.mca.util.ExtensibleTypeRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
    private static final String DIALOGUE_DOT_ESCAPE = "%2E";
    private static final ExtensibleTypeRegistry<Personality> REGISTRY = new ExtensibleTypeRegistry<>(MCA.MOD_ID, "personality");
    private static final List<Personality> LEGACY_VALUES = new ArrayList<>();
    private static final RandomSource RANDOM = RandomSource.create();

    //Fallback on error.
    public static final Personality UNASSIGNED = registerLegacyBuiltIn("unassigned");

    public static final Personality FRIENDLY = registerLegacyBuiltIn("friendly");       // Easier to make friends
    public static final Personality FLIRTY = registerLegacyBuiltIn(                     // Likes to chat, flirt and kiss
            "flirty",
            Personality::isOldEnoughToFlirt
    );
    public static final Personality PLAYFUL = registerLegacyBuiltIn("playful");         // Loves games and fun activities
    public static final Personality GLOOMY = registerLegacyBuiltIn("gloomy");           // Always assuming the worst
    public static final Personality SENSITIVE = registerLegacyBuiltIn("sensitive");     // Double heart penalty
    public static final Personality GREEDY = registerLegacyBuiltIn("greedy");           // Finds less on chores
    public static final Personality ODD = registerLegacyBuiltIn("odd");                 // some interactions are more difficult
    public static final Personality CRABBY = registerLegacyBuiltIn("crabby");           // Hard to talk to
    public static final Personality EXTROVERTED = registerLegacyBuiltIn("extroverted"); // Enjoys group activities
    public static final Personality INTROVERTED = registerLegacyBuiltIn("introverted"); // Prefers solitary activities
    public static final Personality RELAXED = registerLegacyBuiltIn("relaxed");         // Calm and unbothered
    public static final Personality ANXIOUS = registerLegacyBuiltIn("anxious");         // Easily stressed
    public static final Personality PEACEFUL = registerLegacyBuiltIn("peaceful");       // Avoids conflict
    public static final Personality UPBEAT = registerLegacyBuiltIn("upbeat");           // Optimistic and cheerful

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
    public static Personality register(ResourceLocation id, Predicate<AgeState> agePredicate) {
        Objects.requireNonNull(agePredicate, "agePredicate");
        return REGISTRY.register(id, registeredId -> new Personality(registeredId, agePredicate, REGISTRY.size()));
    }

    public static Optional<Personality> get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static Optional<Personality> get(String id) {
        return REGISTRY.get(id);
    }

    public static List<Personality> all() {
        return REGISTRY.all();
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
        return REGISTRY.translationSuffix(id);
    }

    /**
     * Escapes dots so a namespaced id can be embedded in MCA's dot-delimited dialogue flags.
     */
    public static String encodeDialogueId(ResourceLocation id) {
        return Objects.requireNonNull(id, "id").toString().replace(".", DIALOGUE_DOT_ESCAPE);
    }

    public static String getDialoguePrefix(String encodedId) {
        ResourceLocation parsed = REGISTRY.parse(decodeDialogueId(encodedId)).orElse(UNASSIGNED.id);
        return REGISTRY.translationSuffix(parsed);
    }

    public Component getName() {
        return Component.translatable("personality." + REGISTRY.translationSuffix(id));
    }

    public Component getDescription() {
        return Component.translatable("personalityDescription." + REGISTRY.translationSuffix(id));
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
        return Integer.compare(compatibilityOrdinal, other.compatibilityOrdinal);
    }

    @Override
    public String toString() {
        return id.toString();
    }

    private static Personality registerLegacyBuiltIn(String path) {
        return registerLegacyBuiltIn(path, ageState -> true);
    }

    private static Personality registerLegacyBuiltIn(String path, Predicate<AgeState> agePredicate) {
        Personality personality = register(MCA.locate(path), agePredicate);
        LEGACY_VALUES.add(personality);
        return personality;
    }

    private static String decodeDialogueId(String value) {
        return value == null ? null : value.replace(DIALOGUE_DOT_ESCAPE, ".");
    }

    private static boolean isOldEnoughToFlirt(AgeState ageState) {
        return ageState != AgeState.BABY && ageState != AgeState.TODDLER && ageState != AgeState.CHILD;
    }
}
