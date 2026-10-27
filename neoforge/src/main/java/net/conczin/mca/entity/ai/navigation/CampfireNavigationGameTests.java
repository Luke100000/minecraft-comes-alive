package net.conczin.mca.entity.ai.navigation;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.brain.tasks.SmarterOpenDoorsTask;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.conczin.mca.gametest.GameTestTerrain;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.TagParser;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@PrefixGameTestTemplate(false)
public final class CampfireNavigationGameTests {
    private static final String BATCH = "mca_campfire_departure";
    private static final List<Villager> ACTORS = new ArrayList<>();
    private static final Set<ChunkPos> FORCED_CHUNKS = new HashSet<>();

    private CampfireNavigationGameTests() { }

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        ACTORS.forEach(Villager::discard);
        ACTORS.clear();
        FORCED_CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x(), chunk.z(), false));
        FORCED_CHUNKS.clear();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 160)
    public static void villagerLeavesExtinguishedCampfire(GameTestHelper helper) {
        assertDeparture(helper, false, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 160)
    public static void villagerLeavesLitCampfire(GameTestHelper helper) {
        assertDeparture(helper, false, true);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 160)
    public static void vanillaVillagerLeavesExtinguishedCampfire(GameTestHelper helper) {
        assertDeparture(helper, true, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 160)
    public static void vanillaVillagerLeavesLitCampfire(GameTestHelper helper) {
        assertDeparture(helper, true, true);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 160)
    public static void villagerCrossesCampfireBridge(GameTestHelper helper) {
        assertDeparture(helper, false, false, true);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 160)
    public static void vanillaCannotRouteAlongBridgeFromEmbeddedCampfireStart(GameTestHelper helper) {
        assertDeparture(helper, true, false, true);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 620)
    public static void reportedWaterloggedTrapdoorSpawnDoesNotCircle(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        ServerLevel level = helper.getLevel();
        BlockPos origin = placeReportedBridge(helper);
        // Sponge v3 crop: (5088,74,-1053), size 10x6x5; reported spawn is (5093,75,-1052).
        // Ecologics gates/fences/signs use equivalent vanilla shapes; decorative mod plants
        // use vanilla counterparts. All saved positions and waterlogged states are retained.
        BlockPos trapdoor = origin.offset(5, 0, 1);
        helper.assertTrue(level.getBlockState(trapdoor).is(Blocks.SPRUCE_TRAPDOOR)
                && level.getBlockState(trapdoor).getValue(TrapDoorBlock.WATERLOGGED)
                && !level.getBlockState(trapdoor).getValue(TrapDoorBlock.OPEN), "reported support must remain waterlogged and closed");
        VillagerEntityMCA villager = VillagerFactory.newVillager(level).withAge(0)
                .withPosition(Vec3.atBottomCenterOf(trapdoor.above())).spawn(EntitySpawnReason.SPAWN_ITEM_USE);
        ACTORS.add(villager);
        helper.assertTrue(!villager.isNoAi() && level.noBlockCollision(villager, villager.getBoundingBox()),
                "normally spawned villager must have active AI and body clearance");
        Vec3[] anchor = {villager.position()};
        float[] yaw = {villager.getYRot()};
        double[] rotation = {0.0D};
        helper.onEachTick(() -> {
            rotation[0] += Math.abs(Mth.wrapDegrees(villager.getYRot() - yaw[0]));
            yaw[0] = villager.getYRot();
            if (villager.position().distanceToSqr(anchor[0]) > 1.0D) {
                villager.discard();
                helper.succeed();
                return;
            }
            if (helper.getTick() % 10 == 0) {
                Path path = villager.getNavigation().getPath();
                System.out.println("REPORTED_TRAPDOOR tick=" + helper.getTick() + " pos=" + villager.position()
                        + " yaw=" + villager.getYRot() + " rotation=" + rotation[0]
                        + " water=" + villager.isInWater() + " ground=" + villager.onGround()
                        + " target=" + villager.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
                                .map(w -> w.getTarget().currentBlockPosition())
                        + " next=" + (path == null || path.isDone() ? "done" : path.getNextNode())
                        + " activity=" + villager.getBrain().getActiveNonCoreActivity());
            }
            helper.assertTrue(rotation[0] < 720.0D, "normally spawned villager circled without leaving reported trapdoor: " + villager.position());
        });
        helper.runAtTickTime(600, () -> {
            helper.assertTrue(villager.tickCount >= 500, "fixture villager must actually tick");
            villager.discard();
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void nikkiCanLeaveReportedBottomTrapdoor(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        assertReportedBottomTrapdoor(helper, 0.3020984F, 0.5797018F);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void jordynCanLeaveReportedBottomTrapdoor(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        assertReportedBottomTrapdoor(helper, 0.70069313F, 0.6897938F);
    }

    private static void assertReportedBottomTrapdoor(GameTestHelper helper, float size, float width)
            throws IOException, CommandSyntaxException {
        ServerLevel level = helper.getLevel();
        BlockPos origin = placeReportedBridge(helper);
        BlockPos trapdoor = origin.offset(2, 1, 2);
        VillagerEntityMCA villager = VillagerFactory.newVillager(level).withAge(0).withGender(Gender.FEMALE)
                .withPosition(Vec3.atBottomCenterOf(trapdoor).add(0, 3.0D / 16.0D, 0.03D))
                .spawn(EntitySpawnReason.SPAWN_ITEM_USE);
        ACTORS.add(villager);
        villager.getGenetics().setGene(Genetics.SIZE, size);
        villager.getGenetics().setGene(Genetics.WIDTH, width);
        villager.getTraits().removeTrait(Traits.DWARFISM);
        villager.refreshDimensions();
        villager.setOnGround(true);
        villager.getBrain().removeAllBehaviors();
        helper.assertTrue(level.getBlockState(trapdoor).is(Blocks.SPRUCE_TRAPDOOR)
                && !level.getBlockState(trapdoor).getValue(TrapDoorBlock.OPEN)
                && !level.getBlockState(trapdoor).getValue(TrapDoorBlock.WATERLOGGED)
                && level.noBlockCollision(villager, villager.getBoundingBox()), "captured bottom trapdoor pose must be clear");
        BlockPos target = trapdoor.north().above();
        Path path = villager.getNavigation().createPath(target, 0);
        helper.assertTrue(path != null && path.canReach(), "captured villager must be able to step onto adjacent campfire: " + path);
        helper.assertTrue(villager.getNavigation().moveTo(path, 0.65D), "captured villager's departure must start");
        villager.getBrain().setMemory(MemoryModuleType.PATH, path);
        SmarterOpenDoorsTask gates = new SmarterOpenDoorsTask();
        float[] yaw = {villager.getYRot()};
        double[] rotation = {0.0D};
        helper.onEachTick(() -> {
            if (gates.getStatus() == Behavior.Status.RUNNING) {
                gates.tickOrStop(level, villager, level.getGameTime());
            } else {
                gates.tryStart(level, villager, level.getGameTime());
            }
            rotation[0] += Math.abs(Mth.wrapDegrees(villager.getYRot() - yaw[0]));
            yaw[0] = villager.getYRot();
            helper.assertTrue(!level.getBlockState(trapdoor).getValue(TrapDoorBlock.OPEN), "walkable support must stay closed");
            if (villager.getZ() < trapdoor.getZ() && villager.getY() > trapdoor.getY() + 0.4D && villager.onGround()) {
                villager.discard();
                helper.succeed();
            }
        });
        helper.runAtTickTime(230, () -> helper.fail("captured villager circled or stalled: " + villager.position()
                + ", next=" + path.getNextNodeIndex() + "/" + path.getNodeCount() + ", yawTravel=" + rotation[0]
                + ", first=" + path.getNode(0) + "/" + path.getNode(0).type
                + ", wantedY=" + villager.getMoveControl().getWantedY()));
    }

    private static BlockPos placeReportedBridge(GameTestHelper helper)
            throws IOException, CommandSyntaxException {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(new BlockPos(20, 5, 20));
        ChunkPos min = ChunkPos.containing(origin.offset(-4, 0, -4));
        ChunkPos max = ChunkPos.containing(origin.offset(14, 0, 9));
        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (level.setChunkForced(x, z, true)) FORCED_CHUNKS.add(chunk);
            }
        }
        GameTestTerrain.prepareFlatArea(helper, origin.offset(5, -1, 2), 9, 10);
        for (int x = -3; x <= 12; x++) {
            for (int z = -3; z <= 7; z++) {
                level.setBlock(origin.offset(x, -1, z), Blocks.WATER.defaultBlockState(), 2);
                level.setBlock(origin.offset(x, 0, z), Blocks.WATER.defaultBlockState(), 2);
            }
        }
        StructureTemplate template = new StructureTemplate();
        var resource = level.getServer().getResourceManager().getResourceOrThrow(
                MCA.locate("gametest/reported_waterlogged_trapdoor_bridge.snbt"));
        try (var input = resource.open()) {
            template.load(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK),
                    TagParser.parseCompoundFully(new String(input.readAllBytes(), StandardCharsets.UTF_8)));
        }
        helper.assertTrue(template.placeInWorld(level, origin, origin, new StructurePlaceSettings(),
                level.getRandom(), 2), "reported schematic must be placed");
        return origin;
    }

    private static void assertDeparture(GameTestHelper helper, boolean vanilla, boolean lit) {
        assertDeparture(helper, vanilla, lit, false);
    }

    private static void assertDeparture(GameTestHelper helper, boolean vanilla, boolean lit, boolean bridge) {
        ServerLevel level = helper.getLevel();
        BlockPos campfire = helper.absolutePos(new BlockPos(20, 5, 20));
        ChunkPos min = ChunkPos.containing(campfire.offset(-6, 0, -6));
        ChunkPos max = ChunkPos.containing(campfire.offset(6, 0, 6));
        for (int x = min.x(); x <= max.x(); x++) {
            for (int z = min.z(); z <= max.z(); z++) {
                ChunkPos chunk = new ChunkPos(x, z);
                if (level.setChunkForced(x, z, true)) FORCED_CHUNKS.add(chunk);
            }
        }
        GameTestTerrain.prepareFlatArea(helper, bridge ? campfire.below(3) : campfire, 6, 7);
        for (int x = 0; x <= (bridge ? 4 : 0); x++) {
            level.setBlock(campfire.east(x), Blocks.CAMPFIRE.defaultBlockState().setValue(BlockStateProperties.LIT, lit), 3);
        }
        Vec3 start = Vec3.atBottomCenterOf(campfire).add(0, 7.0D / 16.0D, 0);
        Villager villager;
        if (vanilla) {
            villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.STRUCTURE);
            helper.assertTrue(villager != null, "vanilla villager must spawn");
            villager.addTag("doorGhost");
            villager.setPos(start);
            level.addFreshEntity(villager);
        } else {
            VillagerEntityMCA mca = VillagerFactory.newVillager(level)
                    .withAge(0).withPosition(start).spawn(EntitySpawnReason.STRUCTURE);
            mca.getTraits().removeTrait(Traits.DWARFISM);
            mca.getGenetics().setGene(Genetics.SIZE, 0.5F);
            mca.getGenetics().setGene(Genetics.WIDTH, 0.5F);
            mca.refreshDimensions();
            mca.refreshBrain(level);
            villager = mca;
        }
        ACTORS.add(villager);
        villager.getBrain().removeAllBehaviors();
        villager.setOnGround(true);
        villager.setPermanentlyInvulnerable(true);
        helper.assertTrue(level.noBlockCollision(villager, villager.getBoundingBox()),
                "villager must start on the campfire surface");
        BlockPos destination = bridge ? campfire.east(4).above() : campfire.east(4);
        Path path = villager.getNavigation().createPath(destination, 0);
        helper.assertTrue(path != null, "campfire departure must produce a path");
        if (vanilla && bridge) {
            helper.assertTrue(!path.canReach() && path.getNodePos(0).equals(campfire),
                    "vanilla control must reproduce the embedded start and incomplete bridge route");
            villager.discard();
            helper.succeed();
            return;
        }
        helper.assertTrue(path.canReach(), "campfire departure must reach its destination: " + path);
        if (bridge) {
            helper.assertTrue(path.getNodePos(0).equals(campfire.above()),
                    "bridge route must start above its supporting block: " + path.getNode(0));
        }
        helper.assertTrue(villager.getNavigation().moveTo(path, 0.6D), "departure path must start");
        helper.onEachTick(() -> {
            helper.assertTrue(!villager.isRemoved(), "departure actor must not be converted or removed");
            if (bridge) helper.assertTrue(villager.getY() >= campfire.getY(),
                    "villager must cross on the bridge instead of dropping below it: " + villager.position());
            if (villager.getX() > destination.getX() && villager.onGround()
                    && (!bridge || Math.abs(villager.getY() - start.y) < 0.125D)) {
                villager.discard();
                helper.succeed();
            }
        });
        helper.runAtTickTime(150, () -> helper.assertTrue(false,
                "villager failed to leave campfire: pos=" + villager.position()
                        + ", next=" + path.getNextNodeIndex() + "/" + path.getNodeCount()
                        + ", wantedY=" + villager.getMoveControl().getWantedY()));
    }
}
