package net.conczin.mca.client.gui;

import net.conczin.mca.dialogue.DialogueEngine;
import net.conczin.mca.network.ClientHandlerImpl;
import net.conczin.mca.network.s2c.InteractionDialogueNodeResponse;
import net.conczin.mca.network.s2c.InteractionDialogueOptionsResponse;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DialogueRenderContentTest {
    private static final InteractScreen.ReplyIndicators READY = new InteractScreen.ReplyIndicators(false, false);

    @Test
    void unchangedMenuReusesRowsAndExpandingASectionInvalidatesThem() {
        var presentation = new ClientHandlerImpl.DialoguePresentation();
        presentation.beginRequest();
        assertTrue(presentation.acceptOptions(new InteractionDialogueOptionsResponse(1L, Optional.empty(), List.of(
                new InteractionDialogueOptionsResponse.EventOption(InteractionDialogueOptionsResponse.Mode.ASK,
                        ResourceLocation.parse("test:one"), Component.literal("First"), "relationship"),
                new InteractionDialogueOptionsResponse.EventOption(InteractionDialogueOptionsResponse.Mode.ASK,
                        ResourceLocation.parse("test:two"), Component.literal("Second"), "relationship")), false)));
        var cache = content(new AtomicInteger());
        var sections = EnumSet.noneOf(DialogueTopicSection.class);
        var collapsed = cache.rows(presentation, READY, sections);
        assertEquals(1, collapsed.size());
        assertSame(collapsed, cache.rows(presentation, READY, sections));

        sections.add(DialogueTopicSection.RELATIONSHIPS);
        var expanded = cache.rows(presentation, READY, sections);
        assertEquals(List.of("First", "Second"), expanded.subList(1, 3).stream()
                .map(row -> row.text().getString()).toList());
        assertSame(expanded, cache.rows(presentation, READY, sections));
        sections.clear();
        assertEquals(1, cache.rows(presentation, READY, sections).size());
    }

    @Test
    void sameOfferRefreshReplacesCachedChoices() {
        var presentation = new ClientHandlerImpl.DialoguePresentation();
        UUID session = UUID.randomUUID();
        assertTrue(presentation.acceptNode(node(session, "Old"), 0L, Locale.ENGLISH));
        presentation.revealFully();
        var cache = content(new AtomicInteger());
        var sections = EnumSet.noneOf(DialogueTopicSection.class);
        var old = cache.rows(presentation, READY, sections);
        assertEquals("Old", old.getFirst().text().getString());
        assertTrue(presentation.acceptNode(node(session, "New"), 40L, Locale.ENGLISH));
        var updated = cache.rows(presentation, READY, sections);
        assertEquals("New", updated.getFirst().text().getString());
        assertSame(updated, cache.rows(presentation, READY, sections));
    }

    @Test
    void bothMenuWidthsReuseMeasurementsUntilScaleOrContentChanges() {
        var wraps = new AtomicInteger();
        var cache = content(wraps);
        var rows = List.of(new InteractScreen.DialogueRow(Component.literal("Choice"), null));
        Object font = new Object();
        cache.layout(font, Language.getInstance(), 1.0f, 0);
        var wide = cache.measureRows(rows, 160);
        var narrow = cache.measureRows(rows, 152);
        assertSame(wide, cache.measureRows(rows, 160));
        assertSame(narrow, cache.measureRows(rows, 152));
        assertEquals(2, wraps.get(), "wrapping should occur once for each actual panel width");

        cache.layout(font, Language.getInstance(), 1.5f, 0);
        assertEquals(33, cache.measureRows(rows, 160).height());
        assertEquals(3, wraps.get());
        cache.measureRows(List.of(new InteractScreen.DialogueRow(Component.literal("New choice"), null)), 160);
        assertEquals(4, wraps.get());
    }

    @Test
    void fontOptionChangesAndScreenReinitializationInvalidateMeasurements() {
        var wraps = new AtomicInteger();
        var cache = content(wraps);
        Object font = new Object();
        Component speech = Component.literal("A dialogue line");
        cache.layout(font, Language.getInstance(), 1.0f, 0);
        cache.fullSpeech(speech, 120);
        cache.layout(font, Language.getInstance(), 1.0f, 1);
        cache.fullSpeech(speech, 120);
        assertEquals(2, wraps.get());
        cache.invalidateLayout();
        cache.fullSpeech(speech, 120);
        assertEquals(3, wraps.get());
    }

    @Test
    void speechWrapIsReusedBetweenRevealsAndFullLineHasSeparateMetrics() {
        var wraps = new AtomicInteger();
        var cache = content(wraps);
        Component full = Component.literal("Hello world");
        Component prefix = Component.literal("Hel");
        var fullLines = cache.fullSpeech(full, 120);
        var prefixLines = cache.visibleSpeech(full, prefix, 120);
        assertSame(fullLines, cache.fullSpeech(full, 120));
        assertSame(prefixLines, cache.visibleSpeech(full, prefix, 120));
        assertEquals(2, wraps.get());
        cache.visibleSpeech(full, Component.literal("Hell"), 120);
        assertEquals(3, wraps.get());
        assertSame(fullLines, cache.visibleSpeech(full, full, 120));
        assertEquals(3, wraps.get());
        cache.fullSpeech(full, 100);
        assertEquals(4, wraps.get(), "a resize must wrap at the new width");
    }

    private static DialogueRenderContent content(AtomicInteger wraps) {
        var content = new DialogueRenderContent((text, width) -> {
            wraps.incrementAndGet();
            return List.of(FormattedCharSequence.forward(text.getString(), Style.EMPTY));
        });
        content.layout(new Object(), Language.getInstance(), 1.0f, 0);
        return content;
    }

    private static InteractionDialogueNodeResponse node(UUID session, String choice) {
        return new InteractionDialogueNodeResponse(session, 2L, InteractionDialogueNodeResponse.State.ACTIVE,
                Optional.of(new InteractionDialogueNodeResponse.Node(ResourceLocation.parse("test:story"),
                        Component.literal("Question"), false, DialogueEngine.AdvanceKind.NONE,
                        List.of(new InteractionDialogueNodeResponse.Choice("answer", Component.literal(choice))))));
    }
}
