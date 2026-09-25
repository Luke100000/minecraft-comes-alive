# Floor / Room Single-State Transaction Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Delete competing Floor/Room/Structure/LogicalBuilding mutation-finalization paths so functional building state is published through one Village-owned transactional boundary, with a net-negative production-Java diff.

**Architecture:** Keep the existing scanner, `FloorGeometry`, Room partitioning, planner, workflow, and persisted maps. Add no production class. Consolidate the existing `BuildingStateSnapshot` rollback + logical reconciliation + dimension refresh + dirtiness behavior into one small package-owned `Village.publishBuildingMutation(Runnable)` boundary, route existing mutation methods through it, and delete duplicated finalization/rollback plumbing around those methods.

**Tech Stack:** Java, Gradle, Minecraft 1.21.1, common + Fabric + NeoForge modules, JUnit and NeoForge GameTest.

**Spec:** `docs/superpowers/specs/2026-09-25-floor-room-single-state-transaction-design.md`

## Global Constraints

- Production Java changed by this plan must be net-negative in lines overall; tests/docs may grow.
- Add no production class, transaction framework, repository/service layer, second state model, second geometry model, cache, or persistence field.
- `FloorGeometry` remains the sole spatial Floor-membership truth.
- `SelectedFloorScanner`, `RoomPartitioner`, `RoomScanPlanner`, and `RoomWorkflow` keep their current ownership boundaries.
- Reuse `Village.BuildingStateSnapshot`; do not introduce another rollback representation.
- Preserve current save format and network protocol.
- Preserve unrelated staged/unstaged user work; do not reset, stash, broad-clean, or rewrite the branch.
- Use TDD for changed behavior and the smallest relevant verification before broad verification.
- For the final Java diff, run all four `java-code-review-cleanup` lenses: reuse, quality, correctness, efficiency.

## Review Focus

1. **Post-publication exception:** if derived-state refresh throws after maps change, Rooms/Structures/LogicalBuildings must return exactly to the pre-operation snapshot. Task 1 adds a Main Room rollback regression and keeps the existing Floor-refresh rollback regression.
2. **Main Room re-anchoring:** changing Main Room must update Ground Floor numbering atomically and preserve Room preferences. Task 1 extends `VillageFloorSystemTest` around `setMainRoom`.
3. **Inheritance/type coupling:** invalid or stale type selection must not partially change inheritance/contribution state; successful disable + selected type must publish together. Task 2 keeps/strengthens the existing inheritance atomicity tests.
4. **Removal invariants:** Room/Floor/Structure/logical-building removal must not leave dangling identity and must recompute logical ownership/bounds once. Task 3 extends the existing removal tests.
5. **Full-scan late failure:** if Room A refreshes and later Room B fails, Room A and aggregate metadata must roll back to the pre-scan state. Task 4 adds an end-to-end GameTest using stable Room-ID order.

---

### Task 1: Establish the single Village publication boundary

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Village.java:185-430,969-1040`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/VillageFloorSystemTest.java:227-260,778-835`
- Test: `common/src/test/java/net/conczin/mca/server/world/data/VillageManagerExpandedRoomCommitTest.java`

**Interfaces:**
- Consumes: existing `Village.snapshotBuildingState()`, `restoreBuildingState(...)`, `refreshLogicalBuildings()`, `calculateDimensions()`, `markDirty()`.
- Produces: package-private `void publishBuildingMutation(Runnable mutation)` as the only building-domain publication/finalization boundary used by `Village` and `VillageManager`.

- [ ] **Step 1: Add the RED rollback regression for a non-Floor mutation**

Add a small `ThrowOnceMarkDirtyVillage` test fixture beside the existing `ThrowOnceCalculateVillage`. Register a ground Main Room and an upper Room, capture the original Main Room ID and floor numbers, then force the next `markDirty()` to throw while changing Main Room. This fails against the current code because `setMainRoom(...)` mutates Main Room/floor numbering before calling `markDirty()` and has no rollback:

```java
@Test
void failedMainRoomPublicationRestoresMainRoomAndFloorNumbers() {
    ThrowOnceMarkDirtyVillage village = new ThrowOnceMarkDirtyVillage();
    StructureFloor ground = TestStructureFloors.create(0, 64, 68, 0, region(64));
    StructureFloor first = TestStructureFloors.create(1, 68, 72, 1, region(68));
    Structure structure = new Structure(10, BlockPos.ZERO, List.of(ground, first));
    Building main = room(100, 10, 0, true);
    Building upper = room(101, 10, 1, true);
    registerStructure(village, structure, main);
    registerRoom(village, upper);

    village.failNextMarkDirty();

    assertThrows(IllegalStateException.class, () -> village.setMainRoom(upper));
    assertEquals(100, village.getLogicalBuilding(10).orElseThrow().mainRoomId());
    assertEquals(0, village.getStructure(10).orElseThrow().getFloor(0).orElseThrow().floorNumber());
    assertEquals(1, village.getStructure(10).orElseThrow().getFloor(1).orElseThrow().floorNumber());
}
```

