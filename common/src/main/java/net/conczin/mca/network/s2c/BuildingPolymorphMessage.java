package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.c2s.ReportBuildingMessage;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public record BuildingPolymorphMessage(List<String> matchingTypes,
                                       BlockPos scanPos,
                                       ReportBuildingMessage.Action action,
                                       int expectedRoomId) implements HandleablePayload {
    public static final CustomPacketPayload.Type<BuildingPolymorphMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("building_polymorph"));

    public static final StreamCodec<FriendlyByteBuf, BuildingPolymorphMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), BuildingPolymorphMessage::matchingTypes,
            BlockPos.STREAM_CODEC, BuildingPolymorphMessage::scanPos,
            ByteBufCodecs.idMapper(i -> i >= 0 && i < ReportBuildingMessage.Action.VALUES.length ? ReportBuildingMessage.Action.VALUES[i] : ReportBuildingMessage.Action.AUTO_SCAN, ReportBuildingMessage.Action::ordinal), BuildingPolymorphMessage::action,
            ByteBufCodecs.VAR_INT, BuildingPolymorphMessage::expectedRoomId,
            BuildingPolymorphMessage::new
    );

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleBuildingPolymorph(this);
    }

    @Override
    public CustomPacketPayload.Type<BuildingPolymorphMessage> type() {
        return TYPE;
    }
}
