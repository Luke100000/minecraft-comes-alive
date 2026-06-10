package net.conczin.mca.client.book.pages;

import net.conczin.mca.client.gui.ExtendedBookScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class CenteredListPage extends ListPage {
    public static final int ENTRIES_PER_PAGE = 11;
    final Component title;

    public CenteredListPage(Component title, List<Component> text) {
        super(text);

        this.title = title;
    }

    public CenteredListPage(String title, List<Component> text) {
        this(Component.translatable(title).withStyle(ChatFormatting.BLACK).withStyle(ChatFormatting.BOLD), text);
    }

    private static void drawCenteredText(ExtendedBookScreen screen, GuiGraphicsExtractor context, Font textRenderer, Component text, int centerX, int y, int color) {
        FormattedCharSequence orderedText = text.getVisualOrderText();
        context.text(textRenderer, orderedText, (centerX - textRenderer.width(orderedText) / 2), y, color, screen.getBook().hasTextShadow());
    }

    @Override
    int getEntriesPerPage() {
        return 11;
    }

    @Override
    public void render(ExtendedBookScreen screen, GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        drawCenteredText(screen, context, screen.getTextRenderer(), title, screen.width / 2, 35, 0xFFFFFFFF);

        int y = 48;
        for (int i = page * ENTRIES_PER_PAGE; i < Math.min(text.size(), (page + 1) * ENTRIES_PER_PAGE); i++) {
            drawCenteredText(screen, context, screen.getTextRenderer(), text.get(i), screen.width / 2 - 4, y, 0xFFFFFFFF);
            y += 10;
        }
    }
}