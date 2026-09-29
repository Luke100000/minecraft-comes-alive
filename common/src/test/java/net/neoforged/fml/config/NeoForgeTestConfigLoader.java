package net.neoforged.fml.config;

import com.electronwill.nightconfig.core.CommentedConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

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
}
