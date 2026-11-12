# Interaction Command Cleanup Implementation Plan (draft)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans for native execution, or superpowers:subagent-driven-development if the user chooses delegation. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Keep MCA's existing commands, dialogue JSON and packet, while fixing Work permissions and replacing duplicated routing with one addon-extensible dispatcher.

**Architecture:** One `InteractionCommands` registry handles builtin and addon commands from menu and validated dialogue completion. Keep `DialogueAction.Command`, `InteractionVillagerMessage`, existing dialogue extension APIs, the existing interaction/session owners and `VillagerCommandHandler.DialogueCommandResult`. Reuse domain operations and remove duplicate authorization/acceptance heuristics after migration.

**Tech Stack:** Java 21, Minecraft 1.21.1, Fabric Loader 0.20.1, NeoForge 21.1.265, common Java, Gradle, JUnit and NeoForge GameTests.

**Spec:** [Command-preserving cleanup design](../specs/2026-11-11-interaction-legacy-retirement-design.md).

## Global Constraints

- Revision 2026-11-12 supersedes the previous typed-effect/new-menu-packet tasks. Keep `mca:command`, the existing string packet, builtin names and bundled JSON. Do not implement the superseded seven-effect family or menu enum/packet.
- Work in the existing `dev/1.21.1-dialogue-system-b` Windows worktree. No WSL, new checkout, push, merge, reset, clean, or overwriting concurrent dirty work. Inspect live diffs before editing; stage only task-owned work if committing is authorized.
- Reuse the [Work finding](../reviews/2026-11-11-deferred-work-command-authorization.md) and [earlier authorization plan](2026-11-10-interaction-command-authorization.md); keep the Work page and Chore owner.
- Preserve dialogue action/condition/async resolver registration signatures and behavior, native cure, container handoffs, ownership UUIDs and direct editor-item inventory access.
- Add one common command registry, nested Context/Source, and optional button `command`. Reuse the existing result record; no second action registry, owner map, protocol, slash-command bridge or arbitrary argument framework.
- The selected direction is approved for document revision. This plan is a reviewable deliverable, not permission to start product implementation or commits.

## Review Focus

1. A valid interaction owner who cannot access Work must not assign or stop a chore (Task 1).
2. A forged menu command must not execute a dialogue-only relationship operation; a permissive addon callback must still obey owner/range/session checks (Tasks 2–3).
3. Existing bare names, camel-case aliases, profession names containing dots and namespaced addon IDs must resolve without changing meaning or splitting addon paths (Tasks 2–3).
4. Failed hire/adoption/procreation/inventory operations must not commit dialogue rewards/history; accepted non-closing operations must succeed (Tasks 2–3).
5. Resource-pack buttons, server-only addon registration, Gift shortcuts, Trade/Inventory handoff, editor inventory and native cure must survive cleanup (Tasks 3–4).

---

### Task 1: Finish the deferred Work permission fix

**Files:** Modify `common/src/main/java/net/conczin/mca/entity/interaction/InteractionMenuCommandAccess.java`; test `neoforge/src/main/java/net/conczin/mca/dialogue/DialogueEngineGameTests.java`. Read existing `assets/mca/api/gui/{main,work}.json` and the linked finding; no UI rewrite.

**Interfaces:** Preserve `InteractionMenuCommandAccess.allows(VillagerLike<?>, ServerPlayer, String): boolean`. Registry builtin MENU predicates later reuse this method.

