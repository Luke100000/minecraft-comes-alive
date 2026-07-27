package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record ConfirmBuildingPolymorphMessage(BlockPos source,
                                              ReportBuildingMessage.Action action,
                                              int expectedRoomId,
                                              String chosenType) implements HandleablePayload {
    public static final CustomPacketPayload.Type<ConfirmBuildingPolymorphMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("confirm_building_polymorph"));

    public static final StreamCodec<FriendlyByteBuf, ConfirmBuildingPolymorphMessage> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfirmBuildingPolymorphMessage::source,
            ByteBufCodecs.idMapper(i -> i >= 0 && i < ReportBuildingMessage.Action.VALUES.length ? ReportBuildingMessage.Action.VALUES[i] : ReportBuildingMessage.Action.AUTO_SCAN, ReportBuildingMessage.Action::ordinal), ConfirmBuildingPolymorphMessage::action,
            ByteBufCodecs.VAR_INT, ConfirmBuildingPolymorphMessage::expectedRoomId,
            ByteBufCodecs.STRING_UTF8, ConfirmBuildingPolymorphMessage::chosenType,
            ConfirmBuildingPolymorphMessage::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        try {
            VillageManager villages = VillageManager.get(player.serverLevel());
            switch (action) {
                case ADD_ROOM -> ReportBuildingMessage.addRoom(
                        villages, player, source, chosenType);
                case UPDATE_ROOM -> ReportBuildingMessage.updateRoom(
                        villages, player, source, chosenType, expectedRoomId);
                default -> MCA.LOGGER.warn("Ignoring invalid building polymorph action {} from {}", action, player);
            }
        } finally {
            GetVillageRequest.sendResponse(player);
        }
    }

    @Override
    public CustomPacketPayload.Type<ConfirmBuildingPolymorphMessage> type() {
        return TYPE;
    }
}
