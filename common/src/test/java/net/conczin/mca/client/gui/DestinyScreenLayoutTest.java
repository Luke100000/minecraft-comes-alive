package net.conczin.mca.client.gui;

import net.conczin.mca.FamilyTreeTestSupport;
import net.conczin.mca.destiny.DestinyDestination;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DestinyScreenLayoutTest {
    private static final Font FIXED_WIDTH_FONT = new Font(ignored -> null, false) {
        @Override
        public int width(String text) {
            return text.length() * 6;
        }

        @Override
        public int width(FormattedText text) {
            return width(text.getString());
        }

        @Override
        public String plainSubstrByWidth(String text, int width) {
            return text.substring(0, Math.min(text.length(), Math.max(0, width / 6)));
        }
    };

    @BeforeAll
    static void bootstrapMinecraft() {
        FamilyTreeTestSupport.bootstrapMinecraft();
    }

    @Test
    void guiScaleFourWrapsRowsWithinBoundsAndPaginatesAtMostThreeRowsPerPage() {
        List<DestinyDestination> destinations = new ArrayList<>();
        String[] labels = {
                "Ancient City",
                "Pillager Outpost",
                "Shipwreck Beached",
                "Woodland Mansion",
                "Ocean Monument",
                "Desert Pyramid",
                "Jungle Pyramid",
                "Swamp Hut",
                "Trail Ruins"
        };
        for (String label : labels) {
            destinations.add(new DestinyDestination(label, Optional.of(Level.OVERWORLD)));
        }

        for (int screenWidth : new int[]{320, 284, 213}) {
            int maxRowWidth = screenWidth - DestinyScreen.DESTINY_DIMENSION_SELECTOR_MARGIN * 2;
            List<List<DestinyScreen.DestinationButtonSpec>> allRows = DestinyScreen.wrapDestinationRows(
                    destinations,
                    DestinyScreen.DESTINY_COLUMNS,
                    screenWidth,
                    FIXED_WIDTH_FONT,
                    Component::literal
            );

            assertTrue(allRows.size() > DestinyScreen.DESTINY_ROWS,
                    "Nine wide location labels should wrap into more than 3 total rows at width " + screenWidth);
            for (List<DestinyScreen.DestinationButtonSpec> row : allRows) {
                assertFalse(row.isEmpty());
                assertTrue(row.size() <= DestinyScreen.DESTINY_COLUMNS);
                int rowWidth = DestinyScreen.DESTINY_BUTTON_GAP * (row.size() - 1)
                        + row.stream().mapToInt(DestinyScreen.DestinationButtonSpec::width).sum();
                assertTrue(rowWidth <= maxRowWidth,
                        "Row width " + rowWidth + " exceeded maxRowWidth " + maxRowWidth + " at screenWidth " + screenWidth);
            }

            int pages = DestinyScreen.destinationPageCount(allRows.size());
            assertTrue(pages >= 2, "Wrapped rows beyond 3 must paginate instead of overflowing onto pagination controls");
            int seenButtons = 0;
            for (int page = 0; page < pages; page++) {
                List<List<DestinyScreen.DestinationButtonSpec>> pageRows =
                        DestinyScreen.destinationRowsForPage(allRows, page);
                assertTrue(pageRows.size() <= DestinyScreen.DESTINY_ROWS,
                        "Every page must have at most 3 rows so buttons never collide with pagination or Somewhere");
                seenButtons += pageRows.stream().mapToInt(List::size).sum();
            }
            assertEquals(destinations.size(), seenButtons, "Row pagination must preserve every destination");
        }
    }

    @Test
    void oversizedSingleDestinationClampsAndEllipsizesWithinMaxRowWidth() {
        int screenWidth = 213;
        int maxRowWidth = screenWidth - DestinyScreen.DESTINY_DIMENSION_SELECTOR_MARGIN * 2;
        List<DestinyDestination> destinations = List.of(
                new DestinyDestination("Extremely Long Modded Structure Destination Name", Optional.empty())
        );

        List<List<DestinyScreen.DestinationButtonSpec>> rows = DestinyScreen.wrapDestinationRows(
                destinations,
                Integer.MAX_VALUE,
                screenWidth,
                FIXED_WIDTH_FONT,
                Component::literal
        );

        assertEquals(1, rows.size());
        DestinyScreen.DestinationButtonSpec spec = rows.getFirst().getFirst();
        assertEquals(maxRowWidth, spec.width());
        assertTrue(FIXED_WIDTH_FONT.width(spec.label()) <= maxRowWidth - DestinyScreen.DESTINY_BUTTON_HORIZONTAL_PADDING);
        assertTrue(spec.label().getString().endsWith("..."));
    }
}
