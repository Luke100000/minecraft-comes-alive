package net.conczin.mca.fabric.config;

import net.conczin.mca.Config;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class FabricConfigSyncTest {
    @AfterEach void clear() { Config.SERVER_SPEC.clearRemote(); }

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
                FabricConfigSync.applyRemote(new FabricConfigSync.Snapshot(json.replace("\"values\":{", "\"values\":{\"babyItemGrowUpTime\":15,"))));
        assertFalse(Config.SERVER_SPEC.isRemote());
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
