package net.conczin.mca.client.gui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DialogueMenuOrderingTest {
    private record Option(String id, boolean highlighted) {}

    @Test
    void featuredEventMovesOutOfCategoryOnce() {
        Option ordinary = new Option("joke", false);
        Option featured = new Option("night_sky", true);
        Option later = new Option("mourning", true);
        var selection = DialogueMenuOrdering.split(List.of(ordinary, featured, later), Option::highlighted);
        assertEquals(featured, selection.featured().orElseThrow());
        assertEquals(List.of(ordinary, later), selection.remaining());
    }

    @Test
    void noFeaturedEventLeavesMenuUnchanged() {
        Option ordinary = new Option("joke", false);
        var selection = DialogueMenuOrdering.split(List.of(ordinary), Option::highlighted);
        assertTrue(selection.featured().isEmpty());
        assertEquals(List.of(ordinary), selection.remaining());
    }

    @Test
    void emptyMenuHasNoFeaturedEvent() {
        var selection = DialogueMenuOrdering.split(List.<Option>of(), Option::highlighted);
        assertTrue(selection.featured().isEmpty());
        assertTrue(selection.remaining().isEmpty());
    }

    @Test
    void keyboardStartsAtFirstOrLastVisibleTarget() {
        assertEquals(0, DialogueMenuOrdering.nextFocus(-1, 4, 1));
        assertEquals(3, DialogueMenuOrdering.nextFocus(-1, 4, -1));
    }

    @Test
    void keyboardWrapsAndResetsAnInvalidFocusAfterLayoutChanges() {
        assertEquals(0, DialogueMenuOrdering.nextFocus(3, 4, 1));
        assertEquals(3, DialogueMenuOrdering.nextFocus(0, 4, -1));
        assertEquals(0, DialogueMenuOrdering.nextFocus(5, 4, 1));
        assertEquals(3, DialogueMenuOrdering.nextFocus(5, 4, -1));
        assertEquals(-1, DialogueMenuOrdering.nextFocus(2, 0, 1));
    }
}
