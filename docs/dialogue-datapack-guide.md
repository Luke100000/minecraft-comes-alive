# Making MCA dialogue datapacks

You can add your own conversations to Minecraft Comes Alive (MCA) without writing Java or building a mod. A dialogue datapack decides **when a villager can say something**, **what they say**, **how the player can answer**, and **what happens after the conversation**.

Want to try a conversation first? The optional [DialogueEvent showcase](../testpacks/dialogue-showcase/README.md) includes working examples. Install its datapack and resource pack in a test world; it is not part of normal MCA content.

This guide is for Minecraft **1.21.1** and MCA's **DialogueEvent** system. It starts with a conversation that works for any available MCA villager, then shows how to make it more interesting.

## 1. Set up two packs

Minecraft loads dialogue **rules** from a datapack, but it loads the **words shown on screen** from a resource pack. For this example, make these two folders:

```text
<your world>/datapacks/Villager Stories/
  pack.mcmeta
  data/
    villager_stories/
      dialogue_events/
        hello_neighbor.json

<Minecraft resourcepacks>/Villager Stories Text/
  pack.mcmeta
  assets/
    villager_stories/
      lang/
        en_us.json
```

The folder under `data` or `assets` is your **namespace**. Use lowercase letters, numbers, underscores and other valid Minecraft identifier characters. Here we use `villager_stories`. The event's ID will be `villager_stories:hello_neighbor`.

Put this in the **datapack's** `pack.mcmeta`:

```json
{
  "pack": {
    "pack_format": 48,
    "description": "My MCA villager conversations"
  }
}
```

Put this in the **resource pack's** `pack.mcmeta`:

```json
{
  "pack": {
    "pack_format": 34,
    "description": "Text for my MCA villager conversations"
  }
}
```

These pack formats are for Minecraft 1.21.1. If you target another Minecraft version, check that version's datapack and resource-pack formats.

**Important:** `assets` inside a server datapack is not a replacement for an enabled client resource pack. In multiplayer, the server needs the datapack and each player needs the resource pack (or the server must provide it as a server resource pack).

