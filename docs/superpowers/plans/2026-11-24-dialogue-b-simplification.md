# Dialogue System B Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Simplify dialogue system B and close independently identified B-specific gaps while preserving its gameplay, presentation and addon contracts.

**Architecture:** Retain DialogueEngine, DialogueSession, DialoguePendingRequests, DialoguePresentation and InteractionCommands. Consolidate overlapping transitions and request state within these existing owners. Keep eligibility live at authoritative action boundaries and reuse results only within a single menu-building operation.

**Tech Stack:** Minecraft 1.21.1, Java 21, Fabric Loader 0.20.1 / Fabric API 0.116.17+1.21.1, NeoForge 21.1.265, existing Gradle/JUnit 6.1.3 configuration.

**Spec:** [Dialogue merge-readiness design](../specs/2026-11-24-dialogue-merge-readiness-design.md), B-side phases only. This plan supplements the spec with the second-pass findings below. The dev integration phase is explicitly deferred.

**Status:** Proposed, not executed. Reviewed B HEAD 7cda50286 and the existing dirty UI changes on 2026-11-24.

## Global Constraints

- Work only on dev/1.21.1-dialogue-system-b in its existing checkout. No merge, cherry-pick, port, push or new worktree as part of this plan. The target-branch reconciliation list remains in the combined spec.
- Preserve all unrelated and concurrent edits, especially the docked-layout screen, texture generator, generated texture, tests and documentation.
- Preserve existing packet formats, JSON schemas, data history semantics, translations, gameplay effects, fatigue parity, weighted choices and refusal behavior.
- Keep server-authoritative ownership, interaction/session tokens, action eligibility, generation invalidation, async timeouts and cancellation, and replacement-container safety.
- Keep server-only Java addon registration and authored datapack invocation. Preserve public methods and aliases with active or supported addon callers.
- Do not introduce a new engine, new general-purpose state framework, global eligibility cache, parallel command pipeline, new layout mode or unnecessary wrappers.
- Use existing focused tests and version-matched Minecraft owners. Do not run GameTests without renewed user authorization. Do not claim runtime acceptance from common tests or compilation.
- All test and commit steps below are future implementation work. Stage only reviewed task-owned changes. If concurrent changes share a file, coordinate before editing or staging. No implementation or commits are authorized by the preparation of this document.

## Second-pass review findings

| Priority | Evidence on B | Action |
| --- | --- | --- |
| P2 | InteractionDialogueBeginMessage.java:36–47 checks current owner; DialogueEngine.begin at 84–235 creates offers and can complete scheduling-only previews before using the full canInteract guard on the ordinary path. canInteract at 1487 checks alive, loaded interaction, range, trading and panic. | Apply the existing eligibility boundary before any initial menu mutations. A stale ownership window could otherwise generate an offer and consume preview cooldown. No remote exploit or observed gameplay failure established. Task 1. |
| P2 | EntityCommandHandler.java:95–115 calls broad pause before stopInteracting, which already invokes a scoped release hook. DialogueEngine.java:616–689, 760–809 and 1497 repeat transitions. | Centralize suspension/termination and eliminate redundant broad release behavior. Task 2. |
| P2 | InteractScreen.java:632–778, 1339–1378 holds reply and rendered identity state beside ClientHandlerImpl.DialoguePresentation. The latter's awaitingReply at 661–663 covers async, menu and selection but not Choice/Continue in full. | Give the presentation owner explicit request transitions for all actions before removing screen flag. Tasks 3–4. |
| P2 | DialogueEngine.java:148–168, 194–200 evaluates event eligibility for normal selection, opening preview and fallback ambient selection separately. eventEligible at 1180–1204 computes fatigue, conditions and history, with extension evaluators and lazy history pruning possible. | Reuse the eligible event list within one menu build. Preserve lower-tier ambient fallback after preview, refusal, ordering, and fresh checks when a player selects. Task 5. |
| P3 | DialogueAsyncResolver.java:117–142 has separate built-in and extension codec branches and fallback key inspection. | Simplify only if one existing codec registration path supports the same data and extension contracts. Task 6. |
| P3 | DialogueEngine.java:1550–1555 normalizes topics to 64 characters; InteractionDialogueOptionsResponse.java:35,123 independently defines the same wire limit. | Use one constant, preserve fallback to "other" for invalid topics. Task 7. |
| P3 | DialogueEngine.java:1335–1449 and other gameplay methods include delayed slap effects and coordinate styling among session/protocol work. | Move only responsibilities with a natural existing owner; retain scheduling and completion order. Task 7. |

