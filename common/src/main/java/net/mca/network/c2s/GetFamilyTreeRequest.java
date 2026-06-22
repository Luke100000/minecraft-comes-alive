package net.mca.network.c2s;

import net.mca.cobalt.network.Message;
import net.mca.cobalt.network.NetworkHandler;
import net.mca.network.s2c.GetFamilyTreeResponse;
import net.mca.server.world.data.FamilyTree;
import net.mca.server.world.data.FamilyTreeNode;
import net.minecraft.server.network.ServerPlayerEntity;

import java.io.Serial;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class GetFamilyTreeRequest implements Message {
    @Serial
    private static final long serialVersionUID = -6232925305386763715L;

    final UUID uuid;

    public GetFamilyTreeRequest(UUID uuid) {
        this.uuid = uuid;
    }

    @Override
    public void receive(ServerPlayerEntity player) {
        FamilyTree.get(player.getServerWorld()).getOrEmpty(uuid).ifPresent(entry -> {
            Map<UUID, FamilyTreeNode> familyEntries = Stream.concat(
                            entry.lookup(Stream.of(entry.id(), entry.partner())),
                            entry.lookup(entry.getRelatives(2, 1))
                    ).distinct()
                    .collect(Collectors.toMap(FamilyTreeNode::id, Function.identity()));

            NetworkHandler.sendToPlayer(new GetFamilyTreeResponse(uuid, familyEntries), player);
        });
    }
}
