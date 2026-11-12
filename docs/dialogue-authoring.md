# DialogueEvent authoring guide

Write MCA conversations as namespaced datapack `DialogueEvent` resources. Each event defines when it is available and what happens during the conversation. You don't need Java to add dialogue.

New to datapacks? Start with the [step-by-step tutorial](dialogue-datapack-guide.md). Implementing new conditions or actions in Java? See the [developer guide](dialogue-system-guide.md).

Place resources under:

```text
data/<namespace>/dialogue_events/<path>.json
assets/<namespace>/lang/<locale>.json
```

For example, `data/example_addon/dialogue_events/personal/storm_memory.json` has
the event ID `example_addon:personal/storm_memory`. Keep that full namespaced ID
stable once another story or saved history can refer to it.

## Mental model

The initial trigger is `talk`. When the player opens Talk, the server evaluates
eligible events for that player and villager, applies completion/cooldown/history
rules, and presents them according to their presentation mode. A paused
conversation is offered separately as its authored continuation.

```text
Talk
  -> paused continuation, if one exists for this pair
  -> evaluate new DialogueEvents
  -> Conversations: small_talk, location, game
     -> What's on your mind? (ambient mode, selected separately)
  -> Relationships: relationship
  -> Personal stories: personal, life_event, introduction
  -> Practical matters: service
  -> Other topics: missing or addon topic names
  -> Back to interaction
```

`requirements` control eligibility. `priority` chooses the eligible tier; `weight` controls the random pick within that tier. Do not use requirements to model probability.

## Minimal event

```json
{
  "trigger": "talk",
  "presentation": {
    "mode": "ask",
    "prompt": "dialogue_event.example_addon.storm.prompt",
    "resume_prompt": "dialogue_event.example_addon.storm.resume",
    "topic": "weather"
  },
  "priority": 20,
  "weight": 1,
  "requirements": [
    { "type": "mca:weather", "value": "rain" }
  ],
  "repeat": { "type": "cooldown", "seconds": 5 },
  "history": "story",
  "start": "intro",
  "nodes": {
    "intro": {
      "line": "dialogue_event.example_addon.storm.intro",
      "complete": true
    }
  }
}
```

`trigger`, `presentation`, `repeat`, `start`, and `nodes` are required. `priority`
defaults to `0`, `weight` defaults to `1`, `requirements` defaults to an empty
list, and `history` defaults to `story`.

### Interaction fatigue and refusal endings

An outcome can declare `"fatigue": 1` to add the saved player/villager fatigue
contribution to its weight. Ambient events can also declare an event-level
`fatigue` rate. Both rates default to zero; they must be finite and nonnegative.
An outcome or ambient event may have a zero or negative base `weight` only if
its fatigue rate is positive. Nonpositive *effective* weights are never eligible,
and inactive high-priority entries do not block lower-priority entries.

The server computes the additive contribution using the integer-truncated
`pairFatigue * interactionChanceFatigue` value. For example, with multiplier
1, an ambient event with `"weight": -3, "fatigue": 1` has weight 0 at saved
fatigue 3, weight 1 at fatigue 4, and weight 3 at fatigue 6.

`Config.interactionFatigueEnabled` defaults to `true`. Turning it off prevents
new fatigue accumulation and makes all fatigue-weight contributions zero, but
does not clear existing saved fatigue or prevent its normal cooldown decay.
With the switch enabled and `interactionChanceFatigue = 0`, fatigue still
accumulates but does not affect selection. Ordinary heart changes and other
dialogue effects continue when fatigue is disabled.

Set `"close_interaction": true` on a completing visible node to end its
interaction after the player reads and acknowledges its final line. This flag
defaults to false and is only valid on a `complete` node. The ambient
`root/busy.json` and `root/goaway.json` events use it for fatigue-based
refusals; their base weights are -3 and -5 respectively. When a refusal is
offered, it shows only the refusal and a Finish action. Finishing closes the
matching conversation; leaving early still follows the normal screen-close
path.

