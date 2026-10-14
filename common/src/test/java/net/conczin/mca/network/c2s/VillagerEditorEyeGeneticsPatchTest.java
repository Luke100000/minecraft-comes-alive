package net.conczin.mca.network.c2s;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VillagerEditorEyeGeneticsPatchTest {
    @Test void staleScalarEchoAndClientDnaCannotReplaceServerEyes() {
        CompoundTag server = new CompoundTag(); server.putFloat("GeneEyeColor", 0.6F);
        CompoundTag dna = new CompoundTag(); dna.putInt("Format", 99); server.put("EyeGenetics", dna);
        CompoundTag patch = new CompoundTag(); patch.putFloat("GeneEyeColor", 0.1F); patch.put("EyeGenetics", new CompoundTag());
        patch.putInt("EyeColor", -1); patch.putFloat("GeneSize", 0.9F);
        var merged = VillagerEditorSyncRequest.mergeAllowedEditorPatch(server, patch);
        assertEquals(0.6F, merged.getFloat("GeneEyeColor"));
        assertEquals(dna, merged.getCompound("EyeGenetics"));
        assertEquals(-1, merged.getInt("EyeColor")); assertEquals(0.9F, merged.getFloat("GeneSize"));
        var outgoing = VillagerEditorSyncRequest.createEditorPatch(patch);
        assertFalse(outgoing.contains("GeneEyeColor")); assertFalse(outgoing.contains("EyeGenetics"));
    }
}