The history audit found deliberate bounded rotating pruning of scheduling-only records, durable story choices and read-only preservation of unknown future schemas in DialogueEventHistory.java. DialogueEvents.java validates references after decoding and isolates malformed resources; existing tests cover reloads, reference handling and diagnostic cycles. Keep those behaviors. The full-scan pruning overload is used by tests; no supported external-caller audit establishes safe deletion. AsyncStructureLocator already bounds pending searches and handles cancellation. Its worker/chunk-ticket implementation is outside this cleanup absent an independently demonstrated issue.

B's legacy location command accepts an explicit registered ID while the pool is nonempty; the current shared dispatcher makes it dialogue-only. The existing combined design records the different dev allowlist policy. During Task 6, determine B's documented compatibility contract and callers, and avoid changing trusted authored async resolver semantics or importing dev's legacy policy by default.

No critical or high-priority defect was established by this static pass. Additional findings are source-based risks and maintenance improvements, not reproduced gameplay failures.

## Review Focus

- Initial Topics request arrives while ownership is still present but the player is out of reach or the villager is panicking/trading. No new offer, preview history or displaced session. Task 1.
- Release of a foreign villager or repeat release after a completed async reply. Preserve the newer session or valid resumable result; no extended deadline or duplicate cancellation. Task 2.
- Choice rejection, Continue rejection or a delayed response for an older offer while a newer request is pending. Loading and selectable controls match the current authorized state. Task 3.
- Language/resource reload or resize while viewing an older line, and partial clipping while scrolling. Rows, layout, copy targets and input remain synchronized. Task 4.
- Highest ambient tier has a sole opening preview, with a lower-tier eligible topic and an addon predicate counting evaluations. Preserve fallback, evaluate each event once in the menu operation and reevaluate on selection. Task 5.

---

## Test commands and working convention

Use PowerShell from repository root. For a targeted class, run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.dialogue.DialogueOwnershipReleaseTest'
```

Substitute the fully qualified test class given in each task. Pass multiple --tests arguments when batching independent cases. At the initial and final gates:

```powershell
.\gradlew.bat :common:test
.\gradlew.bat :fabric:compileJava :neoforge:compileJava
git diff --check
```

Confirm Gradle output and actual test reports. Record RED assertion failures and corresponding GREEN results. If a behavior-preserving refactor is already covered by passing characterization tests, record that honestly and do not manufacture a false RED. Inspect the version-matched source before changing vanilla-owned behavior. Future GameTest or client checks are explicitly identified and remain pending if not authorized.

### Task 0: Protect the baseline

**Files:** existing spec and docked-layout plan; common/build.gradle; gradle.properties; B source and existing tests.

- [ ] Record git status, HEAD, branch, local dirty files and test configuration. Coordinate the exact UI baseline and who owns current changes. Do not overwrite existing work.
- [ ] Read the combined design and the B-only exclusions in this plan. Review the existing docked-layout design and current source together.
- [ ] Run the initial common suite and both loader compile tasks. Save failures and tested revision/dirty state. If a concurrent change breaks the baseline, resolve coordination before touching that surface.
- [ ] Confirm no GameTests launched and no dev refs changed. This task changes no production files.

### Task 1: Enforce eligibility before starting a dialogue

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/dialogue/DialogueEngine.java` (begin and canInteract)
- Modify: `common/src/main/java/net/conczin/mca/network/c2s/InteractionDialogueBeginMessage.java`
- Test: `neoforge/src/main/java/net/conczin/mca/dialogue/DialogueEngineGameTests.java` (existing owned-interaction fixtures; execution gated)
- Test: existing common dialogue/protocol tests only if a pure regression can exercise a real owner boundary.

