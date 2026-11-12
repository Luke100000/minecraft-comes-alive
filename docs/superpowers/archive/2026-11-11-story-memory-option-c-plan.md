# Remembered Stories Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans. Track each step with its checkbox.

**Goal:** Restore remembered-story skepticism per player and villager using existing dialogue-event JSON.

**Architecture:** Implement chosen approach C: three short/medium/long exploring memories each contributing +1 to skeptical outcome weight, plus one long-memory skeptical outcome for each Nether, Ender Dragon and Wither story. Reuse existing outcome evaluation, deferred commit and persisted villager memory; introduce no runtime Java pathway.

**Tech Stack:** Minecraft 1.21.1 MCA shared resources, Gson-backed `DialogueEvent` decoder, existing JUnit content tests.

**Spec:** `docs/superpowers/specs/2026-11-11-story-memory-design.md` (requires review before implementation).

## Global Constraints

- Use the current checkout. Do not create a checkout/worktree, change unrelated dirty files, or push.
- Modify only the story event JSON and `NaturalDialogueContentTest.java` as needed.
- Keep all five story choices ungated, and preserve each original outcome's base weight, requirement, hearts, destination and dialogue follow-up.
- Exploring uses three player-scoped memories: `story.exploring.short` (8,000 ticks), `story.exploring.medium` (16,000 ticks), `story.exploring` (24,000 ticks). Each contributes +1 skeptical weight; maximum +3.
- Nether / Ender Dragon / Wither use `story.nether`, `story.enderdragon`, `story.wither` (each 2,400,000 ticks), each contributing a single +10 skeptical outcome.
- `LongTermMemory.remember` adds time to an existing expiry: `max(now, oldExpiry) + duration`. A repeated telling does not necessarily reset expiry; all three exploring memories accumulate independently. Preserve this ownership.
- Do not touch interaction fatigue or Java engine code. Tests must audit many outcomes together, not replicate trivial getter behavior.

## File Responsibilities

- **Modify:** `common/src/main/resources/data/mca/dialogue_events/social/story.json` — three timed exploring memories and three tier-gated skeptical outcomes; one timed memory and extra skeptical outcome for each of the three major stories.
- **Modify:** `common/src/test/java/net/conczin/mca/dialogue/NaturalDialogueContentTest.java` — event-level mass audit of action coverage, tiered eligibility and advancement independence.
- **Inspect only:** `DialogueAction.java`, `DialogueCondition.java`, `DialogueEngine.java`, `LongTermMemory.java` — established parsing, predicates, deferred effects and expiry. A discovered runtime defect requires revisiting this spec.

## Review Focus

1. An exploring success or conditional skeptical result writes only one or two of the three tier memories.
2. Exploring skepticism stays at +3 after some memories expire, or exceeds +3 on repeated telling.
3. Another player's or another topic's memory activates skepticism, or advancement success bonuses lose weight.
4. Extra skeptical routes use wrong hearts, destination or expiry; repeated memory incorrectly resets rather than extends expiry.
5. Generic stories, abandoned conversations or fully expired memories accidentally incur the penalty.

## Task 1: Configure story memory and audit all outcome variants

**Files:** Modify `common/src/main/resources/data/mca/dialogue_events/social/story.json`; test in `common/src/test/java/net/conczin/mca/dialogue/NaturalDialogueContentTest.java`.

**Interfaces consumed:** `DialogueEvent.decode`, `DialogueEngine.eligibleOutcomes(Choice, Predicate<DialogueCondition>)`, `DialogueEngine.choiceEligible`, `DialogueAction.Remember`, `DialogueAction.Hearts` and `DialogueCondition.Defined`.

**Interfaces produced:** Datapack behavior only, no new public Java interface.

- [ ] **Step 1: Write a failing mass-audit test.** Add `storyMemoryRefreshesOnEveryOutcomeAndAddsOnlyRepeatSkepticism` to `NaturalDialogueContentTest`, loading the actual `story` resource. For every exploring outcome assert exactly these three remember actions: `Remember("story.exploring.short", true, OptionalLong.of(8000))`, `Remember("story.exploring.medium", true, OptionalLong.of(16000))` and `Remember("story.exploring", true, OptionalLong.of(24000))`. Check precisely three extra exploring skeptical outcomes, each requiring its own matching `mca:memory`, weight 1, hearts -4 and `exploring_fail` destination, and writing all three memories. Each Nether/Dragon/Wither outcome has exactly one matching remember action; each topic gains only one extra matching-memory skeptical outcome with weight 10 and original failure hearts/destination. Evaluate exploring with no memory, each single tier, each pair, all three, and unrelated memory: additional failure weights 0/1/2/3 respectively and original success-weight total unchanged. Preserve the existing advancement-eligibility test; generic has no new memory.
- [ ] **Step 2: Run the focused test first.** From the repository root run `.\gradlew.bat :common:test --tests "net.conczin.mca.dialogue.NaturalDialogueContentTest"`, verifying the exact Gradle task from current build configuration if different. Expected: the new test fails for missing remember actions and repeat outcome.
- [ ] **Step 3: Edit the story JSON.** Append all three exploring `mca:remember` actions to *every* existing exploring outcome and add three `weight: 1` skeptical outcomes gated individually on the short/medium/long memories; each also writes all three memories. For Nether/Dragon/Wither append one `mca:remember` to every existing outcome and one memory-gated skeptical outcome with weight 10 and the same remember action. Preserve all five choices, original outcome weights, advancement/personality conditions and follow-up nodes.
- [ ] **Step 4: Rerun that test.** Expected: the new mass audit and existing natural dialogue tests pass. Correct any evidence-backed mismatch before considering broader coverage.
- [ ] **Step 5: Review narrowly.** Run `git diff --check` and inspect the resource/test diff. Confirm exploring's 0/+1/+2/+3 added skeptical weight follows active tiers, never exceeds +3, and achievement success weights are unchanged. Inspect existing long-term-memory source/tests for cumulative expiry (`max(now, oldExpiry) + time`); content tests cannot prove in-game persistence, multiplayer isolation or cancellation. Add a targeted lifecycle test only for an identified gap, and do not change the owner by assumption.
- [ ] **Step 6: Prepare review.** Report verified results, changed files and remaining live checks. Commit only explicit task files if a commit was authorized; never stage unrelated work or push.

## Execution gate

Approach **C** is selected; the concrete revised design and plan await review. Do not implement until that review is complete. Keep the independent interaction-fatigue design and its owner's files separate.
