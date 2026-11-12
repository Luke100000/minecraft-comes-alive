# Natural, coherent dialogue across MCA 1.21.1

**Status:** Proposed design for review, not approved for implementation.
**Target:** MCA Minecraft 1.21.1, existing `dev/1.21.1-dialogue-system-b` checkout, shared Fabric/NeoForge resources.

**Current focused addendum:** `docs/superpowers/specs/2026-11-12-selectable-dialogue-content-quality-audit.md` supersedes the older **87-event count** below with the verified **89-event baseline** (44 ambient, 26 Ask, 19 Highlighted), and specifies location-only, role-neutral Prison/Infirmary topics plus **Guard/Outlaw profession-specific Ask conversations anywhere**. Follow its evidence-and-neutral-fallback contract and the linked `docs/superpowers/plans/2026-11-11-natural-dialogue-conversations.md` for implementation priorities. The broader editorial principles in this document still apply.

## Intent

Every villager should respond to what the player actually said, sound like an individual and allow a subject to develop where useful. Current First, Chat, Joke and Story events can stop after one response; some reactions are disconnected from player speech, and some ask questions that the player cannot answer. Keep the livelier writing of old `dev/1.21.1` where it works, without copying its arbitrary insults or outdated mechanics.

This is a review of **all current dialogue**, not a blanket rewrite. Keep natural lines untouched. Review every event, every reachable branch and all English dialogue pools; revise only when there is a real flow, voice, meaning, factual or consistency problem.

## Grounded scope

- Current `data/mca/dialogue_events` has 87 JSON files: 42 ambient (including eight `ambient/root`), 25 personal, seven social, eight relationship, three gameplay and two location.
- `DialogueEvent` supports chained visible lines, choices, weighted/conditional outcomes, `next` and completion, and rejects cyclic or invalid graphs. `DialogueEngine` is authoritative for effects, history and session completion.
- Reuse earlier designs: `2026-10-09-personality-dialogue-library-design.md`, `2026-10-11-dialogue-humanization-biome-design.md`, `2026-10-12-unified-dialogue-flow-design.md`, and `2026-11-09-dialogue-overhaul-master-design.md`. This document adds conversation quality and branch coherence; it does not supersede separate security, lifecycle, slap, UI or async approvals.
- Old `dev/1.21.1` data under `data/mca/dialogues` is useful as a writing reference only, not as a runtime format or compatibility target.
- English keys span the base `mca_dialogue` pack and personality-specific packs. Recount the keys during implementation; 5,605 is an older audit snapshot, not a current verified count.

## Approaches

**A. Text-only editing:** Low risk but does not fix conversations ending prematurely or unanswered follow-up questions.

**B. Authored, contextual branching in existing event JSON (recommended):** Extends conversations using existing `Node`, `Choice`, `Outcome`, conditions and completed-history follow-ups. Preserves localization, datapacks and the current engine; requires editorial and graph tests.

**C. Runtime-generated or unlimited looping chat:** Flexible but introduces substantial game-fact, localization, save-state, multiplayer and safety complexity. Explicitly excluded.

The user requested the spec and plan after hearing the recommendation for B. The design and exchange length are still **proposed** pending review, not established approvals.

## Conversation contract

- Ordinary selectable chats target **two to four meaningful exchanges** when the subject warrants it. Provide a relevant follow-up question, player reaction or option to close. Do not pad every greeting to a fixed length.
- Longer personal stories may use four to seven short spoken passages before an important decision, with two or more meaningful responses. Ambient remarks and practical interactions may be one line.
- Every visible player choice is something the player could naturally say, not an abstract label. Every reply must acknowledge that chosen meaning. If a villager asks a question, the next branch must let the player answer, or the line must be revised.
- Authored graphs stay **acyclic**, matching current parser validation. For a later follow-up, use a separate eligible event based on **completed** history, rather than looping back to the same choice node.
- Use a topic-appropriate exit without an obligatory goodbye at every turn. Preserve current final acknowledgement / Back semantics and effects/history commit rules. The client speaks the chosen player line before the villager's response.
- Personalities change opinions, pacing, humor and willingness to open up. Avoid interchangeable affirmations, repeated catchphrases and fabricated personal memories.

Example shape (illustrative English, subject to review):

