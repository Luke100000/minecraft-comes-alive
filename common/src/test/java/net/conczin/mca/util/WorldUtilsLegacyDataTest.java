package net.conczin.mca.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WorldUtilsLegacyDataTest {
    @Test
    void legacyLookupKeepsCurrentIdFirstAndIncludesOriginalFamilyTreeId() {
        assertEquals(
                List.of("family_tree", "MCA-FamilyTree"),
                WorldUtils.savedDataLookupIds("family_tree", "MCA-FamilyTree")
        );
    }

    @Test
    void legacyLookupDoesNotDuplicateCurrentId() {
        assertEquals(
                List.of("family_tree"),
                WorldUtils.savedDataLookupIds("family_tree", "family_tree")
        );
    }
}
