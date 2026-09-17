# Selected Floor and Room Workflow Simplification — Implementation Plan

> Target branch: `dev/1.21.1` after `329e54327`.

**Goal:** Replace recursive connected-storey discovery and whole-Floor Room reconciliation with explicit selected-Floor scanning plus selected-Room add/update operations, while preserving enclosure, normal uneven floors, door partitioning, ladder/stair attachment, stable selected Room identity, and strict external-basement attachment.

**Spec:** `docs/superpowers/specs/2026-09-17-selected-floor-room-workflow-simplification-design.md`

**Tech:** Java 21, Minecraft 1.21.1, Architectury/Fabric/NeoForge, NeoForge GameTests.

## Global constraints

- Work only on `dev/1.21.1` first.
- Preserve unrelated untracked logs/docs and any peer work.
- `FloorGeometry` remains the one canonical/persisted exact Floor geometry model.
- Fresh interaction logic may select canonical geometry but may not manufacture ordinary Floor cells.
- Keep selected-storey enclosure/exterior pruning.
- Keep doors/gates as Room boundaries.
- Keep deterministic external-basement attachment through strict structural evidence.
- Keep normal uneven Floors using Minecraft collision/floor-height behavior.
- Do not special-case `StairBlock`, `ShelfBlock`, `HopperBlock`, beds, copied-house coordinates or specific Y values.
- Do not introduce another persisted topology model or public graph abstraction.
- Start each behavior change with focused red GameTests.
- Use Minecraft 1.21.1 local source as the movement/collision oracle before inventing custom semantics.
- Apply the fixed final Java diff through reuse, quality, correctness and efficiency cleanup lenses before completion.
- Prefer deletion and direct selected-operation flow over compatibility wrappers.

## Current baseline observations

At `329e54327`:

- `SelectedFloorScanner.scan(...)` calls `Observation.connected(...)`, which resolves one storey and then recursively explores `transitionSeeds` through `discoverConnectedStoreys(...)`.
- `Result` carries `connectedStoreys` and `storeyLinks`; downstream planning uses `directlyConnectedFloors(...)` and `storeyScan(...)`.
- `RoomScanPlanner.connectedTransitionAttachmentPlan(...)` uses recursively discovered Floors to infer next-storey attachment.
- `RoomWorkflow.analyzeRoom(...)` materializes every Room component on a fresh Floor and calls `RegisteredRoomReconciler.reconcileAddition(...)` before publishing the selected Room.
- registered Room update builds a component lineage, runs `RoomIdentityPolicy`, produces assignment lists, and lets `VillageManager` replace/create multiple Rooms.
- `Village.selectAttachmentTarget(...)` already contains strict connector-less vertical attachment evidence suitable for external basements.
- `StructureScanner` already contains narrow interaction normalization/handoff code for stairs/slabs/connectors.

Minecraft 1.21.1 local source confirms `WalkNodeEvaluator.getFloorLevel(...)` derives physical standing height from support collision shape while neighbour acceptance separately handles step/collision constraints. Reuse that physical model; do not add block-class staircase tables.

---

## Task 1: Lock selected-only Floor discovery with red tests

**Files:**

