package net.conczin.mca.server;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.conczin.mca.CommonConfig;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.destiny.DestinyDestination;
import net.conczin.mca.network.c2s.DestinyMessage;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.PiecesContainer;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
@PrefixGameTestTemplate(false)
public final class DestinyLocationResolverGameTests {
    private static final Gson GSON = new Gson();

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

    @GameTest(batch = "mca_destiny_dimensions", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void cachedDestinationsStayStableUntilExplicitRefresh(GameTestHelper helper) {
        CommonConfig config = new CommonConfig();
        config.destinySpawnLocations = List.of("somewhere");
        config.autoDiscoverDestinyLocations = false;

        var server = helper.getLevel().getServer();
        try {
            DestinyLocationResolver.refreshCachedDestinations(server, config);
            List<DestinyDestination> cached = DestinyLocationResolver.getCachedDestinations(server);

            config.destinySpawnLocations = List.of("minecraft:village_plains");

            helper.assertTrue(
                    cached.equals(DestinyLocationResolver.getCachedDestinations(server)),
                    "cached Destiny destinations should not be recomputed when config changes"
            );
            helper.assertTrue(
                    cached.equals(List.of(new DestinyDestination("somewhere", Optional.empty()))),
                    "cached Destiny destinations should preserve the startup snapshot"
            );
        } finally {
            DestinyLocationResolver.refreshCachedDestinations(server, Config.getInstance());
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_destiny_concurrency", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 1200)
    public static void concurrentStructureSearchesDoNotHeadOfLineBlock(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel overworld = server.overworld();
        Registry<Structure> structures = overworld.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Holder.Reference<Structure> stronghold = structures.getHolder(ResourceLocation.parse("minecraft:stronghold"))
                .orElseThrow();
        Holder.Reference<Structure> plainsVillage = structures.getHolder(ResourceLocation.parse("minecraft:village_plains"))
                .orElseThrow();
        ChunkGeneratorStructureState state = overworld.getChunkSource().getGeneratorState();
        ConcentricRingsStructurePlacement ringsPlacement = state.getPlacementsForStructure(stronghold).stream()
                .filter(ConcentricRingsStructurePlacement.class::isInstance)
                .map(ConcentricRingsStructurePlacement.class::cast)
                .findFirst()
                .orElseThrow();

        CompletableFuture<List<ChunkPos>> previousRings = state.ringPositions.get(ringsPlacement);
        CompletableFuture<List<ChunkPos>> stalledGate = new CompletableFuture<>();
        state.ringPositions.put(ringsPlacement, stalledGate);

        AsyncStructureLocator locator = new AsyncStructureLocator(server);
        AtomicBoolean cleanedUp = new AtomicBoolean(false);
        Runnable cleanup = () -> {
            if (cleanedUp.compareAndSet(false, true)) {
                if (previousRings != null) {
                    state.ringPositions.put(ringsPlacement, previousRings);
                } else {
                    state.ringPositions.remove(ringsPlacement);
                }
                locator.close();
            }
        };

        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        UUID owner1 = UUID.randomUUID();
        UUID owner2 = UUID.randomUUID();
        UUID owner3 = UUID.randomUUID();
        UUID owner4 = UUID.randomUUID();
        UUID fastOwner = UUID.randomUUID();
        UUID postCancelOwner = UUID.randomUUID();

        CompletableFuture<Optional<BlockPos>> stalled1 = locator.locate(owner1, overworld, origin, HolderSet.direct(stronghold), 1);
        CompletableFuture<Optional<BlockPos>> stalled2 = locator.locate(owner2, overworld, origin, HolderSet.direct(stronghold), 1);
        CompletableFuture<Optional<BlockPos>> stalled3 = locator.locate(owner3, overworld, origin, HolderSet.direct(stronghold), 1);

        @SuppressWarnings("unchecked")
        CompletableFuture<Optional<BlockPos>>[] dynamicFutures = new CompletableFuture[3];
        int[] stage = {0};
        int[] ticks = {0};

        helper.onEachTick(() -> {
            if (cleanedUp.get()) {
                return;
            }
            try {
                int elapsed = ++ticks[0];
                if (elapsed > 800) {
                    cleanup.run();
                    helper.fail("AsyncStructureLocator head-of-line blocked or leaked permits at stage " + stage[0]);
                    return;
                }
                if (stage[0] == 0) {
                    dynamicFutures[0] = locator.locate(fastOwner, overworld, origin, HolderSet.direct(plainsVillage), 0);
                    stage[0] = 1;
                } else if (stage[0] == 1 && dynamicFutures[0].isDone()) {
                    helper.assertTrue(!dynamicFutures[0].isCancelled() && !dynamicFutures[0].isCompletedExceptionally(),
                            "concurrent fast search failed or was cancelled");
                    helper.assertTrue(!stalled1.isDone(),
                            "stalled ring search should still be waiting when concurrent search completes");

                    dynamicFutures[1] = locator.locate(owner4, overworld, origin, HolderSet.direct(stronghold), 1);
                    stage[0] = 2;
                } else if (stage[0] == 2) {
                    locator.cancel(owner1);
                    locator.cancel(owner2);
                    locator.cancel(owner3);
                    locator.cancel(owner4);
                    helper.assertTrue(stalled1.isCancelled() && stalled2.isCancelled()
                                    && stalled3.isCancelled() && dynamicFutures[1].isCancelled(),
                            "cancelling owners must cancel their result futures");

                    dynamicFutures[2] = locator.locate(postCancelOwner, overworld, origin, HolderSet.direct(plainsVillage), 0);
                    stage[0] = 3;
                } else if (stage[0] == 3 && dynamicFutures[2].isDone()) {
                    helper.assertTrue(!dynamicFutures[2].isCancelled() && !dynamicFutures[2].isCompletedExceptionally(),
                            "cancelled searches must release concurrency permits for subsequent searches");
                    cleanup.run();
                    helper.succeed();
                }
            } catch (Throwable t) {
                cleanup.run();
                throw t;
            }
        });
    }

    @GameTest(batch = "mca_destiny_auth", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void unauthenticatedDestinyMessageIsRejected(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel overworld = server.overworld();
        Registry<Structure> structures = overworld.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Holder.Reference<Structure> stronghold = structures.getHolder(ResourceLocation.parse("minecraft:stronghold"))
                .orElseThrow();
        ChunkGeneratorStructureState state = overworld.getChunkSource().getGeneratorState();
        ConcentricRingsStructurePlacement ringsPlacement = state.getPlacementsForStructure(stronghold).stream()
                .filter(ConcentricRingsStructurePlacement.class::isInstance)
                .map(ConcentricRingsStructurePlacement.class::cast)
                .findFirst()
                .orElseThrow();

        CompletableFuture<List<ChunkPos>> previousRings = state.ringPositions.get(ringsPlacement);
        CompletableFuture<List<ChunkPos>> stalledGate = new CompletableFuture<>();
        state.ringPositions.put(ringsPlacement, stalledGate);

        Vec3 startPos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
        ServerPlayer player = new FakePlayer(overworld, new GameProfile(UUID.randomUUID(), "destiny-unauth")) {
            @Override
            protected int getPermissionLevel() {
                return 0;
            }

            @Override
            public boolean teleportTo(ServerLevel level, double x, double y, double z, Set<RelativeMovement> relativeMovements, float yRot, float xRot) {
                boolean result = super.teleportTo(level, x, y, z, relativeMovements, yRot, xRot);
                this.absMoveTo(x, y, z, yRot, xRot);
                return result;
            }
        };
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(startPos);

        CommonConfig testConfig = new CommonConfig();
        testConfig.destinySpawnLocations = List.of("somewhere", "minecraft:village_plains");
        testConfig.autoDiscoverDestinyLocations = false;

        try {
            DestinyLocationResolver.refreshCachedDestinations(server, testConfig);
            ServerInteractionManager.getInstance().clearDestinySession(player.getUUID());
            PlayerSaveData.get(player).setEntityDataSet(true);

            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 600));
            player.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, 600));

            BlockPos initialRespawn = player.getRespawnPosition();
            var initialRespawnDim = player.getRespawnDimension();

            CompletableFuture<Optional<BlockPos>> sentinelSearch = MCA.getStructureLocator().locate(
                    player.getUUID(), overworld, helper.absolutePos(BlockPos.ZERO), HolderSet.direct(stronghold), 1
            );

            DestinyDestination villageDestination = new DestinyDestination("minecraft:village_plains", Optional.of(Level.OVERWORLD));
            DestinyMessage.select(villageDestination).handle(player);
            DestinyMessage.close().handle(player);

            helper.assertTrue(!sentinelSearch.isCancelled() && !sentinelSearch.isDone(),
                    "unauthenticated DestinyMessage.select must not cancel or start structure searches");
            helper.assertTrue(player.position().equals(startPos),
                    "unauthenticated DestinyMessage must not teleport player");
            helper.assertTrue(Objects.equals(player.getRespawnPosition(), initialRespawn)
                            && Objects.equals(player.getRespawnDimension(), initialRespawnDim),
                    "unauthenticated DestinyMessage must not modify respawn position");
            helper.assertTrue(player.hasEffect(MobEffects.INVISIBILITY) && player.hasEffect(MobEffects.HEALTH_BOOST),
                    "unauthenticated DestinyMessage.close must not clear player effects");
        } finally {
            MCA.getStructureLocator().cancel(player.getUUID());
            ServerInteractionManager.getInstance().clearDestinySession(player.getUUID());
            if (previousRings != null) {
                state.ringPositions.put(ringsPlacement, previousRings);
            } else {
                state.ringPositions.remove(ringsPlacement);
            }
            DestinyLocationResolver.refreshCachedDestinations(server, Config.getInstance());
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_destiny_negative_y", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void destinyTeleportSupportsNegativeYAndRejectsVoid(GameTestHelper helper) {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        BlockPos columnOrigin = helper.absolutePos(new BlockPos(1, 0, 1));
        int x = columnOrigin.getX();
        int z = columnOrigin.getZ();
        overworld.getChunk(x >> 4, z >> 4);

        Map<Integer, BlockState> savedColumn = new HashMap<>();
        int minY = overworld.getMinBuildHeight();
        int maxY = overworld.getMaxBuildHeight() - 1;
        for (int y = minY; y <= maxY; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = overworld.getBlockState(pos);
            if (!state.isAir()) {
                savedColumn.put(y, state);
                overworld.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
            }
        }

        try {
            BlockPos deepFloor = new BlockPos(x, -52, z);
            overworld.setBlock(deepFloor, Blocks.DEEPSLATE_BRICKS.defaultBlockState(), 2);

            Optional<BlockPos> ancientCityLanding = DestinyMessage.resolveSafeTeleportPos(
                    overworld, "minecraft:ancient_city", new BlockPos(x, 64, z)
            );
            helper.assertTrue(
                    ancientCityLanding.equals(Optional.of(new BlockPos(x, -51, z))),
                    "ancient_city teleport should land on negative-Y floor at Y=-51, got " + ancientCityLanding
            );

            overworld.setBlock(deepFloor, Blocks.AIR.defaultBlockState(), 2);

            Optional<BlockPos> voidAncientCity = DestinyMessage.resolveSafeTeleportPos(
                    overworld, "minecraft:ancient_city", new BlockPos(x, 64, z)
            );
            Optional<BlockPos> voidSurface = DestinyMessage.resolveSafeTeleportPos(
                    overworld, "minecraft:village_plains", new BlockPos(x, 64, z)
            );
            helper.assertTrue(voidAncientCity.isEmpty(),
                    "ancient_city teleport over all-air void column must return empty, got " + voidAncientCity);
            helper.assertTrue(voidSurface.isEmpty(),
                    "surface teleport over all-air void column must return empty, got " + voidSurface);
        } finally {
            overworld.setBlock(new BlockPos(x, -52, z), Blocks.AIR.defaultBlockState(), 2);
            for (Map.Entry<Integer, BlockState> entry : savedColumn.entrySet()) {
                overworld.setBlock(new BlockPos(x, entry.getKey(), z), entry.getValue(), 2);
            }
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_destiny_async_failure", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void failedAsyncDestinationSelectionSettlesPendingSession(GameTestHelper helper) {
        runGatedDestinyFailures(helper, GatedFailure.LOOKUP);
    }

    @GameTest(batch = "mca_destiny_async_departure", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 200)
    public static void departedPlayerCannotReceiveLateDestinyTeleport(GameTestHelper helper) {
        runGatedDestinyFailures(helper, GatedFailure.DEPARTURE);
    }

    @GameTest(batch = "mca_destiny_async_disabled", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 200)
    public static void disablingDestinyWhilePendingPreventsTeleport(GameTestHelper helper) {
        runGatedDestinyFailures(helper, GatedFailure.DISABLED);
    }

    private enum GatedFailure {
        LOOKUP, DEPARTURE, DISABLED
    }

    private static void runGatedDestinyFailures(GameTestHelper helper, GatedFailure scenario) {
        var server = helper.getLevel().getServer();
        ServerLevel overworld = server.overworld();
        DestinyDestination destination = new DestinyDestination("minecraft:stronghold", Optional.of(Level.OVERWORLD));
        Holder.Reference<Structure> stronghold = overworld.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getHolder(ResourceLocation.parse(destination.location())).orElseThrow();
        ChunkGeneratorStructureState state = overworld.getChunkSource().getGeneratorState();
        ConcentricRingsStructurePlacement ringsPlacement = state.getPlacementsForStructure(stronghold).stream()
                .filter(ConcentricRingsStructurePlacement.class::isInstance)
                .map(ConcentricRingsStructurePlacement.class::cast)
                .findFirst().orElseThrow();

        GatedStronghold strongholdTarget = scenario == GatedFailure.LOOKUP
                ? null : prepareGatedStronghold(overworld, stronghold, ringsPlacement);
        CompletableFuture<List<ChunkPos>> ringGate = new CompletableFuture<>();
        CompletableFuture<List<ChunkPos>> previousRings = state.ringPositions.put(ringsPlacement, ringGate);
        boolean originalAllowTeleport = Config.getInstance().allowDestinyTeleportation;
        CommonConfig testConfig = new CommonConfig();
        testConfig.destinySpawnLocations = List.of(destination.location());
        testConfig.autoDiscoverDestinyLocations = false;
        ServerInteractionManager interactionManager = ServerInteractionManager.getInstance();
        DestinyTestPlayer first = new DestinyTestPlayer(overworld, "destiny-first");
        DestinyTestPlayer second = new DestinyTestPlayer(overworld, "destiny-second");
        Vec3 startPos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
        first.setPos(startPos);
        second.setPos(startPos);

        AtomicBoolean cleanedUp = new AtomicBoolean();
        Runnable cleanup = () -> {
            if (!cleanedUp.compareAndSet(false, true)) {
                return;
            }
            interactionManager.clearDestinySession(first.getUUID());
            interactionManager.clearDestinySession(second.getUUID());
            first.channel.finishAndReleaseAll();
            second.channel.finishAndReleaseAll();
            state.ringPositions.put(ringsPlacement, previousRings);
            if (strongholdTarget != null) {
                strongholdTarget.restore().run();
            }
            Config.getInstance().allowDestinyTeleportation = originalAllowTeleport;
            DestinyLocationResolver.refreshCachedDestinations(server, Config.getInstance());
        };

        try {
            DestinyLocationResolver.refreshCachedDestinations(server, testConfig);
            Config.getInstance().allowDestinyTeleportation = true;
            for (DestinyTestPlayer player : List.of(first, second)) {
                helper.assertTrue(player.connection.isAcceptingMessages(), "Destiny selection requires a live play connection");
                PlayerSaveData.get(player).setEntityDataSet(false);
                player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 600));
                ServerInteractionManager.launchDestiny(player);
                DestinyMessage.select(destination).handle(player);
                helper.assertTrue(interactionManager.hasPendingDestinyTeleport(player),
                        "authorized stronghold selection must await the gated search");
            }
            switch (scenario) {
                case LOOKUP -> {
                    // The locator's cancel path completes the actual packet's future exceptionally.
                    MCA.getStructureLocator().cancel(first.getUUID());
                    assertFailedDestinySelection(helper, first, startPos);
                }
                case DEPARTURE -> {
                    first.channel.close().syncUninterruptibly();
                    helper.assertTrue(!first.connection.isAcceptingMessages() && !first.hasDisconnected() && !first.isRemoved(),
                            "closed channel must precede vanilla's player logout and entity removal");
                    second.setServerLevel(Objects.requireNonNull(server.getLevel(Level.NETHER)));
                }
                case DISABLED -> Config.getInstance().allowDestinyTeleportation = false;
            }
            ringGate.complete(strongholdTarget == null ? List.of() : List.of(strongholdTarget.chunk()));
        } catch (Throwable error) {
            cleanup.run();
            throw error;
        }

        helper.runAtTickTime(90, () -> {
            if (!cleanedUp.get()) {
                cleanup.run();
                helper.fail("Destiny searches did not release their pending selections");
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(!interactionManager.hasPendingDestinyTeleport(first)
                                && !interactionManager.hasPendingDestinyTeleport(second),
                        "Destiny searches must release pending selections"))
                .thenExecute(() -> {
                    try {
                        if (scenario == GatedFailure.DEPARTURE) {
                            helper.assertTrue(first.failureMessages == 0 && first.teleports == 0
                                            && !PlayerSaveData.get(first).isEntityDataSet(),
                                    "closed play connection must receive no late Destiny teleport, feedback or onboarding mutations");
                            helper.assertTrue(second.serverLevel() == server.getLevel(Level.NETHER),
                                    "late Destiny selection must preserve the player's new world");
                        } else if (scenario == GatedFailure.DISABLED) {
                            assertFailedDestinySelection(helper, first, startPos);
                        }
                        assertFailedDestinySelection(helper, second, startPos);
                        ServerInteractionManager.launchDestiny(second);
                        helper.assertTrue(interactionManager.hasActiveDestinySession(second),
                                "finished failure must permit a newly authorized selection");
                    } finally {
                        cleanup.run();
                    }
                })
                .thenSucceed();
    }

    private static void assertFailedDestinySelection(GameTestHelper helper, DestinyTestPlayer player, Vec3 startPos) {
        helper.assertTrue(!ServerInteractionManager.getInstance().hasPendingDestinyTeleport(player),
                "failed selection must release its pending request");
        helper.assertTrue(player.failureMessages == 1, "eligible player must receive exactly one Destiny failure message");
        helper.assertTrue(PlayerSaveData.get(player).isEntityDataSet(),
                "failed selection must complete onboarding");
        helper.assertTrue(!player.hasEffect(MobEffects.INVISIBILITY),
                "failed selection must clear onboarding effects when it completes before UI close");
        helper.assertTrue(player.position().equals(startPos) && player.teleports == 0,
                "failed selection must leave the player at the original position");
    }

    private static final class DestinyTestPlayer extends FakePlayer {
        private final EmbeddedChannel channel;
        private int failureMessages;
        private int teleports;

        private DestinyTestPlayer(ServerLevel level, String name) {
            super(level, new GameProfile(UUID.randomUUID(), name));
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            new ServerGamePacketListenerImpl(level.getServer(), connection, this,
                    CommonListenerCookie.createInitial(getGameProfile(), false)) {
                @Override
                public void send(Packet<?> packet, PacketSendListener listener) {
                    // This GameTest exercises play connection liveness without negotiating payload codecs.
                }
            };
        }

        @Override
        protected int getPermissionLevel() {
            return 0;
        }

        @Override
        public void sendSystemMessage(Component message) {
            if (message.getContents() instanceof TranslatableContents translation
                    && translation.getKey().equals("destiny.teleport.failed")) {
                failureMessages++;
            }
        }

        @Override
        public boolean teleportTo(ServerLevel level, double x, double y, double z, Set<RelativeMovement> relativeMovements,
                                  float yRot, float xRot) {
            teleports++;
            boolean result = super.teleportTo(level, x, y, z, relativeMovements, yRot, xRot);
            absMoveTo(x, y, z, yRot, xRot);
            return result;
        }
    }

    @GameTest(batch = "mca_destiny_story_close", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 200)
    public static void destinyCloseDuringPendingSearchPreservesTeleportAndRejectsSecondSelect(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        ServerLevel overworld = server.overworld();
        Registry<Structure> structures = overworld.registryAccess().registryOrThrow(Registries.STRUCTURE);
        Holder.Reference<Structure> stronghold = structures.getHolder(ResourceLocation.parse("minecraft:stronghold"))
                .orElseThrow();
        ChunkGeneratorStructureState state = overworld.getChunkSource().getGeneratorState();
        ConcentricRingsStructurePlacement ringsPlacement = state.getPlacementsForStructure(stronghold).stream()
                .filter(ConcentricRingsStructurePlacement.class::isInstance)
                .map(ConcentricRingsStructurePlacement.class::cast)
                .findFirst()
                .orElseThrow();

        GatedStronghold strongholdTarget = prepareGatedStronghold(overworld, stronghold, ringsPlacement);
        BlockPos expectedLanding = strongholdTarget.landing();

        CompletableFuture<List<ChunkPos>> previousRings = state.ringPositions.get(ringsPlacement);
        CompletableFuture<List<ChunkPos>> stalledGate = new CompletableFuture<>();
        state.ringPositions.put(ringsPlacement, stalledGate);

        boolean originalAllowTeleport = Config.getInstance().allowDestinyTeleportation;
        CommonConfig testConfig = new CommonConfig();
        testConfig.destinySpawnLocations = List.of("minecraft:stronghold", "minecraft:village_plains");
        testConfig.autoDiscoverDestinyLocations = false;
        DestinyLocationResolver.refreshCachedDestinations(server, testConfig);
        Config.getInstance().allowDestinyTeleportation = true;

        Vec3 startPos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
        DestinyTestPlayer player = new DestinyTestPlayer(overworld, "destiny-story-close");
        player.setGameMode(GameType.SURVIVAL);
        player.setPos(startPos);

        AtomicBoolean cleanedUp = new AtomicBoolean(false);
        Runnable cleanup = () -> {
            if (cleanedUp.compareAndSet(false, true)) {
                ServerInteractionManager.getInstance().clearDestinySession(player.getUUID());
                player.channel.finishAndReleaseAll();
                strongholdTarget.restore().run();
                if (previousRings != null) {
                    state.ringPositions.put(ringsPlacement, previousRings);
                } else {
                    state.ringPositions.remove(ringsPlacement);
                }
                Config.getInstance().allowDestinyTeleportation = originalAllowTeleport;
                DestinyLocationResolver.refreshCachedDestinations(server, Config.getInstance());
            }
        };

        try {
            PlayerSaveData.get(player).setEntityDataSet(false);
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 600));
            player.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, 600));
            ServerInteractionManager.launchDestiny(player);

