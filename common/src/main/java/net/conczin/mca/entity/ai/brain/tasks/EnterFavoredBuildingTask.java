package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.registry.ProfessionsMCA;
import net.conczin.mca.server.world.data.Building;
import net.minecraft.core.BlockPos;

import java.util.Optional;

public class EnterFavoredBuildingTask extends EnterBuildingTask {
    private static final int TICKS_PER_MOOD = 1200;
    private int lastMoodIncrease = 0;

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
    protected Optional<BlockPos> getNextPosition(VillagerEntityMCA villager) {
        Optional<Building> b = getNearestBuilding(villager);
        if (b.isPresent()) {
            if (b.get().containsPos(villager.blockPosition())) {
                if (villager.tickCount > lastMoodIncrease + TICKS_PER_MOOD && villager.getVillagerBrain().getMoodValue() < 0) {
                    lastMoodIncrease = villager.tickCount;
                    villager.getVillagerBrain().modifyMoodValue(1);
                }
            } else {
                return getRandomPositionIn(b.get(), villager.level(), villager);
            }
        }
        return Optional.empty();
    }
}
