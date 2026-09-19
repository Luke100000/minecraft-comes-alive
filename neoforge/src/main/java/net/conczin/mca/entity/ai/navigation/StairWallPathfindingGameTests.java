package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class StairWallPathfindingGameTests {
    private static final int ROUTE_TIMEOUT_TICKS = 140;

    private StairWallPathfindingGameTests() {
    }

    @GameTest(batch = "mca_stair_wall_across_wall_south_north", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 200)
    public static void naturalSouthNorthRouteAroundRaisedWallReachesTarget(GameTestHelper helper) {
        Fixture fixture = prepareFixture(helper);
        runBrainRoute(helper, fixture.stair().south(3), fixture.stair().north(3), "south-north");
    }

    @GameTest(batch = "mca_stair_wall_across_wall_north_south", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 200)
    public static void naturalNorthSouthRouteAroundRaisedWallReachesTarget(GameTestHelper helper) {
        Fixture fixture = prepareFixture(helper);
        runBrainRoute(helper, fixture.stair().north(3), fixture.stair().south(3), "north-south");
    }

    @GameTest(batch = "mca_stair_wall_ascent_guard", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 200)
    public static void naturalStairAscentBesideWallReachesRaisedLanding(GameTestHelper helper) {
        Fixture fixture = prepareFixture(helper);
        prepareRaisedLanding(helper, fixture.stair());
        runBrainRoute(helper, fixture.stair().west(2), fixture.stair().east(2).above(), "stair-ascent");
    }

    @GameTest(batch = "mca_stair_wall_descent_guard", templateNamespace = "mca",
            template = "gametest/copied_open_house", timeoutTicks = 200)
    public static void naturalStairDescentBesideWallReachesLowerFloor(GameTestHelper helper) {
        Fixture fixture = prepareFixture(helper);
        prepareRaisedLanding(helper, fixture.stair());
        runBrainRoute(helper, fixture.stair().east(2).above(), fixture.stair().west(2), "stair-descent");
    }

    private static void runBrainRoute(GameTestHelper helper, BlockPos start, BlockPos target, String label) {
        VillagerEntityMCA villager = spawnVillager(helper, start, "Stair Wall " + label);
        villager.refreshBrain(helper.getLevel());
        villager.getBrain().eraseMemory(MemoryModuleType.PATH);
        villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        villager.getBrain().setMemory(
                MemoryModuleType.WALK_TARGET,
                new WalkTarget(Vec3.atBottomCenterOf(target), 0.65F, 0)
        );
        villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);

        int[] ticks = {0};
        String[] firstPath = {null};
        helper.onEachTick(() -> {
            ticks[0]++;
            Path path = villager.getNavigation().getPath();
            if (firstPath[0] == null && path != null) {
                firstPath[0] = describePath(path);
            }

            if (villager.distanceToSqr(Vec3.atBottomCenterOf(target)) < 2.25D) {
                villager.discard();
                helper.succeed();
                return;
            }

            if (ticks[0] >= ROUTE_TIMEOUT_TICKS) {
                Vec3 position = villager.position();
                boolean horizontalCollision = villager.horizontalCollision;
                String currentPath = describePath(villager.getNavigation().getPath());
                villager.discard();
                helper.fail("stair/wall route stalled; route=" + label
                        + ", pos=" + position
                        + ", horizontalCollision=" + horizontalCollision
                        + ", firstPath=" + firstPath[0]
                        + ", currentPath=" + currentPath);
            }
        });
    }

    private static void prepareRaisedLanding(GameTestHelper helper, BlockPos stair) {
        helper.getLevel().setBlock(stair.east(), Blocks.COBBLESTONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(stair.east(2), Blocks.COBBLESTONE.defaultBlockState(), 3);
    }

    private static Fixture prepareFixture(GameTestHelper helper) {
        BlockPos feet = helper.absolutePos(new BlockPos(5, 2, 5));
        for (int x = feet.getX() - 3; x <= feet.getX() + 4; x++) {
            for (int z = feet.getZ() - 3; z <= feet.getZ() + 3; z++) {
                helper.getLevel().setBlock(new BlockPos(x, feet.getY() - 1, z), Blocks.STONE.defaultBlockState(), 3);
                for (int y = feet.getY(); y <= feet.getY() + 5; y++) {
                    helper.getLevel().setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }

        BlockPos stair = feet.east();
        helper.getLevel().setBlock(stair, Blocks.COBBLESTONE_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.EAST)
                .setValue(StairBlock.HALF, Half.BOTTOM), 3);
        BlockPos wall = stair.north();
        helper.getLevel().setBlock(wall, Blocks.COBBLESTONE_WALL.defaultBlockState(), 3);
        helper.getLevel().setBlock(wall.above(), Blocks.TORCH.defaultBlockState(), 3);
        helper.getLevel().setBlock(stair.above(3), Blocks.COBBLESTONE_STAIRS.defaultBlockState()
                .setValue(StairBlock.FACING, Direction.WEST)
                .setValue(StairBlock.HALF, Half.TOP), 3);
        return new Fixture(stair);
    }

    private static VillagerEntityMCA spawnVillager(GameTestHelper helper, BlockPos feet, String name) {
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(feet))
                .withName(name)
                .spawn(MobSpawnType.STRUCTURE);
        villager.getTraits().removeTrait(Traits.DWARFISM);
        villager.getTraits().removeTrait(Traits.TOUGH);
        villager.getTraits().removeTrait(Traits.WEAK);
        villager.getGenetics().setGene(Genetics.SIZE, 0.5F);
        villager.getGenetics().setGene(Genetics.WIDTH, 0.5F);
        villager.refreshDimensions();
        villager.setOnGround(true);
        return villager;
    }

    private static String describePath(Path path) {
        if (path == null) {
            return "null";
        }

        int shownNodes = Math.min(path.getNodeCount(), 10);
        StringBuilder description = new StringBuilder("next=")
                .append(path.getNextNodeIndex())
                .append('/').append(path.getNodeCount())
                .append(" nodes=");
        for (int index = 0; index < shownNodes; index++) {
            if (index > 0) {
                description.append("->");
            }
            BlockPos node = path.getNodePos(index);
            description.append('(')
                    .append(node.getX()).append(',')
                    .append(node.getY()).append(',')
                    .append(node.getZ()).append(')');
        }
        if (shownNodes < path.getNodeCount()) {
            description.append("->...");
        }
        return description.toString();
    }

    private record Fixture(BlockPos stair) {
    }
}