- [ ] **RED test:** Add `workPacketRequiresWorkMenuEligibility`. With valid owner/UUID/range, assert `harvesting` and `stopworking` cannot change a non-family/non-mercenary villager's job or ownership. Cover family baby denial, non-baby family acceptance and non-family mercenary acceptance.
- [ ] **Run RED:** `./gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=DialogueEngineGameTests#workPacketRequiresWorkMenuEligibility --console=plain --no-configuration-cache --max-workers=1`. Verify the permission assertion fails, not fixture/discovery setup.
- [ ] **Implement:** Gate `fishing`, `harvesting`, `hunting`, `chopping`, `stopworking` with `(Constraint.FAMILY && Constraint.NOT_BABY) || (Constraint.NOT_FAMILY && Constraint.MERCENARY)`. Leave Locations rules unchanged.
- [ ] **GREEN:** Rerun the focused test and `legacyMenuCommandRejectsOutOfReachAndPrivilegedActions` with eligible Work fixtures. Update the deferred finding's status only when behavior is verified. Review task-owned diff; commit only if authorized.

### Task 2: Introduce the shared command registration and real outcomes

**Files:** Create `common/src/main/java/net/conczin/mca/entity/interaction/InteractionCommands.java`. Modify `common/src/main/java/net/conczin/mca/entity/interaction/{VillagerCommandHandler,InteractionMenuCommandAccess}.java`; touch `EntityCommandHandler` only for necessary common executor access. Create `common/src/test/java/net/conczin/mca/entity/interaction/InteractionCommandsTest.java`; add behavior cases to `neoforge/src/main/java/net/conczin/mca/dialogue/DialogueActionGameTests.java`.

**Interfaces:** Reuse `VillagerCommandHandler.DialogueCommandResult(boolean accepted, boolean closeScreen)` and its factories. `InteractionCommands` defines `Source { MENU, DIALOGUE }`, `record Context(ServerPlayer player, VillagerLike<?> villager, UUID interactionId, Source source, String command)`, `register(ResourceLocation, Predicate<Context>, Function<Context,DialogueCommandResult>): void`, `canExecute(Context): boolean`, and `execute(Context): DialogueCommandResult`. An internal exact-alias resolver returns `Optional<ResourceLocation>`; no public parser framework. Canonical builtin execution is `VillagerCommandHandler.executeBuiltin(ServerPlayer,String): DialogueCommandResult`; it must not call back into registry dispatch.

- [ ] **RED registration tests:** Add `duplicateCommandsCannotReplaceHandlers`, `builtinAliasesAndAddonNamespacesStayDistinct`, and `unknownCommandsReject`. Assert duplicate/reserved builtin registration fails, `divorceConfirm` maps to `mca:divorce_confirm`, `profession.guard` remains one command, and `one:inspect.part` differs from `two:inspect.part`. Reject unknown bare names rather than manufacturing aliases. Register unique IDs per test; no public reset API.
- [ ] **RED behavior tests:** Add `commandSourceAndOwnerChecksPrecedeAddonExecution`: a permissive executor counter stays zero for foreign/stale/distant requests; source-aware dialogue-only handlers reject MENU, and menu-only handlers reject DIALOGUE. Test permitted execution once, read-only predicate denial, unknown IDs and callback exception without automatic closure. Assert insufficient emeralds, refused adoption/procreation and failed paper delivery return actual rejection; accepted non-closing apology/adoption are accepted independently of closure. Preserve intentional `rejected(true)` domain behavior.
- [ ] **Run RED:** `./gradlew.bat :common:test --tests '*InteractionCommandsTest' --console=plain --no-configuration-cache --max-workers=1`; run focused new action GameTests via `:neoforge:runGameTestServer -PmcaGameTest=DialogueActionGameTests`. Verify tests are discovered and fail for missing behavior.
- [ ] **Implement registry:** Register once during common initialization before resource loading/interaction handling; keep entries across resource reload. Reject null/duplicate IDs and public addon registrations under `mca`. Centralize live player/target, same-world, reach and current owner/UUID guards and callback exception handling. Caller supplies trusted Source; no source value is accepted from the client. `execute` rechecks before mutation even if `canExecute` was called earlier.
- [ ] **Implement canonical operations:** Route builtin registrations to the existing domain owners via `executeBuiltin`, sharing existing hire/procreate helpers and exact 5 emeralds/3 days and 10 emeralds/7 days values. Consolidate repeated branches; retain at most one canonical builtin dispatch switch. Do not infer acceptance from close-screen booleans or non-closing command lists. Existing wrappers may delegate during migration, without a second implementation. Builtin MENU predicates reuse `InteractionMenuCommandAccess`; DIALOGUE predicates preserve current relationship/age/cost rules and supported command scope. Preserve currently reachable builtin argument handling; don't split namespaced addon IDs generically.
- [ ] **GREEN:** Rerun registry and focused action tests. Confirm no new outcome record, second permission state or new effect codec was added. Record independent task result; commit only if authorized.

