package net.mca.network.c2s;

import net.mca.Config;
import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.s2c.ConfigResponse;
import net.minecraft.server.level.ServerPlayer;
import java.io.Serial;

public class ConfigRequest implements Message {
    @Serial
    private static final long serialVersionUID = 7108115056986169352L;

    @Override
    public void receive(ServerPlayer player) {
        NetworkHandler.sendToPlayer(new ConfigResponse(Config.getInstance()), player);
    }
}
