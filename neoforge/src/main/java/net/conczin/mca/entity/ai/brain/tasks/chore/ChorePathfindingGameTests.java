package net.conczin.mca.entity.ai.brain.tasks.chore;

import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.navigation.PathRequestDiagnostics;
import net.conczin.mca.entity.ai.navigation.PersistentPathTarget;
import net.conczin.mca.neoforge.gametest.GameTest;
import net.conczin.mca.neoforge.gametest.GameTestHolder;
import net.conczin.mca.neoforge.gametest.PrefixGameTestTemplate;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Field;

import static net.conczin.mca.gametest.GameTestTerrain.prepareFlatArea;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class ChorePathfindingGameTests {
    private ChorePathfindingGameTests() {
    }

    @GameTest(batch = "mca_persistent_target_publication", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void repeatedWorkDestinationPreservesAndRefreshesMovementIntent(GameTestHelper helper) {
        BlockPos start = helper.absolutePos(new BlockPos(4, 2, 4));
        BlockPos target = start.east(6);
        prepareFlatArea(helper, start, 10, 2);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setNoAi(true);
        var brain = villager.getBrain();
        try {
            villager.moveTowardsPersistent(target, 0.5F, 1);
            var walkTarget = brain.getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
            var lookTarget = brain.getMemory(MemoryModuleType.LOOK_TARGET).orElseThrow();
            int replacements = 0;
            for (int request = 0; request < 200; request++) {
                villager.moveTowardsPersistent(target, 0.5F, 1);
                if (brain.getMemory(MemoryModuleType.WALK_TARGET).orElseThrow() != walkTarget
                        || brain.getMemory(MemoryModuleType.LOOK_TARGET).orElseThrow() != lookTarget) {
                    replacements++;
                }
            }
            helper.assertTrue(replacements == 0,
                    "unchanged work destination replaced walk/look memories " + replacements + " times");

            LookAtTargetSink lookSink = new LookAtTargetSink(45, 90);
            long startedAt = helper.getLevel().getGameTime();
            helper.assertTrue(lookSink.tryStart(helper.getLevel(), villager, startedAt),
                    "look sink did not start for the selected work destination");
            lookSink.tickOrStop(helper.getLevel(), villager, startedAt + 91L);
            helper.assertTrue(!brain.hasMemoryValue(MemoryModuleType.LOOK_TARGET),
                    "look sink did not clear its expired look intent");
            villager.moveTowardsPersistent(target, 0.5F, 1);
            helper.assertTrue(brain.getMemory(MemoryModuleType.WALK_TARGET).orElseThrow() == walkTarget
                            && brain.getMemory(MemoryModuleType.LOOK_TARGET).orElseThrow()
                            .currentPosition().equals(Vec3.atCenterOf(target)),
                    "renewing expired look intent disrupted walking or lost the work destination");

            brain.setMemory(MemoryModuleType.LOOK_TARGET,
                    new BlockPosTracker(Vec3.atCenterOf(target).add(0.25D, 0.0D, 0.0D)));
            villager.moveTowardsPersistent(target, 0.5F, 1);
            helper.assertTrue(brain.getMemory(MemoryModuleType.LOOK_TARGET).orElseThrow()
                            .currentPosition().equals(Vec3.atCenterOf(target)),
                    "same-block look intent prevented restoring the precise work destination");

            villager.moveTowardsPersistent(target, 0.8F, 1);
            helper.assertTrue(brain.getMemory(MemoryModuleType.WALK_TARGET).orElseThrow()
                            .getSpeedModifier() == 0.8F,
                    "unchanged destination prevented movement speed from updating");
            villager.moveTowardsPersistent(target, 0.8F, 2);
            helper.assertTrue(brain.getMemory(MemoryModuleType.WALK_TARGET).orElseThrow()
                            .getCloseEnoughDist() == 2,
                    "unchanged destination prevented arrival radius from updating");
            BlockPos nextTarget = target.north();
            villager.moveTowardsPersistent(nextTarget, 0.8F, 2);
            helper.assertTrue(brain.getMemory(MemoryModuleType.WALK_TARGET).orElseThrow()
                            .getTarget().currentBlockPosition().equals(nextTarget)
                            && brain.getMemory(MemoryModuleType.LOOK_TARGET).orElseThrow()
                            .currentPosition().equals(Vec3.atCenterOf(nextTarget)),
                    "retargeted work did not update both walking and looking");

            brain.setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(nextTarget, 0.8F, 2));
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
            villager.moveTowardsPersistent(nextTarget, 0.8F, 2);
            helper.assertTrue(brain.getMemory(MemoryModuleType.WALK_TARGET).orElseThrow()
                            .getTarget() instanceof PersistentPathTarget,
                    "same-position ordinary target prevented work intent from becoming persistent");
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.moveTowardsPersistent(nextTarget, 0.8F, 2);
            helper.assertTrue(brain.hasMemoryValue(MemoryModuleType.WALK_TARGET),
                    "cleared walk intent was not restored for ongoing work");
        } finally {
            villager.discard();
        }
        helper.succeed();
    }

    @GameTest(batch = "mca_chop_wall_recovery", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void choppingDestinationRetainsExtendedRecovery(GameTestHelper helper) {
        checkRecovery(helper, Chore.CHOP);
    }

    @GameTest(batch = "mca_harvest_wall_recovery", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void harvestingDestinationRetainsExtendedRecovery(GameTestHelper helper) {
        checkRecovery(helper, Chore.HARVEST);
    }

    @GameTest(batch = "mca_fish_wall_recovery", templateNamespace = "minecraft",
            template = "bastion/blocks/air", timeoutTicks = 80)
    public static void fishingDestinationRetainsExtendedRecovery(GameTestHelper helper) {
        checkRecovery(helper, Chore.FISH);
    }

    private static void checkRecovery(GameTestHelper helper, Chore chore) {
        BlockPos start = helper.absolutePos(new BlockPos(35, 1, 35));
        BlockPos target = start.east(6);
        prepareFlatArea(helper, start, 30, 3);
        for (int z = -23; z <= 23; z++) {
            for (int y = 0; y < 3; y++) {
                helper.getLevel().setBlock(start.offset(1, y, z), Blocks.STONE.defaultBlockState(), 3);
            }
        }
        helper.getLevel().setBlock(target, switch (chore) {
            case CHOP -> Blocks.OAK_LOG.defaultBlockState();
            case HARVEST -> Blocks.WHEAT.defaultBlockState();
            default -> Blocks.WATER.defaultBlockState();
        }, 3);
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0).withPosition(Vec3.atBottomCenterOf(start)).spawn(EntitySpawnReason.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.setNoAi(true);
        villager.setOnGround(true);
        villager.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(8.0D);
        villager.getInventory().addItem(new ItemStack(switch (chore) {
            case CHOP -> Items.IRON_AXE;
            case HARVEST -> Items.IRON_HOE;
            default -> Items.FISHING_ROD;
        }));
        villager.getVillagerBrain().assignJob(chore, helper.makeMockPlayer(GameType.SURVIVAL));
        AbstractChoreTask task = switch (chore) {
            case CHOP -> new ChoppingTask();
            case HARVEST -> new HarvestingTask();
            default -> new FishingTask();
        };
        Config config = Config.getInstance();
        int previousDistance = config.villagerPathfindingDistance;
        try {
            config.villagerPathfindingDistance = 160;
            long time = helper.getLevel().getGameTime();
            task.start(helper.getLevel(), villager, time);
            setTarget(task, switch (chore) {
                case CHOP -> "targetTree";
                case HARVEST -> "currentPos";
                default -> "targetWater";
            }, target);
            // Exercise movement toward an already-selected work destination.
            task.tick(helper.getLevel(), villager, time);
            var walkTarget = villager.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
            helper.assertTrue(walkTarget.getTarget().currentBlockPosition().equals(target),
                    chore + " changed the selected work destination");
            var before = PathRequestDiagnostics.snapshot(villager);
            var path = villager.getNavigation().createPath(target, 0);
            var after = PathRequestDiagnostics.snapshot(villager);
            // Fishers cast from nearby ground instead of entering expensive WATER
            // nodes. Verify the task can actually begin work at that endpoint.
            boolean fishingApproach = chore == Chore.FISH && path != null && path.getEndNode() != null
                    && Vec3.atBottomCenterOf(path.getEndNode().asBlockPos())
                    .distanceToSqr(new Vec3(target.getX(), target.getY(), target.getZ())) < 5.0D;
            helper.assertTrue(path != null && (path.canReach() || fishingApproach)
                            && after.extendedSearches() > before.extendedSearches(),
                    chore + " lost extended recovery around a long wall: " + path
                            + ", end=" + (path == null ? null : path.getEndNode()) + ", " + after);
            if (chore == Chore.FISH) {
                villager.setPos(Vec3.atBottomCenterOf(path.getEndNode().asBlockPos()));
                task.tick(helper.getLevel(), villager, time + 1);
                helper.assertTrue(villager.getFishingBobber() != null,
                        "recovered fishing approach did not let the task cast at its selected water");
                villager.getFishingBobber().discard();
            }
        } finally {
            config.villagerPathfindingDistance = previousDistance;
            villager.discard();
        }
        helper.succeed();
    }

    private static void setTarget(AbstractChoreTask task, String name, BlockPos target) {
        try {
            Field field = task.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(task, target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("could not prepare selected work destination", exception);
        }
    }
}