## Presentation modes

Use the smallest mode that matches the importance of the conversation:

- `highlighted` is for the most important currently relevant story. The highest
  priority highlighted event gets the highlighted slot; additional highlighted
  events remain selectable in their semantic section rather than disappearing.
- `ask` is a directly selectable event under the section named by its `topic`.
- `ambient` participates in the `What's on your mind?` pool. Only the highest
  eligible ambient priority tier is considered, then positive `weight` chooses
  within that tier.

`prompt` is required for `highlighted` and `ask`. Ambient events do not need a
new-topic prompt. Every event must provide `resume_prompt`, including ambient
events, because a paused run needs a specific way back into that exact
conversation.

`presentation.mode` controls how an event is offered. `presentation.topic` controls its Talk menu section (for directly selectable events). Known topics
map to Conversations (`small_talk`, `location`, `game`), Relationships
(`relationship`), Personal stories (`personal`, `life_event`, `introduction`),
or Practical matters (`service`). Missing, blank, overlong, or unrecognized
topics are safely offered in **Other topics**, rather than silently hidden.
Sections can be expanded/collapsed without starting an event. Highlighted
events are marked inside their corresponding section.

## Continuation labels and `%1$s`

Write continuation labels for the actual unfinished conversation, not a generic
"Continue our conversation" label. The server may pass the speaking villager's
display name as `%1$s` to the `resume_prompt` translation.

```json
{
  "dialogue_event.example_addon.storm.resume": "%1$s, about the storm keeping you awake..."
}
```

The name is optional in the prose. A more natural topic-specific label is better
than forcing `%1$s` into every continuation:

```json
{
  "dialogue_event.example_addon.mourning.resume": "You were telling me about your brother..."
}
```

Resume wording belongs in the Talk menu only. Do not insert an extra villager line
merely to announce that a conversation resumed.

## Nodes, passages, and routing

Each event owns a node graph. `start` is a local node ID, not a global resource
ID. Node IDs are internal graph structure and may be reorganized; choice IDs are
the durable story API.

A visible node has exactly one of `line` or `lines`, plus exactly one continuation
shape: `choices`, `next`, `complete`, or `end`.

Use `line` for one passage:

```json
"intro": {
  "line": "dialogue_event.example_addon.story.intro",
  "next": "question"
}
```

Use `lines` for ordered consecutive villager passages:

```json
"intro": {
  "lines": [
    "dialogue_event.example_addon.cure.intro.heard_about_cures",
    "dialogue_event.example_addon.cure.intro.same_person"
  ],
  "choices": [
    {
      "id": "experience_changes_you",
      "text": "dialogue_event.example_addon.cure.choice.experience_changes_you",
      "next": "reflect"
    }
  ]
}
```

Those keys are a sequence. MCA's existing `/1`, `/2`, and similar localization
suffixes are pooled alternative phrasings, not ordered passages. Do not use pooled
suffixes when you mean "say A, then B".

### Automatic routing nodes

For a weighted or conditional route that should happen before anything is shown,
use a line-less `outcomes` node:

```json
"start": {
  "outcomes": [
    {
      "requirements": [
        { "type": "mca:personality", "value": "gloomy" }
      ],
      "weight": 3,
      "next": "gloomy_version"
    },
    {
      "weight": 1,
      "next": "generic_version"
    }
  ]
}
```

An automatic routing node has no `line` or `lines`, and it must include at least
one unconditional outcome so the route cannot dead-end solely because conditions
fail.

### Asynchronous node results

A visible node may declare an optional `resolve` object when its line depends on
a server-side lookup. Version 1 accepts exactly one `line` alongside `resolve`.
Do not combine it with `lines` or an automatic `outcomes` route.

```json
"location": {
  "line": "dialogue_event.example_addon.location.found",
  "resolve": {
    "type": "mca:structure_location",
    "pool": "configured_rumors",
    "pending_line": "dialogue_event.example_addon.location.searching",
    "failure_line": "dialogue.location.forgot"
  },
  "complete": true
}
```