- Modify: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`
- Modify only if needed for copied-house acceptance: `neoforge/src/main/java/net/conczin/mca/server/world/data/CopiedOpenHouseGameTests.java`

### 1.1 Replace the recursive-discovery contract

`connectedStoreyDiscoveryCanonicalizesDeepStairChain` currently proves behavior choice 2A. Replace it with a selected-only contract, for example:

```java
@GameTest(...)
public static void selectedFloorScanDoesNotRecursivelyDiscoverDeepStairChain(GameTestHelper helper) {
    // Build the same multi-storey stair fixture.
    // Scan the lower selected Floor.
    // Assert only the selected Floor is returned as fresh geometry.
    // Assert upper/lower attachment is discovered only when scanning that Floor explicitly.
}
```

The exact public assertion should use the post-refactor API, not preserve `connectedStoreys()` just for the test.

Update `CopiedOpenHouseGameTests.everyCopiedHouseRoomComponentValidates` too: once selected-only scanning is the sole normal API, remove the duplicate `scanSelected(...)` call and the `directlyConnectedFloors(...)` assertion. Scanning each explicit `STOREY_SEEDS` entry should continue to prove that every selected Floor independently produces valid Room topology.

### 1.2 Keep normal stair/uneven-Floor contracts

Retain as required behavior:

- `staircaseKeepsUpperRoomOutOfLowerStorey`
- `twoBlockStaircaseSeparatesBroadStoreys`
- `flatUpperLandingBesideDescentRemainsFloorCell`
- `slabAndStairUseTransientSurfaceEvidence`
- `unevenThreeArmRoomIsSourceIndependent`
- `interiorFloorHoleDoesNotInvalidateRemainingRoom`

If an existing test only exists to prove exotic recursive staircase inference, rewrite/remove that requirement rather than reintroducing complexity.

`staircaseKeepsUpperRoomOutOfLowerStorey` and `twoBlockStaircaseSeparatesBroadStoreys` currently end with `connectedFloors(...)` assertions. Preserve their selected-Floor separation/edge assertions, but replace the recursive-graph assertion with local stair-transition evidence or with the explicit attachment-planning test in Task 3. After those callers are converted, delete the test-only `connectedFloors(...)` helper.

### 1.3 Keep enclosure and geometry contracts

Required unchanged:

- `fullHeightPartialObstacleDoesNotBecomeFloorCell`
- `highCeilingDoesNotCreateExtraFloorLayers`
- `lowerExteriorCheckDoesNotClimbOpenUpperStorey`
- `roofedExteriorAcrossDoorIsNotOwnedFloorGeometry`

### 1.4 Run red/guard evidence

Run the NeoForge GameTest server and record the expected failure from the selected-only test before production edits:

```powershell
.\gradlew.bat :neoforge:runGameTestServer --no-daemon --no-build-cache
```

Do not weaken enclosure/uneven-floor guards to make the new test pass.

### 1.5 Commit the test contract

Stage only the test file(s):

```powershell
git add neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java neoforge/src/main/java/net/conczin/mca/server/world/data/CopiedOpenHouseGameTests.java
git commit -m "test: define selected floor workflow"
```

Omit unchanged paths from the actual `git add` command.

---

## Task 2: Remove recursive connected-storey discovery from `SelectedFloorScanner`

**Files:**

- Modify: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java`

### 2.1 Make selected Floor resolution the normal scan path

Change `scan(...)` and `Observation` so normal fresh scanning resolves one canonical selected storey only.

Prefer one obvious API rather than keeping `scan`, `scanSelected`, `selected`, and `connected` variants if they become aliases.

Update `StructureScanner.resolveAttachmentSeed(...)` at the same time: its exact, vertical-handoff, standing-surface, sub-full-block, and doorway candidate scans should all resolve the candidate's selected Floor only. Replace the current `observation.connected(...)` calls rather than leaving a hidden recursive entry point behind.

Target shape:

```text
interaction seed
  -> resolve supported/canonical seed
  -> discover enclosed volume
  -> derive supported cells
  -> select one storey
  -> FloorGeometry + local connector evidence
```

### 2.2 Delete recursive state

When callers no longer need it, remove:

- `discoverConnectedStoreys(...)`
- `indexStorey(...)`
- `sameLink(...)`
- `PendingTransition`
- `ConnectedStoreys`
- `StoreyLink`
- `Result.connectedStoreys`
- `Result.storeyLinks`
- `Result.storeyScan(...)`
- `Result.directlyConnectedFloors(...)`

Do not retain dead fields "for future use".

After those recursive users are removed, re-check `DiscoveredStorey` and `StoreyResolution`. If each has become only a one-to-one wrapper around the single selected `Result`/Floor state, collapse it instead of preserving names and records whose only purpose was the old graph traversal. Keep a record only when it still represents a distinct invariant or materially simplifies the selected-scan cache.

### 2.3 Keep local attachment evidence only

Keep the selected scan's `transitionSeeds` (or a smaller equivalent selected-storey boundary value) as local stair evidence if required. Stairs are not `FloorConnector` types, so these positions are the clean way to prove that the selected Floor reaches an already-registered adjacent Floor. Do not retain `DiscoveredStorey` merely as a container for this set if `Result` can own it directly.

The consumer may compare a transition position with persisted `StructureFloor.geometry()` / interaction geometry. It must not call `Observation.resolve(...)`, `scanSelected(...)`, or another fresh world scan for that adjacent Floor. Multiple matching registered targets are ambiguous and must be rejected.

For ladders/trapdoors, reuse connector positions/markers already present in `FloorGeometry` rather than retaining a second topology structure.

### 2.4 Simplify `StructureScanner.FloorObservation`

Remove `directlyConnectedFloors()` if it exists only to expose recursive results.

