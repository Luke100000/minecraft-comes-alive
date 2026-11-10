# Interaction Command Authorization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans or superpowers:subagent-driven-development with TDD. Steps are checkboxes for tracking.

**Goal:** Bind each legacy Interact-menu command to the correct player, screen ID and server-authorized action without reintroducing a second Talk authority.

**Architecture:** Validate current ownership + range at the packet boundary, and validate the built-in GUI action set against `Constraint` state before executing the existing non-Talk command handler. Rotate command payloads with the already-present interaction UUID and close only the matching owner.

**Tech Stack:** Java 21, Minecraft 1.21.1, MCA common, NeoForge GameTests, Fabric/NeoForge compile.

**Spec:** [Interaction command authorization design](../specs/2026-11-10-interaction-command-authorization-design.md).

## Global Constraints

- Do not expose DialogueEvent actions through the GUI command packet; DialogueEngine owns authoring, session state, effects and history.
- Preserve supported non-Talk UI actions and zombie gifting, including valid movement, chores, inventory, trade and profession changes.
- Validate player, villager, interaction ID, proximity and server-side eligibility; reject silently with no partial side effect. Never unqualified-close a newer interaction.
- No new worktree or unrelated dialogue engine/UI refactor.

## Review Focus

1. Foreign player with a valid stolen villager UUID cannot execute or close owner screen (Task 1).
2. Same player using a stale screen UUID cannot execute on the replacement screen (Task 2).
3. Correct owner cannot trigger `slap` or privilege-gated `execute` via an arbitrary packet (Task 3).
4. Genuine GUI action remains effective once, and authorized close targets the current screen (Task 2/3).
5. Zombie gift, owner replacement, out-of-range requests, and cross-loader codecs remain compatible (Task 3/4).

---

### Task 1: Reject non-owner and privileged arbitrary commands

**Files:**
- Modify `neoforge/src/main/java/net/conczin/mca/dialogue/DialogueEngineGameTests.java`.
- Modify `common/src/main/java/net/conczin/mca/network/c2s/InteractionVillagerMessage.java`.

**Interfaces:** `InteractionVillagerMessage.handleServer(ServerPlayer)` initially uses existing two-field form; Task 2 extends the protocol.

- [ ] Write GameTest `legacyCommandPacketRejectsForeignPlayerAndDialogueOnlyEffects`: a real Interact owner exists; another player attempts `armor`, owner attempts `slap`/`execute` while not mayor. Assert actor state/health/owner unchanged.
- [ ] Run `:neoforge:runGameTestServer -PmcaGameTest=DialogueEngineGameTests#legacyCommandPacketRejectsForeignPlayerAndDialogueOnlyEffects --console=plain` and observe *expected assertion failure* (not compile errors).
- [ ] Add a single server-side built-in command authorization check and current-player owner validation before `handle()`; reject dialogue-only commands from the GUI packet.
- [ ] Rerun focused GameTest until PASS; commit packet, tests, design/plan documents.

### Task 2: Bind commands to an interaction ID

**Files:**
- Modify `common/src/main/java/net/conczin/mca/network/c2s/InteractionVillagerMessage.java`, `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java`.
- Modify `neoforge/src/main/java/net/conczin/mca/dialogue/DialogueEngineGameTests.java`.

**Interfaces:** Produce final `InteractionVillagerMessage(String command, UUID villagerUUID, UUID interactionId)` and network codec; existing `EntityCommandHandler.matchesInteraction(Player,UUID)` and `stopInteracting(Player,UUID)` are canonical owner checks.

- [ ] Test same player reopens same villager; old interaction packet with previous UUID is ignored; new UUID `armor` command succeeds; a matching close affects only current owner.
- [ ] Run focused GameTest to witness RED (missing ID protection).
- [ ] Extend record codec, migrate all three GUI send sites to current `interactionId`; update server handler to require matching UUID before dispatch and use qualified close.
- [ ] Run focused tests, commit changes.

### Task 3: Enforce built-in command permissions and proximity

**Files:**
- Modify `common/src/main/java/net/conczin/mca/network/c2s/InteractionVillagerMessage.java` (or a small server-side validation owner if clearer).
- Modify `neoforge/src/main/java/net/conczin/mca/dialogue/DialogueEngineGameTests.java`.

**Interfaces:** Use `Constraint.allMatching(villager,player)` and existing command names, not client `MCAScreens` singleton. `DialogueEngine` actions are separate.

- [ ] Add RED GameTests for disallowed `execute`/`profession.guard` without mayor/monarch; invalid command; valid `armor` and work; out-of-range request; restored acceptance in range.
- [ ] Implement allowlist/constraint policy including exact argument tokens and short radius; no user-supplied arbitrary command dispatch.
- [ ] Run focused relevant GameTests and regression; commit changes.

### Task 4: Data access and loader verification

**Files:**
- Modify `common/src/main/java/net/conczin/mca/network/c2s/GetInteractDataRequest.java` if owner-data tests demonstrate need.
- Modify `neoforge/src/main/java/net/conczin/mca/dialogue/DialogueEngineGameTests.java` with focused owner read and zombie gift coverage where practical.

- [ ] Add RED tests showing wrong-owner data query / zombie gift with forged ownership is rejected, and valid owners succeed.
- [ ] Reuse existing interaction owner and distance checks; do not invent second state store.
- [ ] Run focused tests, `:common:test`, Fabric+NeoForge build and relevant in-game interaction checks if available; commit verified fixes.
