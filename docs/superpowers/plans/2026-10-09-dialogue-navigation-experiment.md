# Dialogue Navigation Experiment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Improve Talk navigation using inline expandable Ask topics and Previous/Next speech review.

**Architecture:** Keep event selection and progression on the server. Store bounded, session-scoped speech history in the existing client presentation owner; render historical lines as read-only without changing progression. Add one local expansion toggle to the existing Talk menu.

**Tech Stack:** Java 21, Minecraft 1.21.1, MCA `common/`, JUnit 5, Fabric and NeoForge.

**Spec:** `docs/superpowers/specs/2026-10-09-dialogue-navigation-experiment-design.md`

## Global Constraints

- Do not change dialogue engine, packets, save data, or event JSON.
- 64-line client-only bounded history, reset on session replacement/end; preserve across pause/resume.
- Every previously viewed line is read-only and appears instantly; only the live line may advance the server.
- No new checkout, worktree or unrelated changes; commit the completed experiment on the current branch.

## Review Focus

- A duplicate node packet must not create duplicate history.
- Changing sessions must not leak the previous NPC's speech.
- Opening Topics and resuming must not reset read-only history or the active line's reveal position.
- Reviewing older speech must never expose stale reply choices or send progression.
- At most 64 lines are retained; the current line must stay reachable.

### Task 1: DialoguePresentation read-only line history

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/network/ClientHandlerImpl.java`
- Test: `common/src/test/java/net/conczin/mca/network/DialoguePresentationTest.java`

**Interfaces:**
- `boolean canReviewPrevious()` and `boolean reviewingHistory()`
- `boolean reviewPrevious()` / `boolean reviewNext()` return whether the view changed; no packets.
- `visibleLine()` and `fullLine()` return the reviewed line when active; `canAdvance()`/`visibleChoices()` return false/empty while reviewing.

- [x] Write failing real-response tests for line history, client-only movement, preserved reveal, pause/resume, duplicate offers, replacement/end and 64-entry bound.
- [x] Run focused JUnit and confirm feature assertions fail before implementation.
- [x] Implement bounded line storage and cursor in existing `DialoguePresentation`; preserve authoritative live node/reveal state separately.
- [x] Run focused and full common JUnit suites.

### Task 2: Talk screen controls and expandable Ask section

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java`
- Modify: `common/src/main/resources/assets/mca/lang/en_us.json`

**Interfaces:** `DialogueClickKind.PREVIOUS`/`REVIEW_NEXT` call Task 1 methods without networking. `ASK_TOGGLE` only updates local expansion and scroll position. On latest line, existing `ADVANCE` keeps handling Next/Done.

- [x] Implement expandable Ask row in the flat menu with inline eligible entries and no server request on toggle.
- [x] Show compact Previous/Next review actions in active dialogue; hide reply controls on reviewed lines; keep Topics distinct and existing server advance on live line.
- [x] Run `:common:test`, `:fabric:compileJava`, `:neoforge:compileJava`, `git diff --check`.
- [ ] Review layout, clipping, button hover/click alignment at varying GUI scales in a client if feasible; report any unverified visual observations.
- [x] Stage the scoped worktree changes and commit with a descriptive message; no push.

Compilation and automated presentation tests passed; in-game GUI-scale verification remains a manual follow-up for this experiment.
