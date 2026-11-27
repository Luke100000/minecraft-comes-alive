package net.conczin.mca.server.world.data;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatAIResourceDataTest {
    @Test
    void inworldAssignmentsRoundTripAsWorldOwnedSavedData() {
        UUID villagerId = UUID.fromString("4f6b6d8c-724c-4a8f-83b8-2f4ce9f22110");
        ChatAIResourceData data = new ChatAIResourceData(Map.of(villagerId, "characters/original"));

        assertEquals("characters/original", data.getResourceName(villagerId));
        data.putResourceName(villagerId, "characters/updated");
        assertTrue(data.isDirty());

        CompoundTag saved = new CompoundTag();
        data.save(saved, null);

        ChatAIResourceData loaded = new ChatAIResourceData(saved, null);
        assertEquals("characters/updated", loaded.getResourceName(villagerId));
        loaded.putResourceName(villagerId, "characters/updated");
        assertFalse(loaded.isDirty(), "Writing the same mapping must not dirty saved data");

        loaded.putResourceName(villagerId, "");
        assertEquals("", loaded.getResourceName(villagerId));
        assertTrue(loaded.isDirty());
    }
}