**Interfaces:** Introduce a checked server entry point `Optional<DialogueOptions> beginIfEligible(ServerPlayer player, VillagerEntityMCA villager, boolean showOpening)` if the existing return contract of begin cannot represent rejection. Keep existing begin overloads for verified direct callers and route packet and automatic reload refresh through the same checked boundary. Do not introduce a new packet or broad visibility change merely to call a predicate.

- [ ] Trace direct begin callers, packet handler and interaction tick/release. Verify that full eligibility is required for both initial opening and Topics refresh.
- [ ] Add focused regression cases using the existing GameTest fixture. Example assertions after making a still-owned interaction unavailable: `assertTrue(engine.beginIfEligible(player, villager, true).isEmpty())`; verify prior offer/session and preview cooldown unchanged. Cover valid owner, foreign owner, out-of-range and panicking/trading villager using actual fixture state. Prefer a handler-entry test when feasible. Do not add fake world helpers merely to avoid the runtime lane.
- [ ] Obtain authorization before running the runtime RED. Without it, record the check as unexecuted and perform only authorized source/common verification. Do not call a test passing because it compiled.
- [ ] Implement the single early eligibility gate before invalidation, preview selection, session mutation or history writes. Reuse canInteract. For a rejected legitimate screen request, use the existing ownership-scoped release/close handling where appropriate; never close another player's or a newer valid screen. Test client recovery rather than leaving indefinite loading.
- [ ] Run the approved regression and targeted common tests, then both loader compilation tasks. Inspect that valid server-pushed reload refresh continues working.
- [ ] Review and make a task-scoped commit during authorized execution, e.g. `fix(dialogue): validate initial interaction before building offers`.

### Task 2: Centralize session suspension and terminal cleanup

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/dialogue/DialogueEngine.java`
- Modify: `common/src/main/java/net/conczin/mca/entity/interaction/EntityCommandHandler.java`
- Reuse: `common/src/main/java/net/conczin/mca/dialogue/DialogueSession.java`, `DialoguePendingRequests.java`
- Test: `common/src/test/java/net/conczin/mca/dialogue/DialogueOwnershipReleaseTest.java`, `DialogueAsyncLifecycleTest.java`, `DialogueSessionTest.java`

**Interfaces:** Preserve `pause(ServerPlayer)`, `pause(ServerPlayer, UUID, UUID, long)`, `onInteractionReleased(ServerPlayer, UUID)` and the package-private `onInteractionReleased(UUID, UUID, long)` unless callers justify a narrower API. Preserve `DialoguePendingRequests.cancelPending(UUID)`, `invalidate(UUID)` and `resumeResolved(Key, Key)`. Use one engine-private operation for suspending an active session, such as `DialogueSession suspendSession(DialogueSession session, long gameTime)`, and keep map mutations with the current caller during tick iteration.

- [ ] Extend ownership characterization tests: in the existing fixture release at 1,000 then 1,100, assert the same paused session deadline 3,400 and rejected old offer 41. For foreign-villager release assert current session and offer remain identical. Extend async tests for completed result surviving temporary pause and resume, and removal on terminal invalidation; late completion must not overwrite a replacement ticket.
- [ ] Run DialogueOwnershipReleaseTest, DialogueAsyncLifecycleTest and DialogueSessionTest. Establish baseline/RED for any new behavior-specific assertion.
- [ ] Route begin suspension, explicit leave, scoped release, unavailable-villager pause and tick suspension through the shared operation. It cancels only in-flight work, leaves completed resumable results, and preserves existing deadlines. Keep terminal invalidation separate and remove retained results on actual end, expiry, reload or disconnect.
- [ ] Remove redundant broad pause calls in EntityCommandHandler replacement paths once the scoped release tests demonstrate matching behavior. Preserve explicit screen-close IDs, container handoff, notifications, existing paused/active semantics, and any required offer-only cleanup. Review direct callers before removing an obsolete private branch.
- [ ] Rerun the three common classes and DialogueSelectionTest. Add or extend the existing runtime cases for interaction replacement, old-screen close and replacement container handoff only where uncovered; mark runtime verification pending if unauthorized.
- [ ] Review and make a task-scoped commit, e.g. `refactor(dialogue): centralize session lifecycle transitions`.

### Task 3: Give DialoguePresentation ownership of reply state

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/network/ClientHandlerImpl.java` (DialoguePresentation and packet accept/reject paths)
- Modify: `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java`
- Test: `common/src/test/java/net/conczin/mca/network/DialoguePresentationTest.java`

