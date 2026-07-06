package net.conczin.mca.client.gui;

import net.conczin.mca.network.Network;
import net.conczin.mca.network.c2s.ConfirmBuildingPolymorphMessage;
import net.conczin.mca.util.compat.ButtonWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Objects;

public class BuildingPolymorphScreen extends Screen {
    private final List<String> matchingTypes;
    private final BlockPos scanPos;
    private final boolean isRoom;

    public BuildingPolymorphScreen(List<String> matchingTypes, BlockPos scanPos, boolean isRoom) {
        super(Component.translatable("gui.building_polymorph.title"));
        this.matchingTypes = matchingTypes;
        this.scanPos = scanPos;
        this.isRoom = isRoom;
    }

    private int getTextYStart() {
        return height / 2 - ((matchingTypes.size() * 26) + 50) / 2;
    }

    @Override
    protected void init() {
        super.init();
        int btnWidth = 180;
        int btnHeight = 20;
        int spacing = 6;
        int textYStart = getTextYStart();
        int startY = textYStart + 35;

        for (int i = 0; i < matchingTypes.size(); i++) {
            String typeName = matchingTypes.get(i);
            int y = startY + i * (btnHeight + spacing);
            addRenderableWidget(new ButtonWidget(
                    width / 2 - btnWidth / 2,
                    y,
                    btnWidth,
                    btnHeight,
                    Component.translatable("buildingType." + typeName),
                    button -> {
                        Network.sendToServer(new ConfirmBuildingPolymorphMessage(scanPos, isRoom, typeName));
                        Objects.requireNonNull(this.minecraft).setScreen(null);
                    }
            ));
        }

        // Cancel button
        addRenderableWidget(new ButtonWidget(
                width / 2 - 50,
                startY + matchingTypes.size() * (btnHeight + spacing) + 10,
                100,
                20,
                Component.translatable("gui.blueprint.cancel"),
                button -> Objects.requireNonNull(this.minecraft).setScreen(null)
        ));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float offset) {
        super.render(context, mouseX, mouseY, offset);
        int textYStart = getTextYStart();
        context.drawCenteredString(font, Component.translatable("gui.building_polymorph.title"), width / 2, textYStart, 0xffffff);
        context.drawCenteredString(font, Component.translatable("gui.building_polymorph.desc"), width / 2, textYStart + 15, 0xaaaaaa);
    }
}
