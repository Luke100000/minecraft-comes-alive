package net.conczin.mca.entity.ai.chatAI.modules;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PlayerModule {
    private final static Map<Identifier, String> advancements = Map.of(
            Identifier.parse("story/mine_diamond"), "$player found diamonds.",
            Identifier.parse("story/enter_the_nether"), "$player explored the nether.",
            Identifier.parse("nether/find_fortress"), "$player found a nether fortress.",
            Identifier.parse("story/enchant_item"), "$player enchanted items.",
            Identifier.parse("story/cure_zombie_villager"), "$player cured a zombie villager.",
            Identifier.parse("end/kill_dragon"), "$player killed the ender dragon.",
            Identifier.parse("nether/summon_wither"), "$player summoned the wither.",
            Identifier.parse("adventure/hero_of_the_village"), "$player is the hero of the village."
    );

    public static void apply(List<String> input, VillagerEntityMCA villager, ServerPlayer player) {
        List<String> list = advancements.entrySet().stream()
                .filter(entry -> {
                    AdvancementHolder advancement = Objects.requireNonNull(player.level().getServer()).getAdvancements().get(entry.getKey());
                    if (advancement == null) {
                        MCA.LOGGER.warn("Advancement {} not found.", entry.getKey());
                        return false;
                    }
                    return player.getAdvancements().getOrStartProgress(advancement).isDone();
                })
                .map(Map.Entry::getValue)
                .toList();

        if (!list.isEmpty()) {
            input.add("Player has completed the following advancements: ");
            for (String advancement : list) {
                input.add(advancement + " ");
            }
        }
    }
}
