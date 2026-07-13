package net.conczin.mca.entity.interaction.gifts;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.LongTermMemory;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.conczin.mca.entity.interaction.Constraint;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.Tasks;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.function.BiFunction;

public class GiftPredicate {
    public static final Map<String, Factory<JsonElement>> CONDITION_TYPES = new HashMap<>();

    static {
        register("profession", (json, name) ->
                ResourceLocation.parse(GsonHelper.convertToString(json, name)), profession -> (villager, stack, player) -> BuiltInRegistries.VILLAGER_PROFESSION.getKey(villager.getProfession()).equals(profession) ? 1.0f : 0.0f);
        register("age_group", (json, name) ->
                AgeState.valueOf(GsonHelper.convertToString(json, name).toUpperCase(Locale.ENGLISH)), group -> (villager, stack, player) -> villager.getAgeState() == group ? 1.0f : 0.0f);
        register("gender", (json, name) ->
                Gender.valueOf(GsonHelper.convertToString(json, name).toUpperCase(Locale.ENGLISH)), gender -> (villager, stack, player) -> villager.getGenetics().getGender() == gender ? 1.0f : 0.0f);
        register("min_health", GsonHelper::convertToFloat, health ->
                (villager, stack, player) ->
                        villager.getHealth() > health ? 1.0f : 0.0f
        );
        register("is_married", GsonHelper::convertToBoolean, married ->
                (villager, stack, player) ->
                        villager.getRelationships().isMarried() == married ? 1.0f : 0.0f
        );
        register("has_home", GsonHelper::convertToBoolean, hasHome ->
                (villager, stack, player) ->
                        villager.getResidency().getHome().isPresent() == hasHome ? 1.0f : 0.0f
        );
        register("has_village", GsonHelper::convertToBoolean, hasVillage ->
                (villager, stack, player) ->
                        villager.getResidency().getHomeVillage().isPresent() == hasVillage ? 1.0f : 0.0f
        );
        register("min_infection_progress", GsonHelper::convertToFloat, progress ->
                (villager, stack, player) ->
                        villager.getInfectionProgress() > progress ? 1.0f : 0.0f
        );
        register("mood", (json, name) ->
                GsonHelper.convertToString(json, name).toLowerCase(Locale.ENGLISH), mood ->
                (villager, stack, player) ->
                        villager.getVillagerBrain().getMood().getName().equals(mood) ? 1.0f : 0.0f
        );
        register("personality", (json, name) ->
                Personality.valueOf(GsonHelper.convertToString(json, name).toUpperCase(Locale.ENGLISH)), personality ->
                (villager, stack, player) ->
                        villager.getVillagerBrain().getPersonality() == personality ? 1.0f : 0.0f
        );
        register("is_pregnant", GsonHelper::convertToBoolean, pregnant ->
                (villager, stack, player) ->
                        villager.getRelationships().getPregnancy().isPregnant() == pregnant ? 1.0f : 0.0f
        );
        register("min_pregnancy_progress", GsonHelper::convertToInt, progress ->
                (villager, stack, player) ->
                        villager.getRelationships().getPregnancy().getBabyAge() > progress ? 1.0f : 0.0f
        );
        register("pregnancy_child_gender", (json, name) ->
                Gender.valueOf(GsonHelper.convertToString(json, name).toUpperCase(Locale.ENGLISH)), gender ->
                (villager, stack, player) ->
                        villager.getRelationships().getPregnancy().getGender() == gender ? 1.0f : 0.0f
        );
        register("current_chore", (json, name) ->
                Chore.valueOf(GsonHelper.convertToString(json, name).toUpperCase(Locale.ENGLISH)), chore ->
                (villager, stack, player) ->
                        villager.getVillagerBrain().getCurrentJob() == chore ? 1.0f : 0.0f
        );
        register("item", (json, name) -> {
            ResourceLocation id = ResourceLocation.parse(GsonHelper.convertToString(json, name));
            Item item = BuiltInRegistries.ITEM.getOptional(id).orElseThrow(() -> new JsonSyntaxException("Unknown item '" + id + "'"));
            return Ingredient.of(new ItemStack(item));
        }, (Ingredient ingredient) -> (villager, stack, player) -> ingredient.test(stack) ? 1.0f : 0.0f);
        register("tag", (json, name) -> {
            ResourceLocation id = ResourceLocation.parse(GsonHelper.convertToString(json, name));
            TagKey<Item> tag = TagKey.create(Registries.ITEM, id);
            if (tag == null) {
                throw new JsonSyntaxException("Unknown item tag '" + id + "'");
            }

            return Ingredient.of(tag);
        }, (Ingredient ingredient) -> (villager, stack, player) -> ingredient.test(stack) ? 1.0f : 0.0f);
        register("trait", (json, name) -> {
            String id = GsonHelper.convertToString(json, name).toLowerCase(Locale.ENGLISH);
            Traits.Trait trait = Traits.Trait.valueOf(id);
            if (trait == null) {
                throw new JsonSyntaxException("Unknown trait '" + id + "'");
            }
            return trait;
        }, trait ->
                (villager, stack, player) ->
                        villager.getTraits().hasTrait(trait) ? 1.0f : 0.0f
        );
        register("hearts_min", GsonHelper::convertToInt, hearts -> (villager, stack, player) -> {
            assert player != null;
            int h = villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts();
            return h >= hearts ? 1.0f : 0.0f;
        });
        register("hearts_max", GsonHelper::convertToInt, hearts -> (villager, stack, player) -> {
            assert player != null;
            int h = villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts();
            return h <= hearts ? 1.0f : 0.0f;
        });
        register("hearts", GsonHelper::convertToJsonObject, json -> (villager, stack, player) -> {
            assert player != null;
            int h = villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts();
            return divideAndAdd(json, h);
        });
        register("memory", GsonHelper::convertToJsonObject, json -> (villager, stack, player) -> {
            String id = LongTermMemory.parseId(json, player);
            long ticks = villager.getLongTermMemory().getMemory(id);
            return divideAndAdd(json, ticks);
        });
        register("emeralds", GsonHelper::convertToInt, amount -> (villager, stack, player) -> (player != null && player.getInventory().countItem(Items.EMERALD) >= amount) ? 1.0f : 0.0f);
        register("village_has_building", GsonHelper::convertToString, name ->
                (villager, stack, player) ->
                        villager.getResidency().getHomeVillage().filter(v -> v.hasBuilding(name)).isPresent() ? 1.0f : 0.0f
        );
        register("rank", GsonHelper::convertToString, name ->
                (villager, stack, player) ->
                        villager.getResidency().getHomeVillage().filter(v -> Tasks.getRank(v, player) == Rank.fromName(name)).isPresent() ? 1.0f : 0.0f
        );
        register("time_min", GsonHelper::convertToLong, time ->
                (villager, stack, player) -> villager.level().getDayTime() % 24000L >= time ? 1.0f : 0.0f);
        register("time_max", GsonHelper::convertToLong, time ->
                (villager, stack, player) -> villager.level().getDayTime() % 24000L <= time ? 1.0f : 0.0f);
        register("biome", (json, name) ->
                ResourceLocation.parse(GsonHelper.convertToString(json, name)), biome ->
                (villager, stack, player) ->
                        villager.level().getBiome(villager.blockPosition())
                                .unwrap().left().filter(b -> b.location().equals(biome)).isPresent() ? 1.0f : 0.0f
        );
        register("advancement", (json, name) -> ResourceLocation.parse(GsonHelper.convertToString(json, name)), id -> (villager, stack, player) -> {
            assert player != null;
            AdvancementHolder advancement = Objects.requireNonNull(player.getServer()).getAdvancements().get(id);
            return (advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone()) ? 1.0f : 0.0f;
        });
        register("constraints", (json, name) -> Constraint.fromStringList(GsonHelper.convertToString(json, name)), constraints -> (villager, stack, player) -> {
            Set<Constraint> c = Constraint.allMatching(villager, player);
            return c.containsAll(constraints) ? 1.0f : 0.0f;
        });
    }

