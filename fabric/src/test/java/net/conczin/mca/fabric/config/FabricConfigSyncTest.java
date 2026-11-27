package net.conczin.mca.fabric.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import net.conczin.mca.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FabricConfigSyncTest {
    @AfterEach void clear() { Config.SERVER_SPEC.clearRemote(); }

    @Test
    void fcpNativeSyncIsSelectedOnlyWhenBothPeersHaveFcp() {
        assertFalse(FabricConfigSync.requiresMcaSnapshot(true, true));
        assertTrue(FabricConfigSync.requiresMcaSnapshot(true, false));
        assertTrue(FabricConfigSync.requiresMcaSnapshot(false, true));
        assertTrue(FabricConfigSync.requiresMcaSnapshot(false, false));
    }

    @Test
    void clientCannotUseLocalServerPolicyWhileAuthoritativeSyncIsPending() {
        var serverSnapshot = FabricConfigSync.snapshot();
        Config.SERVER_SPEC.beginRemoteSync();
        try {
            assertTrue(Config.SERVER_SPEC.isAwaitingRemote());
            assertThrows(IllegalStateException.class, () -> Config.SERVER.babyItemGrowUpTime.get());
            FabricConfigSync.applyRemote(serverSnapshot);
            assertFalse(Config.SERVER_SPEC.isAwaitingRemote());
            assertEquals(24000, Config.SERVER.babyItemGrowUpTime.get());
        } finally {
            Config.SERVER_SPEC.clearRemote();
        }
    }

    @Test
    void clientLoginDependentWorkRunsOnlyAfterTheAuthoritativeServerSnapshot() {
        var serverSnapshot = FabricConfigSync.snapshot();
        var called = new java.util.concurrent.atomic.AtomicBoolean();
        Config.SERVER_SPEC.beginRemoteSync();
        try {
            FabricConfigSync.onClientJoin(() -> called.set(true));
            assertFalse(called.get(), "client login logic must not consume stale local SERVER policy");
            FabricConfigSync.applyRemote(serverSnapshot);
            FabricConfigSync.finishClientSync();
            assertTrue(called.get());
        } finally {
            FabricConfigSync.onClientDisconnect();
        }
    }

    @Test
    void serverOnlyValuesRoundTripIntoAtomicRemoteOverlay() {
        var packet = FabricConfigSync.snapshot();
        assertTrue(packet.json().contains("babyItemGrowUpTime"));
        assertFalse(packet.json().contains("villagerChatAIToken"));
        assertFalse(packet.json().contains("onlineTTSModel"));

        FabricConfigSync.applyRemote(packet);
        assertTrue(Config.SERVER_SPEC.isRemote());
        assertEquals(24000, Config.SERVER.babyItemGrowUpTime.get());
    }

    @Test
    void badProtocolAndDuplicateKeysCannotChangeServerOverlay() {
        String json = FabricConfigSync.snapshot().json();
        assertThrows(IllegalArgumentException.class, () ->
                FabricConfigSync.applyRemote(new FabricConfigSync.Snapshot(json.replace("\"protocolVersion\":1", "\"protocolVersion\":77"))));
        assertFalse(Config.SERVER_SPEC.isRemote());

        assertThrows(IllegalArgumentException.class, () ->
                FabricConfigSync.applyRemote(new FabricConfigSync.Snapshot(json.replace("\"protocolVersion\":1", "\"protocolVersion\":1.9"))));
        assertFalse(Config.SERVER_SPEC.isRemote());

        assertThrows(IllegalArgumentException.class, () ->
                FabricConfigSync.applyRemote(new FabricConfigSync.Snapshot(json.replace("\"values\":{", "\"values\":{\"villager_behavior.babyItemGrowUpTime\":15,"))));
        assertFalse(Config.SERVER_SPEC.isRemote());
    }

    @Test
    void snapshotEnvelopeAllowsEitherFieldOrderButRejectsMissingAndDuplicateFields() {
        var payload = JsonParser.parseString(FabricConfigSync.snapshot().json()).getAsJsonObject();
        String values = payload.getAsJsonObject("values").toString();
        String reordered = "{\"values\":" + values + ",\"protocolVersion\":1}";
        FabricConfigSync.applyRemote(new FabricConfigSync.Snapshot(reordered));
        assertEquals(24000, Config.SERVER.babyItemGrowUpTime.get());

        for (String invalid : List.of(
                "{\"values\":" + values + "}",
                "{\"protocolVersion\":1}",
                "{\"protocolVersion\":1,\"protocolVersion\":1,\"values\":" + values + "}",
                "{\"protocolVersion\":1,\"values\":" + values + ",\"values\":" + values + "}",
                reordered + "{}")) {
            assertThrows(IllegalArgumentException.class,
                    () -> FabricConfigSync.applyRemote(new FabricConfigSync.Snapshot(invalid)));
            assertEquals(24000, Config.SERVER.babyItemGrowUpTime.get());
        }
    }

    @Test
    void missingOrOutOfRangeSettingCannotReplaceRemoteState() {
        var payload = JsonParser.parseString(FabricConfigSync.snapshot().json()).getAsJsonObject();
        var values = payload.getAsJsonObject("values");
        values.addProperty("villager_behavior.babyItemGrowUpTime", 14000);
        FabricConfigSync.applyRemote(new FabricConfigSync.Snapshot(payload.toString()));

        values.addProperty("villager_behavior.babyItemGrowUpTime", 38000);
        values.addProperty("villager_behavior.villagerMaxAgeTime", 3);
        assertThrows(IllegalArgumentException.class,
                () -> FabricConfigSync.applyRemote(new FabricConfigSync.Snapshot(payload.toString())));
        assertEquals(14000, Config.SERVER.babyItemGrowUpTime.get());

        values.remove("villager_behavior.villagerMaxAgeTime");
        assertThrows(IllegalArgumentException.class,
                () -> FabricConfigSync.applyRemote(new FabricConfigSync.Snapshot(payload.toString())));
        assertEquals(14000, Config.SERVER.babyItemGrowUpTime.get());
    }

    @Test
    void wrongTypeOrUnknownFieldIsRejectedAtomically() {
        String json = FabricConfigSync.snapshot().json();
        String broken = json.replace("\"villager_behavior.babyItemGrowUpTime\":24000", "\"villager_behavior.babyItemGrowUpTime\":\"wrong\"");
        assertNotEquals(json, broken);
        assertThrows(IllegalArgumentException.class, () -> FabricConfigSync.applyRemote(new FabricConfigSync.Snapshot(broken)));
        assertFalse(Config.SERVER_SPEC.isRemote());

        String unknown = json.replace("\"values\":{", "\"values\":{\"ai.villagerChatAIToken\":\"secret\",");
        assertThrows(IllegalArgumentException.class, () -> FabricConfigSync.applyRemote(new FabricConfigSync.Snapshot(unknown)));
        assertFalse(Config.SERVER_SPEC.isRemote());
    }

    @Test
    void rejectsInvalidStringListElementsWithoutReplacingRemoteState() {
        var payload = JsonParser.parseString(FabricConfigSync.snapshot().json()).getAsJsonObject();
        var values = payload.getAsJsonObject("values");
        String listKey = "villager_behavior.professionConversionsMap";
        String entry = "villagersplus:alchemist=minecraft:farmer";
        var flatList = new JsonArray();
        flatList.add(entry);
        values.add(listKey, flatList);
        values.addProperty("villager_behavior.babyItemGrowUpTime", 14000);

        FabricConfigSync.applyRemote(new FabricConfigSync.Snapshot(payload.toString()));
        assertEquals(List.of(entry), Config.SERVER.professionConversionsMap.get());
        assertEquals(14000, Config.SERVER.babyItemGrowUpTime.get());

        values.addProperty("villager_behavior.babyItemGrowUpTime", 38000);
        String json = payload.toString();
        String field = "\"" + listKey + "\":" + flatList;
        String deeplyNested = "[".repeat(20000) + "\"x\"" + "]".repeat(20000);
        for (String invalidArray : List.of("[1]", "[true]", "[null]", "[{}]", "[[\"x\"]]", deeplyNested)) {
            String invalid = json.replace(field, "\"" + listKey + "\":" + invalidArray);
            assertNotEquals(json, invalid);
            assertThrows(IllegalArgumentException.class,
                    () -> FabricConfigSync.applyRemote(new FabricConfigSync.Snapshot(invalid)));
            assertTrue(Config.SERVER_SPEC.isRemote());
            assertEquals(14000, Config.SERVER.babyItemGrowUpTime.get());
            assertEquals(List.of(entry), Config.SERVER.professionConversionsMap.get());
        }
    }

    @Test
    void rejectsOversizedContentBeforeParsing() {
        var oversized = new FabricConfigSync.Snapshot(" ".repeat(FabricConfigSync.MAX_BYTES + 1));
        assertTrue(oversized.json().getBytes(StandardCharsets.UTF_8).length > FabricConfigSync.MAX_BYTES);
        assertThrows(IllegalArgumentException.class, () -> FabricConfigSync.applyRemote(oversized));
        assertFalse(Config.SERVER_SPEC.isRemote());
    }

    @Test
    void reconnectToDifferentServerNeverRetainsThePreviousServerSnapshot() {
        String original = FabricConfigSync.snapshot().json();
        String a = original.replace("\"villager_behavior.babyItemGrowUpTime\":24000",
                "\"villager_behavior.babyItemGrowUpTime\":14000");
        String b = original.replace("\"villager_behavior.babyItemGrowUpTime\":24000",
                "\"villager_behavior.babyItemGrowUpTime\":38000");
        assertNotEquals(original, a);
        assertNotEquals(original, b);

        FabricConfigSync.applyRemote(new FabricConfigSync.Snapshot(a));
        assertEquals(14000, Config.SERVER.babyItemGrowUpTime.get());
        Config.SERVER_SPEC.clearRemote();
        assertFalse(Config.SERVER_SPEC.isRemote());
        FabricConfigSync.applyRemote(new FabricConfigSync.Snapshot(b));
        assertEquals(38000, Config.SERVER.babyItemGrowUpTime.get());
    }
}
