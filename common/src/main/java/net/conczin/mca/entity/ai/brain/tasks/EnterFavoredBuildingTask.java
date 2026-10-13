package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.Optional;

public class EnterFavoredBuildingTask extends EnterBuildingTask {
    private static final int TICKS_PER_MOOD = 1200;
    private static final int MISSING_BUILDING_RETRY_TICKS = 10;
    private int lastMoodIncrease = 0;
    private long nextMissingBuildingRetryTick;

    public EnterFavoredBuildingTask(float speed) {
        super("", speed);
    }

    @Override
    public String getBuilding(VillagerEntityMCA villager) {
        String building = villager.getVillagerBrain().getMood().getBuilding();
        if (building != null) {
            return building;
        } else {
            return ProfessionsMCA.getFavoredBuilding(villager.getProfession());
        }
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        return level.getGameTime() >= nextMissingBuildingRetryTick;
    }

    @Override
    protected Optional<BlockPos> getNextPosition(VillagerEntityMCA villager) {
        Optional<Building> b = getNearestBuilding(villager);
        if (b.isEmpty()) {
            nextMissingBuildingRetryTick = villager.level().getGameTime() + MISSING_BUILDING_RETRY_TICKS;
            return Optional.empty();
        }
        if (isInsideBuilding(b.get(), villager)) {
            if (villager.tickCount > lastMoodIncrease + TICKS_PER_MOOD && villager.getVillagerBrain().getMoodValue() < 0) {
                lastMoodIncrease = villager.tickCount;
                villager.getVillagerBrain().modifyMoodValue(1);
            }
        } else {
            return getRandomPositionIn(b.get(), villager.level(), villager);
        }
        return Optional.empty();
    }
}