The `type` names a built-in or Java-registered addon resolver. `failure_line`
is required. `pending_line` defaults to `gui.dialogue.searching`. For the
built-in structure lookup, provide exactly one of `pool: "configured_rumors"`
or `structure: "namespace:path"`. The successful built-in result uses the
existing `dialogue.location.<structurePath>` translations, substituting the
player's name into `%1$s` and the exact `x,y,z` coordinates into `%2$s`.
An addon resolver can instead use the authored `line` key or return a validated
override key. Supply any custom keys in the client's resource pack.

Registered resolver providers must perform idempotent, read-only work. The server
owns the lookup, pending state, 30-second timeout and cancellation. A datapack
can configure installed resolvers but cannot define executable provider code.
Successful dialogue history and actions commit only after the player finishes
the resolved node. During the search, the parchment shows the localized pending
line and an indeterminate bar. Choices become available after the resolved line
has finished revealing.

## Choices and weighted outcomes

Choice text should read like a natural player reply. Do not expose labels such as
"positive", "neutral", "negative", heart deltas, or outcome probabilities.

A direct choice may have `requirements`, optional `actions`, and one `next`:

```json
{
  "id": "comfort",
  "text": "dialogue_event.example_addon.mourning.choice.comfort",
  "actions": [
    { "type": "mca:hearts", "amount": 5 }
  ],
  "next": "comforted"
}
```

Choice IDs must be unique across the entire event. Use semantic stable IDs such as
`comfort`, `ask_why`, or `defend_village`, especially when a later event may refer
to them. Avoid generic IDs such as `yes` or `no` when they would become ambiguous
story history.

If one reply can resolve in several ways, give the choice `outcomes` instead of a
direct `next`/`actions` pair:

```json
{
  "id": "ask_about_work",
  "text": "dialogue_event.example_addon.work.choice.ask",
  "outcomes": [
    {
      "requirements": [
        { "type": "mca:mood", "value": "happy" }
      ],
      "weight": 2,
      "actions": [
        { "type": "mca:hearts", "amount": 3 }
      ],
      "next": "enthusiastic_reply"
    },
    {
      "weight": 1,
      "next": "ordinary_reply"
    }
  ]
}
```

The server first discards outcomes whose hard requirements do not match, then
retains only the eligible outcomes at the highest `priority` before the
weighted draw. A choice is not offered when none of its outcomes are eligible.
For player-invoked affection, show the villager's opening line and a real
first-person player `choices[].text` **before** choosing a reaction. Established
romantic partners use a highest-priority positive-only tier (the shipped
Flirt/Hug/Kiss events use priority `100` and `romantic_partner`); nonpartner
Flirt odds improve at `30` and `60` hearts using lower-priority tiers, while
the normal age/family requirements still apply. Consequential gameplay requests
may instead use concise confirmations and direct feedback.

## Completion, ending, and rewards

`complete: true` makes a successful ending. Normally the player must select
**Finish conversation**, which returns to the topic list, to save their choices,
history, cooldown and pending effects. A provoking reply that leads directly to a
single-line, completed slap reaction is the exception: accepting the reply
completes the dialogue immediately, closes the entire interaction UI, and commits
history and other authorized effects once. The server applies the slap three ticks
later, then sends the final authored reaction in localized chat once, without a
final **Finish conversation** click.

```json
"good_end": {
  "line": "dialogue_event.example_addon.story.good_end",
  "complete": true
}
```

`end: true` stops without successful completion. For `once` or `cooldown` events,
a reachable non-completing end must explicitly opt into `retryable: true`:

```json
"not_now": {
  "line": "dialogue_event.example_addon.story.not_now",
  "end": true,
  "retryable": true
}
```

Choosing Topics/Q, backing out or closing the screen is **not** completion.
It pauses the active run without awarding pending effects. If the pause
expires or the run is otherwise invalidated, unfinished choices and pending effects
are discarded.

