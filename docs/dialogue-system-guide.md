# DialogueEvent developer guide

MCA's DialogueEvent system runs data-driven villager conversations on Minecraft 1.21.1. This guide explains its Java ownership, supported datapack format, selection and execution rules, history, and extension points. Most new conversations need only a JSON resource and translations. New types of gameplay facts or effects require Java changes.

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

## 1. Define an event

Datapacks and addon mods supply resources at these exact paths:

```text
data/<namespace>/dialogue_events/<path>.json
assets/<namespace>/lang/<locale>.json
```

For example, `data/example_addon/dialogue_events/weather/rain_chat.json` has the ID `example_addon:weather/rain_chat`. The namespace and nested path are part of the ID. Do not put an additional `id` field into the JSON. A standalone datapack puts the `data/` path beside its `pack.mcmeta`; translations belong in an enabled client resource pack or mod assets.

This complete example offers an Ask topic in rainy weather. The player hears two passages, chooses one reply, and queues a heart reward with amount `2` for successful completion.

```json
{
  "trigger": "talk",
  "presentation": {
    "mode": "ask",
    "prompt": "dialogue_event.example_addon.rain.prompt",
    "resume_prompt": "dialogue_event.example_addon.rain.resume"
  },
  "requirements": [
    { "type": "mca:weather", "value": "rain" }
  ],
  "repeat": { "type": "cooldown", "seconds": 5 },
  "history": "story",
  "start": "intro",
  "nodes": {
    "intro": {
      "lines": [
        "dialogue_event.example_addon.rain.first",
        "dialogue_event.example_addon.rain.second"
      ],
      "choices": [
        {
          "id": "agree_about_rain",
          "text": "dialogue_event.example_addon.rain.agree",
          "actions": [{ "type": "mca:hearts", "amount": 2 }],
          "next": "goodbye"
        }
      ]
    },
    "goodbye": {
      "line": "dialogue_event.example_addon.rain.goodbye",
      "complete": true
    }
  }
}
```

Supply `assets/example_addon/lang/en_us.json`:

```json
{
  "dialogue_event.example_addon.rain.prompt": "Do you enjoy the rain?",
  "dialogue_event.example_addon.rain.resume": "We were talking about the rain...",
  "dialogue_event.example_addon.rain.first": "It's been raining all afternoon.",
  "dialogue_event.example_addon.rain.second": "The sound on the roof is rather nice, though.",
  "dialogue_event.example_addon.rain.agree": "I like it too.",
  "dialogue_event.example_addon.rain.goodbye": "Then perhaps we can enjoy it together."
}
```

Once the pack is enabled, `/reload` updates server dialogue resources. Talk to an awake, interactable MCA villager in rain to find the new Ask choice. The last `Back to topics` acknowledgement is required for completion and the heart reward.

### Top-level fields

| Field | Required | Meaning |
| --- | --- | --- |
| `trigger` | Yes | Currently only `"talk"` |
| `presentation` | Yes | `mode` and `resume_prompt`; `prompt` required for Ask and highlighted; optional `topic` |
| `priority` | No | Integer, defaults to `0` |
| `weight` | No | Finite positive number, defaults to `1` |
| `requirements` | No | List of conditions, defaults to empty; every entry must match |
| `repeat` | Yes | `always`, `once` or `cooldown` policy |
| `history` | No | `story` (default) or `scheduling` |
| `start` | Yes | Name of the first local node |
| `nodes` | Yes | Object containing the event's node graph |

