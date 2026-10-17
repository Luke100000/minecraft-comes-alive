package net.conczin.mca;

import net.neoforged.fml.config.NeoForgeTestConfigLoader;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.conczin.mca.config.ConfigScope;
import net.conczin.mca.config.nativeapi.NeoForgeConfigBackend;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class NativeConfigDefaultsExtension implements BeforeAllCallback {
    private static final NeoForgeConfigBackend BACKEND = new NeoForgeConfigBackend();

    public static ModConfigSpec spec(ConfigScope scope) {
        return BACKEND.spec(scope);
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        for (ConfigScope scope : ConfigScope.values()) {
            NeoForgeTestConfigLoader.loadDefaults(spec(scope));
        }
    }
}
