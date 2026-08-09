package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.CommonConfig;
import net.mca.cobalt.network.Message;

import java.io.Serial;
import java.util.List;

public class ConfigResponse implements Message {
    @Serial
    private static final long serialVersionUID = -559319583580183137L;

    private final CommonConfig config;

    public ConfigResponse(CommonConfig config) {
        this.config = new CommonConfig(config);
    }

    public ConfigResponse(CommonConfig config, List<String> destinySpawnLocations) {
        this(config);
        this.config.destinySpawnLocations = List.copyOf(destinySpawnLocations);
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleConfigResponse(this);
    }

    public CommonConfig getConfig() {
        return config;
    }
}
