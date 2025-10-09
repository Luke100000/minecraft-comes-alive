package net.conczin.mca.network.s2c;

import com.google.gson.GsonBuilder;
import net.conczin.mca.ClientProxy;
import net.conczin.mca.Config;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

public record ConfigResponse(String json) implements HandleablePayload {
    public static final CustomPacketPayload.Type<ConfigResponse> TYPE = new CustomPacketPayload.Type<>(MCA.locate("config_response"));
    public static final StreamCodec<FriendlyByteBuf, ConfigResponse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, ConfigResponse::json,
            ConfigResponse::new
    );

    public ConfigResponse(Config config) {
        this(new GsonBuilder().setPrettyPrinting().create().toJson(config));
    }

    public Config getConfig() {
        try {
            return new GsonBuilder().setPrettyPrinting().create().fromJson(json, Config.class);
        } catch (Exception e) {
            // Fallback to client local instance on parse errors
            return Config.getInstance();
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
