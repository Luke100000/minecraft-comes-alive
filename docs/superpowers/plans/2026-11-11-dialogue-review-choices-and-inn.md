# Dialogue Review Choices and Missing-Inn Reply Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let Previous/Next display every earlier reply with the accepted selection marked, and remove the repeated missing-inn question.

**Architecture:** Extend the existing client-only bounded speech history to hold immutable line-and-choice snapshots. Record a selected choice only after an accepted server transition, and render reviewed choices as disabled rows in the existing reply panel. Simplify one JSON dialogue edge.

**Tech Stack:** MCA 1.21.1, Java 21, JUnit, Fabric 0.20.1, NeoForge 21.1.265.

**Spec:** [Dialogue review choices and inn design](../specs/2026-11-11-dialogue-review-choices-and-inn-design.md).

## Global constraints

- Work only in the provided `dialogue-event-overhaul` worktree. Inspect its live diff first, preserve concurrent edits, and do not use WSL, create another worktree, or stage/commit/push without user instruction.
- History remains read-only, bounded to 64 entries and one current session. No new packets, persistent data, server-side dialogue replay, or extra animation.
- Keep the original options' order/text, and record a selected answer only for an accepted, matching session/offer transition. Preserve the existing duplicate, rejection, pause/resume, ambient-preview and terminal handling.
- Keep all existing `social/first` choices, endings and inn-present branch.

## Files and interfaces

| File | Responsibility |
| --- | --- |
| `common/src/main/java/net/conczin/mca/network/ClientHandlerImpl.java` | Own bounded history snapshots and pending/accepted selected choice |
| `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java` | Read reviewed snapshot, show disabled earlier choices and checkmark |
| `common/src/main/resources/data/mca/dialogue_events/social/first.json` | Attach existing missing-inn options to the response itself |
| `common/src/test/java/net/conczin/mca/network/DialoguePresentationTest.java` | History, acknowledgment, lifecycle and read-only tests |
| `common/src/test/java/net/conczin/mca/dialogue/NaturalDialogueContentTest.java` | First-contact branch regression |

## Review focus

1. Rejected or stale choices must never receive a checkmark (Task 1).
2. Duplicate node tokens, pause/resume and ambient previews must retain correct history entries (Task 1).
3. A 65th entry must evict the oldest line together with its choices/selection (Task 1).
4. Previous/Next, keyboard focus and mouse clicks must never activate historical options (Task 2).
5. Absent-inn and present-inn paths must both reach the original completing endings (Task 3).

---

### Task 1: Store original choices beside each historical line

**Files:** `ClientHandlerImpl.java`, `DialoguePresentationTest.java`.

**Interfaces:**
- Produce nested `DialoguePresentation.HistoryEntry(Component line, List<InteractionDialogueNodeResponse.Choice> choices, DialogueEngine.AdvanceKind advanceKind, Optional<String> selectedChoiceId)`.
- Produce `public Optional<HistoryEntry> reviewedEntry()` and `public void markChoiceRequested(long offerToken, String choiceId)`.
- Preserve existing `reviewPrevious()`, `reviewNext()`, `reviewLatest()`, `fullLine()`, `visibleChoices()` contracts.

