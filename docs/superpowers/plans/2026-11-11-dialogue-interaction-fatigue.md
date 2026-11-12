# Dialogue Interaction Fatigue Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans after the proposal is approved. Track steps with checkboxes (`- [ ]`).

**Status:** Approved for implementation on 2026-11-11 with story-memory Option A.
**Goal:** Restore fatigue-sensitive story reactions through existing player/villager memory and one datapack outcome field.
**Architecture:** Add optional numeric `fatigue` to `Outcome` and pass the current pair-fatigue snapshot to existing outcome selection. Reuse requirement/priority filtering and `weightedPick`, then annotate existing skeptical story entries through one coordinated JSON edit.
**Tech Stack:** MCA 1.21.1, Java 21, Minecraft Codec, JUnit, existing NeoForge GameTests, Fabric and NeoForge.
**Spec:** [interaction fatigue design](../specs/2026-11-11-dialogue-interaction-fatigue-design.md).

## Global constraints

- Use the existing dirty `dev/1.21.1-dialogue-system-b` worktree. Inspect current diffs and preserve concurrent edits. No WSL, new worktrees, staging, commit or push without separate instruction.
- No new fatigue NBT, Java memory hooks, session counters, packet, weight cache, cooldown, or persistence behavior.
- JSON rate is a finite nonnegative double defaulting to 0.0. Compute `legacyPoints = (int)(max(0, stored) * positiveFiniteOrZero(global))`, `bonus = (int)(legacyPoints * rate)`, and `effectiveWeight = base + bonus`.
- Keep existing eligibility, top-priority filtering, one RNG draw, pending/commit timing, `end:true`, stale token protection, slap completion, story paths and independent story-memory behavior.
- Integrate `social/story.json` with a single owner after reviewing the concurrent story-memory state.

## Files and reasons for each necessary mechanism

| Future file | Purpose |
| --- | --- |
| `common/src/main/java/net/conczin/mca/dialogue/DialogueEvent.java` | Outcome field/codec and both strict allowlists; static weight alone has no fatigue input |
| `common/src/main/java/net/conczin/mca/dialogue/DialogueEngine.java` | Server reads per-pair fatigue when selecting; existing picker accepts a computed weight |
| `common/src/main/resources/data/mca/dialogue_events/social/story.json` | Opt only five original skeptical results into fatigue |
| `common/src/test/java/net/conczin/mca/dialogue/DialogueEventParsingTest.java` | Ensure old packs and new scalar validation |
| `common/src/test/java/net/conczin/mca/dialogue/DialogueSelectionTest.java` | Verify probability, priorities, hidden routes and single-roll behavior |
| `common/src/test/java/net/conczin/mca/dialogue/McaDialogueEventResourcesTest.java` | Catch missing or double-annotated story outcomes during integration |

Retain the existing five-argument `Outcome` constructor for current tests/callers, delegating to a six-argument record with rate 0.0. Preserve the existing two-argument `pickOutcome` and four-argument static `resolveAutomaticTransitions` entry points as no-fatigue delegates where called by current tests. Introduce only the corresponding fatigue-aware overloads needed by the engine. No nested `Fatigue` type, custom normalized picker or additional per-rate/global safety caps.

## Review focus

1. Unmarked existing packs preserve weights and RNG sequence (Tasks 1–2).
2. Negative/corrupt fatigue, bad runtime config and extreme positive authored rate remain safe via finite validation and int saturation (Task 2).
3. High fatigue does not bypass requirement or priority gating (Task 2).
4. Automatic routing uses the same pair value and still queues effects without committing (Task 3).
5. Four memory-conditioned outcomes remain unmarked (Task 4).

---

### Task 1 — Decode one numeric field

**Files:** `DialogueEvent.java` and `DialogueEventParsingTest.java` at the paths above.

- [ ] **RED:** Add `fatigueIsOptionalScalarOnChoiceAndNodeOutcomes`. Decode rates 1.0 and 0.5 in a choice and a hidden node, and assert an unmarked outcome and existing five-argument constructor yield 0.0.
- [ ] **RED:** Add `invalidFatigueIsRejected`. Reject negative, NaN/Infinity, string/object and unknown keys; accept explicit 0.0.
- [ ] **Verify RED:** Run `.\gradlew.bat :common:test --tests "net.conczin.mca.dialogue.DialogueEventParsingTest" --console=plain`. New valid parsing case must fail with the old schema.
- [ ] **GREEN:** Add primitive `double fatigue` and `Codec.DOUBLE.optionalFieldOf("fatigue", 0.0)` to `Outcome`, extend both strict allowlists, reject nonfinite/negative values in validation, and add the five-argument compatibility constructor.
- [ ] **Verify GREEN:** Rerun that class and inspect its scoped diff.

