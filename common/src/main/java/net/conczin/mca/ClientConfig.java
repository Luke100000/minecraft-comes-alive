package net.conczin.mca;

import java.util.List;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class ClientConfig {
    public final ModConfigSpec.ConfigValue<Boolean> useSquidwardModels;
    public final ModConfigSpec.ConfigValue<Boolean> showNameTags;
    public final ModConfigSpec.ConfigValue<Double> nameTagDistance;
    public final ModConfigSpec.ConfigValue<Boolean> enableBoobs;
    public final ModConfigSpec.ConfigValue<Boolean> enableOnlineTTS;
    public final ModConfigSpec.ConfigValue<String> onlineTTSModel;
    public final ModConfigSpec.ConfigValue<String> onlineTTSServer;
    public final ModConfigSpec.ConfigValue<String> player2Url;
    public final ModConfigSpec.ConfigValue<String> elevenlabsPrivateAPIkey;
    public final ModConfigSpec.ConfigValue<String> elevenlabsModel;
    public final ModConfigSpec.ConfigValue<List<? extends String>> elevenlabsMaleVoices;
    public final ModConfigSpec.ConfigValue<List<? extends String>> elevenlabsFemaleVoices;
    public final ModConfigSpec.ConfigValue<String> immersiveLibraryUrl;
    public final ModConfigSpec.ConfigValue<Boolean> enablePlayerShaders;
    public final ModConfigSpec.ConfigValue<Boolean> enableVillagerPlayerModel;
    public final ModConfigSpec.ConfigValue<Boolean> forceVillagerPlayerModel;
    public final ModConfigSpec.ConfigValue<List<? extends String>> shaderLocationsMap;
    public final ModConfigSpec.ConfigValue<List<? extends String>> playerRendererBlacklist;
    public final ModConfigSpec.ConfigValue<Boolean> scaleEyeHeightWithPlayerHeight;
    private final Config.DecodedMapCache<String, String> shaderLocationsCache =
            new Config.DecodedMapCache<>(String.class, String.class, "shaderLocationsMap");
    private final Config.DecodedMapCache<String, String> playerRendererBlacklistCache =
            new Config.DecodedMapCache<>(String.class, String.class, "playerRendererBlacklist");

    ClientConfig(ModConfigSpec.Builder builder) {
        builder.translation("mca.configuration.section.villager_behavior").push("villager_behavior");
        useSquidwardModels = builder

                .comment("If true, villagers use the vanilla \"Squidward\" (nose) model.")
                .translation("mca.configuration.useSquidwardModels")
                .gameRestart()
                .define("useSquidwardModels", false);
        showNameTags = builder

                .comment("If true, shows villager name tags above their heads.")
                .translation("mca.configuration.showNameTags")
                .define("showNameTags", true);
        nameTagDistance = builder

                .comment("Maximum distance at which name tags are visible.")
                .translation("mca.configuration.nameTagDistance")
                .defineInRange("nameTagDistance", 5.0, 0.0, 10000.0);
        enableBoobs = builder

                .comment("Enables female body features (visual only).")
                .translation("mca.configuration.enableBoobs")
                .gameRestart()
                .define("enableBoobs", true);
        builder.pop();

        builder.translation("mca.configuration.section.tts").push("tts");
        enableOnlineTTS = builder

                .comment("Enables online TTS (text-to-speech) for villager dialogue.")
                .translation("mca.configuration.enableOnlineTTS")
                .define("enableOnlineTTS", false);
        onlineTTSModel = builder

                .comment("Online TTS model to use.")
                .translation("mca.configuration.onlineTTSModel")
                .define("onlineTTSModel", "default");
        onlineTTSServer = builder

                .comment("URL of the online TTS server.")
                .translation("mca.configuration.onlineTTSServer")
                .gameRestart()
                .define("onlineTTSServer", "https://api-rk.conczin.net/");
        player2Url = builder

                .comment("Player2 API url.")
                .translation("mca.configuration.player2Url")
                .gameRestart()
                .define("player2Url", "http://127.0.0.1:4315/");
        elevenlabsPrivateAPIkey = builder

                .comment("Elevenlabs API key.")
                .translation("mca.configuration.elevenlabsPrivateAPIkey")
                .define("elevenlabsPrivateAPIkey", "");
        elevenlabsModel = builder

                .comment("ElevenLabs TTS model to use.")
                .translation("mca.configuration.elevenlabsModel")
                .define("elevenlabsModel", "eleven_turbo_v2_5");
        elevenlabsMaleVoices = builder

                .comment("List of male voice IDs for ElevenLabs TTS.")
                .translation("mca.configuration.elevenlabsMaleVoices")
                .defineListAllowEmpty("elevenlabsMaleVoices", List.of(
            "ErXwobaYiN019PkySvjV",
            "VR6AewLTigWG4xSOukaG",
            "onwK4e9ZLuTAKqWW03F9",
            "onwK4e9ZLuTAKqWW03F9"
    ), () -> "", value -> value instanceof String);
        elevenlabsFemaleVoices = builder

                .comment("List of female voice IDs for ElevenLabs TTS.")
                .translation("mca.configuration.elevenlabsFemaleVoices")
                .defineListAllowEmpty("elevenlabsFemaleVoices", List.of(
            "MF3mGyEYCl7XYWbV9V6O",
            "AZnzlk1XvdvUeBnXmlld",
            "pMsXgVXv3BLzUgSXRplE",
            "AZnzlk1XvdvUeBnXmlld"
    ), () -> "", value -> value instanceof String);
        builder.pop();

        builder.translation("mca.configuration.section.player_customization").push("player_customization");
        immersiveLibraryUrl = builder

                .comment("URL for the immersive skin library.")
                .translation("mca.configuration.immersiveLibraryUrl")
                .define("immersiveLibraryUrl", "https://mca.conczin.net");
        enablePlayerShaders = builder

                .comment("Enables shader locations mapping for players.")
                .translation("mca.configuration.enablePlayerShaders")
                .define("enablePlayerShaders", true);
        enableVillagerPlayerModel = builder

                .comment("Player can select the villager model.")
                .translation("mca.configuration.enableVillagerPlayerModel")
                .gameRestart()
                .define("enableVillagerPlayerModel", true);
        forceVillagerPlayerModel = builder

                .comment("Forces the player model to look like a villager.")
                .translation("mca.configuration.forceVillagerPlayerModel")
                .define("forceVillagerPlayerModel", false);
        shaderLocationsMap = builder

                .comment("Maps traits to shader locations, applied to players when camera entity has the trait. Requires enablePlayerShaders to be true.")
                .translation("mca.configuration.shaderLocationsMap")
                .defineListAllowEmpty("shaderLocationsMap", Config.encodeMap(Map.of(
            "color_blind", "mca:shaders/post/color_blind.json",
            "sirben", "mca:shaders/post/sirben.json"
    )), () -> "", value -> Config.isMapEntry(value, String.class, String.class));
        playerRendererBlacklist = builder

                .comment("Player renderer elements that can be disabled for certain mods. Supported values: arms, left_arm, right_arm, all, block_player, block_villager")
                .translation("mca.configuration.playerRendererBlacklist")
                .gameRestart()
                .defineListAllowEmpty("playerRendererBlacklist", Config.encodeMap(Map.of(
            "morph", "arms",
            "firstpersonmod", "arms",
            "firstperson", "arms",
            "epicfight", "all"
    )), () -> "", value -> Config.isMapEntry(value, String.class, String.class));
        scaleEyeHeightWithPlayerHeight = builder

                .comment("Moves the player's eye height according to their in-game height. Does not change hitbox size.")
                .translation("mca.configuration.scaleEyeHeightWithPlayerHeight")
                .define("scaleEyeHeightWithPlayerHeight", true);
        builder.pop();

    }

    public Map<String, String> shaderLocationsMap() {
        return shaderLocationsCache.get(shaderLocationsMap.get());
    }

    public Map<String, String> playerRendererBlacklist() {
        return playerRendererBlacklistCache.get(playerRendererBlacklist.get());
    }
}
