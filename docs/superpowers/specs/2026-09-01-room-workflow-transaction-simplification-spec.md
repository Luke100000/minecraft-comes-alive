# Room Workflow and Transaction Simplification Specification

Date: 2026-09-01

Status: Proposed implementation specification

Related specification:

- `2026-08-30-floor-scanner-simplification-spec.md`

This specification is a production-hardening pass over the current `feature/1.21.1-floor-clean-squash`
branch relative to `origin/1.21.1`. It does **not** redesign the scanner, Floor model, connector model,
Room topology, or Blueprint renderer.

## 1. Problem statement

The floor/Room redesign has established clearer physical and persistence models, but the production
orchestration around those models has two remaining structural problems.

First, `VillageManager.fullScan()` behaves like a transaction without owning a complete transaction
snapshot. It currently snapshots Rooms and Structures, then mutates the live Village Room-by-Room.
If a later Room update fails, rollback reconstructs Rooms through persistence NBT and restores
Structures, but it does not restore every piece of live building state:

- `Building.lastScan` is runtime state and is not serialized by `Building.save()`;
- `LogicalBuilding` state is not included in the rollback snapshot;
- Main Room, Ground Floor, inheritance, and other logical-building mutations can therefore survive a
  failed full scan even when Room/Structure geometry is restored;
- `lastBuildingId` must be restored together with Village building state so a failed scan cannot
  partially consume Room IDs.

Second, Room workflow decisions are spread across `ReportBuildingMessage` and `VillageManager`.
The packet currently knows how to analyze, validate, request polymorph selection, and commit several
Room operations. `VillageManager` simultaneously owns SavedData/world coordination and most of the
same Room workflow. This makes it harder to identify the one authoritative business rule for an
operation and encourages packet-specific branches.

The solution must make these operations easier to reason about **without** introducing a service
framework, command hierarchy, repository layer, event bus, generic transaction engine, or another
large redesign.

## 2. Goals

The implementation must achieve these goals:

1. `fullScan()` is atomic for all MCA building-domain state it can mutate.
2. Rollback uses an explicit in-memory domain snapshot, not persistence serialization as a cloning
   mechanism.
3. Room scan/update business workflow has one server-side owner outside the network packet.
4. `ReportBuildingMessage` and `ConfirmBuildingPolymorphMessage` remain transport/presentation
   adapters: decode input, invoke the Room workflow, then send the appropriate response.
5. `VillageManager` remains the SavedData/world-level aggregate coordinator and ID owner.
6. The refactor reduces duplicated decisions and long orchestration methods rather than moving the
   same complexity into several new abstractions.
7. Existing scanner, Room topology, connector, floor-numbering, and Room-type rules remain the
   authoritative implementations. This pass composes those rules; it does not duplicate them.

## 3. Simplicity constraints

This section is normative. It exists specifically to prevent overengineering.

### 3.1 At most one new production workflow class

The implementation may add **one** production class for Room workflow orchestration, named
`RoomWorkflow` unless an existing class proves to be a better fit during implementation. It should
use the narrowest visibility compatible with direct packet delegation. Because MCA's network packet
classes and world-data classes are separate Java packages, making `RoomWorkflow` public is preferable
to adding a redundant public `VillageManager` facade solely to hide a package-private workflow.

Do not add:

- `RoomService` + `RoomRepository` + `RoomCommandHandler` layers;
- interfaces for a class with one implementation;
- dependency-injection plumbing;
- a generic transaction abstraction;
- a generic result/command framework shared with unrelated MCA systems;
- a second scanner/matcher/type resolver;
- a new persistence format merely to support rollback.

`RoomWorkflow` may directly collaborate with `VillageManager`, `Village`, `StructureScanner`,
`BuildingRoomScanner`, `RegisteredRoomReconciler`, and `RoomTypeResolver`. These are already the
domain objects for the operation.

### 3.2 Keep the existing network protocol

`ReportBuildingMessage(Action, String data)` is weakly typed internally, but replacing the packet
format is not required by this pass. The packet must parse the existing string payload once at its
boundary and pass typed values to the workflow.

Do not create new packet types solely to make Java call sites prettier.

### 3.3 Do not split large classes by line count alone

`VillageManager`, `Village`, and `BlueprintScreen` are large, but size alone is not sufficient reason
to create more classes. Extract only the coherent Room workflow described here.

The Blueprint/client cleanup found in the branch audit is explicitly deferred.

### 3.4 Consolidate rules before adding abstractions

When two existing paths derive the same domain candidates or make the same business decision, first
extract the smallest shared helper that represents that rule. Do not solve duplication by adding a
new facade while leaving both old implementations intact.