`StructureScanner.hasDirectStoreyConnection(...)` also currently depends on `Result.directlyConnectedFloors(...)`. Remove or rewrite that caller before deleting the API. Prefer rejecting same-semantic-band overlap unless the new local stair/ladder attachment evidence already proves the intended registered target; do not recreate a recursively discovered Floor graph just to preserve this validation helper.

Keep:

- one canonical scan seed;
- one selected `Result`/`FloorGeometry`;
- local stair transition positions needed to match already-registered Floors;
- ladder/trapdoor connector evidence to already-registered Floors;
- interaction handoff.

### 2.5 Verify focused Floor tests

Run the full NeoForge GameTest server; inspect failures specifically around Floor/storey behavior before continuing.

Commit after green:

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java
git commit -m "refactor: scan only selected floor"
```

---

## Task 3: Simplify Floor attachment planning without losing basements/ladders

**Files:**

- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomScanPlanner.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomWorkflow.java`
- Modify if required: `common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java`
- Modify if required: `common/src/main/java/net/conczin/mca/server/world/data/Village.java`
- Test: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`
- Test: `neoforge/src/main/java/net/conczin/mca/server/world/data/CopiedOpenHouseGameTests.java`

### 3.1 Preserve four explicit attachment cases

Planning should distinguish only:

1. **same registered Floor** — interaction resolves to a persisted/fresh matching Floor and mode is Add/Update Room;
2. **local stair attachment** — a selected-scan transition position uniquely matches one already-registered adjacent Floor; no scan of that adjacent Floor occurs;
3. **local ladder/trapdoor attachment** — `FloorConnector` marker/column evidence connects the selected new Floor to one registered Floor;
4. **strict external vertical attachment** — no connector, but `Village.selectAttachmentTarget(...)` proves direct vertical structural contact through exact overlapping columns.

No recursive scan of intermediate/remote storeys.

### 3.2 Replace `connectedTransitionAttachmentPlan(...)`

Delete it once the selected scan can directly identify the target registered Floor from either local stair-transition evidence or ladder/trapdoor connector evidence.

Do not replace it with another multi-storey search helper.

If small helpers are required, name them by the evidence they check (for example `stairAttachmentTarget` and `connectorAttachmentTarget`) rather than by recursive "connected storey" semantics.

For stairs, consume `transitionSeeds` only as coordinates to query persisted Floors. Do not resolve those seeds through `SelectedFloorScanner.Observation`; doing so would quietly reintroduce choice 2A under a different name.

The candidate Floor stored in the attachment plan must now be the same Floor as `analysis.observation().scan().floor()`. Remove the old possibility where planning selects a recursively discovered `connectedStorey` as the candidate.

Update `RoomWorkflow.resolvePlannedAttachmentScan(...)` accordingly: validate/materialize the already-observed selected scan with `StructureScanner.resultFromObservedStorey(...)`. Do not call the removed `Result.storeyScan(...)` and do not fresh-scan a second storey to satisfy the plan.

### 3.3 Keep external basement evidence strict

Preserve the current `Village.selectAttachmentTarget(...)` fallback semantics:

- footprint intersection required;
- `hasDirectVerticalAttachmentEvidence(...)` required;
- ambiguity across logical Buildings rejected;
- unproven overlap rejected.

Keep `externalBasementDoorAttachesToOverlappingBuilding` green.

Add/retain an ambiguity test proving two equally valid external Buildings do not get guessed.

### 3.4 Keep ladder handoff without fake geometry

Required green:

- `ladderTrapdoorAttachesFloorsWithoutMergingRooms`
- `airAboveLadderResolvesFloorWithoutInventingSupport`
- `ladderTopExitStaysOnUpperStorey`

The unsupported ladder exit must remain absent from `FloorGeometry`.

Also retain/add a normal-stair test proving a newly selected adjacent Floor can attach to an already-registered Floor using local transition evidence alone, while a single scan still does not return/pre-scan the registered neighbor.

### 3.5 Copied-house acceptance

Re-evaluate these under explicit one-Floor-at-a-time behavior:

- `registeredMainRoomOffersUpperAndLowerStoreyAttachments`
- `addingBasementPreservesRegisteredUpperStorey`
- `successiveLowerStairRoomsAttachOneLevelAtATime`
- `eachLowerStaircaseRegistersThePlannedRoomUnderTheHouse`

The important contract is that each explicitly selected lower/upper Floor attaches to the same logical Building one level at a time. Tests must not require one scan to pre-discover the whole chain.

Commit after green:

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/RoomScanPlanner.java common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java common/src/main/java/net/conczin/mca/server/world/data/RoomWorkflow.java common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java common/src/main/java/net/conczin/mca/server/world/data/Village.java neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java neoforge/src/main/java/net/conczin/mca/server/world/data/CopiedOpenHouseGameTests.java
git commit -m "refactor: simplify floor attachment planning"
```

