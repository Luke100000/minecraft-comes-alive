package net.conczin.mca.client.gui;

import net.conczin.mca.MCA;
import net.conczin.mca.client.gui.widget.WidgetUtils;
import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.ConfirmBuildingPolymorphMessage;
import net.conczin.mca.resources.BuildingTypes;
import net.conczin.mca.resources.data.BuildingType;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Objects;

public class BuildingPolymorphScreen extends ExtendedScreen {
    private static final Identifier ICON_TEXTURES = MCA.locate("textures/buildings.png");
    private static final int BUTTON_WIDTH = 180;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 6;
    private static final int MAX_ENTRIES_PER_PAGE = 7;

    private final List<String> matchingTypes;
    private final BlockPos scanPos;
    private final boolean isRoom;
    private int page;

    public BuildingPolymorphScreen(List<String> matchingTypes, BlockPos scanPos, boolean isRoom) {
        super(Component.translatable("gui.building_polymorph.title"));
        this.matchingTypes = List.copyOf(matchingTypes);
        this.scanPos = scanPos;
        this.isRoom = isRoom;
    }

    private void drawBuildingIcon(GuiGraphicsExtractor context, String typeName, int x, int y) {
        BuildingType type = BuildingTypes.getInstance().getBuildingType(typeName);
        if (!type.hasIcon()) {
            return;
        }
        WidgetUtils.drawBuildingIcon(context, ICON_TEXTURES, x, y, type.iconU(), type.iconV());
    }

    private int getEntriesPerPage() {
        return Math.max(1, Math.min(MAX_ENTRIES_PER_PAGE, (height - 116) / (BUTTON_HEIGHT + BUTTON_SPACING)));
    }

    private int getPageCount() {
        return Math.max(1, (matchingTypes.size() + getEntriesPerPage() - 1) / getEntriesPerPage());
    }

    private int getVisibleEntries() {
        int pageStart = page * getEntriesPerPage();
        return Math.min(getEntriesPerPage(), Math.max(0, matchingTypes.size() - pageStart));
    }

    private boolean hasMultiplePages() {
        return getPageCount() > 1;
    }

    private int getContentTop() {
        int visibleEntries = getVisibleEntries();
        int listHeight = visibleEntries * BUTTON_HEIGHT + Math.max(0, visibleEntries - 1) * BUTTON_SPACING;
        int navigationHeight = hasMultiplePages() ? BUTTON_HEIGHT + BUTTON_SPACING : 0;
        int contentHeight = 35 + listHeight + 10 + navigationHeight + BUTTON_HEIGHT;
        return Math.max(18, height / 2 - contentHeight / 2);
    }

    private void setPage(int page) {
        this.page = Mth.clamp(page, 0, getPageCount() - 1);
        rebuildWidgets();
    }

    @Override
    protected void init() {
        super.init();
        page = Mth.clamp(page, 0, getPageCount() - 1);
        int startY = getContentTop() + 35;
        int pageStart = page * getEntriesPerPage();
        int visibleEntries = getVisibleEntries();

        for (int i = 0; i < visibleEntries; i++) {
            String typeName = matchingTypes.get(pageStart + i);
            int y = startY + i * (BUTTON_HEIGHT + BUTTON_SPACING);
            addRenderableWidget(new PolymorphButton(
                    width / 2 - BUTTON_WIDTH / 2,
                    y,
                    BUTTON_WIDTH,
                    BUTTON_HEIGHT,
                    typeName,
                    Component.translatable("buildingType." + typeName),
                    button -> {
                        Network.sendToServer(new ConfirmBuildingPolymorphMessage(scanPos, isRoom, typeName));
                        Objects.requireNonNull(this.minecraft).setScreen(null);
                    }
            ));
        }

        int footerY = startY + visibleEntries * (BUTTON_HEIGHT + BUTTON_SPACING) + 10;
        if (hasMultiplePages()) {
            addRenderableWidget(new ButtonWidget(
                    width / 2 - 90,
                    footerY,
                    32,
                    BUTTON_HEIGHT,
                    Component.literal("<"),
                    button -> setPage(page - 1)
            )).active = page > 0;
            addRenderableWidget(new ButtonWidget(
                    width / 2 - 54,
                    footerY,
                    108,
                    BUTTON_HEIGHT,
                    Component.literal((page + 1) + " / " + getPageCount()),
                    button -> {
                    }
            )).active = false;
            addRenderableWidget(new ButtonWidget(
                    width / 2 + 58,
                    footerY,
                    32,
                    BUTTON_HEIGHT,
                    Component.literal(">"),
                    button -> setPage(page + 1)
            )).active = page < getPageCount() - 1;
            footerY += BUTTON_HEIGHT + BUTTON_SPACING;
        }

        addRenderableWidget(new ButtonWidget(
                width / 2 - 50,
                footerY,
                100,
                BUTTON_HEIGHT,
                Component.translatable("gui.blueprint.cancel"),
                button -> Objects.requireNonNull(this.minecraft).setScreen(null)
        ));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float offset) {
        super.extractRenderState(context, mouseX, mouseY, offset);
        int textYStart = getContentTop();
        context.centeredText(font, Component.translatable("gui.building_polymorph.title"), width / 2, textYStart, 0xffffffff);
        context.centeredText(font, Component.translatable("gui.building_polymorph.desc"), width / 2, textYStart + 15, 0xffaaaaaa);
    }

    private class PolymorphButton extends ButtonWidget {
        private final String typeName;

        public PolymorphButton(int x, int y, int width, int height, String typeName, Component text, OnPress onPress) {
            super(x, y, width, height, text, onPress);
            this.typeName = typeName;
        }

        @Override
        protected void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
            super.extractContents(guiGraphics, mouseX, mouseY, partialTick);

            drawBuildingIcon(guiGraphics, typeName, getX() + 10, getY() + 10);
        }
    }
}
