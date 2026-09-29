package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.Config;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.Mourning;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;

import java.util.Optional;

public class GrieveTask extends Behavior<VillagerEntityMCA> {
    public GrieveTask() {
        super(ImmutableMap.of());
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA entity) {
        if (!Config.getInstance().enableMourning) {
            return false;
        }

        Optional<GlobalPos> site = entity.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE);
        if (site.isEmpty()) {
            return false;
        }

        if (!site.orElseThrow().dimension().equals(world.dimension())) {
            return false;
        }

        Optional<Long> retryAt = entity.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_RETRY_AT);
        if (retryAt.isPresent() && world.getGameTime() < retryAt.orElseThrow()) {
            return false;
        }

        if (Mourning.isTemporarilyBlocked(entity)) {
            return false;
        }

        if (Mourning.isKnownInvalidSite(entity)) {
            Mourning.finish(entity);
            return false;
        }

        if (Mourning.isAssignedGraveUnsafe(entity)) {
            Mourning.deferUnsafe(entity);
            return false;
        }

        return true;
    }

    @Override
    protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
        Mourning.resume(villager);
    }
}
