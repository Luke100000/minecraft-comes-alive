package net.conczin.mca.fabric;

import net.conczin.mca.PlatformHelper;
import net.fabricmc.loader.api.FabricLoader;

public class FabricPlatformHelper extends PlatformHelper {
    @Override
    protected boolean isModLoadedUncached(String namespace) {
        return FabricLoader.getInstance().isModLoaded(namespace);
    }
}
