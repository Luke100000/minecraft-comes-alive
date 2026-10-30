package net.conczin.mca.entity.ai.brain.tasks.chore;

import net.conczin.mca.MCA;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.util.LandRandomPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.Map;
import java.util.Optional;

public abstract class AbstractChoreTask extends Behavior<VillagerEntityMCA> {
    protected static final int FAILED_COOLDOWN = 100;
    protected static final int WALKING_THRESHOLD = 200;

    protected VillagerEntityMCA villager;
    protected int failedTicks, walkingTicks;
    protected int lastAge;

    public AbstractChoreTask(Map<MemoryModuleType<?>, MemoryStatus> requirements) {
        super(requirements, 400);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA entity) {
        int diff = Math.max(0, entity.tickCount - lastAge);
        lastAge = entity.tickCount;

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

        return villager == null || !villager.getVillagerBrain().isPanicking();
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
    }

    Optional<Player> getAssigningPlayer() {
        return villager.getVillagerBrain().getJobAssigner();
    }

    void abandonJobWithMessage(String message) {
        getAssigningPlayer().ifPresent(player -> villager.sendChatMessage(player, message));
        villager.getVillagerBrain().abandonJob();
    }
}
