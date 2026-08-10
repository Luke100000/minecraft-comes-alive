package net.mca.network.s2c;

import net.mca.ClientProxy;
import net.mca.cobalt.network.Message;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import java.io.Serial;
import java.util.UUID;

public class VillagerMessage implements Message {
    @Serial
    private static final long serialVersionUID = -4135222437610000843L;

    private final String prefix;
    private final String message;
    private final UUID uuid;

    public VillagerMessage(MutableComponent prefix, MutableComponent message, UUID uuid) {
        this.prefix = Component.Serializer.toJson(prefix);
        this.message = Component.Serializer.toJson(message);
        this.uuid = uuid;
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleVillagerMessage(this);
    }

    public MutableComponent safeLoadFromJson(String json) {
        MutableComponent mutableText = Component.Serializer.fromJson(json);
        if (mutableText == null) return Component.literal("");
        return mutableText;
    }

    public MutableComponent getMessage() {
        return safeLoadFromJson(prefix).append(safeLoadFromJson(message));
    }

    public MutableComponent getContent() {
        return safeLoadFromJson(message);
    }

    public UUID getUuid() {
        return uuid;
    }
}
