package net.conczin.mca;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConfigEarlyAccessTest {
    @TempDir
    Path tempDirectory;

    @Test
    void entityAttributeRegistrationWorksBeforeNativeConfigIsLoaded() throws Exception {
        Process process = new ProcessBuilder(
                javaExecutable(),
                "-cp",
                absoluteClasspath(),
                EarlyAttributeProbe.class.getName())
                .directory(tempDirectory.toFile())
                .redirectErrorStream(true)
                .start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
    }

    private static String javaExecutable() {
        String executable = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private static String absoluteClasspath() {
        return Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
                .map(Path::of)
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));
    }

    public static final class EarlyAttributeProbe {
        private EarlyAttributeProbe() {
        }

        public static void main(String[] args) {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();

            if (Config.SERVER_SPEC.isLoaded()) {
                throw new AssertionError("SERVER spec unexpectedly loaded before probe");
            }

            AttributeSupplier attributes = VillagerEntityMCA.createAttributes().build();
            requireEquals(
                    Config.SERVER.villagerMaxHealth.getDefault().doubleValue(),
                    attributes.getBaseValue(Attributes.MAX_HEALTH),
                    "MAX_HEALTH registration default");
            requireEquals(
                    Config.SERVER.villagerFollowRange.getDefault().doubleValue(),
                    attributes.getBaseValue(Attributes.FOLLOW_RANGE),
                    "FOLLOW_RANGE registration default");
        }

        private static void requireEquals(double expected, double actual, String message) {
            if (Double.compare(expected, actual) != 0) {
                throw new AssertionError(message + ": expected=" + expected + ", actual=" + actual);
            }
        }
    }
}
