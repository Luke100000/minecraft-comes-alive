package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Holds a flower while the core movement task takes a mourner to its selected tombstone,
 * then keeps it there for an immediate line and three delayed remembrance lines.
 */
public class MournAtGraveTask extends Behavior<VillagerEntityMCA> {
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
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA villager) {
        completed = false;
        hasArrived = false;
        boolean hasTarget = hasMourningTarget(villager);
        if (MCA.platformHelper.isDevelopmentEnvironment()) {
            MCA.LOGGER.info("[MOURNING_TRACE_V3] task-start villager={} position={} grave={} stand={} walking={} arrived={} hasTarget={}",
                    villager.getName().getString(),
                    villager.blockPosition(),
                    villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE.get()).orElse(null),
                    villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_POSITION.get()).orElse(null),
                    hasWalkTarget(villager),
                    EnterGraveyardTask.isAtMourningSite(villager),
                    hasTarget);
        }
        return hasTarget;
    }

    @Override
    protected boolean canStillUse(ServerLevel world, VillagerEntityMCA villager, long time) {
        if (hasArrived) {
            return remainingDialogues > 0 && EnterGraveyardTask.isWithinMourningArea(villager);
        }
        return hasMourningTarget(villager);
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        remainingDialogues = DIALOGUE_COUNT;
        villager.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(getFlower(villager)));
        if (MCA.platformHelper.isDevelopmentEnvironment()) {
            MCA.LOGGER.info("[MOURNING_TRACE_V3] flower-held villager={} position={} time={}", villager.getName().getString(), villager.blockPosition(), time);
        }
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        if (!hasArrived) {
            if (!EnterGraveyardTask.isAtMourningSite(villager)) {
                return;
            }
            hasArrived = true;
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            villager.getNavigation().stop();
            villager.sendChatToAllAround("villager.grieving");
            nextDialogueTime = time + getDialogueDelay(villager);
            if (MCA.platformHelper.isDevelopmentEnvironment()) {
                MCA.LOGGER.info("[MOURNING_TRACE_V3] arrived villager={} position={} grave={} nextLine={}",
                        villager.getName().getString(),
                        villager.blockPosition(),
                        villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE.get()).orElse(null),
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
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        completed = hasArrived && remainingDialogues == 0 && EnterGraveyardTask.isWithinMourningArea(villager);
        if (MCA.platformHelper.isDevelopmentEnvironment()) {
            MCA.LOGGER.info("[MOURNING_TRACE_V3] task-finish villager={} completed={} arrived={} linesLeft={} position={} walking={}",
                    villager.getName().getString(),
                    completed,
                    hasArrived,
                    remainingDialogues,
                    villager.blockPosition(),
                    hasWalkTarget(villager));
        }
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getNavigation().stop();
        villager.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    @Override
    protected boolean timedOut(long time) {
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
        return villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).isPresent();
    }

    private static void lookAtGrave(VillagerEntityMCA villager) {
        villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE.get())
                .ifPresent(grave -> villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(grave)));
    }
}
