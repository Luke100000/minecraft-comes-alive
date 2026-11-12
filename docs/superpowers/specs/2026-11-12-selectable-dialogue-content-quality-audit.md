# Selectable dialogue quality: audit and proposed revision spec

**Date:** 2026-11-12
**Status:** Initial static editorial audit completed; context-aware design added on 2026-11-12. Proposal **not approved for implementation**.
**Scope:** MCA 1.21.1, the **26 Ask** and **19 Highlighted** Talk events in the current worktree, plus a proposed pair of profession-specific selectable conversations. No changes to event JSON, translations, Java or tests were made as part of this audit.

## Decision requested

Improve the value and **contextual accuracy** of existing selectable conversations before authoring more. Keep the server-side dialogue engine, memory, story-history, action and presentation systems. Revise conversations where the villager assumes an unverified role, the response fails to answer the selected topic, an ending feels abrupt, or characters sound interchangeable. Short greetings and service actions can stay short.

The user's intent has two distinct parts: **Prison/Infirmary conversations continue to depend only on being inside their building** ("just keep being in building for now"), while **Guards and Outlaws get distinct profession-specific dialogue branches available anywhere they can Talk** ("Anywhere you meet them"). This means meaningful **authored conversations and player replies**, not simply permission to open the Talk menu, and not an assertion that an Outlaw is incarcerated or a Guard works at this prison. A new prisoner/patient role system is out of scope.

This is a **focused companion** to `2026-11-11-natural-dialogue-conversations-design.md`, which already proposes authored contextual branches and a broader audit of all English packs. That older design's **87-event** count is outdated: the current worktree has **89 events** (44 ambient, 26 Ask, 19 Highlighted). It has not been superseded or independently approved by this document. Existing story-memory behaviour is separately owned by `2026-11-11-story-memory-design.md`.

## What was inspected, and limits

- Enumerated the nodes, player choices, outcome destinations and **base English visible lines** for all **45** in-scope JSON events from the current `mca_dialogue` and `mca` locale files. Sampled relevant numbered text pools and command/condition schemas; this was **not** an exhaustive action-by-action code or runtime review.
- Found **11/26 Ask** events with no player-reply choice and **0/19 Highlighted** events without choices. This is a structure count, **not** a defect count. Several no-choice Ask entries are legitimate actions or concise answers.
- Identified all **54 occurrences** of unresolved direct-key lookups in the extraction as references to valid numbered English pools (`key/1` through `key/N`). These are **not missing translations**. A full editorial reading of every pool entry and every personality override has **not** been completed here.
- `docs/dialogue-review-inventory.md` is a Nov 11 snapshot of **87** events; its old graph/locale checks do not constitute a completed 89-event editorial or in-game acceptance test.
- No game client, server, GameTests, unit tests, or multiplayer session was run. Findings are static editorial observations, with runtime presentation explicitly marked for later verification.

## Strongest findings

**P1 context correctness, omitted from the initial triage:** `location/prison.json` and `location/infirmary.json` require only `mca:in_building`. This checks physical presence, not incarceration, illness or employment. Their three-line speeches imply being detained or receiving treatment, so ordinary visitors or workers can say something untrue. This is a confirmed content-eligibility mismatch, not a demonstrated engine/runtime defect. Treat both as P1.

