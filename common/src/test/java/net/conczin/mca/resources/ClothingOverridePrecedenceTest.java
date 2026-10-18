package net.conczin.mca.resources;

import net.conczin.mca.entity.ai.relationship.Gender;
import net.conczin.mca.resources.data.skin.Clothing;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClothingOverridePrecedenceTest {
    @Test
    void knownLibraryClothingUsesEffectiveGenderButUnknownLibraryIdRemainsPermissive() {
        String id = "immersive_library:42";
        Clothing effective = new Clothing(id, null, 0, false, Gender.FEMALE);
        assertTrue(SkinVisualIds.matchesKnownClothing(id, Gender.FEMALE, effective));
        assertFalse(SkinVisualIds.matchesKnownClothing(id, Gender.MALE, effective));
        assertTrue(SkinVisualIds.matchesKnownClothing(id, Gender.MALE, null));
    }

    @Test
    void customDefinitionsReplaceDatapackDefinitionsById() {
        Clothing datapack = new Clothing("immersive_library:42", "minecraft:farmer", 0, false, Gender.MALE);
        Clothing custom = new Clothing("immersive_library:42", "minecraft:cleric", 5, true, Gender.FEMALE);
        Clothing unrelated = new Clothing("mca:other", null, 0, false, Gender.NEUTRAL);
        Map<String, Clothing> baseline = Map.of(datapack.getIdentifier(), datapack, unrelated.getIdentifier(), unrelated);

        Map<String, Clothing> effective = ClothingList.effectiveClothing(baseline, Map.of(custom.getIdentifier(), custom));
        assertEquals(2, effective.size());
        assertSame(custom, effective.get(custom.getIdentifier()));
        assertSame(unrelated, effective.get(unrelated.getIdentifier()));
        assertSame(datapack, baseline.get(datapack.getIdentifier()), "Datapack source remains untouched");
        assertSame(datapack, ClothingList.effectiveClothing(baseline, Map.of()).get(datapack.getIdentifier()));
        var forProfession = SkinSelection.clothingForProfession(effective.values(), Gender.FEMALE, "minecraft:cleric");
        assertEquals(2, forProfession.size(), "Neutral unrelated clothing and one effective custom override");
        assertEquals(1, SkinSelection.toPool(forProfession, "missing").getEntries().stream()
                .filter(e -> e.getValue().equals(custom.getIdentifier())).count());
        assertTrue(SkinSelection.clothingForProfession(effective.values(), Gender.MALE, "minecraft:farmer")
                .stream().noneMatch(c -> c.getIdentifier().equals(custom.getIdentifier())),
                "A shadowed male datapack definition must not survive a female custom override");
        assertEquals(1, SkinSelection.clothingForProfession(baseline.values(), Gender.MALE, "minecraft:farmer")
                .stream().filter(c -> c.getIdentifier().equals(datapack.getIdentifier())).count(),
                "Removing the override restores the original datapack option");
    }

}