### Built-in actions

Current built-in server actions are:

```json
{ "type": "mca:hearts", "amount": 5 }
{ "type": "mca:mood", "amount": -2 }
{ "type": "mca:remember", "id": "some_memory" }
{ "type": "mca:remember", "id": "some_memory", "var": "player", "time": 24000 }
{ "type": "mca:command", "command": "existing_villager_command" }
{ "type": "mca:slap", "amount": 2.0 }
```

Actions are server-owned and are committed with the successful conversation
completion, not when the client merely displays a line. For a slap-ending reply,
other authorized actions and history commit immediately, while the physical hit
and final chat reaction follow the three-tick delay. Use `mca:command` only to
delegate to an existing MCA gameplay command owner; do not reimplement gameplay
logic in dialogue data.

`mca:slap` makes the villager swing an empty hand when one is available and hit
the player with the specified positive amount of vanilla mob-attack damage
(1.0 damage = half a heart). It does not equip or use a weapon. Author slap
outcomes with the typed action. The former `mca:command` `slap` action is no
longer accepted by the dialogue engine.

`mca:hearts` already follows MCA's heart-reward behavior. Do not automatically add
an equal `mca:mood` action beside it just to mimic legacy dialogue data, because
that can double-apply mood changes. Add a separate mood action only when the story
actually intends an additional mood delta.

`mca:command` accepts the existing dialogue commands `adopt`, `apologize`, `divorcePapers`, `divorceConfirm`, `hire_short`, `hire_long`, `procreate`, `stay_in_village`, and `location`, plus namespaced commands registered by an installed Java addon, such as `myaddon:inspect`. A datapack can *invoke* a registered command, but cannot define its executable behavior or run arbitrary Minecraft commands/functions. Use at most one command action per conversation. The server rechecks the interaction owner, distance, command permissions and the handler's success before committing other actions or history.

## Requirements

Event, choice, and outcome requirements are deterministic hard gates. An array is
implicit AND. Use separate events for alternatives until authored content proves a
need for a general boolean-expression format.

Common examples:

```json
{ "type": "mca:personality", "value": "gloomy" }
{ "type": "mca:mood", "value": "sad" }
{ "type": "mca:hearts", "min": 20 }
{ "type": "mca:relationship", "value": "spouse" }
{ "type": "mca:age_group", "value": "adult" }
{ "type": "mca:profession", "value": "minecraft:farmer" }
{ "type": "mca:trait", "value": "lactose_intolerance" }
{ "type": "mca:health", "min": 1.0, "max": 10.0 }
{ "type": "mca:infected" }
{ "type": "mca:infected", "min": 0.2, "max": 0.8 }
{ "type": "mca:time", "value": "night" }
{ "type": "mca:time", "min": 13000, "max": 23000 }
{ "type": "mca:weather", "value": "rain" }
{ "type": "mca:mourning" }
{ "type": "mca:biome", "values": ["minecraft:plains"] }
{ "type": "mca:biome", "values": ["minecraft:plains", "#mca:forest_biomes"], "scope": "current" }
{ "type": "mca:biome", "values": ["#mca:snowy_biomes"], "scope": "home" }
{ "type": "mca:advancement", "value": "minecraft:story/mine_diamond" }
{ "type": "mca:village_has_building", "value": "infirmary" }
{ "type": "mca:in_building", "value": "infirmary" }
```

### Condition reference

All built-in requirements are shown below. Conditions can be placed at event, choice or outcome level; every condition in an array must match.

