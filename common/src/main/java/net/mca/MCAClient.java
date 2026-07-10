package net.mca;

import net.mca.client.gui.SkinLibraryScreen;
import net.mca.client.render.DynamicSkinCache;
import net.mca.client.resources.ClientSkinCatalog;
import net.mca.client.tts.SpeechManager;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.PlayerDimensions;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.VillagerLike;
import net.mca.network.c2s.ConfigRequest;
import net.mca.network.c2s.PlayerDataRequest;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerEntity;

import java.util.*;

public class MCAClient {
    public static VillagerEntityMCA fallbackVillager;
    public static final Map<UUID, VillagerLike<?>> playerData = new HashMap<>();
    public static final Set<UUID> playerDataRequests = new HashSet<>();

    private static final DestinyManager destinyManager = new DestinyManager();

    public static DestinyManager getDestinyManager() {
        return destinyManager;
    }

    public static void onLogin() {
        playerData.clear();
        playerDataRequests.clear();
        ClientSkinCatalog.clear();
        DynamicSkinCache.clear();
        NetworkHandler.sendToServer(new ConfigRequest());
        ClientSkinCatalog.sync();
    }

    public static Optional<VillagerLike<?>> getPlayerData(UUID uuid) {
        if (isPlayerRendererAllowed() || needsPlayerDataForDimensions()) {
            if (!MCAClient.playerDataRequests.contains(uuid) && MinecraftClient.getInstance().getNetworkHandler() != null) {
                MCAClient.playerDataRequests.add(uuid);
                NetworkHandler.sendToServer(new PlayerDataRequest(uuid));
            }
            if (MCAClient.playerData.containsKey(uuid)) {
                return Optional.of(MCAClient.playerData.get(uuid));
            }
        }
        return Optional.empty();
    }

    public static boolean useExpandedPersonalityTranslations() {
        boolean isTTSPackActive = MinecraftClient.getInstance().getResourceManager().streamResourcePacks().anyMatch(pack -> {
            return pack.getName().contains("MCAVoices");
        });
        String language = MinecraftClient.getInstance().options.language;
        return !isTTSPackActive && (language.equals("en_us") || language.equals("ru_ru")) && !Config.getInstance().enableOnlineTTS;
    }

    public static boolean useGeneticsRenderer(UUID uuid) {
        return getPlayerData(uuid).filter(f -> f.getPlayerModel() != VillagerLike.PlayerModel.VANILLA).isPresent();
    }

    public static boolean useVillagerRenderer(UUID uuid) {
        return useGeneticsRenderer(uuid) && MCAClient.playerData.get(uuid).getPlayerModel() == VillagerLike.PlayerModel.VILLAGER;
    }

    public static boolean renderArms(UUID uuid, String key) {
        return useVillagerRenderer(uuid) &&
                Config.getInstance().playerRendererBlacklist.entrySet().stream()
                        .filter(entry -> entry.getValue().equals("arms") || entry.getValue().equals(key))
                        .noneMatch(entry -> MCA.doesModExist(entry.getKey()));
    }

    public static void tickClient(MinecraftClient client) {
        destinyManager.tick(client);

        if (KeyBindings.SKIN_LIBRARY.wasPressed()) {
            MinecraftClient.getInstance().setScreen(new SkinLibraryScreen());
        }

        SpeechManager.INSTANCE.tick(client);
    }

    public static void addPlayerData(UUID uuid, VillagerEntityMCA villager) {
        playerData.put(uuid, villager);

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            PlayerEntity player = client.world.getPlayerByUuid(uuid);
            if (player != null) {
                refreshPlayerDimensions(player, "client player data refresh");
            }
        }
    }

    public static void refreshPlayerDataDependentDimensions() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }

        for (PlayerEntity player : client.world.getPlayers()) {
            if (needsPlayerDataForDimensions()) {
                getPlayerData(player.getUuid());
            }
            refreshPlayerDimensions(player, "client config refresh");
        }
    }

    private static void refreshPlayerDimensions(PlayerEntity player, String reason) {
        PlayerDimensions.debugRefresh(player, "before " + reason);
        player.calculateDimensions();
        PlayerDimensions.debugRefresh(player, "after " + reason);
    }

    private static boolean needsPlayerDataForDimensions() {
        return Config.getServerConfig().scalePlayerHitboxWithSizeAndWidth
                || Config.getInstance().scaleEyeHeightWithPlayerHeight;
    }

    public static boolean isPlayerRendererAllowed() {
        return Config.getInstance().enableVillagerPlayerModel &&
                Config.getInstance().playerRendererBlacklist.entrySet().stream()
                        .filter(entry -> entry.getValue().equals("all") || entry.getValue().equals("block_player"))
                        .noneMatch(entry -> MCA.doesModExist(entry.getKey()));
    }

    public static boolean isVillagerRendererAllowed() {
        return !Config.getInstance().forceVillagerPlayerModel &&
                Config.getInstance().playerRendererBlacklist.entrySet().stream()
                        .filter(entry -> entry.getValue().equals("all") || entry.getValue().equals("block_villager"))
                        .noneMatch(entry -> MCA.doesModExist(entry.getKey()));
    }

    public static boolean areShadersAllowed(String key) {
        return Config.getInstance().enablePlayerShaders &&
                Config.getInstance().playerRendererBlacklist.entrySet().stream()
                        .filter(entry -> entry.getValue().equals("shaders") || entry.getValue().equals(key))
                        .noneMatch(entry -> MCA.doesModExist(entry.getKey()));
    }

    public static boolean areShadersAllowed() {
        return areShadersAllowed("shaders");
    }
}
