package net.conczin.mca.server;

import net.conczin.mca.destiny.DestinyDestination;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DestinyLocationResolverTest {
    @Test
    void sameLocationInDifferentDimensionsIsADifferentDestination() {
        DestinyDestination overworld = new DestinyDestination(
                "minecraft:village_plains",
                Optional.of(Level.OVERWORLD)
        );
        DestinyDestination nether = new DestinyDestination(
                "minecraft:village_plains",
                Optional.of(Level.NETHER)
        );

        assertNotEquals(overworld, nether);
    }

    @Test
    void somewhereIsDimensionless() {
        DestinyDestination somewhere = new DestinyDestination("somewhere", Optional.empty());

        assertEquals("somewhere", somewhere.location());
        assertTrue(somewhere.dimension().isEmpty());
    }

    @Test
    void destinationRejectsLocationsLongerThanTheNetworkLimit() {
        String location = "a".repeat(129);

        assertThrows(
                IllegalArgumentException.class,
                () -> new DestinyDestination(location, Optional.of(Level.OVERWORLD))
        );
    }

    @Test
    void autoDiscoveryCanBeDisabledForAnExactManualList() {
        List<String> locations = DestinyLocationResolver.resolveLocationIds(
                List.of("somewhere", "minecraft:village_plains"),
                false,
                List.of("ctov:village_plains_large"),
                List.of()
        );

        assertEquals(List.of("somewhere", "minecraft:village_plains"), locations);
    }

    @Test
    void exactBlacklistRemovesManualAndDiscoveredLocations() {
        List<String> locations = DestinyLocationResolver.resolveLocationIds(
                List.of("somewhere", "minecraft:village_plains"),
                true,
                List.of("ctov:village_plains", "minecraft:village_plains"),
                List.of("minecraft:village_plains")
        );

        assertEquals(List.of("somewhere", "ctov:village_plains"), locations);
    }

    @Test
    void namespaceWildcardRemovesEveryLocationFromThatNamespace() {
        List<String> locations = DestinyLocationResolver.resolveLocationIds(
                List.of("somewhere", "minecraft:village_plains"),
                true,
                List.of(
                        "ctov:village_plains_large",
                        "ctov:village_desert_medium",
                        "othermod:village_oak"
                ),
                List.of("ctov:*")
        );

        assertEquals(
                List.of("somewhere", "minecraft:village_plains", "othermod:village_oak"),
                locations
        );
    }

    @Test
    void simpleWildcardsCanMatchAnywhereInAnIdentifier() {
        List<String> locations = DestinyLocationResolver.resolveLocationIds(
                List.of("somewhere"),
                true,
                List.of(
                        "ctov:village_plains_large",
                        "ctov:village_plains_small",
                        "ctov:village_desert_medium",
                        "ctov:village_desert_small"
                ),
                List.of("ctov:*large*", "*:village_*_medium")
        );

        assertEquals(
                List.of("somewhere", "ctov:village_desert_small", "ctov:village_plains_small"),
                locations
        );
    }

    @Test
    void duplicateDiscoveredLocationsAreNotAddedTwice() {
        List<String> locations = DestinyLocationResolver.resolveLocationIds(
                List.of("minecraft:village_plains"),
                true,
                List.of("minecraft:village_plains", "ctov:village_plains", "ctov:village_plains"),
                List.of()
        );

        assertEquals(List.of("minecraft:village_plains", "ctov:village_plains"), locations);
    }
}