1. **Hire reuses an acceptance-sounding line before payment.** `gameplay/hire.json` displays `dialogue.hire` at both the initial offer and the hired ending. Numbered English variants include “Alright, just tell me when you need help. I will be around for the paid time.” That line assumes the contract is agreed before the player selects a duration. Give the *offer* its own accurate prompt and reserve acceptance wording for success, preserving all command and emerald checks.
2. **Rumors often supplies little interaction.** `social/rumors.json` auto-selects a location clue, one lore line or a short failure. There is no player question or reply in the event. The location route has a real structure resolver and must retain it; the old lore pool includes specific unusual names and claims that deserve a **canon/editorial** check, not automatic removal.
3. **Some explicitly selected topics are one-way monologues.** `location/infirmary.json`, `location/prison.json` and `personal/lactose_intolerance.json` each present three passages and finish without a reply. The building events additionally make unsupported role assumptions; repair those before polishing their emotional tone. Keep the lactose joke, and add a relevant **optional** reply only where the selected subject earns one.
4. **Four follow-ups repeat a static update and stop.** `gloomy_reflection_followup` (four lines) and the anxious/greedy/playful personality follow-ups (three lines each) have no player reply, although their prerequisites correctly depend on the player's completed earlier choice. Their repeated presentation after a cooldown risks sounding like no time has passed. Give them an earned response or tighten repetition policy; preserve their already working history gates.
5. **The 13 `personal/personality_*.json` highlighted stories share one strong template.** Typically a context line, four-line personal dilemma, two short advice options, and two-line agreement endings. The voices differ, but repeatedly being asked for advice makes many personalities feel similar. Favor distinctive scenes, disagreement and different conversational shapes over extra passages. In particular, the friendly and peaceful stories both concern resolving others' disagreements.
6. **A serious highlighted story sometimes stops abruptly.** In `personal/mourning_depressed.json`, the player can choose to listen, but the resulting endings contain only one line. A slightly more specific, optional continuation on the *listening* route may earn its featured status. Preserve the blunt rejection and space-giving exits as short where appropriate.
7. **Some labels are vague.** `personal/cured_zombies.json` is an eligible, well-developed discussion about cures, but its Ask prompt is “Something on your mind?” The three personality follow-ups share the generic “Ask about our earlier advice”. Offer text should tell the player what topic they are selecting.
8. **Potential confirm/cancel echo, to verify in the client.** `relationship/adopt.json`, `relationship/divorce.json`, `relationship/procreate.json` reuse “Confirm”/“Cancel” choice keys at terminal nodes with `silent: true`. Whether this is awkward in the live UI requires inspection. Do not modify command outcomes or treat this as a confirmed rendering defect.

## P1: Context-aware building conversations

**What MCA actually models:** `data/mca/building_types/prison.json` defines a recognised prison building with **8 iron bars, 1 iron door and 1 bed** required. The villager professions `mca:outlaw` and `mca:guard` are registered in `ProfessionsMCA`. A mayor's `execute` interaction changes profession to Outlaw; `pardon` clears it. Outlaws are flagged hostile. No prison-cell assignment, active inmate confinement, prisoner status or guard-posting system was found in the inspected code. A prison therefore provides a recognised building context and existing Outlaw/Guard story opportunities, **not evidence of an operational jail simulation**. The actual experience of talking to a hostile Outlaw still needs a client check.

**Existing conditions:** `mca:in_building` checks a villager's physical presence inside a typed village building. `mca:profession` already checks real registered professions (including `mca:guard`, used in current events; `mca:outlaw` is registered). `mca:building_assignment` only detects a job/home site in **some** building of that type in the villager's village, not their work in this exact room; it does not identify a prisoner, guard post, clinician or patient.

**Proof-of-context rule for all revised conversations:** Use `mca:profession` for the villager's actual profession; `mca:in_building` for current physical presence; `mca:building_assignment` for a recorded home (`source: "home"`) or job site (`source: "workplace"`) within a matching building; `mca:relationship` / `mca:family` for actual player-villager relationships; and `mca:event_choice` only for a **completed** earlier player choice. Match the line's factual claim to what its condition truly proves. A workplace assignment does **not** prove the villager is on duty or physically there; a relationship condition does not prove an invented shared experience. When the fact is absent, inapplicable or unavailable, use a genuine neutral response or omit the claim. Do not introduce a context-dependent line without an eligible neutral path. Existing condition-specific stories and generic fallbacks should be retained unless their actual path is demonstrably incoherent.

**Normal Talk reachability (separate from authored event selection):** `VillagerEntityMCA.interactAt` and `EntityCommandHandler.interactAt` do **not** reject `mca:guard` or `mca:outlaw` solely by profession. They reject interaction while `isConversationDangerous()`; that predicate includes active panic, an `ATTACK_TARGET` memory and an ignited-creeper threat. An Outlaw is marked hostile and may therefore be actively attacking, so **the ability to open Talk with a calm Outlaw is suggested by the code but not yet verified in-game**. Preserve protection against conversations while actively in combat for the first pass; do not silently bypass panic/attack-target checks. Whether active-combat parley is desired is an open design choice requiring separate approval.

