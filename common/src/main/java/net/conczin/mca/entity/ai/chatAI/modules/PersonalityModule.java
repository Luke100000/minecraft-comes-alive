package net.conczin.mca.entity.ai.chatAI.modules;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.relationship.AgeState;
import net.conczin.mca.entity.ai.relationship.Personality;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.VillagerProfession;

import java.util.List;

import static net.conczin.mca.entity.ai.chatAI.OpenAIChatAI.translate;

public class PersonalityModule {
    public static void apply(List<String> input, VillagerEntityMCA villager, ServerPlayer player) {
        input.add("This is a conversation with a " + translate(villager.getGenetics().getGender().name()) + " Minecraft villager named $villager and the Player named $player." + " ");

        Personality personality = villager.getVillagerBrain().getPersonality();
        String personalityName = personality.getId().getNamespace().equals(MCA.MOD_ID)
                ? personality.getId().getPath()
                : personality.getId().toString();
        input.add("$villager is " + translate(personalityName) + " and "
                + translate(villager.getVillagerBrain().getMood().getName()) + ". ");
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
            input.add("$villager is a " + translate(villager.getProfession().name()) + ". ");
        }
    }
}
