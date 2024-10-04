package net.mca.client.book.pages;

import net.mca.client.gui.ExtendedBookScreen;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class CenteredListPage extends ListPage {
    final Text title;

    public static final int ENTRIES_PER_PAGE = 11;

    public CenteredListPage(Text title, List<Text> text) {
        super(text);

        this.title = title;
    }

    public CenteredListPage(String title, List<Text> text) {
        this(Text.translatable(title).formatted(Formatting.BLACK).formatted(Formatting.BOLD), text);
    }

    @Override
    int getEntriesPerPage() {
        return 11;
    }

    private static void drawCenteredText(ExtendedBookScreen screen, DrawContext context, TextRenderer textRenderer, Text text, int centerX, int y, int color) {
        OrderedText orderedText = text.asOrderedText();
        context.drawText(textRenderer, orderedText, (centerX - textRenderer.getWidth(orderedText) / 2), y, color, screen.getBook().hasTextShadow());
    }

    @Override
    public void render(ExtendedBookScreen screen, DrawContext context, int mouseX, int mouseY, float delta) {
        drawCenteredText(screen, context, screen.getTextRenderer(), title, screen.width / 2, 35, 0xFFFFFFFF);

        int y = 48;
        for (int i = page * ENTRIES_PER_PAGE; i < Math.min(text.size(), (page + 1) * ENTRIES_PER_PAGE); i++) {
            drawCenteredText(screen, context, screen.getTextRenderer(), text.get(i), screen.width / 2 - 4, y, 0xFFFFFFFF);
            y += 10;
        }
    }
}