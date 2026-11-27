package net.conczin.mca;

import net.conczin.mca.config.ConfigScope;
import net.conczin.mca.config.ConfigValue;
import net.conczin.mca.entity.VillagerLike;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MCAClientRendererPolicyTest {
    private final UUID playerId = UUID.randomUUID();
    private final String installedMod = "mca_lookup_test_" + UUID.randomUUID().toString().replace("-", "");
    private List<? extends String> previousBlacklist;
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
        previousBlacklist = Config.CLIENT.playerRendererBlacklist.get();
        previousPlayerModel = Config.CLIENT.enableVillagerPlayerModel.get();
        previousForceModel = Config.CLIENT.forceVillagerPlayerModel.get();
        previousShaders = Config.CLIENT.enablePlayerShaders.get();
        previousEyeScaling = Config.CLIENT.scaleEyeHeightWithPlayerHeight.get();
        previousHitboxScaling = Config.SERVER.scalePlayerHitboxWithSizeAndWidth.get();
        setEffective(ConfigScope.CLIENT, Config.CLIENT.enableVillagerPlayerModel, true);
        Config.CLIENT.forceVillagerPlayerModel.set(false);
        Config.CLIENT.enablePlayerShaders.set(true);
        previousPlatform = MCA.platformHelper;
        MCA.platformHelper = new PlatformHelper() {
            @Override
            protected boolean isModLoadedUncached(String namespace) {
                return namespace.equals(installedMod);
            }
        };
        setEffective(ConfigScope.CLIENT, Config.CLIENT.playerRendererBlacklist, List.of("mca_lookup_test_absent=all"));
        MCAClient.playerDataRequests.add(playerId);
        MCAClient.playerData.put(playerId, playerData(VillagerLike.PlayerModel.VILLAGER));
    }

    @AfterEach
    void tearDown() {
        setEffective(ConfigScope.CLIENT, Config.CLIENT.playerRendererBlacklist, previousBlacklist);
        setEffective(ConfigScope.CLIENT, Config.CLIENT.enableVillagerPlayerModel, previousPlayerModel);
        Config.CLIENT.forceVillagerPlayerModel.set(previousForceModel);
        Config.CLIENT.enablePlayerShaders.set(previousShaders);
        MCA.platformHelper = previousPlatform;
        Config.CLIENT.scaleEyeHeightWithPlayerHeight.set(previousEyeScaling);
        setEffective(ConfigScope.SERVER, Config.SERVER.scalePlayerHitboxWithSizeAndWidth, previousHitboxScaling);
        MCAClient.playerData.remove(playerId);
        MCAClient.playerDataRequests.remove(playerId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void rendererLookupDoesNotRepeatTheCompatibilityScanInsideTheDataLookup() throws ReflectiveOperationException {
        var value = Config.CLIENT.playerRendererBlacklist;
        var accessField = ConfigValue.class.getDeclaredField("access");
        accessField.setAccessible(true);
        var originalAccess = (ConfigValue.Access<List<? extends String>>) accessField.get(value);
        AtomicInteger reads = new AtomicInteger();
        value.bind(new ConfigValue.Access<>() {
            @Override
            public List<? extends String> get() {
                reads.incrementAndGet();
                return originalAccess.get();
            }

            @Override
            public void set(List<? extends String> entries) {
                originalAccess.set(entries);
            }
        });
        try {
            assertTrue(MCAClient.useVillagerRenderer(playerId));
            assertEquals(1, reads.get());

            reads.set(0);
            assertTrue(MCAClient.useGeneticsRenderer(playerId));
            assertEquals(1, reads.get());
        } finally {
            value.bind(originalAccess);
        }
    }

    @Test
    void rendererChecksRespectLiveConfigurationAndPlayerModelChanges() {
        assertTrue(MCAClient.useVillagerRenderer(playerId));
        setEffective(ConfigScope.CLIENT, Config.CLIENT.enableVillagerPlayerModel, false);
        assertFalse(MCAClient.useVillagerRenderer(playerId));

        setEffective(ConfigScope.CLIENT, Config.CLIENT.enableVillagerPlayerModel, true);
        MCAClient.playerData.put(playerId, playerData(VillagerLike.PlayerModel.VANILLA));
        assertFalse(MCAClient.useVillagerRenderer(playerId));
        assertFalse(MCAClient.useGeneticsRenderer(playerId));
    }

    @Test
    void disablingRenderingRetainsDimensionOnlyDataAccess() {
        setEffective(ConfigScope.CLIENT, Config.CLIENT.enableVillagerPlayerModel, false);
        Config.CLIENT.scaleEyeHeightWithPlayerHeight.set(true);
        setEffective(ConfigScope.SERVER, Config.SERVER.scalePlayerHitboxWithSizeAndWidth, false);
        assertSame(MCAClient.playerData.get(playerId), MCAClient.getPlayerData(playerId).orElseThrow());

        Config.CLIENT.scaleEyeHeightWithPlayerHeight.set(false);
        assertTrue(MCAClient.getPlayerData(playerId).isEmpty());
        assertTrue(MCAClient.playerDataRequests.contains(playerId));
    }

    @Test
    void blacklistCategoriesAndConfigurationEditsRemainLive() {
        setBlacklistCategory("block_player");
        assertFalse(MCAClient.useVillagerRenderer(playerId));
        assertTrue(MCAClient.isVillagerRendererAllowed());

        setBlacklistCategory("block_villager");
        assertTrue(MCAClient.useVillagerRenderer(playerId));
        assertFalse(MCAClient.isVillagerRendererAllowed());

        setBlacklistCategory("all");
        assertFalse(MCAClient.isPlayerRendererAllowed());
        assertFalse(MCAClient.isVillagerRendererAllowed());

        setBlacklistCategory("left_arm");
        assertFalse(MCAClient.renderArms(playerId, "left_arm"));
        assertTrue(MCAClient.renderArms(playerId, "right_arm"));
        setBlacklistCategory("arms");
        assertFalse(MCAClient.renderArms(playerId, "right_arm"));

        setBlacklistCategory("color_blind_shader");
        assertFalse(MCAClient.areShadersAllowed("color_blind_shader"));
        assertTrue(MCAClient.areShadersAllowed("sirben_shader"));
        setBlacklistCategory("shaders");
        assertFalse(MCAClient.areShadersAllowed("sirben_shader"));
        setEffective(ConfigScope.CLIENT, Config.CLIENT.playerRendererBlacklist, List.of("mca_lookup_test_absent=all"));
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

    private void setBlacklistCategory(String category) {
        setEffective(ConfigScope.CLIENT, Config.CLIENT.playerRendererBlacklist,
                List.of("mca_lookup_test_absent=all", installedMod + "=" + category));
    }

    private static <T> void setEffective(ConfigScope scope, ConfigValue<T> handle, T value) {
        handle.set(value);
        // Exercise the policy with newly effective settings, not a pending restart edit.
        // Native GAME/WORLD settings retain their cached values on set(); simulate
        // the restart boundary for just this setting, without rebuilding renderers.
        ModConfigSpec.ConfigValue<?> nativeValue = NativeConfigDefaultsExtension.spec(scope)
                .getValues().get(handle.getPath());
        nativeValue.clearCache();
        assertEquals(value, handle.get());
    }
}
