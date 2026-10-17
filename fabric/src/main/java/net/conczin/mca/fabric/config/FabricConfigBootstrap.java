package net.conczin.mca.fabric.config;

import net.conczin.mca.Config;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

/** Only this class is reached by the unconditional Fabric entrypoints. */
public final class FabricConfigBootstrap {
    private static FabricTomlConfigBackend standalone;
    private static boolean fcp;

    private FabricConfigBootstrap() {}

    public static void initialize() {
        fcp = FabricLoader.getInstance().isModLoaded("forgeconfigapiport");
        if (fcp) {
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
