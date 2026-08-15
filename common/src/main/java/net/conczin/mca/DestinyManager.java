package net.conczin.mca;

import net.conczin.mca.client.gui.DestinyScreen;
import net.minecraft.client.Minecraft;

public class DestinyManager {
    private boolean openDestiny;
    private boolean allowTeleportation;

    public void tick(Minecraft client) {
        if (openDestiny && client.screen == null) {
            assert client.player != null;
            client.setScreen(new DestinyScreen(client.player.getUUID(), allowTeleportation));
        }
    }

    public void requestOpen(boolean allowTeleportation) {
        this.openDestiny = true;
        this.allowTeleportation = allowTeleportation;
    }

    public void allowClosing() {
        this.openDestiny = false;
    }
}