| Type | Fields / meaning |
| --- | --- |
| `mca:personality` | `value`: registered personality, such as `crabby` |
| `mca:mood` | `value`: `depressed`, `sad`, `unhappy`, `passive`, `fine`, `happy`, `overjoyed` |
| `mca:hearts` | `min` and/or `max`: hearts with the player |
| `mca:relationship` | `value`: `spouse`, `engaged`, `promised`, `romantic_partner`, `single`, `widow` |
| `mca:family` | `value`: `family`, `relative`, `parent`, `child`, `orphan` |
| `mca:age_group` | `value`: MCA age group such as `adult` |
| `mca:profession` | `value`: profession ID such as `minecraft:farmer` |
| `mca:rank` | `value`: player's rank in the villager's village |
| `mca:trait` | `value`: registered trait |
| `mca:health` | `min` and/or `max`: villager health |
| `mca:infected` | Active infection; optional `min`/`max` progress from 0.0 to 1.0 |
| `mca:mourning` | Villager currently has an active mourning assignment; no fields |
| `mca:gender` | `value`: MCA gender |
| `mca:pregnancy` | Boolean `value`; when true, optional `min_progress`, `max_progress`, `child_gender` |
| `mca:time` | `value`: `day`/`night`, or `min`/`max` ticks in [0, 24000] |
| `mca:weather` | `value`: `clear`, `rain` or `thunder` |
| `mca:biome` | Nonempty `values` list of IDs/tags; `scope`: `current` or `home` |
| `mca:advancement` | `value`: completed advancement ID |
| `mca:village_has_building` | `value`: building type in home village |
| `mca:in_building` | `value`: building type at villager's position |
| `mca:building_assignment` | `value`: building type; `source`: `home` (default) or `workplace` |
| `mca:item` / `mca:tag` | `value`: item or tag ID; optional inventory `min`/`max` |
| `mca:inventory` | Exactly one `item` or `tag` ID; optional inventory `min`/`max` |
| `mca:memory` | `id`: memory; optional `var: "player"`, `present` (default true) |
| `mca:recent_event` | `event`: registered life-event ID; optional `within_ticks` |
| `mca:hit_by` | Villager was hit by this player; no fields |
| `mca:village_has_space` | Space in player's last-seen village; no fields |
| `mca:event_completed` | `event`: completed `story` ID for this pair |
| `mca:event_choice` | `event` and `choice`: latest completed story choice ID |
| `mca:not` | `condition`: nested condition to negate |

Numeric bounds are inclusive. Hearts/health need at least one bound; inventory counts default to at least one. Time ranges use day time modulo 24,000. `mca:memory` with `present: false` checks for an absent memory; `var: "player"` checks player-scoped memory. A missing dependency is unavailable, even when wrapped in `mca:not`.

`mca:infected` requires an active infection. Its optional inclusive `min`/`max`
bounds use infection progress from `0.0` to `1.0` (for example, `0.2` is 20%).
Omit either bound for an open-ended infection stage, or omit both to match any
currently infected villager. A healthy villager never matches, even with
`"max": 0.0`.

`village_has_building` means the village contains that building type.
`in_building` means this villager is actually inside that building context. Use
the latter for lines such as hospital/prison conversations that depend on the
speaker's current location.

### Biome selectors and ordered outcomes

`mca:biome` requires a nonempty **`values` array**, even for a single ID.
Each value is an exact biome identifier or a `#namespace:biome_tag`, and the
condition matches when **any** value matches. The old single-string `value`
shape is not supported. Vanilla or modded biome membership comes from the live
server registry and worldgen biome tags; datapacks can extend an MCA category
under `data/mca/tags/worldgen/biome/`.

`scope: "current"` (the default) checks the villager's position. `scope: "home"`
checks the villager's home village center when safely loaded. A villager
without a home uses its current biome instead; a known home in an inaccessible
chunk produces an unavailable result, not a forced chunk load. Use neutral
wording if the same line can be spoken by a villager with no home.

For dialogue **outcomes**, optional integer `priority` defaults to `0`. Selection has three steps: discard outcomes whose requirements do not match, keep the highest-priority eligible tier, then choose within it using positive `weight`. This is separate from the event's top-level `priority`.

