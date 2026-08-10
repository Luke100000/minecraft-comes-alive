package net.mca.quilt;

import net.mca.PlatformHelper;
import org.quiltmc.loader.api.QuiltLoader;

public final class QuiltPlatformHelper extends PlatformHelper {
    @Override
    protected boolean isModLoadedUncached(String namespace) {
        return QuiltLoader.isModLoaded(namespace);
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        return QuiltLoader.isDevelopmentEnvironment();
    }
}
