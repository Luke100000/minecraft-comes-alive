package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.gametest.GameTestTerrain;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.BeforeBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.schedule.Activity;


import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@PrefixGameTestTemplate(false)
public final class ArcherCombatPolicyGameTests {
    private static final String BATCH = "archer_combat_policy";
    private static final List<Arena> ARENAS = new ArrayList<>();
    private static long previousDayTime;

    private ArcherCombatPolicyGameTests() { }

    @BeforeBatch(batch = BATCH)
    public static void prepare(ServerLevel level) {
        previousDayTime = level.clockManager().getTotalTicks(level.dimensionType().defaultClock().orElseThrow());
        level.clockManager().setTotalTicks(level.dimensionType().defaultClock().orElseThrow(), 18000);
    }

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        ARENAS.forEach(Arena::close);
        ARENAS.clear();
        level.clockManager().setTotalTicks(level.dimensionType().defaultClock().orElseThrow(), previousDayTime);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 60)
    public static void firingRouteRejectsNewlyOccupiedThreatLane(GameTestHelper helper) {
        Arena a = new Arena(helper);
        Zombie target = a.zombie(a.start.east(14).above());
        Zombie hazard = a.zombie(a.start.north(8));
        hazard.setTarget(a.guard);
        a.guard.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        a.guard.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, List.of(target, hazard));
        var firing = RangedCombatPositioning.findFiringTarget(a.guard, target, List.of(hazard), 256)
                .orElseThrow(() -> new AssertionError("fixture needs a firing destination"));
        Path original = firing.createPath(a.guard.getNavigation(), a.guard, 0);
        helper.assertTrue(original != null && original.canReach() && original.getNodeCount() >= 3,
                "fixture needs a native route to the firing destination");
        Vec3 intermediate = original.getEntityPosAtNode(a.guard, 2);
        Vec3 outward = intermediate.subtract(a.guard.position()).multiply(1, 0, 1).normalize();
        hazard.setPos(intermediate.add(outward));
        helper.assertTrue(a.guard.distanceTo(hazard) >= 2.5,
                "route hazard must start farther away than the forbidden path clearance");
        Path admitted = firing.createPath(a.guard.getNavigation(), a.guard, 0);
        if (admitted != null) {
            for (int i = admitted.getNextNodeIndex(); i < admitted.getNodeCount(); i++) {
                Vec3 node = admitted.getEntityPosAtNode(a.guard, i);
                if (!BlockPos.containing(node).equals(a.guard.blockPosition())) {
                    helper.assertTrue(node.distanceTo(hazard.position()) >= 1.5,
                            "firing route admitted a step beside an active melee threat: " + node);
                }
            }
        }
        a.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 60)
    public static void firingRouteStillAdmitsClearNativePath(GameTestHelper helper) {
        Arena a = new Arena(helper);
        Zombie target = a.zombie(a.start.east(12));
        var firing = RangedCombatPositioning.findFiringTarget(a.guard, target, List.of(), 225)
                .orElseThrow(() -> new AssertionError("clear terrain must offer a firing destination"));
        Path path = firing.createPath(a.guard.getNavigation(), a.guard, 0);
        helper.assertTrue(path != null && path.canReach(), "safe firing path was rejected");
        a.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 60)
    public static void repositionFindsStandingCellOnCarpet(GameTestHelper helper) {
        partialFloor(helper, Blocks.WHITE_CARPET.defaultBlockState(), 0.0625);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 60)
    public static void repositionFindsStandingCellOnBottomSlab(GameTestHelper helper) {
        partialFloor(helper, Blocks.STONE_SLAB.defaultBlockState(), 0.5);
    }

    private static void partialFloor(GameTestHelper helper, BlockState surface, double floorHeight) {
        Arena a = new Arena(helper);
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                helper.getLevel().setBlock(a.start.offset(x, 0, z), surface, 3);
            }
        }
        a.guard.setPos(a.guard.getX(), a.start.getY() + floorHeight, a.guard.getZ());
        Zombie target = a.zombie(a.start.east(12));
        target.setPos(target.getX(), a.guard.getY(), target.getZ());
        var firing = RangedCombatPositioning.findFiringTarget(a.guard, target, List.of(), 225);
        helper.assertTrue(firing.isPresent(), "tactical graph rejected every standing cell on " + surface);
        Path path = firing.orElseThrow().createPath(a.guard.getNavigation(), a.guard, 0);
        helper.assertTrue(path != null && path.canReach(), "fractional-floor firing destination was not reachable");
        var end = path.getEndNode();
        a.guard.setPos(end.x + 0.5, a.start.getY() + floorHeight, end.z + 0.5);
        helper.assertTrue(firing.orElseThrow().isReached(a.guard, 0),
                "physical arrival on a fractional floor was not recognized");
        a.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 60)
    public static void meleeGuardRetainsFightOnNeighbourAlarm(GameTestHelper helper) {
        neighbourAlarm(helper, ProfessionsMCA.GUARD);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 60)
    public static void archerRetainsFightOnNeighbourAlarm(GameTestHelper helper) {
        neighbourAlarm(helper, ProfessionsMCA.ARCHER);
    }

    private static void neighbourAlarm(GameTestHelper helper, VillagerProfession profession) {
        Arena a = new Arena(helper);
        a.guard.setProfession(profession);
        if (profession == ProfessionsMCA.GUARD) {
            a.guard.setItemSlot(EquipmentSlot.MAINHAND, Items.IRON_SWORD.getDefaultInstance());
        }
        Zombie current = a.zombie(a.start.east(2));
        Skeleton attacker = a.skeleton(a.start.south(12));
        VillagerEntityMCA victim = a.villager(net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE).value(), a.start.south(4));
        a.guard.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, current);
        helper.assertTrue(victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 1),
                "neighbour fixture damage was rejected");
        helper.assertTrue(a.guard.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, current),
                "neighbour alarm replaced a useful " + profession + " fight");
        a.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void archerDefendsAgainstVisibleAttackerWhileOldTargetIsHidden(GameTestHelper helper) {
        selfDefence(helper, true);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void archerKeepsUsableFightAfterAnotherRangedHit(GameTestHelper helper) {
        selfDefence(helper, false);
    }

    private static void selfDefence(GameTestHelper helper, boolean hideCurrent) {
        Arena a = new Arena(helper);
        Skeleton current = a.skeleton(a.start.east(12));
        Skeleton attacker = a.skeleton(a.start.south(12));
        if (hideCurrent) {
            for (int y = 0; y < 4; y++) {
                helper.getLevel().setBlock(a.start.east(3).above(y), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        a.guard.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, current);
        helper.assertTrue(a.guard.hasLineOfSight(current) != hideCurrent && a.guard.hasLineOfSight(attacker),
                "self-defence fixture must distinguish the current and incoming shot");
        helper.assertTrue(a.guard.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(attacker), 1),
                "self-defence fixture damage was rejected");
        assertTarget(helper, a.guard, hideCurrent ? attacker : current);
        a.guard.setNoAi(false);
        helper.runAfterDelay(5, () -> {
            assertTarget(helper, a.guard, hideCurrent ? attacker : current);
            a.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 1500)
    public static void normalHealthArcherKillsPursuingFullHealthSpider(GameTestHelper helper) {
        Arena a = new Arena(helper);
        Spider spider = EntityType.SPIDER.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
        if (spider == null) throw new IllegalStateException("No spider for combat fixture");
        spider.snapTo(a.start.getX() + 10.5, a.start.getY(), a.start.getZ() + 0.5);
        spider.setPersistenceRequired();
        spider.setTarget(a.guard);
        helper.getLevel().addFreshEntity(spider);
        a.entities.add(spider);
        a.guard.getRandom().setSeed(42);
        spider.getRandom().setSeed(42);
        a.guard.setNoAi(false);
        a.guard.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, spider);
        helper.assertTrue(a.guard.getHealth() == 20 && spider.getHealth() == 16,
                "sustained fight must use normal full health");
        int[] hits = {0};
        float[] previous = {spider.getHealth()};
        helper.onEachTick(() -> {
            if (a.closed) return;
            helper.assertTrue(a.guard.isAlive(), "normal-health archer died during spider pursuit");
            if (spider.getHealth() < previous[0]) {
                var damage = spider.getLastDamageSource();
                helper.assertTrue(damage != null && damage.getEntity() == a.guard
                                && damage.getDirectEntity() instanceof AbstractArrow,
                        "spider damage did not come from the archer's arrows");
                hits[0]++;
                previous[0] = spider.getHealth();
            }
            if (!spider.isAlive()) {
                helper.assertTrue(spider.getHealth() <= 0 && hits[0] >= 2,
                        "sustained fight did not require repeated damaging arrows");
                net.conczin.mca.MCA.LOGGER.info("[MCA Archer Policy] spider killed tick={} hits={} archerHealth={}",
                        helper.getTick(), hits[0], a.guard.getHealth());
                a.succeed();
            } else if (helper.getTick() >= 1400) {
                helper.fail("normal-health archer failed to kill pursuing spider; hits=" + hits[0]
                        + ", spiderHealth=" + spider.getHealth() + ", archerHealth=" + a.guard.getHealth()
                        + ", combat=" + RangedCombatState.current(a.guard));
            }
        });
    }

    private static void assertTarget(GameTestHelper helper, VillagerEntityMCA guard, Mob expected) {
        helper.assertTrue(guard.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, expected),
                "guard did not retain the expected self-defence target");
    }

    private static final class Arena {
        private final GameTestHelper helper;
        private final BlockPos start;
        private final VillagerEntityMCA guard;
        private final List<Entity> entities = new ArrayList<>();
        private final Set<Long> addedChunks = new HashSet<>();
        private final Map<BlockPos, BlockState> terrain = new LinkedHashMap<>();
        private boolean closed;

        private Arena(GameTestHelper helper) {
            this.helper = helper;
            start = helper.absolutePos(new BlockPos(56, 3, 56));
            ARENAS.add(this);
            for (BlockPos pos : BlockPos.betweenClosed(start.offset(-20, -1, -20), start.offset(20, 7, 20))) {
                terrain.put(pos.immutable(), helper.getLevel().getBlockState(pos));
            }
            Set<Long> forced = new HashSet<>(helper.getLevel().getForceLoadedChunks());
            GameTestTerrain.prepareFlatArea(helper, start, 20, 8);
            for (long chunk : helper.getLevel().getForceLoadedChunks()) {
                if (!forced.contains(chunk)) addedChunks.add(chunk);
            }
            guard = villager(ProfessionsMCA.ARCHER, start);
            guard.getTraits().removeTrait(Traits.DWARFISM);
            guard.getGenetics().setGene(Genetics.SIZE, 0.5F);
            guard.getGenetics().setGene(Genetics.WIDTH, 0.5F);
            guard.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);
            guard.setHealth(20);
            guard.setItemSlot(EquipmentSlot.MAINHAND, Items.BOW.getDefaultInstance());
            guard.refreshBrain(helper.getLevel());
            guard.getBrain().setSchedule(net.conczin.mca.gametest.GameTestBrain.fixedSchedule(Activity.WORK));
            guard.getBrain().setActiveActivityIfPossible(Activity.WORK);
            guard.setOnGround(true);
        }

        private VillagerEntityMCA villager(VillagerProfession profession, BlockPos position) {
            VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel()).withAge(0)
                    .withProfession(profession).withPosition(Vec3.atBottomCenterOf(position))
                    .spawn(EntitySpawnReason.STRUCTURE);
            villager.setNoAi(true);
            entities.add(villager);
            return villager;
        }

        private Zombie zombie(BlockPos position) {
            Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
            if (zombie == null) throw new IllegalStateException("No zombie for policy fixture");
            prepareMob(zombie, position);
            return zombie;
        }

        private Skeleton skeleton(BlockPos position) {
            Skeleton skeleton = EntityType.SKELETON.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
            if (skeleton == null) throw new IllegalStateException("No skeleton for policy fixture");
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, Items.BOW.getDefaultInstance());
            prepareMob(skeleton, position);
            return skeleton;
        }

        private void prepareMob(Mob mob, BlockPos position) {
            mob.snapTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
            mob.setNoAi(true);
            mob.setPersistenceRequired();
            helper.getLevel().addFreshEntity(mob);
            entities.add(mob);
        }

        private void succeed() {
            close();
            helper.succeed();
        }

        private void close() {
            if (closed) return;
            closed = true;
            helper.getLevel().getEntitiesOfClass(AbstractArrow.class, guard.getBoundingBox().inflate(40),
                    arrow -> entities.contains(arrow.getOwner())).forEach(Entity::discard);
            entities.forEach(Entity::discard);
            terrain.forEach((pos, state) -> helper.getLevel().setBlock(pos, state, 3));
            addedChunks.forEach(chunk -> {
                ChunkPos pos = ChunkPos.unpack(chunk);
                helper.getLevel().setChunkForced(pos.x(), pos.z(), false);
            });
        }
    }
}
