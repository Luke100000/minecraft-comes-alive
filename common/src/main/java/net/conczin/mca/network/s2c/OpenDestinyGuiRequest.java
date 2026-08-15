package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.Config;
import net.conczin.mca.cobalt.network.Message;
import net.minecraft.server.level.ServerPlayer;
import java.io.Serial;

public class OpenDestinyGuiRequest implements Message {
    @Serial
    private static final long serialVersionUID = -8912548616237596312L;

    public final int player;
    public final boolean allowTeleportation;

    public OpenDestinyGuiRequest(ServerPlayer player) {
        this.player = player.getId();
        this.allowTeleportation = Config.getInstance().allowDestinyTeleportation;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleDestinyGuiRequest(this);
    }
}