### Task 3: Connect the retained packet, dialogue command and addon buttons

**Files:** Modify `common/src/main/java/net/conczin/mca/network/c2s/InteractionVillagerMessage.java`, `common/src/main/java/net/conczin/mca/dialogue/DialogueEngine.java`, and `common/src/main/java/net/conczin/mca/client/gui/{InteractScreen,MCAButton}.java`. Test `neoforge/src/main/java/net/conczin/mca/dialogue/{DialogueActionGameTests,DialogueEngineGameTests}.java`; preserve `common/src/test/java/net/conczin/mca/dialogue/{DialogueEventParsingTest,McaDialogueEventResourcesTest,DialogueExtensionRegistrationTest}.java` and `common/src/test/java/net/conczin/mca/network/DialogueEventCodecTest.java`. Add `common/src/test/java/net/conczin/mca/client/gui/MCAButtonTest.java` using the existing client-capable common test harness. Update `docs/dialogue-{authoring,system-guide,datapack-guide}.md`.

**Interfaces:** Keep `InteractionVillagerMessage(String command,UUID villagerUUID,UUID interactionId)` and its existing payload ID/field order/codec bounds; construct `Context(...,Source.MENU,command)` on the server. Engine constructs `Context(...,Source.DIALOGUE,command)` only after existing offer/session/choice/ack checks. Preserve `DialogueAction.Command` codec and `DialogueAction.register(...)`. `MCAButton.command(): String` is nullable when absent; keep current constructor and add overload as needed.

- [ ] **RED integration tests:** Add `registeredCommandWorksFromMenuAndDialogue`: install `command_test:inspect`, execute through actual menu packet and authored `mca:command` completion, and assert permitted invocation/correct Source. Denied/wrong-source/stale completion calls leave the counter unchanged. Repeated final acknowledgement cannot execute twice. A refused builtin command must prevent accompanying hearts/history/registered callback execution; keep the existing maximum of one command per completion.
- [ ] **RED compatibility tests:** Keep all existing bundled `mca:command` JSON loading unchanged, including aliases/costs and spoken lines. Round-trip the existing string packet. Add `buttonCommandDefaultsToExistingIdentifier`, `addonButtonSendsExplicitCommand`, `blankExplicitCommandDoesNotFallBack`; preserve translation identifier, current constructor, navigation and Gift precedence. Test missing server addon rejects the packet safely.
- [ ] **Run RED:** `./gradlew.bat :common:test --tests '*DialogueEventParsingTest' --tests '*McaDialogueEventResourcesTest' --tests '*DialogueExtensionRegistrationTest' --tests '*DialogueEventCodecTest' --tests '*MCAButtonTest' --console=plain --no-configuration-cache --max-workers=1`; run the new integration GameTest selector. Confirm discovered new assertions fail before wiring.
- [ ] **Wire existing paths:** Packet resolves the target and delegates authority/execution to `InteractionCommands`. Dialogue commit resolves its retained command through the same registry before other effects/history, replacing `RELIABLE_DIALOGUE_COMMANDS` and old adapter dispatch. Use the returned accepted/close values; closure targets the matching UUID and preserves newly opened containers. Add optional button `command`; absent retains old identifier derivation, explicit values are sent unchanged, blank rejects. Replace no packet or bundled event JSON. Existing typed slap, custom typed actions and structure resolver keep their pipeline.
- [ ] **Document example:** Show one server `InteractionCommands.register` with an eligibility predicate inspecting trusted Source and an executor returning the existing result, the matching `{"type":"mca:command","command":"myaddon:inspect"}` and button JSON with `"command":"myaddon:inspect"`. Explain common-init timing, clients' layout/translation assets, server Java requirement, missing addon/unknown rejection, side/thread rules and no rollback after addon partial mutation. Keep existing condition/action/resolver examples; no forced migration table or arbitrary slash-command bridge.
- [ ] **GREEN:** Run integration GameTests and targeted common tests; compile both loaders with `./gradlew.bat :fabric:compileJava :neoforge:compileJava --console=plain --no-configuration-cache --max-workers=1`. Verify custom typed action execution still occurs once independently of command registration, and reload retains registered handlers while invalidating stale sessions. Review task-owned changes; commit only if authorized.

