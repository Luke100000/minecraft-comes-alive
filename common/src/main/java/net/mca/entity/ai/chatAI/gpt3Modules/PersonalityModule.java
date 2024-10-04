package net.mca.entity.ai.chatAI.gpt3Modules;

import net.mca.Config;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.relationship.AgeState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.village.VillagerProfession;

import java.util.List;

import static net.mca.entity.ai.chatAI.GPT3.translate;

public class PersonalityModule {
    public static void apply(List<String> input, VillagerEntityMCA villager, ServerPlayerEntity player) {
        input.add("This is a conversation with a " + translate(villager.getGenetics().getGender().name()) + " Minecraft villager named $villager and the Player named $player." + " ");

        if (Config.getInstance().villagerChatAIIntelligence >= 1) {
            input.add("$villager is " + translate(villager.getVillagerBrain().getPersonality().name()) + " and " + translate(villager.getVillagerBrain().getMood().getName()) + ". ");
            if (villager.getAgeState() == AgeState.TODDLER) {
                input.add("$villager is a toddler. ");
            }
            if (villager.getAgeState() == AgeState.CHILD) {
                input.add("$villager is a child. ");
            }
            if (villager.getAgeState() == AgeState.TEEN) {
                input.add("$villager is a teen. ");
            } else if (villager.getProfession() != VillagerProfession.NONE) {
                input.add("$villager is a " + translate(villager.getProfession().id()) + ". ");
            }
        }
    }
}
