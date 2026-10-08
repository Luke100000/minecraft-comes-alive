# DialogueEvent authoring guide

MCA conversations are authored as namespaced datapack `DialogueEvent` resources.
An event owns both its eligibility rules and its conversation graph, so an addon can
add a complete conversation without Java code.

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
  -> highlighted contextual story
  -> Ask about... topics
  -> What's on your mind? ambient pool
  -> Back
```

Requirements answer only "may this event/path happen?". `priority` decides which
eligible event wins a presentation tier. `weight` is randomness only among
equivalent eligible candidates or outcomes. Do not encode probability as a hard
condition.

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

## Presentation modes

Use the smallest mode that matches the importance of the conversation:

- `highlighted` is for the most important currently relevant story. The highest
  priority highlighted event gets the highlighted slot; additional highlighted
  events remain available under Ask rather than disappearing.
- `ask` is a directly selectable contextual topic under `Ask about...`.
- `ambient` participates in the `What's on your mind?` pool. Only the highest
  eligible ambient priority tier is considered, then positive `weight` chooses
  within that tier.

`prompt` is required for `highlighted` and `ask`. Ambient events do not need a
new-topic prompt. Every event must provide `resume_prompt`, including ambient
events, because a paused run needs a specific way back into that exact
conversation.

The optional `topic` is organizational metadata for authors/debugging. It does not
create a submenu by itself.

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
weights only the remaining outcomes. A choice is not offered when none of its
outcomes are eligible.

## Completion, ending, and rewards

`complete: true` means the conversation successfully completes. Its accepted
choices, completion history, cooldown, and pending actions are committed only when
the player acknowledges the terminal line with `Back to topics`.

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

Closing the screen is not completion. It pauses the active run. If the pause
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
```

Actions are server-owned and are committed with the successful conversation
completion, not when the client merely displays a line. Use `mca:command` only to
delegate to an existing MCA gameplay command owner; do not reimplement gameplay
logic in dialogue data.

`mca:hearts` already follows MCA's heart-reward behavior. Do not automatically add
an equal `mca:mood` action beside it just to mimic legacy dialogue data, because
that can double-apply mood changes. Add a separate mood action only when the story
actually intends an additional mood delta.

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
{ "type": "mca:time", "value": "night" }
{ "type": "mca:time", "min": 13000, "max": 23000 }
{ "type": "mca:weather", "value": "rain" }
{ "type": "mca:biome", "value": "minecraft:plains" }
{ "type": "mca:advancement", "value": "minecraft:story/mine_diamond" }
{ "type": "mca:village_has_building", "value": "infirmary" }
{ "type": "mca:in_building", "value": "infirmary" }
```

`village_has_building` means the village contains that building type.
`in_building` means this villager is actually inside that building context. Use
the latter for lines such as hospital/prison conversations that depend on the
speaker's current location.

Negate a known condition explicitly:

```json
{
  "type": "mca:not",
  "condition": { "type": "mca:weather", "value": "rain" }
}
```

Missing or unavailable referenced data stays unavailable under `mca:not`; it does
not become an accidental match.

Recent gameplay facts use their authoritative gameplay producer, not conversation
history:

```json
{
  "type": "mca:recent_event",
  "event": "mca:cured",
  "within_ticks": 24000
}
```

Use only registered recent-event IDs. The engine does not invent facts such as
"recently mourned", "recently cured", or "recently revived" just because a
conversation wants them.

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

For a multi-line node, `Next` appears only after the current passage is fully
revealed and advances to the next authored passage. Player replies appear only
after the final passage is fully revealed. A terminal completing/ending line uses
`Back to topics`, not a generic `Continue`; that final acknowledgement is the
server-authoritative commit boundary for completion/history/cooldown/pending
effects.

## Stardew Valley inspiration, not Stardew behavior

Useful references:

- [Stardew Valley dialogue modding](https://stardewvalleywiki.com/Modding:Dialogue)
- [Stardew Valley event data](https://stardewvalleywiki.com/Modding:Event_data)

MCA borrows stable event IDs, explicit prerequisites, remembered choices, event
history, and inspectable selection. It does **not** copy Stardew's location-owned
event model, event command language, or automatic answered-question suppression.

Stardew's documented `$q`/`$r`/`$p` dialogue mechanisms can suppress or replace an
answered question and later check a remembered reply. MCA instead models this with
repeatable pair-scoped `DialogueEvent` stories plus separately authored follow-up
events using `event_completed` and `event_choice`.

That difference is intentional: an MCA story can become eligible again after its
authored cooldown while its latest completed reply remains available to later
stories. If you do not want repetition, opt into `repeat: { "type": "once" }`
explicitly rather than assuming Stardew-style answered-question suppression.

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
- current reference stories/small talk use `seconds: 5` unless intentionally
  testing another authored interval;
- the 2400-tick pause deadline is not copied into event cooldown data;
- gameplay facts such as cure, revival, mourning, attack, hospital/prison context,
  or raid survival come from an authoritative condition owner;
- actions are necessary, server-owned, and cannot be applied twice through an
  equivalent legacy effect;
- completion is intended to commit only after the final `Back to topics`
  acknowledgement.
