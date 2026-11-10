# Immediate Slap Dialogue Exit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** After a provoking reply leads to a slap, close the entire interaction UI, then visibly slap the player, then send the final authored reaction in chat.

**Architecture:** The server-owned `DialogueEngine` recognizes completed typed `mca:slap` paths during choice resolution, commits the dialogue once, and uses its existing server-tick loop to defer the physical hit for three ticks. The existing interaction-ID-qualified CLOSE packet dismisses the screen before the hit, and the villager's existing chat pipeline delivers the localized last line after damage. Bundled Flirt/Kiss resources use typed slap actions. The legacy slap text command is retired on this development branch.

**Tech Stack:** Minecraft 1.21.1, Java 21, shared MCA common code with Fabric and NeoForge, NeoForge GameTests.

**Spec:** `docs/superpowers/specs/2026-11-09-immediate-slap-dialogue-exit-design.md`

## Global Constraints

- Work only in the existing dialogue worktree. Do not create a worktree, use WSL, or modify unrelated staged UI styling.
- Retain server authority, existing session/offer replay protection, the authenticated villager interaction ID, and the existing close packet. Do not add a new packet or parallel interaction lifecycle.
- Use a three-server-tick delay (150 ms at 20 TPS) as the implementation choice. The client should receive CLOSE before damage. True rendered-frame ordering still needs in-game validation and might need acknowledgement if the delay proves insufficient.
- Commit history and other authored effects once when the accepted path completes. Delay only the physical slap and its subsequent authored reaction. Preserve ordinary and resumable dialogue and unrelated gameplay commands.
- A pending hit expires without damage or chat when either actor dies, disappears, changes world, or the server clears the dialogue engine. Keep queued work short-lived and server-thread-confined.

## Review Focus

- **Old choice token/retry** — duplicate selection or final acknowledgement cannot queue another hit. Test in Task 1.
- **Concurrent/changed owner** — the close targets the captured interaction ID and never closes a newer player's UI. Test in Task 2.
- **Actor lost during delay** — no damage or stale chat if the player or villager is unavailable. Test in Task 1.
- **Other outcome** — non-slap success/failure still renders its terminal dialogue normally and commits on acknowledgement. Test in Task 1.
- **Legacy rejection** — obsolete `mca:command/slap` and standalone text slap cannot execute, while typed `mca:slap` retains damage values, hearts/history and correct localization. Test in Tasks 1 and 3.

---

### Task 1: Commit slap-ending choices once and defer the hit

**Files**
- Modify: `common/src/main/java/net/conczin/mca/dialogue/DialogueEngine.java`
- Test: `neoforge/src/main/java/net/conczin/mca/dialogue/DialogueActionGameTests.java`

**Interfaces**
- Consumes existing `DialogueEngine.choose(ServerPlayer,long,String)`, `advance(ServerPlayer,long)`, `commit(...)`, `tick(MinecraftServer)` and `clear(MinecraftServer)`.
- Produces an accepted `TransitionResult.completion(session.id())` for a choice that reaches a one-line, complete, no-further-choice slap reaction. Keeps `TransitionResult.advanced(...)` for ordinary choices.
- Add one private short-lived pending-hit record or equivalent, carrying the accepted session/actor identity, due tick, damage amount and captured localized reaction. Process only on the server thread. Do not store it in `DialogueSession`.

