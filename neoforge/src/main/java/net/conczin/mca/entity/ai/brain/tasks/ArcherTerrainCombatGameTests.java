package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.brain.VillagerTasksMCA;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

@PrefixGameTestTemplate(false)
public final class ArcherTerrainCombatGameTests {
    private static final List<Entity> ACTORS = new ArrayList<>();
    private ArcherTerrainCombatGameTests() {
    }

    @AfterBatch(batch = "archer_terrain_recovery")
    public static void cleanupRecovery(ServerLevel level) { cleanup(level); }
    @AfterBatch(batch = "archer_terrain_blocked_arrow")
    public static void cleanupBlockedArrow(ServerLevel level) { cleanup(level); }
    @AfterBatch(batch = "archer_terrain_draw_cancel")
    public static void cleanupDraw(ServerLevel level) { cleanup(level); }
    @AfterBatch(batch = "archer_terrain_uphill_shots")
    public static void cleanupUphill(ServerLevel level) { cleanup(level); }
    @AfterBatch(batch = "archer_terrain_downhill_shots")
    public static void cleanupDownhill(ServerLevel level) { cleanup(level); }
    @AfterBatch(batch = "archer_terrain_target_priority")
    public static void cleanupPriority(ServerLevel level) { cleanup(level); }
    @AfterBatch(batch = "archer_terrain_descending_threat")
    public static void cleanupDescending(ServerLevel level) { cleanup(level); }
    @AfterBatch(batch = "archer_terrain_baby_chase")
    public static void cleanupBaby(ServerLevel level) { cleanup(level); }
    @AfterBatch(batch = "archer_terrain_adult_chase")
    public static void cleanupAdult(ServerLevel level) { cleanup(level); }
    @AfterBatch(batch = "archer_terrain_sealed_control")
    public static void cleanupSealed(ServerLevel level) { cleanup(level); }
    @AfterBatch(batch = "archer_terrain_natural_uphill")
    public static void cleanupNaturalUphill(ServerLevel level) { cleanup(level); }
    @AfterBatch(batch = "archer_terrain_natural_downhill")
    public static void cleanupNaturalDownhill(ServerLevel level) { cleanup(level); }
    @AfterBatch(batch = "archer_terrain_ledge_shot")
    public static void cleanupLedge(ServerLevel level) { cleanup(level); }
    @AfterBatch(batch = "archer_terrain_escape_commitment")
    public static void cleanupCommitment(ServerLevel level) { cleanup(level); }
    @AfterBatch(batch = "archer_terrain_drop_safety")
    public static void cleanupDrop(ServerLevel level) { cleanup(level); }
    @AfterBatch(batch = "archer_terrain_group_cost")
    public static void cleanupGroupCost(ServerLevel level) { cleanup(level); }
    @AfterBatch(batch = "archer_terrain_lost_contact")
    public static void cleanupContact(ServerLevel level) { cleanup(level); }

    @AfterBatch(batch = "archer_terrain_review")
    public static void cleanupReview(ServerLevel level) { cleanup(level); }

