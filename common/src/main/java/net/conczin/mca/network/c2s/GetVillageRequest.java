package net.conczin.mca.network.c2s;

import net.conczin.mca.cobalt.network.Message;
import net.conczin.mca.cobalt.network.NetworkHandler;
import net.conczin.mca.network.s2c.GetVillageFailedResponse;
import net.conczin.mca.network.s2c.GetVillageResponse;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.Tasks;
import net.conczin.mca.server.world.data.GraveyardManager;
import net.conczin.mca.server.world.data.Village;
import net.minecraft.server.level.ServerPlayer;
import java.io.Serial;
import java.util.Optional;
import java.util.Set;

public class GetVillageRequest implements Message {
    @Serial
    private static final long serialVersionUID = -1302412553466016247L;

    @Override
    public void receive(ServerPlayer player) {
        Optional<Village> village = Village.findNearest(player);
        if (village.isPresent()) {
            GraveyardManager.get(player.serverLevel()).reportToVillageManager(player);
            village.get().updateMaxPopulation();
            int reputation = village.get().getReputation(player);
            boolean isVillage = village.get().isVillage();
            Rank rank = Tasks.getRank(village.get(), player);
            Set<String> ids = Tasks.getCompletedIds(village.get(), player);
            NetworkHandler.sendToPlayer(new GetVillageResponse(village.get(), rank, reputation, isVillage, ids), player);
        } else {
            NetworkHandler.sendToPlayer(new GetVillageFailedResponse(), player);
        }
    }
}
