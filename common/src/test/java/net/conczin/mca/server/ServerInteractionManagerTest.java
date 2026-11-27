package net.conczin.mca.server;

import net.conczin.mca.CommonConfig;
import net.conczin.mca.Config;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerInteractionManagerTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void playerDimensionRefreshFollowsLocalServerConfigDespiteRemoteSnapshot(boolean localScaling) throws ReflectiveOperationException {
        Config local = Config.getInstance();
        boolean previousScaling = local.scalePlayerHitboxWithSizeAndWidth;
        // Read the nullable snapshot itself: the public getter falls back to local config.
        Field snapshotField = Config.class.getDeclaredField("serverConfig");
        snapshotField.setAccessible(true);
        CommonConfig previousSnapshot = (CommonConfig) snapshotField.get(null);
        CommonConfig remoteSnapshot = new CommonConfig();
        remoteSnapshot.scalePlayerHitboxWithSizeAndWidth = !localScaling;
        try {
            local.scalePlayerHitboxWithSizeAndWidth = localScaling;
            Config.setServerConfig(remoteSnapshot);

            assertEquals(localScaling, ServerInteractionManager.shouldRefreshPlayerDimensions(),
                    "server dimension refresh must follow local config, not the remote snapshot");
        } finally {
            local.scalePlayerHitboxWithSizeAndWidth = previousScaling;
            Config.setServerConfig(previousSnapshot);
        }
    }
}