    @GameTest(batch = "archer_terrain_review", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 60)
    public static void underwaterBowBypassesAirPrediction(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(56, 3, 56));
        prepareFlatArea(helper, start, 16, 8);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        archer.setNoAi(true);
        Zombie target = spawnTarget(helper, start.east(10).below(3));
        // The air-arrow path is blocked; fallback weapons must retain their own
        // shooting contract rather than inherit this ordinary-arrow restriction.
        for (int x = 1; x <= 6; x++) {
            helper.getLevel().setBlock(start.east(x).below(), Blocks.STONE.defaultBlockState(), 3);
        }
        helper.assertTrue(!net.conczin.mca.entity.ai.RangedWeaponHelper.canShootBowFrom(archer, archer.position(), target),
                "fixture must block the ordinary nominal trajectory");
        helper.getLevel().setBlock(start, Blocks.WATER.defaultBlockState(), 3);
        helper.getLevel().setBlock(start.above(), Blocks.WATER.defaultBlockState(), 3);
        helper.runAfterDelay(2, () -> {
            helper.assertTrue(archer.isInWater(), "fixture must immerse the archer");
            helper.assertTrue(net.conczin.mca.entity.ai.RangedWeaponHelper.canShootBowFrom(archer, archer.position(), target),
                    "underwater fallback was rejected by air-arrow prediction");
            helper.succeed();
        });
    }

    @GameTest(batch = "archer_terrain_review", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 60)
    public static void ordinaryRetreatStaysValidAtJumpApex(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(56, 3, 56));
        prepareFlatArea(helper, start, 12, 6);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        archer.setNoAi(true);
        archer.setOnGround(true);
        var path = archer.getNavigation().createPath(start.west(6), 0);
        helper.assertTrue(path != null && path.canReach(), "fixture needs a reachable level retreat");
        path.setNextNodeIndex(Math.min(1, path.getNodeCount() - 1));
        archer.setPos(archer.getX(), archer.getY() + 1.2, archer.getZ());
        archer.setOnGround(false);
        var context = new RangedCombatPositioning.ThreatContext(List.of(), List.of(), null);
        helper.assertTrue(RangedCombatPositioning.isEscapePathSafe(archer, path, context, false),
                "jump apex was mistaken for a trapping terrain drop");
        helper.succeed();
    }

    @GameTest(batch = "archer_terrain_review", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 60)
    public static void recoveryRejectsUnsafeNativeDetour(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(56, 3, 56));
        prepareFlatArea(helper, start, 16, 7);
        for (int x = 2; x <= 8; x++) {
            helper.getLevel().setBlock(start.east(x), Blocks.STONE.defaultBlockState(), 3);
        }
        VillagerEntityMCA archer = spawnArcher(helper, start);
        archer.setNoAi(true);
        archer.setOnGround(true);
        Zombie target = spawnTarget(helper, start.east(14).above());
        Zombie hazard = spawnTarget(helper, start.north(8));
        var threats = new RangedCombatPositioning.ThreatContext(List.of(hazard), List.of(hazard), hazard);
        var recovery = RangedCombatPositioning.findRecoveryTarget(archer, target, threats, 256).orElseThrow();
        var path = archer.getNavigation().createPath(recovery.currentBlockPosition(), 0);
        helper.assertTrue(path != null && path.canReach(), "fixture needs a reachable rising recovery endpoint");
        // A later obstacle or moving enemy can make the actual route unsafe even
        // though the selected endpoint and its straight-line probe were safe.
        Vec3 intermediate = path.getEntityPosAtNode(archer, Math.min(2, path.getNodeCount() - 1));
        hazard.setPos(intermediate.add(0, 0, 1));
        helper.assertTrue(recovery instanceof net.conczin.mca.entity.ai.navigation.CombatEscapePositionTracker
                        && !((net.conczin.mca.entity.ai.navigation.CombatEscapePositionTracker) recovery).isPathSafe(archer, path),
                "recovery admitted a native route through a newly occupied melee hazard");
        helper.succeed();
    }

    @GameTest(batch = "archer_terrain_review", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 60)
    public static void recoveryCanSelectNativeExitBelowRaisedFloor(GameTestHelper helper) {
        BlockPos ground = helper.absolutePos(new BlockPos(56, 3, 56));
        prepareFlatArea(helper, ground, 16, 7);
        helper.getLevel().setBlock(ground, Blocks.STONE.defaultBlockState(), 3);
        VillagerEntityMCA archer = spawnArcher(helper, ground.above());
        archer.setNoAi(true);
        archer.setOnGround(true);
        Zombie target = spawnTarget(helper, ground.east(12));
        for (int y = 0; y <= 3; y++) {
            helper.getLevel().setBlock(ground.east(3).above(y), Blocks.STONE.defaultBlockState(), 3);
        }
        helper.assertTrue(!archer.hasLineOfSight(target), "raised block must begin with an obstructed shot");
        var threats = new RangedCombatPositioning.ThreatContext(List.of(), List.of(), null);
        var exit = RangedCombatPositioning.findRecoveryTarget(archer, target, threats, 225);
        helper.assertTrue(exit.isPresent(), "raised-floor recovery rejected every reachable downward exit");
        var path = archer.getNavigation().createPath(exit.orElseThrow().currentBlockPosition(), 0);
        helper.assertTrue(path != null && path.canReach(), "downward recovery endpoint is not natively reachable");
        helper.assertTrue(path.getEndNode().y == ground.getY(), "recovery did not choose the lower surrounding floor");
        helper.succeed();
    }

    @GameTest(batch = "archer_terrain_lost_contact", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 500)
    public static void unreachableUnseenTargetDoesNotKeepCombatForever(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(56, 3, 56));
        prepareFlatArea(helper, start, 16, 6);
        for (BlockPos side : List.of(start.east(), start.west(), start.north(), start.south())) {
            for (int y = 0; y < 4; y++) {
                helper.getLevel().setBlock(side.above(y), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(10));
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        helper.onEachTick(() -> {
            if (helper.getTick() >= 440) {
                helper.assertTrue(archer.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty(),
                        "unreachable unseen enemy kept archer in combat indefinitely");
                helper.succeed();
            }
        });
    }

    @GameTest(batch = "archer_terrain_group_cost", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 480)
    public static void groupsKeepNativeCombatRequestsBounded(GameTestHelper helper) {
        BlockPos center = helper.absolutePos(new BlockPos(56, 3, 56));
        prepareFlatArea(helper, center, 40, 7);
        int[] counts = {1, 8, 24};
        int[] stage = {0};
        List<VillagerEntityMCA> archers = new ArrayList<>();
        helper.onEachTick(() -> {
            long phaseTick = (helper.getTick() - 1) % 120;
            if (phaseTick == 0) {
                cleanup(helper.getLevel());
                archers.clear();
                for (int index = 0; index < counts[stage[0]]; index++) {
                    BlockPos position = center.offset((index % 6) * 6 - 18, 0, (index / 6) * 6 - 12);
                    VillagerEntityMCA archer = spawnArcher(helper, position);
                    Zombie target = spawnTarget(helper, position.east(10));
                    archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
                    archers.add(archer);
                }
            }
            if (phaseTick == 119) {
                long requests = 0;
                long nanos = 0;
                for (VillagerEntityMCA archer : archers) {
                    var snapshot = net.conczin.mca.entity.ai.navigation.PathRequestDiagnostics.snapshot(archer);
                    requests += snapshot.sinkRequests() + snapshot.directRequests();
                    nanos += snapshot.searchNanos();
                    helper.assertTrue(archer.tickCount >= 100, "group-cost actor must actually tick");
                }
                net.conczin.mca.MCA.LOGGER.info("[MCA Archer group acceptance] archers={} ticks=120 nativeRequests={} searchMs={}",
                        counts[stage[0]], requests, nanos / 1.0E6);
                helper.assertTrue(requests < counts[stage[0]] * 60L,
                        "combat repeatedly republished native path requests: " + requests);
                if (++stage[0] == counts.length) {
                    helper.succeed();
                }
            }
        });
    }

    @GameTest(batch = "archer_terrain_drop_safety", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 80)
    public static void ordinaryEscapeRejectsTwoBlockDropWhenLevelExitExists(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(56, 5, 56));
        prepareFlatArea(helper, start.below(2), 12, 8);
        for (int x = -10; x <= 10; x++) {
            for (int z = -10; z <= 10; z++) {
                helper.getLevel().setBlock(start.offset(x, -2, z), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(start.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        BlockPos hole = start.west(2).below(2);
        helper.getLevel().setBlock(hole, Blocks.AIR.defaultBlockState(), 3);
        helper.getLevel().setBlock(hole.above(), Blocks.AIR.defaultBlockState(), 3);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        archer.setNoAi(true);
        archer.setOnGround(true);
        Zombie threat = spawnTarget(helper, start.east(2));
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, threat);
        var dropPath = archer.getNavigation().createPath(hole, 0);
        var levelPath = archer.getNavigation().createPath(start.north(6), 0);
        helper.assertTrue(dropPath != null && dropPath.canReach(), "fixture requires a native descent into the pit");
        helper.assertTrue(levelPath != null && levelPath.canReach(), "fixture requires a safe level alternative");
        var context = RangedCombatPositioning.collectThreatContext(archer, threat);
        helper.assertTrue(RangedCombatPositioning.isEscapePathSafe(archer, levelPath, context, false),
                "ordinary escape rejected the level alternative");
        helper.assertTrue(!RangedCombatPositioning.isEscapePathSafe(archer, dropPath, context, false),
                "ordinary escape accepted a drop with no return step despite a level alternative");
        helper.assertTrue(!RangedCombatPositioning.isEscapePathSafe(archer, dropPath, context, true),
                "emergency clearance fallback accepted a trapping two-block drop");
        helper.succeed();
    }

    @GameTest(batch = "archer_terrain_escape_commitment", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 60)
    public static void spacingExitKeepsOwnedRetreatUntilSafeWaypoint(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(56, 3, 56));
        prepareFlatArea(helper, start, 16, 5);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        archer.setNoAi(true);
        archer.setOnGround(true);
        Zombie target = spawnTarget(helper, start.east(2));
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
        long now = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), archer, now);
        movement.tick(helper.getLevel(), archer, now + 1);
        var escape = archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
        target.absMoveTo(start.getX() + 10.5, start.getY(), start.getZ() + 0.5);
        movement.tick(helper.getLevel(), archer, now + 2);
        helper.assertTrue(archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == escape,
                "spacing threshold discarded owned retreat before its safe waypoint");
        helper.succeed();
    }

    @GameTest(batch = "archer_terrain_ledge_shot", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 300)
    public static void blockedLedgeMakesArcherSeekClearShot(GameTestHelper helper) {
        BlockPos low = helper.absolutePos(new BlockPos(56, 3, 56));
        prepareFlatArea(helper, low, 20, 12);
        BlockPos start = low.above(5);
        for (int x = -4; x <= 3; x++) {
            helper.getLevel().setBlock(start.offset(x, -1, 0), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 0; y <= 3; y++) {
                helper.getLevel().setBlock(start.offset(x, y, -1), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(start.offset(x, y, 1), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, low.east(10));
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        helper.assertTrue(!net.conczin.mca.entity.ai.RangedWeaponHelper.canShootBowFrom(archer, archer.position(), target),
                "ledge must obstruct the nominal arrow");
        helper.onEachTick(() -> {
            if (target.getHealth() < 195 && archer.getX() > start.getX() + 1) {
                helper.succeed();
            } else if (helper.getTick() >= 240) {
                helper.fail("visible target kept archer behind an obstructing lip; pos=" + archer.position()
                        + ", targetHealth=" + target.getHealth() + ", state=" + RangedCombatState.current(archer));
            }
        });
    }

    @GameTest(batch = "archer_terrain_baby_chase", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 420)
    public static void archerEngagesPursuingBabyZombie(GameTestHelper helper) {
        runPursuit(helper, true);
    }

    @GameTest(batch = "archer_terrain_adult_chase", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 420)
    public static void archerEngagesPursuingAdultZombie(GameTestHelper helper) {
        runPursuit(helper, false);
    }

    private static void runPursuit(GameTestHelper helper, boolean baby) {
        BlockPos start = helper.absolutePos(new BlockPos(56, 3, 56));
        prepareFlatArea(helper, start, 40, 6);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        archer.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        archer.setHealth(200);
        Zombie pursuer = spawnTarget(helper, start.east(8));
        pursuer.setBaby(baby);
        pursuer.setNoAi(false);
        pursuer.setTarget(archer);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, pursuer);
        float[] previousHealth = {pursuer.getHealth()};
        int[] hits = {0};
        boolean[] escaped = {false};
        helper.onEachTick(() -> {
            escaped[0] |= archer.position().distanceToSqr(Vec3.atBottomCenterOf(start)) >= 9;
            if (pursuer.getHealth() < previousHealth[0]) {
                hits[0]++;
                previousHealth[0] = pursuer.getHealth();
            }
            if (hits[0] >= 2 && escaped[0]) {
                helper.succeed();
            } else if (helper.getTick() >= 360) {
                helper.fail("natural " + (baby ? "baby" : "adult") + " pursuit: hits=" + hits[0]
                        + ", moved=" + escaped[0] + ", archer=" + archer.position()
                        + ", pursuer=" + pursuer.position() + ", state=" + RangedCombatState.current(archer)
                        + ", actorTicks=" + archer.tickCount + ", health=" + archer.getHealth()
                        + ", activity=" + archer.getBrain().getActiveNonCoreActivity()
                        + ", target=" + archer.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET));
            }
        });
    }

    @GameTest(batch = "archer_terrain_sealed_control", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 280)
    public static void archerDoesNotBlindlyPursueThroughSealedCorridor(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(56, 3, 56));
        prepareFlatArea(helper, start, 16, 5);
        for (int x = -14; x <= 14; x++) {
            for (int y = 0; y <= 4; y++) {
                helper.getLevel().setBlock(start.offset(x, y, -1), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(start.offset(x, y, 1), Blocks.STONE.defaultBlockState(), 3);
                if (x == 8 || x == 11) {
                    helper.getLevel().setBlock(start.offset(x, y, 0), Blocks.STONE.defaultBlockState(), 3);
                }
            }
            helper.getLevel().setBlock(start.offset(x, 3, 0), Blocks.STONE.defaultBlockState(), 3);
        }
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(10));
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        helper.onEachTick(() -> {
            helper.assertTrue(archer.position().distanceToSqr(Vec3.atBottomCenterOf(start)) <= 1,
                    "sealed-corridor recovery became blind pursuit");
            if (helper.getTick() >= 220) {
                helper.succeed();
            }
        });
    }

    private static void cleanup(ServerLevel level) {
        for (Entity actor : ACTORS) {
            if (actor instanceof VillagerEntityMCA) {
                level.getEntitiesOfClass(AbstractArrow.class, actor.getBoundingBox().inflate(32),
                        arrow -> arrow.getOwner() == actor).forEach(AbstractArrow::discard);
            }
            actor.discard();
        }
        ACTORS.clear();
    }

    @GameTest(batch = "archer_terrain_target_priority", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 60)
    public static void nearbyAttackerInterruptsDistantArcherTarget(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(56, 3, 56));
        prepareFlatArea(helper, start, 24, 5);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie distant = spawnTarget(helper, start.east(20));
        Zombie attacker = spawnTarget(helper, start.west(4));
        attacker.setTarget(archer);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, distant);
        archer.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, List.of(attacker));
        helper.onEachTick(() -> {
            if (archer.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == attacker) {
                helper.succeed();
            } else if (helper.getTick() >= 40) {
                helper.fail("archer kept a distant enemy while an eligible nearby attacker pursued it");
            }
        });
    }

    @GameTest(batch = "archer_terrain_descending_threat", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 60)
    public static void descendingPursuerRemainsThreatAboveOldVerticalCutoff(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(56, 3, 56));
        prepareFlatArea(helper, start, 12, 8);
        for (int x = 1; x <= 5; x++) {
            for (int y = 0; y < Math.min(x, 3); y++) {
                helper.getLevel().setBlock(start.offset(x, y, 0), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie attacker = spawnTarget(helper, start.east(3).above(3));
        attacker.setOnGround(true);
        attacker.setTarget(archer);
        var path = attacker.getNavigation().createPath(start, 0);
        helper.assertTrue(path != null && path.canReach(), "descending staircase must connect pursuer to archer");
        attacker.getNavigation().moveTo(path, 1);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, attacker);
        archer.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, List.of(attacker));
        helper.assertTrue(RangedCombatPositioning.collectThreatContext(archer, attacker).escapeThreats().contains(attacker),
                "a pursuing enemy on descending steps vanished at the vertical cutoff");
        helper.succeed();
    }

    @GameTest(batch = "archer_terrain_draw_cancel", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 60)
    public static void recoveryLowersDrawnBow(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(56, 3, 56));
        prepareFlatArea(helper, start, 12, 5);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        archer.setNoAi(true);
        Zombie target = spawnTarget(helper, start.east(10));
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        BowTask<VillagerEntityMCA> bow = new BowTask<>(20, 15);
        long now = helper.getLevel().getGameTime();
        bow.start(helper.getLevel(), archer, now);
        bow.tick(helper.getLevel(), archer, now + 1);
        helper.assertTrue(archer.isUsingItem(), "fixture must begin drawing");
        archer.getBrain().setMemory(MemoryModuleTypeMCA.RANGED_COMBAT_STATE, RangedCombatState.RECOVER);
        bow.tick(helper.getLevel(), archer, now + 2);
        boolean stillDrawing = archer.isUsingItem();
        archer.discard();
        target.discard();
        helper.assertTrue(!stillDrawing, "recovery kept the bow drawn instead of committing to movement");
        helper.succeed();
    }

    @GameTest(batch = "archer_terrain_recovery", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 300)
    public static void archerRecoversFromTwoBlockDepressionWhileTargetRemainsInRange(GameTestHelper helper) {
        BlockPos start = prepareTwoStepExit(helper);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        helper.getLevel().setBlock(start.east(13).above(3), Blocks.STONE.defaultBlockState(), 3);
        Zombie target = spawnTarget(helper, start.east(13).above(4));
        target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
        archer.setOnGround(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        helper.assertTrue(!archer.hasLineOfSight(target), "recovery fixture must start without LOS");
        var exitPath = archer.getNavigation().createPath(start.east(6).above(2), 0);
        helper.assertTrue(exitPath != null && exitPath.canReach(), "stepped exit must be navigable");
        Vec3 exit = Vec3.atBottomCenterOf(start.east(7).above(2));
        helper.assertTrue(exit.distanceToSqr(target.position()) >= ArcherMovementTask.KITE_ENTER_DISTANCE_SQUARED,
                "stepped exit must preserve combat spacing");
        helper.assertTrue(net.conczin.mca.entity.ai.RangedWeaponHelper.canShootBowFrom(archer, exit, target),
                "stepped exit must offer a clear arrow trajectory");
        helper.assertTrue(!net.conczin.mca.entity.ai.RangedWeaponHelper.canShootBowFrom(archer,
                        Vec3.atBottomCenterOf(start.offset(3, 1, 1)), target),
                "first step must not already satisfy the shooting objective");
        int[] hits = {0};
        float[] previousHealth = {target.getHealth()};
        helper.onEachTick(() -> {
            if (target.getHealth() < previousHealth[0]) {
                hits[0]++;
                previousHealth[0] = target.getHealth();
            }
            if (hits[0] >= 2 && archer.getY() >= start.getY() + 1.9
                    && archer.position().distanceToSqr(Vec3.atBottomCenterOf(start)) >= 9) {
                helper.succeed();
            } else if (helper.getTick() >= 240) {
                helper.fail("archer did not regain a shooting lane on its depression exit: hits=" + hits[0]
                        + ", pos=" + archer.position() + ", state=" + RangedCombatState.current(archer)
                        + ", walk=" + archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET));
            }
        });
    }

    @GameTest(batch = "archer_terrain_review", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 60)
    public static void firingCellArrivalRequiresActualClearShot(GameTestHelper helper) {
        BlockPos start = prepareTwoStepExit(helper);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        archer.setNoAi(true);
        helper.getLevel().setBlock(start.east(11).above(3), Blocks.STONE.defaultBlockState(), 3);
        Zombie target = spawnTarget(helper, start.east(11).above(4));
        var firing = RangedCombatPositioning.findFiringTarget(archer, target, List.of(), 225).orElseThrow();
        BlockPos cell = start.offset(3, 1, 1);
        helper.assertTrue(firing.getPathTargets(archer).contains(cell), "fixture needs the narrow first-step firing cell");
        Vec3 centre = Vec3.atBottomCenterOf(cell);
        archer.setPos(centre.add(0, 0, -0.35));
        helper.assertTrue(!archer.hasLineOfSight(target), "edge of firing block must still be obscured by the column");
        helper.assertTrue(!firing.isReached(archer, 0), "firing arrival accepted the obscured edge of a shootable cell");
        archer.setPos(centre);
        helper.assertTrue(archer.hasLineOfSight(target)
                        && net.conczin.mca.entity.ai.RangedWeaponHelper.canShootBowFrom(archer, centre, target),
                "centre must provide an actual usable shot");
        helper.assertTrue(firing.isReached(archer, 0), "usable shooting pose did not complete the firing route");
        helper.succeed();
    }

    private static BlockPos prepareTwoStepExit(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(56, 3, 56));
        prepareFlatArea(helper, start, 16, 7);
        // A low rear chamber has a normal two-step exit toward the enemy.
        for (int x = -4; x <= 12; x++) {
            for (int y = 0; y <= 5; y++) {
                helper.getLevel().setBlock(start.offset(x, y, -1), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(start.offset(x, y, 1), Blocks.STONE.defaultBlockState(), 3);
            }
            helper.getLevel().setBlock(start.offset(x, 6, 0), Blocks.STONE.defaultBlockState(), 3);
            if (x >= 3) {
                helper.getLevel().setBlock(start.offset(x, 0, 0), Blocks.STONE.defaultBlockState(), 3);
            }
            if (x >= 5) {
                helper.getLevel().setBlock(start.offset(x, 1, 0), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        // The surrounding terrain is genuinely two blocks higher. A flat
        // unbounded outside arena would offer an unrelated walk around the pit.
        for (int x = -16; x <= 16; x++) {
            for (int z = -16; z <= 16; z++) {
                if (Math.abs(z) >= 2) {
                    helper.getLevel().setBlock(start.offset(x, 0, z), Blocks.STONE.defaultBlockState(), 3);
                    helper.getLevel().setBlock(start.offset(x, 1, z), Blocks.STONE.defaultBlockState(), 3);
                }
            }
        }
        // A column blocks the direct shot; a supported side passage goes around it.
        for (int y = 2; y <= 5; y++) {
            helper.getLevel().setBlock(start.offset(5, y, 0), Blocks.STONE.defaultBlockState(), 3);
        }
        for (int x = 3; x <= 7; x++) {
            for (int y = 0; y <= 5; y++) {
                helper.getLevel().setBlock(start.offset(x, y, 1), Blocks.AIR.defaultBlockState(), 3);
            }
            helper.getLevel().setBlock(start.offset(x, x < 5 ? 0 : 1, 1), Blocks.STONE.defaultBlockState(), 3);
        }
        return start;
    }

    @GameTest(batch = "archer_terrain_blocked_arrow", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 60)
    public static void bowDoesNotLaunchThroughSolidTerrain(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(56, 3, 56));
        prepareFlatArea(helper, start, 14, 6);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        archer.setNoAi(true);
        Zombie target = spawnTarget(helper, start.east(10));
        for (int y = 0; y <= 4; y++) {
            helper.getLevel().setBlock(start.east(4).above(y), Blocks.STONE.defaultBlockState(), 3);
        }
        helper.assertTrue(!archer.hasLineOfSight(target), "solid wall must block target");
        archer.performRangedAttack(target, 1.0F);
        var arrows = helper.getLevel().getEntitiesOfClass(AbstractArrow.class,
                archer.getBoundingBox().inflate(2), arrow -> arrow.getOwner() == archer);
        boolean launched = !arrows.isEmpty();
        arrows.forEach(AbstractArrow::discard);
        archer.discard();
        target.discard();
        helper.assertTrue(!launched, "bow spawned a projectile on a known blocked route");
        helper.succeed();
    }

    @GameTest(batch = "archer_terrain_uphill_shots", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void productionBowHitsHighTarget(GameTestHelper helper) {
        runElevatedShots(helper, 5, false);
    }

    @GameTest(batch = "archer_terrain_downhill_shots", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 240)
    public static void productionBowHitsLowTarget(GameTestHelper helper) {
        runElevatedShots(helper, -5, false);
    }

    @GameTest(batch = "archer_terrain_natural_uphill", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 300)
    public static void naturalCombatHitsHigherTarget(GameTestHelper helper) {
        runElevatedShots(helper, 5, true);
    }

    @GameTest(batch = "archer_terrain_natural_downhill", templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 300)
    public static void naturalCombatHitsLowerTarget(GameTestHelper helper) {
        runElevatedShots(helper, -5, true);
    }

    private static void runElevatedShots(GameTestHelper helper, int elevation, boolean naturalCombat) {
        BlockPos low = helper.absolutePos(new BlockPos(56, 3, 56));
        prepareFlatArea(helper, low, 16, 12);
        BlockPos start = elevation < 0 ? low.above(5) : low;
        BlockPos destination = start.east(10).above(elevation);
        int platformRadius = naturalCombat ? 3 : 0;
        for (int x = -platformRadius; x <= platformRadius; x++) {
            for (int z = -platformRadius; z <= platformRadius; z++) {
                helper.getLevel().setBlock(start.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        helper.getLevel().setBlock(destination.below(), Blocks.STONE.defaultBlockState(), 3);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        archer.setNoAi(!naturalCombat);
        Zombie target = spawnTarget(helper, destination);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        int[] hits = {0};
        float[] health = {target.getHealth()};
        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (!naturalCombat && tick >= 10 && tick <= 150 && (tick - 10) % 20 == 0) {
                archer.performRangedAttack(target, 1);
            }
            if (target.getHealth() < health[0]) {
                hits[0]++;
                health[0] = target.getHealth();
            }
            if (tick >= (naturalCombat ? 240 : 190)) {
                String actorState = ", actorTicks=" + archer.tickCount + ", health=" + archer.getHealth()
                        + ", activity=" + archer.getBrain().getActiveNonCoreActivity()
                        + ", target=" + archer.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET)
                        + ", combat=" + RangedCombatState.current(archer);
                helper.getLevel().getEntitiesOfClass(AbstractArrow.class, archer.getBoundingBox().inflate(24),
                        arrow -> arrow.getOwner() == archer).forEach(AbstractArrow::discard);
                archer.discard();
                target.discard();
                net.conczin.mca.MCA.LOGGER.info("[MCA Archer terrain acceptance] natural={} elevation={} hits={}",
                        naturalCombat, elevation, hits[0]);
                helper.assertTrue(hits[0] >= (naturalCombat ? 2 : 3), "production bow hit only " + hits[0]
                        + " times at elevation " + elevation + ", natural=" + naturalCombat + actorState);
                helper.succeed();
            }
        });
    }

    private static VillagerEntityMCA spawnArcher(GameTestHelper helper, BlockPos position) {
        VillagerEntityMCA archer = VillagerFactory.newVillager(helper.getLevel()).withAge(0)
                .withProfession(ProfessionsMCA.ARCHER).withPosition(Vec3.atBottomCenterOf(position))
                .withName("Terrain Combat Archer").spawn(MobSpawnType.STRUCTURE);
        archer.setItemSlot(EquipmentSlot.MAINHAND, Items.BOW.getDefaultInstance());
        archer.refreshBrain(helper.getLevel());
        ACTORS.add(archer);
        return archer;
    }

    private static Zombie spawnTarget(GameTestHelper helper, BlockPos position) {
        Zombie target = EntityType.ZOMBIE.create(helper.getLevel());
        if (target == null) {
            throw new IllegalStateException("Could not create terrain combat target");
        }
        target.absMoveTo(position.getX() + 0.5, position.getY(), position.getZ() + 0.5);
        target.setNoAi(true);
        target.setItemSlot(EquipmentSlot.HEAD, Items.IRON_HELMET.getDefaultInstance());
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        target.setHealth(200);
        helper.getLevel().addFreshEntity(target);
        ACTORS.add(target);
        return target;
    }
}
