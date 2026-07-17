package net.conczin.mca.network.s2c;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.conczin.mca.ClientProxy;
import net.conczin.mca.CommonConfig;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public record ConfigResponse(String json) implements HandleablePayload {
    public static final CustomPacketPayload.Type<ConfigResponse> TYPE = new CustomPacketPayload.Type<>(MCA.locate("config_response"));
    public static final StreamCodec<FriendlyByteBuf, ConfigResponse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ConfigResponse::json,
            ConfigResponse::new
    );

    private static final Gson GSON = new Gson();

    public ConfigResponse(Config config) {
        this(GSON.toJson(config, CommonConfig.class));
    }

    public ConfigResponse(Config config, List<String> destinySpawnLocations) {
        this(createJson(config, destinySpawnLocations));
    }

    private static String createJson(Config config, List<String> destinySpawnLocations) {
        JsonObject json = GSON.toJsonTree(config, CommonConfig.class).getAsJsonObject();
        json.add("destinySpawnLocations", GSON.toJsonTree(destinySpawnLocations));
        return GSON.toJson(json);
    }

    public CommonConfig getConfig() {
        try {
            return GSON.fromJson(json, CommonConfig.class);
        } catch (Exception e) {
            // Fallback to default values on parse errors
            return new CommonConfig();
        }
    }

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleConfigResponse(this);
    }

    @Override
    public CustomPacketPayload.Type<ConfigResponse> type() {
        return TYPE;
    }
}
