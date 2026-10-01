package net.conczin.mca.network;

import io.netty.buffer.Unpooled;
import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.c2s.GetFamilyTreeRequest;
import net.conczin.mca.network.s2c.GetFamilyTreeResponse;
import net.conczin.mca.server.world.data.FamilyTreeNode;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static net.conczin.mca.network.FamilyTreeView.Direction.ANCESTORS;
import static net.conczin.mca.network.FamilyTreeView.Direction.DESCENDANTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FamilyTreeViewCodecTest {
    private static final UUID ROOT = uuid(1);
    private static final UUID UNAVAILABLE = uuid(2);

    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
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
        FamilyTreeView view = new FamilyTreeView(
                Map.of(ROOT, rootNode),
                Set.of(
                        new FamilyTreeView.Continuation(ROOT, ANCESTORS),
                        new FamilyTreeView.Continuation(ROOT, DESCENDANTS)
                ),
                Set.of(UNAVAILABLE)
        );
        GetFamilyTreeResponse response = new GetFamilyTreeResponse(41L, ROOT, true, view);

        GetFamilyTreeResponse decoded = roundTrip(GetFamilyTreeResponse.STREAM_CODEC, response);

        assertEquals(41L, decoded.requestId());
        assertEquals(ROOT, decoded.uuid());
        assertTrue(decoded.found());
        assertEquals(Set.of(ROOT), decoded.view().nodes().keySet());
        assertEquals(rootNode.save(), decoded.view().nodes().get(ROOT).save());
        assertEquals(view.continuations(), decoded.view().continuations());
        assertEquals(Set.of(UNAVAILABLE), decoded.view().unavailable());
    }

    @Test
    void missingResponseRoundTripsWithEmptyView() {
        FamilyTreeView empty = new FamilyTreeView(Map.of(), Set.of(), Set.of());
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
    void searchEntryRoundTripsRecordedParentMetadataAndDeceasedState() {
        FamilyTreeSearchEntry entry = new FamilyTreeSearchEntry(
                ROOT,
                "Root",
                true,
                "Father",
                true,
                "",
                true
        );

        FamilyTreeSearchEntry decoded = roundTripByteBuf(FamilyTreeSearchEntry.STREAM_CODEC, entry);

        assertEquals(entry, decoded);
        assertTrue(decoded.fatherRecorded());
        assertTrue(decoded.motherRecorded());
        assertTrue(decoded.deceased());
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

    private static UUID uuid(int value) {
        return UUID.fromString("00000000-0000-0000-0000-" + String.format("%012d", value));
    }
}
