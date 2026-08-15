package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.cobalt.network.Message;

import java.io.Serial;

public class GetVillageFailedResponse implements Message {
    @Serial
    private static final long serialVersionUID = 4021214184633955444L;

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleVillageDataFailedResponse(this);
    }
}
