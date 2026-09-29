package net.conczin.mca;

import com.google.common.collect.ImmutableMap;
import java.util.List;
import java.util.Map;
import net.conczin.mca.entity.EquipmentSet;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ServerConfig {
    public final ModConfigSpec.ConfigValue<Boolean> enableVillagerChatAI;
    public final ModConfigSpec.ConfigValue<String> villagerChatAIModel;
    public final ModConfigSpec.ConfigValue<Boolean> villagerChatAIUseTools;
    public final ModConfigSpec.ConfigValue<Integer> villagerChatAIContextPermissionLevel;
    public final ModConfigSpec.ConfigValue<Boolean> archerArrowsIgnoreVillagers;
    public final ModConfigSpec.ConfigValue<Boolean> villagersInteractWithFenceGates;
    public final ModConfigSpec.ConfigValue<Integer> babyItemGrowUpTime;
    public final ModConfigSpec.ConfigValue<Integer> villagerMaxAgeTime;
    public final ModConfigSpec.ConfigValue<Integer> addContentGloballyPermissionLevel;
    public final ModConfigSpec.ConfigValue<Boolean> allowPlayerSizeAdjustment;
    public final ModConfigSpec.ConfigValue<Boolean> scalePlayerHitboxWithSizeAndWidth;
    public final ModConfigSpec.ConfigValue<Boolean> allowBodyCustomizationInDestiny;
    public final ModConfigSpec.ConfigValue<Boolean> allowTraitCustomizationInDestiny;
    public final ModConfigSpec.ConfigValue<List<? extends String>> destinySpawnLocations;
    public final ModConfigSpec.ConfigValue<Boolean> autoDiscoverDestinyLocations;
    public final ModConfigSpec.ConfigValue<List<? extends String>> destinySpawnLocationBlacklist;
    public final ModConfigSpec.ConfigValue<Boolean> destinyOverworldOnly;
    public final ModConfigSpec.ConfigValue<List<? extends String>> destinyDimensionBlacklist;
    public final ModConfigSpec.ConfigValue<List<? extends String>> destinyLocationsToTranslationMap;
    public final ModConfigSpec.ConfigValue<List<? extends String>> enabledTraits;
    public final ModConfigSpec.ConfigValue<Boolean> overwriteOriginalVillagers;
    public final ModConfigSpec.ConfigValue<List<? extends String>> moddedVillagerWhitelist;
    public final ModConfigSpec.ConfigValue<Boolean> overwriteOriginalZombieVillagers;
    public final ModConfigSpec.ConfigValue<Boolean> overwriteAllZombiesWithZombieVillagers;
    public final ModConfigSpec.ConfigValue<List<? extends String>> moddedZombieVillagerWhitelist;
    public final ModConfigSpec.ConfigValue<Double> babyZombieChance;
    public final ModConfigSpec.ConfigValue<Boolean> villagerTagsHacks;
    public final ModConfigSpec.ConfigValue<Boolean> enableInfection;
    public final ModConfigSpec.ConfigValue<Boolean> allowGrimReaper;
    public final ModConfigSpec.ConfigValue<String> villagerChatPrefix;
    public final ModConfigSpec.ConfigValue<Boolean> canHurtBabies;
    public final ModConfigSpec.ConfigValue<Boolean> enterVillageNotification;
    public final ModConfigSpec.ConfigValue<Boolean> villagerMarriageNotification;
    public final ModConfigSpec.ConfigValue<Boolean> villagerBirthNotification;
    public final ModConfigSpec.ConfigValue<Boolean> innArrivalNotification;
    public final ModConfigSpec.ConfigValue<Boolean> villagerRestockNotification;
    public final ModConfigSpec.ConfigValue<Boolean> showNotificationsAsChat;
    public final ModConfigSpec.ConfigValue<Boolean> giveAdvancementBooks;
    public final ModConfigSpec.ConfigValue<Integer> heartsToBeConsideredAsFriend;
    public final ModConfigSpec.ConfigValue<Boolean> enableVillagerMailingPlayers;
    public final ModConfigSpec.ConfigValue<Boolean> enableGenderCheckForPlayers;
    public final ModConfigSpec.ConfigValue<Double> zombieBiteInfectionChance;
    public final ModConfigSpec.ConfigValue<Double> infectionChanceDecreasePerLevel;
    public final ModConfigSpec.ConfigValue<Integer> infectionTime;
    public final ModConfigSpec.ConfigValue<Double> twinBabyChance;
    public final ModConfigSpec.ConfigValue<Integer> marriageHeartsRequirement;
    public final ModConfigSpec.ConfigValue<Integer> engagementHeartsRequirement;
    public final ModConfigSpec.ConfigValue<Integer> bouquetHeartsRequirement;
    public final ModConfigSpec.ConfigValue<Integer> villagerMaxHealth;
    public final ModConfigSpec.ConfigValue<Boolean> allowVillagerTeleporting;
    public final ModConfigSpec.ConfigValue<Double> villagerMinTeleportationDistance;
    public final ModConfigSpec.ConfigValue<Integer> villagerPathfindingDistance;
    public final ModConfigSpec.ConfigValue<Integer> villagerFollowRange;
    public final ModConfigSpec.ConfigValue<Integer> childInitialHearts;
    public final ModConfigSpec.ConfigValue<Integer> greetHeartsThreshold;
    public final ModConfigSpec.ConfigValue<Integer> greetAfterDays;
    public final ModConfigSpec.ConfigValue<Double> geneticImmigrantChance;
    public final ModConfigSpec.ConfigValue<Double> traitChance;
    public final ModConfigSpec.ConfigValue<Double> traitInheritChance;
    public final ModConfigSpec.ConfigValue<Double> nightOwlChance;
    public final ModConfigSpec.ConfigValue<Boolean> allowAnyNightOwl;
    public final ModConfigSpec.ConfigValue<Integer> heartsForPardonHit;
    public final ModConfigSpec.ConfigValue<Integer> pardonPlayerTicks;
    public final ModConfigSpec.ConfigValue<Boolean> guardsTargetMonsters;
    public final ModConfigSpec.ConfigValue<Double> maleVillagerHeightFactor;
    public final ModConfigSpec.ConfigValue<Double> femaleVillagerHeightFactor;
    public final ModConfigSpec.ConfigValue<Double> maleVillagerWidthFactor;
    public final ModConfigSpec.ConfigValue<Double> femaleVillagerWidthFactor;
    public final ModConfigSpec.ConfigValue<Boolean> useMCAVoices;
    public final ModConfigSpec.ConfigValue<Boolean> useVanillaVoices;
    public final ModConfigSpec.ConfigValue<Double> interactionChanceFatigue;
    public final ModConfigSpec.ConfigValue<Integer> interactionFatigueCooldown;
    public final ModConfigSpec.ConfigValue<Integer> villagerHealthBonusPerLevel;
    public final ModConfigSpec.ConfigValue<Integer> burnedClothingTickLength;
    public final ModConfigSpec.ConfigValue<Double> coloredHairChance;
    public final ModConfigSpec.ConfigValue<Integer> heartsRequiredToAutoSpawnGravestone;
    public final ModConfigSpec.ConfigValue<String> defaultHeadstoneType;
    public final ModConfigSpec.ConfigValue<Boolean> enableMourning;
    public final ModConfigSpec.ConfigValue<Boolean> useSmarterDoorAI;
    public final ModConfigSpec.ConfigValue<Integer> procreationCooldown;
    public final ModConfigSpec.ConfigValue<Boolean> trackVillagerPosition;
    public final ModConfigSpec.ConfigValue<Integer> trackVillagerPositionEveryNTicks;
    public final ModConfigSpec.ConfigValue<Double> guardSpawnFraction;
    public final ModConfigSpec.ConfigValue<List<? extends String>> guardEquipment;
    public final ModConfigSpec.ConfigValue<List<? extends String>> archerEquipment;
    public final ModConfigSpec.ConfigValue<Double> taxesFactor;
    public final ModConfigSpec.ConfigValue<Integer> taxSeason;
    public final ModConfigSpec.ConfigValue<Double> marriageChancePerMinute;
    public final ModConfigSpec.ConfigValue<Double> adventurerAtInnChancePerMinute;
    public final ModConfigSpec.ConfigValue<Integer> adventurerStayTime;
    public final ModConfigSpec.ConfigValue<Double> villagerProcreationChancePerMinute;
    public final ModConfigSpec.ConfigValue<Integer> bountyHunterInterval;
    public final ModConfigSpec.ConfigValue<Integer> bountyHunterHearts;
    public final ModConfigSpec.ConfigValue<Boolean> innSpawnsAdventurers;
    public final ModConfigSpec.ConfigValue<Boolean> innSpawnsCultists;
    public final ModConfigSpec.ConfigValue<Boolean> innSpawnsWanderingTraders;
    public final ModConfigSpec.ConfigValue<Double> fractionOfVanillaVillages;
    public final ModConfigSpec.ConfigValue<Double> fractionOfVanillaZombies;
    public final ModConfigSpec.ConfigValue<Integer> minimumBuildingsToBeConsideredAVillage;
    public final ModConfigSpec.ConfigValue<List<? extends String>> villagerDimensionBlacklist;
    public final ModConfigSpec.ConfigValue<List<? extends String>> allowedSpawnReasons;
    public final ModConfigSpec.ConfigValue<List<? extends String>> villagerInteractionItemBlacklist;
    public final ModConfigSpec.ConfigValue<Boolean> enableAutoScanByDefault;
    public final ModConfigSpec.ConfigValue<Integer> giftDesaturationQueueLength;
    public final ModConfigSpec.ConfigValue<Double> giftDesaturationFactor;
    public final ModConfigSpec.ConfigValue<Double> giftDesaturationExponent;
    public final ModConfigSpec.ConfigValue<Double> giftSatisfactionFactor;
    public final ModConfigSpec.ConfigValue<Double> giftMoodEffect;
    public final ModConfigSpec.ConfigValue<Double> baseGiftMoodEffect;
    public final ModConfigSpec.ConfigValue<Integer> giftDesaturationReset;
    public final ModConfigSpec.ConfigValue<Boolean> allowPlayerMarriage;
    public final ModConfigSpec.ConfigValue<Integer> minBuildingSize;
    public final ModConfigSpec.ConfigValue<Integer> maxBuildingSize;
    public final ModConfigSpec.ConfigValue<Integer> maxBuildingRadius;
    public final ModConfigSpec.ConfigValue<Integer> minPillarHeight;
    public final ModConfigSpec.ConfigValue<Integer> maxTreeHeight;
    public final ModConfigSpec.ConfigValue<List<? extends String>> maxTreeTicks;
    public final ModConfigSpec.ConfigValue<List<? extends String>> validTreeSources;
    public final ModConfigSpec.ConfigValue<Boolean> launchIntoDestiny;
    public final ModConfigSpec.ConfigValue<Boolean> allowDestinyCommandOnce;
    public final ModConfigSpec.ConfigValue<Boolean> allowDestinyCommandMoreThanOnce;
    public final ModConfigSpec.ConfigValue<Boolean> allowDestinyTeleportation;
    public final ModConfigSpec.ConfigValue<Boolean> allowLimitedPlayerEditor;
    public final ModConfigSpec.ConfigValue<Boolean> allowFullPlayerEditor;
    public final ModConfigSpec.ConfigValue<Boolean> bypassTraitRestrictions;
    public final ModConfigSpec.ConfigValue<Boolean> useModernUSANamesOnly;
    public final ModConfigSpec.ConfigValue<List<? extends String>> guardsTargetEntities;
    public final ModConfigSpec.ConfigValue<List<? extends String>> unSafeBlocksToTeleportOn;
    public final ModConfigSpec.ConfigValue<List<? extends String>> structuresInRumors;
    public final ModConfigSpec.ConfigValue<List<? extends String>> professionConversionsMap;
    public final ModConfigSpec.ConfigValue<List<? extends String>> taxesMap;
    private final Config.DecodedMapCache<String, String> destinyLocationsToTranslationCache =
            new Config.DecodedMapCache<>(String.class, String.class, "destinyLocationsToTranslationMap");
    private final Config.DecodedMapCache<String, Boolean> enabledTraitsCache =
            new Config.DecodedMapCache<>(String.class, Boolean.class, "enabledTraits");
    private final Config.DecodedMapCache<String, EquipmentSet> guardEquipmentCache =
            new Config.DecodedMapCache<>(String.class, EquipmentSet.class, "guardEquipment");
    private final Config.DecodedMapCache<String, EquipmentSet> archerEquipmentCache =
            new Config.DecodedMapCache<>(String.class, EquipmentSet.class, "archerEquipment");
    private final Config.DecodedMapCache<String, Integer> maxTreeTicksCache =
            new Config.DecodedMapCache<>(String.class, Integer.class, "maxTreeTicks");
    private final Config.DecodedMapCache<String, Integer> guardsTargetEntitiesCache =
            new Config.DecodedMapCache<>(String.class, Integer.class, "guardsTargetEntities");
    private final Config.DecodedMapCache<String, String> professionConversionsCache =
            new Config.DecodedMapCache<>(String.class, String.class, "professionConversionsMap");
    private final Config.DecodedMapCache<String, Float> taxesCache =
            new Config.DecodedMapCache<>(String.class, Float.class, "taxesMap");

    ServerConfig(ModConfigSpec.Builder builder) {
        builder.translation("mca.configuration.section.ai").push("ai");
        enableVillagerChatAI = builder

                .comment("Enables the AI chat for villagers.")
                .translation("mca.configuration.enableVillagerChatAI")
                .define("enableVillagerChatAI", false);
        villagerChatAIModel = builder

                .comment("AI model to use for villager chat.")
                .translation("mca.configuration.villagerChatAIModel")
                .define("villagerChatAIModel", "default");
        villagerChatAIUseTools = builder

                .comment("Villagers try to follow commands like \"follow me\", ...")
                .translation("mca.configuration.villagerChatAIUseTools")
                .define("villagerChatAIUseTools", false);
        villagerChatAIContextPermissionLevel = builder

                .comment("Permission level required to edit ChatAI context prompts.")
                .translation("mca.configuration.villagerChatAIContextPermissionLevel")
                .defineInRange("villagerChatAIContextPermissionLevel", 3, 0, 4);
        builder.pop();

        builder.translation("mca.configuration.section.destiny").push("destiny");
        allowBodyCustomizationInDestiny = builder

                .comment("Whether body customization (e.g., height, size) is available in the Destiny editor.")
                .translation("mca.configuration.allowBodyCustomizationInDestiny")
                .define("allowBodyCustomizationInDestiny", true);
        allowTraitCustomizationInDestiny = builder

                .comment("Whether trait customization is available in the Destiny editor.")
                .translation("mca.configuration.allowTraitCustomizationInDestiny")
                .define("allowTraitCustomizationInDestiny", true);
        destinySpawnLocations = builder

                .comment("Locations where the Destiny feature can teleport the player. <a href=\"https://github.com/Luke100000/minecraft-comes-alive/wiki/Custom-Rumors-and-Destiny-Structures\">Wiki</a>")
                .translation("mca.configuration.destinySpawnLocations")
                .defineListAllowEmpty("destinySpawnLocations", List.of(
            "somewhere",
            "minecraft:shipwreck_beached",
            "minecraft:village_desert",
            "minecraft:village_taiga",
            "minecraft:village_snowy",
            "minecraft:village_plains",
            "minecraft:village_savanna",
            "minecraft:ancient_city"
    ), () -> "", Config::isDestinySelector);
        autoDiscoverDestinyLocations = builder

                .comment("Automatically adds registered village structures to the Destiny screen.")
                .translation("mca.configuration.autoDiscoverDestinyLocations")
                .define("autoDiscoverDestinyLocations", true);
        destinySpawnLocationBlacklist = builder

                .comment("Removes matching locations from the Destiny screen after manual and automatic locations are combined.")
                .translation("mca.configuration.destinySpawnLocationBlacklist")
                .defineListAllowEmpty("destinySpawnLocationBlacklist", List.of(), () -> "", value -> value instanceof String);
        destinyOverworldOnly = builder

                .comment("Restricts dimension-bound Destiny destinations to the Overworld.")
                .translation("mca.configuration.destinyOverworldOnly")
                .define("destinyOverworldOnly", false);
        destinyDimensionBlacklist = builder

                .comment("Removes Destiny destinations from matching dimensions after Minecraft determines where they can generate.")
                .translation("mca.configuration.destinyDimensionBlacklist")
                .defineListAllowEmpty("destinyDimensionBlacklist", List.of(), () -> "", value -> value instanceof String);
        destinyLocationsToTranslationMap = builder

                .comment("Maps Destiny locations to translation keys for UI text.")
                .translation("mca.configuration.destinyLocationsToTranslationMap")
                .defineListAllowEmpty("destinyLocationsToTranslationMap", Config.encodeMap(Map.of(
            "default", "destiny.story.travelling",
            "minecraft:shipwreck_beached", "destiny.story.sailing"
    )), () -> "", value -> Config.isMapEntry(value, String.class, String.class));
        launchIntoDestiny = builder

                .comment("Launches a player into Destiny feature when they first join.")
                .translation("mca.configuration.launchIntoDestiny")
                .define("launchIntoDestiny", true);
        allowDestinyCommandOnce = builder

                .comment("Allows the player to modify their Destiny once via command.")
                .translation("mca.configuration.allowDestinyCommandOnce")
                .define("allowDestinyCommandOnce", true);
        allowDestinyCommandMoreThanOnce = builder

                .comment("Allows the player to modify their Destiny multiple times via command.")
                .translation("mca.configuration.allowDestinyCommandMoreThanOnce")
                .define("allowDestinyCommandMoreThanOnce", false);
        allowDestinyTeleportation = builder

                .comment("Players can teleport to Destiny locations.")
                .translation("mca.configuration.allowDestinyTeleportation")
                .define("allowDestinyTeleportation", true);
        builder.pop();

        builder.translation("mca.configuration.section.mod_features").push("mod_features");
        overwriteOriginalVillagers = builder

                .comment("Overwrite newly spawned vanilla villagers with MCA villagers. If set to false, original villagers will remain unchanged.")
                .translation("mca.configuration.overwriteOriginalVillagers")
                .define("overwriteOriginalVillagers", true);
        moddedVillagerWhitelist = builder

                .comment("A whitelist of modded villagers to be converted into MCA villagers.")
                .translation("mca.configuration.moddedVillagerWhitelist")
                .defineListAllowEmpty("moddedVillagerWhitelist", List.of(), () -> "",
                        value -> Config.isLoadSafeRegistryId(value, Registries.ENTITY_TYPE));
        overwriteOriginalZombieVillagers = builder

                .comment("Overwrite vanilla zombie villagers with MCA zombie villagers.")
                .translation("mca.configuration.overwriteOriginalZombieVillagers")
                .define("overwriteOriginalZombieVillagers", true);
        overwriteAllZombiesWithZombieVillagers = builder

                .comment("Overwrite all zombies (not just zombie villagers) with MCA zombie villagers. May cause unpredictable behavior.")
                .translation("mca.configuration.overwriteAllZombiesWithZombieVillagers")
                .define("overwriteAllZombiesWithZombieVillagers", false);
        moddedZombieVillagerWhitelist = builder

                .comment("Whitelist of modded zombie villagers to be converted into MCA zombie villagers.")
                .translation("mca.configuration.moddedZombieVillagerWhitelist")
                .defineListAllowEmpty("moddedZombieVillagerWhitelist", List.of(), () -> "",
                        value -> Config.isLoadSafeRegistryId(value, Registries.ENTITY_TYPE));
        babyZombieChance = builder

                .comment("Chance (0-1) that a spawned zombie will be a baby zombie.")
                .translation("mca.configuration.babyZombieChance")
                .defineInRange("babyZombieChance", 0.25, 0.0, 1.0);
        villagerTagsHacks = builder

                .comment("Injects MCA villagers into the villager tag.")
                .translation("mca.configuration.villagerTagsHacks")
                .define("villagerTagsHacks", true);
        enableInfection = builder

                .comment("Enables the villager infection system. Infected villagers can turn into zombie villagers after some time.")
                .translation("mca.configuration.enableInfection")
                .define("enableInfection", true);
        allowGrimReaper = builder

                .comment("Allows summoning of the Grim Reaper entity.")
                .translation("mca.configuration.allowGrimReaper")
                .define("allowGrimReaper", true);
        villagerChatPrefix = builder

                .comment("Prefix used in villager chat messages.")
                .translation("mca.configuration.villagerChatPrefix")
                .define("villagerChatPrefix", "");
        canHurtBabies = builder

                .comment("If true, players can damage baby villagers.")
                .translation("mca.configuration.canHurtBabies")
                .define("canHurtBabies", true);
        enterVillageNotification = builder

                .comment("Whether to show notifications when entering a village.")
                .translation("mca.configuration.enterVillageNotification")
                .define("enterVillageNotification", true);
        villagerMarriageNotification = builder

                .comment("Whether to show notifications when villagers get married.")
                .translation("mca.configuration.villagerMarriageNotification")
                .define("villagerMarriageNotification", true);
        villagerBirthNotification = builder

                .comment("Whether to show notifications when a villager gives birth.")
                .translation("mca.configuration.villagerBirthNotification")
                .define("villagerBirthNotification", true);
        innArrivalNotification = builder

                .comment("Whether to show notifications when a visitor arrives at the inn.")
                .translation("mca.configuration.innArrivalNotification")
                .define("innArrivalNotification", true);
        villagerRestockNotification = builder

                .comment("Whether to show notifications when a villager restocks their trades.")
                .translation("mca.configuration.villagerRestockNotification")
                .define("villagerRestockNotification", true);
        showNotificationsAsChat = builder

                .comment("If true, all notifications (village entry, marriage, birth, etc.) are shown in the chat instead of above the hotbar.")
                .translation("mca.configuration.showNotificationsAsChat")
                .define("showNotificationsAsChat", false);
        giveAdvancementBooks = builder

                .comment("If true, MCA book rewards are granted from advancements.")
                .translation("mca.configuration.giveAdvancementBooks")
                .define("giveAdvancementBooks", true);
        heartsToBeConsideredAsFriend = builder

                .comment("The number of hearts required for a villager to consider the player a friend.")
                .translation("mca.configuration.heartsToBeConsideredAsFriend")
                .defineInRange("heartsToBeConsideredAsFriend", 40, 0, 10000);
        enableVillagerMailingPlayers = builder

                .comment("Enables MCA villagers to send letters or mail to players.")
                .translation("mca.configuration.enableVillagerMailingPlayers")
                .define("enableVillagerMailingPlayers", true);
        enableGenderCheckForPlayers = builder

                .comment("Check for matching gender when trying to marry a villager.")
                .translation("mca.configuration.enableGenderCheckForPlayers")
                .define("enableGenderCheckForPlayers", true);
        zombieBiteInfectionChance = builder

                .comment("Chance (0-1) for infection when bitten by a zombie.")
                .translation("mca.configuration.zombieBiteInfectionChance")
                .defineInRange("zombieBiteInfectionChance", 0.05, 0.0, 1.0);
        infectionChanceDecreasePerLevel = builder

                .comment("Reduction in infection chance per villager trading level.")
                .translation("mca.configuration.infectionChanceDecreasePerLevel")
                .defineInRange("infectionChanceDecreasePerLevel", 0.25, 0.0, 1.0);
        infectionTime = builder

                .comment("Duration (in ticks) until a villager turns into a zombie after infection. 20 ticks = 1 second; 72000 ticks = 1 hour.")
                .translation("mca.configuration.infectionTime")
                .defineInRange("infectionTime", 72000, 1, 100000000);
        builder.pop();

        builder.translation("mca.configuration.section.villager_behavior").push("villager_behavior");
        babyItemGrowUpTime = builder

                .comment("Time (in ticks) until a baby grows up when held as an item.")
                .translation("mca.configuration.babyItemGrowUpTime")
                .defineInRange("babyItemGrowUpTime", 24000, 0, 100000000);
        villagerMaxAgeTime = builder

                .comment("Maximum villager lifetime in ticks (Time to grow fully up).")
                .translation("mca.configuration.villagerMaxAgeTime")
                .defineInRange("villagerMaxAgeTime", 384000, 0, 100000000);
        enabledTraits = builder

                .comment("Map of enabled traits. Keys are trait IDs, values are true/false.")
                .translation("mca.configuration.enabledTraits")
                .defineListAllowEmpty("enabledTraits", List.of(), () -> "", value -> Config.isMapEntry(value, String.class, Boolean.class));
        twinBabyChance = builder

                .comment("Fraction (0-1) of babies that are born as twins.")
                .translation("mca.configuration.twinBabyChance")
                .defineInRange("twinBabyChance", 0.05, 0.0, 1.0);
        marriageHeartsRequirement = builder

                .comment("Number of hearts required to marry a villager.")
                .translation("mca.configuration.marriageHeartsRequirement")
                .defineInRange("marriageHeartsRequirement", 100, 1, 10000);
        engagementHeartsRequirement = builder

                .comment("Number of hearts required to get engaged to a villager.")
                .translation("mca.configuration.engagementHeartsRequirement")
                .defineInRange("engagementHeartsRequirement", 50, 0, 10000);
        bouquetHeartsRequirement = builder

                .comment("Number of hearts required to give a bouquet to a villager.")
                .translation("mca.configuration.bouquetHeartsRequirement")
                .defineInRange("bouquetHeartsRequirement", 10, 0, 10000);
        villagerMaxHealth = builder

                .comment("Maximum health of a villager.")
                .translation("mca.configuration.villagerMaxHealth")
                .defineInRange("villagerMaxHealth", 20, 1, 10000);
        allowVillagerTeleporting = builder

                .comment("If true, allows stuck villagers to teleport to a safe location. Disabled by default as it can cause villagers to disappear unexpectedly.")
                .translation("mca.configuration.allowVillagerTeleporting")
                .define("allowVillagerTeleporting", false);
        villagerMinTeleportationDistance = builder

                .comment("Minimum squared distance at which teleportation becomes possible for villagers.")
                .translation("mca.configuration.villagerMinTeleportationDistance")
                .defineInRange("villagerMinTeleportationDistance", 128, 0.0, 10000.0);
        villagerPathfindingDistance = builder

                .comment("Maximum geometric path horizon used for long-range villager destinations such as beds.")
                .translation("mca.configuration.villagerPathfindingDistance")
                .defineInRange("villagerPathfindingDistance", 160, 16, 256);
        villagerFollowRange = builder

                .comment("Vanilla follow-range attribute for villagers.")
                .translation("mca.configuration.villagerFollowRange")
                .defineInRange("villagerFollowRange", 48, 16, 64);
        childInitialHearts = builder

                .comment("Number of hearts a child starts with towards their parent.")
                .translation("mca.configuration.childInitialHearts")
                .defineInRange("childInitialHearts", 100, -10000, 10000);
        greetHeartsThreshold = builder

                .comment("Number of hearts required for a villager to greet the player.")
                .translation("mca.configuration.greetHeartsThreshold")
                .defineInRange("greetHeartsThreshold", 75, 0, 10000);
        greetAfterDays = builder

                .comment("Number of in-game days after which villagers begin greeting the player.")
                .translation("mca.configuration.greetAfterDays")
                .defineInRange("greetAfterDays", 1, 0, 1000000);
        geneticImmigrantChance = builder

                .comment("Fraction (0-1) of villagers who will have biome-independent skin tones.")
                .translation("mca.configuration.geneticImmigrantChance")
                .defineInRange("geneticImmigrantChance", 0.2, 0.0, 1.0);
        traitChance = builder

                .comment("Chance multiplier (0-1) of a villager having one trait.")
                .translation("mca.configuration.traitChance")
                .defineInRange("traitChance", 0.25, 0.0, 1.0);
        traitInheritChance = builder

                .comment("Chance multiplier (0-1) of a child inheriting a parent's personality trait.")
                .translation("mca.configuration.traitInheritChance")
                .defineInRange("traitInheritChance", 0.5, 0.0, 1.0);
        nightOwlChance = builder

                .comment("Fraction (0-1) of villagers who are night owls (awake at night, asleep during the day).")
                .translation("mca.configuration.nightOwlChance")
                .worldRestart()
                .defineInRange("nightOwlChance", 0.5, 0.0, 1.0);
        allowAnyNightOwl = builder

                .comment("Allows all villagers to potentially become night owls.")
                .translation("mca.configuration.allowAnyNightOwl")
                .worldRestart()
                .define("allowAnyNightOwl", false);
        heartsForPardonHit = builder

                .comment("For every X hearts, players may hit a villager once without guards attacking them.")
                .translation("mca.configuration.heartsForPardonHit")
                .defineInRange("heartsForPardonHit", 30, 1, 10000);
        pardonPlayerTicks = builder

                .comment("Time (in ticks) before a player's pardon resets after hitting a villager.")
                .translation("mca.configuration.pardonPlayerTicks")
                .defineInRange("pardonPlayerTicks", 1200, 1, 100000000);
        guardsTargetMonsters = builder

                .comment("If true, guards will attack all monsters (including modded ones). May cause guards to attack neutral mobs depending on configuration.")
                .translation("mca.configuration.guardsTargetMonsters")
                .define("guardsTargetMonsters", false);
        maleVillagerHeightFactor = builder

                .comment("Height scaling factor for male villagers.")
                .translation("mca.configuration.maleVillagerHeightFactor")
                .worldRestart()
                .defineInRange("maleVillagerHeightFactor", 0.9, 0.0, 1.0);
        femaleVillagerHeightFactor = builder

                .comment("Height scaling factor for female villagers.")
                .translation("mca.configuration.femaleVillagerHeightFactor")
                .worldRestart()
                .defineInRange("femaleVillagerHeightFactor", 0.85, 0.0, 1.0);
        maleVillagerWidthFactor = builder

                .comment("Width scaling factor for male villagers.")
                .translation("mca.configuration.maleVillagerWidthFactor")
                .worldRestart()
                .defineInRange("maleVillagerWidthFactor", 1.0, 0.0, 1.0);
        femaleVillagerWidthFactor = builder

                .comment("Width scaling factor for female villagers.")
                .translation("mca.configuration.femaleVillagerWidthFactor")
                .worldRestart()
                .defineInRange("femaleVillagerWidthFactor", 0.95, 0.0, 1.0);
        useMCAVoices = builder

                .comment("Enables MCA villager voice lines. Set to false to mute all villager sounds.")
                .translation("mca.configuration.useMCAVoices")
                .define("useMCAVoices", true);
        useVanillaVoices = builder

                .comment("If true, villagers use vanilla Minecraft voice sounds instead of MCA custom voices.")
                .translation("mca.configuration.useVanillaVoices")
                .define("useVanillaVoices", false);
        interactionChanceFatigue = builder

                .comment("Amount of interaction fatigue gained per interaction. Each fatigue point makes the next interaction harder.")
                .translation("mca.configuration.interactionChanceFatigue")
                .defineInRange("interactionChanceFatigue", 1.0, 0.0, 1.0);
        interactionFatigueCooldown = builder

                .comment("Time (in ticks) before fatigue resets after interacting with a villager.")
                .translation("mca.configuration.interactionFatigueCooldown")
                .defineInRange("interactionFatigueCooldown", 4800, 0, 100000000);
        villagerHealthBonusPerLevel = builder

                .comment("Extra health villagers gain per trading level.")
                .translation("mca.configuration.villagerHealthBonusPerLevel")
                .defineInRange("villagerHealthBonusPerLevel", 5, 0, 100);
        burnedClothingTickLength = builder

                .comment("Duration (in ticks) that burned clothing effects remain visible.")
                .translation("mca.configuration.burnedClothingTickLength")
                .defineInRange("burnedClothingTickLength", 3600, 0, 100000000);
        coloredHairChance = builder

                .comment("Chance (0-1) that a villager will spawn with colored hair.")
                .translation("mca.configuration.coloredHairChance")
                .defineInRange("coloredHairChance", 0.02, 0.0, 1.0);
        heartsRequiredToAutoSpawnGravestone = builder

                .comment("Minimum hearts required for a villager to automatically appear on a tombstone when they die. Family members always appear regardless of this threshold.")
                .translation("mca.configuration.heartsRequiredToAutoSpawnGravestone")
                .defineInRange("heartsRequiredToAutoSpawnGravestone", 10, 0, 10000);
        defaultHeadstoneType = builder

                .comment("The type of headstone that automatically spawns when a villager dies.")
                .translation("mca.configuration.defaultHeadstoneType")
                .define("defaultHeadstoneType", "cross_headstone");
        enableMourning = builder

                .comment("Enables personal and ambient villager mourning at occupied graves.")
                .translation("mca.configuration.enableMourning")
                .define("enableMourning", true);
        useSmarterDoorAI = builder

                .comment("Enables smarter villager door AI, allowing them to open gates as well.")
                .translation("mca.configuration.useSmarterDoorAI")
                .define("useSmarterDoorAI", false);
        procreationCooldown = builder

                .comment("Time (in ticks) before villagers can procreate again.")
                .translation("mca.configuration.procreationCooldown")
                .defineInRange("procreationCooldown", 72000, 0, 100000000);
        useModernUSANamesOnly = builder

                .comment("Use the USA name set instead of international names.")
                .translation("mca.configuration.useModernUSANamesOnly")
                .define("useModernUSANamesOnly", false);
        structuresInRumors = builder

                .comment("Structures that can be mentioned in Rumors conversation options.")
                .translation("mca.configuration.structuresInRumors")
                .defineListAllowEmpty("structuresInRumors", List.of(
            "minecraft:igloo",
            "minecraft:pyramid",
            "minecraft:ruined_portal_desert",
            "minecraft:ruined_portal_swamp",
            "minecraft:ruined_portal",
            "minecraft:ruined_portal_mountain",
            "minecraft:mansion",
            "minecraft:monument",
            "minecraft:shipwreck",
            "minecraft:shipwreck_beached",
            "minecraft:village_desert",
            "minecraft:village_taiga",
            "minecraft:village_snowy",
            "minecraft:village_plains",
            "minecraft:village_savanna",
            "minecraft:swamp_hut",
            "minecraft:mineshaft",
            "minecraft:jungle_pyramid",
            "minecraft:pillager_outpost",
            "minecraft:ancient_city"
    ), () -> "", Config::isResourceLocation);
        professionConversionsMap = builder

                .comment("Maps modded professions to MCA professions for clothing conversion. Only adult clothing is used; toddlers and children remain unchanged.")
                .translation("mca.configuration.professionConversionsMap")
                .defineListAllowEmpty("professionConversionsMap", Config.encodeMap(Map.of()), () -> "", Config::isProfessionConversionEntry);
        builder.pop();

        builder.translation("mca.configuration.section.tracker").push("tracker");
        trackVillagerPosition = builder

                .comment("Tracks villager positions for debugging or AI purposes. Slightly increases world size and CPU overhead.")
                .translation("mca.configuration.trackVillagerPosition")
                .define("trackVillagerPosition", true);
        trackVillagerPositionEveryNTicks = builder

                .comment("Number of ticks between position tracking updates for villagers.")
                .translation("mca.configuration.trackVillagerPositionEveryNTicks")
                .defineInRange("trackVillagerPositionEveryNTicks", 200, 1, 100000000);
        builder.pop();

        builder.translation("mca.configuration.section.village_behavior").push("village_behavior");
        archerArrowsIgnoreVillagers = builder

                .comment("If true, arrows fired by MCA archers pass through villagers instead of hitting them.")
                .translation("mca.configuration.archerArrowsIgnoreVillagers")
                .define("archerArrowsIgnoreVillagers", true);
        villagersInteractWithFenceGates = builder

                .comment("If true, MCA villagers may path through and open or close fence gates.")
                .translation("mca.configuration.villagersInteractWithFenceGates")
                .define("villagersInteractWithFenceGates", true);
        guardSpawnFraction = builder

                .comment("Fraction (0-1) of villagers that spawn as guards.")
                .translation("mca.configuration.guardSpawnFraction")
                .defineInRange("guardSpawnFraction", 0.175, 0.0, 1.0);
        guardEquipment = builder

                .comment("Equipment used by guards at each village equipment level.")
                .translation("mca.configuration.guardEquipment")
                .defineListAllowEmpty("guardEquipment", Config.encodeMap(ImmutableMap.<String, EquipmentSet>builder()
            .put("0", EquipmentSet.GUARD_0)
            .put("1", EquipmentSet.GUARD_1)
            .put("2", EquipmentSet.GUARD_2)
            .build()), () -> "", value -> Config.isMapEntry(value, String.class, EquipmentSet.class));
        archerEquipment = builder

                .comment("Equipment used by archers at each village equipment level.")
                .translation("mca.configuration.archerEquipment")
                .defineListAllowEmpty("archerEquipment", Config.encodeMap(ImmutableMap.<String, EquipmentSet>builder()
            .put("0", EquipmentSet.ARCHER_0)
            .put("1", EquipmentSet.ARCHER_1)
            .put("2", EquipmentSet.ARCHER_2)
            .build()), () -> "", value -> Config.isMapEntry(value, String.class, EquipmentSet.class));
        guardsTargetEntities = builder

                .comment("Map of entity names to guard attack priorities. Negative values indicate the entity should be ignored.")
                .translation("mca.configuration.guardsTargetEntities")
                .defineListAllowEmpty("guardsTargetEntities", Config.encodeMap(ImmutableMap.<String, Integer>builder()
            .put("minecraft:creeper", -1)
            .put("minecraft:drowned", 2)
            .put("minecraft:evoker", 3)
            .put("minecraft:husk", 2)
            .put("minecraft:illusioner", 3)
            .put("minecraft:phantom", 0)
            .put("minecraft:pillager", 3)
            .put("minecraft:ravager", 3)
            .put("minecraft:skeleton_horse", -1)
            .put("minecraft:vex", 0)
            .put("minecraft:vindicator", 4)
            .put("minecraft:zoglin", 2)
            .put("minecraft:zombie", 4)
            .put("minecraft:zombie_horse", -1)
            .put("minecraft:zombie_villager", 3)
            .put("minecraft:spider", 0)
            .put("minecraft:cave_spider", 0)
            .put("minecraft:slime", 0)
            .put("#minecraft:undead", 0)
            .put(MCA.MOD_ID + ":female_zombie_villager", 3)
            .put(MCA.MOD_ID + ":male_zombie_villager", 3)
            .build()), () -> "", value -> Config.isMapEntryWithLoadSafeRegistryKey(value, Registries.ENTITY_TYPE, true, Integer.class));
        taxesFactor = builder

                .comment("Multiplier of taxes paid by villages.")
                .translation("mca.configuration.taxesFactor")
                .defineInRange("taxesFactor", 0.5, 0.0, 1.0);
        taxSeason = builder

                .comment("Interval (in ticks) between tax collection seasons.")
                .translation("mca.configuration.taxSeason")
                .defineInRange("taxSeason", 168000, 1, 100000000);
        taxesMap = builder

                .comment("Map of tax items to their value in units. If item is too expensive, it may not be picked until tax budget is sufficient.")
                .translation("mca.configuration.taxesMap")
                .defineListAllowEmpty("taxesMap", Config.encodeMap(Map.of(
            "minecraft:emerald", 1.0f
    )), () -> "", value -> Config.isMapEntryWithLoadSafeRegistryKey(value, Registries.ITEM, false, Float.class));
        marriageChancePerMinute = builder

                .comment("Chance (0-1) per minute that a marriage event occurs in the village.")
                .translation("mca.configuration.marriageChancePerMinute")
                .defineInRange("marriageChancePerMinute", 0.05, 0.0, 1.0);
        adventurerAtInnChancePerMinute = builder

                .comment("Chance (0-1) per minute that an adventurer arrives at the inn.")
                .translation("mca.configuration.adventurerAtInnChancePerMinute")
                .defineInRange("adventurerAtInnChancePerMinute", 0.05, 0.0, 1.0);
        adventurerStayTime = builder

                .comment("Duration (in ticks) that adventurers stay at the inn.")
                .translation("mca.configuration.adventurerStayTime")
                .defineInRange("adventurerStayTime", 48000, 0, 100000000);
        villagerProcreationChancePerMinute = builder

                .comment("Chance (0-1) per minute that villagers will procreate.")
                .translation("mca.configuration.villagerProcreationChancePerMinute")
                .defineInRange("villagerProcreationChancePerMinute", 0.05, 0.0, 1.0);
        bountyHunterInterval = builder

                .comment("Interval (in ticks) at which bounty hunters attack the player if reputation is low.")
                .translation("mca.configuration.bountyHunterInterval")
                .defineInRange("bountyHunterInterval", 48000, 10, 100000000);
        bountyHunterHearts = builder

                .comment("Negative heart threshold for bounty hunter attacks.")
                .translation("mca.configuration.bountyHunterHearts")
                .defineInRange("bountyHunterHearts", -150, -10000, 10000);
        innSpawnsAdventurers = builder

                .comment("If true, the inn spawns adventurers.")
                .translation("mca.configuration.innSpawnsAdventurers")
                .define("innSpawnsAdventurers", true);
        innSpawnsCultists = builder

                .comment("If true, the inn spawns cultists.")
                .translation("mca.configuration.innSpawnsCultists")
                .define("innSpawnsCultists", true);
        innSpawnsWanderingTraders = builder

                .comment("If true, the inn spawns wandering traders.")
                .translation("mca.configuration.innSpawnsWanderingTraders")
                .define("innSpawnsWanderingTraders", true);
        fractionOfVanillaVillages = builder

                .comment("Fraction (0-1) of villages left as vanilla villages.")
                .translation("mca.configuration.fractionOfVanillaVillages")
                .defineInRange("fractionOfVanillaVillages", 0, 0.0, 1.0);
        fractionOfVanillaZombies = builder

                .comment("Fraction (0-1) of vanilla zombie villagers.")
                .translation("mca.configuration.fractionOfVanillaZombies")
                .defineInRange("fractionOfVanillaZombies", 0, 0.0, 1.0);
        minimumBuildingsToBeConsideredAVillage = builder

                .comment("Minimum number of buildings required to consider an area a village. Below this, it is considered a settlement and welcome notifications are suppressed.")
                .translation("mca.configuration.minimumBuildingsToBeConsideredAVillage")
                .defineInRange("minimumBuildingsToBeConsideredAVillage", 3, 0, 100000);
        villagerDimensionBlacklist = builder

                .comment("Dimensions where villagers should not be converted into MCA villagers.")
                .translation("mca.configuration.villagerDimensionBlacklist")
                .defineListAllowEmpty("villagerDimensionBlacklist", List.of(), () -> "", Config::isResourceLocation);
        allowedSpawnReasons = builder

                .comment("List of allowed spawn reasons for villager conversion.")
                .translation("mca.configuration.allowedSpawnReasons")
                .defineListAllowEmpty("allowedSpawnReasons", List.of(
            "natural",
            "structure"
    ), () -> "", Config::isSpawnReason);
        villagerInteractionItemBlacklist = builder

                .comment("List of items that villagers cannot be interacted with for mod compat.")
                .translation("mca.configuration.villagerInteractionItemBlacklist")
                .defineListAllowEmpty("villagerInteractionItemBlacklist", List.of(
            "minecraft:bucket"
    ), () -> "", value -> Config.isLoadSafeRegistryId(value, Registries.ITEM));
        enableAutoScanByDefault = builder

                .comment("If true, automatically scan for buildings. High CPU usage.")
                .translation("mca.configuration.enableAutoScanByDefault")
                .define("enableAutoScanByDefault", false);
        giftDesaturationQueueLength = builder

                .comment("Number of entries in the gift desaturation queue.")
                .translation("mca.configuration.giftDesaturationQueueLength")
                .defineInRange("giftDesaturationQueueLength", 16, 0, 1000000);
        giftDesaturationFactor = builder

                .comment("Factor controlling how much repeated gifts lose effectiveness.")
                .translation("mca.configuration.giftDesaturationFactor")
                .defineInRange("giftDesaturationFactor", 0.5, 0.0, 1.0);
        giftDesaturationExponent = builder

                .comment("Exponent applied to desaturation formula; reduces impact of expensive gifts.")
                .translation("mca.configuration.giftDesaturationExponent")
                .defineInRange("giftDesaturationExponent", 0.85, 0.0, 10000.0);
        giftSatisfactionFactor = builder

                .comment("Factor multiplying the satisfaction value to determine heart impact from gifts.")
                .translation("mca.configuration.giftSatisfactionFactor")
                .defineInRange("giftSatisfactionFactor", 0.33, 0.0, 1.0);
        giftMoodEffect = builder

                .comment("How much a gift influences a villager's mood.")
                .translation("mca.configuration.giftMoodEffect")
                .defineInRange("giftMoodEffect", 0.5, -10000.0, 10000.0);
        baseGiftMoodEffect = builder

                .comment("Base mood effect of a gift, independent of previous gifts.")
                .translation("mca.configuration.baseGiftMoodEffect")
                .defineInRange("baseGiftMoodEffect", 2, -10000.0, 10000.0);
        giftDesaturationReset = builder

                .comment("Time (in ticks) after which gift desaturation resets.")
                .translation("mca.configuration.giftDesaturationReset")
                .defineInRange("giftDesaturationReset", 24000, 1, 100000000);
        allowPlayerMarriage = builder

                .comment("Allow players to marry each other.")
                .translation("mca.configuration.allowPlayerMarriage")
                .define("allowPlayerMarriage", true);
        minBuildingSize = builder

                .comment("Minimum building size for buildings.")
                .translation("mca.configuration.minBuildingSize")
                .defineInRange("minBuildingSize", 32, 0, 100000);
        maxBuildingSize = builder

                .comment("Maximum building size for buildings.")
                .translation("mca.configuration.maxBuildingSize")
                .defineInRange("maxBuildingSize", 8192, 0, 100000);
        maxBuildingRadius = builder

                .comment("Maximum radius of a building from its center.")
                .translation("mca.configuration.maxBuildingRadius")
                .defineInRange("maxBuildingRadius", 320, 0, 100000);
        minPillarHeight = builder

                .comment("Minimum pillar height for Grim Reaper altars.")
                .translation("mca.configuration.minPillarHeight")
                .defineInRange("minPillarHeight", 2, 0, 100000);
        maxTreeHeight = builder

                .comment("Maximum tree height for chopping chores.")
                .translation("mca.configuration.maxTreeHeight")
                .defineInRange("maxTreeHeight", 8, 0, 100000);
        maxTreeTicks = builder

                .comment("Maximum ticks for valid tree log blocks before they are considered grown.")
                .translation("mca.configuration.maxTreeTicks")
                .defineListAllowEmpty("maxTreeTicks", Config.encodeMap(ImmutableMap.<String, Integer>builder()
            .put("#minecraft:logs", 60)
            .build()), () -> "", value -> Config.isMapEntryWithLoadSafeRegistryKey(value, Registries.BLOCK, true, Integer.class));
        validTreeSources = builder

                .comment("List of valid blocks that can serve as the base of trees.")
                .translation("mca.configuration.validTreeSources")
                .defineListAllowEmpty("validTreeSources", List.of(
            "minecraft:grass_block",
            "minecraft:dirt"
    ), () -> "", value -> Config.isLoadSafeRegistrySelector(value, Registries.BLOCK));
        unSafeBlocksToTeleportOn = builder

                .comment("List of blocks or tags that villagers will not teleport onto.")
                .translation("mca.configuration.unSafeBlocksToTeleportOn")
                .defineListAllowEmpty("unSafeBlocksToTeleportOn", List.of(
            "#minecraft:climbable",
            "#minecraft:fence_gates",
            "#minecraft:fences",
            "#minecraft:fire",
            "#minecraft:portals",
            "#minecraft:slabs",
            "#minecraft:stairs",
            "#minecraft:trapdoors",
            "#minecraft:walls"
    ), () -> "", value -> Config.isLoadSafeRegistrySelector(value, Registries.BLOCK));
        builder.pop();

        builder.translation("mca.configuration.section.player_customization").push("player_customization");
        addContentGloballyPermissionLevel = builder

                .comment("Permission level required to add or remove skins from the server-wide pool.")
                .translation("mca.configuration.addContentGloballyPermissionLevel")
                .defineInRange("addContentGloballyPermissionLevel", 3, 0, 4);
        allowPlayerSizeAdjustment = builder

                .comment("Allow players to modify their size.")
                .translation("mca.configuration.allowPlayerSizeAdjustment")
                .define("allowPlayerSizeAdjustment", true);
        scalePlayerHitboxWithSizeAndWidth = builder

                .comment("Scale player hitboxes using MCA size and width genetics.")
                .translation("mca.configuration.scalePlayerHitboxWithSizeAndWidth")
                .worldRestart()
                .define("scalePlayerHitboxWithSizeAndWidth", false);
        allowLimitedPlayerEditor = builder

                .comment("Allows limited access to player editor (name, gender, etc.).")
                .translation("mca.configuration.allowLimitedPlayerEditor")
                .define("allowLimitedPlayerEditor", true);
        allowFullPlayerEditor = builder

                .comment("Allows full access to the player editor including clothing and hair.")
                .translation("mca.configuration.allowFullPlayerEditor")
                .define("allowFullPlayerEditor", false);
        bypassTraitRestrictions = builder

                .comment("Allows players to bypass restrictions on traits that are normally blocked in the player editor.")
                .translation("mca.configuration.bypassTraitRestrictions")
                .define("bypassTraitRestrictions", false);
        builder.pop();
    }

    public Map<String, String> destinyLocationsToTranslationMap() {
        return destinyLocationsToTranslationCache.get(destinyLocationsToTranslationMap.get());
    }

    public Map<String, Boolean> enabledTraits() {
        return enabledTraitsCache.get(enabledTraits.get());
    }

    public Map<String, EquipmentSet> guardEquipment() {
        return guardEquipmentCache.get(guardEquipment.get());
    }

    public Map<String, EquipmentSet> archerEquipment() {
        return archerEquipmentCache.get(archerEquipment.get());
    }

    public Map<String, Integer> maxTreeTicks() {
        return maxTreeTicksCache.get(maxTreeTicks.get());
    }

    public Map<String, Integer> guardsTargetEntities() {
        return guardsTargetEntitiesCache.get(guardsTargetEntities.get());
    }

    public Map<String, String> professionConversionsMap() {
        return professionConversionsCache.get(professionConversionsMap.get());
    }

    public Map<String, Float> taxesMap() {
        return taxesCache.get(taxesMap.get());
    }

    public int villagerPathfindingDistance() {
        return villagerPathfindingDistance.get();
    }

    public int villagerFollowRange() {
        return villagerFollowRange.get();
    }
}
