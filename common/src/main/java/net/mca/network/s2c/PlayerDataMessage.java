package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.network.NbtDataMessage;
import net.minecraft.nbt.CompoundTag;
import java.io.Serial;
import java.util.UUID;

public class PlayerDataMessage extends NbtDataMessage {
    @Serial
    private static final long serialVersionUID = 145267688456022788L;

    public final UUID uuid;

    public PlayerDataMessage(UUID uuid, CompoundTag nbt) {
        super(nbt.copy());
        this.uuid = uuid;
    }

    @Override
    public CompoundTag getData() {
        return super.getData().copy();
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handlePlayerDataMessage(this);
    }
}
