package net.conczin.mca.client.gui;

public final class VillagerEditorAppearancePanelRegressionTest {
    private VillagerEditorAppearancePanelRegressionTest() {
    }

    public static void main(String[] args) {
        assertEquals(175, VillagerEditorAppearancePanelPolicy.panelWidth(427), "compact editor keeps the base appearance width");
        assertEquals(213, VillagerEditorAppearancePanelPolicy.panelWidth(853), "appearance controls grow with normal widescreen space");
        assertEquals(240, VillagerEditorAppearancePanelPolicy.panelWidth(1200), "appearance controls stop growing before becoming oversized");

        int panelWidth = VillagerEditorAppearancePanelPolicy.panelWidth(853);
        VillagerEditorAppearancePanelPolicy.PanelLayout layout = VillagerEditorAppearancePanelPolicy.layout(panelWidth);

        int tabsWidth = layout.tabWidth() * 3 + layout.lastTabWidth() + layout.tabGap() * 3;
        assertEquals(panelWidth, tabsWidth, "appearance tabs stay inside the editor column");
        assertTrue(layout.tabGap() >= 2, "appearance tabs are visually separated");

        int actionsWidth = layout.leftActionWidth() + layout.actionGap() + layout.rightActionWidth();
        assertEquals(panelWidth, actionsWidth, "paired appearance actions stay inside the editor column");
        assertTrue(layout.actionGap() >= 4, "paired actions read as separate controls");

        int cycleCenterWidth = panelWidth - layout.cycleArrowWidth() * 2;
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
