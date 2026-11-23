package net.conczin.mca;

import net.conczin.mca.entity.VillagerLike;
import net.conczin.mca.network.ClientHandler;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public class ClientProxy {
    private static Impl INSTANCE = new Impl();

    @Nullable
    public static Player getClientPlayer() {
        return INSTANCE.getClientPlayer();
    }

    public static ClientHandler getNetworkHandler() {
        return INSTANCE.getNetworkHandler();
    }

    public static Optional<VillagerLike<?>> getPlayerData(UUID uuid) {
        return INSTANCE.getPlayerData(uuid);
    }

    public static Optional<Float> getGeneticsPassengerScale(Entity vehicle) {
        return INSTANCE.getGeneticsPassengerScale(vehicle);
    }

    public static boolean useExpandedPersonalityTranslations() {
        return INSTANCE.useExpandedPersonalityTranslations();
    }

    public static class Impl {
        protected Impl() {
            INSTANCE = this;
        }

        public Player getClientPlayer() {
            return null;
        }

        public ClientHandler getNetworkHandler() {
            return null;
        }

        public Optional<VillagerLike<?>> getPlayerData(UUID uuid) {
            return Optional.empty();
        }

        public Optional<Float> getGeneticsPassengerScale(Entity vehicle) {
            return Optional.empty();
        }

        public boolean useExpandedPersonalityTranslations() {
            return false;
        }
    }
}
