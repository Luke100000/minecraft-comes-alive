package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record RenameVillageMessage(int id, String name) implements HandleablePayload {
    public static final CustomPacketPayload.Type<RenameVillageMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("rename_village"));
    public static final StreamCodec<FriendlyByteBuf, RenameVillageMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, RenameVillageMessage::id,
            ByteBufCodecs.STRING_UTF8, RenameVillageMessage::name,
            RenameVillageMessage::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        VillageManager.get(player.serverLevel()).getOrEmpty(id).ifPresent(v -> v.setName(name));
    }

    @Override
    public CustomPacketPayload.Type<RenameVillageMessage> type() {
        return TYPE;
    }
}
