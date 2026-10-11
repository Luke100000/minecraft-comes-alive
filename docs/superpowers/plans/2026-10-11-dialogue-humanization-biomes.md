# Dialogue Humanization and Biome Conversations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan in order. Steps use `- [ ]` for tracking.

**Goal:** Make MCA dialogue coherent and personality-specific, and add datapack-extensible biome conversations through one DialogueEvent system.

**Architecture:** Keep `DialogueEvent` as the only dialogue engine. Extend its existing conditions and outcome selection, ship new Ask/ambient data, edit current locale packs, then validate. No old `Dialogues → Question → Answer → Result` engine, adapter, fallback, old-schema decoder or compatibility-only code.

**Tech stack:** Java 21, Minecraft 1.21.1, Fabric/NeoForge common module, Mojang codecs, Gson, JUnit, JSON datapacks and English translation assets.

**Spec:** `docs/superpowers/specs/2026-10-11-dialogue-humanization-biome-design.md`

## Guardrails and parallel ownership

- Work in the **existing** dialogue-event-overhaul worktree. Check `git status --short` before starting; preserve unrelated changes. No new worktree, push, merge, or UI redesign.
- This is **development-only**. Breaking the old single-`value` `mca:biome` JSON shape is approved. Replace every shipped occurrence directly with `values`; do not keep a backward-compatible parser. Keep default outcome priority `0` because live **new-engine** outcomes use weights without explicit priority.
- Preserve gameplay outcomes, server authority, choice/event identity, session/history/cooldown behavior, and actively referenced translation keys/format tokens. Deleting unreferenced translation keys is allowed after checking dynamic line pools and `DialogueType` resolution. No older-format compatibility requirement.
- Only the primary implementer edits Java, shipped event JSON, current locales and tests. Two independent agents may review **disjoint frozen locale-key shards**, submitting proposed edits and coverage reports, never changing shared files. Start their review once the inventory is frozen, while the primary handles Tasks 1–3; wait for their final results before integrating Task 4. Do not message agents merely for status.
- Prefer editing `DialogueCondition` directly. Add `DialogueBiomeSelector` only if isolating biome logic becomes demonstrably clearer; do not build a second registry, selection engine or generic review pipeline. Run focused tests for logic changes, then the integration/build checks at the end.

## Task 1 — Biome condition and outcome priority

**Files:** `common/src/main/java/net/conczin/mca/dialogue/DialogueCondition.java`, `DialogueEvent.java`, `DialogueEngine.java`; `common/src/test/java/net/conczin/mca/dialogue/DialogueEventParsingTest.java`, `DialogueSelectionTest.java`; existing `data/mca/dialogue_events/social/chat.json`.

**Contract:**

```json
{ "type": "mca:biome", "values": ["minecraft:plains", "#mca:forest_biomes"], "scope": "current" }
```

- `values` is a **required nonempty array**, even for one biome; selectors are exact IDs or `#namespace:tag`, matched by OR. Reject the old `value` field, duplicates, malformed identifiers, unknown fields and invalid scope. `scope` defaults to `current`; `home` samples the safely loaded home-village center, falls back to current when **no** home exists, and returns unavailable when a known home cannot be queried without chunk loads.
- Evaluate via active server biome holders/registry tags, not names or cached biome lists. Any match wins; absent tag/ID produces `UNAVAILABLE` if none matched; otherwise nonmatch is `NO_MATCH`. Ensure `mca:not` cannot invert `UNAVAILABLE` into a match. Preserve datapack reload behavior.
- Add `int priority` to `DialogueEvent.Outcome`, serialized with optional `priority: 0` default. Update its codec and **both** node-/choice-outcome raw JSON allowlists. In **both** `DialogueEngine` selection paths filter eligible outcomes to maximum priority before existing weighted selection; preserve the selected outcome's original index and reward/session behavior. Use exact `20` > category `10` > generic `0` in new biome data. Avoid an extra 4-argument constructor solely for legacy support unless a current new-engine caller requires it.

- [ ] Inspect current condition codecs, village resolution and loaded-chunk APIs; record any world-access constraint before implementation.
- [ ] Add focused failing parser/selector/selection regressions: one or multiple values; invalid shape; exact/tag/missing-tag and negation; home/no-home/unloaded-home; equal-tier weights; automatic **and** player-choice outcomes; unchanged weighting at implicit priority zero. Use a GameTest/runtime check for world/tag behavior not executable in the JUnit lane.
- [ ] Implement the smallest shared-code change, migrating the five shipped `social/chat.json` biome conditions to `values` as part of the cutover; do not retain the old decoder.
- [ ] Run `.\gradlew.bat :common:test --tests net.conczin.mca.dialogue.DialogueEventParsingTest --tests net.conczin.mca.dialogue.DialogueSelectionTest` and any new targeted matching test; inspect failures and rerun after fixes. Commit only this work package.

## Task 2 — Coherent local-area Ask topic and biome ambient dialogue

**Files:** `common/src/main/resources/data/mca/dialogue_events/social/chat.json`; new `social/local_area.json` and `ambient/biome_*.json`; new `common/src/main/resources/data/mca/tags/worldgen/biome/*.json`; `common/src/main/resources/assets/mca_dialogue/lang/en_us.json`; `McaDialogueEventResourcesTest.java`.

