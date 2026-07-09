package net.mca;

import net.mca.entity.VillagerLike;
import net.mca.network.ClientInteractionManager;
import net.mca.network.ClientInteractionManagerImpl;

import java.util.Optional;
import java.util.UUID;

/**
 * Workaround for Forge's BS
 */
public abstract class ClientProxyAbstractImpl extends ClientProxy.Impl {

    private final ClientInteractionManager networkHandler = new ClientInteractionManagerImpl();

    @Override
    public final ClientInteractionManager getNetworkHandler() {
        return networkHandler;
    }

    @Override
    public Optional<VillagerLike<?>> getPlayerData(UUID uuid) {
        return MCAClient.getPlayerData(uuid);
    }
}
