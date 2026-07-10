package net.mca.client.gui;

import net.mca.MCA;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.c2s.DamageItemMessage;
import net.mca.resources.data.skin.LayeredHair;
import net.minecraft.text.Text;
import net.mca.util.compat.ButtonWidget;

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
        if (event.startsWith("hair")) {
            NetworkHandler.sendToServer(new DamageItemMessage(MCA.locate("comb")));
        }
    }

    @Override
    protected void setPage(String page) {
        if (page.equals("loading")) {
            super.setPage("loading");
        } else if (page.equals("hair_style") || isDoneFromLayeredHair(page)) {
            syncVillagerData();
            close();
        } else if (page.equals("hair_advanced") || page.equals("hair") || isLayeredHairDestination(page)) {
            super.setPage(page);
        } else {
            super.setPage("hair");
        }
    }

    @Override
    protected void rebuildCurrentPageFromData() {
        super.setPage(page);
    }

    private boolean isDoneFromLayeredHair(String dest) {
        return dest.equals("hair_advanced") && page != null && isLayeredHairDestination(page);
    }

    private boolean isLayeredHairDestination(String dest) {
        if (!dest.startsWith("hair_")) return false;
        return LayeredHair.Category.byNameOrNull(dest.substring("hair_".length())) != null;
    }
}
