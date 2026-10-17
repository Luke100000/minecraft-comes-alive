package net.conczin.mca;

import net.conczin.mca.entity.ai.EyeGenetics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
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
    @Test void oldConfigDefaultsToDiverse() throws Exception {
        Path file = directory.resolve("mca.json");
        Files.writeString(file, "{\"version\":2}");
        assertEquals("DIVERSE", Config.loadOrCreate(file.toFile()).eyePopulationPreset);
        assertTrue(Files.readString(file).contains("DIVERSE"));
    }
    @Test void unknownOrNullPresetIsRejectedBeforeRewrite() throws Exception {
        Path file = directory.resolve("mca.json");
        for (String value : new String[]{"\"unknown\"", "null"}) {
            String input = "{\"version\":2,\"eyePopulationPreset\":" + value + "}";
            Files.writeString(file, input);
            assertThrows(IllegalArgumentException.class, () -> Config.loadOrCreate(file.toFile()));
            assertEquals(input, Files.readString(file));
        }
    }
    @Test void generationPresetDoesNotChangeSavedExpression() {
        var genotype = EyeGenetics.founder(EyeGenetics.PopulationPreset.DIVERSE, RandomSource.create(47));
        CompoundTag saved = EyeGenetics.encode(genotype, null, new CompoundTag());
        int before = EyeGenetics.resolveNatural(genotype, null, false);
        EyeGenetics.parsePreset("DIVERSE");
        assertEquals(before, EyeGenetics.resolveNatural(EyeGenetics.decodeGenotype(saved), null, false));
        assertThrows(IllegalArgumentException.class, () -> EyeGenetics.parsePreset(null));
    }
}
