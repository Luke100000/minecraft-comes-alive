package net.conczin.mca.entity.ai.brain.tasks;

import com.mojang.authlib.GameProfile;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Genetics;
import net.conczin.mca.entity.ai.MCABodyRotationControl;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.Traits;
import net.conczin.mca.entity.ai.SchedulesMCA;
import net.conczin.mca.entity.ai.navigation.CombatEscapePositionTracker;
import net.conczin.mca.entity.ai.navigation.PersistentPathTarget;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.conczin.mca.neoforge.gametest.BeforeBatch;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.schedule.Activity;


import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;

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
    private static final String NIGHT_BATCH = "archer_night_corner";
    private static final String PHANTOM_KILL_BATCH = "archer_phantom_kill";
    private static final String UNDER_FIRE_BATCH = "archer_under_fire_review";
    private static final List<Arena> ARENAS = new ArrayList<>();
    private static long savedDayTime;
    private static boolean savedDaylightCycle;

    private ArcherLiveRegressionGameTests() { }

    @AfterBatch(batch = BATCH)
    public static void cleanup(ServerLevel level) {
        for (Arena arena : ARENAS) arena.close();
        ARENAS.clear();
    }

    @AfterBatch(batch = UNDER_FIRE_BATCH)
    public static void cleanupUnderFire(ServerLevel level) {
        cleanup(level);
    }

    @GameTest(batch = UNDER_FIRE_BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 480)
    public static void activeSkeletonPressureSeed42(GameTestHelper helper) { underFireFight(helper, 42); }

    @GameTest(batch = UNDER_FIRE_BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 480)
    public static void activeSkeletonPressureSeed84(GameTestHelper helper) { underFireFight(helper, 84); }

    @GameTest(batch = UNDER_FIRE_BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 480)
    public static void activeSkeletonPressureSeed126(GameTestHelper helper) { underFireFight(helper, 126); }

    @GameTest(batch = UNDER_FIRE_BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 480)
    public static void activeSkeletonPressureSeed168(GameTestHelper helper) { underFireFight(helper, 168); }

    private static void underFireFight(GameTestHelper helper, long seed) {
        Arena a = new Arena(helper, 0);
        // Keep random traits such as TOUGH from changing damage resistance
        // or body width between the two movement variants.
        for (Traits.Trait trait : a.archer.getTraits().getTraits()) a.archer.getTraits().removeTrait(trait);
        a.archer.updateAttributes();
        a.archer.refreshDimensions();
        a.target(12);
        a.archer.getRandom().setSeed(seed);
        a.target.getRandom().setSeed(seed + 1);
        a.target.setNoAi(false);
        Set<UUID> incomingArrows = new HashSet<>();
        int[] holdTicks = {0};
        int[] strafeTicks = {0};
        float[] previousHealth = {a.archer.getHealth()};
        float[] damage = {0};
        a.observe(() -> {
            helper.getLevel().getEntitiesOfClass(AbstractArrow.class, a.archer.getBoundingBox().inflate(40),
                    arrow -> arrow.getOwner() == a.target).forEach(arrow -> incomingArrows.add(arrow.getUUID()));
            RangedCombatState state = RangedCombatState.current(a.archer).orElse(null);
            if (state == RangedCombatState.HOLD) holdTicks[0]++;
            if (state == RangedCombatState.STRAFE) strafeTicks[0]++;
            damage[0] += Math.max(0.0F, previousHealth[0] - a.archer.getHealth());
            previousHealth[0] = a.archer.getHealth();
            if (helper.getTick() == 400) {
                helper.assertTrue(incomingArrows.size() >= 2 && a.shots.size() >= 2,
                        "under-fire fixture did not exercise both shooters; enemyShots=" + incomingArrows.size() + a.describe());
                MCA.LOGGER.info("[MCA Archer UnderFire] variant={} seed={} damageTaken={} damageDealt={} shots={} enemyShots={} holdTicks={} strafeTicks={}",
                        System.getProperty("mca.archer.reviewVariant", "bounded"), seed, damage[0],
                        a.target.getMaxHealth() - a.target.getHealth(), a.shots.size(), incomingArrows.size(), holdTicks[0], strafeTicks[0]);
                for (UUID arrowId : incomingArrows) {
                    Entity arrow = helper.getLevel().getEntity(arrowId);
                    if (arrow != null) arrow.discard();
                }
                a.succeed();
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void activeRangedPressureKeepsStrafingThroughBowCycles(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        a.target(12);
        a.archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, a.target);
        int[] movingTicks = {0};
        long[] pressureEndedAt = {-1};
        a.observe(() -> {
            RangedCombatState state = RangedCombatState.current(a.archer).orElse(null);
            if (pressureEndedAt[0] >= 0) {
                if (state == RangedCombatState.HOLD) {
                    helper.assertTrue(a.shots.size() >= 2 && a.target.getHealth() < a.target.getMaxHealth(),
                            "continuous movement prevented repeated damaging shots" + a.describe());
                    a.succeed();
                } else if (helper.getTick() - pressureEndedAt[0] >= 5) {
                    helper.fail("strafe did not return to ordinary holding after ranged pressure ended" + a.describe());
                }
                return;
            }
            if (state == RangedCombatState.STRAFE) {
                helper.assertTrue(Math.abs(a.archer.xxa) > 0.01F,
                        "STRAFE retained its state but released lateral input under fire" + a.describe());
                if (++movingTicks[0] >= 100) {
                    a.target.setTarget(null);
                    pressureEndedAt[0] = helper.getTick();
                }
            } else if (movingTicks[0] > 0) {
                helper.fail("safe strafe planted its feet while the ranged enemy still targeted it; movingTicks="
                        + movingTicks[0] + a.describe());
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 160)
    public static void rangedPressureHonorsBlockedStrafeCooldown(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        a.target(12);
        a.archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, a.target);
        int[] movingTicks = {0};
        boolean[] blocked = {false};
        long[] stoppedAt = {-1};
        a.observe(() -> {
            RangedCombatState state = RangedCombatState.current(a.archer).orElse(null);
            if (!blocked[0]) {
                if (state == RangedCombatState.STRAFE && ++movingTicks[0] >= 5) {
                    Vec3 heading = a.archer.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).normalize();
                    helper.assertTrue(heading.lengthSqr() > 0.9D, "fixture did not establish physical lateral movement");
                    BlockPos wall = BlockPos.containing(a.archer.position().add(heading.scale(1.25D)));
                    for (int y = 0; y < 3; y++) {
                        helper.getLevel().setBlock(wall.above(y), Blocks.STONE.defaultBlockState(), 3);
                    }
                    helper.assertTrue(helper.getLevel().noCollision(a.archer), "fixture placed its wall inside the archer");
                    helper.assertTrue(a.archer.hasLineOfSight(a.target), "fixture wall occluded the ranged attacker");
                    blocked[0] = true;
                }
                return;
            }
            if (stoppedAt[0] < 0) {
                if (state != RangedCombatState.STRAFE) stoppedAt[0] = helper.getTick();
                else if (movingTicks[0]++ > 25) helper.fail("ranged pressure continued strafing into a blocked step" + a.describe());
                return;
            }
            helper.assertTrue(state != RangedCombatState.STRAFE,
                    "ranged pressure bypassed the blocked-step cooldown" + a.describe());
            if (helper.getTick() - stoppedAt[0] >= 18) a.succeed();
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void chargedArcherEscapesPursuingSpiderWithoutRestartingDraw(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        for (int x = -7; x <= -2; x++) {
            for (int z = -23; z <= 23; z++) {
                helper.getLevel().setBlock(a.floor.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        var spider = EntityTypes.SPIDER.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
        if (spider == null) throw new IllegalStateException("No spider for pursuit regression");
        spider.snapTo(a.origin.x + 7, a.origin.y, a.origin.z);
        spider.setNoAi(true);
        spider.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        spider.setHealth(200);
        helper.getLevel().addFreshEntity(spider);
        a.target = spider;
        a.archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, spider);
        boolean[] started = {false}, emergency = {false}, climbed = {false};
        a.observe(() -> {
            if (!started[0]) {
                if (a.archer.isUsingItem() && a.archer.getTicksUsingItem() >= 8) {
                    spider.snapTo(a.archer.getX() + 2, a.archer.getY(), a.archer.getZ());
                    spider.setTarget(a.archer);
                    spider.setNoAi(false);
                    started[0] = true;
                }
                return;
            }
            RangedCombatState state = RangedCombatState.current(a.archer).orElse(null);
            emergency[0] |= state == RangedCombatState.EMERGENCY_FLEE;
            climbed[0] |= a.archer.onGround() && a.archer.getY() > a.origin.y + 0.9D;
            if (state == RangedCombatState.EMERGENCY_FLEE && a.shots.isEmpty()) {
                helper.assertTrue(a.archer.isUsingItem(), "emergency flight discarded the unfinished draw" + a.describe());
            }
            helper.assertTrue(a.archer.isAlive(), "pursuing spider killed the escaping archer" + a.describe());
            if (a.shots.size() >= 2 && a.target.getHealth() < a.target.getMaxHealth() && climbed[0]) {
                helper.assertTrue(emergency[0] && a.archer.position().distanceToSqr(a.origin) >= 9,
                        "fixture did not escape onto its raised terrace" + a.describe());
                a.succeed();
            } else if (helper.getTick() == 210) {
                helper.fail("pursuing spider/ledge escape failed to produce repeated damaging shots; climbed="
                        + climbed[0] + a.describe());
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 40)
    public static void armedFacingTurnsGraduallyWhenCombatStartsAndEnds(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        a.archer.setNoAi(true);
        boolean[] aiming = {true};
        MCABodyRotationControl body = new MCABodyRotationControl(a.archer, () -> aiming[0]);
        a.archer.yBodyRot = 90;
        a.archer.yHeadRot = 270;
        for (int tick = 0; tick < 6; tick++) {
            float previous = a.archer.yBodyRot;
            body.clientTick();
            helper.assertTrue(Math.abs(Mth.wrapDegrees(a.archer.yBodyRot - previous)) <= 30.01F,
                    "combat admission snapped the displayed body");
        }
        helper.assertTrue(Math.abs(Mth.wrapDegrees(a.archer.yBodyRot - 270)) < 1,
                "bounded combat facing never reached the enemy bearing");
        aiming[0] = false;
        a.archer.setYRot(90);
        a.archer.xo = a.archer.getX() - 0.1;
        for (int tick = 0; tick < 6; tick++) {
            float previous = a.archer.yBodyRot;
            body.clientTick();
            helper.assertTrue(Math.abs(Mth.wrapDegrees(a.archer.yBodyRot - previous)) <= 30.01F,
                    "emergency travel handoff snapped the displayed body");
        }
        helper.assertTrue(Math.abs(Mth.wrapDegrees(a.archer.yBodyRot - 90)) < 1,
                "bounded travel facing never reached the route bearing");
        a.archer.setItemSlot(EquipmentSlot.MAINHAND, Items.AIR.getDefaultInstance());
        a.archer.setYRot(0);
        body.clientTick();
        helper.assertTrue(a.archer.yBodyRot == 0, "unarmed movement stopped using native body control");
        a.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 40)
    public static void distantGuardAlarmDoesNotReplaceArcherFight(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        a.archer.setNoAi(true);
        a.target(12);
        a.archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, a.target);
        var alarm = EntityTypes.SKELETON.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
        if (alarm == null) throw new IllegalStateException("No alarm attacker");
        alarm.setNoAi(true);
        alarm.snapTo(a.origin.x + 20, a.origin.y, a.origin.z);
        helper.getLevel().addFreshEntity(alarm);
        a.extraEntities.add(alarm);
        var victim = VillagerFactory.newVillager(helper.getLevel()).withAge(0)
                .withProfession(net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE).value())
                .withPosition(a.origin.add(16, 0, 0)).spawn(EntitySpawnReason.STRUCTURE);
        victim.setNoAi(true);
        a.extraEntities.add(victim);
        helper.assertTrue(victim.hurtServer(helper.getLevel(), helper.getLevel().damageSources().mobAttack(alarm), 1),
                "fixture damage did not trigger a neighbour's alarm");
        helper.assertTrue(a.archer.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == a.target,
                "distant neighbour alarm overwrote the archer's valid fight");
        a.archer.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        victim.invulnerableTime = 0;
        victim.hurt(helper.getLevel().damageSources().mobAttack(alarm), 1);
        helper.assertTrue(a.archer.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == alarm,
                "idle archer no longer responded to the neighbour's alarm");
        a.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 40)
    public static void escapeStateChangeUpdatesSpeedWithoutReplacingPath(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        a.archer.setNoAi(true);
        a.target(2);
        a.archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, a.target);
        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
        long now = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), a.archer, now);
        movement.tick(helper.getLevel(), a.archer, now + 1);
        var oldWalk = a.archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
        helper.assertTrue(oldWalk.getTarget() instanceof CombatEscapePositionTracker,
                "fixture did not publish an emergency escape");
        var escape = (CombatEscapePositionTracker) oldWalk.getTarget();
        var path = a.archer.getNavigation().createPath(escape.getPathTargets(a.archer), 0);
        helper.assertTrue(path != null && path.canReach(), "fixture escape path is unreachable");
        a.archer.getNavigation().moveTo(path, oldWalk.getSpeedModifier());
        a.archer.getBrain().setMemory(MemoryModuleType.PATH, path);
        a.target.snapTo(a.origin.x + 6, a.origin.y, a.origin.z);
        movement.tick(helper.getLevel(), a.archer, now + 2);
        helper.assertTrue(RangedCombatState.current(a.archer).orElse(null) == RangedCombatState.KITE,
                "fixture did not leave emergency flight");
        var walk = a.archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
        helper.assertTrue(walk.getTarget() == escape && a.archer.getNavigation().getPath() == path,
                "changing escape speed discarded the route");
        a.archer.getNavigation().tick();
        helper.assertTrue(walk.getSpeedModifier() <= 0.75F
                        && a.archer.getMoveControl().getSpeedModifier() <= 0.75001D,
                "KITE retained the emergency sprint speed; walk=" + walk.getSpeedModifier()
                        + ", control=" + a.archer.getMoveControl().getSpeedModifier());
        a.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 300)
    public static void hiddenSkeletonInsideMeleeBandAllowsHillFiringRoute(GameTestHelper helper) {
        hillFiringRoute(helper, 5);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 300)
    public static void hiddenSkeletonInsideRangedBandAllowsHillFiringRoute(GameTestHelper helper) {
        hillFiringRoute(helper, 7);
    }

    private static void hillFiringRoute(GameTestHelper helper, int distance) {
        Arena a = new Arena(helper, 0);
        // A two-block ridge blocks both shooters, with native step-up and
        // ground-level routes around its ends inside the allocated arena.
        for (int x = 1; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                for (int y = 0; y < (x == 2 ? 2 : 1); y++) {
                    helper.getLevel().setBlock(a.floor.offset(x, y, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        a.target(distance);
        if (distance == 7) a.target.setNoAi(false);
        a.archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, a.target);
        helper.assertTrue(!a.archer.hasLineOfSight(a.target), "fixture ridge does not hide the skeleton");
        int[] hiddenTicks = {0};
        boolean[] soughtShot = {false};
        a.observe(() -> {
            boolean visible = a.archer.hasLineOfSight(a.target);
            hiddenTicks[0] = visible ? 0 : hiddenTicks[0] + 1;
            var state = RangedCombatState.current(a.archer).orElse(null);
            soughtShot[0] |= state == RangedCombatState.REPOSITION;
            if (hiddenTicks[0] > 4 && a.archer.onGround()) {
                helper.assertTrue(state != RangedCombatState.KITE && state != RangedCombatState.EMERGENCY_FLEE,
                        "hidden bow skeleton interrupted the hill firing route with retreat" + a.describe());
            }
            helper.assertTrue(a.archer.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == a.target,
                    "hill encounter forgot its valid combat target" + a.describe());
            if (a.shots.size() >= 2 && a.target.getHealth() < a.target.getMaxHealth()) {
                helper.assertTrue(soughtShot[0] && a.archer.position().distanceToSqr(a.origin) >= 1,
                        "fixture did not physically seek a firing position around its ridge" + a.describe());
                a.succeed();
            } else if (helper.getTick() == 270) {
                helper.fail("hill encounter failed to regain sight and land repeated shots" + a.describe());
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 40)
    public static void hiddenRangedRetreatKeepsBriefGraceThenYieldsToShotSeeking(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        a.archer.setNoAi(true);
        a.target(7);
        a.archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, a.target);
        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
        long now = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), a.archer, now);
        movement.tick(helper.getLevel(), a.archer, now + 1);
        var escape = a.archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
        for (int z = -3; z <= 3; z++) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(a.floor.offset(3, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        helper.assertTrue(!a.archer.hasLineOfSight(a.target), "fixture failed to occlude the ranged threat");
        for (int tick = 1; tick <= 25; tick++) {
            a.archer.getSensing().tick();
            movement.tick(helper.getLevel(), a.archer, now + 1 + tick);
            var state = RangedCombatState.current(a.archer).orElse(null);
            if (tick <= 4) {
                helper.assertTrue(state == RangedCombatState.KITE
                                && a.archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == escape,
                        "brief occlusion restarted the committed ranged escape");
            } else {
                helper.assertTrue(state != RangedCombatState.KITE && state != RangedCombatState.EMERGENCY_FLEE,
                        "grounded escape remained committed after the skeleton lost its firing line" + a.describe());
            }
        }
        helper.assertTrue(RangedCombatState.current(a.archer).orElse(null) == RangedCombatState.REPOSITION,
                "sustained occlusion did not seek a firing position" + a.describe());
        a.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 200)
    public static void archerConversionAcquiresThreatAndDrawsWithoutSensorWarmup(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        a.archer.setProfession(net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE).value());
        a.archer.setItemSlot(EquipmentSlot.MAINHAND, Items.AIR.getDefaultInstance());
        long[] convertedAt = {-1};
        helper.runAfterDelay(30, () -> {
            helper.assertTrue(a.archer.tickCount >= 5, "conversion fixture is not entity-ticking");
            a.target(12);
            a.archer.getBrain().eraseMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY);
            a.archer.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            a.archer.setProfession(ProfessionsMCA.ARCHER);
            convertedAt[0] = helper.getTick();
            helper.assertTrue(a.archer.getBrain().getMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY).orElse(null) == a.target,
                    "conversion left the archer waiting for its first guard sensor scan");
        });
        boolean[] drewPromptly = {false};
        a.observe(() -> {
            if (convertedAt[0] < 0) return;
            drewPromptly[0] |= a.archer.isUsingItem();
            if (helper.getTick() - convertedAt[0] >= 4) {
                helper.assertTrue(drewPromptly[0], "new archer did not equip, acquire and start a real draw promptly" + a.describe());
            }
            if (a.shots.size() >= 2 && a.target.getHealth() < a.target.getMaxHealth()) a.succeed();
            else if (helper.getTick() == 170) helper.fail("converted archer did not land repeated shots" + a.describe());
        });
    }

    @BeforeBatch(batch = NIGHT_BATCH)
    public static void startNight(ServerLevel level) {
        savedDayTime = level.clockManager().getTotalTicks(level.dimensionType().defaultClock().orElseThrow());
        savedDaylightCycle = level.getGameRules().get(GameRules.ADVANCE_TIME);
        level.getGameRules().set(GameRules.ADVANCE_TIME, false, level.getServer());
        level.clockManager().setTotalTicks(level.dimensionType().defaultClock().orElseThrow(), 18000);
    }

    @AfterBatch(batch = NIGHT_BATCH)
    public static void finishNight(ServerLevel level) {
        cleanup(level);
        level.clockManager().setTotalTicks(level.dimensionType().defaultClock().orElseThrow(), savedDayTime);
        level.getGameRules().set(GameRules.ADVANCE_TIME, savedDaylightCycle, level.getServer());
    }

    @BeforeBatch(batch = PHANTOM_KILL_BATCH)
    public static void startPhantomNight(ServerLevel level) {
        startNight(level);
    }

    @AfterBatch(batch = PHANTOM_KILL_BATCH)
    public static void finishPhantomNight(ServerLevel level) {
        finishNight(level);
    }

    @GameTest(batch = PHANTOM_KILL_BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 2040)
    public static void archerKillsNaturallySwoopingPhantomSeed42(GameTestHelper helper) {
        swoopingPhantom(helper, 42);
    }

    @GameTest(batch = PHANTOM_KILL_BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 2040)
    public static void archerKillsNaturallySwoopingPhantomSeed84(GameTestHelper helper) {
        swoopingPhantom(helper, 84);
    }

    private static void swoopingPhantom(GameTestHelper helper, long seed) {
        Arena a = new Arena(helper, 0);
        a.archer.getAttribute(Attributes.MAX_HEALTH).setBaseValue(20);
        a.archer.setHealth(20);
        Phantom phantom = spawnPhantom(a, seed, 34);
        // Vanilla reacquires players after each swoop. A one-time villager
        // target only exercises the first pass and cannot prove a whole fight.
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "PhantomBait")) {
            @Override
            public boolean isInvulnerableTo(ServerLevel level, net.minecraft.world.damagesource.DamageSource source) {
                return true;
            }
        };
        player.setInvulnerable(false);
        player.setGameMode(GameType.SURVIVAL);
        player.snapTo(a.origin.x + 8, a.origin.y, a.origin.z);
        helper.getLevel().addNewPlayer(player);
        a.extraEntities.add(player);
        float startingHealth = phantom.getHealth();
        helper.assertTrue(startingHealth == 20, "phantom must start with its full vanilla health");
        int[] shootingOpportunities = {0};
        int[] arrowHits = {0};
        float[] previousHealth = {startingHealth};
        a.observe(() -> {
            helper.assertTrue(a.archer.isAlive(), "archer died before killing the native swooping phantom; seed="
                    + seed + ", phantomHealth=" + phantom.getHealth() + a.describe());
            if (a.archer.distanceToSqr(phantom) <= 225 && a.archer.hasLineOfSight(phantom)) {
                shootingOpportunities[0]++;
            }
            var damage = phantom.getLastDamageSource();
            if (phantom.getHealth() < previousHealth[0]) {
                helper.assertTrue(damage != null && damage.getEntity() == a.archer
                                && damage.getDirectEntity() instanceof AbstractArrow,
                        "phantom took damage from outside the archer's arrows; seed=" + seed);
                arrowHits[0]++;
            }
            previousHealth[0] = phantom.getHealth();
            if (!phantom.isAlive()) {
                helper.assertTrue(phantom.getHealth() <= 0 && damage != null && damage.getEntity() == a.archer
                                && damage.getDirectEntity() instanceof AbstractArrow,
                        "phantom died without a lethal archer-owned arrow; seed=" + seed + a.describe());
                helper.assertTrue(arrowHits[0] >= 2 && a.shots.size() >= 2,
                        "phantom kill did not exercise repeated arrow hits; seed=" + seed + a.describe());
                MCA.LOGGER.info("[MCA Archer Phantom Kill] seed={} tick={} opportunities={} shots={} hits={} damage={} archerHealth={}",
                        seed, helper.getTick(), shootingOpportunities[0], a.shots.size(), arrowHits[0],
                        startingHealth - phantom.getHealth(), a.archer.getHealth());
                a.succeed();
            } else {
                helper.assertTrue(phantom.getTarget() == null || phantom.getTarget() == player,
                        "native phantom acquired a target outside its fixture");
                if (helper.getTick() == 2000) {
                    helper.fail("archer failed to kill the native swooping phantom; seed=" + seed
                            + ", opportunities=" + shootingOpportunities[0] + ", hits=" + arrowHits[0]
                            + ", phantomHealth=" + phantom.getHealth()
                            + ", phantom=" + phantom.position() + a.describe());
                }
            }
        });
    }

    @GameTest(batch = NIGHT_BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 280)
    public static void highCirclingPhantomDoesNotMonopolizeCombat(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        // Remain inside the proposed 30-block acquisition radius, so reducing
        // that radius alone cannot make this regression pass.
        Phantom phantom = spawnPhantom(a, 42, 24);
        Zombie[] zombie = {null};
        long[] acquiredAt = {-1};
        a.observe(() -> {
            helper.assertTrue(a.archer.distanceToSqr(phantom) > 225,
                    "circling phantom unexpectedly entered bow range");
            if (acquiredAt[0] < 0) {
                if (a.archer.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, phantom)) {
                    acquiredAt[0] = helper.getTick();
                } else if (helper.getTick() == 40) {
                    helper.fail("fixture did not naturally acquire the distant circling phantom" + a.describe());
                }
                return;
            }
            if (helper.getTick() - acquiredAt[0] == 15) {
                helper.assertTrue(RangedCombatState.current(a.archer).orElse(null) == RangedCombatState.HOLD
                                && a.archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isEmpty(),
                        "archer walked after a circling phantom above vertical bow reach" + a.describe());
            }
            if (zombie[0] == null && helper.getTick() - acquiredAt[0] >= 20) {
                zombie[0] = EntityTypes.ZOMBIE.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
                if (zombie[0] == null) throw new IllegalStateException("No zombie for phantom priority regression");
                // Keep the ground target stationary so this checks acquisition and
                // actual shooting, rather than the close-attacker emergency override.
                zombie[0].setNoAi(true);
                zombie[0].snapTo(a.archer.getX() + 10, a.origin.y, a.archer.getZ());
                helper.getLevel().addFreshEntity(zombie[0]);
                a.extraEntities.add(zombie[0]);
                helper.assertTrue(a.archer.hasLineOfSight(zombie[0]), "ground enemy must offer a visible shot");
            }
            if (zombie[0] != null) {
                var damage = zombie[0].getLastDamageSource();
                if (damage != null && damage.getEntity() == a.archer && damage.getDirectEntity() instanceof AbstractArrow) {
                    a.succeed();
                } else if (helper.getTick() - acquiredAt[0] >= 200) {
                    helper.fail("unshootable circling phantom monopolized combat while a visible zombie was in range"
                            + "; phantom=" + phantom.position() + ", zombie=" + zombie[0].position() + a.describe());
                }
            }
        });
    }

    @GameTest(batch = NIGHT_BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 160)
    public static void archerDoesNotAcquirePhantomBeyondThirtyBlocks(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        Phantom phantom = spawnPhantom(a, 42, 34);
        a.observe(() -> {
            helper.assertTrue(a.archer.distanceToSqr(phantom) > 900,
                    "fixture phantom unexpectedly entered the 30-block acquisition radius");
            helper.assertTrue(!a.archer.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, phantom),
                    "archer acquired a distant ambient phantom beyond 30 blocks" + a.describe());
            if (helper.getTick() == 100) {
                helper.assertTrue(a.archer.tickCount >= 80 && phantom.tickCount >= 80,
                        "range fixture actors did not entity-tick");
                a.succeed();
            }
        });
    }

    private static Phantom spawnPhantom(Arena a, long seed, int elevation) {
        Phantom phantom = EntityTypes.PHANTOM.create(a.helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
        if (phantom == null) throw new IllegalStateException("No phantom for combat regression");
        phantom.getRandom().setSeed(seed);
        a.archer.getRandom().setSeed(seed + 1);
        phantom.snapTo(a.origin.x + 6, a.origin.y + elevation, a.origin.z);
        phantom.finalizeSpawn(a.helper.getLevel(), a.helper.getLevel().getCurrentDifficultyAt(phantom.blockPosition()),
                EntitySpawnReason.STRUCTURE, null);
        phantom.setPersistenceRequired();
        a.helper.getLevel().addFreshEntity(phantom);
        a.target = phantom;
        return phantom;
    }

    @GameTest(batch = NIGHT_BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 480)
    public static void nightArcherRoutesAroundLWallWithoutForgettingTarget(GameTestHelper helper) {
        nightCorner(helper, false);
    }

    @GameTest(batch = NIGHT_BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 480)
    public static void nightArcherReturnsToShotAfterEmergencyFleeAroundLWall(GameTestHelper helper) {
        nightCorner(helper, true);
    }

    @GameTest(batch = NIGHT_BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 480)
    public static void reportedVillageCornerRestoresShootingAfterFlee(GameTestHelper helper) {
        reportedNightCorner(helper);
    }

    /** Optional terrain replay, only from an isolated copy of the reported save. */
    private static void reportedNightCorner(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        Vec3i size = new Vec3i(40, 22, 40);
        StructureTemplate terrain;
        if (Boolean.getBoolean("mca.archer.reportedWorld")) {
            BlockPos source = new BlockPos(603, 68, -891);
            for (int x = source.getX() >> 4; x <= (source.getX() + size.getX()) >> 4; x++) {
                for (int z = source.getZ() >> 4; z <= (source.getZ() + size.getZ()) >> 4; z++) {
                    helper.getLevel().getChunk(x, z);
                }
            }
            helper.assertTrue(!helper.getLevel().getBlockState(new BlockPos(623, 76, -870)).isAir(),
                    "reported-world replay requires the saved village terrain, not the ordinary flat test world");
            terrain = new StructureTemplate();
            terrain.fillFromWorld(helper.getLevel(), source, size, false, null);
            String export = System.getProperty("mca.archer.terrainExport");
            if (export != null) {
                try {
                    NbtIo.writeCompressed(terrain.save(new CompoundTag()), java.nio.file.Path.of(export));
                } catch (java.io.IOException e) {
                    throw new IllegalStateException("Could not export the reported terrain fixture", e);
                }
            }
        } else {
            terrain = helper.getLevel().getStructureManager().get(Identifier.fromNamespaceAndPath(
                    "mca", "gametest/reported_archer_corner")).orElseThrow();
        }
        helper.assertTrue(terrain.getSize().equals(size), "reported terrain fixture has the wrong bounds");
        BlockPos destination = a.floor.offset(-20, -9, -21);
        a.capture(destination, destination.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1));
        helper.assertTrue(terrain.placeInWorld(helper.getLevel(), destination, destination,
                        new StructurePlaceSettings().setIgnoreEntities(true), helper.getLevel().getRandom(), 3),
                "could not place reported village terrain in the isolated arena");
        a.archer.snapTo(a.origin.x, a.origin.y, a.origin.z);
        a.target(1);
        a.target.setPos(a.origin.add(-1, -1, -2));
        a.archer.refreshBrain(helper.getLevel());
        a.archer.getBrain().setSchedule(SchedulesMCA.GUARD);
        a.archer.getBrain().setActiveActivityIfPossible(Activity.REST);
        a.archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, a.target);
        helper.assertTrue(helper.getLevel().noCollision(a.archer), "reported archer start overlaps terrain");
        boolean[] fled = {false};
        a.observe(() -> {
            fled[0] |= RangedCombatState.current(a.archer).orElse(null) == RangedCombatState.EMERGENCY_FLEE;
            helper.assertTrue(a.archer.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == a.target,
                    "reported-corner archer forgot its reachable skeleton" + a.describe());
            if (fled[0] && a.archer.position().distanceToSqr(a.origin) >= 16
                    && a.shots.size() >= 2 && a.target.getHealth() < a.target.getMaxHealth()) a.succeed();
            else if (helper.getTick() == 440) helper.fail("reported-corner archer fled without regaining a shot" + a.describe());
        });
    }

    private static void nightCorner(GameTestHelper helper, boolean fleeFirst) {
        Arena a = new Arena(helper, 0);
        for (int z = -5; z <= 5; z++) {
            for (int y = 0; y < 4; y++) {
                helper.getLevel().setBlock(a.floor.offset(4, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (int x = 4; x <= 12; x++) {
            for (int y = 0; y < 4; y++) {
                helper.getLevel().setBlock(a.floor.offset(x, y, 5), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        a.target(fleeFirst ? 2 : 8);
        a.target.setTarget(null);
        if (!fleeFirst) a.target.setPos(a.origin.add(8, 0, 3));
        // Retain the real guard tasks and REST schedule. Seed an existing fight,
        // since initial acquisition through an opaque wall is not the subject.
        a.archer.refreshBrain(helper.getLevel());
        a.archer.getBrain().setSchedule(SchedulesMCA.GUARD);
        a.archer.getBrain().setActiveActivityIfPossible(Activity.REST);
        a.archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, a.target);
        boolean[] fled = {!fleeFirst}, cornered = {!fleeFirst}, traversed = {false};
        if (!fleeFirst) helper.assertTrue(!a.archer.hasLineOfSight(a.target), "L-wall must obscure the initial target");
        a.observe(() -> {
            helper.assertTrue(net.conczin.mca.gametest.GameTestBrain.scheduledActivityAt(helper.getLevel(), a.archer, 18000) == Activity.REST,
                    "night fixture lost the native REST schedule");
            var state = RangedCombatState.current(a.archer).orElse(null);
            fled[0] |= state == RangedCombatState.EMERGENCY_FLEE;
            if (fleeFirst && !cornered[0] && fled[0] && a.archer.position().distanceToSqr(a.origin) >= 9) {
                a.target.setPos(a.origin.add(8, 0, 3));
                cornered[0] = true;
                helper.assertTrue(!a.archer.hasLineOfSight(a.target), "post-flee target must be hidden by the L-wall");
            }
            traversed[0] |= a.archer.getX() > a.origin.x + 4 && a.archer.getZ() < a.origin.z - 5;
            helper.assertTrue(a.archer.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == a.target,
                    "night archer forgot a reachable target behind an L-wall" + a.describe());
            if (cornered[0] && traversed[0] && a.shots.size() >= 2 && a.target.getHealth() < a.target.getMaxHealth()) {
                helper.assertTrue(fled[0], "fixture never exercised emergency flee");
                a.succeed();
            } else if (helper.getTick() == 440) {
                helper.fail("night archer did not navigate around the L-wall and resume shooting; fled="
                        + fled[0] + ", cornered=" + cornered[0] + ", traversed=" + traversed[0] + a.describe());
            }
        });
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
        // Exercise retreat from an engaged close attack, not sensor-acquisition timing.
        a.archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, a.target);
        helper.assertTrue(a.archer.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == a.target,
                "retreat fixture must begin with the close ranged attacker selected" + a.describe());
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

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void combatStartReleasesAlreadyPublishedStrollMotion(GameTestHelper helper) {
        Arena a = new Arena(helper, 1);
        buildTerrace(a, false, true, false);
        a.target(12);
        // Test the stationary handoff; active ranged pressure intentionally
        // permits a validated lateral command on the first combat tick.
        a.target.setTarget(null);
        a.archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, a.target);
        BlockPos strollGoal = a.floor.offset(0, -1, 4);
        Vec3 destination = Vec3.atBottomCenterOf(strollGoal);
        a.archer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(strollGoal, 0.5F, 0));
        helper.assertTrue(a.archer.getNavigation().moveTo(destination.x, destination.y, destination.z, 0.5D),
                "fixture did not create its pre-combat stroll route");
        a.archer.getBrain().setMemory(MemoryModuleType.PATH, a.archer.getNavigation().getPath());
        // Navigation runs before Brain; its old movement command can already
        // be published when the combat behavior starts.
        a.archer.getMoveControl().setWantedPosition(destination.x, destination.y, destination.z, 0.5D);
        a.archer.setDeltaMovement(Vec3.ZERO);
        Vec3 origin = a.archer.position();
        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
        long now = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), a.archer, now);
        movement.tick(helper.getLevel(), a.archer, now + 1);
        helper.assertTrue(RangedCombatState.current(a.archer).orElse(null) == RangedCombatState.HOLD,
                "fixture did not enter combat HOLD");
        a.archer.getMoveControl().tick();
        a.archer.travel(new Vec3(a.archer.xxa, 0.0D, a.archer.zza));
        helper.assertTrue(a.archer.getNavigation().isDone()
                        && a.archer.position().subtract(origin).horizontalDistanceSqr() < 1.0E-8D,
                "combat HOLD retained the released stroll's native path or movement command" + a.describe());
        a.succeed();
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 260)
    public static void emergencyRetreatStopsAtSafeShotBeforeLeavingBowRange(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        for (int x = -23; x <= -4; x++) {
            for (int z = -23; z <= 23; z++) {
                helper.getLevel().setBlock(a.floor.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        a.target(2);
        boolean[] fled = {false};
        boolean[] climbed = {false};
        a.observe(() -> {
            RangedCombatState state = RangedCombatState.current(a.archer).orElse(null);
            fled[0] |= state == RangedCombatState.EMERGENCY_FLEE;
            climbed[0] |= a.archer.onGround() && a.archer.getY() > a.origin.y + 0.9D;
            helper.assertTrue(a.archer.distanceToSqr(a.target) <= 225.0D,
                    "emergency-to-kite retreat kept following a distant waypoint beyond bow range" + a.describe());
            if (helper.getTick() == 220) {
                helper.assertTrue(fled[0] && climbed[0], "fixture did not flee onto the raised ground" + a.describe());
                helper.assertTrue(a.shots.size() >= 2 && a.target.getHealth() < a.target.getMaxHealth(),
                        "archer did not settle and land repeated shots after emergency retreat" + a.describe());
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
        // Exercise combat footing from the raised start, before idle strolling
        // can move the archer off its narrow terrace during sensor startup.
        a.archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, a.target);
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
        a.archer.snapTo(a.origin.x, a.origin.y, a.floor.getZ() + 0.65);
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

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 200)
    public static void combatAcquisitionStopsAlreadyRunningPersistentJourney(GameTestHelper helper) {
        persistentJourneyCombatHandoff(helper, false, 12);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 200)
    public static void combatRejectsPersistentJourneyPublishedAfterAcquisition(GameTestHelper helper) {
        persistentJourneyCombatHandoff(helper, true, 12);
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void approachRejectsPersistentJourneyPublishedDuringCombat(GameTestHelper helper) {
        persistentJourneyCombatHandoff(helper, true, 24);
    }

    private static void persistentJourneyCombatHandoff(GameTestHelper helper, boolean publishDuringCombat, int targetDistance) {
        Arena a = new Arena(helper, 0);
        WalkTarget journey = new WalkTarget(new PersistentPathTarget(a.floor.west(20), Activity.IDLE), 0.5F, 0);
        if (publishDuringCombat) {
            a.target(targetDistance);
        } else {
            a.archer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, journey);
            helper.runAfterDelay(8, () -> {
                helper.assertTrue(!a.archer.getNavigation().isDone()
                                && a.archer.getBrain().hasMemoryValue(MemoryModuleType.PATH),
                        "persistent journey fixture never began navigating");
                a.target(targetDistance);
            });
        }
        long[] acquiredAt = {-1};
        Vec3[] combatStart = {null};
        a.observe(() -> {
            if (acquiredAt[0] < 0 && a.target != null
                    && a.archer.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, a.target)
                    && RangedCombatState.current(a.archer).isPresent()) {
                acquiredAt[0] = helper.getTick();
                combatStart[0] = a.archer.position();
                if (publishDuringCombat) a.archer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, journey);
            }
            if (acquiredAt[0] >= 0 && helper.getTick() - acquiredAt[0] == 8) {
                helper.assertTrue(a.archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) != journey,
                        "persistent routine travel kept steering an archer in combat" + a.describe());
                helper.assertTrue(targetDistance > 15 || a.archer.getNavigation().isDone(),
                        "persistent routine navigation survived the combat handoff" + a.describe());
                helper.assertTrue(a.archer.position().distanceToSqr(combatStart[0]) < 1,
                        "archer kept walking away from its opponent during combat" + a.describe());
            }
            if (a.target != null && a.shots.size() >= 2 && a.target.getHealth() < a.target.getMaxHealth()) {
                a.succeed();
            } else if (helper.getTick() == 220) {
                helper.fail("persistent journey prevented repeated damaging combat shots" + a.describe());
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 200)
    public static void combatPausesFollowingRouteWithoutForgettingPlayer(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.snapTo(a.origin.x - 20, a.origin.y, a.origin.z);
        a.archer.getBrain().setMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING, player);
        a.archer.refreshBrain(helper.getLevel());
        WalkTarget following = new WalkTarget(player, 0.5F, 0);
        a.archer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, following);
        helper.runAfterDelay(8, () -> a.target(12));
        long[] acquiredAt = {-1};
        a.observe(() -> {
            if (a.target != null && acquiredAt[0] < 0
                    && a.archer.getBrain().isMemoryValue(MemoryModuleType.ATTACK_TARGET, a.target)) {
                acquiredAt[0] = helper.getTick();
            }
            helper.assertTrue(a.archer.getBrain().isMemoryValue(MemoryModuleTypeMCA.PLAYER_FOLLOWING, player),
                    "combat discarded the follow instruction instead of pausing travel");
            if (acquiredAt[0] >= 0 && helper.getTick() - acquiredAt[0] == 8) {
                helper.assertTrue(a.archer.getNavigation().isDone(),
                        "following route kept dragging a fighting archer away" + a.describe());
            }
            if (a.target != null && a.shots.size() >= 2 && a.target.getHealth() < a.target.getMaxHealth()) {
                a.succeed();
            } else if (helper.getTick() == 170) {
                helper.fail("following archer never settled into repeated damaging shots" + a.describe());
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 440)
    public static void archerArrowsKillFullHealthOutlaw(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        VillagerEntityMCA outlaw = VillagerFactory.newVillager(helper.getLevel()).withAge(0)
                .withProfession(ProfessionsMCA.OUTLAW).withPosition(a.origin.add(10, 0, 0))
                .withName("Arrow Collision Outlaw").spawn(EntitySpawnReason.STRUCTURE);
        outlaw.setNoAi(true);
        a.target = outlaw;
        float startingHealth = outlaw.getHealth();
        helper.assertTrue(outlaw.isHostile() && startingHealth == outlaw.getMaxHealth(),
                "outlaw must start hostile and at full health");
        a.observe(() -> {
            if (!outlaw.isAlive()) {
                var damage = outlaw.getLastDamageSource();
                helper.assertTrue(outlaw.getHealth() <= 0 && a.shots.size() >= 2
                                && damage != null && damage.getEntity() == a.archer
                                && damage.getDirectEntity() instanceof AbstractArrow,
                        "outlaw did not die from repeated archer arrows" + a.describe());
                a.succeed();
            } else if (helper.getTick() == 400) {
                helper.fail("archer arrows passed through the hostile outlaw; health=" + outlaw.getHealth()
                        + ", startingHealth=" + startingHealth + a.describe());
            }
        });
    }

    @GameTest(batch = BATCH, templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void becomingArcherWhileWalkingAcquiresAndHitsEnemy(GameTestHelper helper) {
        Arena a = new Arena(helper, 0);
        a.archer.setProfession(net.minecraft.core.registries.BuiltInRegistries.VILLAGER_PROFESSION.getOrThrow(VillagerProfession.NONE).value());
        a.archer.getBrain().setSchedule(net.conczin.mca.gametest.GameTestBrain.fixedSchedule(Activity.IDLE));
        a.archer.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        a.archer.getInventory().addItem(Items.BOW.getDefaultInstance());
        WalkTarget stroll = new WalkTarget(a.origin.add(-15, 0, 0), 0.5F, 0);
        a.archer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, stroll);
        helper.runAfterDelay(8, () -> {
            helper.assertTrue(!a.archer.getNavigation().isDone(), "unemployed fixture never began walking");
            a.archer.setProfession(ProfessionsMCA.ARCHER);
            a.archer.getBrain().setSchedule(net.conczin.mca.gametest.GameTestBrain.fixedSchedule(Activity.IDLE));
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
        private final List<Entity> extraEntities = new ArrayList<>();
        private final List<ChunkPos> addedChunks = new ArrayList<>();
        private final Map<BlockPos, BlockState> terrain = new LinkedHashMap<>();
        private Mob target;
        private boolean closed;

        private void capture(BlockPos minimum, BlockPos maximum) {
            for (BlockPos pos : BlockPos.betweenClosed(minimum, maximum)) {
                terrain.putIfAbsent(pos.immutable(), helper.getLevel().getBlockState(pos));
            }
        }

        private Arena(GameTestHelper helper, int elevation) {
            this.helper = helper;
            floor = helper.absolutePos(new BlockPos(56, 3, 56));
            ARENAS.add(this);
            Set<Long> forced = new HashSet<>(helper.getLevel().getForceLoadedChunks());
            for (BlockPos pos : BlockPos.betweenClosed(floor.offset(-24, -1, -24), floor.offset(24, 8, 24))) {
                terrain.put(pos.immutable(), helper.getLevel().getBlockState(pos));
            }
            prepareFlatArea(helper, floor, 24, 8);
            for (long chunk : helper.getLevel().getForceLoadedChunks()) {
                if (!forced.contains(chunk)) addedChunks.add(ChunkPos.unpack(chunk));
            }
            origin = Vec3.atBottomCenterOf(floor.above(elevation));
            archer = VillagerFactory.newVillager(helper.getLevel()).withAge(0)
                    .withProfession(ProfessionsMCA.ARCHER).withPosition(origin)
                    .withName("Live Regression Archer").spawn(EntitySpawnReason.STRUCTURE);
            archer.getTraits().removeTrait(Traits.DWARFISM);
            archer.getGenetics().setGene(Genetics.SIZE, 0.5F);
            archer.getGenetics().setGene(Genetics.WIDTH, 0.5F);
            archer.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
            archer.setHealth(100);
            archer.setItemSlot(EquipmentSlot.MAINHAND, Items.BOW.getDefaultInstance());
            archer.refreshBrain(helper.getLevel());
            archer.getBrain().setSchedule(net.conczin.mca.gametest.GameTestBrain.fixedSchedule(Activity.IDLE));
            archer.getBrain().setActiveActivityIfPossible(Activity.IDLE);
            archer.setYRot(270);
            archer.yHeadRot = archer.yBodyRot = 270;
            archer.setOnGround(true);
        }

        private void target(int distance) {
            Skeleton skeleton = EntityTypes.SKELETON.create(helper.getLevel(), net.minecraft.world.entity.EntitySpawnReason.STRUCTURE);
            if (skeleton == null) throw new IllegalStateException("No skeleton for live regression");
            // Isolate the guard's spacing from the enemy's own retreat. This target
            // advertises an active attack but cannot move or knock the guard back.
            skeleton.setNoAi(true);
            skeleton.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
            skeleton.setHealth(200);
            skeleton.setItemSlot(EquipmentSlot.HEAD, Items.IRON_HELMET.getDefaultInstance());
            skeleton.setItemSlot(EquipmentSlot.MAINHAND, Items.BOW.getDefaultInstance());
            skeleton.snapTo(archer.getX() + distance, archer.getY(), archer.getZ());
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
                        arrow -> arrow.getOwner() == archer || arrow.getOwner() == target).forEach(Entity::discard);
                archer.discard();
            }
            if (target != null) target.discard();
            extraEntities.forEach(Entity::discard);
            terrain.forEach((pos, state) -> helper.getLevel().setBlock(pos, state, 3));
            for (ChunkPos chunk : addedChunks) helper.getLevel().setChunkForced(chunk.x(), chunk.z(), false);
        }
    }
}
