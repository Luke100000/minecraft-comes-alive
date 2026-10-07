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
  -> collect DialogueEvents for this player/villager
  -> evaluate hard requirements
  -> evaluate completion, previous-choice, and repeat/cooldown state
  -> classify eligible events by presentation mode
  -> show one highlighted contextual prompt plus other selectable topics
  -> player chooses an event or the generic conversation option
  -> create a server-owned DialogueSession at the event's start node
  -> execute event nodes, choices, and actions
  -> record completed event and choices when the event explicitly completes
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
    topic             optional organizational string
  priority            integer, default 0
  weight              positive number, default 1
  requirements[]      deterministic hard requirements
  repeat              ALWAYS | ONCE | COOLDOWN
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
  "repeat": { "type": "once" },
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
      "end": true
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
- `event_choice`.
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

In-progress state is not persisted. If a player closes the screen, disconnects,
or the server stops before explicit event completion, the event remains eligible
later and its temporary choices are discarded.

This produces the desired continuity without making shared world facts
player-specific. Two players can therefore have different conversation histories
with the same villager while both observe that the villager was recently cured.

## Seen/completed and previous-choice semantics

The event system uses `completed`, not an ambiguous `seen` boolean, as the durable
prerequisite. An event becomes completed only when its dialogue explicitly reaches
an event-completion action while the matching active session is valid.

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

## Repeat and cooldown policy

Initial repeat policies are intentionally small:

```text
always
once
cooldown
```

`once` means once per villager-player pair after successful completion.
`always` adds no history gate.

`cooldown` uses a minimum and maximum number of game ticks:

```json
{
  "type": "cooldown",
  "min_ticks": 48000,
  "max_ticks": 120000
}
```

Equal min/max values are a fixed cooldown. Different values create the requested
random interval; after successful completion the server rolls the next eligible
game time within the inclusive range. This is useful for repeatable small talk so
it does not reappear on a mechanical exact schedule.

Conversation cooldown is pair-scoped. A world fact may expire
globally, but whether a player has recently had a particular conversation is a
relationship-history concern.

## Selection rules

On TALK, the server evaluates events in a stable canonical ID order and produces
the eligible set. Selection then follows presentation mode.

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
a localisation key and either choices or a terminal state. A choice has a stable
ID, localisation key, optional hard requirements, and either one direct
action/next path or an `outcomes` list for server-selected reaction variation.

An outcome has deterministic hard requirements, a positive `weight`, zero or more
typed server-side actions, and an optional next node. When a choice uses outcomes,
the server first removes outcomes whose hard requirements fail, then performs a
weighted draw only among the remaining outcomes. If no outcome remains eligible,
that choice is not offered to the player. Direct choices are equivalent to one
always-eligible outcome with weight 1.

This preserves the useful part of the existing result system for cases such as
greetings, jokes, stories, and rock-paper-scissors without carrying forward
numeric condition modifiers. Conditions answer *can this outcome happen?*;
`weight` answers *which eligible reaction is chosen?*.

`complete: true` is explicit event completion. `end: true` ends the conversation
without consuming a once-only event. Closing the UI, walking away, disconnecting,
or reaching an ordinary non-completing terminal never implicitly marks an event
complete.

Terminal intent must be explicit. A terminal node must specify exactly one of
`complete: true` or `end: true`. For `once` and `cooldown` events, a reachable
non-completing `end: true` is rejected unless the node also sets
`retryable: true`. That opt-in means the author deliberately wants the player to
be able to encounter the event again after taking that branch. A completing
terminal consumes `once` or starts the cooldown; a retryable terminal does neither.

Actions are rewritten as typed, validated data (`mca:hearts`, `mca:mood`,
`mca:remember`, command where currently supported, and other existing semantics
that are actually needed). They execute only on the server after the session
accepts the offered choice. The old numeric `Result` probability/constraint model
is not carried into the new engine; random variation is represented explicitly
where authored content needs it.

`DialogueType` keeps its existing purpose: it controls how a line is phrased or
which translation fallback is selected. DialogueEvent requirements decide what
the villager wants to discuss. Personality may therefore affect both event
eligibility and line phrasing without merging those responsibilities.

## Server-authoritative session and packets

DialogueEvent selection and progression are server authoritative.

At most one active event conversation exists per player. Its transient server
session contains at least:

```text
player UUID
villager UUID
event ID
generation/token
current node ID
currently offered choice IDs
temporary selected node/choice path
```

The TALK/event-list response sends only displayable prompt/event identifiers and
presentation data. Selecting an event sends the chosen event ID plus the current
generation/token. Selecting a choice sends only the choice identity for the active
node; the server never trusts a client-supplied arbitrary dialogue state.

Before starting an event or accepting an answer, the server verifies:

- the active player and villager match;
- the villager still exists and is interactable;
- the player is still within the normal interaction distance;
- the generation/token is current;
- the event is the event offered by the current TALK result;
- the choice belongs to the current node and was actually offered;
- hard choice requirements still pass where applicable.

Stale, replayed, or forged selections are ignored without applying actions.

The same answer-validity check should harden the legacy dialogue packet path as a
prerequisite fix because the current implementation validates question/answer
existence but does not re-run the eligibility gate before executing the result.

## Reload validation and failure behaviour

`DialogueEvents` is a server resource reload listener and publishes one immutable
registry snapshot only after reload preparation succeeds.

Each event is validated for:

