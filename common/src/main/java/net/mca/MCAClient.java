package net.mca;

import net.mca.client.gui.SkinLibraryScreen;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.VillagerLike;
import net.mca.network.c2s.ConfigRequest;
import net.mca.network.c2s.PlayerDataRequest;
import net.minecraft.client.MinecraftClient;

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
        playerDataRequests.clear();
        NetworkHandler.sendToServer(new ConfigRequest());
    }

    public static Optional<VillagerLike<?>> getPlayerData(UUID uuid) {
        if (isPlayerRendererAllowed()) {
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
        return !isTTSPackActive && MinecraftClient.getInstance().options.language.equals("en_us") && !Config.getInstance().enableOnlineTTS;
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
    }

    public static void addPlayerData(UUID uuid, VillagerEntityMCA villager) {
        playerData.put(uuid, villager);

        // Refresh eye height
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) {
            client.player.calculateDimensions();
        }
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
