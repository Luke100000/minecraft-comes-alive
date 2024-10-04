package net.mca.entity.ai.chatAI.gpt3Modules;

import net.mca.Config;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.Traits;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.mca.entity.ai.chatAI.GPT3.translate;

public class TraitsModule {
    private static final Map<String, String> traitDescription = new HashMap<>() {{
        put("lactose_intolerance", "$villager is intolerant to lactose.");
        put("coeliac_disease", "$villager has coeliac disease.");
        put("diabetes", "$villager has diabetes.");
        put("sirben", "$villager is unfortunately born as a Sirben.");
        put("dwarfism", "$villager has dwarfism.");
        put("albinism", "$villager is an albino.");
        put("heterochromia", "$villager has heterochromia.");
        put("color_blind", "$villager is color-blind.");
        put("vegetarian", "$villager is a vegetarian.");
        put("bisexual", "$villager is bisexual.");
        put("homosexual", "$villager is homosexual.");
        put("left_handed", "$villager is left handed.");
        put("electrified", "$villager has been struck by lightning.");
        put("rainbow", "$villager has colorful hair.");
    }};

    public static void apply(List<String> input, VillagerEntityMCA villager, ServerPlayerEntity player) {
        if (Config.getInstance().villagerChatAIIntelligence >= 1) {
            for (Traits.Trait trait : villager.getTraits().getTraits()) {
                input.add(traitDescription.getOrDefault(trait.id(), "$villager has " + translate(trait.id()) + ". "));
            }
        }
    }
}
