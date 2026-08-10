package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.network.NbtDataMessage;
import net.minecraft.nbt.CompoundTag;
import java.io.Serial;

public class GetFamilyResponse extends NbtDataMessage {
    @Serial
    private static final long serialVersionUID = -8537919427646877115L;

    public GetFamilyResponse(CompoundTag data) {
        super(data);
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleFamilyDataResponse(this);
    }
}
