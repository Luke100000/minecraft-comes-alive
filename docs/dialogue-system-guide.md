# DialogueEvent developer guide

This guide covers the Java implementation of MCA's DialogueEvent system on Minecraft 1.21.1: server ownership, selection, session lifecycle, networking and addon extensions. For event JSON, use the [authoring reference](dialogue-authoring.md). For your first datapack, use the [beginner tutorial](dialogue-datapack-guide.md).

## Architecture

| Component | Owns |
| --- | --- |
| `DialogueEvent` | Parsed, validated event definition: presentation, requirements, graph, choices, repeat rules |
| `DialogueEvents` | Datapack reload, strict decoding, history-reference validation, immutable registry snapshot and generation |
| `DialogueCondition` / `DialogueContext` | Deterministic eligibility using the server-side villager, player, world and pair history |
| `DialogueEngine` | Offers, priorities, weighted selection, node progression, pending actions, session lifecycle and commits |
| `DialogueSession` | Transient node/line position, accepted choices, resolved outcomes, effects, status and offer tokens |
| `DialogueEventHistory` | Saved per-player/villager/event completion history, latest choices and cooldown deadlines |
| `DialogueAction` | Built-in hearts, mood, memory and command effects |
| `RecentVillagerEvents` | Villager-owned gameplay timestamps used in recent-life-event conditions |
| `InteractScreen` and `ClientHandlerImpl.DialoguePresentation` | Menu rendering and client-only timed text reveal |

The owners live in `common/src/main/java/net/conczin/mca/`. `MCA.startServer` creates one engine for the running server. Fabric (`MCAFabric`) and NeoForge (`CommonNeoForge`) register the same `DialogueEvents.INSTANCE` reload listener and server lifecycle hooks. The engine runs on the server thread; the client never decides event eligibility, allowed choices or rewards.

## 1. Loading and validating events

`DialogueEvents` reloads resources from `data/<namespace>/dialogue_events/<path>.json`. The namespace and path form the event ID; the file contains no separate `id` field. The client gets text from `assets/<namespace>/lang/<locale>.json` in an enabled resource pack or mod. Fabric and NeoForge register the same server datapack reload listener. NeoForge's separate top-level `neoforge:conditions` field is not part of this schema; MCA event-level `requirements` must be used instead. Normal datapack priority resolves duplicate resource paths.

