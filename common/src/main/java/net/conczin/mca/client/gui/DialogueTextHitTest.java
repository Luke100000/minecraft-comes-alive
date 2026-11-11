package net.conczin.mca.client.gui;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/** Converts the actual drawn, scrolled text geometry into a glyph position. */
final class DialogueTextHitTest {
    private DialogueTextHitTest() {
    }

    record Position(int lineIndex, int unscaledX) {}

    /** Keeps a copy action inert until its whole styled phrase has been drawn. */
    static Component readyCopySpans(Component full, Component visible) {
        int visibleLength = visible.getString().length();
        if (visibleLength == 0 || visibleLength >= full.getString().length()) {
            return visible;
        }

        Map<ClickEvent, Integer> copyEnds = new IdentityHashMap<>();
        int[] offset = {0};
        full.visit((style, part) -> {
            offset[0] += part.length();
            ClickEvent click = style.getClickEvent();
            if (click != null && click.getAction() == ClickEvent.Action.COPY_TO_CLIPBOARD && !part.isEmpty()) {
                copyEnds.put(click, offset[0]);
            }
            return Optional.empty();
        }, Style.EMPTY);

        // Empty components from rich-text closing tags retain the end of nested copy scopes.
        int markerOffset = 0;
        for (Component part : full.getSiblings()) {
            String text = part.getString();
            markerOffset += text.length();
            ClickEvent click = part.getStyle().getClickEvent();
            if (text.isEmpty() && click != null && click.getAction() == ClickEvent.Action.COPY_TO_CLIPBOARD) {
                copyEnds.put(click, markerOffset);
            }
        }

        if (copyEnds.values().stream().noneMatch(end -> end > visibleLength)) {
            return visible;
        }

        MutableComponent ready = Component.empty();
        visible.visit((style, part) -> {
            if (!part.isEmpty()) {
                ClickEvent click = style.getClickEvent();
                Style effective = click != null && copyEnds.getOrDefault(click, 0) > visibleLength
                        ? style.withClickEvent(null).withHoverEvent(null) : style;
                ready.append(Component.literal(part).setStyle(effective));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return ready;
    }

    static Optional<Position> locate(double mouseX, double mouseY, int originX, int originY,
                                     float scale, int lineHeight, int glyphHeight, int scrollY, int lineCount,
                                     DialogueOverlayLayout.Rect clip) {
        if (!Double.isFinite(mouseX) || !Double.isFinite(mouseY) || scale <= 0 || lineHeight <= 0 || glyphHeight <= 0
                || lineCount <= 0 || mouseX < clip.x() || mouseX >= clip.right()
                || mouseY < clip.y() || mouseY >= clip.bottom() || mouseX < originX) {
            return Optional.empty();
        }
        double dy = mouseY - originY + scrollY;
        if (dy < 0 || dy >= (double) lineHeight * lineCount) {
            return Optional.empty();
        }
        int lineIndex = (int) (dy / lineHeight);
        if (dy - (double) lineIndex * lineHeight >= glyphHeight) {
            return Optional.empty();
        }
        return Optional.of(new Position(lineIndex, (int) ((mouseX - originX) / scale)));
    }

    static Optional<String> copyValue(Style style) {
        ClickEvent click = style.getClickEvent();
        return click != null && click.getAction() == ClickEvent.Action.COPY_TO_CLIPBOARD
                ? Optional.of(click.getValue()) : Optional.empty();
    }
}
