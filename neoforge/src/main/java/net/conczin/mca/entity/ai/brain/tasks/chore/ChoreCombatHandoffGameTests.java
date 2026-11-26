package net.conczin.mca.entity.ai.brain.tasks.chore;

import net.conczin.mca.gametest.GameTestBrain;
import net.conczin.mca.gametest.SensorTestFixtures;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.authlib.GameProfile;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.MCAFishingBobberEntity;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.ActivitiesMCA;
import net.conczin.mca.entity.ai.Memories;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.brain.VillagerBrain;
import net.conczin.mca.entity.ai.navigation.PersistentPathTarget;
import net.conczin.mca.entity.ai.brain.tasks.WanderOrTeleportToTargetTask;
import net.conczin.mca.util.InventoryUtils;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.BlockPos;
import net.conczin.mca.util.network.datasync.CDataParameter;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.conczin.mca.neoforge.gametest.AfterBatch;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class ChoreCombatHandoffGameTests {
    private static final String REAL_SLEEP_BATCH = "mca_chore_rest_real_sleep";
    private static final String CREEPER_RETREAT_BATCH = "mca_chore_creeper_retreat";
    private static final List<ChunkPos> REAL_SLEEP_CHUNKS = new ArrayList<>();
    private static final List<ChunkPos> CREEPER_RETREAT_CHUNKS = new ArrayList<>();
    private static BlockPos realSleepBedFoot;
    private static VillagerEntityMCA realSleepVillager;
    private static ServerPlayer realSleepAssigner;
    private static BlockPos creeperRetreatTree;
    private static VillagerEntityMCA creeperRetreatVillager;
    private static Creeper creeperRetreatCreeper;
    private static ServerPlayer creeperRetreatAssigner;

    private ChoreCombatHandoffGameTests() {
    }

    @GameTest(batch = "mca_chore_combat_chop", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void choppingStopsWhenAttackTargetAppears(GameTestHelper helper) {
        checkThreatInterruption(helper, Chore.CHOP);
    }

    @GameTest(batch = "mca_chore_combat_harvest", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void harvestingStopsWhenAttackTargetAppears(GameTestHelper helper) {
        checkThreatInterruption(helper, Chore.HARVEST);
    }

    @GameTest(batch = "mca_chore_combat_hunt", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void huntingStopsWhenAttackTargetAppears(GameTestHelper helper) {
        checkThreatInterruption(helper, Chore.HUNT);
    }

    @GameTest(batch = "mca_chore_combat_fish", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void fishingStopsWhenAttackTargetAppears(GameTestHelper helper) {
        checkThreatInterruption(helper, Chore.FISH);
    }

    @GameTest(batch = "mca_chore_combat_cooldown", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void failedCooldownCannotPublishCombatWalkTarget(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnChoreVillager(helper, Chore.CHOP);
        try {
            ChoppingTask task = new ChoppingTask();
            long time = helper.getLevel().getGameTime();
            helper.assertTrue(task.tryStart(helper.getLevel(), villager, time), "chore did not start without danger");
            task.failedTicks = 30;
            task.walkingTicks = AbstractChoreTask.WALKING_THRESHOLD + 1;
            Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
            helper.assertTrue(zombie != null, "could not prepare combat target");
            villager.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, zombie);

            task.tickOrStop(helper.getLevel(), villager, time + 1);

            helper.assertTrue(task.getStatus() == Behavior.Status.STOPPED, "chore continued during combat cooldown");
            helper.assertTrue(!villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                    "cooldown published a walk target while fighting");
            helper.assertTrue(task.walkingTicks == AbstractChoreTask.WALKING_THRESHOLD + 1,
                    "combat consumed wandering cooldown state");
            helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == Chore.CHOP,
                    "combat abandoned the assigned chore");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_chore_combat_panic_activity", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void assignedChoreDoesNotOverridePanicActivity(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnChoreVillager(helper, Chore.CHOP);
        try {
            villager.getBrain().setActiveActivityIfPossible(Activity.PANIC);
            helper.assertTrue(villager.getBrain().isActive(Activity.PANIC),
                    "fixture could not enter native panic activity");

            // Called on each server AI tick: this must not force CHORE back over PANIC.
            villager.getVillagerBrain().think();
            helper.assertTrue(villager.getBrain().isActive(Activity.PANIC),
                    "assigned chore displaced native panic activity");
            helper.assertTrue(villager.getVillagerBrain().isPanicking(),
                    "panic status was not exposed to chore eligibility");
            helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == Chore.CHOP,
                    "panic abandoned the assigned chore");

            villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);
            villager.getVillagerBrain().think();
            helper.assertTrue(villager.getBrain().getActiveNonCoreActivity().orElse(null)
                            == net.conczin.mca.entity.ai.ActivitiesMCA.CHORE,
                    "assigned chore did not resume after panic ended");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_chore_scheduled_rest", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void assignedChoreYieldsToScheduledRestThenResumes(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnChoreVillager(helper, Chore.CHOP);
        try {
            var brain = villager.getBrain();
            helper.assertTrue(brain.isActive(ActivitiesMCA.CHORE), "fixture did not begin with the assigned chore");
            BlockPos choreDestination = villager.blockPosition().east(6);
            brain.setMemory(MemoryModuleType.WALK_TARGET,
                    new WalkTarget(new PersistentPathTarget(choreDestination, ActivitiesMCA.CHORE), 0.5F, 1));

            brain.setSchedule(GameTestBrain.fixedSchedule(Activity.REST));
            brain.updateActivityFromSchedule(helper.getLevel().environmentAttributes(), helper.getLevel().getGameTime() + 100, villager.position());
            helper.assertTrue(brain.isActive(Activity.REST), "fixture did not transition into scheduled REST");

            villager.getVillagerBrain().think();
            helper.assertTrue(brain.isActive(Activity.REST), "assigned chore displaced scheduled REST");
            // The existing CORE movement consumer retires stale activity-owned journeys.
            new WanderOrTeleportToTargetTask().tryStart(helper.getLevel(), villager,
                    helper.getLevel().getGameTime());
            helper.assertTrue(!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET),
                    "pending chore-owned movement survived the transition into REST");
            helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == Chore.CHOP,
                    "rest abandoned the assigned chore instead of pausing it");

            brain.setSchedule(GameTestBrain.fixedSchedule(Activity.IDLE));
            brain.setActiveActivityIfPossible(Activity.IDLE);
            villager.getVillagerBrain().think();
            helper.assertTrue(brain.isActive(ActivitiesMCA.CHORE), "assigned chore did not resume when REST ended");
            helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == Chore.CHOP,
                    "resume changed the assigned chore");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_chore_scheduled_rest", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void staleIdleWalkTargetDoesNotCarryIntoAssignedChore(GameTestHelper helper) throws ReflectiveOperationException {
        VillagerEntityMCA villager = spawnChoreVillager(helper, Chore.CHOP);
        try {
            var brain = villager.getMCABrain();
            brain.removeAllBehaviors();
            GameTestBrain.addActivity(brain, Activity.IDLE, 0, ImmutableList.of(
                    BehaviorBuilder.create(ctx -> ctx.group(ctx.absent(MemoryModuleType.WALK_TARGET))
                            .apply(ctx, walk -> (world, entity, time) -> {
                                walk.set(new WalkTarget(entity.blockPosition().east(10), 0.5F, 0));
                                return true;
                            }))));
            GameTestBrain.addActivity(brain, ActivitiesMCA.CHORE, 0, ImmutableList.of(new DoNothing(20, 40)));
            brain.setCoreActivities(ImmutableSet.of());
            brain.setSchedule(GameTestBrain.fixedSchedule(Activity.IDLE));
            brain.setActiveActivityIfPossible(Activity.IDLE);
            helper.assertTrue(brain.isActive(Activity.IDLE), "fixture could not enter scheduled IDLE");

            Method step = VillagerEntityMCA.class.getDeclaredMethod("customServerAiStep", ServerLevel.class);
            step.setAccessible(true);
            step.invoke(villager, helper.getLevel());

            helper.assertTrue(brain.isActive(ActivitiesMCA.CHORE), "assigned chore did not resume before Brain.tick()");
            helper.assertTrue(!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET),
                    "IDLE walk producer ran before pre-tick chore selection");

            WalkTarget choreWalk = new WalkTarget(
                    new PersistentPathTarget(villager.blockPosition().west(5), ActivitiesMCA.CHORE), 0.5F, 1);
            brain.setActiveActivityIfPossible(Activity.IDLE);
            brain.setMemory(MemoryModuleType.WALK_TARGET, choreWalk);
            step.invoke(villager, helper.getLevel());
            helper.assertTrue(brain.isActive(ActivitiesMCA.CHORE), "assigned chore did not resume its own journey");
            helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null) == choreWalk,
                    "assigned chore lost its own persistent walk target");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @AfterBatch(batch = CREEPER_RETREAT_BATCH)
    public static void cleanupCreeperRetreatFixture(ServerLevel level) {
        if (creeperRetreatAssigner != null) {
            level.players().remove(creeperRetreatAssigner);
            creeperRetreatAssigner = null;
        }
        if (creeperRetreatCreeper != null) {
            creeperRetreatCreeper.discard();
            creeperRetreatCreeper = null;
        }
        if (creeperRetreatVillager != null) {
            creeperRetreatVillager.discard();
            creeperRetreatVillager = null;
        }
        if (creeperRetreatTree != null) {
            level.removeBlock(creeperRetreatTree.above(), false);
            level.removeBlock(creeperRetreatTree, false);
            creeperRetreatTree = null;
        }
        CREEPER_RETREAT_CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x(), chunk.z(), false));
        CREEPER_RETREAT_CHUNKS.clear();
    }

    @GameTest(batch = CREEPER_RETREAT_BATCH, templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 180)
    public static void activeChoreYieldsToIgnitedCreeperRetreatThenResumes(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(56, 1, 56));
        BlockPos workTree = start.east(6);
        prepareFlatArea(helper, start, 16, 3, CREEPER_RETREAT_CHUNKS);
        helper.getLevel().setBlock(workTree.below(), Blocks.DIRT.defaultBlockState(), 3);
        helper.getLevel().setBlock(workTree, Blocks.OAK_LOG.defaultBlockState(), 3);
        helper.getLevel().setBlock(workTree.above(), Blocks.OAK_LEAVES.defaultBlockState(), 3);
        creeperRetreatTree = workTree;

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start))
                .spawn(EntitySpawnReason.STRUCTURE);
        creeperRetreatVillager = villager;
        villager.refreshBrain(helper.getLevel());
        villager.getInventory().setItem(0, new ItemStack(Items.IRON_AXE));
        ServerPlayer assigner = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "creeper-chore-assigner"), ClientInformation.createDefault());
        creeperRetreatAssigner = assigner;
        helper.getLevel().players().add(assigner);
        villager.getVillagerBrain().assignJob(Chore.CHOP, assigner);
        var brain = villager.getBrain();
        brain.setSchedule(null);
        brain.setActiveActivityIfPossible(ActivitiesMCA.CHORE);

        Creeper creeper = EntityType.CREEPER.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
        helper.assertTrue(creeper != null, "could not spawn creeper threat");
        creeperRetreatCreeper = creeper;
        creeper.snapTo(start.getX() + 4.5D, start.getY(), start.getZ() + 0.5D, 0.0F, 0.0F);
        creeper.setNoAi(true);
        creeper.setPersistenceRequired();
        SensorTestFixtures.setFuse(creeper, (short) 30000);
        helper.getLevel().addFreshEntity(creeper);

        int[] phase = {0};
        double[] ignitionDistance = {0.0D};
        long[] phaseStart = {helper.getLevel().getGameTime()};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            try {
                if (phase[0] == 0) {
                    if (villager.getX() > start.getX() + 0.8D) {
                        creeper.ignite();
                        brain.setMemory(MemoryModuleTypeMCA.NEAREST_IGNITED_CREEPER, creeper);
                        ignitionDistance[0] = villager.distanceTo(creeper);
                        phase[0] = 1;
                        phaseStart[0] = now;
                    } else if (now - phaseStart[0] >= 60) {
                        helper.fail("chore villager never started walking toward work tree");
                    }
                } else if (phase[0] == 1) {
                    if (now <= phaseStart[0]) {
                        return;
                    }
                    helper.assertTrue(!brain.isActive(ActivitiesMCA.CHORE),
                            "active chore continued running while an ignited creeper threatened the villager");
                    helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == Chore.CHOP,
                            "creeper retreat abandoned the assigned chore");
                    helper.assertTrue(helper.getLevel().getBlockState(workTree).is(Blocks.OAK_LOG),
                            "villager kept chopping instead of retreating from ignited creeper");
                    if (villager.distanceTo(creeper) > ignitionDistance[0] + 2.0D) {
                        creeper.discard();
                        creeperRetreatCreeper = null;
                        brain.eraseMemory(MemoryModuleTypeMCA.NEAREST_IGNITED_CREEPER);
                        phase[0] = 2;
                        phaseStart[0] = now;
                    } else if (now - phaseStart[0] >= 80) {
                        helper.fail("chore villager did not retreat away from ignited creeper");
                    }
                } else if (brain.isActive(ActivitiesMCA.CHORE)) {
                    helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == Chore.CHOP,
                            "resuming after creeper retreat lost the assigned chore");
                    cleanupCreeperRetreatFixture(helper.getLevel());
                    helper.succeed();
                } else if (now - phaseStart[0] >= 40) {
                    helper.fail("assigned chore did not resume after ignited creeper threat cleared");
                }
            } catch (RuntimeException | Error failure) {
                cleanupCreeperRetreatFixture(helper.getLevel());
                throw failure;
            }
        });
    }

    @SuppressWarnings("unchecked")
    @GameTest(batch = "mca_chore_memories_safety", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void malformedMemoriesEntriesDoNotCrashFatigueTick(GameTestHelper helper) throws ReflectiveOperationException {
        VillagerEntityMCA villager = spawnChoreVillager(helper, Chore.CHOP);
        try {
            var player = helper.makeMockPlayer(GameType.SURVIVAL);
            Memories primary = villager.getVillagerBrain().getMemoriesForPlayer(player);
            primary.setHearts(25);
            primary.setInteractionFatigue(3);

            UUID secondUuid = UUID.randomUUID();
            Memories secondary = new Memories(null, 0L, secondUuid);
            secondary.setInteractionFatigue(2);

            Field memoriesField = VillagerBrain.class.getDeclaredField("MEMORIES");
            memoriesField.setAccessible(true);
            CDataParameter<CompoundTag> memoriesParam = (CDataParameter<CompoundTag>) memoriesField.get(null);

            CompoundTag corrupted = villager.getTrackedValue(memoriesParam).copy();
            corrupted.put(secondUuid.toString(), secondary.toCNBT());
            corrupted.put("empty-tag", new CompoundTag());
            CompoundTag invalidUuid = new CompoundTag();
            invalidUuid.putString("playerUUID", "not-a-valid-uuid");
            invalidUuid.putInt("interactionFatigue", 4);
            corrupted.put("not-a-valid-uuid", invalidUuid);
            CompoundTag missingUuid = new CompoundTag();
            missingUuid.putInt("interactionFatigue", 4);
            corrupted.put(UUID.randomUUID().toString(), missingUuid);
            villager.setTrackedValue(memoriesParam, corrupted);

            villager.tickCount = Math.max(1, Config.getInstance().interactionFatigueCooldown);
            villager.getVillagerBrain().think();

            Memories updatedPrimary = villager.getVillagerBrain().getMemoriesForPlayer(player);
            helper.assertTrue(updatedPrimary.getInteractionFatigue() == 2 && updatedPrimary.getHearts() == 25,
                    "batched fatigue update did not decrement valid player memory cleanly");
            var loaded = villager.getVillagerBrain().getMemories();
            helper.assertTrue(loaded.size() == 2 && loaded.get(secondUuid) != null
                            && loaded.get(secondUuid).getInteractionFatigue() == 1,
                    "getMemories did not skip malformed sub-tags or preserve batched secondary update");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_chore_scheduled_rest", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void choreHandoffPreservesEmergencyEscape(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnChoreVillager(helper, Chore.CHOP);
        Creeper creeper = EntityType.CREEPER.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
        try {
            helper.assertTrue(creeper != null, "could not create creeper threat");
            creeper.setPos(villager.position().add(3, 0, 0));
            creeper.ignite();
            var brain = villager.getBrain();
            brain.setSchedule(GameTestBrain.fixedSchedule(Activity.IDLE));
            brain.setActiveActivityIfPossible(Activity.IDLE);
            brain.setMemory(MemoryModuleTypeMCA.NEAREST_IGNITED_CREEPER, creeper);
            // The emergency escape producer has already run before the chore handoff.
            WalkTarget escape = new WalkTarget(villager.blockPosition().west(5), 0.65F, 0);
            brain.setMemory(MemoryModuleType.WALK_TARGET, escape);
            villager.getVillagerBrain().think();
            helper.assertTrue(brain.getMemory(MemoryModuleType.WALK_TARGET).orElse(null) == escape,
                    "chore handoff erased the active creeper escape route");

            brain.eraseMemory(MemoryModuleTypeMCA.NEAREST_IGNITED_CREEPER);
            villager.getVillagerBrain().think();
            helper.assertTrue(brain.isActive(ActivitiesMCA.CHORE),
                    "assigned chore did not resume after the creeper threat cleared");
        } finally {
            if (creeper != null) creeper.discard();
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_chore_scheduled_rest", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void runningChoreStopsWithoutDisturbingRestMovement(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnChoreVillager(helper, Chore.CHOP);
        try {
            ChoppingTask task = new ChoppingTask();
            long time = helper.getLevel().getGameTime();
            helper.assertTrue(task.tryStart(helper.getLevel(), villager, time),
                    "assigned chopping task could not start before rest");
            var brain = villager.getBrain();
            brain.setSchedule(GameTestBrain.fixedSchedule(Activity.REST));
            brain.setActiveActivityIfPossible(Activity.REST);
            WalkTarget bedWalk = new WalkTarget(
                    new PersistentPathTarget(villager.blockPosition().west(5), Activity.REST), 0.5F, 1);
            brain.setMemory(MemoryModuleType.WALK_TARGET, bedWalk);

            task.tickOrStop(helper.getLevel(), villager, time + 1);

            helper.assertTrue(task.getStatus() == Behavior.Status.STOPPED,
                    "running chore continued after REST took activity ownership");
            helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null) == bedWalk,
                    "stopping the chore removed REST-owned movement");
            helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == Chore.CHOP,
                    "REST handoff abandoned the assigned chore");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_chore_scheduled_rest", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void scheduledRestDoesNotConsumeChoreCooldown(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnChoreVillager(helper, Chore.CHOP);
        try {
            var brain = villager.getBrain();
            brain.setSchedule(GameTestBrain.fixedSchedule(Activity.REST));
            brain.setActiveActivityIfPossible(Activity.REST);
            ChoppingTask task = new ChoppingTask();
            task.failedTicks = 30;
            task.walkingTicks = AbstractChoreTask.WALKING_THRESHOLD + 1;

            helper.assertTrue(!task.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime()),
                    "cooling-down chore should not start during REST");
            helper.assertTrue(task.failedTicks == 30
                            && task.walkingTicks == AbstractChoreTask.WALKING_THRESHOLD + 1,
                    "REST consumed chore cooldown or issued a wandering attempt");
            helper.assertTrue(!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET),
                    "inactive chore published a wandering target during REST");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_chore_scheduled_rest", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void pendingChoreCooldownWalkReleasesOnlyItsOwnMovement(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnChoreVillager(helper, Chore.CHOP);
        try {
            var brain = villager.getBrain();
            WalkTarget cooldownWalk = new WalkTarget(villager.blockPosition().east(5), 0.4F, 0);
            brain.setMemory(MemoryModuleType.WALK_TARGET, cooldownWalk);
            villager.getVillagerBrain().rememberChoreCooldownMovement(cooldownWalk);
            brain.setSchedule(GameTestBrain.fixedSchedule(Activity.REST));
            villager.getVillagerBrain().think();
            helper.assertTrue(!brain.hasMemoryValue(MemoryModuleType.WALK_TARGET),
                    "chore cooldown walk blocked the scheduled bed journey");

            brain.setActiveActivityIfPossible(ActivitiesMCA.CHORE);
            brain.setMemory(MemoryModuleType.WALK_TARGET, cooldownWalk);
            villager.getVillagerBrain().rememberChoreCooldownMovement(cooldownWalk);
            WalkTarget restWalk = new WalkTarget(
                    new PersistentPathTarget(villager.blockPosition().west(5), Activity.REST), 0.5F, 1);
            brain.setMemory(MemoryModuleType.WALK_TARGET, restWalk);
            villager.getVillagerBrain().think();
            helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null) == restWalk,
                    "chore cooldown cleanup erased another task's bed movement");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_chore_scheduled_rest", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void scheduledRestRetainsRestOwnedMovement(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnChoreVillager(helper, Chore.CHOP);
        try {
            var brain = villager.getBrain();
            brain.setSchedule(GameTestBrain.fixedSchedule(Activity.REST));
            brain.setActiveActivityIfPossible(Activity.REST);
            helper.assertTrue(brain.isActive(Activity.REST), "fixture could not start REST");
            WalkTarget bedWalk = new WalkTarget(
                    new PersistentPathTarget(villager.blockPosition().west(5), Activity.REST), 0.5F, 1);
            brain.setMemory(MemoryModuleType.WALK_TARGET, bedWalk);

            villager.getVillagerBrain().think();
            helper.assertTrue(brain.isActive(Activity.REST), "scheduled REST was overwritten by CHORE");
            helper.assertTrue(brain.getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null) == bedWalk,
                    "rest handoff removed the bed task's movement target");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_chore_scheduled_rest", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void assigningChoreDuringRestKeepsRestActive(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnChoreVillager(helper, Chore.CHOP);
        var clock = helper.getLevel().dimensionType().defaultClock().orElseThrow();
        long previousDayTime = helper.getLevel().clockManager().getTotalTicks(clock);
        boolean previousNightOwl = Config.getInstance().allowAnyNightOwl;
        try {
            // Refreshing the brain inside assignJob chooses this villager's real schedule.
            Config.getInstance().allowAnyNightOwl = false;
            helper.getLevel().clockManager().setTotalTicks(clock, 14000L);
            helper.getLevel().environmentAttributes().invalidateTickCache();
            villager.getVillagerBrain().assignJob(Chore.CHOP, helper.makeMockPlayer(GameType.SURVIVAL));
            var brain = villager.getBrain();
            helper.assertTrue(GameTestBrain.scheduledActivityAt(helper.getLevel(), villager, 14000) == Activity.REST,
                    "assignment fixture did not refresh into a scheduled rest period");
            helper.assertTrue(brain.isActive(Activity.REST),
                    "assigning a chore during scheduled REST activated work instead of sleep");
            helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == Chore.CHOP,
                    "rest-period assignment was discarded");
        } finally {
            helper.getLevel().clockManager().setTotalTicks(clock, previousDayTime);
            helper.getLevel().environmentAttributes().invalidateTickCache();
            Config.getInstance().allowAnyNightOwl = previousNightOwl;
            villager.discard();
        }
        helper.succeed();
    }

    @AfterBatch(batch = REAL_SLEEP_BATCH)
    public static void cleanupRealSleepFixture(ServerLevel level) {
        if (realSleepAssigner != null) {
            level.players().remove(realSleepAssigner);
            realSleepAssigner = null;
        }
        if (realSleepVillager != null) {
            if (realSleepVillager.isSleeping()) realSleepVillager.stopSleeping();
            realSleepVillager.discard();
            realSleepVillager = null;
        }
        if (realSleepBedFoot != null) {
            level.removeBlock(realSleepBedFoot, false);
            level.removeBlock(realSleepBedFoot.east(), false);
            realSleepBedFoot = null;
        }
        REAL_SLEEP_CHUNKS.forEach(chunk -> level.setChunkForced(chunk.x(), chunk.z(), false));
        REAL_SLEEP_CHUNKS.clear();
    }

    @GameTest(batch = REAL_SLEEP_BATCH, templateNamespace = "mca",
            template = "gametest/isolated_ai_arena", timeoutTicks = 650)
    public static void assignedChoreWalksToBedSleepsThenResumes(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(56, 1, 56));
        BlockPos workTree = start.west(8);
        BlockPos bedFoot = start.east(5);
        BlockPos bedHead = bedFoot.east();
        prepareFlatArea(helper, start, 11, 3, REAL_SLEEP_CHUNKS);
        helper.getLevel().setBlock(workTree.below(), Blocks.DIRT.defaultBlockState(), 3);
        helper.getLevel().setBlock(workTree, Blocks.OAK_LOG.defaultBlockState(), 3);
        helper.getLevel().setBlock(workTree.above(), Blocks.OAK_LEAVES.defaultBlockState(), 3);
        var bed = Blocks.WHITE_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.EAST);
        realSleepBedFoot = bedFoot;
        helper.getLevel().setBlock(bedFoot, bed.setValue(BedBlock.PART, BedPart.FOOT), 3);
        helper.getLevel().setBlock(bedHead, bed.setValue(BedBlock.PART, BedPart.HEAD), 3);
        BlockPos claimed = helper.getLevel().getPoiManager().take(type -> type.is(PoiTypes.HOME),
                (type, pos) -> pos.equals(bedHead), bedHead, 1).orElse(null);
        helper.assertTrue(claimed != null, "chore REST fixture did not claim its bed HOME POI");

        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start))
                .spawn(EntitySpawnReason.STRUCTURE);
        realSleepVillager = villager;
        villager.refreshBrain(helper.getLevel());
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        // Keep the actual inventory stack for the identity assertion below.
        // SimpleContainer.addItem copies its input stack.
        villager.getInventory().setItem(0, axe);
        // A fake ServerPlayer tracked only in the level player list allows the chore to
        // keep its assigner without registering an unconnected ChunkMap player.
        ServerPlayer assigner = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "rest-assigner"), ClientInformation.createDefault());
        realSleepAssigner = assigner;
        helper.getLevel().players().add(assigner);
        villager.getVillagerBrain().assignJob(Chore.CHOP, assigner);
        var brain = villager.getBrain();
        brain.setSchedule(GameTestBrain.fixedSchedule(Activity.IDLE));
        brain.setMemory(MemoryModuleType.HOME, GlobalPos.of(helper.getLevel().dimension(), claimed));
        brain.setActiveActivityIfPossible(ActivitiesMCA.CHORE);
        brain.setMemory(MemoryModuleType.WALK_TARGET,
                new WalkTarget(new PersistentPathTarget(workTree, ActivitiesMCA.CHORE), 0.5F, 1));

        int[] phase = {0};
        long[] phaseStart = {helper.getLevel().getGameTime()};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            try {
                if (phase[0] == 0) {
                    helper.assertTrue(brain.isActive(ActivitiesMCA.CHORE), "initial work activity stopped early");
                    if (villager.getX() < start.getX() + 0.1D) {
                        // The AI was moving toward the work tree when REST took over.
                        phase[0] = 1;
                        phaseStart[0] = now;
                        brain.setSchedule(GameTestBrain.fixedSchedule(Activity.REST));
                        brain.updateActivityFromSchedule(helper.getLevel().environmentAttributes(), now + 100, villager.position());
                        helper.assertTrue(brain.isActive(Activity.REST), "schedule failed to interrupt work");
                    } else if (now - phaseStart[0] >= 80) {
                        helper.fail("AI-enabled villager never began walking toward assigned work");
                    }
                } else if (phase[0] == 1) {
                    helper.assertTrue(brain.isActive(Activity.REST), "assigned chore displaced live REST");
                    helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == Chore.CHOP,
                            "REST abandoned the assigned chore");
                    helper.assertTrue(helper.getLevel().getBlockState(workTree).is(Blocks.OAK_LOG),
                            "chopping continued while the villager was resting");
                    if (villager.isSleeping()) {
                        helper.assertTrue(villager.getSleepingPos().filter(bedHead::equals).isPresent(),
                                "assigned villager slept in another bed");
                        phase[0] = 2;
                        phaseStart[0] = now;
                        brain.setSchedule(GameTestBrain.fixedSchedule(Activity.IDLE));
                        brain.updateActivityFromSchedule(helper.getLevel().environmentAttributes(), now + 100, villager.position());
                    } else if (now - phaseStart[0] >= 350) {
                        helper.fail("AI-enabled villager did not reach its actual bed during REST");
                    }
                } else if (!villager.isSleeping() && brain.isActive(ActivitiesMCA.CHORE)) {
                    helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == Chore.CHOP
                                    && villager.getVillagerBrain().getJobAssigner().orElse(null) == assigner,
                            "waking did not retain the same assigned chore and owner");
                    helper.assertTrue(InventoryUtils.containsReference(villager.getInventory(), axe),
                            "resumed chore lost the original work tool");
                    boolean resumedWorkTravel = brain.getMemoryInternal(MemoryModuleType.WALK_TARGET)
                            .map(walk -> walk.getTarget() instanceof PersistentPathTarget persistent
                                    && persistent.isRequiredFor(ActivitiesMCA.CHORE)
                                    && persistent.currentBlockPosition().equals(workTree))
                            .orElse(false);
                    if (resumedWorkTravel || !helper.getLevel().getBlockState(workTree).is(Blocks.OAK_LOG)) {
                        cleanupRealSleepFixture(helper.getLevel());
                        helper.succeed();
                    } else if (now - phaseStart[0] >= 150) {
                        helper.fail("the villager woke with its assignment but never resumed tree work");
                    }
                } else if (now - phaseStart[0] >= 150) {
                    helper.fail("the villager did not wake and resume its chore after REST");
                }
            } catch (RuntimeException | Error failure) {
                cleanupRealSleepFixture(helper.getLevel());
                throw failure;
            }
        });
    }

    @GameTest(batch = "mca_chore_combat_item", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void combatReplacementSurvivesChoreStop(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnChoreVillager(helper, Chore.CHOP);
        try {
            ItemStack axe = villager.getInventory().getItem(0);
            ChoppingTask task = new ChoppingTask();
            long time = helper.getLevel().getGameTime();
            helper.assertTrue(task.tryStart(helper.getLevel(), villager, time), "chopping did not start");
            helper.assertTrue(villager.getItemInHand(villager.getDominantHand()) == axe,
                    "chopping did not borrow its inventory axe");
            ItemStack combatSword = new ItemStack(Items.IRON_SWORD);
            villager.setItemInHand(villager.getDominantHand(), combatSword);
            villager.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, attackTarget(helper));
            task.tickOrStop(helper.getLevel(), villager, time + 1);
            helper.assertTrue(task.getStatus() == Behavior.Status.STOPPED, "chopping did not yield to combat");
            helper.assertTrue(villager.getItemInHand(villager.getDominantHand()) == combatSword,
                    "chore cleanup removed combat's replacement weapon");
            helper.assertTrue(villager.getInventory().getItem(0) == axe
                            && InventoryUtils.containsReference(villager.getInventory(), axe),
                    "chore cleanup lost the borrowed inventory axe");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_chore_combat_hunting_item", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void huntingSwordCanCarryIntoCombat(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnChoreVillager(helper, Chore.HUNT);
        try {
            ItemStack sword = villager.getInventory().getItem(0);
            HuntingTask task = new HuntingTask();
            long time = helper.getLevel().getGameTime();
            helper.assertTrue(task.tryStart(helper.getLevel(), villager, time), "hunting did not start");
            helper.assertTrue(villager.getItemInHand(villager.getDominantHand()) == sword,
                    "hunting did not borrow its inventory sword");
            villager.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, attackTarget(helper));
            task.tickOrStop(helper.getLevel(), villager, time + 1);
            helper.assertTrue(villager.getItemInHand(villager.getDominantHand()) == sword,
                    "hunting removed a sword which combat can still use");
            helper.assertTrue(InventoryUtils.containsReference(villager.getInventory(), sword),
                    "hunting lost its borrowed inventory sword");

            villager.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            helper.assertTrue(task.tryStart(helper.getLevel(), villager, time + 2),
                    "hunting did not resume after combat");
            task.doStop(helper.getLevel(), villager, time + 3);
            helper.assertTrue(villager.getItemInHand(villager.getDominantHand()).isEmpty(),
                    "hunting did not release its hand on peaceful stop");
            helper.assertTrue(InventoryUtils.containsReference(villager.getInventory(), sword),
                    "peaceful hunting stop lost its inventory sword");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_chore_combat_harvest_item", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void harvestSwappedBonemealDoesNotClearReplacedCombatHand(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnChoreVillager(helper, Chore.HARVEST);
        try {
            BlockPos crop = villager.blockPosition().east();
            helper.getLevel().setBlockAndUpdate(crop.below(), Blocks.FARMLAND.defaultBlockState());
            helper.getLevel().setBlockAndUpdate(crop, Blocks.WHEAT.defaultBlockState());
            villager.getInventory().addItem(new ItemStack(Items.BONE_MEAL));
            int boneMealSlot = InventoryUtils.getFirstSlotContainingItem(villager.getInventory(),
                    item -> item.is(Items.BONE_MEAL));
            helper.assertTrue(boneMealSlot >= 0, "harvesting inventory did not accept bonemeal");
            ItemStack boneMeal = villager.getInventory().getItem(boneMealSlot);

            HarvestingTask task = new HarvestingTask();
            villager.tickCount = 8; // Advance past the initial crop scan delay.
            long time = helper.getLevel().getGameTime();
            helper.assertTrue(task.tryStart(helper.getLevel(), villager, time), "harvesting did not start");
            helper.assertTrue(villager.getItemInHand(villager.getDominantHand()) == boneMeal,
                    "harvesting did not switch to inventory bonemeal");
            ItemStack combatSword = new ItemStack(Items.IRON_SWORD);
            villager.setItemInHand(villager.getDominantHand(), combatSword);
            villager.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, attackTarget(helper));
            task.tickOrStop(helper.getLevel(), villager, time + 1);
            helper.assertTrue(task.getStatus() == Behavior.Status.STOPPED, "harvesting did not yield to combat");
            helper.assertTrue(villager.getItemInHand(villager.getDominantHand()) == combatSword,
                    "harvesting removed combat equipment after a bonemeal swap");
            helper.assertTrue(InventoryUtils.containsReference(villager.getInventory(), boneMeal),
                    "harvesting lost the original inventory bonemeal");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_chore_combat_resume_tool", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void choppingResumesWithMatchingAxeAfterCombat(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnChoreVillager(helper, Chore.CHOP);
        try {
            ItemStack axe = villager.getInventory().getItem(0);
            ItemStack combatSword = new ItemStack(Items.IRON_SWORD);
            villager.setItemInHand(villager.getDominantHand(), combatSword);
            ChoppingTask task = new ChoppingTask();
            helper.assertTrue(task.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime()),
                    "chopping did not resume after combat cleared");
            helper.assertTrue(villager.getItemInHand(villager.getDominantHand()) == axe,
                    "chopping resumed using an unrelated combat weapon instead of an axe");
            helper.assertTrue(InventoryUtils.containsReference(villager.getInventory(), axe),
                    "resuming with the axe lost its inventory source");
            helper.assertTrue(InventoryUtils.contains(villager.getInventory(), stack -> stack.is(Items.IRON_SWORD)),
                    "re-equipping the axe lost the combat sword");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_chore_combat_fishing_item", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void interruptedFishingReleasesBobberAndPreservesAnotherRod(GameTestHelper helper) {
        VillagerEntityMCA villager = spawnChoreVillager(helper, Chore.FISH);
        try {
            ItemStack borrowedRod = villager.getInventory().getItem(0);
            FishingTask task = new FishingTask();
            long time = helper.getLevel().getGameTime();
            helper.assertTrue(task.tryStart(helper.getLevel(), villager, time), "fishing did not start");
            try {
                Method method = FishingTask.class.getDeclaredMethod("equipFishingRod", VillagerEntityMCA.class);
                method.setAccessible(true);
                helper.assertTrue((boolean) method.invoke(task, villager), "fishing could not equip the rod");
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("could not use real fishing equip method", exception);
            }
            helper.assertTrue(villager.getItemInHand(villager.getDominantHand()) == borrowedRod,
                    "fishing did not borrow the inventory rod");

            BlockPos water = villager.blockPosition().east(3);
            helper.getLevel().setBlock(water, Blocks.WATER.defaultBlockState(), 3);
            MCAFishingBobberEntity bobber = MCAFishingBobberEntity.cast(helper.getLevel(), villager, water);
            helper.assertTrue(villager.getFishingBobber() == bobber && !bobber.isRemoved(),
                    "fishing interruption fixture did not produce a live bobber");

            ItemStack anotherRod = new ItemStack(Items.FISHING_ROD);
            villager.setItemInHand(villager.getDominantHand(), anotherRod);
            villager.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, attackTarget(helper));
            task.tickOrStop(helper.getLevel(), villager, time + 1);
            helper.assertTrue(task.getStatus() == Behavior.Status.STOPPED, "fishing continued during combat");
            helper.assertTrue(bobber.isRemoved() && villager.getFishingBobber() == null,
                    "stopped fishing left an active bobber");
            helper.assertTrue(villager.getItemInHand(villager.getDominantHand()) == anotherRod,
                    "fishing erased another behaviour's rod");
            helper.assertTrue(InventoryUtils.containsReference(villager.getInventory(), borrowedRod),
                    "fishing lost the inventory rod it borrowed");
            helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == Chore.FISH,
                    "fishing interruption abandoned the job");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    private static void checkThreatInterruption(GameTestHelper helper, Chore chore) {
        VillagerEntityMCA villager = spawnChoreVillager(helper, chore);
        try {
            AbstractChoreTask task = switch (chore) {
                case CHOP -> new ChoppingTask();
                case HARVEST -> new HarvestingTask();
                case HUNT -> new HuntingTask();
                case FISH -> new FishingTask();
                default -> throw new AssertionError("not a working chore: " + chore);
            };
            long time = helper.getLevel().getGameTime();
            helper.assertTrue(task.tryStart(helper.getLevel(), villager, time), chore + " could not start without danger");
            if (task instanceof HarvestingTask) {
                // The eligibility fixture needs an active field target to test combat preemption.
                setField(task, "currentPos", villager.blockPosition().east());
            }
            helper.assertTrue(task.getStatus() == Behavior.Status.RUNNING,
                    chore + " failed to enter running state before danger arrived");

            villager.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, attackTarget(helper));
            task.tickOrStop(helper.getLevel(), villager, time + 1);
            helper.assertTrue(task.getStatus() == Behavior.Status.STOPPED, chore + " continued during combat");
            helper.assertTrue(villager.getVillagerBrain().getCurrentJob() == chore,
                    chore + " was abandoned instead of interrupted");

            villager.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            helper.assertTrue(task.tryStart(helper.getLevel(), villager, time + 2),
                    chore + " did not become eligible again after danger cleared");
            task.doStop(helper.getLevel(), villager, time + 3);
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    private static VillagerEntityMCA spawnChoreVillager(GameTestHelper helper, Chore chore) {
        BlockPos start = helper.absolutePos(new BlockPos(3, 2, 3));
        helper.getLevel().getChunk(start);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setNoAi(true);
        villager.getInventory().addItem(new ItemStack(switch (chore) {
            case CHOP -> Items.IRON_AXE;
            case HARVEST -> Items.IRON_HOE;
            case HUNT -> Items.IRON_SWORD;
            case FISH -> Items.FISHING_ROD;
            default -> throw new AssertionError("not a working chore: " + chore);
        }));
        villager.getVillagerBrain().assignJob(chore, helper.makeMockPlayer(GameType.SURVIVAL));
        // Only the scheduled-rest tests change this controlled daytime work schedule.
        villager.getBrain().setSchedule(null);
        villager.getBrain().setActiveActivityIfPossible(ActivitiesMCA.CHORE);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        return villager;
    }

    private static Zombie attackTarget(GameTestHelper helper) {
        Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
        helper.assertTrue(zombie != null, "could not prepare combat target");
        return zombie;
    }

    private static void setField(AbstractChoreTask task, String name, Object value) {
        try {
            Field field = task.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(task, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("could not prepare chore target " + name, exception);
        }
    }
}
