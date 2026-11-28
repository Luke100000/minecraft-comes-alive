package net.conczin.mca.network.c2s;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillagerEditorSyncRequestTest {
    @Test
    void editorPatchPreservesSlimAppearanceFlag() {
        CompoundTag source = new CompoundTag();
        source.putBoolean("Slim", true);
        source.putBoolean("NotAnEditorField", true);

        CompoundTag patch = VillagerEditorSyncRequest.createEditorPatch(source);

        assertTrue(patch.getBoolean("Slim"));
        assertFalse(patch.contains("NotAnEditorField"));
    }

    @Test
    void disablingSlimReplacesSavedFlagWithoutChangingOtherServerData() {
        CompoundTag serverData = new CompoundTag();
        serverData.putBoolean("Slim", true);
        serverData.putInt("DespawnDelay", 42);
        CompoundTag source = new CompoundTag();
        source.putBoolean("Slim", false);
        source.putInt("DespawnDelay", 0);

        CompoundTag merged = VillagerEditorSyncRequest.mergeAllowedEditorPatch(serverData,
                VillagerEditorSyncRequest.createEditorPatch(source));

        assertTrue(merged.contains("Slim"));
        assertFalse(merged.getBoolean("Slim"));
        assertEquals(42, merged.getInt("DespawnDelay"));
        assertTrue(serverData.getBoolean("Slim"), "Merging must not mutate stored source data");
    }
}
