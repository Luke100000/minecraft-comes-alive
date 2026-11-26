package net.conczin.mca.network.c2s;

import net.conczin.mca.MCA;
import net.conczin.mca.network.FamilyTreeSearchEntry;
import net.conczin.mca.network.HandleablePayload;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.s2c.FamilyTreeUUIDResponse;
import net.conczin.mca.server.world.data.FamilyTree;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.util.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public record FamilyTreeUUIDLookup(long requestId, String search) implements HandleablePayload {
    public static final int MAX_SEARCH_LENGTH = 64;
    public static final long RATE_LIMIT_MS = 100L;
    private static final int MAX_TRACKED_PLAYERS = 512;
    private static final Map<UUID, Long> LAST_REQUEST_MS = new ConcurrentHashMap<>();

    public static final CustomPacketPayload.Type<FamilyTreeUUIDLookup> TYPE = new CustomPacketPayload.Type<>(MCA.locate("family_tree_uuid_lookup"));
    public static final StreamCodec<FriendlyByteBuf, FamilyTreeUUIDLookup> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, FamilyTreeUUIDLookup::requestId,
            ByteBufCodecs.stringUtf8(MAX_SEARCH_LENGTH), FamilyTreeUUIDLookup::search,
            FamilyTreeUUIDLookup::new
    );

    public static boolean allowRequest(UUID playerId, long nowMs) {
        if (LAST_REQUEST_MS.size() > MAX_TRACKED_PLAYERS) {
            LAST_REQUEST_MS.entrySet().removeIf(entry -> nowMs - entry.getValue() >= RATE_LIMIT_MS);
        }
        boolean[] allowed = {false};
        LAST_REQUEST_MS.compute(playerId, (id, previous) -> {
            if (previous == null || nowMs - previous >= RATE_LIMIT_MS) {
                allowed[0] = true;
                return nowMs;
            }
            return previous;
        });
        return allowed[0];
    }

    @Override
    public void handleServer(ServerPlayer player) {
        if (!allowRequest(player.getUUID(), Util.getMillis())) {
            return;
        }
        FamilyTree tree = FamilyTree.get(player.level());
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