- [ ] Add a failing resource/route assertion that Chat's village answer follows the player's question and that an eligible `mca:social/local_area` Ask event resolves with a generic fallback. The event should expose natural choices about **here** (`scope: current`) and, where sensible, **home** (`scope: home`).
- [ ] Move scenery reactions out of Chat; write localized current-/home-area dialogue for the five existing biome themes: plains, savanna, taiga, snowy plains and desert. Write a few concise environment-category tags (snowy/forest/dry) whose vanilla members are confirmed against 1.21.1; support datapack-added modded biome members. Separate exact and tag outcomes so `priority` actually orders them.
- [ ] Ship a small set of **separately eligible** ambient biome resources (not one universal routing event). Match existing everyday ambient priority/weight, use deliberate seconds-based cooldown and scheduling-only history, and leave the always-eligible generic baseline available. Do not assert unrecorded repairs, raids or weather.
- [ ] Validate event parsing, translations, exact>tag>generic resolution, no-home fallback and baseline reachability with the existing resource/selection tests. Commit the event/tag/localization changes together.

## Task 3 — Repair Chat, Joke, Story, introductions and personality voice

**Files:** `common/src/main/resources/data/mca/dialogue_events/social/{first,chat,joke,story}.json` when necessary; `common/src/main/resources/assets/mca_dialogue/lang/en_us.json` and nine existing `mca_dialogue_<personality>/lang/en_us.json`; `McaDialogueEventResourcesTest.java`, `PersonalityDialogueLibraryTest.java`.

- [ ] Trace every reachable player question/choice to each randomized villager response and follow-up. Identify what the player actually said before revising any line; ensure introductions always lead to a matching question, Chat/Joke/Story have meaningful replies, and resume prompts remain **player-authored** when selected by the player.
- [ ] Verify current `DialogueType.applyFallback` and language pool behavior before creating personality variants. Humanize the ~47 new Chat/Joke/Story base keys and add personality-specific lines where the resolver can actually select them. Preserve justified odd humor, distinguish personality voices, avoid exaggerated or invented world facts, and keep adult-gated romantic paths safe.
- [ ] Test representative distinct replies, personality fallback, gender/age constraints, valid graph nodes, rewards/history and localization references. Inspect all changed conversation routes in context; run `McaDialogueEventResourcesTest` and `PersonalityDialogueLibraryTest`. Commit the focused content change.

## Task 4 — Two-agent English corpus review and safe integration

**Files:** existing base and nine personality `en_us.json` assets only; proposed-review/coverage reports under `docs/superpowers/audits/` as needed. No new production review framework.

- [ ] **Before Tasks 1–3 edit locales**, freeze a deterministic `(pack,key,text)` inventory in ignored task-local scratch, count entries (historically **5,605**), and identify keys owned by Task 3. Split the **remaining** keys into two nonoverlapping sorted shards. The main implementer reviews Task 3 keys; combined coverage must include every inventory entry.
- [ ] Dispatch two review agents once with their frozen shards and Humanizer skill. They mark each key `keep` or `rewrite`, give short reasons/replacement text for rewrites, check dialogue context and return standalone proposals. They must not edit shared locales, other resources or each other's reports. The primary executes Tasks 1–3 while they review.
- [ ] When both reports finish, check shard coverage, duplicates, missing entries, `/1` alternatives and formatting placeholders. Compare proposed text with the **current** value, since Tasks 2–3 may have edited base text; manually resolve overlaps. A short disposable Python/PowerShell comparison is sufficient—no committed inventory/apply CLI or accompanying generic test suite.
- [ ] Apply only justified edits; keep already-natural lines. Remove proven-unused English translation keys only after checking dynamic pools, fallback and event references; never delete a key just because the old branch originally introduced it. Record reviewed/kept/edited counts and unresolved issues. Commit reviewed locale changes and a concise audit report, not automatically generated rewrite noise.

## Task 5 — Documentation, verification and handoff

**Files:** primarily `docs/dialogue-authoring.md`, linked references in `docs/dialogue-datapack-guide.md` and `docs/dialogue-system-guide.md`; update existing dialogue tests only for uncovered behavior; report under `docs/superpowers/audits/`.

- [ ] Make `docs/dialogue-authoring.md` the authoritative JSON reference. Demonstrate `values: ["minecraft:plains", "#mca:forest_biomes"]`, `scope: current|home`, optional outcome `priority`, and exact>tag>generic data. Update/remove contradictory `value` examples in other guides and link them to the authoritative section instead of copying the whole reference.
- [ ] Run relevant focused common tests; then `.\gradlew.bat :common:test :fabric:build :neoforge:build --console=plain`. Check shipped events/translation lookups and the codebase for remaining single-`value` biome conditions or an accidental old-engine dependency. Use `git diff --check` and review every changed file.
- [ ] In a disposable 1.21.1 world, check one villager in each original biome family; one tagged/modded and one unmatched biome; home/no-home/unloaded-home behavior; Chat/Joke/Story questions and reactions; child/personality paths; ambient cooldown/variation; choices, rewards, pause/resume and visible text. State explicitly what was not run. The tests should cover actual behavior, not tautological implementation shape.
- [ ] Commit verified documentation/tests. Report results, known issues, exact commit(s) and any unverified in-game checks. **Do not implement any old compatibility layer or push/merge without a new instruction.**

## Execution

**This document is planning only.** After approval, use `superpowers:executing-plans`, `minecraft`, `java-fundamentals`, `java-concurrency`, `humanizer`, and the existing parallel-agent tool where available. Freeze the review inventory **first**, then overlap its read-only agent reviews with the primary implementation. No unrelated worktree changes.
