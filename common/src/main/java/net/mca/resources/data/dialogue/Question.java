package net.mca.resources.data.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.mca.entity.interaction.Constraint;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.interaction.InteractionPredicate;
import net.minecraft.server.level.ServerPlayer;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public class Question {
    private final String name;
    private final List<Answer> answers;
    private final boolean auto;
    private final boolean silent;

    private final Random random = new Random();

    public Question(String id, List<Answer> answers, boolean auto, boolean silent) {
        this.name = id;
        this.answers = answers;
        this.auto = auto;
        this.silent = silent;
    }

    public static Question fromJson(String id, JsonObject json) {
        boolean auto = json.has("auto") && json.get("auto").getAsBoolean();
        boolean silent = json.has("silent") && json.get("silent").getAsBoolean();

        List<Answer> answers = new LinkedList<>();
        for (JsonElement e : json.getAsJsonArray("answers")) {
            answers.add(Answer.fromJson(e.getAsJsonObject()));
        }

        //sometimes the conditions are the same for all results
        //todo assigned to Luke1000000: that's horseshit, improve
        if (json.has("baseConditions")) {
            int r = 0;
            for (JsonElement conditions : json.getAsJsonArray("baseConditions")) {
                for (JsonElement e : conditions.getAsJsonArray()) {
                    InteractionPredicate predicate = InteractionPredicate.fromJson(e.getAsJsonObject());
                    int finalR = r;
                    answers.forEach(a -> a.getResults().get(finalR).getConditions().add(predicate));
                }
                r++;
            }
        }

        return new Question(id, answers, auto, silent);
    }

    public String getName() {
        return name;
    }

    public List<Answer> getAnswers() {
        return answers;
    }

    public boolean isCloseScreen() {
        return answers == null;
    }

    public Answer getAnswer(String answer) {
        for (Answer a : answers) {
            if (a.getName().equals(answer)) {
                return a;
            }
        }
        return null;
    }

    public static String getTranslationKey(String question) {
        return "dialogue." + question;
    }

    public static String getTranslationKey(String question, String answer) {
        return "dialogue." + question + "." + answer;
    }

    public boolean isAuto() {
        return auto;
    }

    public List<String> getValidAnswers(ServerPlayer player, VillagerEntityMCA villager) {
        Set<Constraint> constraints = Constraint.allMatching(villager, player);
        List<String> ans = new LinkedList<>();
        for (Answer a : answers) {
            if (a.isValidForConstraint(constraints)) {
                ans.add(a.getName());
            }
        }
        return ans;
    }

    public boolean isSilent() {
        return silent;
    }

    public void merge(Question question) {
        answers.addAll(question.getAnswers());
    }

    public Answer getRandomAnswer() {
        return answers.get(random.nextInt(answers.size()));
    }
}
