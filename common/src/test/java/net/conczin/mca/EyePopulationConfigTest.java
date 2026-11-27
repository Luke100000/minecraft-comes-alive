package net.conczin.mca;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class EyePopulationConfigTest {
    @BeforeAll static void bootstrapMinecraft() {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
    }
    @TempDir Path directory;
    @Test void oldConfigNeedsNoPopulationPreset() throws Exception {
        Path file = directory.resolve("mca.json");
        Files.writeString(file, "{\"version\":2}");
        assertNotNull(Config.loadOrCreate(file.toFile()));
        assertFalse(Files.readString(file).contains("eyePopulationPreset"));
    }
    @Test void retiredPopulationPresetIsIgnoredWithoutResettingOtherSettings() throws Exception {
        Path file = directory.resolve("mca.json");
        for (String value : new String[]{"\"DIVERSE\"", "\"unknown\"", "null"}) {
            String input = "{\"version\":2,\"traitChance\":0.37,\"eyePopulationPreset\":" + value + "}";
            Files.writeString(file, input);
            Config config = assertDoesNotThrow(() -> Config.loadOrCreate(file.toFile()));
            assertEquals(0.37F, config.traitChance);
            assertFalse(Files.readString(file).contains("eyePopulationPreset"));
        }
    }
}
