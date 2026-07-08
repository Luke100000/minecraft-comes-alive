package net.conczin.mca;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.conczin.mca.entity.EquipmentSet;
import net.conczin.mca.entity.ai.Traits;
import net.minecraft.util.Mth;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class Config extends CommonConfig {
    private static final int VERSION = 2;
    private static final Config INSTANCE = loadOrCreate();

    private static CommonConfig serverConfig;

    @SuppressWarnings("unused")
    public String README = "https://github.com/Luke100000/minecraft-comes-alive/wiki";
    public int version = 0;

    //////////////////
    // Mod features //
    //////////////////

    /**
     * Overwrite newly spawned vanilla villagers with MCA villagers.
     * If set to false, original villagers will remain unchanged.
     */
    public boolean overwriteOriginalVillagers = true;

    /**
     * A whitelist of modded villagers to be converted into MCA villagers.
     * <p>
     * Note: Modded villagers must extend from the {@code Villager} class.
     * Example: Guard Villagers do not, and thus cannot be converted.
     */
    public List<String> moddedVillagerWhitelist = List.of();

    /**
     * Overwrite vanilla zombie villagers with MCA zombie villagers.
     */
    public boolean overwriteOriginalZombieVillagers = true;

    /**
     * Overwrite all zombies (not just zombie villagers)
     * with MCA zombie villagers. May cause unpredictable behavior.
     */
    public boolean overwriteAllZombiesWithZombieVillagers = false;

    /**
     * Whitelist of modded zombie villagers to be converted into MCA zombie villagers.
     */
    public List<String> moddedZombieVillagerWhitelist = List.of();

    /**
     * Chance (0–1) that a spawned zombie will be a baby zombie.
     */
    public float babyZombieChance = 0.25f;

    /**
     * Injects MCA villagers into the villager tag.
     */
    public boolean villagerTagsHacks = true;

    /**
     * Enables the villager infection system.
     * Infected villagers can turn into zombie villagers after some time.
     */
    public boolean enableInfection = true;

    /**
     * Allows summoning of the Grim Reaper entity.
     */
    public boolean allowGrimReaper = true;

    /**
     * Prefix used in villager chat messages.
     */
    public String villagerChatPrefix = "";

    /**
     * If true, players can damage baby villagers.
     */
    public boolean canHurtBabies = true;

    /**
     * Whether to show notifications when entering a village.
     */
    public boolean enterVillageNotification = true;

    /**
     * Whether to show notifications when villagers get married.
     */
    public boolean villagerMarriageNotification = true;

    /**
     * Whether to show notifications when a villager gives birth.
     */
    public boolean villagerBirthNotification = true;

    /**
     * Whether to show notifications when a visitor arrives at the inn.
     */
    public boolean innArrivalNotification = true;

    /**
     * Whether to show notifications when a villager restocks their trades.
     */
    public boolean villagerRestockNotification = true;

    /**
     * If true, all notifications (village entry, marriage, birth, etc.)
     * are shown in the chat instead of above the hotbar.
     */
    public boolean showNotificationsAsChat = false;

    /**
     * If true, MCA book rewards are granted from advancements.
     */
    public boolean giveAdvancementBooks = true;

    /**
     * The number of hearts required for a villager to consider the player a friend.
     */
    public int heartsToBeConsideredAsFriend = 40;

    /**
     * Enables MCA villagers to send letters or mail to players.
     */
    public boolean enableVillagerMailingPlayers = true;

    /**
     * Check for matching gender when trying to marry a villager.
     */
    public boolean enableGenderCheckForPlayers = true;

    /**
     * Chance (0–1) for infection when bitten by a zombie.
     */
    public float zombieBiteInfectionChance = 0.05f;

    /**
     * Reduction in infection chance per villager trading level.
     */
    public float infectionChanceDecreasePerLevel = 0.25f;

    /**
     * Duration (in ticks) until a villager turns into a zombie after infection.
     * 20 ticks = 1 second; 72000 ticks = 1 hour.
     */
    public int infectionTime = 72000;

    ///////////////////////
    // Villager behavior //
    ///////////////////////

    /**
     * Fraction (0–1) of babies that are born as twins.
     */
    public float twinBabyChance = 0.05f;

    /**
     * Number of hearts required to marry a villager.
     */
    public int marriageHeartsRequirement = 100;

    /**
     * Number of hearts required to get engaged to a villager.
     */
    public int engagementHeartsRequirement = 50;

    /**
     * Number of hearts required to give a bouquet to a villager.
     */
    public int bouquetHeartsRequirement = 10;

    /**
     * Maximum health of a villager.
     */
    public int villagerMaxHealth = 20;

    /**
     * If true, allows stuck villagers to teleport to a safe location.
     * Disabled by default as it can cause villagers to disappear unexpectedly.
     */
    public boolean allowVillagerTeleporting = false;

    /**
     * Minimum squared distance at which teleportation becomes possible for villagers.
     */
    public double villagerMinTeleportationDistance = 128;

    /**
     * Maximum pathfinding distance used when villagers walk to long-range memories such as beds.
     */
    public int villagerPathfindingDistance = 80;

    /**
     * Maximum follow-range attribute for villagers. Affects how far they pursue entities and how large the pathfinding search budget is.
     * Smaller values improve performance at the cost of reduced detection range.
     */
    public int villagerFollowRange = 48;


    /**
     * Number of hearts a child starts with towards their parent.
     */
    public int childInitialHearts = 100;

    /**
     * Number of hearts required for a villager to greet the player.
     */
    public int greetHeartsThreshold = 75;

    /**
     * Number of in-game days after which villagers begin greeting the player.
     */
    public int greetAfterDays = 1;

    /**
     * Fraction (0–1) of villagers who will have biome-independent skin tones.
     */
    public float geneticImmigrantChance = 0.2f;

    /**
     * Chance multiplier (0–1) of a villager having one trait.
     */
    public float traitChance = 0.25f;

    /**
     * Chance multiplier (0–1) of a child inheriting a parent’s personality trait.
     */
    public float traitInheritChance = 0.5f;

    /**
     * Allows the player to bypass restrictions on certain traits
     * that are normally blocked (e.g., Left-Handed, Diabetes, Vegetarian, etc.).
     */
    public boolean bypassTraitRestrictions = false;

    /**
     * Fraction (0–1) of villagers who are night owls (awake at night, asleep during the day).
     */
    public float nightOwlChance = 0.5f;

    /**
     * Allows all villagers to potentially become night owls.
     */
    public boolean allowAnyNightOwl = false;

    /**
     * For every X hearts, players may hit a villager once without guards attacking them.
     */
    public int heartsForPardonHit = 30;

    /**
     * Time (in ticks) before a player’s pardon resets after hitting a villager.
     */
    public int pardonPlayerTicks = 1200;

    /**
     * If true, guards will attack all monsters (including modded ones).
     * May cause guards to attack neutral mobs depending on configuration.
     */
    public boolean guardsTargetMonsters = false;

    /**
     * Height scaling factor for male villagers.
     */
    public float maleVillagerHeightFactor = 0.9f;

    /**
     * Height scaling factor for female villagers.
     */
    public float femaleVillagerHeightFactor = 0.85f;

    /**
     * Width scaling factor for male villagers.
     */
    public float maleVillagerWidthFactor = 1.0f;

    /**
     * Width scaling factor for female villagers.
     */
    public float femaleVillagerWidthFactor = 0.95f;

    /**
     * If true, shows villager name tags above their heads.
     */
    public boolean showNameTags = true;

    /**
     * Maximum distance at which name tags are visible.
     */
    public float nameTagDistance = 5.0f;

    /**
     * Enables MCA villager voice lines.
     * Set to false to mute all villager sounds.
     */
    public boolean useMCAVoices = true;

    /**
     * If true, villagers use vanilla Minecraft voice sounds
     * instead of MCA custom voices.
     */
    public boolean useVanillaVoices = false;

    /**
     * Amount of interaction fatigue gained per interaction.
     * Each fatigue point makes the next interaction harder.
     */
    public float interactionChanceFatigue = 1.0f;

    /**
     * Time (in ticks) before fatigue resets after interacting with a villager.
     */
    public int interactionFatigueCooldown = 4800;

    /**
     * Extra health villagers gain per trading level.
     */
    public int villagerHealthBonusPerLevel = 5;

    /**
     * If true, villagers use the vanilla “Squidward” (nose) model.
     */
    public boolean useSquidwardModels = false;

    /**
     * Enables female body features (visual only).
     */
    public boolean enableBoobs = true;

    /**
     * Duration (in ticks) that burned clothing effects remain visible.
     */
    public int burnedClothingTickLength = 3600;

    /**
     * Chance (0–1) that a villager will spawn with colored hair.
     */
    public float coloredHairChance = 0.02f;

    /**
     * Minimum hearts required for a villager to automatically appear
     * on a tombstone when they die.
     * Family members always appear regardless of this threshold.
     */
    public int heartsRequiredToAutoSpawnGravestone = 10;

    /**
     * The type of headstone that automatically spawns when a villager dies.
     * Options: "cross_headstone", "gravelling_headstone", "upright_headstone", "slanted_headstone", "wall_headstone",
     * "cobblestone_upright_headstone", "cobblestone_slanted_headstone", "wooden_upright_headstone", "wooden_slanted_headstone",
     * "golden_upright_headstone", "golden_slanted_headstone", "deepslate_upright_headstone", "deepslate_slanted_headstone"
     */
    public String defaultHeadstoneType = "cross_headstone";

    /**
     * Enables smarter villager door AI,
     * allowing them to open gates as well.
     */
    public boolean useSmarterDoorAI = false;

    /**
     * Time (in ticks) before villagers can procreate again.
     */
    public int procreationCooldown = 72000;


    /////////////
    // Tracker //
    /////////////

    /**
     * Tracks villager positions for debugging or AI purposes.
     * Slightly increases world size and CPU overhead.
     */
    public boolean trackVillagerPosition = true;

    /**
     * Number of ticks between position tracking updates for villagers.
     */
    public int trackVillagerPositionEveryNTicks = 200;


    ////////
    // AI //
    /// ////
    @SuppressWarnings("unused")
    public String _read_this_before_using_villager_ai =
            "https://github.com/Luke100000/minecraft-comes-alive/wiki/GPT3-based-conversations";

    /**
     * Enables the AI chat for villagers.
     */
    public boolean enableVillagerChatAI = false;

    /**
     * Chat completion endpoint for villager AI chat requests.
     */
    public String villagerChatAIEndpoint = "https://api.conczin.net/v1/mca/chat";

    /**
     * Villager try to follow commands like "follow me", ...
     */
    public boolean villagerChatAIUseTools = false;

    /**
     * Optional API token.
     */
    public String villagerChatAIToken = "";

    /**
     * AI model to use for villager chat.
     */
    public String villagerChatAIModel = "default";

    /**
     * System prompt used to guide global villager AI behavior.
     */
    public String villagerChatAISystemPrompt = "";

    /**
     * If true, the system prompt is prepended to the user message instead of sent as a separate system message.
     * Use this for OpenAI-compatible endpoints that ignore the system role.
     */
    public boolean villagerChatAIFuseSystemPrompt = false;

    /**
     * If true, AI uses long-term memory for persistent conversations.
     */
    public boolean villagerChatAIUseLongTermMemory = false;

    /**
     * If false, villager will have separate memories per player.
     */
    public boolean villagerChatAIUseSharedLongTermMemory = false;

    /**
     * If true, session-specific information is included in AI requests.
     * Only relevant if writing a custom backend.
     */
    public boolean villagerChatAIIncludeSessionInformation = false;

    /**
     * Inworld API token.
     */
    public String inworldAIToken = "";

    /**
     * See GitHub wiki.
     */
    public Map<UUID, String> inworldAIResourceNames = new HashMap<>();

    /**
     * Enables online TTS (text-to-speech) for villager dialogue.
     */
    public boolean enableOnlineTTS = false;

    /**
     * Online TTS model to use.
     */
    public String onlineTTSModel = "default";

    /**
     * URL of the online TTS server.
     */
    public String onlineTTSServer = "https://api-rk.conczin.net/";

    /**
     * Player2 API url.
     */
    public String player2Url = "http://127.0.0.1:4315/";

    /**
     * Elevenlabs API key.
     */
    public String elevenlabsPrivateAPIkey = "";

    /**
     * ElevenLabs TTS model to use.
     */
    public String elevenlabsModel = "eleven_turbo_v2_5";

    /**
     * List of male voice IDs for ElevenLabs TTS.
     */
    public List<String> elevenlabsMaleVoices = List.of(
            "ErXwobaYiN019PkySvjV",
            "VR6AewLTigWG4xSOukaG",
            "onwK4e9ZLuTAKqWW03F9",
            "onwK4e9ZLuTAKqWW03F9"
    );

    /**
     * List of female voice IDs for ElevenLabs TTS.
     */
    public List<String> elevenlabsFemaleVoices = List.of(
            "MF3mGyEYCl7XYWbV9V6O",
            "AZnzlk1XvdvUeBnXmlld",
            "pMsXgVXv3BLzUgSXRplE",
            "AZnzlk1XvdvUeBnXmlld"
    );

    //////////////////////
    // Village behavior //
    //////////////////////

    /**
     * Fraction (0–1) of villagers that spawn as guards.
     */
    public float guardSpawnFraction = 0.175f;

    /**
     * Equipment used by guards at each village equipment level.
     * Level 0 is the default, level 1 requires an armory, level 2 requires an armory with a blacksmith.
     */
    public Map<String, EquipmentSet> guardEquipment = ImmutableMap.<String, EquipmentSet>builder()
            .put("0", EquipmentSet.GUARD_0)
            .put("1", EquipmentSet.GUARD_1)
            .put("2", EquipmentSet.GUARD_2)
            .build();

    /**
     * Equipment used by archers at each village equipment level.
     */
    public Map<String, EquipmentSet> archerEquipment = ImmutableMap.<String, EquipmentSet>builder()
            .put("0", EquipmentSet.ARCHER_0)
            .put("1", EquipmentSet.ARCHER_1)
            .put("2", EquipmentSet.ARCHER_2)
            .build();

    /**
     * Multiplier of taxes paid by villages.
     */
    public float taxesFactor = 0.5f;

    /**
     * Interval (in ticks) between tax collection seasons.
     */
    public int taxSeason = 168000;

    /**
     * Chance (0–1) per minute that a marriage event occurs in the village.
     */
    public float marriageChancePerMinute = 0.05f;

    /**
     * Chance (0–1) per minute that an adventurer arrives at the inn.
     */
    public float adventurerAtInnChancePerMinute = 0.05f;

    /**
     * Duration (in ticks) that adventurers stay at the inn.
     */
    public int adventurerStayTime = 48000;

    /**
     * Chance (0–1) per minute that villagers will procreate.
     */
    public float villagerProcreationChancePerMinute = 0.05f;

    /**
     * Interval (in ticks) at which bounty hunters attack the player if reputation is low.
     */
    public int bountyHunterInterval = 48000;

    /**
     * Negative heart threshold for bounty hunter attacks.
     */
    public int bountyHunterHearts = -150;

    /**
     * If true, the inn spawns adventurers.
     */
    public boolean innSpawnsAdventurers = true;

    /**
     * If true, the inn spawns cultists.
     */
    public boolean innSpawnsCultists = true;

    /**
     * If true, the inn spawns wandering traders.
     */
    public boolean innSpawnsWanderingTraders = true;

    /**
     * Fraction (0–1) of villages left as vanilla villages.
     */
    public float fractionOfVanillaVillages = 0;

    /**
     * Fraction (0–1) of vanilla zombie villagers.
     */
    public float fractionOfVanillaZombies = 0;

    /**
     * Minimum number of buildings required to consider an area a village.
     * Below this, it is considered a settlement and welcome notifications are suppressed.
     */
    public int minimumBuildingsToBeConsideredAVillage = 3;

    /**
     * Dimensions where villagers should not be converted into MCA villagers.
     */
    public List<String> villagerDimensionBlacklist = List.of();

    /**
     * List of allowed spawn reasons for villager conversion.
     */
    public List<String> allowedSpawnReasons = List.of(
            "natural",
            "structure"
    );

    /**
     * List of items that villagers cannot be interacted with for mod compat.
     */
    public List<String> villagerInteractionItemBlacklist = List.of(
            "minecraft:bucket"
    );

    /**
     * If true, automatically scan for buildings. High CPU usage.
     */
    public boolean enableAutoScanByDefault = false;

    /**
     * URL for the immersive skin library.
     */
    public String immersiveLibraryUrl = "https://mca.conczin.net";

    /**
     * Number of entries in the gift desaturation queue.
     */
    public int giftDesaturationQueueLength = 16;

    /**
     * Factor controlling how much repeated gifts lose effectiveness.
     */
    public float giftDesaturationFactor = 0.5f;

    /**
     * Exponent applied to desaturation formula; reduces impact of expensive gifts.
     */
    public double giftDesaturationExponent = 0.85;

    /**
     * Factor multiplying the satisfaction value to determine heart impact from gifts.
     */
    public double giftSatisfactionFactor = 0.33;

    /**
     * How much a gift influences a villager's mood.
     */
    public float giftMoodEffect = 0.5f;

    /**
     * Base mood effect of a gift, independent of previous gifts.
     */
    public float baseGiftMoodEffect = 2;

    /**
     * Time (in ticks) after which gift desaturation resets.
     */
    public int giftDesaturationReset = 24000;

    /**
     * Allow players to marry each other.
     */
    public boolean allowPlayerMarriage = true;

    /**
     * Minimum building size for buildings.
     */
    public int minBuildingSize = 32;

    /**
     * Maximum building size for buildings.
     */
    public int maxBuildingSize = 8192;

    /**
     * Maximum radius of a building from its center.
     */
    public int maxBuildingRadius = 320;

    /**
     * Minimum pillar height for Grim Reaper altars.
     */
    public int minPillarHeight = 2;

    /**
     * Maximum tree height for chopping chores.
     */
    public int maxTreeHeight = 8;

    /**
     * Maximum ticks for valid tree log blocks before they are considered grown.
     */
    public Map<String, Integer> maxTreeTicks = ImmutableMap.<String, Integer>builder()
            .put("#minecraft:logs", 60)
            .build();

    /**
     * List of valid blocks that can serve as the base of trees.
     */
    public List<String> validTreeSources = List.of(
            "minecraft:grass_block",
            "minecraft:dirt"
    );

    //////////////////////////
    // Player customization //
    //////////////////////////

    /**
     * Launches a player into Destiny feature when they first join.
     */
    public boolean launchIntoDestiny = true;

    /**
     * Allows the player to modify their Destiny once via command.
     */
    public boolean allowDestinyCommandOnce = true;

    /**
     * Allows the player to modify their Destiny multiple times via command.
     */
    public boolean allowDestinyCommandMoreThanOnce = false;

    /**
     * Players can teleport to Destiny locations.
     */
    public boolean allowDestinyTeleportation = true;

    /**
     * Enables shader locations mapping for players.
     */
    public boolean enablePlayerShaders = true;

    /**
     * Player can select the villager model.
     */
    public boolean enableVillagerPlayerModel = true;

    /**
     * Forces the player model to look like a villager.
     */
    public boolean forceVillagerPlayerModel = false;

    /**
     * Allows limited access to player editor (name, gender, etc.).
     */
    public boolean allowLimitedPlayerEditor = true;

    /**
     * Allows full access to the player editor including clothing and hair.
     */
    public boolean allowFullPlayerEditor = false;

    /**
     * Use the USA name set instead of international names.
     */
    public boolean useModernUSANamesOnly = false;

    /**
     * Map of entity names to guard attack priorities.
     * Negative values indicate the entity should be ignored.
     */
    public Map<String, Integer> guardsTargetEntities = ImmutableMap.<String, Integer>builder()
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
            .build();

    /**
     * List of blocks or tags that villagers will not teleport onto.
     */
    public List<String> safeBlocksToTeleportOn = List.of(
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

    /**
     * Blocks or tags that should trigger exact villager body clearance checks during pathfinding.
     * Use this for small decorative blocks with awkward collision shapes, such as lanterns.
     */
    public List<String> villagerPathfindingCollisionCheckBlocks = List.of(
            "#mca:villager_pathfinding_collision_checks"
    );

    /**
     * If enabled, villagers run exact body clearance checks for every accepted path node.
     * This can help with unusual modded collision issues, but it is more expensive in busy villages.
     * For example, modded lanterns, it'd probably save a bit of performance keeping this off.
     */
    public boolean villagerPathfindingCheckAllNodeCollisions = false;

    /**
     * Structures that can be mentioned in Rumors conversation options.
     */
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

    /**
     * Maps modded professions to MCA professions for clothing conversion.
     * Only adult clothing is used; toddlers and children remain unchanged.
     */
    public Map<String, String> professionConversionsMap = Map.of();

    /**
     * Maps traits to shader locations, applied to players when camera entity has the trait.
     * Requires enablePlayerShaders to be true.
     */
    public Map<String, String> shaderLocationsMap = Map.of(
            "color_blind", "mca:shaders/post/color_blind.json",
            "sirben", "mca:shaders/post/sirben.json"
    );

    /**
     * Player renderer elements that can be disabled for certain mods.
     * Supported values: arms, left_arm, right_arm, all, block_player, block_villager
     */
    public Map<String, String> playerRendererBlacklist = Map.of(
            "morph", "arms",
            "firstpersonmod", "arms",
            "firstperson", "arms",
            "epicfight", "all"
    );

    /**
     * Map of tax items to their value in units.
     * If item is too expensive, it may not be picked until tax budget is sufficient.
     */
    public Map<String, Float> taxesMap = Map.of(
            "minecraft:emerald", 1.0f
    );

    /**
     * Moves the player's eye height according to their in-game height. Does not change hitbox size.
     */
    public boolean scaleEyeHeightWithPlayerHeight = true;

    public static Config getInstance() {
        return INSTANCE;
    }

    public static File getConfigFile() {
        return new File("./config/mca.json");
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

    public static CommonConfig getServerConfig() {
        if (serverConfig == null) {
            return Config.getInstance();
        } else {
            return serverConfig;
        }
    }

    public static void setServerConfig(CommonConfig config) {
        serverConfig = config;
    }

    public int getVillagerPathfindingDistance() {
        return Math.max(16, Math.min(256, villagerPathfindingDistance));
    }

    public int getVillagerFollowRange() {
        return Mth.clamp(villagerFollowRange, 16, 64);
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
}