            DestinyMessage.select(new DestinyDestination("minecraft:stronghold", Optional.of(Level.OVERWORLD))).handle(player);
            helper.assertTrue(!ServerInteractionManager.getInstance().hasActiveDestinySession(player),
                    "first DestinyMessage.select must consume active UI session");
            helper.assertTrue(ServerInteractionManager.getInstance().hasPendingDestinyTeleport(player),
                    "first DestinyMessage.select must register pending Destiny teleport");

            // A second select while pending must be rejected without cancelling the first search.
            DestinyMessage.select(new DestinyDestination("minecraft:village_plains", Optional.of(Level.OVERWORLD))).handle(player);
            helper.assertTrue(ServerInteractionManager.getInstance().hasPendingDestinyTeleport(player),
                    "second DestinyMessage.select must be rejected while first teleport is pending");

            // Story completion sends close() before gated search finishes: effects clear & entityDataSet=true, teleport preserved.
            DestinyMessage.close().handle(player);
            helper.assertTrue(!player.hasEffect(MobEffects.INVISIBILITY) && !player.hasEffect(MobEffects.HEALTH_BOOST),
                    "DestinyMessage.close during pending teleport must still clear onboarding effects");
            helper.assertTrue(PlayerSaveData.get(player).isEntityDataSet(),
                    "DestinyMessage.close must mark entityDataSet=true even without VillagerEditorSyncRequest");
            helper.assertTrue(ServerInteractionManager.getInstance().hasPendingDestinyTeleport(player),
                    "DestinyMessage.close must not cancel pending Destiny teleport");
            helper.assertTrue(player.position().equals(startPos),
                    "player should not teleport before gated ringPositions future completes");

