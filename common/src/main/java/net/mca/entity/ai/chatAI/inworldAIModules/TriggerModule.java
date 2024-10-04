package net.mca.entity.ai.chatAI.inworldAIModules;

import com.google.common.collect.ImmutableMap;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.MoveState;
import net.mca.entity.ai.chatAI.inworldAIModules.api.Interaction;
import net.mca.entity.ai.chatAI.inworldAIModules.api.TriggerEvent;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Map;
import java.util.function.BiConsumer;

/**
 * Class to manage command triggers (wear armor, follow me, etc.)
 */
public class TriggerModule {

    /** Map for trigger name => actions */
    private static final Map<String, BiConsumer<ServerPlayerEntity, VillagerEntityMCA>> triggerActions = ImmutableMap.of(
            "follow-player", (p, v) -> v.getVillagerBrain().setMoveState(MoveState.FOLLOW, p),
            "stay-here", (p, v) -> v.getVillagerBrain().setMoveState(MoveState.STAY, p),
            "move-freely", (p, v) -> v.getVillagerBrain().setMoveState(MoveState.MOVE, p),
            "wear-armor", (p, v) -> v.getVillagerBrain().setArmorWear(true),
            "remove-armor", (p, v) -> v.getVillagerBrain().setArmorWear(false),
            "try-go-home", (p, v) -> v.getResidency().goHome(p)
    );

    /**
     * Looks for outgoing triggers from the last interaction
     * and executes {@link #triggerActions specific actions} associated with different trigger names
     * @param interaction Interaction object from a SendText request
     * @param player Player in the conversation
     * @param villager Villager in the conversation
     */
    public void processTriggers(Interaction interaction, ServerPlayerEntity player, VillagerEntityMCA villager) {
        // Get triggers sent from server
        TriggerEvent[] triggerEvents = interaction.outgoingTriggers();
        for (TriggerEvent event : triggerEvents) {
            // Get the action for the trigger
            BiConsumer<ServerPlayerEntity, VillagerEntityMCA> action = triggerActions.get(event.trigger());

            // Execute the action if it exists
            if (action != null) {
                action.accept(player, villager);
            }
        }
    }
}
