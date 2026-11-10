# Inline Location Rumours Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show location Rumours and their coordinates inline in Talk, with a shared loading row while a structure is being found.

**Architecture:** Replace only the Rumours location-command action with an explicit async dialogue marker. Share the existing locator and translation logic with the legacy command. The server holds the selected dialogue session pending, delivers one resolved normal node packet, and commits the event only after its final acknowledgment; the separate indicator work renders the wait.

**Tech Stack:** Minecraft 1.21.1, Java 21, MCA common server/client, Fabric and NeoForge, `CompletableFuture`, existing dialogue payloads and NeoForge GameTests.

**Spec:** `docs/superpowers/specs/2026-11-09-inline-location-rumors-design.md`

## Global Constraints

- **Proposed plan; obtain design/plan approval before implementation.** The other Codex thread owns the loading row and response/reveal timing.
- Preserve current Rumours weights, requirements, translation keys, 5-second cooldown, configured structure list, random origin, and radius `64`.
- The legacy `mca:command` `location` must keep its current chat behavior for other callers; only the Rumours-specific new action produces inline text.
- Do not block Minecraft's server thread or deliver stale results to a new/paused/ended dialogue. Proposed deadline: `600` server ticks.
- Keep shared/client and loader state correctly sided in both Fabric and NeoForge; keep all state authoritative on the server.
- Do not touch the dirty `InteractScreen`, `DialogueOverlayLayout`, row-styling docs or unrelated tests without coordinating their owners. Do not create another worktree.

## Review Focus

- **Already-completed future**: must not drop the response if completion happens during `select()`; cover in Task 3.
- **Slow future and repeated input**: second selection/advance cannot start another search or prematurely commit history; cover in Task 3.
- **Stale result**: old session, paused screen, different villager/world or datapack generation cannot receive or commit; cover in Task 3.
- **Failure/timeout**: exceptional/canceled/queue-full lookup or 600 ticks without an answer must not hang the UI; cover in Task 3.
- **Other consumers**: a normal legacy location command must still use chat and not become an inline Rumours action; cover in Task 2.

---

### Task 1: Distinguish the async Rumours route from a legacy command

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/dialogue/DialogueAction.java`
- Modify: `common/src/main/resources/data/mca/dialogue_events/social/rumors.json`
- Test: `common/src/test/java/net/conczin/mca/dialogue/DialogueEventParsingTest.java`

**Interfaces:** New parameterless sealed action `DialogueAction.RumorLocation` with `type()` = `MCA.locate("rumor_location")`; codec representation `{ "type": "mca:rumor_location" }` (reject other fields). Only the two location-route outcomes use it; terminal code must not execute it as `Command`.

- [ ] **Write a failing parsing test.** Decode/encode the new marker; reject extra fields. Decode the Rumours resource and assert its two location outcomes use `RumorLocation` (not `Command("location")`) while weights and lore/failed outcomes remain intact.
- [ ] **Run** `.\gradlew.bat :common:test --tests 'net.conczin.mca.dialogue.DialogueEventParsingTest' --console=plain --no-configuration-cache --max-workers=1`. Expect relevant red assertion or unknown-action failure.
- [ ] **Implement** `RumorLocation` in the sealed action's permits/codec and update the two JSON actions; do not change `location` command semantics.
- [ ] **Rerun** the same focused test and confirm `BUILD SUCCESSFUL`; stage only this task's files for its commit.

### Task 2: Share the existing search and localized result without sharing chat delivery

**Files:**
- Create: `common/src/main/java/net/conczin/mca/dialogue/RumorLocationLookup.java`
- Modify: `common/src/main/java/net/conczin/mca/entity/interaction/VillagerCommandHandler.java`
- Test: `neoforge/src/main/java/net/conczin/mca/dialogue/DialogueActionGameTests.java`

**Interfaces:** `RumorLocationLookup.locate(VillagerEntityMCA villager, ServerPlayer player): CompletableFuture<Component>` returns `villager.getTranslatable(player,"dialogue.location." + path, x + "," + y + "," + z)` for success or `villager.getTranslatable(player,"dialogue.location.forgot")` for missing/invalid result. Exceptions/cancellation are observable so the engine can differentiate an active failure from an invalidated session. Deliver component on server thread. The legacy command attaches its **chat send** to this helper; the new action does not.

- [ ] **Write a focused GameTest** proving the helper returns `dialogue.location.forgot` when `Config.structuresInRumors = List.of()`; restore config in `finally`. Keep the existing `locationCommandReusesRumorsOwnerAndCompletes` GameTest as the legacy compatibility regression.
- [ ] **Run** `.\gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=DialogueActionGameTests --console=plain` and confirm the new test is red for the expected missing behavior; coordinate runs with the other Codex thread.
- [ ] **Extract the existing `case "location"` lookup** into the helper, retaining registry checks, configured random selection, origin/radius, translations and server-thread callbacks. Let `VillagerCommandHandler` emit its old chat message by consuming the helper. Do not copy the lookup algorithm into `DialogueEngine`.
- [ ] **Rerun** the focused GameTest class and verify the helper and legacy-command tests pass; stage only this task's files for its commit.

### Task 3: Resolve the location Rumour inside the same authoritative dialogue session

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/dialogue/DialogueEngine.java`
- Modify: `common/src/main/java/net/conczin/mca/network/c2s/InteractionDialogueSelectMessage.java`
- Test: `neoforge/src/main/java/net/conczin/mca/dialogue/DialogueActionGameTests.java`
- Test/compatibility update: `neoforge/src/main/java/net/conczin/mca/dialogue/DialogueEngineGameTests.java` and existing `DialogueEngine.select` callers if the API changes.

