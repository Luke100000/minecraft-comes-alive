package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.resources.data.Analysis;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.player.Player;

public record AnalysisResults(Analysis analysis) implements HandleablePayload {
    public static final CustomPacketPayload.Type<AnalysisResults> TYPE = new CustomPacketPayload.Type<>(MCA.locate("analysis_results"));
    public static final StreamCodec<FriendlyByteBuf, AnalysisResults> STREAM_CODEC = StreamCodec.composite(
            Analysis.STREAM_CODEC, AnalysisResults::analysis,
            AnalysisResults::new
    );

    @Override
    public void handle(Player player) {
        ClientProxy.getNetworkHandler().handleSkinListResponse(this);
    }

    @Override
    public Type<AnalysisResults> type() {
        return TYPE;
    }
}
