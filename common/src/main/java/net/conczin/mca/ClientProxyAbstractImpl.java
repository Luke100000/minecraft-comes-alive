package net.conczin.mca;

import net.conczin.mca.client.model.CommonVillagerModel;
import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.network.ClientHandler;
import net.conczin.mca.network.ClientHandlerImpl;
import net.minecraft.world.entity.Entity;

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

    @Override
    public Optional<Float> getGeneticsPassengerScale(Entity vehicle) {
        if (vehicle != null && MCAClient.useGeneticsRenderer(vehicle.getUUID())) {
            return Optional.of(CommonVillagerModel.getVillager(vehicle).getVisualVerticalScaleFactor());
        }
        return Optional.empty();
    }

    @Override
    public boolean useExpandedPersonalityTranslations() {
        return MCAClient.useExpandedPersonalityTranslations();
    }
}
