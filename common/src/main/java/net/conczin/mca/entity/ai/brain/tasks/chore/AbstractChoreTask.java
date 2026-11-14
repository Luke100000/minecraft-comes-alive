package net.conczin.mca.entity.ai.brain.tasks.chore;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.Chore;
import net.conczin.mca.entity.ai.ActivitiesMCA;
import net.conczin.mca.entity.ai.navigation.PersistentPathTarget;
import net.conczin.mca.util.InventoryUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
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
    private WalkTarget ownedPlainWalkTarget;

    public AbstractChoreTask(Map<MemoryModuleType<?>, MemoryStatus> requirements) {
        super(requirements, 400);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA entity) {
        if (!entity.getBrain().isActive(ActivitiesMCA.CHORE)) {
            return false;
        }
        int activeTicks = entity.getVillagerBrain().getActiveChoreTicks();
        int diff = Math.max(0, activeTicks - lastAge);
        lastAge = activeTicks;

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
                Optional<WalkTarget> walk = createStableWalkTarget(entity, LandRandomPos.getPos(entity, 10, 5));
                entity.getBrain().setMemory(MemoryModuleType.WALK_TARGET, walk);
                walk.ifPresent(entity.getVillagerBrain()::rememberChoreCooldownMovement);
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
        ownedPlainWalkTarget = null;
    }

    /** Keep persistent chore journeys attached to CHORE so the CORE movement sink retires them for REST. */
    protected final void moveTowardsChorePosition(BlockPos pos, float speed, int closeEnoughDist) {
        WalkTarget current = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
        if (current != null && current.getTarget() instanceof PersistentPathTarget persistent
                && persistent.isRequiredFor(ActivitiesMCA.CHORE)
                && persistent.currentBlockPosition().equals(pos)
                && current.getSpeedModifier() == speed
                && current.getCloseEnoughDist() == closeEnoughDist) {
            if (villager.getBrain().getMemoryInternal(MemoryModuleType.LOOK_TARGET).orElse(null) != persistent) {
                villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, persistent);
            }
            return;
        }
        BehaviorUtils.setWalkAndLookTargetMemories(villager,
                new PersistentPathTarget(pos, ActivitiesMCA.CHORE), speed, closeEnoughDist);
    }

    /** A moving prey uses a disposable walk request; only release the one hunting last issued. */
    protected final void moveTowardsChorePrey(BlockPos pos, float speed, int closeEnoughDist) {
        villager.moveTowards(pos, speed, closeEnoughDist);
        ownedPlainWalkTarget = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
    }

    protected final void releaseChorePreyMovement(VillagerEntityMCA entity) {
        if (ownedPlainWalkTarget != null
                && entity.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null) == ownedPlainWalkTarget) {
            entity.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
        ownedPlainWalkTarget = null;
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
