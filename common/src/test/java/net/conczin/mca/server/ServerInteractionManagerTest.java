package net.conczin.mca.server;

import net.conczin.mca.Config;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerInteractionManagerTest {
    @Test
    void playerDimensionRefreshFollowsServerHitboxScalingConfig() {
        boolean previous = Config.getServerConfig().scalePlayerHitboxWithSizeAndWidth;
        try {
            Config.getServerConfig().scalePlayerHitboxWithSizeAndWidth = false;
            assertFalse(ServerInteractionManager.shouldRefreshPlayerDimensions());

            Config.getServerConfig().scalePlayerHitboxWithSizeAndWidth = true;
            assertTrue(ServerInteractionManager.shouldRefreshPlayerDimensions());
        } finally {
            Config.getServerConfig().scalePlayerHitboxWithSizeAndWidth = previous;
        }
    }
}
