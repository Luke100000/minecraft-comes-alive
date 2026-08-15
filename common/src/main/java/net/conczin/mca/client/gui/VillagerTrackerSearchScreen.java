package net.conczin.mca.client.gui;

import net.conczin.mca.MCA;
import net.conczin.mca.cobalt.network.NetworkHandler;
import net.conczin.mca.network.c2s.SetTargetMessage;

import java.util.UUID;

public class VillagerTrackerSearchScreen extends FamilyTreeSearchScreen{
    @Override
    void selectVillager(String name, UUID villager) {
        NetworkHandler.sendToServer(new SetTargetMessage(MCA.locate("villager_tracker"), name, villager));
        onClose();
    }
}
