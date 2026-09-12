package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.Mourning;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;

import java.util.Optional;

public class GrieveTask extends Behavior<VillagerEntityMCA> {
    public GrieveTask() {
        super(ImmutableMap.of());
    }

    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA entity) {
        if (!Config.getInstance().enableMourning) {
            return false;
        }

        Optional<BlockPos> site = entity.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE);
        if (site.isEmpty()) {
            return false;
        }

        if (!Mourning.isMournableTombstone(world, site.get())) {
            Mourning.clear(entity);
            return false;
        }

        return entity.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_RETRY_AT)
                .filter(retryAt -> world.getGameTime() >= retryAt)
                .isPresent();
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)
                .ifPresent(grave -> Mourning.start(villager, grave));
    }
}
