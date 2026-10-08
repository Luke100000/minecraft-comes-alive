package net.conczin.mca.entity.ai.brain.tasks;

import com.google.common.collect.ImmutableMap;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.ai.RecentVillagerEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.raid.Raid;
import org.jetbrains.annotations.Nullable;

/** Records one raid-victory fact per raid while the villager is in its existing RAID activity. */
public final class RecordRaidSurvivalTask extends Behavior<VillagerEntityMCA> {
    @Nullable
    private Raid currentRaid;
    @Nullable
    private Raid recordedRaid;

    public RecordRaidSurvivalTask() {
        // A victory remains active for 600 ticks. Stay running beyond that window so this behavior
        // cannot restart and refresh the timestamp for the same Raid instance.
        super(ImmutableMap.of(), 1_200);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, VillagerEntityMCA villager) {
        Raid raid = level.getRaidAt(villager.blockPosition());
        if (raid == null || raid == recordedRaid || !raid.isVictory()) {
            return false;
        }
        currentRaid = raid;
        return true;
    }

    @Override
    protected void start(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        recordedRaid = currentRaid;
        villager.getRecentVillagerEvents().record(
                RecentVillagerEvents.RAID_SURVIVED,
                RecentVillagerEvents.gameTime(level)
        );
    }

    @Override
    protected boolean canStillUse(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        return currentRaid != null && currentRaid.isVictory() && !currentRaid.isStopped();
    }

    @Override
    protected void stop(ServerLevel level, VillagerEntityMCA villager, long gameTime) {
        currentRaid = null;
    }
}
