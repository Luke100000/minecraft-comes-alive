package net.mca.client.gui;

import net.mca.MCA;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.c2s.DamageItemMessage;

import java.util.UUID;

public class NeedleScreen extends VillagerEditorScreen {
    public NeedleScreen(UUID playerUUID) {
        super(playerUUID, playerUUID);
    }

    public NeedleScreen(UUID villagerUUID, UUID playerUUID) {
        super(villagerUUID, playerUUID);
    }

    @Override
    protected boolean shouldShowPageSelection() {
        return false;
    }

    @Override
    protected void eventCallback(String event) {
        if (event.equals("clothing")) {
            NetworkHandler.sendToServer(new DamageItemMessage(MCA.locate("needle_and_thread")));
        }
    }

    @Override
    protected void setPage(String page) {
        if (page.equals("loading")) {
            super.setPage("loading");
        } else if (page.equals("body") || page.equals("clothing_style")) {
            syncVillagerData();
            close();
        } else {
            super.setPage("clothing");
        }
    }
    @Override
    protected void rebuildCurrentPageFromData() {
        super.setPage(page);
    }
}
