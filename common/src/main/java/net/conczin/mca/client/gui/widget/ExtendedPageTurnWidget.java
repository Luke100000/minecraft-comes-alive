package net.conczin.mca.client.gui.widget;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.resources.ResourceLocation;

public class ExtendedPageTurnWidget extends PageButton {
    private final ResourceLocation texture;

    private final boolean isNextPageButton;

    public ExtendedPageTurnWidget(int x, int y, boolean isNextPageButton, OnPress action, boolean playPageTurnSound, ResourceLocation texture) {
        super(x, y, isNextPageButton, action, playPageTurnSound);
        this.isNextPageButton = isNextPageButton;
        this.texture = texture;
    }

    @Override
    public void renderWidget(GuiGraphics context, int mouseX, int mouseY, float delta) {
        int i = 0;
        int j = 192;
        if (isHovered()) {
            i += 23;
        }

        if (!isNextPageButton) {
            j += 13;
        }

        context.blit(texture, getX(), getY(), i, j, 23, 13);
    }
}
