package net.conczin.mca.entity.ai.brain.tasks.chore;

import com.mojang.authlib.GameProfile;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.ActivitiesMCA;
import net.conczin.mca.entity.ai.Chore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Field;
import java.util.UUID;

@PrefixGameTestTemplate(false)
public final class ChoreReviewRegressionGameTests {
    private ChoreReviewRegressionGameTests() {
    }

    @GameTest(batch = "mca_review_rest_cooldown", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void longRestDoesNotExhaustPendingChoreCooldown(GameTestHelper helper) {
        Worker worker = worker(helper, Chore.CHOP);
        try {
            VillagerEntityMCA villager = worker.villager;
            var brain = villager.getBrain();
            ChoppingTask task = new ChoppingTask();
            task.failedTicks = 80;
            task.lastAge = villager.tickCount;
            brain.setSchedule(new ScheduleBuilder(new Schedule()).changeActivityAt(0, Activity.REST).build());
            brain.setActiveActivityIfPossible(Activity.REST);
            helper.assertTrue(brain.isActive(Activity.REST), "fixture did not enter REST");
            for (int i = 0; i < 150; i++) {
                villager.tickCount++;
                villager.getVillagerBrain().think();
            }
            helper.assertTrue(!task.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime()),
                    "chore became eligible while still resting");
            brain.setSchedule(Schedule.EMPTY);
            brain.setActiveActivityIfPossible(ActivitiesMCA.CHORE);
            villager.tickCount++;
            villager.getVillagerBrain().think();
            helper.assertTrue(brain.isActive(ActivitiesMCA.CHORE), "fixture did not resume CHORE");
            helper.assertTrue(!task.tryStart(helper.getLevel(), villager, helper.getLevel().getGameTime() + 1),
                    "rest incorrectly exhausted the pending chore cooldown");
            helper.assertTrue(task.failedTicks >= 78 && task.walkingTicks <= 2,
                    "rest duration advanced the active-work cooldown or wandering timer");
        } finally {
            worker.close(helper);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_review_hunting_stale", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void huntReacquiresLivePreyAfterInterruptedTargetWasRemoved(GameTestHelper helper) {
        Worker worker = worker(helper, Chore.HUNT);
        BlockPos start = worker.villager.blockPosition();
        Cow stale = prey(helper, start.east());
        Cow replacement = prey(helper, start.west());
        try {
            HuntingTask task = new HuntingTask();
            long time = helper.getLevel().getGameTime();
            task.start(helper.getLevel(), worker.villager, time);
            setField(task, "target", stale);
            task.stop(helper.getLevel(), worker.villager, time + 1);
            stale.discard();
            task.start(helper.getLevel(), worker.villager, time + 2);
            task.tick(helper.getLevel(), worker.villager, time + 3);
            task.tick(helper.getLevel(), worker.villager, time + 4);
            helper.assertTrue(replacement.getHealth() < replacement.getMaxHealth(),
                    "resumed hunt stayed on removed prey instead of attacking available living prey");
        } finally {
            stale.discard();
            replacement.discard();
            worker.close(helper);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_review_hunting_cancel", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void vanishedAssignerStopsHuntingBeforeDamage(GameTestHelper helper) {
        Worker worker = worker(helper, Chore.HUNT);
        Cow prey = prey(helper, worker.villager.blockPosition().east());
        try {
            HuntingTask task = new HuntingTask();
            long time = helper.getLevel().getGameTime();
            task.start(helper.getLevel(), worker.villager, time);
            setField(task, "target", prey);
            helper.getLevel().players().remove(worker.assigner);
            helper.assertTrue(worker.villager.getVillagerBrain().getJobAssigner().isEmpty(),
                    "fixture still has an assigning player");
            task.tick(helper.getLevel(), worker.villager, time + 1);
            helper.assertTrue(prey.getHealth() == prey.getMaxHealth(),
                    "hunter damaged prey after its assigning player vanished");
            helper.assertTrue(worker.villager.getVillagerBrain().getCurrentJob() == Chore.NONE,
                    "missing assigning player did not cancel the job");
        } finally {
            prey.discard();
            worker.close(helper);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_review_abandoned_cooldown", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void abandoningChoreReleasesPendingCooldownMovement(GameTestHelper helper) {
        Worker worker = worker(helper, Chore.CHOP);
        try {
            WalkTarget cooldownTarget = new WalkTarget(worker.villager.blockPosition().east(6), 0.4f, 0);
            worker.villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, cooldownTarget);
            worker.villager.getVillagerBrain().rememberChoreCooldownMovement(cooldownTarget);
            worker.villager.getVillagerBrain().abandonJob();
            helper.assertTrue(!worker.villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET),
                    "abandoned chore leaked its cooldown WALK_TARGET into the idle brain");
        } finally {
            worker.close(helper);
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_review_chopping_cancel", templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void vanishedAssignerStopsChoppingBeforeBreakingLog(GameTestHelper helper) {
        Worker worker = worker(helper, Chore.CHOP);
        BlockPos log = worker.villager.blockPosition().east();
        helper.getLevel().setBlock(log, Blocks.OAK_LOG.defaultBlockState(), 3);
        try {
            ChoppingTask task = new ChoppingTask();
            long time = helper.getLevel().getGameTime();
            task.start(helper.getLevel(), worker.villager, time);
            setField(task, "targetTree", log);
            setField(task, "targetTreeTicks", 1);
            helper.getLevel().players().remove(worker.assigner);
            helper.assertTrue(worker.villager.getVillagerBrain().getJobAssigner().isEmpty(),
                    "fixture still has an assigning player");
            task.tick(helper.getLevel(), worker.villager, time + 1);
            helper.assertTrue(helper.getLevel().getBlockState(log).is(Blocks.OAK_LOG),
                    "chopper destroyed a log after its assigning player vanished");
            helper.assertTrue(worker.villager.getVillagerBrain().getCurrentJob() == Chore.NONE,
                    "missing assigning player did not cancel the job");
        } finally {
            worker.close(helper);
        }
        helper.succeed();
    }

    private static Worker worker(GameTestHelper helper, Chore chore) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        helper.getLevel().getChunk(start);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(MobSpawnType.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setNoAi(true);
        villager.getInventory().addItem(new ItemStack(chore == Chore.HUNT ? Items.IRON_SWORD : Items.IRON_AXE));
        ServerPlayer assigner = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "review-assigner"), ClientInformation.createDefault());
        helper.getLevel().players().add(assigner);
        villager.getVillagerBrain().assignJob(chore, assigner);
        villager.getBrain().setSchedule(Schedule.EMPTY);
        villager.getBrain().setActiveActivityIfPossible(ActivitiesMCA.CHORE);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        helper.assertTrue(villager.getVillagerBrain().getJobAssigner().orElse(null) == assigner,
                "fixture did not register the real assigning player");
        return new Worker(villager, assigner);
    }

    private static Cow prey(GameTestHelper helper, BlockPos position) {
        Cow cow = new Cow(EntityType.COW, helper.getLevel());
        cow.setPos(Vec3.atBottomCenterOf(position).x, Vec3.atBottomCenterOf(position).y,
                Vec3.atBottomCenterOf(position).z);
        cow.setNoAi(true);
        helper.assertTrue(helper.getLevel().addFreshEntity(cow), "could not spawn hunting prey");
        return cow;
    }

    private static void setField(Object task, String name, Object value) {
        try {
            Field field = task.getClass().getDeclaredField(name);
            field.setAccessible(true);
            if (field.getType() == int.class) {
                field.setInt(task, (Integer) value);
            } else {
                field.set(task, value);
            }
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not set up chore regression target " + name, e);
        }
    }

    private record Worker(VillagerEntityMCA villager, ServerPlayer assigner) {
        private void close(GameTestHelper helper) {
            helper.getLevel().players().remove(assigner);
            villager.discard();
        }
    }
}
