# Blueprint and Floor Consistency Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task in the existing checkout. Steps use checkbox (`- [x]`) syntax for tracking. Do not create worktrees or delegate implementation without authorization.

**Goal:** Reduce competing Blueprint/floor decisions by retaining one shared selection policy and explicit target through server execution, while sharing repeated room reconstruction.

**Architecture:** Keep `SelectedFloorScanner` → `FloorGeometry` → `RoomPartitioner` → `RoomScanPlanner`/`RoomScanPlan`. Blueprint projects the plan; `RoomWorkflow` consumes operation-local evidence; `VillageManager` validates and publishes the planned operation through existing Village mutation methods. Extend existing result carriers rather than introduce a context/framework.

**Tech Stack:** Minecraft 1.21.1, Java 21, Gradle wrapper, common JUnit/Minecraft bootstrap tests, NeoForge GameTests, Fabric and NeoForge compilation.

**Spec:** `docs/superpowers/specs/2026-11-02-blueprint-floor-consistency-design.md` (read in full before implementation).

## Global Constraints

- Work in the existing `dev/1.21.1` checkout; no WSL or new worktrees without consent.
- Preserve unrelated dirty work. User authorized native implementation and related commits on 2026-11-02; no push is authorized.
- Use existing production classes; no class extraction or new top-level production classes.
- Keep the existing Blueprint controls, packet formats, type-choice flow, and save format.
- Add no server-preview protocol, per-player plan store, version/token system, synchronization framework, asynchronous scan executor, or new cache layer.
- Keep scanning and mutation on their existing threads; live server world access remains on the server thread.
- Keep current scan budgets, loaded-column guards, and bounded hole tolerance.
- Production Java should shrink overall. Report scoped production additions/deletions separately from tests; do not delete required behavior to meet a line target.

User approved execution natively and related commits on 2026-11-02. Implement inline in this checkout; stage only files belonging to this plan.

## Review Focus

1. Same X/Z at different heights, stair rows, unsupported handoffs, and shared doors must not borrow registered room identity (Tasks 1, 3).
2. Type confirmation must revalidate movement, transmitted expected identities, current physical validity and chosen type; reject incompatible/stale requests without mutation (Tasks 3, 4).
3. A rejected scan/commit must leave IDs, saved structures, sibling rooms, and publication state unchanged (Tasks 4, 5).
4. A copied village snapshot with identical world evidence must produce the same action/target; an old preview is not permission to bypass server validation (Tasks 1–4).
5. Slabs, beds, shelf obstructions, bounded wall/roof holes, and unavailable columns must retain their geometry, area, rejection and work-budget guarantees (Tasks 1, 6).

## Baseline and file map

Repository root: `C:/Users/Mik/Downloads/MCA/minecraft-comes-alive-1.21.1-floor-clean-squash`.

Baseline inspected while writing: `37a2a9fde`. Recheck HEAD and dirty work before execution. The earlier common-suite count was 684; do not hard-code it as the expected final count. These two planning documents were explicitly committed at user request in `6800cddd5`; other ignored planning artifacts remain unrelated.

| Existing production file | Planned responsibility/change |
| --- | --- |
| `common/src/main/java/net/conczin/mca/client/gui/BlueprintScreen.java` | Project only the supplied plan into scan requests; remove the separate mode argument. |
| `common/src/main/java/net/conczin/mca/server/world/data/RoomScanPlanner.java` | Preserve sole action-selection ownership; change selection only for a reproduced inconsistency. |
| `common/src/main/java/net/conczin/mca/server/world/data/RoomScanPlan.java` | Preserve the current immutable decision and constructor invariants. |
| `common/src/main/java/net/conczin/mca/server/world/data/RoomWorkflow.java` | Preserve planned seed/target, reuse available evidence, pass plans through materialization, share registered-room reconstruction. |
| `common/src/main/java/net/conczin/mca/server/world/data/BuildingScanResult.java` | Carry the explicit plan; remove mode/target inference. |
| `common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java` | Dispatch addition by plan and validate its prepared payload; preserve publication/ID ownership. |
| `common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java` | Existing scan/validation APIs; modify only if an existing-result adapter is needed to preserve seed/provenance. |
| `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java` | Existing geometry authority; modify only for a demonstrated position-resolution regression. |

