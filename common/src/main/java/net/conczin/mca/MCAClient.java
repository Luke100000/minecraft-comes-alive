package net.conczin.mca;

import net.conczin.mca.client.gui.BlueprintScreen;
import net.conczin.mca.client.gui.SkinLibraryScreen;
import net.conczin.mca.client.resources.ClientSkinCatalog;
import net.conczin.mca.client.tts.SpeechManager;
import net.conczin.mca.entity.PlayerDimensions;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.ConfigRequest;
import net.conczin.mca.network.c2s.PlayerDataRequest;
import net.conczin.mca.registry.EntitiesMCA;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import java.util.*;

public class MCAClient {
    private static final Map<UUID, VillagerLike<?>> playerData = new HashMap<>();
    private static final Set<UUID> playerDataRequests = new HashSet<>();
    private static final DestinyManager destinyManager = new DestinyManager();
    private static VillagerEntityMCA fallbackVillager;

    public static DestinyManager getDestinyManager() {
        return destinyManager;
    }

    public static void onLogin() {
        Config.setServerConfig(null);
        playerData.clear();
        playerDataRequests.clear();
        fallbackVillager = null;
        ClientSkinCatalog.clear();
        Network.sendToServer(new ConfigRequest());
        ClientSkinCatalog.sync();
    }

    public static Optional<VillagerLike<?>> getPlayerData(UUID uuid) {
        if (!isPlayerRendererAllowed() && !needsPlayerDataForDimensions()) {
            return Optional.empty();
        }

        return getOrRequestPlayerData(uuid);
    }

    private static Optional<VillagerLike<?>> getOrRequestPlayerData(UUID uuid) {
        if (!playerDataRequests.contains(uuid) && Minecraft.getInstance().getConnection() != null) {
            playerDataRequests.add(uuid);
            Network.sendToServer(new PlayerDataRequest(uuid));
        }
        return Optional.ofNullable(playerData.get(uuid));
    }

    public static VillagerLike<?> resolveVillager(Entity entity) {
        if (entity instanceof VillagerLike<?> villager) {
            return villager;
        }
        if (fallbackVillager == null) {
            fallbackVillager = Objects.requireNonNull(
                    EntitiesMCA.MALE_VILLAGER.create(entity.level()),
                    "Failed to create fallback MCA villager"
            );
        }
        return playerData.getOrDefault(entity.getUUID(), fallbackVillager);
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
        return getRenderedGeneticsPlayerData(uuid).isPresent();
    }

    static Optional<VillagerLike<?>> getRenderedGeneticsPlayerData(UUID uuid) {
        if (!isPlayerRendererAllowed()) {
            return Optional.empty();
        }
        return getOrRequestPlayerData(uuid).filter(data -> data.getPlayerModel() != VillagerLike.PlayerModel.VANILLA);
    }

    public static boolean useVillagerRenderer(UUID uuid) {
        return isPlayerRendererAllowed() && getOrRequestPlayerData(uuid)
                .filter(data -> data.getPlayerModel() == VillagerLike.PlayerModel.VILLAGER)
                .isPresent();
    }

    public static boolean renderArms(UUID uuid, String key) {
        return useVillagerRenderer(uuid) && !isRendererBlocked("arms", key);
    }

    public static void tickClient(Minecraft client) {
        BlueprintScreen.maintainSessionState(client);
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
        return Config.getInstance().enableVillagerPlayerModel && !isRendererBlocked("all", "block_player");
    }

    public static boolean isVillagerRendererAllowed() {
        return !Config.getInstance().forceVillagerPlayerModel && !isRendererBlocked("all", "block_villager");
    }

    public static boolean areShadersAllowed(String key) {
        return Config.getInstance().enablePlayerShaders && !isRendererBlocked("shaders", key);
    }

    private static boolean isRendererBlocked(String category, String key) {
        for (var entry : Config.getInstance().playerRendererBlacklist.entrySet()) {
            String value = entry.getValue();
            if ((value.equals(category) || value.equals(key)) && MCA.platformHelper.isModLoaded(entry.getKey())) {
                return true;
            }
        }
        return false;
    }

    public static boolean areShadersAllowed() {
        return areShadersAllowed("shaders");
    }
}
