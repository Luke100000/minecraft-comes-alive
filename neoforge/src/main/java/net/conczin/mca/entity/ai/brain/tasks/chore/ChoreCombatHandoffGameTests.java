package net.conczin.mca.entity.ai.brain.tasks.chore;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.MCAFishingBobberEntity;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.util.InventoryUtils;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.function.Predicate;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class ChoreCombatHandoffGameTests {
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
            Zombie zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
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
            HarvestingTask task = new HarvestingTask();
            long time = helper.getLevel().getGameTime();
            helper.assertTrue(task.tryStart(helper.getLevel(), villager, time), "harvesting did not start");
            setField(task, "currentPos", villager.blockPosition().east());
            villager.getInventory().addItem(new ItemStack(Items.BONE_MEAL));
            int boneMealSlot = InventoryUtils.getFirstSlotContainingItem(villager.getInventory(),
                    item -> item.is(Items.BONE_MEAL));
            helper.assertTrue(boneMealSlot >= 0, "harvesting inventory did not accept bonemeal");
            ItemStack boneMeal = villager.getInventory().getItem(boneMealSlot);
            try {
                Method method = HarvestingTask.class.getDeclaredMethod("swapItem", Predicate.class);
                method.setAccessible(true);
                method.invoke(task, (Predicate<ItemStack>) item -> item.is(Items.BONE_MEAL));
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("could not use real harvesting item switch", exception);
            }
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
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        return villager;
    }

    private static Zombie attackTarget(GameTestHelper helper) {
        Zombie zombie = EntityTypes.ZOMBIE.create(helper.getLevel(), EntitySpawnReason.STRUCTURE);
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
