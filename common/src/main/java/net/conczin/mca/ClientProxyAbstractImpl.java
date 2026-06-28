package net.conczin.mca;

import net.conczin.mca.network.ClientHandler;
import net.conczin.mca.network.ClientHandlerImpl;
import net.conczin.mca.entity.VillagerLike;

import java.util.Optional;
import java.util.UUID;

public abstract class ClientProxyAbstractImpl extends ClientProxy.Impl {
    private final ClientHandler networkHandler = new ClientHandlerImpl();

    @Override
    public final ClientHandler getNetworkHandler() {
        return networkHandler;
    }

    @Override
    public Optional<VillagerLike<?>> getPlayerData(UUID uuid) {
        return MCAClient.getPlayerData(uuid);
    }
}
