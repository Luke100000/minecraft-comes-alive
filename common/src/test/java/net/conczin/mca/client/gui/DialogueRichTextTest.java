package net.conczin.mca.client.gui;

import net.conczin.mca.dialogue.DialogueCoordinates;
import net.minecraft.ChatFormatting;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DialogueRichTextTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void nestedNamedColourAndBoldProduceStyledText() {
        Component parsed = DialogueRichText.parse(Component.literal("<gold><bold>Hi</bold></gold>!"));
        assertEquals("Hi!", parsed.getString());
        Style style = styleOf(parsed, "Hi");
        assertEquals(TextColor.fromLegacyFormat(ChatFormatting.GOLD), style.getColor());
        assertTrue(style.isBold());
        assertFalse(styleOf(parsed, "!").isBold());
    }

    @Test
    void hexColourAndItalicAreIndependent() {
        Component parsed = DialogueRichText.parse(Component.literal(
                "What a <color:#56C8C2><italic>lovely</italic></color> day"));
        assertEquals("What a lovely day", parsed.getString());
        assertEquals(TextColor.fromRgb(0x56C8C2), styleOf(parsed, "lovely").getColor());
        assertTrue(styleOf(parsed, "lovely").isItalic());
    }

    @Test
    void copyAndLocalizedHoverApplyOnlyToTheirPhrase() {
        Component parsed = DialogueRichText.parse(Component.literal(
                "Look for <copy><hover:gui.dialogue.copy>treasure</hover></copy> today"));
        assertEquals("Look for treasure today", parsed.getString());
        Style styled = styleOf(parsed, "treasure");
        assertEquals(ClickEvent.Action.COPY_TO_CLIPBOARD, styled.getClickEvent().getAction());
        assertEquals("treasure", styled.getClickEvent().getValue());
        assertEquals(HoverEvent.Action.SHOW_TEXT, styled.getHoverEvent().getAction());
        assertEquals(Component.translatable("gui.dialogue.copy"),
                styled.getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT));
        assertNull(styleOf(parsed, " today").getClickEvent());
    }

    @Test
    void copyCoordinatesKeepsVisibleCommaFormattingButUsesSpaceSeparatedClipboard() {
        Component parsed = DialogueRichText.parse(Component.literal(
                "<copy_coords>234,70,-980</copy_coords>"));
        assertEquals("234,70,-980", parsed.getString());
        assertEquals("234 70 -980", styleOf(parsed, "234,70,-980").getClickEvent().getValue());
        assertEquals(Optional.of("234 70 -980"), DialogueCoordinates.copyValue("234,70,-980"));
        assertEquals(Optional.of("-1 64 300"), DialogueCoordinates.copyValue(" -1 64 300 "));
    }

    @Test
    void invalidCoordinateTripletsStayReadableAndNoninteractive() {
        for (String invalid : List.of("234,70", "one,70,30", "234,70,30,4", "999999999999,0,1", "1,2,3 extra")) {
            assertTrue(DialogueCoordinates.copyValue(invalid).isEmpty());
            Component parsed = DialogueRichText.parse(Component.literal("<copy_coords>" + invalid + "</copy_coords>"));
            assertEquals(invalid, parsed.getString());
            assertNull(styleOf(parsed, invalid).getClickEvent());
        }
    }

    @Test
    void plainUnknownEscapedAndUnbalancedTagsRemainLiteral() {
        assertEquals("Hello", DialogueRichText.parse(Component.literal("Hello")).getString());
        assertEquals("<gold> literal", DialogueRichText.parse(Component.literal("\\<gold> literal")).getString());
        assertEquals("Hi <click:run_command>oops</click>",
                DialogueRichText.parse(Component.literal("Hi <click:run_command>oops</click>")).getString());
        Component malformed = DialogueRichText.parse(Component.literal("<copy>unfinished"));
        assertEquals("<copy>unfinished", malformed.getString());
        assertNull(styleOf(malformed, "unfinished").getClickEvent());
        assertEquals("<gold>too deep", DialogueRichText.parse(Component.literal("<gold>too deep")).getString());
    }

    @Test
    void existingStyledArgumentRetainsEventsInsideMarkup() {
        Style argumentStyle = Style.EMPTY.withUnderlined(true)
                .withColor(TextColor.fromRgb(0x237B80))
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, "234 70 -980"));
        Component source = Component.literal("<gold>At ")
                .append(Component.literal("234,70,-980").setStyle(argumentStyle))
                .append(Component.literal("</gold>"));
        Component parsed = DialogueRichText.parse(source);
        assertEquals("At 234,70,-980", parsed.getString());
        Style styled = styleOf(parsed, "234,70,-980");
        assertEquals(argumentStyle.getColor(), styled.getColor());
        assertTrue(styled.isUnderlined());
        assertEquals("234 70 -980", styled.getClickEvent().getValue());
    }

    @Test
    void overlyDeepInputDoesNotEnableCopyActions() {
        String deep = "<copy>".repeat(17) + "X" + "</copy>".repeat(17);
        Component parsed = DialogueRichText.parse(Component.literal(deep));
        assertEquals(deep, parsed.getString());
        assertNull(styleOf(parsed, "X").getClickEvent());
    }

    private static Style styleOf(Component component, String text) {
        List<Style> styles = new ArrayList<>();
        component.visit((style, segment) -> {
            if (segment.contains(text)) {
                styles.add(style);
            }
            return Optional.empty();
        }, Style.EMPTY);
        assertEquals(1, styles.size(), "Expected exactly one styled run for: " + text);
        return styles.getFirst();
    }
}