    private final int satisfactionBoost;
    @Nullable
    private final Condition condition;
    List<String> conditionKeys;

    public GiftPredicate(int satisfactionBoost, @Nullable Condition condition, List<String> conditionKeys) {
        this.satisfactionBoost = satisfactionBoost;
        this.condition = condition;
        this.conditionKeys = conditionKeys;
    }

    public static float divideAndAdd(JsonObject json, long value) {
        return Mth.clamp(
                value
                / (json.has("dividend") ? json.get("dividend").getAsFloat() : 1.0f)
                + (json.has("add") ? json.get("add").getAsFloat() : 0.0f),
                0.0f,
                json.has("max") ? json.get("max").getAsFloat() : 1.0f
        );
    }

    public static <T> void register(String name, BiFunction<JsonElement, String, T> jsonParser, Factory<T> predicate) {
        CONDITION_TYPES.put(name, json -> predicate.parse(jsonParser.apply(json, name)));
    }

    public static GiftPredicate fromJson(JsonObject json) {
        int satisfaction = 0;
        @Nullable
        Condition condition = null;
        List<String> conditionKeys = new LinkedList<>();

        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if ("satisfaction_boost".equals(entry.getKey())) {
                satisfaction = GsonHelper.convertToInt(entry.getValue(), entry.getKey());
            } else if (CONDITION_TYPES.containsKey(entry.getKey())) {
                Condition parsed = CONDITION_TYPES.get(entry.getKey()).parse(entry.getValue());
                conditionKeys.add(entry.getKey());
                if (condition == null) {
                    condition = parsed;
                } else {
                    condition = condition.and(parsed);
                }
            }
        }

        return new GiftPredicate(satisfaction, condition, conditionKeys);
    }

    public float test(VillagerEntityMCA recipient, ItemStack stack, @Nullable ServerPlayer player) {
        return condition != null ? condition.test(recipient, stack, player) : 0.0f;
    }

    public int getSatisfactionFor(VillagerEntityMCA recipient, ItemStack stack, @Nullable ServerPlayer player) {
        return (int) (test(recipient, stack, player) * satisfactionBoost);
    }

    public List<String> getConditionKeys() {
        return conditionKeys;
    }

    public interface Factory<T> {
        Condition parse(T value);
    }

    public interface Condition {
        float test(VillagerEntityMCA villager, ItemStack stack, @Nullable ServerPlayer player);

        default Condition and(Condition b) {
            final Condition a = this;
            return (villager, stack, player) -> a.test(villager, stack, player) * b.test(villager, stack, player);
        }
    }
}
