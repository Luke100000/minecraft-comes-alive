package net.conczin.mca.network;

import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.network.s2c.AppearanceClothingUpdate;
import net.conczin.mca.network.s2c.AppearanceHairUpdate;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.Hair;
import net.conczin.mca.resources.data.skin.HairStyle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppearanceCatalogEffectiveEntryTest {
    @Test
    void resolvesClothingOverrideAndFallback() {
        String id = "immersive_library:42";
        Clothing datapack = new Clothing(id, null, 0, false, Gender.NEUTRAL);
        Clothing custom = new Clothing(id, "minecraft:farmer", 2, true, Gender.FEMALE);
        assertSame(custom, AppearanceClothingUpdate.effective(custom, datapack).orElseThrow());
        assertSame(datapack, AppearanceClothingUpdate.effective(null, datapack).orElseThrow());
        assertTrue(AppearanceClothingUpdate.effective(null, null).isEmpty());
    }

    @Test
    void explicitHairStylesWinOverDerivedOnes() {
        String id = "immersive_library:7";
        Hair custom = new Hair(id, Gender.FEMALE, .5f);
        HairStyle explicit = HairStyle.singleLayer(id, Gender.NEUTRAL, 1f);
        assertSame(explicit, AppearanceHairUpdate.effectiveStyle(custom, explicit).orElseThrow());
        assertEquals(Gender.FEMALE, AppearanceHairUpdate.effectiveStyle(custom, null).orElseThrow().getGender());
        assertSame(explicit, AppearanceHairUpdate.effectiveStyle(null, explicit).orElseThrow());
        assertTrue(AppearanceHairUpdate.effectiveStyle(null, null).isEmpty());
    }
}
