package net.conczin.mca.entity.ai.brain.sensor;

import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedMeleeAttackTask;
import net.conczin.mca.entity.ai.brain.tasks.BowTask;
import net.conczin.mca.entity.ai.brain.tasks.ExtendedCrossbowAttackTask;
import net.conczin.mca.entity.ai.brain.tasks.WanderOrTeleportToTargetTask;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.datafixers.util.Pair;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.conczin.mca.gametest.SensorTestFixtures;
import net.conczin.mca.registry.ProfessionsMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.sensing.VillagerHostilesSensor;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.ai.memory.NearestVisibleLivingEntities;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

@PrefixGameTestTemplate(false)
public final class CreeperSafetyGameTests {
    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void standaloneCreeperSensorNeverOverwritesVanillaHostile(GameTestHelper helper) {
        var villager = villager(helper);
        var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(58, 2, 56));
        var creeper = helper.spawn(EntityType.CREEPER, new BlockPos(60, 2, 56));
        try {
            creeper.ignite();
            villager.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                    new NearestVisibleLivingEntities(villager, List.of(zombie, creeper)));
            var hostileSensor = new VillagerHostilesSensor();
            var creeperSensor = new ExplodingCreeperSensor();
            for (int i = 0; i < 20; i++) hostileSensor.tick(helper.getLevel(), villager);
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE).orElse(null) == zombie,
                    "vanilla hostile sensor control must retain the zombie");
            villager.getBrain().eraseMemory(creeperMemory(helper));
            for (int i = 0; i < 20; i++) creeperSensor.tick(helper.getLevel(), villager);
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE).orElse(null) == zombie,
                    "standalone creeper sensor must never overwrite vanilla hostile memory");
            helper.assertTrue(villager.getBrain().getMemory(creeperMemory(helper)).orElse(null) == creeper,
                    "standalone creeper sensor must publish its independent warning");
            villager.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                    new NearestVisibleLivingEntities(villager, List.of(zombie)));
            for (int i = 0; i < 20; i++) creeperSensor.tick(helper.getLevel(), villager);
            helper.assertTrue(villager.getBrain().getMemory(creeperMemory(helper)).isEmpty(),
                    "creeper sensor must clear its own warning when no eligible creeper remains");
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE).orElse(null) == zombie,
                    "clearing creeper warning must not erase another sensor's zombie memory");
            helper.succeed();
        } finally { SensorTestFixtures.restore(helper); villager.discard(); zombie.discard(); creeper.discard(); }
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void failedEscapeSamplingDropsApproachButPreservesRetreat(GameTestHelper helper) throws ReflectiveOperationException {
        var villager = villager(helper);
        var creeper = helper.spawn(EntityType.CREEPER, new BlockPos(59, 2, 56));
        try {
            villager.getBrain().removeAllBehaviors();
            var approach = new WalkTarget(creeper.position(), 0.5F, 0);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, approach);
            helper.assertTrue(villager.getNavigation().moveTo(creeper.getX(), creeper.getY(), creeper.getZ(), 0.5),
                    "fallback fixture must have a running approach path");
            // A zero-radius real mob restriction excludes every sampled escape destination.
            villager.restrictTo(villager.blockPosition(), 0);
            helper.assertTrue(LandRandomPos.getPosAway(villager, 16, 7, creeper.position()) == null,
                    "fixture must prevent vanilla escape sampling");
            creeper.ignite();
            scanThreats(helper, villager);
            var step = VillagerEntityMCA.class.getDeclaredMethod("customServerAiStep");
            step.setAccessible(true);
            step.invoke(villager);
            helper.assertTrue(!villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                    "failed escape sampling must retract movement toward creeper");
            helper.assertTrue(villager.getNavigation().isDone(), "failed escape sampling must stop old approach navigation");
            var retreat = new WalkTarget(villager.position().add(-4, 0, 0), 0.75F, 0);
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, retreat);
            step.invoke(villager);
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == retreat,
                    "failed sampling must preserve an existing away-directed escape");
            helper.succeed();
        } finally { SensorTestFixtures.restore(helper); villager.discard(); creeper.discard(); }
    }

    private static final List<Entity> ESCAPE_ACTORS = new ArrayList<>();

    @AfterBatch(batch = "mca_creeper_escape")
    public static void cleanupEscape(net.minecraft.server.level.ServerLevel level) {
        ESCAPE_ACTORS.forEach(Entity::discard);
        ESCAPE_ACTORS.clear();
        SensorTestFixtures.restoreAll();
    }

    @AfterBatch(batch = "mca_creeper_running_bow")
    public static void cleanupRunningBow(net.minecraft.server.level.ServerLevel level) {
        cleanupEscape(level);
    }

    @AfterBatch(batch = "mca_creeper_running_crossbow")
    public static void cleanupRunningCrossbow(net.minecraft.server.level.ServerLevel level) {
        cleanupEscape(level);
    }

    @GameTest(batch = "mca_creeper_running_bow", templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 160)
    public static void ignitionInterruptsRunningBowDrawAndEscapes(GameTestHelper helper) {
        runningRangedAttack(helper, false);
    }

    @GameTest(batch = "mca_creeper_running_crossbow", templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 160)
    public static void ignitionInterruptsRunningCrossbowChargeAndEscapes(GameTestHelper helper) {
        runningRangedAttack(helper, true);
    }

    @SuppressWarnings("unchecked")
    private static void runningRangedAttack(GameTestHelper helper, boolean crossbow) {
        var pos = helper.absolutePos(new BlockPos(56, 2, 56));
        SensorTestFixtures.prepare(helper, pos, 20);
        var archer = VillagerFactory.newVillager(helper.getLevel()).withAge(0).withProfession(ProfessionsMCA.ARCHER)
                .withPosition(Vec3.atBottomCenterOf(pos)).spawn(MobSpawnType.STRUCTURE);
        var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(62, 2, 56));
        var creeper = helper.spawn(EntityType.CREEPER, new BlockPos(60, 2, 56));
        zombie.setNoAi(true);
        creeper.setNoAi(true);
        ESCAPE_ACTORS.addAll(List.of(archer, zombie, creeper));
        var nbt = new CompoundTag();
        creeper.addAdditionalSaveData(nbt);
        nbt.putShort("Fuse", (short) 30000);
        creeper.readAdditionalSaveData(nbt);
        archer.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(crossbow ? Items.CROSSBOW : Items.BOW));
        archer.getInventory().addItem(new ItemStack(Items.ARROW, 64));
        var brain = (net.minecraft.world.entity.ai.Brain<VillagerEntityMCA>) (net.minecraft.world.entity.ai.Brain<?>) archer.getBrain();
        brain.removeAllBehaviors();
        BehaviorControl<? super VillagerEntityMCA> attack = crossbow
                ? new ExtendedCrossbowAttackTask<VillagerEntityMCA, VillagerEntityMCA>() : new BowTask<VillagerEntityMCA>(20, 15);
        // Keep real Brain ticks and weapon lifecycle, isolating equipment replacement and ordinary work.
        brain.addActivity(Activity.CORE, ImmutableList.of(Pair.of(0, attack), Pair.of(1, new WanderOrTeleportToTargetTask())));
        brain.setCoreActivities(ImmutableSet.of(Activity.CORE));
        brain.setActiveActivityIfPossible(Activity.CORE);
        brain.setMemory(MemoryModuleType.ATTACK_TARGET, zombie);
        scanThreats(helper, archer);
        long[] ignitionTime = {-1};
        double[] initialDistance = {0};
        helper.onEachTick(() -> {
            if (ignitionTime[0] < 0) {
                if (archer.isUsingItem() && archer.getTicksUsingItem() >= 4) {
                    helper.assertTrue(attack.getStatus() == net.minecraft.world.entity.ai.behavior.Behavior.Status.RUNNING,
                            "weapon draw must belong to an already-running combat behavior");
                    creeper.ignite();
                    scanThreats(helper, archer);
                    ignitionTime[0] = helper.getLevel().getGameTime();
                    initialDistance[0] = archer.distanceTo(creeper);
                }
                return;
            }
            if (helper.getLevel().getGameTime() <= ignitionTime[0]) return;
            helper.assertTrue(!archer.isUsingItem(), "ignition must cancel an already-running weapon draw");
            helper.assertTrue(!brain.hasMemoryValue(MemoryModuleType.ATTACK_TARGET), "ignition must clear combat target");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(Projectile.class, helper.getBounds(),
                    projectile -> projectile.getOwner() == archer).isEmpty(), "cancelled ranged attack must not release projectile");
            if (archer.distanceTo(creeper) > initialDistance[0] + 2) helper.succeed();
        });
    }

    @GameTest(batch = "mca_creeper_escape", templateNamespace = "mca", template = "gametest/isolated_ai_arena", timeoutTicks = 120)
    public static void awakeGuardEscapesIgnitingCreeperDuringZombieFight(GameTestHelper helper) {
        var pos = helper.absolutePos(new BlockPos(56, 2, 56));
        SensorTestFixtures.prepare(helper, pos, 20);
        var guard = VillagerFactory.newVillager(helper.getLevel()).withAge(0).withProfession(ProfessionsMCA.GUARD)
                .withPosition(Vec3.atBottomCenterOf(pos)).spawn(MobSpawnType.STRUCTURE);
        var creeper = helper.spawn(EntityType.CREEPER, new BlockPos(59, 2, 56));
        var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(68, 2, 56));
        ESCAPE_ACTORS.addAll(List.of(guard, creeper, zombie));
        creeper.setNoAi(true);
        zombie.setNoAi(true);
        var nbt = new CompoundTag();
        creeper.addAdditionalSaveData(nbt);
        nbt.putShort("Fuse", (short) 30000);
        creeper.readAdditionalSaveData(nbt);
        creeper.ignite();
        guard.refreshBrain(helper.getLevel());
        guard.getBrain().setSchedule(Schedule.EMPTY);
        guard.getBrain().setActiveActivityIfPossible(Activity.IDLE);
        guard.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, zombie);
        helper.assertTrue(guard.canAttack(zombie), "guard must be engaged before escape behavior runs");
        scanThreats(helper, guard);
        double initialDistance = guard.distanceTo(creeper);
        helper.runAfterDelay(1, () -> {
            helper.assertTrue(guard.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).isEmpty(),
                    "igniting creeper must interrupt existing zombie combat");
            var walk = guard.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
            helper.assertTrue(walk != null && walk.getTarget().currentPosition().subtract(guard.position())
                            .dot(creeper.position().subtract(guard.position())) < 0,
                    "escape destination must point away from creeper on the next AI tick");
            helper.succeedWhen(() -> helper.assertTrue(guard.distanceTo(creeper) > initialDistance + 2,
                    "awake guard must actually move away from igniting creeper"));
        });
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void creeperWarningClearsForOcclusionAndRange(GameTestHelper helper) {
        var villager = villager(helper);
        var creeper = helper.spawn(EntityType.CREEPER, new BlockPos(60, 2, 56));
        try {
            var memory = creeperMemory(helper);
            scanThreats(helper, villager);
            helper.assertTrue(villager.getBrain().getMemory(memory).isEmpty(), "unignited creeper is not an explosion warning");
            creeper.ignite();
            scanThreats(helper, villager);
            helper.assertTrue(villager.getBrain().getMemory(memory).orElse(null) == creeper, "visible ignited control missing");
            for (int y = 0; y < 3; y++) helper.getLevel().setBlock(villager.blockPosition().east(2).above(y), Blocks.STONE.defaultBlockState(), 3);
            villager.getSensing().tick();
            scanThreats(helper, villager);
            helper.assertTrue(villager.getBrain().getMemory(memory).isEmpty(), "occluded creeper warning must clear");
            creeper.setPos(villager.position().add(24, 0, 0));
            scanThreats(helper, villager);
            helper.assertTrue(villager.getBrain().getMemory(memory).isEmpty(), "distant creeper must not become local danger");
            helper.succeed();
        } finally { SensorTestFixtures.restore(helper); villager.discard(); creeper.discard(); }
    }
    @SuppressWarnings("unchecked")
    private static MemoryModuleType<LivingEntity> creeperMemory(GameTestHelper helper) {
        var memory = MemoryModuleTypeMCA.MEMORY_MODULES.get(MCA.locate("nearest_ignited_creeper"));
        helper.assertTrue(memory != null, "igniting creepers need an independent danger memory");
        return (MemoryModuleType<LivingEntity>) memory;
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void mixedThreatsRetainZombieAndCreeperIndependently(GameTestHelper helper) {
        var villager = villager(helper);
        var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(58, 2, 56));
        var creeper = helper.spawn(EntityType.CREEPER, new BlockPos(60, 2, 56));
        zombie.setNoAi(true);
        creeper.setNoAi(true);
        try {
            creeper.ignite();
            var memory = creeperMemory(helper);
            scanThreats(helper, villager);
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE).orElse(null) == zombie,
                    "creeper warning must not replace the vanilla zombie danger");
            helper.assertTrue(villager.getBrain().getMemory(memory).orElse(null) == creeper,
                    "zombie must not overwrite the igniting-creeper warning");
            creeper.discard();
            scanThreats(helper, villager);
            helper.assertTrue(villager.getBrain().getMemory(memory).isEmpty(), "dead creeper warning must clear");
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE).orElse(null) == zombie,
                    "clearing creeper warning must retain zombie");
            zombie.discard();
            scanThreats(helper, villager);
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.NEAREST_HOSTILE).isEmpty(),
                    "vanilla hostile memory must clear after loss");
            helper.succeed();
        } finally { SensorTestFixtures.restore(helper);
            villager.discard(); zombie.discard(); creeper.discard();
        }
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void guardRejectsPassiveCreeperDespiteConfig(GameTestHelper helper) {
        var villager = villager(helper);
        var creeper = helper.spawn(EntityType.CREEPER, new BlockPos(58, 2, 56));
        var config = Config.getInstance();
        var oldTargets = config.guardsTargetEntities;
        try {
            villager.setProfession(ProfessionsMCA.GUARD);
            config.guardsTargetEntities = Map.of("minecraft:creeper", 100);
            scanThreats(helper, villager);
            helper.assertTrue(!GuardEnemiesSensor.isGuardEnemy(creeper, villager),
                    "config must not make a passive creeper a guard enemy");
            helper.assertTrue(villager.getBrain().getMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY).isEmpty(),
                    "guard must not select creeper");
            helper.succeed();
        } finally { SensorTestFixtures.restore(helper);
            config.guardsTargetEntities = oldTargets;
            villager.discard(); creeper.discard();
        }
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void creeperTargetingVillagerCanBeAttackedUntilItStops(GameTestHelper helper) {
        var attacker = villager(helper);
        var resident = helper.spawn(EntityType.VILLAGER, new BlockPos(59, 2, 56));
        var creeper = helper.spawn(EntityType.CREEPER, new BlockPos(58, 2, 56));
        try {
            attacker.setProfession(ProfessionsMCA.GUARD);
            creeper.setTarget(resident);
            helper.assertTrue(GuardEnemiesSensor.isGuardEnemy(creeper, attacker),
                    "creeper targeting another villager must be eligible for defense despite default -1 config");
            scanThreats(helper, attacker);
            helper.assertTrue(attacker.getBrain().getMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY).orElse(null) == creeper,
                    "guard must detect creeper attacking a villager");
            helper.assertTrue(attacker.canAttack(creeper) && attacker.doHurtTarget(creeper),
                    "melee must allow creeper actively targeting a villager");
            attacker.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
            helper.assertTrue(attacker.tryPerformBowAttack(creeper, 1), "bow must allow villager defense against creeper");
            creeper.setTarget(attacker);
            helper.assertTrue(attacker.canAttack(creeper) && GuardEnemiesSensor.isGuardEnemy(creeper, attacker),
                    "exception must also allow creepers targeting MCA villagers themselves");
            creeper.setTarget(null);
            helper.assertTrue(!attacker.canAttack(creeper) && !attacker.doHurtTarget(creeper),
                    "attack permission must end immediately when creeper stops targeting villager");
            helper.assertTrue(!attacker.tryPerformBowAttack(creeper, 1), "stale bow target must be rejected after targeting stops");
            scanThreats(helper, attacker);
            helper.assertTrue(attacker.getBrain().getMemory(MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY).isEmpty(),
                    "guard memory must clear after villager defense exception ends");
            helper.succeed();
        } finally { SensorTestFixtures.restore(helper);
            helper.getLevel().getEntitiesOfClass(Projectile.class, attacker.getBoundingBox().inflate(16),
                    projectile -> projectile.getOwner() == attacker).forEach(Entity::discard);
            attacker.discard(); resident.discard(); creeper.discard();
        }
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void selfDefenceNeverSwingsAtCreeper(GameTestHelper helper) {
        var villager = villager(helper);
        var creeper = helper.spawn(EntityType.CREEPER, new BlockPos(57, 2, 56));
        try {
            villager.getBrain().setMemory(MemoryModuleType.NEAREST_HOSTILE, creeper);
            villager.getBrain().setMemory(MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
                    new NearestVisibleLivingEntities(villager, List.of(creeper)));
            helper.assertTrue(!new ExtendedMeleeAttackTask(15, 2.5F, MemoryModuleType.NEAREST_HOSTILE)
                            .tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime()),
                    "self-defence must reject creeper before swinging or setting cooldown");
            helper.assertTrue(!villager.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_COOLING_DOWN),
                    "rejected creeper attack must not consume cooldown");
            helper.succeed();
        } finally { SensorTestFixtures.restore(helper); villager.discard(); creeper.discard(); }
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void directMeleeRejectsCreeperButStillHitsZombie(GameTestHelper helper) {
        var villager = villager(helper);
        var creeper = helper.spawn(EntityType.CREEPER, new BlockPos(57, 2, 56));
        var zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(58, 2, 56));
        try {
            float health = creeper.getHealth();
            helper.assertTrue(!villager.doHurtTarget(creeper) && creeper.getHealth() == health,
                    "direct melee must reject creeper without damage");
            helper.assertTrue(!villager.canAttack(creeper), "creeper must not be attack eligible");
            helper.assertTrue(villager.canAttack(zombie) && villager.doHurtTarget(zombie),
                    "ordinary hostile attacks must still work");
            helper.succeed();
        } finally { SensorTestFixtures.restore(helper); villager.discard(); creeper.discard(); zombie.discard(); }
    }

    @GameTest(templateNamespace = "mca", template = "gametest/isolated_ai_arena")
    public static void directBowAndLoadedCrossbowRejectCreeper(GameTestHelper helper) {
        var villager = villager(helper);
        var creeper = helper.spawn(EntityType.CREEPER, new BlockPos(60, 2, 56));
        try {
            villager.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
            helper.assertTrue(!villager.tryPerformBowAttack(creeper, 1.0F), "direct bow must reject creeper");
            var crossbow = new ItemStack(Items.CROSSBOW);
            crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(new ItemStack(Items.ARROW)));
            villager.setItemInHand(InteractionHand.MAIN_HAND, crossbow);
            villager.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, creeper);
            villager.performRangedAttack(creeper, 1.0F);
            helper.assertTrue(!crossbow.getOrDefault(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY).isEmpty(),
                    "crossbow must retain ammunition when target is creeper");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(Projectile.class, villager.getBoundingBox().inflate(16),
                    projectile -> projectile.getOwner() == villager).isEmpty(), "no projectile may be fired at creeper");
            helper.succeed();
        } finally { SensorTestFixtures.restore(helper); villager.discard(); creeper.discard(); }
    }

    private static void scanThreats(GameTestHelper helper, VillagerEntityMCA villager) {
        new GuardEnemiesSensor().doTick(helper.getLevel(), villager);
        var hostile = new VillagerHostilesSensor();
        var creeper = new ExplodingCreeperSensor();
        for (int i = 0; i < 20; i++) {
            hostile.tick(helper.getLevel(), villager);
            creeper.tick(helper.getLevel(), villager);
        }
    }

    private static VillagerEntityMCA villager(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(56, 2, 56));
        SensorTestFixtures.prepare(helper, pos.east(2), 8);
        var villager = VillagerFactory.newVillager(helper.getLevel()).withAge(0)
                .withPosition(Vec3.atBottomCenterOf(pos)).spawn(MobSpawnType.STRUCTURE);
        villager.setNoAi(true);
        villager.setOnGround(true);
        return villager;
    }
}
