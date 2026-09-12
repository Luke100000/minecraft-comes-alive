package net.conczin.mca.entity.ai;

import net.conczin.mca.block.TombstoneBlock;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.brain.VillagerTasksMCA;
import net.conczin.mca.registry.TagsMCA;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.Level;

import java.util.List;

public final class Mourning {
    private Mourning() {
    }

    public static void start(VillagerEntityMCA villager, BlockPos grave) {
        villager.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_SITE, grave);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_POSITION);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_RETRY_AT);
        villager.getBrain().eraseMemory(MemoryModuleType.PATH);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(grave));
        villager.getBrain().setActiveActivityIfPossible(ActivitiesMCA.GRIEVE);
    }

    public static void clear(VillagerEntityMCA villager) {
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_SITE);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_POSITION);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_RETRY_AT);
    }

    public static boolean isMournableTombstone(Level level, BlockPos position) {
        return level.getBlockState(position).is(TagsMCA.Blocks.TOMBSTONES)
                && TombstoneBlock.Data.of(level.getBlockEntity(position))
                .filter(TombstoneBlock.Data::hasEntity)
                .filter(data -> !data.isResurrecting())
                .isPresent();
    }

    public static List<BlockPos> getMournableGraves(Village village, Level level) {
        return village.getBuildingsOfType("graveyard")
                .filter(Building::isComplete)
                .flatMap(Building::getBlockPosStream)
                .distinct()
                .filter(position -> isMournableTombstone(level, position))
                .toList();
    }

    public static boolean canMournAmbiently(VillagerEntityMCA villager) {
        boolean ambientActivity = villager.getBrain().isActive(Activity.IDLE)
                || villager.getBrain().isActive(Activity.MEET);
        return villager.isAlive()
                && ambientActivity
                && !villager.getBrain().isActive(ActivitiesMCA.GRIEVE)
                && villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isEmpty()
                && !VillagerTasksMCA.isInDanger(villager)
                && villager.getVillagerBrain().getCurrentJob() == Chore.NONE;
    }
}
