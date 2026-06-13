package net.conczin.mca.client.book.pages;

import net.conczin.mca.client.gui.ExtendedBookScreen;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class CenteredTextPage extends TextPage {
    public CenteredTextPage(String name, int page) {
        super(name, page);
    }

    public CenteredTextPage(Component content) {
        super(content);
    }

    @Override
    public void render(ExtendedBookScreen screen, GuiGraphics context, int mouseX, int mouseY, float delta) {
        //prepare page
        if (content != null) {
            Font textRenderer = screen.getTextRenderer();

            // text
            int l = Math.min(128 / 9, getCachedPage(screen).size());
            int i = (screen.width - 192) / 2;
            for (int m = 0; m < l; ++m) {
                FormattedCharSequence orderedText = getCachedPage(screen).get(m);
                int x = i + 36;
                context.drawString(textRenderer, orderedText, x + 114 / 2 - textRenderer.width(orderedText) / 2, (32 + (m + 7 - (l / 2)) * 9), 0xFF000000, screen.getBook().hasTextShadow());
            }
        }
    }
}
