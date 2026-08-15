package net.conczin.mca.entity.interaction;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.interaction.gifts.GiftPredicate;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class InteractionPredicate {
    public static InteractionPredicate fromJson(JsonObject json) {
        int chance = 0;

        @Nullable
        GiftPredicate.Condition condition = null;
        List<String> conditionKeys = new LinkedList<>();

        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if ("chance".equals(entry.getKey())) {
                chance = GsonHelper.convertToInt(entry.getValue(), entry.getKey());
            } else if (GiftPredicate.CONDITION_TYPES.containsKey(entry.getKey())) {
                GiftPredicate.Condition parsed = GiftPredicate.CONDITION_TYPES.get(entry.getKey()).parse(entry.getValue());
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
    final List<String> conditionKeys;

    public InteractionPredicate(int chance, @Nullable GiftPredicate.Condition condition, List<String> conditionKeys) {
        this.chance = chance;
        this.condition = condition;
        this.conditionKeys = conditionKeys;
    }

    public float test(VillagerEntityMCA villager, ServerPlayer player) {
        return condition != null ? condition.test(villager, ItemStack.EMPTY, player) : 0.0f;
    }

    public int getChance() {
        return chance;
    }

    public List<String> getConditionKeys() {
        return conditionKeys;
    }
}
