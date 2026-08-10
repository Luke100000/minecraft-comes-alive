package net.mca.entity.ai.chatAI.modules;

import net.mca.MCA;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class PlayerModule {
    private final static Map<ResourceLocation, String> advancements = Map.of(
            new ResourceLocation("story/mine_diamond"), "$player found diamonds.",
            new ResourceLocation("story/enter_the_nether"), "$player explored the nether.",
            new ResourceLocation("nether/find_fortress"), "$player found a nether fortress.",
            new ResourceLocation("story/enchant_item"), "$player enchanted items.",
            new ResourceLocation("story/cure_zombie_villager"), "$player cured a zombie villager.",
            new ResourceLocation("end/kill_dragon"), "$player killed the ender dragon.",
            new ResourceLocation("nether/summon_wither"), "$player summoned the wither.",
            new ResourceLocation("adventure/hero_of_the_village"), "$player is the hero of the village."
    );

    public static void apply(List<String> input, VillagerEntityMCA villager, ServerPlayer player) {
        List<String> list = advancements.entrySet().stream()
                .filter(entry -> {
                    Advancement advancement = Objects.requireNonNull(player.getServer()).getAdvancements().getAdvancement(entry.getKey());
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