On NeoForge, MCA loads these as ordinary server datapack resources. Use MCA `requirements` to restrict when a conversation can appear; the separate `neoforge:conditions` field is not supported in event JSON. See the [developer guide](dialogue-system-guide.md#1-loading-and-validating-events) for loader details.

## 2. Write your first conversation

Create `data/villager_stories/dialogue_events/hello_neighbor.json` and paste:

```json
{
  "trigger": "talk",
  "presentation": {
    "mode": "ask",
    "prompt": "dialogue_event.villager_stories.hello.prompt",
    "resume_prompt": "dialogue_event.villager_stories.hello.resume",
    "topic": "small_talk"
  },
  "repeat": { "type": "cooldown", "seconds": 5 },
  "history": "story",
  "start": "hello",
  "nodes": {
    "hello": {
      "line": "dialogue_event.villager_stories.hello.opening",
      "choices": [
        {
          "id": "friendly",
          "text": "dialogue_event.villager_stories.hello.friendly",
          "actions": [
            { "type": "mca:hearts", "amount": 2 }
          ],
          "next": "friendly_reply"
        },
        {
          "id": "busy",
          "text": "dialogue_event.villager_stories.hello.busy",
          "next": "busy_reply"
        }
      ]
    },
    "friendly_reply": {
      "line": "dialogue_event.villager_stories.hello.friendly_reply",
      "complete": true
    },
    "busy_reply": {
      "line": "dialogue_event.villager_stories.hello.busy_reply",
      "complete": true
    }
  }
}
```

What it does:

1. Adds **How are you doing?** under **Conversations** in an MCA villager's Talk menu.
2. Lets the villager greet the player.
3. Offers two player replies, each leading to a different last line.
4. Gives **2 hearts** for the friendly reply once the player finishes the conversation.
5. Makes that event available again after a **5-second cooldown** for that player and villager.

`start` names the first node, `hello`. The `next` values point to other nodes **inside the same event file**. Every reachable path in this example ends with `complete: true`.

## 3. Add the actual words

Create `assets/villager_stories/lang/en_us.json` in the **resource pack**:

```json
{
  "dialogue_event.villager_stories.hello.prompt": "How are you doing?",
  "dialogue_event.villager_stories.hello.resume": "We were talking about your day...",
  "dialogue_event.villager_stories.hello.opening": "Oh, hello there! It's good to see a familiar face.",
  "dialogue_event.villager_stories.hello.friendly": "It's good to see you too!",
  "dialogue_event.villager_stories.hello.busy": "Sorry, I should get going.",
  "dialogue_event.villager_stories.hello.friendly_reply": "You're always welcome around here.",
  "dialogue_event.villager_stories.hello.busy_reply": "Of course. I'll see you later."
}
```

The JSON event contains **translation keys**, not the displayed sentences. Every `prompt`, `resume_prompt`, `line`, and choice `text` needs a matching key in the client language file. You can provide other languages by adding files such as `de_de.json` with the same keys.

## 4. Try it in Minecraft

1. Install a compatible MCA build for Minecraft 1.21.1. Put **Villager Stories** in the world's `datapacks` directory. A world may be a singleplayer save or a multiplayer server world.
2. Enable **Villager Stories Text** in **Options > Resource Packs** on the client.
3. Enter the world, run `/datapack list` to check the datapack is enabled, and run `/reload` after adding or editing dialogue JSON. Reload client resources with **F3+T** after editing translations.
4. Interact with an awake, available **MCA villager**, open **Talk**, expand **Conversations** and choose **How are you doing?**.
5. Pick a reply. On the final line, click **Finish conversation**. This returns you to the topic list and applies any earned reward.

If you quit the Talk screen midway, MCA can offer a topic-specific continuation when you return to that villager. Closing the screen is **not** the same as completing the dialogue.

You can also distribute either pack as a ZIP. Keep `pack.mcmeta`, `data/`, or `assets/` at the **top level of the ZIP**, not inside an extra enclosing folder.

## 5. Set conditions

Add a `requirements` array to the **top level** of the event, alongside `trigger` and `presentation`. For example:

```json
"requirements": [
  { "type": "mca:weather", "value": "rain" },
  { "type": "mca:hearts", "min": 20 }
]
```

Now the event appears only when **both** requirements match: the weather is rain and this villager has at least 20 hearts for the player. Remove the array to return to an always-eligible starter conversation.

You can also put `requirements` on an individual **choice** or **outcome**. This hides or excludes only that reply or outcome, rather than the whole event.

See the [authoring reference](dialogue-authoring.md#requirements) for conditions, including personality, mood, biome and recent gameplay events.

## 6. Choose a mode and topic

`presentation.mode` controls how an event is offered. `presentation.topic` places directly selectable conversations in a Talk section.

| Mode | Behavior | Good for |
| --- | --- | --- |
| `ask` | Selectable entry in its topic section | Ordinary topics and questions |
| `highlighted` | Featured entry in its topic section | Important current stories |
| `ambient` | Candidate for **What's on your mind?** within Conversations | Random small talk |

Talk has five expandable sections: **Conversations**, **Relationships**, **Personal stories**, **Practical matters** and **Other topics**. For example, `"topic": "small_talk"` places the starter event in Conversations. Unknown or missing topics appear in Other topics. Ambient conversations are selected separately within Conversations; `ambient` is a mode, not a topic.

`ask` and `highlighted` require a `prompt`. Every mode requires a `resume_prompt`; `ambient` needs no `prompt`. For exact topic names and selection priority, see [presentation modes](dialogue-authoring.md#presentation-modes).

Tip: start with `ask` while testing. It's easier to verify than a random ambient conversation.

## 7. Add more lines, replies and outcomes

Replace a node's single `line` with `lines` when you want the villager to speak in several consecutive passages:

```json
"lines": [
  "dialogue_event.villager_stories.story.first",
  "dialogue_event.villager_stories.story.second"
]
```

Add translations for **both** keys. After those lines, the node still needs exactly one continuation: `next`, `choices`, `complete: true`, or `end: true`.

If one player reply can lead to several reactions, use weighted `outcomes` instead of a direct `next`. The engine filters out ineligible results, keeps the highest-priority eligible tier and then uses `weight` to choose within that tier. See [choices and outcomes](dialogue-authoring.md#choices-and-weighted-outcomes) for a complete example and node limits.

## 8. Rewards, repeat rules and follow-up stories

Actions on a choice or its selected outcome can change hearts, mood or memory when the conversation finishes. See the [action reference](dialogue-authoring.md#built-in-actions) for supported types and how to use them.

Every event needs a `repeat` policy:

| Policy | Example | Effect |
| --- | --- | --- |
| Always | `{"type":"always"}` | No repeat restriction after a successful run |
| Once | `{"type":"once"}` | Can complete once per player/villager pair |
| Cooldown | `{"type":"cooldown","seconds":5}` | Available again after a successful run and the specified delay |

With `history: "story"`, MCA saves the completed event and **which reply IDs were picked** for that player and villager. Another event can require the starter conversation's friendly reply:

```json
"requirements": [
  {
    "type": "mca:event_completed",
    "event": "villager_stories:hello_neighbor"
  },
  {
    "type": "mca:event_choice",
    "event": "villager_stories:hello_neighbor",
    "choice": "friendly"
  }
]
```

Use these requirements in a **separate new event file** with its own `presentation`, `repeat`, `start`, `nodes`, and translation keys. The follow-up becomes available after the first event **successfully finishes**; it does not start automatically.

For throwaway small talk with a cooldown, `history: "scheduling"` is an option, but those events cannot be referenced by `event_completed` or `event_choice`.

An installed Java addon may also expose a namespaced interaction command. You can invoke it in a choice or outcome using the **existing** action format, for example:

```json
"actions": [{ "type": "mca:command", "command": "myaddon:inspect" }]
```

The addon must register `myaddon:inspect` through the server's Java `InteractionCommands` API. Otherwise that choice cannot successfully commit. You cannot define executable commands, scripts or `.mcfunction` behavior in a dialogue datapack. Authors can use the same command in an existing resource-pack menu layout via an optional button `"command": "myaddon:inspect"`, but the server still decides whether it is permitted. See the [developer guide](dialogue-system-guide.md#register-a-server-side-interaction-command) for Java registration and a complete button example.

**Rewards and story history are saved only when the player selects Finish conversation on a `complete: true` ending.** Walking away, closing the menu or reaching an `end: true` node does not count as successful completion.

## 9. Resolve a dialogue line with an addon or a structure search

An optional `resolve` object lets a **registered server-side resolver** supply a line while the Talk screen stays open. MCA shows one disabled grey three-square reply row while it waits. A genuine server-confirmed async lookup also displays a localized pending sentence and an indeterminate bar **inside the parchment**. When the result arrives, the bar disappears and the result reveals character by character. **Finish conversation** or the actual reply choices appear together only after the line is revealed. Ordinary text reveal uses the grey row without the async bar.

Version 1 requires a single `line` on a resolved node and exactly one continuation (`choices`, `next`, `complete`, or `end`). Do not use `lines` or automatic `outcomes` on that node. The `resolve` object needs a registered namespaced `type` and a `failure_line`; `pending_line` is optional and defaults to `gui.dialogue.searching`. Any extra fields belong to the registered resolver's codec. Unrecognized resolver IDs, invalid fields or conflicting node shapes cause the event to be rejected on `/reload`.

### Addon example with a registered resolver

Create `data/villager_stories/dialogue_events/old_well.json` in the datapack. This is a **complete event**, but `landmarkaddon:landmark_note` is an **illustrative addon resolver ID**, not an MCA built-in. It works only when an installed Java addon registers that exact ID and accepts its `clue` parameter before the datapack reloads.

```json
{
  "trigger": "talk",
  "presentation": {
    "mode": "ask",
    "prompt": "dialogue_event.villager_stories.well.prompt",
    "resume_prompt": "dialogue_event.villager_stories.well.resume",
    "topic": "location"
  },
  "repeat": { "type": "cooldown", "seconds": 5 },
  "history": "story",
  "start": "lookup",
  "nodes": {
    "lookup": {
      "line": "dialogue_event.villager_stories.well.found",
      "resolve": {
        "type": "landmarkaddon:landmark_note",
        "clue": "old_well",
        "pending_line": "dialogue_event.villager_stories.well.searching",
        "failure_line": "dialogue_event.villager_stories.well.missing"
      },
      "complete": true
    }
  }
}
```

For a minimal addon implementation, the following registration can be placed in the addon's **common initialization**, before dialogue resources load. It uses a completed future to demonstrate the codec and result contract. A real asynchronous provider would return its read-only lookup's `CompletionStage` instead. This code is an **example for a separate addon mod**, not a class or resolver already shipped by MCA.

```java
DialogueAsyncResolver.register(
    ResourceLocation.fromNamespaceAndPath("landmarkaddon", "landmark_note"),
    Codec.STRING.fieldOf("clue").codec(),
    (clue, context, operationId) -> CompletableFuture.completedFuture(
        "old_well".equals(clue)
            ? DialogueAsyncResolver.Result.found("348,70,-126")
            : DialogueAsyncResolver.Result.missing()
    )
);
```

The snippet needs imports for `DialogueAsyncResolver`, `ResourceLocation`, `Codec` and `CompletableFuture`. Its immediate result may reach the client before the loading animation becomes visible. To test the fallback, set `clue` to an unrecognized value and run `/reload`. The server will use the authored `failure_line`.

### Built-in structure search

The `mca:structure_location` resolver comes from MCA itself. To look up one of the entries in the server's `Config.structuresInRumors` pool, create `data/villager_stories/dialogue_events/find_ruin.json` with this complete event.

```json
{
  "trigger": "talk",
  "presentation": {
    "mode": "ask",
    "prompt": "dialogue_event.villager_stories.ruin.prompt",
    "resume_prompt": "dialogue_event.villager_stories.ruin.resume",
    "topic": "location"
  },
  "repeat": { "type": "cooldown", "seconds": 5 },
  "history": "story",
  "start": "search",
  "nodes": {
    "search": {
      "line": "dialogue_event.villager_stories.ruin.authored",
      "resolve": {
        "type": "mca:structure_location",
        "pool": "configured_rumors",
        "pending_line": "dialogue_event.villager_stories.ruin.searching",
        "failure_line": "dialogue.location.forgot"
      },
      "complete": true
    }
  }
}
```

You can replace `"pool": "configured_rumors"` with `"structure": "minecraft:igloo"` to request one registered structure. Supply **exactly one** of those selectors. The configured pool uses MCA's server settings and structure registry, with the existing random search origin and lookup radius. A missing structure, empty pool, unsuccessful search, exception or timeout uses the fallback. The required authored `line` is a schema field; a successful built-in lookup **overrides** it with MCA's existing `dialogue.location.<structurePath>` translations and the exact `x,y,z` result. It does not emit a separate vanilla chat message.

### Resource-pack translations for both events

Add the following keys to `assets/villager_stories/lang/en_us.json` in the **client resource pack**. If you used the earlier starter example, merge these entries into its existing JSON object instead of replacing its keys.

```json
{
  "dialogue_event.villager_stories.well.prompt": "Where is the old well?",
  "dialogue_event.villager_stories.well.resume": "About the old well...",
  "dialogue_event.villager_stories.well.found": "%1$s, the old well is at %2$s.",
  "dialogue_event.villager_stories.well.searching": "Checking my landmark notes...",
  "dialogue_event.villager_stories.well.missing": "Sorry, I don't know where that is.",
  "dialogue_event.villager_stories.ruin.prompt": "Know of any ruins nearby?",
  "dialogue_event.villager_stories.ruin.resume": "About those ruins...",
  "dialogue_event.villager_stories.ruin.authored": "I might know a place worth exploring.",
  "dialogue_event.villager_stories.ruin.searching": "Looking for an interesting place..."
}
```

MCA's villager translator supplies the player's name as **`%1$s`**. The first argument returned by an addon provider becomes **`%2$s`** here, so the example coordinates display in that slot. The built-in resolver likewise calls the villager's localized translation path with the found coordinates as the first explicit argument. Existing MCA English location variants, such as `dialogue.location.igloo/1` and `/2`, use `%2$s`, and `dialogue.location.forgot/1` through `/3` cover a miss. MCA can choose numbered variants and dialogue-type, personality, profession and gender variants where translations are present. Keep those existing MCA language assets in place. Add equivalent keys in other client locale files, such as `de_de.json`, to localize your own events; do not assume an unsuffixed MCA location key is present.

### Install, reload and test

Install MCA and the addon providing `landmarkaddon:landmark_note` on the **server** (and on clients if that addon itself requires a client installation). MCA's resolver codec/registry is shared by Fabric and NeoForge, but install the addon build for the loader you run. Enable the datapack on the world or server, enable the matching client resource pack, then run `/datapack list` and `/reload`. Use **F3+T** on each client after editing `en_us.json`. Enter Talk with an available MCA villager and select the new Ask entries under **Conversations** (the `location` topic). Select a resolver entry, check the pending text and bar when work is genuinely asynchronous, then the inline result and Finish after reveal.

A pending operation has a hard deadline of **600 server ticks (30 seconds)**. Closing or leaving Talk, switching villagers, disconnecting or reloading invalidates that request. A paused lookup may restart only on explicit Resume with a fresh identity; an old callback cannot update the new conversation. The result cannot commit rewards or history until the normal Finish acknowledgment. Datapacks can **configure registered resolvers** but cannot define executable Java providers, scripts or arbitrary commands.

## Optional colour, formatting and copyable phrases

The datapack's existing `line`, `choices.text` and `presentation.prompt` translation keys stay unchanged. Add optional formatting tags to their **client resource-pack** language values:

```json
{
  "dialogue_event.villager_stories.well.found": "<gold><bold>Found it!</bold></gold> The well is at <copy_coords>234,70,-980</copy_coords>.",
  "dialogue_event.villager_stories.ruin.authored": "Look for <color:#56C8C2><italic>the old bridge</italic></color>.",
  "dialogue_event.villager_stories.ruin.prompt": "Ask about <copy><hover:villager_stories.copy_hint>the old bridge</hover></copy>",
  "villager_stories.copy_hint": "Copy this phrase"
}
```

Use any vanilla named colour such as `<gold>...</gold>`, a six-digit `<color:#RRGGBB>...</color>`, and optional `<bold>`, `<italic>` or `<underlined>`. Tags must close in reverse nesting order. `<copy>any phrase</copy>` copies the enclosed **displayed text**, while `<copy_coords>x,y,z</copy_coords>` validates three integers and copies them as `x y z`. A `<hover:translation.key>...</hover>` shows a localized tooltip. Copy, hover and colour are independent; no other click actions or commands are supported. Escape a literal `<` as `\\<`. Unknown or malformed tags remain literal.

During animated speech, a copyable phrase becomes hoverable and clickable **as soon as that phrase is fully visible**, even while later words are still appearing. A partially shown phrase retains its colour/formatting but cannot copy or display its copy tooltip yet. Clicking ordinary speech continues to reveal the rest of the line. Choices are already visible and can be copied immediately; clicking a copyable phrase does not select its choice.

Each `<copy>` tag activates independently, even when adjacent tags copy identical text. Nested copy tags activate at their own closing boundaries; the innermost tag supplies the copy action on text it encloses.

Built-in Rumours locations automatically show teal underlined coordinates with a **Copy coordinates** tooltip. They use the existing localized `%2$s` argument, so no changes to the 41+ location translations or the Rumours event JSON are needed. Custom dialogue text can use the general `<copy>` tag for any text.

## 10. Why isn't my dialogue showing up?

Check these common problems in order:

1. **Wrong folder or namespace:** The server event must be in `data/<namespace>/dialogue_events/*.json`. `dialogues/` is the old format, not the new `dialogue_events/` directory.
2. **Pack isn't enabled:** Check `/datapack list`, then `/reload`. On a server, edit the server world's datapack, not just your local client.
3. **The event was rejected:** Check the server log for `Dialogue event <id> was not loaded`. The loader rejects unknown fields, bad conditions, missing nodes, invalid branches and other schema errors.
4. **Conditions don't match:** Temporarily remove `requirements` and use `mode: "ask"`. Check villager availability, hearts, personality, weather and time as appropriate.
5. **Text displays as a key:** Enable the separate client resource pack and check the exact spelling of the translation key in `en_us.json`. Try F3+T.
6. **The story is unavailable again:** Check `repeat`, a previous completion, or whether you have a paused run with that villager.
7. **The reward doesn't happen:** Finish at `complete: true` and click **Finish conversation**. Verify the action is on the selected choice or outcome.
8. **An async event is rejected:** Verify its addon is installed and registered before `/reload`; check the log for an unknown `resolve.type`, invalid codec parameter, missing `failure_line`, unsupported `lines`/`outcomes`, or an extra field. `landmarkaddon:landmark_note` exists only if your addon registers it.
9. **A lookup shows the fallback:** For `mca:structure_location`, check the configured structure pool or explicit registry ID. A missing/empty pool, unsuccessful lookup, provider error or 600-tick timeout returns the localized failure line. For an addon, confirm its provider returns `Result.found(...)` for the input.
10. **A result is missing or appears as a key:** Keep the resource pack enabled on each client and reload assets with F3+T. Remember that the built-in structure success uses MCA's dynamic `dialogue.location.<structurePath>` **numbered variants**, and the failure uses `dialogue.location.forgot` variants. The async bar appears only for a genuine server pending state; the three dots during regular typing are expected.

For examples shipped with MCA, inspect `common/src/main/resources/data/mca/dialogue_events/` in the source repository. Good starting points are `social/joke.json` (choices and reactions), `ambient/crabby_night.json` (context-sensitive small talk), and `personal/cured_zombies_followup.json` (a story that checks an earlier reply).

For the complete JSON format, conditions and writing examples, use the [DialogueEvent authoring reference](dialogue-authoring.md). Addon developers can use the [developer guide](dialogue-system-guide.md).
