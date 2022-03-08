package mca.entity.interaction;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import mca.MCA;
import mca.entity.VillagerEntityMCA;
import mca.entity.interaction.gifts.GiftPredicate;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.JsonHelper;
import org.jetbrains.annotations.Nullable;

import static mca.entity.interaction.gifts.GiftPredicate.CONDITION_TYPES;

public class InteractionPredicate {
    public static InteractionPredicate fromJson(JsonObject json) {
        int chance = 0;

        @Nullable
        GiftPredicate.Condition condition = null;
        List<String> conditionKeys = new LinkedList<>();

        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if ("chance".equals(entry.getKey())) {
                chance = JsonHelper.asInt(entry.getValue(), entry.getKey());
            } else if (CONDITION_TYPES.containsKey(entry.getKey())) {
                GiftPredicate.Condition parsed = CONDITION_TYPES.get(entry.getKey()).parse(entry.getValue());
                conditionKeys.add(entry.getKey());
                if (condition == null) {
                    condition = parsed;
                } else {
                    condition = condition.and(parsed);
                }
            } else {
                MCA.LOGGER.warn("Interaction predicate " + entry.getKey() + " does not exist!");
            }
        }

        return new InteractionPredicate(chance, condition, conditionKeys);
    }

    private final int chance;

    @Nullable
    private final GiftPredicate.Condition condition;
    List<String> conditionKeys;

    public InteractionPredicate(int chance, @Nullable GiftPredicate.Condition condition, List<String> conditionKeys) {
        this.chance = chance;
        this.condition = condition;
        this.conditionKeys = conditionKeys;
    }

    public boolean test(VillagerEntityMCA villager, ServerPlayerEntity player) {
        return condition != null && condition.test(villager, ItemStack.EMPTY, player);
    }

    public int getChance() {
        return chance;
    }

    public List<String> getConditionKeys() {
        return conditionKeys;
    }
}
