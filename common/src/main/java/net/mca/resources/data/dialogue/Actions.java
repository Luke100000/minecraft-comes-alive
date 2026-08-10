package net.mca.resources.data.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.mca.MCA;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.LongTermMemory;
import net.mca.network.s2c.InteractionDialogueQuestionResponse;
import net.mca.network.s2c.InteractionDialogueResponse;
import net.mca.resources.Dialogues;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public class Actions {
    public static final Map<String, Factory<JsonElement>> TYPES = new HashMap<>();

    static {
        register("next", GsonHelper::convertToString, id -> (villager, player) -> {
            if (id != null) {
                Question newQuestion = Dialogues.getInstance().getQuestion(id);
                if (newQuestion != null) {
                    if (newQuestion.isAuto()) {
                        // fire an random answer automatically
                        Dialogues.getInstance().selectAnswer(villager, player, newQuestion.getName(), newQuestion.getRandomAnswer().getName());
                        return;
                    } else {
                        // a silent message might be a question the player asks one self and should not be spoken by the villager
                        MutableComponent text = villager.getTranslatable(player, Question.getTranslationKey(id));
                        NetworkHandler.sendToPlayer(new InteractionDialogueResponse(newQuestion, player, villager), player);
                        NetworkHandler.sendToPlayer(new InteractionDialogueQuestionResponse(newQuestion.isSilent(), text), player);
                    }
                } else {
                    // we send nevertheless and assume it's a final question
                    villager.sendChatMessage(player, Question.getTranslationKey(id));
                }

                // close screen
                if (newQuestion == null || newQuestion.isCloseScreen()) {
                    villager.getInteractions().stopInteracting();
                }
            } else {
                villager.getInteractions().stopInteracting();
            }
        });

        register("say", GsonHelper::convertToString, id -> (villager, player) -> {
            MutableComponent text = villager.getTranslatable(player, Question.getTranslationKey(id));
            NetworkHandler.sendToPlayer(new InteractionDialogueQuestionResponse(false, text), player);
        });

        register("remember", GsonHelper::convertToJsonObject, json -> (villager, player) -> {
            String id = LongTermMemory.parseId(json, player);
            if (json.has("time")) {
                villager.getLongTermMemory().remember(id, json.get("time").getAsLong());
            } else {
                villager.getLongTermMemory().remember(id);
            }
        });

        register("quit", (a, b) -> a, id -> (villager, player) -> villager.getInteractions().stopInteracting());

        register("negative", GsonHelper::convertToInt, hearts -> (villager, player) -> {
            villager.getVillagerBrain().modifyMoodValue(-hearts);
            villager.getVillagerBrain().rewardHearts(player, -hearts);
        });

        register("positive", GsonHelper::convertToInt, hearts -> (villager, player) -> {
            villager.getVillagerBrain().modifyMoodValue(hearts);
            villager.getVillagerBrain().rewardHearts(player, hearts);
        });

        register("command", GsonHelper::convertToString, command -> (villager, player) ->
                villager.getInteractions().handle(player, command));
    }

    public static <T> void register(String name, BiFunction<JsonElement, String, T> jsonParser, Factory<T> predicate) {
        TYPES.put(name, json -> predicate.parse(jsonParser.apply(json, name)));
    }

    public interface Factory<T> {
        Action parse(T value);
    }

    public static Actions fromJson(JsonObject json) {
        List<Action> actions = new LinkedList<>();
        boolean positive = false;
        boolean negative = false;

        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (TYPES.containsKey(entry.getKey())) {
                Action parsed = TYPES.get(entry.getKey()).parse(entry.getValue());
                actions.add(parsed);

                if (entry.getKey().equals("positive")) {
                    positive = true;
                }
                if (entry.getKey().equals("negative")) {
                    negative = true;
                }
            } else {
                MCA.LOGGER.info("Unknown dialogue action " + entry.getKey());
            }
        }

        if (!json.has("next")) {
            Action parsed = TYPES.get("quit").parse(json);
            actions.add(parsed);
        }

        return new Actions(actions, positive, negative);
    }

    public interface Action {
        void trigger(VillagerEntityMCA villager, ServerPlayer player);
    }

    private final List<Action> actions;
    private final boolean positive;
    private final boolean negative;

    public Actions(List<Action> actions, boolean positive, boolean negative) {
        this.actions = actions;
        this.positive = positive;
        this.negative = negative;
    }

    public void trigger(VillagerEntityMCA villager, ServerPlayer player) {
        for (Action c : actions) {
            c.trigger(villager, player);
        }
    }

    public boolean isPositive() {
        return positive;
    }

    public boolean isNegative() {
        return negative;
    }
}
