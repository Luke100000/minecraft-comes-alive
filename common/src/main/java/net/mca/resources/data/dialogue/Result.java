package net.mca.resources.data.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.mca.Config;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Memories;
import net.mca.entity.interaction.InteractionPredicate;
import net.mca.resources.data.analysis.IntAnalysis;
import net.minecraft.server.level.ServerPlayer;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class Result {
    private final Actions actions;
    private final int baseChance;
    private final List<InteractionPredicate> conditions;
    private final boolean applyFatigue;

    public Result(Actions actions, int baseChance, List<InteractionPredicate> conditions, boolean applyFatigue) {
        this.actions = actions;
        this.baseChance = baseChance;
        this.conditions = conditions;
        this.applyFatigue = applyFatigue;
    }

    public static Result fromJson(JsonObject json) {
        Actions actions = Actions.fromJson(json.getAsJsonObject("actions"));

        int baseChance = json.has("baseChance") ? json.get("baseChance").getAsInt() : 0;

        List<InteractionPredicate> conditions = new LinkedList<>();
        if (json.has("conditions")) {
            for (JsonElement e : json.getAsJsonArray("conditions")) {
                conditions.add(InteractionPredicate.fromJson(e.getAsJsonObject()));
            }
        }

        boolean applyFatigue = json.has("applyFatigue") && json.get("applyFatigue").getAsBoolean();

        return new Result(actions, baseChance, conditions, applyFatigue);
    }

    public Actions getActions() {
        return actions;
    }

    public int getBaseChance() {
        return baseChance;
    }

    public boolean shouldApplyFatigue() {
        return applyFatigue;
    }

    public List<InteractionPredicate> getConditions() {
        return Objects.requireNonNullElse(conditions, Collections.emptyList());
    }

    public IntAnalysis getChances(VillagerEntityMCA villager, ServerPlayer player) {
        IntAnalysis analysis = new IntAnalysis();
        Memories memory = villager.getVillagerBrain().getMemoriesForPlayer(player);

        // base chance
        if (getBaseChance() != 0) {
            analysis.add("base", getBaseChance());
        }

        int f = (int)(memory.getInteractionFatigue() * Config.getInstance().interactionChanceFatigue);
        if (shouldApplyFatigue() && f > 0) {
            analysis.add("fatigue", f);
        }

        // condition chance
        for (InteractionPredicate c : getConditions()) {
            int chance = (int)(c.getChance() * c.test(villager, player));
            if (chance != 0) {
                analysis.add(c.getConditionKeys().get(0), chance);
            }
        }

        return analysis;
    }
}