            stalledGate.complete(List.of(strongholdTarget.chunk()));
        } catch (Throwable t) {
            cleanup.run();
            throw t;
        }

        int[] ticks = {0};
        helper.onEachTick(() -> {
            if (cleanedUp.get()) {
                return;
            }
            try {
                if (++ticks[0] > 160) {
                    cleanup.run();
                    helper.fail("pending Destiny teleport did not complete after stalledGate resolved");
                    return;
                }
                if (!ServerInteractionManager.getInstance().hasPendingDestinyTeleport(player)) {
                    helper.assertTrue(expectedLanding.equals(player.blockPosition()),
                            "pending Destiny teleport should move player to " + expectedLanding + ", got " + player.blockPosition());
                    helper.assertTrue(expectedLanding.equals(player.getRespawnPosition()),
                            "pending Destiny teleport should update respawn position to " + expectedLanding);
                    cleanup.run();
                    helper.succeed();
                }
            } catch (Throwable t) {
                cleanup.run();
                throw t;
            }
        });
    }

    private record GatedStronghold(ChunkPos chunk, BlockPos landing, Runnable restore) {}

    private static GatedStronghold prepareGatedStronghold(ServerLevel overworld, Holder.Reference<Structure> stronghold,
                                                        ConcentricRingsStructurePlacement ringsPlacement) {
        int freshChunkX = 2400 + Math.floorMod(UUID.randomUUID().hashCode(), 400);
        int freshChunkZ = 2400 + Math.floorMod(UUID.randomUUID().hashCode(), 400);
        ChunkPos chunk = new ChunkPos(freshChunkX, freshChunkZ);
        LevelChunk loadedChunk = overworld.getChunk(chunk.x, chunk.z);
        StructureStart originalStart = loadedChunk.getStartForStructure(stronghold.value());
        StructureStart syntheticStart = new StructureStart(
                stronghold.value(),
                chunk,
                0,
                new PiecesContainer(List.of(new StrongholdPieces.StartPiece(
                        RandomSource.create(0L),
                        chunk.getBlockX(2),
                        chunk.getBlockZ(2)
                )))
        );
        loadedChunk.setStartForStructure(stronghold.value(), syntheticStart);

        BlockPos rawLocatePos = ringsPlacement.getLocatePos(chunk);
        BlockPos floor = rawLocatePos.atY(overworld.getMinBuildHeight());
        BlockState originalFloor = overworld.getBlockState(floor);
        boolean needsFloor = DestinyMessage.resolveSafeTeleportPos(overworld, "minecraft:stronghold", rawLocatePos).isEmpty();
        if (needsFloor) {
            overworld.setBlock(floor, Blocks.STONE.defaultBlockState(), 2);
        }
        BlockPos landing = DestinyMessage.resolveSafeTeleportPos(overworld, "minecraft:stronghold", rawLocatePos)
                .orElseThrow();
        return new GatedStronghold(chunk, landing, () -> {
            loadedChunk.setStartForStructure(stronghold.value(),
                    originalStart == null ? StructureStart.INVALID_START : originalStart);
            if (needsFloor) {
                overworld.setBlock(floor, originalFloor, 2);
            }
        });
    }
}