Add the fixture in the same test class:

```java
private static final class ThrowOnceMarkDirtyVillage extends Village {
    private boolean failNextMarkDirty;

    private ThrowOnceMarkDirtyVillage() {
        super(1, null);
    }

    void failNextMarkDirty() {
        failNextMarkDirty = true;
    }

    @Override
    public void markDirty() {
        if (failNextMarkDirty) {
            failNextMarkDirty = false;
            throw new IllegalStateException("forced dirty failure");
        }
        super.markDirty();
    }
}
```

- [ ] **Step 2: Run the focused RED test**

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.VillageFloorSystemTest.failedMainRoomPublicationRestoresMainRoomAndFloorNumbers' --no-daemon
```

Expected before the production change: FAIL because `setMainRoom(...)` mutates logical state before `markDirty()` throws and there is no shared rollback boundary.

- [ ] **Step 3: Add the smallest shared publication helper**

In `Village`, add one package-private helper adjacent to the existing snapshot methods:

```java
void publishBuildingMutation(Runnable mutation) {
    BuildingStateSnapshot snapshot = snapshotBuildingState();
    try {
        mutation.run();
        refreshLogicalBuildings();
        calculateDimensions();
        markDirty();
    } catch (RuntimeException exception) {
        restoreBuildingState(snapshot);
        throw exception;
    }
}
```

Do not add a generic transaction type, result wrapper, boolean flag, nested transaction stack, or second snapshot model.

- [ ] **Step 4: Collapse `publishFloorRefresh(...)` onto the shared boundary**

Keep all existing validation and `nextBuildings` construction. Replace its local snapshot/try/reconcile/calculate block with:

```java
publishBuildingMutation(() -> {
    structures.put(refreshed.getId(), refreshed);
    buildings.clear();
    buildings.putAll(nextBuildings);
});
return true;
```

Delete the old local `BuildingStateSnapshot`, `try/catch`, direct `reconcileLogicalBuilding(...)`, and direct `calculateDimensions()` calls.

- [ ] **Step 5: Route registration, Room/Floor/Structure removal, and Main Room change through the boundary**

For each operation, preserve its existing precondition checks, then put only the mutation inside `publishBuildingMutation(...)` and delete duplicated reconciliation/dimension/dirtiness calls.

Representative forms:

```java
publishBuildingMutation(() -> buildings.remove(roomId));
return true;
```

```java
publishBuildingMutation(() -> logical.setMainRoomId(room.getId()));
return true;
```

```java
publishBuildingMutation(() -> {
    for (Structure structure : getBuildingStructures(buildingId)) {
        // existing remove-Floor loop body unchanged
    }
});
return true;
```

`registerStructure(...)` and `registerRoom(...)` must perform their existing validation before calling the boundary; invalid registration must still throw before live state changes.

- [ ] **Step 6: Run focused common tests**

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.VillageFloorSystemTest' --tests 'net.conczin.mca.server.world.data.VillageManagerExpandedRoomCommitTest' --tests 'net.conczin.mca.server.world.data.VillageBuildingStateSnapshotTest' --no-daemon
```

Expected: PASS, including the pre-existing `failedPostPublicationStepRestoresPreviousAggregateState` test and the new Main Room rollback test.

- [ ] **Step 7: Check production LOC before moving on**

Run:

```powershell
git diff --numstat -- common/src/main/java/net/conczin/mca/server/world/data/Village.java
```

Expected: this task should already trend net-negative or close to neutral. If it grows substantially, simplify before proceeding rather than compensating with unrelated deletion.

### Task 2: Collapse inheritance and type edits into one publication operation

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Village.java:981-1040`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java:477-490`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/VillageFloorSystemTest.java:152-230,631-640`

**Interfaces:**
- Consumes: `Village.publishBuildingMutation(Runnable)` from Task 1 and existing `RoomInheritanceUpdate` / `RoomTypeResolver` validation.
- Produces: inheritance/contribution + associated type changes as one publication; forced-type edit also publishes through the same Village boundary.

- [ ] **Step 1: Strengthen the inheritance no-partial-mutation assertion**

In the existing invalid polymorph test, assert both sharing state and type state remain unchanged:

```java
String typeBefore = room.getType();
boolean forcedBefore = room.isTypeForced();

