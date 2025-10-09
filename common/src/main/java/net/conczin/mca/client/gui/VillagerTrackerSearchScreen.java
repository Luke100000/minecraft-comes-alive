package net.conczin.mca.client.gui;

import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.SetTargetMessage;

import java.util.UUID;

public class VillagerTrackerSearchScreen extends FamilyTreeSearchScreen {
    @Override
    void selectVillager(String name, UUID villager) {
        Network.sendToServer(new SetTargetMessage(name, villager));
    }
}
