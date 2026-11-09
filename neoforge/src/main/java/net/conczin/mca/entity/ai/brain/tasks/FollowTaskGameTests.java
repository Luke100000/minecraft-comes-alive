package net.conczin.mca.entity.ai.brain.tasks;

import com.mojang.authlib.GameProfile;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.ActivitiesMCA;
import net.conczin.mca.entity.ai.ConversationManager;
import net.conczin.mca.entity.ai.MoveState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

@PrefixGameTestTemplate(false)
public final class FollowTaskGameTests {
    private static final String BATCH = "mca_follow_ladder_arrival";
    private static final List<Entity> ACTORS = new ArrayList<>();
    private static final Set<ChunkPos> FORCED_CHUNKS = new HashSet<>();
    private static final Set<BlockPos> COLUMN_BLOCKS = new HashSet<>();

    private FollowTaskGameTests() { }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void followCoreYieldsItsTargetsToActiveDialogue(GameTestHelper helper) {
        ConversationFixture fixture = createConversationFixture(helper, MoveState.FOLLOW);
        VillagerEntityMCA villager = fixture.villager();
        FollowTask follow = new FollowTask();
        long time = helper.getLevel().getGameTime();
        helper.assertTrue(follow.tryStart(helper.getLevel(), villager, time),
                "follow command should start before the dialogue");
        follow.tickOrStop(helper.getLevel(), villager, time);

        villager.getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        helper.assertTrue(villager.getBrain().isActive(ActivitiesMCA.INTERACT),
                "dialogue must own the non-CORE activity");
        WalkTarget dialogueWalk = new WalkTarget(fixture.player(), 0.5F, 2);
        EntityTracker dialogueLook = new EntityTracker(fixture.player(), true);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, dialogueWalk);
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, dialogueLook);

        follow.tickOrStop(helper.getLevel(), villager, time + 1);
        helper.assertTrue(follow.getStatus() == Behavior.Status.STOPPED,
                "running CORE follow must yield when dialogue takes ownership");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null) == dialogueWalk
                        && villager.getBrain().getMemoryInternal(MemoryModuleType.LOOK_TARGET).orElse(null) == dialogueLook,
                "CORE follow must preserve interaction-owned walk and gaze targets");
        villager.getInteractions().stopInteracting(fixture.player(), villager.getInteractions().interactionId());
        helper.assertTrue(follow.tryStart(helper.getLevel(), villager, time + 2),
                "follow command should become eligible again after the dialogue closes");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void stayCoreCannotClearActiveDialogueWalkTarget(GameTestHelper helper) {
        ConversationFixture fixture = createConversationFixture(helper, MoveState.STAY);
        VillagerEntityMCA villager = fixture.villager();
        StayTask stay = new StayTask();
        long time = helper.getLevel().getGameTime();
        helper.assertTrue(stay.tryStart(helper.getLevel(), villager, time),
                "stay command should start before the dialogue");

        villager.getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        helper.assertTrue(villager.getBrain().isActive(ActivitiesMCA.INTERACT),
                "dialogue must own the non-CORE activity");
        WalkTarget dialogueWalk = new WalkTarget(fixture.player(), 0.5F, 2);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, dialogueWalk);

        stay.tickOrStop(helper.getLevel(), villager, time + 1);
        helper.assertTrue(stay.getStatus() == Behavior.Status.STOPPED,
                "running CORE stay must yield when dialogue takes ownership");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null) == dialogueWalk,
                "CORE stay must not clear the conversation's walk destination");
        villager.getInteractions().stopInteracting(fixture.player(), villager.getInteractions().interactionId());
        helper.assertTrue(stay.tryStart(helper.getLevel(), villager, time + 2),
                "stay command should become eligible again after the dialogue closes");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void ambientMessageCorePreservesNewDialogueTargetsWhenStopped(GameTestHelper helper) {
        ConversationFixture fixture = createConversationFixture(helper, MoveState.MOVE);
        VillagerEntityMCA villager = fixture.villager();
        villager.conversationManager.addMessage(new ConversationManager.PhraseText(fixture.player(), "welcome"));
        DeliverMessageTask deliver = new DeliverMessageTask();
        long time = helper.getLevel().getGameTime();
        helper.assertTrue(deliver.tryStart(helper.getLevel(), villager, time),
                "ambient message behavior must have an actual message before interaction");
        deliver.tickOrStop(helper.getLevel(), villager, time);

        villager.getInteractions().interactAt(fixture.player(), Vec3.ZERO, InteractionHand.MAIN_HAND);
        WalkTarget dialogueWalk = new WalkTarget(fixture.player(), 0.5F, 2);
        EntityTracker dialogueLook = new EntityTracker(fixture.player(), true);
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, dialogueWalk);
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, dialogueLook);

        deliver.tickOrStop(helper.getLevel(), villager, time + 1);
        helper.assertTrue(deliver.getStatus() == Behavior.Status.STOPPED,
                "ambient CORE conversation must yield to the active player dialogue");
        helper.assertTrue(villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null) == dialogueWalk
                        && villager.getBrain().getMemoryInternal(MemoryModuleType.LOOK_TARGET).orElse(null) == dialogueLook,
                "ambient message cleanup must preserve the dialogue owner's replacement targets");
        helper.succeed();
    }

    private static ConversationFixture createConversationFixture(GameTestHelper helper, MoveState state) {
        BlockPos base = helper.absolutePos(new BlockPos(16, 1, 16));
        ChunkPos min = new ChunkPos(base.offset(-4, 0, -4));
        ChunkPos max = new ChunkPos(base.offset(4, 0, 4));
        for (int x = min.x; x <= max.x; x++) {
            for (int z = min.z; z <= max.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!helper.getLevel().getForcedChunks().contains(chunk.toLong())) {
                    FORCED_CHUNKS.add(chunk);
                }
            }
        }
        prepareFlatArea(helper, base, 4);
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "core-dialogue"));
        player.setPos(base.getX() + 1.5D, base.getY(), base.getZ() + 0.5D);
        ACTORS.add(player);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel()).withAge(0)
                .withPosition(new Vec3(base.getX() + 0.5D, base.getY(), base.getZ() + 0.5D))
                .spawn(MobSpawnType.STRUCTURE);
        ACTORS.add(villager);
        villager.getVillagerBrain().setMoveState(state, state == MoveState.FOLLOW ? player : null);
        return new ConversationFixture(villager, player);
    }

    private record ConversationFixture(VillagerEntityMCA villager, FakePlayer player) { }

    @AfterBatch(batch = BATCH)
    public static void cleanUp(ServerLevel level) {
        ACTORS.forEach(Entity::discard);
        ACTORS.clear();
        COLUMN_BLOCKS.forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3));
        COLUMN_BLOCKS.clear();
        FORCED_CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
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
        ChunkPos min = new ChunkPos(base.offset(-6, 0, -6));
        ChunkPos max = new ChunkPos(base.offset(6, 0, 6));
        for (int x = min.x; x <= max.x; x++) {
            for (int z = min.z; z <= max.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!helper.getLevel().getForcedChunks().contains(chunk.toLong())) {
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
                .spawn(MobSpawnType.STRUCTURE);
        ACTORS.add(villager);
        villager.getVillagerBrain().setMoveState(MoveState.FOLLOW, player);
        villager.refreshBrain(helper.getLevel());
        villager.setOnGround(false);
        helper.assertTrue(villager.onClimbable(), "follower must begin suspended on the ladder");

        return new LadderFollower(villager, player, target);
    }

    private record LadderFollower(VillagerEntityMCA villager, Player player, BlockPos target) { }
}
