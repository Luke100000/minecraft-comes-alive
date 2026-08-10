package net.mca.network.c2s;

import net.mca.client.gui.FamilyTreeSearchScreen;
import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.s2c.FamilyTreeUUIDResponse;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.minecraft.server.level.ServerPlayer;
import java.io.Serial;
import java.util.List;
import java.util.stream.Collectors;

public class FamilyTreeUUIDLookup implements Message {
    @Serial
    private static final long serialVersionUID = 3458196476082270702L;

    private final String search;

    public FamilyTreeUUIDLookup(String search) {
        this.search = search;
    }

    @Override
    public void receive(ServerPlayer player) {
        FamilyTree tree = FamilyTree.get(player.serverLevel());
        List<FamilyTreeSearchScreen.Entry> list = tree.getAllWithName(search)
                .map(entry -> new FamilyTreeSearchScreen.Entry(
                        entry.id(),
                        entry.getName(),
                        tree.getOrEmpty(entry.father()).map(FamilyTreeNode::getName).orElse(""),
                        tree.getOrEmpty(entry.mother()).map(FamilyTreeNode::getName).orElse("")))
                .limit(16)
                .collect(Collectors.toList());
        NetworkHandler.sendToPlayer(new FamilyTreeUUIDResponse(list), player);
    }
}
