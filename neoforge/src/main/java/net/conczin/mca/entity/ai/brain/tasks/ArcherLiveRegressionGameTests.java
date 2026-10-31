package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

/** Full Brain and physical movement regressions from the Oct 30 gameplay log. */
@PrefixGameTestTemplate(false)
public final class ArcherLiveRegressionGameTests {
    private static final String BATCH = "archer_live_regression";
    private static final List<Arena> ARENAS = new ArrayList<>();

    private ArcherLiveRegressionGameTests() { }

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        for (Arena arena : ARENAS) arena.close();
        ARENAS.clear();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 220)
    public static void distantActiveSkeletonAllowsShootingAndStrafing(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        a.target(12);
        int[] strafeTicks = {0};
        a.observe(() -> {
            var state = RangedCombatState.current(a.archer).orElse(null);
            if (state == RangedCombatState.STRAFE) strafeTicks[0]++;
            helper.assertTrue(state != RangedCombatState.KITE && state != RangedCombatState.APPROACH,
                    "clear 12-block shot caused retreat/approach instead of holding or strafing" + a.describe());
            if (helper.getTick() == 180) {
                helper.assertTrue(a.shots.size() >= 2, "distant target did not receive repeated shots" + a.describe());
                helper.assertTrue(a.target.getHealth() < a.target.getMaxHealth(), "repeated shots never hit" + a.describe());
                helper.assertTrue(strafeTicks[0] >= 20, "open firing ground produced no useful strafe burst" + a.describe());
                a.succeed();
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void closeSkeletonRetreatSettlesInsideShootingRange(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        a.target(7);
        boolean[] retreated = {false};
        a.observe(() -> {
            retreated[0] |= RangedCombatState.current(a.archer).orElse(null) == RangedCombatState.KITE
                    && a.archer.position().distanceToSqr(a.origin) > 1;
            helper.assertTrue(a.archer.distanceToSqr(a.target) <= 225,
                    "retreat crossed maximum shooting range against a stationary attacker" + a.describe());
            if (helper.getTick() == 190) {
                helper.assertTrue(retreated[0], "close attacker never produced physical retreat" + a.describe());
                helper.assertTrue(a.shots.size() >= 2, "retreat prevented repeated shots" + a.describe());
                helper.assertTrue(a.target.getHealth() < a.target.getMaxHealth(), "retreat shots never hit" + a.describe());
                helper.assertTrue(RangedCombatState.current(a.archer).orElse(null) != RangedCombatState.KITE,
                        "archer never settled after opening safe spacing" + a.describe());
                a.succeed();
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void climbingRetreatKeepsDrawAndFires(GameTestHelper helper) {
        ledgeRetreat(helper, true);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void descendingRetreatKeepsDrawAndFires(GameTestHelper helper) {
        ledgeRetreat(helper, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 260)
    public static void strafingClimbsAdjacentTerraceAndKeepsShooting(GameTestHelper helper) {
        terraceStrafe(helper, true, false, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 260)
    public static void strafingDescendsAdjacentTerraceAndKeepsShooting(GameTestHelper helper) {
        terraceStrafe(helper, false, false, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 260)
    public static void strafingRejectsDeepTerraceDrop(GameTestHelper helper) {
        terraceStrafe(helper, false, true, false);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 260)
    public static void strafingRejectsTerraceWithoutHeadroom(GameTestHelper helper) {
        terraceStrafe(helper, true, false, true);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 260)
    public static void strafingStepsOntoHalfSlabWithoutJumping(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        buildTerrace(a, true, false, false);
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                if (Math.abs(z) >= 1) {
                    helper.getLevel().setBlock(a.floor.offset(x, 0, z), Blocks.STONE_SLAB.defaultBlockState(), 3);
                }
            }
        }
        a.target(12);
        boolean[] crossed = {false};
        a.observe(() -> {
            if (RangedCombatState.current(a.archer).orElse(null) == RangedCombatState.STRAFE) {
                helper.assertTrue(a.archer.getDeltaMovement().y < 0.2,
                        "collision-sized slab step unnecessarily requested a jump" + a.describe());
                crossed[0] |= a.archer.onGround() && Math.abs(a.archer.getY() - a.origin.y - 0.5) < 0.02
                        && Math.abs(a.archer.getZ() - a.origin.z) >= 1;
            }
            if (crossed[0] && a.shots.size() >= 2 && a.target.getHealth() < a.target.getMaxHealth()) a.succeed();
            else if (helper.getTick() == 220) helper.fail("strafe did not step onto its half slab and shoot" + a.describe());
        });
    }

    private static void terraceStrafe(GameTestHelper helper, boolean ascent, boolean deepDrop, boolean lowRoof) {
        Arena a = new Arena(helper, ascent ? 0 : 1);
        buildTerrace(a, ascent, deepDrop, lowRoof);
        a.target(12);
        boolean[] crossed = {false}, previouslyUsing = {false}, resumedStrafe = {false};
        int[] previousShots = {0};
        a.observe(() -> {
            boolean using = a.archer.isUsingItem();
            var state = RangedCombatState.current(a.archer).orElse(null);
            helper.assertTrue(state == null || state == RangedCombatState.HOLD || state == RangedCombatState.STRAFE,
                    "local terrace movement left ordinary firing/strafe state" + a.describe());
            if (state == RangedCombatState.STRAFE) {
                helper.assertTrue(a.archer.getNavigation().isDone()
                                && !a.archer.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
                                && !a.archer.getBrain().hasMemoryValue(MemoryModuleType.PATH),
                        "local strafe created a navigation route" + a.describe());
            }
            boolean changedHeight = ascent ? a.archer.getY() >= a.origin.y + 0.95
                    : a.archer.getY() <= a.origin.y - 0.95;
            crossed[0] |= a.archer.onGround() && changedHeight && Math.abs(a.archer.getZ() - a.origin.z) >= 1;
            resumedStrafe[0] |= crossed[0] && RangedCombatState.current(a.archer).orElse(null) == RangedCombatState.STRAFE;
            helper.assertTrue(!previouslyUsing[0] || using || a.shots.size() > previousShots[0],
                    "lateral step discarded a bow draw" + a.describe());
            previouslyUsing[0] = using;
            previousShots[0] = a.shots.size();
            if (deepDrop || lowRoof) {
                helper.assertTrue(!changedHeight, "strafe crossed an unsafe terrace edge" + a.describe());
                if (helper.getTick() == 220) {
                    helper.assertTrue(a.shots.size() >= 2, "blocked terrace stopped shooting" + a.describe());
                    a.succeed();
                }
            } else if (crossed[0] && resumedStrafe[0] && a.shots.size() >= 2 && a.target.getHealth() < a.target.getMaxHealth()) {
                a.succeed();
            } else if (helper.getTick() == 220) {
                helper.fail("strafe never crossed its adjacent terrace; crossed=" + crossed[0]
                        + ", resumedStrafe=" + resumedStrafe[0] + a.describe());
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 60)
    public static void unfinishedBowDrawSurvivesDirectTerrainStrafe(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        buildTerrace(a, true, false, false);
        a.archer.setNoAi(true);
        a.target(12);
        a.archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, a.target);
        long now = helper.getLevel().getGameTime();
        BowTask<VillagerEntityMCA> bow = new BowTask<>(20, 15);
        helper.assertTrue(bow.tryStart(helper.getLevel(), a.archer, now), "fixture did not start its bow task");
        bow.tick(helper.getLevel(), a.archer, now);
        helper.runAfterDelay(8, () -> {
            int charge = a.archer.getTicksUsingItem();
            helper.assertTrue(a.archer.isUsingItem() && charge >= 6 && charge < 20,
                    "fixture must enter the handoff with an unfinished real draw");
            ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
            movement.start(helper.getLevel(), a.archer, now + 8);
            for (int tick = 1; tick <= 20; tick++) movement.tick(helper.getLevel(), a.archer, now + 8 + tick);
            bow.tick(helper.getLevel(), a.archer, now + 29);
            helper.assertTrue(a.archer.isUsingItem() && a.archer.getTicksUsingItem() >= charge,
                    "terrain strafe discarded an unfinished bow draw");
            helper.assertTrue(RangedCombatState.current(a.archer).orElse(null) == RangedCombatState.STRAFE
                            && a.archer.getNavigation().isDone()
                            && !a.archer.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                    "local step required a navigation handoff instead of direct strafing");
            a.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 40)
    public static void lateralAdmissionDoesNotSubstituteForwardMovementBesideWall(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        a.archer.setNoAi(true);
        a.archer.absMoveTo(a.origin.x, a.origin.y, a.floor.getZ() + 0.65);
        a.archer.setYRot(0);
        helper.getLevel().setBlock(a.floor.south(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(a.floor.south().above(), Blocks.STONE.defaultBlockState(), 3);
        helper.assertTrue(helper.getLevel().noCollision(a.archer), "wall fixture overlaps the starting body");
        var control = (net.conczin.mca.entity.ai.MCAMoveControl) a.archer.getMoveControl();
        control.strafe(0, 0.35F, 0.5);
        control.tick();
        helper.assertTrue(a.archer.xxa > 0 && a.archer.zza == 0,
                "safe sideways input was replaced by forward input into the adjacent wall");
        a.succeed();
    }

    private static void buildTerrace(Arena a, boolean ascent, boolean deepDrop, boolean lowRoof) {
        GameTestHelper helper = a.helper;
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                boolean side = Math.abs(z) >= 1;
                if (ascent ? side : !side) {
                    helper.getLevel().setBlock(a.floor.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
                }
                if (side && deepDrop) {
                    for (int y = -1; y >= -3; y--) {
                        BlockPos below = a.floor.offset(x, y, z);
                        a.terrain.putIfAbsent(below, helper.getLevel().getBlockState(below));
                        helper.getLevel().setBlock(below, Blocks.AIR.defaultBlockState(), 3);
                    }
                    // Two blocks below the elevated starting terrace, not an
                    // unbounded void: one-block descent must remain the limit.
                    helper.getLevel().setBlock(a.floor.below(2).offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
                }
                if (side && lowRoof) {
                    helper.getLevel().setBlock(a.floor.offset(x, 2, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
    }

    private static void ledgeRetreat(GameTestHelper helper, boolean ascent) {
        Arena a = new Arena(helper, ascent ? 0 : 1);
        // A broad shelf leaves native pathfinding free to choose a retreat route,
        // but every westward exit must cross its one-block height transition.
        for (int x = -20; x <= 20; x++) {
            for (int z = -20; z <= 20; z++) {
                if (ascent ? x <= -2 : x >= -1) {
                    helper.getLevel().setBlock(a.floor.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
            for (int y = 0; y <= 4; y++) {
                helper.getLevel().setBlock(a.floor.offset(x, y, -3), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(a.floor.offset(x, y, 3), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        a.target(5);
        boolean[] drew = {false}, crossed = {false}, previouslyUsing = {false};
        int[] previousShots = {0}, airborneDrawTicks = {0};
        a.observe(() -> {
            boolean using = a.archer.isUsingItem();
            drew[0] |= using;
            if (using && !a.archer.onGround()) airborneDrawTicks[0]++;
            crossed[0] |= ascent ? a.archer.getY() >= a.origin.y + 0.95
                    : a.archer.getY() <= a.origin.y - 0.95;
            helper.assertTrue(!previouslyUsing[0] || using || a.shots.size() > previousShots[0],
                    "ordinary ledge traversal discarded an unfinished bow draw" + a.describe());
            // Acquisition can occur after an ordinary stroll has turned the mob.
            // Let the bounded 30-degree look control settle before measuring pose.
            if (using && a.archer.getTicksUsingItem() >= 8) {
                helper.assertTrue(a.archer.isAggressive(), "jumping changed combat facing while drawing" + a.describe());
                float targetYaw = (float) Math.toDegrees(Math.atan2(a.target.getZ() - a.archer.getZ(),
                        a.target.getX() - a.archer.getX())) - 90;
                helper.assertTrue(Math.abs(net.minecraft.util.Mth.wrapDegrees(targetYaw - a.archer.yBodyRot)) < 30,
                        "ordinary ledge traversal turned the body away from its target" + a.describe());
            }
            previouslyUsing[0] = using;
            previousShots[0] = a.shots.size();
            if (crossed[0] && a.shots.size() >= 2 && a.target.getHealth() < a.target.getMaxHealth()) {
                helper.assertTrue(drew[0] && airborneDrawTicks[0] > 0,
                        "fixture did not exercise a bow draw during airborne traversal" + a.describe());
                helper.assertTrue(a.archer.position().distanceToSqr(a.origin) >= 4,
                        "archer did not physically leave the ledge" + a.describe());
                a.succeed();
            } else if (helper.getTick() == 140) {
                helper.fail("ledge retreat did not cross and shoot; crossed=" + crossed[0]
                        + ", airborneDrawTicks=" + airborneDrawTicks[0] + a.describe());
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 120)
    public static void combatAcquisitionStopsAlreadyRunningStroll(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        WalkTarget stroll = new WalkTarget(a.origin.add(-15, 0, 0), 0.5F, 0);
        a.archer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, stroll);
        Vec3[] combatStart = {null};
        long[] acquiredAt = {-1};
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(!a.archer.getNavigation().isDone()
                            && a.archer.getBrain().hasMemoryValue(MemoryModuleType.PATH),
                    "fixture did not start a real Brain-owned stroll");
            a.target(12);
        });
        a.observe(() -> {
            if (acquiredAt[0] < 0 && a.target != null
                    && a.archer.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == a.target
                    && RangedCombatState.current(a.archer).isPresent()) {
                acquiredAt[0] = helper.getTick();
                combatStart[0] = a.archer.position();
            }
            if (acquiredAt[0] >= 0 && helper.getTick() - acquiredAt[0] == 8) {
                helper.assertTrue(a.archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) != stroll,
                        "combat retained the already-running ordinary stroll" + a.describe());
                helper.assertTrue(a.archer.getNavigation().isDone(), "stroll navigation survived combat handoff" + a.describe());
                helper.assertTrue(a.archer.position().distanceToSqr(combatStart[0]) < 1,
                        "pre-combat stroll kept moving the archer after acquisition" + a.describe());
                a.succeed();
            } else if (helper.getTick() == 90) {
                helper.fail("fixture never naturally acquired its combat target" + a.describe());
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void becomingArcherWhileWalkingAcquiresAndHitsEnemy(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        a.archer.setProfession(VillagerProfession.NONE);
        a.archer.getBrain().setSchedule(new ScheduleBuilder(new Schedule()).changeActivityAt(0, Activity.IDLE).build());
        a.archer.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        a.archer.getInventory().addItem(Items.BOW.getDefaultInstance());
        WalkTarget stroll = new WalkTarget(a.origin.add(-15, 0, 0), 0.5F, 0);
        a.archer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, stroll);
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(!a.archer.getNavigation().isDone(), "unemployed fixture never began walking");
            a.archer.setProfession(ProfessionsMCA.ARCHER);
            a.archer.getBrain().setSchedule(new ScheduleBuilder(new Schedule()).changeActivityAt(0, Activity.IDLE).build());
            a.archer.getBrain().setActiveActivityIfPossible(Activity.IDLE);
            a.target(12);
        });
        a.observe(() -> {
            if (a.target != null && a.shots.size() >= 2 && a.target.getHealth() < a.target.getMaxHealth()) {
                helper.assertTrue(a.archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) != stroll,
                        "new archer retained the old profession's stroll" + a.describe());
                a.succeed();
            } else if (helper.getTick() == 140) {
                helper.fail("new archer did not naturally equip, acquire and hit its enemy" + a.describe());
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 340)
    public static void repeatedShortStrafesLeaveNarrowFiringNook(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        for (int x = -3; x <= 2; x++) {
            for (int y = 0; y <= 3; y++) {
                for (int z = -2; z <= 2; z++) {
                    if (Math.abs(z) == 2 || x == -3) {
                        helper.getLevel().setBlock(a.floor.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
                    }
                }
            }
        }
        a.target(12);
        int[] bursts = {0};
        int[] previousShots = {0};
        boolean[] previouslyDrawing = {false};
        boolean[] startedBoundaryDraw = {false};
        boolean[] strafing = {false}, relocated = {false};
        a.observe(() -> {
            var state = RangedCombatState.current(a.archer).orElse(null);
            helper.assertTrue(state != RangedCombatState.RECOVER || !previouslyDrawing[0]
                            || a.archer.isUsingItem() || a.shots.size() > previousShots[0],
                    "optional roomier relocation discarded an unfinished clear bow draw" + a.describe());
            previouslyDrawing[0] = a.archer.isUsingItem();
            previousShots[0] = a.shots.size();
            boolean nowStrafing = state == RangedCombatState.STRAFE;
            if (nowStrafing && !strafing[0]) bursts[0]++;
            if (!startedBoundaryDraw[0] && bursts[0] >= 2 && strafing[0]
                    && state == RangedCombatState.HOLD) {
                // Enter the optional-relocation boundary with a fresh unfinished
                // real draw, rather than depending on its random cooldown phase.
                a.archer.stopUsingItem();
                a.archer.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
                previouslyDrawing[0] = true;
                startedBoundaryDraw[0] = true;
            }
            strafing[0] = nowStrafing;
            relocated[0] |= a.archer.getBoundingBox().minX > a.floor.getX() + 3;
            if (relocated[0] && a.shots.size() >= 2 && a.target.getHealth() < a.target.getMaxHealth()) {
                helper.assertTrue(bursts[0] >= 2, "fixture did not exercise repeated short strafes" + a.describe());
                a.succeed();
            } else if (helper.getTick() == 300) {
                helper.fail("short left/right strafes never left the narrow firing nook; bursts=" + bursts[0] + a.describe());
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 360)
    public static void raisedCoveredPassageExitsDownwardAndRegainsShot(GameTestHelper helper) {
        Arena a = new Arena(helper, 1);
        for (int x = -12; x <= 8; x++) {
            for (int z = -12; z <= 12; z++) {
                for (int y = 0; y <= 6; y++) {
                    if (x < -1 || z != 0 || y >= 3) {
                        helper.getLevel().setBlock(a.floor.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
                    }
                }
            }
        }
        helper.getLevel().setBlock(a.floor, Blocks.STONE.defaultBlockState(), 3);
        a.target(12);
        a.target.setPos(a.origin.add(12, -1, 8));
        a.archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, a.target);
        a.archer.getBrain().setActiveActivityIfPossible(Activity.FIGHT);
        helper.assertTrue(!a.archer.hasLineOfSight(a.target), "passage must hide the initial shot");
        var exitPath = a.archer.getNavigation().createPath(a.floor.east(10), 0);
        helper.assertTrue(exitPath != null && exitPath.canReach(), "fixture requires a reachable downward exit");
        boolean[] descended = {false}, committedThroughCover = {false};
        a.observe(() -> {
            var path = a.archer.getBrain().getMemory(MemoryModuleType.PATH).orElse(null);
            helper.assertTrue(path == null || path.canReach(), "relocation selected an unreachable firing endpoint" + a.describe());
            if (helper.getTick() == 100) {
                helper.assertTrue(a.archer.getX() > a.origin.x + 2,
                        "lost sight left the archer stationary at its raised exit for five seconds" + a.describe());
            }
            descended[0] |= a.archer.getY() < a.origin.y - 0.8;
            committedThroughCover[0] |= a.archer.getX() > a.origin.x + 3 && !a.archer.hasLineOfSight(a.target);
            if (a.archer.getX() > a.origin.x + 8.5 && a.shots.size() >= 1 && a.target.getHealth() < a.target.getMaxHealth()) {
                helper.assertTrue(descended[0] && committedThroughCover[0], "exit must descend and cross temporary cover" + a.describe());
                a.succeed();
            } else if (helper.getTick() == 320) {
                helper.fail("archer stayed on raised ground instead of following the covered exit" + a.describe());
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 360)
    public static void raisedRoomDoorwayLeadsToOpenFiringSpace(GameTestHelper helper) {
        roomExit(helper, true);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 360)
    public static void levelRoomDoorwayLeadsToOpenFiringSpace(GameTestHelper helper) {
        roomExit(helper, false);
    }

    private static void roomExit(GameTestHelper helper, boolean raised) {
        int feetY = raised ? 1 : 0;
        Arena a = new Arena(helper, feetY);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                helper.getLevel().setBlock(a.floor.offset(x, feetY - 1, z), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(a.floor.offset(x, feetY + 3, z), Blocks.STONE.defaultBlockState(), 3);
                if (Math.abs(x) == 3 || Math.abs(z) == 3) {
                    for (int y = feetY; y < feetY + 3; y++) {
                        helper.getLevel().setBlock(a.floor.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
                    }
                }
            }
        }
        BlockPos door = a.floor.offset(3, feetY, 0);
        BlockState lower = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.EAST);
        helper.getLevel().setBlock(door, lower, 3);
        helper.getLevel().setBlock(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 3);
        a.target(12);
        a.target.setPos(a.origin.add(12, -feetY, 0));
        a.archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, a.target);
        a.archer.getBrain().setActiveActivityIfPossible(Activity.FIGHT);
        var path = a.archer.getNavigation().createPath(a.floor.east(6), 0);
        helper.assertTrue(path != null && path.canReach(), "closed wooden doorway must have a native exit route");
        helper.assertTrue(!a.archer.hasLineOfSight(a.target), "closed door must block the initial eye ray");
        boolean[] leftRoom = {false};
        a.observe(() -> {
            leftRoom[0] |= a.archer.getBoundingBox().minX > a.floor.getX() + 4
                    && Math.abs(a.archer.getY() - a.floor.getY()) < 0.1;
            if (leftRoom[0] && a.shots.size() >= 2 && a.target.getHealth() < a.target.getMaxHealth()) a.succeed();
            else if (helper.getTick() == 320) helper.fail("archer did not leave its "
                    + (raised ? "raised" : "level") + " room through the native doorway and shoot" + a.describe());
        });
    }

    private static final class Arena {
        private final GameTestHelper helper;
        private final BlockPos floor;
        private final VillagerEntityMCA archer;
        private final Vec3 origin;
        private final Set<UUID> shots = new HashSet<>();
        private final List<ChunkPos> addedChunks = new ArrayList<>();
        private final Map<BlockPos, BlockState> terrain = new LinkedHashMap<>();
        private Mob target;
        private boolean closed;

        private Arena(GameTestHelper helper, int elevation) {
            this.helper = helper;
            floor = helper.absolutePos(new BlockPos(56, 3, 56));
            ARENAS.add(this);
            Set<Long> forced = new HashSet<>(helper.getLevel().getForcedChunks());
            for (BlockPos pos : BlockPos.betweenClosed(floor.offset(-24, -1, -24), floor.offset(24, 8, 24))) {
                terrain.put(pos.immutable(), helper.getLevel().getBlockState(pos));
            }
            prepareFlatArea(helper, floor, 24, 8);
            for (long chunk : helper.getLevel().getForcedChunks()) {
                if (!forced.contains(chunk)) addedChunks.add(new ChunkPos(chunk));
            }
            origin = Vec3.atBottomCenterOf(floor.above(elevation));
            archer = VillagerFactory.newVillager(helper.getLevel()).withAge(0)
                    .withProfession(ProfessionsMCA.ARCHER).withPosition(origin)
                    .withName("Live Regression Archer").spawn(MobSpawnType.STRUCTURE);
            archer.getTraits().removeTrait(Traits.DWARFISM);
            archer.getGenetics().setGene(Genetics.SIZE, 0.5F);
            archer.getGenetics().setGene(Genetics.WIDTH, 0.5F);
            archer.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            archer.setHealth(100);
            archer.setItemSlot(EquipmentSlot.MAINHAND, Items.BOW.getDefaultInstance());
            archer.refreshBrain(helper.getLevel());
            archer.getBrain().setSchedule(new ScheduleBuilder(new Schedule()).changeActivityAt(0, Activity.IDLE).build());
            archer.getBrain().setActiveActivityIfPossible(Activity.IDLE);
            archer.setYRot(270);
            archer.yHeadRot = archer.yBodyRot = 270;
            archer.setOnGround(true);
        }

        private void target(int distance) {
            Skeleton skeleton = EntityType.SKELETON.create(helper.getLevel());
            if (skeleton == null) throw new IllegalStateException("No skeleton for live regression");
            // Isolate the guard's spacing from the enemy's own retreat. This target
            // advertises an active attack but cannot move or knock the guard back.
            skeleton.setNoAi(true);
            skeleton.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
            skeleton.setHealth(200);
            skeleton.setItemSlot(EquipmentSlot.HEAD, Items.IRON_HELMET.getDefaultInstance());
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, Items.BOW.getDefaultInstance());
            skeleton.absMoveTo(archer.getX() + distance, archer.getY(), archer.getZ());
            skeleton.setTarget(archer);
            helper.getLevel().addFreshEntity(skeleton);
            target = skeleton;
        }

        private void observe(Runnable observation) {
            helper.onEachTick(() -> {
                if (closed) return;
                helper.getLevel().getEntitiesOfClass(AbstractArrow.class, archer.getBoundingBox().inflate(40),
                        arrow -> arrow.getOwner() == archer).forEach(arrow -> shots.add(arrow.getUUID()));
                observation.run();
            });
        }

        private String describe() {
            return "; tick=" + helper.getTick() + ", pos=" + archer.position()
                    + ", state=" + RangedCombatState.current(archer) + ", shots=" + shots.size()
                    + ", useTicks=" + archer.getTicksUsingItem() + ", ground=" + archer.onGround()
                    + ", path=" + archer.getBrain().getMemory(MemoryModuleType.PATH)
                    + ", walk=" + archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
        }

        private void succeed() {
            MCA.LOGGER.info("[MCA Archer Regression] passed tick={} shots={} damage={} progress={}",
                    helper.getTick(), shots.size(), target == null ? 0 : target.getMaxHealth() - target.getHealth(),
                    archer.position().distanceTo(origin));
            close();
            helper.succeed();
        }

        private void close() {
            if (closed) return;
            closed = true;
            if (archer != null) {
                helper.getLevel().getEntitiesOfClass(AbstractArrow.class, archer.getBoundingBox().inflate(40),
                        arrow -> arrow.getOwner() == archer).forEach(Entity::discard);
                archer.discard();
            }
            if (target != null) target.discard();
            terrain.forEach((pos, state) -> helper.getLevel().setBlock(pos, state, 3));
            for (ChunkPos chunk : addedChunks) helper.getLevel().setChunkForced(chunk.x, chunk.z, false);
        }
    }
}