assertEquals(Building.validationResult.INVALID_TYPE,
        village.commitRoomInheritanceUpdate(update, "not_eligible"));
assertEquals(previousEnabled, room.contributesToMain());
assertEquals(typeBefore, room.getType());
assertEquals(forcedBefore, room.isTypeForced());
```

Use `isBuildingInheritanceEnabled(room)` instead of `contributesToMain()` when the fixture is the Main Room.

- [ ] **Step 2: Run the focused inheritance tests before refactoring**

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.VillageFloorSystemTest.invalidPolymorphChoiceDoesNotDisableRoomSharing' --tests 'net.conczin.mca.server.world.data.VillageFloorSystemTest.ambiguousInheritancePolymorphAppliesSharingAndSelectedTypeAtomically' --no-daemon
```

Expected: PASS; these are characterization tests protecting the refactor.

- [ ] **Step 3: Route the public inheritance setters through the shared publication boundary**

Preserve the existing public methods to avoid unnecessary API/test churn. Keep their current validation, replace their direct mutation + `markDirty()` with the shared boundary, and return success afterward:

```java
publishBuildingMutation(() -> logical.setInheritanceEnabled(enabled));
return true;
```

```java
publishBuildingMutation(() -> room.setContributesToMain(contributes));
return true;
```

Do **not** call those public setters from `commitRoomInheritanceUpdate(...)`, because that would publish the sharing flag before the coupled type change. Instead, after all validation and type resolution, publish the complete inheritance/type change once:

```java
publishBuildingMutation(() -> {
    if (update.mainRoom()) {
        logical.setInheritanceEnabled(update.enabled());
    } else {
        room.setContributesToMain(update.enabled());
    }
    if (!update.enabled()) {
        room.setType(forcedType != null ? forcedType : automaticType);
        room.setTypeForced(forcedType != null);
    }
});
return Building.validationResult.SUCCESS;
```

Resolve `logical` and `automaticType` before mutation. Preserve all stale/invalid/type-selection guard clauses.

- [ ] **Step 4: Route forced Room type publication through Village**

Keep `VillageManager.forceRoomType(...)` responsible for resolving the target and desired next type, but publish the actual Room field changes through the shared boundary:

```java
String resolvedType = room.getType().equals(type)
        ? RoomTypeResolver.create(village).resolve(room).updatedType(null)
        : type;
boolean forced = !room.getType().equals(type);
if (resolvedType == null) return BuildingEditResult.NO_BUILDING;

village.publishBuildingMutation(() -> {
    room.setType(resolvedType);
    room.setTypeForced(forced);
});
return BuildingEditResult.SUCCESS;
```

Delete the direct `village.markDirty()` and the old duplicated field branches.

- [ ] **Step 5: Run focused tests**

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.VillageFloorSystemTest' --tests 'net.conczin.mca.server.world.data.RoomWorkflowTest' --no-daemon
```

Expected: PASS.

### Task 3: Remove Manager/Village finalization duplication and transactionalize logical-building removal

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Village.java:413-430,1050-1069`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java:237-347,517-579`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/VillageFloorSystemTest.java:118-150,658-770`

**Interfaces:**
- Consumes: `Village.publishBuildingMutation(Runnable)` and existing raw logical-building removal logic.
- Produces: `VillageManager.finalizeVillageMutation(...)` handles only cross-Village merge/SavedData concerns; internal logical reconciliation/dimensions are already complete before it runs.

- [ ] **Step 1: Add/strengthen removal invariant assertions**

Extend the existing logical-building and Floor removal tests so successful removal asserts no remaining Room references a missing Structure/Floor:

```java
for (Building remaining : village.getRooms().toList()) {
    Structure owner = village.getStructure(remaining.getStructureId()).orElseThrow();
    assertTrue(owner.getFloor(remaining.getFloorId()).isPresent());
}
```

Also retain the existing assertions that Main Room cannot be removed and terminal Floor removal affects only the selected logical building.

- [ ] **Step 2: Run removal characterization tests**

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.VillageFloorSystemTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 3: Make Manager-level logical-building deletion enter the Village publication boundary**

Keep the existing raw `removeLogicalBuilding(int)` body as the internal operation used by reconciliation. At the external Manager call site, replace the raw call + later duplicated finalization with:

