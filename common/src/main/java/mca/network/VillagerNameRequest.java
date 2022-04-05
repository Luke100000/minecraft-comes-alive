package mca.network;

import mca.cobalt.network.Message;
import mca.cobalt.network.NetworkHandler;
import mca.entity.ai.relationship.Gender;
import mca.network.client.VillagerNameResponse;
import mca.resources.API;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.Serial;

public class VillagerNameRequest implements Message {
    @Serial
    private static final long serialVersionUID = -7850240766540487322L;

    private final Gender gender;

    public VillagerNameRequest(Gender gender) {
        this.gender = gender;
    }

    @Override
    public void receive(PlayerEntity e) {
        String name = API.getVillagePool().pickCitizenName(gender);
        NetworkHandler.sendToPlayer(new VillagerNameResponse(name), (ServerPlayerEntity) e);
    }
}
