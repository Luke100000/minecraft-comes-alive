# Dialogue event conversation system

Status: draft for user review; implementation not started.
Primary implementation branch: `dev/1.21.1`, with the same conceptual design
ported afterward to 26.1.2 and 26.2.

## Intended outcome

MCA should support authored conversations that feel connected to a villager's
life instead of every interaction being an isolated random line. Talking to a
villager should discover contextually relevant `DialogueEvent`s using hard
requirements such as personality, hearts, weather, time, health, mood, traits,
family events, recent attacks, zombification/cure, revival, profession, village
state, and current building context.

The design borrows the useful part of Stardew Valley's event model: stable event
IDs, explicit prerequisites, prior-event and prior-choice dependencies, repeat
rules, and inspectable eligibility. It does not copy Stardew's location-owned or
automatically forced presentation. MCA conversations remain face-to-face and
player initiated.

Existing interactions such as Hug, Joke, Gift, and Kiss remain available to the
player, but the conversation implementation is rewritten around one coherent
server-authoritative `DialogueEvent` engine. The current
`Question -> Answer -> Result -> Actions` stack is migration input, not the
permanent execution model.

The primary TALK flow is:

```text
TALK
  -> offer a personalized continuation if this pair has a valid paused run
  -> also collect other DialogueEvents for this player/villager
  -> evaluate hard requirements
  -> evaluate completion, previous-choice, and repeat/cooldown state
  -> classify eligible events by presentation mode
  -> show one highlighted contextual prompt plus other selectable topics
  -> player chooses continuation, another topic, ambient conversation, or leaves
  -> continuation resumes the exact saved run; a new topic starts at its start node
  -> advance ordered lines and execute validated node/choice transitions
  -> commit pending story effects, completed event, and choices on completion
  -> return to refreshed Talk options; never automatically chain the next event
  -> if no contextual event qualifies, use ordinary always-eligible/ambient
     DialogueEvents; a legacy root adapter exists only while old content migrates
```

Success means a datapack author can create the whole conversation in one
namespaced event resource without Java code: eligibility, presentation, dialogue
nodes, choices, actions, completion, repeat policy, and later dependencies on what
the player previously saw or chose. The same condition model must cover ordinary
ambience and authored life events such as mourning, cure, revival, health, and
building context once those facts have an authoritative gameplay owner.

## Non-goals

The overhaul does not:

- redesign gifts or broadly refactor `GiftPredicate`;
- automatically force story conversations when the player interacts;
- implement cutscenes, camera scripting, NPC movement scripting, or a general
  quest engine;
- make every possible MCA state into a dialogue condition without a concrete
  authored use;
- persist an in-progress conversation across disconnects or server restarts.

Migrating legacy dialogue content is part of delivery, but compatibility can be
kept temporarily so the rewrite does not require one giant content conversion
commit.

## Player experience

Talking to a villager remains voluntary. Eligible events modify the available
conversation choices rather than hijacking the interaction.

Right-click opens normal interaction controls; Talk opens the conversation menu.
Neither action automatically starts or resumes a story. The player can select a
personalized continuation, the highlighted topic, another eligible Ask topic,
ambient conversation, an ordinary interaction, or leave. Priority decides which
new topic is highlighted, not which conversation the player must play.

The overhaul may make a **minimal extension to the existing Talk UI**
to expose highlighted, `Ask about...`, and ambient event choices. It must not
redesign the overall interaction screen or replace unrelated interaction controls.

There are three presentation modes.

### Highlighted

Story-important events can surface one natural prompt directly in the interaction
screen, for example:

```text
[!] You've been quiet lately...
```

The player may ignore it and use another interaction. If several highlighted
events are eligible, the highest-priority one receives the highlighted slot. The
remaining eligible events remain reachable under `Ask about...`; they are not
discarded merely because another event ranked higher.

### Ask

Optional contextual events appear under `Ask about...` using natural language,
not implementation labels. Examples are:

```text
How have you been feeling lately?
You seemed shaken after the raid...
Do you like working here?
```

The UI exposes contextual prompts directly rather than forcing broad
Family/Village/Work submenus. Event metadata may include a topic for later
grouping and debugging, but topic grouping is not required for the initial UI.

### Ambient

Repeatable flavour conversations such as weather, mood, time-of-day, profession,
or personality small talk form the pool behind the generic `What's on your
mind?` choice. The server selects the highest-priority eligible ambient tier and
uses weight only to vary events within that equal-priority tier.

If no contextual DialogueEvent is eligible, TALK still offers ordinary
conversation through always-eligible or ambient DialogueEvents. During migration,
unconverted legacy content may additionally be exposed through a compatibility
adapter; the final architecture does not depend on legacy `root`.

### Personalized continuation and returning to the menu

When this player/villager pair has a valid paused run, its continuation appears
in a dedicated slot alongside new topics; it does not compete for the highlighted
slot. Its label is authored for that event and localized, not the generic
"Continue our conversation". Examples:

```text
Alice, about what you said about cured villagers...
You were telling me about your brother...
About the storm keeping you awake...
```

Use required `presentation.resume_prompt` translation keys for every event mode,
including ambient events. The server builds the prompt Component and supplies
the speaking villager's current display name as optional translation argument
`%1$s`; writers need not repeat the name when a topic-specific phrase is more
natural. Keep speaker context visible in the existing screen. Do not generate
labels from event IDs, expose an unchosen branch, or add an extra NPC line when
resuming. An event-level label is sufficient; node-specific labels are not needed.

Browsing Talk/Ask options or choosing Back/Leave preserves the paused run and its
original deadline. Selecting its continuation resumes the saved node/line with
a fresh token. Selecting another valid event, including another topic with the
same villager, replaces the paused run and discards its unfinished effects and
choices. Invalid/stale selections do not abandon it. Do not offer the paused
event as a second new-start option, including through the ambient candidate pool.

After explicit completion or a non-completing end, return to refreshed Talk
options if interaction remains valid; otherwise close normally. A completed
story is absent until its authored cooldown expires, and newly eligible follow-ups
may appear. No follow-up or other event starts until the player selects it. Other eligible topics remain
accessible while a continuation is offered.

### Reading and replying

Nodes may contain several consecutive villager lines. Show one line at a time;
Continue advances to the next line, and replies appear after the final line is
fully revealed. Replies use natural language and do not display positive,
neutral, negative, heart deltas, or outcome probabilities.

Provide a client-side typewriter presentation. The agreed timing is
one complete displayed word every 250 ms, not one character; it is a
presentation default, not an event condition or server delay. Revealing must
use locale-aware word boundaries, preserve punctuation/spacing and formatted
Components, and never split a Unicode character. The reveal cannot be skipped
or accelerated by clicking, and there is no instant-display setting. Continue
becomes available only after the current line finishes revealing; clicks during
reveal neither advance the line nor queue an advance for later. Replies become
available only after the final line of the passage finishes revealing.
The server sends the complete current line; there is no packet per revealed word.
Client reveal timing is not proof that a player read a line and grants no rewards.

### Pausing and resuming