```json
{
  "outcomes": [
    { "requirements": [{ "type": "mca:biome", "values": ["minecraft:plains"] }],
      "priority": 20, "next": "plains" },
    { "requirements": [{ "type": "mca:biome", "values": ["#mca:forest_biomes"] }],
      "priority": 10, "next": "forest" },
    { "priority": 0, "next": "generic" }
  ]
}
```

The unconditional generic outcome remains selectable when the exact ID/tag is
missing or unknown. See the shipped `social/local_area.json` for both current
and home examples. Unknown tags never become true under `mca:not`.

Negate a known condition explicitly:

```json
{
  "type": "mca:not",
  "condition": { "type": "mca:weather", "value": "rain" }
}
```

Missing or unavailable referenced data stays unavailable under `mca:not`; it does
not become an accidental match.

Recent gameplay facts come from the game's recorded events, not dialogue history:

```json
{
  "type": "mca:recent_event",
  "event": "mca:cured",
  "within_ticks": 24000
}
```

Registered IDs are `mca:relative_death`, `mca:attacked`, `mca:zombified`, `mca:cured`, `mca:revived` and `mca:raid_survived`. Use only registered recent-event IDs. The engine does not invent facts such as
"recently mourned", "recently cured", or "recently revived" just because a
conversation wants them.

Omit `within_ticks` to match any recorded occurrence, however long ago it
happened:

```json
{ "type": "mca:recent_event", "event": "mca:cured" }
```

With `within_ticks`, only the latest recorded occurrence is tested against the
specified window. Without it, the check remains true after the window expires.
Combine the permanent condition with `mca:not` around a time-bounded condition
to write stories about past cures but exclude recently cured villagers. The
`mca:cured` event is recorded when an MCA zombie villager is converted back;
curing an infection *before* zombification does not currently record that event.

## Story history and follow-ups

Conversation history is scoped to `(player UUID, villager UUID, event ID)`. Two
players can therefore remember different conversations with the same villager, and
two villagers with the same personality do not share cooldowns or choices.

Use `history: "story"` when later content may care about completion or choices.
Use `history: "scheduling"` for ordinary cooldown-only small talk that has no
durable story meaning. Scheduling history is valid only with `repeat.type:
"cooldown"` and cannot be the target of `event_completed` or `event_choice`.

A follow-up can require completion:

```json
{
  "type": "mca:event_completed",
  "event": "example_addon:personal/cured_zombies"
}
```

and a stable reply from the latest completed run:

```json
{
  "type": "mca:event_choice",
  "event": "example_addon:personal/cured_zombies",
  "choice": "experience_changes_you"
}
```

Repeatable stories remember only the latest successfully completed run's choice
set. If the player later completes the original story with a different reply, the
older reply no longer satisfies `event_choice`. An abandoned attempt changes
nothing. `event_completed` remains true and the completion count continues to
advance.

Follow-ups are separate events with their own namespaced ID, requirements, graph,
presentation, and repeat policy. Becoming eligible never auto-starts a follow-up;
the player still chooses it from Talk.

## Repeat policies and the pause deadline

Every event must author one repeat policy.

Immediate repetition:

```json
{ "type": "always" }
```

One successful completion per player/villager pair:

```json
{ "type": "once" }
```

Fixed cooldown:

```json
{ "type": "cooldown", "seconds": 5 }
```

Random cooldown:

```json
{
  "type": "cooldown",
  "min_seconds": 30,
  "max_seconds": 90
}
```

Use either `seconds` or both `min_seconds` and `max_seconds`, never both forms.
Seconds are the datapack authoring unit; the engine normalizes them to ticks with
`ceil(seconds * 20)` and stores the rolled next-eligible game time on successful
completion. The random interval is rolled once at completion, not every time Talk
opens.

For the current reference stories and small talk, author `seconds: 5`. That is a
story scheduling rule: five seconds is 100 ticks at 20 TPS and applies only to this
player/villager/event tuple.