`Village`, save codecs, packet codecs, `FloorGeometry`, `FloorCeilingResolver`, and `IndoorRoomCache` are retained owners, not rewrite targets. Network feedback/continuation classes are verification surfaces; no wire changes are planned.

## Task 1: Pin the shared selection contract

**Files:**

- Test: `common/src/test/java/net/conczin/mca/server/world/data/RoomScanPlannerTest.java`
- Test: `common/src/test/java/net/conczin/mca/server/world/data/BlueprintSnapshotProtocolTest.java`
- Test: `common/src/test/java/net/conczin/mca/server/world/data/SelectedFloorScannerConnectorTest.java`
- Runtime test: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`

**Interfaces:** Consume existing `RoomScanPlanner.analyze(...)`, `analyzeFresh(...)`, `SelectedFloorScanner.Observation.selected(BlockPos)`, `RoomScanPlan`, and village save/load. Produce reusable fixtures/assertions in these existing test classes; no new production interface.

- [x] Record branch/HEAD, scoped diff, and current test baseline. Read this spec alongside the September 17 selected-room and September 25 publication constraints; use current source for actual APIs.
- [x] Add `equivalentInteriorSourcesKeepCanonicalFloorAndAction`: independent observations of ordinary/slab/bed/shelf positions in one unchanged enclosed-room fixture must have equal `sameExactGeometry`, `anchorY`, selected component cell set, mode, structure/floor target, and room ID where registered. Test unregistered and registered copies separately so one successful addition does not change the next input's state. Run both query orders with a shared observation too.
- [x] Add `snapshotAndServerPlannerAgreeForIdenticalEvidence` to the snapshot suite: encode/decode the existing village response, then run the same shared planner against both village instances with identical fresh evidence. Compare `mode`, selected room ID, structure/floor IDs, attachment target and prospective floor number. Obtain real door orientation through the world-aware path for the door case rather than passing `null` and assuming it proves live boundary parity.
- [x] Keep explicit negatives for wrong-height cells, unregistered expansion, unsupported handoffs, door-only overlap, stairs, and ambiguous floors. Existing tests named in R1 remain authoritative; do not replace their expectations with broad overlap-based ownership.
- [x] Add/extend the existing runtime fixture to cover equivalent interior source selection after village registration and save/load. Use the fixture's physical roof, with assertions on setup; do not let the arena ceiling close an intentionally open roof.
- [x] Run focused tests:

```powershell
.\gradlew.bat :common:test --tests '*RoomScanPlannerTest' --tests '*BlueprintSnapshotProtocolTest' --tests '*SelectedFloorScannerConnectorTest' --console=plain --no-configuration-cache --no-parallel --no-daemon '-Dorg.gradle.jvmargs=-Xmx2G'
```

Existing behavior may already pass characterization tests. For any actual selection bug exposed, record the failing input/result before changing production, make the smallest fix inside the current owner, and rerun the same case plus negative boundaries. Do not manufacture a RED result for a behavior-preserving refactor.

**Checkpoint:** Distinguish confirmed failures from passing invariants and unsupported boundary positions. No claim of client/server divergence is made from snapshot parity tests alone.

## Task 2: Make Blueprint scan requests accept only a plan

**Files:**

- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintScreen.java`
- Test: `common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java`

**Interfaces:** Replace `structureScanMessage(Village.RoomScanMode, RoomScanPlan)` with package-private `static ReportBuildingMessage structureScanMessage(RoomScanPlan plan)`. Replace the private sending helper with `sendStructureScan(RoomScanPlan plan)`. No packet fields change.

