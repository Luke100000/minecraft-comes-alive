package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.s2c.PlayerDataMessage;
import net.mca.server.world.data.PlayerSaveData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import java.io.Serial;
import java.util.UUID;

public class PlayerDataRequest implements Message {
    @Serial
    private static final long serialVersionUID = -1869959282406697226L;

    private final UUID uuid;

    public PlayerDataRequest(UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public void receive(ServerPlayer player) {
        Player playerEntity = player.level().getPlayerByUUID(uuid);
        if (playerEntity instanceof ServerPlayer serverPlayerEntity) {
            PlayerSaveData data = PlayerSaveData.get(serverPlayerEntity);
            if (data.isEntityDataSet()) {
                CompoundTag nbt = data.getEntityData();
                NetworkHandler.sendToPlayer(new PlayerDataMessage(uuid, nbt), player);
            }
        }
    }
}