**Interfaces:** Preserve `awaitingReply(): boolean`, `beginRequest()`, `markSelectionRequested(DialogueEngine.DialogueSelection)`, `markChoiceRequested(long, String)` and existing accept/rejection entry points. Add an explicit token-bound `markAdvanceRequested(long)` where Continue/Finish currently need the screen flag. Keep pending choice acknowledgement separate from generic waiting because it records the answer in history only after acceptance.

- [ ] Extend existing presentation fixtures: after marking Choice/Advance for the currently offered token, assert awaitingReply true and no selectable controls. Accept a newer matching node or matching rejection and assert the request clears/refreshes as appropriate. A stale token must not clear a newer wait. Verify pending async progress remains distinct, and pause/replacement clears only the correct presentation.
- [ ] Run DialoguePresentationTest and confirm failures for any missing wait transition. Existing tests already cover rejection, history acknowledgement, replacement, reload and bounded history; avoid duplicating them.
- [ ] Track reply correlation in DialoguePresentation using existing token and session fields. Mark advance and choice requests through the model before sending packets; release waits on the same accepted/rejected transitions that own presentation updates. Remove InteractScreen.dialogueAwaitingReply only after its consumers derive from presentation.awaitingReply(). Keep purely visual identity and hit-target clearing in the screen.
- [ ] Run DialoguePresentationTest, relevant existing GUI tests, then both loader compiles. Verify typing, async loading, reviewing history, and keyboard interaction in a client when possible.
- [ ] Review and make a task-scoped commit, e.g. `refactor(dialogue): own reply state in dialogue presentation`.

### Task 4: Avoid repeated UI row and text derivation

**Files:**
- Modify as justified: `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java`, `DialogueRowLayout.java`, `DialogueRichText.java`, `DialogueMenuScrollWidget.java`
- Reuse: `DialogueOverlayLayout.java`, `DialogueTextHitTest.java`, existing widgets and layout helpers.
- Test: `common/src/test/java/net/conczin/mca/client/gui/DialogueRowLayoutTest.java`, `DialogueRichTextTest.java`, `DialogueTextHitTestTest.java`, `DialogueOverlayLayoutTest.java`, `DialogueMenuScrollWidgetTest.java`, `DialogueMenuOrderingTest.java`.

**Interfaces:** Continue using existing row/render data and widget APIs. Keep dialogueRows/replyRows derivation local to the existing presentation/screen boundary; no public cache API. Retain independent speech and options scroll positions.

- [ ] Inspect render call paths with the latest docked-layout changes. Confirm stable translated/parsed content and wrapping actually repeat on idle frames; distinguish reveal animation and projection-dependent geometry.
- [ ] Characterize equal render/hit-test geometry after changed width, history page, response availability and translation/resource reload. Assert identical stable semantic row inputs reuse the derived result where a cache is justified, while changed inputs produce new layout. Use existing layout tests or narrowly scoped instrumentation; don't introduce test-only production state.
- [ ] Run affected GUI classes before changes. If the optimization cannot simplify existing derivation or demonstrate redundant work, retain the existing path and record the reason.
- [ ] Move stable work to the owning presentation or widget's existing update/invalidation boundary. Include node/offer/history, available choices, language/resource changes, font/width/scale and configuration in invalidation as relevant. Keep reveal/animation and dynamic positions per-frame; one set of clipped bounds for drawing and input.
- [ ] Rerun the named tests. Client verification at normal and large GUI scales: long translated speech, partial clipping, copyable coordinates, both scroll panes, wheel/drag/focus, history, pending dots and Finish. Check no layout regressions in the user's docked changes. Measure any claimed improvement using comparable idle/active frames.
- [ ] Review and make a task-scoped commit only if an improvement is retained, e.g. `perf(dialogue): reuse stable dialogue row derivation`.

