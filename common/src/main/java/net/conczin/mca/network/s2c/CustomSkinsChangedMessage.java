package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.cobalt.network.Message;

import java.io.Serial;

public class CustomSkinsChangedMessage implements Message {
    @Serial
    private static final long serialVersionUID = 2044285891943685881L;

    public CustomSkinsChangedMessage() {
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleCustomSkinsChangedMessage(this);
    }
}
