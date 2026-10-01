package net.neoforged.fml.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.concurrent.locks.ReentrantLock;

public final class NeoForgeTestConfigLoader {
    private NeoForgeTestConfigLoader() {
    }

    public static void loadDefaults(ModConfigSpec spec) {
        if (spec.isLoaded()) {
            return;
        }

        CommentedConfig config = CommentedConfig.inMemory();
        spec.correct(config);
        spec.acceptConfig(new LoadedConfig(config, null, null));
    }

    public static void loadConfig(ModConfigSpec spec, CommentedConfig config) {
        spec.correct(config);
        spec.acceptConfig(new LoadedConfig(config, null, null));
    }

    public static ModConfig pathlessServerConfig(ModConfigSpec spec) {
        ModConfig config = new ModConfig(ModConfig.Type.SERVER, spec, null, "mca-server.toml", new ReentrantLock());
        config.loadedConfig = new LoadedConfig(CommentedConfig.inMemory(), null, config);
        return config;
    }
}
