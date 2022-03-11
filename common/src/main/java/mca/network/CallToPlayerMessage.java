package mca.network;

import mca.cobalt.network.Message;
import mca.entity.VillagerEntityMCA;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.io.Serial;
import java.util.UUID;

public class CallToPlayerMessage implements Message {
    @Serial
    private static final long serialVersionUID = 2556280539773400447L;

    private final UUID uuid;

    public CallToPlayerMessage(UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public void receive(PlayerEntity player) {
        Entity e = ((ServerWorld) player.world).getEntity(uuid);
        if (e instanceof VillagerEntityMCA v) {
            v.setPosition(player.getX(), player.getY(), player.getZ());
        }
    }
}