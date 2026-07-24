package net.conczin.mca;

import net.conczin.mca.client.gui.SkinLibraryScreen;
import net.conczin.mca.client.resources.ClientSkinCatalog;
import net.conczin.mca.client.tts.SpeechManager;
import net.conczin.mca.entity.PlayerDimensions;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.ConfigRequest;
import net.conczin.mca.network.c2s.PlayerDataRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.util.*;

public class MCAClient {
    public static final Map<UUID, VillagerLike<?>> playerData = new HashMap<>();
    public static final Set<UUID> playerDataRequests = new HashSet<>();
    private static final DestinyManager destinyManager = new DestinyManager();
    public static VillagerEntityMCA fallbackVillager;

    public static DestinyManager getDestinyManager() {
        return destinyManager;
    }

    public static void onLogin() {
        playerData.clear();
        playerDataRequests.clear();
        ClientSkinCatalog.clear();
        Network.sendToServer(new ConfigRequest());
        ClientSkinCatalog.sync();
    }

    public static Optional<VillagerLike<?>> getPlayerData(UUID uuid) {
        if (!isPlayerRendererAllowed() && !needsPlayerDataForDimensions()) {
            return Optional.empty();
        }

        if (!playerDataRequests.contains(uuid) && Minecraft.getInstance().getConnection() != null) {
            playerDataRequests.add(uuid);
            Network.sendToServer(new PlayerDataRequest(uuid));
        }
        return Optional.ofNullable(playerData.get(uuid));
    }

    public static boolean useExpandedPersonalityTranslations() {
        boolean isTTSPackActive = Minecraft.getInstance().getResourceManager().listPacks().anyMatch(pack -> {
            return pack.packId().contains("MCAVoices");
        });
        return !isTTSPackActive && Minecraft.getInstance().options.languageCode.equals("en_us") && !Config.getInstance().enableOnlineTTS;
    }

    public static Optional<VillagerLike<?>> getGeneticsPlayerData(UUID uuid) {
        return getPlayerData(uuid)
                .filter(data -> data.getPlayerModel() != VillagerLike.PlayerModel.VANILLA);
    }

    public static boolean useGeneticsRenderer(UUID uuid) {
        return isPlayerRendererAllowed() && getGeneticsPlayerData(uuid).isPresent();
    }

    public static boolean useVillagerRenderer(UUID uuid) {
        return isPlayerRendererAllowed() && getGeneticsPlayerData(uuid)
                .filter(data -> data.getPlayerModel() == VillagerLike.PlayerModel.VILLAGER)
                .isPresent();
    }

    public static boolean renderArms(UUID uuid, String key) {
        return useVillagerRenderer(uuid) &&
               Config.getInstance().playerRendererBlacklist.entrySet().stream()
                       .filter(entry -> entry.getValue().equals("arms") || entry.getValue().equals(key))
                       .noneMatch(entry -> MCA.platformHelper.isModLoaded(entry.getKey()));
    }

    public static void tickClient(Minecraft client) {
        destinyManager.tick(client);

        if (KeyBindings.SKIN_LIBRARY.consumeClick()) {
            Minecraft.getInstance().setScreen(new SkinLibraryScreen());
        }

        SpeechManager.INSTANCE.tick(client);
    }

    public static void addPlayerData(UUID uuid, VillagerEntityMCA villager) {
        playerData.put(uuid, villager);

        Minecraft client = Minecraft.getInstance();
        if (client.level != null) {
            Player player = client.level.getPlayerByUUID(uuid);
            if (player != null) {
                refreshPlayerDimensions(player, "client player data refresh");
            }
        }
    }

    public static void refreshPlayerDataDependentDimensions() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            return;
        }

        for (Player player : client.level.players()) {
            if (needsPlayerDataForDimensions()) {
                getPlayerData(player.getUUID());
            }
            refreshPlayerDimensions(player, "client config refresh");
        }
    }

    private static void refreshPlayerDimensions(Player player, String reason) {
        PlayerDimensions.debugRefresh(player, "before " + reason);
        player.refreshDimensions();
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
                       .noneMatch(entry -> MCA.platformHelper.isModLoaded(entry.getKey()));
    }

    public static boolean isVillagerRendererAllowed() {
        return !Config.getInstance().forceVillagerPlayerModel &&
               Config.getInstance().playerRendererBlacklist.entrySet().stream()
                       .filter(entry -> entry.getValue().equals("all") || entry.getValue().equals("block_villager"))
                       .noneMatch(entry -> MCA.platformHelper.isModLoaded(entry.getKey()));
    }

    public static boolean areShadersAllowed(String key) {
        return Config.getInstance().enablePlayerShaders &&
               Config.getInstance().playerRendererBlacklist.entrySet().stream()
                       .filter(entry -> entry.getValue().equals("shaders") || entry.getValue().equals(key))
                       .noneMatch(entry -> MCA.platformHelper.isModLoaded(entry.getKey()));
    }

    public static boolean areShadersAllowed() {
        return areShadersAllowed("shaders");
    }
}
