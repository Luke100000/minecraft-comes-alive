package mca.network;

import mca.cobalt.network.Message;
import mca.entity.VillagerLike;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

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
    public void receive(PlayerEntity player) {
        Entity villager = ((ServerWorld) player.world).getEntity(villagerUUID);
        if (villager instanceof VillagerLike<?>) {
            if (((VillagerLike<?>)villager).getInteractions().handle((ServerPlayerEntity)player, command)) {
                ((VillagerLike<?>)villager).getInteractions().stopInteracting();
            }
        }
    }
}