The `DialogueEvent` decoder rejects unknown fields, invalid conditions, bad graph links and malformed weights. The loader validates cross-event story references. Each successful `/reload` replaces the registry snapshot and invalidates stale sessions. For a complete event, translations and field defaults, see the [beginner example](dialogue-datapack-guide.md#2-write-your-first-conversation) and [authoring reference](dialogue-authoring.md#minimal-event).

## 2. Presentation and selection

| Mode | Behavior |
| --- | --- |
| `highlighted` | Highest-priority eligible event receives a featured marker; other eligible highlighted events remain selectable in their topic section |
| `ask` | Selectable entry in its topic section |
| `ambient` | Candidate behind `What's on your mind?` within Conversations |

`presentation.mode` determines how an event is offered; `presentation.topic` determines the section of the Talk menu for a directly selectable event. `InteractScreen` groups topics into five expandable sections: Conversations (`small_talk`, `location`, `game`), Relationships (`relationship`), Personal stories (`personal`, `life_event`, `introduction`), Practical matters (`service`) and Other topics (missing or unrecognized topics). Ambient conversations are selected separately inside Conversations; `ambient` is a mode, not a topic. Highlighted and Ask events require `presentation.prompt`; every mode requires `presentation.resume_prompt` (which can use `%1$s` for the villager name).

The Kai-style Talk overlay keeps the villager visible between two independent panels: an upper-right parchment bubble for the villager's name and current dialogue, and a bottom-centred dark panel for topic sections or player replies. A single server-selected highlighted event gets the starred top row, outside the collapsible sections; any other offered events remain in their sections. Sections start collapsed and can be opened without contacting the server. Previous/Next under the speech only reread accepted lines; Return to current exits review. Replies and Continue/Finish stay in the choices panel and are disabled during review. The small ≡ control moves speech between the upper-right corner and directly above the fixed choices panel for the current client session. Speech above choices uses a separate, matching-width frame without a pointer. The parchment and menu scroll independently, with menu height held steady during scrollbar gestures. Scrollbar visibility follows settled overflow so expansion cannot flash a temporary scrollbar. The footer retains Q Back/Topics and Esc Close. The frames are small nine-sliced textures in `assets/mca/textures/gui/dialogue/`, generated by `tools/generate-dialogue-skin.py`. Their sizes and panel geometry adapt to the scaled client screen without changing the server's offer or session rules.

Highlighted and Ask events are ordered by descending `priority`, then ID. At most **64** direct event options are transmitted per menu. For ambient selection, only the highest eligible priority tier participates; the engine selects among events in that tier by `weight` when the player clicks the ambient option. Weight does not let an ineligible event bypass prerequisites. The shipped `mca:ambient/baseline` has the lowest possible priority and a zero-second cooldown, preserving ordinary small talk.

Expanding a section scrolls its heading to the top of the list, bounded by the available scroll range. Q and Esc are centred with their labels in the two footer halves, using softly stepped keycaps. The featured row uses a larger gold Minecraft-font star and the same content width as the other rows.

## 3. Node graphs, replies and random branches

Each node is local to one event. A visible node has exactly one `line` string or `lines` array of ordered, nonblank translation keys. It also has exactly one continuation shape:

| Field | Result |
| --- | --- |
| `next` | Advance to the named node |
| `choices` | Show eligible player replies after the final line |
| `complete: true` | Successful terminal; commit when the player acknowledges it |
| `end: true` | End the run without committing a completion |
| `outcomes` | Automatically choose another node without displaying this routing node |

The UI reveals one passage at a time. `Next` advances between passages or nodes. Choices become clickable after the final passage has finished revealing. A terminal displays **Finish conversation**, returning to the topic list. On a `complete: true` terminal, this also commits the run. For line-authoring rules, see [nodes and passages](dialogue-authoring.md#nodes-passages-and-routing).

`silent: true` is an optional visible-node flag. In the client, it runs the text through `villager.transformMessage` instead of the ordinary `villager.sendChatMessage` path. The default is `false`.

Each choice accepts:

- `id`: nonblank, unique throughout this event, at most **96** characters. History stores this ID.
- `text`: nonblank translation key displayed as the player's reply.
- `requirements`: optional additional conditions, all required to match.
- Direct `next` plus optional `actions`, or `outcomes` in place of both.

A visible node can offer at most **32** choices. A direct choice requires `next`; an outcome-based choice cannot also have direct `next` or direct `actions`. Both node targets and choice targets must exist, and graphs cannot have cycles.

Outcome selection runs in order: (1) remove outcomes with unmet requirements, (2) retain only those in the highest eligible `priority` tier, (3) pick among that tier using positive `weight`. A reply with no eligible outcomes is hidden, and its eligibility is rechecked when the player chooses it. The selected outcome is retained on pause/resume. See [authoring outcomes](dialogue-authoring.md#choices-and-weighted-outcomes) for the JSON format.

For routing before any text, define a node with `outcomes` and no `line`/`lines`. An automatic routing node must include at least one unconditional outcome so a destination remains possible. A reachable `end: true` node in a `once` or `cooldown` event must explicitly set `retryable: true`. Use `complete: true` for successful endings.

## 4. Supported conditions

Event, choice and outcome requirements are typed `DialogueCondition` codecs evaluated against the server state. An array is logical AND. See the [authoring condition reference](dialogue-authoring.md#requirements) for available types and fields.

Recent-event timestamps are recorded by gameplay producers, not by the dialogue engine. The [authoring reference](dialogue-authoring.md#requirements) lists registered IDs.

Condition evaluation has three states: match, no match, or unavailable. Missing registered content, such as an absent addon event, is unavailable even inside `mca:not`. A history requirement must reference an event whose history policy is `story`; it cannot target scheduling-only ambient chatter. The reload loader validates references to existing events and choices; missing optional addon references remain unavailable at runtime.

## 5. Built-in actions and commit rules

Actions are permitted on a direct choice or a selected outcome. An event or node cannot declare a free-standing `actions` field.

| Type | Required and optional fields | Effect |
| --- | --- | --- |
| `mca:hearts` | Integer `amount`, positive or negative | Calls the villager brain's heart-reward path for this player |
| `mca:mood` | Integer `amount` | Adjusts the villager's mood by that delta |
| `mca:remember` | Nonblank `id`, optional `var: "player"`, optional positive integer `time` in ticks | Records villager long-term memory, optionally scoped to this player or timed |
| `mca:command` | Nonblank `command` | Delegates to a supported existing villager interaction command |
| `mca:slap` | Positive finite numeric `amount` (damage points) | Villager swings an empty hand when available and damages the player using vanilla mob-attack attribution |

Examples:

```json
{ "type": "mca:hearts", "amount": 5 }
{ "type": "mca:mood", "amount": -2 }
{ "type": "mca:remember", "id": "heard_about_storm", "var": "player", "time": 24000 }
{ "type": "mca:command", "command": "stay_in_village" }
{ "type": "mca:slap", "amount": 2.0 }
```

The current `mca:command` allowlist in `DialogueEngine` is `adopt`, `apologize`, `divorcePapers`, `divorceConfirm`, `hire_short`, `hire_long`, `procreate`, `slap`, `stay_in_village`, and `location`. The engine allows at most **one** command action in a run and requires the owner to report it accepted before committing. It does not execute arbitrary Minecraft command strings.

Effects accumulate in the server-side session. They are applied only when the player acknowledges a `complete: true` terminal with **Finish conversation**. The engine checks history writability, validates any command, runs the command, applies other actions and records completion. An `end: true` terminal, discarded session or failed commit does not record completion. `mca:hearts` may affect mood too; avoid duplicating that effect.

## 6. Repeat, pair history, and follow-ups

`DialogueEvent.Repeat` handles `always`, `once` and `cooldown` policies. Cooldowns use fixed or ranged seconds, converted to ticks with `ceil(seconds * 20)`. The engine rolls a ranged cooldown once on successful completion. See [repeat policy authoring](dialogue-authoring.md#repeat-policies-and-the-pause-deadline) for JSON formats.

Eligibility and history are scoped to **(player UUID, villager UUID, event ID)**. Successful `story` history records a completion count, latest completion time, next eligible time, and choice IDs from the latest completed run. Repeating the same story with a different reply replaces those remembered choice IDs. Abandoning a run does not rewrite story history.

`history: "scheduling"` records only a cooldown, and expired scheduling records can be pruned. It is valid only with `repeat.type: "cooldown"`. A scheduling-only event cannot be the target of `mca:event_completed` or `mca:event_choice`.

Write follow-ups as separate events. For example, a follow-up to the [beginner tutorial's hello event](dialogue-datapack-guide.md#2-write-your-first-conversation) could use:

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

The follow-up needs its own presentation, repeat policy, graph, ID, and translations. It becomes eligible after the original finishes but never starts automatically. Use stable choice IDs in any event other content references.

`DialogueEventHistory` persists these records in overworld `SavedData` named `mca_dialogue_event_history`. Unsupported newer save schemas are preserved read-only rather than overwritten. If the history store is not writable, the engine does not offer or commit normal dialogue events.

## 7. Server lifecycle, packets, and client UI

```text
Interact with MCA villager -> normal interaction screen
  Talk -> InteractionDialogueBeginMessage
    DialogueEngine.begin(player, villager):
      conditions + repeat/history -> continuation, highlighted, Ask, ambient
    <- InteractionDialogueOptionsResponse
  Select event / ambient / resume -> InteractionDialogueSelectMessage
    DialogueEngine.select(...): validate offer and villager; start/resume session
    <- InteractionDialogueNodeResponse (current complete line Component)
  Choose reply -> InteractionDialogueChoiceMessage
    DialogueEngine.choose(...): validate choice, resolve outcome, advance
  Next / Finish conversation -> InteractionDialogueAdvanceMessage
    DialogueEngine.advance(...): next passage/node, or commit/end
    <- InteractionDialogueNodeResponse (active / paused / ended)
  Leave Talk / close screen -> InteractionDialogueLeaveMessage / InteractionCloseRequest
    DialogueEngine.pause(...)
```

`DialogueEngine` retains one active or paused session per player, plus one menu offer. Each session carries the bound player and villager IDs, resource generation, session ID, current node and passage index, offered choices, accepted choice IDs, selected outcomes, pending effects, and token. The server rejects unoffered event IDs, stale or replayed tokens, unavailable choices, and invalid interactions. It checks that the bound villager is alive, available, in range, awake, and not trading or panicking.

Closing the screen or moving out of range pauses an unfinished run for **2,400 overworld game ticks**. The player can reopen Talk with the same villager and explicitly select the topic-specific continuation. Browsing the menu does not resume or extend the pause. Starting a different valid event abandons it. Paused sessions do not survive logout, server stop, resource reload, permanent villager removal, or the pause deadline. A datapack reload increments `DialogueEvents.generation()` and invalidates stale sessions/offers.

The server sends complete text Components for each passage. `ClientHandlerImpl.DialoguePresentation` reveals one Unicode grapheme every **40 ms**, without per-character packets. Replies, `Next` and **Finish conversation** appear after the passage is fully revealed. Early clicks cannot skip or queue progress. `InteractScreen` renders the five expandable sections and their entries. Presentation timing never grants completion or rewards.

## 8. Extending functionality in Java

Datapacks can create namespaced events, nodes, replies, weighted outcomes, requirements and effects. New **behavior** needs a mod: addons may register namespaced condition and action codecs during common mod initialization, before the server reloads dialogue resources. A datapack alone cannot execute arbitrary Java logic.

```java
DialogueCondition.register(
    ResourceLocation.fromNamespaceAndPath("myaddon", "min_experience"),
    Codec.INT.fieldOf("levels").codec(),
    (levels, ctx) -> ctx.player().experienceLevel >= levels
        ? DialogueCondition.Evaluation.MATCH : DialogueCondition.Evaluation.NO_MATCH
);
DialogueAction.register(
    ResourceLocation.fromNamespaceAndPath("myaddon", "add_experience"),
    Codec.INT.fieldOf("levels").codec(),
    (levels, ctx) -> ctx.player().giveExperienceLevels(levels)
);
```

These types can be used as `{"type":"myaddon:min_experience","levels":5}` in event/choice/outcome requirements and `{"type":"myaddon:add_experience","levels":1}` in choice/outcome actions. Codecs parse the object **without `type`**, and must serialize an object without that field. Invalid or unknown types reject the containing resource. Registered action executors run on the logical server only when the final completion is acknowledged; they should not throw after applying partial effects. Condition evaluations must preserve `UNAVAILABLE` when a referenced dependency is missing, including under `mca:not`.

The registry is process-wide, and IDs cannot be duplicated or override built-in types. There is **no legacy `Actions.register`/`GiftPredicate.register` compatibility adapter**. See the [datapack guide](dialogue-datapack-guide.md) for a complete matching JSON example.

If new behavior is necessary, add it at its existing owner:

1. For a **new condition**, register a typed codec and evaluator with `DialogueCondition.register`. Read state from the gameplay class that already owns it; preserve the distinction between false and unavailable.
2. For a **new action**, register a typed codec and server-side executor with `DialogueAction.register`. Define failure and completion behavior before wiring it to content; the existing completion path handles deferral.
3. For a **new recent gameplay event**, call `RecentVillagerEvents.register(id)` during mod initialization, then record it at the real gameplay transition. The existing villager NBT lifecycle saves registered IDs; dialogue text alone never records gameplay facts.
4. For **UI or packet changes**, keep session authority in `DialogueEngine`, validate untrusted client IDs and tokens, and preserve shared behavior across Fabric and NeoForge. Only presentation belongs on the client.

Existing conditions are often sufficient. For example, a story about actually being cured uses `mca:recent_event` with `mca:cured`; a general discussion of cured villagers can use personality, age, time and hearts without claiming a cure happened. The current system has no authored scene specifically for witnessing a killing. An attack or relative-death fact does not automatically provide that dialogue.

## 9. Testing and troubleshooting

These shipped resources make useful fixtures:

| Resource | What to verify |
| --- | --- |
| `mca:social/joke` | Ask mode; direct replies; personality/mood-weighted outcomes; hearts changes |
| `mca:ambient/crabby_night` | `crabby` personality plus night; shares the ordinary priority-0 ambient tier |
| `mca:personal/cured_zombies` | Adult gloomy speaker, hearts >= 20, day time 13000 to 23000; ordered lines and replies, without a personal cure requirement |
| `mca:personal/cured_zombies_followup` | Separate Ask topic after `cured_zombies` completion with `experience_changes_you` |
| `mca:personal/cured_identity` | Highlighted personal story for a speaker with a recorded `mca:cured` event within 24000 ticks |
| `mca:personal/mourning` / `mca:personal/revived` | Actual recorded gameplay events as requirements |
| `mca:personal/mourning_depressed` | Depressed speaker with at least 10 hearts and an active mourning assignment; two weighted +10 and two weighted -10 response outcomes, plus one neutral reply |

For a manual smoke test, finish Joke by acknowledging its final line, reopen Talk after its five-second cooldown, and check the updated hearts. Speak to an awake crabby villager at night (`/time set night`) to try its ambient line. For the general cured-zombie topic, use an adult gloomy villager with at least 20 hearts at night, select `experience_changes_you`, finish the story, then check the follow-up. Separately cure an MCA zombie villager and Talk to the converted villager within one in-game day to check its personal story.

For developers working on the engine, the checkout has targeted JUnit, resource, protocol and GameTest coverage. Examples from the repository root:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.dialogue.DialogueEventParsingTest' --console=plain
.\gradlew.bat :common:test --console=plain
.\gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=DialogueEngineGameTests --console=plain
```

Also inspect the client in game for reveal pacing, click targets and menu layout; builds alone cannot verify these. Useful negative cases include forged choices, consumed offer tokens, switching villagers, reload with an open story, losing interaction range, pause expiry, and two players sharing a villager.

When an event does not appear, inspect the resource ID/path, active pack, reload log, schema validation, conditions and repeat policy. `DialogueEvents` logs and skips invalid resources, validates history references, and warns about prerequisite cycles. A missing addon event reference remains unavailable at runtime. For displayed translation keys, check the client resource and exact `prompt`, `resume_prompt`, `line` and `text` keys. A missing translation does not change server eligibility.

For a conversation that ends unexpectedly, distinguish a deliberate `end` terminal from an unsuccessful command commit, expired or invalidated pause, stale offer token, or invalid interaction. A revealed line alone never commits effects.

For content examples, see [DialogueEvent authoring](dialogue-authoring.md). The [design specification](superpowers/specs/2026-10-06-dialogue-event-conversation-system-design.md) records earlier decisions; the rules here reflect the implemented 1.21.1 code. Real-client and multiplayer testing on both loaders is still required for release confidence; automated server tests alone cannot verify UI and network behavior.
