package net.conczin.mca.dialogue;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Validates a coordinate tuple and formats its display and clipboard representations. */
public final class DialogueCoordinates {
    private static final Pattern TRIPLET = Pattern.compile(
            "\\s*(-?\\d+)(?:\\s*,\\s*|\\s+)(-?\\d+)(?:\\s*,\\s*|\\s+)(-?\\d+)\\s*");

    private DialogueCoordinates() {
    }

    static Component styled(String text) {
        return copyValue(text)
                .<Component>map(copy -> Component.literal(text).setStyle(Style.EMPTY
                        .withColor(TextColor.fromRgb(0x237B80))
                        .withUnderlined(true)
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("gui.dialogue.copy_coordinates")))
                        .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, copy))))
                .orElseGet(() -> Component.literal(text));
    }

    public static Optional<String> copyValue(String text) {
        if (text == null || text.length() > 128) {
            return Optional.empty();
        }
        Matcher match = TRIPLET.matcher(text);
        if (!match.matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.parseInt(match.group(1)) + " "
                    + Integer.parseInt(match.group(2)) + " "
                    + Integer.parseInt(match.group(3)));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
