package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.entity.VillagerEntityMCA;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

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
    public void receive(ServerPlayerEntity player) {
        Entity e = player.getServerWorld().getEntity(uuid);
        if (e instanceof VillagerEntityMCA v) {
            if (v.isSleeping()) {
                v.wakeUp();
            }
            v.stopRiding();
            v.setPosition(player.getX(), player.getY(), player.getZ());
        }
    }
}