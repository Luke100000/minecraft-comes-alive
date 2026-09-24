package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashSet;
import java.util.Set;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;
import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatPath;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class MCAGroundPathNavigationGameTests {
    private static final Set<ChunkPos> PROGRESSIVE_FORCED_CHUNKS = new HashSet<>();

    private MCAGroundPathNavigationGameTests() {
    }

    @GameTest(batch = "mca_navigation_recompute", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void airborneRecomputePreservesActivePath(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(2, 1, 2));
        BlockPos target = start.east(8);
        prepareFlatPath(helper, start, target);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Recompute Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        villager.setOnGround(true);

        var navigation = villager.getNavigation();
        helper.assertTrue(navigation instanceof MCAGroundPathNavigation,
                "fixture villager did not use MCA ground navigation");
        Path path = navigation.createPath(target, 0);
        helper.assertTrue(path != null && path.canReach(),
                "fixture could not create a reachable active path");
        helper.assertTrue(navigation.moveTo(path, 0.0D),
                "fixture could not install the active path");

        helper.runAfterDelay(21, () -> {
            Path originalPath = navigation.getPath();
            helper.assertTrue(originalPath != null,
                    "fixture lost its active path before recomputation");

            villager.setPos(villager.getX(), villager.getY() + 2.0D, villager.getZ());
            villager.setOnGround(false);
            navigation.recomputePath();

            helper.assertTrue(navigation.getPath() == originalPath,
                    "airborne recomputation discarded the active path");

            villager.setPos(Vec3.atBottomCenterOf(start));
            villager.setOnGround(true);
            navigation.recomputePath();
            helper.assertTrue(navigation.getPath() != null,
                    "delayed recomputation did not resume after landing");

            villager.discard();
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_navigation_required_path_length", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void ordinaryNavigationRemains48WhenFollowRangeIsLowered(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos target = start.east(30);
        prepareFlatPath(helper, start, target);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Required Path Length Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
        double followRangeBefore = villager.getAttributeValue(Attributes.FOLLOW_RANGE);

        Path path = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(path != null && path.canReach(),
                "ordinary navigation fell below the 48-block navigation floor when FOLLOW_RANGE was lowered");
        helper.assertTrue(villager.getAttributeValue(Attributes.FOLLOW_RANGE) == followRangeBefore,
                "ordinary path creation mutated FOLLOW_RANGE");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_long_distance_horizon", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void farStaticWalkTargetEscalatesOnlyAfterFailureEvidence(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos target = start.east(80);
        prepareFlatPath(helper, start, target);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Long Path Horizon Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setNoAi(true);
        villager.setOnGround(true);
        double followRangeBefore = villager.getAttributeValue(Attributes.FOLLOW_RANGE);

        Path ordinary = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(ordinary != null && !ordinary.canReach() && ordinary.getEndNode() != null,
                "fixture ordinary path did not stop at its normal bounded horizon");

        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new LongDistancePathTarget(target), 0.5F, 0));
        Path progressive = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(progressive != null && progressive.getEndNode() != null,
                "long-distance WALK_TARGET did not receive a progressive path result");
        helper.assertTrue(target.equals(progressive.getTarget()),
                "progressive path did not retain the real destination");
        helper.assertTrue(progressive.getEndNode().asBlockPos().equals(ordinary.getEndNode().asBlockPos()),
                "first far-static segment expanded beyond the ordinary geometric horizon");

        villager.getBrain().setMemory(
                MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
                helper.getLevel().getGameTime()
        );
        Path escalated = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(escalated != null && escalated.getEndNode() != null,
                "canonical retry did not receive an extended path result");
        helper.assertTrue(target.equals(escalated.getTarget()),
                "extended retry did not retain the real destination");
        helper.assertTrue(start.distSqr(escalated.getEndNode().asBlockPos())
                        > start.distSqr(progressive.getEndNode().asBlockPos()),
                "canonical retry did not extend the geometric path horizon");
        helper.assertTrue(escalated.getEndNode().asBlockPos().distSqr(target) < start.distSqr(target),
                "extended retry did not make useful progress toward the real destination");
        helper.assertTrue(villager.getAttributeValue(Attributes.FOLLOW_RANGE) == followRangeBefore,
                "long-distance path request mutated FOLLOW_RANGE");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_unloaded_chunk", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void extendedSearchDoesNotLoadFarChunk(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        BlockPos target = start.east(150);
        prepareFlatArea(helper, start, 2, 3);
        helper.assertTrue(!helper.getLevel().isLoaded(target),
                "fixture requires the distant target chunk to start unloaded");

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Unloaded Path Horizon Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new LongDistancePathTarget(target), 0.5F, 0));

        Config config = Config.getInstance();
        int originalPathfindingDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 160;
            villager.getNavigation().createPath(target, 0);
        } finally {
            config.villagerPathfindingDistance = originalPathfindingDistance;
        }

        helper.assertTrue(!helper.getLevel().isLoaded(target),
                "extended path request synchronously loaded the distant target chunk");

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_progressive_walk", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 1_200)
    public static void longDistanceTargetWalksAcrossMultiplePathSegments(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 1, 4));
        int pathHorizon = Math.max(Config.getInstance().getVillagerPathfindingDistance(), 48);
        BlockPos target = start.east(pathHorizon + 32);
        ChunkPos startChunk = new ChunkPos(start);
        ChunkPos targetChunk = new ChunkPos(target);
        for (int chunkX = startChunk.x; chunkX <= targetChunk.x; chunkX++) {
            PROGRESSIVE_FORCED_CHUNKS.add(new ChunkPos(chunkX, startChunk.z));
            helper.getLevel().setChunkForced(chunkX, startChunk.z, true);
        }
        prepareFlatPath(helper, start, target);

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Progressive Navigation Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new LongDistancePathTarget(target), 1.0F, 0));

        Path firstSegment = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(firstSegment != null && !firstSegment.canReach(),
                "fixture did not produce a bounded first path segment");
        helper.assertTrue(target.equals(firstSegment.getTarget()),
                "first path segment did not retain the real destination");
        helper.assertTrue(villager.getNavigation().moveTo(firstSegment, 1.0D),
                "first long-distance path segment could not start");

        helper.succeedWhen(() -> {
            Path activePath = villager.getNavigation().getPath();
            helper.assertTrue(villager.blockPosition().equals(target),
                    "villager did not walk across multiple path segments to the real destination"
                            + "; pos=" + villager.blockPosition()
                            + "; posLoaded=" + helper.getLevel().isLoaded(villager.blockPosition())
                            + "; targetLoaded=" + helper.getLevel().isLoaded(target)
                            + "; navDone=" + villager.getNavigation().isDone()
                            + "; path=" + summarizePath(activePath));
            villager.discard();
        });
    }

    @GameTest(batch = "mca_navigation_nearby_detour", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 600)
    public static void nearbyStaticTargetCanTakeRouteLongerThanFollowRange(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(40, 1, 40));
        BlockPos target = start.east(2);
        prepareFlatArea(helper, start, 30, 3);

        for (int z = -23; z <= 23; z++) {
            BlockPos wall = start.offset(1, 0, z);
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(wall.above(y), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Nearby Long Detour Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
        double followRangeBefore = villager.getAttributeValue(Attributes.FOLLOW_RANGE);
        var navigation = villager.getNavigation();
        navigation.stop();
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

        Path ordinary = navigation.createPath(target, 0);
        helper.assertTrue(ordinary != null && !ordinary.canReach(),
                "fixture ordinary path unexpectedly solved a detour longer than 48 blocks");

        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.5F, 0));
        Config config = Config.getInstance();
        int originalPathfindingDistance = config.villagerPathfindingDistance;
        Path extended;
        try {
            config.villagerPathfindingDistance = 160;
            extended = navigation.createPath(target, 0);
        } finally {
            config.villagerPathfindingDistance = originalPathfindingDistance;
        }

        helper.assertTrue(extended != null && extended.canReach(),
                "extended search did not find the route around the 47-block wall");
        helper.assertTrue(target.equals(extended.getTarget()),
                "extended detour changed the logical destination");
        helper.assertTrue(villager.getAttributeValue(Attributes.FOLLOW_RANGE) == followRangeBefore,
                "extended detour changed sensing range");
        helper.assertTrue(navigation.moveTo(extended, 0.5D),
                "extended detour could not start navigation");

        Path cached = navigation.createPath(target, 0);
        helper.assertTrue(cached == extended,
                "fixture did not exercise vanilla's active-path cache reuse");

        // Exercise normal navigation, movement controls and collision, without moving
        // the villager or advancing path nodes from the test.
        helper.succeedWhen(() -> {
            helper.assertTrue(villager.blockPosition().equals(target),
                    "villager has not walked around the wall to the destination");
            villager.discard();
        });
    }

    @GameTest(batch = "mca_navigation_progressive_budget", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void longDistanceStaticTargetKeepsBoundedBudget(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(40, 1, 40));
        BlockPos target = start.east(80);
        prepareFlatArea(helper, start.east(40), 45, 3);

        for (int z = -23; z <= 23; z++) {
            BlockPos wall = start.offset(24, 0, z);
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(wall.above(y), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Progressive Budget Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);

        Config config = Config.getInstance();
        int originalPathfindingDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 160;

            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.5F, 0));
            Path progressive = villager.getNavigation().createPath(target, 0);
            helper.assertTrue(progressive != null && !progressive.canReach(),
                    "far static search consumed the enlarged detour budget in one pass");
            helper.assertTrue(MCAGroundPathNavigation.isUsefulPartialPath(progressive, target),
                    "bounded far-static search did not retain useful progress toward the real destination");

            villager.getBrain().setMemory(
                    MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
                    helper.getLevel().getGameTime()
            );
            Path escalated = villager.getNavigation().createPath(target, 0);
            helper.assertTrue(escalated != null && escalated.canReach(),
                    "canonical far-target retry did not receive the enlarged detour budget");
            helper.assertTrue(target.equals(escalated.getTarget()),
                    "canonical far-target retry changed the logical destination");
        } finally {
            config.villagerPathfindingDistance = originalPathfindingDistance;
        }

        villager.discard();
        helper.succeed();
    }

    @GameTest(batch = "mca_navigation_progressive_budget", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void usefulNearbyPartialDefersExpensiveDetourEscalation(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(40, 1, 40));
        BlockPos target = start.east(30);
        prepareFlatArea(helper, start.east(15), 40, 3);

        for (int z = -23; z <= 23; z++) {
            BlockPos wall = start.offset(20, 0, z);
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(wall.above(y), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Progressive Nearby Detour Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().removeAllBehaviors();
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.5F, 0));

        Config config = Config.getInstance();
        int originalPathfindingDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 160;
            Path first = villager.getNavigation().createPath(target, 0);
            helper.assertTrue(first != null && !first.canReach(),
                    "useful ordinary partial was replaced by an immediate enlarged detour search");
            helper.assertTrue(MCAGroundPathNavigation.isUsefulPartialPath(first, target),
                    "ordinary search did not produce useful progress toward the nearby target");

            BlockPos checkpoint = first.getEndNode().asBlockPos();
            villager.setPos(Vec3.atBottomCenterOf(checkpoint));
            Path escalated = villager.getNavigation().createPath(target, 0);
            helper.assertTrue(escalated != null && escalated.canReach(),
                    "detour escalation was not available after bounded progress became exhausted");
        } finally {
            config.villagerPathfindingDistance = originalPathfindingDistance;
        }

        villager.discard();
        helper.succeed();
    }

    @AfterBatch(batch = "mca_navigation_progressive_walk")
    public static void releaseProgressiveNavigationChunks(ServerLevel level) {
        PROGRESSIVE_FORCED_CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
        PROGRESSIVE_FORCED_CHUNKS.clear();
    }

    private static String summarizePath(Path path) {
        if (path == null) {
            return "null";
        }
        return "target=" + path.getTarget()
                + ", end=" + (path.getEndNode() == null ? null : path.getEndNode().asBlockPos())
                + ", canReach=" + path.canReach()
                + ", next=" + path.getNextNodeIndex() + '/' + path.getNodeCount();
    }

}
