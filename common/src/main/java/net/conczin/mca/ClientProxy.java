package net.conczin.mca;

import net.conczin.mca.network.ClientHandler;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public class ClientProxy {
    private static Impl INSTANCE = new Impl();

    @Nullable
    public static Player getClientPlayer() {
        return INSTANCE.getClientPlayer();
    }

    public static ClientHandler getNetworkHandler() {
        return INSTANCE.getNetworkHandler();
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
    }
}
