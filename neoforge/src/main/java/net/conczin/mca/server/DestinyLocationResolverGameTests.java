package net.conczin.mca.server;

import net.conczin.mca.Config;
import net.conczin.mca.destiny.DestinyDestination;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class DestinyLocationResolverGameTests {
    private DestinyLocationResolverGameTests() {
    }

    @GameTest(batch = "mca_destiny_dimensions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void plainsVillagePlacementIsDimensionSpecific(GameTestHelper helper) {
        Registry<Structure> structures = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        Holder.Reference<Structure> plainsVillage = structures.getHolder(ResourceLocation.parse("minecraft:village_plains"))
                .orElseThrow();
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);

        helper.assertTrue(
                !overworld.getChunkSource().getGeneratorState().getPlacementsForStructure(plainsVillage).isEmpty(),
                "plains village should have Overworld placements"
        );
        helper.assertTrue(
                nether != null && nether.getChunkSource().getGeneratorState().getPlacementsForStructure(plainsVillage).isEmpty(),
                "plains village should not have Nether placements"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_destiny_dimensions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void resolverUsesGeneratorPlacementsForDestinationDimensions(GameTestHelper helper) {
        List<DestinyDestination> destinations;
        try (var ignored = overrideDestinyConfig(
                List.of("somewhere", "minecraft:village_plains"), false, false, List.of())) {
            destinations = DestinyLocationResolver.resolve(helper.getLevel().getServer(), Config.SERVER);
        }

        helper.assertTrue(
                destinations.contains(new DestinyDestination("somewhere", Optional.empty())),
                "somewhere should stay dimensionless"
        );
        helper.assertTrue(
                destinations.contains(new DestinyDestination("minecraft:village_plains", Optional.of(Level.OVERWORLD))),
                "plains village should resolve to Overworld"
        );
        helper.assertTrue(
                !destinations.contains(new DestinyDestination("minecraft:village_plains", Optional.of(Level.NETHER))),
                "plains village must not resolve to Nether"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_destiny_dimensions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void structureTagUsesMemberPlacementsForDestinationDimensions(GameTestHelper helper) {
        List<DestinyDestination> destinations;
        try (var ignored = overrideDestinyConfig(
                List.of("#minecraft:village"), false, false, List.of())) {
            destinations = DestinyLocationResolver.resolve(helper.getLevel().getServer(), Config.SERVER);
        }

        helper.assertTrue(
                destinations.contains(new DestinyDestination("#minecraft:village", Optional.of(Level.OVERWORLD))),
                "village tag should resolve to Overworld"
        );
        helper.assertTrue(
                !destinations.contains(new DestinyDestination("#minecraft:village", Optional.of(Level.NETHER))),
                "village tag must not resolve to Nether"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_destiny_dimensions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void dimensionBlacklistRemovesValidDestinationDimension(GameTestHelper helper) {
        List<DestinyDestination> destinations;
        try (var ignored = overrideDestinyConfig(
                List.of("minecraft:bastion_remnant"), false, false, List.of("minecraft:the_nether"))) {
            destinations = DestinyLocationResolver.resolve(helper.getLevel().getServer(), Config.SERVER);
        }

        helper.assertTrue(
                !destinations.contains(new DestinyDestination("minecraft:bastion_remnant", Optional.of(Level.NETHER))),
                "dimension blacklist should remove valid Nether destinations"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_destiny_dimensions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void overworldOnlyKeepsDimensionlessAndOverworldDestinations(GameTestHelper helper) {
        List<DestinyDestination> destinations;
        try (var ignored = overrideDestinyConfig(
                List.of("somewhere", "minecraft:village_plains", "minecraft:bastion_remnant"), false, true, List.of())) {
            destinations = DestinyLocationResolver.resolve(helper.getLevel().getServer(), Config.SERVER);
        }

        helper.assertTrue(
                destinations.contains(new DestinyDestination("somewhere", Optional.empty())),
                "Overworld-only mode should keep dimensionless choices"
        );
        helper.assertTrue(
                destinations.contains(new DestinyDestination("minecraft:village_plains", Optional.of(Level.OVERWORLD))),
                "Overworld-only mode should keep Overworld destinations"
        );
        helper.assertTrue(
                !destinations.contains(new DestinyDestination("minecraft:bastion_remnant", Optional.of(Level.NETHER))),
                "Overworld-only mode should remove non-Overworld destinations"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_destiny_dimensions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void cachedDestinationsStayStableUntilExplicitRefresh(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        try (var ignored = overrideDestinyConfig(List.of("somewhere"), false, false, List.of())) {
            DestinyLocationResolver.refreshCachedDestinations(server, Config.SERVER);
            List<DestinyDestination> cached = DestinyLocationResolver.getCachedDestinations(server);

            Config.SERVER.destinySpawnLocations.set(List.of("minecraft:village_plains"));

            helper.assertTrue(
                    cached.equals(DestinyLocationResolver.getCachedDestinations(server)),
                    "cached Destiny destinations should not be recomputed when config changes"
            );
            helper.assertTrue(
                    cached.equals(List.of(new DestinyDestination("somewhere", Optional.empty()))),
                    "cached Destiny destinations should preserve the startup snapshot"
            );
        } finally {
            DestinyLocationResolver.refreshCachedDestinations(server, Config.SERVER);
        }
        helper.succeed();
    }

    private static DestinyConfigOverride overrideDestinyConfig(
            List<String> locations,
            boolean autoDiscover,
            boolean overworldOnly,
            List<String> dimensionBlacklist
    ) {
        DestinyConfigOverride previous = new DestinyConfigOverride(
                List.copyOf(Config.SERVER.destinySpawnLocations.get()),
                Config.SERVER.autoDiscoverDestinyLocations.get(),
                Config.SERVER.destinyOverworldOnly.get(),
                List.copyOf(Config.SERVER.destinyDimensionBlacklist.get())
        );
        Config.SERVER.destinySpawnLocations.set(List.copyOf(locations));
        Config.SERVER.autoDiscoverDestinyLocations.set(autoDiscover);
        Config.SERVER.destinyOverworldOnly.set(overworldOnly);
        Config.SERVER.destinyDimensionBlacklist.set(List.copyOf(dimensionBlacklist));
        return previous;
    }

    private record DestinyConfigOverride(
            List<String> locations,
            boolean autoDiscover,
            boolean overworldOnly,
            List<String> dimensionBlacklist
    ) implements AutoCloseable {
        @Override
        public void close() {
            Config.SERVER.destinySpawnLocations.set(locations);
            Config.SERVER.autoDiscoverDestinyLocations.set(autoDiscover);
            Config.SERVER.destinyOverworldOnly.set(overworldOnly);
            Config.SERVER.destinyDimensionBlacklist.set(dimensionBlacklist);
        }
    }
}
