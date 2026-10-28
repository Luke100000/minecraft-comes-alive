package net.conczin.mca.entity.ai.brain.sensor;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.SchedulesMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
@PrefixGameTestTemplate(false)
public final class GuardEnemiesSensorGameTests {
    private static final List<Entity> TEST_ENTITIES = new ArrayList<>();
    private static Long originalBedtimeDayTime;

    private GuardEnemiesSensorGameTests() {
    }

    @GameTest(batch = "mca_guard_bedtime_far_enemy", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 240)
    public static void distantEnemyDoesNotKeepGuardAwakeAtBedtime(GameTestHelper helper) {
        cleanupTestEntities();
        var level = helper.getLevel();
        originalBedtimeDayTime = level.getDayTime();
        level.setDayTime(14990);

        BlockPos guardPos = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos enemyPos = guardPos.east(24);
        prepareClearLane(helper, guardPos, enemyPos);
        // The air template has no floor alongside the sightline. Provide a
        // solid approach to HOME so falling into the void is not a false failure.
        for (BlockPos floor : BlockPos.betweenClosed(guardPos.offset(-4, -1, -5),
                guardPos.offset(9, -1, 5))) {
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 3);
            level.setBlock(floor.above(), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(floor.above(2), Blocks.AIR.defaultBlockState(), 3);
        }
        // Test bedtime selection, not long-distance pathfinding: HOME is beside the guard.
        BlockPos foot = guardPos.north();
        BlockPos head = foot.east();
        level.setBlock(foot.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(head.below(), Blocks.STONE.defaultBlockState(), 3);
        var bed = Blocks.WHITE_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.EAST)
                .setValue(BedBlock.PART, BedPart.FOOT);
        level.setBlock(foot, bed, 3);
        level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 3);