> Villager: "I don't think we've met. Are you passing through?"
>
> Player: "Just having a look around."
>
> Villager: "Fair enough. Thinking of staying, or just seeing what's here?"
>
> Player: "I might stay." / "Is there somewhere to sleep?" / "Just exploring." / "I should get going."

If the inn exists, a villager can recommend it; without the inn, they must not invent one.

## Content by topic

### Social: first meeting, Chat, Joke, Story, Rumours, games

First meeting follows exploring/settling/lodging with short responsive branches. Chat distinguishes day, life in the village and news: an ordinary day question must not randomly inflict a hostile reaction and heart loss. A guarded villager can decline politely. News cannot invent raids or events. Joke player text must express an actual joke; responses must match its subject, perhaps with one natural retort. Story options remain available without advancements so players can exaggerate, but earned achievements increase the chance of a positive villager reaction; skeptical responses must remain possible. Reactions cannot ask "How did you get away?" then terminate without a response path. Games and Rumours keep their own gameplay and async contracts.

### Personal and ambient

Review all fourteen built-in personality main stories, existing follow-ups and two everyday subjects per personality. Keep meaningful choice differences, verified context branches and eligible fallback. One or two concise sentences per spoken passage is a pacing guide. Ambient events, including biome, mood, time and root greetings, need variation and priority/cooldown checks but usually no forced player response.

### Location and contextual claims

Local-area commentary must match home/current biome rules, known buildings and real server context, with generic fallback. Preserve exact/tag/fallback priority and modded-biome support from the earlier biome design. Any Rumours result must come from actual resolver data, not invented locations.

### Relationships and consequential gameplay

Preserve the age/family/relationship restrictions, partner-positive routes, hearts-tier selection and existing slap semantics. Hiring, staying, adoption and divorce must have clear user intent and correct server effects. Do not lengthen a consequential action with filler small talk or change its outcome merely to improve prose.

## Editorial and localization rules

Use the installed upstream Humanizer 4.0.0 as a **review lens**, not a bulk style replacement. Read full paths through a conversation, identify speaker, previous choice, what each knows, next node and emotional consequence. Preserve deliberate humor, informal speech, personality distinctions and original lines that already work.

Audit every key in `assets/mca_dialogue/lang/en_us.json` and every `assets/mca_dialogue_*/lang/en_us.json`. Build a repeatable inventory by pack, topic, usage, personality, branch, issue and disposition: **keep / revise / intentionally unused / follow-up needed**. Alternative `/1` voice pools are variants, not ordered turns. Preserve translation identifiers, placeholders, escaping, dynamic gender/personality fallback and unmodified non-English locales. English wording lives in locale JSON, not event nodes.

## Boundaries

- No new generation model, dependencies, parallel dialogue engine, global rewrites, new worktree, WSL or UI redesign.
- Current checkout has extensive uncommitted dialogue, UI and localization changes. Inspect the latest per-file diff before touching implementation files; do not overwrite them. A single integrator owns changes to the shared base English locale.
- The master overhaul's P0 security, P1 interaction lifecycle, P2 slap, P3 row styling and P4 async work remain separately controlled. If a requirement truly needs an engine change, document the failing case and request a design change rather than silently changing session authority.

## Acceptance criteria

1. All **87** event resources and every current English dialogue key receive an auditable review result; no event family is skipped.
2. First/Chat/Joke/Story have sensible choice-to-response sequences with natural optional follow-ups and no dangling questions or unearned claims.
3. Every changed event decodes, every branch destination exists, all referenced translations and placeholders are valid, and every intended path can end or complete safely.
4. Tests cover neutral vs provocative choices, personality differences, absence of an inn/event, advancement-weighted story reactions, child restrictions, relationship tiers, completed versus abandoned histories, cooldown/fallback selection and one-time effects.
5. No regressions to gameplay commands, server history/ownership, async pending, client reveal/acknowledgement, datapack compatibility or Fabric/NeoForge loading.
6. Common tests, both loader builds, `git diff --check` and manual in-game conversation review are reported separately. Never claim playtesting was run unless it was.

**Approval needed:** confirm the authored/contextual approach and whether typical subjects should be around 2–4 exchanges with optional later follow-ups. The implementation plan is a draft for review; no dialogue implementation is authorized yet.