The `DialogueEvent` decoder validates allowed fields and rejects unknown event, node, choice, outcome, condition or action fields. Write fully namespaced cross-event IDs so they resolve to the intended pack (an unqualified resource location defaults to Minecraft's namespace). Weights must be finite and greater than zero.

## 2. Presentation and selection

| Mode | Behavior |
| --- | --- |
| `highlighted` | Highest-priority eligible event occupies the highlighted slot; other eligible highlighted events remain in Ask |
| `ask` | Selectable topic under the grey, non-clickable `Ask about...` heading |
| `ambient` | Candidate behind `What's on your mind?`, with no separate topic prompt |

`highlighted` and `ask` require `presentation.prompt`. Every mode, including `ambient`, requires `presentation.resume_prompt`. Resume labels may use `%1$s` for the villager's name. Optional `presentation.topic` is authoring metadata, not a submenu.

Highlighted and Ask events are ordered by descending `priority`, then ID. At most **64** direct event options are transmitted per menu. For ambient selection, only the highest eligible priority tier participates; the engine selects among events in that tier by `weight` when the player clicks the ambient option. Weight does not let an ineligible event bypass prerequisites. The shipped `mca:ambient/baseline` has the lowest possible priority and a zero-second cooldown, preserving ordinary small talk.

## 3. Node graphs, replies and random branches

Each node is local to one event. A visible node has exactly one `line` string or `lines` array of ordered, nonblank translation keys. It also has exactly one continuation shape:

| Field | Result |
| --- | --- |
| `next` | Advance to the named node |
| `choices` | Show eligible player replies after the final line |
| `complete: true` | Successful terminal; commit when the player acknowledges it |
| `end: true` | End the run without committing a completion |
| `outcomes` | Automatically choose another node without displaying this routing node |

The UI reveals one complete passage at a time. `Next` advances between passages or to a `next` node. Choices become clickable after the final passage has finished revealing. A terminal displays `Back to topics`; clicking it commits a successful `complete` node and refreshes Talk. Translation suffixes such as `/1` and `/2` are MCA's alternative phrasings, not ordered passages. Use distinct keys inside `lines`.

`silent: true` is an optional visible-node flag. In the client, it runs the text through `villager.transformMessage` instead of the ordinary `villager.sendChatMessage` path. The default is `false`.

Each choice accepts:

- `id`: nonblank, unique throughout this event, at most **96** characters. History stores this ID.
- `text`: nonblank translation key displayed as the player's reply.
- `requirements`: optional additional conditions, all required to match.
- Direct `next` plus optional `actions`, or `outcomes` in place of both.

A visible node can offer at most **32** choices. A direct choice requires `next`; an outcome-based choice cannot also have direct `next` or direct `actions`. Both node targets and choice targets must exist, and graphs cannot have cycles.

When you want an eligible reply to have several possible results, use `outcomes`:

```json
{
  "id": "ask_about_work",
  "text": "dialogue_event.example_addon.work.ask",
  "outcomes": [
    {
      "requirements": [{ "type": "mca:mood", "value": "happy" }],
      "weight": 3,
      "actions": [{ "type": "mca:hearts", "amount": 2 }],
      "next": "happy_answer"
    },
    { "weight": 1, "next": "ordinary_answer" }
  ]
}
```

An outcome has optional `requirements`, positive `weight` (default `1`), optional `actions` and required `next`. The server filters ineligible outcomes before making the weighted choice. A reply with no eligible outcomes is hidden; its eligibility is checked again when the client sends the reply. The chosen outcome is retained in the session rather than rerolled on resume.

For routing before any text, define a node with `outcomes` and no `line`/`lines`. An automatic routing node must include at least one unconditional outcome so a destination remains possible. A reachable `end: true` node in a `once` or `cooldown` event must explicitly set `retryable: true`. Use `complete: true` for successful endings.

## 4. Supported conditions

Every requirement is a typed JSON object, for example `{ "type": "mca:hearts", "min": 20 }`. Requirements can appear on the event, a player choice, or an outcome. An array means logical AND. Probability belongs in weights, not in the condition definitions.

`DialogueCondition` currently recognizes the following built-in types:

| Type | Fields and evaluated state |
| --- | --- |
| `mca:personality` | `value`: registered MCA personality, such as `gloomy` or `crabby` |
| `mca:mood` | `value`: `depressed`, `sad`, `unhappy`, `passive`, `fine`, `happy`, or `overjoyed` |
| `mca:hearts` | `min` and/or `max`: villager's heart count for this player |
| `mca:relationship` | `value`: `spouse`, `engaged`, `promised`, `romantic_partner`, `single`, or `widow` |
| `mca:family` | `value`: `family`, `relative`, `parent`, `child`, or `orphan` |
| `mca:age_group` | `value`: valid MCA `AgeState` enum (e.g. `adult`, `child`) |
| `mca:profession` | `value`: registered vanilla/MCA profession ID, such as `minecraft:farmer` or `mca:guard` |
| `mca:rank` | `value`: MCA `Rank` enum for the player's rank in the villager's village |
| `mca:trait` | `value`: registered MCA trait, such as `lactose_intolerance` |
| `mca:health` | `min` and/or `max`: villager's current health |
| `mca:infected` | Active infection; optional inclusive progress `min`/`max` from 0.0 to 1.0, or neither for any infection |
| `mca:gender` | `value`: valid MCA `Gender` enum |
| `mca:pregnancy` | Required boolean `value`; if true, optional `min_progress`, `max_progress`, `child_gender` |
| `mca:time` | `value: "day"` / `"night"`, or a `min`/`max` range in [0, 24000] game-time ticks |
| `mca:weather` | `value`: `clear`, `rain`, or `thunder`; ordinary rain excludes thunderstorms |
| `mca:biome` | `value`: biome ID at the villager's position |
| `mca:advancement` | `value`: ID of an advancement completed by this player |
| `mca:village_has_building` | `value`: registered building type in the villager's home village |
| `mca:in_building` | `value`: building type containing the villager's current position |
| `mca:building_assignment` | `value`: building type at `source` `home` (default) or `workplace` |
| `mca:item` | `value`: registered item ID; optional `min`/`max` count in player's inventory |
| `mca:tag` | `value`: item tag ID; optional `min`/`max` count in player's inventory |
| `mca:inventory` | Exactly one `item` ID or `tag` ID; optional `min`/`max` inventory count |
| `mca:memory` | `id`: long-term villager memory; optional `var: "player"` and `present` (default true) |
| `mca:recent_event` | `event`: a registered villager life-event ID; optional nonnegative `within_ticks` (omitted means ever occurred) |
| `mca:hit_by` | No additional fields; villager's tracked hit-by-this-player state |
| `mca:village_has_space` | No additional fields; capacity in the player's last-seen village |
| `mca:event_completed` | `event`: full event ID with a successful `story` completion for this pair |
| `mca:event_choice` | `event` and `choice`: stable choice ID from that story's latest completed run |
| `mca:not` | `condition`: one nested, negated condition |

Numeric ranges use inclusive boundaries. The `hearts` and `health` conditions need at least one bound. Inventory-count conditions default to a minimum count of one. A time-of-day range uses the world's day time modulo 24,000. The `mca:memory` condition checks `present: false` when you need an absent memory; `var: "player"` addresses a memory scoped to the talking player.

Registered `mca:recent_event` IDs are `mca:relative_death`, `mca:attacked`, `mca:zombified`, `mca:cured`, `mca:revived`, and `mca:raid_survived`. These timestamps come from actual gameplay producers. A story cannot create a missing life event merely by referring to its ID.

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

Effects accumulate in the server-side session. They are applied only when the player acknowledges a `complete: true` terminal with `Back to topics`. The engine checks history writability, validates any command, runs the command, applies the other actions, and stores the successful completion. An `end: true` terminal, discarded session, or unsuccessful commit does not record a successful event. The usual heart-reward path can affect mood too, so do not add a separate matching mood action just to imitate old dialogue data.

## 6. Repeat, pair history, and follow-ups

The `repeat` field is mandatory and supports these exact formats:

```json
{ "type": "always" }
{ "type": "once" }
{ "type": "cooldown", "seconds": 5 }
{ "type": "cooldown", "min_seconds": 30, "max_seconds": 90 }
```

Use either `seconds` or the pair `min_seconds`/`max_seconds` for a cooldown, never both. All durations are finite nonnegative seconds, normalized to ticks with `ceil(seconds * 20)`. A range is rolled once on successful completion. Legacy social/reference conversations commonly use five seconds, or 100 ticks at 20 TPS; authored personality conversations use 60–180 seconds for ambient chatter and 300–900 seconds for stories.

Eligibility and history are scoped to **(player UUID, villager UUID, event ID)**. Successful `story` history records a completion count, latest completion time, next eligible time, and choice IDs from the latest completed run. Repeating the same story with a different reply replaces those remembered choice IDs. Abandoning a run does not rewrite story history.

`history: "scheduling"` records only a cooldown, and expired scheduling records can be pruned. It is valid only with `repeat.type: "cooldown"`. A scheduling-only event cannot be the target of `mca:event_completed` or `mca:event_choice`.

Write follow-ups as separate events. For example, put these requirements in the follow-up to the rain event above:

```json
"requirements": [
  {
    "type": "mca:event_completed",
    "event": "example_addon:weather/rain_chat"
  },
  {
    "type": "mca:event_choice",
    "event": "example_addon:weather/rain_chat",
    "choice": "agree_about_rain"
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
  Next / Back to topics -> InteractionDialogueAdvanceMessage
    DialogueEngine.advance(...): next passage/node, or commit/end
    <- InteractionDialogueNodeResponse (active / paused / ended)
  Leave Talk / close screen -> InteractionDialogueLeaveMessage / InteractionCloseRequest
    DialogueEngine.pause(...)
```

`DialogueEngine` retains one active or paused session per player, plus one menu offer. Each session carries the bound player and villager IDs, resource generation, session ID, current node and passage index, offered choices, accepted choice IDs, selected outcomes, pending effects, and token. The server rejects unoffered event IDs, stale or replayed tokens, unavailable choices, and invalid interactions. It checks that the bound villager is alive, available, in range, awake, and not trading or panicking.

Closing the screen or moving out of range pauses an unfinished run for **2,400 overworld game ticks**. The player can reopen Talk with the same villager and explicitly select the topic-specific continuation. Browsing the menu does not resume or extend the pause. Starting a different valid event abandons it. Paused sessions do not survive logout, server stop, resource reload, permanent villager removal, or the pause deadline. A datapack reload increments `DialogueEvents.generation()` and invalidates stale sessions/offers.

The server sends a complete text Component for each visible passage. `ClientHandlerImpl.DialoguePresentation` reveals one Unicode grapheme every **40 ms**, without extra packets for each character. Replies and `Next`/`Back to topics` appear only after the current passage has finished revealing. Clicking early cannot skip or queue progress; there is no instant-reveal setting. `InteractScreen` renders the grey, non-clickable `Ask about...` section header and the actual selectable topics. Presentation timing does not grant completion or rewards.

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

More examples are in [DialogueEvent authoring](dialogue-authoring.md). [DialogueEvent porting](dialogue-porting.md) describes newer Minecraft API seams, not completed ports. The [design specification](superpowers/specs/2026-10-06-dialogue-event-conversation-system-design.md) includes intentions and proposals; the rules in this guide describe the implemented 1.21.1 code.

Before release, complete the [interactive and multiplayer acceptance checklist](dialogue-release-smoke.md) with evidence from real clients on both loaders. Automated server tests cannot certify those UI and wire-protocol gates.
