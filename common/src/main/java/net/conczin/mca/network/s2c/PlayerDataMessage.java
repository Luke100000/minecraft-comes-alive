package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

public record PlayerDataMessage(UUID uuid, CompoundTag nbt) implements HandleablePayload {
    public static final CustomPacketPayload.Type<PlayerDataMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("player_data"));
    public static final StreamCodec<FriendlyByteBuf, PlayerDataMessage> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, PlayerDataMessage::uuid,
            ByteBufCodecs.COMPOUND_TAG, PlayerDataMessage::nbt,
            PlayerDataMessage::new
    );

    public PlayerDataMessage {
        nbt = nbt.copy();
    }

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handlePlayerDataMessage(this);
    }

    @Override
    public CustomPacketPayload.Type<PlayerDataMessage> type() {
        return TYPE;
    }
}
