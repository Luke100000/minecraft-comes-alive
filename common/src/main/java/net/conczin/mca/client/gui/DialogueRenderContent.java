package net.conczin.mca.client.gui;

import net.conczin.mca.network.ClientHandlerImpl;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/** Prepares dialogue content independently of animated viewport geometry. */
final class DialogueRenderContent {
    private final BiFunction<Component, Integer, List<FormattedCharSequence>> split;
    private Object fontIdentity;
    private Language language;
    private float scale;
    private int fontOptions;
    private RowState rowState;
    private List<InteractScreen.DialogueRow> preparedRows;
    private List<InteractScreen.DialogueRow> measuredSource;
    private final Map<Integer, MeasuredRows> measurements = new HashMap<>();
    private Component speechSource;
    private DialogueTextHitTest.CopySpans copySpans;
    private Component visibleSource;
    private int fullWidth;
    private int visibleWidth;
    private List<FormattedCharSequence> fullLines;
    private List<FormattedCharSequence> visibleLines;

    DialogueRenderContent(BiFunction<Component, Integer, List<FormattedCharSequence>> split) {
        this.split = split;
    }

    void layout(Object fontIdentity, Language language, float scale, int fontOptions) {
        if (this.fontIdentity == fontIdentity && this.language == language && this.scale == scale
                && this.fontOptions == fontOptions) {
            return;
        }
        if (this.language != language) {
            rowState = null;
        }
        this.fontIdentity = fontIdentity;
        this.language = language;
        this.scale = scale;
        this.fontOptions = fontOptions;
        invalidateMeasurements();
    }

    void invalidateLayout() {
        rowState = null;
        invalidateMeasurements();
    }

    private void invalidateMeasurements() {
        measurements.clear();
        speechSource = null;
        copySpans = null;
        visibleSource = null;
        fullLines = null;
        visibleLines = null;
    }

    List<InteractScreen.DialogueRow> rows(ClientHandlerImpl.DialoguePresentation presentation,
                                        InteractScreen.ReplyIndicators indicators,
                                        Set<DialogueTopicSection> expanded) {
        var options = presentation.options().orElse(null);
        var node = presentation.node().orElse(null);
        var history = presentation.reviewedEntry().orElse(null);
        var choices = presentation.visibleChoices();
        var advance = presentation.advanceKind().orElse(null);
        if (rowState == null || rowState.options != options || rowState.node != node
                || rowState.history != history || rowState.choices != choices || rowState.advance != advance
                || !rowState.indicators.equals(indicators) || !rowState.expanded.equals(expanded)) {
            rowState = new RowState(options, node, history, choices, advance, indicators, Set.copyOf(expanded));
            preparedRows = InteractScreen.buildDialogueRows(presentation, indicators, expanded);
        }
        return preparedRows;
    }

    MeasuredRows measureRows(List<InteractScreen.DialogueRow> rows, int panelWidth) {
        if (measuredSource != rows) {
            measuredSource = rows;
            measurements.clear();
        }
        MeasuredRows cached = measurements.get(panelWidth);
        if (cached != null) {
            return cached;
        }
        var measured = rows.stream().map(row -> {
            var insets = DialogueRowLayout.insets(panelWidth, scale, row.kind());
            List<FormattedCharSequence> lines = row.placeholder() ? List.of() : wrap(row.text(), insets.textWidth());
            int height = row.placeholder() ? Math.round(26 * scale)
                    : DialogueRowLayout.height(scale, lines.size(), row.kind());
            return new MeasuredRow(row, insets, lines, height);
        }).toList();
        var result = new MeasuredRows(measured, measured.stream().mapToInt(MeasuredRow::height).sum());
        if (measurements.size() >= 2) {
            measurements.clear();
        }
        measurements.put(panelWidth, result);
        return result;
    }

    List<FormattedCharSequence> fullSpeech(Component full, int width) {
        setSpeechSource(full);
        if (fullLines == null || fullWidth != width) {
            fullWidth = width;
            fullLines = wrap(full, width);
        }
        return fullLines;
    }

    List<FormattedCharSequence> visibleSpeech(Component full, Component visible, int width) {
        if (full == visible) {
            return fullSpeech(full, width);
        }
        setSpeechSource(full);
        if (visibleLines == null || visibleSource != visible || visibleWidth != width) {
            visibleSource = visible;
            visibleWidth = width;
            if (copySpans == null) {
                copySpans = DialogueTextHitTest.prepareCopySpans(full);
            }
            visibleLines = wrap(copySpans.ready(visible), width);
        }
        return visibleLines;
    }

    private void setSpeechSource(Component full) {
        if (speechSource != full) {
            speechSource = full;
            copySpans = null;
            fullLines = null;
            visibleLines = null;
        }
    }

    private List<FormattedCharSequence> wrap(Component text, int width) {
        return split.apply(text, Math.max(1, (int) (width / scale)));
    }

    record MeasuredRow(InteractScreen.DialogueRow row, DialogueRowLayout.Insets insets,
                       List<FormattedCharSequence> lines, int height) {}

    record MeasuredRows(List<MeasuredRow> rows, int height) {}

    private record RowState(Object options, Object node, Object history, Object choices, Object advance,
                            InteractScreen.ReplyIndicators indicators, Set<DialogueTopicSection> expanded) {}
}
