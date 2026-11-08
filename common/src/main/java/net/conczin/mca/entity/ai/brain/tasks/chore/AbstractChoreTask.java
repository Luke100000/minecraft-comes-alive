package net.conczin.mca.entity.ai.brain.tasks.chore;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Optional;

public abstract class AbstractChoreTask extends Behavior<VillagerEntityMCA> {
    protected static final int FAILED_COOLDOWN = 100;
    protected static final int WALKING_THRESHOLD = 200;

    protected VillagerEntityMCA villager;
    protected int failedTicks, walkingTicks;
    protected int lastAge;
    private ItemStack choreHand = ItemStack.EMPTY;

    public AbstractChoreTask(Map<MemoryModuleType<?>, MemoryStatus> requirements) {
        super(requirements, 400);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA entity) {
        int diff = Math.max(0, entity.tickCount - lastAge);
        lastAge = entity.tickCount;

        // Combat and panic take precedence over work and cooldown wandering.
        if (entity.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                || entity.getVillagerBrain().isPanicking()) {
            return false;
        }

        // wander around
        if (failedTicks > 0) {
            failedTicks -= diff;
            walkingTicks += diff;

            if (walkingTicks > WALKING_THRESHOLD) {
                entity.getBrain().setMemory(
                        MemoryModuleType.WALK_TARGET,
                        createStableWalkTarget(entity, LandRandomPos.getPos(entity, 10, 5))
                );
                walkingTicks = 0;
            }

            return false;
        }

        return true;
    }

    static Optional<WalkTarget> createStableWalkTarget(VillagerEntityMCA entity, Vec3 candidate) {
        return Optional.ofNullable(candidate)
                .filter(position -> entity.getNavigation().isStableDestination(BlockPos.containing(position)))
                .map(position -> new WalkTarget(position, 0.4f, 0));
    }

    @Override
    protected void tick(ServerLevel world, VillagerEntityMCA entity, long time) {
        if (getAssigningPlayer().isEmpty()) {
            MCA.LOGGER.info("Force-stopped chore because assigning player was not present.");
            villager.getVillagerBrain().abandonJob();
        }
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA entity, long time) {
        this.villager = entity;
        choreHand = ItemStack.EMPTY;
    }

    protected final void rememberChoreHand(ItemStack stack) {
        choreHand = stack;
    }

    /** Borrows a matching tool without copying it or discarding the displaced hand item. */
    protected final boolean equipChoreTool(VillagerEntityMCA entity, Chore chore, String missingMessage) {
        ItemStack held = entity.getItemInHand(entity.getDominantHand());
        if (chore.matchesTool(held)) {
            rememberChoreHand(held);
            return true;
        }

        int slot = InventoryUtils.getFirstSlotContainingItem(entity.getInventory(), chore::matchesTool);
        if (slot < 0) {
            abandonJobWithMessage(missingMessage);
            return false;
        }
        ItemStack tool = entity.getInventory().getItem(slot);
        entity.setItemInHand(entity.getDominantHand(), tool);
        rememberChoreHand(tool);
        return true;
    }

    /** Only release the stack this task last equipped, never another behaviour's replacement. */
    protected final void releaseChoreHand(VillagerEntityMCA entity, Chore chore) {
        ItemStack held = entity.getItemInHand(entity.getDominantHand());
        if (!choreHand.isEmpty() && held == choreHand
                && !(chore == Chore.HUNT && entity.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET))) {
            entity.setItemInHand(entity.getDominantHand(), ItemStack.EMPTY);
        }
        choreHand = ItemStack.EMPTY;
    }

    Optional<Player> getAssigningPlayer() {
        return villager.getVillagerBrain().getJobAssigner();
    }

    void abandonJobWithMessage(String message) {
        getAssigningPlayer().ifPresent(player -> villager.sendChatMessage(player, message));
        villager.getVillagerBrain().abandonJob();
    }
}
