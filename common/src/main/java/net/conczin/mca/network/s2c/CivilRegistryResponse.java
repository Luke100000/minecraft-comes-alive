package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public record CivilRegistryResponse(int index, List<String> lines) implements HandleablePayload {
    public static final CustomPacketPayload.Type<CivilRegistryResponse> TYPE = new CustomPacketPayload.Type<>(MCA.locate("civil_registry_response"));
    public static final StreamCodec<FriendlyByteBuf, CivilRegistryResponse> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, CivilRegistryResponse::index,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), CivilRegistryResponse::lines,
            CivilRegistryResponse::new
    );

    public static CivilRegistryResponse fromComponents(int index, List<Component> components) {
        return new CivilRegistryResponse(index, components.stream().map(Component::getString).toList());
    }

    public int getIndex() {
        return index;
    }

    public List<Component> getLines() {
        List<Component> out = new ArrayList<>(lines.size());
        for (String s : lines) {
            out.add(Component.literal(s));
        }
        return out;
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
