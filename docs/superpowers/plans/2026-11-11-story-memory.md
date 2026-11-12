# Remembered Stories (Option A) Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans or superpowers:subagent-driven-development. Check off steps.

**Status:** Approved for implementation, 2026-11-11.
**Goal:** Restore player-scoped remembered-story skepticism using one timed memory per story.
**Architecture:** JSON-only changes to the shared story event, with one additional memory-conditioned skeptical outcome for exploring, Nether, Ender Dragon and Wither. Integrate with the separate approved fatigue feature in one editor.
**Tech Stack:** Minecraft 1.21.1 MCA, existing Gson-backed dialogue resources and JUnit.
**Spec:** `docs/superpowers/specs/2026-11-11-story-memory-design.md`.

## Global constraints

- Reuse this already-established worktree. Preserve concurrent/dirty edits. Do not create worktrees, use WSL, stage unrelated files or push.
- Maintain all five visible story choices, existing success/failure weights, heart effects, advancement and personality requirements and all follow-up paths.
- Exploring has `story.exploring` for 24000 ticks with one conditional skeptical outcome weight 3.
- Nether, Dragon and Wither have `story.nether`, `story.enderdragon`, `story.wither` for 2400000 ticks, each with one conditional skeptical outcome weight 10.
- All memories are `var: "player"` and are written by all outcomes of each topic after dialogue completion.
- Coordinate a single change to `common/src/main/resources/data/mca/dialogue_events/social/story.json` with fatigue, whose `fatigue: 1.0` applies only to five original skeptical outcomes.
- Legacy memory expiration is cumulative. No engine/NBT migration or independent fatigue changes in this task.

## Files

- `common/src/main/resources/data/mca/dialogue_events/social/story.json`: four memories, four additional conditional skeptical outcomes.
- `common/src/test/java/net/conczin/mca/dialogue/NaturalDialogueContentTest.java`: mass audit all original and extra outcomes and advancement eligibility.
- Java owners `DialogueAction`, `DialogueCondition`, `DialogueEngine`, `LongTermMemory`: inspect only.

## Review focus

- Missing remember action on a conditional success or failure.
- Story-memory condition accidentally gates the whole choice.
- Wrong hearts/destination on one added skeptical outcome.
- Fatigue applied twice on the added skeptical outcome.
- An unrelated topic memory changing another story, or generic acquiring a memory.

## Task 1 — Story JSON and content mass audit

- [ ] Write one failing grouped content test `storyMemoriesAreScopedToEachTopic` that loads the actual story resource and checks all four topics, one memory action in every outcome, one matching memory-conditioned skeptical outcome per topic, and unchanged advancement eligibility.
- [ ] Run `.\gradlew.bat :common:test --tests "net.conczin.mca.dialogue.NaturalDialogueContentTest" --console=plain` and verify failure for missing memory.
- [ ] Modify only the existing story JSON. Append the one topic remember action to each existing outcome and add exactly one conditional skeptical outcome with the matching hearts and fail path, preserving existing structure.
- [ ] Rerun the focused content test until it passes; inspect the JSON diff and `git diff --check`.
- [ ] Run the integrated common test and both loader builds after fatigue implementation, reporting actual results. Do not change interaction fatigue ownership or add redundant tests.

## Review

Report actual test results and any runtime validation gaps. Preserve the archived Option C files unchanged. No staging or push.