- [ ] **Step 1: Make the existing slap GameTest red.** Revise `slapActionUsesEmptyHandAndVillagerDamageOnce` to assert that choosing the provoking reply completes the path immediately and leaves health at 20 before the deferred hit. After three server ticks, assert health 17, villager damage attribution, an OFF_HAND swing with the occupied main hand unchanged, recorded history, and one hit after replaying the old token. Use the existing FakePlayer fixture conventions. Owner release is covered in Task 2.
- [ ] **Step 2: Run the focused GameTest to confirm RED.** Run `.\gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=DialogueActionGameTests#slapActionUsesEmptyHandAndVillagerDamageOnce --console=plain --no-configuration-cache --max-workers=1`. Expected failure is the old delayed-acknowledgement contract. Confirm the selector really ran.
- [ ] **Step 3: Implement the transition in `DialogueEngine`.** After resolving an accepted choice and automatic routes, detect a complete, one-line final reaction with exactly one pending `DialogueAction.Slap`. Consume the session and offer token once, commit non-slap effects and history with existing validation, and enqueue the deferred hit at the typed action's authored amount. Keep existing acknowledgement/commit behavior for unusual multi-slap or intermediate-node paths, avoiding an unrequested change to their semantics.
- [ ] **Step 4: Process the pending hit in `DialogueEngine.tick(MinecraftServer)`.** At or after the due tick, remove the entry before execution, validate actors/world, call the existing `villager.getInteractions().slap(player, amount)`, and only then call `villager.sendChatMessage(capturedReaction, player)`. Discard invalid entries and clear pending hits on engine clear/removal. Use the current server tick registration in both loaders.
- [ ] **Step 5: Add focused negative cases to the same GameTest class.** Cover a typed `mca:slap` with an accompanying hearts action, repeated choice token, disappearance before the due tick, and an ordinary no-slap choice still requiring final acknowledgement. Verify the removed legacy slap text command is rejected without damage or history. Keep nonclosing command assertions on valid unrelated command fixtures.
- [ ] **Step 6: Verify GREEN.** Run `.\gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=DialogueActionGameTests --console=plain --no-configuration-cache --max-workers=1`. Confirm the focused class executes and finishes successfully. Use `.\gradlew.bat :common:test --console=plain --no-configuration-cache --max-workers=1` for relevant shared behavior.
- [ ] **Step 7: Commit only Task 1 files.** Use `git commit --only -m "feat(dialogue): complete slap paths before deferred hit" -- <exact tracked Task 1 paths>`. Check `git status --short` beforehand because unrelated styling is already staged. Do not run an unscoped `git commit`.

### Task 2: Close the correct screen before the delayed hit and verify the reaction

**Files**
- Modify: `common/src/main/java/net/conczin/mca/dialogue/DialogueEngine.java`
- Test: `neoforge/src/main/java/net/conczin/mca/dialogue/DialogueActionGameTests.java`
- Reference unchanged: `common/src/main/java/net/conczin/mca/network/c2s/InteractionDialogueChoiceMessage.java`, `common/src/main/java/net/conczin/mca/network/s2c/OpenGuiRequest.java`, `common/src/main/java/net/conczin/mca/network/ClientHandlerImpl.java`, `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java`

**Interfaces**
- Consumes the Task 1 completed slap transition and the current interaction ID.
- Produces `Network.sendToPlayer(new OpenGuiRequest(OpenGuiRequest.Type.CLOSE, villager, interactionId), player)` before the pending-hit due tick. The existing completed transition also sends `InteractionDialogueNodeResponse.ended(...)`.

- [ ] **Step 1: Extend the GameTest with a failing owner-closure assertion.** Start a concrete interaction via `interactAt`, capture `interactionId`, select the slap choice and assert the original owner is released immediately. Exercise a subsequently started second interaction and assert delayed slap processing does not close it.
- [ ] **Step 2: Confirm RED with the focused selector.** Run `.\gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=DialogueActionGameTests#slapActionUsesEmptyHandAndVillagerDamageOnce --console=plain --no-configuration-cache --max-workers=1` and confirm the intended ownership assertion fails.
- [ ] **Step 3: Wire server closure at successful slap-path completion.** Capture the old ID before `stopInteracting(player, interactionId)`; send the existing authenticated CLOSE packet before the scheduled hit can execute. Preserve `InteractionDialogueChoiceMessage.sendTransition`'s ended-state cleanup. Never send an unqualified close and never close a new interaction during delayed processing.
- [ ] **Step 4: Verify reaction content in the existing path.** Resolve the complete node's final line with `villager.getTranslatable(player, lineKey)` when the choice is accepted, then deliver it through `villager.sendChatMessage(Component, player)` only after the slap. Verify `flirt.json` and `kiss.json` use the existing `dialogue.flirt.slap` and `dialogue.kiss.slap` translation keys. No new hard-coded chat copy.
- [ ] **Step 5: Verify runtime and regressions.** Run the full `DialogueActionGameTests` class, `McaDialogueEventResourcesTest`, `:common:test`, `:fabric:compileJava`, and `:neoforge:compileJava`. For client confirmation, provoke a Flirt or Kiss slap and observe UI close, a visible strike, then the translated chat line. If slow networking shows the hit before a world frame, measure that case and decide whether a small close-ack packet is warranted before treating the ordering as verified.
- [ ] **Step 6: Check `git diff --check` and commit only Task 2 files.** Use `git commit --only -m "feat(dialogue): close interaction before slap" -- <exact tracked Task 2 paths>` so the unrelated staged styling is excluded.

