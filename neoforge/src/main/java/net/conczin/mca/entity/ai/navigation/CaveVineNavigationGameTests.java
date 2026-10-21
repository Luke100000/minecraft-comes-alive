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
import net.minecraft.core.registries.Registries;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Regressions for climb routes that do not fit beneath solid ceilings. */
@PrefixGameTestTemplate(false)
public final class CaveVineNavigationGameTests {
    private CaveVineNavigationGameTests() {
    }

    @GameTest(batch = "mca_reported_bridge_saved_post", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 300)
    public static void savedBridgePostDoesNotPullVillagerIntoWallCap(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        assertSavedPostRoute(helper, false);
    }

    @GameTest(batch = "mca_reported_bridge_saved_recovery", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 300)
    public static void savedHoveringVillagerCanLeaveVineForBridgeDeck(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        assertSavedPostRoute(helper, true);
    }

    @GameTest(batch = "mca_reported_bridge_partial_roof", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 300)
    public static void unreachableRoofDoesNotLeaveVillagerSuspendedOnDecorativeVine(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        assertSavedPostRoute(helper, false, true);
    }

    private static void assertSavedPostRoute(GameTestHelper helper, boolean recoverHovering)
            throws IOException, CommandSyntaxException {
        assertSavedPostRoute(helper, recoverHovering, false);
    }

