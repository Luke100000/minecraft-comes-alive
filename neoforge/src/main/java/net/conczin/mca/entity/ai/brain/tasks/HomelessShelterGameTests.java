package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.BedPoiCompatibility;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.navigation.MultiTargetPositionTracker;
import net.conczin.mca.entity.ai.SchedulesMCA;
import net.conczin.mca.entity.ai.brain.VillagerTasksMCA;
import net.conczin.mca.entity.ai.navigation.BedApproachTarget;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.IndoorRoomCache;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

@PrefixGameTestTemplate(false)
public final class HomelessShelterGameTests {
    private static final List<VillagerEntityMCA> STROLL_VILLAGERS = new ArrayList<>();
    private static final Map<BlockPos, BlockState> STROLL_BLOCKS = new LinkedHashMap<>();
    private static final Set<ChunkPos> STROLL_CHUNKS = new HashSet<>();
    private static final Set<Integer> STROLL_VILLAGES = new HashSet<>();
    private static Boolean previousTeleport;
    private static Long previousDayTime;
    private static Boolean previousDaylightCycle;

    private HomelessShelterGameTests() {
    }

    @GameTest(batch = "mca_unregistered_shelter_arrival", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void unregisteredRoomFloorCompletesShelterAwayFromBed(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(1104, 2, 80));
        BlockPos head = prepareBedsideHouse(helper, center);
        VillagerEntityMCA villager = spawn(helper, center.north(2));
        STROLL_VILLAGERS.add(villager);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(villager.getResidency().getHomeVillage().isEmpty(), "fixture house was registered");
            helper.assertTrue(!BedApproachTarget.create(helper.getLevel(), head).orElseThrow()
                    .getPathTargets(villager).contains(villager.blockPosition()), "fixture origin is beside bed");
            helper.assertTrue(!new SeekIndoorShelterTask(0.5F).tryStart(helper.getLevel(), villager,
                    helper.getLevel().getGameTime()), "unregistered room arrival still required standing beside the bed");
            helper.assertTrue(!villager.getBrain().hasMemoryValue(MemoryModuleType.HOME), "room arrival claimed HOME");
            helper.succeed();
        });
    }

    // Separate extended arenas avoid reusing chunks whose tickets the previous batch just released.
    @GameTest(batch = "mca_registered_shelter_arrival", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void registeredRoomFloorCompletesShelterAwayFromBed(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(80, 2, 80));
        BlockPos head = prepareBedsideHouse(helper, center);
        helper.runAfterDelay(10, () -> {
            Village village = registerStrollHouse(helper, center);
            VillagerEntityMCA villager = spawn(helper, center.north(2));
            STROLL_VILLAGERS.add(villager);
            villager.getResidency().reconcileVillageMembership();
            Building room = village.findPhysicalRoomAt(head).orElseThrow();
            helper.assertTrue(room.containsFloorPosition(villager.blockPosition()), "fixture origin is outside registered room");
            helper.assertTrue(!BedApproachTarget.create(helper.getLevel(), head).orElseThrow()
                    .getPathTargets(villager).contains(villager.blockPosition()), "fixture origin is beside bed");
            helper.assertTrue(!new SeekIndoorShelterTask(0.5F).tryStart(helper.getLevel(), villager,
                    helper.getLevel().getGameTime()), "sheltered villager was pulled back toward bed");
            helper.assertTrue(!villager.getBrain().hasMemoryValue(MemoryModuleType.HOME), "room arrival claimed HOME");
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_registered_stroll_selection", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void registeredRoomWanderingUsesRoomFloorAndLongerSteps(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(208, 2, 80));
        prepareBedsideHouse(helper, center);
        helper.runAfterDelay(10, () -> {
            Village village = registerStrollHouse(helper, center);
            VillagerEntityMCA villager = spawn(helper, center.east().south(3));
            STROLL_VILLAGERS.add(villager);
            villager.getResidency().reconcileVillageMembership();
            Building room = village.findPhysicalRoomAt(villager.blockPosition()).orElseThrow();
            helper.assertTrue(!room.containsFloorPosition(center.east().south(5))
                    && !helper.getLevel().canSeeSky(center.east().south(5)), "fixture lacks covered exterior floor");
            // A floor-to-ceiling obstruction must still be checked live.
            // A single solid block instead creates a valid raised floor surface.
            BlockPos obstructed = center.south();
            helper.assertTrue(room.containsFloorPosition(obstructed), "fixture obstruction is outside stored floor");
            helper.getLevel().setBlock(obstructed, Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(obstructed.above(), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(obstructed.above(2), Blocks.STONE.defaultBlockState(), 3);
            helper.assertTrue(!EnterBuildingTask.hasStandingSpace(helper.getLevel(), villager, obstructed),
                    "fixture obstruction did not block standing clearance");
            var wander = LocalInsideBrownianWalk.create(0.5F);
            long now = helper.getLevel().getGameTime();
            boolean longerStep = false;
            for (int attempt = 0; attempt < 128; attempt++) {
                long time = now + attempt * 100L;
                wander.tryStart(helper.getLevel(), villager, time);
                var walk = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
                if (walk.isPresent()) {
                    BlockPos target = walk.orElseThrow().getTarget().currentBlockPosition();
                    assertRegisteredDestination(helper, villager, room, target);
                    longerStep |= target.distSqr(villager.blockPosition()) >= 4.0D;
                }
                villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                wander.doStop(helper.getLevel(), villager, time);
            }
            helper.assertTrue(longerStep, "registered room wandering still only selected one-block steps");
            helper.succeed();
        });
    }

    private static Village registerStrollHouse(GameTestHelper helper, BlockPos center) {
        VillageManager manager = VillageManager.get(helper.getLevel());
        helper.assertTrue(manager.findNearestVillage(center, Village.BORDER_MARGIN).isEmpty(),
                "fixture would modify an existing village");
        var result = manager.processBuilding(center);
        helper.assertTrue(result == Building.validationResult.SUCCESS, "fixture room registration failed: " + result);
        Village village = manager.findNearestVillage(center, Village.BORDER_MARGIN).orElseThrow();
        STROLL_VILLAGES.add(village.getId());
        village.setAutoScan(false);
        return village;
    }

    private static void assertRegisteredDestination(GameTestHelper helper, VillagerEntityMCA villager,
                                                     Building room, BlockPos target) {
        helper.assertTrue(room.containsFloorPosition(target), "stroll escaped registered room: " + target);
        helper.assertTrue(!BedPoiCompatibility.isCompatibleBedState(helper.getLevel().getBlockState(target))
                        && !BedPoiCompatibility.isCompatibleBedState(helper.getLevel().getBlockState(target.below())),
                "stroll selected a bed surface: " + target);
        helper.assertTrue(EnterBuildingTask.hasStandingSpace(helper.getLevel(), villager, target),
                "stroll used stale room support or clearance: " + target);
    }

    private static void prepareRestNight(ServerLevel level) {
        previousTeleport = Config.getInstance().allowVillagerTeleporting;
        Config.getInstance().allowVillagerTeleporting = false;
        previousDayTime = level.getOverworldClockTime();
        previousDaylightCycle = level.getGameRules().get(GameRules.ADVANCE_TIME);
        level.getGameRules().set(GameRules.ADVANCE_TIME, false, level.getServer());
        setDayTime(level, 16000L);
    }

    private static void startHomelessRest(VillagerEntityMCA villager) {
        var brain = villager.getMCABrain();
        brain.removeAllBehaviors();
        brain.addActivity(Activity.CORE, ImmutableList.of(
                Pair.of(0, new SmarterOpenDoorsTask()), Pair.of(1, new WanderOrTeleportToTargetTask())),
                Set.of(), Set.of());
        brain.addActivity(Activity.REST, VillagerTasksMCA.getRestPackage(0.5F), Set.of(), Set.of());
        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.REST);
        brain.setSchedule(SchedulesMCA.DEFAULT);
        brain.setActiveActivityIfPossible(Activity.REST);
        villager.setNoAi(false);
    }

    @GameTest(batch = "mca_registered_rest_movement", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 600)
    public static void homelessRestWandersWithinRegisteredRoomIncludingFromBed(GameTestHelper helper) {
        verifyRestRoomMovement(helper, true);
    }

    @GameTest(batch = "mca_unregistered_rest_movement", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 600)
    public static void homelessRestWandersWithinUnregisteredStairRoofRoom(GameTestHelper helper) {
        verifyRestRoomMovement(helper, false);
    }

    private static void verifyRestRoomMovement(GameTestHelper helper, boolean registered) {
        BlockPos center = helper.absolutePos(new BlockPos(registered ? 976 : 1232, 2, 80));
        BlockPos head = prepareBedsideHouse(helper, center);
        ServerLevel level = helper.getLevel();
        prepareRestNight(level);
        List<VillagerEntityMCA> walkers = new ArrayList<>();
        Map<VillagerEntityMCA, Set<BlockPos>> visited = new LinkedHashMap<>();
        Set<VillagerEntityMCA> longerWalks = new HashSet<>();
        VillagerEntityMCA[] owner = {null};
        Village[] village = {null};
        IndoorRoomCache.Room[] room = {null};
        long[] started = {0L};
        helper.runAfterDelay(10, () -> {
            if (registered) {
                village[0] = registerStrollHouse(helper, center);
            } else {
                for (int x = -4; x <= 4; x++) {
                    for (int z = -4; z <= 4; z++) {
                        level.setBlock(center.offset(x, 3, z), Blocks.OAK_STAIRS.defaultBlockState(), 3);
                    }
                }
                for (int x = 0; x <= 2; x++) {
                    level.setBlock(center.offset(x, 3, 5), Blocks.OAK_STAIRS.defaultBlockState(), 3);
                }
            }
            room[0] = VillageManager.get(level).getIndoorRooms().resolve(head).orElseThrow();
            helper.assertTrue(!room[0].floorCells().contains(center.east().south(5)), "room included roofed doorstep");
            level.getPoiManager().take(poi -> poi.is(PoiTypes.HOME), (poi, pos) -> pos.equals(head), head, 1).orElseThrow();
            owner[0] = spawn(helper, head);
            STROLL_VILLAGERS.add(owner[0]);
            owner[0].getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), head));
            if (registered) owner[0].getResidency().onHomeClaimed();
            owner[0].startSleeping(head);
            for (BlockPos position : List.of(head.west(), center.north(2), center.west(2))) {
                VillagerEntityMCA villager = spawn(helper, position);
                STROLL_VILLAGERS.add(villager);
                villager.getResidency().reconcileVillageMembership();
                if (position.equals(head.west())) {
                    villager.setPos(Vec3.atBottomCenterOf(position).add(0.0D, 0.5625D, 0.0D));
                }
                startHomelessRest(villager);
                walkers.add(villager);
                visited.put(villager, new HashSet<>());
            }
            started[0] = level.getGameTime();
        });
        helper.onEachTick(() -> {
            if (walkers.isEmpty()) return;
            long elapsed = level.getGameTime() - started[0];
            for (VillagerEntityMCA villager : walkers) {
                helper.assertTrue(!villager.getBrain().hasMemoryValue(MemoryModuleType.HOME) && !villager.isSleeping(),
                        "homeless REST took the resident's bed");
                villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).ifPresent(walk -> {
                    BlockPos target = walk.getTarget().currentBlockPosition();
                    helper.assertTrue(room[0].floorCells().contains(target), "REST destination left resolved room: " + target);
                    assertStandingDestination(helper, villager, target);
                    if (target.distSqr(villager.blockPosition()) >= 4.0D) longerWalks.add(villager);
                });
                if (villager.getY() <= center.getY() + 0.1D) {
                    helper.assertTrue(room[0].floorCells().contains(villager.blockPosition()),
                            "villager left its resolved room: " + villager.position());
                    visited.get(villager).add(villager.blockPosition());
                }
                if (elapsed >= 400) {
                    helper.assertTrue(visited.get(villager).size() >= 3 && longerWalks.contains(villager),
                            "villager did not wander across room floor: " + visited.get(villager));
                    helper.assertTrue(!BedPoiCompatibility.isCompatibleBedState(level.getBlockState(villager.blockPosition())),
                            "villager remained on occupied bed");
                }
            }
            if (elapsed >= 400) {
                helper.assertTrue(owner[0].isSleeping() && (!registered || village[0].isResidentHomeCurrent(owner[0]))
                                && owner[0].getBrain().getMemory(MemoryModuleType.HOME)
                                .filter(home -> home.pos().equals(head)).isPresent()
                                && level.getPoiManager().getCountInRange(poi -> poi.is(PoiTypes.HOME), head, 0,
                                net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE) == 0,
                        "room wandering disturbed the resident's HOME, sleep or POI ticket");
                helper.succeed();
            }
        });
    }

    @GameTest(batch = "mca_shelter_bedside", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void roofedDoorstepStillSelectsFloorBesideBed(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(336, 2, 80));
        BlockPos head = prepareBedsideHouse(helper, center);
        BlockPos doorstep = center.east().south(5);
        VillagerEntityMCA villager = spawn(helper, doorstep);
        STROLL_VILLAGERS.add(villager);
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!helper.getLevel().canSeeSky(doorstep), "fixture overhang did not cover doorstep");
            helper.assertTrue(villager.getResidency().getHomeVillage().isEmpty(), "fixture house was registered");
            var bedside = BedApproachTarget.create(helper.getLevel(), head).orElseThrow().getPathTargets(villager);
            var path = villager.getNavigation().createPath(bedside, 0);
            helper.assertTrue(path != null && path.canReach(), "fixture bedside floor was unreachable");
            SeekIndoorShelterTask shelter = new SeekIndoorShelterTask(0.5F);
            BlockPos selected = shelter.getNextPosition(villager).orElseThrow();
            var room = VillageManager.get(helper.getLevel()).getIndoorRooms().resolve(head).orElseThrow();
            helper.assertTrue(room.floorCells().contains(selected) && !selected.equals(doorstep),
                    "shelter selected roofed ground outside instead of room floor: " + selected);
            helper.assertTrue(shelter.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime()),
                    "roofed doorstep incorrectly counted as completed shelter arrival");
            var walk = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
            helper.assertTrue(room.floorCells().contains(walk.getTarget().currentBlockPosition()), "shelter did not publish a room target");
            helper.assertTrue(!villager.getBrain().hasMemoryValue(MemoryModuleType.HOME), "shelter claimed HOME");
            shelter.doStop(helper.getLevel(), villager, helper.getLevel().getGameTime());
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.setPos(Vec3.atBottomCenterOf(selected));
            helper.assertTrue(!new SeekIndoorShelterTask(0.5F).tryStart(helper.getLevel(), villager,
                    helper.getLevel().getGameTime()), "already at bedside floor but shelter restarted");
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_shelter_bedside_movement", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 500)
    public static void homelessRestEntersFromRoofedDoorstepWithoutClaimingBed(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(464, 2, 80));
        BlockPos head = prepareBedsideHouse(helper, center);
        BlockPos door = center.east().south(4);
        ServerLevel level = helper.getLevel();
        prepareRestNight(level);
        VillagerEntityMCA[] walker = {null};
        VillagerEntityMCA[] owner = {null};
        boolean[] bedsideIntent = {false};
        boolean[] openedDoor = {false};
        helper.runAfterDelay(10, () -> {
            level.getPoiManager().take(poi -> poi.is(PoiTypes.HOME), (poi, pos) -> pos.equals(head), head, 1).orElseThrow();
            owner[0] = spawn(helper, center.north(2));
            STROLL_VILLAGERS.add(owner[0]);
            owner[0].getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), head));
            // The claimed bed is visually empty, as in the reported house.
            walker[0] = spawn(helper, door.south());
            STROLL_VILLAGERS.add(walker[0]);
            startHomelessRest(walker[0]);
        });
        helper.onEachTick(() -> {
            if (walker[0] == null) return;
            VillagerEntityMCA villager = walker[0];
            Set<BlockPos> bedside = VillageManager.get(level).getIndoorRooms().resolve(head).orElseThrow().floorCells();
            villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).ifPresent(walk -> {
                if (bedside.contains(walk.getTarget().currentBlockPosition())) bedsideIntent[0] = true;
            });
            openedDoor[0] |= level.getBlockState(door).getValue(DoorBlock.OPEN);
            helper.assertTrue(!villager.getBrain().hasMemoryValue(MemoryModuleType.HOME) && !villager.isSleeping(),
                    "homeless REST claimed the resident's bed");
            if (bedsideIntent[0] && bedside.contains(villager.blockPosition()) && villager.getY() <= center.getY() + 0.1D) {
                helper.assertTrue(openedDoor[0], "villager reached bedside without opening the closed fixture door");
                helper.assertTrue(owner[0].getBrain().getMemory(MemoryModuleType.HOME)
                                .filter(home -> home.pos().equals(head)).isPresent()
                                && level.getPoiManager().getCountInRange(poi -> poi.is(PoiTypes.HOME), head, 0,
                                net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE) == 0,
                        "shelter arrival disturbed HOME ownership or its POI ticket");
                helper.succeed();
            }
        });
    }

    private static BlockPos prepareBedsideHouse(GameTestHelper helper, BlockPos center) {
        prepareStrollRoom(helper, center);
        ServerLevel level = helper.getLevel();
        level.setBlock(center.east(), Blocks.AIR.defaultBlockState(), 3);
        level.setBlock(center, Blocks.AIR.defaultBlockState(), 3);
        BlockPos foot = center.south(2);
        var bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        level.setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
        level.setBlock(foot.east(), bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
        BlockPos door = center.east().south(4);
        var doorState = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.NORTH);
        level.setBlock(door, doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(door.above(), doorState.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        for (int x = 0; x <= 2; x++) {
            BlockPos outside = center.offset(x, 0, 5);
            for (int y = -1; y <= 4; y++) {
                BlockPos pos = outside.above(y);
                STROLL_BLOCKS.putIfAbsent(pos.immutable(), level.getBlockState(pos));
                level.setBlock(pos, y == -1 || y == 3 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            }
        }
        return foot.east();
    }

    @GameTest(batch = "mca_shelter_reachability", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void reachableShelterIsNotRejectedByAnUnreachableFloorSample(GameTestHelper helper) {
        BlockPos floor = helper.absolutePos(new BlockPos(3, 1, 3));
        for (BlockPos pos : BlockPos.betweenClosed(floor.west(2), floor.offset(6, 0, 6))) {
            helper.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(pos.above(y), Blocks.AIR.defaultBlockState(), 3);
            }
            if (pos.getX() >= floor.getX()) {
                helper.getLevel().setBlock(pos.above(3), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        encloseFloor(helper, floor);
        BlockPos foot = floor.offset(2, 0, 3);
        BlockPos head = foot.east();
        var bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        helper.getLevel().setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
        helper.getLevel().setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 3);

        BlockPos unreachable = floor.offset(5, 0, 3);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(unreachable.relative(direction).above(y),
                        Blocks.STONE.defaultBlockState(), 3);
            }
        }
        VillagerEntityMCA villager = spawn(helper, floor.west(2));
        helper.runAfterDelay(10, () -> {
            var blockedPath = villager.getNavigation().createPath(unreachable, 0);
            helper.assertTrue(blockedPath == null || !blockedPath.canReach(),
                    "fixture enclosed floor was reachable");
            var reachablePath = villager.getNavigation().createPath(floor.east().south(), 0);
            helper.assertTrue(reachablePath != null && reachablePath.canReach(),
                    "fixture did not contain reachable shelter");
            SeekIndoorShelterTask task = new SeekIndoorShelterTask(0.5F);
            helper.assertTrue(EnterBuildingTask.isUsableFloor(helper.getLevel(), villager, unreachable),
                    "fixture enclosed floor did not qualify as a shelter candidate");
            helper.assertTrue(villager.getResidency().getHomeVillage().isEmpty(),
                    "fixture unexpectedly used a registered room instead of nearby-floor discovery");
            helper.getLevel().getRandom().setSeed(0x5EEDL);
            for (int attempt = 0; attempt < 128; attempt++) {
                var selected = task.getNextPosition(villager);
                helper.assertTrue(selected.isPresent(),
                        "reachable shelter was rejected by the sampled floor; attempt=" + attempt);
                var path = villager.getNavigation().createPath(selected.orElseThrow(), 0);
                helper.assertTrue(path != null && path.canReach(), "selected shelter was unreachable: target="
                        + selected.orElseThrow() + ", state=" + helper.getLevel().getBlockState(selected.orElseThrow())
                        + ", pathTarget=" + (path == null ? "null" : path.getTarget()));
            }
            helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.HOME).isEmpty(),
                    "shelter selection claimed HOME");
            villager.discard();
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_occupied_shelter_floor", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void homelessRestChoosesIndoorFloorInsteadOfOccupiedBed(GameTestHelper helper) {
        BlockPos floor = helper.absolutePos(new BlockPos(2, 1, 2));
        setDayTime(helper.getLevel(), 13000L);
        for (BlockPos pos : BlockPos.betweenClosed(floor, floor.offset(6, 0, 6))) {
            helper.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(pos.above(2), Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(pos.above(3), Blocks.STONE.defaultBlockState(), 3);
        }
        for (BlockPos pos : BlockPos.betweenClosed(floor.west(3), floor.offset(0, 0, 6))) {
            helper.getLevel().setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
        }
        BlockPos foot = floor.offset(3, 0, 3);
        encloseFloor(helper, floor);
        BlockPos head = foot.east();
        var bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        helper.getLevel().setBlock(foot, bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
        helper.getLevel().setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
        helper.getLevel().getPoiManager().take(poi -> poi.is(PoiTypes.HOME),
                (poi, pos) -> pos.equals(head), head, 1).orElseThrow();

        VillagerEntityMCA sleeper = spawn(helper, head);
        sleeper.startSleeping(head);
        VillagerEntityMCA homeless = spawn(helper, floor.west(3));
        helper.runAfterDelay(10, () -> {
            helper.assertTrue(!helper.getLevel().canSeeSky(floor), "fixture roof did not provide indoor shelter");
            var rest = VillagerTasksMCA.getRestPackage(0.5F).stream()
                    .filter(entry -> entry.getFirst() == 5).findFirst().orElseThrow().getSecond();
            long now = helper.getLevel().getGameTime();
            boolean selected = false;
            for (int attempt = 0; attempt < 80 && !selected; attempt++) {
                long time = now + attempt * 100L;
                rest.tryStart(helper.getLevel(), homeless, time);
                var walk = homeless.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
                if (walk != null) {
                    BlockPos target = walk.getTarget().currentBlockPosition();
                    helper.assertTrue(!BedPoiCompatibility.isCompatibleBedState(helper.getLevel().getBlockState(target))
                                    && !BedPoiCompatibility.isCompatibleBedState(helper.getLevel().getBlockState(target.below())),
                            "homeless REST targeted the occupied bed: " + target);
                    selected = !helper.getLevel().canSeeSky(target)
                            && homeless.getNavigation().isStableDestination(target);
                    homeless.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                }
                rest.doStop(helper.getLevel(), homeless, time);
            }
            var floorPath = homeless.getNavigation().createPath(floor, 0);
            helper.assertTrue(selected, "homeless REST never selected an indoor floor destination; originSky="
                    + helper.getLevel().canSeeSky(homeless.blockPosition())
                    + " floorSky=" + helper.getLevel().canSeeSky(floor)
                    + " stable=" + homeless.getNavigation().isStableDestination(floor)
                    + " collisionFree=" + helper.getLevel().noCollision(homeless,
                    homeless.getBoundingBox().move(Vec3.atBottomCenterOf(floor).subtract(homeless.position())))
                    + " floorPath=" + (floorPath == null ? "null" : floorPath.canReach()));
            helper.assertTrue(homeless.getBrain().getMemoryInternal(MemoryModuleType.HOME).isEmpty(),
                    "shelter movement claimed somebody else's HOME");
            sleeper.stopSleeping();
            sleeper.discard();
            homeless.discard();
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_indoor_stroll_selection", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void indoorWanderingDoesNotTargetBedSurface(GameTestHelper helper) {
        BlockPos foot = helper.absolutePos(new BlockPos(592, 2, 80));
        prepareStrollRoom(helper, foot);
        VillagerEntityMCA villager = spawn(helper, foot.west());
        STROLL_VILLAGERS.add(villager);
        helper.runAfterDelay(10, () -> {
            verifyStandingDestinations(helper, villager);
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_indoor_stroll_leaves", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void indoorWanderingDoesNotChooseLeafSupport(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(720, 2, 80));
        prepareStrollRoom(helper, center);
        BlockPos origin = center.west(2);
        BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-1, 0, -1), origin.offset(1, 0, 1))) {
            helper.getLevel().setBlock(pos.below(), leaves, 3);
        }
        helper.getLevel().setBlock(origin.below(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(origin.south().below(), Blocks.STONE.defaultBlockState(), 3);
        VillagerEntityMCA villager = spawn(helper, origin);
        STROLL_VILLAGERS.add(villager);
        helper.runAfterDelay(10, () -> {
            verifyStandingDestinations(helper, villager);
            helper.succeed();
        });
    }

    private static void verifyStandingDestinations(GameTestHelper helper, VillagerEntityMCA villager) {
        helper.assertTrue(!helper.getLevel().canSeeSky(villager.blockPosition()), "stroll fixture has open sky");
        var wander = LocalInsideBrownianWalk.create(0.5F);
        long now = helper.getLevel().getGameTime();
        int publications = 0;
        for (int attempt = 0; attempt < 128; attempt++) {
            long time = now + attempt * 100L;
            wander.tryStart(helper.getLevel(), villager, time);
            var walk = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
            if (walk.isPresent()) {
                var tracker = walk.orElseThrow().getTarget();
                Set<BlockPos> targets = tracker instanceof MultiTargetPositionTracker multiTarget
                        ? multiTarget.getPathTargets(villager) : Set.of(tracker.currentBlockPosition());
                for (BlockPos target : targets) {
                    assertStandingDestination(helper, villager, target);
                    helper.assertTrue(helper.getLevel().noCollision(villager,
                                    villager.getBoundingBox().move(Vec3.atBottomCenterOf(target).subtract(villager.position()))),
                            "stroll selected a destination without body clearance: " + target);
                }
                var path = villager.getNavigation().createPath(targets, 0);
                helper.assertTrue(path != null && path.canReach() && targets.contains(path.getTarget()),
                        "stroll endpoints required surface relocation or were unreachable: " + targets);
                publications++;
            }
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            wander.doStop(helper.getLevel(), villager, time);
        }
        helper.assertTrue(publications > 0, "indoor stroll never selected available clear floor space");
    }

    @GameTest(batch = "mca_indoor_stroll_movement", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 500)
    public static void villagersStrollOnFloorAndLeaveBedWithoutTakingHome(GameTestHelper helper) {
        BlockPos foot = helper.absolutePos(new BlockPos(848, 2, 80));
        BlockPos head = foot.east();
        prepareStrollRoom(helper, foot);
        previousTeleport = Config.getInstance().allowVillagerTeleporting;
        Config.getInstance().allowVillagerTeleporting = false;
        List<VillagerEntityMCA> walkers = new ArrayList<>();
        Map<VillagerEntityMCA, Set<BlockPos>> visited = new LinkedHashMap<>();
        Map<VillagerEntityMCA, Integer> lateBedTicks = new LinkedHashMap<>();
        VillagerEntityMCA[] owner = {null};
        long[] started = {0L};
        helper.runAfterDelay(10, () -> {
            helper.getLevel().getPoiManager().take(poi -> poi.is(PoiTypes.HOME),
                    (poi, pos) -> pos.equals(head), head, 1).orElseThrow();
            owner[0] = spawn(helper, head);
            STROLL_VILLAGERS.add(owner[0]);
            owner[0].getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), head));
            owner[0].startSleeping(head);
            for (BlockPos position : List.of(foot, foot.west(2), foot.south(2))) {
                VillagerEntityMCA villager = spawn(helper, position);
                STROLL_VILLAGERS.add(villager);
                if (position.equals(foot)) {
                    villager.setPos(Vec3.atBottomCenterOf(foot).add(0.0D, 0.5625D, 0.0D));
                }
                var brain = villager.getBrain();
                brain.removeAllBehaviors();
                brain.addActivity(Activity.CORE, ImmutableList.of(
                        Pair.of(0, LocalInsideBrownianWalk.create(0.5F)),
                        Pair.of(1, new WanderOrTeleportToTargetTask())), Set.of(), Set.of());
                brain.setCoreActivities(Set.of(Activity.CORE));
                villager.setNoAi(false);
                walkers.add(villager);
                visited.put(villager, new HashSet<>());
                lateBedTicks.put(villager, 0);
            }
            started[0] = helper.getLevel().getGameTime();
        });
        helper.onEachTick(() -> {
            if (owner[0] == null) {
                return;
            }
            long elapsed = helper.getLevel().getGameTime() - started[0];
            for (VillagerEntityMCA villager : walkers) {
                helper.assertTrue(!villager.getBrain().hasMemoryValue(MemoryModuleType.HOME) && !villager.isSleeping(),
                        "homeless stroll took HOME or slept in the claimed bed");
                villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).ifPresent(walk ->
                        assertStandingDestination(helper, villager, walk.getTarget().currentBlockPosition()));
                if (villager.getY() <= foot.getY() + 0.1D) {
                    visited.get(villager).add(villager.blockPosition());
                }
                if (elapsed >= 300 && BedPoiCompatibility.isCompatibleBedState(
                        helper.getLevel().getBlockState(villager.blockPosition()))) {
                    lateBedTicks.compute(villager, (ignored, count) -> count + 1);
                }
            }
            if (elapsed >= 400) {
                for (VillagerEntityMCA villager : walkers) {
                    helper.assertTrue(visited.get(villager).size() >= 3,
                            "villager did not stroll between floor cells: " + villager.position() + ", cells=" + visited.get(villager));
                    helper.assertTrue(lateBedTicks.get(villager) < 20,
                            "villager lingered on the bed: " + lateBedTicks.get(villager) + " of the final 100 ticks");
                }
                helper.assertTrue(owner[0].isSleeping()
                                && owner[0].getBrain().getMemory(MemoryModuleType.HOME)
                                .filter(home -> home.pos().equals(head)).isPresent(),
                        "strolling villagers disturbed the resident's HOME or sleep");
                helper.assertTrue(helper.getLevel().getPoiManager().getCountInRange(
                                poi -> poi.is(PoiTypes.HOME), head, 0,
                                net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.HAS_SPACE) == 0,
                        "strolling villagers released the resident's HOME ticket");
                helper.succeed();
            }
        });
    }

    private static void assertStandingDestination(GameTestHelper helper, VillagerEntityMCA villager, BlockPos target) {
        var state = helper.getLevel().getBlockState(target);
        var support = helper.getLevel().getBlockState(target.below());
        helper.assertTrue(!state.is(BlockTags.DOORS), "stroll selected a doorway as idle shelter: " + target);
        helper.assertTrue(!state.is(BlockTags.LEAVES) && !support.is(BlockTags.LEAVES),
                "stroll selected leaf support: " + target);
        helper.assertTrue(!BedPoiCompatibility.isCompatibleBedState(state)
                        && !BedPoiCompatibility.isCompatibleBedState(support)
                        && !BedPoiCompatibility.isCompatibleBedState(helper.getLevel().getBlockState(target.above())),
                "stroll selected the bed or its underlying support: " + target);
        helper.assertTrue(!helper.getLevel().canSeeSky(target) && villager.getNavigation().isStableDestination(target)
                        && state.getCollisionShape(helper.getLevel(), target).isEmpty(),
                "stroll selected a support block instead of a covered standing position: " + target);
    }

    private static void prepareStrollRoom(GameTestHelper helper, BlockPos center) {
        ServerLevel level = helper.getLevel();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -1, -4), center.offset(4, 4, 4))) {
            STROLL_BLOCKS.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        }
        ChunkPos min = ChunkPos.containing(center.offset(-20, 0, -20));
        ChunkPos max = ChunkPos.containing(center.offset(20, 0, 20));
        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!level.getForceLoadedChunks().contains(chunk.pack())) {
                    STROLL_CHUNKS.add(chunk);
                }
                level.setChunkForced(x, z, true);
                level.getChunk(x, z);
            }
        }
        prepareFlatArea(helper, center, 4, 5);
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                for (int y = 0; y <= 3; y++) {
                    if (Math.abs(x) == 4 || Math.abs(z) == 4 || y == 3) {
                        level.setBlock(center.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
                    }
                }
            }
        }
        var bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        level.setBlock(center, bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
        level.setBlock(center.east(), bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
        level.setBlock(center.offset(2, 0, 2), Blocks.CHEST.defaultBlockState(), 3);
        level.setBlock(center.offset(-2, 0, -2), Blocks.OAK_LEAVES.defaultBlockState()
                .setValue(BlockStateProperties.PERSISTENT, true), 3);
    }

    @AfterBatch(batch = "mca_indoor_stroll_selection")
    public static void cleanupSelection(ServerLevel level) {
        cleanupStroll(level);
    }

    @AfterBatch(batch = "mca_registered_shelter_arrival")
    public static void cleanupRegisteredArrival(ServerLevel level) {
        cleanupStroll(level);
    }

    @AfterBatch(batch = "mca_unregistered_shelter_arrival")
    public static void cleanupUnregisteredArrival(ServerLevel level) {
        cleanupStroll(level);
    }

    @AfterBatch(batch = "mca_registered_stroll_selection")
    public static void cleanupRegisteredSelection(ServerLevel level) {
        cleanupStroll(level);
    }

    @AfterBatch(batch = "mca_registered_rest_movement")
    public static void cleanupRegisteredRest(ServerLevel level) {
        cleanupStroll(level);
    }

    @AfterBatch(batch = "mca_unregistered_rest_movement")
    public static void cleanupUnregisteredRest(ServerLevel level) {
        cleanupStroll(level);
    }

    private static void encloseFloor(GameTestHelper helper, BlockPos floor) {
        for (int x = 0; x <= 6; x++) {
            for (int z = 0; z <= 6; z++) {
                if (x == 0 || x == 6 || z == 0 || z == 6) {
                    for (int y = 0; y < 3; y++) {
                        helper.getLevel().setBlock(floor.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
                    }
                }
            }
        }
        BlockPos door = floor.south(3);
        var state = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        helper.getLevel().setBlock(door, state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        helper.getLevel().setBlock(door.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
    }

    @AfterBatch(batch = "mca_shelter_bedside")
    public static void cleanupBedside(ServerLevel level) {
        cleanupStroll(level);
    }

    @GameTest(batch = "mca_shelter_room_cache", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 560)
    public static void sharedRoomCacheReusesBedsAndInvalidatesRoofChanges(GameTestHelper helper) {
        verifyRoomCache(helper, false);
    }

    @GameTest(batch = "mca_registered_room_cache", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 560)
    public static void registeredRoomCacheDoesNotRestoreInvalidatedGeometry(GameTestHelper helper) {
        verifyRoomCache(helper, true);
    }

    @GameTest(batch = "mca_registered_room_partition_cache", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 100)
    public static void registeredRoomCacheDiscoversRemovedPartitionOnFirstUse(GameTestHelper helper) {
        verifyRemovedPartition(helper, false);
    }

    @GameTest(batch = "mca_registered_room_partition_cache", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 1500)
    public static void registeredRoomCacheDiscoversRemovedPartitionAfterExpiry(GameTestHelper helper) {
        verifyRemovedPartition(helper, true);
    }

    private static void verifyRemovedPartition(GameTestHelper helper, boolean expire) {
        BlockPos center = helper.absolutePos(new BlockPos(expire ? 2128 : 1872, 2, 80));
        BlockPos head = prepareBedsideHouse(helper, center);
        ServerLevel level = helper.getLevel();
        BlockPos adjacentRoom = center.west(3).north(2);
        for (int z = -3; z <= 3; z++) {
            for (int y = 0; y < 3; y++) {
                level.setBlock(center.offset(-1, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        BlockPos door = center.west();
        var state = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        level.setBlock(door, state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), 3);
        level.setBlock(door.above(), state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        var cache = VillageManager.get(level).getIndoorRooms();
        Building[] registered = {null};
        helper.runAfterDelay(10, () -> {
            Village village = registerStrollHouse(helper, center);
            registered[0] = village.findPhysicalRoomAt(head).orElseThrow();
            helper.assertTrue(!registered[0].containsFloorPosition(adjacentRoom),
                    "fixture partition did not separate the bed from the adjacent room");
            if (expire) {
                helper.assertTrue(!cache.resolve(head).orElseThrow().floorCells().contains(adjacentRoom),
                        "initial room discovery ignored the partition");
            }
        });
        // Wait beyond both the idle lifetime and the cache's periodic eviction pass.
        int removeAt = expire ? 1420 : 20;
        helper.runAfterDelay(removeAt, () -> {
            for (int z = -3; z <= 3; z++) {
                // Keep the door column: removing its upper half also changes
                // a recorded ceiling and would mask stale wall membership.
                if (z == 0) continue;
                for (int y = 0; y < 3; y++) {
                    level.setBlock(center.offset(-1, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        });
        helper.runAfterDelay(removeAt + 10, () -> {
            helper.assertTrue(!registered[0].containsFloorPosition(adjacentRoom),
                    "fixture registration refreshed before testing cache discovery");
            var resolved = cache.resolve(head).orElseThrow();
            helper.assertTrue(resolved.floorCells().contains(adjacentRoom)
                            && resolved.floorCells().contains(door),
                    "room discovery restored a stale registered partition after its wall was removed");
            helper.assertTrue(!resolved.floorCells().contains(center.east().south(5)),
                    "merged interior room included the covered exterior doorstep");
            helper.succeed();
        });
    }

    private static void verifyRoomCache(GameTestHelper helper, boolean registered) {
        BlockPos center = helper.absolutePos(new BlockPos(registered ? 1616 : 1360, 2, 80));
        BlockPos head = prepareBedsideHouse(helper, center);
        ServerLevel level = helper.getLevel();
        BlockPos secondFoot = center.west(3).south();
        BlockPos secondHead = secondFoot.east();
        var bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        level.setBlock(secondFoot, bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
        level.setBlock(secondHead, bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
        BlockPos gate = center.west(4);
        level.setBlock(gate, Blocks.OAK_FENCE_GATE.defaultBlockState(), 3);
        var cache = VillageManager.get(level).getIndoorRooms();
        IndoorRoomCache.Room[] original = {null};
        BlockPos roof = center.north().above(3);
        helper.runAfterDelay(10, () -> {
            if (registered) registerStrollHouse(helper, center);
            original[0] = cache.resolve(head).orElseThrow();
            helper.assertTrue(cache.resolve(secondHead).orElseThrow() == original[0],
                    "beds in the same room did not reuse one geometry result");
            helper.assertTrue(!original[0].floorCells().contains(center.east().south(5)), "cache included covered exterior");
            level.setBlock(head, level.getBlockState(head).setValue(BedBlock.OCCUPIED, true), 3);
            BlockPos door = center.east().south(4);
            level.setBlock(door, level.getBlockState(door).setValue(DoorBlock.OPEN, true), 3);
            level.setBlock(door.above(), level.getBlockState(door.above()).setValue(DoorBlock.OPEN, true), 3);
            level.setBlock(gate, level.getBlockState(gate).setValue(BlockStateProperties.OPEN, true), 3);
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(cache.resolve(head).orElseThrow() == original[0],
                    "bed occupancy or door/gate movement unnecessarily rescanned the room");
            level.setBlock(roof, Blocks.AIR.defaultBlockState(), 3);
        });
        helper.runAfterDelay(110, () -> {
            helper.assertTrue(cache.resolve(head).isEmpty(), "roof opening retained stale indoor room geometry");
        });
        helper.runAfterDelay(320, () -> {
            helper.assertTrue(cache.resolve(head).isEmpty(), "retry resurrected stale registered geometry with an open roof");
            level.setBlock(roof, Blocks.STONE.defaultBlockState(), 3);
        });
        helper.runAfterDelay(525, () -> {
            helper.assertTrue(cache.resolve(head).orElseThrow() != original[0], "repaired roof did not produce fresh geometry");
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_shelter_memory", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void selectedShelterIsForgottenWhenRestEnds(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(1488, 2, 80));
        prepareBedsideHouse(helper, center);
        VillagerEntityMCA villager = spawn(helper, center.north(2));
        STROLL_VILLAGERS.add(villager);
        helper.runAfterDelay(10, () -> {
            villager.getBrain().setActiveActivityIfPossible(Activity.REST);
            new SeekIndoorShelterTask(0.5F).tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime());
            helper.assertTrue(villager.getBrain().hasMemoryValue(MemoryModuleTypeMCA.SHELTER_BED),
                    "REST did not remember the selected shelter");
            villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);
            helper.assertTrue(!villager.getBrain().hasMemoryValue(MemoryModuleTypeMCA.SHELTER_BED),
                    "selected shelter survived leaving REST");
            helper.succeed();
        });
    }

    @AfterBatch(batch = "mca_shelter_room_cache")
    public static void cleanupRoomCache(ServerLevel level) {
        cleanupStroll(level);
    }

    @AfterBatch(batch = "mca_registered_room_cache")
    public static void cleanupRegisteredRoomCache(ServerLevel level) {
        cleanupStroll(level);
    }

    @AfterBatch(batch = "mca_registered_room_partition_cache")
    public static void cleanupRegisteredPartitionCache(ServerLevel level) {
        cleanupStroll(level);
    }

    @AfterBatch(batch = "mca_shelter_memory")
    public static void cleanupShelterMemory(ServerLevel level) {
        cleanupStroll(level);
    }

    @AfterBatch(batch = "mca_shelter_bedside_movement")
    public static void cleanupBedsideMovement(ServerLevel level) {
        cleanupStroll(level);
    }

    @AfterBatch(batch = "mca_indoor_stroll_movement")
    public static void cleanupMovement(ServerLevel level) {
        cleanupStroll(level);
    }

    @AfterBatch(batch = "mca_indoor_stroll_leaves")
    public static void cleanupLeaves(ServerLevel level) {
        cleanupStroll(level);
    }

    private static void cleanupStroll(ServerLevel level) {
        STROLL_VILLAGERS.forEach(villager -> {
            if (villager.isSleeping()) {
                villager.stopSleeping();
            }
            villager.releasePoi(MemoryModuleType.HOME);
            villager.discard();
        });
        STROLL_VILLAGERS.clear();
        STROLL_VILLAGES.forEach(id -> VillageManager.get(level).removeVillage(id));
        STROLL_VILLAGES.clear();
        STROLL_BLOCKS.forEach((pos, state) -> level.setBlock(pos, state, 3));
        STROLL_BLOCKS.clear();
        STROLL_CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x(), chunk.z(), false));
        STROLL_CHUNKS.clear();
        if (previousTeleport != null) {
            Config.getInstance().allowVillagerTeleporting = previousTeleport;
            previousTeleport = null;
        }
        if (previousDayTime != null) {
            setDayTime(level, previousDayTime);
            previousDayTime = null;
        }
        if (previousDaylightCycle != null) {
            level.getGameRules().set(GameRules.ADVANCE_TIME, previousDaylightCycle, level.getServer());
            previousDaylightCycle = null;
        }
    }

    private static VillagerEntityMCA spawn(GameTestHelper helper, BlockPos pos) {
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(pos)).spawn(EntitySpawnReason.STRUCTURE);
        villager.setNoAi(true);
        villager.refreshBrain(helper.getLevel());
        villager.setOnGround(true);
        return villager;
    }

    private static void setDayTime(ServerLevel level, long ticks) {
        var clock = level.dimensionType().defaultClock().orElseThrow();
        level.clockManager().setTotalTicks(clock, ticks);
    }
}
