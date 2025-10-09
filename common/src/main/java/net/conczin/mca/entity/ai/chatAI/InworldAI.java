package net.conczin.mca.entity.ai.chatAI;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.chatAI.inworldAIModules.EmotionModule;
import net.conczin.mca.entity.ai.chatAI.inworldAIModules.RelationshipModule;
import net.conczin.mca.entity.ai.chatAI.inworldAIModules.SessionModule;
import net.conczin.mca.entity.ai.chatAI.inworldAIModules.TriggerModule;
import net.conczin.mca.entity.ai.chatAI.inworldAIModules.api.Interaction;
import net.conczin.mca.entity.ai.chatAI.inworldAIModules.api.TriggerEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

public class InworldAI implements ChatAIStrategy {
    private final SessionModule sessionModule;
    private final RelationshipModule relationshipModule;
    private final TriggerModule triggerModule;
    private final EmotionModule emotionModule;

    public InworldAI(String resourceName) {
        this.sessionModule = new SessionModule(resourceName);
        this.relationshipModule = new RelationshipModule();
        this.triggerModule = new TriggerModule();
        this.emotionModule = new EmotionModule();
    }

    /**
     * Gets a reply from InworldAI for a given message.
     *
     * @param player   The player requesting the answer
     * @param villager The villager responding
     * @param msg      The message
     * @return {@code Optional.EMPTY} on error, Optional containing the answer to a message on success
     */
    public Optional<String> answer(ServerPlayer player, VillagerEntityMCA villager, String msg) {

        // Create status update event for Character (Includes relationship and current emotional state)
        TriggerEvent.Parameter relationshipTrigger = relationshipModule.getRelationshipTriggerParameter(player, villager);
        TriggerEvent.Parameter emotionTrigger = emotionModule.getEmotionTriggerParameter(villager);
        TriggerEvent event = new TriggerEvent("status-update", new TriggerEvent.Parameter[]{relationshipTrigger, emotionTrigger});

        // Get response
        Optional<Interaction> optionalResponse = sessionModule.getResponse(player, msg, event);
        if (optionalResponse.isPresent()) {
            Interaction response = optionalResponse.get();

            // Use response data to update heart level
            relationshipModule.updateRelationship(response, player, villager);

            // Use response data to update move state
            triggerModule.processTriggers(response, player, villager);

            // Answer, or be silent if there is no answer (e.g., on triggers)
            String answer = String.join("", response.textList());
            if (answer.isEmpty()) {
                return Optional.empty();
            } else {
                return Optional.of(answer);
            }
        } else {
            player.displayClientMessage(Component.translatable("mca.ai_broken").withStyle(ChatFormatting.RED), false);
            return Optional.empty();
        }
    }
}
