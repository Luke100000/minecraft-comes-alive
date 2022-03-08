package mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import java.util.Optional;
import mca.entity.VillagerEntityMCA;
import mca.entity.ai.MemoryModuleTypeMCA;
import mca.util.BlockBoxExtended;
import net.minecraft.entity.ai.NoPenaltyTargeting;
import net.minecraft.entity.ai.brain.MemoryModuleState;
import net.minecraft.entity.ai.brain.MemoryModuleType;
import net.minecraft.entity.ai.brain.task.LookTargetUtil;
import net.minecraft.entity.ai.brain.task.Task;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public class PatrolVillageTask extends Task<VillagerEntityMCA> {
    private final int completionRange;
    private final float speed;

    public PatrolVillageTask(int completionRange, float speed) {
        super(ImmutableMap.of(
                MemoryModuleTypeMCA.PLAYER_FOLLOWING.get(), MemoryModuleState.VALUE_ABSENT,
                MemoryModuleType.INTERACTION_TARGET, MemoryModuleState.VALUE_ABSENT,
                MemoryModuleType.ATTACK_TARGET, MemoryModuleState.VALUE_ABSENT,
                MemoryModuleType.WALK_TARGET, MemoryModuleState.VALUE_ABSENT,
                MemoryModuleType.LOOK_TARGET, MemoryModuleState.REGISTERED));
        this.completionRange = completionRange;
        this.speed = speed;
    }

    @Override
    protected boolean shouldRun(ServerWorld world, VillagerEntityMCA entity) {
        return !InteractTask.shouldRun(entity);
    }

    @Override
    protected void run(ServerWorld serverWorld, VillagerEntityMCA villager, long l) {
        getNextPosition(villager).ifPresent(pos -> LookTargetUtil.walkTowards(villager, pos, speed, completionRange));
    }

    private Optional<BlockPos> getNextPosition(VillagerEntityMCA villager) {
        return villager.getResidency().getHomeVillage().map(village -> {
            BlockBoxExtended box = village.getBox();
            int x = box.getMinX() + villager.getRandom().nextInt(box.getBlockCountX());
            int z = box.getMinZ() + villager.getRandom().nextInt(box.getBlockCountZ());
            Vec3d targetPos = new Vec3d(x, box.getCenter().getY(), z);

            return NoPenaltyTargeting.findTo(villager, 32, 16, targetPos, Math.PI * 0.5);
        }).map(BlockPos::new);
    }
}