**Location approach selected:** Preserve existing `mca:in_building` eligibility and write location-neutral dialogue for **any** villager present, with a useful optional reply. Guard/Outlaw-specific dialogue is a **separate, anywhere-available feature** described below, not a branch of `location/prison`. A full prisoner/medical-role simulation needs new gameplay mechanics and is a separate, unapproved feature.

**Proposed event graphs:**

1. Keep `location/prison` and `location/infirmary` as Ask events, respectively gated only by `mca:in_building: prison` and `mca:in_building: infirmary`. Preserve their event IDs, topic, 5-second cooldown, priority and scheduling history. **Do not introduce profession, workplace, prisoner or visitor conditions or conditional role-routing outcomes.**
2. Rewrite the existing unbranched prison monologue as a truthful observation about the prison building. The villager may discuss iron bars, doors, quietness or their own reaction, but must not claim to be detained, employed there or guarding prisoners.
3. Rewrite the existing infirmary monologue as a truthful observation about the infirmary. The villager must not claim to be a patient, nurse, doctor or local worker, nor invent treatments or sick villagers.
4. Add one meaningful optional player question/reaction with a responsive villager line, plus a polite exit. Both paths terminate cleanly, without extra heart penalties or invented memories. No engine changes or new roles.

Illustrative wording (**proposal**, not approved final copy):

| Where | Villager opening | Player reply options | Expected next line |
| --- | --- | --- | --- |
| Prison, any villager | "These bars make it feel different from the rest of the village." | "What do you think of this place?" / "See you around." | An observation about the room, or a short goodbye; no claim to be imprisoned or to work here. |
| Infirmary, any villager | "It's quieter here than outside." | "Is that a good thing?" / "See you around." | A personal reaction, without claiming illness, patients, treatments or employment. |

**Acceptance:** Guard and Outlaw villagers must not be refused ordinary Talk **merely because of their profession**. Verify a calm Guard and a calm Outlaw can open the ordinary interaction screen, select Talk and finish a safe conversation even **outside** a prison; record actual behaviour if the Outlaw is already in combat. Inside a recognised prison or infirmary, any villager with a safely open Talk menu receives the corresponding role-neutral event; outside, those location-specific events are not offered. Do **not** bypass the existing active-danger protections or add combat AI/interaction changes without separate design approval. Both location events have a meaningful optional exchange and a clean exit; neither invents a role. Preserve existing building/cooldown behaviour.

## P1b: Guard and Outlaw conversations anywhere

**Goal:** Villagers with the actual `mca:guard` or `mca:outlaw` profession should offer different, worthwhile conversations **wherever the player meets them**. These are not prison-only conversations and do not change hostile AI.

**Approaches:** Extend `social/chat` with profession-specific reactions (less content duplication, but risks disturbing its existing day/village/news choices, weighted hearts and follow-ups), or add **two small profession-gated Ask topics** (more discoverable, independently testable, keeps existing Chat intact). **Recommend two focused Ask events** under `social/`, provisionally `social/guard_duties` and `social/outlaw_reputation`. The same event-node system, `mca:profession` condition and English locale already support this; `social/joke` already uses `mca:profession = mca:guard`. No extra engine or role state.

**Proposed content contract:**

1. Both events use `trigger: "talk"`, `presentation.mode: "ask"`, clearly named prompts, existing-style repeat cooldown and `mca:profession` event requirements: Guard requires `value: "mca:guard"`, Outlaw requires `value: "mca:outlaw"`. **No `mca:in_building` gate or home/job-site condition.** They appear only for the matching profession in any area where normal Talk works.
2. Each has a small, **acyclic** branch graph: a relevant opening, at least two **different topic-specific player replies/questions** plus a polite exit, and an answer that specifically addresses each selected reply. Do not add empty acknowledgments purely to increase line count. Avoid new heart penalties/rewards or memories unless separately justified.
3. Guard themes: day-to-day safety, how it feels to protect people, making choices under pressure. Do not presume this individual guard is posted to any particular prison, saw a specific attack, or made an arrest.
4. Outlaw themes: the consequences of being declared an outlaw, mistrust, whether reputation can change. Do not presume prison custody, conviction, a crime or personal backstory. An Outlaw is marked hostile; **calm Talk access is a validation risk**, not permission to suppress combat AI or bypass `isConversationDangerous()`.
5. Keep existing `social/chat` and `social/joke` branches and weighted outcomes intact. These new topics are an **addition**, not a rewrite of existing story memory or a replacement for location events. Existing 89-event / 26-Ask counts are the **baseline**; two additions would raise the total to **91 events / 28 Ask** if implemented and accepted.

