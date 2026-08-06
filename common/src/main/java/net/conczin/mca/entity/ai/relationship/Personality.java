package net.conczin.mca.entity.ai.relationship;

import net.conczin.mca.MCA;
import net.conczin.mca.util.ExtensibleTypeRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Extensible villager personality type.
 *
 * <p>Addons should register personalities during mod initialization using a
 * namespaced {@link ResourceLocation}. Addons that need custom behaviour can
 * subclass this type and register the subtype through {@link #register(Personality)}.
 * The identifier is also the stable value used for persistence and client synchronization.</p>
 */
public class Personality implements Comparable<Personality> {
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

    protected Personality(@NotNull ResourceLocation id, @NotNull Predicate<AgeState> agePredicate) {
        this.id = id;
        this.agePredicate = agePredicate;
    }

    /**
     * Registers an unrestricted base personality.
     */
    public static @NotNull Personality register(@NotNull ResourceLocation id) {
        return register(new Personality(id, ageState -> true));
    }

    /**
     * Registers a base personality with an age eligibility rule.
     */
    public static @NotNull Personality register(
            @NotNull ResourceLocation id,
            @NotNull Predicate<AgeState> agePredicate
    ) {
        return register(new Personality(id, agePredicate));
    }

    /**
     * Registers a personality instance, including addon-defined subtypes, in MCA's shared registry.
     */
    public static <T extends Personality> @NotNull T register(@NotNull T personality) {
        return REGISTRY.register(personality.getId(), registeredId -> personality);
    }

    public static @NotNull Optional<Personality> get(@NotNull ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static @NotNull Optional<Personality> get(@Nullable String id) {
        return REGISTRY.get(id);
    }

    public static @NotNull List<Personality> all() {
        return REGISTRY.all();
    }

    public static @NotNull Personality getRandom() {
        return getRandom(AgeState.ADULT);
    }

    public static @NotNull Personality getRandom(@NotNull AgeState ageState) {
        List<Personality> valid = new ArrayList<>();
        for (Personality personality : all()) {
            if (personality != UNASSIGNED && personality.isValidFor(ageState)) {
                valid.add(personality);
            }
        }
        return valid.get(RANDOM.nextInt(valid.size()));
    }

    public static @NotNull Personality byLegacyOrdinal(int ordinal) {
        return ordinal >= 0 && ordinal < LEGACY_VALUES.size() ? LEGACY_VALUES.get(ordinal) : UNASSIGNED;
    }

    public @NotNull ResourceLocation getId() {
        return id;
    }

    public boolean isValidFor(@NotNull AgeState ageState) {
        return agePredicate.test(ageState);
    }

    public @NotNull String getDialoguePrefix() {
        return REGISTRY.translationSuffix(id);
    }

    /**
     * Escapes dots so a namespaced id can be embedded in MCA's dot-delimited dialogue flags.
     */
    public static @NotNull String encodeDialogueId(@NotNull ResourceLocation id) {
        return id.toString().replace(".", DIALOGUE_DOT_ESCAPE);
    }

    public static @NotNull String getDialoguePrefix(@Nullable String encodedId) {
        ResourceLocation parsed = REGISTRY.parse(decodeDialogueId(encodedId)).orElse(UNASSIGNED.id);
        return REGISTRY.translationSuffix(parsed);
    }

    public @NotNull Component getName() {
        return Component.translatable("personality." + REGISTRY.translationSuffix(id));
    }

    public @NotNull Component getDescription() {
        return Component.translatable("personalityDescription." + REGISTRY.translationSuffix(id));
    }

    @Override
    public int compareTo(@NotNull Personality other) {
        return id.compareTo(other.id);
    }

    @Override
    public @NotNull String toString() {
        return id.toString();
    }

    private static @NotNull Personality registerLegacyBuiltIn(@NotNull String path) {
        return registerLegacyBuiltIn(path, ageState -> true);
    }

    private static @NotNull Personality registerLegacyBuiltIn(
            @NotNull String path,
            @NotNull Predicate<AgeState> agePredicate
    ) {
        Personality personality = register(MCA.locate(path), agePredicate);
        LEGACY_VALUES.add(personality);
        return personality;
    }

    private static @Nullable String decodeDialogueId(@Nullable String value) {
        return value == null ? null : value.replace(DIALOGUE_DOT_ESCAPE, ".");
    }

    private static boolean isOldEnoughToFlirt(@NotNull AgeState ageState) {
        return ageState != AgeState.BABY && ageState != AgeState.TODDLER && ageState != AgeState.CHILD;
    }
}