For this pass specifically:

- initial requests and confirmed polymorph requests must share the same analyze/type-select/commit
  workflow;
- full scan must reuse the normal registered-Room update path rather than maintain a second update
  algorithm;
- an extraction is successful only when the duplicated old decision code is deleted.

This is a consolidation rule, not permission to reorganize unrelated scanner, connector, client, or
Village systems.

## 4. Required architecture

The production flow becomes:

```text
network request
    -> ReportBuildingMessage parses transport data
    -> RoomWorkflow performs one Room business operation
        -> existing VillageManager/Village/scanner/domain policies
        -> commit OR type-choice request OR validation failure
    -> packet renders/sends the result
```

For a full scan:

```text
live Village building state
    -> complete in-memory BuildingStateSnapshot
    -> apply Room updates sequentially using the normal Room update path
        -> all succeed: finalize once
        -> any fail: restore snapshot + lastBuildingId exactly
```

There is no new parallel implementation of scanning, matching, Room identity, or type resolution.

## 5. Atomic full-scan transaction

### 5.1 Snapshot scope

Introduce one explicit snapshot of the Village state that a Room/full-scan mutation may alter. The
snapshot belongs with the aggregate whose state it captures, preferably as a package-private nested
record/class in `Village`.

Equivalent shape:

```java
record BuildingStateSnapshot(
        List<Building> rooms,
        List<Structure> structures,
        List<LogicalBuilding> logicalBuildings) {
}
```

Exact collection types may differ, but the snapshot must be immutable from the caller's perspective
and contain deep copies of mutable domain objects.

The snapshot deliberately excludes unrelated Village state such as residents, reputation, taxes,
name, auto-scan, and external/grouped POIs because `fullScan()` does not mutate those fields.

### 5.2 Copy semantics

Snapshot copies are **runtime-domain copies**, not save/load round trips.

Required behavior:

- a copied `Building` preserves geometry, POIs, Room IDs, Structure/Floor IDs, type, forced-type
  state, contribution state, source position, and `lastScan`;
- a copied `Structure` preserves its existing complete copy semantics;
- a copied `LogicalBuilding` preserves ID, Ground Floor reference, Main Room ID, and inheritance
  flag;
- no copy shares mutable collections or mutable domain children with the live aggregate.

`Building.copy()` must copy the live domain fields directly (or through existing non-persistence
copy helpers). It must not call `save()`/NBT construction. Transaction correctness must remain
independent from which fields happen to be persisted.

`LogicalBuilding` should gain a small `copy()` method rather than introducing a serializer-based
transaction helper.

### 5.3 Restore semantics

`Village.restoreBuildingState(snapshot)` must restore all three collections together:

- Rooms;
- Structures;
- Logical Buildings.

After restoration it may recompute derived Village bounds, but it must not call a reconciliation
method that intentionally changes the captured logical metadata. The restored state itself is the
authoritative pre-transaction state.

The transaction owner (`VillageManager.fullScan()` or its extracted Room workflow equivalent) also
captures and restores `lastBuildingId`.

### 5.4 Transaction algorithm

The simple required algorithm is:

```text
snapshot Village building state
snapshot lastBuildingId

for each Room ID selected for full scan:
    analyze using the existing registered-Room update path
    if analysis fails:
        restore snapshot and lastBuildingId
        return failure

    apply using the existing registered-Room commit path without finalizing globally
    if apply fails:
        restore snapshot and lastBuildingId
        return failure

finalize the Village mutation once
return success
```

Do **not** replace this with a generic transaction manager or try to pre-compute every Room update
against the original Village. Later Room scans may legitimately depend on state produced by earlier
successful Room updates in the same full scan. A complete rollback snapshot is both simpler and
safer.

### 5.5 Atomicity acceptance rule

For any failure on Room N of a full scan, observable building-domain state after the method returns
must equal the state immediately before the full scan began, including:

- Room count and IDs;
- Room footprints, POIs, types, forced flags, inheritance-contribution flags, source positions, and
  `lastScan`;
- Structure IDs, logical-building IDs, floor geometry, floor numbers, and connector metadata;
- Logical Building Ground Floor, Main Room, and inheritance state;
- `lastBuildingId`.

It is acceptable for SavedData to remain marked dirty after rollback as long as the state that would
be saved is the restored pre-scan state. Do not add dirty-flag rollback machinery solely for this.

## 6. One Room workflow boundary

### 6.1 Responsibility

`RoomWorkflow` owns orchestration for the operations that currently require the packet to coordinate
analysis, polymorph/type selection, and commit:

- add Room;
- add Building/initial Room;
- add Floor;
- add Basement;
- update Room;
- Room inheritance updates that can require type selection;
- full scan.