- [x] Extend existing message projection tests to cover the spec's four-row action/expected-target matrix. Assert both action and expected ID; an attachment's logical-building ID must not become a structure/floor/room ID.
- [x] Remove the independent mode parameter and update all call sites/tests. The primary button, label and packet projection read the same plan mode. Keep projection-to-translation switches local to Blueprint.
- [x] Preserve `setVillage` invalidation, position-based caching, floor-selection UI, removal controls and inheritance controls. Do not add timers, listeners, preview requests or a new cache.
- [x] Run `:common:test --tests '*BlueprintScreenMapInteractionTest' --tests '*BlueprintSnapshotProtocolTest' --tests '*ReportBuildingMessageCodecTest'` with the common command flags from Task 1. Confirm existing packet round trips and UI target-selection tests pass.

**Checkpoint:** There is no scan-message API that can pair one plan with another mode; unrelated edit actions retain their current behavior.

## Task 3: Preserve the planned seed and operation-local evidence on the server

**Files:**

- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomWorkflow.java`
- Modify if needed: `common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java`
- Test: `common/src/test/java/net/conczin/mca/server/world/data/RoomWorkflowTest.java`
- Test: `common/src/test/java/net/conczin/mca/network/c2s/BuildingScanContinuationTest.java`
- Runtime test: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorBatchRefreshGameTests.java`

**Interfaces:** Add private `RegisteredRoomUpdate analyzePlannedRoomUpdate(Village village, RoomScanPlanner.Analysis analysis)`. Change private `updateRoom` to accept `(Village village, RoomScanPlanner.Analysis analysis, int expectedRoomId, String selectedType)` instead of a separately selected `Building` and source. Extend private `analyzeRegisteredFloor` to `(Village village, Structure structure, Building expected, BlockPos source, StructureScanner.Result observed, List<RoomPartitioner.Component> observedComponents)`, with `null` components meaning partition the validated fresh scan here. Preserve the existing package-private `analyzeRegisteredRoomUpdate(..., StructureScanner.Result observed)` diagnostic entry point.

- [x] Extend workflow tests with `recoveredUpdateConsumesPlannedSeedAndObservation` and `registeredFastPathScansOnlyWhenExecutionNeedsFreshEvidence`. Assert selected room/seed and unchanged state on failure; reuse the existing diagnostic observation-reuse runtime fixture for attributable scan work. Count actual world/discovery work at the established seam rather than inventing a new public counter API.
- [x] Route the `UPDATE_ROOM` case through its existing `Analysis`. If it contains fresh evidence, validate it using `StructureScanner.resultFromObservedFloor` with the plan's exact structure/floor and existing ignored-structure/logical-building rules. Use `plan.scanSeed()` for component selection and retain `plan.interactionSource()` for feedback/continuation provenance.
- [x] Allow the private registered-floor analysis helper to accept the already-computed component list alongside validated observed evidence. When none exists, retain the current `scanExistingFloor` and world-aware partition path. Never supply a partition from another scan or from a rejected fresh plan.
- [x] Keep the cheap no-observation registered-room preview path. Preserve explicit request policy: `SCAN_ROOM` with a nonnegative expected room requires that room or returns `TARGET_CHANGED`; with `-1`, an already registered room yields `IDENTICAL`. `ADD_BUILDING` retains its established attachment compatibility and overlap rejection; `ADD_ATTACHMENT` requires the expected logical building. Do not execute another action merely because replanning suggests it.
- [x] Confirm type-choice continuation re-enters the same request path with fresh evidence, retains the existing source-position check, and makes no mutation for movement, changed transmitted expected IDs, physical rejection or an invalid current type choice. Add Room confirmation has expected ID `-1` and therefore replans the current component rather than pinning the earlier one; attachment pins its logical building and recomputes its floor number. Do not add tests promising identity information the packet never sent. Keep existing feedback categories; physical scan failure must not be relabeled `TARGET_CHANGED`.
- [x] Run `:common:test --tests '*RoomWorkflowTest' --tests '*BuildingScanContinuationTest' --tests '*RoomScanPlannerTest'` with Task 1 flags. Run the existing diagnostic observation-reuse fixture as part of the Task 6 refresh suite.