- [ ] **RED:** Add `reviewHistoryPreservesAllOfferedChoicesAndOrder`. Accept a node with three distinct replies, move to its successor, review the earlier node, and assert its original three choice IDs/text/order while `visibleChoices()` stays empty.
- [ ] **RED:** Add `onlyAcknowledgedChoiceIsMarked`. Request one of those choices using the correct offer token, accept a newer node in the same session, and assert only that ID is selected in the reviewed entry. Verify rejected, stale-token and cross-session requests leave no selected ID.
- [ ] **RED:** Extend existing duplicate/resume, ambient-preview, and 64-entry tests to check the matching choices/selection as well as speech. An ambient preview snapshot has an empty choice list.
- [ ] **Verify RED:** Run `.\gradlew.bat :common:test --tests "net.conczin.mca.network.DialoguePresentationTest" --offline --no-daemon --no-configuration-cache --max-workers=1 --console=plain`. New assertions should fail against speech-only history.
- [ ] **GREEN:** Replace `speechHistory: List<Component>` with `List<HistoryEntry>` while retaining the review cursor and max 64. Snapshot `incoming.choices()` and `incoming.advanceKind()` when a genuinely new active line is accepted. Keep the ambient preview as a line-only entry.
- [ ] **GREEN:** Store the pending choice ID with its original offer token and session in `markChoiceRequested`. Copy it onto the prior entry only when an accepted successor node/pending response confirms that transition; discard it on rejection, new session, or clear. Preserve first-line reveal and duplicate/resume suppression.
- [ ] **Verify GREEN:** Rerun `DialoguePresentationTest`, inspect rejection/pending/terminal cases, and keep all pre-existing assertions.

### Task 2: Show the historical options in the existing choices panel

**Files:** `InteractScreen.java`, `DialoguePresentationTest.java` (existing presentation assertions).

**Consumes:** `DialoguePresentation.reviewedEntry()` and `markChoiceRequested(long, String)` from Task 1.

- [ ] **RED:** Add or extend a focused presentation test proving the reviewed entry exposes its old `AdvanceKind`, old choice list and selected ID, while `visibleChoices()` and `canAdvance()` remain inactive during review.
- [ ] **GREEN:** After validating a live `DialogueClickKind.CHOICE`, call `markChoiceRequested(token, choiceId)` before the existing packet send. Update `replyRows` to read `reviewedEntry()` during review, render every offered choice as a `DialogueRow` with null click, and prefix/color the accepted answer with a checkmark. Render the historical Continue/Finish as disabled only when the old entry offered it. Live reply rendering remains as before.
- [ ] **Verify:** Run the focused presentation class. In a real client, revisit a line with three options, confirm the original list and highlighted selection, click/tab over old rows, and return to live with current reveal/choices intact. Check narrow GUI scale, scroll and both speech positions.

### Task 3: Remove the extra missing-inn question

**Files:** `social/first.json`, `NaturalDialogueContentTest.java`.

- [ ] **RED:** Extend `firstEncounterOffersCoherentFollowups` to assert `spent_night_failed` itself offers exactly `no_inn_travel` and `no_inn_stay`, has no automatic `next`, and both choices lead to the existing completing replies.
- [ ] **Verify RED:** Run `.\gradlew.bat :common:test --tests "net.conczin.mca.dialogue.NaturalDialogueContentTest" --offline --no-daemon --no-configuration-cache --max-workers=1 --console=plain` and confirm the new assertion fails.
- [ ] **GREEN:** Move the two choices from `no_inn_followup` onto `spent_night_failed`; remove the unreachable intermediate node. Preserve translation keys, all other first-contact paths, choice IDs and terminal replies. Avoid editing the shared locale unless necessary.
- [ ] **Verify GREEN:** Rerun `NaturalDialogueContentTest` and `McaDialogueEventResourcesTest`. Manually confirm the no-inn response now presents the two replies immediately, while an inn-present village follows its prior branch.

## Final verification and handoff

- [ ] Run focused tests for both modified areas together, then `.\gradlew.bat :common:test :fabric:build :neoforge:build --offline --no-daemon --no-configuration-cache --max-workers=1 --console=plain` when the worktree is free of competing Gradle processes.
- [ ] Review scoped diffs for the five named files. Verify no history-click packets, state mutation, or unrelated menu styling changes. Report any existing unrelated test failure separately.
- [ ] Confirm in-game rendering at normal and narrow GUI scales. Compile results alone are not visual acceptance.
- [ ] Present the completed implementation and test evidence for review. Keep staging, commits and pushes under separate user direction.
