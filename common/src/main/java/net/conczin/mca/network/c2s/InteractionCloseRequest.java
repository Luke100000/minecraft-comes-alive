package net.conczin.mca.network.c2s;

import net.conczin.mca.cobalt.network.Message;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import java.io.Serial;
import java.util.UUID;

public class InteractionCloseRequest implements Message {
    @Serial
    private static final long serialVersionUID = 5410526074172819931L;

    private final UUID villagerUUID;

    public InteractionCloseRequest(UUID uuid) {
        villagerUUID = uuid;
    }

    @Override
    public void receive(ServerPlayer player) {
        Entity v = player.serverLevel().getEntity(villagerUUID);
        if (v instanceof VillagerEntityMCA villager) {
            villager.getInteractions().stopInteracting();
        }
    }
}
