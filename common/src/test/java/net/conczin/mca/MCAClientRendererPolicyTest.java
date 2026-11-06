package net.conczin.mca;

import net.conczin.mca.entity.VillagerLike;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MCAClientRendererPolicyTest {
    private final UUID playerId = UUID.randomUUID();
    private final CountingBlacklist blacklist = new CountingBlacklist();
    private final String installedMod = "mca_lookup_test_" + UUID.randomUUID().toString().replace("-", "");
    private Map<String, String> previousBlacklist;
    private PlatformHelper previousPlatform;
    private boolean previousPlayerModel;
    private boolean previousForceModel;
    private boolean previousShaders;
    private boolean previousEyeScaling;
    private boolean previousHitboxScaling;

    @BeforeAll
    static void bootstrap() {
        FamilyTreeTestSupport.bootstrapMinecraft();
    }

    @BeforeEach
    void setUp() {
        Config config = Config.getInstance();
        previousBlacklist = config.playerRendererBlacklist;
        previousPlayerModel = config.enableVillagerPlayerModel;
        previousForceModel = config.forceVillagerPlayerModel;
        previousShaders = config.enablePlayerShaders;
        previousEyeScaling = config.scaleEyeHeightWithPlayerHeight;
        previousHitboxScaling = Config.getServerConfig().scalePlayerHitboxWithSizeAndWidth;
        config.playerRendererBlacklist = blacklist;
        config.enableVillagerPlayerModel = true;
        config.forceVillagerPlayerModel = false;
        config.enablePlayerShaders = true;
        previousPlatform = MCA.platformHelper;
        MCA.platformHelper = new PlatformHelper() {
            @Override
            protected boolean isModLoadedUncached(String namespace) {
                return namespace.equals(installedMod);
            }
        };
        blacklist.put("mca_lookup_test_absent", "all");
        MCAClient.playerDataRequests.add(playerId);
        MCAClient.playerData.put(playerId, playerData(VillagerLike.PlayerModel.VILLAGER));
    }

    @AfterEach
    void tearDown() {
        Config config = Config.getInstance();
        config.playerRendererBlacklist = previousBlacklist;
        config.enableVillagerPlayerModel = previousPlayerModel;
        config.forceVillagerPlayerModel = previousForceModel;
        config.enablePlayerShaders = previousShaders;
        MCA.platformHelper = previousPlatform;
        config.scaleEyeHeightWithPlayerHeight = previousEyeScaling;
        Config.getServerConfig().scalePlayerHitboxWithSizeAndWidth = previousHitboxScaling;
        MCAClient.playerData.remove(playerId);
        MCAClient.playerDataRequests.remove(playerId);
    }

    @Test
    void rendererLookupDoesNotRepeatTheCompatibilityScanInsideTheDataLookup() {
        assertTrue(MCAClient.useVillagerRenderer(playerId));
        assertEquals(1, blacklist.scans);

        blacklist.scans = 0;
        assertTrue(MCAClient.useGeneticsRenderer(playerId));
        assertEquals(1, blacklist.scans);
    }

    @Test
    void rendererChecksRespectLiveConfigurationAndPlayerModelChanges() {
        assertTrue(MCAClient.useVillagerRenderer(playerId));
        Config.getInstance().enableVillagerPlayerModel = false;
        assertFalse(MCAClient.useVillagerRenderer(playerId));

        Config.getInstance().enableVillagerPlayerModel = true;
        MCAClient.playerData.put(playerId, playerData(VillagerLike.PlayerModel.VANILLA));
        assertFalse(MCAClient.useVillagerRenderer(playerId));
        assertFalse(MCAClient.useGeneticsRenderer(playerId));
    }

    @Test
    void disablingRenderingRetainsDimensionOnlyDataAccess() {
        Config config = Config.getInstance();
        config.enableVillagerPlayerModel = false;
        config.scaleEyeHeightWithPlayerHeight = true;
        Config.getServerConfig().scalePlayerHitboxWithSizeAndWidth = false;
        assertSame(MCAClient.playerData.get(playerId), MCAClient.getPlayerData(playerId).orElseThrow());

        config.scaleEyeHeightWithPlayerHeight = false;
        assertTrue(MCAClient.getPlayerData(playerId).isEmpty());
        assertTrue(MCAClient.playerDataRequests.contains(playerId));
    }

    @Test
    void blacklistCategoriesAndInPlaceEditsRemainLive() {
        blacklist.put(installedMod, "block_player");
        assertFalse(MCAClient.useVillagerRenderer(playerId));
        assertTrue(MCAClient.isVillagerRendererAllowed());

        blacklist.put(installedMod, "block_villager");
        assertTrue(MCAClient.useVillagerRenderer(playerId));
        assertFalse(MCAClient.isVillagerRendererAllowed());

        blacklist.put(installedMod, "all");
        assertFalse(MCAClient.isPlayerRendererAllowed());
        assertFalse(MCAClient.isVillagerRendererAllowed());

        blacklist.put(installedMod, "left_arm");
        assertFalse(MCAClient.renderArms(playerId, "left_arm"));
        assertTrue(MCAClient.renderArms(playerId, "right_arm"));
        blacklist.put(installedMod, "arms");
        assertFalse(MCAClient.renderArms(playerId, "right_arm"));

        blacklist.put(installedMod, "color_blind_shader");
        assertFalse(MCAClient.areShadersAllowed("color_blind_shader"));
        assertTrue(MCAClient.areShadersAllowed("sirben_shader"));
        blacklist.put(installedMod, "shaders");
        assertFalse(MCAClient.areShadersAllowed("sirben_shader"));
        blacklist.remove(installedMod);
        assertTrue(MCAClient.areShadersAllowed("sirben_shader"));
    }

    private static VillagerLike<?> playerData(VillagerLike.PlayerModel model) {
        return (VillagerLike<?>) Proxy.newProxyInstance(VillagerLike.class.getClassLoader(),
                new Class<?>[]{VillagerLike.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getPlayerModel")) {
                        return model;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class CountingBlacklist extends LinkedHashMap<String, String> {
        private int scans;

        @Override
        public Set<Map.Entry<String, String>> entrySet() {
            scans++;
            return super.entrySet();
        }
    }
}