Closing the screen or walking out of interaction range pauses the conversation.
The server retains its node, line index, accepted choices, already-selected
outcomes, and pending story effects in memory. Right-clicking that same villager
opens the normal interaction screen. Pressing its existing Talk button within
the pause window offers the event's personalized continuation alongside other
eligible topics. Only selecting that continuation resumes the saved graph.
Opening either screen alone does not resume, reset the deadline, or launch a
conversation.

The pause window is 2400 overworld game ticks (approximately two minutes at
20 TPS), counted from suspension rather than continuously refreshed by rejected
packets. Server lag extends the real elapsed time. Resuming requires a living,
loaded, interactable villager in the same dimension and normal interaction range.
Resume uses a fresh offer token; packets from before suspension stay invalid.

At expiry, discard unfinished choices and pending effects without marking the
event complete or consuming its normal repeat policy. The villager may deliver
one localized line such as "I guess you don't want to talk..." if the player and
villager are currently loaded and within normal speech range. Do not load chunks,
send a remote reprimand, or apply an automatic hearts penalty. Do not reroll a
previously selected outcome while resuming. The same resolved current line should
be restored rather than choosing another pooled translation on every reopen.

Disconnect, server stop, villager death/removal/conversion, and datapack reload
invalidate unfinished sessions; these are not resumable save records. Temporary
unload suspends an existing session without retaining a strong entity reference;
its existing pause deadline still runs. Starting a different conversation with
the same or another villager ends the previous paused run, invalidates its offers, and
discards its unfinished choices and effects without completion or cooldown.
Retain at most one active or paused run per player. Merely opening another
villager's interaction screen or browsing Talk options does not abandon the
paused run. A continuation is offered only for the paused run's own villager.

### Personality writing consistency

Maintain an author-facing guide for each personality: usual tone, weather and
time-of-day preferences, favourite mobs, recurring interests, and exceptions.
These are writing conventions first, not a new gameplay preference registry.
Writers should use the guide consistently across ordinary and contextual stories.
Introduce a mechanically queryable preference only when concrete content or
gameplay needs an authoritative lookup.

Personality remains one requirement, not the owner of a conversation list.
Preferences influence writing and optional requirements; they do not make every
villager of that personality share completion or cooldown state.

## Resource ownership and IDs

Dialogue events are datapack resources:

```text
data/<namespace>/dialogue_events/<path>.json
```

The canonical event ID comes from the resource location. For example:

```text
data/mca/dialogue_events/personal/gloomy_reflection.json
-> mca:personal/gloomy_reflection

data/example_addon/dialogue_events/zombie/identity.json
-> example_addon:zombie/identity
```

The JSON must not contain a second authoritative `id` field. Full namespaced IDs
are retained internally; namespace and directory path must never be stripped to
the final filename as the legacy dialogue loader currently does.

Normal datapack replacement semantics apply to the event resource as a whole.
An addon can add its own events or intentionally override the same namespaced
event ID through pack priority. Event resources replace as whole resources rather
than merging fragments from multiple files.

## DialogueEvent model

An event is immutable reload data with this conceptual model:

```text
DialogueEvent
  id                  namespaced resource ID
  trigger             TALK initially; trigger model remains extensible
  presentation
    mode              HIGHLIGHTED | ASK | AMBIENT
    prompt            translation key for selectable modes
    resume_prompt     required event-specific continuation translation key
    topic             optional organizational string
  priority            integer, default 0
  weight              positive number, default 1
  requirements[]      deterministic hard requirements
  repeat              required: ALWAYS | ONCE | COOLDOWN (stories use cooldown)
  history             STORY (default) | SCHEDULING
  start               node ID within this event
  nodes{}             namespaced event-owned conversation graph
```

An example resource:

```json
{
  "trigger": "talk",
  "presentation": {
    "mode": "highlighted",
    "prompt": "dialogue_event.mca.personal.gloomy_reflection.prompt",
    "resume_prompt": "dialogue_event.mca.personal.gloomy_reflection.resume",
    "topic": "personal"
  },
  "priority": 100,
  "weight": 1,
  "requirements": [
    { "type": "mca:personality", "value": "gloomy" },
    { "type": "mca:hearts", "min": 40 },
    { "type": "mca:mood", "value": "sad" },
    { "type": "mca:time", "min": 13000, "max": 23000 }
  ],
  "repeat": { "type": "cooldown", "seconds": 5 },
  "start": "intro",
  "nodes": {
    "intro": {
      "line": "dialogue_event.mca.personal.gloomy_reflection.intro",
      "choices": [
        {
          "id": "comfort",
          "text": "dialogue_event.mca.personal.gloomy_reflection.comfort",
          "actions": [
            { "type": "mca:hearts", "amount": 5 }
          ],
          "next": "comforted"
        },
        {
          "id": "leave",
          "text": "dialogue_event.mca.personal.gloomy_reflection.leave",
          "next": "goodbye"
        }
      ]
    },
    "comforted": {
      "line": "dialogue_event.mca.personal.gloomy_reflection.comforted",
      "complete": true
    },
    "goodbye": {
      "line": "dialogue_event.mca.personal.gloomy_reflection.goodbye",
      "end": true,
      "retryable": true
    }
  }
}
```

Nodes are inline because the event and its conversation form one authored story,
share one namespaced identity, and are normally changed together. This also
eliminates the legacy basename collision problem for new content. A reusable
separate conversation resource is intentionally not introduced until real content
needs to share one graph across multiple events.

`start` is therefore a local node ID, not a legacy question ID. Node IDs are graph
structure and may change as an authored conversation is reorganized. Choice IDs
are the durable story API: every choice ID must be unique across the entire event,
and durable history is stored as `event + choice`. This lets authors move a choice
between nodes without silently breaking later `event_choice` prerequisites.

## Requirements and condition semantics

Event requirements are deterministic boolean gates. They are not chance
modifiers. All entries in `requirements` must pass for an event to be eligible.

Requirements use typed fields, not legacy comma-separated shorthand. For example,
adult, gloomy, nighttime, and at least 20 hearts are positive requirements:

```json
[
  { "type": "mca:age_group", "value": "adult" },
  { "type": "mca:personality", "value": "gloomy" },
  { "type": "mca:time", "min": 13000, "max": 23000 },
  { "type": "mca:hearts", "min": 20 }
]
```

The night range above is an authored window, not a claim that all vanilla night
or sleeping checks use those exact boundaries. Genuine exclusions use the explicit
`mca:not` wrapper, for example
`{ "type": "mca:not", "condition": { "type": "mca:age_group", "value": "adult" } }`.
Unknown or unavailable references remain unavailable under negation and cannot
become eligible just because `not` inverted a failed lookup.

This deliberately differs from the existing `InteractionPredicate`/`Result`
system, where conditions can contribute numeric weight. Weight is used only
after eligibility has been established.

Unknown condition types, malformed parameters, and unknown MCA-owned IDs are
reload errors. They must never be silently dropped or interpreted as an empty
condition set. A bad event resource is skipped with a precise log message rather
than weakened into a more permissive event.

