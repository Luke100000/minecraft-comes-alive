package net.conczin.mca.client.book.pages;

import net.conczin.mca.client.gui.ExtendedBookScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

public class TitlePage extends Page {
    final Component title;
    final Component subtitle;

    public TitlePage(String book) {
        this(book, ChatFormatting.BLACK);
    }

    public TitlePage(String book, ChatFormatting color) {
        this("item.mca.book_" + book, "mca.books." + book + ".author", color);
    }

    public TitlePage(String title, String subtitle) {
        this(title, subtitle, ChatFormatting.BLACK);
    }

    public TitlePage(String title, String subtitle, ChatFormatting color) {
        this(Component.translatable(title).withStyle(color).withStyle(ChatFormatting.BOLD),
                Component.translatable(subtitle).withStyle(color).withStyle(ChatFormatting.ITALIC));
    }

    public TitlePage(Component title, Component subtitle) {
        this.title = title;
        this.subtitle = subtitle;
    }

    private static void drawCenteredText(ExtendedBookScreen screen, GuiGraphicsExtractor context, Font textRenderer, Component text, int centerX, int y, int color) {
        FormattedCharSequence orderedText = text.getVisualOrderText();
        drawCenteredText(screen, context, textRenderer, orderedText, centerX, y, color);
    }

    private static void drawCenteredText(ExtendedBookScreen screen, GuiGraphicsExtractor context, Font textRenderer, FormattedCharSequence text, int centerX, int y, int color) {
        context.text(textRenderer, text, (centerX - textRenderer.width(text) / 2), y, color, screen.getBook().hasTextShadow());
    }

    @Override
    public void render(ExtendedBookScreen screen, GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        List<FormattedCharSequence> texts = screen.getTextRenderer().split(title, 114);
        int y = 80 - 5 * texts.size();
        for (FormattedCharSequence t : texts) {
            drawCenteredText(screen, context, screen.getTextRenderer(), t, screen.width / 2 - 2, y, 0xFFFFFF);
            y += 10;
        }
        y = 82 + 5 * texts.size();
        drawCenteredText(screen, context, screen.getTextRenderer(), subtitle, screen.width / 2 - 2, y, 0xFFFFFF);
    }
}