### Task 4: Remove duplicate internals and verify the preserved system

**Files:** Review `common/src/main/java/net/conczin/mca/entity/interaction/{EntityCommandHandler,VillagerCommandHandler,ZombieCommandHandler}.java`, `common/src/main/java/net/conczin/mca/item/VillagerEditorItem.java`, `common/src/main/java/net/conczin/mca/client/gui/{InteractScreen,MCAScreens,MCAButton}.java` and the existing command/dialogue tests and guides. Keep `InteractionVillagerMessage`, its registration, `DialogueAction.Command` and public extension APIs.

**Interfaces:** Keep `EntityCommandHandler.matchesInteraction(Player,UUID)` and `stopInteracting(Player,UUID)`. Internal obsolete `handle`/`handleDialogue` routing may be removed or reduced to a thin delegate when documented external callers require it; no second behavior implementation or permission bypass through a compatibility wrapper. Direct editor inventory must retain its tool-owned access independently of menu tokens.

- [ ] **Caller audit:** Search production/tests/resources/docs for old handler signatures, reliable/non-closing lists, dynamic `command`/`clothing` layouts and button helpers. Classify retained public command/schema as intentional supported functionality. Remove only redundant routing or proven-unused code; record addon-facing decisions explicitly.
- [ ] **Regression checks:** Add or preserve behavior tests for editor-item inventory and native zombie cure; retain Work and packet authority tests. Verify a wrapper, if retained, delegates to the shared guarded path rather than reconstructing an interaction token or bypassing source checks. Keep tool-owned inventory behavior at its existing owner.
- [ ] **Cleanup:** Delete duplicate switches/acceptance heuristics and unnecessary internal wrappers once callers migrate. Keep dynamic-layout hooks whose external contract remains in use or uncertain. Do not introduce aliases/fallbacks to obsolete engines or delete useful command code just to increase deletion counts.
- [ ] **Full verification:** Run `./gradlew.bat :common:test --console=plain --no-configuration-cache --max-workers=1`, both loader Java compiles, then focused `DialogueEngineGameTests` and `DialogueActionGameTests` via NeoForge. Confirm actual test execution. Verify dedicated-server startup and client Gift shortcuts, Trade, Inventory, Work, Locations, professions, editor item, dialogue success/refusal and one addon button. Client UI checks and server behavior checks are separate evidence.
- [ ] **Final report:** Measure production lines added/deleted and removed routing/entry points separately from tests/docs. Confirm `mca:command`, old JSON and packet remain supported, registration APIs still work, and only one canonical command pipeline remains. List intentionally retained wrappers/dynamic hooks and any unverified runtime cases. Preserve unrelated changes; commit only when authorized.

## Execution handoff

Review this revised design/plan before product implementation. Native task-by-task execution is recommended because tasks share a busy worktree and source owners. Task 1 is independently shippable; the API and cleanup follow after its permission boundary is verified. No production code or tests were changed/run to produce this document.