### Task 3: Migrate bundled legacy slap actions and update authoring guidance

**Files**
- Modify: `common/src/main/resources/data/mca/dialogue_events/relationship/flirt.json`
- Modify: `common/src/main/resources/data/mca/dialogue_events/relationship/kiss.json`
- Test: `common/src/test/java/net/conczin/mca/dialogue/McaDialogueEventResourcesTest.java`
- Modify: `docs/dialogue-authoring.md`
- Modify: `docs/dialogue-system-guide.md`

**Interfaces**
- Bundled event data uses `{ "type": "mca:slap", "amount": 1.0 }` instead of `{ "type": "mca:command", "command": "slap" }`.
- `DialogueAction.Command("slap")` is rejected by the dialogue command allowlist. The standalone legacy `VillagerCommandHandler.handleDialogue("slap")` is also rejected.

- [ ] **Step 1: Add a failing resource test.** Assert every `relationship/flirt` and `relationship/kiss` slap outcome uses `DialogueAction.Slap(1.0f)`, none uses `DialogueAction.Command("slap")`, and negative heart deltas, weights, requirements and target `slap` nodes remain unchanged.
- [ ] **Step 2: Confirm RED.** Run `.\gradlew.bat :common:test --tests 'net.conczin.mca.dialogue.McaDialogueEventResourcesTest' --console=plain --no-configuration-cache --max-workers=1`. Expected FAIL on the current bundled command actions.
- [ ] **Step 3: Migrate the ten bundled slap command actions.** Replace each in `flirt.json` (seven) and `kiss.json` (three) with `mca:slap` at amount 1.0, preserving all outcome weights, priorities, requirements, hearts values, next links and `dialogue.flirt.slap`/`dialogue.kiss.slap` reaction keys.
- [ ] **Step 4: Update the two dialogue guides.** Document the new exception to final-acknowledgement commit: a typed slap-ending response immediately completes, closes the UI, then hits after the short delay and delivers the localized terminal reaction in chat. Document removal of the legacy slap text command for datapacks.
- [ ] **Step 5: Verify GREEN and compatibility.** Run the same resource test, `.\gradlew.bat :common:test --console=plain --no-configuration-cache --max-workers=1`, and the NeoForge `DialogueActionGameTests` class from Tasks 1–2. Confirm `DialogueEventParsingTest` still round-trips the typed slap action.
- [ ] **Step 6: Commit only Task 3 paths.** Verify `git diff --check` and use `git commit --only -m "refactor(dialogue): migrate bundled slap actions" -- <exact tracked Task 3 paths>`, keeping unrelated staged styling untouched.

## Completion Criteria

Typed slap actions, rejection of legacy slap text commands, old-token replay, disappearance, owner change and no-slap routes pass focused GameTests. Bundled Flirt/Kiss events use typed slap actions, and unrelated gameplay command handling remains compatible. Common tests and both loader compiles pass. A client check confirms the visual UI-close → slap → chat order, or the result is explicitly reported as still unverified.

## General Legacy Handling Audit — Separate Follow-up Workstreams

A read-only subagent audited legacy dialogue, interaction packets, GUI routes, chat feedback, translations and tests. **Tasks 1–3 are the approved slap plan. Items below are an inventory of independent proposed work, not authorization to implement those changes in the slap patch.** Priorities reflect source-level impact; runtime exploitability and client presentation remain untested.

### P0 — Interaction command authorization (separate security plan)

