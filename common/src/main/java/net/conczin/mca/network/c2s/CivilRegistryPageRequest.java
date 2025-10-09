package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.CivilRegistryResponse;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public record CivilRegistryPageRequest(int index, int from, int to) implements HandleablePayload {
    public static final CustomPacketPayload.Type<CivilRegistryPageRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("civil_registry_page_request"));
    public static final StreamCodec<FriendlyByteBuf, CivilRegistryPageRequest> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, CivilRegistryPageRequest::index,
            ByteBufCodecs.INT, CivilRegistryPageRequest::from,
            ByteBufCodecs.INT, CivilRegistryPageRequest::to,
            CivilRegistryPageRequest::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        PlayerSaveData.get(player).getLastSeenVillage(VillageManager.get((ServerLevel) player.level())).flatMap(Village::getCivilRegistry).ifPresentOrElse(c -> {
            List<Component> page = c.getPage(from, to);
            Network.sendToPlayer(CivilRegistryResponse.fromComponents(index, page), player);
        }, () -> {
            Network.sendToPlayer(CivilRegistryResponse.fromComponents(index, List.of(Component.translatable("civil_registry.empty"))), player);
        });
    }

    @Override
    public CustomPacketPayload.Type<CivilRegistryPageRequest> type() {
        return TYPE;
    }
}
