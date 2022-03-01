package mca.resources.data.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import mca.MCA;
import mca.cobalt.network.NetworkHandler;
import mca.entity.VillagerEntityMCA;
import mca.network.client.InteractionDialogueResponse;
import mca.resources.Dialogues;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.JsonHelper;

public class Actions {
    public static final Map<String, Factory<JsonElement>> TYPES = new HashMap<>();

    static {
        register("next", JsonHelper::asString, id -> {
            return (villager, player) -> {
                if (id != null) {
                    Question newQuestion = Dialogues.getInstance().getRandomQuestion(id);
                    if (newQuestion != null) {
                        if (newQuestion.isAuto()) {
                            // this is basically a placeholder and fires an answer automatically
                            // use cases are n to 1 links or to split file size
                            Dialogues.getInstance().selectAnswer(villager, player, newQuestion.getId(), newQuestion.getAnswers().get(0).getName());
                            return;
                        } else {
                            NetworkHandler.sendToPlayer(new InteractionDialogueResponse(newQuestion, player, villager), player);
                        }
                    } else {
                        // we send nevertheless and assume it's a final question
                        villager.sendChatMessage(player, "dialogue." + id);
                    }

                    // close screen
                    if (newQuestion == null || newQuestion.isCloseScreen()) {
                        villager.getInteractions().stopInteracting();
                    }
                } else {
                    villager.getInteractions().stopInteracting();
                }
            };
        });

        register("quit", (a, b) -> a, id -> {
            return (villager, player) -> {
                villager.getInteractions().stopInteracting();
            };
        });

        register("negative", JsonHelper::asInt, hearts -> {
            return (villager, player) -> {
                villager.getVillagerBrain().modifyMoodValue(-hearts);
                villager.getVillagerBrain().rewardHearts(player, -hearts);
            };
        });

        register("positive", JsonHelper::asInt, hearts -> {
            return (villager, player) -> {
                villager.getVillagerBrain().modifyMoodValue(hearts);
                villager.getVillagerBrain().rewardHearts(player, hearts);
            };
        });

        register("command", JsonHelper::asString, command -> {
            return (villager, player) -> {
                villager.getInteractions().handle(player, command);
            };
        });
    }

    public static <T> void register(String name, BiFunction<JsonElement, String, T> jsonParser, Factory<T> predicate) {
        TYPES.put(name, json -> predicate.parse(jsonParser.apply(json, name)));
    }

    public interface Factory<T> {
        Action parse(T value);
    }

    public static Actions fromJson(JsonObject json) {
        List<Action> actions = new LinkedList<>();

        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
            if (TYPES.containsKey(entry.getKey())) {
                Action parsed = TYPES.get(entry.getKey()).parse(entry.getValue());
                actions.add(parsed);
            } else {
                MCA.LOGGER.info("Unknown dialogue action " + entry.getKey());
            }
        }

        if (!json.has("next")) {
            Action parsed = TYPES.get("quit").parse(json);
            actions.add(parsed);
        }

        return new Actions(actions);
    }

    public interface Action {
        void trigger(VillagerEntityMCA villager, ServerPlayerEntity player);
    }

    List<Action> conditions;

    public Actions(List<Action> conditions) {
        this.conditions = conditions;
    }

    public void trigger(VillagerEntityMCA villager, ServerPlayerEntity player) {
        for (Action c : conditions) {
            c.trigger(villager, player);
        }
    }
}