Stage only changed files in the actual commit.

---

## Task 4: Make Add Room persist only the selected component

**Files:**

- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomWorkflow.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/BuildingScanResult.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java`
- Modify/delete after callers are gone: `common/src/main/java/net/conczin/mca/server/world/data/RegisteredRoomReconciler.java`
- Reuse unchanged unless tests prove otherwise: `Village.replaceStructureAndRegisterRoom(...)` and its `publishFloorRefresh(...)` owner path
- Test: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`
- Test: `neoforge/src/main/java/net/conczin/mca/server/world/data/CopiedOpenHouseGameTests.java`

### 4.1 Add selected-only persistence tests first

Existing useful guards include:

- `unregisteredRoomAcrossDoorCanBeAddedWithoutOverlap`
- `threeDoorFloorCanAddOneUnregisteredRoomWithoutOverlap`
- `initialRegistrationPreservesFloorButRegistersOnlySelectedRoom`
- `registeredLowerRoomLeavesSiblingAsAddRoom`

Add explicit tests proving:

1. adding Room B leaves registered Room A's ID/type/cells unchanged;
2. Add Room does not materialize/register unselected Room C;
3. if the selected new component overlaps registered Room identity, Add Room returns `OVERLAP` instead of rewriting Room A;
4. fresh Floor geometry may be published only when all existing registered Room-owned cells remain valid in that Floor.

Record the red result before production changes.

### 4.2 Simplify `RoomWorkflow.analyzeRoom(...)`

After obtaining the one fresh selected Floor:

```text
partition Floor
  -> select component at canonical scan seed
  -> materialize selected component only
  -> validate against existing registered Rooms
  -> produce one pending Room addition + optional Floor refresh
```

Keep the single transient full-Floor partition if `RoomPoiEvidence.candidates(...)` still needs all components to resolve perimeter ownership. Choice 7B removes sibling Room materialization/persistence reconciliation; it does not justify a second special selected-only topology traversal. Only replace the full partition with a selected-component traversal if the same door/boundary/POI ownership semantics can be proven with less code.

Delete the loop that materializes every component solely for reconciliation.

Delete the call to `RegisteredRoomReconciler.reconcileAddition(...)`.

### 4.3 Define local conflict checks

Prefer small predicates close to the workflow rather than a new assignment engine:

- selected new Room must not have stable identity overlap with any registered Room on the target Floor;
- existing Room cells must remain representable by the refreshed canonical Floor;
- door/boundary ownership differences alone must not count as stable identity overlap if existing `FloorGeometry.roomIdentityOverlapCount(...)` already encodes that rule.

Reuse the existing identity-overlap primitive rather than duplicating boundary-cell logic.

### 4.4 Persist without rewriting siblings

Reuse the existing narrow atomic owner: `Village.replaceStructureAndRegisterRoom(...)` already gathers current sibling Rooms unchanged, validates all of them against the refreshed Floor, appends the one new Room, and delegates to `publishFloorRefresh(...)` without mutating Village state first.

The selected Add Room analysis should therefore return the refreshed Structure as the normal pending Structure mutation once its local conflict checks pass, allowing the existing `VillageManager.commitExpandedRoom(...)` branch to call `replaceStructureAndRegisterRoom(...)`.

`RoomWorkflow.analyzeRoom(...)` is currently the only producer of `withPendingFloorRefresh(...)`. Once its whole-Floor reconciliation path is removed, delete the now-dead `BuildingScanResult.PendingFloorRefresh` model rather than keeping two representations for the same pending Structure refresh:

- remove `pendingFloorRefresh` from `BuildingScanResult`;
- remove `withPendingFloorRefresh(...)`;
- make `targetBuildingId()` read only `pendingStructure`;
- remove the `pendingFloorRefresh` dispatch branch from `VillageManager.commitRoomAddition(...)`;
- simplify `commitExpandedRoom(...)` to consume `pendingStructure` and call `replaceStructureAndRegisterRoom(...)` directly.

Do not introduce another persistence helper, clone/re-materialize sibling Rooms, or pass a manually rebuilt sibling list when the existing owner already does that work.

Commit after focused + full floor tests are green:

```powershell
git commit -m "refactor: add only selected room"
```

---

## Task 5: Make Update Room replace exactly one Room and preserve its ID

**Files:**

- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomWorkflow.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RegisteredRoomUpdate.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java`
- Delete if unused: `common/src/main/java/net/conczin/mca/server/world/data/RegisteredRoomReconciler.java`
- Delete if unused: `common/src/main/java/net/conczin/mca/server/world/data/RoomIdentityPolicy.java`
- Add/update focused GameTests in `FloorScannerGameTests.java`

### 5.1 Define selected-update tests

Add tests proving:

1. moving a wall and explicitly updating Room `#12` keeps ID `#12`;
2. forced/non-forced type metadata is preserved/resolved according to existing Room type rules;
3. another registered Room on the same Floor is byte-for-behavior unchanged by the selected update;
4. when the fresh selected component overlaps another registered Room, update fails `OVERLAP` rather than merging/reassigning it;
5. if the old Room splits into two fresh components, only the component selected by the interaction replaces the old Room; the other component is left unregistered;
6. interaction on a valid boundary/handoff still selects the intended component deterministically.

### 5.2 Replace lineage analysis

Remove `BuildingRoomScanner.partition(...).map(...all components...)` as an identity-reconciliation input.

Instead:

1. partition once;
2. select one component using the canonical interaction/scan seed;
3. require stable identity overlap with the expected registered Room;
4. require no stable overlap with other registered Rooms;
5. materialize one replacement Room;
6. preserve the expected Room's ID/type/forced/contributes-to-main metadata.

As in Add Room, the transient component list may remain because materialization/POI perimeter ownership currently consumes shared component context. Do not materialize sibling components just to feed identity reconciliation, and do not add a duplicate selected-only partition path unless it actually replaces the existing topology pipeline cleanly.

If canonical interaction/handoff cannot select one component, fail instead of adding a second overlap-ranking heuristic. Stable identity overlap is a validation rule after deterministic selection, not an alternate selection engine.

### 5.3 Shrink `RegisteredRoomUpdate`

Target a record containing only data needed for one selected replacement, for example:

```text
result
source
village
refreshedStructure
structureId
floorId
expectedRoomId
replacementRoom
matchingTypes
```

Remove `previousRoomIds` and assignment lists when no longer needed.

### 5.4 Simplify `VillageManager` commit logic

Delete lineage/assignment machinery whose only job is multi-Room reconciliation:

- `validateRoomAssignments(...)`
- assignment identity allocation for sibling components;
- creation of extra sibling Room IDs;
- removed-ID/main-room replacement heuristics that are unnecessary for one-room replacement;
- related helpers only referenced by that pipeline.

Validate that the currently persisted expected Room still has the expected ID/structure/floor before applying the detached result.

Resolve the selected replacement's type with the existing `RoomTypeResolver` and then replace only that Room plus the refreshed Floor.

### 5.5 Delete obsolete global identity engine

Run:

```powershell
rg -n "RegisteredRoomReconciler|RoomIdentityPolicy" common/src/main/java neoforge/src/main/java
```

If all supported callers are gone, delete both classes instead of retaining unused abstractions.

Commit after green:

```powershell
git commit -m "refactor: update only selected room"
```

---

## Task 6: Tighten interaction handoff and normal-stair behavior

**Files:**

- Review/modify only if tests prove needed: `StructureScanner.java`
- Review/modify only if tests prove needed: `StructureConnector.java`
- Test: `FloorScannerGameTests.java`

### 6.1 Keep common forgiving positions

Required interaction cases:

- stair interaction;
- slab/partial-height supported interaction;
- doorway boundary;
- ladder/trapdoor exit;
- position within a persisted Floor cell's physical vertical interval.

### 6.2 Keep ambiguity conservative

`resolveFloorHandoff(...)` already rejects equally relevant candidates that resolve to different exact Floor cell sets. Preserve that principle.

Do not add nearest-by-distance guessing across multiple Floors.

### 6.3 Review `isSubFullInteraction(...)`

Confirm through Minecraft local collision source and GameTests that the generic collision predicate is sufficient for supported stair/slab interactions.

Do not replace it with `instanceof StairBlock` or block tags unless vanilla ownership proves a narrower generic API exists.

### 6.4 Remove unsupported exotic staircase tests/logic

Once normal stair + uneven-floor contracts pass, remove only heuristics/tests whose sole requirement is bizarre multi-rise/recursive staircase inference not covered by choices 1A/3B.

