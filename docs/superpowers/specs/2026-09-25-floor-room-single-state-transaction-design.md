# Floor / Room Single-State Transaction Cleanup — Design

Date: 2026-09-25

Status: approved design

## Goal

Finish the Floor/Room architecture cleanup by making the existing building-domain state behave as one transactionally published state while reducing production Java.

The desired model is:

```text
Minecraft world
    -> SelectedFloorScanner
    -> FloorGeometry
    -> RoomPartitioner
    -> RoomScanPlanner / RoomWorkflow
    -> one Village-owned building-state mutation boundary
    -> Rooms + Structures/Floors + LogicalBuildings published together
```

This is a deletion/consolidation pass, not a new transaction framework.

## Primary acceptance constraint

Production Java changed by this work must be **net-negative in lines**.

Tests and documentation may add lines because they prove the simplification. Production code must shrink overall. Each modified production Java file should also be net-negative where practical; a file that grows must have a concrete reason and the total production-Java diff must still be negative.

The final implementation must report `git diff --numstat` for production Java so this requirement is objectively verifiable.

## Existing architecture to preserve

The current spatial architecture is already the intended source of truth and is not being redesigned:

```text
Minecraft world
      ↓
SelectedFloorScanner          fresh selected-storey discovery
      ↓
FloorGeometry                 exact 3D Floor membership
      ↓
RoomPartitioner               Room components within that Floor
      ↓
RoomScanPlanner               action selection from fresh geometry + saved identity
      ↓
RoomWorkflow                  server-side business orchestration
      ↓
Village / StructureFloor      persisted identity and ownership
```

The following existing rules remain locked:

- `FloorGeometry` is the sole spatial source of truth for Floor membership.
- `SelectedFloorScanner` owns fresh physical Floor discovery.
- `RoomPartitioner` partitions one canonical Floor; it never discovers another Floor.
- `RoomScanPlanner` chooses the action; it does not manufacture or rewrite geometry.
- `StructureFloor` matching may associate fresh geometry with persisted identity but never alter fresh membership.
- POI evidence is derived after Room topology and never becomes a second Floor/Room geometry model.
- `RoomWorkflow` remains the single orchestration owner for add Room, add Building, add Floor, add Basement, update Room, and Room inheritance operations that require type selection.
- polymorph/type confirmation re-enters the same `RoomWorkflow` path and revalidates current state.
- `VillageManager` remains the world/SavedData coordinator and global ID owner.

## Problem

The spatial side has one source of truth, but building-domain mutation still has multiple publication/finalization protocols.

Examples in the current code include:

- refreshed Floor + Room publication through `Village.publishFloorRefresh(...)`;
- initial Structure/Room registration through `registerStructure(...)` / `registerRoom(...)`;
- Room and Floor removals through direct `Village` mutations;
- Main Room changes through `Village.setMainRoom(...)`;
- inheritance/contribution changes through separate setters and `commitRoomInheritanceUpdate(...)`;
- forced Room type changes through direct `Building` mutation in `VillageManager`;
- full scan using an explicit `BuildingStateSnapshot` rollback path;
- several operations independently remembering to call some combination of logical-building reconciliation, dimension recomputation, Village/SavedData dirtiness, or outer Village finalization.

Most of these paths are individually correct. The problem is architectural: correctness depends on each path remembering the same publication obligations.

The system should have one answer to this question:

> How does an accepted change to Rooms, Structures/Floors, or LogicalBuildings become live state?

## Decision

Keep the existing domain objects and maps. Do **not** replace them with a new immutable aggregate type.

Instead, consolidate existing publication/rollback behavior into the smallest possible `Village`-owned building-state mutation boundary and delete competing finalization logic around it.

The shape is conceptually:

```text
validate operation-specific preconditions
    ↓
snapshot existing building-domain state when rollback can be required
    ↓
apply the narrow mutation
    ↓
reconcile canonical logical-building state once
    ↓
recalculate derived Village dimensions once
    ↓
publish success / mark dirty once

failure after mutation begins
    ↓
restore the same BuildingStateSnapshot
```

The exact helper name and signature may follow existing conventions. The important design rule is that there is one publication/finalization path, not one new abstraction layer.

## Canonical building-domain state

The coherent persisted state is the existing trio:

```text
Rooms (`Village.buildings`)
Structures / StructureFloors (`Village.structures`)
LogicalBuildings (`Village.logicalBuildings`)
```

These collections together are the building-domain aggregate.

`Village.BuildingStateSnapshot` remains the rollback representation. Do not add `BuildingState`, `VillageTransaction`, `MutationContext`, repository interfaces, command objects, or another copy of these maps merely to make the architecture look transactional.

`lastBuildingId` remains owned by `VillageManager`, but the current registered-Room update path used by `fullScan()` does not allocate IDs. The existing `fullScan()` save/restore of `lastBuildingId` is therefore redundant transaction state and should be deleted. If a future full-scan update path starts allocating IDs again, that change must add explicit rollback coverage at the same time.