### Task 2 — Apply bonus through the existing picker

**Files:** `DialogueEngine.java` and `DialogueSelectionTest.java`.

- [ ] **RED:** Add `fatigueChangesMarkedOutcomeWeight` using unmarked success weight 4 and marked skepticism weight 2. At fatigue 5/config 1/rate 1, independently calculate one seeded draw across 4:7. At zero fatigue/rate/config, assert 4:2 and the prior RNG result.
- [ ] **RED:** Add `fatigueRespectsEligibilityAndPriority` with ineligible and lower-priority marked candidates, negative pair fatigue, bad global config and extreme rate. Check exclusion and finite effective weights from int saturation.
- [ ] **Verify RED:** Run `.\gradlew.bat :common:test --tests "net.conczin.mca.dialogue.DialogueSelectionTest" --console=plain`; the new fatigue assertions must fail.
- [ ] **GREEN:** Extend `pickOutcome` to accept `int pairFatigue` and `float multiplier`. Apply the spec's two casts within the existing top-priority draw and pass `outcome -> outcome.weight() + bonus` to `weightedPick`. Preserve its existing one-roll algorithm and the original overload for callers.
- [ ] **Verify GREEN:** Rerun `DialogueSelectionTest` and its original seeded cases.

### Task 3 — Wire real server context into both resolution paths

**Files:** `DialogueEngine.java` and `DialogueSelectionTest.java`.

- [ ] **RED:** Extend the automatic-route test with fatigue 5, baseline 4:2 and a marked skeptical route. Check the selected next node and queued effects against a deterministic 4:7 draw, with no early heart/history mutation.
- [ ] **Verify RED:** Rerun `DialogueSelectionTest` and confirm the old static routing behavior fails this scenario.
- [ ] **GREEN:** In `choose` and the private `resolveAutomaticTransitions(session, context)` wrapper, read `context.villager().getVillagerBrain().getMemoriesForPlayer(context.player()).getInteractionFatigue()` and current `Config.interactionChanceFatigue` at resolution. Pass them to the fatigue-aware picker and route selector. Preserve the original static route signature for tests, direct choices, index tracking, and deferred commits.
- [ ] **Verify GREEN:** Rerun `DialogueSelectionTest`, `DialogueSessionTest` and `DialogueEventParsingTest`. Use an existing NeoForge dialogue fixture for one real server player/villager resolved story when available; report a missing runtime seam check explicitly instead of building a broad persistence/decay suite.

### Task 4 — Update shipped story data and validate integration

**Files:** `social/story.json` and `McaDialogueEventResourcesTest.java` at the full paths above. Coordinate with [story-memory plan](2026-11-11-story-memory.md).

- [ ] **RED:** Add `storyFatigueMarksOnlyBaselineSkepticism`. Parse shipped JSON and assert one rate-1.0 original skeptical result for each of five choices and no marked success or memory-conditional extra failure. When story memory is integrated, check the single exploring memory and other three topic memories.
- [ ] **Verify RED:** Run `.\gradlew.bat :common:test --tests "net.conczin.mca.dialogue.McaDialogueEventResourcesTest" --console=plain` and observe missing metadata.
- [ ] **GREEN:** After checking live diffs and assigning the shared editor, add `"fatigue": 1.0` only to the five existing baseline skeptical results; preserve all other weights, requirements, actions, destinations and memory outcomes.
- [ ] **Verify GREEN:** Rerun `McaDialogueEventResourcesTest` and `NaturalDialogueContentTest`, then `.\gradlew.bat :common:test :fabric:build :neoforge:build --console=plain`. Review a scoped diff and report actual GameTest/client/dedicated-server coverage separately.

## Review gate

Apply accepted decisions D1–D3 in the [spec](../specs/2026-11-11-dialogue-interaction-fatigue-design.md). Reuse existing `/reload` generation invalidation and fatigue/NBT ownership; test newly authored rates on a fresh valid session during runtime verification if possible. Author-guide edits require separate scope. Implementation is authorized. Staging and push remain outside scope.
