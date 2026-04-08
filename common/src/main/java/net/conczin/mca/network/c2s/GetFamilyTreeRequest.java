package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.GetFamilyTreeResponse;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record GetFamilyTreeRequest(UUID uuid) implements HandleablePayload {
    public static final CustomPacketPayload.Type<GetFamilyTreeRequest> TYPE = new CustomPacketPayload.Type<>(MCA.locate("get_family_tree_request"));
    public static final StreamCodec<FriendlyByteBuf, GetFamilyTreeRequest> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, GetFamilyTreeRequest::uuid,
            GetFamilyTreeRequest::new
    );

    @Override
    public void handleServer(ServerPlayer player) {
        FamilyTree.get(player.level()).getOrEmpty(uuid).ifPresent(entry -> {
            Map<UUID, FamilyTreeNode> familyEntries = Stream.concat(
                            entry.lookup(Stream.of(entry.id(), entry.partner())),
                            entry.lookup(entry.getRelatives(2, 1))
                    ).distinct()
                    .collect(Collectors.toMap(FamilyTreeNode::id, Function.identity()));

            Network.sendToPlayer(new GetFamilyTreeResponse(uuid, familyEntries), player);
        });
    }

    @Override
    public CustomPacketPayload.Type<GetFamilyTreeRequest> type() {
        return TYPE;
    }
}
