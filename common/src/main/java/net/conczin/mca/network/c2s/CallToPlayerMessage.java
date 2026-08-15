package net.conczin.mca.network.c2s;

import net.conczin.mca.cobalt.network.Message;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
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
    public void receive(ServerPlayer player) {
        Entity e = player.serverLevel().getEntity(uuid);
        if (e instanceof VillagerEntityMCA v) {
            if (v.isSleeping()) {
                v.stopSleeping();
            }
            v.stopRiding();
            v.setPos(player.getX(), player.getY(), player.getZ());
        }
    }
}