package net.conczin.mca.util.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

public class ButtonWidget extends net.minecraft.client.gui.components.Button {
    /**
     * Creates a 1.19.2 and lower button implementation.
     *
     * @since MC 1.19.3
     */
    public ButtonWidget(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION);
    }

    public ButtonWidget(int x, int y, int width, int height, Component message, OnPress onPress, Component tooltip) {
        this(x, y, width, height, message, onPress);
        setTooltip(Tooltip.create(tooltip));
    }

    @Override
    protected void renderContents(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderDefaultSprite(guiGraphics);
        this.renderDefaultLabel(guiGraphics.textRendererForWidget(this, GuiGraphics.HoveredTextEffects.NONE));
    }
}
