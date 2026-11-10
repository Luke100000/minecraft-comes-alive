# Dialogue reply placeholders and asynchronous parchment results Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Show a single disabled three-dot reply row until character reveal is finished, show an indeterminate parchment progress bar during genuine async dialogue operations, and display results (including rumour coordinates) inline via reusable, datapack-configured resolvers.

**Architecture:** Keep server-owned session authority in `DialogueEngine` and character reveal in `DialoguePresentation`. Add optional typed `resolve` on a dialogue node, a server-thread-scoped resolver registry plus correlated pending/ready messages; replace only the dialogue rumour's deferred `location` command with a node resolver. Keep non-dialogue location command behavior intact.

**Tech Stack:** Minecraft 1.21.1, Java 21, shared Fabric/NeoForge, Mojang `Codec` / `StreamCodec`, CompletableFuture/CompletionStage, existing 9-sliced GUI.

**Spec:** `docs/superpowers/specs/2026-11-09-dialogue-pending-and-async-design.md` — **draft; implementation requires design approval**.

## Global Constraints

- Follow approved **Character reveal** and **Rumours search** variants; retain **40 ms per Unicode grapheme**. Do not insert a minimum wait or force instant text.
- One disabled grey three-square row while choices/Continue/Finish are unavailable. An indeterminate **parchment** bar appears only for server-confirmed true async operations, never for normal text reveal.
- New async event feature is optional and strictly decoded from `data/<namespace>/dialogue_events/*.json`; normal existing packs remain compatible. No arbitrary JSON scripts or commands.
- Results, lifecycle and rewards stay server-authoritative. Addon resolvers are namespaced and registered via Java during initialization. Results are shown in parchment, **never vanilla chat**, for the dialogue location path.
- Retain Java 21, Minecraft 1.21.1, existing common multiloader networking, scrolling, skin textures, history, HUD clearance and client-side physical separation. No new dependencies or worktrees.
- Preserve unrelated dirty changes already present in `InteractScreen.java`, `DialogueOverlayLayout.java`, their tests, and unrelated 2026-11-09 plans.
- Do not commit or execute these steps until the user has approved the design and an execution approach.

## Review Focus

1. **Duplicate, delayed or out-of-order callbacks** must not reveal an old result or duplicate effects (Task 2: `staleAsyncCompletionsAreDiscarded`).
2. **Close, pause, reload or shutdown during lookup** must terminate pending UI/state; explicit Resume may restart an idempotent lookup under a fresh token (Task 2: `leavingOrReloadingCancelsPendingWork`).
3. **Empty pool, unknown structure or provider exception** must show a localized fallback, not get stuck or claim fake coordinates (Tasks 1, 2, 5: `invalidResolveConfigIsRejected`, `failedAsyncOperationResolvesToFallback`, `emptyRumourPoolShowsFailure`).
4. **Non-async lines with no choices or only Finish** must show only one placeholder during reveal, then the proper action, without changing history (Task 4: `typingOnlyNodeHasOnePlaceholderUntilComplete`).
5. **Client dedicated-server boundary / codec size / unrecognized provider type** must fail safely without side-only class loading or incompatible loader registration (Tasks 1, 3: `unknownResolveTypeIsRejected`, `pendingPacketCodecRoundTrip`; final Fabric/NeoForge dedicated-server smoke test).

---

## File map / interfaces

| Owner | Files | Role |
| --- | --- | --- |
| Resolver schema | New `common/src/main/java/net/conczin/mca/dialogue/DialogueAsyncResolver.java`; update `DialogueEvent.java` | Namespaced resolver type registry, codec, validated node `resolve` config and result API. |
| Runtime | `DialogueEngine.java`, optional focused `DialoguePendingRequests.java` helper if lifecycle exceeds one owner | One request per session/player, timeouts, callback validity, result substitutions and completion barrier. |
| Protocol | New `common/src/main/java/net/conczin/mca/network/s2c/InteractionDialoguePendingResponse.java`, `MessagesMCA.java`, `ClientHandler.java`, `ClientHandlerImpl.java`; `InteractionDialogueSelectMessage.java`, `InteractionDialogueChoiceMessage.java` | Pending versus ready S2C events, correlation and client presentation. |
| Screen | `InteractScreen.java`, existing `DialogueOverlayLayout.java` only if geometry requires | Choice placeholder and animated square dots, progress bar in parchment, existing scroll/frames/accessibility. |
| Built-in lookup | `DialogueAsyncResolver.java` built-in registration / dedicated `StructureLocationDialogueResolver.java`, `social/rumors.json`, core lang assets | Structure lookup/provider, localized inline success/failure/pending, no deferred location command on this event. |
| Guides | `docs/dialogue-authoring.md`, `docs/dialogue-datapack-guide.md`, `docs/dialogue-system-guide.md` | Formal schema, copy/paste example and addon/network/lifecycle contract. |

