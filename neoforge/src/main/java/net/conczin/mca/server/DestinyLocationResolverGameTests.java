package net.conczin.mca.server;

import com.google.gson.Gson;
import net.conczin.mca.CommonConfig;
import net.conczin.mca.destiny.DestinyDestination;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.util.List;
import java.util.Optional;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class DestinyLocationResolverGameTests {
    private static final Gson GSON = new Gson();

    private DestinyLocationResolverGameTests() {
    }

    @GameTest(batch = "mca_destiny_dimensions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void plainsVillagePlacementIsDimensionSpecific(GameTestHelper helper) {
        Registry<Structure> structures = helper.getLevel().registryAccess().lookupOrThrow(Registries.STRUCTURE);
        Holder.Reference<Structure> plainsVillage = structures.get(Identifier.parse("minecraft:village_plains"))
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
        CommonConfig config = new CommonConfig();
        config.destinySpawnLocations = List.of("somewhere", "minecraft:village_plains");
        config.autoDiscoverDestinyLocations = false;

        List<DestinyDestination> destinations = DestinyLocationResolver.resolve(helper.getLevel().getServer(), config);

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
        CommonConfig config = new CommonConfig();
        config.destinySpawnLocations = List.of("#minecraft:village");
        config.autoDiscoverDestinyLocations = false;

        List<DestinyDestination> destinations = DestinyLocationResolver.resolve(helper.getLevel().getServer(), config);

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
        CommonConfig config = GSON.fromJson("""
                {
                  "destinySpawnLocations": ["minecraft:bastion_remnant"],
                  "autoDiscoverDestinyLocations": false,
                  "destinyDimensionBlacklist": ["minecraft:the_nether"]
                }
                """, CommonConfig.class);

        List<DestinyDestination> destinations = DestinyLocationResolver.resolve(helper.getLevel().getServer(), config);

        helper.assertTrue(
                !destinations.contains(new DestinyDestination("minecraft:bastion_remnant", Optional.of(Level.NETHER))),
                "dimension blacklist should remove valid Nether destinations"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_destiny_dimensions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void overworldOnlyKeepsDimensionlessAndOverworldDestinations(GameTestHelper helper) {
        CommonConfig config = GSON.fromJson("""
                {
                  "destinySpawnLocations": ["somewhere", "minecraft:village_plains", "minecraft:bastion_remnant"],
                  "autoDiscoverDestinyLocations": false,
                  "destinyOverworldOnly": true
                }
                """, CommonConfig.class);

        List<DestinyDestination> destinations = DestinyLocationResolver.resolve(helper.getLevel().getServer(), config);

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
}
