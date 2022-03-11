package mca;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;

public final class Config implements Serializable {
    @Serial
    private static final long serialVersionUID = 956221997003825933L;

    private static final Config INSTANCE = loadOrCreate();

    public static Config getInstance() {
        return INSTANCE;
    }

    public static final int VERSION = 1;

    public int version = 0;
  
    public final boolean overwriteOriginalVillagers = true;
    public final boolean overwriteOriginalZombieVillagers = true;
    public final boolean villagerTagsHacks = true;
    public final boolean enableInfection = true;
    public final int infectionChance = 5;
    public final boolean allowGrimReaper = true;
    public final int guardSpawnRate = 6;
    public final int chanceToHaveTwins = 2;
    public final float marriageHeartsRequirement = 100;
    public final int babyGrowUpTime = 20;
    public final int villagerMaxAgeTime = 192000;
    public final int villagerMaxHealth = 20;
    public final String villagerChatPrefix = "";
    public final boolean allowPlayerMarriage = true;
    public final int marriageChance = 5;
    public final int childrenChance = 5;
    public final int giftDesaturationQueueLength = 16;
    public final float giftDesaturationFactor = 0.5f;
    public final double giftDesaturationExponent = 0.85;
    public final double giftSatisfactionFactor = 0.33;
    public final int baseGiftMoodEffect = 2;
    public final int giftDesaturationReset = 24000;
    public final int greetHeartsThreshold = 75;
    public final int greetAfterDays = 1;
    public final int childInitialHearts = 100;
    public final int immigrantChance = 20;
    public final int bountyHunterInterval = 24000;
    public final int bountyHunterThreshold = -5;
    public final float traitChance = 0.25f;
    public final float traitInheritChance = 0.5f;
    public final float villagerHeight = 0.9f;
    public final boolean canHurtBabies = true;
    public final boolean useVoices = false;
    public final boolean useVanillaVoices = false;
    public final int interactionChanceFatigue = 1;
    public final int interactionFatigueCooldown = 4800;
    public final float taxesFactor = 0.5f;
    public final boolean enterVillageNotification = true;
    public final boolean showNameTags = true;
    public final int villagerHealthBonusPerLevel = 5;
    public final boolean useSquidwardModels = false;
    public final int maxBuildingSize = 8192;
    public final int maxBuildingRadius = 320;

    public static File getConfigFile() {
        return new File("./config/mca.json");
    }

    public void save() {
        try (FileWriter writer = new FileWriter(getConfigFile())) {
            version = VERSION;
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(this, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Config loadOrCreate() {
        try (FileReader reader = new FileReader(getConfigFile())) {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Config config = gson.fromJson(reader, Config.class);
            if (config.version != VERSION) {
                config = new Config();
            }
            config.save();
            return config;
        } catch (IOException e) {
            //e.printStackTrace();
        }
        Config config = new Config();
        config.save();
        return config;
    }
}