    private static void assertSavedPostRoute(GameTestHelper helper, boolean recoverHovering, boolean roofTarget)
            throws IOException, CommandSyntaxException {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(40, 4, 40));
        prepareFlatArea(helper, origin.offset(8, 0, 5), 12, 11);
        StructureTemplate template = new StructureTemplate();
        var resource = level.getServer().getResourceManager()
                .getResourceOrThrow(MCA.locate("gametest/reported_bridge_saved_post.snbt"));
        try (var input = resource.open()) {
            template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK),
                    TagParser.parseCompoundFully(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
        helper.assertTrue(template.placeInWorld(level, origin, origin, new StructurePlaceSettings(),
                level.getRandom(), 2), "saved bridge post was not placed");
        helper.assertTrue(level.getBlockState(origin.offset(4, 4, 7)).is(Blocks.STONE_BRICK_WALL)
                        && level.getBlockState(origin.offset(4, 3, 6)).is(Blocks.CAVE_VINES),
                "saved wall cap and adjacent glow berries must be intact");
        Vec3 start = Vec3.atLowerCornerOf(origin).add(recoverHovering
                ? new Vec3(4.500688208112D, 4.23789775371552D, 6.4999900665656D)
                : new Vec3(5.5D, 3.0D, 6.5D));
        BlockPos target = roofTarget ? origin.offset(4, 7, 6)
                : origin.offset(8, 3, recoverHovering ? 5 : 6);
        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0).withGender(Gender.FEMALE).withPosition(start).spawn(EntitySpawnReason.STRUCTURE);
        villager.getTraits().removeTrait(Traits.DWARFISM);
        villager.getGenetics().setGene(Genetics.SIZE, 0.57306147F);
        villager.getGenetics().setGene(Genetics.WIDTH, 0.43695217F);
        villager.refreshDimensions();
        villager.refreshBrain(level);
        villager.getBrain().removeAllBehaviors();
        villager.setOnGround(!recoverHovering);
        helper.assertTrue(level.noCollision(villager), "saved post approach must start clear");
        Path path = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(path != null && (roofTarget || path.canReach()),
                "saved post target needs a usable route: " + summarizePath(path));
        if (roofTarget) {
            helper.assertTrue(!path.canReach() && path.getEndNode() != null
                            && level.getBlockState(path.getEndNode().asBlockPos()).is(BlockTags.CLIMBABLE),
                    "roof fixture must produce a partial route ending on a vine: " + summarizePath(path));
        }
        villager.getNavigation().moveTo(path, 0.6D);
        String planned = summarizePath(path);
        int[] ticks = {0};
        helper.onEachTick(() -> {
            boolean arrived = villager.distanceToSqr(Vec3.atBottomCenterOf(target)) < 1.0D;
            boolean safelyStoppedAtPartialRoute = roofTarget && villager.getNavigation().isDone()
                    && !villager.onClimbable() && level.noCollision(villager);
            if ((arrived || safelyStoppedAtPartialRoute) && villager.onGround()) {
                villager.discard();
                helper.succeed();
            } else if (++ticks[0] >= 260) {
                String error = "saved bridge post stalled: pos=" + villager.position()
                        + ", delta=" + villager.getDeltaMovement() + ", planned=" + planned
                        + ", current=" + summarizePath(villager.getNavigation().getPath());
                villager.discard();
                helper.fail(error);
            }
        });
    }

    @GameTest(batch = "mca_reported_bridge_entrance", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void reportedBridgeEntranceDoesNotTrapVillagerOnDecorativeVine(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        assertReportedBridgeCrossing(helper, 0);
    }

    @GameTest(batch = "mca_reported_bridge_vine_start", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void reportedBridgePartialSlabVineStartCanReachDeck(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        assertReportedBridgeCrossing(helper, 1);
    }

    @GameTest(batch = "mca_reported_bridge_diagonal", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void reportedBridgeDiagonalApproachDoesNotClimbPost(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        assertReportedBridgeCrossing(helper, 2);
    }

    @GameTest(batch = "mca_reported_bridge_departure", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void reportedBridgeDeparturePastPostVineDoesNotClimb(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        assertReportedBridgeCrossing(helper, 3);
    }

    private static void assertReportedBridgeCrossing(GameTestHelper helper, int scenario)
            throws IOException, CommandSyntaxException {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(40, 3, 40));
        prepareFlatArea(helper, origin.offset(4, 0, 2), 7, 8);
        // Exact block states from bridge.schem (26.3), min=(4995,63,-957).
        // The reported (4998,65,-956) is the post vine at relative (3,2,1).
        StructureTemplate template = new StructureTemplate();
        var resource = level.getServer().getResourceManager()
                .getResourceOrThrow(MCA.locate("gametest/reported_bridge.snbt"));
        try (var input = resource.open()) {
            template.load(level.registryAccess().lookupOrThrow(Registries.BLOCK),
                    TagParser.parseCompoundFully(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
        template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), 2);
        Vec3 start = Vec3.atLowerCornerOf(origin).add(switch (scenario) {
            case 1 -> new Vec3(3.5D, 1.5D, 1.5D);
            case 2 -> new Vec3(5.5D, 0.5D, 3.0D);
            case 3 -> new Vec3(1.5D, 2.0D, 1.05D);
            default -> new Vec3(5.5D, 0.5D, 1.5D);
        });
        BlockPos target = origin.offset(scenario == 3 ? 5 : 0, scenario == 3 ? 1 : 2, 1);
        VillagerEntityMCA villager = VillagerFactory.newVillager(level)
                .withAge(0).withGender(Gender.FEMALE).withPosition(start)
                .spawn(EntitySpawnReason.STRUCTURE);
        villager.getTraits().removeTrait(Traits.DWARFISM);
        villager.getGenetics().setGene(Genetics.SIZE, 0.5F);
        villager.getGenetics().setGene(Genetics.WIDTH, 0.5F);
        villager.refreshDimensions();
        villager.refreshBrain(level);
        villager.getBrain().removeAllBehaviors();
        villager.setOnGround(true);
        helper.assertTrue(level.noCollision(villager), "reported bridge start overlaps a block: " + start);
        Path path = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(path != null && path.canReach(),
                "reported bridge deck needs a reachable stair route: " + summarizePath(path));
        helper.assertTrue(villager.getNavigation().moveTo(path, 0.6D), "reported bridge route could not start");
        String planned = summarizePath(path);
        int[] elapsed = {0};
        double[] highestY = {start.y};
        helper.onEachTick(() -> {
            highestY[0] = Math.max(highestY[0], villager.getY());
            if (villager.distanceToSqr(Vec3.atBottomCenterOf(target)) < 0.6D && villager.onGround()) {
                villager.discard();
                helper.succeed();
            } else if (++elapsed[0] >= 210) {
                String error = "reported bridge entrance stalled: pos=" + villager.position()
                        + ", highestY=" + highestY[0] + ", climbable=" + villager.onClimbable()
                        + ", horizontalCollision=" + villager.horizontalCollision
                        + ", climbControl=" + ((MCAGroundPathNavigation) villager.getNavigation())
                        .isControllingClimbableMovement() + ", planned=" + planned
                        + ", current=" + summarizePath(villager.getNavigation().getPath());
                villager.discard();
                helper.fail(error);
            }
        });
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
                .spawn(EntitySpawnReason.STRUCTURE);
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
                .spawn(EntitySpawnReason.STRUCTURE);
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
