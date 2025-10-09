package net.conczin.mca;

import java.util.HashMap;
import java.util.Map;

public class PlatformHelper {
    private static final Map<String, Boolean> MOD_CACHE = new HashMap<>();

    final public boolean isModLoaded(String namespace) {
        return MOD_CACHE.computeIfAbsent(namespace, this::isModLoadedUncached);
    }

    protected boolean isModLoadedUncached(String namespace) {
        return false;
    }
}
