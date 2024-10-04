package net.mca;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.mca.entity.ai.Traits;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Config implements Serializable {
    @Serial
    private static final long serialVersionUID = 956221997003825933L;

    private static final Config INSTANCE = loadOrCreate();
    private static Config serverConfig;

    public static Config getInstance() {
        return INSTANCE;
    }

    public static final int VERSION = 1;

    @SuppressWarnings("unused")
    public String README = "https://github.com/Luke100000/minecraft-comes-alive/wiki";

    public int version = 0;

    //mod features
    public boolean overwriteOriginalVillagers = true;
    public List<String> moddedVillagerWhitelist = List.of();
    public boolean overwriteOriginalZombieVillagers = true;
    public boolean overwriteAllZombiesWithZombieVillagers = false;
    public List<String> moddedZombieVillagerWhitelist = List.of();
    public float babyZombieChance = 0.25f;
    public boolean villagerTagsHacks = true;
    public boolean enableInfection = true;
    public boolean allowGrimReaper = true;
    public String villagerChatPrefix = "";
    public boolean canHurtBabies = true;
    public boolean enterVillageNotification = true;
    public boolean villagerMarriageNotification = true;
    public boolean villagerBirthNotification = true;
    public boolean innArrivalNotification = true;
    public boolean villagerRestockNotification = true;
    public boolean showNotificationsAsChat = false;
    public int heartsToBeConsideredAsFriend = 40;
    public boolean enableVillagerMailingPlayers = true;
    public boolean allowBodyCustomizationInDestiny = true;
    public boolean allowTraitCustomizationInDestiny = true;
    public boolean enableGenderCheckForPlayers = true;

    public float zombieBiteInfectionChance = 0.05f;
    public float infectionChanceDecreasePerLevel = 0.25f;
    public int infectionTime = 72000;

    //villager behavior
    public float twinBabyChance = 0.02f;
    public int marriageHeartsRequirement = 100;
    public int engagementHeartsRequirement = 50;
    public int bouquetHeartsRequirement = 10;
    public int babyItemGrowUpTime = 24000;
    public int villagerMaxAgeTime = 384000;
    public int villagerMaxHealth = 20;
    public boolean allowVillagerTeleporting = false;
    public double villagerMinTeleportationDistance = 64;
    public int childInitialHearts = 100;
    public int greetHeartsThreshold = 75;
    public int greetAfterDays = 1;
    public float geneticImmigrantChance = 0.2f;
    public float traitChance = 0.25f;
    public float traitInheritChance = 0.5f;
    public boolean bypassTraitRestrictions = false;
    public float nightOwlChance = 0.5f;
    public boolean allowAnyNightOwl = false;
    public int heartsForPardonHit = 30;
    public int pardonPlayerTicks = 1200;
    public boolean guardsTargetMonsters = false;
    public float maleVillagerHeightFactor = 0.9f;
    public float femaleVillagerHeightFactor = 0.85f;
    public float maleVillagerWidthFactor = 1.0f;
    public float femaleVillagerWidthFactor = 0.95f;
    public boolean showNameTags = true;
    public float nameTagDistance = 5.0f;
    public boolean useMCAVoices = true;
    public boolean useVanillaVoices = false;
    public float interactionChanceFatigue = 1.0f;
    public int interactionFatigueCooldown = 4800;
    public int villagerHealthBonusPerLevel = 5;
    public boolean useSquidwardModels = false;
    public boolean enableBoobs = true;
    public int burnedClothingTickLength = 3600;
    public float coloredHairChance = 0.02f;
    public int heartsRequiredToAutoSpawnGravestone = 10;
    public boolean useSmarterDoorAI = false;
    public int procreationCooldown = 72000;

    //tracker
    public boolean trackVillagerPosition = true;
    public int trackVillagerPositionEveryNTicks = 200;

    //AI
    public String _read_this_before_using_villager_ai = "https://github.com/Luke100000/minecraft-comes-alive/wiki/GPT3-based-conversations";
    public boolean enableVillagerChatAI = false;
    public int villagerChatAIIntelligence = 4;
    public String villagerChatAIEndpoint = "https://api.conczin.net/v1/mca/chat";
    public String villagerChatAIToken = "";
    public String villagerChatAIModel = "default";
    public String villagerChatAISystemPrompt = "";
    public boolean villagerChatAIUseLongTermMemory = false;
    public boolean villagerChatAIUseSharedLongTermMemory = false;
    public boolean villagerChatAIIncludeSessionInformation = false;

    public String inworldAIToken = "";
    public Map<UUID, String> inworldAIResourceNames = new HashMap<>();

    // TTS
    public boolean enableOnlineTTS = false;
    public String villagerTTSServer = "http://api.rk.conczin.net/";

    //village behavior
    public float guardSpawnFraction = 0.175f;
    public float taxesFactor = 0.5f;
    public int taxSeason = 168000;
    public float marriageChancePerMinute = 0.05f;
    public float adventurerAtInnChancePerMinute = 0.05f;
    public int adventurerStayTime = 48000;
    public float villagerProcreationChancePerMinute = 0.05f;
    public int bountyHunterInterval = 48000;
    public int bountyHunterHearts = -150;
    public boolean innSpawnsAdventurers = true;
    public boolean innSpawnsCultists = true;
    public boolean innSpawnsWanderingTraders = true;
    public float fractionOfVanillaVillages = 0;
    public float fractionOfVanillaZombies = 0;
    public int minimumBuildingsToBeConsideredAVillage = 3;
    public List<String> villagerDimensionBlacklist = List.of();
    public List<String> allowedSpawnReasons = List.of(
            "natural",
            "structure"
    );
    public List<String> villagerInteractionItemBlacklist = List.of(
            "minecraft:bucket"
    );
    public boolean enableAutoScanByDefault = false;

    //skin library
    public String immersiveLibraryUrl = "https://mca.conczin.net";
    public boolean allowEveryoneToAddContentGlobally = false;

    //gifts
    public int giftDesaturationQueueLength = 16;
    public float giftDesaturationFactor = 0.5f;
    public double giftDesaturationExponent = 0.85;
    public double giftSatisfactionFactor = 0.33;
    public float giftMoodEffect = 0.5f;
    public float baseGiftMoodEffect = 2;
    public int giftDesaturationReset = 24000;

    //player interactions
    public boolean allowPlayerMarriage = true;

    //structure settings
    public int minBuildingSize = 32;
    public int maxBuildingSize = 8192;
    public int maxBuildingRadius = 320;
    public int minPillarHeight = 2;
    public int maxTreeHeight = 8;
    public Map<String, Integer> maxTreeTicks = ImmutableMap.<String, Integer>builder()
            .put("#minecraft:logs", 60)
            .build();
    public List<String> validTreeSources = List.of(
            "minecraft:grass_block",
            "minecraft:dirt"
    );

    //player customization
    public boolean launchIntoDestiny = true;
    public boolean allowDestinyCommandOnce = true;
    public boolean allowDestinyCommandMoreThanOnce = false;
    public boolean allowDestinyTeleportation = true;
    public boolean enablePlayerShaders = true;
    public boolean enableVillagerPlayerModel = true;
    public boolean forceVillagerPlayerModel = false;
    public boolean allowLimitedPlayerEditor = true;
    public boolean allowFullPlayerEditor = false;
    public boolean allowPlayerSizeAdjustment = true;
    public boolean adjustPlayerEyesToHeight = false;

    public boolean useModernUSANamesOnly = false;

    public Map<String, Integer> guardsTargetEntities = ImmutableMap.<String, Integer>builder()
            .put("minecraft:creeper", -1)
            .put("minecraft:drowned", 2)
            .put("minecraft:evoker", 3)
            .put("minecraft:husk", 2)
            .put("minecraft:illusioner", 3)
            .put("minecraft:pillager", 3)
            .put("minecraft:ravager", 3)
            .put("minecraft:vex", 0)
            .put("minecraft:vindicator", 4)
            .put("minecraft:zoglin", 2)
            .put("minecraft:zombie", 4)
            .put("minecraft:zombie_villager", 3)
            .put("minecraft:spider", 0)
            .put("minecraft:skeleton", 0)
            .put("minecraft:slime", 0)
            .put(MCA.MOD_ID + ":female_zombie_villager", 3)
            .put(MCA.MOD_ID + ":male_zombie_villager", 3)
            .build();

    public List<String> villagerPathfindingBlacklist = List.of(
            "#minecraft:climbable",
            "#minecraft:fence_gates",
            "#minecraft:fences",
            "#minecraft:fire",
            "#minecraft:portals",
            "#minecraft:slabs",
            "#minecraft:stairs",
            "#minecraft:trapdoors",
            "#minecraft:walls"
    );

    public List<String> structuresInRumors = List.of(
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
    );

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

    public Map<String, String> destinyLocationsToTranslationMap = Map.of(
            "default", "destiny.story.travelling",
            "minecraft:shipwreck_beached", "destiny.story.sailing"
    );

    public Map<String, String> professionConversionsMap = Map.of();

    public Map<String, String> shaderLocationsMap = Map.of(
            "color_blind", "minecraft:shaders/post/desaturate.json",
            "sirben", "mca:shaders/post/sirben.json"
    );

    public Map<String, String> playerRendererBlacklist = Map.of(
            "morph", "arms",
            "firstpersonmod", "arms",
            "firstperson", "arms",
            "epicfight", "all"
    );

    public Map<String, Boolean> enabledTraits = new HashMap<>();

    public Map<String, Float> taxesMap = Map.of(
            "minecraft:emerald", 1.0f
    );

    public static File getConfigFile() {
        return new File("./config/mca.json");
    }

    public void autocomplete() {
        for (Traits.Trait trait : Traits.Trait.values()) {
            enabledTraits.putIfAbsent(trait.id(), true);
        }
    }

    public void save() {
        autocomplete();

        try (FileWriter writer = new FileWriter(getConfigFile())) {
            version = VERSION;
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(this, writer);
        } catch (IOException e) {
            MCA.LOGGER.error(e);
        }
    }

    public static Config loadOrCreate() {
        File file = getConfigFile();
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                Config config = gson.fromJson(reader, Config.class);
                if (config == null || config.version != VERSION) {
                    config = new Config();
                }
                config.save();
                return config;
            } catch (JsonSyntaxException e) {
                MCA.LOGGER.error("");
                MCA.LOGGER.error("|||||||||||||||||||||||||||||||||||||||||||||||||||||||||");
                MCA.LOGGER.error("Minecraft Comes Alive config (mca.json) failed to launch!");
                MCA.LOGGER.error(e);
                MCA.LOGGER.error("|||||||||||||||||||||||||||||||||||||||||||||||||||||||||");
                MCA.LOGGER.error("");
            } catch (IOException e) {
                MCA.LOGGER.error(e);
            }
        }

        Config config = new Config();
        config.save();
        return config;
    }

    public static void setServerConfig(Config config) {
        serverConfig = config;
    }

    public static Config getServerConfig() {
        if (serverConfig == null) {
            return Config.getInstance();
        } else {
            return serverConfig;
        }
    }
}
