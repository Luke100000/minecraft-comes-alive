package net.mca.entity.ai.chatAI.modules;

import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.relationship.AgeState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.village.VillagerProfession;

import java.util.List;

import static net.mca.entity.ai.chatAI.OpenAIChatAI.translate;

public class PersonalityModule {
    public static void apply(List<String> input, VillagerEntityMCA villager, ServerPlayerEntity player) {
        input.add("This is a conversation with a " + translate(villager.getGenetics().getGender().name()) + " Minecraft villager named $villager and the Player named $player." + " ");

        input.add("$villager is " + translate(villager.getVillagerBrain().getPersonality().name()) + " and " + translate(villager.getVillagerBrain().getMood().getName()) + ". ");
        if (villager.getAgeState() == AgeState.BABY) {
            input.add("$villager is a baby. ");
        }
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
