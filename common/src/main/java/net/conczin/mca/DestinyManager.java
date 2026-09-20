package net.conczin.mca;

import net.conczin.mca.client.gui.DestinyScreen;
import net.conczin.mca.destiny.DestinyDestination;
import net.minecraft.client.Minecraft;

import java.util.List;

public class DestinyManager {
    private boolean openDestiny;
    private boolean allowTeleportation;
    private List<DestinyDestination> destinations = List.of();

    public void tick(Minecraft client) {
        if (openDestiny && client.gui.screen() == null) {
            assert client.player != null;
            client.gui.setScreen(new DestinyScreen(client.player.getUUID(), allowTeleportation));
        }
    }

    public void requestOpen(boolean allowTeleportation, List<DestinyDestination> destinations) {
        this.openDestiny = true;
        this.allowTeleportation = allowTeleportation;
        this.destinations = List.copyOf(destinations);
    }

    public List<DestinyDestination> getDestinations() {
        return destinations;
    }

    public void allowClosing() {
        this.openDestiny = false;
    }
}
