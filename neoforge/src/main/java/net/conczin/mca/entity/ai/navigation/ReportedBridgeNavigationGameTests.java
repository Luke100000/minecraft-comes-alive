package net.conczin.mca.entity.ai.navigation;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.core.BlockPos;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Reported bridge geometry protects both normal crossings and recovery from a suspended vine. */
@GameTestHolder("mca")
@PrefixGameTestTemplate(false)
public final class ReportedBridgeNavigationGameTests {
    private ReportedBridgeNavigationGameTests() {
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
        // Offline world crop: (4988,62,-960)..(5003,70,-950). Street lamps outside
        // the bridge use air; all blocks around the saved vine/roof stall are vanilla.
        var resource = level.getServer().getResourceManager()
                .getResourceOrThrow(MCA.locate("gametest/reported_bridge_saved_post.snbt"));
        try (var input = resource.open()) {
            template.load(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),
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
                : origin.offset(8, 3, recoverHovering ? 5 : 8);
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
        helper.assertTrue(path != null && (!recoverHovering || path.canReach()),
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
            if ((recoverHovering ? villager.distanceToSqr(Vec3.atBottomCenterOf(target)) < 1.0D
                    : villager.getNavigation().isDone()) && villager.onGround()) {
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

    @GameTest(batch = "mca_reported_bridge_close_post", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void reportedBridgeClosePostApproachDoesNotClimb(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        assertReportedBridgeCrossing(helper, 4);
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
            template.load(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),
                    TagParser.parseCompoundFully(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
        helper.assertTrue(template.placeInWorld(level, origin, origin,
                new StructurePlaceSettings(), level.getRandom(), 2), "bridge structure was not placed");
        helper.assertTrue(level.getBlockState(origin.offset(3, 2, 1)).is(Blocks.VINE)
                        && level.getBlockState(origin.offset(3, 3, 0)).is(Blocks.TUFF_BRICK_WALL)
                        && level.getBlockState(origin.offset(0, 1, 1)).is(Blocks.SPRUCE_SLAB),
                "reported bridge palette did not preserve vine, wall cap, and deck");
        Vec3 start = Vec3.atLowerCornerOf(origin).add(switch (scenario) {
            case 1 -> new Vec3(3.5D, 1.5D, 1.5D);
            case 2 -> new Vec3(5.5D, 0.5D, 3.0D);
            case 3 -> new Vec3(1.5D, 2.0D, 1.05D);
            case 4 -> new Vec3(4.5D, 1.0D, 1.35D);
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
