package net.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.mca.entity.VillagerEntityMCA;
import net.mca.entity.ai.MemoryModuleTypeMCA;
import net.mca.util.BlockBoxExtended;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

public class PatrolVillageTask extends Behavior<VillagerEntityMCA> {
    private final int completionRange;
    private final float speed;

    public PatrolVillageTask(int completionRange, float speed) {
        super(ImmutableMap.of(
                MemoryModuleTypeMCA.PLAYER_FOLLOWING.get(), MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.INTERACTION_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED));
        this.completionRange = completionRange;
        this.speed = speed;
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA entity) {
        return entity.getResidency().getHomeVillage().isPresent()
                && !InteractTask.shouldRun(entity);
    }

    @Override
    protected void start(ServerLevel serverWorld, VillagerEntityMCA villager, long l) {
        getNextPosition(villager).ifPresent(pos -> BehaviorUtils.setWalkAndLookTargetMemories(villager, pos, speed, completionRange));
    }

    private Optional<BlockPos> getNextPosition(VillagerEntityMCA villager) {
        return villager.getResidency().getHomeVillage().map(village -> {
            BlockBoxExtended box = village.getBox();
            int x = box.minX() + villager.getRandom().nextInt(box.getXSpan());
            int z = box.minZ() + villager.getRandom().nextInt(box.getZSpan());
            Vec3 targetPos = new Vec3(x, box.getCenter().getY(), z);

            return DefaultRandomPos.getPosTowards(villager, 32, 16, targetPos, Math.PI * 0.5);
        }).map(BlockPos::containing);
    }
}
