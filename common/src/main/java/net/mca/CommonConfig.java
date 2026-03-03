package net.mca;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommonConfig implements Serializable {
    @Serial
    private static final long serialVersionUID = -8238866449153504236L;

    public CommonConfig() {
    }

    public CommonConfig(CommonConfig config) {
        this.babyItemGrowUpTime = config.babyItemGrowUpTime;
        this.villagerMaxAgeTime = config.villagerMaxAgeTime;
        this.allowEveryoneToAddContentGlobally = config.allowEveryoneToAddContentGlobally;
        this.allowPlayerSizeAdjustment = config.allowPlayerSizeAdjustment;
        this.allowBodyCustomizationInDestiny = config.allowBodyCustomizationInDestiny;
        this.allowTraitCustomizationInDestiny = config.allowTraitCustomizationInDestiny;
        this.destinySpawnLocations = config.destinySpawnLocations;
        this.destinyLocationsToTranslationMap = config.destinyLocationsToTranslationMap;
        this.enabledTraits = config.enabledTraits;
    }

    /**
     * Time (in ticks) until a baby grows up when held as an item.
     */
    public int babyItemGrowUpTime = 24000;

    /**
     * Maximum villager lifetime in ticks (Time to grow fully up).
     */
    public int villagerMaxAgeTime = 384000;

    /**
     * If true, allows non-ops to add skins from the library to the server wide pool.
     */
    public boolean allowEveryoneToAddContentGlobally = false;

    /**
     * Allow players to modify their size.
     */
    public boolean allowPlayerSizeAdjustment = true;

    /**
     * Whether body customization (e.g., height, size) is available in the Destiny editor.
     */
    public boolean allowBodyCustomizationInDestiny = true;

    /**
     * Whether trait customization is available in the Destiny editor.
     */
    public boolean allowTraitCustomizationInDestiny = true;

    /**
     * Locations where the Destiny feature can teleport the player.
     * <a href="https://github.com/Luke100000/minecraft-comes-alive/wiki/Custom-Rumors-and-Destiny-Structures">Wiki</a>
     */
    public List<String> destinySpawnLocations = List.of(
            "somewhere",
            "minecraft:shipwreck_beached",
            "minecraft:village_desert",
            "minecraft:village_taiga",
            "minecraft:village_snowy",
            "minecraft:village_plains",
            "minecraft:village_savanna",
            "minecraft:ancient_city"
    );

    /**
     * Maps Destiny locations to translation keys for UI text.
     */
    public Map<String, String> destinyLocationsToTranslationMap = Map.of(
            "default", "destiny.story.travelling",
            "minecraft:shipwreck_beached", "destiny.story.sailing"
    );

    /**
     * Map of enabled traits. Keys are trait IDs, values are true/false.
     */
    public Map<String, Boolean> enabledTraits = new HashMap<>();

}
