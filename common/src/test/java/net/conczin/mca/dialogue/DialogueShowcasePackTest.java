package net.conczin.mca.dialogue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueShowcasePackTest {
    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void optionalShowcaseIsAPlayableTranslatedEventGraph() throws IOException {
        Path pack = findRepoRoot().resolve("testpacks/dialogue-showcase");
        Path events = pack.resolve("datapack/data/showcase/dialogue_events");
        JsonObject translations = JsonParser.parseString(Files.readString(
                pack.resolve("resourcepack/assets/showcase/lang/en_us.json"))).getAsJsonObject();
        Map<ResourceLocation, DialogueEvent> loaded = new HashMap<>();
        try (var paths = Files.walk(events)) {
            for (Path path : paths.filter(Files::isRegularFile).filter(p -> p.toString().endsWith(".json")).toList()) {
                String name = events.relativize(path).toString().replace('\\', '/').replaceAll("\\.json$", "");
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("showcase", name);
                DialogueEvent event = DialogueEvent.decode(id, JsonParser.parseString(Files.readString(path)).getAsJsonObject());
                loaded.put(id, event);
                event.presentation().prompt().ifPresent(key -> assertTrue(translations.has(key), id + ": " + key));
                assertTrue(translations.has(event.presentation().resumePrompt()), id.toString());
                for (DialogueEvent.Node node : event.nodes().values()) {
                    for (String key : node.lines()) {
                        assertTrue(translations.has(key), id + ": " + key);
                    }
                    for (DialogueEvent.Choice choice : node.choices().orElse(List.of())) {
                        assertTrue(translations.has(choice.text()), id + ": " + choice.text());
                    }
                }
            }
        }

        assertEquals(7, loaded.size(), "showcase must exercise highlighted, ask, ambient and conditional content");
        var story = loaded.get(ResourceLocation.parse("showcase:story/bridge"));
        assertEquals(DialogueEvent.PresentationMode.ASK, story.presentation().mode());
        assertEquals(DialogueEvent.HistoryPolicy.STORY, story.history());
        assertEquals(100L, story.repeat().minTicks());
        assertEquals(100L, story.repeat().maxTicks());
        assertEquals(3, story.nodes().get(story.start()).lines().size());
        assertEquals(3, story.nodes().get(story.start()).choices().orElseThrow().size());

        var intro = loaded.get(ResourceLocation.parse("showcase:story/welcome"));
        assertEquals(DialogueEvent.PresentationMode.HIGHLIGHTED, intro.presentation().mode());
        assertEquals(DialogueEvent.RepeatType.ONCE, intro.repeat().type());
        assertTrue(DialogueEngine.repeatAvailable(intro, false, 0L, 0L));
        assertFalse(DialogueEngine.repeatAvailable(intro, true, 0L, 100L));
        assertFalse(DialogueEngine.repeatAvailable(story, true, 100L, 99L));
        assertTrue(DialogueEngine.repeatAvailable(story, true, 100L, 100L));

        var followup = loaded.get(ResourceLocation.parse("showcase:story/bridge_followup"));
        Set<DialogueCondition> conditions = Set.copyOf(followup.requirements());
        assertTrue(conditions.stream().anyMatch(c -> c instanceof DialogueCondition.EventCompleted completed
                && completed.event().equals(story.id())));
        assertTrue(conditions.stream().anyMatch(c -> c instanceof DialogueCondition.EventChoice choice
                && choice.event().equals(story.id()) && choice.choiceId().equals("listen")));

        for (DialogueEvent event : loaded.values()) {
            for (DialogueCondition condition : event.requirements()) {
                if (condition instanceof DialogueCondition.EventCompleted completed) {
                    assertTrue(loaded.containsKey(completed.event()), event.id().toString());
                }
                if (condition instanceof DialogueCondition.EventChoice choice) {
                    DialogueEvent referenced = loaded.get(choice.event());
                    assertTrue(referenced != null && referenced.nodes().values().stream()
                            .flatMap(node -> node.choices().orElse(List.of()).stream())
                            .anyMatch(candidate -> candidate.id().equals(choice.choiceId())), event.id().toString());
                }
            }
        }
        DialogueEngine.SelectionPlan plan = DialogueEngine.planSelection(
                loaded.values(), e -> e.requirements().isEmpty(), null);
        assertEquals(intro.id(), plan.highlighted().orElseThrow().id());
        assertTrue(plan.ask().stream().anyMatch(e -> e.id().equals(story.id())));
        assertFalse(plan.ask().stream().anyMatch(e -> e.id().equals(followup.id())));
        assertEquals(2, plan.ambient().size());
        assertEquals(Set.of(1.0, 3.0), plan.ambient().stream()
                .map(DialogueEvent::weight).collect(Collectors.toSet()));
        assertFalse(story.nodes().values().stream().filter(node -> node.complete()).collect(Collectors.toSet()).isEmpty());
    }

    private static Path findRepoRoot() {
        for (Path current = Path.of("").toAbsolutePath(); current != null; current = current.getParent()) {
            if (Files.isRegularFile(current.resolve("settings.gradle"))
                    && Files.isDirectory(current.resolve("testpacks"))) {
                return current;
            }
        }
        throw new IllegalStateException("Could not locate repository testpacks from working directory");
    }
}
