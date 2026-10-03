package net.conczin.mca.network;

import io.netty.buffer.Unpooled;
import net.conczin.mca.FamilyTreeTestSupport;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.c2s.FamilyTreeUUIDLookup;
import net.conczin.mca.network.c2s.GetFamilyTreeRequest;
import net.conczin.mca.network.s2c.FamilyTreeUUIDResponse;
import net.conczin.mca.network.s2c.GetFamilyTreeResponse;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.UUID;

import static net.conczin.mca.network.FamilyTreeView.Direction.ANCESTORS;
import static net.conczin.mca.network.FamilyTreeView.Direction.DESCENDANTS;
import static net.conczin.mca.FamilyTreeTestSupport.uuid;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FamilyTreeViewCodecTest {
    private static final UUID ROOT = uuid(1);
    private static final UUID UNAVAILABLE = uuid(2);

    @BeforeAll
    static void bootstrapMinecraft() {
        FamilyTreeTestSupport.bootstrapMinecraft();
    }

    @Test
    void responseRoundTripsNodesContinuationsAndUnavailableRecords() {
        FamilyTreeNode rootNode = new FamilyTreeNode(
                null,
                ROOT,
                "Root",
                false,
                Gender.MALE,
                Util.NIL_UUID,
                Util.NIL_UUID
        );
        VillagerProfession farmer = BuiltInRegistries.VILLAGER_PROFESSION
                .getOrThrow(VillagerProfession.FARMER).value();
        rootNode.setProfession(farmer);
        FamilyTreeView view = new FamilyTreeView(
                Map.of(ROOT, rootNode),
                Set.of(
                        new FamilyTreeView.Continuation(ROOT, ANCESTORS),
                        new FamilyTreeView.Continuation(ROOT, DESCENDANTS)
                ),
                Set.of(UNAVAILABLE),
                Set.of(ROOT),
                Map.of()
        );
        GetFamilyTreeResponse response = new GetFamilyTreeResponse(41L, ROOT, true, view);

        GetFamilyTreeResponse decoded = roundTrip(GetFamilyTreeResponse.STREAM_CODEC, response);

        assertEquals(41L, decoded.requestId());
        assertEquals(ROOT, decoded.uuid());
        assertTrue(decoded.found());
        assertEquals(Set.of(ROOT), decoded.view().nodes().keySet());
        assertEquals(farmer, decoded.view().nodes().get(ROOT).getProfession());
        assertEquals(rootNode.save(), decoded.view().nodes().get(ROOT).save());
        assertEquals(view.continuations(), decoded.view().continuations());
        assertEquals(Set.of(UNAVAILABLE), decoded.view().unavailable());
        assertEquals(Set.of(ROOT), decoded.view().orphans());
    }

    @Test
    void missingResponseRoundTripsWithEmptyView() {
        FamilyTreeView empty = FamilyTreeView.empty();
        GetFamilyTreeResponse response = new GetFamilyTreeResponse(42L, ROOT, false, empty);

        GetFamilyTreeResponse decoded = roundTrip(GetFamilyTreeResponse.STREAM_CODEC, response);

        assertEquals(42L, decoded.requestId());
        assertEquals(ROOT, decoded.uuid());
        assertFalse(decoded.found());
        assertTrue(decoded.view().nodes().isEmpty());
        assertTrue(decoded.view().continuations().isEmpty());
        assertTrue(decoded.view().unavailable().isEmpty());
    }

    @Test
    void requestRoundTripsNonDefaultDepthAndRequestId() {
        GetFamilyTreeRequest request = new GetFamilyTreeRequest(ROOT, 3, 1, 43L);

        assertEquals(request, roundTrip(GetFamilyTreeRequest.STREAM_CODEC, request));
    }

    @Test
    void defaultRequestLoadsFourGenerationsPerDirection() {
        assertEquals(4, GetFamilyTreeRequest.DEFAULT_ANCESTOR_DEPTH);
        assertEquals(4, GetFamilyTreeRequest.DEFAULT_DESCENDANT_DEPTH);
        assertEquals(8, GetFamilyTreeRequest.MAX_DEPTH);
    }

    @Test
    void requestDecodeClampsDepthsBeforeServerHandling() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            UUIDUtil.STREAM_CODEC.encode(buffer, ROOT);
            ByteBufCodecs.VAR_INT.encode(buffer, 99);
            ByteBufCodecs.VAR_INT.encode(buffer, -7);
            ByteBufCodecs.VAR_LONG.encode(buffer, 44L);

            GetFamilyTreeRequest decoded = GetFamilyTreeRequest.STREAM_CODEC.decode(buffer);

            assertEquals(GetFamilyTreeRequest.MAX_DEPTH, decoded.ancestorDepth());
            assertEquals(0, decoded.descendantDepth());
            assertEquals(44L, decoded.requestId());
        } finally {
            buffer.release();
        }
    }

    @Test
    void searchEntryRoundTripsRecordedParentMetadata() {
        FamilyTreeSearchEntry entry = new FamilyTreeSearchEntry(
                ROOT,
                "Root",
                true,
                "Father",
                true,
                ""
        );

        FamilyTreeSearchEntry decoded = roundTripByteBuf(FamilyTreeSearchEntry.STREAM_CODEC, entry);

        assertEquals(entry, decoded);
        assertTrue(decoded.fatherRecorded());
        assertTrue(decoded.motherRecorded());
    }

    @Test
    void searchPayloadsRoundTripTheRequestIdentityThatProducedTheirResults() {
        FamilyTreeSearchEntry entry = new FamilyTreeSearchEntry(
                ROOT,
                "Root",
                false,
                "",
                false,
                ""
        );
        FamilyTreeUUIDLookup request = new FamilyTreeUUIDLookup(73L, "root");
        FamilyTreeUUIDResponse response = new FamilyTreeUUIDResponse(73L, "root", java.util.List.of(entry));

        FamilyTreeUUIDLookup decodedRequest = roundTrip(FamilyTreeUUIDLookup.STREAM_CODEC, request);
        FamilyTreeUUIDResponse decoded = roundTrip(FamilyTreeUUIDResponse.STREAM_CODEC, response);

        assertEquals(73L, decodedRequest.requestId());
        assertEquals("root", decodedRequest.search());
        assertEquals(73L, decoded.requestId());
        assertEquals("root", decoded.search());
        assertEquals(java.util.List.of(entry), decoded.list());
    }

    @Test
    void viewRejectsMoreThanTheWireNodeBudget() {
        Map<UUID, FamilyTreeNode> nodes = new LinkedHashMap<>();
        for (int index = 0; index < 257; index++) {
            UUID id = uuid(10_000 + index);
            nodes.put(id, new FamilyTreeNode(null, id, id.toString(), false, Gender.MALE, Util.NIL_UUID, Util.NIL_UUID));
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> new FamilyTreeView(nodes, Set.of(), Set.of(), Set.of(), Map.of())
        );
    }

    @Test
    void viewRejectsNodeWithMoreThanTheWireRelationshipBudget() {
        FamilyTreeNode root = new FamilyTreeNode(
                null,
                ROOT,
                "Root",
                false,
                Gender.MALE,
                Util.NIL_UUID,
                Util.NIL_UUID
        );
        for (int index = 0; index < 257; index++) {
            root.addChild(uuid(20_000 + index));
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> new FamilyTreeView(Map.of(ROOT, root), Set.of(), Set.of(), Set.of(), Map.of())
        );
    }

    @Test
    void familyTreeViewRoundTripPreservesExactGraves() {
        FamilyTreeNode rootNode = new FamilyTreeNode(
                null,
                ROOT,
                "Root",
                false,
                Gender.MALE,
                Util.NIL_UUID,
                Util.NIL_UUID
        );
        GlobalPos grave = GlobalPos.of(Level.NETHER, new BlockPos(12, 64, -31));
        FamilyTreeView view = new FamilyTreeView(
                Map.of(ROOT, rootNode),
                Set.of(),
                Set.of(),
                Set.of(),
                Map.of(ROOT, grave)
        );

        FamilyTreeView decoded = roundTrip(FamilyTreeView.STREAM_CODEC, view);

        assertEquals(Map.of(ROOT, grave), decoded.graves());
    }

    @Test
    void familyTreeViewRejectsOversizedGraveMap() {
        FamilyTreeNode rootNode = new FamilyTreeNode(
                null,
                ROOT,
                "Root",
                false,
                Gender.MALE,
                Util.NIL_UUID,
                Util.NIL_UUID
        );
        Map<UUID, GlobalPos> graves = new LinkedHashMap<>();
        for (int index = 0; index < FamilyTreeView.MAX_NODES + 1; index++) {
            graves.put(uuid(30_000 + index), GlobalPos.of(Level.OVERWORLD, new BlockPos(index, 64, 0)));
        }

        assertThrows(
                IllegalArgumentException.class,
                () -> new FamilyTreeView(Map.of(ROOT, rootNode), Set.of(), Set.of(), Set.of(), graves)
        );
    }

    @Test
    void familyTreeViewRejectsOrphanMetadataOutsideBoundedNodes() {
        FamilyTreeNode rootNode = new FamilyTreeNode(
                null,
                ROOT,
                "Root",
                false,
                Gender.MALE,
                Util.NIL_UUID,
                Util.NIL_UUID
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> new FamilyTreeView(
                        Map.of(ROOT, rootNode),
                        Set.of(),
                        Set.of(),
                        Set.of(UNAVAILABLE),
                        Map.of()
                )
        );
    }

    private static <T> T roundTrip(StreamCodec<FriendlyByteBuf, T> codec, T value) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            codec.encode(buffer, value);
            return codec.decode(buffer);
        } finally {
            buffer.release();
        }
    }

    private static <T> T roundTripByteBuf(StreamCodec<io.netty.buffer.ByteBuf, T> codec, T value) {
        io.netty.buffer.ByteBuf buffer = Unpooled.buffer();
        try {
            codec.encode(buffer, value);
            return codec.decode(buffer);
        } finally {
            buffer.release();
        }
    }

}
