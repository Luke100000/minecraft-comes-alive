package net.conczin.mca.entity.ai.navigation;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Regressions for climb routes that do not fit beneath solid ceilings. */
@PrefixGameTestTemplate(false)
public final class CaveVineNavigationGameTests {
    private CaveVineNavigationGameTests() {
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 40)
    public static void caveVinesOfferUpwardNavigationNodes(GameTestHelper helper) {
        BlockPos vine = helper.absolutePos(new BlockPos(4, 2, 4));
        prepareFlatArea(helper, vine, 3, 4);
        ServerLevel level = helper.getLevel();
        level.setBlock(vine.above(3), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(vine.above(2), Blocks.CAVE_VINES_PLANT.defaultBlockState(), 3);
        level.setBlock(vine.above(), Blocks.CAVE_VINES_PLANT.defaultBlockState(), 3);
        level.setBlock(vine, Blocks.CAVE_VINES.defaultBlockState(), 3);

        helper.assertTrue(level.getBlockState(vine).is(BlockTags.CLIMBABLE)
                        && level.getBlockState(vine.above()).is(BlockTags.CLIMBABLE),
                "the cave-vine column was not recognized as climbable");
        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0).withPosition(Vec3.atBottomCenterOf(vine))
                .spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        MCAWalkNodeEvaluator evaluator = new MCAWalkNodeEvaluator();
        try {
            evaluator.prepare(new PathNavigationRegion(level,
                    vine.offset(-4, -2, -4), vine.offset(4, 5, 4)), villager);
            Node from = evaluator.getStart();
            helper.assertTrue(from.asBlockPos().equals(vine),
                    "cave-vine pathfinding started outside the vine: " + from.asBlockPos());
            Node[] neighbors = new Node[64];
            int count = evaluator.getNeighbors(neighbors, from);
            boolean offersUpwardStep = false;
            for (int index = 0; index < count; index++) {
                offersUpwardStep |= neighbors[index].asBlockPos().equals(vine.above());
            }
            helper.assertTrue(offersUpwardStep,
                    "climbable cave vines should still offer an unobstructed upward step");
        } finally {
            evaluator.done();
            villager.discard();
        }
        helper.succeed();
    }

    /** A decorated bridge should still be traversable at floor level. */
    @GameTest(batch = "mca_cave_vine_unroofed_level_route", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 210)
    public static void villagerWalksUnderBridgeWithHangingGlowBerries(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, center, 7, 7);
        ServerLevel level = helper.getLevel();
        buildBridgeRoof(level, center);
        for (int dx : new int[]{-2, 0, 2}) {
            hangGlowBerries(level, center.offset(dx, 0, 0));
        }

        BlockPos start = center.west(5);
        BlockPos target = center.east(5);
        VillagerEntityMCA villager = spawnWalkingVillager(level, start);
        Path path = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(path != null && path.canReach(),
                "flat crossing under the berry-covered bridge should have a route: " + path);
        helper.assertTrue(villager.getNavigation().moveTo(path, 0.6D),
                "villager could not begin the crossing");

