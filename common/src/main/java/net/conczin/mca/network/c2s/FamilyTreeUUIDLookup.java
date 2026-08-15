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
import java.util.stream.Collectors;

public record FamilyTreeUUIDLookup(String search) implements HandleablePayload {
    public static final CustomPacketPayload.Type<FamilyTreeUUIDLookup> TYPE = new CustomPacketPayload.Type<>(MCA.locate("family_tree_uuid_lookup"));
    public static final StreamCodec<FriendlyByteBuf, FamilyTreeUUIDLookup> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, FamilyTreeUUIDLookup::search,
            FamilyTreeUUIDLookup::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        FamilyTree tree = FamilyTree.get(player.level());
        List<FamilyTreeSearchEntry> list = tree.getAllWithNameContaining(search)
                .map(entry -> new FamilyTreeSearchEntry(
                        entry.id(),
                        entry.getName(),
                        tree.getOrEmpty(entry.father()).map(FamilyTreeNode::getName).orElse(""),
                        tree.getOrEmpty(entry.mother()).map(FamilyTreeNode::getName).orElse("")))
                .limit(16)
                .collect(Collectors.toList());
        Network.sendToPlayer(new FamilyTreeUUIDResponse(list), player);
    }

    @Override
    public CustomPacketPayload.Type<FamilyTreeUUIDLookup> type() {
        return TYPE;
    }
}
