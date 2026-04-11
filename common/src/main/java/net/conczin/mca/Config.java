package net.conczin.mca;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Map.Entry;
import net.conczin.mca.entity.ai.Traits;

public final class Config extends CommonConfig {
   private static final int VERSION = 2;
   private static final Config INSTANCE = loadOrCreate();
   private static CommonConfig serverConfig;
   public String README = "https://github.com/Luke100000/minecraft-comes-alive/wiki";
   public int version = 0;
   public boolean overwriteOriginalVillagers = true;
   public List<String> moddedVillagerWhitelist = List.of();
   public boolean overwriteOriginalZombieVillagers = true;
   public boolean overwriteAllZombiesWithZombieVillagers = false;
   public List<String> moddedZombieVillagerWhitelist = List.of();
   public float babyZombieChance = 0.25F;
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
   public boolean enableGenderCheckForPlayers = true;
   public float zombieBiteInfectionChance = 0.05F;
   public float infectionChanceDecreasePerLevel = 0.25F;
   public int infectionTime = 72000;
   public float twinBabyChance = 0.05F;
   public int marriageHeartsRequirement = 100;
   public int engagementHeartsRequirement = 50;
   public int bouquetHeartsRequirement = 10;
   public int villagerMaxHealth = 20;
   public boolean allowVillagerTeleporting = false;
   public double villagerMinTeleportationDistance = 128.0;
   public int childInitialHearts = 100;
   public int greetHeartsThreshold = 75;
   public int greetAfterDays = 1;
   public float geneticImmigrantChance = 0.2F;
   public float traitChance = 0.25F;
   public float traitInheritChance = 0.5F;
   public boolean bypassTraitRestrictions = false;
   public float nightOwlChance = 0.5F;
   public boolean allowAnyNightOwl = false;
   public int heartsForPardonHit = 30;
   public int pardonPlayerTicks = 1200;
   public boolean guardsTargetMonsters = false;
   public float maleVillagerHeightFactor = 0.9F;
   public float femaleVillagerHeightFactor = 0.85F;
   public float maleVillagerWidthFactor = 1.0F;
   public float femaleVillagerWidthFactor = 0.95F;
   public boolean showNameTags = true;
   public float nameTagDistance = 5.0F;
   public boolean useMCAVoices = true;
   public boolean useVanillaVoices = false;
   public float interactionChanceFatigue = 1.0F;
   public int interactionFatigueCooldown = 4800;
   public int villagerHealthBonusPerLevel = 5;
   public boolean useSquidwardModels = false;
   public boolean enableBoobs = true;
   public int burnedClothingTickLength = 3600;
   public float coloredHairChance = 0.02F;
   public int heartsRequiredToAutoSpawnGravestone = 10;
   public boolean useSmarterDoorAI = false;
   public int procreationCooldown = 72000;
   public boolean trackVillagerPosition = true;
   public int trackVillagerPositionEveryNTicks = 200;
   public String _read_this_before_using_villager_ai = "https://github.com/Luke100000/minecraft-comes-alive/wiki/GPT3-based-conversations";
   public boolean enableVillagerChatAI = false;
   public String villagerChatAIEndpoint = "https://api.conczin.net/v1/mca/chat";
   public boolean villagerChatAIUseTools = false;
   public String villagerChatAIToken = "";
   public String villagerChatAIModel = "default";
   public String villagerChatAISystemPrompt = "";
   public boolean villagerChatAIUseLongTermMemory = false;
   public boolean villagerChatAIUseSharedLongTermMemory = false;
   public boolean villagerChatAIIncludeSessionInformation = false;
   public String inworldAIToken = "";
   public Map<UUID, String> inworldAIResourceNames = new HashMap<>();
   public boolean enableOnlineTTS = false;
   public String onlineTTSModel = "default";
   public String onlineTTSServer = "https://api.rk.conczin.net/";
   public String player2Url = "http://127.0.0.1:4315/";
   public String elevenlabsPrivateAPIkey = "";
   public String elevenlabsModel = "eleven_turbo_v2_5";
   public List<String> elevenlabsMaleVoices = List.of("ErXwobaYiN019PkySvjV", "VR6AewLTigWG4xSOukaG", "onwK4e9ZLuTAKqWW03F9", "onwK4e9ZLuTAKqWW03F9");
   public List<String> elevenlabsFemaleVoices = List.of("MF3mGyEYCl7XYWbV9V6O", "AZnzlk1XvdvUeBnXmlld", "pMsXgVXv3BLzUgSXRplE", "AZnzlk1XvdvUeBnXmlld");
   public float guardSpawnFraction = 0.175F;
   public float taxesFactor = 0.5F;
   public int taxSeason = 168000;
   public float marriageChancePerMinute = 0.05F;
   public float adventurerAtInnChancePerMinute = 0.05F;
   public int adventurerStayTime = 48000;
   public float villagerProcreationChancePerMinute = 0.05F;
   public int bountyHunterInterval = 48000;
   public int bountyHunterHearts = -150;
   public boolean innSpawnsAdventurers = true;
   public boolean innSpawnsCultists = true;
   public boolean innSpawnsWanderingTraders = true;
   public float fractionOfVanillaVillages = 0.0F;
   public float fractionOfVanillaZombies = 0.0F;
   public int minimumBuildingsToBeConsideredAVillage = 3;
   public List<String> villagerDimensionBlacklist = List.of();
   public List<String> allowedSpawnReasons = List.of("natural", "structure");
   public List<String> villagerInteractionItemBlacklist = List.of("minecraft:bucket");
   public boolean enableAutoScanByDefault = false;
   public String immersiveLibraryUrl = "https://mca.conczin.net";
   public int giftDesaturationQueueLength = 16;
   public float giftDesaturationFactor = 0.5F;
   public double giftDesaturationExponent = 0.85;
   public double giftSatisfactionFactor = 0.33;
   public float giftMoodEffect = 0.5F;
   public float baseGiftMoodEffect = 2.0F;
   public int giftDesaturationReset = 24000;
   public boolean allowPlayerMarriage = true;
   public int minBuildingSize = 32;
   public int maxBuildingSize = 8192;
   public int maxBuildingRadius = 320;
   public int minPillarHeight = 2;
   public int maxTreeHeight = 8;
   public Map<String, Integer> maxTreeTicks = ImmutableMap.<String, Integer>builder().put("#minecraft:logs", 60).build();
   public List<String> validTreeSources = List.of("minecraft:grass_block", "minecraft:dirt");
   public boolean launchIntoDestiny = true;
   public boolean allowDestinyCommandOnce = true;
   public boolean allowDestinyCommandMoreThanOnce = false;
   public boolean allowDestinyTeleportation = true;
   public boolean enablePlayerShaders = true;
   public boolean enableVillagerPlayerModel = true;
   public boolean forceVillagerPlayerModel = false;
   public boolean allowLimitedPlayerEditor = true;
   public boolean allowFullPlayerEditor = false;
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
      .put("mca:female_zombie_villager", 3)
      .put("mca:male_zombie_villager", 3)
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
   public Map<String, String> professionConversionsMap = Map.of();
   public Map<String, String> shaderLocationsMap = Map.of("color_blind", "mca:color_blind", "sirben", "mca:sirben");
   public Map<String, String> playerRendererBlacklist = Map.of("morph", "arms", "firstpersonmod", "arms", "firstperson", "arms", "epicfight", "all");
   public Map<String, Float> taxesMap = Map.of("minecraft:emerald", 1.0F);
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
         try {
            Config var4;
            try (FileReader reader = new FileReader(file)) {
               Gson gson = new GsonBuilder().setPrettyPrinting().create();
               Config config = (Config)gson.fromJson(reader, Config.class);
               if (config == null || config.version != 2) {
                  config = new Config();
               }

               config.save();
               var4 = config;
            }

            return var4;
         } catch (JsonSyntaxException var7) {
            MCA.LOGGER.error("");
            MCA.LOGGER.error("|||||||||||||||||||||||||||||||||||||||||||||||||||||||||");
            MCA.LOGGER.error("Minecraft Comes Alive config (mca.json) failed to launch!");
            MCA.LOGGER.error(var7);
            MCA.LOGGER.error("|||||||||||||||||||||||||||||||||||||||||||||||||||||||||");
            MCA.LOGGER.error("");
         } catch (IOException var8) {
            MCA.LOGGER.error(var8);
         }
      }

      Config config = new Config();
      config.save();
      return config;
   }

   public static CommonConfig getServerConfig() {
      return (CommonConfig)(serverConfig == null ? getInstance() : serverConfig);
   }

   public static void setServerConfig(CommonConfig config) {
      serverConfig = config;
   }

   public void autocomplete() {
      for (Traits.Trait trait : Traits.Trait.values()) {
         this.enabledTraits.putIfAbsent(trait.id(), true);
      }

      Map<String, String> normalizedShaderLocations = new HashMap<>();

      for (Entry<String, String> entry : this.shaderLocationsMap.entrySet()) {
         normalizedShaderLocations.put(entry.getKey(), normalizeShaderLocation(entry.getValue()));
      }

      this.shaderLocationsMap = normalizedShaderLocations;
   }

   public static String normalizeShaderLocation(String shaderLocation) {
      if (shaderLocation == null) {
         return null;
      } else {
         String normalized = shaderLocation.trim();
         int namespaceSeparator = normalized.indexOf(58);
         if (namespaceSeparator < 0) {
            return normalized;
         } else {
            String namespace = normalized.substring(0, namespaceSeparator + 1);
            String path = normalized.substring(namespaceSeparator + 1);
            if (path.endsWith(".json")) {
               path = path.substring(0, path.length() - 5);
            }

            int lastSlash = path.lastIndexOf(47);
            if (lastSlash >= 0) {
               path = path.substring(lastSlash + 1);
            }

            return namespace + path;
         }
      }
   }

   public void save() {
      this.autocomplete();

      try (FileWriter writer = new FileWriter(getConfigFile())) {
         this.version = 2;
         Gson gson = new GsonBuilder().setPrettyPrinting().create();
         gson.toJson(this, writer);
      } catch (IOException var6) {
         MCA.LOGGER.error(var6);
      }
   }
}
