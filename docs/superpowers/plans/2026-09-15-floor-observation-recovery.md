# Floor observation recovery

Approved in the current task: checkpoint existing floor changes, recover the
hotspot investigation, compare recent history, and implement inline.

## Contract

Keep exact FloorGeometry, registered room IDs, logical building grouping, and
atomic publication. A room operation reads one fresh observation and partitions
that observation once. A later confirmation obtains a fresh observation and
validates the expected identity. Registration never creates physical ownership.
Preserve the current FACING door rule; the September 10 audit describes an older
opposite-facing rule and is not authority to reverse the current contract.

The copied house must support main/lower/upper registration under one building,
with staircase positions selecting the correct prospective floor, stable
registered identities after refresh/save/load, and unrelated spaces remaining
unregistered. Tests must use the house's real roof rather than a GameTest roof.

## Work

- [x] Checkpoint the 13 floor files as 10b135aa2, preserving other staged work.
- [x] Read September 8/13 scanner implementations and the September 10 audit.
- [x] Recover the interrupted task: exterior re-traversal and record hashing are
  measured hotspots; leave ceiling semantics intact.
- [x] Reproduce current copied-house behavior with real sky access and a complete
  registration/save-load test, including the three lower staircase positions.
- [x] Consolidate candidate-local exterior evidence and use canonical storey
  identity for visited transition keys. Cache only proven reachable results;
  directed traversal must not propagate an exterior result to sibling branches.
- [x] Separate selected-floor observation from explicit connected-storey
  discovery through the existing scanner owner. Reuse fresh observation and
  partition in RoomScanPlanner/RoomWorkflow; retain stale-plan validation.
- [x] Fix demonstrated ownership/registration failures at their responsible
  stage; retain exact geometry, furniture, door, cave and deep-stair controls.
- [x] Run common unit tests, both loader compiles, and the full GameTest lane.
  Inspect actual named copied-house tests and compare batch durations.
- [x] Review the final diff inline for reuse, quality, correctness and efficiency.

## Files and verification

Production owners: SelectedFloorScanner, StructureScanner, RoomScanPlanner,
RoomWorkflow, BuildingRoomScanner, RoomScanPlan. Expand this set only for a
demonstrated failure. Use private helpers or existing records before adding
top-level abstractions. Transient caches never enter saved data.

Regression owner: CopiedOpenHouseGameTests; existing FloorScannerGameTests and
world-data JUnit tests cover doors, uneven floors, stairs, room addition and
publication. New tests assert actual operation outcomes and persisted ownership.

Use JDK 21.0.11 and gradlew.bat :common:test :fabric:compileJava
:neoforge:compileJava. Run only one :neoforge:runGameTestServer at a time.
Treat cached unit results, fresh GameTests and client verification separately.
Do not modify the user's save or include unrelated staged changes in commits.

## Resulting ownership

| Concept | Owner and responsibility |
| --- | --- |
| Building | `LogicalBuilding` keeps the stable building ID and Main Room identity. |
| Physical section | `Structure` persists its exact floors and building ID. |
| Floor | `StructureFloor` owns immutable `FloorGeometry`; cells retain real heights. |
| Room | `Building` stores registered membership in one floor. `RoomPartitioner` only partitions the scanner's cells. |
| Outline | `FloorGeometry.projection()` derives the display footprint; it does not decide membership. |
| Connections | The scanner's connected-storey result supplies attachment evidence without merging the floors. |

`SelectedFloorScanner.Observation` is local to a synchronous operation. Selected
resolution and connected discovery share its surface/ceiling/seed evidence. New
building scans and registered-floor refreshes select one floor; attachment
planning explicitly requests connections. Handoff candidates reuse the same
observation and the selected result is validated directly.

`RoomScanPlanner.Analysis` carries the action, observed geometry and partition
into `RoomWorkflow`. The workflow materializes that partition, or partitions the
already-observed connected floor when a staircase selects it. Attachment target
selection has one owner. A later request re-observes and checks the expected
building/action; the explicit expected-plan path also checks selected geometry.

## Findings and inline review

- Reuse: removed the pass-through StoreyCandidate record, duplicate physical-cell
  lookup, duplicate attachment resolver and repeated boundary result assembly.
- Quality: enforce RoomScanPlan action-specific targets; preserve existing names
  and persistence layout rather than introduce another hierarchy.
- Correctness: the copied-house tests previously used GameTest's synthetic roof.
  With real sky access the open main storey is one room; the lower storey still
  provides the sibling-room regression. Refresh after attachments reproduced
  OVERLAP because validation omitted the current building identity. Refresh and
  expansion now permit other storeys of that same building while retaining the
  existing same-band overlap rejection.
- Efficiency: cache exterior reachability within one candidate; positive results
  apply only to the queried start because traversal can be directional. Negative
  results cover the fully explored component. Transition visitation uses canonical
  storey identity instead of hashing whole geometry records. Candidate anchors are
  checked in distance order with a shared ceiling resolver.
- Vanilla ownership: retain WalkNodeEvaluator collision heights, current door
  FACING ownership and SavedData publication. No ceiling-height cap, persistent
  cache, save migration, or rollback to the earlier height-band implementation.

## Verification

- RoomScanPlan invalid-target tests were observed failing before the constructor
  checks; the common suite then passed all 285 tests.
- Fabric and NeoForge compile with JDK 21.0.11.
- Full real-sky lane passed all 135 required GameTests in 20.13 seconds, including
  house registration, sibling Add Room, main/lower/upper refresh and save/reload,
  and changed-geometry/duplicate confirmation rejection.
- Final lane: all 136 required GameTests passed in 17.64 seconds, including
  direct registration from each of the three lower staircase positions. Each
  commit preserves the planned floor and building identity; subsequent selection
  offers Update Room or Add Room rather than another building/attachment.
- Earlier failing lifecycle evidence: main-room refresh returned OVERLAP after
  lower and upper attachment; the corrected lifecycle now passes.
- Timing is not an isolated benchmark: the suite and fixture roof changed. Do not
  attribute whole-suite timing differences solely to the scanner cache.
- Client visuals and the original modded saved world were not revalidated. The
  copied structure fixture covers the reported geometry using its real roof.

Checkpoint: `10b135aa2`. Implementation remains separate and uncommitted; unrelated
staged and concurrent work was preserved. No subagents were used for implementation.