Illustrative paths (**not final approved English copy**):

| Villager | Opening | Player replies | Villager answer must address |
| --- | --- | --- | --- |
| Guard | "Keeping people safe means noticing trouble before it grows." | "What do you watch for?" / "Does that get tiring?" / "Stay safe." | Warning signs vs personal strain vs short goodbye. |
| Outlaw | "Being called an outlaw changes how people look at you." | "Can their minds change?" / "How does that feel?" / "I'll go." | Whether trust might return vs personal reaction vs short goodbye. |

**Acceptance:** Both prompts appear for their matching profession **inside or outside** a prison, and not for regular villagers or the other profession; all player replies receive distinct, coherent responses and can finish without unexpected effects. In separate tests, `location/prison` and `location/infirmary` remain strictly `mca:in_building` gated and role-neutral. Test a **calm** Outlaw's Talk menu in-game and report any existing hostile-interaction barrier; changing combat behaviour would need a separate design and approval.

## Event-by-event editorial triage

The labels below mean **Keep** (protect current behaviour), **Polish** (targeted copy/flow), or **Revise** (specific quality issue worth drafting). None authorizes a blind rewrite.

### Ask (26)

| Event path under `dialogue_events/` | Triage | Proposed scope |
| --- | --- | --- |
| `gameplay/divorce_papers` | Keep | Brief cleric service outcome is appropriate; verify paper handoff text against actual command. |
| `gameplay/hire` | **Revise P1** | Distinguish pre-contract offer from successful hire; retain durations, emerald checks and commands. |
| `gameplay/stay` | Keep | Short success/failure/repeat/no-space results are appropriate for the action. |
| `location/infirmary` | **Revise P1** | Remove assumed patient identity; one truthful location-neutral dialogue with a useful optional reply. No invented nurse/doctor or remedies. |
| `location/prison` | **Revise P1** | Remove assumed prisoner identity; one truthful location-neutral dialogue and useful optional reply. Outlaw/Guard conversations are separate anywhere-available Ask topics, not branches of this event. |
| `personal/cured_zombies_followup` | Keep | Remembers the exact earlier choice and offers two different answers and endings. |
| `personal/cured_zombies` | Polish P1 | Meaningful three-way discussion; make vague menu prompt topic-specific. |
| `personal/gloomy_reflection_followup` | **Revise P1** | Four-line update with no reply; make it an earned callback, avoid identical frequent replay. |
| `personal/lactose_intolerance` | Polish P2 | Keep the food joke; optional relevant reply if useful, otherwise leave short. |
| `personal/personality_anxious_followup` | **Revise P1** | Replace generic Ask label and allow a natural response to the remembered plan. |
| `personal/personality_greedy_followup` | **Revise P1** | Name the earlier sharing advice; avoid repeating the same static resolution. |
| `personal/personality_playful_followup` | **Revise P1** | Reflect the earlier apology choice; allow acknowledgment without forcing a new story. |
| `relationship/adopt` | Keep / verify | Preserve child/orphan eligibility and once-only command; client-check silent confirmation echo. |
| `relationship/apologize` | Keep | Explicit apology and mood-dependent result fit the action; check result-pool tone. |
| `relationship/divorce` | Keep / verify | Preserve spouse restriction, command and paper advice; check silent confirmation echo. |
| `relationship/flirt` | Polish P2 | Keep consent/refusal and heart/slap branches; examine random voice-pool relevance. |
| `relationship/hug` | Polish P2 | Generic opener (“Is there something on your mind?”) could answer the actual request more directly. |
| `relationship/kiss` | Keep | An explicit request with meaningful acceptance/refusal route; preserve restrictions. |
| `relationship/procreate_engaged` | Keep | A short relationship-boundary response is intentional and coherent. |
| `relationship/procreate` | Keep / verify | Preserve consent/confirmation semantics, age and spouse gating; client-check echo. |
| `social/chat` | Keep | Day/village/news branches already have meaningful player follow-ups; editorially sample all numbered/personality text pools. |
| `social/joke` | Keep | Three actual jokes with distinct follow-ups; retain optional failed reaction and personality voice. |
| `social/local_area` | Polish P2 | Already branches on actual current/home biome with player replies; improve only weak generic fallbacks, no invented places. |
| `social/rock_paper_scissor` | Keep | Short game-specific outcome is appropriate. |
| `social/rumors` | **Revise P1** | Richer player-led question or reaction; keep real structure coordinates, failed lookup and intentional lore. |
| `social/story` | Keep | Extensive reply branches and existing topic memories/skepticism rules; don't add a second memory system. |