        VillagerEntityMCA guard = VillagerFactory.newVillager(level)
                .withAge(0)
                .withProfession(ProfessionsMCA.GUARD)
                .withPosition(Vec3.atBottomCenterOf(guardPos))
                .withName("Guard Bedtime Combat Probe")
                .spawn(MobSpawnType.STRUCTURE);
        TEST_ENTITIES.add(guard);
        guard.refreshBrain(level);
        guard.getBrain().setSchedule(SchedulesMCA.GUARD);
        guard.getBrain().updateActivityFromSchedule(level.getDayTime(), level.getGameTime());
        helper.assertTrue(level.getPoiManager().take(poi -> poi.is(PoiTypes.HOME),
                        (poi, pos) -> pos.equals(head), head, 1).isPresent(),
                "fixture failed to reserve a real HOME bed");
        guard.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), head));

        Zombie target = EntityType.ZOMBIE.create(level);
        if (target == null) {
            throw new IllegalStateException("failed to create distant enemy");
        }
        target.absMoveTo(enemyPos.getX() + 0.5D, enemyPos.getY(), enemyPos.getZ() + 0.5D);
        target.setNoAi(true);
        target.setTarget(guard);
        level.addFreshEntity(target);
        TEST_ENTITIES.add(target);
        new GuardEnemiesSensor().doTick(level, guard);
        helper.assertTrue(guard.getBrain().getMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY)
                        .orElse(null) == target,
                "fixture guard failed to detect the 24-block enemy while on duty");
        guard.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);

        level.setDayTime(15010);
        guard.getBrain().updateActivityFromSchedule(level.getDayTime(), level.getGameTime());
        long started = level.getGameTime();
        helper.onEachTick(() -> {
            if (guard.isSleeping()) {
                helper.assertTrue(guard.getBrain().isActive(Activity.REST),
                        "guard fell asleep outside its REST activity");
                helper.assertTrue(guard.getSleepingPos().filter(head::equals).isPresent(),
                        "guard slept in a different bed");
                helper.assertTrue(guard.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty(),
                        "guard kept the distant enemy while sleeping");
                helper.succeed();
            } else if (level.getGameTime() - started >= 180) {
                helper.fail("guard stayed awake at bedtime with a 24-block enemy: activity="
                        + guard.getBrain().getActiveNonCoreActivity() + ", attackTarget="
                        + guard.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET)
                        + ", home=" + guard.getBrain().getMemory(MemoryModuleType.HOME)
                        + ", walk=" + guard.getBrain().getMemory(MemoryModuleType.WALK_TARGET)
                        + ", path=" + guard.getNavigation().getPath()
                        + ", pos=" + guard.position());
            }
        });
    }

    @GameTest(batch = "mca_guard_bedtime_near_enemy", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void nearbyEnemyStillKeepsGuardDefendingAtBedtime(GameTestHelper helper) {
        cleanupTestEntities();
        var level = helper.getLevel();
        originalBedtimeDayTime = level.getDayTime();
        level.setDayTime(15010);

        BlockPos guardPos = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos enemyPos = guardPos.east(6);
        prepareClearLane(helper, guardPos, enemyPos);
        BlockPos foot = guardPos.north();
        BlockPos head = foot.east();
        level.setBlock(foot.below(), Blocks.STONE.defaultBlockState(), 3);
        level.setBlock(head.below(), Blocks.STONE.defaultBlockState(), 3);
        var bed = Blocks.WHITE_BED.defaultBlockState()
                .setValue(BedBlock.FACING, Direction.EAST)
                .setValue(BedBlock.PART, BedPart.FOOT);
        level.setBlock(foot, bed, 3);
        level.setBlock(head, bed.setValue(BedBlock.PART, BedPart.HEAD), 3);

        VillagerEntityMCA guard = VillagerFactory.newVillager(level)
                .withAge(0)
                .withProfession(ProfessionsMCA.GUARD)
                .withPosition(Vec3.atBottomCenterOf(guardPos))
                .withName("Guard Nearby Bedtime Defense Probe")
                .spawn(MobSpawnType.STRUCTURE);
        TEST_ENTITIES.add(guard);
        guard.refreshBrain(level);
        guard.getBrain().setSchedule(SchedulesMCA.GUARD);
        helper.assertTrue(level.getPoiManager().take(poi -> poi.is(PoiTypes.HOME),
                        (poi, pos) -> pos.equals(head), head, 1).isPresent(),
                "fixture failed to reserve a real HOME bed");
        guard.getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(level.dimension(), head));

        Zombie target = EntityType.ZOMBIE.create(level);
        if (target == null) {
            throw new IllegalStateException("failed to create nearby enemy");
        }
        target.absMoveTo(enemyPos.getX() + 0.5D, enemyPos.getY(), enemyPos.getZ() + 0.5D);
        target.setNoAi(true);
        level.addFreshEntity(target);
        TEST_ENTITIES.add(target);
        guard.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        guard.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        guard.getBrain().updateActivityFromSchedule(level.getDayTime(), level.getGameTime());

        helper.runAfterDelay(10, () -> {
            helper.assertTrue(target.isAlive() && guard.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET)
                            .orElse(null) == target,
                    "a nearby hostile stopped being a valid attack target during REST");
            helper.assertTrue(!guard.isSleeping(),
                    "guard slept instead of defending against the nearby hostile");
            helper.succeed();
        });
    }

    @AfterBatch(batch = "mca_guard_bedtime_far_enemy")
    public static void restoreBedtimeGameTest(net.minecraft.server.level.ServerLevel level) {
        cleanupTestEntities();
        if (originalBedtimeDayTime != null) {
            level.setDayTime(originalBedtimeDayTime);
            originalBedtimeDayTime = null;
        }
    }

    @AfterBatch(batch = "mca_guard_bedtime_near_enemy")
    public static void restoreNearbyBedtimeGameTest(net.minecraft.server.level.ServerLevel level) {
        restoreBedtimeGameTest(level);
    }

    @GameTest(batch = "mca_guard_scan_bounds", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void distantFollowedPlayerDoesNotCreateUnboundedGuardScan(GameTestHelper helper)
            throws ReflectiveOperationException {
        BlockPos guardPos = helper.absolutePos(new BlockPos(3, 2, 3));
        VillagerEntityMCA guard = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withProfession(ProfessionsMCA.GUARD)
                .withPosition(Vec3.atBottomCenterOf(guardPos))
                .withName("Guard Scan Bounds Probe")
                .spawn(MobSpawnType.STRUCTURE);
        Player followedPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        Method getBounds = GuardEnemiesSensor.class.getDeclaredMethod(
                "getGuardScanBounds", VillagerEntityMCA.class, Optional.class);
        getBounds.setAccessible(true);
        try {
            AABB guardOnly = guard.getBoundingBox().inflate(48.0D);

            followedPlayer.absMoveTo(guard.getX() + 24.0D, guard.getY(), guard.getZ());
            AABB nearbyFollowBounds = (AABB) getBounds.invoke(new GuardEnemiesSensor(),
                    guard, Optional.of(followedPlayer));
            helper.assertTrue(nearbyFollowBounds.equals(
                            guardOnly.minmax(followedPlayer.getBoundingBox().inflate(48.0D))),
                    "nearby followed players should retain both 48-block defense areas");

            followedPlayer.absMoveTo(guard.getX() + 320.0D, guard.getY(), guard.getZ());
            AABB distantFollowBounds = (AABB) getBounds.invoke(new GuardEnemiesSensor(),
                    guard, Optional.of(followedPlayer));
            helper.assertTrue(distantFollowBounds.equals(guardOnly),
                    "a distant followed player must not expand the guard scan across unloaded space; bounds="
                            + distantFollowBounds);
            helper.succeed();
        } finally {
            guard.discard();
            followedPlayer.discard();
        }
    }

    @GameTest(batch = "mca_guard_followed_player_defense", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void distantFollowedPlayerEnemiesAreScannedWithoutHugeBoundingBox(GameTestHelper helper) {
        // Keep the attacker in the test's ticking chunk and the sightline
        // above other templates, without changing terrain outside this fixture.
        BlockPos enemyPos = helper.absolutePos(new BlockPos(3, 140, 3));
        BlockPos guardPos = enemyPos.east(116);

        VillagerEntityMCA guard = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withProfession(ProfessionsMCA.GUARD)
                .withPosition(Vec3.atBottomCenterOf(enemyPos))
                .withName("Distant Followed Player Defense Probe")
                .spawn(MobSpawnType.STRUCTURE);
        Player followedPlayer = helper.makeMockPlayer(GameType.SURVIVAL);
        Zombie enemy = EntityType.ZOMBIE.create(helper.getLevel());
        if (enemy == null) {
            throw new IllegalStateException("failed to create followed player attacker");
        }
        try {
            guard.setNoAi(true);
            guard.refreshBrain(helper.getLevel());
            guard.absMoveTo(Vec3.atBottomCenterOf(guardPos).x, guardPos.getY(),
                    Vec3.atBottomCenterOf(guardPos).z);
            followedPlayer.absMoveTo(enemyPos.getX() - 3.5D, enemyPos.getY(), enemyPos.getZ() + 0.5D);
            enemy.absMoveTo(enemyPos.getX() + 0.5D, enemyPos.getY(), enemyPos.getZ() + 0.5D);
            enemy.setNoAi(true);
            enemy.setTarget(followedPlayer);
            helper.getLevel().addFreshEntity(enemy);
            guard.getBrain().setMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING, followedPlayer);

            helper.assertTrue(guard.distanceToSqr(enemy) > 48.0D * 48.0D,
                    "fixture enemy must be outside guard's own 48-block scan");
            helper.assertTrue(followedPlayer.distanceToSqr(enemy) < 48.0D * 48.0D,
                    "fixture enemy must be in followed player's defense range");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(
                            LivingEntity.class,
                            followedPlayer.getBoundingBox().inflate(48.0D),
                            candidate -> candidate == enemy).contains(enemy),
                    "fixture enemy must be queryable in the test's loaded area");
            helper.assertTrue(guard.getSensing().hasLineOfSight(enemy),
                    "fixture guard must have line of sight to distant enemy");
            helper.assertTrue(guard.getBrain().getMemory(MemoryModuleTypeMCA.PLAYER_FOLLOWING)
                            .orElse(null) == followedPlayer && enemy.getTarget() == followedPlayer,
                    "fixture must preserve the followed-player memory and attacker target");

            new GuardEnemiesSensor().doTick(helper.getLevel(), guard);
            helper.assertTrue(guard.getBrain().getMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY)
                            .orElse(null) == enemy,
                    "sensor failed to defend the distant followed player despite clear line of sight");
            helper.succeed();
        } finally {
            enemy.discard();
            guard.discard();
            followedPlayer.discard();
        }
    }

    @GameTest(batch = "mca_guard_enemy_range", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void guardDetectsVisibleEnemyBeyondVanillaSixteenBlockScan(GameTestHelper helper) {
        cleanupTestEntities();
        try {
            BlockPos guardPos = helper.absolutePos(new BlockPos(3, 2, 3));
            BlockPos targetPos = guardPos.east(24);
            prepareClearLane(helper, guardPos, targetPos);

            VillagerEntityMCA guard = VillagerFactory.newVillager(helper.getLevel())
                    .withAge(0)
                    .withProfession(ProfessionsMCA.GUARD)
                    .withPosition(Vec3.atBottomCenterOf(guardPos))
                    .withName("Guard Enemy Sensor Probe")
                    .spawn(MobSpawnType.STRUCTURE);
            guard.refreshBrain(helper.getLevel());
            TEST_ENTITIES.add(guard);

            LivingEntity nearby = EntityType.COW.create(helper.getLevel());
            if (nearby == null) {
                throw new IllegalStateException("failed to create nearby entity");
            }
            BlockPos nearbyPos = guardPos.east(8);
            nearby.absMoveTo(nearbyPos.getX() + 0.5D, nearbyPos.getY(), nearbyPos.getZ() + 0.5D);
            helper.getLevel().addFreshEntity(nearby);
            TEST_ENTITIES.add(nearby);

            Zombie target = EntityType.ZOMBIE.create(helper.getLevel());
            if (target == null) {
                throw new IllegalStateException("failed to create zombie target");
            }
            target.absMoveTo(targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D);
            target.setNoAi(true);
            target.setTarget(guard);
            helper.getLevel().addFreshEntity(target);
            TEST_ENTITIES.add(target);

            guard.getBrain().eraseMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES);
            guard.getBrain().eraseMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
            new GuardEnemiesSensor().doTick(helper.getLevel(), guard);
            Entity detected = guard.getBrain().getMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY).orElse(null);
            List<LivingEntity> nearbyEntities = guard.getBrain()
                    .getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES)
                    .orElse(List.of());
            boolean targetInScan = helper.getLevel().getEntitiesOfClass(
                    LivingEntity.class,
                    guard.getBoundingBox().inflate(48.0D),
                    candidate -> candidate == target
            ).contains(target);
            boolean lineOfSight = guard.getSensing().hasLineOfSight(target);
            helper.assertTrue(
                    detected == target,
                    "guard sensor did not detect a visible enemy 24 blocks away; detected=" + detected
                            + ", targetInScan=" + targetInScan
                            + ", lineOfSight=" + lineOfSight
                            + ", distanceSqr=" + guard.distanceToSqr(target)
                            + ", guardPos=" + guard.position()
                            + ", targetPos=" + target.position()
                            + ", targetMobTargetIsGuard=" + (target.getTarget() == guard)
            );
            helper.assertTrue(
                    nearbyEntities.contains(nearby),
                    "guard sensor did not preserve vanilla nearby-entity memory inside 16 blocks"
            );
            helper.assertTrue(
                    !nearbyEntities.contains(target),
                    "guard sensor leaked a 24-block entity into vanilla 16-block nearby memory"
            );
            helper.succeed();
        } finally {
            cleanupTestEntities();
        }
    }

    @GameTest(batch = "mca_guard_priority_config", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void guardPriorityConfigChangesAreObservedWithoutRestart(GameTestHelper helper) {
        cleanupTestEntities();
        Map<String, Integer> previousTargets = Config.getInstance().guardsTargetEntities;
        boolean previousTargetMonsters = Config.getInstance().guardsTargetMonsters;
        try {
            BlockPos guardPos = helper.absolutePos(new BlockPos(3, 2, 3));
            BlockPos targetPos = guardPos.east(8);
            prepareClearLane(helper, guardPos, targetPos);

            VillagerEntityMCA guard = VillagerFactory.newVillager(helper.getLevel())
                    .withAge(0)
                    .withProfession(ProfessionsMCA.GUARD)
                    .withPosition(Vec3.atBottomCenterOf(guardPos))
                    .withName("Guard Config Refresh Probe")
                    .spawn(MobSpawnType.STRUCTURE);
            guard.refreshBrain(helper.getLevel());
            TEST_ENTITIES.add(guard);

            Zombie target = EntityType.ZOMBIE.create(helper.getLevel());
            if (target == null) {
                throw new IllegalStateException("failed to create zombie target");
            }
            target.absMoveTo(targetPos.getX() + 0.5D, targetPos.getY(), targetPos.getZ() + 0.5D);
            target.setNoAi(true);
            helper.getLevel().addFreshEntity(target);
            TEST_ENTITIES.add(target);

            Config.getInstance().guardsTargetMonsters = false;
            Config.getInstance().guardsTargetEntities = Map.of("minecraft:zombie", 4);
            GuardEnemiesSensor sensor = new GuardEnemiesSensor();
            sensor.doTick(helper.getLevel(), guard);
            helper.assertTrue(
                    guard.getBrain().getMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY).orElse(null) == target,
                    "configured zombie priority was not used before the config change"
            );

            Config.getInstance().guardsTargetEntities = Map.of("minecraft:zombie", -1);
            sensor.doTick(helper.getLevel(), guard);
            helper.assertTrue(
                    guard.getBrain().getMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY).isEmpty(),
                    "guard sensor retained stale configured priority after guardsTargetEntities changed"
            );
            helper.succeed();
        } finally {
            Config.getInstance().guardsTargetEntities = previousTargets;
            Config.getInstance().guardsTargetMonsters = previousTargetMonsters;
            cleanupTestEntities();
        }
    }

    private static void prepareClearLane(GameTestHelper helper, BlockPos from, BlockPos to) {
        for (int x = from.getX(); x <= to.getX(); x++) {
            BlockPos feet = new BlockPos(x, from.getY(), from.getZ());
            helper.getLevel().getChunk(feet);
            helper.getLevel().setBlock(feet.below(), Blocks.STONE.defaultBlockState(), 3);
            helper.getLevel().setBlock(feet, Blocks.AIR.defaultBlockState(), 3);
            helper.getLevel().setBlock(feet.above(), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static void cleanupTestEntities() {
        TEST_ENTITIES.forEach(Entity::discard);
        TEST_ENTITIES.clear();
    }
}
