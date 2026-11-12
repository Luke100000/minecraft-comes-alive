package net.conczin.mca.client.gui;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DialogueTextHitTestTest {
    private static final DialogueOverlayLayout.Rect CLIP = new DialogueOverlayLayout.Rect(0, 10, 120, 40);

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void hitMapsToActualWrappedLineAndUnscaledGlyphOffset() {
        assertEquals(Optional.of(new DialogueTextHitTest.Position(1, 7)),
                DialogueTextHitTest.locate(27, 24, 20, 10, 1.0f, 12, 9, 0, 3, CLIP));
        assertEquals(Optional.of(new DialogueTextHitTest.Position(2, 8)),
                DialogueTextHitTest.locate(36, 31, 20, 10, 2.0f, 10, 9, 0, 4, CLIP));
    }

    @Test
    void scrollingOffsetsTheSameWrappedLineAsDrawing() {
        assertEquals(Optional.of(new DialogueTextHitTest.Position(2, 6)),
                DialogueTextHitTest.locate(26, 18, 20, 10, 1.0f, 10, 9, 20, 5, CLIP));
        assertEquals(Optional.of(new DialogueTextHitTest.Position(0, 0)),
                DialogueTextHitTest.locate(20, 10, 20, 10, 1.0f, 10, 9, 0, 5, CLIP));
    }

    @Test
    void clippedAndOutsideGlyphTargetsAreRejected() {
        assertTrue(DialogueTextHitTest.locate(20, 9, 20, 10, 1, 10, 9, 0, 3, CLIP).isEmpty());
        assertTrue(DialogueTextHitTest.locate(130, 22, 20, 10, 1, 10, 9, 0, 3, CLIP).isEmpty());
        assertTrue(DialogueTextHitTest.locate(19, 22, 20, 10, 1, 10, 9, 0, 3, CLIP).isEmpty());
        assertTrue(DialogueTextHitTest.locate(24, 20, 20, 10, 1, 10, 9, 0, 1, CLIP).isEmpty());
    }

    @Test
    void coordinatesCopyAsSoonAsCompleteEvenWhileTheSentenceContinues() {
        Style coordinates = Style.EMPTY.withUnderlined(true).withColor(TextColor.fromRgb(0x237B80))
                .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, "234 70 -980"))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal("Copy coordinates")));
        Component full = Component.literal("Find ")
                .append(Component.literal("234,70,-980").setStyle(coordinates)).append(" by the river.");
        Component incomplete = Component.literal("Find ")
                .append(Component.literal("234,70,-98").setStyle(coordinates));
        Component complete = Component.literal("Find ")
                .append(Component.literal("234,70,-980").setStyle(coordinates));

        Component masked = DialogueTextHitTest.readyCopySpans(full, incomplete);
        Style waiting = masked.getSiblings().get(1).getStyle();
        assertEquals("Find 234,70,-98", masked.getString());
        assertTrue(waiting.isUnderlined());
        assertEquals(TextColor.fromRgb(0x237B80), waiting.getColor());
        assertNull(waiting.getClickEvent(), "a partially revealed coordinate must not copy a hidden suffix");
        assertNull(waiting.getHoverEvent(), "copy tooltip should appear only after the coordinates finish");

        Component enabled = DialogueTextHitTest.readyCopySpans(full, complete);
        assertEquals(Optional.of("234 70 -980"),
                DialogueTextHitTest.copyValue(enabled.getSiblings().getFirst().getStyle()),
                "the rest of the sentence may still be revealing");
        assertNotNull(enabled.getSiblings().getFirst().getStyle().getHoverEvent());
    }

    @Test
    void completedCopyDoesNotUnlockTheNextUnfinishedCopy() {
        Style first = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, "bridge"));
        Style second = Style.EMPTY.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, "hidden treasure"));
        Component full = Component.literal("Visit ").append(Component.literal("bridge").setStyle(first))
                .append(" then ").append(Component.literal("hidden treasure").setStyle(second));
        Component visible = Component.literal("Visit ").append(Component.literal("bridge").setStyle(first))
                .append(" then ").append(Component.literal("hid").setStyle(second));

        Component result = DialogueTextHitTest.readyCopySpans(full, visible);
        assertEquals(Optional.of("bridge"), DialogueTextHitTest.copyValue(result.getSiblings().get(1).getStyle()));
        assertTrue(DialogueTextHitTest.copyValue(result.getSiblings().get(3).getStyle()).isEmpty());
        assertTrue(DialogueTextHitTest.copyValue(Style.EMPTY).isEmpty(),
                "ordinary text must still follow its original dialogue click handling");
    }

    @Test
    void nestedCopyCannotExposeOuterTextWhileInnerTextReveals() {
        Component full = DialogueRichText.parse(Component.literal("<copy>Go <copy>north</copy> now</copy>!"));

        Component early = DialogueTextHitTest.readyCopySpans(full, visiblePrefix(full, 3));
        assertTrue(DialogueTextHitTest.copyValue(styleAt(early, 1)).isEmpty(),
                "the outer phrase must not copy the hidden 'north now'");

        Component middle = DialogueTextHitTest.readyCopySpans(full, visiblePrefix(full, 8));
        assertTrue(DialogueTextHitTest.copyValue(styleAt(middle, 1)).isEmpty(),
                "the unfinished outer phrase must still be inert");
        assertEquals(Optional.of("north"), DialogueTextHitTest.copyValue(styleAt(middle, 4)),
                "the completed inner phrase may be copied");

        Component complete = DialogueTextHitTest.readyCopySpans(full, visiblePrefix(full, 12));
        assertEquals(Optional.of("Go north now"), DialogueTextHitTest.copyValue(styleAt(complete, 1)));
    }

    @Test
    void outerCopyEndingImmediatelyAfterInnerRemainsLockedUntilInnerFinishes() {
        Component full = DialogueRichText.parse(Component.literal("<copy>Go <copy>north</copy></copy>!"));

        Component early = DialogueTextHitTest.readyCopySpans(full, visiblePrefix(full, 3));
        assertTrue(DialogueTextHitTest.copyValue(styleAt(early, 1)).isEmpty());

        Component complete = DialogueTextHitTest.readyCopySpans(full, visiblePrefix(full, 8));
        assertEquals(Optional.of("Go north"), DialogueTextHitTest.copyValue(styleAt(complete, 1)));
        assertEquals(Optional.of("north"), DialogueTextHitTest.copyValue(styleAt(complete, 4)));
    }

    @Test
    void adjacentEqualValueCopiesActivateIndependently() {
        Component full = DialogueRichText.parse(Component.literal("<copy>bridge</copy><copy>bridge</copy>!"));
        Component partial = DialogueTextHitTest.readyCopySpans(full, visiblePrefix(full, 9));

        assertEquals(Optional.of("bridge"), DialogueTextHitTest.copyValue(styleAt(partial, 1)));
        assertTrue(DialogueTextHitTest.copyValue(styleAt(partial, 7)).isEmpty(),
                "the second bridge must not copy until its own last character appears");
    }

    @Test
    void verticalGapBelowGlyphDoesNotActAsCopyTarget() {
        assertTrue(DialogueTextHitTest.locate(24, 19, 20, 10, 1, 10, 9, 0, 2, CLIP).isEmpty(),
                "the final pixel of the 10px line step is blank space beneath a 9px glyph");
    }

    private static Component visiblePrefix(Component full, int visibleLength) {
        var result = Component.empty();
        int[] remaining = {visibleLength};
        full.visit((style, text) -> {
            int take = Math.min(remaining[0], text.length());
            if (take > 0) {
                result.append(Component.literal(text.substring(0, take)).setStyle(style));
                remaining[0] -= take;
            }
            return remaining[0] == 0 ? Optional.of(Boolean.TRUE) : Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private static Style styleAt(Component text, int index) {
        int[] offset = {0};
        return text.visit((style, part) -> {
            if (offset[0] + part.length() > index) {
                return Optional.of(style);
            }
            offset[0] += part.length();
            return Optional.empty();
        }, Style.EMPTY).orElseThrow();
    }
}
