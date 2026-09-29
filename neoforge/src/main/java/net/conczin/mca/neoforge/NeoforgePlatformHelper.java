package net.conczin.mca.neoforge;

import net.conczin.mca.PlatformHelper;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

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
    public Path getConfigDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }
}
