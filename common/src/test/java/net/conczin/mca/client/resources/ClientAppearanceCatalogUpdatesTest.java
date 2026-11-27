package net.conczin.mca.client.resources;

import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.Clothing;
import net.conczin.mca.resources.data.skin.Hair;
import net.conczin.mca.resources.data.skin.HairStyle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ClientAppearanceCatalogUpdatesTest {
    @AfterEach
    void reset() { ClientAppearanceCatalog.clear(); }

    @Test
    void deltaRequiresSnapshotAndNeverResurrectsClientDefaults() {
        String id = "immersive_library:42";
        Clothing entry = new Clothing(id, null, 0, false, Gender.NEUTRAL);
        ClientAppearanceCatalog.clear();
        assertFalse(ClientAppearanceCatalog.applyClothingUpdate(id, Optional.of(entry)));
        ClientAppearanceCatalog.installServerSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        assertTrue(ClientAppearanceCatalog.applyClothingUpdate(id, Optional.of(entry)));
        assertSame(entry, ClientAppearanceCatalog.clothing().get(id));
        assertTrue(ClientAppearanceCatalog.applyClothingUpdate(id, Optional.empty()));
        assertFalse(ClientAppearanceCatalog.clothing().containsKey(id));
        assertFalse(ClientAppearanceCatalog.applyClothingUpdate(id, Optional.of(new Clothing("immersive_library:43", null, 0, false, Gender.FEMALE))));
        assertFalse(ClientAppearanceCatalog.clothing().containsKey(id));
    }

    @Test
    void hairAndExplicitStyleApplyTogetherAndValidateBothIds() {
        String id = "immersive_library:17";
        Hair hair = new Hair(id, Gender.FEMALE, .5f);
        HairStyle style = HairStyle.singleLayer(id, Gender.NEUTRAL, 1f);
        ClientAppearanceCatalog.installServerSnapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        assertTrue(ClientAppearanceCatalog.applyHairUpdate(id, Optional.of(hair), Optional.of(style)));
        assertSame(hair, ClientAppearanceCatalog.hair().get(id));
        assertSame(style, ClientAppearanceCatalog.hairStyles().get(id));
        assertFalse(ClientAppearanceCatalog.applyHairUpdate(id, Optional.empty(), Optional.of(HairStyle.singleLayer("wrong:id", Gender.MALE, 1f))));
        assertSame(hair, ClientAppearanceCatalog.hair().get(id));
        assertTrue(ClientAppearanceCatalog.applyHairUpdate(id, Optional.empty(), Optional.empty()));
        assertFalse(ClientAppearanceCatalog.hair().containsKey(id));
        assertFalse(ClientAppearanceCatalog.hairStyles().containsKey(id));
    }
}
