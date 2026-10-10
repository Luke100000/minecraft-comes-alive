package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.FamilyTreeSearchEntry;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.FamilyTreeUUIDResponse;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public record FamilyTreeUUIDLookup(long requestId, String search) implements HandleablePayload {
    public static final CustomPacketPayload.Type<FamilyTreeUUIDLookup> TYPE = new CustomPacketPayload.Type<>(MCA.locate("family_tree_uuid_lookup"));
    public static final StreamCodec<FriendlyByteBuf, FamilyTreeUUIDLookup> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, FamilyTreeUUIDLookup::requestId,
            ByteBufCodecs.STRING_UTF8, FamilyTreeUUIDLookup::search,
            FamilyTreeUUIDLookup::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        FamilyTree tree = FamilyTree.get(player.serverLevel());
        List<FamilyTreeSearchEntry> list = tree.getAllWithNameContaining(search, 16).stream()
                .map(entry -> new FamilyTreeSearchEntry(
                        entry.id(),
                        entry.getName(),
                        FamilyTreeNode.isValid(entry.father()),
                        tree.getOrEmpty(entry.father()).map(FamilyTreeNode::getName).orElse(""),
                        FamilyTreeNode.isValid(entry.mother()),
                        tree.getOrEmpty(entry.mother()).map(FamilyTreeNode::getName).orElse("")))
                .toList();
        Network.sendToPlayer(new FamilyTreeUUIDResponse(requestId, search, list), player);
    }

    @Override
    public CustomPacketPayload.Type<FamilyTreeUUIDLookup> type() {
        return TYPE;
    }
}