### Cross-task contract

- `DialogueAsyncResolver.Result(boolean success, List<Component> arguments)` with factory methods `success(List<Component>)` / `failure()`. `Resolver<T>.resolve(T parameters, DialogueContext context) : CompletionStage<Result>`. `register(ResourceLocation id, Codec<T> codec, Resolver<T> resolver)` is available before datapack reload; reject duplicate/built-in IDs. Only read-only, idempotent resolvers are allowed in v1.
- `DialogueEvent.Node.resolve() : Optional<DialogueAsyncResolver.Definition>`; `Definition` holds typed provider parameters, `pendingLine` and `failureLine`, and provider ID. Success uses the node's `line` translation with provider arguments; failure uses `failureLine`. Single-line node only.
- `DialogueEngine.DialogueDisplay` is a sealed view: `Ready(DialogueNodeView view)` or `Pending(DialoguePendingView view)`. `DialoguePendingView(UUID sessionId, long offerToken, Component pendingLine)`. Transition `view()` becomes `display()`; selectors return an optional display instead of an optional ready-only view. Keep existing rejection/paused/ended statuses.
- `DialogueEngine` owns a pending ticket keyed by player UUID and bound to player/villager UUIDs, session UUID, token, reload generation, node ID and line index. On completion, it verifies the ticket on the **server thread** and sends a normal `InteractionDialogueNodeResponse.active` with a resolved Component. `InteractionDialoguePendingResponse` conveys `sessionId`, `offerToken` and localized `pendingLine`; no spoofed percentage.
- Client `DialoguePresentation.acceptPending(InteractionDialoguePendingResponse)`, `pendingAsync()` and `pendingLine()` expose the genuine pending phase; `awaitingReply()` and `revealingLine()` expose presentation-only waiting and typing. `acceptNode` clears matching pending state and rejects older packets; `pause`/`clear`/`beginRequest` clear stale pending UI.

## Task 1: Typed async resolver schema and datapack validation

**Files:**
- Create `common/src/main/java/net/conczin/mca/dialogue/DialogueAsyncResolver.java`.
- Modify `common/src/main/java/net/conczin/mca/dialogue/DialogueEvent.java`.
- Test `common/src/test/java/net/conczin/mca/dialogue/DialogueEventParsingTest.java` and `DialogueExtensionRegistrationTest.java`.

**Interfaces:** Produce `Definition`, `Resolver<T>`, `Result` and registration contract above; a visible node's `resolve` is optional and absent by default. Decode fields `type`, `pending_line` (optional localized default), `failure_line` (required), and provider-specific codec fields; reject unknown attributes. Node with `resolve` permits exactly one `line`, never `lines` or routing-only node.

- [ ] **Step 1: Add failing parsing tests** `asyncNodeRoundTripsWithoutChangingLegacyEvents`, `invalidResolveConfigIsRejected` (missing `failure_line`, conflicting `lines`, unexpected key), and `unknownResolveTypeIsRejected` (unregistered namespace). Add extension registry test that duplicate ID registration fails; no new default action semantics.
- [ ] **Step 2: Run targeted tests; confirm new cases fail.** `.\gradlew.bat :common:test --tests 'net.conczin.mca.dialogue.DialogueEventParsingTest' --tests 'net.conczin.mca.dialogue.DialogueExtensionRegistrationTest' --console=plain --no-configuration-cache --max-workers=1`.
- [ ] **Step 3: Implement** codec/registry and optional Node field. Follow existing `DialogueAction` registration style and `DialogueEvent` strict field validator, including registered-codec serialization and reload safety. Do not attempt to resolve client translation keys on server; validate nonblank syntax only.
- [ ] **Step 4: Re-run targeted classes, expect PASS;** inspect round-trip serialized JSON and legacy parser tests. Update `docs/dialogue-authoring.md` for schema, defaults and idempotency contract.
- [ ] **Step 5: Review this independently testable schema unit.** After approval to execute, commit only the Task 1 files (exclude pre-existing unrelated modifications).

