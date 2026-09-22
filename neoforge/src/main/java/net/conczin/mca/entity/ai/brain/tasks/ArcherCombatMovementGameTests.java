package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.MCAMoveControl;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.RangedWeaponHelper;
import net.conczin.mca.entity.ai.navigation.MultiTargetPositionTracker;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
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
import java.util.Set;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class ArcherCombatMovementGameTests {
    private static final List<Entity> TEST_ENTITIES = new ArrayList<>();

    private ArcherCombatMovementGameTests() {
    }

    @GameTest(batch = "mca_multi_target_sink_completion", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void multiTargetSinkStopsAfterAlternateEndpointReached(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(10, 2, 10));
        prepareFlatArea(helper, start, 10);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        archer.setNoAi(true);
        archer.setOnGround(true);

        BlockPos preferred = start.east(6);
        BlockPos alternate = start.north(4);
        for (int z = -10; z <= 10; z++) {
            setWallColumn(helper, start.offset(2, 0, z));
        }

        MultiTargetPositionTracker target = new MultiTargetPositionTracker() {
            private final Set<BlockPos> targets = Set.of(preferred, alternate);

            @Override
            public Set<BlockPos> getPathTargets(net.minecraft.world.entity.Mob mob) {
                return targets;
            }

            @Override
            public boolean isReached(net.minecraft.world.entity.Mob mob, int closeEnoughDistance) {
                return targets.stream().anyMatch(pos ->
                        pos.distManhattan(mob.blockPosition()) <= closeEnoughDistance);
            }

            @Override
            public Vec3 currentPosition() {
                return Vec3.atBottomCenterOf(preferred);
            }

            @Override
            public BlockPos currentBlockPosition() {
                return preferred;
            }

            @Override
            public boolean isVisibleBy(LivingEntity livingEntity) {
                return true;
            }
        };

        archer.getBrain().eraseMemory(MemoryModuleType.PATH);
        archer.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        var directPath = archer.getNavigation().createPath(target.getPathTargets(archer), 0);
        helper.assertTrue(directPath != null && directPath.canReach(),
                "fixture alternate endpoint was not vanilla-reachable");
        archer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.5F, 0));
        WanderOrTeleportToTargetTask sink = new WanderOrTeleportToTargetTask();
        long gameTime = helper.getLevel().getGameTime();
        helper.assertTrue(sink.tryStart(helper.getLevel(), archer, gameTime),
                "multi-target movement sink did not start");

        var path = archer.getNavigation().getPath();
        helper.assertTrue(path != null && path.canReach(), "multi-target sink did not create a reachable path");
        helper.assertTrue(!path.getTarget().equals(preferred),
                "fixture did not force vanilla to choose the alternate endpoint");

        archer.teleportTo(
                path.getTarget().getX() + 0.5D,
                path.getTarget().getY(),
                path.getTarget().getZ() + 0.5D
        );
        while (!path.isDone()) {
            path.advance();
        }
        sink.tickOrStop(helper.getLevel(), archer, gameTime + 1);

        helper.assertTrue(
                !archer.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "multi-target sink kept a completed alternate endpoint alive because preferred endpoint differed"
        );
        helper.succeed();
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

        helper.assertTrue(
                RangedCombatPositioning.findBestStrafeDirection(archer, List.of()) == -1.0F,
                "strafe selection did not reject the blocked right side"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_move_control", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void sharedMoveControlOwnsVillagerMovement(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        prepareFlatArea(helper, start, 3);
        VillagerEntityMCA archer = spawnArcher(helper, start);

        helper.assertTrue(
                archer.getMoveControl() instanceof MCAMoveControl,
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

    @GameTest(batch = "mca_archer_emergency_group", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void emergencyEscapeCandidateOpensDistanceFromNearbyThreatGroup(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 12);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie eastThreat = spawnTarget(helper, start.east(2));
        Zombie westThreat = spawnTarget(helper, start.west(4));
        archer.setOnGround(true);

        double eastInitial = archer.distanceToSqr(eastThreat);
        double westInitial = archer.distanceToSqr(westThreat);
        var escapeTarget = RangedCombatPositioning.findEmergencyEscapeTarget(
                archer,
                List.of(eastThreat, westThreat),
                List.of(eastThreat, westThreat),
                6.0D
        ).orElseThrow(() -> new AssertionError("no emergency escape candidate found on an open flat area"));
        var path = archer.getNavigation().createPath(escapeTarget.getPathTargets(archer), 0);
        helper.assertTrue(path != null && path.canReach(), "vanilla navigation could not reach any emergency escape target");
        Vec3 destination = Vec3.atBottomCenterOf(path.getTarget());

        helper.assertTrue(destination.distanceToSqr(eastThreat.position()) > eastInitial,
                "emergency escape candidate moved closer to the east threat");
        helper.assertTrue(destination.distanceToSqr(westThreat.position()) > westInitial,
                "emergency escape candidate moved closer to the west threat");
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_escape_topology", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void escapeTargetSetRejectsDeadEndPocketForUpwardOnwardRoute(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(12, 2, 12));
        prepareFlatArea(helper, start, 12);
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                helper.getLevel().setBlock(start.offset(x, -1, z), Blocks.AIR.defaultBlockState(), 3);
                helper.getLevel().setBlock(start.offset(x, 0, z), Blocks.AIR.defaultBlockState(), 3);
            }
        }

        helper.getLevel().setBlock(start.below(), Blocks.STONE.defaultBlockState(), 3);

        // Ground-level west branch: a locally roomy 3x3 pocket whose only exit heads back toward the origin.
        for (int x = -1; x >= -5; x--) {
            helper.getLevel().setBlock(start.offset(x, -1, 0), Blocks.STONE.defaultBlockState(), 3);
        }
        for (int x = -3; x >= -5; x--) {
            for (int z = -1; z <= 1; z++) {
                helper.getLevel().setBlock(start.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        BlockPos deadEnd = start.west(4);

        // North branch climbs one block, then opens only after the sampled escape point.
        for (int z = -1; z >= -3; z--) {
            helper.getLevel().setBlock(start.offset(0, -1, z), Blocks.STONE.defaultBlockState(), 3);
        }
        for (int x = 0; x >= -8; x--) {
            helper.getLevel().setBlock(start.offset(x, 0, -4), Blocks.STONE.defaultBlockState(), 3);
        }
        for (int z = -5; z >= -8; z--) {
            helper.getLevel().setBlock(start.offset(-5, 0, z), Blocks.STONE.defaultBlockState(), 3);
        }
        for (int z = -3; z >= -1; z--) {
            helper.getLevel().setBlock(start.offset(-5, 0, z), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(start.offset(-6, 0, z), Blocks.STONE.defaultBlockState(), 3);
        }

        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie threat = spawnTarget(helper, start.east(2));
        archer.setOnGround(true);

        var escapeTarget = RangedCombatPositioning.findGroupEscapeTarget(
                archer,
                List.of(threat),
                List.of(threat),
                6.0D
        ).orElseThrow(() -> new AssertionError("no escape target found for topology fixture"));

        helper.assertTrue(
                !escapeTarget.getPathTargets(archer).contains(deadEnd),
                "escape target set retained the locally-open dead-end pocket instead of preferring onward space"
        );
        helper.assertTrue(
                escapeTarget.getPathTargets(archer).stream().anyMatch(pos -> pos.getY() > start.getY()),
                "escape target set did not retain the raised route with useful onward space"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_emergency_group_integration", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void emergencyFleePublishesEscapeFromNearbyThreatGroup(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 12);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie eastThreat = spawnTarget(helper, start.east(2));
        Zombie westThreat = spawnTarget(helper, start.west(4));
        archer.setOnGround(true);
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, eastThreat);
        archer.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, List.of(eastThreat, westThreat));
        archer.getBrain().setMemory(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                new net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities(archer, List.of(eastThreat, westThreat))
        );

        double eastInitial = archer.distanceToSqr(eastThreat);
        double westInitial = archer.distanceToSqr(westThreat);
        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
        long gameTime = helper.getLevel().getGameTime();
        helper.assertTrue(movement.tryStart(helper.getLevel(), archer, gameTime), "emergency group fixture could not start archer movement");
        movement.tickOrStop(helper.getLevel(), archer, gameTime + 1);

        helper.assertTrue(RangedCombatState.current(archer).orElse(null) == RangedCombatState.EMERGENCY_FLEE,
                "close threat did not enter EMERGENCY_FLEE");
        var walkTarget = archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        helper.assertTrue(walkTarget != null, "EMERGENCY_FLEE did not publish a group escape WALK_TARGET");
        Vec3 destination = walkTarget.getTarget().currentPosition();
        helper.assertTrue(destination.distanceToSqr(eastThreat.position()) > eastInitial,
                "EMERGENCY_FLEE group target moved closer to the east threat");
        helper.assertTrue(destination.distanceToSqr(westThreat.position()) > westInitial,
                "EMERGENCY_FLEE group target moved closer to the west threat");
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_strafe_sweep", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void strafeRejectsSideBlockedJustBeyondOldEndpointProbe(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 6);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        archer.setYRot(0.0F);
        setWallColumn(helper, start.south(2));

        helper.assertTrue(
                RangedCombatPositioning.findBestStrafeDirection(archer, List.of()) == -1.0F,
                "strafe selection accepted the side with less than 1.5 blocks of body clearance"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_strafe_hazard_side", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void strafePrefersSideFartherFromPassiveHazard(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 10);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        archer.setYRot(0.0F);
        Zombie southHazard = spawnTarget(helper, start.south(7));

        float direction = RangedCombatPositioning.findBestStrafeDirection(archer, List.of(southHazard));
        helper.assertTrue(
                direction == -1.0F,
                "strafe did not prefer the side farther from the hazard: " + direction
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_strafe_stable_tie", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void equalStrafeSidesUseStableTieBreak(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 10);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        archer.setYRot(0.0F);

        float first = RangedCombatPositioning.findBestStrafeDirection(archer, List.of());
        float second = RangedCombatPositioning.findBestStrafeDirection(archer, List.of());
        helper.assertTrue(
                first != 0.0F && first == second,
                "equal strafe sides did not produce a stable direction"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_passive_secondary", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void passiveSecondaryOutsideEmergencyDoesNotDriveRetreat(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 20);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(18));
        Zombie passive = spawnTarget(helper, start.north(5));
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        seedNearbyEnemies(archer, List.of(target, passive));

        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
        long now = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), archer, now);
        movement.tick(helper.getLevel(), archer, now + 1);

        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.APPROACH,
                "passive secondary hostile incorrectly drove retreat"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_engaging_secondary", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void engagingSecondaryDoesDriveRetreat(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 20);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(18));
        Zombie engaging = spawnTarget(helper, start.north(5));
        engaging.setTarget(archer);
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        seedNearbyEnemies(archer, List.of(target, engaging));

        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
        long now = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), archer, now);
        movement.tick(helper.getLevel(), archer, now + 1);

        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.KITE,
                "engaging secondary hostile did not drive kite spacing"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_vertical_threat_filter", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void elevatedAttackTargetDoesNotMaskSameLevelEscapeDriver(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 12);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie elevatedTarget = spawnTarget(helper, start.above(3));
        Zombie engaging = spawnTarget(helper, start.north(4));
        engaging.setTarget(archer);
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, elevatedTarget);
        seedNearbyEnemies(archer, List.of(elevatedTarget, engaging));

        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
        long now = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), archer, now);
        movement.tick(helper.getLevel(), archer, now + 1);

        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.KITE,
                "vertically irrelevant ATTACK_TARGET masked the same-level engaging threat"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_very_close_secondary", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void veryClosePassiveSecondaryTriggersEmergency(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 20);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(18));
        Zombie passive = spawnTarget(helper, start.north(3));
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        seedNearbyEnemies(archer, List.of(target, passive));

        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
        long now = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), archer, now);
        movement.tick(helper.getLevel(), archer, now + 1);

        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.EMERGENCY_FLEE,
                "hostile inside emergency range was ignored because it was passive"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_occluded_attack_threat", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void occludedAttackTargetStillDrivesCloseRetreat(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 10);
        setWallColumn(helper, start.east());
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(2));
        archer.setOnGround(true);
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        archer.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, List.of(target));
        seedVisibleEnemies(archer, List.of());

        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
        long now = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), archer, now);
        movement.tick(helper.getLevel(), archer, now + 1);

        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.EMERGENCY_FLEE,
                "occluded ATTACK_TARGET stopped being a close escape driver"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_combat_ownership", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void emergencyFleeTakesOverAfterPreCombatMovementOwnerYields(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 12);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(2));
        archer.setOnGround(true);
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        archer.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, List.of(target));

        var preCombatTarget = new net.minecraft.world.entity.ai.memory.WalkTarget(
                Vec3.atBottomCenterOf(start.east(10)),
                0.5F,
                0
        );
        archer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, preCombatTarget);

        WanderOrTeleportToTargetTask sink = new WanderOrTeleportToTargetTask();
        helper.assertTrue(sink.tryStart(helper.getLevel(), archer, helper.getLevel().getGameTime()),
                "fixture pre-combat movement owner did not start");
        var preCombatPath = archer.getBrain().getMemory(MemoryModuleType.PATH).orElse(null);
        helper.assertTrue(preCombatPath != null && preCombatPath.canReach(),
                "fixture pre-combat movement owner did not create a reachable path");

        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
        long gameTime = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), archer, gameTime);
        movement.tick(helper.getLevel(), archer, gameTime + 1);

        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.EMERGENCY_FLEE,
                "close threat did not enter EMERGENCY_FLEE"
        );
        var currentTarget = archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        helper.assertTrue(
                currentTarget == preCombatTarget,
                "EMERGENCY_FLEE directly stole a WALK_TARGET before its movement owner yielded"
        );

        sink.tickOrStop(helper.getLevel(), archer, gameTime + 2);
        helper.assertTrue(!archer.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                "pre-combat movement owner did not yield its WALK_TARGET to EMERGENCY_FLEE");
        helper.assertTrue(!archer.getBrain().hasMemoryValue(MemoryModuleType.PATH),
                "pre-combat movement owner did not release its PATH to EMERGENCY_FLEE");

        movement.tick(helper.getLevel(), archer, gameTime + 3);
        var emergencyTarget = archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        helper.assertTrue(emergencyTarget != null && emergencyTarget != preCombatTarget,
                "EMERGENCY_FLEE did not publish an escape target after the previous owner yielded");
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_foreign_walk_target", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void holdTransitionPreservesForeignWalkTarget(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 20);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(18));
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        seedNearbyEnemies(archer, List.of(target));

        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
        long gameTime = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), archer, gameTime);
        movement.tick(helper.getLevel(), archer, gameTime + 1);
        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.APPROACH,
                "fixture did not enter APPROACH before ownership handoff"
        );

        target.absMoveTo(start.getX() + 10.5D, start.getY(), start.getZ() + 0.5D);
        WalkTarget foreignWalkTarget = new WalkTarget(Vec3.atBottomCenterOf(start.north(6)), 0.4F, 0);
        archer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, foreignWalkTarget);

        movement.tick(helper.getLevel(), archer, gameTime + 2);

        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.HOLD,
                "in-range target did not transition combat movement to HOLD"
        );
        helper.assertTrue(
                archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == foreignWalkTarget,
                "HOLD transition erased a WALK_TARGET owned by another behavior"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_emergency_occluded_target", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void emergencyFleeStillEscapesOccludedAttackTarget(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 10);
        for (int y = 0; y <= 3; y++) {
            helper.getLevel().setBlock(start.east().above(y), Blocks.STONE.defaultBlockState(), 3);
        }

        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(2));
        archer.setOnGround(true);
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        archer.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, List.of(target));

        double initialDistanceSquared = archer.distanceToSqr(target);
        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
        long gameTime = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), archer, gameTime);
        movement.tick(helper.getLevel(), archer, gameTime + 1);

        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.EMERGENCY_FLEE,
                "occluded close attack target did not enter EMERGENCY_FLEE"
        );
        var walkTarget = archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        helper.assertTrue(walkTarget != null, "occluded close attack target left EMERGENCY_FLEE without an escape WALK_TARGET");
        helper.assertTrue(
                walkTarget.getTarget().currentPosition().distanceToSqr(target.position()) > initialDistanceSquared,
                "occluded close attack target produced an escape target that did not open distance"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_crowd_escape", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 220)
    public static void emergencyFleeChoosesOpenSideAgainstLargeThreatGroup(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(12, 2, 12));
        prepareFlatArea(helper, start, 18);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        List<Zombie> threats = List.of(
                spawnTarget(helper, start.west(2)),
                spawnTarget(helper, start.west(4).north(2)),
                spawnTarget(helper, start.west(4).south(2)),
                spawnTarget(helper, start.north(3)),
                spawnTarget(helper, start.south(3)),
                spawnTarget(helper, start.west(6).north(5)),
                spawnTarget(helper, start.west(6).south(5)),
                spawnTarget(helper, start.north(6).west(2)),
                spawnTarget(helper, start.south(6).west(2)),
                spawnTarget(helper, start.west(8))
        );
        Zombie attackTarget = threats.getFirst();
        threats.forEach(threat -> threat.setTarget(archer));
        archer.setOnGround(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, attackTarget);
        archer.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, List.copyOf(threats));
        archer.getBrain().setMemory(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                new net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities(archer, List.copyOf(threats))
        );
        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
        long gameTime = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), archer, gameTime);
        movement.tick(helper.getLevel(), archer, gameTime + 1);

        Vec3 origin = archer.position();
        double initialMinimumDistanceSquared = minimumDistanceSquared(origin, threats);
        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.EMERGENCY_FLEE,
                "large threat group did not trigger EMERGENCY_FLEE"
        );
        var walkTarget = archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        helper.assertTrue(walkTarget != null, "large threat group did not produce a group-aware escape target");
        Vec3 destination = walkTarget.getTarget().currentPosition();
        helper.assertTrue(
                destination.x > origin.x + 0.5D,
                "crowded EMERGENCY_FLEE target did not choose the open east side: destination=" + destination
        );
        helper.assertTrue(
                minimumDistanceSquared(destination, threats) > initialMinimumDistanceSquared,
                "crowded EMERGENCY_FLEE target did not improve minimum group spacing"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_crowd_natural_movement", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 260)
    public static void crowdedRetreatDoesNotThrashOrReverseRepeatedly(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(12, 2, 12));
        prepareFlatArea(helper, start, 18);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        List<Zombie> threats = List.of(
                spawnTarget(helper, start.west(2)),
                spawnTarget(helper, start.west(4).north(2)),
                spawnTarget(helper, start.west(4).south(2)),
                spawnTarget(helper, start.north(3)),
                spawnTarget(helper, start.south(3)),
                spawnTarget(helper, start.west(6).north(5)),
                spawnTarget(helper, start.west(6).south(5)),
                spawnTarget(helper, start.north(6).west(2)),
                spawnTarget(helper, start.south(6).west(2)),
                spawnTarget(helper, start.west(8)),
                spawnTarget(helper, start.north(8).west(4)),
                spawnTarget(helper, start.south(8).west(4))
        );
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, threats.getFirst());

        Vec3 origin = archer.position();
        double initialMinimumDistanceSquared = minimumDistanceSquared(origin, threats);
        Vec3[] lastPosition = {origin};
        Vec3[] lastMeaningfulDirection = {null};
        Vec3[] lastPublishedDestination = {null};
        RangedCombatState[] previousState = {null};
        int[] ticks = {0};
        int[] meaningfulMotionSamples = {0};
        int[] majorMotionReversals = {0};
        int[] destinationChanges = {0};
        int[] emergencyTicks = {0};
        int[] rangedUseTicks = {0};
        double[] emergencyEpisodeStartMinimumDistanceSquared = {Double.NaN};
        double[] bestCompletedEmergencySpacingGain = {Double.NEGATIVE_INFINITY};

        helper.onEachTick(() -> {
            ticks[0]++;
            RangedCombatState state = RangedCombatState.current(archer).orElse(null);
            if (state == RangedCombatState.EMERGENCY_FLEE) {
                if (previousState[0] != RangedCombatState.EMERGENCY_FLEE) {
                    emergencyEpisodeStartMinimumDistanceSquared[0] = minimumDistanceSquared(archer.position(), threats);
                }
                emergencyTicks[0]++;
                if (archer.isUsingItem()) {
                    rangedUseTicks[0]++;
                }

                var walkTarget = archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
                if (walkTarget != null) {
                    Vec3 destination = walkTarget.getTarget().currentPosition();
                    if (lastPublishedDestination[0] != null
                            && destination.distanceToSqr(lastPublishedDestination[0]) > 1.0D) {
                        destinationChanges[0]++;
                    }
                    lastPublishedDestination[0] = destination;
                }
            } else if (previousState[0] == RangedCombatState.EMERGENCY_FLEE
                    && !Double.isNaN(emergencyEpisodeStartMinimumDistanceSquared[0])) {
                double completedEpisodeGain = minimumDistanceSquared(archer.position(), threats)
                        - emergencyEpisodeStartMinimumDistanceSquared[0];
                bestCompletedEmergencySpacingGain[0] = Math.max(
                        bestCompletedEmergencySpacingGain[0],
                        completedEpisodeGain
                );
                emergencyEpisodeStartMinimumDistanceSquared[0] = Double.NaN;
            }
            previousState[0] = state;

            Vec3 position = archer.position();
            Vec3 motion = position.subtract(lastPosition[0]).multiply(1.0D, 0.0D, 1.0D);
            lastPosition[0] = position;
            if (motion.lengthSqr() > 0.0025D) {
                Vec3 direction = motion.normalize();
                meaningfulMotionSamples[0]++;
                if (lastMeaningfulDirection[0] != null && direction.dot(lastMeaningfulDirection[0]) < -0.5D) {
                    majorMotionReversals[0]++;
                }
                lastMeaningfulDirection[0] = direction;
            }

            if (ticks[0] >= 140) {
                helper.assertTrue(emergencyTicks[0] >= 10,
                        "crowd fixture produced too little EMERGENCY_FLEE time: " + emergencyTicks[0]);
                helper.assertTrue(meaningfulMotionSamples[0] >= 10,
                        "crowd fixture produced too little physical movement: " + meaningfulMotionSamples[0]);
                helper.assertTrue(majorMotionReversals[0] <= 2,
                        "crowded retreat repeatedly reversed direction: " + majorMotionReversals[0]);
                helper.assertTrue(destinationChanges[0] <= 4,
                        "crowded retreat thrashed WALK_TARGET destinations: " + destinationChanges[0]);
                helper.assertTrue(rangedUseTicks[0] == 0,
                        "archer tried to use its ranged weapon during EMERGENCY_FLEE for " + rangedUseTicks[0] + " ticks");
                helper.assertTrue(archer.position().distanceToSqr(origin) > 4.0D,
                        "crowded retreat did not make sustained physical progress: origin=" + origin + ", pos=" + archer.position());
                helper.assertTrue(bestCompletedEmergencySpacingGain[0] > 1.0D,
                        "no completed EMERGENCY_FLEE episode improved minimum crowd spacing: bestGain="
                                + bestCompletedEmergencySpacingGain[0]);
                helper.succeed();
            }
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
        closeThreat.setTarget(archer);
        archer.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, attackTarget);
        seedNearbyEnemies(archer, List.of(closeThreat, attackTarget));
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
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_archer_kite_group_escape", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void kiteRetreatDoesNotCloseOnNearbyThreatGroup(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 14);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie attackTarget = spawnTarget(helper, start.east(10));
        Zombie eastThreat = spawnTarget(helper, start.east(5));
        Zombie westThreat = spawnTarget(helper, start.west(5));
        archer.setOnGround(true);
        eastThreat.setTarget(archer);
        westThreat.setTarget(archer);
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, attackTarget);
        archer.getBrain().setMemory(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                new net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities(
                        archer,
                        List.of(eastThreat, westThreat, attackTarget)
                )
        );
        archer.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, List.of(eastThreat, westThreat, attackTarget));

        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(24);
        long gameTime = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), archer, gameTime);
        movement.tick(helper.getLevel(), archer, gameTime + 1);

        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.KITE,
                "two-sided threat fixture did not enter KITE"
        );
        var walkTarget = archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        helper.assertTrue(walkTarget != null, "KITE did not publish a retreat WALK_TARGET");
        Vec3 destination = walkTarget.getTarget().currentPosition();
        helper.assertTrue(
                destination.distanceToSqr(eastThreat.position()) > archer.distanceToSqr(eastThreat),
                "KITE retreat closed on east threat: destination=" + destination
        );
        helper.assertTrue(
                destination.distanceToSqr(westThreat.position()) > archer.distanceToSqr(westThreat),
                "KITE retreat closed on west threat: destination=" + destination
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_kite_group_fallback", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void kiteDoesNotUseEmergencyFallbackThatClosesOnAnotherThreat(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(12, 2, 12));
        prepareFlatArea(helper, start, 16);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie eastThreat = spawnTarget(helper, start.east(5));
        Zombie westThreat = spawnTarget(helper, start.west(10));
        Zombie northThreat = spawnTarget(helper, start.north(10));
        Zombie southThreat = spawnTarget(helper, start.south(10));
        List<Zombie> threats = List.of(eastThreat, westThreat, northThreat, southThreat);
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, eastThreat);
        archer.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, List.copyOf(threats));
        archer.getBrain().setMemory(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                new net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities(archer, List.copyOf(threats))
        );

        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(24);
        long gameTime = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), archer, gameTime);
        movement.tick(helper.getLevel(), archer, gameTime + 1);

        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.KITE,
                "surrounded fallback fixture did not enter KITE"
        );
        var walkTarget = archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (walkTarget != null) {
            Vec3 origin = archer.position();
            Vec3 destination = walkTarget.getTarget().currentPosition();
            for (Zombie threat : threats) {
                double requiredDistance = Math.min(origin.distanceTo(threat.position()), 6.0D);
                helper.assertTrue(
                        destination.distanceTo(threat.position()) + 1.0E-6D >= requiredDistance,
                        "KITE violated passive-hazard clearance: threat=" + threat.position()
                                + ", required=" + requiredDistance + ", destination=" + destination
                );
            }
            helper.assertTrue(
                    destination.distanceToSqr(eastThreat.position()) > origin.distanceToSqr(eastThreat.position()),
                    "KITE did not open distance from its active attack target: destination=" + destination
            );
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_passive_hazard_escape", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void retreatOpensFromDriverWithoutCuttingTowardPassiveHazard(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(12, 2, 12));
        prepareFlatArea(helper, start, 16);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie driver = spawnTarget(helper, start.east(2));
        Zombie passive = spawnTarget(helper, start.west(6));
        archer.setOnGround(true);
        double passiveStart = archer.distanceTo(passive);

        var escapeTarget = RangedCombatPositioning.findEmergencyEscapeTarget(
                archer,
                List.of(driver),
                List.of(driver, passive),
                6.0D
        ).orElseThrow();
        var path = archer.getNavigation().createPath(escapeTarget.getPathTargets(archer), 0);
        helper.assertTrue(path != null && path.canReach(), "escape target set was not reachable by vanilla navigation");
        Vec3 destination = Vec3.atBottomCenterOf(path.getTarget());

        helper.assertTrue(
                destination.distanceTo(driver.position()) > archer.distanceTo(driver),
                "escape did not open distance from the driver"
        );
        helper.assertTrue(
                destination.distanceTo(passive.position()) >= Math.min(passiveStart, 6.0D),
                "escape cut into passive hazard clearance"
        );
        for (int index = 0; index < path.getNodeCount(); index++) {
            Vec3 nodePosition = path.getEntityPosAtNode(archer, index);
            helper.assertTrue(
                    nodePosition.distanceTo(passive.position()) + 1.0E-6D >= Math.min(passiveStart, 6.0D),
                    "vanilla escape path cut through passive hazard clearance at " + nodePosition
            );
            helper.assertTrue(
                    nodePosition.distanceTo(driver.position()) + 1.0E-6D >= archer.distanceTo(driver),
                    "vanilla escape path initially closed on the escape driver at " + nodePosition
            );
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_hazard_approach", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void approachCandidateRoutesAroundSecondaryHazard(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(12, 2, 12));
        prepareFlatArea(helper, start, 20);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(18));
        Zombie passive = spawnTarget(helper, start.east(6));
        archer.setOnGround(true);

        Vec3 destination = RangedCombatPositioning.findApproachPosition(
                archer,
                target,
                List.of(passive)
        ).orElseThrow(() -> new AssertionError("no detour waypoint found on open terrain"));

        helper.assertTrue(
                destination.distanceTo(passive.position()) >= archer.distanceTo(passive),
                "approach waypoint entered the secondary hostile's safety bubble"
        );
        helper.assertTrue(
                Math.abs(destination.z - archer.getZ()) >= 1.0D,
                "approach waypoint did not route laterally around the hostile"
        );
        var path = archer.getNavigation().createPath(BlockPos.containing(destination), 0);
        helper.assertTrue(path != null && path.canReach(), "approach waypoint was not reachable by vanilla navigation");
        double requiredClearance = archer.distanceTo(passive);
        for (int index = 0; index < path.getNodeCount(); index++) {
            Vec3 nodePosition = path.getEntityPosAtNode(archer, index);
            helper.assertTrue(
                    nodePosition.distanceTo(passive.position()) + 1.0E-6D >= requiredClearance,
                    "vanilla approach path cut through secondary-hazard clearance at " + nodePosition
            );
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_inside_hazard", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void approachCandidateInsideHazardBubbleDoesNotMoveCloser(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(12, 2, 12));
        prepareFlatArea(helper, start, 20);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(18));
        Zombie passive = spawnTarget(helper, start.north(4));
        archer.setOnGround(true);
        double current = archer.distanceTo(passive);

        Vec3 destination = RangedCombatPositioning.findApproachPosition(
                archer,
                target,
                List.of(passive)
        ).orElseThrow();

        helper.assertTrue(
                destination.distanceTo(passive.position()) >= current,
                "approach moved closer while already inside a passive hazard bubble"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_hazard_ring", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void blockedByHazardRingDoesNotFallBackToDirectApproach(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(14, 2, 14));
        prepareFlatArea(helper, start, 22);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(18));
        List<Zombie> hazards = List.of(
                spawnTarget(helper, start.north(6)),
                spawnTarget(helper, start.south(6)),
                spawnTarget(helper, start.east(6)),
                spawnTarget(helper, start.west(6))
        );
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        List<LivingEntity> all = new ArrayList<>(hazards);
        all.add(target);
        seedNearbyEnemies(archer, all);

        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
        long now = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), archer, now);
        movement.tick(helper.getLevel(), archer, now + 1);

        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.APPROACH,
                "hazard ring unexpectedly changed tactical state"
        );
        helper.assertTrue(
                archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isEmpty(),
                "no-safe-waypoint case fell back to a direct target WALK_TARGET"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_hazard_removed", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 160)
    public static void removedSecondaryHazardReturnsToDirectApproach(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(12, 2, 12));
        prepareFlatArea(helper, start, 20);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(18));
        Zombie passive = spawnTarget(helper, start.east(6));
        archer.setOnGround(true);
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        seedNearbyEnemies(archer, List.of(target, passive));

        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
        long now = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), archer, now);
        movement.tick(helper.getLevel(), archer, now + 1);

        WalkTarget detour = archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
        helper.assertTrue(
                detour.getTarget().currentPosition().distanceToSqr(target.position()) > 0.01D,
                "secondary hazard did not produce a positional detour waypoint"
        );

        passive.discard();
        seedNearbyEnemies(archer, List.of(target));
        archer.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

        for (int tick = 2; tick <= 25; tick++) {
            movement.tick(helper.getLevel(), archer, now + tick);
        }

        WalkTarget direct = archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
        helper.assertTrue(
                direct.getTarget().currentPosition().distanceToSqr(target.position()) < 0.01D,
                "archer did not resume direct approach after the secondary hazard disappeared"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_kite_step_candidate", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void groupEscapeCanChooseOneBlockHigherGround(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(10, 2, 10));
        prepareFlatArea(helper, start, 12);
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                helper.getLevel().setBlock(start.offset(x, -1, z), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        helper.getLevel().setBlock(start.below(), Blocks.STONE.defaultBlockState(), 3);
        for (int x = -1; x >= -10; x--) {
            helper.getLevel().setBlock(start.offset(x, 0, 0), Blocks.STONE.defaultBlockState(), 3);
        }

        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie threat = spawnTarget(helper, start.east(5));
        helper.getLevel().setBlock(threat.blockPosition().below(), Blocks.STONE.defaultBlockState(), 3);
        archer.setOnGround(true);

        var escapeTarget = RangedCombatPositioning.findGroupEscapeTarget(
                        archer,
                        List.of(threat),
                        List.of(threat),
                        9.0D
                )
                .orElseThrow(() -> new AssertionError("group escape rejected the only safe one-block-higher lane"));
        var path = archer.getNavigation().createPath(escapeTarget.getPathTargets(archer), 0);
        helper.assertTrue(path != null && path.canReach(), "vanilla navigation could not reach the raised escape lane");
        Vec3 destination = Vec3.atBottomCenterOf(path.getTarget());
        helper.assertTrue(
                destination.y > archer.getY() + 0.5D,
                "group escape did not resolve the raised lane onto its walkable surface: destination=" + destination
        );
        helper.assertTrue(
                destination.distanceToSqr(threat.position()) > archer.distanceToSqr(threat),
                "raised escape candidate did not open distance from the threat"
        );
        helper.succeed();
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
        archer.setNoAi(true);
        archer.getBrain().setMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.ATTACK_TARGET, target);

        ArcherMovementTask<VillagerEntityMCA> task = new ArcherMovementTask<>(24);
        long gameTime = helper.getLevel().getGameTime();
        task.start(helper.getLevel(), archer, gameTime);

        target.absMoveTo(archer.getX() + 5.5D, archer.getY(), archer.getZ());
        task.tick(helper.getLevel(), archer, gameTime);
        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.KITE,
                "KITE did not enter below the 6-block threshold"
        );

        for (int i = 1; i <= 12; i++) {
            target.absMoveTo(archer.getX() + 7.0D, archer.getY(), archer.getZ());
            task.tick(helper.getLevel(), archer, gameTime + i);
            helper.assertTrue(
                    RangedCombatState.current(archer).orElse(null) == RangedCombatState.KITE,
                    "KITE exited inside 6/9 hysteresis band at 7 blocks"
            );
        }

        for (int i = 13; i <= 24; i++) {
            target.absMoveTo(archer.getX() + 8.9D, archer.getY(), archer.getZ());
            task.tick(helper.getLevel(), archer, gameTime + i);
            helper.assertTrue(
                    RangedCombatState.current(archer).orElse(null) == RangedCombatState.KITE,
                    "KITE exited before the 9-block threshold"
            );
        }

        target.absMoveTo(archer.getX() + 9.0D, archer.getY(), archer.getZ());
        task.tick(helper.getLevel(), archer, gameTime + 25);
        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.HOLD,
                "KITE did not exit at the 9-block threshold"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_range_hysteresis", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void attackRangeBoundaryDoesNotPingPongApproachAndHold(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start.east(8), 20);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(16));
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);

        ArcherMovementTask<VillagerEntityMCA> task = new ArcherMovementTask<>(24);
        long gameTime = helper.getLevel().getGameTime();
        task.start(helper.getLevel(), archer, gameTime);

        target.absMoveTo(archer.getX() + 15.05D, archer.getY(), archer.getZ());
        task.tick(helper.getLevel(), archer, gameTime + 1);
        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.APPROACH,
                "range-boundary fixture did not enter APPROACH just outside attack range"
        );

        target.absMoveTo(archer.getX() + 14.95D, archer.getY(), archer.getZ());
        task.tick(helper.getLevel(), archer, gameTime + 2);
        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.APPROACH,
                "tiny range-boundary crossing caused APPROACH -> "
                        + RangedCombatState.current(archer).orElse(null)
        );
        helper.succeed();
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

    @GameTest(batch = "mca_archer_kite_corner_escape", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void kiteUsesOpenLateralEscapeInsteadOfHoldingInCorner(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(10, 2, 10));
        prepareFlatArea(helper, start, 14);

        for (int x = -14; x <= 14; x++) {
            for (int z = -14; z <= 14; z++) {
                helper.getLevel().setBlock(start.offset(x, -1, z), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        helper.getLevel().setBlock(start.below(), Blocks.STONE.defaultBlockState(), 3);
        for (int z = -1; z >= -10; z--) {
            helper.getLevel().setBlock(start.offset(0, -1, z), Blocks.STONE.defaultBlockState(), 3);
        }

        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(5));
        helper.getLevel().setBlock(target.blockPosition().below(), Blocks.STONE.defaultBlockState(), 3);
        archer.setOnGround(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        archer.getRandom().setSeed(12L);

        ArcherMovementTask<VillagerEntityMCA> task = new ArcherMovementTask<>(24);
        long gameTime = helper.getLevel().getGameTime();
        task.start(helper.getLevel(), archer, gameTime);
        task.tick(helper.getLevel(), archer, gameTime);

        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.KITE,
                "corner fixture did not enter KITE"
        );
        var walkTarget = archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        helper.assertTrue(walkTarget != null, "KITE held still even though the lateral escape lane is open");
        helper.assertTrue(
                walkTarget.getTarget().currentPosition().distanceToSqr(target.position()) > archer.distanceToSqr(target),
                "KITE corner escape did not open distance from the threat"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_kite_body_facing", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 260)
    public static void movingKiteBowDrawKeepsTargetLookWhileNavigationOwnsBody(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 16);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(7));
        Zombie closeThreat = spawnTarget(helper, start.north(4));
        closeThreat.setTarget(archer);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
        target.setHealth(200.0F);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        seedNearbyEnemies(archer, List.of(closeThreat, target));
        Vec3 origin = archer.position();
        int[] ticks = {0};

        helper.onEachTick(() -> {
            ticks[0]++;
            boolean moved = archer.position().distanceToSqr(origin) > 0.25D;
            if (RangedCombatState.current(archer).orElse(null) == RangedCombatState.KITE
                    && archer.isUsingItem()
                    && archer.getTicksUsingItem() >= 4
                    && moved) {
                var lookTarget = archer.getBrain().getMemory(MemoryModuleType.LOOK_TARGET).orElse(null);
                helper.assertTrue(
                        lookTarget != null && lookTarget.currentPosition().distanceToSqr(target.getEyePosition()) < 0.01D,
                        "moving KITE bow draw lost attack-target look ownership"
                );
                helper.assertTrue(
                        Math.abs(Mth.wrapDegrees(archer.getYRot() - archer.yBodyRot)) <= 5.0F,
                        "moving KITE bow draw fought navigation body yaw; yRot=" + archer.getYRot()
                                + ", bodyYaw=" + archer.yBodyRot
                );
                helper.assertTrue(
                        Math.abs(Mth.wrapDegrees(archer.yHeadRot - archer.yBodyRot)) <= archer.getMaxHeadYRot() + 1.0F,
                        "moving KITE bow draw exceeded vanilla head/body yaw limit"
                );
                helper.succeed();
                return;
            }

            if (ticks[0] >= 220) {
                helper.fail("fixture never observed a moving KITE bow draw");
            }
        });
    }

    @GameTest(batch = "mca_archer_kite_speed", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void activeKiteRangedUsePreservesNavigationSpeed(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 16);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(7));
        Zombie closeThreat = spawnTarget(helper, start.north(4));
        closeThreat.setTarget(archer);
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        seedNearbyEnemies(archer, List.of(closeThreat, target));
        archer.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);

        ArcherMovementTask<VillagerEntityMCA> task = new ArcherMovementTask<>(24);
        long gameTime = helper.getLevel().getGameTime();
        double moveControlSpeedBeforeTick = archer.getMoveControl().getSpeedModifier();
        task.start(helper.getLevel(), archer, gameTime);
        task.tick(helper.getLevel(), archer, gameTime);

        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.KITE,
                "active ranged-use fixture did not enter KITE"
        );
        helper.assertTrue(
                Math.abs(archer.getMoveControl().getSpeedModifier() - moveControlSpeedBeforeTick) < 1.0E-6D,
                "active KITE ranged use bypassed Brain navigation with a direct MoveControl command; before="
                        + moveControlSpeedBeforeTick + ", actual=" + archer.getMoveControl().getSpeedModifier()
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_bow_facing_ownership", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void bowDrawDoesNotOverrideNavigationFacing(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 12);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(10));
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);

        BowTask<VillagerEntityMCA> bow = new BowTask<>(20, 24);
        long gameTime = helper.getLevel().getGameTime();
        bow.start(helper.getLevel(), archer, gameTime);
        bow.tick(helper.getLevel(), archer, gameTime + 1);
        helper.assertTrue(archer.isUsingItem(), "bow fixture did not begin drawing");

        archer.setYRot(45.0F);
        bow.tick(helper.getLevel(), archer, gameTime + 2);
        helper.assertTrue(
                Math.abs(Mth.wrapDegrees(archer.getYRot() - 45.0F)) < 1.0E-3F,
                "BowTask overrode locomotion yaw while drawing; actual=" + archer.getYRot()
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_kite_path_speed", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void kitePathDoesNotAccelerateAboveNormalApproachSpeed(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 16);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(10));
        Zombie closeThreat = spawnTarget(helper, start.north(4));
        archer.setOnGround(true);
        closeThreat.setTarget(archer);
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        seedNearbyEnemies(archer, List.of(closeThreat, target));

        ArcherMovementTask<VillagerEntityMCA> task = new ArcherMovementTask<>(24);
        long gameTime = helper.getLevel().getGameTime();
        task.start(helper.getLevel(), archer, gameTime);
        task.tick(helper.getLevel(), archer, gameTime);

        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.KITE,
                "path-speed fixture did not enter KITE"
        );
        var walkTarget = archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        helper.assertTrue(walkTarget != null, "KITE did not publish a WALK_TARGET");
        helper.assertTrue(
                Math.abs(walkTarget.getSpeedModifier() - 0.5F) < 1.0E-6F,
                "KITE path accelerated above normal approach speed; actual=" + walkTarget.getSpeedModifier()
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_kite_crossbow_facing", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 300)
    public static void kiteCrossbowChargeKeepsTargetLookOwnership(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 16);
        VillagerEntityMCA archer = spawnArcher(helper, start, Items.CROSSBOW.getDefaultInstance());
        Zombie target = spawnTarget(helper, start.east(7));
        Zombie closeThreat = spawnTarget(helper, start.north(4));
        closeThreat.setTarget(archer);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
        target.setHealth(200.0F);
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        seedNearbyEnemies(archer, List.of(closeThreat, target));

        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
        ExtendedCrossbowAttackTask<VillagerEntityMCA, Zombie> crossbow = new ExtendedCrossbowAttackTask<>();
        long gameTime = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), archer, gameTime);
        helper.assertTrue(
                crossbow.tryStart(helper.getLevel(), archer, gameTime),
                "crossbow behavior did not start for visible in-range target"
        );
        int[] ticks = {0};

        helper.onEachTick(() -> {
            ticks[0]++;
            movement.tick(helper.getLevel(), archer, gameTime + ticks[0]);
            crossbow.tickOrStop(helper.getLevel(), archer, gameTime + ticks[0]);
            if (RangedCombatState.current(archer).orElse(null) == RangedCombatState.KITE
                    && archer.isUsingItem()
                    && archer.getTicksUsingItem() >= 4) {
                var lookTarget = archer.getBrain().getMemory(MemoryModuleType.LOOK_TARGET).orElse(null);
                helper.assertTrue(
                        lookTarget != null && lookTarget.currentPosition().distanceToSqr(target.getEyePosition()) < 0.01D,
                        "KITE crossbow charge lost attack-target look ownership"
                );
                helper.succeed();
                return;
            }

            if (ticks[0] >= 260) {
                helper.fail("fixture never observed a moving KITE crossbow charge");
            }
        });
    }

    @GameTest(batch = "mca_archer_retreat_airborne_handoff", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void airborneEmergencyToKiteKeepsExistingEscapeRoute(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 12);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(2));
        archer.setOnGround(true);
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        seedNearbyEnemies(archer, List.of(target));

        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
        long gameTime = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), archer, gameTime);
        movement.tick(helper.getLevel(), archer, gameTime + 1);

        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.EMERGENCY_FLEE,
                "close target did not enter EMERGENCY_FLEE"
        );
        WalkTarget emergencyTarget = archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();

        target.absMoveTo(start.getX() + 6.5D, start.getY(), start.getZ() + 0.5D);
        archer.setOnGround(false);
        movement.tick(helper.getLevel(), archer, gameTime + 2);

        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.KITE,
                "EMERGENCY_FLEE did not transition to KITE inside the retreat hysteresis band"
        );
        helper.assertTrue(
                archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == emergencyTarget,
                "airborne retreat transition discarded the existing safe escape route"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_kite_step_navigation", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 360)
    public static void kiteNavigationClimbsOneBlockRaisedEscapeLane(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(10, 2, 10));
        prepareFlatArea(helper, start, 12);
        for (int x = -12; x <= 12; x++) {
            for (int z = -12; z <= 12; z++) {
                helper.getLevel().setBlock(start.offset(x, -1, z), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        helper.getLevel().setBlock(start.below(), Blocks.STONE.defaultBlockState(), 3);
        for (int x = -1; x >= -10; x--) {
            helper.getLevel().setBlock(start.offset(x, 0, 0), Blocks.STONE.defaultBlockState(), 3);
        }

        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(5));
        helper.getLevel().setBlock(target.blockPosition().below(), Blocks.STONE.defaultBlockState(), 3);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200.0D);
        target.setHealth(200.0F);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);

        double startY = archer.getY();
        int[] ticks = {0};
        helper.onEachTick(() -> {
            ticks[0]++;
            if (archer.getY() > startY + 0.75D && archer.getX() < start.getX()) {
                helper.succeed();
                return;
            }

            if (ticks[0] >= 300) {
                helper.fail("KITE navigation never climbed the one-block escape lane; state="
                        + RangedCombatState.current(archer).orElse(null)
                        + ", pos=" + archer.position()
                        + ", navDone=" + archer.getNavigation().isDone());
            }
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

    @GameTest(batch = "mca_archer_reposition_fallback", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 300)
    public static void blockedLosRepositionPublishesMovementAndTracksAttackTarget(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        BlockPos targetPos = start.east(10);
        prepareFlatArea(helper, start.east(5), 12);
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }
                BlockPos blocked = start.offset(x, 0, z);
                helper.getLevel().setBlock(blocked, Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(blocked.above(), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, targetPos);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        int[] ticks = {0};

        helper.onEachTick(() -> {
            ticks[0]++;
            RangedCombatState state = RangedCombatState.current(archer).orElse(null);
            if (state == RangedCombatState.REPOSITION) {
                var walkTarget = archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
                helper.assertTrue(walkTarget != null, "REPOSITION did not publish a movement target");
                var lookTarget = archer.getBrain().getMemory(MemoryModuleType.LOOK_TARGET).orElse(null);
                helper.assertTrue(
                        lookTarget != null && lookTarget.currentPosition().distanceToSqr(target.getEyePosition()) < 0.01D,
                        "REPOSITION did not keep tracking the attack target"
                );
                helper.succeed();
                return;
            }

            if (ticks[0] >= 200) {
                helper.fail("blocked LOS never produced REPOSITION movement; state=" + state);
            }
        });
    }

    @GameTest(batch = "mca_archer_blocked_close_los", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void sustainedBlockedCloseTargetKeepsKiteSafety(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        BlockPos targetPos = start.east(5);
        prepareFlatArea(helper, start.east(2), 8);
        for (int z = -1; z <= 1; z++) {
            for (int y = 0; y <= 3; y++) {
                helper.getLevel().setBlock(start.east(2).offset(0, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }

        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, targetPos);
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);

        ArcherMovementTask<VillagerEntityMCA> task = new ArcherMovementTask<>(24);
        long gameTime = helper.getLevel().getGameTime();
        task.start(helper.getLevel(), archer, gameTime);
        int[] ticks = {0};

        helper.onEachTick(() -> {
            ticks[0]++;
            task.tick(helper.getLevel(), archer, gameTime + ticks[0]);
            RangedCombatState state = RangedCombatState.current(archer).orElse(null);
            if (ticks[0] >= 11) {
                helper.assertTrue(
                        state == RangedCombatState.KITE,
                        "sustained blocked close target left close-threat safety for " + state
                );
            }
            if (ticks[0] >= 40) {
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
        Zombie target = spawnTarget(helper, start.east(7));
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        seedNearbyEnemies(archer, List.of(target));
        ExtendedCrossbowAttackTask<VillagerEntityMCA, Zombie> task = new ExtendedCrossbowAttackTask<>();
        long gameTime = helper.getLevel().getGameTime();
        helper.assertTrue(
                task.tryStart(helper.getLevel(), archer, gameTime),
                "crossbow behavior did not start before emergency transition"
        );
        task.tickOrStop(helper.getLevel(), archer, gameTime + 1);
        helper.assertTrue(archer.isUsingItem(), "crossbow fixture did not begin charging");
        helper.assertTrue(
                archer.getBrain().getMemory(MemoryModuleType.LOOK_TARGET)
                        .map(tracker -> tracker.currentPosition().distanceToSqr(target.getEyePosition()) < 0.01D)
                        .orElse(false),
                "crossbow behavior did not claim attack-target LOOK_TARGET while charging"
        );

        archer.getBrain().setMemory(MemoryModuleTypeMCA.RANGED_COMBAT_STATE, RangedCombatState.EMERGENCY_FLEE);
        task.tickOrStop(helper.getLevel(), archer, gameTime + 2);
        helper.assertTrue(!archer.isUsingItem(), "crossbow charge continued during EMERGENCY_FLEE");
        helper.assertTrue(
                task.getStatus() == net.minecraft.world.entity.ai.behavior.Behavior.Status.STOPPED,
                "crossbow behavior remained running during EMERGENCY_FLEE"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_reposition", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 240)
    public static void sustainedBlockedLosKeepsLookAndPublishesRepositionIntent(GameTestHelper helper) {
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
            var walkTarget = archer.getBrain().getMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).orElse(null);
            helper.assertTrue(walkTarget != null, "REPOSITION did not publish a firing candidate or approach fallback");
            Vec3 destination = walkTarget.getTarget().currentPosition();
            if (destination.distanceToSqr(target.position()) >= 0.01D) {
                    helper.assertTrue(
                            destination.distanceTo(target.position()) >= minimumAllowedDistance,
                            "REPOSITION published a firing destination that closes on the attack target"
                    );
            }
            helper.succeed();
        });
    }

    @GameTest(batch = "mca_archer_reposition_stability", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void unchangedBlockedLosKeepsSamePreferredFiringLane(GameTestHelper helper) {
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
        archer.setNoAi(true);
        archer.setOnGround(true);

        Vec3 first = RangedCombatPositioning.findFiringPosition(archer, target, List.of(target), 225.0D)
                .orElseThrow(() -> new AssertionError("blocked-LOS fixture did not produce a firing lane"));
        for (int attempt = 0; attempt < 4; attempt++) {
            Vec3 repeated = RangedCombatPositioning.findFiringPosition(archer, target, List.of(target), 225.0D)
                    .orElseThrow(() -> new AssertionError("unchanged blocked-LOS fixture lost its firing lane"));
            helper.assertTrue(
                    repeated.distanceToSqr(first) < 1.0E-6D,
                    "unchanged blocked LOS changed firing lane from " + first + " to " + repeated
            );
        }
        helper.succeed();
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
                helper.assertTrue(
                        archer.getNavigation().isDone(),
                        "HOLD cleared WALK_TARGET but left the previous combat navigation running"
                );
                helper.succeed();
            }
        });
    }

    @GameTest(batch = "mca_archer_reaction_time", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void stableVisibleTargetBeginsStrafeAfterActiveHoldDelay(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 12);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(10));
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);

        ArcherMovementTask<VillagerEntityMCA> task = new ArcherMovementTask<>(24);
        long gameTime = helper.getLevel().getGameTime();
        task.start(helper.getLevel(), archer, gameTime);

        for (int tick = 1; tick < 10; tick++) {
            task.tick(helper.getLevel(), archer, gameTime + tick);
        }

        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.HOLD,
                "stable visible target strafed before the active 10-tick hold delay; state="
                        + RangedCombatState.current(archer).orElse(null)
        );

        task.tick(helper.getLevel(), archer, gameTime + 10);
        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.STRAFE,
                "stable visible target did not strafe after the active 10-tick hold delay; state="
                        + RangedCombatState.current(archer).orElse(null)
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_hold_ownership", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void holdPreservesPreCombatNavigation(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 12);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(10));
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        WalkTarget preCombatWalkTarget = new WalkTarget(Vec3.atBottomCenterOf(start.north(8)), 0.5F, 0);
        archer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, preCombatWalkTarget);
        archer.setOnGround(true);
        helper.assertTrue(
                archer.getNavigation().moveTo(start.getX() + 0.5D, start.getY(), start.getZ() - 8.5D, 0.5D),
                "pre-combat navigation fixture could not start a path"
        );
        helper.assertTrue(!archer.getNavigation().isDone(), "pre-combat navigation fixture started already done");

        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(24);
        long gameTime = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), archer, gameTime);
        movement.tick(helper.getLevel(), archer, gameTime + 1);

        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.HOLD,
                "stable target did not enter HOLD; state=" + RangedCombatState.current(archer).orElse(null)
        );
        helper.assertTrue(
                archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == preCombatWalkTarget,
                "HOLD erased a pre-combat WALK_TARGET owned by another behavior"
        );
        helper.assertTrue(!archer.getNavigation().isDone(), "HOLD stopped pre-combat navigation owned by another behavior");
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_hold_handoff", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void holdDoesNotContinuouslyEraseLaterMovementIntent(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 12);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(10));
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);

        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(24);
        long gameTime = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), archer, gameTime);
        movement.tick(helper.getLevel(), archer, gameTime + 1);

        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.HOLD,
                "stable target did not enter HOLD before handoff; state=" + RangedCombatState.current(archer).orElse(null)
        );

        WalkTarget laterMovementIntent = new WalkTarget(Vec3.atBottomCenterOf(start.north(6)), 0.5F, 0);
        archer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, laterMovementIntent);
        movement.tick(helper.getLevel(), archer, gameTime + 2);

        helper.assertTrue(
                archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == laterMovementIntent,
                "stable HOLD repeatedly erased movement intent published after direct movement was claimed"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_kite_handoff", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void kiteDoesNotOverwriteLaterMovementIntent(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 12);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(5));
        archer.setOnGround(true);
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);

        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(24);
        long gameTime = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), archer, gameTime);
        movement.tick(helper.getLevel(), archer, gameTime + 1);

        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.KITE,
                "close target did not enter KITE before handoff; state=" + RangedCombatState.current(archer).orElse(null)
        );
        helper.assertTrue(
                archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isPresent(),
                "KITE did not publish its initial combat WALK_TARGET"
        );

        WalkTarget laterMovementIntent = new WalkTarget(Vec3.atBottomCenterOf(start.north(6)), 0.5F, 0);
        archer.getBrain().setMemory(MemoryModuleType.WALK_TARGET, laterMovementIntent);
        movement.tick(helper.getLevel(), archer, gameTime + 2);

        helper.assertTrue(
                archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == laterMovementIntent,
                "stable KITE overwrote movement intent published by another owner"
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_strafe_speed", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
    public static void directStrafeUsesNormalCombatSpeedModifier(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
        prepareFlatArea(helper, start, 12);
        VillagerEntityMCA archer = spawnArcher(helper, start);
        Zombie target = spawnTarget(helper, start.east(10));
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);

        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(24);
        long gameTime = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), archer, gameTime);
        for (int tick = 1; tick <= 20; tick++) {
            movement.tick(helper.getLevel(), archer, gameTime + tick);
        }

        helper.assertTrue(
                RangedCombatState.current(archer).orElse(null) == RangedCombatState.STRAFE,
                "stable target did not enter STRAFE; state=" + RangedCombatState.current(archer).orElse(null)
        );
        helper.assertTrue(
                Math.abs(archer.getMoveControl().getSpeedModifier() - 0.5D) < 1.0E-6D,
                "direct STRAFE inherited vanilla's 0.25 speed modifier; actual=" + archer.getMoveControl().getSpeedModifier()
        );
        helper.succeed();
    }

    @GameTest(batch = "mca_archer_stable_strafe", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 360)
    public static void stableTargetUsesBoundedStrafeBursts(GameTestHelper helper) {
        cleanupTestEntities();
        BlockPos start = helper.absolutePos(new BlockPos(6, 2, 6));
        prepareFlatArea(helper, start.east(5), 40);
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
                        helper.assertTrue(nonStrafeGap >= 20, "STRAFE cooldown gap was only " + nonStrafeGap + " ticks");
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
                helper.assertTrue(currentRunTicks[0] <= 40, "STRAFE burst exceeded 40 ticks");
            } else if (currentRunTicks[0] > 0) {
                helper.assertTrue(currentRunTicks[0] >= 24, "clear-lane STRAFE burst ended after only " + currentRunTicks[0] + " ticks");
                helper.assertTrue(currentRunTicks[0] <= 40, "clear-lane STRAFE burst lasted " + currentRunTicks[0] + " ticks");
                completedRuns[0]++;
                lastRunEndTick[0] = ticks[0] - 1;
                currentRunTicks[0] = 0;
                currentRunSign[0] = 0;
            }

            if (ticks[0] >= 280) {
                helper.assertTrue(completedRuns[0] >= 2, "stable target produced fewer than two completed STRAFE bursts: " + completedRuns[0]);
                helper.assertTrue(holdTicks[0] > 0, "stable target never returned to HOLD between STRAFE bursts");
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
        archer.setNoAi(true);
        archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        seedNearbyEnemies(archer, List.of(target));

        ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
        long gameTime = helper.getLevel().getGameTime();
        movement.start(helper.getLevel(), archer, gameTime);

        boolean[] blockedBurst = {false};
        boolean[] sawCancellation = {false};
        int[] cancellationTick = {-1};
        int[] ticks = {0};

        helper.onEachTick(() -> {
            ticks[0]++;
            movement.tick(helper.getLevel(), archer, gameTime + ticks[0]);
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

    private static void seedNearbyEnemies(VillagerEntityMCA archer, List<? extends LivingEntity> enemies) {
        archer.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, List.copyOf(enemies));
        seedVisibleEnemies(archer, enemies);
    }

    private static void seedVisibleEnemies(VillagerEntityMCA archer, List<? extends LivingEntity> enemies) {
        archer.getBrain().setMemory(
                MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                new net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities(archer, List.copyOf(enemies))
        );
    }

    private static void setWallColumn(GameTestHelper helper, BlockPos feet) {
        helper.getLevel().setBlock(feet, Blocks.STONE.defaultBlockState(), 3);
        helper.getLevel().setBlock(feet.above(), Blocks.STONE.defaultBlockState(), 3);
    }

    private static void cleanupTestEntities() {
        TEST_ENTITIES.forEach(Entity::discard);
        TEST_ENTITIES.clear();
    }

    private static double minimumDistanceSquared(Vec3 position, List<? extends Entity> threats) {
        double minimum = Double.POSITIVE_INFINITY;
        for (Entity threat : threats) {
            minimum = Math.min(minimum, position.distanceToSqr(threat.position()));
        }
        return minimum;
    }

    private static float yawTo(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return (float)(Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
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
