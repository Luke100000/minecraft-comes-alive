package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.List;

public record BuildingPolymorphMessage(List<String> matchingTypes, BlockPos scanPos, boolean isRoom) implements HandleablePayload {
    public static final CustomPacketPayload.Type<BuildingPolymorphMessage> TYPE = new CustomPacketPayload.Type<>(MCA.locate("building_polymorph"));

    private static final StreamCodec<FriendlyByteBuf, BlockPos> BLOCK_POS_CODEC = StreamCodec.of(
            (buf, pos) -> buf.writeBlockPos(pos), buf -> buf.readBlockPos()
    );

    public static final StreamCodec<FriendlyByteBuf, BuildingPolymorphMessage> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8), BuildingPolymorphMessage::matchingTypes,
            BLOCK_POS_CODEC, BuildingPolymorphMessage::scanPos,
            ByteBufCodecs.BOOL, BuildingPolymorphMessage::isRoom,
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
