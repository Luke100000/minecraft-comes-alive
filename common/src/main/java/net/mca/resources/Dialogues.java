package net.mca.resources;

import com.google.gson.JsonElement;
import net.mca.MCA;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerEntityMCA;
import net.mca.network.s2c.AnalysisResults;
import net.mca.resources.data.SerializablePair;
import net.mca.resources.data.analysis.ChanceAnalysis;
import net.mca.resources.data.analysis.IntAnalysis;
import net.mca.resources.data.dialogue.Actions;
import net.mca.resources.data.dialogue.Answer;
import net.mca.resources.data.dialogue.Question;
import net.mca.resources.data.dialogue.Result;
import net.minecraft.resource.JsonDataLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.profiler.Profiler;

import java.util.*;

public class Dialogues extends JsonDataLoader {
    protected static final Identifier ID = new Identifier(MCA.MOD_ID, "dialogues");

    private static Dialogues INSTANCE;

    public static Dialogues getInstance() {
        return INSTANCE;
    }

    private final Map<String, Question> questions = new HashMap<>();

    public Dialogues() {
        super(Resources.GSON, "dialogues");
        INSTANCE = this;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, Profiler profiler) {
        questions.clear();
        data.forEach(this::loadDialogue);
    }

    private void loadDialogue(Identifier identifier, JsonElement element) {
        String id = identifier.getPath().substring(identifier.getPath().lastIndexOf('/') + 1);
        if (!this.checkIsMcaDialogue(element)) {
            MCA.LOGGER.warn("Dialogue {} is not properly formatted, not loading", identifier);
            return;
        }

        Question q = Question.fromJson(id, element.getAsJsonObject());

        // Merge questions to allow injections
        if (this.questions.containsKey(id)) {
            q.merge(this.questions.get(id));
        }
        q.getAnswers().sort(Comparator.comparingInt(Answer::getPriority));

        this.questions.put(id, q);
    }

    private boolean checkIsMcaDialogue(JsonElement element) {
        JsonElement answersElement = element.getAsJsonObject().get("answers");
        return answersElement != null && answersElement.isJsonArray();
    }

    public Question getQuestion(String i) {
        return questions.get(i);
    }

    //selects a specific answer while being in given question
    public void selectAnswer(VillagerEntityMCA villager, ServerPlayerEntity player, String questionId, String answerId) {
        Question question = getQuestion(questionId);
        Answer answer = question.getAnswer(answerId);

        //fetch chances for each result
        int total = 0;
        List<IntAnalysis> analysis = new LinkedList<>();
        for (Result r : answer.getResults()) {
            IntAnalysis a = r.getChances(villager, player);
            analysis.add(a);
            total += Math.max(0, a.getTotal());
        }

        //choose weighted random
        int chosen = -1;
        total = total == 0 ? 0 : villager.getRandom().nextInt(total);
        for (IntAnalysis a : analysis) {
            total -= Math.max(0, a.getTotal());
            chosen++;
            if (total < 0) {
                break;
            }
        }

        Actions chosenActions = answer.getResults().get(chosen).getActions();

        //send analysis (if there is a heart impact at all)
        if (chosenActions.isNegative() || chosenActions.isPositive()) {
            ChanceAnalysis finalAnalysis = new ChanceAnalysis();
            for (int i = 0; i < analysis.size(); i++) {
                boolean positive = answer.getResults().get(i).getActions().isPositive();
                boolean negative = answer.getResults().get(i).getActions().isNegative();
                for (SerializablePair<String, Integer> value : analysis.get(i).getSummands()) {
                    finalAnalysis.add(value.getLeft(), value.getRight() * (positive ? 1 : negative ? -1 : 0));
                }
            }
            NetworkHandler.sendToPlayer(new AnalysisResults(finalAnalysis), player);
        }

        //execute that results actions
        chosenActions.trigger(villager, player);
    }
}
