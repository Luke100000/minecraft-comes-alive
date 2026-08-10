package net.mca.entity.ai.chatAI.modules;

import net.mca.entity.VillagerEntityMCA;
import net.mca.server.world.data.Building;
import net.mca.server.world.data.Village;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class VillageModule {
    private static final Map<String, String> nameExceptions = Map.of(
            "fishermans_hut", "fisherman's hut",
            "weaving_mill", "weaving mill",
            "big_house", "big house",
            "music_store", "music store",
            "town_center", "town center",
            "building", "",
            "blocked", "",
            "house", ""
    );

    public static void apply(List<String> input, VillagerEntityMCA villager, ServerPlayer player) {
        Optional<Village> village = villager.getResidency().getHomeVillage();

        // Probably completely over-detailed fact
        String biome = villager.level().getBiome(villager.blockPosition()).unwrapKey().map(v -> v.location().getPath()).orElse("plains");

        String size = "small";
        if (village.isPresent()) {
            int population = village.get().getPopulation();
            if (population > 45) {
                size = "huge";
            } else if (population > 30) {
                size = "large";
            } else if (population > 15) {
                size = "medium-sized";
            }
        }

        input.add("$villager lives in a " + size + ", medieval village in a " + biome.replace("_", " ") + " biom. ");

        // Buildings
        village.ifPresent(v -> {
            String buildings = v.getBuildings().values().stream()
                    .map(Building::getType)
                    .map(b -> nameExceptions.getOrDefault(b, b))
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .collect(Collectors.joining(", "));
            if (!buildings.isEmpty()) {
                input.add("The village has a " + buildings + ". ");
            }
        });
    }
}
