package net.conczin.mca.neoforge;

import net.conczin.mca.MCA;
import net.conczin.mca.PlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;

public class NeoforgePlatformHelper extends PlatformHelper {
    @Override
    protected boolean isModLoadedUncached(String namespace) {
        return ModList.get().isLoaded(namespace);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return !FMLLoader.isProduction();
    }

    @Override
    public String getModVersion() {
        return ModList.get().getModContainerById(MCA.MOD_ID)
                .map(mod -> mod.getModInfo().getVersion().toString())
                .orElseGet(super::getModVersion);
    }
}
