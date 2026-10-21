package net.conczin.mca;

import com.google.gson.Gson;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMourningTest {
    private static final Gson GSON = new Gson();

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void missingMourningFlagKeepsDefaultEnabled() {
        Config config = GSON.fromJson("{\"version\":2}", Config.class);

        assertTrue(config.enableMourning);
    }

    @Test
    void explicitMourningDisableIsLoaded() {
        Config config = GSON.fromJson("{\"version\":2,\"enableMourning\":false}", Config.class);

        assertFalse(config.enableMourning);
    }
}
