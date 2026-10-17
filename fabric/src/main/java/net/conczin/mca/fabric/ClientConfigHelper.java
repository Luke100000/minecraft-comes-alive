package net.conczin.mca.fabric;

import net.conczin.mca.fabric.config.FabricConfigBootstrap;

public final class ClientConfigHelper {
    private ClientConfigHelper() {
    }

    public static void register() {
        FabricConfigBootstrap.initializeClient();
    }
}
