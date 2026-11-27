package net.conczin.mca.client.gui;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

/** Converts the actual drawn, scrolled text geometry into a glyph position. */
final class DialogueTextHitTest {
    private DialogueTextHitTest() {
    }

    record Position(int lineIndex, int unscaledX) {}

    static CopySpans prepareCopySpans(Component full) {
        Map<ClickEvent, Integer> copyEnds = new IdentityHashMap<>();
        collectCopyEnds(full, Style.EMPTY, new int[]{0}, copyEnds);
        int lastEnd = copyEnds.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        return new CopySpans(copyEnds, lastEnd);
    }

    private static void collectCopyEnds(Component component, Style parent, int[] offset,
                                        Map<ClickEvent, Integer> copyEnds) {
        Style style = component.getStyle().applyTo(parent);
        // A closing marker has no glyphs, but retains the end of its copy scope.
        ClickEvent marker = component.getStyle().getClickEvent();
        if (component.getContents() == PlainTextContents.EMPTY && marker != null
                && marker.getAction() == ClickEvent.Action.COPY_TO_CLIPBOARD) {
            copyEnds.put(marker, offset[0]);
        }
        component.getContents().visit((partStyle, part) -> {
            offset[0] += part.length();
            ClickEvent click = partStyle.getClickEvent();
            if (click != null && click.getAction() == ClickEvent.Action.COPY_TO_CLIPBOARD && !part.isEmpty()) {
                copyEnds.put(click, offset[0]);
            }
            return Optional.empty();
        }, style);
        for (Component sibling : component.getSiblings()) {
            collectCopyEnds(sibling, style, offset, copyEnds);
        }
    }

    static final class CopySpans {
        private final Map<ClickEvent, Integer> ends;
        private final int lastEnd;

        private CopySpans(Map<ClickEvent, Integer> ends, int lastEnd) {
            this.ends = ends;
            this.lastEnd = lastEnd;
        }

        /** Keeps a copy action inert until its whole styled phrase has been drawn. */
        Component ready(Component visible) {
            if (ends.isEmpty()) {
                return visible;
            }
            int visibleLength = visible.getString().length();
            if (visibleLength == 0 || visibleLength >= lastEnd) {
                return visible;
            }
            MutableComponent ready = Component.empty();
            visible.visit((style, part) -> {
                if (!part.isEmpty()) {
                    ClickEvent click = style.getClickEvent();
                    Style effective = click != null && ends.getOrDefault(click, 0) > visibleLength
                            ? style.withClickEvent(null).withHoverEvent(null) : style;
                    ready.append(Component.literal(part).setStyle(effective));
                }
                return Optional.empty();
            }, Style.EMPTY);
            return ready;
        }
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
