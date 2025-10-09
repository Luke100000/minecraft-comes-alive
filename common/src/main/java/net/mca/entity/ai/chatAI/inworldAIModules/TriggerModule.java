package net.mca.entity.ai.chatAI.inworldAIModules;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.chatAI.TriggerCommandInfos;
import net.mca.entity.ai.chatAI.inworldAIModules.api.Interaction;
import net.mca.entity.ai.chatAI.inworldAIModules.api.TriggerEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * Class to manage command triggers (wear armor, follow me, etc.)
 */
public class TriggerModule {
    /**
     * Looks for outgoing triggers from the last interaction
     * and executes {@link TriggerCommandInfos specific actions} associated with different trigger names
     *
     * @param interaction Interaction object from a SendText request
     * @param player      Player in the conversation
     * @param villager    Villager in the conversation
     */
    public void processTriggers(Interaction interaction, ServerPlayer player, VillagerEntityMCA villager) {
        // Get triggers sent from server
        TriggerEvent[] triggerEvents = interaction.outgoingTriggers();
        for (TriggerEvent event : triggerEvents) {
            TriggerCommandInfos.findCommand(event.trigger(), player, villager)
                    .ifPresent(commandInfo -> commandInfo.call.accept(player, villager));
        }
    }
}