## Task 2: Server pending lifecycle and authoritative async results

**Files:**
- Modify `common/src/main/java/net/conczin/mca/dialogue/DialogueEngine.java`.
- Add `common/src/main/java/net/conczin/mca/dialogue/DialoguePendingRequests.java` only if separating request ownership materially improves clarity; otherwise keep the map in `DialogueEngine`.
- Test `common/src/test/java/net/conczin/mca/dialogue/DialogueSessionTest.java` and `DialogueSelectionTest.java`; add focused `DialogueAsyncLifecycleTest.java` using a test resolver and controlled incomplete future.

**Interfaces:** Consume `Node.resolve` and `Resolver.resolve`; produce `DialogueDisplay` variants and pending ticket validation described above. Implement pending state in `select`/`choose`/`advance` and automatic node entry; `tick(MinecraftServer)` enforces a **30s** deadline, refresh/cancel on logout/reload; `pause`/`clear`/`onVillagerLeave` discard tickets. A server-thread continuation sends resolved node only for a matching current ticket, using `Component.translatable(node.line, result.arguments)` for success and `Component.translatable(failureLine)` otherwise.

- [ ] **Step 1: Write failing lifecycle tests** `pendingAsyncDisablesAdvanceAndChoices`, `asyncSuccessResolvesInlineAndOnlyThenAllowsFinish`, `failedAsyncOperationResolvesToFallback`, `staleAsyncCompletionsAreDiscarded`, `leavingOrReloadingCancelsPendingWork`, `resumeGetsFreshTicket` and `completionEffectsApplyOnceAfterResolution`. Include callback after death and a new villager session.
- [ ] **Step 2: Run focused tests; confirm the new cases fail.** `.\gradlew.bat :common:test --tests 'net.conczin.mca.dialogue.DialogueAsyncLifecycleTest' --tests 'net.conczin.mca.dialogue.DialogueSessionTest' --console=plain --no-configuration-cache --max-workers=1`.
- [ ] **Step 3: Implement** one ticket per player, safe lifecycle transition and timeout; marshal completed futures to the Minecraft server executor even if the source future currently completes on-server. Guard all updates and replies with current IDs/token/generation before editing the session, applying actions, or sending packets. Retain the resolved view during this node so duplicate requests do not restart the provider.
- [ ] **Step 4: Re-run focused tests, expect PASS;** verify ordinary completion and pause/Resume tests also remain green. Include simultaneous pending players and out-of-order completion cases.
- [ ] **Step 5: Review this independently testable server lifecycle unit.** After approval to execute, commit only related files.

## Task 3: Pending network payload and client state

**Files:**
- Create `common/src/main/java/net/conczin/mca/network/s2c/InteractionDialoguePendingResponse.java`.
- Modify `common/src/main/java/net/conczin/mca/network/{MessagesMCA.java,ClientHandler.java,ClientHandlerImpl.java}`, `network/c2s/{InteractionDialogueSelectMessage.java,InteractionDialogueChoiceMessage.java}` and adjust `InteractionDialogueAdvanceMessage.java` if its transition sender changes.
- Test `common/src/test/java/net/conczin/mca/network/{DialogueEventCodecTest.java,DialoguePresentationTest.java}`.

**Interfaces:** `InteractionDialoguePendingResponse(UUID sessionId, long offerToken, Component pendingLine)`, stream codec registered S2C; `DialoguePresentation.acceptPending`/`pendingAsync`/`pendingLine`/`awaitingReply`/`revealingLine`. Consume Task 2 `DialogueDisplay` in selection, advance and choice handlers; continue to send active NodeResponse on ready and existing paused/ended/rejected packets on other statuses.

