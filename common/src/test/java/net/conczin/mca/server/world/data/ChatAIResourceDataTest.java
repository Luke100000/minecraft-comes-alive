package net.conczin.mca.server.world.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class ChatAIResourceDataTest {
    @Test
    void inworldAssignmentsRoundTripAsWorldOwnedSavedData() throws Exception {
        Class<?> type = dataType();
        UUID villagerId = UUID.fromString("4f6b6d8c-724c-4a8f-83b8-2f4ce9f22110");
        Constructor<?> seededConstructor = type.getDeclaredConstructor(Map.class);
        seededConstructor.setAccessible(true);
        Object data = seededConstructor.newInstance(Map.of(villagerId, "characters/original"));

        Method getResourceName = type.getDeclaredMethod("getResourceName", UUID.class);
        Method putResourceName = type.getDeclaredMethod("putResourceName", UUID.class, String.class);
        getResourceName.setAccessible(true);
        putResourceName.setAccessible(true);

        assertEquals("characters/original", getResourceName.invoke(data, villagerId));
        putResourceName.invoke(data, villagerId, "characters/updated");

        CompoundTag saved = new CompoundTag();
        Method save = type.getMethod("save", CompoundTag.class, HolderLookup.Provider.class);
        save.invoke(data, saved, null);

        Constructor<?> loadConstructor = type.getDeclaredConstructor(CompoundTag.class, HolderLookup.Provider.class);
        loadConstructor.setAccessible(true);
        Object loaded = loadConstructor.newInstance(saved, null);
        assertEquals("characters/updated", getResourceName.invoke(loaded, villagerId));

        putResourceName.invoke(loaded, villagerId, "");
        assertEquals("", getResourceName.invoke(loaded, villagerId));
    }

    private static Class<?> dataType() {
        try {
            return Class.forName("net.conczin.mca.server.world.data.ChatAIResourceData");
        } catch (ClassNotFoundException exception) {
            fail("Inworld villager assignments must be world-owned saved data, not COMMON config");
            return null;
        }
    }
}
