package net.conczin.mca.client.gui;

import net.conczin.mca.dialogue.DialogueCoordinates;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Safe, opt-in rich text tags within localized dialogue strings. */
public final class DialogueRichText {
    private static final int MAX_LENGTH = 16_384;
    private static final int MAX_NESTING = 16;

    private DialogueRichText() {
    }

    public static Component parse(Component source) {
        if (source.getString().indexOf('<') < 0) {
            return source;
        }
        StringBuilder raw = new StringBuilder();
        List<StyledSpan> spans = new ArrayList<>();
        source.visit((style, part) -> {
            int start = raw.length();
            raw.append(part);
            spans.add(new StyledSpan(start, raw.length(), style));
            return Optional.empty();
        }, Style.EMPTY);
        if (raw.length() > MAX_LENGTH || raw.indexOf("<") < 0) {
            return source;
        }
        String input = raw.toString();
        Map<Integer, Boundary> boundaries = matchedTags(input);
        if (boundaries == null) {
            return source;
        }
        if (boundaries.isEmpty() && input.indexOf("\\<") < 0) {
            return source;
        }
        MutableComponent output = Component.empty();
        Deque<Scope> active = new ArrayDeque<>();
        StringBuilder chunk = new StringBuilder();
        Style currentStyle = null;
        int spanIndex = 0;
        for (int index = 0; index < input.length();) {
            Boundary boundary = boundaries.get(index);
            if (boundary != null) {
                if (!chunk.isEmpty()) {
                    output.append(Component.literal(chunk.toString()).setStyle(currentStyle));
                    chunk.setLength(0);
                    currentStyle = null;
                }
                if (boundary.open()) {
                    active.push(boundary.scope());
                } else {
                    Scope finished = active.pop();
                    if (finished.click() != null) {
                        // Keep the scope's end even when a nested copy tag covers its final glyphs.
                        output.append(Component.empty().setStyle(Style.EMPTY.withClickEvent(finished.click())));
                    }
                }
                index = boundary.end();
                continue;
            }
            boolean escaped = input.charAt(index) == '\\'
                    && index + 1 < input.length() && input.charAt(index + 1) == '<';
            if (escaped) {
                index++;
            }
            while (spanIndex + 1 < spans.size() && spans.get(spanIndex).end() <= index) {
                spanIndex++;
            }
            Style original = spans.isEmpty() ? Style.EMPTY : spans.get(spanIndex).style();
            Style markup = Style.EMPTY;
            Iterator<Scope> scopes = active.descendingIterator();
            while (scopes.hasNext()) {
                markup = scopes.next().apply(markup);
            }
            Style effective = original.applyTo(markup);
            if (currentStyle != null && !currentStyle.equals(effective)) {
                output.append(Component.literal(chunk.toString()).setStyle(currentStyle));
                chunk.setLength(0);
            }
            currentStyle = effective;
            chunk.append(input.charAt(index++));
        }
        if (!chunk.isEmpty()) {
            output.append(Component.literal(chunk.toString()).setStyle(currentStyle));
        }
        return output;
    }

    private static Map<Integer, Boundary> matchedTags(String input) {
        Map<Integer, Boundary> matches = new HashMap<>();
        Deque<Opening> pending = new ArrayDeque<>();
        for (int index = 0; index < input.length();) {
            if (input.charAt(index) == '\\' && index + 1 < input.length() && input.charAt(index + 1) == '<') {
                index += 2;
                continue;
            }
            if (input.charAt(index) != '<') {
                index++;
                continue;
            }
            int close = input.indexOf('>', index + 1);
            if (close < 0) {
                index++;
                continue;
            }
            String label = input.substring(index + 1, close);
            if (label.startsWith("/") && !pending.isEmpty()) {
                Opening opening = pending.peek();
                if (opening.tag().name().equals(label.substring(1))) {
                    pending.pop();
                    String copiedText = visibleText(input, opening.end(), index, matches);
                    String copy = switch (opening.tag().name()) {
                        case "copy" -> copiedText;
                        case "copy_coords" -> DialogueCoordinates.copyValue(copiedText).orElse(null);
                        default -> null;
                    };
                    Scope scope = new Scope(opening.tag(), copy == null ? null
                            : new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, copy));
                    matches.put(opening.start(), new Boundary(opening.end(), true, scope));
                    matches.put(index, new Boundary(close + 1, false, scope));
                    index = close + 1;
                    continue;
                }
                return null; // A mismatched recognized close leaves the original text intact.
            }
            if (!label.startsWith("/")) {
                Tag tag = supported(label);
                if (tag != null) {
                    if (pending.size() >= MAX_NESTING) {
                        return null;
                    }
                    pending.push(new Opening(index, close + 1, tag));
                    index = close + 1;
                    continue;
                }
            }
            index++;
        }
        return pending.isEmpty() ? matches : null;
    }

    private static String visibleText(String input, int start, int end, Map<Integer, Boundary> matches) {
        StringBuilder output = new StringBuilder();
        for (int index = start; index < end;) {
            Boundary nested = matches.get(index);
            if (nested != null) {
                index = nested.end();
            } else if (input.charAt(index) == '\\' && index + 1 < end && input.charAt(index + 1) == '<') {
                output.append('<');
                index += 2;
            } else {
                output.append(input.charAt(index++));
            }
        }
        return output.toString();
    }

    private static Tag supported(String label) {
        return switch (label) {
            case "bold", "italic", "underlined", "copy", "copy_coords" -> new Tag(label, null, null);
            default -> {
                if (label.startsWith("color:#") && label.length() == 13) {
                    try {
                        yield new Tag("color", TextColor.fromRgb(Integer.parseInt(label.substring(7), 16)), null);
                    } catch (NumberFormatException ignored) {
                        yield null;
                    }
                }
                if (label.startsWith("hover:") && label.substring(6).matches("[a-zA-Z0-9_.-]{1,128}")) {
                    yield new Tag("hover", null, label.substring(6));
                }
                ChatFormatting colour = ChatFormatting.getByName(label);
                yield colour != null && colour.isColor() ? new Tag(label, TextColor.fromLegacyFormat(colour), null) : null;
            }
        };
    }

    private record StyledSpan(int start, int end, Style style) {}
    private record Tag(String name, TextColor color, String hoverKey) {}
    private record Opening(int start, int end, Tag tag) {}
    private record Boundary(int end, boolean open, Scope scope) {}

    private record Scope(Tag tag, ClickEvent click) {
        Style apply(Style style) {
            return switch (tag.name()) {
                case "bold" -> style.withBold(true);
                case "italic" -> style.withItalic(true);
                case "underlined" -> style.withUnderlined(true);
                case "hover" -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable(tag.hoverKey())));
                case "copy", "copy_coords" -> click == null ? style : style.withClickEvent(click);
                default -> tag.color() == null ? style : style.withColor(tag.color());
            };
        }
    }
}
