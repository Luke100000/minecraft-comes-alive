package net.conczin.mca.server.world.data;

import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.Hair;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CustomClothingManagerStorageTest {
    @Test
    void onlyRealClothingMutationsAreDirty() {
        var storage = new CustomClothingManager.Storage<Clothing>();
        String id = "immersive_library:42";
        Clothing original = new Clothing(id, null, 0, false, Gender.NEUTRAL);
        assertFalse(storage.removeEntry(id));
        assertFalse(storage.isDirty());
        assertTrue(storage.addEntry(id, original));
        assertTrue(storage.isDirty());
        storage.setDirty(false);
        assertFalse(storage.addEntry(id, new Clothing(id, null, 0, false, Gender.NEUTRAL)));
        assertFalse(storage.isDirty());
        assertTrue(storage.addEntry(id, new Clothing(id, "minecraft:farmer", 1, true, Gender.FEMALE)));
        assertTrue(storage.isDirty());
        storage.setDirty(false);
        assertTrue(storage.removeEntry(id));
        assertTrue(storage.isDirty());
        storage.setDirty(false);
        assertFalse(storage.removeEntry(id));
        assertFalse(storage.isDirty());
    }

    @Test
    void identicalHairMetadataDoesNotChangeStorage() {
        var storage = new CustomClothingManager.Storage<Hair>();
        String id = "immersive_library:7";
        assertTrue(storage.addEntry(id, new Hair(id, Gender.NEUTRAL, 1f)));
        storage.setDirty(false);
        assertFalse(storage.addEntry(id, new Hair(id, Gender.NEUTRAL, 1f)));
        assertFalse(storage.isDirty());
        assertTrue(storage.addEntry(id, new Hair(id, Gender.FEMALE, .5f)));
    }
}
