package net.mca.entity.ai.chatAI.inworldAIModules;


import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.chatAI.inworldAIModules.api.Interaction;
import net.mca.entity.ai.chatAI.inworldAIModules.api.TriggerEvent;

import java.util.Map;

/**
 * Class to manage villager mood to character emotion mapping
 * Does not try to map emotions back to villager mood. They're too different.
 */
public class EmotionModule {

    /**
     * Inworld has a completely complicated mood system than MCA does, so this mapping is a bit arbitrary
     */
    private static final Map<String, Interaction.Emotion.SpaffCode> moodMap = Map.of(
            "depressed", Interaction.Emotion.SpaffCode.SADNESS,
            "sad", Interaction.Emotion.SpaffCode.SADNESS,
            "unhappy", Interaction.Emotion.SpaffCode.SADNESS,
            "passive", Interaction.Emotion.SpaffCode.NEUTRAL,
            "fine", Interaction.Emotion.SpaffCode.NEUTRAL,
            "happy", Interaction.Emotion.SpaffCode.JOY,
            "overjoyed", Interaction.Emotion.SpaffCode.JOY
    );

    /**
     * Maps the villager's mood to Emotion SpaffCode
     * The SpaffCode is then used to modify the AI's initial mood
     *
     * @param villager The VillagerEntityMCA object whose mood is to be mapped to a SpaffCode.
     * @return The parameter has the name "emotion" and the value being the name of the SpaffCode corresponding to the villager's mood.
     */
    public TriggerEvent.Parameter getEmotionTriggerParameter(VillagerEntityMCA villager) {
        String villagerMood = villager.getVillagerBrain().getMood().getName();
        return new TriggerEvent.Parameter("emotion", moodMap.getOrDefault(villagerMood, Interaction.Emotion.SpaffCode.NEUTRAL).name());
    }

}
