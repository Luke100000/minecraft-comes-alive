package net.conczin.mca.client.gui;

public final class VillagerEditorAppearancePanelRegressionTest {
    private VillagerEditorAppearancePanelRegressionTest() {
    }

    public static void main(String[] args) {
        VillagerEditorAppearancePanelPolicy.PanelLayout layout = VillagerEditorAppearancePanelPolicy.layout(175);

        int tabsWidth = layout.tabWidth() * 3 + layout.lastTabWidth() + layout.tabGap() * 3;
        assertEquals(175, tabsWidth, "appearance tabs stay inside the editor column");
        assertTrue(layout.tabGap() >= 2, "appearance tabs are visually separated");

        int actionsWidth = layout.leftActionWidth() + layout.actionGap() + layout.rightActionWidth();
        assertEquals(175, actionsWidth, "paired appearance actions stay inside the editor column");
        assertTrue(layout.actionGap() >= 4, "paired actions read as separate controls");

        int cycleCenterWidth = 175 - layout.cycleArrowWidth() * 2;
        assertTrue(cycleCenterWidth >= 120, "current selection keeps enough room for its label");
        assertTrue(layout.groupGap() > layout.rowGap(), "customization is separated from selection controls");
    }

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + ": expected " + expected + ", got " + actual);
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
