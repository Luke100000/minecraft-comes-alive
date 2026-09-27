package net.conczin.mca;

import net.neoforged.fml.config.NeoForgeTestConfigLoader;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public final class NativeConfigDefaultsExtension implements BeforeAllCallback {
    @Override
    public void beforeAll(ExtensionContext context) {
        NeoForgeTestConfigLoader.loadDefaults(Config.COMMON_SPEC);
        NeoForgeTestConfigLoader.loadDefaults(Config.SERVER_SPEC);
        NeoForgeTestConfigLoader.loadDefaults(Config.CLIENT_SPEC);
    }
}
