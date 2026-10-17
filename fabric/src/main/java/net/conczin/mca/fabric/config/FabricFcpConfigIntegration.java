package net.conczin.mca.fabric.config;

import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeConfigRegistry;
import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.NeoForgeModConfigEvents;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.config.ConfigScope;
import net.conczin.mca.config.nativeapi.NeoForgeConfigBackend;
import net.conczin.mca.server.DestinyLocationResolver;
import net.neoforged.fml.config.ModConfig;

/** Loaded only when the user has opted into FCP. Preserves the original MCA screen. */
public final class FabricFcpConfigIntegration {
    private static NeoForgeConfigBackend backend;

    private FabricFcpConfigIntegration() {}

    public static void initialize() {
        backend = new NeoForgeConfigBackend();
        Config.setBackend(backend);
        NeoForgeModConfigEvents.loading(MCA.MOD_ID).register(config -> {
            for (ConfigScope scope : ConfigScope.values()) {
                if (config.getSpec() != backend.spec(scope)) continue;
                try {
                    boolean migrated = Config.migrateLegacy(Config.schema(scope), config.getFullPath());
                    boolean upgraded = Config.upgradeDefaultMaps(Config.schema(scope));
                    if (migrated || upgraded) {
                        backend.save(scope);
                        if (migrated) Config.markLegacyImported(Config.schema(scope));
                    }
                    Config.schema(scope).markLoaded();
                } catch (IllegalStateException pathless) {
                    // A pathless remote SERVER snapshot has no disk destination.
                }
            }
        });
        NeoForgeModConfigEvents.reloading(MCA.MOD_ID).register(config -> {
            if (config.getSpec() == backend.spec(ConfigScope.SERVER)) {
                MCA.getServer().ifPresent(server -> server.execute(() -> {
                    DestinyLocationResolver.refreshCachedDestinations(server, Config.SERVER);
                    FabricConfigSync.onFcpServerReload(server);
                }));
            }
        });
        NeoForgeConfigRegistry.INSTANCE.register(MCA.MOD_ID, ModConfig.Type.COMMON,
                backend.spec(ConfigScope.COMMON), Config.COMMON_FILE_NAME);
        NeoForgeConfigRegistry.INSTANCE.register(MCA.MOD_ID, ModConfig.Type.SERVER,
                backend.spec(ConfigScope.SERVER), Config.SERVER_FILE_NAME);
    }

    static void initializeClientConfig() {
        NeoForgeConfigRegistry.INSTANCE.register(MCA.MOD_ID, ModConfig.Type.CLIENT,
                backend.spec(ConfigScope.CLIENT), Config.CLIENT_FILE_NAME);
    }
}
