package net.mca.entity.ai.relationship;

import net.mca.MCA;
import net.mca.util.ExtensibleTypeRegistry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.random.Random;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Extensible villager personality type backed by a stable namespaced identifier.
 */
public class Personality {
    private static final String DIALOGUE_DOT_ESCAPE = "%2E";
    private static final ExtensibleTypeRegistry<Personality> REGISTRY = new ExtensibleTypeRegistry<>(MCA.MOD_ID, "personality");
    private static final Random RANDOM = Random.create();

    public static final Personality UNASSIGNED = registerBuiltIn("unassigned");

    // Keep the 1.20.1 personalities that have no clear 1.21.1 successor as
    // normal rollable personalities. Their dialogue packs are still present.
    public static final Personality CONFIDENT = registerBuiltIn("confident");
    public static final Personality PEPPY = registerBuiltIn("peppy");

    public static final Personality FRIENDLY = registerBuiltIn("friendly");
    public static final Personality FLIRTY = registerBuiltIn("flirty", Personality::isOldEnoughToFlirt);
    public static final Personality PLAYFUL = registerBuiltIn("playful");
    public static final Personality GLOOMY = registerBuiltIn("gloomy");
    public static final Personality SENSITIVE = registerBuiltIn("sensitive");
    public static final Personality GREEDY = registerBuiltIn("greedy");
    public static final Personality ODD = registerBuiltIn("odd");
    public static final Personality CRABBY = registerBuiltIn("crabby");
    public static final Personality EXTROVERTED = registerBuiltIn("extroverted");
    public static final Personality INTROVERTED = registerBuiltIn("introverted");
    public static final Personality RELAXED = registerBuiltIn("relaxed");
    public static final Personality ANXIOUS = registerBuiltIn("anxious");
    public static final Personality PEACEFUL = registerBuiltIn("peaceful");
    public static final Personality UPBEAT = registerBuiltIn("upbeat");

    private final Identifier id;
    private final Predicate<AgeState> agePredicate;

    protected Personality(Identifier id, Predicate<AgeState> agePredicate) {
        this.id = Objects.requireNonNull(id, "id");
        this.agePredicate = Objects.requireNonNull(agePredicate, "agePredicate");
    }

    public static Personality register(Identifier id) {
        return register(new Personality(id, ageState -> true));
    }

    public static Personality register(Identifier id, Predicate<AgeState> agePredicate) {
        return register(new Personality(id, agePredicate));
    }

    public static <T extends Personality> T register(T personality) {
        Objects.requireNonNull(personality, "personality");
        return REGISTRY.register(personality.getId(), registeredId -> personality);
    }

    public static Optional<Personality> get(Identifier id) {
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

    public Identifier getId() {
        return id;
    }

    public boolean isValidFor(AgeState ageState) {
        return agePredicate.test(ageState);
    }

    public String getDialoguePrefix() {
        return REGISTRY.translationSuffix(id);
    }

    public static String encodeDialogueId(Identifier id) {
        return id.toString().replace(".", DIALOGUE_DOT_ESCAPE);
    }

    public static String getDialoguePrefix(@Nullable String encodedId) {
        Identifier parsed = REGISTRY.parse(decodeDialogueId(encodedId)).orElse(UNASSIGNED.id);
        return REGISTRY.translationSuffix(parsed);
    }

    public Text getName() {
        return Text.translatable("personality." + REGISTRY.translationSuffix(id));
    }

    public Text getDescription() {
        return Text.translatable("personalityDescription." + REGISTRY.translationSuffix(id));
    }

    @Override
    public String toString() {
        return id.toString();
    }

    private static Personality registerBuiltIn(String path) {
        return registerBuiltIn(path, ageState -> true);
    }

    private static Personality registerBuiltIn(String path, Predicate<AgeState> agePredicate) {
        return register(MCA.locate(path), agePredicate);
    }

    private static @Nullable String decodeDialogueId(@Nullable String value) {
        return value == null ? null : value.replace(DIALOGUE_DOT_ESCAPE, ".");
    }

    private static boolean isOldEnoughToFlirt(AgeState ageState) {
        return ageState != AgeState.BABY && ageState != AgeState.TODDLER && ageState != AgeState.CHILD;
    }
}