```java
village.publishBuildingMutation(() -> village.removeLogicalBuilding(structure.getLogicalBuildingId()));
```

Do not wrap the internal `reconcileLogicalBuilding(...) -> removeLogicalBuilding(...)` call in another transaction; it already runs inside the outer publication when invoked from `refreshLogicalBuildings()`.

- [ ] **Step 4: Shrink `VillageManager.finalizeVillageMutation(...)`**

Delete:

```java
target.refreshLogicalBuildings();
target.calculateDimensions();
```

Those are now guaranteed by the Village publication boundary. Keep only cross-Village merge/removal logic and `setDirty()` because the manager owns its Village map and SavedData lifecycle.

Delete any now-redundant `refreshLogicalBuildings()` in `commitInitialRoom(...)` and duplicated `setDirty()` calls immediately following a manager finalization.

- [ ] **Step 5: Simplify orphaned-room removal**

In `VillageManager.removeBuilding(...)`, avoid repeatedly publishing one removal per orphaned Room. Validate/collect the IDs first, then perform the batch removal in one Village publication:

```java
village.publishBuildingMutation(() -> village.removeRooms(orphanedRoomIds));
```

If the target itself is the only orphan and the current list-building logic omits it, fix the collection so the batch contains the actual orphaned target; do not keep a separate one-Room branch.

- [ ] **Step 6: Run focused common tests**

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.VillageFloorSystemTest' --tests 'net.conczin.mca.server.world.data.VillageManagerExpandedRoomCommitTest' --no-daemon
```

Expected: PASS.

### Task 4: Prove full-scan rollback and delete dead ID rollback state

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java:439-474`
- Modify: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java:1844-1940`

**Interfaces:**
- Consumes: stable `VillageManager.fullScanRoomIds(...)`, normal `RoomWorkflow.analyzeRegisteredRoomUpdate(...)`, normal registered-Room commit path, and `Village.BuildingStateSnapshot`.
- Produces: direct regression that a later Room failure restores an earlier successful Room refresh; removes obsolete `lastBuildingId` save/restore from `fullScan()`.

- [ ] **Step 1: Add the end-to-end rollback GameTest**

Add a GameTest beside the existing selected-Room update tests. Reuse `buildClosedRoom(...)`, `placeDoor(...)`, `RoomWorkflow`, and the two-Room registration pattern already used in `selectedRoomSplitReplacesOnlySelectedComponentAndLeavesSiblingUntouched(...)`.

Test sequence:

```java
// 1. Build/register Room A then Room B so A has the smaller persisted ID.
// 2. Capture A/B cells, persisted Floor geometry, Main Room ID and inheritance state.
// 3. Move A's outer wall one block so analyzeRegisteredRoomUpdate(A) is SUCCESS
//    and its replacement cells differ from persisted A.
// 4. Break B's enclosure (remove one full wall/roof route) so B's rescan fails.
// 5. Call manager.fullScan(village).
// 6. Assert result != SUCCESS.
// 7. Assert A/B cells, StructureFloor geometry, Main Room and inheritance equal step 2.
```

Before calling `fullScan`, explicitly prove Room A would change:

```java
RegisteredRoomUpdate firstUpdate = workflow.analyzeRegisteredRoomUpdate(
        village, firstRoomId, firstSeed);
helper.assertTrue(firstUpdate.result() == Building.validationResult.SUCCESS,
        "first Room was not a valid refresh candidate");
helper.assertTrue(!firstUpdate.replacementRoom().getFloorCells().equals(firstCellsBefore),
        "first Room fixture does not exercise rollback of a real earlier mutation");