## Mutation ownership

### `RoomWorkflow`

`RoomWorkflow` owns business orchestration:

- analyze the requested operation;
- revalidate stale expected IDs;
- request type selection without mutation when needed;
- send a fully validated operation to the commit owner.

It does not own persisted maps, IDs, SavedData, geometry algorithms, or rollback infrastructure.

### `Village`

`Village` owns publication of changes to Rooms, Structures/Floors, and LogicalBuildings.

Operation-specific methods may remain descriptive (`removeRoom`, `setMainRoom`, `publishFloorRefresh`, etc.), but they must delegate to one common publication/finalization mechanism instead of each maintaining its own reconciliation/dirty/dimension protocol.

Low-level helpers that only mutate one collection may remain as implementation details where they reduce code. They are not independent publication paths.

### `VillageManager`

`VillageManager` owns:

- Village lookup and creation;
- global Building/Village ID counters;
- SavedData dirtiness;
- cross-Village merge/removal behavior;
- external/grouped Building handling;
- delegating accepted functional-Room changes into the Village aggregate.

It must not duplicate Room/Floor/LogicalBuilding reconciliation that belongs to `Village`.

## Required simplifications

### S1. One post-mutation finalization path

Delete repeated combinations of:

- `refreshLogicalBuildings()`;
- `reconcileLogicalBuilding(...)` when the operation can use the shared aggregate finalizer;
- `calculateDimensions()`;
- `markDirty()` / equivalent local building dirtiness where the shared publication path already performs it.

Keep operation-specific validation. Consolidate publication side effects.

### S2. Reuse the existing snapshot

`BuildingStateSnapshot` is the only rollback representation for functional building state.

Do not introduce a second snapshot/candidate class. If a mutation can fail after touching live aggregate state, either validate before mutation or use the existing snapshot and restore it.

### S3. Keep single-step operations simple

The goal is one publication protocol, not wrapping every field assignment in a heavyweight transaction.

For example, `setMainRoom` may remain a descriptive operation. Its implementation should use the same aggregate finalization path as other accepted building-state changes rather than independently applying floor numbers and dirtiness through a separate protocol.

### S4. Initial registration and refresh share aggregate publication semantics

Initial Structure/Room registration and refreshed Floor/Room publication may have different validation because they represent different operations. Once validated, they must converge on the same rules for publishing canonical aggregate state.

Do not duplicate validation merely to force the methods into one signature.

### S5. Removals are state transitions too

Removing a Room, Floor, Structure, or logical building must leave the same invariants as additions/updates:

- no Room references a missing Structure/Floor;
- every Structure references an existing LogicalBuilding;
- the Main Room is valid or deterministically repaired;
- Ground Floor numbering derives from the Main Room;
- removing the final Room deletes the now-empty logical building/Structures according to existing behavior;
- derived bounds are current.

These operations should no longer carry their own bespoke finalization sequences when the shared Village path can do it.

### S6. Type/inheritance changes are one accepted state transition

Inheritance/contribution and any associated Room type change must publish as one operation.

Existing behavior that validates type selection before changing sharing state is preserved. Invalid/stale type selections perform no mutation.

`VillageManager.forceRoomType(...)` should delegate the accepted change to a Village-owned mutation method or reuse an existing Village operation rather than mutating `Building` directly and separately remembering dirtiness.

### S7. Full scan keeps one outer rollback

`VillageManager.fullScan()` intentionally applies several ordinary Room updates sequentially because later scans may depend on earlier successful updates.

Keep that behavior. The outer full-scan transaction:

1. snapshots `BuildingStateSnapshot`;
2. applies normal update operations;
3. finalizes successfully once at the end where possible;
4. restores the Village snapshot on any failure.

Do not add a generic nested transaction stack.

The implementation may simplify redundant inner finalization only if behavior and rollback remain correct.

## No new framework

Explicitly forbidden as part of this cleanup:

- generic transaction manager;
- service/repository/command-handler hierarchy;
- event bus for building-state commits;
- a second `BuildingState` model;
- persisted mutation journal;
- copy-on-write framework;
- new Floor/Room geometry representation;
- new global caches;
- interfaces with one implementation;
- broad packet/protocol redesign;
- speculative concurrency support for a server-thread-owned mutation flow.

The best implementation is the one that deletes the most duplicated state-management code while preserving the existing domain boundaries.

## Failure semantics

Business-rule failure before mutation returns the existing result type and changes nothing.

If a mutation can throw or reject after touching live building state, the pre-operation `BuildingStateSnapshot` is restored before the failure escapes/returns.

After any failed functional-building mutation, these observable values must match the pre-operation state:

- Room IDs and Room membership cells;
- Room POIs/type/forced-type/contribution flags;
- Structure IDs and logical-building IDs;
- Floor geometry, floor IDs, floor numbers, and connector metadata;
- Main Room and inheritance metadata;
- derived Village bounds after restoration;

The current full-scan path must not retain rollback bookkeeping for state it cannot mutate.

## Java cleanup requirements

The final Java diff must be reviewed through all four `java-code-review-cleanup` lenses.

### Reuse

- reuse `BuildingStateSnapshot`;
- reuse `publishFloorRefresh` validation/publication behavior rather than adding an equivalent path;
- reuse `RoomWorkflow` for polymorph/revalidation orchestration;
- reuse existing `RoomTypeResolver` and logical-building reconciliation;
- reuse vanilla/loader behavior where relevant rather than adding custom lifecycle state.

### Quality

- one owner for publication/finalization;
- remove redundant wrappers and duplicated branches;
- prefer guard clauses;
- narrow visibility where production callers no longer need direct mutation methods;
- avoid boolean-flag transaction helpers or parameter-heavy generic APIs;
- comments explain invariants only.

### Correctness

- no partial aggregate state after failure;
- no dangling Room/Structure/Floor references;
- no dead ID-counter rollback state in `fullScan()` while its update path cannot allocate IDs;
- Main Room / Ground Floor derivation remains stable;
- confirmation paths still revalidate current state;
- no change to exact Floor geometry ownership.

### Efficiency

- reconciliation/dimension recomputation happens once per accepted operation where possible;
- validate before snapshotting, then take exactly one `BuildingStateSnapshot` per accepted publication so reconciliation/dimension/dirtiness failures can roll back; do not add nested or duplicate snapshots around the same publication;
- no repeated scans or geometry materialization introduced by transaction cleanup;
- no new intermediate collection churn merely to make APIs generic.

## Testing contract

### Existing tests that remain authoritative

Preserve current coverage for:

- atomic `publishFloorRefresh` rejection;
- Structure/Room registration validation without mutation;
- inheritance/type atomicity;
- Main Room / Ground Floor behavior;
- Room/Floor removal invariants;
- `BuildingStateSnapshot` restoration;
- RoomWorkflow type-selection no-commit behavior;
- stale update rejection;
- scanner/Floor/Room integration GameTests.

### Required new regression: full-scan rollback

Add a focused test that forces `VillageManager.fullScan()` to fail after at least one earlier Room update has already changed live state.

After failure, assert the pre-scan values are restored for:

- Room geometry and POIs;
- `Building.lastScan`;
- Structure/Floor geometry and numbering;
- Main Room ID;
- LogicalBuilding inheritance state;
- Room IDs;

The test does not need an ID-counter assertion because registered-Room full-scan updates do not allocate IDs. Instead, the implementation must remove the dead `previousLastBuildingId`/restore plumbing from `fullScan()` and keep ID rollback only in flows that can actually allocate IDs.

This is required because the implementation currently contains the rollback logic but lacks a direct regression proving the complete manager-level transaction.

### Required mutation-path regressions

Where consolidation changes an existing direct edit, add or strengthen the smallest test proving both success and failure/no-mutation behavior. Prefer extending `VillageFloorSystemTest` and existing workflow/commit tests over creating new broad fixtures.

Do not duplicate scanner geometry matrices in transaction tests.

## Verification

Minimum completion verification:

1. focused tests for changed mutation paths;
2. `:common:test`;
3. Fabric compile;
4. NeoForge compile;
5. full NeoForge GameTest server;
6. `git diff --check`;
7. `git diff --numstat` filtered to changed production `.java` files, with deletions greater than additions;
8. final four-lens `java-code-review-cleanup` pass on the fixed Java diff.

Any unrelated pre-existing GameTest failure must be named explicitly and compared against the pre-change baseline; it must not be hidden by the cleanup.

## Non-goals

- no Floor scanner behavior change;
- no Room partition behavior change;
- no attachment-policy change;
- no persistence format or data-version change;
- no network packet format change;
- no rewrite of `Village` into an immutable aggregate;
- no new transaction subsystem;
- no generic cleanup outside the building-state mutation paths touched by this design;
- no attempt to make production code prettier at the cost of positive net Java lines.

## Acceptance

The cleanup is complete only when all of the following are true:

- `FloorGeometry` remains the single spatial truth;
- all functional building-state operations use one Village-owned publication/finalization protocol;
- `RoomWorkflow` remains the single complex Room-operation orchestration owner;
- duplicate reconciliation/dimension/dirtiness sequences are removed;
- failed operations leave the aggregate unchanged;
- full-scan rollback has a direct regression test and redundant `lastBuildingId` rollback state is removed;
- no new transaction/framework/state-model abstraction was introduced;
- production Java is net-negative in lines;
- focused/common/loader/GameTest verification passes except explicitly identified unrelated baseline failures;
- the final four-lens Java cleanup review finds no worthwhile in-scope deletion or consolidation left.