The built-in condition vocabulary covers the authored stories MCA actually wants,
but every condition must read a fact with an explicit authoritative owner. The
dialogue engine must not fabricate or infer gameplay history just to satisfy a
condition.

### Relationship and identity

- hearts minimum/maximum;
- spouse, engaged, parent/child, relative, orphan and related family state;
- age group and gender where authored content needs them;
- personality;
- mood;
- traits, including ordinary data-driven traits such as lactose intolerance;
- profession and rank;
- pregnancy/family state where relevant.

### Built-in conditions

- personality;
- mood;
- hearts minimum/maximum;
- relationship/family state already queryable by MCA;
- age group;
- profession and rank;
- traits;
- current health/health range;
- time/day/night;
- clear/rain/thunder weather;
- biome;
- player advancement;
- village/building state only where the current MCA APIs can already answer the
  question reliably;
- `event_completed`;
- `event_choice`;
- explicit `not` wrapping a supported condition;
- mourning/recent relative death;
- recently hurt/attacked;
- raid aftermath;
- recently zombified/cured;
- recently revived.

Current state such as health, mood, relationship, time, weather, and biome is read
directly from its existing owner. Historical facts such as mourning, recent attack,
cure, and revival require an authoritative producer. Where `LongTermMemory` has
the right expiry semantics it may back a boolean recent-fact flag, but conversion
must preserve it. If a fact needs structured payload beyond what memory can hold,
give that gameplay fact its own narrow owner instead of expanding conversation
history into a general world-history database. Recent facts use server game time
and explicit expiry windows, never wall-clock time.

### Environment and ambience

- time range plus convenient day/night forms;
- clear, rain, and thunder weather;
- biome;
- indoors/outdoors when reliable physical context exists;
- current village membership/state;
- village has a building/type;
- current building context.

Building context is required because a villager in a prison or hospital should be
able to unlock conversations that would make no sense elsewhere. The condition
vocabulary distinguishes at least:

```text
in_building
village_has_building
assigned_to_building
```

Building matching should use canonical building identity/type supplied by MCA's
building subsystem. If hospital/prison semantics are not currently represented by
a stable type or tag, the overhaul must add the smallest authoritative building
classification extension needed by authored content. A supported condition must
still distinguish
"the village has a hospital" from "this villager is inside a hospital".

### Player and history

- player advancement;
- player inventory/item/tag where needed;
- existing villager memory where appropriate;
- previous DialogueEvent completed;
- previous choice made inside a DialogueEvent.

The first format uses implicit AND between requirements. A future `any_of` or
general expression grammar is not added until authored content demonstrates a
need for it; separate events can cover alternatives initially.

## Gameplay facts versus conversation history

Two different kinds of state must not be conflated.

### Villager/world facts

Facts such as:

```text
relative died recently
villager was cured recently
villager was revived recently
villager was attacked recently
village survived a raid
current mood
current health
current building
```

describe the world or the villager and are shared regardless of which player is
talking. Conditions read these facts from their authoritative MCA owners.

Short-lived facts may use `LongTermMemory` where its semantics are sufficient.
Structured facts that cannot be represented safely as a string/expiry pair should
get an explicit domain owner rather than overloading conversation history.

### Villager-player conversation history

What a particular player has discussed with a particular villager is pair-scoped.
A dedicated `DialogueEventHistory` SavedData stores it independently of entity
NBT and independently of `LongTermMemory`.

Conceptually the key is:

```text
player UUID -> villager UUID -> event ID -> EventRecord
```

`EventRecord` stores only durable information required by the event system:

```text
completion count
last completion game time
next eligible game time for cooldown events
completed choice IDs (event-wide unique stable IDs)
```

In-progress state is not persisted to disk. Screen closure suspends it in memory
for the pause window; timeout, disconnect, or server stop discard its temporary
choices and pending effects. Resuming within the window continues the same run.

`history: "story"` retains durable completion/choice state, including for repeatable
stories. `history: "scheduling"` is allowed only with `repeat.type: "cooldown"` and
retains only next-eligible timing for ordinary small talk. Such an event cannot be
the target of `event_completed` or `event_choice`; reject a resolved reference to
it rather than silently invent durable history. This makes cooldown pruning real
without deleting facts that later stories depend on.

This produces the desired continuity without making shared world facts
player-specific. Two players can therefore have different conversation histories
with the same villager while both observe that the villager was recently cured.

## Seen/completed and previous-choice semantics

### Remembered questions and authored follow-ups

