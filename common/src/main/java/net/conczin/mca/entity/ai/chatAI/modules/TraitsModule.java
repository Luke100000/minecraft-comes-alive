package net.conczin.mca.entity.ai.chatAI.modules;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Traits;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.conczin.mca.entity.ai.chatAI.OpenAIChatAI.translate;

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
        put("asexual", "$villager is asexual.");
        put("left_handed", "$villager is left handed.");
        put("electrified", "$villager has been struck by lightning.");
        put("rainbow", "$villager has rainbow hair.");
        put("rainbow_eyes", "$villager has rainbow eyes.");
    }};

    public static void apply(List<String> input, VillagerEntityMCA villager, ServerPlayer player) {
        for (Traits.Trait trait : villager.getTraits().getTraits()) {
            input.add(traitDescription.getOrDefault(trait.id(), "$villager has " + translate(trait.id()) + ". "));
        }
    }
}
