package net.conczin.mca;

import net.conczin.mca.client.gui.SkinLibraryScreen;
import net.conczin.mca.client.tts.SpeechManager;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.ConfigRequest;
import net.conczin.mca.network.c2s.PlayerDataRequest;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

import java.util.*;

public class MCAClient {
    public static final Map<UUID, VillagerLike<?>> playerData = new HashMap<>();
    public static final Map<UUID, ItemStack> equippedRings = new HashMap<>();
    public static final Set<UUID> playerDataRequests = new HashSet<>();
    private static final DestinyManager destinyManager = new DestinyManager();

    public static DestinyManager getDestinyManager() {
        return destinyManager;
    }

    public static void onLogin() {
        playerDataRequests.clear();
        equippedRings.clear();
        Network.sendToServer(new ConfigRequest());
    }

    public static Optional<VillagerLike<?>> getPlayerData(UUID uuid) {
        if (isPlayerRendererAllowed()) {
            if (!MCAClient.playerDataRequests.contains(uuid) && Minecraft.getInstance().getConnection() != null) {
                MCAClient.playerDataRequests.add(uuid);
                Network.sendToServer(new PlayerDataRequest(uuid));
            }
            if (MCAClient.playerData.containsKey(uuid)) {
                return Optional.of(MCAClient.playerData.get(uuid));
            }
        }
        return Optional.empty();
    }

    public static Optional<ItemStack> getEquippedRing(UUID uuid) {
        if (!MCAClient.playerDataRequests.contains(uuid) && Minecraft.getInstance().getConnection() != null) {
            MCAClient.playerDataRequests.add(uuid);
            Network.sendToServer(new PlayerDataRequest(uuid));
        }

        ItemStack ring = equippedRings.get(uuid);
        if (ring == null || ring.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(ring.copy());
    }

    public static void setEquippedRing(UUID uuid, ItemStack ring) {
        if (ring == null || ring.isEmpty()) {
            equippedRings.remove(uuid);
            return;
        }

        equippedRings.put(uuid, ring.copy());
    }

    public static boolean useExpandedPersonalityTranslations() {
        boolean isTTSPackActive = Minecraft.getInstance().getResourceManager().listPacks().anyMatch(pack -> {
            return pack.packId().contains("MCAVoices");
        });
        return !isTTSPackActive && Minecraft.getInstance().options.languageCode.equals("en_us") && !Config.getInstance().enableOnlineTTS;
    }

    public static boolean useGeneticsRenderer(UUID uuid) {
        return getGeneticsRendererData(uuid).isPresent();
    }

    public static boolean useVillagerRenderer(UUID uuid) {
        return getVillagerRendererData(uuid).isPresent();
    }

    public static Optional<VillagerLike<?>> getGeneticsRendererData(UUID uuid) {
        return getPlayerData(uuid).filter(data -> data.getPlayerModel() != VillagerLike.PlayerModel.VANILLA);
    }

    public static Optional<VillagerLike<?>> getVillagerRendererData(UUID uuid) {
        return getPlayerData(uuid).filter(data -> data.getPlayerModel() == VillagerLike.PlayerModel.VILLAGER);
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

    public static void addPlayerData(UUID uuid, VillagerEntityMCA villager, ItemStack equippedRing) {
        playerData.put(uuid, villager);
        setEquippedRing(uuid, equippedRing);

        // Refresh eye height
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.refreshDimensions();
        }
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