It does **not** own physical scanning algorithms, Room partitioning, type matching rules, Village
merging, SavedData serialization, networking, or UI text.

Simple one-step edits such as rename Village, toggle auto-scan, remove a Building, or set a Main
Room do not need to be forced through this workflow unless doing so removes real duplication.

### 6.2 No generic action dispatcher in the domain

Prefer descriptive workflow methods over another all-purpose `execute(Action, Object...)` switch.

Equivalent API:

```java
Outcome addRoom(BlockPos source, String selectedType);
Outcome addBuilding(BlockPos source, String selectedType);
Outcome addFloor(BlockPos source, int expectedBuildingId, String selectedType);
Outcome addBasement(BlockPos source, int expectedBuildingId, String selectedType);
Outcome updateRoom(BlockPos source, int expectedRoomId, String selectedType);
Outcome updateInheritance(BlockPos source, int expectedRoomId,
                          boolean enabled, String selectedType);
Building.validationResult fullScan(Village village);
```

Exact visibility may differ. The important rule is that the network `Action` enum does not become a
dependency of the world/data package.

### 6.3 One small typed outcome

Operations that may require player type selection need one simple typed outcome. Use one record and
one small status enum rather than exceptions, nullable conventions, or a sealed class hierarchy.

Equivalent shape:

```java
record Outcome(
        Status status,
        Building.validationResult result,
        BlockPos source,
        List<String> matchingTypes,
        int expectedTargetId) {
}

enum Status {
    COMMITTED,
    REQUIRES_TYPE_SELECTION,
    FAILED
}
```

Rules:

- `COMMITTED` means the requested mutation completed and `result` is `SUCCESS`;
- `REQUIRES_TYPE_SELECTION` means no mutation occurred and `matchingTypes` contains the eligible
  choices;
- `FAILED` means no requested mutation was committed and `result` contains the existing validation
  reason;
- collections leaving the workflow are immutable snapshots;
- translation keys and player-facing messages are not part of this domain result.

If implementation proves an existing result type already expresses these semantics cleanly, reuse
it instead of adding `Outcome`. Do not maintain two equivalent result models.

### 6.4 Existing domain rules stay authoritative

`RoomWorkflow` must call, not duplicate:

- `StructureScanner` for physical Structure/Floor discovery;
- `BuildingRoomScanner` for Room geometry;
- `RegisteredRoomReconciler` / `RoomIdentityPolicy` for update identity;
- `RoomTypeResolver` for type eligibility/selection;
- `Village` for logical-building ownership and Room membership;
- `VillageManager` for global Village lookup, ID ownership, finalization, and SavedData dirtiness.

There must be only one implementation of rules such as:

- `0` eligible types -> normal fallback;
- `1` eligible type -> automatic choice;
- `2+` eligible types -> player choice;
- expected Room/Building ID validation after a polymorph round trip;
- registered-Room update overlap/identity validation.

## 7. `VillageManager` after the refactor

`VillageManager` remains MCA's world-level owner for:

- loading/saving Village SavedData;
- the Village map;
- nearest-Village lookup;
- queued/report/auto-scan entry points;
- global Building/Village ID counters;
- external/grouped Building processing;
- Village merge/removal/final mutation;
- unrelated world systems already hosted there (for example bounty/reaper coordination).

Room-specific workflow methods may remain as thin compatibility/delegation methods if existing
callers/tests benefit from them, but they must not retain a second copy of orchestration logic beside
`RoomWorkflow`.

Do not attempt to turn `VillageManager` into a repository, nor split every unrelated responsibility
in this pass.

## 8. Network boundary after the refactor

### 8.1 `ReportBuildingMessage`

The packet is responsible for:

1. parsing its existing wire payload;
2. mapping network actions to one descriptive workflow call;
3. sending `BuildingPolymorphMessage` when the outcome requests a type selection;
4. translating validation/success outcomes into existing client messages;
5. sending the refreshed Village response in the existing lifecycle.

The packet must not independently:

- run a scanner;
- decide whether a Room update is ambiguous;
- validate selected Room lineage;
- commit Room geometry;
- choose a Room type;
- duplicate expected-target conflict rules.

### 8.2 `ConfirmBuildingPolymorphMessage`

Confirmation must re-enter the same `RoomWorkflow` method as the initial request, supplying the
selected type and expected target ID. This preserves the current important race-safety behavior:
the server revalidates the operation against current state instead of trusting the earlier analysis.

Do not add a server-side pending-command cache for polymorph selection. Re-analysis with the
expected target ID is simpler and already matches the existing model.

