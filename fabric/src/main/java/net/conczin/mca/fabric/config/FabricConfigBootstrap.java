package net.conczin.mca.fabric.config;

import net.conczin.mca.Config;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.LevelResource;

/** Only this class is reached by the unconditional Fabric entrypoints. */
public final class FabricConfigBootstrap {
    private static FabricTomlConfigBackend standalone;
    private static boolean fcp;

    private FabricConfigBootstrap() {}

    public static boolean hasFcp() { return fcp; }

    public static void initialize() {
        // Before native FCP registration/load, repair recognized old TOML paths without losing overrides.
        FabricLegacyTomlMigration.migrate(FabricLoader.getInstance().getConfigDir(),
                FabricLoader.getInstance().getEnvironmentType() == EnvType.CLIENT);
        fcp = FabricLoader.getInstance().isModLoaded("forgeconfigapiport");
        if (fcp) {
            // FCP's SERVER_STARTING 'before' phase loads world TOML; our phase must run earlier.
            ResourceLocation migratePhase = ResourceLocation.fromNamespaceAndPath("mca", "migrate_config");
            ServerLifecycleEvents.SERVER_STARTING.addPhaseOrdering(migratePhase,
                    ResourceLocation.fromNamespaceAndPath("forgeconfigapiport", "before"));
            ServerLifecycleEvents.SERVER_STARTING.register(migratePhase, server ->
                    FabricLegacyTomlMigration.migrateWorld(server.getWorldPath(LevelResource.ROOT).resolve("serverconfig")));
            // FCP and all NeoForge config types are reached exclusively in this branch.
            FabricFcpConfigIntegration.initialize();
        } else {
            standalone = new FabricTomlConfigBackend(FabricLoader.getInstance().getConfigDir());
            Config.setBackend(standalone);
            standalone.loadGlobal(false);
        }
    }

    public static void initializeClient() {
        if (fcp) FabricFcpConfigClient.initialize();
        else standalone.loadGlobal(true);
    }

    public static void startWorld(MinecraftServer server) {
        if (!fcp) standalone.startWorld(server.getWorldPath(LevelResource.ROOT).resolve("serverconfig"));
    }

    public static void stopWorld() {
        if (!fcp) standalone.stopWorld();
    }
}
