package net.conczin.mca.fabric;

import net.conczin.mca.MCA;
import net.conczin.mca.PlatformHelper;
import net.fabricmc.loader.api.FabricLoader;

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
    public String getModVersion() {
        return FabricLoader.getInstance().getModContainer(MCA.MOD_ID)
                .map(mod -> mod.getMetadata().getVersion().getFriendlyString())
                .orElseGet(super::getModVersion);
    }
}
