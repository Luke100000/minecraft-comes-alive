package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.ActivitiesMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.Mourning;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Map;

/** Holds a flower and delivers the mourning dialogue once the villager reaches its assigned grave. */
public class MournAtGraveTask extends Behavior<VillagerEntityMCA> {
    private static final int MIN_DIALOGUE_DELAY = 100;
    private static final int MAX_DIALOGUE_DELAY = 300;
    private static final int DIALOGUE_COUNT = 3;
    private static final int SAFETY_CHECK_INTERVAL = 20;

    private int remainingDialogues;
    private long nextDialogueTime;
    private long nextSafetyCheckTime;

    public MournAtGraveTask() {
        super(Map.of(
                MemoryModuleTypeMCA.MOURNING_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleTypeMCA.MOURNING_POSITION, MemoryStatus.VALUE_PRESENT
        ), Integer.MAX_VALUE - 1);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA villager) {
        return villager.getBrain().isActive(ActivitiesMCA.GRIEVE)
                && !Mourning.isTemporarilyBlocked(villager)
                && !Mourning.isAssignedGraveUnsafe(villager)
                && EnterGraveyardTask.isAtMourningSite(villager);
    }

    @Override
    protected boolean canStillUse(ServerLevel world, VillagerEntityMCA villager, long time) {
        if (time >= nextSafetyCheckTime) {
            nextSafetyCheckTime = time + SAFETY_CHECK_INTERVAL;
            if (Mourning.isAssignedGraveUnsafe(villager)) {
                return false;
            }
        }
        return villager.getBrain().isActive(ActivitiesMCA.GRIEVE)
                && !Mourning.isTemporarilyBlocked(villager)
                && remainingDialogues > 0
                && EnterGraveyardTask.isWithinMourningArea(villager);
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        remainingDialogues = DIALOGUE_COUNT;
        if (villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_PREVIOUS_MAIN_HAND).isEmpty()) {
            ItemStack previousMainHand = villager.getMainHandItem().copy();
            ItemStack mourningFlower = new ItemStack(getFlower(villager));
            villager.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_PREVIOUS_MAIN_HAND, previousMainHand);
            villager.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_FLOWER, mourningFlower.copy());
            villager.setItemInHand(InteractionHand.MAIN_HAND, mourningFlower);
        }
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getNavigation().stop();
        villager.sendChatToAllAround("villager.grieving");
        nextDialogueTime = time + getDialogueDelay(villager);
        nextSafetyCheckTime = time + SAFETY_CHECK_INTERVAL;
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA villager, long time) {
        lookAtGrave(villager);
        if (time >= nextDialogueTime) {
            villager.sendChatToAllAround("villager.grieving");
            remainingDialogues--;
            nextDialogueTime = time + getDialogueDelay(villager);
        }
    }

    @Override
    protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
        boolean completed = remainingDialogues == 0 && EnterGraveyardTask.isWithinMourningArea(villager);
        restorePreviousHand(villager);

        if (completed || Mourning.isKnownInvalidSite(villager)) {
            Mourning.finish(villager);
        } else if (Mourning.isAssignedGraveUnsafe(villager)) {
            Mourning.deferUnsafe(villager);
        } else if (Mourning.isTemporarilyBlocked(villager)
                || !villager.getBrain().isActive(ActivitiesMCA.GRIEVE)) {
            Mourning.pause(villager);
        } else {
            Mourning.retry(villager);
        }
    }

    private void restorePreviousHand(VillagerEntityMCA villager) {
        ItemStack currentMainHand = villager.getMainHandItem();
        villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_FLOWER)
                .filter(mourningFlower -> ItemStack.matches(currentMainHand, mourningFlower))
                .flatMap(mourningFlower -> villager.getBrain()
                        .getMemoryInternal(MemoryModuleTypeMCA.MOURNING_PREVIOUS_MAIN_HAND))
                .ifPresent(previousMainHand -> villager.setItemInHand(
                        InteractionHand.MAIN_HAND,
                        previousMainHand.copy()
                ));
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_PREVIOUS_MAIN_HAND);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_FLOWER);
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

    private static void lookAtGrave(VillagerEntityMCA villager) {
        villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)
                .filter(site -> site.dimension().equals(villager.level().dimension()))
                .map(GlobalPos::pos)
                .filter(grave -> villager.getBrain().getMemoryInternal(MemoryModuleType.LOOK_TARGET)
                        .filter(target -> target.currentBlockPosition().equals(grave))
                        .isEmpty())
                .ifPresent(grave -> villager.getBrain().setMemory(
                        MemoryModuleType.LOOK_TARGET,
                        new BlockPosTracker(grave)
                ));
    }
}
