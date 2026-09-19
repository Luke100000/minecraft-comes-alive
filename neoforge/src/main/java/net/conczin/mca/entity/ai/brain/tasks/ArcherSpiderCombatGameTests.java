package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.CaveSpider;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class ArcherSpiderCombatGameTests {
    private static final List<Entity> TEST_ENTITIES = new ArrayList<>();

    private ArcherSpiderCombatGameTests() {
    }

    @GameTest(batch = "mca_archer_spider_bow", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 360)
    public static void archerKillsSpiderWithBow(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos archerPos = helper.absolutePos(new BlockPos(8, 22, 8));
        prepareFlatArea(helper, archerPos, 14);

        VillagerEntityMCA archer = spawnArcher(helper, archerPos);
        archer.setNoAi(true);
        Spider spider = spawnSpider(helper, EntityType.SPIDER, archerPos.east(10), true);
        startCombat(archer, spider);

        requireBowTaskKill(helper, archer, spider, 300, "spider");
    }

    @GameTest(batch = "mca_archer_cave_spider_bow", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 360)
    public static void archerKillsCaveSpiderWithBow(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos archerPos = helper.absolutePos(new BlockPos(8, 22, 8));
        prepareFlatArea(helper, archerPos, 14);

        VillagerEntityMCA archer = spawnArcher(helper, archerPos);
        archer.setNoAi(true);
        CaveSpider spider = spawnSpider(helper, EntityType.CAVE_SPIDER, archerPos.east(10), true);
        startCombat(archer, spider);

        requireBowTaskKill(helper, archer, spider, 300, "cave spider");
    }

    @GameTest(batch = "mca_archer_spider_cave_escape", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 500)
    public static void archerEscapesCaveCornerAndKillsPursuingSpider(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos archerPos = helper.absolutePos(new BlockPos(14, 22, 14));
        prepareFlatArea(helper, archerPos, 18);
        buildCaveCorner(helper, archerPos);

        VillagerEntityMCA archer = spawnArcher(helper, archerPos);
        Spider spider = spawnSpider(helper, EntityType.SPIDER, archerPos.east(2), false);
        spider.setTarget(archer);
        startCombat(archer, spider);

        Vec3 origin = archer.position();
        double initialDistanceSquared = archer.distanceToSqr(spider);
        double[] bestDistanceSquared = {initialDistanceSquared};
        double[] furthestProgressSquared = {0.0D};
        boolean[] sawEmergency = {false};
        int[] ticks = {0};

        helper.onEachTick(() -> {
            ticks[0]++;
            bestDistanceSquared[0] = Math.max(bestDistanceSquared[0], archer.distanceToSqr(spider));
            furthestProgressSquared[0] = Math.max(furthestProgressSquared[0], archer.position().distanceToSqr(origin));
            sawEmergency[0] |= RangedCombatState.current(archer).orElse(null) == RangedCombatState.EMERGENCY_FLEE;

            if (!archer.isAlive()) {
                helper.fail("pursuing spider killed the archer before it escaped the cave corner");
                return;
            }

            if (ticks[0] == 60 && bestDistanceSquared[0] < 25.0D) {
                helper.fail("EMERGENCY_FLEE did not open five blocks of spacing within three seconds; bestDistanceSqr="
                        + bestDistanceSquared[0]);
                return;
            }

            if (!spider.isAlive()) {
                helper.assertTrue(sawEmergency[0], "close pursuing spider never triggered EMERGENCY_FLEE");
                helper.assertTrue(
                        bestDistanceSquared[0] >= 25.0D,
                        "archer killed the spider without ever opening useful emergency spacing; bestDistanceSqr="
                                + bestDistanceSquared[0]
                );
                helper.assertTrue(
                        furthestProgressSquared[0] >= 9.0D,
                        "archer never escaped the cave corner before killing the spider; progressSqr="
                                + furthestProgressSquared[0]
                );
                helper.succeed();
                return;
            }

            if (ticks[0] >= 420) {
                helper.fail("archer did not escape the cave corner and kill the pursuing spider; state="
                        + RangedCombatState.current(archer).orElse(null)
                        + ", bestDistanceSqr=" + bestDistanceSquared[0]
                        + ", progressSqr=" + furthestProgressSquared[0]
                        + ", spiderHealth=" + spider.getHealth());
            }
        });
    }

    @GameTest(batch = "mca_archer_spider_cave_route", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void emergencyEscapeRejectsBlockedFarSideForOpenCaveLane(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos archerPos = helper.absolutePos(new BlockPos(14, 22, 14));
        prepareFlatArea(helper, archerPos, 14);

        // The west side looks geometrically ideal because it is directly away from the spider,
        // but a long solid wall makes those candidate cells unreachable. North remains open.
        for (int z = -12; z <= 12; z++) {
            setWallColumn(helper, archerPos.offset(-1, 0, z));
        }

        VillagerEntityMCA archer = spawnArcher(helper, archerPos);
        archer.setNoAi(true);
        Spider spider = spawnSpider(helper, EntityType.SPIDER, archerPos.east(2), true);

        Vec3 destination = RangedCombatPositioning.findEmergencyEscapePosition(
                archer,
                List.of(spider),
                6.0D
        ).orElseThrow(() -> new AssertionError("no emergency escape candidate found beside an open cave lane"));

        helper.assertTrue(
                destination.x >= archer.getX() - 0.5D,
                "emergency escape chose the blocked west side instead of the reachable cave lane: destination="
                        + destination
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_spider_cave_pocket", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void emergencyEscapeAvoidsOneEntryCavePocket(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos archerPos = helper.absolutePos(new BlockPos(14, 22, 14));
        prepareFlatArea(helper, archerPos, 14);
        buildOneEntryCavePocket(helper, archerPos);

        VillagerEntityMCA archer = spawnArcher(helper, archerPos);
        archer.setNoAi(true);
        Spider spider = spawnSpider(helper, EntityType.SPIDER, archerPos.east(2), true);

        Vec3 destination = RangedCombatPositioning.findEmergencyEscapePosition(
                archer,
                List.of(spider),
                6.0D
        ).orElseThrow(() -> new AssertionError("no emergency escape candidate found beside the cave pocket"));

        helper.assertTrue(
                destination.x >= archer.getX() - 2.5D,
                "emergency escape committed deeper into the one-entry cave pocket instead of keeping onward space: destination="
                        + destination
        );
        helper.succeed();
    }

    private static void requireBowTaskKill(
            GameTestHelper helper,
            VillagerEntityMCA archer,
            Spider spider,
            int deadlineTicks,
            String targetName
    ) {
        BowTask<VillagerEntityMCA> bowTask = new BowTask<>(20, 15);
        long startTime = helper.getLevel().getGameTime();
        bowTask.start(helper.getLevel(), archer, startTime);
        int[] ticks = {0};
        helper.onEachTick(() -> {
            ticks[0]++;
            bowTask.tick(helper.getLevel(), archer, startTime + ticks[0]);
            if (!archer.isAlive()) {
                helper.fail("archer died before killing the " + targetName);
                return;
            }
            if (!spider.isAlive()) {
                helper.succeed();
                return;
            }
            if (ticks[0] >= deadlineTicks) {
                helper.fail("archer failed to kill the " + targetName + "; health=" + spider.getHealth()
                        + ", state=" + RangedCombatState.current(archer).orElse(null));
            }
        });
    }

    private static void startCombat(VillagerEntityMCA archer, Spider spider) {
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, spider);
        archer.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, List.of(spider));
    }

    private static VillagerEntityMCA spawnArcher(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().getChunk(pos);
        VillagerEntityMCA archer = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withProfession(ProfessionsMCA.ARCHER)
                .withPosition(Vec3.atBottomCenterOf(pos))
                .withName("Spider Combat Archer")
                .spawn(MobSpawnType.STRUCTURE);
        archer.setItemSlot(EquipmentSlot.MAINHAND, Items.BOW.getDefaultInstance());
        archer.setOnGround(true);
        archer.refreshBrain(helper.getLevel());
        TEST_ENTITIES.add(archer);
        return archer;
    }

    private static <S extends Spider> S spawnSpider(
            GameTestHelper helper,
            EntityType<S> type,
            BlockPos pos,
            boolean noAi
    ) {
        helper.getLevel().getChunk(pos);
        S spider = type.create(helper.getLevel());
        if (spider == null) {
            throw new IllegalStateException("failed to create spider target");
        }
        spider.absMoveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        spider.setNoAi(noAi);
        helper.getLevel().addFreshEntity(spider);
        TEST_ENTITIES.add(spider);
        return spider;
    }

    private static void buildCaveCorner(GameTestHelper helper, BlockPos center) {
        // A roofed chamber with a long north-side exit. The tempting west corner is farther from
        // the spider but is a dead end; a useful flee must keep moving into the open corridor.
        for (int x = -6; x <= 6; x++) {
            for (int z = -6; z <= 6; z++) {
                helper.getLevel().setBlock(center.offset(x, 3, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        for (int z = -6; z <= 6; z++) {
            setWallColumn(helper, center.offset(-4, 0, z));
            if (z != -4) {
                setWallColumn(helper, center.offset(4, 0, z));
            }
        }
        for (int x = -4; x <= 4; x++) {
            setWallColumn(helper, center.offset(x, 0, 4));
            if (x < 1 || x > 3) {
                setWallColumn(helper, center.offset(x, 0, -4));
            }
        }

        // Dead-end pocket west of the archer that greedy distance maximization tends to prefer.
        for (int z = -2; z <= 2; z++) {
            setWallColumn(helper, center.offset(-2, 0, z));
        }
        helper.getLevel().setBlock(center.offset(-2, 0, -2), Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(center.offset(-2, 1, -2), Blocks.AIR.defaultBlockState(), 3);
    }

    private static void buildOneEntryCavePocket(GameTestHelper helper, BlockPos center) {
        for (int x = -7; x <= -1; x++) {
            setWallColumn(helper, center.offset(x, 0, -2));
            setWallColumn(helper, center.offset(x, 0, 2));
        }
        for (int z = -2; z <= 2; z++) {
            setWallColumn(helper, center.offset(-7, 0, z));
        }

        // Make the recess asymmetric so the regression is about one-exit topology, not a 3x3 shape.
        setWallColumn(helper, center.offset(-5, 0, 1));
        setWallColumn(helper, center.offset(-6, 0, 1));
    }

    private static void setWallColumn(GameTestHelper helper, BlockPos feet) {
        helper.getLevel().setBlock(feet, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(feet.above(), Blocks.STONE.defaultBlockState(), 3);
    }

    private static void prepareFlatArea(GameTestHelper helper, BlockPos center, int radius) {
        ChunkPos minChunk = new ChunkPos(center.offset(-radius, 0, -radius));
        ChunkPos maxChunk = new ChunkPos(center.offset(radius, 0, radius));
        for (int chunkX = minChunk.x; chunkX <= maxChunk.x; chunkX++) {
            for (int chunkZ = minChunk.z; chunkZ <= maxChunk.z; chunkZ++) {
                helper.getLevel().setChunkForced(chunkX, chunkZ, true);
            }
        }

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos feet = center.offset(x, 0, z);
                helper.getLevel().setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(feet.above(2), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }

    private static void cleanupTestEntities() {
        TEST_ENTITIES.forEach(Entity::discard);
        TEST_ENTITIES.clear();
    }
}
