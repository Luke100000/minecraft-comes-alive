package net.conczin.mca.fabric.config;

import fuzs.forgeconfigapiport.fabric.api.neoforge.v4.client.ConfigScreenFactoryRegistry;
import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.ConfigScreenSearch;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;

/** Referenced only from the Fabric client entrypoint when FCP is present. */
public final class FabricFcpConfigClient {
    private FabricFcpConfigClient() {}

    public static void initialize() {
        FabricFcpConfigIntegration.initializeClientConfig();
        ConfigScreenFactoryRegistry.INSTANCE.register(MCA.MOD_ID, (modId, parent) ->
                ConfigScreenSearch.createRoot(parent,
                        new ConfigurationScreen(modId, parent, ConfigScreenSearch::createSection), modId));
    }
}
