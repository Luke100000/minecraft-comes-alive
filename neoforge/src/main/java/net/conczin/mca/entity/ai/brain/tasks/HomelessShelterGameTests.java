package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.BedPoiCompatibility;
import net.conczin.mca.entity.ai.SchedulesMCA;
import net.conczin.mca.entity.ai.brain.VillagerTasksMCA;
import net.conczin.mca.entity.ai.navigation.BedApproachTarget;
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
    private static Boolean previousTeleport;
    private static Long previousDayTime;
    private static Boolean previousDaylightCycle;

    private HomelessShelterGameTests() {
    }

    @GameTest(batch = "mca_shelter_bedside", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void roofedDoorstepStillSelectsFloorBesideBed(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(80, 2, 80));
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
            helper.assertTrue(bedside.contains(selected) && selected.getZ() < center.getZ() + 4,
                    "shelter selected roofed ground outside instead of bedside floor: " + selected);
            helper.assertTrue(shelter.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime()),
                    "roofed doorstep incorrectly counted as completed shelter arrival");
            var walk = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
            helper.assertTrue(bedside.contains(walk.getTarget().currentBlockPosition()), "shelter did not publish a bedside target");
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
        BlockPos center = helper.absolutePos(new BlockPos(80, 2, 80));
        BlockPos head = prepareBedsideHouse(helper, center);
        BlockPos door = center.east().south(4);
        ServerLevel level = helper.getLevel();
        previousTeleport = Config.getInstance().allowVillagerTeleporting;
        Config.getInstance().allowVillagerTeleporting = false;
        previousDayTime = level.getOverworldClockTime();
        previousDaylightCycle = level.getGameRules().get(GameRules.ADVANCE_TIME);
        level.getGameRules().set(GameRules.ADVANCE_TIME, false, level.getServer());
        setDayTime(level, 16000L);
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
            var brain = walker[0].getMCABrain();
            brain.removeAllBehaviors();
            brain.addActivity(Activity.CORE, ImmutableList.of(
                    Pair.of(0, new SmarterOpenDoorsTask()), Pair.of(1, new WanderOrTeleportToTargetTask())),
                    Set.of(), Set.of());
            brain.addActivity(Activity.REST, VillagerTasksMCA.getRestPackage(0.5F), Set.of(), Set.of());
            brain.setCoreActivities(Set.of(Activity.CORE));
            brain.setDefaultActivity(Activity.REST);
            brain.setSchedule(SchedulesMCA.DEFAULT);
            brain.setActiveActivityIfPossible(Activity.REST);
            walker[0].setNoAi(false);
        });
        helper.onEachTick(() -> {
            if (walker[0] == null) return;
            VillagerEntityMCA villager = walker[0];
            Set<BlockPos> bedside = BedApproachTarget.create(level, head).orElseThrow().getPathTargets(villager);
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
        var bed = Blocks.BED.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
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
        BlockPos foot = floor.offset(2, 0, 3);
        BlockPos head = foot.east();
        var bed = Blocks.BED.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
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
            var reachablePath = villager.getNavigation().createPath(floor, 0);
            helper.assertTrue(reachablePath != null && reachablePath.canReach(),
                    "fixture did not contain reachable shelter");
            SeekIndoorShelterTask task = new SeekIndoorShelterTask(0.5F);
            helper.assertTrue(task.isGoodFloorWalkTarget(helper.getLevel(), villager, unreachable),
                    "fixture enclosed floor did not qualify as a shelter candidate");
            helper.assertTrue(villager.getResidency().getHomeVillage().isEmpty(),
                    "fixture unexpectedly used a registered room instead of nearby-floor discovery");
            helper.getLevel().getRandom().setSeed(0x5EEDL);
            for (int attempt = 0; attempt < 128; attempt++) {
                var selected = task.getNextPosition(villager);
                helper.assertTrue(selected.isPresent(),
                        "reachable shelter was rejected by the sampled floor; attempt=" + attempt);
                var path = villager.getNavigation().createPath(selected.orElseThrow(), 0);
                helper.assertTrue(path != null && path.canReach(), "selected shelter was unreachable");
            }
            helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.HOME).isEmpty(),
                    "shelter selection claimed HOME");
            villager.discard();
            helper.succeed();
        });
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
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
        BlockPos head = foot.east();
        var bed = Blocks.BED.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
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
        BlockPos foot = helper.absolutePos(new BlockPos(80, 2, 80));
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
        BlockPos center = helper.absolutePos(new BlockPos(80, 2, 80));
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
                BlockPos target = walk.orElseThrow().getTarget().currentBlockPosition();
                assertStandingDestination(helper, villager, target);
                helper.assertTrue(helper.getLevel().noCollision(villager,
                                villager.getBoundingBox().move(Vec3.atBottomCenterOf(target).subtract(villager.position()))),
                        "stroll selected a destination without body clearance: " + target);
                var path = villager.getNavigation().createPath(target, 0);
                helper.assertTrue(path != null && path.canReach() && path.getTarget().equals(target),
                        "stroll destination required surface relocation or was unreachable: " + target);
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
        BlockPos foot = helper.absolutePos(new BlockPos(80, 2, 80));
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
        ChunkPos min = ChunkPos.containing(center.offset(-4, 0, -4));
        ChunkPos max = ChunkPos.containing(center.offset(4, 0, 4));
        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!level.getForceLoadedChunks().contains(chunk.pack())) {
                    STROLL_CHUNKS.add(chunk);
                }
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
        var bed = Blocks.BED.pick(net.minecraft.world.item.DyeColor.WHITE).defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
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

    @AfterBatch(batch = "mca_shelter_bedside")
    public static void cleanupBedside(ServerLevel level) {
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