**Checkpoint:** An operation reuses valid evidence it already owns; a later network request/confirmation obtains fresh evidence. Seed, anchor and requested source retain distinct meanings.

## Task 4: Carry the explicit plan through materialization and commit

**Files:**

- Modify: `common/src/main/java/net/conczin/mca/server/world/data/BuildingScanResult.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomWorkflow.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java`
- Test/update constructors: `common/src/test/java/net/conczin/mca/server/world/data/RoomWorkflowTest.java`
- Test/update constructors: `common/src/test/java/net/conczin/mca/server/world/data/VillageManagerExpandedRoomCommitTest.java`
- Test/update constructors: `common/src/test/java/net/conczin/mca/server/world/data/RoomTypeResolverTest.java`
- Update other discovered Java constructor callers mechanically; do not retain guessing constructors to keep tests unchanged.

**Interfaces:** Append `RoomScanPlan plan` to the canonical `BuildingScanResult` record constructor. Provide a six-argument convenience constructor `(result, source, building, matchingTypes, village, plan)` delegating to `pendingStructure = null`; remove the old constructors lacking a plan. Success requires a non-null plan. Failures may have no resolved plan and remain non-committable. Existing `source()` feedback semantics remain unchanged. Add a `RoomScanPlan plan` parameter to the private `materializeRoom` and `roomResultFromGeometry` methods; callers pass the selected plan, including `UPDATE_ROOM` for intermediate registered-room materialization.

- [x] Add `commitRejectsPayloadThatDisagreesWithPlan` and `successfulAdditionRequiresExplicitPlanAndPreparedStructure`. For a selected structure 10/floor 0, a payload for structure 11 must fail without room publication or ID consumption; changing the pending ID cannot turn `ADD_ROOM` into `ADD_BUILDING`. Assert a successful result cannot be built without a plan. Reuse existing publication-failure tests to prove prepared results remain reusable after rollback.
- [x] Thread the existing plan through materialization. For explicit new-building/reporting operations where no selection plan exists, create `RoomScanPlan.addBuilding(originalInteractionSource)` from that known operation; never infer it from the prepared structure. Preserve intentional report-neighbor discovery behavior.
- [x] Make `withSource` and `withPendingStructure` preserve the plan. Derive `targetBuildingId()` solely from an attachment plan, otherwise `-1`; failures without a plan return `-1`.
- [x] Switch `VillageManager.commitRoomAddition` on `scan.plan().mode()`. Require a valid prepared structure at this boundary. `ADD_ROOM` validates the existing structure/floor target and uses `commitExpandedRoom`; `ADD_BUILDING`/`ADD_ATTACHMENT` use `commitInitialRoom` with existing ground/attachment and numbering checks. Reject an `UPDATE_ROOM` result here with `NOT_IN_BUILDING` and no mutation; registered-room commits retain their own existing entry point.
- [x] Remove the bare-room/no-prepared-structure addition branch and negative-ID-based action dispatch. Negative provisional IDs may still be validated/allocated as persistence mechanics, not interpreted as an alternate operation plan.
- [x] Preserve expected target checks, forced-type validation, floor-number revalidation, atomic publication and rollback. Search every `new BuildingScanResult` and every `commitRoomAddition` caller to confirm no production path is silently dropped.
- [x] Run `:common:test --tests '*RoomWorkflowTest' --tests '*VillageManagerExpandedRoomCommitTest' --tests '*RoomTypeResolverTest' --tests '*BuildingScanContinuationTest'` with Task 1 flags. For behavior fixes, demonstrate the relevant assertion failing before the fix; constructor/projection consolidation may use passing characterization tests.

**Checkpoint:** The manager validates a named operation and its payload; it does not choose an operation from absent fields. The packet/save formats remain untouched.

## Task 5: Share registered-room reconstruction without merging update policies

**Files:**

- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomWorkflow.java`
- Test: `common/src/test/java/net/conczin/mca/server/world/data/RegisteredRoomUpdateTest.java`
- Test: `common/src/test/java/net/conczin/mca/server/world/data/VillageManagerExpandedRoomCommitTest.java`
- Runtime test: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorBatchRefreshGameTests.java`

**Interfaces:** Add one private `BuildingScanResult materializeRegisteredRoom(Village village, Structure refreshed, StructureFloor floor, RoomScanPlan plan, RoomPoiEvidence.Index evidence, RoomPartitioner.Component component)` inside `RoomWorkflow`. Require `plan.mode() == UPDATE_ROOM`; obtain the original room from `plan.currentRoom()`. Reuse existing materialization and detached result types; add no new result/context class.

- [x] Extend existing selected-update and batch tests to assert equal reconstruction rules for IDs, structure/floor ownership, forced type, automatic type behavior and `contributesToMain`. Compare persisted sibling-room data before/after an individual update.
- [x] Move common component materialization and stable metadata copying into the private helper. Both single-room and full-floor paths call it. The single path passes its real plan; the batch creates an update plan for each matched registered room using that room's source.
- [x] Reuse `RoomPoiEvidence.Index` once per partition. Keep full-floor one-to-one matching and prospective batch type resolution in the batch caller; keep single-room overlap/sibling checks and type choice in its caller. Do not add flags to combine those policies.
- [x] Retain existing `refreshedStructure`, `BuildingRoomScanner.components`, and `StructureScanner.scanExistingFloor` helpers instead of adding another preparation layer. All materialization remains detached until existing publication succeeds.
- [x] Run the selected-update tests from Task 4 plus `*RegisteredRoomUpdateTest`. Exercise full-floor merge/split/lost-identity/unregistered-component and rollback behavior in the native suite below.

**Checkpoint:** Shared reconstruction has one implementation; an individual edit still changes only its selected room. Full scan still handles the registered floor atomically and preserves rollback.

## Task 6: Verify the complete flow and review the simplification

**Files:** Existing test suites above, plus `ReportedFloorInteractionGameTests`, `IndoorRoomCacheGameTests`, and existing scanner hole/performance tests. Update this plan with actual results when implemented.

- [x] Run all common tests and both loader compilations:

```powershell
.\gradlew.bat :common:test :fabric:compileJava :neoforge:compileJava --console=plain --no-configuration-cache --no-parallel --no-daemon '-Dorg.gradle.jvmargs=-Xmx2G'
```

- [x] Run each focused native class sequentially. Use this command with each exact class name: `FloorScannerGameTests`, `FloorBatchRefreshGameTests`, `ReportedFloorInteractionGameTests`, `IndoorRoomCacheGameTests`.

```powershell
.\gradlew.bat :neoforge:runGameTestServer '-PmcaGameTest=FloorScannerGameTests' -I neoforge/shelf-collision-gametest.init.gradle --console=plain --no-configuration-cache --no-parallel --no-daemon '-Dorg.gradle.jvmargs=-Xmx2G'
```

Expected: exit 0, zero required test failures, and the intended class registered. Record actual counts for every run. Inspect named full-suite failures instead of treating focused passes as a substitute. Avoid concurrent Gradle runs; if another task owns the output/runtime directories, coordinate or isolate outputs with the existing supported mechanism, not a new worktree.

- [x] Review the scoped production diff for reuse, quality, correctness and efficiency. Trace the client button → packet action/expected ID → server planner → selected component → commit → returned snapshot. Check normal boundaries, type confirmation, attachment numbering, failed publication, and cheap registered lookup. Fix material findings and rerun affected checks only.
- [x] Run `git diff --check` and scoped production `git diff --numstat`; report net production reduction separately from tests. Confirm no new top-level production classes, packet/save fields, preview protocol, cache layer, or geometry fallback appeared. Preserve unrelated work and do not stage/commit it.
- [x] If a normal client/server instance is available for authorized testing, compare two ordinary positions and the original shelf/window positions before and after registration, then after response refresh and save/reload. Use a separate test world; do not alter the user's source schematic or saves. Otherwise report that ordinary gameplay remains unverified. A schematic replay using substitute mod blocks is supplementary evidence with explicit limitations.