**Interfaces:** Introduce `DialogueEngine.SelectionStatus { REJECTED, ACTIVE, PENDING }` and `DialogueEngine.SelectionResult(SelectionStatus status, Optional<DialogueNodeView> view)` for `select(ServerPlayer,long,DialogueSelection,@Nullable ResourceLocation)`, replacing its current `Optional<DialogueNodeView>` result. Ordinary selections still return `ACTIVE(view)` or `REJECTED`; a chosen `RumorLocation` returns `PENDING` with **no** immediate node/rejection packet. Introduce an injectable lookup source `BiFunction<VillagerEntityMCA,ServerPlayer,CompletableFuture<Component>>` (production default `RumorLocationLookup::locate`) for controlled GameTests. The engine holds one pending/resolved value keyed to current session ID, token, generation, player, villager and world; `view(...)` replaces the location node's generic `line` with the resolved `Component`. Existing `InteractionDialogueNodeResponse.active` carries the final result; no new codec is needed.

- [ ] **Write deterministic failing GameTests** with an incomplete lookup future. Assert selection returns `PENDING`, emits no interim active/rejection node, keeps the session open, rejects extra input, and does not commit history before result. Completing the future yields one active inline node with the injected exact line, followed by one normal final acknowledge/history commit.
- [ ] **Write failure/lifecycle GameTests** for immediately completed future, no result, exceptional or canceled future, timeout at 600 ticks, pause/leave, disconnected player, villager removal, changed world, resource generation change and stale callback after a replacement session. Assert fallback only when session is active, no result when invalid, and no duplicate send/history commit.
- [ ] **Run** `.\gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=DialogueActionGameTests --console=plain` and `.\gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=DialogueEngineGameTests --console=plain`; verify added tests actually execute and fail on the intended missing behavior.
- [ ] **Implement the pending transition in `DialogueEngine`.** After resolving the selected Rumours route, install session and pending state **before** registering the future callback. Retain the existing session until result and final acknowledgment. Block advances/duplicate lookups during pending. On completion, run on server thread, re-check token/session/generation/interaction/world and send exactly one `InteractionDialogueNodeResponse.active` with localized line. On exception or timeout use forgotten line, on invalid owner drop silently. Avoid leaving the `RumorLocation` action to execute as a terminal legacy command. Clear the transient state during completion, invalidation, pause/leave and server shutdown; if an unresolved paused session resumes, start a fresh lookup with fresh token.
- [ ] **Update the network select handler** to send active only on `ACTIVE`, rejection only on `REJECTED`, and no fake response on `PENDING`. Update test call sites for the new selection result; leave ordinary paths as before.
- [ ] **Rerun** both focused GameTest classes and confirm `BUILD SUCCESSFUL`, no early history commit, exactly one real response, and `git diff --check`; stage only this task's files for its commit.

### Task 4: Integrate with the separately developed UI and verify

**Files:**
- Read/review: `common/src/main/java/net/conczin/mca/network/ClientHandlerImpl.java`
- Read/review: `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java`
- Read/review: `common/src/main/java/net/conczin/mca/client/gui/DialogueOverlayLayout.java`
- Test: existing client dialogue presentation tests **only if** the settled indicator contract has an uncovered real regression.

**Interfaces:** The shared UI's pending-selection state displays one disabled grey three-square row until the valid final active packet arrives. This task must **reuse** the other thread's renderer; no Rumours-only animation, minimum delay or duplicate pending UI.

- [ ] **Inspect the final indicator diff/behavior** and verify pending select→single grey row→resolved active packet→normal choices; if its interface conflicts with the plan, revise the integration step rather than overwriting its in-progress changes.
- [ ] **Compile both loaders** with `.\gradlew.bat :common:compileJava :fabric:compileJava :neoforge:compileJava --console=plain --no-configuration-cache --max-workers=1`; expect `BUILD SUCCESSFUL`.
- [ ] **Run a real client smoke test**: Talk→Rumours location success (coordinates inline, no chat), failure/fallback inline, delay shows one disabled row, immediate/lore/failed paths feel instant, final acknowledgment returns to topics, and resizing/focus do not enable pending row input.
- [ ] **Check lifecycle during a manual slow lookup** by closing and reopening with a different villager; no stale answer leaks into the new Talk. Inspect final diff, run `git diff --check`, and report actual test results and any remaining UI verification limits.

## Execution handoff

This is a **proposal**, not approval to code. Confirm the spec and final indicator UI contract before starting. Since the server session and packet transitions are tightly coupled, **native inline execution** with one independent review is recommended over concurrent implementers; coordinate build/test access with the indicator thread and do not alter its working UI files.
