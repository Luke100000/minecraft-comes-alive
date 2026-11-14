package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class EnterFavoredBuildingTask extends EnterBuildingTask {
    private static final int TICKS_PER_MOOD = 1200;
    private static final int MISSING_BUILDING_RETRY_TICKS = 10;
    private int lastMoodIncrease = 0;
    private long nextMissingBuildingRetryTick;
    private String lastMissingBuildingType;

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
        return super.checkExtraStartConditions(level, villager)
                && (level.getGameTime() >= nextMissingBuildingRetryTick
                || !Objects.equals(getBuilding(villager), lastMissingBuildingType));
    }

    @Override
    protected Optional<BlockPos> getNextPosition(VillagerEntityMCA villager) {
        String buildingType = getBuilding(villager);
        List<Building> matching = getMatchingBuildings(villager, buildingType);
        if (matching.isEmpty()) {
            lastMissingBuildingType = buildingType;
            nextMissingBuildingRetryTick = villager.level().getGameTime() + MISSING_BUILDING_RETRY_TICKS;
            return Optional.empty();
        }
        if (isInsideBuilding(matching.getFirst(), villager)
                && getRandomPositionIn(matching.getFirst(), villager.level(), villager).isPresent()) {
            if (villager.tickCount > lastMoodIncrease + TICKS_PER_MOOD && villager.getVillagerBrain().getMoodValue() < 0) {
                lastMoodIncrease = villager.tickCount;
                villager.getVillagerBrain().modifyMoodValue(1);
            }
            return Optional.empty();
        }
        return getPositionInAvailableBuilding(villager, matching, buildingType);
    }
}
