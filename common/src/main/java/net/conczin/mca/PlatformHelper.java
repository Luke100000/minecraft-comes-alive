package net.conczin.mca;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlatformHelper {
    private final Map<String, Boolean> modCache = new ConcurrentHashMap<>();

    final public boolean isModLoaded(String namespace) {
        return modCache.computeIfAbsent(namespace, this::isModLoadedUncached);
    }

    protected boolean isModLoadedUncached(String namespace) {
        return false;
    }

    public boolean isDevelopmentEnvironment() {
        return false;
    }

    public String getModVersion() {
        return "unknown";
    }

    /** Config commands are native-NeoForge-only; Fabric editing uses the optional FCP GUI. */
    public boolean allowsConfigCommands() {
        return true;
    }

    public Path getConfigDirectory() {
        return Path.of("config");
    }
}
