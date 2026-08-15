package net.conczin.mca.network.s2c;

import net.conczin.mca.ClientProxy;
import net.conczin.mca.cobalt.network.Message;
import net.minecraft.world.entity.Entity;
import java.io.Serial;
import java.util.UUID;

public class OpenGuiRequest implements Message {
    @Serial
    private static final long serialVersionUID = -2371116419166251497L;

    public final int gui;

    public final int villager;

    public final UUID villagerUuid;

    public OpenGuiRequest(OpenGuiRequest.Type gui, Entity villager) {
        this(gui, villager.getId(), villager.getUUID());
    }

    public OpenGuiRequest(OpenGuiRequest.Type gui, int villager) {
        this(gui, villager, null);
    }

    private OpenGuiRequest(OpenGuiRequest.Type gui, int villager, UUID villagerUuid) {
        this.gui = gui.ordinal();
        this.villager = villager;
        this.villagerUuid = villagerUuid;
    }

    public OpenGuiRequest(OpenGuiRequest.Type gui) {
        this(gui, 0, null);
    }

    @Override
    public void receive() {
        ClientProxy.getNetworkHandler().handleGuiRequest(this);
    }

    public Type getGui() {
        return Type.values()[gui];
    }

    public enum Type {
        BABY_NAME,
        WHISTLE,
        BLUEPRINT,
        INTERACT,
        VILLAGER_EDITOR,
        LIMITED_VILLAGER_EDITOR,
        BOOK,
        FAMILY_TREE,
        VILLAGER_TRACKER,
        NEEDLE_AND_THREAD,
        COMB,
        CLOSE,
    }
}
