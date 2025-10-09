package net.conczin.mca.neoforge;

import net.conczin.mca.PlatformHelper;
import net.neoforged.fml.ModList;

public class NeoforgePlatformHelper extends PlatformHelper {
    @Override
    protected boolean isModLoadedUncached(String namespace) {
        return ModList.get().isLoaded(namespace);
    }
}
