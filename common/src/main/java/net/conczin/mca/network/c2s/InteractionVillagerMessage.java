package net.conczin.mca.network.c2s;

import net.conczin.mca.cobalt.network.Message;
import net.conczin.mca.entity.VillagerLike;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import java.io.Serial;
import java.util.UUID;

public class InteractionVillagerMessage implements Message {
    @Serial
    private static final long serialVersionUID = 2563941495766992462L;

    private final String command;
    private final UUID villagerUUID;

    public InteractionVillagerMessage(String command, UUID villagerUUID) {
        this.command = command.replace("gui.button.", "");
        this.villagerUUID = villagerUUID;
    }

    @Override
    public void receive(ServerPlayer player) {
        Entity v = player.serverLevel().getEntity(villagerUUID);
        if (v instanceof VillagerLike<?> villager) {
            if (villager.getInteractions().handle(player, command)) {
                villager.getInteractions().stopInteracting();
            }
        }
    }
}
