package net.conczin.mca.fabric;

import net.conczin.mca.PlatformHelper;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public class FabricPlatformHelper extends PlatformHelper {
    @Override
    protected boolean isModLoadedUncached(String namespace) {
        return FabricLoader.getInstance().isModLoaded(namespace);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }

    @Override
    public Path getConfigDirectory() {
        return FabricLoader.getInstance().getConfigDir();
    }
}
