package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import net.conczin.mca.MCA;
import net.conczin.mca.dialogue.DialogueCondition;
import net.conczin.mca.dialogue.DialogueEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class DialogueEvents extends SimpleJsonResourceReloadListener {
    public static final ResourceLocation ID = MCA.locate("dialogue_events");
    public static final DialogueEvents INSTANCE = new DialogueEvents();

    private volatile Snapshot snapshot = new Snapshot(Map.of(), 0L);

    public DialogueEvents() {
        super(Resources.GSON, ID.getPath());
    }

    public Optional<DialogueEvent> get(ResourceLocation id) {
        return Optional.ofNullable(snapshot.events().get(id));
    }

    public Collection<DialogueEvent> all() {
        return snapshot.events().values();
    }

    public long generation() {
        return snapshot.generation();
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> data, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, DialogueEvent> loaded = new LinkedHashMap<>();
        data.forEach((id, element) -> {
            try {
                loaded.put(id, DialogueEvent.decode(id, element.getAsJsonObject()));
            } catch (RuntimeException exception) {
                MCA.LOGGER.warn("Dialogue event {} was not loaded: {}", id, exception.getMessage());
            }
        });

        validateHistoryReferences(loaded);
        warnPrerequisiteCycles(loaded);

        Snapshot previous = snapshot;
        snapshot = new Snapshot(Map.copyOf(loaded), previous.generation() + 1L);
    }

    private static void validateHistoryReferences(Map<ResourceLocation, DialogueEvent> events) {
        while (true) {
            Map<ResourceLocation, String> invalid = new LinkedHashMap<>();
            for (DialogueEvent event : events.values()) {
                try {
                    validateHistoryReferences(event, events);
                } catch (IllegalArgumentException exception) {
                    invalid.put(event.id(), exception.getMessage());
                }
            }
            if (invalid.isEmpty()) {
                return;
            }
            invalid.forEach((id, reason) -> {
                MCA.LOGGER.warn("Dialogue event {} was not loaded: {}", id, reason);
                events.remove(id);
            });
        }
    }

    private static void validateHistoryReferences(
            DialogueEvent event,
            Map<ResourceLocation, DialogueEvent> events
    ) {
        forEachHistoryReference(event, reference -> {
            DialogueEvent target = events.get(reference.event());
            if (target == null) {
                if (MCA.MOD_ID.equals(reference.event().getNamespace())) {
                    throw new IllegalArgumentException("missing shipped history event " + reference.event());
                }
                return;
            }
            if (target.history() != DialogueEvent.HistoryPolicy.STORY) {
                throw new IllegalArgumentException("history requirement targets scheduling-only event " + reference.event());
            }
            reference.choiceId().ifPresent(choiceId -> {
                if (!choiceIds(target).contains(choiceId)) {
                    throw new IllegalArgumentException(
                            "history requirement targets unknown choice '" + choiceId + "' in " + reference.event()
                    );
                }
            });
        });
    }

    private static Set<String> choiceIds(DialogueEvent event) {
        Set<String> choiceIds = new HashSet<>();
        for (DialogueEvent.Node node : event.nodes().values()) {
            for (DialogueEvent.Choice choice : node.choices().orElse(List.of())) {
                choiceIds.add(choice.id());
            }
        }
        return choiceIds;
    }

    private static void forEachHistoryReference(DialogueEvent event, Consumer<HistoryReference> consumer) {
        for (DialogueCondition condition : event.requirements()) {
            forEachHistoryReference(condition, consumer);
        }
        for (DialogueEvent.Node node : event.nodes().values()) {
            for (DialogueEvent.Choice choice : node.choices().orElse(List.of())) {
                for (DialogueCondition condition : choice.requirements()) {
                    forEachHistoryReference(condition, consumer);
                }
                for (DialogueEvent.Outcome outcome : choice.outcomes().orElse(List.of())) {
                    for (DialogueCondition condition : outcome.requirements()) {
                        forEachHistoryReference(condition, consumer);
                    }
                }
            }
        }
    }

    private static void forEachHistoryReference(DialogueCondition condition, Consumer<HistoryReference> consumer) {
        if (condition instanceof DialogueCondition.EventCompleted completed) {
            consumer.accept(new HistoryReference(completed.event(), Optional.empty()));
        } else if (condition instanceof DialogueCondition.EventChoice choice) {
            consumer.accept(new HistoryReference(choice.event(), Optional.of(choice.choiceId())));
        } else if (condition instanceof DialogueCondition.Not not) {
            forEachHistoryReference(not.condition(), consumer);
        }
    }

    private static void warnPrerequisiteCycles(Map<ResourceLocation, DialogueEvent> events) {
        Set<ResourceLocation> complete = new HashSet<>();
        LinkedHashSet<ResourceLocation> visiting = new LinkedHashSet<>();
        for (ResourceLocation id : events.keySet()) {
            warnPrerequisiteCycles(id, events, visiting, complete);
        }
    }

    private static void warnPrerequisiteCycles(
            ResourceLocation id,
            Map<ResourceLocation, DialogueEvent> events,
            LinkedHashSet<ResourceLocation> visiting,
            Set<ResourceLocation> complete
    ) {
        if (complete.contains(id)) {
            return;
        }
        visiting.add(id);
        for (ResourceLocation dependency : historyDependencies(events.get(id))) {
            if (!events.containsKey(dependency)) {
                continue;
            }
            if (visiting.contains(dependency)) {
                List<ResourceLocation> cycle = new ArrayList<>();
                boolean copy = false;
                for (ResourceLocation member : visiting) {
                    if (member.equals(dependency)) {
                        copy = true;
                    }
                    if (copy) {
                        cycle.add(member);
                    }
                }
                cycle.add(dependency);
                MCA.LOGGER.warn("Dialogue event prerequisite cycle detected: {}", cycle);
                continue;
            }
            warnPrerequisiteCycles(dependency, events, visiting, complete);
        }
        visiting.remove(id);
        complete.add(id);
    }

    private static Set<ResourceLocation> historyDependencies(DialogueEvent event) {
        Set<ResourceLocation> dependencies = new LinkedHashSet<>();
        forEachHistoryReference(event, reference -> dependencies.add(reference.event()));
        return dependencies;
    }

    private record HistoryReference(ResourceLocation event, Optional<String> choiceId) {
    }

    private record Snapshot(Map<ResourceLocation, DialogueEvent> events, long generation) {
    }
}