- [ ] **Step 1: Add failing codec + presentation tests** `pendingPacketCodecRoundTrip`, `pendingToReadyMatchesToken`, `stalePendingCannotReplaceNewerNode`, `pendingClearsWhenLeavingOrBeginningMenu`, `typingNodeNeverClaimsServerAsync`. Verify short and non-ASCII Component content.
- [ ] **Step 2: Run focused tests; confirm the new cases fail.** `.\gradlew.bat :common:test --tests 'net.conczin.mca.network.DialogueEventCodecTest' --tests 'net.conczin.mca.network.DialoguePresentationTest' --console=plain --no-configuration-cache --max-workers=1`.
- [ ] **Step 3: Wire the common payload** and client handler, validate session/token correlation, preserve existing reply and character reveal semantics and never interpret mere network latency as a server-confirmed async bar.
- [ ] **Step 4: Re-run focused tests and compile both loaders;** use the repository's actual configured compile tasks (check Gradle task names first), expect no codec/side violations.
- [ ] **Step 5: Review the protocol unit**, especially packet size, ordering and dedicated-server classloading.

## Task 4: One grey reply row and parchment loading bar

**Files:**
- Modify `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java`; only modify `DialogueOverlayLayout.java` if bounds must change.
- Test `common/src/test/java/net/conczin/mca/network/DialoguePresentationTest.java` and `common/src/test/java/net/conczin/mca/client/gui/DialogueOverlayLayoutTest.java`; add narrow GUI-state test to `common/src/test/java/net/conczin/mca/client/gui/DialogueMenuAnimationTest.java`.

**Interfaces:** Consume Task 3 client presentation states. Use a non-clickable `DialogueRow` placeholder (no `DialogueClick` or hit target), containing three staggered animated squares; one row only, until fully revealed; a separate indeterminate bar inside the speech/parchment panel only when `pendingAsync()`. Keep normal row backgrounds/hover/featured layout unchanged.

- [ ] **Step 1: Add failing state/layout tests** `typingOnlyNodeHasOnePlaceholderUntilComplete`, `pendingAsyncShowsBothIndicators`, `readyAfterTypingShowsAllChoicesTogether`, `historyReviewHasNoActivePlaceholder` and `pendingStateKeepsFooterAndHudClear`. Cover empty choice list, Continue/Finish and tiny GUI sizes.
- [ ] **Step 2: Run the targeted classes; confirm new cases fail.** `.\gradlew.bat :common:test --tests 'net.conczin.mca.client.gui.DialogueMenuAnimationTest' --tests 'net.conczin.mca.client.gui.DialogueOverlayLayoutTest' --tests 'net.conczin.mca.network.DialoguePresentationTest' --console=plain --no-configuration-cache --max-workers=1`.
- [ ] **Step 3: Implement client rendering** with consistent progress strip placement for both parchment docking modes, no invented percent, no fabricated wait, no selectable placeholder, no scroll jumping. Honor Minecraft 1.21.1 GUI clipping/focus and reduced-motion accessibility. Keep controls otherwise unchanged.
- [ ] **Step 4: Re-run targeted classes and a client visual pass** (normal screen and docked parchment, short/long text, GUI scale, keyboard/mouse input, history, HUD and reduced motion); retain screenshot evidence for review.
- [ ] **Step 5: Review the UI unit.** Do not overwrite the unrelated, already-dirty `InteractScreen.java` changes.

## Task 5: Generic structure resolver, migrate rumours, and document datapacks/addons

**Files:**
- Create `common/src/main/java/net/conczin/mca/dialogue/StructureLocationDialogueResolver.java` (or a comparably focused built-in provider).
- Modify `common/src/main/resources/data/mca/dialogue_events/social/rumors.json`, related `common/src/main/resources/assets/mca_dialogue/lang/en_us.json` and the owning source for any additional localized pending/result keys; leave `VillagerCommandHandler.java`'s non-dialogue command behavior unchanged.
- Update `docs/dialogue-authoring.md`, `docs/dialogue-datapack-guide.md` and `docs/dialogue-system-guide.md`.
- Test `common/src/test/java/net/conczin/mca/dialogue/{DialogueEventParsingTest.java,McaDialogueEventResourcesTest.java}`; add or extend focused resolver tests in `DialogueAsyncLifecycleTest.java`. Add one datapack example fixture if existing testpack conventions permit.

