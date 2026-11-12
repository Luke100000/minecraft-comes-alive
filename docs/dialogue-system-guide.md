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
| `DialogueAsyncResolver` | Typed async node lookups, the built-in structure locator and registered Java addon providers |
| `RecentVillagerEvents` | Villager-owned gameplay timestamps used in recent-life-event conditions |
| `InteractScreen` and `ClientHandlerImpl.DialoguePresentation` | Menu rendering and client-only timed text reveal |

The owners live in `common/src/main/java/net/conczin/mca/`. `MCA.startServer` creates one engine for the running server. Fabric (`MCAFabric`) and NeoForge (`CommonNeoForge`) register the same `DialogueEvents.INSTANCE` reload listener and server lifecycle hooks. The engine runs on the server thread; the client never decides event eligibility, allowed choices or rewards.

## 1. Loading and validating events

`DialogueEvents` reloads resources from `data/<namespace>/dialogue_events/<path>.json`. The namespace and path form the event ID; the file contains no separate `id` field. The client gets text from `assets/<namespace>/lang/<locale>.json` in an enabled resource pack or mod. Fabric and NeoForge register the same server datapack reload listener. NeoForge's separate top-level `neoforge:conditions` field is not part of this schema; MCA event-level `requirements` must be used instead. Normal datapack priority resolves duplicate resource paths.