## 9. Building collection encapsulation is deferred

The branch audit also found that `Building.getBlocks()` exposes the mutable internal POI map while
production callers currently use it as read-only input. That is a valid invariant cleanup, but it is
not required for transaction or workflow correctness.

Do **not** change `getBlocks()` as part of this implementation pass unless a required transaction
copy cannot be made safely without doing so. Treat POI collection encapsulation as a separate small
follow-up with its own focused test/review.

## 10. Error handling and mutation rules

Business-rule failures continue to use existing `Building.validationResult` values. Do not convert
normal validation failures into exceptions.

Exceptions remain appropriate for violated programmer invariants such as registering an impossible
Room/Structure relationship.

For every workflow method:

- analysis either returns detached candidate data or a failure;
- a type-selection outcome performs no requested mutation;
- a failed validation performs no requested mutation;
- commit happens only after the operation is fully validated;
- full scan is the only multi-step workflow in this scope that intentionally mutates several Room
  updates before the final result, and it is protected by the complete rollback snapshot.

## 11. Test strategy

Use TDD. Tests must demonstrate failures before production changes where the current behavior is
wrong.

### 11.1 Full-scan transaction tests

Add focused common tests that force a failure after at least one earlier Room update has mutated the
live Village.

Required assertions after rollback:

1. Room geometry/POIs equal the pre-scan state;
2. `Building.lastScan` equals the pre-scan value;
3. Structure/floor state equals the pre-scan state;
4. Main Room ID equals the pre-scan value;
5. Ground Floor reference equals the pre-scan value;
6. inheritance state equals the pre-scan value;
7. Room IDs and next `lastBuildingId` allocation behave as if the failed full scan never occurred.

Do not make these tests depend on NBT serialization to establish expected transaction behavior.

### 11.2 Workflow tests

Use focused unit/domain tests for:

- zero matching types commits/falls back according to the existing Room rule;
- one matching type commits automatically;
- multiple matching types return `REQUIRES_TYPE_SELECTION` without mutation;
- confirmed type selection revalidates and commits through the same workflow;
- stale expected Room/Building ID returns the existing conflict/failure result without mutation;
- update/add Floor/add Basement retain their current target validation;
- inheritance type-selection round trip uses the same workflow boundary.

Do not duplicate scanner geometry matrices here. Existing floor/connector/GameTests remain the
integration coverage for physical behavior.

### 11.3 Network tests

Network tests only need to prove mapping/presentation behavior that is not covered by the domain
workflow, such as:

- `REQUIRES_TYPE_SELECTION` sends the polymorph message;
- failure/success maps to the existing translation behavior;
- malformed string payloads are rejected/defaulted at the packet boundary and never leak into
  domain parsing.

Do not retest Room topology through packet tests.

### 11.4 Regression verification

After the focused tests:

- run `:common:test`;
- compile common, Fabric, and NeoForge;
- run the existing NeoForge MCA GameTest suite;
- run `git diff --check`;
- inspect the production diff to ensure orchestration was removed from old locations rather than
  copied into the new workflow.

## 12. Required simplifications/deletions

Once equivalent RED/GREEN coverage exists, remove or collapse:

- Room analyze/commit/type-choice orchestration from `ReportBuildingMessage`;
- duplicate polymorph confirmation branches that do not re-enter the same workflow;
- the incomplete `fullScan()` Room/Structure-only rollback snapshot;
- serializer-based Room cloning used only for rollback;
- duplicated ambiguity/selected-type checks that are already authoritative in the Room workflow or
  `RoomTypeResolver`;
- any obsolete helper created solely by the old packet-driven flow.

Do not retain both old and new paths "for safety". Tests are the safety net.

## 13. Production-diff audit items explicitly deferred

The broader `origin/1.21.1` production audit identified other maintainability opportunities. They do
not belong in this implementation pass:

### `BlueprintScreen`

Its page/widget construction is large and could later be separated into page-specific builders.
This is client maintainability work, not part of Room transaction correctness.

### `BlueprintMapRenderer`

Its render method could later derive a compact immutable hover/render state before executing render
passes. Current behavior is covered and this does not belong in the server workflow refactor.

### Broad `VillageManager` decomposition

Unrelated systems such as bounty hunters, external Buildings, ticking, and Village merging stay in
place. This specification extracts only the coherent Room workflow.

### Scanner/floor/connector classes

`FloorSurface`, `SelectedFloorScanner`, `FloorSurfacePartitioner`, `StructureConnector`,
`BuildingFloorRegion`, `RegisteredRoomReconciler`, `RoomIdentityPolicy`, `StructureFloor`, and
`RoomTypeResolver` are not to be reorganized merely to reduce branch diff size.

