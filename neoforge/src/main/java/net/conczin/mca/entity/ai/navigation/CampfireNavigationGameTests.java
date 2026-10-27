package net.conczin.mca.entity.ai.navigation;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.gametest.GameTestTerrain;
import net.minecraft.core.BlockPos;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
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
        villager.setInvulnerable(true);
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
