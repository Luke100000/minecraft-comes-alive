package net.conczin.mca.client.gui;

import net.conczin.mca.MCA;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.DamageItemMessage;

import java.util.UUID;

public class CombScreen extends VillagerEditorScreen {
    public CombScreen(UUID playerUUID) {
        super(playerUUID, playerUUID);
    }

    public CombScreen(UUID villagerUUID, UUID playerUUID) {
        super(villagerUUID, playerUUID);
    }

    @Override
    protected boolean shouldShowPageSelection() {
        return false;
    }

    @Override
    protected void eventCallback(String event) {
        if (event.equals("hair")) {
            Network.sendToServer(new DamageItemMessage(MCA.locate("comb")));
        }
    }

    @Override
    protected void setPage(String page) {
        if (page.equals("loading")) {
            super.setPage("loading");
        } else if (page.equals("head")) {
            syncVillagerData();
            onClose();
        } else {
            super.setPage("hair");
        }
    }
}
