package net.mca.entity.ai.brain.tasks.chore;

import net.mca.MCA;
import net.mca.entity.VillagerEntityMCA;
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
    protected VillagerEntityMCA villager;
    protected int failedTicks, walkingTicks;
    protected int lastAge;
    protected static final int FAILED_COOLDOWN = 100;
    protected static final int WALKING_THRESHOLD = 200;

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
                Optional<Vec3> optional = Optional.ofNullable(LandRandomPos.getPos(entity, 10, 5));
                entity.getBrain().setMemory(MemoryModuleType.WALK_TARGET, optional.map(vec3d -> new WalkTarget(vec3d, 0.4f, 0)));
                walkingTicks = 0;
            }

            return false;
        }

        return villager == null || !villager.getVillagerBrain().isPanicking();
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
