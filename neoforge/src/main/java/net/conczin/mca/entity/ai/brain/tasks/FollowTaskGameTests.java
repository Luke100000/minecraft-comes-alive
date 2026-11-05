package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.MoveState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.phys.Vec3;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

@PrefixGameTestTemplate(false)
public final class FollowTaskGameTests {
    private static final String BATCH = "mca_follow_ladder_arrival";
    private static final List<Entity> ACTORS = new ArrayList<>();
    private static final Set<ChunkPos> FORCED_CHUNKS = new HashSet<>();
    private static final Set<BlockPos> COLUMN_BLOCKS = new HashSet<>();

    private FollowTaskGameTests() { }

    @AfterBatch(batch = BATCH)
    public static void cleanUp(ServerLevel level) {
        ACTORS.forEach(Entity::discard);
        ACTORS.clear();
        COLUMN_BLOCKS.forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3));
        COLUMN_BLOCKS.clear();
        FORCED_CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x(), chunk.z(), false));
        FORCED_CHUNKS.clear();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 420)
    public static void stationaryPlayerOnLadderDoesNotMakeFollowerSlideAfterArrival(GameTestHelper helper) {
        LadderFollower fixture = createLadderFollower(helper);
        VillagerEntityMCA villager = fixture.villager();
        BlockPos target = fixture.target();
        long startedAt = helper.getLevel().getGameTime();
        long[] arrivedAt = {-1L};
        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - startedAt;
            assertTicking(helper, villager);
            if (arrivedAt[0] < 0L && villager.getY() >= target.getY()) {
                arrivedAt[0] = elapsed;
            }
            if (arrivedAt[0] >= 0L) {
                helper.assertTrue(villager.getY() >= target.getY() - 0.2D,
                        "follower slid down after ladder arrival: pos=" + villager.position()
                                + "; velocity=" + villager.getDeltaMovement()
                                + "; path=" + villager.getNavigation().getPath()
                                + "; elapsed=" + elapsed + "; arrivedAt=" + arrivedAt[0]);
                // Span both FollowTask's old 60-tick restart and the movement sink's deadline.
                if (elapsed - arrivedAt[0] >= 320L) {
                    helper.succeed();
                }
            }
            helper.assertTrue(elapsed < 80L || arrivedAt[0] >= 0L,
                    "follower never climbed to the stationary player: pos=" + villager.position()
                            + "; path=" + villager.getNavigation().getPath());
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void heldFollowerClimbsWhenPlayerMovesUp(GameTestHelper helper) {
        checkPlayerMovementAfterHold(helper, 2);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void heldFollowerDescendsWhenPlayerMovesDown(GameTestHelper helper) {
        checkPlayerMovementAfterHold(helper, -2);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void cancellingFollowReleasesSuspendedLadderHold(GameTestHelper helper) {
        LadderFollower fixture = createLadderFollower(helper);
        VillagerEntityMCA villager = fixture.villager();
        long startedAt = helper.getLevel().getGameTime();
        long[] arrivedAt = {-1L};
        long[] cancelledAt = {-1L};
        double[] cancelledY = {0.0D};
        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - startedAt;
            assertTicking(helper, villager);
            if (arrivedAt[0] < 0L && villager.getY() >= fixture.target().getY()) {
                arrivedAt[0] = elapsed;
            }
            if (arrivedAt[0] >= 0L && elapsed - arrivedAt[0] >= 40L && cancelledAt[0] < 0L) {
                cancelledAt[0] = elapsed;
                cancelledY[0] = villager.getY();
                villager.getVillagerBrain().setMoveState(MoveState.MOVE, null);
            }
            if (cancelledAt[0] >= 0L && villager.getY() < cancelledY[0] - 0.25D) {
                helper.succeed();
            }
            helper.assertTrue(elapsed < 140L,
                    "cancelled follow command retained ladder hold: pos=" + villager.position()
                            + "; cancelledAt=" + cancelledAt[0] + "; arrivedAt=" + arrivedAt[0]);
        });
    }

    private static void checkPlayerMovementAfterHold(GameTestHelper helper, int verticalOffset) {
        LadderFollower fixture = createLadderFollower(helper);
        VillagerEntityMCA villager = fixture.villager();
        BlockPos newTarget = fixture.target().above(verticalOffset);
        long startedAt = helper.getLevel().getGameTime();
        long[] arrivedAt = {-1L};
        long[] movedAt = {-1L};
        long[] arrivedAgainAt = {-1L};
        helper.onEachTick(() -> {
            long elapsed = helper.getLevel().getGameTime() - startedAt;
            assertTicking(helper, villager);
            if (arrivedAt[0] < 0L && villager.getY() >= fixture.target().getY()) {
                arrivedAt[0] = elapsed;
            }
            if (arrivedAt[0] >= 0L && elapsed - arrivedAt[0] >= 40L && movedAt[0] < 0L) {
                movedAt[0] = elapsed;
                fixture.player().setPos(newTarget.getX() + 0.5D, newTarget.getY() + 0.5D, newTarget.getZ() + 0.4D);
            }
            if (movedAt[0] >= 0L && villager.blockPosition().equals(newTarget)) {
                if (arrivedAgainAt[0] < 0L) {
                    arrivedAgainAt[0] = elapsed;
                }
                if (elapsed - arrivedAgainAt[0] >= 40L) {
                    helper.succeed();
                }
            } else {
                arrivedAgainAt[0] = -1L;
            }
            helper.assertTrue(elapsed < 200L,
                    "follower did not resume and settle after player moved " + verticalOffset
                            + " blocks: pos=" + villager.position() + "; target=" + newTarget
                            + "; path=" + villager.getNavigation().getPath());
        });
    }

    private static void assertTicking(GameTestHelper helper, VillagerEntityMCA villager) {
        helper.assertTrue(helper.getLevel().isPositionEntityTicking(villager.blockPosition()),
                "ladder follower left its ticking fixture");
    }

    private static LadderFollower createLadderFollower(GameTestHelper helper) {
        BlockPos base = helper.absolutePos(new BlockPos(16, 1, 16));
        ChunkPos min = ChunkPos.containing(base.offset(-6, 0, -6));
        ChunkPos max = ChunkPos.containing(base.offset(6, 0, 6));
        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!helper.getLevel().getForceLoadedChunks().contains(chunk.pack())) {
                    FORCED_CHUNKS.add(chunk);
                }
            }
        }
        prepareFlatArea(helper, base, 6, 11);
        for (int y = 0; y < 10; y++) {
            BlockPos rung = base.above(y);
            helper.getLevel().setBlock(rung.south(), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(rung,
                    Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.NORTH), 3);
            COLUMN_BLOCKS.add(rung);
            COLUMN_BLOCKS.add(rung.south());
        }

        BlockPos target = base.above(5);
        // A stationary player's pose is supplied to the real follow behavior. Player input,
        // networking and body pushing are outside this navigation/physics regression.
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(target.getX() + 0.5D, target.getY() + 0.5D, target.getZ() + 0.4D);
        player.setOnGround(false);
        ACTORS.add(player);
        helper.assertTrue(player.onClimbable(), "follow target must be suspended on the ladder");

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel()).withAge(0)
                .withPosition(new Vec3(base.getX() + 0.5D, base.getY() + 2.0D, base.getZ() + 0.4D))
                .spawn(EntitySpawnReason.STRUCTURE);
        ACTORS.add(villager);
        villager.getVillagerBrain().setMoveState(MoveState.FOLLOW, player);
        villager.refreshBrain(helper.getLevel());
        villager.setOnGround(false);
        helper.assertTrue(villager.onClimbable(), "follower must begin suspended on the ladder");

        return new LadderFollower(villager, player, target);
    }

    private record LadderFollower(VillagerEntityMCA villager, Player player, BlockPos target) { }
}
