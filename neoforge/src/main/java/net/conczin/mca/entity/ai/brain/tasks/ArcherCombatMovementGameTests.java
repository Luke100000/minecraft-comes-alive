package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.RangedWeaponHelper;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class ArcherCombatMovementGameTests {
    private static final List<Entity> TEST_ENTITIES = new ArrayList<>();

    private ArcherCombatMovementGameTests() {
    }

    @GameTest(batch = "mca_archer_blocked_side", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void blockedSideIsRejectedBeforeStrafe(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        prepareFlatArea(helper, start, 4);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        archer.setYRot(0.0F);

        helper.getLevel().setBlock(start.south(), Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(start.south().above(), Blocks.STONE.defaultBlockState(), 3);

        helper.assertTrue(!RangedCombatPositioning.isStrafeSideWalkable(archer, 1.0F), "blocked right strafe side was accepted");
        helper.assertTrue(RangedCombatPositioning.isStrafeSideWalkable(archer, -1.0F), "clear left strafe side was rejected");
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_move_control", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void sharedMoveControlOwnsVillagerMovement(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        prepareFlatArea(helper, start, 3);
        VillagerEntityMCA archer = spawnArcher(helper, start);

        helper.assertTrue(
                archer.getMoveControl().getClass().getSimpleName().equals("MCAMoveControl"),
                "MCA villager still uses a specialised archer move controller: " + archer.getMoveControl().getClass().getName()
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_approach", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 180)
    public static void approachPublishesWalkAndRetainsLookTarget(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos targetPos = start.east(18);
        prepareFlatArea(helper, start.east(9), 14);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, targetPos);
        archer.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, target);

        int[] ticks = {0};
        helper.onEachTick(() -> {
            ticks[0]++;
            if (RangedCombatState.current(archer).orElse(null) == RangedCombatState.APPROACH) {
                helper.assertTrue(archer.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).isPresent(), "APPROACH did not publish WALK_TARGET");
                helper.assertTrue(archer.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET).isPresent(), "APPROACH lost LOOK_TARGET while pathing");
                helper.succeed();
                return;
            }
            if (ticks[0] >= 160) {
                helper.fail("archer never entered APPROACH; state=" + RangedCombatState.current(archer).orElse(null));
            }
        });
    }

    @GameTest(batch = "mca_archer_emergency", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 240)
    public static void closeThreatPublishesAwayWalkTarget(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(2, 2, 2));
        prepareFlatArea(helper, start, 14);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(2));
        archer.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, target);
        double initialDistanceSquared = archer.distanceToSqr(target);

        helper.onEachTick(() -> {
            if (RangedCombatState.current(archer).orElse(null) != RangedCombatState.EMERGENCY_FLEE) {
                return;
            }
            net.minecraft.world.entity.ai.memory.WalkTarget walkTarget = archer.getBrain()
                    .getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
                    .orElse(null);
            if (walkTarget == null) {
                return;
            }
            helper.assertTrue(
                    walkTarget.getTarget().currentPosition().distanceToSqr(target.position()) > initialDistanceSquared,
                    "EMERGENCY_FLEE did not publish a position farther from the close threat"
            );
            helper.assertTrue(
                    walkTarget.getTarget().currentPosition().distanceToSqr(target.position()) >= 36.0D,
                    "EMERGENCY_FLEE ignored its 6-block desired retreat distance"
            );
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_archer_nearest_threat", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 240)
    public static void nearestVisibleThreatControlsRetreat(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(5, 2, 5));
        prepareFlatArea(helper, start.east(5), 12);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie attackTarget = spawnTarget(helper, start.east(10));
        Zombie closeThreat = spawnTarget(helper, start.north(4));
        archer.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, attackTarget);
        archer.getBrain().setMemory(
                net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                new net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities(archer, List.of(closeThreat, attackTarget))
        );
        double initialCloseDistanceSquared = archer.distanceToSqr(closeThreat);

        helper.onEachTick(() -> {
            RangedCombatState state = RangedCombatState.current(archer).orElse(null);
            if (state != RangedCombatState.KITE && state != RangedCombatState.EMERGENCY_FLEE) {
                return;
            }
            net.minecraft.world.entity.ai.memory.WalkTarget walkTarget = archer.getBrain()
                    .getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
                    .orElse(null);
            if (walkTarget == null) {
                return;
            }
            double destinationDistanceSquared = walkTarget.getTarget().currentPosition().distanceToSqr(closeThreat.position());
            helper.assertTrue(
                    destinationDistanceSquared > initialCloseDistanceSquared,
                    "retreat destination did not open distance from physically nearest threat: initial="
                            + initialCloseDistanceSquared + ", destination=" + destinationDistanceSquared
            );
            helper.assertTrue(
                    destinationDistanceSquared >= 81.0D,
                    "KITE ignored its 9-block desired retreat distance; destination=" + destinationDistanceSquared
            );
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_archer_reposition_retry", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 320)
    public static void unreachableRepositionDoesNotPathSpam(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start.east(5), 12);
        for (int z = -2; z <= 2; z++) {
            for (int y = 0; y <= 3; y++) {
                helper.getLevel().setBlock(start.east(5).offset(0, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        for (BlockPos side : List.of(start.north(), start.south(), start.east(), start.west())) {
            helper.getLevel().setBlock(side, Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(side.above(), Blocks.STONE.defaultBlockState(), 3);
        }

        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(10));
        archer.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, target);

        net.minecraft.world.entity.ai.memory.WalkTarget[] firstPublished = {null};
        int[] firstPublishTick = {-1};
        boolean[] sawUnreachableMark = {false};
        int[] ticks = {0};

        helper.onEachTick(() -> {
            ticks[0]++;
            if (RangedCombatState.current(archer).orElse(null) != RangedCombatState.REPOSITION) {
                return;
            }

            net.minecraft.world.entity.ai.memory.WalkTarget current = archer.getBrain()
                    .getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
                    .orElse(null);
            if (archer.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE).isPresent()) {
                sawUnreachableMark[0] = true;
            }
            if (firstPublished[0] == null) {
                if (current != null) {
                    firstPublished[0] = current;
                    firstPublishTick[0] = ticks[0];
                }
                return;
            }

            int elapsed = ticks[0] - firstPublishTick[0];
            if (current == null) {
                return;
            }
            if (current == firstPublished[0]) {
                return;
            }

            helper.assertTrue(sawUnreachableMark[0], "REPOSITION retried before Brain/navigation marked the original intent unreachable");
            helper.assertTrue(elapsed >= 10, "REPOSITION republished unreachable WALK_TARGET after only " + elapsed + " ticks");
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_archer_cleanup", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 300)
    public static void targetLossAndWeaponRemovalClearCombatState(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        prepareFlatArea(helper, start.east(9), 14);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        archer.setNoAi(true);
        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
        long gameTime = helper.getLevel().getGameTime();

        Zombie firstTarget = spawnTarget(helper, start.east(18));
        archer.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, firstTarget);
        helper.assertTrue(movement.tryStart(helper.getLevel(), archer, gameTime), "target-loss fixture could not start archer movement");
        movement.tickOrStop(helper.getLevel(), archer, gameTime + 1);
        helper.assertTrue(RangedCombatState.current(archer).isPresent(), "target-loss fixture never produced ranged state");
        helper.assertTrue(archer.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).isPresent(),
                "target-loss fixture never produced combat WALK_TARGET");

        archer.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET);
        firstTarget.discard();
        movement.tickOrStop(helper.getLevel(), archer, gameTime + 2);
        helper.assertTrue(RangedCombatState.current(archer).isEmpty(), "target loss did not clear ranged state");
        helper.assertTrue(archer.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).isEmpty(),
                "target loss retained stale combat WALK_TARGET");

        Zombie secondTarget = spawnTarget(helper, start.east(18));
        archer.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, secondTarget);
        helper.assertTrue(movement.tryStart(helper.getLevel(), archer, gameTime + 3), "weapon-removal fixture could not restart archer movement");
        movement.tickOrStop(helper.getLevel(), archer, gameTime + 4);
        helper.assertTrue(RangedCombatState.current(archer).isPresent(), "weapon-removal fixture never produced ranged state");
        helper.assertTrue(archer.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).isPresent(),
                "weapon-removal fixture never produced combat WALK_TARGET");

        archer.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        helper.assertTrue(!RangedWeaponHelper.isHoldingSupportedWeapon(archer), "weapon-removal fixture still had a supported ranged weapon");
        movement.tickOrStop(helper.getLevel(), archer, gameTime + 5);
        helper.assertTrue(RangedCombatState.current(archer).isEmpty(), "weapon removal did not clear ranged state");
        helper.assertTrue(archer.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).isEmpty(),
                "weapon removal retained stale combat WALK_TARGET");
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_panic", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 360)
    public static void panicPreemptsCombatMovement(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        prepareFlatArea(helper, start.east(9), 14);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(18));
        archer.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, target);

        boolean[] hurt = {false};
        int[] panicTicks = {0};

        helper.onEachTick(() -> {
            if (!hurt[0]) {
                if (RangedCombatState.current(archer).isPresent()
                        && archer.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).isPresent()) {
                    archer.setHealth(archer.getMaxHealth() * 0.2F);
                    helper.assertTrue(
                            archer.hurt(helper.getLevel().damageSources().mobAttack(target), 1.0F),
                            "panic fixture damage was rejected"
                    );
                    hurt[0] = true;
                }
                return;
            }

            if (!archer.getBrain().isActive(net.minecraft.world.entity.schedule.Activity.PANIC)) {
                return;
            }

            panicTicks[0]++;
            helper.assertTrue(RangedCombatState.current(archer).isEmpty(), "ranged combat state survived PANIC preemption");
            if (panicTicks[0] >= 8) {
                helper.assertTrue(
                        archer.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
                                .map(walk -> walk.getTarget().currentPosition().distanceToSqr(target.position()) > 4.0D)
                                .orElse(true),
                        "old combat WALK_TARGET was republished while PANIC owned movement"
                );
                helper.succeed();
            }
        });
    }

    @GameTest(batch = "mca_archer_kite_hysteresis", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 280)
    public static void kiteBandDoesNotPingPong(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(5, 2, 5));
        prepareFlatArea(helper, start, 14);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(5));
        archer.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, target);

        int[] phase = {0};
        int[] phaseTicks = {0};

        helper.onEachTick(() -> {
            RangedCombatState state = RangedCombatState.current(archer).orElse(null);
            if (phase[0] == 0) {
                target.absMoveTo(archer.getX() + 5.5D, archer.getY(), archer.getZ());
                if (state == RangedCombatState.KITE) {
                    phase[0] = 1;
                    phaseTicks[0] = 0;
                }
                return;
            }

            phaseTicks[0]++;
            if (phase[0] == 1) {
                target.absMoveTo(archer.getX() + 7.0D, archer.getY(), archer.getZ());
                helper.assertTrue(state == RangedCombatState.KITE, "KITE exited inside 6/9 hysteresis band at 7 blocks: " + state);
                if (phaseTicks[0] >= 12) {
                    phase[0] = 2;
                    phaseTicks[0] = 0;
                }
                return;
            }

            if (phase[0] == 2) {
                target.absMoveTo(archer.getX() + 8.9D, archer.getY(), archer.getZ());
                helper.assertTrue(state == RangedCombatState.KITE, "KITE exited before 9-block threshold: " + state);
                if (phaseTicks[0] >= 12) {
                    phase[0] = 3;
                    phaseTicks[0] = 0;
                }
                return;
            }

            target.absMoveTo(archer.getX() + 9.0D, archer.getY(), archer.getZ());
            if (state == RangedCombatState.HOLD) {
                helper.succeed();
            } else if (phaseTicks[0] > 20) {
                helper.fail("KITE did not exit at 9-block threshold; state=" + state);
            }
        });
    }

    @GameTest(batch = "mca_archer_navigation", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 600)
    public static void approachUsesExistingNavigationThroughObstacle(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos wall = start.east(8);
        prepareFlatArea(helper, start.east(15), 22);
        for (int z = -8; z <= 8; z++) {
            if (z == 2 || z == 3) {
                continue;
            }
            helper.getLevel().setBlock(wall.offset(0, 0, z), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(wall.offset(0, 1, z), Blocks.STONE.defaultBlockState(), 3);
        }

        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(30));
        archer.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, target);
        boolean[] sawApproachIntent = {false};
        boolean[] sawPathMemory = {false};

        helper.onEachTick(() -> {
            if (RangedCombatState.current(archer).orElse(null) == RangedCombatState.APPROACH
                    && archer.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).isPresent()) {
                sawApproachIntent[0] = true;
            }
            if (archer.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.PATH).isPresent()) {
                sawPathMemory[0] = true;
            }

            if (archer.getX() > wall.getX() + 1.0D) {
                helper.assertTrue(sawApproachIntent[0], "archer crossed obstacle without publishing APPROACH WALK_TARGET");
                helper.assertTrue(sawPathMemory[0], "archer crossed obstacle without Brain-owned PATH memory");
                helper.succeed();
            }
        });
    }

    @GameTest(batch = "mca_archer_look_ownership", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 360)
    public static void normalRangedPathingKeepsLookOwnership(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(5, 2, 5));
        prepareFlatArea(helper, start.east(9), 14);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(18));
        archer.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, target);
        boolean[] sawApproach = {false};
        BlockPos[] wallCenter = {null};

        helper.onEachTick(() -> {
            RangedCombatState state = RangedCombatState.current(archer).orElse(null);
            if (!sawApproach[0] && state == RangedCombatState.APPROACH) {
                net.minecraft.world.entity.ai.behavior.PositionTracker look = archer.getBrain()
                        .getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET)
                        .orElse(null);
                helper.assertTrue(look != null && look.currentPosition().distanceToSqr(target.getEyePosition()) < 0.01D,
                        "APPROACH LOOK_TARGET did not track attack target");
                helper.assertTrue(archer.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).isPresent(),
                        "APPROACH missing independent WALK_TARGET");
                sawApproach[0] = true;

                BlockPos archerPos = archer.blockPosition();
                target.absMoveTo(archer.getX() + 10.0D, archer.getY(), archer.getZ());
                wallCenter[0] = archerPos.east(5);
                for (int z = -2; z <= 2; z++) {
                    for (int y = 0; y <= 3; y++) {
                        helper.getLevel().setBlock(wallCenter[0].offset(0, y, z), Blocks.STONE.defaultBlockState(), 3);
                    }
                }
                return;
            }

            if (sawApproach[0] && state == RangedCombatState.REPOSITION) {
                net.minecraft.world.entity.ai.behavior.PositionTracker look = archer.getBrain()
                        .getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET)
                        .orElse(null);
                helper.assertTrue(look != null && look.currentPosition().distanceToSqr(target.getEyePosition()) < 0.01D,
                        "REPOSITION LOOK_TARGET did not track attack target");
                helper.succeed();
            }
        });
    }

    @GameTest(batch = "mca_archer_kite_look_ownership", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 240)
    public static void kiteKeepsAttackTargetLookOwnership(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 12);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(5));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.absMoveTo(archer.getX(), archer.getY(), archer.getZ() + 2.0D);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);

        helper.onEachTick(() -> {
            if (RangedCombatState.current(archer).orElse(null) != RangedCombatState.KITE) {
                return;
            }

            var lookTarget = archer.getBrain().getMemory(MemoryModuleType.LOOK_TARGET).orElse(null);
            helper.assertTrue(lookTarget != null, "KITE left LOOK_TARGET vacant");
            helper.assertTrue(
                    lookTarget.currentPosition().distanceToSqr(target.getEyePosition()) < 0.01D,
                    "KITE surrendered LOOK_TARGET to a nearby player"
            );
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_archer_flee_look_ownership", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 240)
    public static void emergencyFleeNeverLeavesLookTargetVacant(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 12);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(2));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.absMoveTo(archer.getX(), archer.getY(), archer.getZ() + 2.0D);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);

        helper.onEachTick(() -> {
            if (RangedCombatState.current(archer).orElse(null) != RangedCombatState.EMERGENCY_FLEE) {
                return;
            }

            helper.assertTrue(
                    archer.getBrain().getMemory(MemoryModuleType.LOOK_TARGET).isPresent(),
                    "EMERGENCY_FLEE left LOOK_TARGET vacant for generic player-look behavior"
            );
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_archer_reposition_no_charge", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 300)
    public static void blockedLosRepositionDoesNotChargeTarget(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        BlockPos targetPos = start.east(10);
        prepareFlatArea(helper, start.east(5), 12);
        for (int z = -2; z <= 2; z++) {
            for (int y = 0; y <= 3; y++) {
                helper.getLevel().setBlock(start.east(5).offset(0, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, targetPos);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        double minimumAllowedDistance = archer.distanceTo(target) - 0.5D;
        boolean[] sawReposition = {false};
        int[] ticks = {0};

        helper.onEachTick(() -> {
            ticks[0]++;
            RangedCombatState state = RangedCombatState.current(archer).orElse(null);
            if (state == RangedCombatState.REPOSITION) {
                sawReposition[0] = true;
                var walkTarget = archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
                if (walkTarget != null) {
                    helper.assertTrue(
                            walkTarget.getTarget().currentPosition().distanceTo(target.position()) >= minimumAllowedDistance,
                            "REPOSITION published a firing destination that closes on the attack target"
                    );
                }
            }

            if (sawReposition[0]) {
                helper.assertTrue(
                        state != RangedCombatState.KITE && state != RangedCombatState.EMERGENCY_FLEE,
                        "REPOSITION closed distance until retreat triggered: state=" + state
                );
            }

            if (ticks[0] >= 100) {
                helper.assertTrue(sawReposition[0], "blocked LOS never entered REPOSITION");
                helper.succeed();
            }
        });
    }

    @GameTest(batch = "mca_archer_bow_emergency", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 240)
    public static void emergencyFleeCancelsBowDraw(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(2, 2, 2));
        prepareFlatArea(helper, start.east(5), 10);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(10));
        archer.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, target);
        boolean[] drawStarted = {false};

        helper.onEachTick(() -> {
            if (!drawStarted[0] && archer.isUsingItem()) {
                drawStarted[0] = true;
                target.absMoveTo(archer.getX() + 2.0D, archer.getY(), archer.getZ());
                return;
            }
            if (drawStarted[0] && RangedCombatState.current(archer).orElse(null) == RangedCombatState.EMERGENCY_FLEE) {
                helper.assertTrue(!archer.isUsingItem(), "bow draw continued during EMERGENCY_FLEE");
                helper.succeed();
            }
        });
    }

    @GameTest(batch = "mca_archer_crossbow_emergency", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 240)
    public static void emergencyFleeCancelsCrossbowCharge(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(2, 2, 2));
        prepareFlatArea(helper, start.east(5), 10);
        VillagerEntityMCA archer = spawnArcher(helper, start, Items.CROSSBOW.getDefaultInstance());
        Zombie target = spawnTarget(helper, start.east(10));
        ExtendedCrossbowAttackTask<VillagerEntityMCA, Zombie> task = new ExtendedCrossbowAttackTask<>();
        task.crossbowAttack(archer, target);
        helper.assertTrue(archer.isUsingItem(), "crossbow fixture did not begin charging");

        archer.getBrain().setMemory(MemoryModuleTypeMCA.RANGED_COMBAT_STATE, RangedCombatState.EMERGENCY_FLEE);
        task.crossbowAttack(archer, target);
        helper.assertTrue(!archer.isUsingItem(), "crossbow charge continued during EMERGENCY_FLEE");
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_reposition", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 240)
    public static void sustainedBlockedLosUsesSafeRepositionOrHolds(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        BlockPos targetPos = start.east(10);
        prepareFlatArea(helper, start.east(5), 12);
        for (int z = -2; z <= 2; z++) {
            for (int y = 0; y <= 3; y++) {
                helper.getLevel().setBlock(start.east(5).offset(0, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, targetPos);
        archer.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, target);
        double minimumAllowedDistance = archer.distanceTo(target) - 0.5D;

        helper.onEachTick(() -> {
            if (RangedCombatState.current(archer).orElse(null) != RangedCombatState.REPOSITION) {
                return;
            }
            var lookTarget = archer.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.LOOK_TARGET).orElse(null);
            helper.assertTrue(lookTarget != null && lookTarget.currentPosition().distanceToSqr(target.getEyePosition()) < 0.01D,
                    "REPOSITION lost attack-target LOOK_TARGET ownership");
            archer.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).ifPresent(walkTarget ->
                    helper.assertTrue(
                            walkTarget.getTarget().currentPosition().distanceTo(target.position()) >= minimumAllowedDistance,
                            "REPOSITION published a firing destination that closes on the attack target"
                    ));
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_archer_hold", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 300)
    public static void holdClearsCombatWalkTarget(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start.east(8), 18);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(18));
        archer.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, target);
        boolean[] sawApproachWalkTarget = {false};

        helper.onEachTick(() -> {
            RangedCombatState state = RangedCombatState.current(archer).orElse(null);
            if (!sawApproachWalkTarget[0]) {
                if (state == RangedCombatState.APPROACH
                        && archer.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).isPresent()) {
                    sawApproachWalkTarget[0] = true;
                    target.absMoveTo(archer.getX() + 10.0D, archer.getY(), archer.getZ());
                }
                return;
            }

            if (state == RangedCombatState.HOLD) {
                helper.assertTrue(
                        archer.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).isEmpty(),
                        "HOLD retained the combat-owned WALK_TARGET"
                );
                helper.succeed();
            }
        });
    }

    @GameTest(batch = "mca_archer_stable_strafe", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 360)
    public static void stableTargetUsesBoundedStrafeBursts(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(6, 2, 6));
        prepareFlatArea(helper, start.east(5), 12);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(10));
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
        target.setHealth(200.0F);
        archer.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, target);

        int[] ticks = {0};
        int[] holdTicks = {0};
        int[] strafeTicks = {0};
        int[] currentRunTicks = {0};
        int[] currentRunSign = {0};
        int[] completedRuns = {0};
        int[] lastRunEndTick = {-1000};

        helper.onEachTick(() -> {
            ticks[0]++;
            RangedCombatState state = RangedCombatState.current(archer).orElse(null);
            if (state == RangedCombatState.HOLD) {
                holdTicks[0]++;
            }

            if (state == RangedCombatState.STRAFE) {
                strafeTicks[0]++;
                if (currentRunTicks[0] == 0) {
                    int nonStrafeGap = ticks[0] - lastRunEndTick[0] - 1;
                    if (completedRuns[0] > 0) {
                        helper.assertTrue(nonStrafeGap >= 40, "STRAFE cooldown gap was only " + nonStrafeGap + " ticks");
                    }
                }

                currentRunTicks[0]++;
                int sign = Math.abs(archer.xxa) < 1.0E-3F ? 0 : archer.xxa > 0.0F ? 1 : -1;
                if (sign != 0) {
                    if (currentRunSign[0] == 0) {
                        currentRunSign[0] = sign;
                    } else {
                        helper.assertTrue(sign == currentRunSign[0], "STRAFE direction reversed inside one burst");
                    }
                }
                helper.assertTrue(currentRunTicks[0] <= 14, "STRAFE burst exceeded 14 ticks");
            } else if (currentRunTicks[0] > 0) {
                helper.assertTrue(currentRunTicks[0] >= 8, "clear-lane STRAFE burst ended after only " + currentRunTicks[0] + " ticks");
                helper.assertTrue(currentRunTicks[0] <= 14, "clear-lane STRAFE burst lasted " + currentRunTicks[0] + " ticks");
                completedRuns[0]++;
                lastRunEndTick[0] = ticks[0] - 1;
                currentRunTicks[0] = 0;
                currentRunSign[0] = 0;
            }

            if (ticks[0] >= 280) {
                helper.assertTrue(completedRuns[0] >= 2, "stable target produced fewer than two completed STRAFE bursts: " + completedRuns[0]);
                helper.assertTrue(holdTicks[0] > strafeTicks[0], "archer spent more time strafing than holding: hold=" + holdTicks[0] + ", strafe=" + strafeTicks[0]);
                helper.succeed();
            }
        });
    }

    @GameTest(batch = "mca_archer_blocked_strafe", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 260)
    public static void blockedStrafeCancelsWithoutDirectionFlip(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(6, 2, 6));
        prepareFlatArea(helper, start.east(5), 12);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(10));
        archer.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, target);

        boolean[] blockedBurst = {false};
        boolean[] sawCancellation = {false};
        int[] cancellationTick = {-1};
        int[] ticks = {0};

        helper.onEachTick(() -> {
            ticks[0]++;
            RangedCombatState state = RangedCombatState.current(archer).orElse(null);
            if (!blockedBurst[0] && state == RangedCombatState.STRAFE) {
                blockedBurst[0] = true;
                BlockPos feet = archer.blockPosition();
                for (BlockPos side : List.of(feet.north(), feet.south(), feet.east(), feet.west())) {
                    helper.getLevel().setBlock(side, Blocks.STONE.defaultBlockState(), 3);
                    helper.getLevel().setBlock(side.above(), Blocks.STONE.defaultBlockState(), 3);
                }
                return;
            }

            if (blockedBurst[0] && !sawCancellation[0]) {
                if (state == RangedCombatState.HOLD) {
                    sawCancellation[0] = true;
                    cancellationTick[0] = ticks[0];
                } else if (ticks[0] > 80) {
                    helper.fail("blocked STRAFE did not cancel to HOLD; state=" + state);
                }
                return;
            }

            if (sawCancellation[0]) {
                int sinceCancellation = ticks[0] - cancellationTick[0];
                if (sinceCancellation < 40) {
                    helper.assertTrue(state != RangedCombatState.STRAFE, "opposite STRAFE restarted before cooldown expiry at +" + sinceCancellation + " ticks");
                } else {
                    helper.succeed();
                }
            }
        });
    }

    @GameTest(batch = "mca_archer_strafe", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 360)
    public static void sameBlockLeftRightShimmyDoesNotRecur(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(6, 2, 6));
        BlockPos targetPos = start.east(10);
        prepareFlatArea(helper, start.east(5), 10);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, targetPos);
        archer.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, target);

        Vec3 origin = archer.position();
        Vec3 toTarget = target.position().subtract(origin).multiply(1.0, 0.0, 1.0).normalize();
        Vec3 lateral = new Vec3(-toTarget.z, 0.0, toTarget.x);
        double[] lastProjection = {0.0D};
        Deque<Double> projections = new ArrayDeque<>();
        Deque<Integer> signs = new ArrayDeque<>();
        projections.add(0.0D);
        int[] ticks = {0};

        helper.onEachTick(() -> {
            ticks[0]++;
            double projection = archer.position().subtract(origin).dot(lateral);
            double delta = projection - lastProjection[0];
            int inputSign = Math.abs(archer.xxa) < 1.0E-3F ? 0 : archer.xxa > 0.0F ? 1 : -1;
            int motionSign = Math.abs(delta) < 1.0E-3D ? 0 : delta > 0.0D ? 1 : -1;
            int sign = inputSign != 0 ? inputSign : motionSign;
            lastProjection[0] = projection;

            projections.addLast(projection);
            signs.addLast(sign);
            if (projections.size() > 21) {
                projections.removeFirst();
            }
            if (signs.size() > 20) {
                signs.removeFirst();
            }

            int lastSign = 0;
            int signChanges = 0;
            for (int sampleSign : signs) {
                if (sampleSign == 0) {
                    continue;
                }
                if (lastSign != 0 && sampleSign != lastSign) {
                    signChanges++;
                }
                lastSign = sampleSign;
            }
            double minProjection = projections.stream().mapToDouble(Double::doubleValue).min().orElse(projection);
            double maxProjection = projections.stream().mapToDouble(Double::doubleValue).max().orElse(projection);

            if (signChanges > 1 && maxProjection - minProjection < 1.0D) {
                helper.fail("repeated left/right shimmy inside one block over 20 ticks: signChanges=" + signChanges
                        + ", envelope=" + (maxProjection - minProjection) + ", pos=" + archer.position());
                return;
            }
            if (ticks[0] >= 260) {
                helper.succeed();
            }
        });
    }

    private static VillagerEntityMCA spawnArcher(GameTestHelper helper, BlockPos start) {
        return spawnArcher(helper, start, Items.BOW.getDefaultInstance());
    }

    private static VillagerEntityMCA spawnArcher(GameTestHelper helper, BlockPos start, ItemStack weapon) {
        helper.getLevel().getChunk(start);
        VillagerEntityMCA archer = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withProfession(ProfessionsMCA.ARCHER)
                .withPosition(Vec3.atBottomCenterOf(start))
                .withName("Archer Combat Movement Probe")
                .spawn(MobSpawnType.STRUCTURE);
        archer.setItemSlot(EquipmentSlot.MAINHAND, weapon);
        archer.refreshBrain(helper.getLevel());
        TEST_ENTITIES.add(archer);
        return archer;
    }

    private static Zombie spawnTarget(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().getChunk(pos);
        Zombie target = EntityType.ZOMBIE.create(helper.getLevel());
        if (target == null) {
            throw new IllegalStateException("failed to create zombie target");
        }
        target.absMoveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
        target.setNoAi(true);
        helper.getLevel().addFreshEntity(target);
        TEST_ENTITIES.add(target);
        return target;
    }

    private static void cleanupTestEntities() {
        TEST_ENTITIES.forEach(Entity::discard);
        TEST_ENTITIES.clear();
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
            }
        }
    }
}
