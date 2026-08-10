package net.mca.fabric;

import net.fabricmc.loader.api.FabricLoader;
import net.mca.PlatformHelper;

public final class FabricPlatformHelper extends PlatformHelper {
    @Override
    protected boolean isModLoadedUncached(String namespace) {
        return FabricLoader.getInstance().isModLoaded(namespace);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return FabricLoader.getInstance().isDevelopmentEnvironment();
    }
}
