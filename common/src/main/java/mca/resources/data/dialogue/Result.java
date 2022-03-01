package mca.resources.data.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import mca.Config;
import mca.entity.VillagerEntityMCA;
import mca.entity.ai.Memories;
import mca.entity.interaction.InteractionPredicate;
import mca.resources.data.analysis.IntAnalysis;
import net.minecraft.entity.player.PlayerEntity;

public class Result {
    private final Actions actions;
    private final int baseChance;
    private final List<InteractionPredicate> conditions;
    private final boolean applyFatigue;
    private final boolean positive;

    public Result(Actions actions, int baseChance, List<InteractionPredicate> conditions, boolean applyFatigue, boolean positive) {
        this.actions = actions;
        this.baseChance = baseChance;
        this.conditions = conditions;
        this.applyFatigue = applyFatigue;
        this.positive = positive;
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
        boolean positive = json.has("positive") && json.get("positive").getAsBoolean();

        return new Result(actions, baseChance, conditions, applyFatigue, positive);
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

    public boolean isPositive() {
        return positive;
    }

    public List<InteractionPredicate> getConditions() {
        return Objects.requireNonNullElse(conditions, Collections.emptyList());
    }

    public IntAnalysis getChances(VillagerEntityMCA villager, PlayerEntity player) {
        IntAnalysis analysis = new IntAnalysis();
        Memories memory = villager.getVillagerBrain().getMemoriesForPlayer(player);

        // base chance
        if (getBaseChance() > 0) {
            analysis.add("base", getBaseChance());
        }

        if (shouldApplyFatigue()) {
            analysis.add("fatigue", memory.getInteractionFatigue() * Config.getInstance().interactionChanceFatigue);
        }

        // condition chance
        for (InteractionPredicate c : getConditions()) {
            if (c.getChance() != 0 && c.test(villager)) {
                analysis.add(c.getConditionKeys().get(0), c.getChance());
            }
        }

        return analysis;
    }
}