### Task 5: Evaluate eligible events once per menu build

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/dialogue/DialogueEngine.java` (begin and selection helpers)
- Test: `common/src/test/java/net/conczin/mca/dialogue/DialogueSelectionTest.java`

**Interfaces:** Retain existing `planSelection(Collection<DialogueEvent>, Predicate<DialogueEvent>, ResourceLocation)` and `pickPreview` behavior. Introduce at most one request-local eligible candidate list. Reuse current selection helpers with already eligible candidates. The server action paths continue re-evaluating conditions live.

- [ ] Add a counting-predicate characterization for the combined menu build: two eligible ambient events in distinct priority tiers plus an ineligible event, with the sole high-priority event picked for preview. Assert each candidate is evaluated once, preview is excluded from ambient candidates, the lower tier remains selectable, paused event is excluded and ineligible event never appears. Reuse the existing event-building fixtures and injected random.
- [ ] Run DialogueSelectionTest. Confirm the counting test exercises the actual combined menu path; a synthetic helper test disconnected from begin is insufficient.
- [ ] Refactor begin to derive the eligible event collection once per operation, preserving its order-independent canonical sorting and the existing fatigue/refusal rules. Reuse the collection to classify highlighted/ask/ambient and preview candidates, and derive fallback after removing the selected preview. Preserve the 64 wire option limit. Reuse candidate membership rather than storing a cross-request cache. Keep separate preview-start choice checks and later authoritative action eligibility.
- [ ] Rerun DialogueSelectionTest, DialogueRecentEventExtensionTest and DialogueEventHistoryTest. Verify disabled fatigue, zero-weight alternatives, lower-tier fallback, one-shot/expired cooldown semantics, and choice requirement changes.
- [ ] Review and make a task-scoped commit, e.g. `refactor(dialogue): reuse eligible candidates within menu generation`.

### Task 6: Review resolver codec and legacy lookup overlap

**Files:**
- Inspect/modify if warranted: `common/src/main/java/net/conczin/mca/dialogue/DialogueAsyncResolver.java`, `DialogueAction.java`, `common/src/main/java/net/conczin/mca/entity/interaction/VillagerCommandHandler.java`, `InteractionCommands.java`
- Test: `common/src/test/java/net/conczin/mca/dialogue/DialogueExtensionRegistrationTest.java`, `DialogueEventParsingTest.java`, `DialogueAsyncLifecycleTest.java`, `common/src/test/java/net/conczin/mca/entity/interaction/InteractionCommandsTest.java`

**Interfaces:** Preserve all existing public register methods, ResourceLocation identifiers, codecs, JSON field names, resolver/provider cancellation signatures, legacy aliases and command result contract.

- [ ] Audit current registration paths, callers and docs for built-in and external resolvers and the legacy location command. Verify B's intended explicit-structure contract before proposing any restriction; leave dev-specific policy to the deferred integration spec unless a B-local defect is demonstrated.
- [ ] Characterize built-in and addon serialization round trips with explicit default values, unknown-field rejection, duplicate registration, registered type dispatch, resolver cancellation, and failure fallback. Reuse the existing extension tests. Include a non-MapCodec wrapped codec to preserve currently supported behavior.
- [ ] Run DialogueExtensionRegistrationTest, DialogueEventParsingTest, DialogueAsyncLifecycleTest and InteractionCommandsTest. Retain existing strictness rather than weakening validation to reduce code.
- [ ] If the existing registration machinery can handle built-in structure_location with fewer special cases, use it and remove the duplicate branch. Otherwise keep current design. If legacy and authored searches truly share lookup mechanics, reuse only registry validation or search setup that is identical, maintaining independent authorization, output and cancellation. Do not delete public legacy methods without supported-caller evidence. No new dispatch framework.
- [ ] Rerun the same tests and both loader compiles. Update authoring/API docs only for a real contract change. Record deferred candidates with reason.
- [ ] Review and make a scoped commit if code changes, e.g. `refactor(dialogue): simplify resolver codec dispatch`.

### Task 7: Narrow engine responsibilities and small consistency cleanup

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/dialogue/DialogueEngine.java`, `DialogueCoordinates.java`, `common/src/main/java/net/conczin/mca/network/s2c/InteractionDialogueOptionsResponse.java`
- Inspect: `common/src/main/java/net/conczin/mca/entity/interaction/VillagerCommandHandler.java`, `DialogueAction.java` for gameplay ownership
- Test: `common/src/test/java/net/conczin/mca/dialogue/DialogueTextCoordinatesTest.java`, `DialogueSelectionTest.java`, `common/src/test/java/net/conczin/mca/network/DialogueEventCodecTest.java`