## Completion record

Completed 2026-11-02, natively in the existing checkout.

- Reviewed implementation HEAD: `28220d932`, following `1c958ac94` (position/snapshot characterization), `4859ecf1d` (Blueprint projection), `3c106a80f` (planned update evidence), and `38d49427a` (explicit commit plan). The unrelated dirty AI/navigation work was excluded from all related commits.
- RED/GREEN evidence: the new one-argument projection contract initially failed compilation; the captured-update native regression initially lacked its evidence-consumption path; two commit regressions returned `SUCCESS` for an inconsistent/unprepared payload before the fix; attachment-without-village rejection initially consumed a village ID. All now pass. Existing geometric ownership negatives remain intact.
- Full common suite: **690 tests, zero failures/errors/skips**.
- Native suites: **FloorScanner 106/106; FloorBatchRefresh 15/15; ReportedFloorInteraction 2/2; IndoorRoomCache 10/10; ReportedStairHouse 9/9**. Total **142/142**; each Gradle run exited 0 and registered the intended focused class.
- Fabric and NeoForge `compileJava`: both successful. Existing deprecation warnings remain.
- Scoped production Java against `37a2a9fde`: **106 additions, 113 deletions; net -7 lines**, across four existing classes. No new top-level production classes, packet fields, save fields, preview protocol, executor, or cache layer.
- Fresh independent read-only review of all twelve owned source/test files and relevant callers: **APPROVE; no Critical, Important, or Minor findings**. Reviewer inspected verification artifacts without rerunning them.
- Ordinary client/server gameplay and the original schematic in its full modded instance were not rerun; automated results do not establish those outcomes. Unrelated dirty AI/navigation changes were explicitly outside this review.
- Remaining material issues in this scoped implementation: none identified.

### Execution decisions

- Validation of expected room identity stays in `scanRoom`; its private update helper consumes the already-validated analysis instead of checking another independently supplied room ID.
- Registered-floor preparation now accepts the actual `RoomScanPlan`, replacing separate expected-room/source parameters. Diagnostic entry points build an explicit update plan from their known operation.
- The prior bare-room rollback fixture now supplies a prepared structure copy and verifies the same publication/ID rollback invariant through the production contract.
- Existing cheap persisted lookup/snapshot tests and the native physical-rejection test cover the fast-preview/fresh-execution distinction; no duplicate test-only production seam was added.
- Redundant expected-ID retagging and the single-consumer attachment-validation wrapper were removed. Attachment payload validation occurs beside initial-operation checks, before allocation.
- Type confirmation retains the packet-bounded guarantees in R4; no untransmitted component/floor identity is promised across requests.

There is no requirement to perform cosmetic cleanup outside these seams. Stop once the shared decision and reconstruction contracts are satisfied, relevant checks pass, and production complexity has decreased.

## Follow-up consolidation — 2026-11-03

User requested the remaining three simplifications after reviewing the first pass. Implemented natively in the existing classes:

- New building, existing-floor room, and attachment preparation share `prepareAddition`; selection, registered-room conflict checks, numbering and publication policies remain distinct where required.
- Each successful addition constructs its complete result once. Removed both result-copy helpers; existing tests construct complete payloads directly.
- Manual addition reuses an identical exact planner observation and its partition after the original ownership/validation guards. Alternate manual handoffs and reported-building discovery retain their existing priority and fallbacks.
- Follow-up production diff: **63 additions / 107 deletions; net -44 lines**, two existing production classes. No new framework, classes, packets, save fields or geometry/budget changes.
- Verification: **690 common JVM tests; both loader compilations; 144 native GameTests** (refresh/preparation17, scanner106, reported interaction2, cache10, stair house9), all passing. Preparation and reuse regressions have observed RED/GREEN evidence.
- Fresh independent read-only review: **APPROVE, no actionable findings**. Ordinary client/server and full modded-schematic gameplay were not replayed.
