package net.mca.client.book.pages;

import net.mca.client.gui.ExtendedBookScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import java.util.LinkedList;
import java.util.List;

public class TextPage extends Page {
    protected final String content;
    private Style style = Style.EMPTY;
    private List<FormattedCharSequence> cachedPage;

    public TextPage(String name, int page) {
        content = "{ \"translate\": \"mca.books." + name + "." + page + "\" }";
    }

    public TextPage(String content) {
        this.content = content;
    }

    protected List<FormattedCharSequence> getCachedPage(ExtendedBookScreen screen) {
        if (cachedPage == null) {
            FormattedText stringVisitable = FormattedText.of(content, style);
            try {
                MutableComponent text = Component.Serializer.fromJson(content);
                if (text != null) {
                    text.withStyle(style);
                }
                stringVisitable = text;
            } catch (Exception ignored) {
            }
            if (stringVisitable == null) {
                cachedPage = new LinkedList<>();
            } else {
                cachedPage = screen.getTextRenderer().split(stringVisitable, 114);
            }
        }
        return cachedPage;
    }

    public void render(ExtendedBookScreen screen, GuiGraphics context, int mouseX, int mouseY, float delta) {
        //prepare page
        if (content != null) {
            // text
            int l = Math.min(128 / 9, getCachedPage(screen).size());
            int i = (screen.width - 192) / 2;
            for (int m = 0; m < l; ++m) {
                FormattedCharSequence orderedText = getCachedPage(screen).get(m);
                int x = i + 36;
                context.drawString(screen.getTextRenderer(), orderedText, x, (32 + m * 9), 0, screen.getBook().hasTextShadow());
            }
        }
    }

    public TextPage setStyle(Style style) {
        this.style = style;
        return this;
    }
}
