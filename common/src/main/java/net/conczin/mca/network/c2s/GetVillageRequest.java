package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.GetVillageFailedResponse;
import net.conczin.mca.network.s2c.GetVillageResponse;
import net.conczin.mca.resources.Rank;
import net.conczin.mca.resources.Tasks;
import net.conczin.mca.server.world.data.GraveyardManager;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.Set;

public record GetVillageRequest() implements HandleablePayload {
    public static final CustomPacketPayload.Type<GetVillageRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("get_village_request"));
    public static final StreamCodec<FriendlyByteBuf, GetVillageRequest> STREAM_CODEC = StreamCodec.unit(new GetVillageRequest());

    @Override
    public void handleServer(ServerPlayer player) {
        sendResponse(player);
    }

    static void sendResponse(ServerPlayer player) {
        Optional<Village> village = Village.findNearest(player);
        if (village.isPresent()) {
            GraveyardManager.get(player.serverLevel()).reportToVillageManager(player);
            VillageManager.get(player.serverLevel()).ensureStructureHierarchy(village.get());
            village.get().updateMaxPopulation();
            int reputation = village.get().getReputation(player);
            boolean isVillage = village.get().isVillage();
            Rank rank = Tasks.getRank(village.get(), player);
            Set<String> ids = Tasks.getCompletedIds(village.get(), player);
            Network.sendToPlayer(new GetVillageResponse(village.get(), rank, reputation, isVillage, ids), player);
        } else {
            Network.sendToPlayer(new GetVillageFailedResponse(), player);
        }
    }

    @Override
    public Type<GetVillageRequest> type() {
        return TYPE;
    }
}