The `DialogueEvent` decoder rejects unknown fields, invalid conditions, bad graph links and malformed weights. The optional node `resolve` also rejects unknown resolver types, unsupported node shapes and invalid provider codec fields. The loader validates cross-event story references. Each successful `/reload` replaces the registry snapshot and invalidates stale sessions, offers and pending lookups. For complete event JSON and translations, see the [beginner example](dialogue-datapack-guide.md#2-write-your-first-conversation), [async examples](dialogue-datapack-guide.md#9-resolve-a-dialogue-line-with-an-addon-or-a-structure-search) and [authoring reference](dialogue-authoring.md#minimal-event).

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
| `complete: true` | Successful terminal; normally commit when acknowledged, or immediately for a slap-ending reply |
| `end: true` | End the run without committing a completion |
| `outcomes` | Automatically choose another node without displaying this routing node |

The UI reveals one passage at a time. `Next` advances between passages or nodes. Choices become clickable after the final passage has finished revealing. An ordinary terminal displays **Finish conversation**, returning to the topic list and committing a `complete: true` run. If the selected reply leads directly to a single-line completed slap reaction, the server commits the run and closes the entire interaction immediately. The terminal line is delivered in localized chat after the delayed slap, with no final acknowledgement. For line-authoring rules, see [nodes and passages](dialogue-authoring.md#nodes-passages-and-routing).

`silent: true` is an optional visible-node flag. In the client, it runs the text through `villager.transformMessage` instead of the ordinary `villager.sendChatMessage` path. The default is `false`.

Each choice accepts:

- `id`: nonblank, unique throughout this event, at most **96** characters. History stores this ID.
- `text`: nonblank translation key displayed as the player's reply.
- `requirements`: optional additional conditions, all required to match.
- Direct `next` plus optional `actions`, or `outcomes` in place of both.

A visible node can offer at most **32** choices. A direct choice requires `next`; an outcome-based choice cannot also have direct `next` or direct `actions`. Both node targets and choice targets must exist, and graphs cannot have cycles.

Outcome selection runs in order: (1) remove outcomes with unmet requirements, (2) retain only those in the highest eligible `priority` tier, (3) pick among that tier using positive `weight`. A reply with no eligible outcomes is hidden, and its eligibility is rechecked when the player chooses it. The selected outcome is retained on pause/resume. See [authoring outcomes](dialogue-authoring.md#choices-and-weighted-outcomes) for the JSON format.

For routing before any text, define a node with `outcomes` and no `line`/`lines`. An automatic routing node must include at least one unconditional outcome so a destination remains possible. A reachable `end: true` node in a `once` or `cooldown` event must explicitly set `retryable: true`. Use `complete: true` for successful endings.

An async visible node may add `resolve` to obtain a result before revealing its line. Version 1 requires **one authored `line`**, never `lines` or automatic `outcomes`, and still requires one normal continuation (`next`, `choices`, `complete` or `end`). `resolve` contains a registered namespaced `type`, a required nonblank `failure_line`, an optional `pending_line` defaulting to `gui.dialogue.searching` and provider-specific fields validated by that provider's codec. Success uses the node's authored `line` unless the resolver supplies a validated translation-key override. A missing result, exception or timeout uses `failure_line` without arguments. Both paths resolve via `villager.getTranslatable(player, key, args...)`, preserving the implicit player name at `%1$s` and the first explicit result argument at `%2$s`. The usual dialogue-type, gender, personality, profession and numbered language variants still apply, provided the corresponding client resource pack has those translations.

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

The built-in dialogue commands are `adopt`, `apologize`, `divorcePapers`, `divorceConfirm`, `hire_short`, `hire_long`, `procreate`, `stay_in_village`, and `location`. Additional namespaced commands can be registered by installed Java addons through `InteractionCommands`. Menu packets and authenticated dialogue completion share one server-side dispatcher, but pass different trusted `Source` values. The engine allows at most **one** command action in a run and requires the executor to return an accepted result before committing other actions or history. The separate `closeScreen` result is **not** evidence that a command succeeded. MCA does not run arbitrary Minecraft command strings or datapack-defined functions.

Effects accumulate in the server-side session. Normally they are applied when the player acknowledges a `complete: true` terminal with **Finish conversation**. The engine checks history writability, validates any command, applies authorized actions and records completion. For a reply whose only terminal reaction is a completed one-line typed `mca:slap`, the server commits history and other authorized effects once on choice acceptance, sends the interaction-ID-qualified screen close, and schedules the physical slap three server ticks later. It then sends the existing localized terminal reaction through the villager's chat pipeline once. A stale or repeated choice cannot schedule another hit, and the hit and chat are discarded if either actor becomes unavailable. The old `mca:command` with `slap` fails the command validation and cannot complete. An `end: true` terminal, discarded session or failed commit does not record completion. `mca:hearts` may affect mood too; avoid duplicating that effect.

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
    <- InteractionDialoguePendingResponse (session UUID, offer token, pending Component)
       for a resolved node, followed by InteractionDialogueNodeResponse.active when ready
    <- InteractionDialogueNodeResponse.active immediately for an ordinary node
  Choose reply -> InteractionDialogueChoiceMessage
    DialogueEngine.choose(...): validate choice, resolve outcome, advance
    <- pending then active node response when the destination has a resolver
    slap-ending reply: commit once, close interaction, defer hit three ticks, then localized chat
  Next / Finish conversation -> InteractionDialogueAdvanceMessage
    DialogueEngine.advance(...): next passage/node, or commit/end
    <- pending response for a resolved destination, or NodeResponse (active / paused / ended)
  Leave Talk / close screen -> InteractionDialogueLeaveMessage / InteractionCloseRequest
    DialogueEngine.pause(...)
```

`DialogueEngine` retains one active or paused session per player, plus one menu offer. Each session carries the bound player and villager IDs, resource generation, session ID, current node and passage index, offered choices, accepted choice IDs, selected outcomes, pending effects, and token. The server rejects unoffered event IDs, stale or replayed tokens, unavailable choices, and invalid interactions. It checks that the bound villager is alive, available, in range, awake, and not trading or panicking.

Closing the screen or moving out of range pauses an unfinished run for **2,400 overworld game ticks**. The player can reopen Talk with the same villager and explicitly select the topic-specific continuation. Browsing the menu does not resume or extend the pause. Starting a different valid event abandons it. Paused sessions do not survive logout, server stop, resource reload, permanent villager removal, or the pause deadline. A datapack reload increments `DialogueEvents.generation()` and invalidates stale sessions/offers.

The server sends complete text Components for each passage. `ClientHandlerImpl.DialoguePresentation` reveals one Unicode grapheme every **40 ms**, without per-character packets. While a regular passage reveals, the reply panel shows one disabled grey row with three subtly bouncing square dots. A **genuinely server-confirmed pending resolver** shows that same row plus a localized pending sentence and indeterminate bar in the parchment. Once ready, the bar disappears and the result reveals normally. Actual choices, `Next` or ordinary **Finish conversation** become available together after reveal. There is no artificial minimum wait, percentage, or async bar for ordinary typing. Currently, setting Minecraft's **Panorama Speed** to zero disables these animations and leaves a static strip; there is no separate MCA reduced-motion toggle. History browsing must not re-enable live replies, and scrolling, parchment placement, footer and HUD clearance remain as before. Early clicks cannot skip or queue progress. Presentation timing never grants completion or rewards; the slap-ending exception is a server-owned choice transition.

### Pending tickets, completion and cancellation

On entry to a resolved visible node, including automatic routing and choice/advance transitions, `DialogueEngine` installs **at most one pending request per player session**. Its ticket binds the player UUID, villager UUID, session UUID, resource generation, offer token, node ID, passage index, resolver and a newly generated **operation UUID**. The server sends `InteractionDialoguePendingResponse(UUID sessionId, long offerToken, Component pendingLine)` before any ready node response, including when a future is already complete. Pending and ready responses are correlated by session and token. The client ignores out-of-order or duplicate responses for an old session/token.

While pending, no choices, advancement or completion are accepted. Duplicate inbound selections cannot launch a second search or apply effects. A hard **600-server-tick (30-second)** deadline turns unfinished valid work into the localized failure line. Closing or leaving Talk, pausing, logout, death, changing player/villager or world, losing interaction ownership, token replacement, datapack reload and server stop invalidate the ticket. The engine detaches the ticket **before** calling `resolver.cancel(operationId)` on the server thread, so even synchronous cancellation callbacks cannot revive it. A paused, unresolved read-only lookup can restart only when the player explicitly chooses Resume, using a fresh ticket, token and operation UUID.

All provider completions, including already-completed and exceptional futures, must return through the Minecraft server executor before touching game state. The completion path then checks that the ticket is still current and matches player, villager, session, token, generation, node, passage, live world and interaction owner. Invalid or late results are discarded even when the underlying provider could not be cancelled. Exceptions, cancelled jobs and executor failures resolve to controlled fallback behavior while the session is valid. Futures and live entity references stay out of persisted sessions/history. Only a subsequent ordinary final acknowledgment can commit rewards, commands and history once.

The built-in `mca:structure_location` passes this **operation UUID**, distinct for every player, request and Resume, as the owner to `AsyncStructureLocator.locate`, then calls `AsyncStructureLocator.cancel` with that exact owner. It preserves the locator's temporary chunk-ticket cleanup. It never uses the villager UUID, which also owns the unrelated legacy `location` command's search, so cancelling a dialogue lookup cannot cancel that legacy request. Exactly one selector is required, either `pool: "configured_rumors"` from `Config.structuresInRumors` or `structure: "namespace:path"` from the registered world structures. The existing random origin offset uses horizontal range `1024`, vertical `0`, and radius `64`. A successful lookup overrides the authored line with `dialogue.location.<structurePath>` and passes the exact `x,y,z` string as the first explicit translator argument, filling **`%2$s`**. Missing structures, empty pools, misses, exceptions or timeouts use `dialogue.location.forgot`, with its existing numbered locale variants. The outcome appears **inside the parchment**, without sending a vanilla chat message. The unrelated `VillagerCommandHandler` gameplay `location` command keeps its chat behavior. Other `mca:command` effects still follow normal deferred commit and command-authorization rules.

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

These types can be used as `{"type":"myaddon:min_experience","levels":5}` in event/choice/outcome requirements and `{"type":"myaddon:add_experience","levels":1}` in choice/outcome actions. Codecs parse the object **without `type`**, and must serialize an object without that field. Invalid or unknown types reject the containing resource. Registered action executors run on the logical server when the dialogue completes, ordinarily upon final acknowledgement and immediately on the supported slap-ending choice transition; they should not throw after applying partial effects. Condition evaluations must preserve `UNAVAILABLE` when a referenced dependency is missing, including under `mca:not`.

The registry is process-wide, and IDs cannot be duplicated or override built-in types. There is **no legacy `Actions.register`/`GiftPredicate.register` compatibility adapter**. See the [datapack guide](dialogue-datapack-guide.md) for a complete matching JSON example.

### Register a server-side interaction command

Call `InteractionCommands.register` from the addon's **common initialization**, before players use the dialogue or menu. For example (illustrative Java addon code):

```java
InteractionCommands.register(
    ResourceLocation.fromNamespaceAndPath("myaddon", "inspect"),
    context -> context.villager() instanceof VillagerEntityMCA,
    context -> {
        VillagerEntityMCA villager = (VillagerEntityMCA) context.villager();
        villager.sendChatMessage(context.player(), "myaddon.inspect.done");
        return VillagerCommandHandler.DialogueCommandResult.accepted(false);
    }
);
```

The same registered command can be invoked from a choice/outcome action, without any new effect codec:

```json
{ "type": "mca:command", "command": "myaddon:inspect" }
```

Or from an existing client resource-pack GUI layout button (with the corresponding translation key):

```json
{ "identifier": "gui.myaddon.inspect", "notifyServer": true,
  "targetServer": false, "command": "myaddon:inspect" }
```

The optional button `command` is independent of its display identifier. Existing buttons without it keep their original identifier-derived command. An explicitly blank command sends nothing. The server must have the Java addon installed and loaded; a datapack or resource pack cannot register a callback. `Context.source()` is `MENU` or `DIALOGUE`, chosen by the server; an addon's read-only eligibility predicate can accept one or both. The dispatcher checks live player/villager, range, current interaction UUID and source eligibility on the server thread immediately before execution. A denial or missing command rejects without closing the screen. Callback exceptions are logged and rejected, but arbitrary mutations made *before* an exception cannot be rolled back. Do not perform slow/blocking work in a synchronous command callback, and never assume client UI resources confer authority.

### Register a generic async node resolver

`DialogueAsyncResolver.register(ResourceLocation id, Codec<T> codec, DialogueAsyncResolver.Provider<T> provider)` registers a namespaced type during **common mod initialization before the dialogue datapack reload**. The registry and decoder live in shared MCA code for Fabric and NeoForge. An addon must supply and install its Java implementation on the server for the configured type to exist. The following is **illustrative addon code** for `landmarkaddon:landmark_note`, not an existing MCA provider. It matches the [complete addon event and resource-pack example](dialogue-datapack-guide.md#9-resolve-a-dialogue-line-with-an-addon-or-a-structure-search).

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

The snippet requires imports for `DialogueAsyncResolver`, `ResourceLocation`, `Codec` and `CompletableFuture`. It uses an already-completed future to illustrate the schema, so its pending animation may be imperceptible. A real provider implements `Provider<T>.resolve(T parameters, DialogueContext context, UUID operationId)` and returns `CompletionStage<DialogueAsyncResolver.Result>`. It can override `Provider<T>.cancel(T parameters, UUID operationId)` to stop owned work. The default cancel is a no-op, and the engine still guards against late callbacks. The engine calls the resolved node instance's `DialogueAsyncResolver.resolve(context, operationId)` and `cancel(operationId)` with the same identity.

The codec reads and writes only provider-specific parameters, such as `clue`, as an object without the reserved `type`, `pending_line` or `failure_line` fields. Unknown IDs, extra fields, malformed parameters, blank keys and duplicate registrations are rejected. A provider returns `Result.found(args...)` to use the authored success `line`, `Result.withKey(key, args...)` to select a validated nonblank alternate translation key, or `Result.missing()` for the authored failure line. String arguments have bounded size and count. The server resolves the selected key with `villager.getTranslatable(player, key, args...)`, preserving `%1$s` for the player and `%2$s` for the first explicit argument, including coordinates. Language variants are selected by the normal MCA translation pipeline, and each client requires matching localized assets. The server validates key syntax but cannot detect a missing client translation.

Providers must be **idempotent and read-only**. Capture safe immutable inputs before background work, marshal completions to the server executor, and leave mutable entity, world, registry and session access on the server thread. A resolver does not apply commands, rewards or completion history. Datapacks only configure built-in or installed addon Java providers with validated codec fields. They cannot register executable Java, scripts or arbitrary commands. For `mca:structure_location`, the built-in resolver performs the registered-structure search and preserves the legacy location translations and cancellation ownership described above.

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

For async content, verify the addon has registered its resolver before `/reload`, the `resolve` object matches its codec, and the player has enabled the corresponding client resource pack. Unknown resolver IDs, malformed `failure_line`, unsupported `lines`/`outcomes`, conflicting `pool`/`structure` and extra fields are reload errors. For a valid but unsuccessful `mca:structure_location` search, check `Config.structuresInRumors`, registered structure IDs and the 600-tick deadline. A missing or failed lookup resolves to the localized failure line. If a translation key is visible, inspect the precise client locale and MCA's numbered `dialogue.location.<structurePath>/N` or `dialogue.location.forgot/N` variants, then refresh client resources with F3+T. A typing-only three-dot row is expected without an async bar.

The async integration smoke matrix should cover immediate and delayed successful results, misses and timeouts, provider exception/cancellation, repeated clicks, pause and explicit Resume, player logout/death, switch to another villager, `/reload` during search, server shutdown, stale token/session or cross-villager callbacks, and simultaneous players. Verify the parchment result contains coordinates when found, **no dialogue lookup result is sent to vanilla chat**, and rewards/history commit once only at the supported completion point. Exercise normal and reduced-motion reveal, empty choice sets, Finish, both parchment placements and small GUI scales. Test both **Fabric and NeoForge**, including dedicated-server class loading and a real client screen. This is a verification checklist for the P4/P2 implementation owners, not evidence that those tests have run.

For content examples, see [DialogueEvent authoring](dialogue-authoring.md). The [design specification](superpowers/specs/2026-10-06-dialogue-event-conversation-system-design.md) records earlier decisions; the rules here reflect the implemented 1.21.1 code. Real-client and multiplayer testing on both loaders is still required for release confidence; automated server tests alone cannot verify UI and network behavior.
