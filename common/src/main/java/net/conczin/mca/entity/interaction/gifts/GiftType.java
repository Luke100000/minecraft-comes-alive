package net.conczin.mca.entity.interaction.gifts;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.resources.data.Analysis;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GiftType {
    static final List<GiftType> REGISTRY = new ArrayList<>();
    private final Identifier id;
    private final List<GiftPredicate> conditions;
    private final Map<Item, Integer> items;
    private final Map<TagKey<Item>, Integer> tags;
    private final Map<Response, String> responses;
    private int priority;
    private int fail;
    private int good;
    private int better;

    public GiftType(Item item, int satisfaction, Identifier extendFrom) {
        this(item, satisfaction, getDefaultDialogues());
        Optional<GiftType> type = getGiftType(extendFrom);
        type.ifPresent(this::extendFrom);
    }
    public GiftType(Item item, int satisfaction, Map<Response, String> responses) {
        this(
                BuiltInRegistries.ITEM.getKey(item),
                0,
                new LinkedList<>(),
                Collections.singletonMap(item, satisfaction),
                Collections.emptyMap(),
                0, 10, 20,
                responses
        );
    }
    public GiftType(Identifier id, int priority, List<GiftPredicate> conditions, Map<Item, Integer> items, Map<TagKey<Item>, Integer> tags, int fail, int good, int better, Map<Response, String> responses) {
        this.id = id;
        this.priority = priority;
        this.conditions = conditions;
        this.items = items;
        this.tags = tags;
        this.fail = fail;
        this.good = good;
        this.better = better;
        this.responses = responses;
    }

    public static GiftType fromJson(Identifier id, JsonObject json) {
        List<GiftPredicate> conditions = new ArrayList<>();
        GsonHelper.getAsJsonArray(json, "conditions", new JsonArray()).forEach(element -> {
            conditions.add(GiftPredicate.fromJson(GsonHelper.convertToJsonObject(element, "condition")));
        });

        HashMap<Item, Integer> items = new HashMap<>();
        HashMap<TagKey<Item>, Integer> tags = new HashMap<>();
        GsonHelper.getAsJsonObject(json, "items").entrySet().forEach(element -> {
            String string = element.getKey();
            Integer satisfaction = element.getValue().getAsInt();
            if (string.charAt(0) == '#') {
                Identifier identifier = Identifier.parse(string.substring(1));
                TagKey<Item> tag = TagKey.create(Registries.ITEM, identifier);
                tags.put(tag, satisfaction);
            } else {
                Identifier identifier = Identifier.parse(string);
                Optional<Item> item = BuiltInRegistries.ITEM.getOptional(identifier);
                if (item.isPresent()) {
                    items.put(item.get(), satisfaction);
                } else if (identifier.getNamespace().equals(MCA.MOD_ID)) {
                    throw new JsonSyntaxException("Unknown item '" + identifier + "'");
                }
            }
        });

        int priority = GsonHelper.getAsInt(json, "priority", 0);

        JsonObject thresholds = GsonHelper.getAsJsonObject(json, "thresholds", new JsonObject());
        int fail = GsonHelper.getAsInt(thresholds, "fail", 0);
        int good = GsonHelper.getAsInt(thresholds, "good", 10);
        int better = GsonHelper.getAsInt(thresholds, "better", 20);

        JsonObject responsesJson = GsonHelper.getAsJsonObject(json, "responses", new JsonObject());
        Map<Response, String> responses = Stream.of(Response.values()).collect(Collectors.toMap(
                Function.identity(),
                response -> GsonHelper.getAsString(responsesJson, response.name().toLowerCase(Locale.ENGLISH), response.getDefaultDialogue())
        ));

        return new GiftType(id, priority, conditions, items, tags, fail, good, better, responses);
    }

    public static Stream<GiftType> allMatching(ItemStack stack) {
        return REGISTRY.stream().filter(type -> type.matches(stack));
    }

    /**
     * returns the giftType with the highest priority
     * if at least one gift fails, it chooses only from the failed gifts
     */
    public static Optional<GiftType> bestMatching(VillagerEntityMCA recipient, ItemStack stack, ServerPlayer player) {
        int max = GiftType.allMatching(stack).mapToInt(a -> a.priority).max().orElse(0);
        Optional<GiftType> worst = GiftType.allMatching(stack)
                .filter(a -> a.priority == max)
                .filter(a -> a.getResponse(a.getSatisfactionFor(recipient, stack, player).getTotal()) == Response.FAIL)
                .max(Comparator.comparingDouble(a -> a.getSatisfactionFor(recipient, stack, player).getTotal()));

        if (worst.isPresent()) {
            return worst;
        } else {
            return GiftType.allMatching(stack)
                    .filter(a -> a.priority == max)
                    .max(Comparator.comparingDouble(a -> a.getSatisfactionFor(recipient, stack, player).getTotal()));
        }
    }

    public static Optional<GiftType> getGiftType(Identifier id) {
        return REGISTRY.stream().filter(p -> p.id.equals(id)).findFirst();
    }

    private static Map<Response, String> getDefaultDialogues() {
        return Arrays.stream(Response.values()).collect(Collectors.toMap(r -> r, Response::getDefaultDialogue));
    }

    public Identifier getId() {
        return id;
    }

    public List<GiftPredicate> getConditions() {
        return conditions;
    }

    public Map<Response, String> getResponses() {
        return responses;
    }

    /**
     * Checks whether the given item counts for this type of gift.
     */
    public boolean matches(ItemStack stack) {
        return items.keySet().stream().anyMatch(i -> i == stack.getItem()) || tags.keySet().stream().anyMatch(stack::is);
    }

    /**
     * Gets the amount of satisfaction giving this gift to a villager would produce.
     *
     * @return An analysis object of all summands
     */
    public Analysis getSatisfactionFor(VillagerEntityMCA recipient, ItemStack stack, ServerPlayer player) {
        Analysis analysis = new Analysis();

        Optional<Integer> value = items.entrySet().stream().filter(i -> i.getKey() == stack.getItem()).findFirst().map(Map.Entry::getValue);
        int base = value.orElseGet(() -> tags.entrySet().stream().filter(i -> stack.is(i.getKey())).findFirst().map(Map.Entry::getValue).orElse(0));

        analysis.add("base", base);

        // condition chance
        for (GiftPredicate c : conditions) {
            int val = c.getSatisfactionFor(recipient, stack, player);
            if (c.test(recipient, stack, player) > 0.0f) {
                analysis.add(c.getConditionKeys().getFirst(), val);
            }
        }

        return analysis;
    }

    /**
     * Returns the proper response a villager should produce when given this type of gift.
     */
    public Response getResponse(int satisfaction) {
        return satisfaction <= fail ? Response.FAIL
                : satisfaction <= good ? Response.GOOD
                : satisfaction <= better ? Response.BETTER
                : Response.BEST;
    }

    /**
     * Returns a line of dialogue to be spoken when a villager responds to this gift.
     */
    public String getDialogueFor(Response response) {
        return responses.get(response);
    }

    public void extendFrom(GiftType extendingType) {
        conditions.addAll(extendingType.getConditions());
        responses.clear();
        responses.putAll(extendingType.getResponses());
        priority = extendingType.priority;
        fail = extendingType.fail;
        good = extendingType.good;
        better = extendingType.better;
    }
}
