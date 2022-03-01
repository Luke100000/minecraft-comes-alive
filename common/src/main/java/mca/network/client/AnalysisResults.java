package mca.network.client;

import java.io.Serial;
import mca.ClientProxy;
import mca.cobalt.network.Message;
import mca.resources.data.analysis.Analysis;
import net.minecraft.entity.player.PlayerEntity;

public class AnalysisResults implements Message {
    @Serial
    private static final long serialVersionUID = 2451914344295985363L;

    public final Analysis<?> analysis;

    public AnalysisResults(Analysis<?> analysis) {
        this.analysis = analysis;
    }

    @Override
    public void receive(PlayerEntity e) {
        ClientProxy.getNetworkHandler().handleAnalysisResults(this);
    }
}
