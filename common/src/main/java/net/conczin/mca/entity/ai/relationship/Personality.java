package net.conczin.mca.entity.ai.relationship;

import net.conczin.mca.MCA;
import net.conczin.mca.util.ExtensibleTypeRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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
public class Personality {
    private static final String DIALOGUE_DOT_ESCAPE = "%2E";
    private static final ExtensibleTypeRegistry<Personality> REGISTRY = new ExtensibleTypeRegistry<>(MCA.MOD_ID, "personality");
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

    protected Personality(ResourceLocation id, Predicate<AgeState> agePredicate) {
        this.id = Objects.requireNonNull(id, "id");
        this.agePredicate = Objects.requireNonNull(agePredicate, "agePredicate");
    }

    /**
     * Registers an unrestricted base personality.
     */
    public static Personality register(ResourceLocation id) {
        return register(new Personality(id, ageState -> true));
    }

    /**
     * Registers a base personality with an age eligibility rule.
     */
    public static Personality register(
            ResourceLocation id,
            Predicate<AgeState> agePredicate
    ) {
        return register(new Personality(id, agePredicate));
    }

    /**
     * Registers a personality instance, including addon-defined subtypes, in MCA's shared registry.
     */
    public static <T extends Personality> T register(T personality) {
        Objects.requireNonNull(personality, "personality");
        return REGISTRY.register(personality.getId(), registeredId -> personality);
    }

    public static Optional<Personality> get(ResourceLocation id) {
        return REGISTRY.get(id);
    }

    public static Optional<Personality> get(@Nullable String id) {
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
        return id.toString().replace(".", DIALOGUE_DOT_ESCAPE);
    }

    public static String getDialoguePrefix(@Nullable String encodedId) {
        ResourceLocation parsed = REGISTRY.parse(decodeDialogueId(encodedId)).orElse(UNASSIGNED.id);
        return REGISTRY.translationSuffix(parsed);
    }

    public Component getName() {
        return Component.translatable("personality." + REGISTRY.translationSuffix(id));
    }

    public Component getDescription() {
        return Component.translatable("personalityDescription." + REGISTRY.translationSuffix(id));
    }

    @Override
    public String toString() {
        return id.toString();
    }

    private static Personality registerBuiltIn(String path) {
        return registerBuiltIn(path, ageState -> true);
    }

    private static Personality registerBuiltIn(
            String path,
            Predicate<AgeState> agePredicate
    ) {
        return register(MCA.locate(path), agePredicate);
    }

    private static @Nullable String decodeDialogueId(@Nullable String value) {
        return value == null ? null : value.replace(DIALOGUE_DOT_ESCAPE, ".");
    }

    private static boolean isOldEnoughToFlirt(AgeState ageState) {
        return ageState != AgeState.BABY && ageState != AgeState.TODDLER && ageState != AgeState.CHILD;
    }
}
