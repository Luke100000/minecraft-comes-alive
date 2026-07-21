package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
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

/** Holds a flower and delivers the mourning dialogue once the villager reaches its assigned grave. */
public class MournAtGraveTask extends Behavior<VillagerEntityMCA> {
    private static final int MIN_DIALOGUE_DELAY = 100;
    private static final int MAX_DIALOGUE_DELAY = 300;
    private static final int DIALOGUE_COUNT = 3;

    private int remainingDialogues;
    private long nextDialogueTime;
    private boolean completed;
    private boolean hasArrived;

    public MournAtGraveTask() {
        super(ImmutableMap.of(), Integer.MAX_VALUE - 1);
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
        return EnterGraveyardTask.hasValidMourningTarget(villager);
    }

    @Override
    protected boolean canStillUse(ServerLevel world, VillagerEntityMCA villager, long time) {
        return hasArrived
                ? remainingDialogues > 0 && EnterGraveyardTask.isWithinMourningArea(villager)
                : EnterGraveyardTask.hasValidMourningTarget(villager);
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        remainingDialogues = DIALOGUE_COUNT;
        villager.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(getFlower(villager)));
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
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getNavigation().stop();
        villager.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
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
                .ifPresent(grave -> villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(grave)));
    }
}
