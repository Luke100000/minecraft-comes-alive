package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.cobalt.network.Message;
import net.minecraft.util.math.BlockPos;

import java.io.Serial;
import java.util.List;

/**
 * Opens the building-type choice screen when a scan matches more than one visible type.
 */
public class BuildingPolymorphMessage implements Message {
    @Serial
    private static final long serialVersionUID = 3862107062560429644L;

    private final List<String> matchingTypes;
    private final long scanPos;
    private final boolean room;

    public BuildingPolymorphMessage(List<String> matchingTypes, BlockPos scanPos, boolean room) {
        this.matchingTypes = List.copyOf(matchingTypes);
        this.scanPos = scanPos.asLong();
        this.room = room;
    }

    public List<String> matchingTypes() {
        return matchingTypes;
    }

    public BlockPos scanPos() {
        return BlockPos.fromLong(scanPos);
    }

    public boolean isRoom() {
        return room;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleBuildingPolymorph(this);
    }
}
