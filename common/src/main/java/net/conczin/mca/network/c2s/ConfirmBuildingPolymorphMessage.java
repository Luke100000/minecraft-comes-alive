package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.BuildingPolymorphMessage;
import net.conczin.mca.server.world.data.Building;
import net.conczin.mca.server.world.data.BuildingScanResult;
import net.conczin.mca.server.world.data.VillageManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;

public record ConfirmBuildingPolymorphMessage(BlockPos source, boolean strictScan, String chosenType) implements HandleablePayload {
    public static final CustomPacketPayload.Type<ConfirmBuildingPolymorphMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("confirm_building_polymorph"));

    private static final StreamCodec<FriendlyByteBuf, BlockPos> BLOCK_POS_CODEC = StreamCodec.of(
            (buf, pos) -> buf.writeBlockPos(pos), buf -> buf.readBlockPos()
    );

    public static final StreamCodec<FriendlyByteBuf, ConfirmBuildingPolymorphMessage> STREAM_CODEC = StreamCodec.composite(
            BLOCK_POS_CODEC, ConfirmBuildingPolymorphMessage::source,
            ByteBufCodecs.BOOL, ConfirmBuildingPolymorphMessage::strictScan,
            ByteBufCodecs.STRING_UTF8, ConfirmBuildingPolymorphMessage::chosenType,
            ConfirmBuildingPolymorphMessage::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        VillageManager villages = VillageManager.get(player.serverLevel());
        BuildingScanResult scan = strictScan
                ? villages.analyzeRoom(source)
                : villages.analyzeBuilding(source, false);
        Building.validationResult result = villages.commitBuilding(scan, chosenType);
        if (result != Building.validationResult.SUCCESS) {
            displayScanResult(player, result);
            return;
        }

        if (strictScan) {
            player.displayClientMessage(Component.translatable(
                    scan.hasExistingBuilding() ? "blueprint.roomAlreadyAdded" : "blueprint.roomAdded"), true);
            return;
        }

        BuildingScanResult roomScan = villages.analyzeRoom(player.blockPosition());
        if (roomScan.result() == Building.validationResult.SUCCESS && roomScan.isAmbiguous()) {
            Network.sendToPlayer(new BuildingPolymorphMessage(
                    roomScan.matchingTypes(), roomScan.source(), true), player);
            player.displayClientMessage(Component.translatable("blueprint.buildingAddedChooseRoomType"), true);
            return;
        }

        Building.validationResult roomResult = villages.commitBuilding(roomScan, null);
        if (roomResult == Building.validationResult.SUCCESS) {
            player.displayClientMessage(Component.translatable(
                    roomScan.hasExistingBuilding() ? "blueprint.buildingUpdatedRoomExists" : "blueprint.buildingAddedRoomAdded"), true);
        } else {
            player.displayClientMessage(Component.translatable(
                    "blueprint.buildingAddedRoomFailed",
                    Component.translatable("blueprint.scan." + roomResult.name().toLowerCase(Locale.ENGLISH))), true);
        }
    }

    private static void displayScanResult(ServerPlayer player, Building.validationResult result) {
        player.displayClientMessage(Component.translatable(
                "blueprint.scan." + result.name().toLowerCase(Locale.ENGLISH)), true);
    }

    @Override
    public CustomPacketPayload.Type<ConfirmBuildingPolymorphMessage> type() {
        return TYPE;
    }
}
