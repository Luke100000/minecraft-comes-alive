# Automatic Ambient Talk Introduction Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** Pressing Talk immediately presents a server-selected eligible ambient passage while keeping every other topic accessible.

**Architecture:** Reuse existing DialogueEngine menu offers and DialogueSession. Pin a noncommitting preview in DialogueOptions; use existing advance/choice token messages to engage the pinned event atomically. Extend the options packet and client presentation to render both preview and topic list; Topics on an active run pauses before refreshing the menu.

**Tech Stack:** Minecraft 1.21.1, Java 21, shared MCA common, JUnit 5, Fabric/NeoForge StreamCodec.

**Spec:** docs/superpowers/specs/2026-10-09-automatic-ambient-talk-introduction-design.md

## Global Constraints

- Use only the existing dev/1.21.1-dialogue-system worktree. No new worktree or destructive clean.
- No new event JSON schema, registry, session type, custom loader-specific GUI, or forced story.
- Preserve 40 ms/Unicode-grapheme reveal without skip, and reject early Next/replies.
- Story history and queued actions commit explicitly; ambient previews cannot grant rewards.
- Preserve the existing 2400-tick pause window and generation-bound single-use tokens.
- Preserve highlighted/Ask topics, personalized continuations, and legacy gameplay-command owners.

## Review Focus

- A paused run must survive a new Talk preview with deadline intact; Task 1 test.
- Replayed/stale/wrong-villager menu tokens cannot engage twice; Task 1 test.
- Revalidated eligibility can revoke a preview without writing effects; Task 1 test.
- Unsafe high-priority event start nodes must not suppress a safe baseline; Task 1 test.
- Out-of-order options/node packets must not restore stale UI after Topics; Task 2 test.

---

### Task 1: Pin ambient preview and enable secure engagement

**Files:**
- Modify: common/src/main/java/net/conczin/mca/dialogue/DialogueEngine.java
- Modify when needed: common/src/main/java/net/conczin/mca/server/world/data/DialogueEventHistory.java
- Test: common/src/test/java/net/conczin/mca/dialogue/DialogueSelectionTest.java
- Test: common/src/test/java/net/conczin/mca/server/world/data/DialogueEventHistoryTest.java

**Interfaces:**
- Produces: DialogueOptions.preview() returning an optional AmbientPreview with resolved line, offered ChoiceView list, and AdvanceKind.
- Produces: preview-aware paths in DialogueEngine.advance(player, token) and choose(player, token, choiceId), without trusting event IDs.
- Produces: DialogueEngine.begin() pausing the active same-pair run when a player explicitly requests Topics.

- [ ] **Step 1: Write failing tests** for safe preview selection and weighted tiers, recent-preview pair exclusion (max 32 entries), paused-run preservation, scheduling-only line delivery, valid and stale token acceptance.
- [ ] **Step 2: Run test RED:** .\gradlew.bat :common:test --tests 'net.conczin.mca.dialogue.DialogueSelectionTest' --console=plain --no-parallel --no-daemon. Expected: new assertions FAIL for absent behavior.
- [ ] **Step 3: Implement** minimal selection/offer fields, validated promotion to a real session on Next/reply, and explicit Topics pause. Reuse existing advance/choose behavior once promoted. Do not grant effects when rendering a preview.
- [ ] **Step 4: Run tests GREEN** with the same command and the focused history test. Expected: PASS.
- [ ] **Step 5: Commit** feat(dialogue): pin ambient Talk introductions.

### Task 2: Extend options packet and hybrid client UI

**Files:**
- Modify: common/src/main/java/net/conczin/mca/network/s2c/InteractionDialogueOptionsResponse.java
- Modify: common/src/main/java/net/conczin/mca/network/ClientHandlerImpl.java
- Modify: common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java
- Modify if needed: the English GUI localization owner
- Test: common/src/test/java/net/conczin/mca/network/DialogueEventCodecTest.java
- Test: common/src/test/java/net/conczin/mca/network/DialoguePresentationTest.java

**Interfaces:**
- Consumes: DialogueOptions.preview() from Task 1.
- Produces: bounded optional preview in existing options packet; client preview reveal with topics still clickable and Topics action on active sessions. Preview engagement uses existing advance/choice packets.

- [ ] **Step 1: Write failing tests** for packet round-trip with/without preview, client Unicode reveal, stale response rejection, and transition from preview to active node.
- [ ] **Step 2: Run test RED:** .\gradlew.bat :common:test --tests 'net.conczin.mca.network.DialogueEventCodecTest' --tests 'net.conczin.mca.network.DialoguePresentationTest' --console=plain --no-parallel --no-daemon. Expected: new assertions FAIL.
- [ ] **Step 3: Implement** packet, presentation and minimal Talk GUI change: bottom preview, persistent right topics, compact inline initial replies, natural alternate ambient prompt, reachable Topics while an active story is revealing. Preserve existing dialogue progression and action labels.
- [ ] **Step 4: Run tests GREEN** with the same command. Expected: PASS.
- [ ] **Step 5: Commit** feat(dialogue): display ambient opening beside topics.

### Task 3: Verify shared and loader seams

**Files:** Existing tests/changed files only, as needed for demonstrated integration defects.

**Interfaces:** Consumes Task 1 and Task 2 behavior; produces reviewed verified changes.

- [ ] **Step 1: Add a failing regression** for each integration defect encountered; otherwise retain passing focused regressions.
- [ ] **Step 2: Implement minimum fix** and run the reproducing test RED-to-GREEN if a defect occurs.
- [ ] **Step 3: Run** .\gradlew.bat :common:test :fabric:build :neoforge:build --console=plain --no-parallel --no-daemon. Expected: BUILD SUCCESSFUL.
- [ ] **Step 4: Review** complete branch diff for security, stale packets, persistence, awkward layout, and unnecessary classes. Record if in-game visual check could not run.
- [ ] **Step 5: Commit** any verified integration fix and report results. Do not push.

## Sequencing

These tasks share the offer/wire contract; execute sequentially in the
current worktree. Do not introduce a parallel greeting system or convert
existing DialogueEvents. A scheduling-only one-line greeting may begin its
authored cooldown at delivery through the existing history owner, without
pretending it completed a story.