- canonical namespaced resource ID;
- recognized trigger and presentation mode;
- nonblank prompt for selectable modes;
- finite positive weight;
- finite positive outcome weights;
- valid repeat range with `0 <= min_ticks <= max_ticks`;
- recognized condition types and valid condition arguments;
- recognized MCA-owned enum/registry values where the server can validate them;
- valid start node, node IDs, event-wide unique choice IDs, and next-node
  references;
- every nonterminal node has at least one valid choice or explicit automatic
  transition if such transitions are later supported;
- each choice uses exactly one execution shape: direct `actions`/`next` or a
  nonempty `outcomes` list;
- a choice with outcomes is offered only when at least one outcome is currently
  eligible;
- every terminal specifies exactly one of `complete: true` or `end: true`;
- reachable non-completing terminals in `once`/`cooldown` events explicitly opt
  into `retryable: true`;
- references from MCA-owned shipped resources to missing `mca:` events are reload
  errors.

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

DialogueEventCondition
  deterministic condition decoding/evaluation against an explicit context

DialogueEngine
  eligibility, highlighted/ask/ambient selection, and node progression

DialogueEventHistory
  SavedData for pair-scoped completion, choices, and cooldowns

DialogueSession
  transient server-authoritative per-player active event/node/offered choices

LegacyDialogueAdapter
  temporary migration bridge for unconverted dialogues; removable after migration
```

The rewrite should reuse:

```text
DialogueType
InteractScreen rendering where practical
existing villager/player relationship and world-state owners
```

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
checks. Cooldowns store the rolled `nextEligibleGameTime`; they are not rerolled
every time the player talks.

Villager death does not immediately erase pair history. A UUID identifies the
personality/relationship subject MCA already uses for family identity, and losing
the entity should not make a player able to replay once-only history if the same
villager is restored.

History is bounded by storing one `EventRecord` per player/villager/event tuple,
not an append-only conversation log. Durable story state (completion and recorded
choice IDs) is retained indefinitely because later events may depend on it. Pure
scheduling state with no durable completion/choice history may be removed lazily
once its cooldown has expired. An orphaned player/villager pair that contains only
such expired scheduling state may be removed as a whole; a pair containing durable
story state is retained unless an explicit administrative/save-migration purge is
performed. Entity death or temporary unload alone is never a cleanup signal.

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

Addon event IDs always retain their namespace. An addon named `example_addon`
therefore cannot collide accidentally with `mca:personal/gloomy_reflection` merely by
using the same filename.

## Reference conversations

The first migrated content set should prove different axes of the system rather
than large quantities of writing.

1. **Gloomy nighttime reflection**
   - personality + hearts + sad mood + nighttime;
   - highlighted;
   - once per pair;
   - at least one meaningful choice.
2. **Grumpy nighttime conversation**
   - personality + nighttime;
   - ambient or ask;
   - random 2-5 day cooldown.
3. **Weather/ambience conversation**
   - rain or thunder + personality/trait preference;
   - repeatable random cooldown.
4. **Trait conversation**
   - ordinary trait requirement, with lactose intolerance as a concrete data
     example rather than a hard-coded special case.
5. **Mourning conversation**
   - recent relative death + relationship context;
   - highlighted/ask;
   - once or long cooldown.
6. **Zombie cure identity conversation**
   - recently cured;
   - asks whether the villager feels like the same person;
   - meaningful branching choice.
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

These events should be written as test/sample content, not treated as the final
volume or tone of MCA's production dialogue.

## Prerequisite hardening retained from the audit

The source audit found several existing weaknesses that directly affect safe
DialogueEvent execution. They are prerequisites or adjacent fixes, not the
feature's primary architecture.

1. Revalidate legacy dialogue answers on the server before applying results for as
   long as the migration adapter remains reachable.
2. Reject or fail closed on unknown hard constraints instead of silently dropping
   them. Existing shipped references such as `rumors_cooldown` and `child` must be
   corrected or represented by valid conditions.
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
- closing or disconnecting before completion does not consume a once-only event;
- fixed cooldown and random-window cooldown respect stored next-eligible game
  time and random bounds;
- two players have independent history with the same villager;
- a prior event-completion requirement works after save/reload;
- a prior stable event choice requirement works after save/reload even if its node
  is later reorganized;
- duplicate choice IDs within one event are rejected;
- `once`/`cooldown` events reject reachable non-completing terminals unless they
  explicitly set `retryable: true`;
- missing external/addon event prerequisites evaluate false without invalidating
  the referencing event, while broken shipped `mca:` references fail reload;
- expired scheduling-only history can be pruned without removing durable completed
  story/choice history;
- stale/replayed event or choice packets cannot execute actions;
- hidden/invalid legacy answers are rejected server-side;
- recent life-event facts have explicit owners and expiry semantics;
- both Fabric and NeoForge builds remain healthy for the target branch.

The overhaul is successful when testers can encounter the sample conversations,
understand why they feel contextually appropriate without seeing implementation
labels, make a choice, and later receive a follow-up conversation that reflects
that choice.

## Delivery boundary

This spec authorizes a future implementation plan, not implementation itself. The
target architecture is the rewritten DialogueEvent engine: event-owned graphs,
history, authoritative sessions, typed conditions/actions, and contextual
selection. The legacy engine may survive temporarily behind an adapter only to
make migration incremental; it is not part of the desired end state.

Only after this revised design is reviewed should implementation be decomposed
into a written migration plan for `dev/1.21.1` and then ported to 26.1.2/26.2.
