package net.conczin.mca.client.gui;

final class VillagerEditorAppearancePanelPolicy {
    private VillagerEditorAppearancePanelPolicy() {
    }

    static PanelLayout layout(int width) {
        int tabGap = 2;
        int rowGap = 2;
        int groupGap = 8;
        int actionGap = 4;
        int cycleArrowWidth = 22;

        int tabWidth = Math.max(1, (width - tabGap * 3) / 4);
        int lastTabWidth = Math.max(1, width - tabWidth * 3 - tabGap * 3);
        int leftActionWidth = Math.max(1, (width - actionGap) / 2);
        int rightActionWidth = Math.max(1, width - actionGap - leftActionWidth);

        return new PanelLayout(
                tabGap,
                tabWidth,
                lastTabWidth,
                rowGap,
                groupGap,
                actionGap,
                leftActionWidth,
                rightActionWidth,
                cycleArrowWidth
        );
    }

    record PanelLayout(
            int tabGap,
            int tabWidth,
            int lastTabWidth,
            int rowGap,
            int groupGap,
            int actionGap,
            int leftActionWidth,
            int rightActionWidth,
            int cycleArrowWidth
    ) {
    }
}