Do not remove storey separation that prevents a normal staircase from merging two stable Floors.

---

## Task 7: Fixed-diff Java cleanup and final verification

### 7.1 Freeze the final Java diff

Follow the cleanup skill's diff-selection rule first:

```powershell
# If staged changes exist:
git diff HEAD -- common/src/main/java neoforge/src/main/java

# Otherwise, if unstaged changes exist:
git diff -- common/src/main/java neoforge/src/main/java
```

If the worktree is clean because the implementation tasks were committed incrementally, record the implementation-base commit before Task 1 and use:

```powershell
git diff <implementation-base>..HEAD -- common/src/main/java neoforge/src/main/java
```

Freeze the exact diff text/file list chosen by that rule and use the same set for every review lens. Do not let later findings silently widen the review scope except where nearby code is required to understand a changed path.

When delegation is available, run the reuse, quality, correctness/Minecraft-owner, and efficiency passes as four read-only reviews in parallel. If delegation is unavailable, run the same four lenses locally against the frozen diff before fixing anything.

### 7.2 Reuse lens

Check for:

- duplicate Floor/Room selection helpers;
- duplicate collision/interaction predicates that should reuse `SelectedFloorScanner`, `StructureConnector` or `FloorGeometry`;
- stale wrappers preserving removed recursive APIs;
- copied boundary/identity-overlap calculations instead of existing canonical helpers.

### 7.3 Quality lens

Check for:

- dead `connectedStoreys`/assignment/lineage state;
- Optional misuse and needless null-wrapper records;
- methods that only forward to one owner;
- comments describing removed architecture;
- deep planner nesting that can become guard clauses.

### 7.4 Correctness + Minecraft-owner lens

Confirm:

- `FloorGeometry` is still the only persisted Floor geometry;
- interaction handoff cannot create Floor cells;
- existing registered sibling Rooms cannot be silently rewritten;
- selected Room ID is preserved on explicit update;
- external basement attachment is deterministic/ambiguous-safe;
- local Minecraft 1.21.1 collision/floor semantics still own physical standing height/step behavior.

### 7.5 Efficiency lens

Confirm the simplification actually removed work:

- no recursive scanning of connected storeys;
- no repeated full-volume scan for remote storeys;
- no materialization of every Room on Add Room;
- no O(components × previousRooms) global assignment matrix on ordinary selected update;
- one partition per selected operation;
- existing scan-local ceiling/step caches remain.

Do not add parallel streams or speculative caches.

### 7.6 Static hygiene

Run:

```powershell
git diff --check
rg -n "<<<<<<<|=======|>>>>>>>" common/src/main neoforge/src/main docs/superpowers/specs docs/superpowers/plans
rg -n "connectedStoreys|storeyLinks|RoomIdentityPolicy|RegisteredRoomReconciler" common/src/main/java neoforge/src/main/java
git status --short
```

Any remaining hit for a targeted deletion must have a supported-behavior reason.

### 7.7 Java/common verification

```powershell
.\gradlew.bat :common:test :fabric:compileJava :neoforge:compileJava --no-daemon
```

Required: `BUILD SUCCESSFUL`.

### 7.8 Final NeoForge GameTest proof

```powershell
.\gradlew.bat :neoforge:runGameTestServer --no-daemon --no-build-cache
```

Required clean claim: log contains `All N required tests passed :)` and process exits successfully.

If an unrelated known flaky test fails, record its exact name/output before rerunning; do not classify it as unrelated without evidence.

### 7.9 Final behavior checklist

- [ ] One fresh action scans one selected Floor only.
- [ ] Uneven normal Floors remain supported.
- [ ] Normal stairs separate/attach storeys without recursive whole-building discovery.
- [ ] Ladder/trapdoor handoff works without unsupported Floor cells.
- [ ] Covered exterior space remains excluded.
- [ ] Doors still partition Rooms.
- [ ] Add Room persists only the selected Room.
- [ ] Update Room preserves the selected Room ID and leaves siblings alone.
- [ ] Ambiguous Room overlap fails rather than globally reconciling.
- [ ] External basement attachment still works through strict structural evidence.
- [ ] Common interaction positions resolve deterministically.
- [ ] Existing save format remains compatible.
- [ ] Final code is smaller/easier to explain than the `329e54327` baseline.

### 7.10 Commit/push discipline

Keep commits behavior-focused. Do not absorb `.codex*.log` files or unrelated September 16 docs.

Only push after all final verification is green and the user explicitly wants the canonical branch pushed.