**Interfaces:** Registered provider `mca:structure_location`. Exactly one selector: `pool:"configured_rumors"` using config structure pool or `structure:"namespace:id"` resolved against server structure registry; one read-only bounded `AsyncStructureLocator.locate` request with operation-unique owner ID. Resolver returns coordinates in success `%1$s`, or failure status. At lookup completion, send inline line via Task 2/3 flow. Migration removes `mca:command location` only from location outcomes; no chat packet from dialogue searches.

- [ ] **Step 1: Write failing tests** `rumourLocationUsesResolveNotDeferredCommand`, `emptyRumourPoolShowsFailure`, `invalidStructureIdFailsCleanly`, `validDatapackStructureResolverDecodes` and `noInlineRumourResultUsesVanillaChat`. Include a second addon-registered async provider and malformed JSON example to prove this is not special-cased to rumours.
- [ ] **Step 2: Run focused tests; confirm new cases fail.** `.\gradlew.bat :common:test --tests 'net.conczin.mca.dialogue.McaDialogueEventResourcesTest' --tests 'net.conczin.mca.dialogue.DialogueAsyncLifecycleTest' --console=plain --no-configuration-cache --max-workers=1`.
- [ ] **Step 3: Implement the provider and migration.** Reuse `AsyncStructureLocator`, avoid collisions with its owner-based cancellation, never read world/registry or mutate sessions from the worker; preserve existing rumour weights, non-location branches and repeat/history. Add localization strings with coordinates for success, pending and failure; do not start a search on final Finish.
- [ ] **Step 4: Update ALL THREE guides.** In `dialogue-authoring.md` document validated `resolve` fields, single-line restriction, `%1$s`, defaults and registered IDs. In `dialogue-datapack-guide.md` show a **copyable complete event JSON and matching resource-pack `en_us.json` keys**, activation, /reload and failure troubleshooting. In `dialogue-system-guide.md` document resolver API, server-thread scheduling, pending/ready payload lifecycle, timeouts/cancellation, vanilla-command compatibility, server authority, Fabric/NeoForge parity and addon Java registration. Make clear datapacks configure existing/installed async providers; they cannot invent executable handlers.
- [ ] **Step 5: Re-run focused tests, inspect localized keys and docs examples**, and perform end-to-end singleplayer and dedicated-server smoke tests: rumour search successful/missing/timeout, close/reopen mid-search, repeated clicks, no vanilla chat, no stale result in another villager's Talk window. Confirm Fabric/NeoForge behavior where supported by test environment.
- [ ] **Step 6: Review docs and migration as one independently testable unit**; commit only the relevant files when execution is approved.

## Final integration review (after implementation approval)

- [ ] Run `git diff --check` and inspect the combined diff against this spec. Confirm the pre-existing unrelated edits are not overwritten or staged.
- [ ] Run full relevant `:common:test` plus actual Fabric and NeoForge compile lanes; distinguish compilation success from runtime validation.
- [ ] Manually verify the two chosen visual modes and the in-game parchment bar with screenshots, including slow search, fast complete, reduced motion, long/wrapped responses, scrolling and HUD clearance.
- [ ] Confirm there is no path from dialogue location results to vanilla chat; no second asynchronous locate at Finish; old datapacks still load unmodified; invalid resolver JSON fails with a useful error.
- [ ] Ask for a final review of user-facing visuals and the documented datapack format before merging.

## Handoff / approval gate

This is a plan for review only. The user has **not** approved this design doc or requested code execution. Reconcile any design corrections and obtain approval, then choose **Subagent-driven** (independent implement/review per task; preferred for the server/network concurrency risk) or **Native** (one implementer plus whole-branch review). Stay within the existing worktree; don't start a second one. Do not treat previously staged unrelated changes as part of this plan.