Do not confuse that cooldown with the fixed **2400-tick pause window**. The pause
window is an engine/session lifecycle rule (roughly two minutes at 20 TPS), is not
authored in event JSON, and starts when an unfinished conversation is suspended.
It does not consume the event's repeat cooldown.

## Personality writing guide

Personality is a condition and a writing influence, not an owner of a separate
conversation list. Prefer a broadly useful event with a personality requirement or
personality-specific route when the subject genuinely depends on personality.

For each personality, writers may define consistent creative tendencies in their
content notes, such as:

- preferred or disliked weather;
- preferred time of day;
- favorite or disliked mobs;
- conversational tone, pacing, humor, optimism, bluntness, anxiety, curiosity, or
  other recurring voice traits.

These are writing guides, not a new gameplay preference registry. Do not invent a
hardcoded favorite-weather/favorite-mob system merely to make dialogue prose
consistent. When a preference needs to control eligibility, express the actual
supported condition directly in that event.

Consistency should not become sameness. A gloomy villager can still enjoy a sunny
day; a grumpy villager can be kind in a serious situation. Treat exceptions as
deliberate characterization rather than a reason to make every line contradict the
established voice.

Good authoring questions are:

1. What is this villager reacting to right now?
2. Why does this personality phrase the subject this way?
3. Does relationship/mood/context justify exposing the topic?
4. Does the player's reply create a fact worth remembering later?
5. If the story repeats, should a separate follow-up acknowledge the latest reply?

## Writer templates

### Ambient small talk

Use scheduling-only history when the conversation exists for variety and no later
story needs to know that it happened.

```json
{
  "trigger": "talk",
  "presentation": {
    "mode": "ambient",
    "resume_prompt": "dialogue_event.example_addon.rain_small_talk.resume",
    "topic": "weather"
  },
  "priority": 10,
  "weight": 2,
  "requirements": [
    { "type": "mca:weather", "value": "rain" }
  ],
  "repeat": { "type": "cooldown", "seconds": 5 },
  "history": "scheduling",
  "start": "line",
  "nodes": {
    "line": {
      "line": "dialogue_event.example_addon.rain_small_talk.line",
      "complete": true
    }
  }
}
```

### Contextual story with a remembered reply

```json
{
  "trigger": "talk",
  "presentation": {
    "mode": "highlighted",
    "prompt": "dialogue_event.example_addon.reflection.prompt",
    "resume_prompt": "dialogue_event.example_addon.reflection.resume",
    "topic": "personal"
  },
  "priority": 100,
  "requirements": [
    { "type": "mca:personality", "value": "gloomy" },
    { "type": "mca:mood", "value": "sad" },
    { "type": "mca:hearts", "min": 40 },
    { "type": "mca:time", "value": "night" }
  ],
  "repeat": { "type": "cooldown", "seconds": 5 },
  "history": "story",
  "start": "intro",
  "nodes": {
    "intro": {
      "line": "dialogue_event.example_addon.reflection.intro",
      "choices": [
        {
          "id": "ask_why",
          "text": "dialogue_event.example_addon.reflection.choice.ask_why",
          "next": "answer"
        },
        {
          "id": "give_space",
          "text": "dialogue_event.example_addon.reflection.choice.give_space",
          "next": "goodbye"
        }
      ]
    },
    "answer": {
      "line": "dialogue_event.example_addon.reflection.answer",
      "complete": true
    },
    "goodbye": {
      "line": "dialogue_event.example_addon.reflection.goodbye",
      "complete": true
    }
  }
}
```

### Choice-dependent follow-up

```json
{
  "trigger": "talk",
  "presentation": {
    "mode": "ask",
    "prompt": "dialogue_event.example_addon.reflection_followup.prompt",
    "resume_prompt": "dialogue_event.example_addon.reflection_followup.resume",
    "topic": "personal"
  },
  "priority": 80,
  "requirements": [
    {
      "type": "mca:event_completed",
      "event": "example_addon:personal/reflection"
    },
    {
      "type": "mca:event_choice",
      "event": "example_addon:personal/reflection",
      "choice": "ask_why"
    }
  ],
  "repeat": { "type": "cooldown", "seconds": 5 },
  "history": "story",
  "start": "callback",
  "nodes": {
    "callback": {
      "line": "dialogue_event.example_addon.reflection_followup.callback",
      "complete": true
    }
  }
}
```