Stardew's documented `$q` mechanism can replace an already-answered question
with fallback dialogue; `$r` identifies replies and their reactions, and `$p`
checks a remembered reply. Its simpler `$y` exchanges can repeat without durable
answer IDs. These are distinct mechanisms, not a universal latest-answer-overwrite
rule. [Stardew dialogue documentation](https://stardewvalleywiki.com/Modding:Dialogue)
Event prerequisites can also check previously selected answer IDs through
`ChoseDialogueAnswers`.
[Stardew event documentation](https://stardewvalleywiki.com/Modding:Event_data)

MCA adopts remembered replies and authored follow-ups, not Stardew's command
syntax, location ownership or automatic suppression of answered questions.
Stories are repeatable after their datapack-authored cooldown for each
player/villager/event tuple. Completion remains remembered even after the story
becomes eligible again. A separate follow-up has its own namespaced ID,
requirements, graph and repeat rule, including `event_completed`/`event_choice`
prerequisites for the first story. No separate question-history subsystem or new
global answer-ID namespace is needed.

For example, after completing `mca:personal/cured_zombies` with
`experience_changes_you`, an eligible follow-up can say "I've been thinking about
what you said about experience changing people...". Other replies can unlock
different follow-ups. Replaying the original story after its cooldown is allowed;
authors may also write distinct follow-ups that acknowledge the latest completed
answer rather than repeat the original wording.
Follow-up requirements still decide availability and the player chooses whether
to start it. This does not force immediate follow-ups or automatic chaining.

Ordinary repeatable small talk normally uses scheduling-only history and a
cooldown. Stories use durable latest-run choice replacement. Every event must
author its `repeat` policy: there is no implicit once-only story policy or global
cooldown value. `once` remains an explicit author opt-in for exceptional content,
not the policy for the reference stories.

The event system uses `completed`, not an ambiguous `seen` boolean, as the durable
prerequisite. An event becomes completed only when the server accepts Continue
after the last line of a `complete: true` node in the matching valid session.
Merely sending a terminal line or closing its screen does not complete the event.

While an event is active, the server records every accepted choice ID in temporary
session state. On successful completion those choice IDs are committed to
`DialogueEventHistory` with the event record. The active node is session state,
not durable save-format identity.

A follow-up event can therefore require:

```json
{
  "type": "mca:event_completed",
  "event": "mca:personal/gloomy_reflection"
}
```

and:

```json
{
  "type": "mca:event_choice",
  "event": "mca:personal/gloomy_reflection",
  "choice": "comfort"
}
```

Event-wide choice-ID uniqueness prevents collisions between repeated generic names
such as `yes`, `no`, or `leave`. Authors should therefore use semantic stable IDs
such as `comfort`, `ask_why`, or `defend_village` when that choice may be referenced
by later stories.

For stories, each successful completion replaces the
previous choice set with the choices from that latest completed run. Do not
accumulate choices across repeated runs or preserve the first run permanently. Choices not taken in
the latest completed run no longer satisfy `event_choice`, while `event_completed`
remains true and the completion count advances. An unfinished or abandoned run
does not overwrite the previous completed choices.
Follow-ups evaluate this one saved completed choice set; they do not alternate
between conflicting answers retained from different runs.

## Repeat and cooldown policy

Initial repeat policies are intentionally small:

```text
always
once
cooldown
```

`repeat` is required. Story conversations, including Phyrra's sample and
follow-ups, use `cooldown` so they can repeat. `once` is available only when an
author explicitly wants an exceptional event to stop after successful completion
for that villager-player pair. `always` adds no history gate and can repeat
immediately. Completion prerequisites do not themselves prohibit repeating.

`cooldown` is authored in seconds inside the conversation's JSON. A fixed
interval uses:

```json
{
  "type": "cooldown",
  "seconds": 5
}
```

For an authored random interval use `min_seconds` and `max_seconds` instead of
`seconds`, for example:

```json
{
  "type": "cooldown",
  "min_seconds": 30,
  "max_seconds": 90
}
```

Exactly one form is permitted: `seconds`, or both `min_seconds` and `max_seconds`.
Values must be finite, nonnegative numbers; range minimum must not exceed maximum.
Fractional seconds are permitted. Normalize durations to game ticks with
`ceil(seconds * 20)`, checking representability before conversion. For ranges,
roll once within the inclusive normalized tick bounds on successful completion
and persist the next eligible overworld game time. Do not reroll on menu opening,
resume, reload or restart. Reject obsolete `min_ticks`/`max_ticks` authoring fields
rather than retaining a second unit format.

For now, reference stories and small talk each author `seconds: 5` for development
and feedback: five game-clock seconds equals 100 ticks at 20 TPS. The interval
starts only after successful completion; it is not the 2400-tick pause/resume
deadline. Server lag extends real elapsed time and offline time does not advance
the cooldown. Keep game ticks and absolute timestamps internal to execution/save
data; seconds are the datapack authoring unit. This temporary sample value is not
a global override of other authored intervals or explicit `once` policies.

Conversation cooldown is scoped to `(player UUID, villager UUID, event ID)`, not
a lockout on all conversations with that villager. Other eligible topics and
follow-ups remain selectable while this event is cooling down. Eligibility for
a new run also rechecks its current hard requirements. A world fact may expire
globally, but whether a player has recently had a particular conversation is a
relationship-history concern.

For example, finishing an event with Alice blocks that event only for this
player/Alice pair until its cooldown expires. Bob remains eligible even if he has
the same personality, and another player has independent history with Alice.
`event_completed` is a historical fact; it does not itself ban a cooldown event
forever. Authors should give ordinary repeatable conversations a nonzero cooldown
to avoid consecutive repeats. `always` is an explicit authoring choice that may
repeat immediately, not an automatic anti-repetition policy.

## Selection rules

On TALK, the server offers any valid same-pair paused continuation separately,
then evaluates new events in stable canonical ID order to produce the eligible
set. Exclude the paused event from new-start/ambient candidates. New-event
selection follows presentation mode; continuation is never a priority/weight draw.

### Highlighted selection

Eligible `HIGHLIGHTED` events are sorted by descending priority and then canonical
event ID. The first receives the highlighted slot. Any additional highlighted
events are still returned under `Ask about...` so important content is delayed,
not hidden.

### Ask selection

All eligible `ASK` events plus overflow highlighted events are returned as
selectable natural prompts, sorted by descending priority and then canonical ID.
The engine itself does not randomly hide eligible authored story topics.

### Ambient selection

When the player selects `What's on your mind?`, only eligible `AMBIENT` events are
considered. The highest priority present becomes the candidate tier. One event is
then selected by positive `weight` within that tier. Stable canonical ordering is
used before weighted selection so debugging and tests are reproducible apart from
the random draw itself.

If no contextual ambient event qualifies, the generic choice selects from
ordinary always-eligible conversation events. During migration only, an
unconverted legacy root may be surfaced by the compatibility adapter.

Priority represents importance/relevance. Weight represents variety among
already-eligible equivalent ambient content. A failing requirement never becomes
a low-probability event.

## Conversation execution

The rewritten engine executes the selected event's own node graph. A node contains
either `line` (one localization key) or nonempty `lines` (ordered localization
keys), never both. After its last line, it has exactly one continuation shape:
`choices`, `next`, `complete: true`, or `end: true`. A `next`-only node advances on
Continue; it is not an automatic recursive transition. A choice has a stable
ID, localisation key, optional hard requirements, and either one direct
action/next path or an `outcomes` list for server-selected reaction variation.

An outcome has deterministic hard requirements, a positive `weight`, zero or more
typed server-side actions, and a required next node. Direct choices also require
`next`; explicit terminal nodes define how a path ends. When a choice uses outcomes,
the server first removes outcomes whose hard requirements fail, then performs a
weighted draw only among the remaining outcomes. If no outcome remains eligible,
that choice is not offered to the player. Direct choices are equivalent to one
always-eligible outcome with weight 1.

This preserves the useful part of the existing result system for cases such as
greetings, jokes, stories, and rock-paper-scissors without carrying forward
numeric condition modifiers. Conditions answer *can this outcome happen?*;
`weight` answers *which eligible reaction is chosen?*.

`complete: true` declares a completing terminal; the final Continue acknowledges
it and commits completion. `end: true` declares a non-completing terminal.
Closing the UI, walking away, disconnecting, or acknowledging a non-completing
terminal never implicitly marks an event complete. Closing/walking away pauses;
an acknowledged `end` discards the unfinished run immediately.

Terminal intent must be explicit. A terminal node must specify exactly one of
`complete: true` or `end: true`. For `once` and `cooldown` events, a reachable
non-completing `end: true` is rejected unless the node also sets
`retryable: true`. That opt-in means the author deliberately wants the player to
be able to encounter the event again after taking that branch. A completing
terminal consumes `once` or starts the cooldown; a retryable terminal does neither.

Actions are rewritten as typed, validated data (`mca:hearts`, `mca:mood`,
`mca:remember`, command where currently supported, and other existing semantics
that are actually needed). Accepting a choice queues its authored effects in
server session state. Commit them once on successful final Continue together
with the completion record. Pause/resume preserves that queue; timeout,
non-completing end, or invalidation discards it. Do not grant hearts repeatedly
by choosing a rewarding branch, closing, and restarting without completing.
Each permitted repeat that reaches successful completion applies its authored
effects once for that run. The five-second sample interval therefore permits
frequent completed rewards during development; authors can tune cooldowns and
reward amounts for production without introducing a separate hidden reward gate.

Gameplay command effects are supported only on a completing path and must be
revalidated at commit through the existing command owner. Keep commands out of
retryable/non-completing paths, and avoid mixing multiple irreversible commands
into one completion. A failed command must not award story rewards or mark the
event complete. Do not implement general rollback of Minecraft world changes;
command-specific execution/failure semantics must be reviewed during migration.

Define action side effects against the actual MCA owner. In particular,
`VillagerBrain.rewardHearts` also changes mood, interaction fatigue and advancement
state; preserve intended migrated behaviour and do not apply those effects twice
through a separate mood action. Pending effects are not simulated world facts for
evaluating later choices in the same conversation.

The old numeric `Result` probability/constraint model is not carried into the new
engine; random variation is represented explicitly where authored content needs it.

Check event requirements and repeat availability when offering and starting an
event. Once it starts, changing time/weather/mood or the expiry of its triggering
recent fact does not cancel it, including on valid resume. Recheck interaction
validity, explicit choice/outcome requirements, and action-specific constraints
before the relevant transition. If a displayed choice loses eligibility, refresh
the offered choices without executing it or redrawing a previously accepted
outcome. If no choices remain, end the run without completion or effects and
return to ordinary Talk; do not invent an eligible result.

`DialogueType` keeps its existing purpose: it controls how a line is phrased or
which translation fallback is selected. DialogueEvent requirements decide what
the villager wants to discuss. Personality may therefore affect both event
eligibility and line phrasing without merging those responsibilities.

## Server-authoritative session and packets

DialogueEvent selection and progression are server authoritative.

At most one retained event conversation exists per player, either active or
paused. Selecting a different new run with the same or another villager discards
the old paused run only after the new selection has passed validation;
durable history for the two player/villager pairs remains independent. Its
transient server session contains at least:

```text
player UUID
villager UUID
event ID
reload generation
session identity
single-use offer token
ACTIVE or PAUSED status and pause-expiry game time
current node ID
current line index
currently offered choice IDs
temporary accepted stable choice IDs and selected outcomes
pending story effects
```

The TALK/event-list response sends displayable new-topic prompts/event IDs, an
optional server-built continuation prompt for this pair, presentation data, and
a menu offer token. Selecting an event sends its ID plus that token. Selecting
continuation sends `RESUME` plus the menu token; the server derives the retained
run, never trusting client-supplied resume node/session state. Selecting a choice
sends its stable ID plus the current active-run offer token.
Continue sends only the current offer token; the server derives the current
node/line and next transition. Client packets never provide arbitrary node,
line-index, outcome, action, or completion state.

Each accepted event selection, choice, or Continue consumes the offer token
before advancing or applying effects and issues a fresh one for the next view.
Pause invalidates the current offer; resume preserves the session identity but
issues a new offer. Use a server-unique token sequence; a node-local counter
restarting at zero on every conversation is insufficient. Reload generation
belongs to the immutable resource snapshot and is not reused as an offer token.
The engine retains one bounded current menu offer alongside at most one run per
player; a menu offer is not a second dialogue session or history owner. It binds
sender, target villager, generation, offered event IDs, ambient candidates and
whether continuation was offered. Refreshing it invalidates the old menu token,
but does not replace the paused run or refresh its deadline. A forged or expired
`RESUME` selection is rejected, never interpreted as permission to restart.

Keep the current interaction UI flow: opening the GUI shows its normal controls
and sends no dialogue begin/resume request. The Talk button sends the begin
request, whose server handler returns the menu with optional continuation and
other eligible topics. It never auto-resumes. The existing select request's
`RESUME` kind performs explicit resume; no resume-only packet is needed.
Retain the resolved current line in the client presentation state during the
pause window so pooled phrase fallback does not reroll it on reopen; clear that
presentation state when its session is replaced, completed, expired or invalidated.

Before starting an event or accepting an answer, the server verifies:

- the sender owns the applicable menu offer or run, and the target villager
  matches that authoritative state (a new menu may target Bob while Alice is paused);
- the villager still exists and is interactable;
- the player is still within the normal interaction distance;
- the resource snapshot generation and single-use offer token are current;
- new-event/continuation selections match the current menu offer and target;
- continuation was offered for this pair and its retained run is still PAUSED,
  within its deadline, and otherwise valid; do not recheck original entry gates;
- active choice/Continue messages target an ACTIVE run, not PAUSED or expired;
- a new-event selection names an offered event, with current entry/repeat gates
  rechecked before replacing any paused run;
- an active choice belongs to its run's current node, was actually offered, and
  still passes explicit choice requirements where applicable.

Stale, replayed, or forged selections are ignored without applying actions.

The temporary legacy packet path also needs active-question/offered-answer
validation. The currently inspected `dev/1.21.1` `Dialogues.selectAnswer` already
rechecks answer constraints; retain that gate instead of claiming it is absent.
It still does not bind the supplied question/answer pair to an active offered
question. Local `Constraint` changes also reject unknown constraint tokens;
preserve and verify those existing changes rather than replacing them blindly.

## Reload validation and failure behaviour

`DialogueEvents` is a server resource reload listener and publishes one immutable
registry snapshot only after reload preparation succeeds.

Use Mojang codecs with `JsonOps` for typed definition/condition/action decoding,
with a separate graph/reference validation pass and resource ID supplied by the
loader. The 1.21.1 Gson reload listener supplies JSON trees; it does not require
a second handwritten serialization contract. Report errors with event ID and
field/node/choice context. Never accept a partially decoded definition with
missing requirements after codec errors.

Each event is validated for:

- canonical namespaced resource ID;
- recognized trigger and presentation mode;
- nonblank prompt for selectable modes and nonblank event-specific `resume_prompt`
  for every mode; missing client translations display their key without changing
  server authority, and shipped-language tests check that keys actually resolve;
- finite positive weight;
- finite positive outcome weights;
- required repeat policy; cooldown has exactly one seconds form, finite
  nonnegative durations and ordered range bounds, with overflow-safe conversion
  to ticks and next-eligible timestamp calculation;
- valid history policy, with scheduling-only storage restricted to cooldown events;
- recognized condition types and valid condition arguments;
- recognized MCA-owned enum/registry values where the server can validate them;
- valid start node, node IDs, event-wide unique choice IDs, and next-node
  references;
- each node has exactly one of nonblank `line` or nonempty nonblank `lines`;
- each node has exactly one continuation shape: nonempty `choices`, `next`,
  `complete: true`, or `end: true`;
- every direct choice and every outcome has a valid `next` node;
- acyclic node graph for this format; repetition uses event repeat policy, not
  unbounded reward-bearing loops inside an event;
- each choice uses exactly one execution shape: direct `actions`/`next` or a
  nonempty `outcomes` list;
- a choice with outcomes is offered only when at least one outcome is currently
  eligible;
- every terminal specifies exactly one of `complete: true` or `end: true`;
- reachable non-completing terminals in `once`/`cooldown` events explicitly opt
  into `retryable: true`;
- references from MCA-owned shipped resources to missing `mca:` events are reload
  errors.

Validate referenced stable choice IDs when the target exists; resolved history
references must target story-retaining events. Diagnose cyclic event prerequisites
as warnings because arbitrary world conditions prevent a general reachability
proof; reject an unconditional completed-before dependency cycle in shipped
resources during resource tests. Do not claim the loader can prove every event's
conditions satisfiable. Runtime zero-choice handling follows the execution rules.

One malformed event is logged and skipped without invalidating unrelated valid
events. Unknown requirements are never dropped. A failed resource must not become
an unconditional event.

Because the graph is event-owned, reload validation is self-contained. Cross-event
validation is needed only for history requirements such as `event_completed` and
`event_choice`; addon references may legally point at events supplied by another
datapack namespace. An unresolved external/addon event reference is valid resource
syntax but its requirement evaluates `false` until that referenced event exists.
This lets optional addon integrations fail closed without making the referencing
event itself malformed. MCA's own shipped `mca:` references are strict so broken
built-in story chains fail during reload instead of disappearing silently.

## Minimal code ownership

The rewrite should add only the owners needed for the final subsystem.
Names may change during the implementation plan, but responsibilities should not
be merged casually.

```text
DialogueEvents
  owns datapack loading and immutable event registry

DialogueEvent
  immutable decoded event definition and nested presentation/repeat records

DialogueCondition
  deterministic condition decoding/evaluation against an explicit context

DialogueAction
  typed queued effects and validated completion-time execution

DialogueContext
  current villager/player/world references for one server evaluation, not a cache

DialogueEngine
  eligibility, presentation selection, line/node progression and session lifecycle

DialogueEventHistory
  SavedData for pair-scoped completion, choices, and cooldowns

DialogueSession
  transient server-authoritative active/paused event/node/line/offered choices

LegacyDialogueAdapter
  temporary migration bridge for unconverted dialogues; removable after migration
```

The rewrite should reuse:

```text
DialogueType
InteractScreen rendering where practical
existing villager/player relationship and world-state owners
```

Scope engine/session state to a running server and clear it on shutdown; a second
integrated-server world must not inherit sessions or offers from the first.

`Dialogues`, `Question`, `Answer`, `Result`, and `Actions` remain only behind the
migration adapter while old resources are converted. They are not dependencies of
new DialogueEvents and can be removed once migration is complete.

`GiftPredicate` may supply implementation inspiration or small domain lookup
helpers, but the new event requirement contract is boolean and must not inherit
its numeric weighting semantics accidentally.

## Persistence and lifecycle

`DialogueEventHistory` is stored in overworld SavedData so pair history does not
disappear when a villager entity unloads. Event records are keyed by UUID and
namespaced event ID.

The record is dirtied only when completion/history changes, not on eligibility
checks (and when pruning actually removes scheduling records). Cooldowns store
the rolled `nextEligibleGameTime`; they are not rerolled every time the player
talks. Use overworld game time consistently for history and pause deadlines.

SavedData starts with explicit `schema_version: 1`. Validate loaded IDs and
timestamps, bound input collections, and keep version upgrade logic at the
history owner. Do not silently clear saves with an unsupported future version.
Stable IDs are save-facing API; changing an event/choice ID requires an explicit
migration or documented story reset, not an accidental rename.

Villager death does not immediately erase pair history. A UUID identifies the
personality/relationship subject MCA already uses for family identity, and losing
the entity should not erase completed replies or bypass a stored cooldown if the
same villager is restored. Explicit author-selected `once` policies also remain
consumed after restoration.

History has bounded records per player/villager/event tuple, not an append-only
conversation log. It is not a globally constant-size database: the number of
distinct pairs and durable stories can grow. Durable story state (completion and recorded
choice IDs) is retained indefinitely because later events may depend on it. Pure
scheduling state with no durable completion/choice history may be removed lazily
once its cooldown has expired. An orphaned player/villager pair that contains only
such expired scheduling state may be removed as a whole; a pair containing durable
story state is retained unless an explicit administrative/save-migration purge is
performed. Entity death or temporary unload alone is never a cleanup signal.

Scheduling-only events must not store completion counts or choice sets, so their
records can actually expire. Prune those records lazily on relevant accesses and
in bounded maintenance batches; avoid scanning the entire save on every TALK.
Keep removed-addon durable records to allow reinstalling its story chain; provide
an explicit administrative purge/migration path for obsolete IDs and player data.
Do not silently evict permanent story state to meet an arbitrary cap.

MCA villager/zombie conversion preserves the same UUID. On `dev/1.21.1`, the
current conversion pipeline also writes and restores `LongTermMemory` through
`writeAdditionalConversionData` / `readAdditionalConversionData`; dialogue work
should add regression coverage for that existing guarantee rather than introduce
a second conversion-state path. Any new gameplay fact owner used by dialogue must
likewise define whether and how it survives villager/zombie conversion.

## Addon authoring contract

A datapack-only addon can add:

```text
data/<addon>/dialogue_events/*.json
assets/<addon>/lang/*.json
```

without Java code, using built-in conditions, event-owned nodes, and typed actions.

Java addons may register additional condition/action types through deliberately
small server-side extension points once the built-in codec registry shape is
stable. No client-supplied action code is ever executed. Custom conditions must be
deterministic for a given evaluation context.

Translation `/1`, `/2`, etc. are MCA's pooled alternative phrasings, not a sequence
of spoken lines. Use unique semantic keys for ordered `lines`, for example
`intro.heard_about_cures` and `intro.same_person`. Player replies and villager
responses also require distinct keys. Reject duplicate keys in shipped language
source validation; ordinary JSON parsing may otherwise discard earlier entries.

Addon event IDs always retain their namespace. An addon named `example_addon`
therefore cannot collide accidentally with `mca:personal/gloomy_reflection` merely by
using the same filename.

## Reference conversations

The first migrated content set should prove different axes of the system rather
than large quantities of writing.

1. **Gloomy nighttime reflection**
   - personality + hearts + sad mood + nighttime;
   - highlighted;
   - repeatable per pair, `seconds: 5` for now;
   - at least one meaningful choice.
2. **Grumpy nighttime conversation**
   - personality + nighttime;
   - ambient or ask;
   - fixed `seconds: 5` cooldown for now.
3. **Weather/ambience conversation**
   - rain or thunder + personality/trait preference;
   - repeatable, fixed `seconds: 5` cooldown for now; random intervals remain supported.
4. **Trait conversation**
   - ordinary trait requirement, with lactose intolerance as a concrete data
     example rather than a hard-coded special case.
5. **Mourning conversation**
   - recent relative death + relationship context;
   - highlighted/ask;
   - repeatable per pair with `seconds: 5` for now, while the recent-death
     requirements still hold; later grief conversations may be separate events.
6. **Cured-zombie identity discussion and personal cure story**
   - Phyrra's speculative discussion: adult + gloomy + nighttime + hearts >= 20;
   - the speculative speaker need not have been cured;
   - separate personal story may require `mca:recent_event` for a successfully
     recorded cure on the speaking villager;
   - both demonstrate meaningful branching choices and ordered response lines.
   - repeatable per pair with `seconds: 5` for now and separately authored
     remembered-choice follow-ups.
7. **Recently revived conversation**
   - recently revived + mood/relationship;
   - highlighted or ask.
8. **Hospital/prison conversation**
   - actual current building context, not merely village-has-building;
   - demonstrates semantic location conditions.
9. **Follow-up conversation**
   - requires completion of event 1;
   - requires a specific earlier stable choice ID;
   - proves persistent relationship continuity.
   - appears as a selectable new topic, not an automatic next event.

All reference stories, including follow-ups, author a five-second cooldown for
now and retain story history where later content needs it. These events should
be written as test/sample content, not treated as the final
volume or tone of MCA's production dialogue.

### Phyrra's conversation in the new authoring format

Resource: `data/mca/dialogue_events/personal/cured_zombies.json`.
This story is repeatable with an authored `seconds: 5` cooldown for each
player/villager pair. Its latest completed replies remain available to separately
authored follow-up events; cooldown expiry does not erase them. Reward amounts below
remain illustrative content values. The four positive entry
requirements are confirmed; the old `!adult`, `!night`, `!gloomy`, and `!20`
tokens must not be interpreted as the intended exclusions.

```json
{
  "trigger": "talk",
  "presentation": {
    "mode": "ask",
    "prompt": "dialogue_event.mca.cured_zombies.prompt",
    "resume_prompt": "dialogue_event.mca.cured_zombies.resume"
  },
  "priority": 50,
  "requirements": [
    { "type": "mca:age_group", "value": "adult" },
    { "type": "mca:personality", "value": "gloomy" },
    { "type": "mca:time", "min": 13000, "max": 23000 },
    { "type": "mca:hearts", "min": 20 }
  ],
  "repeat": { "type": "cooldown", "seconds": 5 },
  "history": "story",
  "start": "intro",
  "nodes": {
    "intro": {
      "lines": [
        "dialogue_event.mca.cured_zombies.intro.heard_about_cures",
        "dialogue_event.mca.cured_zombies.intro.same_person"
      ],
      "choices": [
        {
          "id": "experience_changes_you",
          "text": "dialogue_event.mca.cured_zombies.choice.experience_changes_you",
          "actions": [{ "type": "mca:hearts", "amount": 5 }],
          "next": "reflect"
        },
        {
          "id": "dont_know",
          "text": "dialogue_event.mca.cured_zombies.choice.dont_know",
          "next": "uncertain"
        },
        {
          "id": "challenge_the_question",
          "text": "dialogue_event.mca.cured_zombies.choice.challenge_the_question",
          "actions": [{ "type": "mca:hearts", "amount": -5 }],
          "next": "apology"
        }
      ]
    },
    "reflect": {
      "lines": [
        "dialogue_event.mca.cured_zombies.reflect.experience",
        "dialogue_event.mca.cured_zombies.reflect.more_than_that"
      ],
      "next": "bedtime"
    },
    "uncertain": {
      "lines": [
        "dialogue_event.mca.cured_zombies.uncertain.guess",
        "dialogue_event.mca.cured_zombies.uncertain.bother"
      ],
      "next": "bedtime"
    },
    "apology": {
      "lines": [
        "dialogue_event.mca.cured_zombies.apology.sorry",
        "dialogue_event.mca.cured_zombies.apology.shouldnt_have_said"
      ],
      "next": "bedtime"
    },
    "bedtime": {
      "line": "dialogue_event.mca.cured_zombies.bedtime",
      "complete": true
    }
  }
}
```

Matching language entries use unique keys, not repeated `/1` keys:

```json
{
  "dialogue_event.mca.cured_zombies.prompt": "Something on your mind?",
  "dialogue_event.mca.cured_zombies.resume": "%1$s, about what you said about cured villagers...",
  "dialogue_event.mca.cured_zombies.intro.heard_about_cures": "I heard about some zombies being able to be cured and turning back into people.",
  "dialogue_event.mca.cured_zombies.intro.same_person": "I can't help but wonder if they're still the same person after that, or if something is... different.",
  "dialogue_event.mca.cured_zombies.choice.experience_changes_you": "I wonder about that too. Maybe they're just different from the experience.",
  "dialogue_event.mca.cured_zombies.choice.dont_know": "I wouldn't know.",
  "dialogue_event.mca.cured_zombies.choice.challenge_the_question": "That's a cruel thing to think about other villagers. Haven't they been through enough?",
  "dialogue_event.mca.cured_zombies.reflect.experience": "I hadn't thought of it like that. It's a lot to go through.",
  "dialogue_event.mca.cured_zombies.reflect.more_than_that": "But what if it's more than that?",
  "dialogue_event.mca.cured_zombies.uncertain.guess": "I suppose I shouldn't try to guess either.",
  "dialogue_event.mca.cured_zombies.uncertain.bother": "Sorry, I shouldn't bother you with this.",
  "dialogue_event.mca.cured_zombies.apology.sorry": "You're right. I'm sorry...",
  "dialogue_event.mca.cured_zombies.apology.shouldnt_have_said": "I knew I shouldn't have said anything.",
  "dialogue_event.mca.cured_zombies.bedtime": "Thanks for listening. I need to go to bed now."
}
```

## Approved gameplay decisions

These decisions include the later remembered-question and player-choice
refinements; none authorizes product implementation:

1. **Task 3 — remembered stories:** stories can repeat after their authored
   per-event/pair cooldown, with separate follow-ups reflecting the recorded
   answer. Each story remembers the latest completed
   run's choice set, replacing rather than accumulating previous choices. If a
   player first says cured people are unchanged, then later says experience
   changes them, follow-ups remember only the second completed answer. Abandoning
   a later attempt leaves the previous completed choice set intact.
2. **Task 5 — conversation selection:** Talk offers an authored, personalized
   continuation alongside other eligible topics instead of auto-resuming.
   Selecting a different valid topic with Alice or Bob abandons the paused run.
   Keep at most one active or paused run per player. Browsing either villager's
   normal controls or Talk menu does not abandon it or reset its deadline.
   Durable history/cooldown remains independent for each pair.
3. **Task 11 — repeat interval for now:** reference conversations, including
   stories, Phyrra's speculative cured-zombie discussion and small talk, author
   `repeat: { "type": "cooldown", "seconds": 5 }` in their datapack JSON.
   Optional random intervals use `min_seconds`/`max_seconds`. Keep the two-minute
   pause window unchanged and retain per-event random cooldown support for later
   content tuning. This supersedes the earlier once-only reference-story decision.

## Prerequisite hardening retained from the audit

The source audit found several existing weaknesses that directly affect safe
DialogueEvent execution. They are prerequisites or adjacent fixes, not the
feature's primary architecture.

1. Bind legacy answers to the active offered question while the migration adapter
   remains reachable, and retain the current server constraint revalidation.
2. Preserve and regression-test the local fail-closed constraint changes. Existing
   shipped references such as `rumors_cooldown` and `child` must be corrected or
   represented by valid conditions rather than restoring silent token dropping.
3. Regression-test the existing `LongTermMemory` villager/zombie conversion
   preservation before relying on it for recent facts, and give any new structured
   fact owner explicit conversion semantics.
4. Keep new **event IDs** fully namespaced and path-preserving. Legacy IDs remain
   isolated inside the temporary adapter instead of constraining the new format.
5. Treat weighted conditions in existing `Result`/`InteractionPredicate` as
   legacy probability logic; do not use them as hard DialogueEvent requirements.

Gift predicate semantics remain outside this overhaul. Legacy dialogue resources
and action semantics are migrated only to the extent needed to retire the old
conversation engine cleanly.

### Legacy removal finish line

The migration is complete only when all of the following are true on the target
branch:

- every shipped `data/mca/dialogues/*.json` conversation has either been converted
  to `dialogue_events` or intentionally replaced by a non-dialogue interaction;
- `InteractScreen` starts and advances conversations only through the new event
  protocol;
- `MessagesMCA` no longer registers the legacy dialogue init/select/response
  payloads;
- no common, Fabric, or NeoForge reload registration constructs `Dialogues` or a
  loader-specific `FabricDialogues` wrapper;
- no production caller references `Dialogues`, `Question`, `Answer`, `Result`, or
  `Actions`;
- compatibility tests no longer require the legacy adapter.

At that point the adapter, legacy packets, `Dialogues`, the four legacy dialogue
data classes, loader registration glue, and old `data/mca/dialogues/` resources
are deleted in the same cleanup phase. The adapter is not kept as a permanent
addon compatibility API.

## Version portability

The design is shared across 1.21.1, 26.1.2, and 26.2. The audited MCA dialogue
model is effectively stable across those branches; the main porting differences
are Minecraft API boundaries around identifiers, reload listeners, SavedData, and
small entity/server accessors.

The implementation should therefore prove behaviour on 1.21.1 first and then port
thin adapters rather than invent three architectures.

Expected version-specific areas are:

- 1.21.1 `ResourceLocation`/Gson-style reload plumbing versus 26.x
  `Identifier`/codec-oriented reload APIs;
- loader registration differences on Fabric and NeoForge;
- 1.21.1 SavedData factory/load APIs versus 26.x SavedData type/codec APIs;
- Java 21 on 1.21.1 versus Java 25 on the reviewed 26.x branches.

Event JSON semantics, IDs, selection rules, history semantics, and server
authority must remain the same across versions.

## Testing and acceptance

The implementation is acceptable when automated tests cover at least:

- canonical event IDs preserve namespace and nested resource path;
- two addons may use the same basename without collision;
- malformed or unknown conditions fail closed and are logged;
- all hard requirements must pass before an event is eligible;
- each built-in condition has focused positive and negative tests;
- any enabled `in_building` condition does not pass merely because the village
  has that building;
- highlighted selection chooses highest priority while keeping other eligible
  events accessible;
- ambient selection uses only the highest priority tier and honors weight within
  that tier;
- choice-outcome selection filters hard requirements first and applies weight only
  among eligible outcomes;
- a choice with zero eligible outcomes is not offered;
- no contextual DialogueEvent still leaves ordinary new-engine conversation
  available; migration-only legacy fallback works only while unconverted content
  remains;
- `once` blocks only after explicit completion;
- closing pauses for 2400 overworld ticks; right-click opens normal controls and
  Talk offers a personalized continuation alongside other topics; selecting that
  continuation resumes the same node/line/choices without rerolling an outcome;
- opening the interaction screen or browsing Talk options does not resume the
  run or extend its deadline;
- menu browsing preserves the paused run/deadline, while selecting a different
  valid topic with the same or another villager abandons it without effects;
  invalid/stale selections preserve it, and at most one run is retained;
- a paused event appears only as continuation, not as a second new-start option
  or an ambient draw candidate; priority does not hide continuation/other topics;
- completing a story excludes that event until its authored cooldown expires;
  completion/choices survive expiry, and other topics or choice-dependent
  follow-ups appear when eligible and selected by the player;
- completion/end returns to refreshed Talk options, never auto-starts another event;
- continuation prompts are event-specific/localized, with optional villager-name
  argument and no guessed generic label or exposure of unchosen story branches;
- expiry discards unfinished history/effects; disconnect or restart does not
  persist unfinished conversations;
- typewriter reveals one complete word per 250 ms, with locale-aware, Unicode-safe
  reveal and no skip/acceleration or instant-display setting; Continue/replies
  become available only when their current/final passage line is fully revealed;
- clicking during reveal neither exposes the rest of the line nor advances or
  queues advancement;
- consecutive localization keys play in order; pooled `/1` and `/2` remain
  alternative phrases rather than consecutive lines;
- terminal lines require final Continue before effects/history/cooldown commit;
- closing a rewarding branch before completion cannot farm hearts or mood;
- weather/time/mood changes after starting do not interrupt a valid run;
- fixed cooldown and random-window cooldown respect stored next-eligible game
  time and random bounds;
- reference stories and small talk author `seconds: 5`, normalized to 100 ticks:
  unavailable at completion + 99
  ticks and eligible at completion + 100 ticks if other requirements still hold;
- missing repeat policy, conflicting seconds forms, obsolete tick fields,
  non-finite/negative seconds, reversed ranges and conversion overflow are rejected;
  fractional positive seconds round upward to a tick and random bounds roll only
  at completion;
- two players have independent history with the same villager;
- a prior event-completion requirement works after save/reload;
- a prior stable event choice requirement works after save/reload even if its node
  is later reorganized;
- a repeat completion replaces the prior choice set; choices omitted from the
  latest completed run no longer match, and an abandoned attempt changes nothing;
- duplicate choice IDs within one event are rejected;
- `once`/`cooldown` events reject reachable non-completing terminals unless they
  explicitly set `retryable: true`;
- missing external/addon event prerequisites evaluate false without invalidating
  the referencing event, while broken shipped `mca:` references fail reload;
- expired scheduling-only history can be pruned without removing durable completed
  story/choice history;
- consumed offer tokens reject duplicate event/choice/Continue packets, including
  packets delivered after pause/resume, completion or resource reload;
- explicit negation cannot make missing/unknown references pass;
- unsupported save versions are not silently reset;
- resolved history references cannot target scheduling-only events;
- zero available choices ends safely without completion/effects;
- hidden/invalid legacy answers are rejected server-side;
- recent life-event facts have explicit owners and expiry semantics;
- both Fabric and NeoForge builds remain healthy for the target branch.

The overhaul is successful when testers can encounter the sample conversations,
understand why they feel contextually appropriate without seeing implementation
labels, make a choice, and later receive a follow-up conversation that reflects
that choice.

Delivery also includes the personality writing guide and the speculative/personal
cure examples. Follow the recorded gameplay decisions above in their dependent
plan tasks; documentation approval does not authorize implementation.

## Delivery boundary

This spec and its accompanying migration plan describe the design, not permission
to implement it. The
target architecture is the rewritten DialogueEvent engine: event-owned graphs,
history, authoritative sessions, typed conditions/actions, and contextual
selection. The legacy engine may survive temporarily behind an adapter only to
make migration incremental; it is not part of the desired end state.

Review these refinements together with the written migration plan for
`dev/1.21.1`. Product implementation still requires explicit authorization;
version-specific adapters can be ported to 26.1.2/26.2 after 1.21.1 is verified.
