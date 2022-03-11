package mca.network;

import mca.cobalt.network.Message;
import net.minecraft.entity.player.PlayerEntity;

import java.io.Serial;

public class InteractionServerMessage implements Message {
    @Serial
    private static final long serialVersionUID = -7968792276814434450L;

    public InteractionServerMessage(String command) {
    }

    @Override
    public void receive(PlayerEntity player) {
        //TODO
    }
}