- [ ] **Secure the legacy command packet.** `common/src/main/java/net/conczin/mca/network/c2s/InteractionVillagerMessage.java` accepts a client-provided villager UUID and command and calls `villager.getInteractions().handle(player, command)` without checking interaction owner/ID, distance or the requested button's server-side permission. A successful command calls unqualified `stopInteracting()`, which may release another player's interaction. `VillagerCommandHandler.java` includes commands affecting relationships, professions, jobs and other state. Design server-side ownership/authorization for every supported command, an authenticated matching-owner close, and the related zombie-villager gift route. Regression tests must reject absent, foreign, expired, distant and disallowed interactions with no world mutation or foreign close, while preserving authorized commands. Source review confirmed missing guards, not an executed exploit.

### P2 — Other migration and hardening candidates

- [ ] **Scope interaction-data access.** `common/src/main/java/net/conczin/mca/network/c2s/GetInteractDataRequest.java` resolves an entity ID and returns constraints and family information without verifying the current interaction owner or proximity. Plan sender/owner/distance validation using the same policy as the P0 fix. Verify legitimate UI requests and rejection of absent, foreign, stale and distant requests.
- [ ] **Show Rumours' asynchronous location reply inside Talk.** Two `mca:command/location` outcomes in `common/src/main/resources/data/mca/dialogue_events/social/rumors.json` call an async structure lookup whose callback sends a separate chat line through `VillagerCommandHandler.java`. A follow-up could resolve and display a localized result in the dialogue session, with success/failure, timeout, cancellation, reloading and stale-completion coverage, while preserving external legacy command callers. Two **separate untracked proposals already exist** at `docs/superpowers/specs/2026-11-09-inline-location-rumors-design.md` and `docs/superpowers/specs/2026-11-09-dialogue-pending-and-async-design.md` with corresponding plans. Reconcile them before selecting an approach; do not edit them here.
- [ ] **Review duplicated chat feedback individually.** Adoption, divorce and rejected procreation paths in `VillagerCommandHandler.java` send direct chat messages alongside event dialogue. Decide which are necessary gameplay/failure notifications and which belong to an authored dialogue node. Preserve error feedback, avoid duplicate story lines, and test success and rejection separately.
- [ ] **Retain gameplay-command ownership for eleven non-slap bundled adapters unless a specific presentation issue calls for migration.** The bundled inventory has **21 `mca:command` actions**: ten slap outcomes covered by Task 3, plus two location, two apology, two hiring and one each of adoption, divorce confirmation, divorce papers, procreation and stay-in-village. `DialogueEngine` already maintains an explicit command allowlist and at-most-one-command rule. These other effects are gameplay operations owned by their handlers, so replacing them indiscriminately with duplicated dialogue code is not recommended. Review command authorization under P0 independently.

### P3 — Compatibility-sensitive cleanup, not immediate removal

- [ ] **Audit dormant dynamic GUI branches before removing them.** `InteractScreen.java` still recognizes `command` and `clothing` layout routes, though no bundled GUI layout supplies either. External addon layouts may still use them. Keep live `interact`, `work`, `professions` and `locations` commands; decide addon compatibility before deleting branches.
- [ ] **Retain legacy-named systems that still serve real features.** `DialogueType` and translation mixins provide gender, personality, profession, relationship and age fallbacks for current Talk lines. `ConversationManager` covers ambient speech, optional chat AI serves another entry point, and `SpeechManager` handles chat audio/TTS. Talk's overlay and chat have different spoken-voice behavior. Treat TTS parity as a separate user-facing feature decision, not a cleanup side effect.

### Already modernized and kept compatible

The Talk entry point now uses `DialogueEngine`, typed server-owned choices, offers/session tokens, resource generations, history, strict action codecs and bounded client presentation. There is no active bundled legacy `dialogues/` directory or `legacyAvailable` response field in the audited source. The unused legacy slap text command has been retired, including its external datapack entry point. Preserve typed-slap parsing, legacy rejection and scoped interaction-close tests.

**Audit verification boundary:** This inventory is based on source inspection only. No Gradle suite, client gameplay run, permission exploit test or migration implementation was performed by the auditor. Existing staged UI changes and the concurrent Rumours design/plan files were not modified.
