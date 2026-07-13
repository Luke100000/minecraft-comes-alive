package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.AnalysisResults;
import net.conczin.mca.resources.data.Analysis;
import net.conczin.mca.resources.data.SerializablePair;
import net.conczin.mca.resources.data.dialogue.Actions;
import net.conczin.mca.resources.data.dialogue.Answer;
import net.conczin.mca.resources.data.dialogue.Question;
import net.conczin.mca.resources.data.dialogue.Result;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.profiling.ProfilerFiller;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class Dialogues extends SimpleJsonResourceReloadListener<JsonElement> {
    public static final Identifier ID = MCA.locate("dialogues");

    private static Dialogues INSTANCE;
    private final Map<String, Question> questions = new HashMap<>();

    public Dialogues() {
        super(ExtraCodecs.JSON, FileToIdConverter.json("dialogues"));
        INSTANCE = this;
    }

    public static Dialogues getInstance() {
        return INSTANCE;
    }

    private static @NotNull Analysis getFinalAnalysis(List<Analysis> analysis, Answer answer) {
        Analysis finalAnalysis = new Analysis();
        for (int i = 0; i < analysis.size(); i++) {
            boolean positive = answer.getResults().get(i).getActions().isPositive();
            boolean negative = answer.getResults().get(i).getActions().isNegative();
            for (SerializablePair<String, Integer> value : analysis.get(i).getSummands()) {
                finalAnalysis.add(value.left(), value.right() * (positive ? 1 : negative ? -1 : 0));
            }
        }
        return finalAnalysis;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
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
    public void selectAnswer(VillagerEntityMCA villager, ServerPlayer player, String questionId, String answerId) {
        Question question = getQuestion(questionId);
        if (question == null) {
            MCA.LOGGER.warn("Player {} tried to select an unknown dialogue question '{}'", player.getGameProfile().getName(), questionId);
            return;
        }

        Answer answer = question.getAnswer(answerId);
        if (answer == null) {
            MCA.LOGGER.warn("Player {} tried to select unknown answer '{}' for dialogue question '{}'", player.getGameProfile().getName(), answerId, questionId);
            return;
        }

        //fetch chances for each result
        int total = 0;
        List<Analysis> analysis = new LinkedList<>();
        for (Result r : answer.getResults()) {
            Analysis a = r.getChances(villager, player);
            analysis.add(a);
            total += Math.max(0, a.getTotal());
        }

        //choose weighted random
        int chosen = -1;
        total = total == 0 ? 0 : villager.getRandom().nextInt(total);
        for (Analysis a : analysis) {
            total -= Math.max(0, a.getTotal());
            chosen++;
            if (total < 0) {
                break;
            }
        }

        Actions chosenActions = answer.getResults().get(chosen).getActions();

        //send analysis (if there is a heart impact at all)
        if (chosenActions.isNegative() || chosenActions.isPositive()) {
            Analysis finalAnalysis = getFinalAnalysis(analysis, answer);
            Network.sendToPlayer(new AnalysisResults(finalAnalysis), player);
        }

        //execute that results actions
        chosenActions.trigger(villager, player);
    }
}
