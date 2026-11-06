package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.world.data.Village;
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
    static final int MAX_NAME_LENGTH = 32;

    @Override
    public void handleServer(ServerPlayer player) {
        Village.findNearest(player).ifPresent(this::applyTo);
    }

    public void applyTo(Village village) {
        if (village == null || village.getId() != id || name == null || name.isBlank() || name.length() > MAX_NAME_LENGTH) {
            return;
        }
        village.setName(name);
    }

    @Override
    public CustomPacketPayload.Type<RenameVillageMessage> type() {
        return TYPE;
    }
}
