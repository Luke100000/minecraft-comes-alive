package net.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import dev.architectury.platform.Platform;
import net.mca.MCA;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.entity.ai.brain.BlockPosLookTarget;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.MultiTickTask;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;

/**
 * Holds a flower while the core movement task takes a mourner to its selected tombstone,
 * then keeps it there for an immediate line and three delayed remembrance lines.
 */
public class MournAtGraveTask extends MultiTickTask<VillagerEntityMCA> {
    private static final int MIN_DIALOGUE_DELAY = 100;
    private static final int MAX_DIALOGUE_DELAY = 300;
    private static final int DIALOGUE_COUNT = 3;

    private int remainingDialogues;
    private long nextDialogueTime;
    private boolean completed;
    private boolean hasArrived;

    public MournAtGraveTask() {
        super(ImmutableMap.of());
    }

    public boolean hasCompleted() {
        return completed;
    }

    public boolean hasArrived() {
        return hasArrived;
    }

    @Override
    protected boolean shouldRun(ServerWorld world, VillagerEntityMCA villager) {
        completed = false;
        hasArrived = false;
        boolean hasTarget = hasMourningTarget(villager);
        if (Platform.isDevelopmentEnvironment()) {
            MCA.LOGGER.info("[MOURNING_TRACE_V3] task-start villager={} position={} grave={} stand={} walking={} arrived={} hasTarget={}",
                    villager.getName().getString(),
                    villager.getBlockPos(),
                    villager.getBrain().getOptionalMemory(MemoryModuleTypeMCA.MOURNING_SITE.get()).orElse(null),
                    villager.getBrain().getOptionalMemory(MemoryModuleTypeMCA.MOURNING_POSITION.get()).orElse(null),
                    hasWalkTarget(villager),
                    EnterGraveyardTask.isAtMourningSite(villager),
                    hasTarget);
        }
        return hasTarget;
    }

    @Override
    protected boolean shouldKeepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        if (hasArrived) {
            return remainingDialogues > 0 && EnterGraveyardTask.isWithinMourningArea(villager);
        }
        return hasMourningTarget(villager);
    }

    @Override
    protected void run(ServerWorld world, VillagerEntityMCA villager, long time) {
        remainingDialogues = DIALOGUE_COUNT;
        villager.setStackInHand(Hand.MAIN_HAND, new ItemStack(getFlower(villager)));
        if (Platform.isDevelopmentEnvironment()) {
            MCA.LOGGER.info("[MOURNING_TRACE_V3] flower-held villager={} position={} time={}", villager.getName().getString(), villager.getBlockPos(), time);
        }
    }

    @Override
    protected void keepRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        if (!hasArrived) {
            if (!EnterGraveyardTask.isAtMourningSite(villager)) {
                return;
            }
            hasArrived = true;
            villager.getBrain().forget(MemoryModuleType.WALK_TARGET);
            villager.getNavigation().stop();
            villager.sendChatToAllAround("villager.grieving");
            nextDialogueTime = time + getDialogueDelay(villager);
            if (Platform.isDevelopmentEnvironment()) {
                MCA.LOGGER.info("[MOURNING_TRACE_V3] arrived villager={} position={} grave={} nextLine={}",
                        villager.getName().getString(),
                        villager.getBlockPos(),
                        villager.getBrain().getOptionalMemory(MemoryModuleTypeMCA.MOURNING_SITE.get()).orElse(null),
                        nextDialogueTime);
            }
        }

        lookAtGrave(villager);

        if (time >= nextDialogueTime) {
            villager.sendChatToAllAround("villager.grieving");
            remainingDialogues--;
            nextDialogueTime = time + getDialogueDelay(villager);
        }
    }

    @Override
    protected void finishRunning(ServerWorld world, VillagerEntityMCA villager, long time) {
        completed = hasArrived && remainingDialogues == 0 && EnterGraveyardTask.isWithinMourningArea(villager);
        if (Platform.isDevelopmentEnvironment()) {
            MCA.LOGGER.info("[MOURNING_TRACE_V3] task-finish villager={} completed={} arrived={} linesLeft={} position={} walking={}",
                    villager.getName().getString(),
                    completed,
                    hasArrived,
                    remainingDialogues,
                    villager.getBlockPos(),
                    hasWalkTarget(villager));
        }
        villager.getBrain().forget(MemoryModuleType.WALK_TARGET);
        villager.getNavigation().stop();
        villager.setStackInHand(Hand.MAIN_HAND, ItemStack.EMPTY);
        villager.getBrain().forget(MemoryModuleType.LOOK_TARGET);
    }

    @Override
    protected boolean isTimeLimitExceeded(long time) {
        return false;
    }

    private static int getDialogueDelay(VillagerEntityMCA villager) {
        return MIN_DIALOGUE_DELAY + villager.getRandom().nextInt(MAX_DIALOGUE_DELAY - MIN_DIALOGUE_DELAY + 1);
    }

    private static Item getFlower(VillagerEntityMCA villager) {
        return switch (villager.getRandom().nextInt(4)) {
            case 0 -> Items.WHITE_TULIP;
            case 1 -> Items.RED_TULIP;
            case 2 -> Items.ORANGE_TULIP;
            default -> Items.PINK_TULIP;
        };
    }

    private static boolean hasMourningTarget(VillagerEntityMCA villager) {
        return EnterGraveyardTask.hasValidMourningTarget(villager);
    }

    private static boolean hasWalkTarget(VillagerEntityMCA villager) {
        return villager.getBrain().getOptionalMemory(MemoryModuleType.WALK_TARGET).isPresent();
    }

    private static void lookAtGrave(VillagerEntityMCA villager) {
        villager.getBrain().getOptionalMemory(MemoryModuleTypeMCA.MOURNING_SITE.get())
                .ifPresent(grave -> villager.getBrain().remember(MemoryModuleType.LOOK_TARGET, new BlockPosLookTarget(grave)));
    }
}