        final double[] highestFeetY = {villager.getY()};
        helper.onEachTick(() -> highestFeetY[0] = Math.max(highestFeetY[0], villager.getY()));
        helper.runAfterDelay(170, () -> {
            try {
                helper.assertTrue(villager.distanceToSqr(Vec3.atBottomCenterOf(target)) < 2.0D,
                        "villager got stuck crossing hanging glow berries: position=" + villager.position()
                                + ", target=" + target + ", path=" + summarizePath(villager.getNavigation().getPath()));
                helper.assertTrue(highestFeetY[0] - start.getY() < 0.25D,
                        "villager climbed decorative glow berries instead of staying on the bridge floor: highestFeetY="
                                + highestFeetY[0] + ", floorY=" + start.getY());
                helper.succeed();
            } finally {
                villager.discard();
            }
        });
    }

    /** Open headroom must not make decorative vines an attractive detour on level ground. */
    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 210)
    public static void villagerIgnoresUnnecessaryUnroofedGlowBerryClimb(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, center, 7, 7);
        ServerLevel level = helper.getLevel();
        level.setBlock(center.above(5), Blocks.STONE.defaultBlockState(), 3);
        hangGlowBerries(level, center);
        level.setBlock(center.above(3), Blocks.CAVE_VINES_PLANT.defaultBlockState(), 3);
        level.setBlock(center.above(4), Blocks.CAVE_VINES_PLANT.defaultBlockState(), 3);

        BlockPos start = center.west(5);
        BlockPos target = center.east(5);
        VillagerEntityMCA villager = spawnWalkingVillager(level, start);
        Path path = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(path != null && path.canReach(),
                "flat route through open cave vines should be reachable: " + summarizePath(path));
        helper.assertTrue(villager.getNavigation().moveTo(path, 0.6D),
                "villager could not start the flat route");

        final double[] highestFeetY = {villager.getY()};
        final double[] closestHorizontalDistanceSqr = {Double.POSITIVE_INFINITY};
        helper.onEachTick(() -> {
            highestFeetY[0] = Math.max(highestFeetY[0], villager.getY());
            double dx = villager.getX() - (center.getX() + 0.5D);
            double dz = villager.getZ() - (center.getZ() + 0.5D);
            closestHorizontalDistanceSqr[0] = Math.min(closestHorizontalDistanceSqr[0], dx * dx + dz * dz);
        });
        helper.runAfterDelay(170, () -> {
            try {
                helper.assertTrue(villager.distanceToSqr(Vec3.atBottomCenterOf(target)) < 2.0D,
                        "villager failed to cross freely climbable vines: position="
                                + villager.position() + ", path=" + summarizePath(villager.getNavigation().getPath()));
                helper.assertTrue(closestHorizontalDistanceSqr[0] < 0.49D,
                        "villager avoided the vines instead of testing contact with them: closestDistanceSqr="
                                + closestHorizontalDistanceSqr[0] + ", path=" + summarizePath(path));
                // Contact with the vine may nudge the feet slightly above a quarter block.
                // A genuine climb would rise at least half a block off this level path.
                helper.assertTrue(highestFeetY[0] - start.getY() < 0.5D,
                        "villager latched onto useless vines while following a level route: highestFeetY="
                                + highestFeetY[0] + ", floorY=" + start.getY());
                helper.succeed();
            } finally {
                villager.discard();
            }
        });
    }

    /** Reproduces an invalid upward path beneath a solid bridge deck, if one is offered. */
    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 180)
    public static void roofedGlowBerriesMustNotOfferUnreachableRoofClimb(GameTestHelper helper) {
        BlockPos vine = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, vine, 6, 8);
        ServerLevel level = helper.getLevel();
        buildBridgeRoof(level, vine);
        hangGlowBerries(level, vine);

        BlockPos rooftop = vine.east().above(4);
        helper.assertTrue(level.getBlockState(vine.above(3)).is(Blocks.SPRUCE_PLANKS),
                "bridge deck must be solid directly above the hanging vines");
        VillagerEntityMCA villager = spawnWalkingVillager(level, vine);
        Path path = villager.getNavigation().createPath(rooftop, 0);
        // A climb directly through the roof is impossible for a nearly two-block-tall villager.
        // If MCA claims that path is reachable, let it actually attempt the route, then
        // report its physical position and path state instead of relying on the graph alone.
        if (path == null || !path.canReach()) {
            villager.discard();
            helper.succeed();
            return;
        }
        helper.assertTrue(villager.getNavigation().moveTo(path, 0.6D),
                "bridge-roof path was marked reachable but could not begin");
        final double[] highestY = {villager.getY()};
        helper.onEachTick(() -> highestY[0] = Math.max(highestY[0], villager.getY()));
        helper.runAfterDelay(140, () -> {
            try {
                helper.assertTrue(villager.distanceToSqr(Vec3.atBottomCenterOf(rooftop)) < 2.0D,
                        "MCA pathfinds upward through glow berries into a solid bridge roof: start=" + vine
                                + ", target=" + rooftop + ", position=" + villager.position()
                                + ", highestFeetY=" + highestY[0]
                                + ", path=" + summarizePath(villager.getNavigation().getPath()));
                helper.succeed();
            } finally {
                villager.discard();
            }
        });
    }

    /** A ladder cannot take a villager through the solid ceiling covering its top. */
    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
    public static void roofedLadderMustNotOfferUnreachableRoofClimb(GameTestHelper helper) {
        BlockPos ladder = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, ladder, 6, 8);
        ServerLevel level = helper.getLevel();
        buildBridgeRoof(level, ladder);
        for (int y = 0; y < 3; y++) {
            level.setBlock(ladder.east().above(y), Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(ladder.above(y),
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST), 3);
        }
        BlockPos rooftop = ladder.west().above(4);
        VillagerEntityMCA villager = spawnWalkingVillager(level, ladder);
        try {
            Path path = villager.getNavigation().createPath(rooftop, 0);
            helper.assertTrue(path == null || !path.canReach(),
                    "MCA considers ladder through solid bridge roof reachable: " + summarizePath(path));
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    private static void buildBridgeRoof(ServerLevel level, BlockPos center) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                level.setBlock(center.offset(dx, 3, dz), Blocks.SPRUCE_PLANKS.defaultBlockState(), 3);
            }
        }
    }

    private static void hangGlowBerries(ServerLevel level, BlockPos feet) {
        level.setBlock(feet.above(2), Blocks.CAVE_VINES_PLANT.defaultBlockState(), 3);
        level.setBlock(feet.above(), Blocks.CAVE_VINES_PLANT.defaultBlockState(), 3);
        level.setBlock(feet, Blocks.CAVE_VINES.defaultBlockState(), 3);
    }

    private static VillagerEntityMCA spawnWalkingVillager(ServerLevel level, BlockPos start) {
        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start))
                .spawn(MobSpawnType.STRUCTURE);
        villager.refreshBrain(level);
        villager.getBrain().removeAllBehaviors();
        // Factory-spawned mobs have not run their first physics tick yet.
        villager.setOnGround(true);
        return villager;
    }

    private static String summarizePath(Path path) {
        if (path == null) {
            return "null";
        }
        StringBuilder nodes = new StringBuilder();
        for (int index = 0; index < path.getNodeCount(); index++) {
            if (index > 0) {
                nodes.append(" -> ");
            }
            nodes.append(path.getNode(index).asBlockPos());
        }
        return "canReach=" + path.canReach() + ", next=" + path.getNextNodeIndex()
                + "/" + path.getNodeCount() + ", nodes=" + nodes;
    }
}
