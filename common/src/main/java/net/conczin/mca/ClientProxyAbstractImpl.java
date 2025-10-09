package net.conczin.mca;

import net.conczin.mca.network.ClientHandler;
import net.conczin.mca.network.ClientHandlerImpl;

public abstract class ClientProxyAbstractImpl extends ClientProxy.Impl {
    private final ClientHandler networkHandler = new ClientHandlerImpl();

    @Override
    public final ClientHandler getNetworkHandler() {
        return networkHandler;
    }
}