```

Use the existing stable-ID-order unit test as the ordering contract; do not add a test-only production hook to `fullScan()`.

- [ ] **Step 2: Run GameTests with the new regression present**

Run:

```powershell
.\gradlew.bat :neoforge:runGameTestServer --no-daemon --no-build-cache --max-workers=1
```

Expected before cleanup: the new full-scan rollback regression should PASS if the current outer Village snapshot is correct. This is a characterization regression, not a forced-red behavioral change. Record any unrelated baseline failures by exact test name.

- [ ] **Step 3: Delete dead `lastBuildingId` rollback plumbing**

The registered-Room full-scan path does not allocate IDs. Remove:

```java
int previousLastBuildingId = lastBuildingId;
```

and delete `previousLastBuildingId` from `restoreFullScanSnapshot(...)`. Since the remaining helper would only call `village.restoreBuildingState(snapshot)`, inline that call at the two failure sites and delete `restoreFullScanSnapshot(...)` entirely:

```java
if (update.result() != Building.validationResult.SUCCESS) {
    village.restoreBuildingState(snapshot);
    return update.result();
}
```

Do the same for commit failure.

- [ ] **Step 4: Run the rollback GameTest again**

Expected: PASS with the same aggregate restoration behavior.

### Task 5: Four-lens cleanup, negative-LOC gate, and complete verification

**Files:**
- Review only the production/test files changed in Tasks 1-4.
- Modify those files only for worthwhile in-scope cleanup found by the review.

**Interfaces:**
- Consumes: completed implementation from Tasks 1-4.
- Produces: final verified diff satisfying the spec and negative production-Java LOC gate.

- [ ] **Step 1: Freeze the Java diff and run the reuse lens**

Inspect:

```powershell
git diff HEAD -- common/src/main/java/net/conczin/mca/server/world/data/Village.java common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java
```

Delete any duplicate snapshot/finalization helper, duplicate type-selection logic, or wrapper that can use the existing `publishBuildingMutation`, `RoomTypeResolver`, or `RoomWorkflow` path.

- [ ] **Step 2: Run the quality lens**

Check specifically for:

```text
- more than one building-state publication/finalization sequence
- public mutation seams with no production caller
- boolean flag arguments introduced by the cleanup
- nested transaction/publication calls
- comments narrating obvious code
- duplicated one-Room versus many-Room removal branches
```

Delete/simplify rather than abstract.

- [ ] **Step 3: Run the correctness lens**

Check every changed mutation path for:

```text
validate -> one publication -> reconcile -> dimensions -> dirty
```

and every exception path for snapshot restoration. Confirm `publishBuildingMutation` is never recursively entered through normal reconciliation. Confirm `fullScan()` still restores its outer snapshot after a later update failure.

- [ ] **Step 4: Run the efficiency lens**

Confirm no new scan, geometry materialization, persistent cache, repeated map lookup loop, or avoidable collection was added. Snapshotting is acceptable for user-driven building mutations because it provides the rollback contract; do not add a second snapshot around a call already inside `publishBuildingMutation`, **except for `fullScan()`'s intentional outer whole-scan snapshot**, which must remain because it rolls back earlier successful Room publications when a later Room fails.

- [ ] **Step 5: Enforce the negative production-Java LOC gate**

Run:

```powershell
$rows = git diff --numstat HEAD -- common/src/main/java |
    Where-Object { $_ -match '\.java$' }
$added = ($rows | ForEach-Object { [int](($_ -split "`t")[0]) } | Measure-Object -Sum).Sum
$deleted = ($rows | ForEach-Object { [int](($_ -split "`t")[1]) } | Measure-Object -Sum).Sum
Write-Output "production Java: +$added / -$deleted"
if ($deleted -le $added) { throw 'production Java must be net-negative' }
```

**Required:** the command completes without throwing. If not, do not waive the requirement; return to Tasks 1-3 and remove redundant code/visibility/wrappers until the gate passes.

- [ ] **Step 6: Run focused common tests**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.VillageFloorSystemTest' --tests 'net.conczin.mca.server.world.data.VillageManagerExpandedRoomCommitTest' --tests 'net.conczin.mca.server.world.data.VillageBuildingStateSnapshotTest' --tests 'net.conczin.mca.server.world.data.RoomWorkflowTest' --no-daemon
```

Expected: PASS.

- [ ] **Step 7: Run broad build verification**

```powershell
.\gradlew.bat :common:test :fabric:compileJava :neoforge:compileJava --no-daemon --no-build-cache --max-workers=1
```

Expected: PASS.

- [ ] **Step 8: Run the full NeoForge GameTest server**

```powershell
.\gradlew.bat :neoforge:runGameTestServer --no-daemon --no-build-cache --max-workers=1
```

Expected: all Floor/Room/transaction tests PASS. Compare any unrelated failure by exact test name against the pre-change baseline instead of hiding it.

- [ ] **Step 9: Run final diff hygiene**

```powershell
git diff --check
git diff --stat
git diff --numstat -- common/src/main/java | Select-String '\.java$'
```

Expected: `git diff --check` is clean and changed production Java is net-negative.

- [ ] **Step 10: Commit only the task files if the working-tree policy allows it**

This checkout already contains unrelated dirty work, so do **not** create a broad commit. If the user explicitly requests a commit after verification, stage only the exact files from this plan and use a focused message such as:

```text
refactor: unify floor room state publication
```