### Highlighted (19)

| Event path under `dialogue_events/` | Triage | Proposed scope |
| --- | --- | --- |
| `personal/cured_identity` | Keep | Context-specific survivor perspective; three distinct replies and endings. |
| `personal/gloomy_reflection` | Keep | Specific emotional arc and completed-choice follow-up; avoid changing its choice IDs. |
| `personal/mourning_depressed` | **Revise P1** | Optional listening branch deserves more than a single reaction; preserve space and rejection exits. |
| `personal/mourning` | Keep | Strong village detail (well, missing storyteller) and two respectful endings. |
| `personal/personality_anxious` | Polish P2 | Concrete preparation fear; retain `make_plan` history dependency, vary advice phrasing. |
| `personal/personality_crabby` | Keep | Distinctive reluctance to admit missing a visitor; credible voice and endings. |
| `personal/personality_extroverted` | Polish P2 | Give gathering dilemma a sharper village scene and less generic coaching. |
| `personal/personality_flirty` | Polish P2 | Sincere-compliment topic works; add personal specificity if it remains too abstract. |
| `personal/personality_friendly` | **Revise P2** | Mediation topic overlaps peaceful; distinguish friendliness from handling injustice. |
| `personal/personality_greedy` | Keep | Specific coin-pouch dilemma, good contrasting replies; preserve `share` follow-up. |
| `personal/personality_introverted` | Polish P2 | Boundaries story suits personality; reduce lecture-like rhythm, preserve choice distinction. |
| `personal/personality_odd` | Keep | Painted-stone route idea is odd and specific, unlike a generic advice story. |
| `personal/personality_peaceful` | **Revise P2** | Differentiate from friendly: boundaries/fairness, not another generic mediation request. |
| `personal/personality_playful` | Keep | Bad-timing joke has believable stakes; preserve `apologize` follow-up choice. |
| `personal/personality_relaxed` | Polish P2 | Calm-versus-avoidance dilemma can use a grounded work example. |
| `personal/personality_sensitive` | Polish P2 | Specific voice is present; a concrete village remark would deepen the theme. |
| `personal/personality_upbeat` | Polish P2 | Avoid another interchangeable self-help exchange; anchor disappointment in a real circumstance. |
| `personal/revived` | Keep | Recent resurrection matters to identity and family; both replies are responsive. |
| `social/first` | Keep / verify | Substantial exploring, settling and inn branches; preserve pending missing-inn design and all history rules. |

## Proposed approach and delivery order

**Recommended:** Improve authored content in-place using existing event JSON, translation variants, conditions, choices and outcomes. A text-only rewrite cannot fix misleading roles or one-way interactions reliably; a new runtime dialogue engine or generated dialogue is unnecessary. Keep event IDs, existing `choice.id` references, cooldown/history semantics and the service/relationship commands stable unless a reviewed story contract explicitly changes.

**P1a: Repair contextual truth first.** Start with a single role-neutral Prison conversation and a single role-neutral Infirmary conversation. Test physical presence eligibility, relevant player replies and truthful endings. Draft each complete player/villager path **before** editing resources. Do not add Outlaw/Guard role routing in this batch.

