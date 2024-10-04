package net.mca.entity.ai.chatAI.gpt3Modules;

import net.mca.Config;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.advancement.Advancement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PlayerModule {
    private final static Map<Identifier, String> advancements = Map.of(
            new Identifier("story/mine_diamond"), "$player found diamonds.",
            new Identifier("story/enter_the_nether"), "$player explored the nether.",
            new Identifier("story/enchant_item"), "$player enchanted items.",
            new Identifier("story/cure_zombie_villager"), "$player cured a zombie villager.",
            new Identifier("end/kill_dragon"), "$player killed the ender dragon.",
            new Identifier("nether/summon_wither"), "$player summoned the wither.",
            new Identifier("adventure/hero_of_the_village"), "$player is a hero."
    );

    public static void apply(List<String> input, VillagerEntityMCA villager, ServerPlayerEntity player) {
        if (Config.getInstance().villagerChatAIIntelligence >= 5) {
            for (Map.Entry<Identifier, String> entry : advancements.entrySet()) {
                Advancement advancement = Objects.requireNonNull(player.getServer()).getAdvancementLoader().get(entry.getKey());
                if (player.getAdvancementTracker().getProgress(advancement).isDone()) {
                    input.add(entry.getValue() + " ");
                }
            }
        }
    }
}
