package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.navigation.PathRequestDiagnostics;
import net.conczin.mca.entity.ai.navigation.MCAGroundPathNavigation;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.gametest.GameTestPoi;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Disposable, separated arenas for house admission and whole-house discovery. */
@PrefixGameTestTemplate(false)
public final class ShelterDistributionGameTests {
    private static final String BATCH = "mca_shelter_distribution";
    private static final String GLOBAL_STATE_BATCH = "mca_shelter_distribution_global_state";
    private static final Map<BlockPos, BlockState> BLOCKS = new LinkedHashMap<>();
    private static final Set<ChunkPos> CHUNKS = new HashSet<>();
    private static final List<Villager> VILLAGERS = new ArrayList<>();
    private static final List<LivingEntity> THREATS = new ArrayList<>();
    private static final Set<Integer> VILLAGES = new HashSet<>();
    private static Long previousDayTime;
    private static Boolean previousDaylight;
    private static Boolean previousTeleport;

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void connectedFloorsShareCapacityIdentity(GameTestHelper helper) {
        BlockPos center = arena(helper, 16);
        BlockPos lower = house(helper.getLevel(), center);
        room(helper.getLevel(), center.above(4));
        BlockPos upper = bed(helper.getLevel(), center.above(4).south());
        for (int y = 0; y <= 4; y++) {
            put(helper.getLevel(), center.east(4).south(3).above(y),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
        }
        helper.runAfterDelay(10, () -> {
            var result = VillageManager.get(helper.getLevel()).getIndoorRooms().resolveHouse(lower).orElseThrow();
            helper.assertTrue(result.membershipKnown(), "connected floors were only partially resolved");
            helper.assertTrue(result.bedHeads().equals(Set.of(lower, upper)), "upper-floor bed was omitted");
            helper.assertTrue(result.contains(upper) && result.contains(lower.above()), "physical vertical membership omitted occupants");
            helper.assertTrue(!result.contains(center.above(8)), "roof counted as interior occupant space");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void registeredDiscoveryDoesNotMutateVillage(GameTestHelper helper) {
        BlockPos center = arena(helper, 17);
        BlockPos first = house(helper.getLevel(), center);
        BlockPos second = addRoom(helper.getLevel(), center);
        helper.runAfterDelay(10, () -> {
            var manager = VillageManager.get(helper.getLevel());
            helper.assertTrue(manager.processBuilding(center) == Building.validationResult.SUCCESS, "fixture registration failed");
            var village = manager.findNearestVillage(center, 0).orElseThrow();
            VILLAGES.add(village.getId());
            village.setAutoScan(false);
            var before = village.save();
            var result = manager.getIndoorRooms().resolveHouse(first).orElseThrow();
            helper.assertTrue(result.membershipKnown() && result.bedHeads().equals(Set.of(first, second)), "registered discovery lost fresh topology");
            helper.assertTrue(before.equals(village.save()), "read-only discovery mutated registered village");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void multipleBedsDoNotHideNextHouse(GameTestHelper helper) {
        BlockPos center = arena(helper, 18);
        house(helper.getLevel(), center);
        put(helper.getLevel(), center.south(), Blocks.AIR.defaultBlockState());
        put(helper.getLevel(), center.east().south(), Blocks.AIR.defaultBlockState());
        for (int z : new int[]{0, 2, 4}) {
            bed(helper.getLevel(), center.south(z));
            bed(helper.getLevel(), center.east(2).south(z));
        }
        BlockPos alternative = house(helper.getLevel(), center.east(16));
        fill(helper, center, 11);
        var incoming = spawn(helper, center.east(2).south(9));
        helper.runAfterDelay(10, () -> {
            var before = PathRequestDiagnostics.snapshot(incoming);
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(alternative), "five beds hid the next house");
            var after = PathRequestDiagnostics.snapshot(incoming);
            int requests = after.ordinarySearches() - before.ordinarySearches()
                    + after.extendedSearches() - before.extendedSearches();
            helper.assertTrue(requests >= 1 && requests <= 2,
                    "two-house selection exceeded one route search per house: " + requests);
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void vanillaOwnerChildAndSleeperCountOnce(GameTestHelper helper) {
        BlockPos center = arena(helper, 19);
        BlockPos nearest = house(helper.getLevel(), center);
        BlockPos alternative = house(helper.getLevel(), center.east(16));
        fill(helper, center, 3);
        Villager owner = new Villager(EntityTypes.VILLAGER, helper.getLevel());
        owner.setPos(Vec3.atBottomCenterOf(center.east(3).south(3)));
        owner.setNoAi(true);
        owner.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), nearest));
        helper.getLevel().addFreshEntity(owner);
        VILLAGERS.add(owner);
        var child = spawn(helper, center.east(4).south(3));
        child.setAge(-24000);
        var sleeper = spawn(helper, center.east(3).south(4));
        var incoming = spawn(helper, center.east(2).south(9));
        helper.runAfterDelay(10, () -> {
            claim(helper, nearest);
            sleeper.startSleeping(nearest);
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(alternative), "mixed vanilla/child/sleeping occupants were omitted");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void exactlyThirtyExtraRouteBlocksAreAllowed(GameTestHelper helper) {
        verifyRouteAllowance(helper, 20, 30, true);
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void moreThanThirtyExtraRouteBlocksAreAllowed(GameTestHelper helper) {
        verifyRouteAllowance(helper, 21, 31, true);
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void fullFirstFiveHousesDoNotHideSixthHouse(GameTestHelper helper) {
        BlockPos center = arena(helper, 45);
        for (BlockPos position : List.of(center, center.east(9), center.east(18),
                center.east(27), center.south(12))) {
            house(helper.getLevel(), position);
            fill(helper, position, 6);
        }
        BlockPos alternative = house(helper.getLevel(), center.east(30).south(12));
        door(helper.getLevel(), center.east(32).south(11), Direction.SOUTH);
        var incoming = spawn(helper, center.east(2).south(9));
        helper.runAfterDelay(10, () -> {
            Path near = pathToRoom(helper, incoming, center.east().south());
            Path far = pathToRoom(helper, incoming, alternative);
            helper.assertTrue(SeekIndoorShelterTask.routeLength(incoming, far)
                    - SeekIndoorShelterTask.routeLength(incoming, near) <= 30.0D,
                    "sixth-house fixture exceeded the old travel allowance");
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(alternative), "five full houses hid a reachable house with space");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void exactlySixtyFourExtraRouteBlocksAreAllowed(GameTestHelper helper) {
        verifyLongRouteAllowance(helper, 46, 64, false, true);
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void moreThanSixtyFourExtraRouteBlocksUsesOverflow(GameTestHelper helper) {
        verifyLongRouteAllowance(helper, 47, 65, false, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void lessCrowdedOverflowRespectsAdditionalTravelLimit(GameTestHelper helper) {
        verifyLongRouteAllowance(helper, 48, 65, true, false);
    }

    private static void verifyLongRouteAllowance(GameTestHelper helper, int id, int extra,
                                                boolean alternativeFull, boolean allowed) {
        BlockPos center = arena(helper, id);
        BlockPos nearest = house(helper.getLevel(), center);
        BlockPos alternative = house(helper.getLevel(), center.east(16));
        fill(helper, center, alternativeFull ? 12 : 6);
        if (alternativeFull) fill(helper, center.east(16), 6);
        BlockPos origin = center.east(2).south(7);
        BlockPos nearTarget = center.east(2).south(2);
        BlockPos farTarget = center.east(extra % 2 == 0 ? 18 : 17).south(2);
        int directExtra = farTarget.getX() - nearTarget.getX();
        BlockPos detour = origin.west((extra - directExtra) / 2);
        // Supply exact-length navigation results to test admission separately from path search.
        Path near = new Path(List.of(new Node(origin.getX(), origin.getY(), origin.getZ()),
                new Node(nearTarget.getX(), nearTarget.getY(), nearTarget.getZ())), nearTarget, true);
        Path far = new Path(List.of(new Node(origin.getX(), origin.getY(), origin.getZ()),
                new Node(detour.getX(), detour.getY(), detour.getZ()),
                new Node(detour.getX(), detour.getY(), farTarget.getZ()),
                new Node(farTarget.getX(), farTarget.getY(), farTarget.getZ())), farTarget, true);
        var incoming = new VillagerEntityMCA(Gender.MALE.getVillagerType(), helper.getLevel(), Gender.MALE) {
            @Override
            protected PathNavigation createNavigation(Level level) {
                return new MCAGroundPathNavigation(this, level) {
                    @Override
                    public Path createPathForPersistentIntent(Set<BlockPos> targets, int reachRange) {
                        return targets.contains(farTarget) ? far : near;
                    }
                };
            }
        };
        incoming.setPos(near.getEntityPosAtNode(incoming, 0));
        incoming.setNoAi(true);
        incoming.setOnGround(true);
        helper.getLevel().addFreshEntity(incoming);
        VILLAGERS.add(incoming);
        incoming.refreshBrain(helper.getLevel());
        incoming.getBrain().eraseMemory(MemoryModuleType.HOME);
        incoming.getBrain().setActiveActivityIfPossible(Activity.REST);
        helper.runAfterDelay(10, () -> {
            double difference = SeekIndoorShelterTask.routeLength(incoming, far) - SeekIndoorShelterTask.routeLength(incoming, near);
            helper.assertTrue(Math.abs(difference - extra) < 1.0E-6D, "fixture route difference is " + difference + ", expected " + extra);
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(allowed ? alternative : nearest), "inclusive additional-route policy failed");
            helper.succeed();
        });
    }

    private static void verifyRouteAllowance(GameTestHelper helper, int id, int extra, boolean allowed) {
        BlockPos center = arena(helper, id);
        BlockPos nearest = house(helper.getLevel(), center);
        BlockPos alternative = house(helper.getLevel(), center.east(extra));
        fill(helper, center, 6);
        var incoming = spawn(helper, center.east(2).south(7));
        // Force both routes along the same straight exterior corridor before turning indoors.
        for (int x = 3; x < extra + 2; x++) {
            put(helper.getLevel(), center.offset(x, 0, 6), Blocks.STONE.defaultBlockState());
            put(helper.getLevel(), center.offset(x, 1, 6), Blocks.STONE.defaultBlockState());
        }
        incoming.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(64.0D);
        helper.runAfterDelay(10, () -> {
            Path near = pathToRoom(helper, incoming, nearest);
            Path far = pathToRoom(helper, incoming, alternative);
            double difference = SeekIndoorShelterTask.routeLength(incoming, far) - SeekIndoorShelterTask.routeLength(incoming, near);
            helper.assertTrue(Math.abs(difference - extra) < 1.0E-6D, "fixture route difference is " + difference + ", expected " + extra);
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(allowed ? alternative : nearest), "inclusive additional-route policy failed");
            helper.succeed();
        });
    }

    private static Path pathToRoom(GameTestHelper helper, VillagerEntityMCA villager, BlockPos bed) {
        var room = VillageManager.get(helper.getLevel()).getIndoorRooms().resolve(bed).orElseThrow();
        Set<BlockPos> floor = new HashSet<>();
        for (BlockPos pos : room.floorCells()) if (EnterBuildingTask.isUsableFloor(helper.getLevel(), villager, pos)) floor.add(pos);
        Path path = ((MCAGroundPathNavigation)villager.getNavigation()).createPathForPersistentIntent(floor, 0);
        helper.assertTrue(path != null && path.canReach(), "fixture room is unreachable: bed=" + bed + " candidates=" + floor.size()
                + " path=" + path + " end=" + (path == null ? null : path.getEndNode()) + " distance=" + (path == null ? -1 : path.getDistToTarget()));
        return path;
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void routeLengthUsesNativeNodePositionsAndScale(GameTestHelper helper) {
        BlockPos center = arena(helper, 22);
        var villager = spawn(helper, center);
        for (double scale : new double[]{1.0D, 2.0D}) {
            villager.getAttribute(Attributes.SCALE).setBaseValue(scale);
            villager.refreshDimensions();
            for (int[] offset : List.of(new int[]{8, 0, 0}, new int[]{1, 0, 1}, new int[]{0, 3, 0})) {
                BlockPos end = center.offset(offset[0], offset[1], offset[2]);
                Path path = new Path(List.of(new Node(center.getX(), center.getY(), center.getZ()),
                        new Node(end.getX(), end.getY(), end.getZ())), end, true);
                villager.setPos(path.getEntityPosAtNode(villager, 0));
                double expected = Math.sqrt(offset[0] * offset[0] + offset[1] * offset[1] + offset[2] * offset[2]);
                helper.assertTrue(Math.abs(SeekIndoorShelterTask.routeLength(villager, path) - expected) < 1.0E-6D,
                        "route length used node count or ignored entity scale");
            }
        }
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void arrivedElsewhereDoesNotFollowStaleSelection(GameTestHelper helper) {
        BlockPos center = arena(helper, 23);
        BlockPos previous = house(helper.getLevel(), center);
        house(helper.getLevel(), center.east(16));
        var sheltered = spawn(helper, center.east(19).south(3));
        sheltered.getBrain().setMemory(MemoryModuleTypeMCA.SHELTER_BED, GlobalPos.of(helper.getLevel().dimension(), previous));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!new SeekIndoorShelterTask(0.5F).tryStart(helper.getLevel(), sheltered,
                    helper.getLevel().getGameTime()), "sheltered villager was sent back to a stale selected bed");
            helper.assertTrue(!sheltered.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET), "capacity evicted a sheltered guest");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 400)
    public static void unloadedPoiDoesNotForceChunkDuringShelterSearch(GameTestHelper helper) {
        BlockPos center = arena(helper, 24);
        BlockPos nearest = house(helper.getLevel(), center);
        BlockPos distant = house(helper.getLevel(), center.east(40));
        ChunkPos unloaded = ChunkPos.containing(distant);
        helper.runAfterDelay(10, () -> {
            ChunkPos min = ChunkPos.containing(center.offset(-32, 0, -32));
            ChunkPos max = ChunkPos.containing(center.offset(64, 0, 40));
            for (int x = min.x(); x <= max.x(); x++) for (int z = min.z(); z <= max.z(); z++) {
                helper.getLevel().setChunkForced(x, z, false);
            }
            helper.startSequence().thenWaitUntil(() -> {
                helper.assertTrue(helper.getLevel().getChunkSource().getChunkNow(unloaded.x(), unloaded.z()) == null, "fixture chunk has not unloaded yet");
            }).thenExecute(() -> {
                for (int x = (nearest.getX() - 17) >> 4; x <= (nearest.getX() + 17) >> 4; x++) {
                    for (int z = (nearest.getZ() - 17) >> 4; z <= (nearest.getZ() + 17) >> 4; z++) helper.getLevel().getChunk(x, z);
                }
                var incoming = spawn(helper, center.east(2).south(9));
                choose(helper, incoming);
                helper.assertTrue(selected(incoming).equals(nearest), "unloaded alternative defeated shelter");
                helper.assertTrue(helper.getLevel().getChunkSource().getChunkNow(unloaded.x(), unloaded.z()) == null, "shelter search forced the POI chunk");
            }).thenExecute(() -> reloadArenaChunks(helper.getLevel(), center)).thenIdle(20).thenSucceed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void unreachableAlternativeUsesOverflow(GameTestHelper helper) {
        BlockPos center = arena(helper, 27);
        BlockPos nearest = house(helper.getLevel(), center);
        house(helper.getLevel(), center.east(16));
        for (int y = 0; y < 3; y++) put(helper.getLevel(), center.offset(18, y, 5), Blocks.STONE.defaultBlockState());
        fill(helper, center, 6);
        var incoming = spawn(helper, center.east(2).south(9));
        helper.runAfterDelay(10, () -> {
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(nearest), "unreachable alternative blocked overflow");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air",
            timeoutTicks = 220)
    public static void unreachableShelterBacksOffRepeatedRouteSearches(GameTestHelper helper) {
        BlockPos center = arena(helper, 49);
        house(helper.getLevel(), center);
        var incoming = spawn(helper, center.east(2).south(9));
        BlockPos trapped = incoming.blockPosition();
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) continue;
                put(helper.getLevel(), trapped.offset(x, 0, z), Blocks.STONE.defaultBlockState());
                put(helper.getLevel(), trapped.offset(x, 1, z), Blocks.STONE.defaultBlockState());
            }
        }
        SeekIndoorShelterTask shelter = new SeekIndoorShelterTask(0.5F);
        helper.runAfterDelay(10, () -> {
            long gameTime = helper.getLevel().getGameTime();
            helper.assertTrue(shelter.checkExtraStartConditions(helper.getLevel(), incoming),
                    "first shelter search was unexpectedly throttled");
            var before = PathRequestDiagnostics.snapshot(incoming);
            helper.assertTrue(shelter.getNextPosition(incoming).isEmpty(),
                    "trapped villager unexpectedly found a shelter route");
            var after = PathRequestDiagnostics.snapshot(incoming);
            int requests = after.ordinarySearches() - before.ordinarySearches()
                    + after.extendedSearches() - before.extendedSearches();
            helper.assertTrue(requests > 0, "fixture did not exercise pathfinding");
            helper.assertTrue(!shelter.checkExtraStartConditions(helper.getLevel(), incoming),
                    "failed search did not retain its immediate retry throttle");
        });
        helper.runAfterDelay(60, () -> helper.assertTrue(
                !shelter.checkExtraStartConditions(helper.getLevel(), incoming),
                "unchanged failed shelter retried pathfinding too soon"));
        helper.runAfterDelay(140, () -> {
            helper.assertTrue(shelter.checkExtraStartConditions(helper.getLevel(), incoming),
                    "failed shelter never became eligible for a bounded retry");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void distantRememberedHostileDoesNotForceOverflow(GameTestHelper helper) {
        BlockPos center = arena(helper, 28);
        house(helper.getLevel(), center);
        BlockPos alternative = house(helper.getLevel(), center.east(16));
        fill(helper, center, 6);
        var incoming = spawn(helper, center.east(2).south(9));
        var threat = new Zombie(EntityTypes.ZOMBIE, helper.getLevel());
        threat.setPos(incoming.position().add(30, 0, 0));
        THREATS.add(threat);
        incoming.getBrain().setMemory(MemoryModuleType.NEAREST_HOSTILE, threat);
        helper.runAfterDelay(10, () -> {
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(alternative), "distant stale hostile forced overflow");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void nearbyAttackerUsesSixBlockBoundary(GameTestHelper helper) {
        BlockPos center = arena(helper, 29);
        BlockPos nearest = house(helper.getLevel(), center);
        BlockPos alternative = house(helper.getLevel(), center.east(16));
        fill(helper, center, 6);
        var incoming = spawn(helper, center.east(2).south(9));
        var threat = new Villager(EntityTypes.VILLAGER, helper.getLevel());
        THREATS.add(threat);
        helper.runAfterDelay(10, () -> {
            threat.setPos(incoming.position().add(6, 0, 0));
            incoming.getBrain().setMemory(MemoryModuleType.HURT_BY_ENTITY, threat);
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(nearest), "nearby attacker ignored");
            incoming.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            threat.setPos(incoming.position().add(6.01D, 0, 0));
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(alternative), "distant remembered attacker forced overflow");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void sharedHouseDiscoveryReusesRoomsAtBoundedCost(GameTestHelper helper) {
        BlockPos center = arena(helper, 25);
        BlockPos first = house(helper.getLevel(), center);
        addRoom(helper.getLevel(), center);
        BlockPos second = house(helper.getLevel(), center.east(16));
        var incoming = spawn(helper, center.east(2).south(9));
        helper.runAfterDelay(10, () -> {
            var cache = VillageManager.get(helper.getLevel()).getIndoorRooms();
            long start = System.nanoTime();
            var cold = cache.resolveHouses(List.of(first, second), 5);
            long coldNanos = System.nanoTime() - start;
            start = System.nanoTime();
            var warm = cache.resolveHouses(List.of(first, second), 5);
            long warmNanos = System.nanoTime() - start;
            helper.assertTrue(cold.size() == 2 && warm.size() == 2, "house grouping lost candidate");
            helper.assertTrue(cold.getFirst().rooms().stream().allMatch(room -> warm.getFirst().rooms().stream().anyMatch(other -> room == other)), "warm lookup repeated room materialization");
            var before = PathRequestDiagnostics.snapshot(incoming);
            start = System.nanoTime();
            choose(helper, incoming);
            long selectionNanos = System.nanoTime() - start;
            var after = PathRequestDiagnostics.snapshot(incoming);
            int requests = after.ordinarySearches() - before.ordinarySearches()
                    + after.extendedSearches() - before.extendedSearches();
            helper.assertTrue(requests >= 1 && requests <= 2,
                    "house selection exceeded two batched path requests: " + requests);
            int loaded = 0;
            for (var entity : helper.getLevel().getAllEntities()) if (entity instanceof Villager) loaded++;
            MCA.LOGGER.info("[ShelterDistribution] houses=2 materializedCells={} loadedVillagers={} coldMicros={} warmMicros={} selectionMicros={} pathRequests={}",
                    cold.stream().mapToInt(house -> house.floorCells().size()).sum(), loaded, coldNanos / 1000, warmNanos / 1000, selectionNanos / 1000, requests);
            helper.succeed();
        });
    }

    @GameTest(batch = GLOBAL_STATE_BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 500)
    public static void consecutiveArrivalsEnterDifferentHousesAtCapacity(GameTestHelper helper) {
        BlockPos center = arena(helper, 26);
        BlockPos nearest = house(helper.getLevel(), center);
        BlockPos alternative = house(helper.getLevel(), center.east(16));
        fill(helper, center, 4);
        var first = spawn(helper, center.east(2).south(6));
        var second = spawn(helper, center.east(3).south(6));
        helper.runAfterDelay(10, () -> {
            var level = helper.getLevel();
            if (previousDayTime == null) {
                previousDayTime = level.getOverworldClockTime();
                previousDaylight = level.getGameRules().get(GameRules.ADVANCE_TIME);
                previousTeleport = Config.getInstance().allowVillagerTeleporting;
            }
            setDayTime(level, 13000L);
            level.getGameRules().set(GameRules.ADVANCE_TIME, false, level.getServer());
            Config.getInstance().allowVillagerTeleporting = false;
            claim(helper, nearest);
            claim(helper, alternative);
            choose(helper, first);
            choose(helper, second);
            helper.assertTrue(selected(first).equals(nearest) && selected(second).equals(alternative), "arrivals were not spread before ticking");
            first.setNoAi(false);
            second.setNoAi(false);
            helper.succeedWhen(() -> {
                var cache = VillageManager.get(level).getIndoorRooms();
                helper.assertTrue(cache.resolve(nearest).orElseThrow().floorCells().contains(first.blockPosition()), "first guest has not arrived");
                helper.assertTrue(cache.resolve(alternative).orElseThrow().floorCells().contains(second.blockPosition()), "second guest has not arrived");
                helper.assertTrue(EnterBuildingTask.isUsableFloor(level, first, first.blockPosition())
                        && EnterBuildingTask.isUsableFloor(level, second, second.blockPosition()), "arrival is on a bed or doorway");
                helper.assertTrue(!first.getBrain().hasMemoryValue(MemoryModuleType.HOME)
                        && !second.getBrain().hasMemoryValue(MemoryModuleType.HOME), "shelter claimed HOME during movement");
            });
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void claimedBedReservesUnloadedOwner(GameTestHelper helper) {
        BlockPos center = arena(helper, 6);
        BlockPos nearest = house(helper.getLevel(), center);
        BlockPos alternative = house(helper.getLevel(), center.east(16));
        fill(helper, center, 5);
        var incoming = spawn(helper, center.east(2).south(9));
        helper.runAfterDelay(10, () -> {
            claim(helper, nearest);
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(alternative), "unloaded owner's claim was omitted");
            helper.assertTrue(GameTestPoi.getFreeTickets(helper.getLevel().getPoiManager(), nearest) == 0, "shelter released ticket");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void returningOwnerIsCountedOnce(GameTestHelper helper) {
        BlockPos center = arena(helper, 7);
        BlockPos nearest = house(helper.getLevel(), center);
        BlockPos alternative = house(helper.getLevel(), center.east(16));
        fill(helper, center, 4);
        var owner = spawn(helper, center.east(3).south(3));
        owner.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), nearest));
        var first = spawn(helper, center.east(2).south(9));
        var second = spawn(helper, center.east(3).south(9));
        helper.runAfterDelay(10, () -> {
            claim(helper, nearest);
            choose(helper, first);
            helper.assertTrue(selected(first).equals(nearest), "owner was double-counted with physical occupant");
            choose(helper, second);
            helper.assertTrue(selected(second).equals(alternative), "owner was not reserved at all");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void forcedHomeOwnerIsReservedOnce(GameTestHelper helper) {
        BlockPos center = arena(helper, 8);
        BlockPos nearest = house(helper.getLevel(), center);
        BlockPos alternative = house(helper.getLevel(), center.east(16));
        fill(helper, center, 5);
        var owner = spawn(helper, center.west(20));
        owner.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), nearest));
        owner.getBrain().setMemory(MemoryModuleTypeMCA.FORCED_HOME, true);
        var incoming = spawn(helper, center.east(2).south(9));
        helper.runAfterDelay(10, () -> {
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(alternative), "forced HOME without ticket was omitted");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void clearedOrRedirectedArrivalReleasesPlace(GameTestHelper helper) {
        BlockPos center = arena(helper, 9);
        BlockPos nearest = house(helper.getLevel(), center);
        house(helper.getLevel(), center.east(16));
        fill(helper, center, 5);
        var former = spawn(helper, center.west(20));
        former.moveTowardsPersistent(center.east(3).south(3), 0.5F, 0);
        var incoming = spawn(helper, center.east(2).south(9));
        helper.runAfterDelay(10, () -> {
            former.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            former.getBrain().setMemory(MemoryModuleTypeMCA.SHELTER_BED, GlobalPos.of(helper.getLevel().dimension(), nearest));
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(nearest), "cleared arrival still reserved a place");
            incoming.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            former.moveTowardsPersistent(center.east(18).south(3), 0.5F, 0);
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(nearest), "redirected arrival still reserved its old house");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void fartherHouseWithinSearchRadiusUsesAvailableSpace(GameTestHelper helper) {
        BlockPos center = arena(helper, 10);
        house(helper.getLevel(), center);
        BlockPos alternative = house(helper.getLevel(), center.east(40));
        fill(helper, center, 6);
        var incoming = spawn(helper, center.east(2).south(9));
        helper.runAfterDelay(10, () -> {
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(alternative), "reachable house with space was rejected by the old travel limit");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void allHousesFullStillSelectsShelter(GameTestHelper helper) {
        BlockPos center = arena(helper, 11);
        BlockPos nearest = house(helper.getLevel(), center);
        house(helper.getLevel(), center.east(16));
        fill(helper, center, 6);
        fill(helper, center.east(16), 6);
        var incoming = spawn(helper, center.east(2).south(9));
        helper.runAfterDelay(10, () -> {
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(nearest), "all-full policy refused nearest shelter");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void overflowPrefersLessCrowdedReachableHouse(GameTestHelper helper) {
        BlockPos center = arena(helper, 37);
        house(helper.getLevel(), center);
        BlockPos alternative = house(helper.getLevel(), center.east(16));
        fill(helper, center, 12);
        fill(helper, center.east(16), 6);
        var incoming = spawn(helper, center.east(2).south(9));
        helper.runAfterDelay(10, () -> {
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(alternative), "overflow chose the overcrowded nearest house");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void overflowCrowdingIncludesPendingArrivals(GameTestHelper helper) {
        BlockPos center = arena(helper, 38);
        BlockPos nearest = house(helper.getLevel(), center);
        house(helper.getLevel(), center.east(16));
        fill(helper, center, 8);
        fill(helper, center.east(16), 6);
        for (int i = 0; i < 4; i++) {
            var pending = spawn(helper, center.east(18 + i).south(10));
            pending.moveTowardsPersistent(center.east(19).south(3), 0.5F, 0);
        }
        var incoming = spawn(helper, center.east(2).south(9));
        helper.runAfterDelay(10, () -> {
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(nearest), "overflow omitted incoming guests from crowding");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void lessCrowdedOverflowAllowsLongerTravel(GameTestHelper helper) {
        BlockPos center = arena(helper, 39);
        house(helper.getLevel(), center);
        BlockPos alternative = house(helper.getLevel(), center.east(40));
        fill(helper, center, 12);
        fill(helper, center.east(40), 6);
        var incoming = spawn(helper, center.east(2).south(9));
        helper.runAfterDelay(10, () -> {
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(alternative), "less crowded overflow was rejected by the old travel limit");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void liveThreatBeforePanicUsesOverflow(GameTestHelper helper) {
        verifyThreat(helper, 12, true, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void ignitedCreeperUsesOverflow(GameTestHelper helper) {
        verifyThreat(helper, 13, true, true);
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void staleOrDeadThreatDoesNotForceOverflow(GameTestHelper helper) {
        verifyThreat(helper, 14, false, false);
    }

    private static void verifyThreat(GameTestHelper helper, int id, boolean active, boolean creeper) {
        BlockPos center = arena(helper, id);
        BlockPos nearest = house(helper.getLevel(), center);
        BlockPos alternative = house(helper.getLevel(), center.east(16));
        fill(helper, center, 6);
        var incoming = spawn(helper, center.east(2).south(9));
        LivingEntity threat = creeper ? new Creeper(EntityTypes.CREEPER, helper.getLevel())
                : new Zombie(EntityTypes.ZOMBIE, helper.getLevel());
        threat.setPos(incoming.position().add(2, 0, 0));
        THREATS.add(threat);
        // Memory tests do not tick or spawn the threat, so ignited creepers cannot explode fixtures.
        if (threat instanceof Creeper value) value.ignite();
        if (!active) threat.discard();
        incoming.getBrain().setMemory(MemoryModuleType.NEAREST_HOSTILE, threat);
        helper.runAfterDelay(10, () -> {
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(active ? nearest : alternative), "threat overflow ignored live sensor semantics");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void emergencyMovementIsNotBlockedByCapacity(GameTestHelper helper) {
        BlockPos center = arena(helper, 15);
        house(helper.getLevel(), center);
        var incoming = spawn(helper, center.east(2).south(9));
        helper.runAfterDelay(10, () -> {
            for (Activity activity : List.of(Activity.PANIC, Activity.HIDE)) {
                incoming.getBrain().setActiveActivityIfPossible(activity);
                helper.assertTrue(incoming.getBrain().isActive(activity), "fixture could not activate emergency behavior");
                helper.assertTrue(!new SeekIndoorShelterTask(0.5F).tryStart(helper.getLevel(), incoming,
                        helper.getLevel().getGameTime()), "REST shelter overwrote emergency movement");
            }
            helper.succeed();
        });
    }

    private static void claim(GameTestHelper helper, BlockPos bed) {
        helper.assertTrue(helper.getLevel().getPoiManager().take(type -> type.is(PoiTypes.HOME),
                (type, pos) -> pos.equals(bed), bed, 1).isPresent(), "fixture could not claim bed");
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void wideNativeEndpointCannotStandInDoorway(GameTestHelper helper) {
        BlockPos center = arena(helper, 33);
        house(helper.getLevel(), center);
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, 3, -1), center.offset(5, 6, 5))) {
            int x = pos.getX() - center.getX(), z = pos.getZ() - center.getZ();
            put(helper.getLevel(), pos, pos.getY() == center.getY() + 6 || x == -1 || x == 5 || z == -1 || z == 5
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        BlockPos node = center.east().south(3);
        BlockPos standing = center.east(2).south(4);
        var openDoor = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH)
                .setValue(DoorBlock.OPEN, true).setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT);
        put(helper.getLevel(), standing, openDoor.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        put(helper.getLevel(), standing.above(), openDoor.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        Path fixture = new Path(List.of(new Node(node.getX(), node.getY(), node.getZ())), node, true);
        boolean[] fixtureUsed = {false};
        var villager = new VillagerEntityMCA(Gender.MALE.getVillagerType(), helper.getLevel(), Gender.MALE) {
            @Override
            protected PathNavigation createNavigation(Level level) {
                return new MCAGroundPathNavigation(this, level) {
                    @Override
                    public Path createPathForPersistentIntent(Set<BlockPos> targets, int reachRange) {
                        fixtureUsed[0] = true;
                        return fixture;
                    }
                };
            }
        };
        villager.setPos(Vec3.atBottomCenterOf(center.east(2).south(9)));
        villager.setNoAi(true);
        helper.getLevel().addFreshEntity(villager);
        VILLAGERS.add(villager);
        villager.getAttribute(Attributes.SCALE).setBaseValue(2.0D);
        villager.refreshDimensions();
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().eraseMemory(MemoryModuleType.HOME);
        villager.getBrain().setActiveActivityIfPossible(Activity.REST);
        helper.runAfterDelay(10, () -> {
            Vec3 endpoint = fixture.getEntityPosAtNode(villager, 0);
            helper.assertTrue(BlockPos.containing(endpoint).equals(standing), "fixture did not reproduce wide native offset");
            helper.assertTrue(EnterBuildingTask.isUsableFloor(helper.getLevel(), villager, node), "fixture integer node is unusable");
            helper.assertTrue(helper.getLevel().noCollision(villager, villager.getBoundingBox().move(endpoint.subtract(villager.position()))), "fixture actual endpoint collides");
            new SeekIndoorShelterTask(0.5F).tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime());
            helper.assertTrue(fixtureUsed[0], "shelter search bypassed the fabricated wide endpoint");
            helper.assertTrue(!villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET), "native doorway endpoint was admitted");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void comparesHouseRoutesInsteadOfFirstBatchedHit(GameTestHelper helper) {
        BlockPos center = arena(helper, 35);
        BlockPos nearest = house(helper.getLevel(), center);
        house(helper.getLevel(), center.east(16));
        BlockPos nearTarget = center.east(2).south(2);
        BlockPos farTarget = nearTarget.east(16);
        int[] searches = {0};
        var villager = new VillagerEntityMCA(Gender.MALE.getVillagerType(), helper.getLevel(), Gender.MALE) {
            @Override
            protected PathNavigation createNavigation(Level level) {
                return new MCAGroundPathNavigation(this, level) {
                    @Override
                    public Path createPathForPersistentIntent(Set<BlockPos> targets, int reachRange) {
                        searches[0]++;
                        // Reproduce a native weighted search selecting the longer route when batched.
                        BlockPos endpoint = targets.contains(farTarget) ? farTarget : nearTarget;
                        return new Path(List.of(new Node(endpoint.getX(), endpoint.getY(), endpoint.getZ())), endpoint, true);
                    }
                };
            }
        };
        villager.setPos(Vec3.atBottomCenterOf(center.east(2).south(9)));
        villager.setNoAi(true);
        helper.getLevel().addFreshEntity(villager);
        VILLAGERS.add(villager);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().eraseMemory(MemoryModuleType.HOME);
        villager.getBrain().setActiveActivityIfPossible(Activity.REST);
        helper.runAfterDelay(10, () -> {
            choose(helper, villager);
            helper.assertTrue(selected(villager).equals(nearest), "batched hit replaced the shorter house route");
            helper.assertTrue(searches[0] == 2, "each inspected house route must be measured exactly once");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void coldSealedRoomDoesNotRequireNeighborChunks(GameTestHelper helper) {
        var level = helper.getLevel();
        // Synchronous source-chunk load, with no forced-chunk ticket loading neighbors.
        BlockPos center = new BlockPos(70003, helper.absolutePos(new BlockPos(0, 2, 0)).getY(), 70003);
        ChunkPos source = ChunkPos.containing(center);
        level.getChunk(source.x(), source.z());
        room(level, center);
        BlockPos bed = bed(level, center.south());
        ChunkPos absent = new ChunkPos(source.x() - 1, source.z());
        helper.assertTrue(level.getChunkSource().getChunkNow(absent.x(), absent.z()) == null,
                "fixture neighbor was already fully loaded");
        var room = VillageManager.get(level).getIndoorRooms().resolve(bed);
        helper.assertTrue(room.isPresent() && room.orElseThrow().floorCells().contains(bed),
                "sealed source-chunk room was rejected by unrelated unloaded surroundings");
        helper.assertTrue(level.getChunkSource().getChunkNow(absent.x(), absent.z()) == null,
                "room discovery loaded an unrelated neighboring chunk");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void coldHouseBesideUnevenPorchDoesNotRequireNeighborChunks(GameTestHelper helper) {
        assertColdHouseBesidePorch(helper, 76000, 4, 7);
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void coldHouseBesideNarrowSteppedPorchDoesNotRequireNeighborChunks(GameTestHelper helper) {
        assertColdHouseBesidePorch(helper, 78000, 5, 6);
    }

    private static void assertColdHouseBesidePorch(GameTestHelper helper, int coordinate, int minPorchZ, int maxPorchZ) {
        var level = helper.getLevel();
        BlockPos origin = new BlockPos(coordinate, helper.absolutePos(new BlockPos(0, 2, 0)).getY(), coordinate);
        ChunkPos source = ChunkPos.containing(origin);
        ChunkPos absent = new ChunkPos(source.x() + 1, source.z());
        level.getChunk(source.x(), source.z());
        helper.assertTrue(level.getChunkSource().getChunkNow(absent.x(), absent.z()) == null,
                "fixture neighbor was already fully loaded");
        Map<BlockPos, BlockState> states = new LinkedHashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(origin.below(), origin.offset(15, 7, 15))) {
            states.put(pos.immutable(), pos.getY() == origin.getY() - 1
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        for (BlockPos pos : BlockPos.betweenClosed(origin.south(4), origin.offset(5, 3, 9))) {
            int x = pos.getX() - origin.getX(), z = pos.getZ() - origin.getZ();
            if (pos.getY() == origin.getY() + 3 || x == 0 || x == 5 || z == 4 || z == 9) {
                states.put(pos.immutable(), Blocks.STONE.defaultBlockState());
            }
        }
        var door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        states.put(origin.offset(5, 0, 6), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        states.put(origin.offset(5, 1, 6), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        for (int x = 6; x < 16; x++) {
            for (int z = minPorchZ; z <= maxPorchZ; z++) states.put(origin.offset(x, 3, z), Blocks.STONE.defaultBlockState());
            states.put(origin.offset(x, -1, minPorchZ), Blocks.AIR.defaultBlockState());
            states.put(origin.offset(x, -2, minPorchZ), Blocks.STONE.defaultBlockState());
        }
        for (var entry : states.entrySet()) {
            BLOCKS.putIfAbsent(entry.getKey(), level.getBlockState(entry.getKey()));
            // The fixture supplies final shapes; skip shape updates that read the absent chunk.
            level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
        BlockPos bed = bed(level, origin.offset(2, 0, 6));
        helper.assertTrue(level.getChunkSource().getChunkNow(absent.x(), absent.z()) == null,
                "fixture construction loaded its neighbor");
        var house = VillageManager.get(level).getIndoorRooms().resolveHouse(bed);
        helper.assertTrue(house.isPresent(), "small house rejected because its exterior porch needed an unloaded chunk");
        helper.assertTrue(house.orElseThrow().membershipKnown() && house.orElseThrow().bedHeads().equals(Set.of(bed)),
                "small house lost complete bed membership");
        helper.assertTrue(!house.orElseThrow().floorCells().contains(origin.offset(6, 0, 6)),
                "exterior porch became indoor shelter");
        helper.assertTrue(level.getChunkSource().getChunkNow(absent.x(), absent.z()) == null,
                "shelter discovery loaded the neighboring chunk");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void villagerIndexFollowsRemovalAndTrackingTransitions(GameTestHelper helper) {
        BlockPos center = arena(helper, 36);
        var villager = spawn(helper, center);
        var manager = VillageManager.get(helper.getLevel());
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(manager.getLoadedVillagers().contains(villager), "spawned MCA villager was not indexed");
            var vanilla = new Villager(EntityTypes.VILLAGER, helper.getLevel());
            vanilla.setPos(Vec3.atBottomCenterOf(center.east(2)));
            vanilla.setNoAi(true);
            boolean overwrite = Config.getInstance().overwriteOriginalVillagers;
            try {
                // Keep this fixture vanilla rather than enqueueing MCA's normal replacement.
                Config.getInstance().overwriteOriginalVillagers = false;
                helper.assertTrue(helper.getLevel().addFreshEntity(vanilla), "vanilla fixture spawn was rejected");
            } finally {
                Config.getInstance().overwriteOriginalVillagers = overwrite;
            }
            VILLAGERS.add(vanilla);
            helper.assertTrue(manager.getLoadedVillagers().contains(vanilla), "vanilla villager was not indexed");
            manager.untrackVillager(villager);
            helper.assertTrue(manager.getLoadedVillagers().contains(villager), "tracking transition dropped a live entity");
            villager.discard();
            vanilla.discard();
            helper.assertTrue(!manager.getLoadedVillagers().contains(villager)
                    && !manager.getLoadedVillagers().contains(vanilla), "removed villagers remained admission occupants");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void coldDiscoveryRejectsAnUnloadedBoundaryWithoutLoadingIt(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos center = new BlockPos(73003, helper.absolutePos(new BlockPos(0, 2, 0)).getY(), 73003);
        ChunkPos source = ChunkPos.containing(center);
        level.getChunk(source.x(), source.z());
        room(level, center);
        BlockPos bed = bed(level, center.south());
        // A covered corridor reaches the west edge of the loaded chunk.
        for (int x = -3; x <= -1; x++) {
            for (int y = -1; y <= 3; y++) {
                for (int z = 1; z <= 3; z++) {
                    put(level, center.offset(x, y, z), y == -1 || y == 3 || z == 1 || z == 3
                            ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
                }
            }
        }
        ChunkPos absent = new ChunkPos(source.x() - 1, source.z());
        helper.assertTrue(level.getChunkSource().getChunkNow(absent.x(), absent.z()) == null,
                "fixture boundary chunk was already loaded");
        helper.assertTrue(VillageManager.get(level).getIndoorRooms().resolve(bed).isEmpty(),
                "discovery accepted incomplete boundary evidence");
        helper.assertTrue(level.getChunkSource().getChunkNow(absent.x(), absent.z()) == null,
                "discovery loaded its missing boundary");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 400)
    public static void cachedConnectorDoesNotReloadUnloadedChunk(GameTestHelper helper) {
        BlockPos arena = arena(helper, 34);
        BlockPos center = new BlockPos((arena.getX() & ~15) + 12, arena.getY(), (arena.getZ() & ~15) + 3);
        BlockPos bed = house(helper.getLevel(), center);
        room(helper.getLevel(), center.above(4));
        bed(helper.getLevel(), center.above(4).south());
        BlockPos connector = center.east(4).south(3);
        for (int y = 0; y <= 4; y++) put(helper.getLevel(), connector.above(y), Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST));
        ChunkPos absent = ChunkPos.containing(connector);
        ChunkPos source = ChunkPos.containing(bed);
        helper.assertTrue(!source.equals(absent), "fixture connector did not cross chunk boundary");
        var level = helper.getLevel();
        var cache = VillageManager.get(level).getIndoorRooms();
        boolean[] armed = {false};
        helper.onEachTick(() -> {
            if (!armed[0]) return;
            level.getChunk(source.x(), source.z());
            if (level.getChunkSource().getChunkNow(absent.x(), absent.z()) != null) {
                cache.resolve(bed);
            } else {
                var house = cache.resolveHouse(bed);
                helper.assertTrue(level.getChunkSource().getChunkNow(absent.x(), absent.z()) == null, "cached connector forced chunk load");
                helper.assertTrue(house.isEmpty() || !house.orElseThrow().membershipKnown(), "unavailable connector was treated as complete");
                armed[0] = false;
                reloadArenaChunks(level, arena);
                helper.startSequence().thenIdle(20).thenSucceed();
            }
        });
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(cache.resolveHouse(bed).orElseThrow().membershipKnown(), "fixture geometry incomplete");
            ChunkPos min = ChunkPos.containing(arena.offset(-32, 0, -32));
            ChunkPos max = ChunkPos.containing(arena.offset(64, 0, 40));
            for (int x = min.x(); x <= max.x(); x++) for (int z = min.z(); z <= max.z(); z++) level.setChunkForced(x, z, false);
            armed[0] = true;
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void wholeHouseBedsExtendBeyondAnchorSearchRadius(GameTestHelper helper) {
        BlockPos center = arena(helper, 31);
        BlockPos first = house(helper.getLevel(), center);
        BlockPos outsideQuery = bed(helper.getLevel(), center.east(3).south());
        helper.runAfterDelay(10, () -> {
            var queried = helper.getLevel().getPoiManager().findAllClosestFirstWithType(type -> type.is(PoiTypes.HOME),
                    pos -> true, center.west(46).south(), 48, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY)
                    .map(pair -> pair.getSecond()).toList();
            helper.assertTrue(queried.contains(first) && !queried.contains(outsideQuery), "fixture beds did not straddle query radius");
            var house = VillageManager.get(helper.getLevel()).getIndoorRooms().resolveHouses(queried, 5).getFirst();
            helper.assertTrue(house.membershipKnown() && house.bedHeads().equals(Set.of(first, outsideQuery)), "capacity omitted bed beyond anchor radius");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void staleRegisteredRoomDoesNotImposePartialCapacity(GameTestHelper helper) {
        BlockPos center = arena(helper, 32);
        BlockPos first = house(helper.getLevel(), center);
        addRoom(helper.getLevel(), center);
        house(helper.getLevel(), center.east(16));
        fill(helper, center, 6);
        var incoming = spawn(helper, center.east(2).south(9));
        helper.runAfterDelay(10, () -> {
            var manager = VillageManager.get(helper.getLevel());
            helper.assertTrue(manager.processBuilding(center) == Building.validationResult.SUCCESS, "fixture registration failed");
            var village = manager.findNearestVillage(center, 0).orElseThrow();
            VILLAGES.add(village.getId());
            village.setAutoScan(false);
            helper.assertTrue(manager.getIndoorRooms().resolveHouse(first).orElseThrow().membershipKnown(), "fresh registered house was incomplete");
            for (int x = 6; x <= 10; x++) for (int z = 0; z <= 4; z++) {
                put(helper.getLevel(), center.offset(x, 3, z), Blocks.AIR.defaultBlockState());
            }
            helper.startSequence().thenIdle(41).thenExecute(() -> {
                var before = village.save();
                var house = manager.getIndoorRooms().resolveHouse(first).orElseThrow();
                helper.assertTrue(!house.membershipKnown(), "stale registered house imposed a guessed partial cap");
                choose(helper, incoming);
                helper.assertTrue(selected(incoming).equals(first), "unknown capacity refused the known reachable room");
                helper.assertTrue(before.equals(village.save()), "discovery rewrote persisted geometry");
            }).thenSucceed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void longDetourUsesOverflow(GameTestHelper helper) {
        BlockPos center = arena(helper, 30);
        BlockPos nearest = house(helper.getLevel(), center);
        BlockPos alternative = house(helper.getLevel(), center.east(16));
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-6, -1, 15), center.offset(24, 2, 45))) {
            put(helper.getLevel(), pos, pos.getY() == center.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        for (int z = -30; z <= 40; z++) for (int y = 0; y < 3; y++) {
            put(helper.getLevel(), center.offset(8, y, z), Blocks.STONE.defaultBlockState());
        }
        fill(helper, center, 6);
        var incoming = spawn(helper, center.east(2).south(9));
        incoming.getNavigation().setMaxVisitedNodesMultiplier(8.0F);
        helper.runAfterDelay(10, () -> {
            Path near = pathToRoom(helper, incoming, nearest);
            Path far = pathToRoom(helper, incoming, alternative);
            double difference = SeekIndoorShelterTask.routeLength(incoming, far) - SeekIndoorShelterTask.routeLength(incoming, near);
            helper.assertTrue(difference > 64.0D, "fixture did not produce a long reachable detour: " + difference);
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(nearest), "straight-line proximity ignored the long detour");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void connectedRoomsShareWholeHouseBeds(GameTestHelper helper) {
        BlockPos center = arena(helper, 0);
        BlockPos first = house(helper.getLevel(), center);
        BlockPos second = addRoom(helper.getLevel(), center);
        helper.runAfterDelay(10, () -> {
            var cache = VillageManager.get(helper.getLevel()).getIndoorRooms();
            var result = cache.resolveHouse(first).orElseThrow();
            helper.assertTrue(result.membershipKnown(), "complete two-room house reported unknown");
            helper.assertTrue(result.bedHeads().equals(Set.of(first, second)), "house omitted the other room's bed");
            helper.assertTrue(result.floorCells().contains(center.east(8).south(3)), "house omitted the other room's floor");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void adjacentUnregisteredHousesRemainDistinct(GameTestHelper helper) {
        BlockPos center = arena(helper, 1);
        BlockPos first = house(helper.getLevel(), center);
        BlockPos second = house(helper.getLevel(), center.east(12));
        helper.runAfterDelay(10, () -> {
            var cache = VillageManager.get(helper.getLevel()).getIndoorRooms();
            var result = cache.resolveHouse(first).orElseThrow();
            helper.assertTrue(result.membershipKnown(), "ordinary house reported unknown");
            helper.assertTrue(result.bedHeads().equals(Set.of(first)), "adjacent house was merged");
            helper.assertTrue(!result.floorCells().contains(second), "adjacent house floor was merged");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void fullNearestHouseUsesNearbyAlternative(GameTestHelper helper) {
        BlockPos center = arena(helper, 2);
        house(helper.getLevel(), center);
        BlockPos alternative = house(helper.getLevel(), center.east(16));
        fill(helper, center, 6);
        var incoming = spawn(helper, center.east(2).south(9));
        helper.runAfterDelay(10, () -> {
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(alternative), "full nearest house attracted another guest");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void lastPlaceIncludesPendingArrival(GameTestHelper helper) {
        BlockPos center = arena(helper, 3);
        BlockPos nearest = house(helper.getLevel(), center);
        BlockPos alternative = house(helper.getLevel(), center.east(16));
        fill(helper, center, 5);
        var first = spawn(helper, center.east(2).south(9));
        var second = spawn(helper, center.east(3).south(9));
        helper.runAfterDelay(10, () -> {
            choose(helper, first);
            helper.assertTrue(selected(first).equals(nearest), "last available place was not used");
            choose(helper, second);
            helper.assertTrue(selected(second).equals(alternative), "second arrival did not observe first intent");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void distantIncomingTargetCounts(GameTestHelper helper) {
        BlockPos center = arena(helper, 4);
        house(helper.getLevel(), center);
        BlockPos alternative = house(helper.getLevel(), center.east(16));
        fill(helper, center, 5);
        var distant = spawn(helper, center.west(70));
        distant.moveTowardsPersistent(center.east(3).south(3), 0.5F, 0);
        var incoming = spawn(helper, center.east(2).south(9));
        helper.runAfterDelay(10, () -> {
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(alternative), "distant incoming destination was omitted");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void rememberedBedWithoutArrivalDoesNotReserve(GameTestHelper helper) {
        BlockPos center = arena(helper, 5);
        BlockPos nearest = house(helper.getLevel(), center);
        house(helper.getLevel(), center.east(16));
        fill(helper, center, 5);
        var former = spawn(helper, center.west(20));
        former.getBrain().setMemory(MemoryModuleTypeMCA.SHELTER_BED, GlobalPos.of(helper.getLevel().dimension(), nearest));
        var incoming = spawn(helper, center.east(2).south(9));
        helper.runAfterDelay(10, () -> {
            choose(helper, incoming);
            helper.assertTrue(selected(incoming).equals(nearest), "bed memory reserved without active walking intent");
            helper.succeed();
        });
    }

    private static BlockPos selected(VillagerEntityMCA villager) {
        return villager.getBrain().getMemory(MemoryModuleTypeMCA.SHELTER_BED).orElseThrow().pos();
    }

    private static void choose(GameTestHelper helper, VillagerEntityMCA villager) {
        helper.assertTrue(new SeekIndoorShelterTask(0.5F).tryStart(helper.getLevel(), villager,
                helper.getLevel().getGameTime()), "shelter behavior did not start");
        helper.assertTrue(villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET), "no arrival intent published");
        helper.assertTrue(!villager.getBrain().hasMemoryValue(MemoryModuleType.HOME), "shelter claimed HOME");
    }

    private static void fill(GameTestHelper helper, BlockPos center, int count) {
        for (int i = 0; i < count; i++) spawn(helper, center.offset(1 + i % 3, 0, 2 + (i / 3) % 2));
    }

    private static VillagerEntityMCA spawn(GameTestHelper helper, BlockPos pos) {
        var villager = VillagerFactory.newVillager(helper.getLevel()).withAge(0)
                .withPosition(Vec3.atBottomCenterOf(pos)).spawn(EntitySpawnReason.STRUCTURE);
        VILLAGERS.add(villager);
        villager.setNoAi(true);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().eraseMemory(MemoryModuleType.HOME);
        villager.getBrain().setActiveActivityIfPossible(Activity.REST);
        villager.setOnGround(true);
        return villager;
    }

    private static BlockPos arena(GameTestHelper helper, int id) {
        BlockPos center = helper.absolutePos(new BlockPos(160 + id * 160, 2, 96));
        ServerLevel level = helper.getLevel();
        ChunkPos min = ChunkPos.containing(center.offset(-32, 0, -32));
        ChunkPos max = ChunkPos.containing(center.offset(64, 0, 40));
        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!level.getForceLoadedChunks().contains(chunk.pack())) CHUNKS.add(chunk);
                level.setChunkForced(x, z, true);
                level.getChunk(x, z);
            }
        }
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-6, -1, -6), center.offset(42, 8, 15))) {
            put(level, pos, pos.getY() == center.getY() - 1 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
        return center;
    }

    /** Let entity loads finish while the server still ticks, before batch restoration and shutdown. */
    private static void reloadArenaChunks(ServerLevel level, BlockPos center) {
        ChunkPos min = ChunkPos.containing(center.offset(-32, 0, -32));
        ChunkPos max = ChunkPos.containing(center.offset(64, 0, 40));
        for (int x = min.x(); x <= max.x(); x++) for (int z = min.z(); z <= max.z(); z++) {
            level.setChunkForced(x, z, true);
            level.getChunk(x, z);
        }
    }

    private static BlockPos house(ServerLevel level, BlockPos center) {
        room(level, center);
        door(level, center.east(2).south(5), Direction.NORTH);
        return bed(level, center.south());
    }

    private static BlockPos addRoom(ServerLevel level, BlockPos center) {
        room(level, center.east(6));
        door(level, center.east(5).south(2), Direction.WEST);
        return bed(level, center.east(6).south());
    }

    private static void room(ServerLevel level, BlockPos center) {
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(5, 3, 5))) {
            int x = pos.getX() - center.getX(), z = pos.getZ() - center.getZ(), y = pos.getY() - center.getY();
            put(level, pos, y == -1 || y == 3 || x == -1 || x == 5 || z == -1 || z == 5
                    ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState());
        }
    }

    private static BlockPos bed(ServerLevel level, BlockPos foot) {
        var state = Blocks.BED.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        put(level, foot, state.setValue(BedBlock.PART, BedPart.FOOT));
        put(level, foot.east(), state.setValue(BedBlock.PART, BedPart.HEAD));
        return foot.east();
    }

    private static void door(ServerLevel level, BlockPos pos, Direction direction) {
        var state = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, direction);
        put(level, pos, state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        put(level, pos.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    private static void put(ServerLevel level, BlockPos pos, BlockState state) {
        BLOCKS.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, 3);
    }

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        cleanupState(level);
    }

    @AfterBatch(batch = GLOBAL_STATE_BATCH)
    public static void cleanupGlobalStateBatch(ServerLevel level) {
        cleanupState(level);
    }

    private static void cleanupState(ServerLevel level) {
        VILLAGERS.forEach(villager -> { if (villager.isSleeping()) villager.stopSleeping(); villager.releasePoi(MemoryModuleType.HOME); villager.discard(); });
        VILLAGERS.clear();
        THREATS.forEach(LivingEntity::discard);
        THREATS.clear();
        BLOCKS.forEach((pos, state) -> level.setBlock(pos, state, 3));
        BLOCKS.clear();
        CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x(), chunk.z(), false));
        CHUNKS.clear();
        VILLAGES.forEach(id -> VillageManager.get(level).removeVillage(id));
        VILLAGES.clear();
        if (previousDayTime != null) {
            setDayTime(level, previousDayTime);
            level.getGameRules().set(GameRules.ADVANCE_TIME, previousDaylight, level.getServer());
            Config.getInstance().allowVillagerTeleporting = previousTeleport;
            previousDayTime = null;
            previousDaylight = null;
            previousTeleport = null;
        }
    }

    private static void setDayTime(ServerLevel level, long ticks) {
        var clock = level.dimensionType().defaultClock().orElseThrow();
        level.clockManager().setTotalTicks(clock, ticks);
    }
}
