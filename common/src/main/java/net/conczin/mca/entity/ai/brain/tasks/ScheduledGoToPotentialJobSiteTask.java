package net.conczin.mca.entity.ai.brain.tasks;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.GoToPotentialJobSite;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import org.jetbrains.annotations.Nullable;

/** Rechecks vanilla's activity eligibility while a CORE job-site trip is running. */
public class ScheduledGoToPotentialJobSiteTask extends GoToPotentialJobSite {
    @Nullable
    private WalkTarget ownedWalkTarget;
    @Nullable
    private PositionTracker ownedLookTarget;

    public ScheduledGoToPotentialJobSiteTask(float speed) {
        super(speed);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return super.checkExtraStartConditions(level, villager)
                && super.canStillUse(level, villager, time);
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long time) {
        super.tick(level, villager, time);
        ownedWalkTarget = villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET).orElse(null);
        ownedLookTarget = villager.getBrain().getMemoryInternal(MemoryModuleType.LOOK_TARGET).orElse(null);
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long time) {
        // A newer REST or combat target may already own the shared Brain slots.
        if (ownedWalkTarget != null && villager.getBrain().getMemoryInternal(MemoryModuleType.WALK_TARGET)
                .orElse(null) == ownedWalkTarget) {
            villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
        if (ownedLookTarget != null && villager.getBrain().getMemoryInternal(MemoryModuleType.LOOK_TARGET)
                .orElse(null) == ownedLookTarget) {
            villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        }
        ownedWalkTarget = null;
        ownedLookTarget = null;
        super.stop(level, villager, time);
    }
}
