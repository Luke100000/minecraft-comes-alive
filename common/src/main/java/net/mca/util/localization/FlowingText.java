package net.mca.util.localization;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

public final class FlowingText {

    private final List<FormattedCharSequence> lines;

    private final float scale;

    public FlowingText(List<FormattedCharSequence> lines, float scale) {
        this.lines = lines;
        this.scale = scale;
    }

    public List<FormattedCharSequence> lines() {
        return lines;
    }

    public float scale() {
        return scale;
    }

    public interface Factory {
        /**
         * Scales the given text to fit a desired width and height.
         */
        static FlowingText wrapLines(Font renderer, Component text, int maxBlockWidth, int maxBlockHeight) {
            float scale = 1;

            List<FormattedCharSequence> output;

            do {
                output = renderer.split(text, (int)Math.ceil(maxBlockWidth / scale));

                if (output.size() * 10 * scale <= maxBlockHeight) {
                    break;
                }

                scale -= 0.01F;
            } while (scale > 0.08F);

            // We trim excess lines in the event fitting isn't perfect
            int maxLines = (int)Math.ceil(maxBlockHeight / (10 * scale));

            return new FlowingText(output.stream().limit(maxLines).collect(Collectors.toList()), scale);
        }
    }

    public static List<Component> wrap(Component text, int maxWidth) {
        return Minecraft.getInstance().font.getSplitter().splitLines(text, maxWidth, Style.EMPTY).stream().map(line -> {
            MutableComponent compiled = Component.literal("");
            line.visit((s, t) -> {
                compiled.append(Component.literal(t).setStyle(s));
                return Optional.empty();
            }, text.getStyle());
            return compiled;
        }).collect(Collectors.toList());
    }
}
