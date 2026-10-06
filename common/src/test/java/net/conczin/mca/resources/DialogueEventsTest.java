package net.conczin.mca.resources;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.conczin.mca.dialogue.DialogueEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DialogueEventsTest {
    @TempDir
    Path tempDir;

    @Test
    void preservesNamespaceAndNestedPathForCoexistingEvents() {
        ResourceLocation mca = id("mca", "personal/gloomy_reflection");
        ResourceLocation addon = id("example_addon", "personal/gloomy_reflection");
        DialogueEvents events = new DialogueEvents();

        events.apply(data(
                mca, simpleEvent("mca.prompt"),
                addon, simpleEvent("addon.prompt")
        ), null, null);

        assertEquals(mca, events.get(mca).orElseThrow().id());
        assertEquals(addon, events.get(addon).orElseThrow().id());
        assertEquals(2, events.all().size());
        assertEquals(1L, events.generation());
    }

    @Test
    void replacesWholeReloadSnapshotAndIsolatesMalformedResources() {
        ResourceLocation replaced = id("mca", "story/replaced");
        ResourceLocation removed = id("mca", "story/removed");
        ResourceLocation malformed = id("example_addon", "story/malformed");
        DialogueEvents events = new DialogueEvents();

        events.apply(data(
                replaced, simpleEvent("old.prompt"),
                removed, simpleEvent("removed.prompt")
        ), null, null);
        Collection<DialogueEvent> firstSnapshot = events.all();

        events.apply(data(
                replaced, simpleEvent("new.prompt"),
                malformed, malformedEvent()
        ), null, null);

        assertEquals("new.prompt", events.get(replaced).orElseThrow().presentation().prompt().orElseThrow());
        assertFalse(events.get(removed).isPresent());
        assertFalse(events.get(malformed).isPresent());
        assertEquals(2, firstSnapshot.size());
        assertEquals(1, events.all().size());
        assertEquals(2L, events.generation());
    }

    @Test
    void higherPriorityPackReplacesWholeResourceBeforeDialogueDecoding() throws IOException {
        Path lower = tempDir.resolve("lower");
        Path upper = tempDir.resolve("upper");
        Path relative = Path.of("data", "mca", "dialogue_events", "story", "priority.json");
        Files.createDirectories(lower.resolve(relative).getParent());
        Files.createDirectories(upper.resolve(relative).getParent());
        Files.writeString(lower.resolve(relative), simpleEvent("lower.prompt"));
        Files.writeString(upper.resolve(relative), simpleEvent("upper.prompt"));

        try (PathPackResources lowerPack = pack("lower", lower);
             PathPackResources upperPack = pack("upper", upper);
             MultiPackResourceManager manager = new MultiPackResourceManager(
                     PackType.SERVER_DATA, java.util.List.of(lowerPack, upperPack))) {
            Map<ResourceLocation, JsonElement> prepared = new LinkedHashMap<>();
            SimpleJsonResourceReloadListener.scanDirectory(manager, DialogueEvents.ID.getPath(), Resources.GSON, prepared);

            DialogueEvents events = new DialogueEvents();
            events.apply(prepared, manager, null);

            DialogueEvent loaded = events.get(id("mca", "story/priority")).orElseThrow();
            assertEquals("upper.prompt", loaded.presentation().prompt().orElseThrow());
            assertEquals(1, events.all().size());
        }
    }

    @Test
    void resolvedHistoryReferencesRequireStoryHistoryAndKnownStableChoice() {
        ResourceLocation target = id("mca", "story/target");
        ResourceLocation scheduling = id("mca", "small_talk/scheduling");
        ResourceLocation validChoice = id("mca", "story/valid_choice");
        ResourceLocation missingChoice = id("mca", "story/missing_choice");
        ResourceLocation invalidScheduling = id("mca", "story/invalid_scheduling");
        DialogueEvents events = new DialogueEvents();

        events.apply(data(
                target, storyWithChoice("ask_why"),
                scheduling, schedulingEvent(),
                validChoice, eventWithRequirement("""
                        { "type": "mca:event_choice", "event": "mca:story/target", "choice": "ask_why" }
                        """),
                missingChoice, eventWithRequirement("""
                        { "type": "mca:event_choice", "event": "mca:story/target", "choice": "never_offered" }
                        """),
                invalidScheduling, eventWithRequirement("""
                        { "type": "mca:event_completed", "event": "mca:small_talk/scheduling" }
                        """)
        ), null, null);

        assertTrue(events.get(target).isPresent());
        assertTrue(events.get(scheduling).isPresent());
        assertTrue(events.get(validChoice).isPresent());
        assertFalse(events.get(missingChoice).isPresent());
        assertFalse(events.get(invalidScheduling).isPresent());
    }

    @Test
    void missingMcaHistoryReferencesAreRejectedWhileExternalReferencesRemainLoadable() {
        ResourceLocation strictMissing = id("mca", "story/strict_missing");
        ResourceLocation externalMissing = id("mca", "story/external_missing");
        ResourceLocation negatedExternalMissing = id("mca", "story/negated_external_missing");
        DialogueEvents events = new DialogueEvents();

        events.apply(data(
                strictMissing, eventWithRequirement("""
                        { "type": "mca:event_completed", "event": "mca:story/not_installed" }
                        """),
                externalMissing, eventWithRequirement("""
                        { "type": "mca:event_completed", "event": "optional_addon:story/not_installed" }
                        """),
                negatedExternalMissing, eventWithRequirement("""
                        {
                          "type": "mca:not",
                          "condition": {
                            "type": "mca:event_completed",
                            "event": "optional_addon:story/not_installed"
                          }
                        }
                        """)
        ), null, null);

        assertFalse(events.get(strictMissing).isPresent());
        assertTrue(events.get(externalMissing).isPresent());
        assertTrue(events.get(negatedExternalMissing).isPresent());
    }

    @Test
    void prerequisiteCyclesRemainLoadedForDiagnosticOnlyValidation() {
        ResourceLocation first = id("mca", "story/first");
        ResourceLocation second = id("mca", "story/second");
        DialogueEvents events = new DialogueEvents();

        events.apply(data(
                first, eventWithRequirement("""
                        { "type": "mca:event_completed", "event": "mca:story/second" }
                        """),
                second, eventWithRequirement("""
                        { "type": "mca:event_completed", "event": "mca:story/first" }
                        """)
        ), null, null);

        assertTrue(events.get(first).isPresent());
        assertTrue(events.get(second).isPresent());
    }

    private static Map<ResourceLocation, JsonElement> data(Object... pairs) {
        Map<ResourceLocation, JsonElement> data = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            data.put((ResourceLocation) pairs[i], JsonParser.parseString((String) pairs[i + 1]));
        }
        return data;
    }

    private static ResourceLocation id(String namespace, String path) {
        return ResourceLocation.fromNamespaceAndPath(namespace, path);
    }

    private static PathPackResources pack(String id, Path root) {
        return new PathPackResources(
                new PackLocationInfo(id, Component.literal(id), PackSource.DEFAULT, Optional.empty()),
                root);
    }

    private static String simpleEvent(String prompt) {
        return """
                {
                  "trigger": "talk",
                  "presentation": {
                    "mode": "ask",
                    "prompt": "%s",
                    "resume_prompt": "resume"
                  },
                  "repeat": { "type": "always" },
                  "start": "done",
                  "nodes": {
                    "done": { "line": "done", "complete": true }
                  }
                }
                """.formatted(prompt);
    }

    private static String storyWithChoice(String choiceId) {
        return """
                {
                  "trigger": "talk",
                  "presentation": {
                    "mode": "ask",
                    "prompt": "target.prompt",
                    "resume_prompt": "resume"
                  },
                  "repeat": { "type": "always" },
                  "start": "intro",
                  "nodes": {
                    "intro": {
                      "line": "intro",
                      "choices": [
                        { "id": "%s", "text": "choice", "next": "done" }
                      ]
                    },
                    "done": { "line": "done", "complete": true }
                  }
                }
                """.formatted(choiceId);
    }

    private static String schedulingEvent() {
        return """
                {
                  "trigger": "talk",
                  "presentation": {
                    "mode": "ambient",
                    "resume_prompt": "resume"
                  },
                  "repeat": { "type": "cooldown", "seconds": 5 },
                  "history": "scheduling",
                  "start": "done",
                  "nodes": {
                    "done": { "line": "done", "complete": true }
                  }
                }
                """;
    }

    private static String eventWithRequirement(String requirement) {
        return """
                {
                  "trigger": "talk",
                  "presentation": {
                    "mode": "ask",
                    "prompt": "dependent.prompt",
                    "resume_prompt": "resume"
                  },
                  "requirements": [%s],
                  "repeat": { "type": "always" },
                  "start": "done",
                  "nodes": {
                    "done": { "line": "done", "complete": true }
                  }
                }
                """.formatted(requirement);
    }

    private static String malformedEvent() {
        return """
                {
                  "trigger": "talk",
                  "presentation": {
                    "mode": "ask",
                    "prompt": "bad.prompt",
                    "resume_prompt": "resume"
                  },
                  "repeat": { "type": "always" },
                  "unknown_field": true,
                  "start": "done",
                  "nodes": {
                    "done": { "line": "done", "complete": true }
                  }
                }
                """;
    }
}
