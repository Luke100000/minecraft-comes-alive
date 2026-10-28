package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.RangedWeaponHelper;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

@PrefixGameTestTemplate(false)
public final class ArcherCombatStabilityGameTests {
    private static final String BATCH = "archer_stability";
    private static final List<Entity> ACTORS = new ArrayList<>();

    private ArcherCombatStabilityGameTests() { }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 100)
    public static void combatAcquisitionReleasesOrdinaryStroll(GameTestHelper helper) {
        Fixture f = fixture(helper, false);
        var stroll = new net.minecraft.world.entity.ai.memory.WalkTarget(Vec3.atBottomCenterOf(f.start().west(8)), 0.5F, 0);
        f.archer().getBrain().setMemory(MemoryModuleType.WALK_TARGET, stroll);
        helper.assertTrue(f.archer().getNavigation().moveTo(f.start().getX() - 7.5, f.start().getY(), f.start().getZ() + 0.5, 0.5),
                "stroll fixture could not start ordinary navigation");
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(f.archer().getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) != stroll,
                    "combat HOLD retained its pre-combat stroll away from the enemy");
            helper.assertTrue(f.archer().getNavigation().isDone(), "ordinary stroll still drives HOLD movement");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 100)
    public static void naturalLeftHandedArcherKeepsBowThroughArmorRefresh(GameTestHelper helper) {
        Fixture f = fixture(helper, false);
        f.archer().getTraits().addTrait(net.conczin.mca.entity.ai.Traits.LEFT_HANDED);
        f.archer().refreshBrain(helper.getLevel());
        f.archer().getBrain().setSchedule(new ScheduleBuilder(new Schedule()).changeActivityAt(0, Activity.IDLE).build());
        f.archer().getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, f.target());
        boolean[] drawing = {false};
        helper.onEachTick(() -> {
            if (f.archer().isUsingItem()) drawing[0] = true;
            if (helper.getTick() == 30) f.archer().getVillagerBrain().setArmorWear(true);
            if (helper.getTick() > 10 && helper.getTick() < 70) {
                helper.assertTrue(RangedWeaponHelper.isHoldingSupportedWeapon(f.archer()),
                        "full Brain initialization removed both bows at tick " + helper.getTick());
            }
            if (helper.getTick() == 70) {
                helper.assertTrue(drawing[0], "natural archer never drew its bow");
                helper.succeed();
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void criticalArcherClimbsOutOfOneBlockCorner(GameTestHelper helper) {
        Fixture f = fixture(helper, false);
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                if (x < 0 || z < 0) helper.getLevel().setBlock(f.start().offset(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        }
        f.target().absMoveTo(f.archer().getX() + 3, f.archer().getY(), f.archer().getZ() + 3);
        f.target().setTarget(f.archer());
        f.archer().getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        f.archer().setHealth(20);
        f.archer().setLastHurtByMob(f.target());
        f.archer().getBrain().setMemory(MemoryModuleType.HURT_BY_ENTITY, f.target());
        f.archer().getBrain().setMemory(MemoryModuleType.NEAREST_HOSTILE, f.target());
        boolean[] panicked = {false};
        helper.onEachTick(() -> {
            panicked[0] |= f.archer().getBrain().isActive(Activity.PANIC);
            if (f.archer().getY() >= f.start().getY() + 0.95
                    && f.archer().position().distanceToSqr(Vec3.atBottomCenterOf(f.start())) >= 16) {
                helper.assertTrue(panicked[0], "corner fixture never entered critical-health panic");
                helper.succeed();
            }
            if (helper.getTick() == 150) helper.fail("critical archer did not navigate over the one-block corner: " + f.archer().position());
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 200)
    public static void healthyArcherLeavesCornerWithNoUsableSidestep(GameTestHelper helper) {
        Fixture f = fixture(helper, false);
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                if (x < 0 || z < 0) helper.getLevel().setBlock(f.start().offset(x, 0, z), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        }
        f.target().absMoveTo(f.archer().getX() + 7, f.archer().getY(), f.archer().getZ() + 7);
        f.target().setTarget(f.archer());
        f.archer().setYRot(-45);
        helper.assertTrue(f.archer().hasLineOfSight(f.target()), "corner must leave the enemy visible");
        helper.assertTrue(RangedCombatPositioning.findBestStrafeDirection(f.archer(), f.target(), List.of()) == 0,
                "corner must block both ordinary strafe sides");
        helper.onEachTick(() -> {
            if (f.archer().getY() >= f.start().getY() + 0.95
                    && f.archer().position().distanceToSqr(Vec3.atBottomCenterOf(f.start())) >= 4) helper.succeed();
            if (helper.getTick() == 160) helper.fail("healthy archer held in its blocked corner instead of using a climbable exit");
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 320)
    public static void unreachablePillarEnemyDoesNotMonopolizeCombat(GameTestHelper helper) {
        Fixture f = fixture(helper, false);
        prepareFlatArea(helper, f.start(), 34, 26);
        for (int y = 0; y < 20; y++) helper.getLevel().setBlock(f.start().offset(4, y, 0), Blocks.STONE.defaultBlockState(), 3);
        f.target().absMoveTo(f.start().getX() + 4.01, f.start().getY() + 20, f.start().getZ() + 0.5);
        Zombie reachable = spawnTarget(helper, f.start().south(28));
        helper.assertTrue(f.archer().hasLineOfSight(f.target()), "pillar fixture must expose the unreachable enemy");
        helper.runAfterDelay(260, () -> {
            helper.assertTrue(f.archer().getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == reachable,
                    "unreachable elevated enemy monopolized combat instead of the reachable enemy");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void unreachableTargetBecomesEligibleAfterMovingOffPillar(GameTestHelper helper) {
        Fixture f = fixture(helper, false);
        prepareFlatArea(helper, f.start(), 24, 26);
        for (int y = 0; y < 20; y++) helper.getLevel().setBlock(f.start().offset(4, y, 0), Blocks.STONE.defaultBlockState(), 3);
        f.target().absMoveTo(f.start().getX() + 4.01, f.start().getY() + 20, f.start().getZ() + 0.5);
        boolean[] rejected = {false};
        helper.onEachTick(() -> {
            boolean blocked = net.conczin.mca.entity.ai.brain.UnreachableCombatTargets.contains(f.archer(), f.target());
            if (!rejected[0] && blocked) {
                rejected[0] = true;
                f.target().absMoveTo(f.start().getX() + 0.5, f.start().getY(), f.start().getZ() + 10.5);
            } else if (rejected[0] && !blocked
                    && f.archer().getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == f.target()) {
                helper.succeed();
            }
            if (helper.getTick() == 210) helper.fail("moved target was not rejected and reacquired after becoming reachable");
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 200)
    public static void isolatedShootableEnemyIsKeptWithoutGroundRoute(GameTestHelper helper) {
        Fixture f = fixture(helper, false);
        for (int x = 7; x <= 13; x++) {
            for (int z = -3; z <= 3; z++) {
                if (x == 10 && z == 0) continue;
                for (int y = -1; y >= -3; y--) helper.getLevel().setBlock(f.start().offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        helper.onEachTick(() -> {
            helper.assertTrue(!net.conczin.mca.entity.ai.brain.UnreachableCombatTargets.contains(f.archer(), f.target()),
                    "shootable enemy across a gap was marked unreachable");
            if (helper.getTick() == 140) {
                helper.assertTrue(f.target().getHealth() < 200, "archer did not hit the isolated shootable enemy");
                helper.assertTrue(f.archer().getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == f.target(),
                        "archer abandoned its shootable enemy across a gap");
                helper.succeed();
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 200)
    public static void blockedCornerWithLowCeilingDoesNotForceJump(GameTestHelper helper) {
        Fixture f = fixture(helper, false);
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                if (x < 0 || z < 0) helper.getLevel().setBlock(f.start().offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(f.start().offset(x, 2, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        f.target().absMoveTo(f.archer().getX() + 7, f.archer().getY(), f.archer().getZ() + 7);
        f.archer().setYRot(-45);
        helper.runAfterDelay(150, () -> {
            helper.assertTrue(f.archer().getY() < f.start().getY() + 0.1, "archer forced a climb into a blocked ceiling");
            helper.assertTrue(f.archer().getHealth() > 0, "ceiling recovery caused suffocation");
            helper.assertTrue(RangedCombatState.current(f.archer()).orElse(null) != RangedCombatState.RECOVER,
                    "archer retained an impossible corner recovery");
            helper.succeed();
        });
    }

    @AfterBatch(batch = "archer_stability_cost")
    public static void cleanupCost(ServerLevel level) { cleanup(level); }

    @GameTest(batch = "archer_stability_cost", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 440)
    public static void obstructedGroupsKeepCombatRequestsBounded(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(56, 3, 56));
        int[] counts = {1, 8, 24};
        int[] phase = {0};
        List<Fixture> fighters = new ArrayList<>();
        int[] starts = {0}, earlyStops = {0}, draws = {0};
        RangedCombatState[] previous = new RangedCombatState[24];
        int[] strafeAge = new int[24];
        boolean[] using = new boolean[24];
        java.util.Set<Integer> arrows = new java.util.HashSet<>();
        helper.onEachTick(() -> {
            int tick = (int) ((helper.getTick() - 1) % 120);
            if (tick == 0) {
                cleanup(helper.getLevel());
                fighters.clear();
                starts[0] = earlyStops[0] = draws[0] = 0;
                arrows.clear();
                for (int index = 0; index < counts[phase[0]]; index++) {
                    BlockPos start = center.offset((index % 6) * 32, 0, (index / 6) * 32);
                    prepareFlatArea(helper, start, 12, 6);
                    VillagerEntityMCA archer = spawnArcher(helper, start);
                    archer.getRandom().setSeed(1337 + index);
                    archer.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.5);
                    archer.setYRot(270);
                    Zombie target = spawnTarget(helper, start.east(10));
                    archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
                    fighters.add(new Fixture(archer, target, start, null, 0));
                    previous[index] = null;
                    strafeAge[index] = 0;
                    using[index] = false;
                }
            }
            for (int index = 0; index < fighters.size(); index++) {
                Fixture f = fighters.get(index);
                if (tick == 30 || tick == 80) arrowLane(helper, f, true);
                if (tick == 55 || tick == 105) arrowLane(helper, f, false);
                RangedCombatState state = RangedCombatState.current(f.archer()).orElse(null);
                if (state == RangedCombatState.STRAFE) {
                    if (previous[index] != state) { starts[0]++; strafeAge[index] = 0; }
                    strafeAge[index]++;
                } else if (previous[index] == RangedCombatState.STRAFE && strafeAge[index] <= 4) {
                    earlyStops[0]++;
                }
                previous[index] = state;
                if (f.archer().isUsingItem() && !using[index]) draws[0]++;
                using[index] = f.archer().isUsingItem();
                helper.getLevel().getEntitiesOfClass(AbstractArrow.class, f.archer().getBoundingBox().inflate(24),
                        arrow -> arrow.getOwner() == f.archer()).forEach(arrow -> arrows.add(arrow.getId()));
            }
            if (tick == 119) {
                long requests = 0, nanos = 0;
                for (Fixture f : fighters) {
                    var snapshot = net.conczin.mca.entity.ai.navigation.PathRequestDiagnostics.snapshot(f.archer());
                    requests += snapshot.sinkRequests() + snapshot.directRequests();
                    nanos += snapshot.searchNanos();
                    helper.assertTrue(f.archer().tickCount >= 100, "cost fixture actor did not actually tick");
                }
                net.conczin.mca.MCA.LOGGER.info(
                        "[MCA Archer stability cost] archers={} ticks=120 strafeStarts={} earlyStops={} draws={} arrows={} requests={} searchMs={}",
                        fighters.size(), starts[0], earlyStops[0], draws[0], arrows.size(), requests, nanos / 1.0E6);
                helper.assertTrue(requests < fighters.size() * 60L, "unbounded combat path requests: " + requests);
                if (++phase[0] == counts.length) helper.succeed();
            }
        });
    }

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        for (Entity actor : ACTORS) {
            level.getEntitiesOfClass(AbstractArrow.class, actor.getBoundingBox().inflate(32),
                    arrow -> arrow.getOwner() == actor).forEach(Entity::discard);
            actor.discard();
        }
        ACTORS.clear();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 160)
    public static void visibleTargetWithBriefBlockedTrajectoryKeepsSafeStrafe(GameTestHelper helper) {
        Fixture f = fixture(helper, false);
        int[] blockedAt = {-1};
        helper.onEachTick(() -> {
            if (blockedAt[0] < 0) {
                if (RangedCombatState.current(f.archer()).orElse(null) != RangedCombatState.STRAFE) {
                    helper.assertTrue(helper.getTick() < 100, "awake archer never began a natural strafe");
                    return;
                }
                arrowLane(helper, f, true);
                assertVisibleButShotBlocked(helper, f);
                blockedAt[0] = (int) helper.getTick();
            } else if (helper.getTick() >= blockedAt[0] + 5) {
                helper.assertTrue(RangedCombatState.current(f.archer()).orElse(null) == RangedCombatState.STRAFE,
                        "five trajectory-blocked ticks interrupted a physically safe strafe");
                helper.assertTrue(f.archer().getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == f.target(),
                        "visible target was lost because the nominal arrow path was blocked");
                helper.assertTrue(f.archer().tickCount >= 10, "fixture actor did not actually tick");
                helper.succeed();
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void turningStrafeUsesOneHeadingForSelectionAndAdmission(GameTestHelper helper) {
        for (float heading : new float[]{0, 45, 90, 180}) {
            Fixture f = fixture(helper, true);
            double targetYaw = Math.toRadians(heading + 90);
            f.target().absMoveTo(f.archer().getX() - Math.sin(targetYaw) * 10, f.archer().getY(),
                    f.archer().getZ() + Math.cos(targetYaw) * 10);
            f.archer().setYRot(heading);
            beginManualStrafe(helper, f);
            double admittedYaw = targetYaw;
            Vec3 before = f.archer().position();
            f.archer().getMoveControl().tick();
            Vec3 admittedSide = new Vec3(Math.cos(admittedYaw), 0, Math.sin(admittedYaw))
                    .scale(Math.signum(f.archer().xxa));
            f.archer().setNoAi(false);
            f.archer().travel(new Vec3(f.archer().xxa, 0, f.archer().zza));
            Vec3 displacement = f.archer().position().subtract(before).multiply(1, 0, 1);
            helper.assertTrue(displacement.dot(admittedSide) > 0.01, "strafe did not move along its admitted side");
            helper.assertTrue(Math.abs(displacement.normalize().cross(admittedSide).y) < 0.1,
                    "strafe turned again after choosing a side at heading " + heading);
            f.archer().discard();
            f.target().discard();
        }
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void previousWalkingCollisionDoesNotCancelClearFirstStrafe(GameTestHelper helper) {
        Fixture f = fixture(helper, true);
        f.archer().horizontalCollision = true;
        beginManualStrafe(helper, f);
        Vec3 before = f.archer().position();
        f.archer().getMoveControl().tick();
        f.archer().setNoAi(false);
        f.archer().travel(new Vec3(f.archer().xxa, 0, f.archer().zza));
        helper.assertTrue(f.archer().position().distanceToSqr(before) > 0.0001,
                "a stale walking collision prevented a physically clear first step");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void oneFrameEyeOcclusionDoesNotCancelSafeStrafe(GameTestHelper helper) {
        Fixture f = fixture(helper, true);
        int tick = beginManualStrafe(helper, f);
        for (int y = 0; y < 4; y++) {
            for (int z = -5; z <= 5; z++) helper.getLevel().setBlock(f.start().offset(5, y, z), Blocks.STONE.defaultBlockState(), 3);
        }
        for (int offset = 1; offset <= 4; offset++) {
            f.archer().getMoveControl().tick();
            f.archer().setNoAi(false);
            f.archer().travel(new Vec3(f.archer().xxa, 0, f.archer().zza));
            f.archer().setNoAi(true);
            f.tick(helper, tick + offset);
            helper.assertTrue(RangedCombatState.current(f.archer()).orElse(null) == RangedCombatState.STRAFE,
                    "brief eye occlusion cancelled a physically safe strafe at tick " + offset);
        }
        f.tick(helper, tick + 5);
        helper.assertTrue(RangedCombatState.current(f.archer()).orElse(null) == RangedCombatState.HOLD,
                "strafe continued beyond four unseen ticks");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void newSideWallStopsStrafeImmediately(GameTestHelper helper) {
        Fixture f = fixture(helper, true);
        int tick = beginManualStrafe(helper, f);
        f.archer().getMoveControl().tick();
        int side = f.archer().xxa > 0 ? -1 : 1; // Facing east, positive lateral input travels north.
        for (int y = 0; y < 3; y++) helper.getLevel().setBlock(f.start().offset(0, y, side), Blocks.STONE.defaultBlockState(), 3);
        f.tick(helper, tick + 1);
        helper.assertTrue(RangedCombatState.current(f.archer()).orElse(null) != RangedCombatState.STRAFE,
                "new wall did not immediately stop lateral input");
        helper.assertTrue(f.archer().xxa == 0 && helper.getLevel().noCollision(f.archer(), f.archer().getBoundingBox()),
                "archer entered the wall or retained lateral input");
        helper.succeed();
    }

    private static int beginManualStrafe(GameTestHelper helper, Fixture f) {
        for (int tick = 1; tick <= 40; tick++) {
            f.tick(helper, tick);
            if (RangedCombatState.current(f.archer()).orElse(null) == RangedCombatState.STRAFE) return tick;
        }
        helper.fail("manual movement never admitted a clear strafe; state=" + RangedCombatState.current(f.archer()));
        return 0;
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void ordinaryRepositionKeepsExistingBowDraw(GameTestHelper helper) {
        Fixture f = fixture(helper, true);
        BowTask<VillagerEntityMCA> bow = startBow(helper, f);
        f.archer().getBrain().setMemory(MemoryModuleTypeMCA.RANGED_COMBAT_STATE, RangedCombatState.REPOSITION);
        bow.tick(helper.getLevel(), f.archer(), f.now() + 2);
        helper.assertTrue(f.archer().isUsingItem(), "ordinary repositioning discarded an existing draw");
        helper.runAfterDelay(22, () -> {
            helper.assertTrue(f.archer().getTicksUsingItem() >= 20, "ordinary repositioning reset draw progress");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void repositionDoesNotStartANewDraw(GameTestHelper helper) {
        Fixture f = fixture(helper, true);
        f.archer().getBrain().setMemory(MemoryModuleTypeMCA.RANGED_COMBAT_STATE, RangedCombatState.REPOSITION);
        BowTask<VillagerEntityMCA> bow = new BowTask<>(20, 15);
        bow.start(helper.getLevel(), f.archer(), f.now());
        bow.tick(helper.getLevel(), f.archer(), f.now() + 1);
        helper.assertTrue(!f.archer().isUsingItem(), "reposition began a new bow draw");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 100)
    public static void blockedReleaseKeepsChargeThenFiresOnceWhenClear(GameTestHelper helper) {
        Fixture f = fixture(helper, true);
        BowTask<VillagerEntityMCA> bow = startBow(helper, f);
        helper.runAfterDelay(22, () -> {
            helper.assertTrue(f.archer().getTicksUsingItem() >= 20, "draw fixture did not reach full charge");
            arrowLane(helper, f, true);
            assertVisibleButShotBlocked(helper, f);
            for (int tick = 0; tick < 5; tick++) bow.tick(helper.getLevel(), f.archer(), f.now() + 23 + tick);
            helper.assertTrue(f.archer().isUsingItem() && f.archer().getTicksUsingItem() >= 20,
                    "blocked release discarded its full charge");
            helper.assertTrue(ownedArrows(helper, f).isEmpty(), "blocked release spawned an arrow");
            arrowLane(helper, f, false);
            bow.tick(helper.getLevel(), f.archer(), f.now() + 28);
            helper.assertTrue(!f.archer().isUsingItem() && ownedArrows(helper, f).size() == 1,
                    "clear opportunity did not release exactly one charged arrow");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 100)
    public static void blockedReleaseExpiresWithoutArrowOrCooldown(GameTestHelper helper) {
        Fixture f = fixture(helper, true);
        BowTask<VillagerEntityMCA> bow = startBow(helper, f);
        helper.runAfterDelay(22, () -> {
            arrowLane(helper, f, true);
            for (int tick = 0; tick < 21; tick++) bow.tick(helper.getLevel(), f.archer(), f.now() + 23 + tick);
            helper.assertTrue(!f.archer().isUsingItem() && ownedArrows(helper, f).isEmpty(),
                    "sustained blockage retained charge or spawned an arrow");
            arrowLane(helper, f, false);
            bow.tick(helper.getLevel(), f.archer(), f.now() + 44);
            helper.assertTrue(f.archer().isUsingItem(), "failed release consumed the successful-shot cooldown");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 100)
    public static void rangeLossDoesNotDiscardFullDrawOrFireOutsideRange(GameTestHelper helper) {
        Fixture f = fixture(helper, true);
        BowTask<VillagerEntityMCA> bow = startBow(helper, f);
        helper.runAfterDelay(22, () -> {
            f.target().setPos(f.archer().position().add(18, 0, 0));
            bow.tick(helper.getLevel(), f.archer(), f.now() + 23);
            helper.assertTrue(f.archer().isUsingItem() && ownedArrows(helper, f).isEmpty(),
                    "short range loss discarded charge or fired outside range");
            f.target().setPos(f.archer().position().add(10, 0, 0));
            bow.tick(helper.getLevel(), f.archer(), f.now() + 24);
            helper.assertTrue(ownedArrows(helper, f).size() == 1, "returning target did not receive the retained full draw");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 100)
    public static void targetChangeRequiresFreshDrawAndTargetLossCancelsUse(GameTestHelper helper) {
        Fixture f = fixture(helper, true);
        BowTask<VillagerEntityMCA> bow = startBow(helper, f);
        helper.runAfterDelay(22, () -> {
            Zombie replacement = spawnTarget(helper, f.start().east(10).south(2));
            f.archer().getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, replacement);
            bow.tick(helper.getLevel(), f.archer(), f.now() + 23);
            helper.assertTrue(f.archer().isUsingItem() && f.archer().getTicksUsingItem() == 0
                            && ownedArrows(helper, f).isEmpty(), "target switch reused a stale full draw");
            replacement.discard();
            bow.tick(helper.getLevel(), f.archer(), f.now() + 24);
            helper.assertTrue(!f.archer().isUsingItem() && ownedArrows(helper, f).isEmpty(),
                    "dead target retained use or received a stale arrow");
            helper.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 100)
    public static void eyeOcclusionCannotChainTwoDrawGraceWindows(GameTestHelper helper) {
        Fixture f = fixture(helper, true);
        BowTask<VillagerEntityMCA> bow = startBow(helper, f);
        helper.runAfterDelay(22, () -> {
            for (int y = 0; y < 4; y++) helper.getLevel().setBlock(f.start().east(5).above(y), Blocks.STONE.defaultBlockState(), 3);
            for (int tick = 0; tick < 19; tick++) bow.tick(helper.getLevel(), f.archer(), f.now() + 23 + tick);
            helper.assertTrue(f.archer().isUsingItem(), "unseen grace ended before 20 ticks");
            for (int tick = 19; tick < 21; tick++) bow.tick(helper.getLevel(), f.archer(), f.now() + 23 + tick);
            helper.assertTrue(!f.archer().isUsingItem() && ownedArrows(helper, f).isEmpty(),
                    "unseen draw chained extra grace or fired through terrain");
            helper.succeed();
        });
    }

    private static BowTask<VillagerEntityMCA> startBow(GameTestHelper helper, Fixture f) {
        BowTask<VillagerEntityMCA> bow = new BowTask<>(20, 15);
        bow.start(helper.getLevel(), f.archer(), f.now());
        bow.tick(helper.getLevel(), f.archer(), f.now() + 1);
        helper.assertTrue(f.archer().isUsingItem(), "fixture must begin drawing a bow");
        return bow;
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void recoveryAndEmergencyCancelExistingBowDrawImmediately(GameTestHelper helper) {
        for (RangedCombatState state : List.of(RangedCombatState.RECOVER, RangedCombatState.EMERGENCY_FLEE)) {
            Fixture f = fixture(helper, true);
            BowTask<VillagerEntityMCA> bow = startBow(helper, f);
            f.archer().getBrain().setMemory(MemoryModuleTypeMCA.RANGED_COMBAT_STATE, state);
            bow.tick(helper.getLevel(), f.archer(), f.now() + 2);
            helper.assertTrue(!f.archer().isUsingItem() && ownedArrows(helper, f).isEmpty(),
                    state + " failed to cancel the draw immediately");
            f.archer().discard();
            f.target().discard();
        }
        helper.succeed();
    }

    private static List<AbstractArrow> ownedArrows(GameTestHelper helper, Fixture f) {
        return helper.getLevel().getEntitiesOfClass(AbstractArrow.class, f.archer().getBoundingBox().inflate(24),
                arrow -> arrow.getOwner() == f.archer());
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 60)
    public static void sustainedBlockedTrajectoryRequestsReposition(GameTestHelper helper) {
        Fixture f = fixture(helper, true);
        arrowLane(helper, f, true);
        assertVisibleButShotBlocked(helper, f);
        for (int tick = 1; tick <= 19; tick++) f.tick(helper, tick);
        helper.assertTrue(RangedCombatState.current(f.archer()).orElse(null) == RangedCombatState.HOLD,
                "a shot obstruction triggered repositioning before 20 consecutive ticks");
        f.tick(helper, 20);
        helper.assertTrue(RangedCombatState.current(f.archer()).orElse(null) == RangedCombatState.REPOSITION,
                "20 consecutive blocked shots did not request repositioning");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 60)
    public static void repositionRequiresThreeClearShotTicks(GameTestHelper helper) {
        Fixture f = fixture(helper, true);
        arrowLane(helper, f, true);
        assertVisibleButShotBlocked(helper, f);
        for (int tick = 1; tick <= 20; tick++) f.tick(helper, tick);
        arrowLane(helper, f, false);
        helper.assertTrue(RangedWeaponHelper.canShootBowFrom(f.archer(), f.archer().position(), f.target()),
                "removing cover did not restore the shot");
        f.tick(helper, 21);
        f.tick(helper, 22);
        helper.assertTrue(RangedCombatState.current(f.archer()).orElse(null) == RangedCombatState.REPOSITION,
                "reposition ended after fewer than three clear-shot ticks");
        f.tick(helper, 23);
        helper.assertTrue(RangedCombatState.current(f.archer()).orElse(null) == RangedCombatState.HOLD,
                "three clear-shot ticks did not settle into HOLD");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 60)
    public static void eyeOcclusionGraceDoesNotPreventSealedCorridorReposition(GameTestHelper helper) {
        Fixture f = fixture(helper, true);
        for (int y = 0; y < 4; y++) {
            for (int z = -5; z <= 5; z++) {
                helper.getLevel().setBlock(f.start().offset(5, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        helper.assertTrue(!f.archer().hasLineOfSight(f.target()), "eye obstruction fixture is not occluded");
        for (int tick = 1; tick <= 20; tick++) f.tick(helper, tick);
        helper.assertTrue(RangedCombatState.current(f.archer()).orElse(null) == RangedCombatState.HOLD,
                "eye occlusion bypassed the 20-tick adjustment grace");
        f.tick(helper, 21);
        helper.assertTrue(RangedCombatState.current(f.archer()).orElse(null) == RangedCombatState.REPOSITION,
                "sustained eye occlusion never requested repositioning");
        helper.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 60)
    public static void friendlyVillagerDoesNotOccludeBlockSightRay(GameTestHelper helper) {
        Fixture f = fixture(helper, true);
        VillagerEntityMCA friend = spawnArcher(helper, f.start().east(5));
        friend.setNoAi(true);
        helper.assertTrue(f.archer().hasLineOfSight(f.target()), "friendly entity occluded the block sight ray");
        helper.assertTrue(RangedWeaponHelper.canShootBowFrom(f.archer(), f.archer().position(), f.target()),
                "friendly entity occluded the nominal block-only trajectory");
        helper.succeed();
    }

    private static Fixture fixture(GameTestHelper helper, boolean manualMovement) {
        BlockPos start = helper.absolutePos(new BlockPos(56, 3, 56));
        prepareFlatArea(helper, start, 16, 8);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        archer.setNoAi(manualMovement);
        archer.setYRot(270);
        Zombie target = spawnTarget(helper, start.east(10));
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
        long now = helper.getLevel().getGameTime();
        if (manualMovement) movement.start(helper.getLevel(), archer, now);
        return new Fixture(archer, target, start, movement, now);
    }

    private static Zombie spawnTarget(GameTestHelper helper, BlockPos position) {
        Zombie target = EntityType.ZOMBIE.create(helper.getLevel());
        if (target == null) throw new IllegalStateException("No zombie for archer stability fixture");
        target.setBaby(true);
        target.setNoAi(true);
        target.setItemSlot(EquipmentSlot.HEAD, Items.IRON_HELMET.getDefaultInstance());
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        target.setHealth(200);
        target.absMoveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        target.setOnGround(true);
        helper.getLevel().addFreshEntity(target);
        ACTORS.add(target);
        return target;
    }

    private static VillagerEntityMCA spawnArcher(GameTestHelper helper, BlockPos position) {
        VillagerEntityMCA archer = VillagerFactory.newVillager(helper.getLevel()).withAge(0)
                .withProfession(ProfessionsMCA.ARCHER).withPosition(Vec3.atBottomCenterOf(position))
                .withName("Stability Archer").spawn(MobSpawnType.STRUCTURE);
        // This fixture distinguishes a body-level arrow obstruction from an eye
        // ray. Fix ordinary adult dimensions rather than randomising that geometry.
        archer.getTraits().removeTrait(net.conczin.mca.entity.ai.Traits.DWARFISM);
        archer.getGenetics().setGene(net.conczin.mca.entity.ai.Genetics.SIZE, 0.5F);
        archer.getGenetics().setGene(net.conczin.mca.entity.ai.Genetics.WIDTH, 0.5F);
        archer.setItemSlot(EquipmentSlot.MAINHAND, Items.BOW.getDefaultInstance());
        archer.refreshBrain(helper.getLevel());
        archer.getBrain().setSchedule(new ScheduleBuilder(new Schedule()).changeActivityAt(0, Activity.IDLE).build());
        archer.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        archer.setOnGround(true);
        ACTORS.add(archer);
        return archer;
    }

    private static void arrowLane(GameTestHelper helper, Fixture f, boolean blocked) {
        for (int z = -5; z <= 5; z++) {
            helper.getLevel().setBlock(f.start().offset(8, 0, z),
                    (blocked ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
        }
    }

    private static void assertVisibleButShotBlocked(GameTestHelper helper, Fixture f) {
        helper.assertTrue(f.archer().hasLineOfSight(f.target()), "one-block lip must leave eye LOS clear");
        helper.assertTrue(!RangedWeaponHelper.canShootBowFrom(f.archer(), f.archer().position(), f.target()),
                "one-block lip must obstruct the body-directed arrow");
    }

    private record Fixture(VillagerEntityMCA archer, Zombie target, BlockPos start,
                           ArcherMovementTask<VillagerEntityMCA> movement, long now) {
        void tick(GameTestHelper helper, int tick) {
            archer.getSensing().tick();
            movement.tick(helper.getLevel(), archer, now + tick);
        }
    }
}
