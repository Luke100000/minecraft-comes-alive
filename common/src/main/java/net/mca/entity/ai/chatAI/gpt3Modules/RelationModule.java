package net.mca.entity.ai.chatAI.gpt3Modules;

import net.mca.Config;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Relationship;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

public class RelationModule {
    public static void apply(List<String> input, VillagerEntityMCA villager, ServerPlayerEntity player) {
        boolean silentHearts = false;

        if (Config.getInstance().villagerChatAIIntelligence >= 2) {
            if (Relationship.IS_MARRIED.test(villager, player)) {
                input.add("$villager is married to $player. ");
                silentHearts = true;
            } else if (villager.getRelationships().isMarried()) {
                input.add("$villager is married. ");
            }
            if (Relationship.IS_PROMISED.test(villager, player)) {
                input.add("$villager is in love with $player. ");
                silentHearts = true;
            }
            if (Relationship.IS_ENGAGED.test(villager, player)) {
                input.add("$villager is engaged with $player. ");
                silentHearts = true;
            }
            if (Relationship.IS_PARENT.test(villager, player)) {
                input.add("$player is $villagers parent. ");
            }
            if (Relationship.IS_KID.test(villager, player)) {
                input.add("$villagers is players parent. ");
            }
        }

        if (Config.getInstance().villagerChatAIIntelligence >= 3) {
            int hearts = villager.getVillagerBrain().getMemoriesForPlayer(player).getHearts();
            if (hearts < -25) {
                input.add("$villager hates $player. ");
            } else if (!silentHearts) {
                if (hearts < 0) {
                    input.add("$villager dislikes $player. ");
                } else if (hearts < 33) {
                    input.add("$villager barely knows $player. ");
                } else if (hearts < 66) {
                    input.add("$villager knows $player well. ");
                } else if (hearts < 100) {
                    input.add("$villager likes $player. ");
                } else {
                    input.add("$villager likes $player really well. ");
                }
            }
        }
    }
}
