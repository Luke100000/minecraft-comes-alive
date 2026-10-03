package net.conczin.mca.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;

/** Small 26.x POI inspection helpers used by MCA GameTests. */
public final class GameTestPoi {
    private GameTestPoi() {
    }

    public static int getFreeTickets(PoiManager poiManager, BlockPos pos) {
        return poiManager.getInRange(ignored -> true, pos, 0, PoiManager.Occupancy.ANY)
                .filter(record -> record.getPos().equals(pos))
                .findFirst()
                .map(PoiRecord::getFreeTickets)
                .orElse(0);
    }
}
