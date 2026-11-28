package net.conczin.mca.entity.interaction;

import net.conczin.mca.PlatformHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class VillagerRumorCommandTest {
    @Test
    void splitCommandPreservesDottedResourceLocations() {
        assertArrayEquals(
                new String[]{"location", "modid:village.oak"},
                VillagerCommandHandler.splitCommand("location.modid:village.oak")
        );
        assertArrayEquals(
                new String[]{"location"},
                VillagerCommandHandler.splitCommand("location")
        );
    }

    @Test
    void resolveAllowedRumorStructureRejectsUnlistedClientArg() {
        List<String> allowedPool = List.of("modid:village.oak");
        RandomSource random = RandomSource.create(42L);

        assertEquals(
                Optional.empty(),
                VillagerCommandHandler.resolveAllowedRumorStructure("minecraft:ancient_city", allowedPool, random)
        );
        assertEquals(
                Optional.of(ResourceLocation.parse("modid:village.oak")),
                VillagerCommandHandler.resolveAllowedRumorStructure("modid:village.oak", allowedPool, random)
        );
        assertEquals(
                Optional.of(ResourceLocation.parse("modid:village.oak")),
                VillagerCommandHandler.resolveAllowedRumorStructure("", allowedPool, random)
        );
        assertEquals(
                Optional.empty(),
                VillagerCommandHandler.resolveAllowedRumorStructure("", List.of(), random)
        );
    }

    @Test
    void platformHelperModCacheIsInstanceScopedAndConcurrentSafe() {
        PlatformHelper onlyAlphaLoaded = new PlatformHelper() {
            @Override
            protected boolean isModLoadedUncached(String namespace) {
                return namespace.startsWith("alpha_");
            }
        };
        PlatformHelper onlyBetaLoaded = new PlatformHelper() {
            @Override
            protected boolean isModLoadedUncached(String namespace) {
                return namespace.startsWith("beta_");
            }
        };

        IntStream.range(0, 200).parallel().forEach(i -> {
            String alphaId = "alpha_" + i;
            String betaId = "beta_" + i;
            assertTrue(onlyAlphaLoaded.isModLoaded(alphaId));
            assertFalse(onlyAlphaLoaded.isModLoaded(betaId));
            assertFalse(onlyBetaLoaded.isModLoaded(alphaId));
            assertTrue(onlyBetaLoaded.isModLoaded(betaId));
        });
    }
}
