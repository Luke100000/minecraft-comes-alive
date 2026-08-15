package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.cobalt.network.Message;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.stream.Collectors;

public class CivilRegistryResponse implements Message {
    private final int index;
    private final List<String> lines;

    public CivilRegistryResponse(int index, List<Component> lines) {
        this.index = index;
        this.lines = lines.stream().map(Component.Serializer::toJson).collect(Collectors.toList());
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleCivilRegistryResponse(this);
    }

    public int getIndex() {
        return index;
    }

    public List<Component> getLines() {
        return lines.stream().map(Component.Serializer::fromJson).collect(Collectors.toList());
    }
}
