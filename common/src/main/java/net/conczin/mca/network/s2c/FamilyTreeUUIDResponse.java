package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.client.gui.FamilyTreeSearchScreen;
import net.conczin.mca.cobalt.network.Message;

import java.io.Serial;
import java.util.List;

public class FamilyTreeUUIDResponse implements Message {
    @Serial
    private static final long serialVersionUID = 8216277949975695897L;

    private final List<FamilyTreeSearchScreen.Entry> list;

    public FamilyTreeUUIDResponse(List<FamilyTreeSearchScreen.Entry> list) {
        this.list = list;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleFamilyTreeUUIDResponse(this);
    }

    public List<FamilyTreeSearchScreen.Entry> getList() {
        return list;
    }
}
