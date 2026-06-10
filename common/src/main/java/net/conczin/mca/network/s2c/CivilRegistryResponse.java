package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public record CivilRegistryResponse(int index, List<Component> lines) implements HandleablePayload {
    public static final CustomPacketPayload.Type<CivilRegistryResponse> TYPE = new CustomPacketPayload.Type<>(MCA.locate("civil_registry_response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CivilRegistryResponse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CivilRegistryResponse::index,
            ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list(64)), CivilRegistryResponse::lines,
            CivilRegistryResponse::new
    );

    public static CivilRegistryResponse fromComponents(int index, List<Component> components) {
        return new CivilRegistryResponse(index, List.copyOf(components));
    }

    public int getIndex() {
        return index;
    }

    public List<Component> getLines() {
        return lines;
    }

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleCivilRegistryResponse(this);
    }

    @Override
    public CustomPacketPayload.Type<CivilRegistryResponse> type() {
        return TYPE;
    }
}
