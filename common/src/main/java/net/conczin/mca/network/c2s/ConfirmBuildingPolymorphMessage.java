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
                                              int expectedTargetId,
                                              String chosenType) implements HandleablePayload {
    public static final CustomPacketPayload.Type<ConfirmBuildingPolymorphMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("confirm_building_polymorph"));

    public static final StreamCodec<FriendlyByteBuf, ConfirmBuildingPolymorphMessage> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfirmBuildingPolymorphMessage::source,
            ByteBufCodecs.idMapper(i -> ReportBuildingMessage.Action.VALUES[i], ReportBuildingMessage.Action::ordinal), ConfirmBuildingPolymorphMessage::action,
            ByteBufCodecs.VAR_INT, ConfirmBuildingPolymorphMessage::expectedTargetId,
            ByteBufCodecs.STRING_UTF8, ConfirmBuildingPolymorphMessage::chosenType,
            ConfirmBuildingPolymorphMessage::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        try {
            ReportBuildingMessage.executeScanAction(
                    VillageManager.get(player.serverLevel()), player, source,
                    chosenType, action, expectedTargetId);
        } finally {
            GetVillageRequest.sendResponse(player);
        }
    }

    @Override
    public CustomPacketPayload.Type<ConfirmBuildingPolymorphMessage> type() {
        return TYPE;
    }
}
