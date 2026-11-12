package net.conczin.mca.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/** Vanilla scroll gestures and scroller sprite for the custom-drawn dialogue choices. */
final class DialogueMenuScrollWidget extends AbstractScrollWidget {
    private int contentHeight;
    private int settledViewportHeight;

    DialogueMenuScrollWidget() {
        super(0, 0, 1, 1, Component.empty());
    }

    void layout(int x, int y, int contentWidth, int viewportHeight, int rowsHeight) {
        layout(x, y, contentWidth, viewportHeight, rowsHeight, viewportHeight);
    }

    void layout(int x, int y, int contentWidth, int viewportHeight, int rowsHeight, int settledHeight) {
        setRectangle(Math.max(1, contentWidth), Math.max(1, viewportHeight), x, y);
        contentHeight = Math.max(0, rowsHeight);
        settledViewportHeight = Math.max(1, settledHeight);
        setScrollAmount(scrollAmount());
    }

    boolean hasScrollbar() {
        return scrollbarVisible();
    }

    int offset() {
        return (int) Math.round(scrollAmount());
    }

    int maxOffset() {
        return getMaxScrollAmount();
    }

    void scrollToSection(int top) {
        setScrollAmount(Math.clamp(top, 0, Math.max(0, contentHeight - settledViewportHeight)));
    }

    void reset() {
        setScrollAmount(0);
        setFocused(false);
    }

    @Override
    protected int getInnerHeight() {
        return contentHeight;
    }

    @Override
    protected int getMaxScrollAmount() {
        // A temporarily clipped fitting list must not acquire a scroll offset
        // that would then be clamped back to zero as the panel finishes growing.
        return contentHeight <= settledViewportHeight ? 0 : Math.max(0, contentHeight - getHeight());
    }

    @Override
    protected boolean scrollbarVisible() {
        return getHeight() >= 32 && getMaxScrollAmount() > 0;
    }

    @Override
    protected double scrollRate() {
        return 24;
    }

    @Override
    protected void renderBackground(GuiGraphics graphics) {
        // The Talk menu owns its background and clipping; vanilla owns the scroller.
    }

    @Override
    protected void renderContents(GuiGraphics graphics, int mouseX, int mouseY, float tickDelta) {
        // Dialogue rows are drawn and hit-tested by InteractScreen.
    }

    @Override
    protected void renderDecorations(GuiGraphics graphics) {
        // Preserve the vanilla thumb and gestures with a warmer colour for this panel.
        graphics.setColor(1.0f, 0.77f, 0.50f, 1.0f);
        int scrollbarX = getX() + getWidth();
        graphics.pose().pushPose();
        graphics.pose().translate(scrollbarX + 1, 0, 0);
        graphics.pose().scale(5.0f / 8.0f, 1.0f, 1.0f);
        graphics.pose().translate(-scrollbarX, 0, 0);
        super.renderDecorations(graphics);
        graphics.pose().popPose();
        graphics.setColor(1.0f, 1.0f, 1.0f, 1.0f);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        // This gesture-only helper is not added to the screen's narrated widgets.
    }
}
