package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.gametest.GameTestTerrain;
import net.conczin.mca.registry.EntitiesMCA;
import net.conczin.mca.util.WorldUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.registries.RegisterEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Real navigation and jump observations; the probe subclasses always invoke vanilla/MCA behavior. */
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = MCA.MOD_ID)
public final class StairSteppingGameTests {
    private static final String BATCH = "mca_stair_stepping";
    private static final int OBSERVATION_TICKS = 220;
    private static final List<Villager> ACTORS = new ArrayList<>();
    private static final Set<ChunkPos> FORCED_CHUNKS = new HashSet<>();
    private static Block customStep;

    private StairSteppingGameTests() { }

    @SubscribeEvent
    public static void registerStepProxy(RegisterEvent event) {
        if (event.getRegistryKey() != Registries.BLOCK || !Boolean.getBoolean("mca.gametest.enabled")) return;
        event.register(Registries.BLOCK, MCA.locate("gametest_irregular_step"), () -> {
            VoxelShape shape = Shapes.or(Shapes.box(0, 0, 0, 1, 0.375D, 1),
                    Shapes.box(0.5D, 0, 0, 1, 0.875D, 1));
            customStep = new Block(BlockBehaviour.Properties.of().strength(1.0F).noOcclusion()) {
                @Override
                public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
                    return shape;
                }

                @Override
                protected boolean isPathfindable(BlockState state, PathComputationType type) {
                    return false;
                }
            };
            return customStep;
        });
    }

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        ACTORS.forEach(Villager::discard);
        ACTORS.clear();
        FORCED_CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x, chunk.z, false));
        FORCED_CHUNKS.clear();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void flatGroundDoesNotNeedJumping(GameTestHelper helper) {
        compare(helper, Layout.FLAT);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void singleBottomSlabDoesNotNeedJumping(GameTestHelper helper) {
        compare(helper, Layout.SINGLE_SLAB);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void halfBlockSlabStaircaseDoesNotNeedJumping(GameTestHelper helper) {
        compare(helper, Layout.HALF_STEP_SLABS);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void singleStairDoesNotNeedJumping(GameTestHelper helper) {
        compare(helper, Layout.SINGLE_STAIR);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void stairsRisingOneYPerXDoNotNeedJumping(GameTestHelper helper) {
        compare(helper, Layout.STAIRCASE);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void stairsBelowDoorwayLintelDoNotNeedJumping(GameTestHelper helper) {
        compare(helper, Layout.LINTEL_STAIRS);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void slabsRisingOneYPerXRetainRequiredJumps(GameTestHelper helper) {
        compare(helper, Layout.FULL_RISE_SLABS);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void fullBlockStaircaseRetainsRequiredJumps(GameTestHelper helper) {
        compare(helper, Layout.FULL_BLOCKS);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void slowStaircaseDoesNotNeedJumping(GameTestHelper helper) {
        compare(helper, Layout.STAIRCASE, 0.3D);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void normalWalkingStaircaseDoesNotNeedJumping(GameTestHelper helper) {
        compare(helper, Layout.STAIRCASE, 0.5D);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void slowSingleSlabDoesNotNeedJumping(GameTestHelper helper) {
        compare(helper, Layout.SINGLE_SLAB, 0.3D);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void slowHalfBlockSlabStaircaseDoesNotNeedJumping(GameTestHelper helper) {
        compare(helper, Layout.HALF_STEP_SLABS, 0.3D);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void slowStairsBelowDoorwayLintelDoNotNeedJumping(GameTestHelper helper) {
        compare(helper, Layout.LINTEL_STAIRS, 0.3D);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void arbitraryStepShapeDoesNotNeedJumping(GameTestHelper helper) {
        helper.assertTrue(customStep != null, "Step proxy must be registered");
        compare(helper, Layout.CUSTOM_STEP, 0.3D);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void tallBackOfStairRetainsRequiredJump(GameTestHelper helper) {
        compare(helper, Layout.REVERSED_STAIR, 0.3D);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void exactAdjacentFullBlockStillRequestsJump(GameTestHelper helper) {
        assertRaisedBoundaryJump(helper, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void unsupportedForwardProbeDoesNotSuppressRequiredJump(GameTestHelper helper) {
        assertRaisedBoundaryJump(helper, true);
    }

    private static void assertRaisedBoundaryJump(GameTestHelper helper, boolean gap) {
        prepareArena(helper);
        BlockPos base = helper.absolutePos(new BlockPos(20, 5, 20));
        Actor actor = spawn(helper, base, Layout.FLAT, 0.6D, true);
        if (gap) {
            helper.getLevel().setBlock(base.east().below(), Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(base.east(2), Blocks.STONE.defaultBlockState(), 3);
            // The live body retains support on the lip, but a quarter-block
            // forward probe would leave it over the gap before reaching the rise.
            actor.entity.setPos(Vec3.atBottomCenterOf(base).add(0.7D, 0, 0));
        } else {
            helper.getLevel().setBlock(base.east(), Blocks.STONE.defaultBlockState(), 3);
        }
        helper.assertTrue(helper.getLevel().noBlockCollision(actor.entity, actor.entity.getBoundingBox())
                        && !helper.getLevel().noBlockCollision(actor.entity,
                        actor.entity.getBoundingBox().move(0, -0.01D, 0)),
                "Adjacent-jump actor must be clear and physically supported");
        actor.entity.getNavigation().stop();
        actor.entity.getMoveControl().setWantedPosition(actor.entity.getX() + 1.0D,
                actor.entity.getY() + 1.0D, actor.entity.getZ(), 0.6D);
        actor.entity.getMoveControl().tick();
        helper.assertTrue(actor.metrics.requests == 1,
                "A required adjacent jump was deferred: gap=" + gap + " " + actor.brief());
        actor.entity.discard();
        helper.succeed();
    }

    private static void compare(GameTestHelper helper, Layout layout) {
        compare(helper, layout, 0.6D);
    }

    private static void compare(GameTestHelper helper, Layout layout, double speed) {
        prepareArena(helper);
        Actor mca = spawn(helper, helper.absolutePos(new BlockPos(20, 5, 20)), layout, speed, true);
        Actor vanilla = spawn(helper, helper.absolutePos(new BlockPos(20, 5, 32)), layout, speed, false);
        boolean[] reported = {false};
        helper.onEachTick(() -> {
            mca.observe();
            vanilla.observe();
            if (!reported[0] && (mca.reached && vanilla.reached || helper.getTick() >= OBSERVATION_TICKS)) {
                reported[0] = true;
                MCA.LOGGER.info("[MCA Stair Probe] layout={} actor=mca {}", layout, mca.summary());
                MCA.LOGGER.info("[MCA Stair Probe] layout={} actor=vanilla {}", layout, vanilla.summary());
                String evidence = layout + " MCA=" + mca.brief() + " vanilla=" + vanilla.brief();
                helper.assertTrue(mca.reached && vanilla.reached, "Both actors must reach the raised destination: " + evidence);
                if (layout.requiresJump) {
                    helper.assertTrue(mca.metrics.airborneTakeoffs > 0 && vanilla.metrics.airborneTakeoffs > 0,
                            "Full-block rises must retain real jumping: " + evidence);
                } else {
                    helper.assertTrue(mca.metrics.jumpStarts == 0, "Stepable terrain must not trigger MCA jumps: " + evidence);
                }
                helper.succeed();
            }
        });
    }

    private static void prepareArena(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos center = helper.absolutePos(new BlockPos(25, 5, 26));
        ChunkPos min = new ChunkPos(center.offset(-11, 0, -11));
        ChunkPos max = new ChunkPos(center.offset(11, 0, 11));
        for (int x = min.x; x <= max.x; x++) {
            for (int z = min.z; z <= max.z; z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!level.getForcedChunks().contains(chunk.toLong())) FORCED_CHUNKS.add(chunk);
            }
        }
        GameTestTerrain.prepareFlatArea(helper, center, 11, 12);
    }

    private static Actor spawn(GameTestHelper helper, BlockPos base, Layout layout, double speed, boolean mca) {
        ServerLevel level = helper.getLevel();
        for (int x = -1; x <= 10; x++) {
            double height = layout.heightAt(x);
            for (int z = -1; z <= 1; z++) {
                for (int y = 0; y < (int) Math.floor(height); y++) {
                    level.setBlock(base.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
                }
                if (height != Math.floor(height)) {
                    level.setBlock(base.offset(x, (int) Math.floor(height), z), Blocks.STONE_SLAB.defaultBlockState(), 3);
                }
                if (layout.isStairColumn(x)) {
                    level.setBlock(base.offset(x, x - 2, z), Blocks.COBBLESTONE_STAIRS.defaultBlockState()
                            .setValue(StairBlock.FACING,
                                    layout == Layout.REVERSED_STAIR ? Direction.WEST : Direction.EAST), 3);
                }
                if (layout == Layout.CUSTOM_STEP && x == 2) {
                    level.setBlock(base.offset(x, 0, z), customStep.defaultBlockState(), 3);
                }
            }
            // Keep the admitted route on the steps; no alternate path around their sides.
            for (int y = 0; y < 10; y++) {
                level.setBlock(base.offset(x, y, -2), Blocks.STONE.defaultBlockState(), 3);
                level.setBlock(base.offset(x, y, 2), Blocks.STONE.defaultBlockState(), 3);
            }
            if (layout == Layout.LINTEL_STAIRS && x >= 3) {
                for (int z = -1; z <= 1; z++) {
                    level.setBlock(base.offset(x, 4, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        Metrics metrics = new Metrics();
        Villager actor = mca ? new ObservedMcaVillager(level, metrics) : new ObservedVanillaVillager(level, metrics);
        ACTORS.add(actor);
        actor.addTag("doorGhost"); // Preserve the vanilla control through MCA conversion hooks.
        actor.setAge(0);
        actor.setPos(Vec3.atBottomCenterOf(base));
        WorldUtils.spawnEntity(level, actor, MobSpawnType.STRUCTURE);
        if (actor instanceof VillagerEntityMCA villager) {
            villager.getTraits().removeTrait(Traits.DWARFISM);
            villager.getGenetics().setGene(Genetics.SIZE, 0.5F);
            villager.getGenetics().setGene(Genetics.WIDTH, 0.5F);
            villager.refreshDimensions();
            villager.refreshBrain(level);
        }
        actor.getBrain().stopAll(level, actor);
        actor.getBrain().removeAllBehaviors();
        actor.setOnGround(true);
        actor.setNoAi(false);
        helper.assertTrue(level.isPositionEntityTicking(base) && level.noBlockCollision(actor, actor.getBoundingBox())
                        && !level.noBlockCollision(actor, actor.getBoundingBox().move(0, -0.01D, 0)),
                "Probe actor must be ticking, clear, and supported");
        Vec3 goal = new Vec3(base.getX() + 8.5D, base.getY() + layout.heightAt(8), base.getZ() + 0.5D);
        Path path = actor.getNavigation().createPath(BlockPos.containing(goal.x, Math.ceil(goal.y), goal.z), 0);
        boolean admitted = path != null && path.canReach() && actor.getNavigation().moveTo(path, speed);
        metrics.origin = Vec3.atBottomCenterOf(base);
        return new Actor(actor, metrics, goal, speed, admitted);
    }

    private enum Layout {
        FLAT(false), SINGLE_SLAB(false), HALF_STEP_SLABS(false), SINGLE_STAIR(false),
        STAIRCASE(false), LINTEL_STAIRS(false), FULL_RISE_SLABS(true), FULL_BLOCKS(true),
        CUSTOM_STEP(false), REVERSED_STAIR(true);

        final boolean requiresJump;

        Layout(boolean requiresJump) { this.requiresJump = requiresJump; }

        boolean isStairColumn(int x) {
            return x >= 2 && x <= (this == SINGLE_STAIR || this == REVERSED_STAIR ? 2 : this == LINTEL_STAIRS ? 3 : 5)
                    && (this == SINGLE_STAIR || this == STAIRCASE || this == LINTEL_STAIRS || this == REVERSED_STAIR);
        }

        double heightAt(int x) {
            if (x < 2) return 0.0D;
            return switch (this) {
                case FLAT -> 0.0D;
                case SINGLE_SLAB -> 0.5D;
                case HALF_STEP_SLABS -> Math.min(x - 1, 6) * 0.5D;
                case SINGLE_STAIR, CUSTOM_STEP, REVERSED_STAIR -> 1.0D;
                case STAIRCASE, FULL_BLOCKS -> Math.min(x - 1, 4);
                case LINTEL_STAIRS -> Math.min(x - 1, 2);
                case FULL_RISE_SLABS -> Math.min(x - 2, 3) + 0.5D;
            };
        }
    }

    private static final class Actor {
        final Villager entity;
        final Metrics metrics;
        final Vec3 goal;
        final double speed;
        final boolean admitted;
        boolean reached;
        boolean wasGrounded = true;

        Actor(Villager entity, Metrics metrics, Vec3 goal, double speed, boolean admitted) {
            this.entity = entity;
            this.metrics = metrics;
            this.goal = goal;
            this.speed = speed;
            this.admitted = admitted;
        }

        void observe() {
            if (wasGrounded && !entity.onGround() && entity.getDeltaMovement().y > 0.05D) {
                metrics.airborneTakeoffs++;
            }
            wasGrounded = entity.onGround();
            if (!reached && entity.onGround() && entity.distanceToSqr(goal) < 0.5D
                    && Math.abs(entity.getY() - goal.y) < 0.125D) {
                reached = true;
                entity.getNavigation().stop();
            }
        }

        String brief() {
            return "speed=" + speed + " reached=" + reached + " admitted=" + admitted + " requests=" + metrics.requests
                    + " jumpStarts=" + metrics.jumpStarts + " airborneTakeoffs=" + metrics.airborneTakeoffs
                    + " position=" + entity.position().subtract(metrics.origin)
                    + " stepHeight=" + entity.maxUpStep() + " width=" + entity.getBbWidth()
                    + " height=" + entity.getBbHeight();
        }

        String summary() {
            return brief() + " requestsAt=" + metrics.trace;
        }
    }

    private static final class Metrics {
        Vec3 origin = Vec3.ZERO;
        int requests;
        int jumpStarts;
        int airborneTakeoffs;
        final List<String> trace = new ArrayList<>();

        void requested(Mob actor) {
            requests++;
            if (trace.size() < 8) {
                BlockPos pos = actor.blockPosition();
                BlockState state = actor.level().getBlockState(pos);
                trace.add("pos=" + actor.position().subtract(origin) + " wanted="
                        + new Vec3(actor.getMoveControl().getWantedX(), actor.getMoveControl().getWantedY(),
                        actor.getMoveControl().getWantedZ()).subtract(origin) + " feetBlock=" + state);
            }
        }
    }

    private static final class ObservingJumpControl extends JumpControl {
        private final Mob actor;
        private final Metrics metrics;

        ObservingJumpControl(Mob actor, Metrics metrics) {
            super(actor);
            this.actor = actor;
            this.metrics = metrics;
        }

        @Override
        public void jump() {
            metrics.requested(actor);
            super.jump();
        }
    }

    private static final class ObservedMcaVillager extends VillagerEntityMCA {
        private final Metrics metrics;

        ObservedMcaVillager(ServerLevel level, Metrics metrics) {
            super(EntitiesMCA.MALE_VILLAGER, level, Gender.MALE);
            this.metrics = metrics;
            this.jumpControl = new ObservingJumpControl(this, metrics);
        }

        @Override
        public void jumpFromGround() {
            metrics.jumpStarts++;
            super.jumpFromGround();
        }
    }

    private static final class ObservedVanillaVillager extends Villager {
        private final Metrics metrics;

        ObservedVanillaVillager(ServerLevel level, Metrics metrics) {
            super(EntityType.VILLAGER, level);
            this.metrics = metrics;
            this.jumpControl = new ObservingJumpControl(this, metrics);
        }

        @Override
        public void jumpFromGround() {
            metrics.jumpStarts++;
            super.jumpFromGround();
        }
    }
}