**P1b: Improve the remaining weak selectable conversations.** Fix Hire's offer text, topic-specific Ask labels, Rumors, the four static personal follow-ups and mourning-depressed listening. A one-line outcome is fine when it actually addresses the player.

**P1b (independent addition): Profession-specific topics.** Add the proposed Guard and Outlaw Ask conversations as a separate reviewed batch, eligible by profession **anywhere**, not by prison/infirmary location. Preserve all existing general Chat/Joke branches and combat safety; confirm the two new Ask prompts, response paths and calm Outlaw reachability before considering the batch finished.

**P2: Distinguish characters and replay value.** Review the 13 structurally similar personality stories together; focus first on Friendly versus Peaceful and the abstract Extroverted/Upbeat stories. Polish Lactose intolerance, Local Area and Hug only where full-path reading shows a real gap. Keep strong stories intact.

**P3: Complete editorial and runtime acceptance.** Rebaseline the stale 87-event inventory to the current 89, read **every numbered /1..N variant** and personality pack variant for changed keys, and perform an actual client playthrough of every changed outcome. The existing `testpacks/dialogue-showcase` is useful for engine mechanics but does not force every shipped event. A deterministic event selector would be a **separate proposal** if real-event testing proves awkward.

## Review contract for future implementation

- Every selected conversation has a point: an answer, a distinct reaction, a remembered continuation or a real service. Adding dialogue length alone is not success.
- A player's reply is acknowledged by the very next villager line and can lead to a different view, mood or ending. One-line refusals, farewells and service results remain valid.
- For any profession, location, assignment, relationship or completed-history claim, record the exact existing condition that proves it and check the neutral branch when that condition does not match. No context fact may be inferred solely from the presence of a building or an eligible Ask topic.
- Any new story branch is acyclic and preserves all valid existing paths. Memory references such as `mca:event_choice` must still match completed outcomes; abandoned stories must not unlock callbacks.
- No unsupported claims about village buildings, prisoner/patient status, **Outlaw imprisonment, Guard prison posting**, remembered adventures, personal history or live game state. Real registered Outlaw and Guard professions are valid context; structure tips use real resolver results and coordinates, never an imaginary substitute for a failed search.
- Preserve action authorisation, command outcome, family/age/relationship conditions, hearts, slap, one-time effect timing, cooldown/priority/weighted selection, localization keys and positional placeholders.
- Use `docs/dialogue-conversation-writing.md` and the existing Humanizer guidance as editorial lenses, while keeping character voices and deliberate Minecraft humour.

## Verification checklist (future, **not executed in this audit**)

1. For each edited event: read every reachable branch aloud, including the selected player line, random outcome pools, fallback conditions and all affected English personality variants.
2. Run existing resource/locale and behaviour tests, including `McaDialogueEventResourcesTest`, `DialogueEventParsingTest`, `PersonalityDialogueLibraryTest`, `DialogueSelectionTest`, and relevant `DialogueShowcasePackTest` / NeoForge `DialogueEngineGameTests`. Add focused assertions only for a real regression the changes could cause.
3. In a disposable world, compare ordinary villagers inside and outside the prison/infirmary, checking location eligibility, replies and short exits. **Explicitly check the Guard-only and Outlaw-only Ask topics appear for the corresponding profession both inside and outside the prison**, and that each response path works. Separately record what happens if the Outlaw is attacking; do not weaken active-combat safety checks just to make a test pass. No line may imply imprisonment, prison employment, illness or an invented profession. Check both loaders' Talk UI, scrolling/Finish, cooldown, rewards, story-memory unlocks, command success/failure and no unintended heart changes.
4. Compare edits against the dirty worktree before touching files; preserve concurrent work. Run `git diff --check`; report build, tests and client-play evidence separately. Never claim a runtime pass based only on parsing.

**Approval gate:** Confirm this P1/P2 shortlist and the preference for **selective, responsive rewrites** rather than a blanket increase in line count. This audit/spec does **not** authorize or carry out those rewrites.
