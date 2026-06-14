package net.mca.client.gui;

import net.mca.MCA;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.c2s.SetTargetMessage;

import java.util.UUID;

public class VillagerTrackerSearchScreen extends FamilyTreeSearchScreen{
    @Override
    void selectVillager(String name, UUID villager) {
        NetworkHandler.sendToServer(new SetTargetMessage(MCA.locate("villager_tracker"), name, villager));
        close();
    }
}
