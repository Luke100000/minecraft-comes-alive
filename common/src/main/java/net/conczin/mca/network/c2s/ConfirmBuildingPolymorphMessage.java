package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.server.world.data.Village;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public record ConfirmBuildingPolymorphMessage(BlockPos source,
                                              ReportBuildingMessage.Action action,
                                              int expectedTargetId,
                                              String chosenType) implements HandleablePayload {
    public static final CustomPacketPayload.Type<ConfirmBuildingPolymorphMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("confirm_building_polymorph"));

    public static final StreamCodec<FriendlyByteBuf, ConfirmBuildingPolymorphMessage> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfirmBuildingPolymorphMessage::source,
            ByteBufCodecs.idMapper(i -> i >= 0 && i < ReportBuildingMessage.Action.VALUES.length ? ReportBuildingMessage.Action.VALUES[i] : ReportBuildingMessage.Action.AUTO_SCAN, ReportBuildingMessage.Action::ordinal), ConfirmBuildingPolymorphMessage::action,
            ByteBufCodecs.VAR_INT, ConfirmBuildingPolymorphMessage::expectedTargetId,
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
                case ADD_BUILDING -> ReportBuildingMessage.addBuilding(
                        villages, player, source, chosenType);
                case ADD_FLOOR -> ReportBuildingMessage.addAttachedRoom(
                        villages, player, source, chosenType,
                        Village.RoomScanMode.ADD_FLOOR, expectedTargetId);
                case ADD_BASEMENT -> ReportBuildingMessage.addAttachedRoom(
                        villages, player, source, chosenType,
                        Village.RoomScanMode.ADD_BASEMENT, expectedTargetId);
                case UPDATE_ROOM -> ReportBuildingMessage.updateRoom(
                        villages, player, source, chosenType, expectedTargetId);
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
