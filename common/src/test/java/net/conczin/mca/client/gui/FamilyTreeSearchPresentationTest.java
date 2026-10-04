package net.conczin.mca.client.gui;

import net.conczin.mca.FamilyTreeTestSupport;
import net.conczin.mca.network.FamilyTreeSearchEntry;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static net.conczin.mca.FamilyTreeTestSupport.uuid;

class FamilyTreeSearchPresentationTest {
    private static final UUID ONE = uuid(1);
    private static final UUID TWO = uuid(2);

    @Test
    void bothResolvedParentsShowBothNames() {
        FamilyTreeSearchEntry entry = entry(ONE, "Alex", true, "Ada", true, "Ben");

        assertEquals(
                Component.translatable("gui.family_tree.child_of_2", "Ada", "Ben"),
                FamilyTreeSearchPresentation.parentLine(entry).orElseThrow()
        );
    }

    @Test
    void oneResolvedParentShowsKnownName() {
        FamilyTreeSearchEntry entry = entry(ONE, "Alex", true, "Ada", false, "");

        assertEquals(
                Component.translatable("gui.family_tree.child_of_1", "Ada"),
                FamilyTreeSearchPresentation.parentLine(entry).orElseThrow()
        );
    }

    @Test
    void neitherRecordedParentUsesNotRecordedCopy() {
        FamilyTreeSearchEntry entry = entry(ONE, "Alex", false, "", false, "");

        assertEquals(
                Component.translatable("gui.family_tree.parents_not_recorded"),
                FamilyTreeSearchPresentation.parentLine(entry).orElseThrow()
        );
    }

    @Test
    void recordedButUnresolvedParentDoesNotClaimOrphanOrNotRecorded() {
        FamilyTreeSearchEntry entry = entry(ONE, "Alex", true, "", false, "");

        assertTrue(FamilyTreeSearchPresentation.parentLine(entry).isEmpty());
    }

    @Test
    void blankPersonNameUsesLocalizedUnnamedCopy() {
        FamilyTreeSearchEntry entry = entry(ONE, "   ", false, "", false, "");

        Component displayName = FamilyTreeSearchPresentation.displayName(entry);

        assertEquals(Component.translatable("gui.family_tree.unnamed_villager"), displayName);
        assertFalse(displayName.getString().contains("???"));
    }

    @Test
    void duplicateNamesRemainSeparateUuidEntries() {
        FamilyTreeSearchEntry first = entry(ONE, "Alex", false, "", false, "");
        FamilyTreeSearchEntry second = entry(TWO, "Alex", false, "", false, "");
        List<FamilyTreeSearchEntry> entries = List.of(first, second);

        assertEquals(FamilyTreeSearchPresentation.displayName(first), FamilyTreeSearchPresentation.displayName(second));
        assertNotEquals(entries.get(0).uuid(), entries.get(1).uuid());
        assertEquals(2, entries.stream().map(FamilyTreeSearchEntry::uuid).distinct().count());
    }

    @Test
    void emptyStandaloneQueryKeepsPlayerNameSeeding() {
        assertEquals(Optional.of("Local Player"), FamilyTreeSearchScreen.standaloneSearchQuery(" ", "Local Player"));
        assertEquals(Optional.of("Alex"), FamilyTreeSearchScreen.standaloneSearchQuery(" Alex ", "Local Player"));
    }

    private static FamilyTreeSearchEntry entry(
            UUID uuid,
            String name,
            boolean fatherRecorded,
            String father,
            boolean motherRecorded,
            String mother
    ) {
        return new FamilyTreeSearchEntry(uuid, name, fatherRecorded, father, motherRecorded, mother);
    }

}
