package net.conczin.mca;

import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.network.ClientHandler;
import net.conczin.mca.network.ClientHandlerImpl;

import java.util.Optional;
import java.util.UUID;

public abstract class ClientProxyAbstractImpl extends ClientProxy.Impl {
    private ClientHandler networkHandler;

    @Override
    public final synchronized ClientHandler getNetworkHandler() {
        if (networkHandler == null) {
            networkHandler = new ClientHandlerImpl();
        }
        return networkHandler;
    }

    @Override
    public final Optional<VillagerLike<?>> getPlayerData(UUID uuid) {
        return MCAClient.getPlayerData(uuid);
    }
}
