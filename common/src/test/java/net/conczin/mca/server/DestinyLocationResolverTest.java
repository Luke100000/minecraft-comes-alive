package net.conczin.mca.server;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DestinyLocationResolverTest {
    @Test
    void autoDiscoveryCanBeDisabledForAnExactManualList() {
        List<String> locations = DestinyLocationResolver.resolve(
                List.of("somewhere", "minecraft:village_plains"),
                false,
                List.of("ctov:village_plains_large"),
                List.of()
        );

        assertEquals(List.of("somewhere", "minecraft:village_plains"), locations);
    }

    @Test
    void exactBlacklistRemovesManualAndDiscoveredLocations() {
        List<String> locations = DestinyLocationResolver.resolve(
                List.of("somewhere", "minecraft:village_plains"),
                true,
                List.of("ctov:village_plains", "minecraft:village_plains"),
                List.of("minecraft:village_plains")
        );

        assertEquals(List.of("somewhere", "ctov:village_plains"), locations);
    }

    @Test
    void namespaceWildcardRemovesEveryLocationFromThatNamespace() {
        List<String> locations = DestinyLocationResolver.resolve(
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
        List<String> locations = DestinyLocationResolver.resolve(
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
        List<String> locations = DestinyLocationResolver.resolve(
                List.of("minecraft:village_plains"),
                true,
                List.of("minecraft:village_plains", "ctov:village_plains", "ctov:village_plains"),
                List.of()
        );

        assertEquals(List.of("minecraft:village_plains", "ctov:village_plains"), locations);
    }
}
