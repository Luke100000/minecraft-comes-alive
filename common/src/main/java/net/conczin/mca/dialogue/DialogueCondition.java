package net.conczin.mca.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.ai.LongTermMemory;
import net.conczin.mca.entity.ai.Relationship;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.conczin.mca.entity.ai.relationship.RelationshipState;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.Tasks;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable deterministic dialogue requirements.
 *
 * <p>Conditions answer only whether an event is eligible. Probability belongs to event/outcome
 * selection, not to this type. {@link Evaluation#UNAVAILABLE} is intentionally distinct from
 * {@link Evaluation#NO_MATCH}: a missing referenced datapack object must remain unavailable under
 * {@code mca:not} instead of becoming an accidental match.</p>
 */
public sealed interface DialogueCondition permits DialogueCondition.Defined, DialogueCondition.EventCompleted,
        DialogueCondition.EventChoice, DialogueCondition.Not {
    ResourceLocation NOT = MCA.locate("not");
    ResourceLocation EVENT_COMPLETED = MCA.locate("event_completed");
    ResourceLocation EVENT_CHOICE = MCA.locate("event_choice");

    ResourceLocation PERSONALITY = MCA.locate("personality");
    ResourceLocation MOOD = MCA.locate("mood");
    ResourceLocation HEARTS = MCA.locate("hearts");
    ResourceLocation RELATIONSHIP = MCA.locate("relationship");
    ResourceLocation FAMILY = MCA.locate("family");
    ResourceLocation AGE_GROUP = MCA.locate("age_group");
    ResourceLocation PROFESSION = MCA.locate("profession");
    ResourceLocation RANK = MCA.locate("rank");
    ResourceLocation TRAIT = MCA.locate("trait");
    ResourceLocation HEALTH = MCA.locate("health");
    ResourceLocation TIME = MCA.locate("time");
    ResourceLocation WEATHER = MCA.locate("weather");
    ResourceLocation BIOME = MCA.locate("biome");
    ResourceLocation ADVANCEMENT = MCA.locate("advancement");
    ResourceLocation VILLAGE_HAS_BUILDING = MCA.locate("village_has_building");
    ResourceLocation IN_BUILDING = MCA.locate("in_building");
    ResourceLocation GENDER = MCA.locate("gender");
    ResourceLocation PREGNANCY = MCA.locate("pregnancy");
    ResourceLocation INVENTORY = MCA.locate("inventory");
    ResourceLocation ITEM = MCA.locate("item");
    ResourceLocation TAG = MCA.locate("tag");
    ResourceLocation MEMORY = MCA.locate("memory");
    ResourceLocation BUILDING_ASSIGNMENT = MCA.locate("building_assignment");

    Set<ResourceLocation> BUILTIN_TYPES = Set.of(
            PERSONALITY, MOOD, HEARTS, RELATIONSHIP, FAMILY, AGE_GROUP, PROFESSION, RANK, TRAIT,
            HEALTH, TIME, WEATHER, BIOME, ADVANCEMENT, VILLAGE_HAS_BUILDING, IN_BUILDING,
            GENDER, PREGNANCY, INVENTORY, ITEM, TAG, MEMORY, BUILDING_ASSIGNMENT,
            EVENT_COMPLETED, EVENT_CHOICE, NOT
    );

    Set<String> MOODS = Set.of("depressed", "sad", "unhappy", "passive", "fine", "happy", "overjoyed");
    Set<String> RELATIONSHIPS = Set.of("spouse", "engaged", "promised", "romantic_partner", "single", "widow");
    Set<String> FAMILY_STATES = Set.of("family", "relative", "parent", "child", "orphan");
    Set<String> WEATHER_STATES = Set.of("clear", "rain", "thunder");
    Set<String> TIME_STATES = Set.of("day", "night");
    Set<String> ASSIGNMENT_SOURCES = Set.of("home", "workplace");

    Codec<DialogueCondition> CODEC = Codec.PASSTHROUGH.comapFlatMap(
            DialogueCondition::decodeDynamic,
            DialogueCondition::encodeDynamic
    );

    Map<ResourceLocation, DefinedEvaluator> EVALUATORS = Map.ofEntries(
            Map.entry(PERSONALITY, DialogueCondition::evaluatePersonality),
            Map.entry(MOOD, DialogueCondition::evaluateMood),
            Map.entry(HEARTS, DialogueCondition::evaluateHearts),
            Map.entry(RELATIONSHIP, DialogueCondition::evaluateRelationship),
            Map.entry(FAMILY, DialogueCondition::evaluateFamily),
            Map.entry(AGE_GROUP, DialogueCondition::evaluateAgeGroup),
            Map.entry(PROFESSION, DialogueCondition::evaluateProfession),
            Map.entry(RANK, DialogueCondition::evaluateRank),
            Map.entry(TRAIT, DialogueCondition::evaluateTrait),
            Map.entry(HEALTH, DialogueCondition::evaluateHealth),
            Map.entry(TIME, DialogueCondition::evaluateTime),
            Map.entry(WEATHER, DialogueCondition::evaluateWeather),
            Map.entry(BIOME, DialogueCondition::evaluateBiome),
            Map.entry(ADVANCEMENT, DialogueCondition::evaluateAdvancement),
            Map.entry(VILLAGE_HAS_BUILDING, DialogueCondition::evaluateVillageHasBuilding),
            Map.entry(IN_BUILDING, DialogueCondition::evaluateInBuilding),
            Map.entry(GENDER, DialogueCondition::evaluateGender),
            Map.entry(PREGNANCY, DialogueCondition::evaluatePregnancy),
            Map.entry(INVENTORY, DialogueCondition::evaluateInventory),
            Map.entry(ITEM, DialogueCondition::evaluateItem),
            Map.entry(TAG, DialogueCondition::evaluateTag),
            Map.entry(MEMORY, DialogueCondition::evaluateMemory),
            Map.entry(BUILDING_ASSIGNMENT, DialogueCondition::evaluateBuildingAssignment)
    );

    ResourceLocation type();

    Evaluation evaluate(DialogueContext context);

    default boolean matches(DialogueContext context) {
        return evaluate(context) == Evaluation.MATCH;
    }

    private static DataResult<DialogueCondition> decodeDynamic(Dynamic<?> dynamic) {
        JsonElement element = dynamic.convert(JsonOps.INSTANCE).getValue();
        if (!element.isJsonObject()) {
            return DataResult.error(() -> "Dialogue condition must be an object");
        }
        JsonObject object = element.getAsJsonObject();
        if (!object.has("type") || !object.get("type").isJsonPrimitive()) {
            return DataResult.error(() -> "Dialogue condition requires string field 'type'");
        }
        ResourceLocation type = ResourceLocation.tryParse(object.get("type").getAsString());
        if (type == null || !BUILTIN_TYPES.contains(type)) {
            return DataResult.error(() -> "Unknown dialogue condition type: " + object.get("type"));
        }

        try {
            if (EVENT_COMPLETED.equals(type)) {
                requireOnly(object, "type", "event");
                return DataResult.success(new EventCompleted(requiredLocation(object, "event")));
            }
            if (EVENT_CHOICE.equals(type)) {
                requireOnly(object, "type", "event", "choice");
                return DataResult.success(new EventChoice(
                        requiredLocation(object, "event"),
                        requiredNonblankString(object, "choice")
                ));
            }
            if (NOT.equals(type)) {
                requireOnly(object, "type", "condition");
                if (!object.has("condition")) {
                    throw new IllegalArgumentException("mca:not requires condition");
                }
                DialogueCondition child = CODEC.parse(JsonOps.INSTANCE, object.get("condition")).getOrThrow();
                return DataResult.success(new Not(child));
            }
            validateDefined(type, object);
            return DataResult.success(new Defined(type, object.deepCopy()));
        } catch (RuntimeException exception) {
            return DataResult.error(() -> Objects.toString(exception.getMessage(), exception.getClass().getSimpleName()));
        }
    }

    private static Dynamic<?> encodeDynamic(DialogueCondition condition) {
        JsonObject object;
        if (condition instanceof Defined defined) {
            object = defined.definition().deepCopy();
        } else {
            object = new JsonObject();
            object.addProperty("type", condition.type().toString());
        }
        if (condition instanceof EventCompleted completed) {
            object.addProperty("event", completed.event().toString());
        } else if (condition instanceof EventChoice choice) {
            object.addProperty("event", choice.event().toString());
            object.addProperty("choice", choice.choiceId());
        } else if (condition instanceof Not not) {
            JsonElement child = CODEC.encodeStart(JsonOps.INSTANCE, not.condition()).getOrThrow();
            object.add("condition", child);
        }
        return new Dynamic<>(JsonOps.INSTANCE, object);
    }

    private static void validateDefined(ResourceLocation type, JsonObject object) {
        if (PERSONALITY.equals(type)) {
            requireOnly(object, "type", "value");
            String value = requiredNonblankString(object, "value");
            Personality.get(value).orElseThrow(() -> new IllegalArgumentException("Unknown personality '" + value + "'"));
        } else if (MOOD.equals(type)) {
            requireOnly(object, "type", "value");
            requireAllowedValue(object, "value", MOODS);
        } else if (HEARTS.equals(type)) {
            requireOnly(object, "type", "min", "max");
            validateIntRange(object, "hearts", Integer.MIN_VALUE, Integer.MAX_VALUE);
        } else if (RELATIONSHIP.equals(type)) {
            requireOnly(object, "type", "value");
            requireAllowedValue(object, "value", RELATIONSHIPS);
        } else if (FAMILY.equals(type)) {
            requireOnly(object, "type", "value");
            requireAllowedValue(object, "value", FAMILY_STATES);
        } else if (AGE_GROUP.equals(type)) {
            requireOnly(object, "type", "value");
            requiredEnum(object, "value", AgeState.class);
        } else if (PROFESSION.equals(type)) {
            requireOnly(object, "type", "value");
            ResourceLocation profession = requiredLocation(object, "value");
            if (BuiltInRegistries.VILLAGER_PROFESSION.getOptional(profession).isEmpty()) {
                throw new IllegalArgumentException("Unknown villager profession '" + profession + "'");
            }
        } else if (RANK.equals(type)) {
            requireOnly(object, "type", "value");
            requiredEnum(object, "value", Rank.class);
        } else if (TRAIT.equals(type)) {
            requireOnly(object, "type", "value");
            String value = requiredNonblankString(object, "value");
            Traits.get(value).orElseThrow(() -> new IllegalArgumentException("Unknown trait '" + value + "'"));
        } else if (HEALTH.equals(type)) {
            requireOnly(object, "type", "min", "max");
            validateDoubleRange(object, "health", 0.0D, Double.MAX_VALUE);
        } else if (TIME.equals(type)) {
            requireOnly(object, "type", "value", "min", "max");
            boolean named = object.has("value");
            boolean ranged = object.has("min") || object.has("max");
            if (named == ranged) {
                throw new IllegalArgumentException("mca:time requires exactly one of value or min/max");
            }
            if (named) {
                requireAllowedValue(object, "value", TIME_STATES);
            } else {
                validateLongRange(object, "time", 0L, 24_000L);
            }
        } else if (WEATHER.equals(type)) {
            requireOnly(object, "type", "value");
            requireAllowedValue(object, "value", WEATHER_STATES);
        } else if (BIOME.equals(type) || ADVANCEMENT.equals(type)) {
            requireOnly(object, "type", "value");
            requiredLocation(object, "value");
        } else if (VILLAGE_HAS_BUILDING.equals(type) || IN_BUILDING.equals(type)) {
            requireOnly(object, "type", "value");
            requiredNonblankString(object, "value");
        } else if (GENDER.equals(type)) {
            requireOnly(object, "type", "value");
            requiredEnum(object, "value", Gender.class);
        } else if (PREGNANCY.equals(type)) {
            validatePregnancy(object);
        } else if (ITEM.equals(type) || TAG.equals(type)) {
            requireOnly(object, "type", "value", "min", "max");
            ResourceLocation selector = requiredLocation(object, "value");
            validateCountRange(object);
            if (ITEM.equals(type) && BuiltInRegistries.ITEM.getOptional(selector).isEmpty()) {
                throw new IllegalArgumentException("Unknown item '" + selector + "'");
            }
        } else if (INVENTORY.equals(type)) {
            validateInventory(object);
        } else if (MEMORY.equals(type)) {
            requireOnly(object, "type", "id", "var", "present");
            requiredNonblankString(object, "id");
            if (object.has("var") && !"player".equals(requiredNonblankString(object, "var"))) {
                throw new IllegalArgumentException("mca:memory var must be 'player'");
            }
            if (object.has("present")) requiredBoolean(object, "present");
        } else if (BUILDING_ASSIGNMENT.equals(type)) {
            requireOnly(object, "type", "value", "source");
            requiredNonblankString(object, "value");
            if (object.has("source")) requireAllowedValue(object, "source", ASSIGNMENT_SOURCES);
        } else {
            throw new IllegalArgumentException("No validator for dialogue condition type " + type);
        }
    }

    private static void validatePregnancy(JsonObject object) {
        requireOnly(object, "type", "value", "min_progress", "max_progress", "child_gender");
        boolean pregnant = requiredBoolean(object, "value");
        if (!pregnant && (object.has("min_progress") || object.has("max_progress") || object.has("child_gender"))) {
            throw new IllegalArgumentException("mca:pregnancy progress/child_gender requires value=true");
        }
        if (object.has("min_progress") || object.has("max_progress")) {
            validateIntRange(object, "pregnancy progress", 0, Integer.MAX_VALUE,
                    "min_progress", "max_progress");
        }
        if (object.has("child_gender")) requiredEnum(object, "child_gender", Gender.class);
    }

    private static void validateInventory(JsonObject object) {
        requireOnly(object, "type", "item", "tag", "min", "max");
        boolean hasItem = object.has("item");
        boolean hasTag = object.has("tag");
        if (hasItem == hasTag) {
            throw new IllegalArgumentException("mca:inventory requires exactly one of item or tag");
        }
        ResourceLocation selector = requiredLocation(object, hasItem ? "item" : "tag");
        if (hasItem && BuiltInRegistries.ITEM.getOptional(selector).isEmpty()) {
            throw new IllegalArgumentException("Unknown item '" + selector + "'");
        }
        validateCountRange(object);
    }

    private static Evaluation evaluateDefined(Defined condition, DialogueContext context) {
        DefinedEvaluator evaluator = EVALUATORS.get(condition.type());
        return evaluator == null ? Evaluation.UNAVAILABLE : evaluator.evaluate(condition, context);
    }

    private static Evaluation evaluatePersonality(Defined condition, DialogueContext context) {
        Optional<Personality> personality = Personality.get(requiredNonblankString(condition.definition(), "value"));
        return personality.map(value -> result(context.villager().getVillagerBrain().getPersonality() == value))
                .orElse(Evaluation.UNAVAILABLE);
    }

    private static Evaluation evaluateMood(Defined condition, DialogueContext context) {
        return result(context.villager().getVillagerBrain().getMood().getName()
                .equals(normalized(condition.definition(), "value")));
    }

    private static Evaluation evaluateHearts(Defined condition, DialogueContext context) {
        int hearts = context.villager().getVillagerBrain().getMemoriesForPlayer(context.player()).getHearts();
        return result(inIntRange(condition.definition(), hearts));
    }

    private static Evaluation evaluateRelationship(Defined condition, DialogueContext context) {
        boolean matches = switch (normalized(condition.definition(), "value")) {
            case "spouse" -> Relationship.IS_MARRIED.test(context.villager(), context.player());
            case "engaged" -> Relationship.IS_ENGAGED.test(context.villager(), context.player());
            case "promised" -> Relationship.IS_PROMISED.test(context.villager(), context.player());
            case "romantic_partner" -> Relationship.IS_ROMANTIC_PARTNER.test(context.villager(), context.player());
            case "single" -> context.villager().getRelationships().getRelationshipState() == RelationshipState.SINGLE;
            case "widow" -> context.villager().getRelationships().getRelationshipState() == RelationshipState.WIDOW;
            default -> false;
        };
        return result(matches);
    }

    private static Evaluation evaluateFamily(Defined condition, DialogueContext context) {
        boolean matches = switch (normalized(condition.definition(), "value")) {
            case "family" -> Relationship.IS_FAMILY.test(context.villager(), context.player());
            case "relative" -> Relationship.IS_RELATIVE.test(context.villager(), context.player());
            case "parent" -> Relationship.IS_KID.test(context.villager(), context.player());
            case "child" -> Relationship.IS_PARENT.test(context.villager(), context.player());
            case "orphan" -> Relationship.IS_ORPHAN.test(context.villager(), context.player());
            default -> false;
        };
        return result(matches);
    }

    private static Evaluation evaluateAgeGroup(Defined condition, DialogueContext context) {
        return result(context.villager().getAgeState() == requiredEnum(condition.definition(), "value", AgeState.class));
    }

    private static Evaluation evaluateProfession(Defined condition, DialogueContext context) {
        ResourceLocation expected = requiredLocation(condition.definition(), "value");
        if (BuiltInRegistries.VILLAGER_PROFESSION.getOptional(expected).isEmpty()) return Evaluation.UNAVAILABLE;
        return result(expected.equals(BuiltInRegistries.VILLAGER_PROFESSION.getKey(context.villager().getProfession())));
    }

    private static Evaluation evaluateRank(Defined condition, DialogueContext context) {
        Rank expected = requiredEnum(condition.definition(), "value", Rank.class);
        return context.village()
                .map(village -> result(Tasks.getRank(village, context.player()) == expected))
                .orElse(Evaluation.NO_MATCH);
    }

    private static Evaluation evaluateTrait(Defined condition, DialogueContext context) {
        Optional<Traits.Trait> trait = Traits.get(requiredNonblankString(condition.definition(), "value"));
        return trait.map(value -> result(context.villager().getTraits().hasTrait(value)))
                .orElse(Evaluation.UNAVAILABLE);
    }

    private static Evaluation evaluateHealth(Defined condition, DialogueContext context) {
        return result(inDoubleRange(condition.definition(), context.villager().getHealth()));
    }

    private static Evaluation evaluateTime(Defined condition, DialogueContext context) {
        JsonObject definition = condition.definition();
        if (definition.has("value")) {
            String value = normalized(definition, "value");
            return result("day".equals(value) ? !context.level().isNight() : context.level().isNight());
        }
        return result(inLongRange(definition, context.level().getDayTime() % 24_000L));
    }

    private static Evaluation evaluateWeather(Defined condition, DialogueContext context) {
        return result(switch (normalized(condition.definition(), "value")) {
            case "clear" -> !context.level().isRaining();
            case "rain" -> context.level().isRaining() && !context.level().isThundering();
            case "thunder" -> context.level().isThundering();
            default -> false;
        });
    }

    private static Evaluation evaluateBiome(Defined condition, DialogueContext context) {
        ResourceLocation expected = requiredLocation(condition.definition(), "value");
        if (context.level().registryAccess().registryOrThrow(Registries.BIOME).getOptional(expected).isEmpty()) {
            return Evaluation.UNAVAILABLE;
        }
        var biomeKey = context.level().getBiome(context.villager().blockPosition()).unwrapKey();
        return biomeKey.map(key -> result(key.location().equals(expected))).orElse(Evaluation.UNAVAILABLE);
    }

    private static Evaluation evaluateAdvancement(Defined condition, DialogueContext context) {
        ResourceLocation id = requiredLocation(condition.definition(), "value");
        AdvancementHolder advancement = Objects.requireNonNull(context.player().getServer()).getAdvancements().get(id);
        if (advancement == null) return Evaluation.UNAVAILABLE;
        return result(context.player().getAdvancements().getOrStartProgress(advancement).isDone());
    }

    private static Evaluation evaluateVillageHasBuilding(Defined condition, DialogueContext context) {
        String type = requiredNonblankString(condition.definition(), "value");
        if (!isKnownBuildingType(type)) return Evaluation.UNAVAILABLE;
        return result(context.village().filter(village -> village.hasBuilding(type)).isPresent());
    }

    private static Evaluation evaluateInBuilding(Defined condition, DialogueContext context) {
        String type = requiredNonblankString(condition.definition(), "value");
        if (!isKnownBuildingType(type)) return Evaluation.UNAVAILABLE;
        return result(context.village()
                .filter(village -> village.isInBuildingOfType(context.villager().blockPosition(), type))
                .isPresent());
    }

    private static Evaluation evaluateGender(Defined condition, DialogueContext context) {
        return result(context.villager().getGenetics().getGender()
                == requiredEnum(condition.definition(), "value", Gender.class));
    }

    private static Evaluation evaluatePregnancy(Defined condition, DialogueContext context) {
        JsonObject definition = condition.definition();
        var pregnancy = context.villager().getRelationships().getPregnancy();
        boolean expected = requiredBoolean(definition, "value");
        if (pregnancy.isPregnant() != expected) return Evaluation.NO_MATCH;
        if (!expected) return Evaluation.MATCH;
        if ((definition.has("min_progress") || definition.has("max_progress"))
                && !inIntRange(definition, pregnancy.getBabyAge(), "min_progress", "max_progress")) {
            return Evaluation.NO_MATCH;
        }
        if (definition.has("child_gender")
                && pregnancy.getGender() != requiredEnum(definition, "child_gender", Gender.class)) {
            return Evaluation.NO_MATCH;
        }
        return Evaluation.MATCH;
    }

    private static Evaluation evaluateItem(Defined condition, DialogueContext context) {
        ResourceLocation id = requiredLocation(condition.definition(), "value");
        Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
        return item.map(value -> result(inCountRange(condition.definition(), context.player().getInventory().countItem(value))))
                .orElse(Evaluation.UNAVAILABLE);
    }

    private static Evaluation evaluateTag(Defined condition, DialogueContext context) {
        return evaluateInventoryTag(condition.definition(), requiredLocation(condition.definition(), "value"), context);
    }

    private static Evaluation evaluateInventory(Defined condition, DialogueContext context) {
        JsonObject definition = condition.definition();
        if (definition.has("item")) {
            ResourceLocation id = requiredLocation(definition, "item");
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(id);
            return item.map(value -> result(inCountRange(definition, context.player().getInventory().countItem(value))))
                    .orElse(Evaluation.UNAVAILABLE);
        }
        return evaluateInventoryTag(definition, requiredLocation(definition, "tag"), context);
    }

    private static Evaluation evaluateInventoryTag(JsonObject definition, ResourceLocation id, DialogueContext context) {
        TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
        if (BuiltInRegistries.ITEM.getTag(tag).isEmpty()) return Evaluation.UNAVAILABLE;
        int count = countMatching(context.player().getInventory(), stack -> stack.is(tag));
        return result(inCountRange(definition, count));
    }

    private static Evaluation evaluateMemory(Defined condition, DialogueContext context) {
        JsonObject definition = condition.definition();
        String id = LongTermMemory.parseId(definition, context.player());
        boolean present = !definition.has("present") || requiredBoolean(definition, "present");
        return result(context.villager().getLongTermMemory().hasMemory(id) == present);
    }

    private static Evaluation evaluateBuildingAssignment(Defined condition, DialogueContext context) {
        JsonObject definition = condition.definition();
        String type = requiredNonblankString(definition, "value");
        if (!isKnownBuildingType(type)) return Evaluation.UNAVAILABLE;
        String source = definition.has("source") ? normalized(definition, "source") : "home";
        Optional<GlobalPos> assigned = "workplace".equals(source)
                ? context.villager().getBrain().getMemory(MemoryModuleType.JOB_SITE)
                : context.villager().getResidency().getHome();
        return result(assigned
                .filter(pos -> pos.dimension().equals(context.level().dimension()))
                .flatMap(pos -> context.village().map(village -> village.isInBuildingOfType(pos.pos(), type)))
                .orElse(false));
    }

    private static boolean isKnownBuildingType(String type) {
        return BuildingTypes.getInstance().getServerBuildingTypes().containsKey(type);
    }

    private static int countMatching(Container inventory, java.util.function.Predicate<ItemStack> predicate) {
        int count = 0;
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (predicate.test(stack)) count = Math.addExact(count, stack.getCount());
        }
        return count;
    }

    private static boolean inCountRange(JsonObject object, int value) {
        int min = object.has("min") ? requiredInt(object, "min") : 1;
        int max = object.has("max") ? requiredInt(object, "max") : Integer.MAX_VALUE;
        return value >= min && value <= max;
    }

    private static boolean inIntRange(JsonObject object, int value) {
        return inIntRange(object, value, "min", "max");
    }

    private static boolean inIntRange(JsonObject object, int value, String minField, String maxField) {
        int min = object.has(minField) ? requiredInt(object, minField) : Integer.MIN_VALUE;
        int max = object.has(maxField) ? requiredInt(object, maxField) : Integer.MAX_VALUE;
        return value >= min && value <= max;
    }

    private static boolean inLongRange(JsonObject object, long value) {
        long min = object.has("min") ? requiredLong(object, "min") : Long.MIN_VALUE;
        long max = object.has("max") ? requiredLong(object, "max") : Long.MAX_VALUE;
        return value >= min && value <= max;
    }

    private static boolean inDoubleRange(JsonObject object, double value) {
        double min = object.has("min") ? requiredDouble(object, "min") : -Double.MAX_VALUE;
        double max = object.has("max") ? requiredDouble(object, "max") : Double.MAX_VALUE;
        return value >= min && value <= max;
    }

    private static void validateCountRange(JsonObject object) {
        int min = object.has("min") ? requiredInt(object, "min") : 1;
        int max = object.has("max") ? requiredInt(object, "max") : Integer.MAX_VALUE;
        if (min < 0 || max < min) {
            throw new IllegalArgumentException("Inventory count requires 0 <= min <= max");
        }
    }

    private static void validateIntRange(JsonObject object, String label, int minimum, int maximum) {
        validateIntRange(object, label, minimum, maximum, "min", "max");
    }

    private static void validateIntRange(
            JsonObject object,
            String label,
            int minimum,
            int maximum,
            String minField,
            String maxField
    ) {
        if (!object.has(minField) && !object.has(maxField)) {
            throw new IllegalArgumentException("mca:" + label + " requires min and/or max");
        }
        int min = object.has(minField) ? requiredInt(object, minField) : minimum;
        int max = object.has(maxField) ? requiredInt(object, maxField) : maximum;
        if (min < minimum || max > maximum || min > max) {
            throw new IllegalArgumentException(label + " requires " + minimum + " <= min <= max <= " + maximum);
        }
    }

    private static void validateLongRange(JsonObject object, String label, long minimum, long maximum) {
        if (!object.has("min") && !object.has("max")) {
            throw new IllegalArgumentException("mca:" + label + " requires min and/or max");
        }
        long min = object.has("min") ? requiredLong(object, "min") : minimum;
        long max = object.has("max") ? requiredLong(object, "max") : maximum;
        if (min < minimum || max > maximum || min > max) {
            throw new IllegalArgumentException(label + " requires " + minimum + " <= min <= max <= " + maximum);
        }
    }

    private static void validateDoubleRange(JsonObject object, String label, double minimum, double maximum) {
        if (!object.has("min") && !object.has("max")) {
            throw new IllegalArgumentException("mca:" + label + " requires min and/or max");
        }
        double min = object.has("min") ? requiredDouble(object, "min") : minimum;
        double max = object.has("max") ? requiredDouble(object, "max") : maximum;
        if (!Double.isFinite(min) || !Double.isFinite(max) || min < minimum || max > maximum || min > max) {
            throw new IllegalArgumentException(label + " requires finite " + minimum + " <= min <= max");
        }
    }

    private static ResourceLocation requiredLocation(JsonObject object, String field) {
        String value = requiredNonblankString(object, field);
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException("Invalid ResourceLocation in '" + field + "': " + value);
        }
        return id;
    }

    private static String requiredNonblankString(JsonObject object, String field) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()) {
            throw new IllegalArgumentException("Missing string field '" + field + "'");
        }
        String value = object.get(field).getAsString();
        if (value.isBlank()) {
            throw new IllegalArgumentException("Blank string field '" + field + "'");
        }
        return value;
    }

    private static String normalized(JsonObject object, String field) {
        return requiredNonblankString(object, field).toLowerCase(Locale.ENGLISH);
    }

    private static boolean requiredBoolean(JsonObject object, String field) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()
                || !object.get(field).getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Missing boolean field '" + field + "'");
        }
        return object.get(field).getAsBoolean();
    }

    private static int requiredInt(JsonObject object, String field) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()
                || !object.get(field).getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Missing integer field '" + field + "'");
        }
        return GsonHelper.convertToInt(object.get(field), field);
    }

    private static long requiredLong(JsonObject object, String field) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()
                || !object.get(field).getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Missing long field '" + field + "'");
        }
        return GsonHelper.convertToLong(object.get(field), field);
    }

    private static double requiredDouble(JsonObject object, String field) {
        if (!object.has(field) || !object.get(field).isJsonPrimitive()
                || !object.get(field).getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Missing number field '" + field + "'");
        }
        return object.get(field).getAsDouble();
    }

    private static <E extends Enum<E>> E requiredEnum(JsonObject object, String field, Class<E> type) {
        String value = requiredNonblankString(object, field);
        try {
            return Enum.valueOf(type, value.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown " + field + " '" + value + "'");
        }
    }

    private static void requireAllowedValue(JsonObject object, String field, Set<String> allowed) {
        String value = normalized(object, field);
        if (!allowed.contains(value)) throw new IllegalArgumentException("Unknown " + field + " '" + value + "'");
    }

    private static void requireOnly(JsonObject object, String... allowedFields) {
        Set<String> allowed = Set.of(allowedFields);
        for (String field : object.keySet()) {
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException("Unknown dialogue condition field '" + field + "'");
            }
        }
    }

    private static Evaluation result(boolean matches) {
        return matches ? Evaluation.MATCH : Evaluation.NO_MATCH;
    }

    enum Evaluation {
        MATCH,
        NO_MATCH,
        UNAVAILABLE;

        Evaluation negate() {
            return switch (this) {
                case MATCH -> NO_MATCH;
                case NO_MATCH -> MATCH;
                case UNAVAILABLE -> UNAVAILABLE;
            };
        }
    }

    @FunctionalInterface
    interface DefinedEvaluator {
        Evaluation evaluate(Defined condition, DialogueContext context);
    }

    record Defined(ResourceLocation type, JsonObject definition) implements DialogueCondition {
        public Defined {
            definition = definition.deepCopy();
        }

        @Override
        public JsonObject definition() {
            return definition.deepCopy();
        }

        @Override
        public Evaluation evaluate(DialogueContext context) {
            return evaluateDefined(this, context);
        }
    }

    record EventCompleted(ResourceLocation event) implements DialogueCondition {
        @Override
        public ResourceLocation type() {
            return EVENT_COMPLETED;
        }

        @Override
        public Evaluation evaluate(DialogueContext context) {
            if (!context.isEventAvailable(event)) return Evaluation.UNAVAILABLE;
            return result(context.history().completed(context.player().getUUID(), context.villager().getUUID(), event));
        }
    }

    record EventChoice(ResourceLocation event, String choiceId) implements DialogueCondition {
        @Override
        public ResourceLocation type() {
            return EVENT_CHOICE;
        }

        @Override
        public Evaluation evaluate(DialogueContext context) {
            if (!context.isEventAvailable(event)) return Evaluation.UNAVAILABLE;
            return result(context.history().chose(
                    context.player().getUUID(), context.villager().getUUID(), event, choiceId));
        }
    }

    record Not(DialogueCondition condition) implements DialogueCondition {
        @Override
        public ResourceLocation type() {
            return NOT;
        }

        @Override
        public Evaluation evaluate(DialogueContext context) {
            return condition.evaluate(context).negate();
        }
    }
}