**Interfaces:** Keep public packet constant `InteractionDialogueOptionsResponse.MAX_TOPIC_LENGTH` (64), but derive it from a common owner used by DialogueEngine rather than duplicating 64. Keep topic fallback `"other"`. Retain current coordinate copy/text and slap effect contracts.

- [ ] Add boundary coverage for absent topic, exactly 64 characters, and 65 characters mapping to "other"; verify resulting packet round-trips. Use existing event and codec fixtures. Preserve valid topic identifiers and localization.
- [ ] Inspect existing coordinate tests and delayed slap entry points, tick scheduling, invalidation and effect ordering. Add behavior assertions only for uncovered cases, especially one-time delayed effect and preservation across unrelated screen closure where existing behavior requires it.
- [ ] Run DialogueTextCoordinatesTest, DialogueSelectionTest and DialogueEventCodecTest. Runtime slap verification remains gated.
- [ ] Define the shared topic limit in an existing common dialogue owner and retain the public packet alias. Move coordinate styling into DialogueCoordinates if that removes unrelated formatting from engine. Move delayed gameplay scheduling only if an existing owner can preserve tick order, player/villager identity, lifecycle and one-time execution with less coupling; otherwise retain the engine path and document why.
- [ ] Rerun the relevant common tests and both loader compiles. Verify no source-level API or gameplay changes caused by extraction.
- [ ] Review and make a task-scoped commit for the retained changes, e.g. `refactor(dialogue): consolidate topic and text responsibilities`.

### Task 8: Whole-B review and acceptance

**Files:** B-owned changes from Tasks 1–7, relevant tests and existing docs. No dev merge or content rewrite.

- [ ] Review each task against this plan and the B-side design requirements. Confirm that new interfaces have real callers, obsolete internal flags/branches are gone, and changes reduce complexity at the owner rather than simply relocating it.
- [ ] Run the full common suite, both loader compilation tasks and git diff --check on the same identified B state. Inspect actual failures/test counts. Check JSON, addon registration and protocol compatibility through existing test coverage.
- [ ] Review complete B changes for token ownership, live selection eligibility, same-dimension handling, refusal/fatigue, resolved Rumours retention, late callbacks, terminal cleanup, replacement-container safety and unchanged non-dialogue interactions.
- [ ] Verify focused dedicated server/client gameplay and agreed visual layout when authorized. Record separate loader/runtime results and remaining gaps; do not claim live readiness without live evidence.
- [ ] Keep the existing natural-dialogue content work separate: [quality audit](../specs/2026-11-12-selectable-dialogue-content-quality-audit.md), [content plan](2026-11-11-natural-dialogue-conversations.md). Existing topic/profession authoring work does not require a new engine and is not silently included in this code refactor.
- [ ] Report B's final source revision, changed files, verification evidence and any declined optional extraction. Leave B integration with dev as a separate future decision; use the combined merge-readiness spec when that work is requested.

## Acceptance summary

B is simpler when one authoritative operation governs each session transition, one client owner correlates each outgoing request, and eligibility is evaluated once per menu generation while remaining fresh at action time. Keep established resource history, extension and protocol behavior. A smaller class count or line count is not a pass condition. Optional codec, UI performance and gameplay extraction changes are retained only if they demonstrably simplify behavior or work. No dev reconciliation or final merge is included.
