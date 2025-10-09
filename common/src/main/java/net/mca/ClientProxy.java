package net.mca;

import net.mca.network.ClientInteractionManager;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Workaround for Forge's BS
 */
public class ClientProxy {

    private static Impl INSTANCE = new Impl();

    @Nullable
    public static Player getClientPlayer() {
        return INSTANCE.getClientPlayer();
    }

    public static ClientInteractionManager getNetworkHandler() {
        return INSTANCE.getNetworkHandler();
    }

    public static class Impl {
        protected Impl() {
            INSTANCE = this;
        }

        public Player getClientPlayer() {
            return null;
        }

        public ClientInteractionManager getNetworkHandler() {
            return null;
        }
    }
}
