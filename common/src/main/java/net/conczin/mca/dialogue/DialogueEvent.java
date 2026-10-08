package net.conczin.mca.dialogue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record DialogueEvent(
        ResourceLocation id,
        Trigger trigger,
        Presentation presentation,
        int priority,
        double weight,
        List<DialogueCondition> requirements,
        Repeat repeat,
        HistoryPolicy history,
        String start,
        Map<String, Node> nodes
) {
    public static final int MAX_CHOICES = 32;
    public static final int MAX_CHOICE_ID_LENGTH = 96;

    private static final Codec<DialogueEvent> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Trigger.CODEC.fieldOf("trigger").forGetter(DialogueEvent::trigger),
            Presentation.CODEC.fieldOf("presentation").forGetter(DialogueEvent::presentation),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(DialogueEvent::priority),
            Codec.DOUBLE.optionalFieldOf("weight", 1.0).forGetter(DialogueEvent::weight),
            DialogueCondition.CODEC.listOf().optionalFieldOf("requirements", List.of()).forGetter(DialogueEvent::requirements),
            Repeat.CODEC.fieldOf("repeat").forGetter(DialogueEvent::repeat),
            HistoryPolicy.CODEC.optionalFieldOf("history", HistoryPolicy.STORY).forGetter(DialogueEvent::history),
            DialogueCodecs.nonblankStringCodec("start").fieldOf("start").forGetter(DialogueEvent::start),
            Codec.unboundedMap(Codec.STRING, Node.CODEC).fieldOf("nodes").forGetter(DialogueEvent::nodes)
    ).apply(instance, (trigger, presentation, priority, weight, requirements, repeat, history, start, nodes) ->
            new DialogueEvent(null, trigger, presentation, priority, weight, requirements, repeat, history, start, nodes)));

    public DialogueEvent {
        requirements = List.copyOf(requirements);
        nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
    }

    public static DialogueEvent decode(ResourceLocation id, JsonObject json) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(json, "json");
        try {
            validateKnownFields(json);
            DialogueEvent decoded = CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();
            DialogueEvent event = new DialogueEvent(
                    id,
                    decoded.trigger,
                    decoded.presentation,
                    decoded.priority,
                    decoded.weight,
                    decoded.requirements,
                    decoded.repeat,
                    decoded.history,
                    decoded.start,
                    decoded.nodes
            );
            event.validate();
            return event;
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegal && illegal.getMessage() != null
                    && illegal.getMessage().startsWith("Dialogue event ")) {
                throw illegal;
            }
            throw new IllegalArgumentException("Dialogue event " + id + " is invalid: " + exception.getMessage(), exception);
        }
    }

    public void validate() {
        if (id == null) {
            throw invalid("missing external resource ID");
        }
        if (!Double.isFinite(weight) || weight <= 0.0) {
            throw invalid("weight must be finite and positive");
        }
        presentation.validate();
        repeat.validate();
        if (history == HistoryPolicy.SCHEDULING && repeat.type != RepeatType.COOLDOWN) {
            throw invalid("history 'scheduling' requires repeat type 'cooldown'");
        }
        if (!nodes.containsKey(start)) {
            throw invalid("start node '" + start + "' does not exist");
        }

        Set<String> choiceIds = new HashSet<>();
        for (Map.Entry<String, Node> entry : nodes.entrySet()) {
            String nodeId = entry.getKey();
            if (nodeId.isBlank()) {
                throw invalid("node ID must not be blank");
            }
            Node node = entry.getValue();
            node.validate(nodeId);
            for (Outcome outcome : node.outcomes.orElse(List.of())) {
                outcome.validate("node '" + nodeId + "'");
                requireNode(outcome.next, "outcome of node '" + nodeId + "'");
            }
            for (Choice choice : node.choices.orElse(List.of())) {
                if (!choiceIds.add(choice.id)) {
                    throw invalid("duplicate choice ID '" + choice.id + "'");
                }
                choice.validate(nodeId);
                if (choice.next.isPresent()) {
                    requireNode(choice.next.get(), "choice '" + choice.id + "'");
                }
                for (Outcome outcome : choice.outcomes.orElse(List.of())) {
                    outcome.validate(choice.id);
                    requireNode(outcome.next, "outcome of choice '" + choice.id + "'");
                }
            }
            node.next.ifPresent(next -> requireNode(next, "node '" + nodeId + "'"));
        }

        rejectCycles();
        validateReachableTerminals();
    }

    private void validateReachableTerminals() {
        Set<String> reachable = new HashSet<>();
        ArrayDeque<String> pending = new ArrayDeque<>();
        pending.add(start);
        while (!pending.isEmpty()) {
            String nodeId = pending.removeFirst();
            if (!reachable.add(nodeId)) {
                continue;
            }
            for (String next : outgoing(nodes.get(nodeId))) {
                pending.addLast(next);
            }
        }
        if (repeat.type == RepeatType.ALWAYS) {
            return;
        }
        for (String nodeId : reachable) {
            Node node = nodes.get(nodeId);
            if (node.end && !node.retryable) {
                throw invalid("reachable non-completing terminal '" + nodeId + "' requires retryable: true");
            }
        }
    }

    private void rejectCycles() {
        Set<String> complete = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String nodeId : nodes.keySet()) {
            visit(nodeId, visiting, complete);
        }
    }

    private void visit(String nodeId, Set<String> visiting, Set<String> complete) {
        if (complete.contains(nodeId)) {
            return;
        }
        if (!visiting.add(nodeId)) {
            throw invalid("conversation graph contains a cycle at node '" + nodeId + "'");
        }
        for (String next : outgoing(nodes.get(nodeId))) {
            visit(next, visiting, complete);
        }
        visiting.remove(nodeId);
        complete.add(nodeId);
    }

    private static List<String> outgoing(Node node) {
        List<String> outgoing = new ArrayList<>();
        node.next.ifPresent(outgoing::add);
        for (Outcome outcome : node.outcomes.orElse(List.of())) {
            outgoing.add(outcome.next);
        }
        for (Choice choice : node.choices.orElse(List.of())) {
            choice.next.ifPresent(outgoing::add);
            for (Outcome outcome : choice.outcomes.orElse(List.of())) {
                outgoing.add(outcome.next);
            }
        }
        return outgoing;
    }

    private void requireNode(String target, String source) {
        if (!nodes.containsKey(target)) {
            throw invalid(source + " points to missing node '" + target + "'");
        }
    }

    private IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Dialogue event " + id + " is invalid: " + message);
    }

    private static void validateKnownFields(JsonObject json) {
        requireOnly(json, Set.of("trigger", "presentation", "priority", "weight", "requirements", "repeat", "history", "start", "nodes"), "event");
        if (json.has("presentation") && json.get("presentation").isJsonObject()) {
            requireOnly(json.getAsJsonObject("presentation"), Set.of("mode", "prompt", "resume_prompt", "topic"), "presentation");
        }
        if (json.has("repeat") && json.get("repeat").isJsonObject()) {
            requireOnly(json.getAsJsonObject("repeat"), Set.of("type", "seconds", "min_seconds", "max_seconds"), "repeat");
        }
        if (json.has("nodes") && json.get("nodes").isJsonObject()) {
            for (Map.Entry<String, JsonElement> nodeEntry : json.getAsJsonObject("nodes").entrySet()) {
                if (!nodeEntry.getValue().isJsonObject()) {
                    continue;
                }
                JsonObject node = nodeEntry.getValue().getAsJsonObject();
                requireOnly(node, Set.of("line", "lines", "choices", "outcomes", "next", "complete", "end", "retryable", "silent"), "node '" + nodeEntry.getKey() + "'");
                if (node.has("outcomes") && node.get("outcomes").isJsonArray()) {
                    for (JsonElement outcomeElement : node.getAsJsonArray("outcomes")) {
                        if (outcomeElement.isJsonObject()) {
                            requireOnly(outcomeElement.getAsJsonObject(), Set.of("requirements", "weight", "actions", "next"), "outcome");
                        }
                    }
                }
                if (node.has("choices") && node.get("choices").isJsonArray()) {
                    for (JsonElement choiceElement : node.getAsJsonArray("choices")) {
                        if (!choiceElement.isJsonObject()) {
                            continue;
                        }
                        JsonObject choice = choiceElement.getAsJsonObject();
                        requireOnly(choice, Set.of("id", "text", "requirements", "actions", "next", "outcomes"), "choice");
                        if (choice.has("outcomes") && choice.get("outcomes").isJsonArray()) {
                            for (JsonElement outcomeElement : choice.getAsJsonArray("outcomes")) {
                                if (outcomeElement.isJsonObject()) {
                                    requireOnly(outcomeElement.getAsJsonObject(), Set.of("requirements", "weight", "actions", "next"), "outcome");
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static void requireOnly(JsonObject object, Set<String> allowed, String context) {
        for (String field : object.keySet()) {
            if (!allowed.contains(field)) {
                throw new IllegalArgumentException("Unknown field '" + field + "' in " + context);
            }
        }
    }

    public enum Trigger {
        TALK;

        public static final Codec<Trigger> CODEC = DialogueCodecs.enumCodec(Trigger.class);
    }

    public enum PresentationMode {
        HIGHLIGHTED,
        ASK,
        AMBIENT;

        public static final Codec<PresentationMode> CODEC = DialogueCodecs.enumCodec(PresentationMode.class);
    }

    public enum RepeatType {
        ALWAYS,
        ONCE,
        COOLDOWN;

        public static final Codec<RepeatType> CODEC = DialogueCodecs.enumCodec(RepeatType.class);
    }

    public enum HistoryPolicy {
        STORY,
        SCHEDULING;

        public static final Codec<HistoryPolicy> CODEC = DialogueCodecs.enumCodec(HistoryPolicy.class);
    }

    public record Presentation(PresentationMode mode, Optional<String> prompt, String resumePrompt, Optional<String> topic) {
        private static final Codec<Presentation> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                PresentationMode.CODEC.fieldOf("mode").forGetter(Presentation::mode),
                DialogueCodecs.nonblankStringCodec("prompt").optionalFieldOf("prompt").forGetter(Presentation::prompt),
                DialogueCodecs.nonblankStringCodec("resume_prompt").fieldOf("resume_prompt").forGetter(Presentation::resumePrompt),
                DialogueCodecs.nonblankStringCodec("topic").optionalFieldOf("topic").forGetter(Presentation::topic)
        ).apply(instance, Presentation::new));

        private void validate() {
            if (mode != PresentationMode.AMBIENT && prompt.isEmpty()) {
                throw new IllegalArgumentException("Selectable presentation mode " + mode + " requires prompt");
            }
        }
    }

    public record Repeat(RepeatType type, long minTicks, long maxTicks) {
        private static final Codec<Repeat> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                RepeatType.CODEC.fieldOf("type").forGetter(Repeat::type),
                Codec.DOUBLE.optionalFieldOf("seconds").forGetter(repeat -> Optional.empty()),
                Codec.DOUBLE.optionalFieldOf("min_seconds").forGetter(repeat -> Optional.empty()),
                Codec.DOUBLE.optionalFieldOf("max_seconds").forGetter(repeat -> Optional.empty())
        ).apply(instance, Repeat::fromSecondsFields));

        private static Repeat fromSecondsFields(
                RepeatType type,
                Optional<Double> seconds,
                Optional<Double> minSeconds,
                Optional<Double> maxSeconds
        ) {
            if (type != RepeatType.COOLDOWN) {
                if (seconds.isPresent() || minSeconds.isPresent() || maxSeconds.isPresent()) {
                    throw new IllegalArgumentException(type.name().toLowerCase(java.util.Locale.ROOT) + " repeat must not define cooldown duration");
                }
                return new Repeat(type, 0L, 0L);
            }

            boolean fixed = seconds.isPresent();
            boolean range = minSeconds.isPresent() || maxSeconds.isPresent();
            if (fixed == range) {
                throw new IllegalArgumentException("cooldown requires exactly seconds or min_seconds + max_seconds");
            }
            if (fixed) {
                long ticks = secondsToTicks(seconds.orElseThrow(), "seconds");
                return new Repeat(type, ticks, ticks);
            }
            if (minSeconds.isEmpty() || maxSeconds.isEmpty()) {
                throw new IllegalArgumentException("cooldown range requires both min_seconds and max_seconds");
            }
            double min = minSeconds.orElseThrow();
            double max = maxSeconds.orElseThrow();
            if (!Double.isFinite(min) || !Double.isFinite(max) || min < 0.0 || max < 0.0 || min > max) {
                throw new IllegalArgumentException("invalid cooldown seconds range");
            }
            return new Repeat(type, secondsToTicks(min, "min_seconds"), secondsToTicks(max, "max_seconds"));
        }

        private static long secondsToTicks(double seconds, String field) {
            if (!Double.isFinite(seconds) || seconds < 0.0) {
                throw new IllegalArgumentException(field + " must be finite and nonnegative");
            }
            double ticks = seconds * 20.0;
            if (!Double.isFinite(ticks) || ticks > Long.MAX_VALUE) {
                throw new IllegalArgumentException(field + " is too large to represent as game ticks");
            }
            return (long) Math.ceil(ticks);
        }

        private void validate() {
            if (minTicks < 0L || maxTicks < minTicks) {
                throw new IllegalArgumentException("invalid normalized repeat tick range");
            }
        }
    }

    public record Node(
            List<String> lines,
            Optional<List<Choice>> choices,
            Optional<List<Outcome>> outcomes,
            Optional<String> next,
            boolean complete,
            boolean end,
            boolean retryable,
            boolean silent
    ) {
        private static final Codec<Node> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                DialogueCodecs.nonblankStringCodec("line").optionalFieldOf("line").forGetter(node -> node.lines.size() == 1 ? Optional.of(node.lines.get(0)) : Optional.empty()),
                DialogueCodecs.nonblankStringCodec("line").listOf().optionalFieldOf("lines").forGetter(node -> node.lines.size() > 1 ? Optional.of(node.lines) : Optional.empty()),
                Choice.CODEC.listOf().optionalFieldOf("choices").forGetter(Node::choices),
                Outcome.CODEC.listOf().optionalFieldOf("outcomes").forGetter(Node::outcomes),
                DialogueCodecs.nonblankStringCodec("next").optionalFieldOf("next").forGetter(Node::next),
                Codec.BOOL.optionalFieldOf("complete", false).forGetter(Node::complete),
                Codec.BOOL.optionalFieldOf("end", false).forGetter(Node::end),
                Codec.BOOL.optionalFieldOf("retryable", false).forGetter(Node::retryable),
                Codec.BOOL.optionalFieldOf("silent", false).forGetter(Node::silent)
        ).apply(instance, Node::fromFields));

        public Node {
            lines = List.copyOf(lines);
            choices = choices.map(List::copyOf);
            outcomes = outcomes.map(List::copyOf);
        }

        public Node(
                List<String> lines,
                Optional<List<Choice>> choices,
                Optional<String> next,
                boolean complete,
                boolean end,
                boolean retryable,
                boolean silent
        ) {
            this(lines, choices, Optional.empty(), next, complete, end, retryable, silent);
        }

        private static Node fromFields(
                Optional<String> line,
                Optional<List<String>> lines,
                Optional<List<Choice>> choices,
                Optional<List<Outcome>> outcomes,
                Optional<String> next,
                boolean complete,
                boolean end,
                boolean retryable,
                boolean silent
        ) {
            if (line.isPresent() && lines.isPresent()) {
                throw new IllegalArgumentException("node cannot define both line and lines");
            }
            boolean automatic = outcomes.isPresent();
            if (automatic && (line.isPresent() || lines.isPresent())) {
                throw new IllegalArgumentException("automatic routing node must not define line or lines");
            }
            if (!automatic && line.isEmpty() && lines.isEmpty()) {
                throw new IllegalArgumentException("visible node requires exactly one of line or lines");
            }
            List<String> normalized = line.map(List::of).orElseGet(() -> lines.orElse(List.of()));
            if (!automatic && normalized.isEmpty()) {
                throw new IllegalArgumentException("node lines must not be empty");
            }
            return new Node(normalized, choices, outcomes, next, complete, end, retryable, silent);
        }

        private void validate(String nodeId) {
            int continuations = (choices.isPresent() ? 1 : 0)
                    + (outcomes.isPresent() ? 1 : 0)
                    + (next.isPresent() ? 1 : 0)
                    + (complete ? 1 : 0)
                    + (end ? 1 : 0);
            if (continuations != 1) {
                throw new IllegalArgumentException("node '" + nodeId + "' requires exactly one continuation shape");
            }
            if (choices.isPresent() && choices.orElseThrow().isEmpty()) {
                throw new IllegalArgumentException("node '" + nodeId + "' choices must not be empty");
            }
            if (choices.isPresent() && choices.orElseThrow().size() > MAX_CHOICES) {
                throw new IllegalArgumentException("node '" + nodeId + "' has too many choices");
            }
            if (outcomes.isPresent()) {
                List<Outcome> routes = outcomes.orElseThrow();
                if (routes.isEmpty()) {
                    throw new IllegalArgumentException("node '" + nodeId + "' outcomes must not be empty");
                }
                if (routes.stream().noneMatch(outcome -> outcome.requirements().isEmpty())) {
                    throw new IllegalArgumentException("automatic routing node '" + nodeId + "' requires an unconditional outcome");
                }
                if (!lines.isEmpty()) {
                    throw new IllegalArgumentException("automatic routing node '" + nodeId + "' must not expose dialogue lines");
                }
            }
        }
    }

    public record Choice(
            String id,
            String text,
            List<DialogueCondition> requirements,
            List<DialogueAction> actions,
            Optional<String> next,
            Optional<List<Outcome>> outcomes
    ) {
        private static final Codec<Choice> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                DialogueCodecs.nonblankStringCodec("choice id").fieldOf("id").forGetter(Choice::id),
                DialogueCodecs.nonblankStringCodec("choice text").fieldOf("text").forGetter(Choice::text),
                DialogueCondition.CODEC.listOf().optionalFieldOf("requirements", List.of()).forGetter(Choice::requirements),
                DialogueAction.CODEC.listOf().optionalFieldOf("actions", List.of()).forGetter(Choice::actions),
                DialogueCodecs.nonblankStringCodec("next").optionalFieldOf("next").forGetter(Choice::next),
                Outcome.CODEC.listOf().optionalFieldOf("outcomes").forGetter(Choice::outcomes)
        ).apply(instance, Choice::new));

        public Choice {
            requirements = List.copyOf(requirements);
            actions = List.copyOf(actions);
            outcomes = outcomes.map(List::copyOf);
        }

        private void validate(String nodeId) {
            if (id.length() > MAX_CHOICE_ID_LENGTH) {
                throw new IllegalArgumentException("choice ID in node '" + nodeId + "' exceeds " + MAX_CHOICE_ID_LENGTH + " characters");
            }
            if (outcomes.isPresent()) {
                if (outcomes.orElseThrow().isEmpty()) {
                    throw new IllegalArgumentException("choice '" + id + "' in node '" + nodeId + "' has empty outcomes");
                }
                if (next.isPresent() || !actions.isEmpty()) {
                    throw new IllegalArgumentException("choice '" + id + "' mixes direct path fields with outcomes");
                }
            } else if (next.isEmpty()) {
                throw new IllegalArgumentException("direct choice '" + id + "' requires next");
            }
        }
    }

    public record Outcome(
            List<DialogueCondition> requirements,
            double weight,
            List<DialogueAction> actions,
            String next
    ) {
        private static final Codec<Outcome> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                DialogueCondition.CODEC.listOf().optionalFieldOf("requirements", List.of()).forGetter(Outcome::requirements),
                Codec.DOUBLE.optionalFieldOf("weight", 1.0).forGetter(Outcome::weight),
                DialogueAction.CODEC.listOf().optionalFieldOf("actions", List.of()).forGetter(Outcome::actions),
                DialogueCodecs.nonblankStringCodec("next").fieldOf("next").forGetter(Outcome::next)
        ).apply(instance, Outcome::new));

        public Outcome {
            requirements = List.copyOf(requirements);
            actions = List.copyOf(actions);
        }

        private void validate(String choiceId) {
            if (!Double.isFinite(weight) || weight <= 0.0) {
                throw new IllegalArgumentException("outcome weight for choice '" + choiceId + "' must be finite and positive");
            }
        }
    }
}

final class DialogueCodecs {
    private DialogueCodecs() {
    }

    static Codec<String> nonblankStringCodec(String name) {
        return Codec.STRING.comapFlatMap(value -> value.isBlank()
                ? DataResult.error(() -> name + " must not be blank")
                : DataResult.success(value), value -> value);
    }

    static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.comapFlatMap(value -> {
            for (E constant : type.getEnumConstants()) {
                if (constant.name().equalsIgnoreCase(value)) {
                    return DataResult.success(constant);
                }
            }
            return DataResult.error(() -> "Unknown " + type.getSimpleName() + ": " + value);
        }, constant -> constant.name().toLowerCase(java.util.Locale.ROOT));
    }
}