## 14. Coding constraints

Apply the reviewed ECC/backend principles in the form appropriate to this Java/Minecraft codebase:

- **KISS:** one workflow helper, one explicit snapshot, no framework;
- **DRY:** one business path for initial request and confirmed polymorph request;
- **YAGNI:** no generic transaction/command/repository infrastructure;
- **single responsibility:** packet = transport, workflow = Room use-case orchestration,
  VillageManager = world/SavedData aggregate coordination, existing scanners/policies = domain
  mechanics;
- **immutable boundaries:** snapshot/result collections are copied before leaving their owner;
- **early exits:** validation failures return before mutation;
- **descriptive methods:** prefer `addFloor`, `updateRoom`, `restoreBuildingState` over mode booleans
  or generic `process(...)` methods;
- **explicit failure results:** preserve `Building.validationResult` instead of swallowing or
  translating errors deep in the domain;
- **test behavior, not implementation details:** especially transaction rollback and type-selection
  semantics.

Framework-specific web backend patterns (controllers, repositories, DTO mapper layers, dependency
injection containers) are not applicable and must not be imported into MCA by analogy.

## 15. Mandatory acceptance scenarios

The implementation is complete only when all of these hold:

1. **Late full-scan failure:** Room 1 updates, Room 2 fails, and all building-domain state is exactly
   restored.
2. **Runtime field rollback:** the same failure restores Room `lastScan`, proving rollback is not
   merely an NBT persistence clone.
3. **Logical metadata rollback:** pre-scan Main Room, Ground Floor, and inheritance metadata is
   exactly restored after a later full-scan failure, even if earlier updates triggered logical
   reconciliation.
4. **ID rollback:** a failed full scan does not consume IDs that alter the next successful Room ID.
5. **Successful full scan:** every selected Room update commits and Village finalization occurs once
   after the sequence.
6. **Ambiguous type:** initial request returns a selection outcome without Room mutation.
7. **Confirmed type:** confirmation revalidates current state and commits through the same workflow.
8. **Stale confirmation:** changed expected Room/Building identity is rejected without mutation.
9. **Packet thinness:** packet code contains transport parsing/response mapping but no Room scanner,
   reconciliation, or type-choice business implementation.
10. **No floor regression:** existing common tests and MCA GameTests retain the scanner/floor/
    connector behavior established by the floor simplification specification.

## 16. Non-goals

This specification does not require:

- changing the floor resolver or connector semantics;
- changing physical discovery or Room partitioning;
- changing Building/Structure/LogicalBuilding persistence format;
- a DFU migration;
- replacing the existing network payload format;
- generic undo/redo support;
- cross-Village transactions;
- moving every `VillageManager` method into services;
- redesigning BlueprintScreen or BlueprintMapRenderer;
- introducing repository, command bus, event sourcing, unit-of-work, dependency-injection, or
  generic transaction abstractions;
- changing the already accepted Room type polymorph rule;
- committing or pushing unrelated work currently present in the working tree.

## 17. Alternatives rejected

### Save/reload the whole Village as rollback

Rejected because persistence format and transaction state are different concerns. It can lose
runtime-only fields, can accidentally include unrelated Village systems, and makes transaction
correctness depend on NBT coverage.

### Replace the Village object in `VillageManager` on rollback

Rejected because callers may still hold references to the existing Village instance, and replacing
the aggregate is broader than restoring the building state that the transaction actually owns.

### Stage every full-scan update before mutating anything

Rejected for this pass. It creates a substantially more complex planning model, and later Room
analysis may depend on earlier accepted updates. Snapshot + sequential apply + rollback is easier to
verify and sufficient.

### Generic transaction framework

Rejected as YAGNI. Only full scan currently needs multi-step rollback in this domain.

### Full service/repository architecture

Rejected. MCA already has domain objects with authoritative behavior. One Room workflow helper is
enough to remove packet orchestration without creating web-backend ceremony.

### Leave orchestration in the packet and only fix rollback

Rejected because it leaves two owners for Room business flow and preserves the main maintainability
problem found in the production diff audit.

## 18. Completion rule

A smaller file count or lower line count is not the success metric.

This pass is complete when:

- full scan has demonstrably complete rollback semantics;
- one Room workflow owns analyze/type-select/commit orchestration;
- packets are thin adapters to that workflow;
- no duplicate old orchestration remains;
- no generic infrastructure was introduced beyond the one coherent workflow helper and explicit
  Village building-state snapshot;
- focused tests, common tests, loader compiles, and MCA GameTests pass;
- unrelated working-tree changes are preserved and are not staged/committed as part of this pass.
