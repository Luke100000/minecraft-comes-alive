package net.conczin.mca.client.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class NamedTextFieldWidget extends EditBox {
    private final Font textRenderer;

    public NamedTextFieldWidget(Font textRenderer, int x, int y, int width, int height, Component text) {
        super(textRenderer, x + width / 2, y, width / 2, height, text);
        this.textRenderer = textRenderer;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractWidgetRenderState(context, mouseX, mouseY, delta);

        FormattedCharSequence orderedText = getMessage().getVisualOrderText();
        context.text(textRenderer, orderedText, (getX() - textRenderer.width(orderedText) - 4), getY() + (height - 8) / 2, 0xffffff);
    }
}
