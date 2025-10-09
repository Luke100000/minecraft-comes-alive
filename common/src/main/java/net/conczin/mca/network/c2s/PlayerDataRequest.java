package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.PlayerDataMessage;
import net.conczin.mca.server.world.data.PlayerSaveData;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public record PlayerDataRequest(UUID uuid) implements HandleablePayload {
    public static final CustomPacketPayload.Type<PlayerDataRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("player_data_request"));
    public static final StreamCodec<FriendlyByteBuf, PlayerDataRequest> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PlayerDataRequest::uuid,
            PlayerDataRequest::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        Player target = player.level().getPlayerByUUID(uuid);
        if (target instanceof ServerPlayer serverTarget) {
            PlayerSaveData data = PlayerSaveData.get(serverTarget);
            if (data.isEntityDataSet()) {
                CompoundTag nbt = data.getEntityData();
                Network.sendToPlayer(new PlayerDataMessage(uuid, nbt), player);
            }
        }
    }

    @Override
    public CustomPacketPayload.Type<PlayerDataRequest> type() {
        return TYPE;
    }
}