## Localization rules

Use unique semantic keys for ordered lines, prompts, replies, responses, and
continuations. Repeating the same JSON source key is invalid authoring practice even
if a permissive JSON parser would silently keep only the last value.

```json
{
  "dialogue_event.example_addon.cure.prompt": "Something on your mind?",
  "dialogue_event.example_addon.cure.resume": "%1$s, about what you said about cured villagers...",
  "dialogue_event.example_addon.cure.intro.heard_about_cures": "I heard some zombies can be cured and turn back into people.",
  "dialogue_event.example_addon.cure.intro.same_person": "I keep wondering whether they're still exactly the same person afterward.",
  "dialogue_event.example_addon.cure.choice.experience_changes_you": "Maybe the experience changes them, even if they're still themselves."
}
```

Missing translations do not grant authority or change event selection, but shipped
content should still validate that all authored keys resolve.

## What the player sees

The client receives complete localized lines from the server and reveals text at
one Unicode-safe grapheme every **40 ms**. This is presentation only: there is no
network packet per character, no instant-reveal control, and clicking during reveal
does not skip, accelerate, advance, or queue an advance.

For a multi-line node, `Next` appears after the current passage is fully revealed and advances to the next passage. Replies appear after the final passage. Ordinary terminals show **Finish conversation** and return the player to the topic list. The server commits history, cooldown and pending effects only for a `complete: true` terminal; `end: true` closes without completion. The slap-ending exception completes and closes the interaction on the provoking reply, with the final localized line delivered in chat after the delayed hit.

## Author checklist

Before submitting an event, check that:

- the resource ID and any referenced event IDs are fully namespaced and stable;
- every event has `trigger`, `presentation`, `repeat`, `start`, and valid nodes;
- highlighted/ask events have a natural `prompt`, and every event has a specific
  `resume_prompt`;
- requirements are hard eligibility rules, while probability lives in weights;
- every choice ID is unique across the event and is stable if later content may
  reference it;
- ordered passages use distinct semantic localization keys rather than pooled
  `/1`, `/2` alternatives;
- each node has exactly one continuation shape and the graph is acyclic;
- repeatable stories use `history: "story"`; disposable small talk can use
  cooldown + `history: "scheduling"`;
- the personality library uses ranged cooldowns (`min_seconds`/`max_seconds`):
  300–900 seconds for main stories and 60–180 seconds for everyday entries;
  legacy social examples and test showcases may still use `seconds: 5`;
- the 2400-tick pause deadline is not copied into event cooldown data;
- gameplay facts such as cure, revival, mourning, attack, hospital/prison context,
  or raid survival come from an authoritative condition owner;
- actions are necessary, server-owned, and cannot be applied twice through an
  equivalent legacy effect;
- completion is intended to commit after the final `Finish conversation`
  acknowledgement, unless the reply leads directly to a completed slap reaction.

## Personality story examples

The shipped `personal/personality_introverted.json` demonstrates a story offered
to a particular personality after 20 hearts, with a night-only variation and an
unconditional fallback. Its choices lead to distinct, acknowledged endings.
`personal/personality_flirty.json` puts adult romantic wording only behind a
conditional `mca:age_group` route, retaining an ordinary fallback.

Two ordinary `ambient/personality_friendly_*.json` conversations share priority
`0` with the existing greetings and root chatter, rather than hiding those
conversations behind a higher-priority tier. `personal/personality_anxious_followup.json`
demonstrates a separate selectable conversation which requires both a completed
story and the recorded `make_plan` player choice. A story abandoned before its
final acknowledgement cannot unlock this follow-up.
