# Exact-Cell Floor Scanner Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `SelectedFloorScanner`'s post-hoc Y-slice/semantic-band reconstruction with direct exact-cell selected-storey traversal while preserving stacked cells, stair/storey separation, furniture-neutral structural topology, connector behavior, Room partitioning, attachment evidence, and persistence compatibility.

**Architecture:** `SelectedFloorScanner` remains the Minecraft-facing world adapter and directly produces one selected `FloorGeometry`. Exact `FloorGeometry.Cell` positions stay canonical; physical connector blocks become metadata and never manufacture floor cells. A small local selected-storey traversal policy classifies discovered transitions as owned, edge, or other-storey during graph traversal, and alternate-storey boundary seeds are scanned separately only to preserve the existing `connectedFloors` attachment evidence contract.

**Tech Stack:** Java 21, Minecraft 1.21.1, Architectury common code, NeoForge GameTest, JUnit 5, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-08-exact-cell-floor-scanner-simplification-design.md`

## Global Constraints

- Exact 3D `FloorGeometry.Cell.feet` positions are the only canonical spatial floor identity.
- `surfaceY` remains exact physical support/traversal metadata; do not floor or bucket it.
- Multiple exact cells in the same X/Z column must remain valid and independently addressable.
- Projection is derived-only and must not drive discovery, storey ownership, Room partitioning, or exact lookup.
- Do not restore `MIN_MEANINGFUL_HEIGHT_SLICE_AREA`, equal-Y slice reconstruction, or a second flattened floor model under another name.
- Preserve semantic Floors: a walkable staircase must not merge lower and upper storeys.
- Preserve the historical staircase and stacked-cell behaviors as regressions, not their old implementation mechanisms.
- Doors/gates are traversal/boundary metadata and must not manufacture `FloorGeometry.Cell`s.
- Ladders/trapdoors attach semantic Floors but do not merge Room components.
- POI/wall/furniture evidence is downstream of topology and must not redefine floor geometry.
- Physical local neighbor discovery must not depend on persisted Floor identity.
- Keep `StructureFloor.BAND_TOLERANCE` only as semantic/local boundary evidence; do not use it to rebuild global height bands.
- Prefer existing owners and private/nested helpers. Do not add a new top-level class unless an existing owner would become less clear without it.
- Do not redesign Room identity, logical-building identity, Main Room selection, `RoomDFU`, or save format in this plan.
- Current checkout baseline is `dev/1.21.1` at/after spec commit `1be4a8878`; verify branch/status before every task and preserve unrelated dirty work.
- `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java` already exists untracked with the two bed regressions. Treat it as task-scoped evidence, inspect it before editing, and never stage unrelated files from its parent directory.

---

## File Structure / Responsibility Map

- `common/src/main/java/net/conczin/mca/server/world/data/FloorGeometry.java`
  - Exact floor cells, exact column lookup, projection, physical connector metadata container.
  - Must stop requiring connector positions to be floor-cell positions.
- `common/src/main/java/net/conczin/mca/server/world/data/FloorConnector.java`
  - Connector type + persisted marker value object only; no new topology subsystem.
- `common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java`
  - World connector classification/normalization, connector marker collection, vertical handoff resolution.
  - Must stop materializing connector cells.
- `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`
  - Physical cell discovery, local selected-storey traversal, alternate-storey evidence, exterior validation.
  - `SemanticBands`, `HeightBand`, slice components, and `FloorSelection` disappear by the end.
- `common/src/main/java/net/conczin/mca/server/world/data/RoomPartitioner.java`
  - Connected exact-cell Room components only.
  - No semantic storey logic and no connector-cell ownership.
- `common/src/main/java/net/conczin/mca/server/world/data/RoomPoiEvidence.java`
  - Downstream Room/perimeter/connector POI candidates with deterministic shared-wall ownership.
- `common/src/main/java/net/conczin/mca/server/world/data/StructureExpansionPolicy.java`
  - Consumes `connectedFloors` as semantic attachment evidence; behavior must remain intact.
- `common/src/main/java/net/conczin/mca/server/world/data/StructureFloor.java`
  - Stable persisted identity around exact geometry; serialized shape remains unchanged.
- `common/src/test/java/net/conczin/mca/server/world/data/SelectedFloorScannerTest.java`
  - Pure selected-storey graph and exact-cell regression coverage.
- `common/src/test/java/net/conczin/mca/server/world/data/FloorGeometryTest.java`
  - Exact-cell and connector-metadata invariants.
- `common/src/test/java/net/conczin/mca/server/world/data/RoomPartitionerTest.java`
  - Exact Room connected-component behavior with door gaps.
- `common/src/test/java/net/conczin/mca/server/world/data/RoomPoiEvidenceTest.java`
  - Perimeter/wall deterministic ownership; existing tests should remain green.
- `common/src/test/java/net/conczin/mca/server/world/data/VillageFloorSystemTest.java`
  - Stair-linked `connectedFloors` attachment proof; existing test must remain green.
- `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`
  - Real Minecraft collision/world regressions: furniture, full blocks, stairs/slabs, doors, cave shape, vertical connectors.

---

### Task 1: Harden structural-support vs occupancy discovery

**Files:**
- Modify: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`

**Interfaces:**
- Consumes: `SelectedFloorScanner.scan(...)`, `SelectedFloorScanner.inspectSurfaceCell(...)`, `WalkNodeEvaluator.getFloorLevel(...)`.
- Produces: one shared private occupancy predicate used by both ordinary discovery and seed-under-obstacle resolution.

- [ ] **Step 1: Preserve the existing bed RED/GREEN evidence and add a full-solid regression**

Keep `bedsDoNotChangeRoomFootprint()` and `scanFromBedTopUsesUnderlyingRoomFloor()` unchanged. Add a GameTest that places a full stone block in an otherwise valid interior floor column and proves that column is not accepted as a floor cell:

```java
@GameTest(batch = "mca_floor_full_block", templateNamespace = "minecraft",
        template = "bastion/blocks/air", timeoutTicks = 80)
public static void fullBlockIsNotTopologyNeutral(GameTestHelper helper) {
    BlockPos roomMin = helper.absolutePos(new BlockPos(4, 2, 4));
    buildClosedRoom(helper, roomMin, 5, 4);
    BlockPos blocked = roomMin.offset(1, 0, 1);
    helper.getLevel().setBlock(blocked, Blocks.STONE.defaultBlockState(), 3);

    SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(
            helper.getLevel(), roomMin.offset(3, 0, 2), 128, 16);

    helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
            "room scan failed: " + scan.result());
    helper.assertTrue(scan.floor().cellAt(blocked).isEmpty(),
            "full block was incorrectly accepted as a floor cell");
    helper.succeed();
}
```

- [ ] **Step 2: Add a representative low-occupancy regression**

Add a carpet test so the fix is not bed-specific:

```java
@GameTest(batch = "mca_floor_carpet", templateNamespace = "minecraft",
        template = "bastion/blocks/air", timeoutTicks = 80)
public static void carpetDoesNotChangeStructuralFootprint(GameTestHelper helper) {
    BlockPos roomMin = helper.absolutePos(new BlockPos(4, 2, 4));
    buildClosedRoom(helper, roomMin, 5, 4);
    BlockPos seed = roomMin.offset(2, 0, 2);
    Set<BlockPos> before = SelectedFloorScanner.scan(helper.getLevel(), seed, 128, 16)
            .floor().projection().cells();
    helper.getLevel().setBlock(roomMin.offset(1, 0, 1), Blocks.RED_CARPET.defaultBlockState(), 3);
    Set<BlockPos> after = SelectedFloorScanner.scan(helper.getLevel(), seed, 128, 16)
            .floor().projection().cells();
    helper.assertTrue(before.equals(after), "carpet changed the structural Room footprint");
    helper.succeed();
}
```

- [ ] **Step 3: Run the GameTests and confirm the current broad predicate is exposed**

Run:

```powershell
.\gradlew.bat :neoforge:runGameTestServer --rerun-tasks
```

Expected before the implementation change: the existing bed tests pass, while `mca_floor_full_block` fails because `collisionShape.max(Y) <= 1.0D` accepts a full cube.

- [ ] **Step 4: Replace the duplicated/broad obstacle rule with one strict occupancy helper**

In `SelectedFloorScanner`, replace `isFloorTopologyPassable(...)` and `isLowObstacle(...)` with one predicate whose contract is “empty or genuinely sub-full-height interior occupancy”:

```java
private static boolean isTopologyNeutralOccupancy(Level world, BlockPos pos) {
    BlockState state = world.getBlockState(pos);
    if (!state.getFluidState().isEmpty()) return false;
    var shape = state.getCollisionShape(world, pos);
    return shape.isEmpty() || shape.max(Direction.Axis.Y) < 1.0D;
}

private static boolean isLowObstacle(Level world, BlockPos pos) {
    BlockState state = world.getBlockState(pos);
    if (!state.getFluidState().isEmpty()) return false;
    var shape = state.getCollisionShape(world, pos);
    return !shape.isEmpty() && shape.max(Direction.Axis.Y) < 1.0D;
}
```

`supportedSurfaceY(...)` must call `isTopologyNeutralOccupancy(...)`, still require open headroom, still validate the support collision footprint below, and still return the exact `WalkNodeEvaluator.getFloorLevel(...)` value.

- [ ] **Step 5: Re-run GameTests and common scanner tests**

Run:

```powershell
.\gradlew.bat :neoforge:runGameTestServer --rerun-tasks
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.SelectedFloorScannerTest' --rerun-tasks
```

Expected: bed, bed-seed, carpet, and full-block tests all pass.

- [ ] **Step 6: Commit only Task 1 files**

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
git add neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java
git commit -m "fix: separate floor support from low occupancy"
```

---

### Task 2: Stop connector blocks from manufacturing floor geometry

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/FloorGeometry.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomPartitioner.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureFloor.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureExpansionPolicy.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/FloorGeometryTest.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/RoomPartitionerTest.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/StructureConnectorTest.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/StructureFloorTest.java`
- Test: `common/src/test/java/net/conczin/mca/server/world/data/StructureExpansionPolicyTest.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`

**Interfaces:**
- Produces: `FloorGeometry` can retain physical `FloorConnector.Marker`s whose positions are not `FloorGeometry.Cell`s.
- Produces: `StructureConnector.withConnectorMarkers(FloorGeometry, Collection<FloorConnector.Marker>)` merges metadata only.
- Removes: `StructureConnector.floorMembershipCells(...)`, `withConnectorAssociations(...)`, `connectorBoundaryCell(...)`, `verticalProbePositions(...)`, and `verticalColumnAtFloorCell(...)` after their callers/tests migrate.
- Preserves: `FloorGeometry.connectorMarkers()` serialized shape (`pos` + `type`) and `StructureFloor.save/load` field names.

- [ ] **Step 1: Rewrite the connector invariant test first**

Replace `connectorMustReferenceExistingExactCell()` with:

```java
@Test
void connectorMarkerDoesNotRequireOrCreateExactCell() {
    BlockPos door = new BlockPos(1, 64, 0);
    FloorGeometry geometry = new FloorGeometry(
            Set.of(cell(0, 64, 0), cell(2, 64, 0)),
            Map.of(door, FloorConnector.Type.DOOR));

    assertTrue(geometry.cellAt(door).isEmpty());
    assertEquals(List.of(new FloorConnector.Marker(door, FloorConnector.Type.DOOR)),
            geometry.connectorMarkers());
}
```

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.FloorGeometryTest' --rerun-tasks
```

Expected: FAIL with `Connector cell is not part of FloorGeometry`.

- [ ] **Step 2: Relax connector metadata membership without changing cell identity**

In `FloorGeometry`, rename the backing field/accessor to describe what it now means:

```java
private final Map<BlockPos, FloorConnector.Type> connectorTypesByPosition;

Map<BlockPos, FloorConnector.Type> connectorTypesByPosition() {
    return connectorTypesByPosition;
}
```

Remove the constructor requirement that every connector key exist in `cellsByPosition`. Keep connector positions immutable through `Map.copyOf(...)`. Update `connectorMarkers()` and exact call sites to use `connectorTypesByPosition()`.

Also change `FloorGeometry.flat(...)` so it retains every supplied valid marker instead of filtering markers with `if (cells.contains(marker.pos()))`. A connector position is metadata, not a projected floor-cell requirement.

Do **not** alter `Cell`, `cellsAtColumn`, `cellAt`, `projection`, or exact persistence field names.

- [ ] **Step 3: Replace connector-cell partitioning with ordinary exact-cell connectivity**

Replace `doorCellBelongsToOneRoomWithoutConnectingBothRooms()` with a door-gap test:

```java
@Test
void doorGapSplitsRoomsWithoutOwningDoorCell() {
    BlockPos door = new BlockPos(1, 64, 0);
    FloorGeometry geometry = geometry(
            Set.of(cell(0, 64, 0), cell(2, 64, 0)),
            Map.of(door, FloorConnector.Type.DOOR));

    List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry);

    assertEquals(2, components.size());
    assertEquals(2, components.stream().mapToInt(RoomPartitioner.Component::area).sum());
    assertTrue(components.stream().noneMatch(component -> component.contains(door)));
}
```

Then simplify `RoomPartitioner.partition(...)`: remove `boundaryCells`, `assignBoundaryClusters(...)`, and `boundaryClusters(...)`. A physical door/gate column is absent from exact floor cells, so the two sides are naturally separate connected components. Keep the shared exact-cell BFS and deterministic `owner(...)` helper for POI/shared-perimeter ownership.

- [ ] **Step 4: Replace connector materialization with marker-only association**

In `StructureConnector`, replace the three materialization helpers with:

```java
static List<FloorConnector.Marker> markers(Level world, Collection<BlockPos> connectors) {
    if (connectors == null || connectors.isEmpty()) return List.of();
    return connectors.stream()
            .map(raw -> {
                BlockState rawState = world.getBlockState(raw);
                BlockPos normalized = normalize(raw, rawState);
                FloorConnector.Type type = FloorConnector.Type.fromBlockState(world.getBlockState(normalized));
                return type == null ? null : new FloorConnector.Marker(normalized, type);
            })
            .filter(java.util.Objects::nonNull)
            .distinct()
            .toList();
}

static FloorGeometry withConnectorMarkers(
        FloorGeometry geometry, Collection<FloorConnector.Marker> markers) {
    LinkedHashMap<BlockPos, FloorConnector.Type> merged =
            new LinkedHashMap<>(geometry.connectorTypesByPosition());
    if (markers != null) {
        for (FloorConnector.Marker marker : markers) {
            merged.putIfAbsent(marker.pos(), marker.type());
        }
    }
    return new FloorGeometry(geometry.cells(), merged);
}
```

Update `SelectedFloorScanner.scan(...)` to call `StructureConnector.markers(...)` + `withConnectorMarkers(...)` and never synthesize a cell at a door/gate position.

- [ ] **Step 5: Update vertical connector code to consume physical marker positions**

Where `StructureConnector.verticalConnections(...)` currently treats a marker position as a floor cell and calls `verticalColumnAtFloorCell(...)`, instead resolve the physical connector column directly from the marker position:

```java
List<BlockPos> column = verticalColumnFromConnector(world, marker.pos());
if (column.isEmpty()) {
    BlockPos interaction = verticalInteractionConnector(world, marker.pos());
    column = interaction == null ? List.of() : verticalColumnFromConnector(world, interaction);
}
```

Keep `connectsFloors(...)` and exact `floorHandoff(...)` as the proof that the connector touches both Floors.

- [ ] **Step 6: Rewrite the connector unit tests around marker-only semantics**

In `StructureConnectorTest`, delete the tests whose only purpose was projecting/materializing a connector into a floor-cell column:

```text
verticalConnectorProbeSpansEntireFloorBandIncludingSupportBlock
connectorOneBlockBelowFloorSupportProjectsToWalkableFeet
connectorMembershipProjectsExactHandoffHeightIntoConnectorColumn
connectorAssociationsMaterializeBoundaryCellBeforeGeometryValidation
```

Replace the materialization assertion with:

```java
@Test
void connectorAssociationKeepsPhysicalMarkerWithoutCreatingCell() {
    BlockPos left = new BlockPos(0, 64, 0);
    BlockPos right = new BlockPos(2, 64, 0);
    BlockPos connector = new BlockPos(1, 64, 0);
    FloorGeometry geometry = new FloorGeometry(Set.of(
            new FloorGeometry.Cell(left, 64, 68),
            new FloorGeometry.Cell(right, 64, 68)), java.util.Map.of());

    FloorGeometry augmented = StructureConnector.withConnectorMarkers(
            geometry, List.of(new FloorConnector.Marker(connector, FloorConnector.Type.DOOR)));

    assertTrue(augmented.cellAt(connector).isEmpty());
    assertEquals(FloorConnector.Type.DOOR,
            augmented.connectorTypesByPosition().get(connector));
}
```

Keep the `connectsFloors(...)` tests because they validate vertical handoff geometry independently of marker materialization.

- [ ] **Step 7: Add the persistence RED before relaxing marker loading**

Extend the existing `StructureFloorTest.connectorMetadataDoesNotManufactureFloorRegionMembership()`:

```java
assertEquals(List.of(marker), loaded.connectors(),
        "physical connector marker was discarded because it was not a floor cell");
assertFalse(loaded.contains(connector.getX(), connector.getZ()),
        "connector metadata must not manufacture persisted Floor geometry");
```

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.StructureFloorTest' --rerun-tasks
```

Expected before the loader change: FAIL because `connectorMap(...)` currently filters the marker out.

- [ ] **Step 8: Make persisted marker loading retain non-cell connector positions**

`StructureFloor.connectorMap(...)` currently drops any marker whose position is not in the exact cell set. Replace it with:

```java
private static Map<BlockPos, FloorConnector.Type> connectorMap(CompoundTag tag) {
    Map<BlockPos, FloorConnector.Type> result = new LinkedHashMap<>();
    for (FloorConnector.Marker marker : loadMarkers(tag)) {
        result.put(marker.pos(), marker.type());
    }
    return Map.copyOf(result);
}
```

Change `load(...)` to call `connectorMap(tag)`. This changes no NBT key or payload shape; it only stops discarding already-serialized connector metadata.

- [ ] **Step 9: Re-run persistence GREEN**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.StructureFloorTest' --rerun-tasks
```

Expected: PASS; the marker round-trips while `loaded.contains(connectorX, connectorZ)` remains false.

- [ ] **Step 10: Remove connector-cell filtering from Room identity matching**

In `StructureExpansionPolicy.registeredRoomForFreshComponent(...)`, connector positions no longer appear in `selected.floorCells()`, so replace:

```java
Set<BlockPos> identityCells = floorCells.stream()
        .filter(cell -> {
            FloorConnector.Type connector = floor.connectorTypesByCell().get(cell);
            return connector == null || !connector.roomBoundary();
        })
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
```

with:

```java
Set<BlockPos> identityCells = selected.floorCells();
```

Keep the existing structure/floor/Room matching order unchanged.

- [ ] **Step 11: Run the existing Room identity regressions**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.StructureExpansionPolicyTest' --rerun-tasks
```

Expected: `freshComponentAcrossDoorBoundaryRemainsANewRoom()` and the registered-Room matching tests remain green after connector positions leave the floor-cell set.

- [ ] **Step 12: Add a real door GameTest**

Build two closed same-height rooms separated by a one-block door column. Scan through the open door and assert:

```java
helper.assertTrue(scan.floor().cellAt(doorPos).isEmpty(),
        "door manufactured a canonical floor cell");
List<RoomPartitioner.Component> rooms = RoomPartitioner.partition(scan.floor());
helper.assertTrue(rooms.size() == 2, "door did not remain a Room boundary");
```

Include both an interior door and an exterior-door variant; the exterior door must not add a protruding floor column.

- [ ] **Step 13: Run focused and runtime verification**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.FloorGeometryTest' --tests 'net.conczin.mca.server.world.data.RoomPartitionerTest' --tests 'net.conczin.mca.server.world.data.RoomPoiEvidenceTest' --tests 'net.conczin.mca.server.world.data.StructureConnectorTest' --tests 'net.conczin.mca.server.world.data.StructureFloorTest' --tests 'net.conczin.mca.server.world.data.StructureExpansionPolicyTest' --rerun-tasks
.\gradlew.bat :neoforge:runGameTestServer --rerun-tasks
```

Expected: exact cells exclude connector positions, Room gaps still partition, deterministic perimeter ownership remains green, GameTests pass.

- [ ] **Step 14: Commit Task 2**

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/FloorGeometry.java
git add common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java
git add common/src/main/java/net/conczin/mca/server/world/data/RoomPartitioner.java
git add common/src/main/java/net/conczin/mca/server/world/data/StructureFloor.java
git add common/src/main/java/net/conczin/mca/server/world/data/StructureExpansionPolicy.java
git add common/src/test/java/net/conczin/mca/server/world/data/FloorGeometryTest.java
git add common/src/test/java/net/conczin/mca/server/world/data/RoomPartitionerTest.java
git add common/src/test/java/net/conczin/mca/server/world/data/StructureConnectorTest.java
git add common/src/test/java/net/conczin/mca/server/world/data/StructureFloorTest.java
git add neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java
git commit -m "refactor: keep connectors outside floor geometry"
```

---

### Task 3: Introduce one physical horizontal-step primitive

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`

**Interfaces:**
- Produces nested/private `HorizontalStep(FloorGeometry.Cell landing, BlockPos connector)`.
- Produces `horizontalStep(...)` used by both selected-floor traversal and `reachesExterior(...)`.
- Preserves `LANDING_Y_OFFSETS = {0, 1, -1}` as a physical probe detail only.

- [ ] **Step 1: Add a slab/stair physical-surface GameTest before refactoring**

Add a room path containing a lower slab and a one-block stair/step. Assert the scan succeeds, retains exact cells, and at least one cell has a fractional `surfaceY`:

```java
helper.assertTrue(scan.floor().cells().stream()
                .anyMatch(cell -> cell.surfaceY() != Math.floor(cell.surfaceY())),
        "partial support height was flattened");
```

Also assert adjacent cells connect only when `FloorGeometry.canStep(...)` allows the exact surface delta.

- [ ] **Step 2: Extract a single horizontal transition resolver**

Add this nested record and helper to `SelectedFloorScanner`:

```java
private record HorizontalStep(FloorGeometry.Cell landing, BlockPos connector) {
}

private static Optional<HorizontalStep> horizontalStep(
        Level world,
        FloorGeometry.Cell current,
        Direction direction,
        FloorCeilingResolver ceilings,
        BlockPos blockedBoundary,
        boolean crossRoomBoundary) {
    // Probe the adjacent column with LANDING_Y_OFFSETS.
    // If a connector occupies the adjacent column, normalize it.
    // Horizontal room boundary: either block it, or resolve the far-side landing.
    // Non-boundary connector: report marker metadata but do not fabricate a landing.
    // No connector: return findLanding(...) in the adjacent column.
}
```

The method must be the only place that interprets horizontal connector columns during physical movement discovery.

- [ ] **Step 3: Make ordinary scan traversal use `horizontalStep(...)`**

Replace the body of `enqueueHorizontalLanding(...)` with the shared resolver. Keep radius/visited logic outside the resolver. When `step.connector() != null`, add the physical connector position to the connector marker set.

- [ ] **Step 4: Make exterior probing use the same resolver**

Replace the independent `LANDING_Y_OFFSETS`/`sameSemanticBand` loop inside `reachesExterior(...)` with `horizontalStep(..., crossRoomBoundary = false)`.

At this stage keep existing semantic-storey gating around the returned landing; Task 4 replaces that policy. The physical landing result itself must now be identical between scan and exterior code.

- [ ] **Step 5: Verify no behavior drift**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.SelectedFloorScannerTest' --rerun-tasks
.\gradlew.bat :neoforge:runGameTestServer --rerun-tasks
```

- [ ] **Step 6: Commit Task 3**

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
git add neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java
git commit -m "refactor: share floor horizontal step discovery"
```

---

### Task 4: Replace semantic-band reconstruction with direct selected-storey traversal

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`
- Rewrite: `common/src/test/java/net/conczin/mca/server/world/data/SelectedFloorScannerTest.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`

**Interfaces:**
- Produces nested `StoreyRole { OWNED, EDGE, OTHER }`.
- Produces nested `StoreyScan(FloorGeometry floor, Set<BlockPos> alternateSeeds, Set<BlockPos> connectors)`.
- Produces `scanStorey(...)` that returns one selected Floor directly from traversal.
- Removes `FloorSelection`, `SemanticBands`, `HeightBand`, and all equal-Y slice helpers.

#### Local policy for the first implementation

The selected-storey rule is intentionally conservative and local:

1. Resolve the seed to an exact physical cell.
2. Determine the local anchor by following descending step-compatible cells only while the current cell has no same-height walkable peer. This makes a click on a sparse stair transition resolve toward the lower storey, while a click in a real upper-room plateau remains anchored to the upper storey.
3. Cells within `StructureFloor.BAND_TOLERANCE` above that anchor are ordinary `OWNED` cells.
4. One immediately step-compatible cell exactly one block above that semantic range is an `EDGE`: retain it as exact lower-storey transition geometry but do not expand selected-storey traversal from it.
5. An `EDGE` cell never grows selected geometry. Probe its cardinal next landings only to collect alternate-storey seeds; those next landings are not added to the selected Floor.
6. Cells encountered beyond the selected boundary are `OTHER` and become alternate-storey seed evidence, never selected geometry.
7. From an anchored same-height plateau, a candidate at the anchor height that has a descending step neighbor toward a lower transition is treated as `OTHER` when entering it from the plateau. This prevents an upper-room scan from reclaiming the lower-owned top stair.
8. Any shape not decidable under these local rules remains on/stops at the selected storey; do not add a size/area threshold to guess.

This preserves the current three-block-storey staircase behavior while removing the global “four cells make a meaningful Y slice” rule. Uneven floors spanning Y, Y+1, and Y+2 stay one storey through ordinary exact step edges.

- [ ] **Step 1: Replace band-oriented unit tests with graph-oriented contracts**

Keep `stepDecisionUsesVanillaJumpThreshold()` and add/rename tests so they describe behavior rather than bands:

```java
@Test
void lowerStoreyOwnsSparseUpwardStairTransitionButNotUpperRoom() { ... }

@Test
void upperRoomDoesNotReclaimLowerOwnedTopStair() { ... }

@Test
void stackedSameColumnCellsSurviveSelectedStoreyTraversal() { ... }

@Test
void unevenExactCellsAcrossThreeHeightsRemainOneSelectedStorey() { ... }

@Test
void selectedStoreyDoesNotDependOnIterationOrder() { ... }
```

Delete tests that call `floorSelection(...)` or inspect “bands”; the new tests must call a package-visible pure helper that exercises the same transition policy used by `scanStorey(...)`.

- [ ] **Step 2: Add pure nested policy data rather than a new top-level class**

Inside `SelectedFloorScanner` add:

```java
enum StoreyRole { OWNED, EDGE, OTHER }

record StoreyContext(int anchorY) {
}

record TransitionEvidence(boolean currentHasSameHeightPeer,
                          boolean candidateHasDescendingStep) {
}

static StoreyRole storeyRole(StoreyContext context,
                             FloorGeometry.Cell current,
                             FloorGeometry.Cell candidate,
                             TransitionEvidence evidence) {
    int minY = context.anchorY();
    int maxOwnedY = minY + StructureFloor.BAND_TOLERANCE;
    int y = candidate.feet().getY();

    if (y < minY || y > maxOwnedY + 1) return StoreyRole.OTHER;
    if (y == maxOwnedY + 1) return StoreyRole.EDGE;
    if (y == minY
            && current.feet().getY() == minY
            && evidence.currentHasSameHeightPeer()
            && evidence.candidateHasDescendingStep()) {
        return StoreyRole.OTHER;
    }
    return StoreyRole.OWNED;
}
```

The world-facing code computes `TransitionEvidence` from exact `horizontalStep(...)` probes; the pure method contains no `Level` dependency.

- [ ] **Step 3: Add local anchor resolution**

Add a private world helper:

```java
private static FloorGeometry.Cell resolveStoreyAnchor(
        Level world, FloorGeometry.Cell seed, FloorCeilingResolver ceilings) {
    FloorGeometry.Cell current = seed;
    Set<BlockPos> seen = new HashSet<>();
    while (seen.add(current.feet()) && !hasSameHeightPeer(world, current, ceilings)) {
        FloorGeometry.Cell lower = lowestDescendingNeighbor(world, current, ceilings);
        if (lower == null) break;
        current = lower;
    }
    return current;
}
```

`lowestDescendingNeighbor(...)` may choose only a cardinal, step-compatible exact landing with a lower `surfaceY`; tie-break by feet Y, X, then Z for deterministic behavior.

- [ ] **Step 4: Implement selected-storey BFS directly**

Add:

```java
private static StoreyScan scanStorey(
        Level world,
        FloorGeometry.Cell requestedSeed,
        int maxSize,
        int maxRadius,
        FloorCeilingResolver ceilings) {
    FloorGeometry.Cell anchor = resolveStoreyAnchor(world, requestedSeed, ceilings);
    StoreyContext context = new StoreyContext(anchor.feet().getY());
    // BFS only OWNED cells.
    // EDGE cells are retained but never enqueued.
    // OTHER cells are not retained; add their feet positions to alternateSeeds.
    // Connector positions are metadata only.
    // maxSize applies to selected exact cells + connector markers, not alternate storeys.
}
```

Use a `LinkedHashMap<BlockPos, FloorGeometry.Cell>` for selected cells, `LinkedHashSet<BlockPos>` for alternate seeds/connectors, and deterministic direction/order already used by the scanner.

When a candidate is `EDGE`, add it to selected cells but do not enqueue it. Immediately inspect the EDGE cell's four cardinal `horizontalStep(...)` results and add each valid landing not already selected to `alternateSeeds`; do not recursively traverse those landings in the selected scan. This is how a lower-floor stair landing exposes the upper-floor room as attachment evidence without merging it into the lower `FloorGeometry`.

- [ ] **Step 5: Make `scan(...)` return the direct selected storey**

Replace broad-discovery + `floorSelection(...)` with:

```java
StoreyScan selected = scanStorey(world, seedCell, maxSize, maxRadius, ceilings);
FloorGeometry floor = StructureConnector.withConnectorMarkers(
        selected.floor(), StructureConnector.markers(world, selected.connectors()));
List<FloorGeometry> connectedFloors = connectedStoreyEvidence(
        world, floor, selected.alternateSeeds(), maxSize, maxRadius, ceilings);
return success(seed, floor, connectedFloors);
```

Do not delete the old semantic helpers until the new tests are green in this step.

- [ ] **Step 6: Derive `connectedFloors` from alternate-storey boundary seeds**

Add:

```java
private static List<FloorGeometry> connectedStoreyEvidence(
        Level world,
        FloorGeometry selected,
        Collection<BlockPos> alternateSeeds,
        int maxSize,
        int maxRadius,
        FloorCeilingResolver ceilings) {
    List<FloorGeometry> result = new ArrayList<>();
    result.add(selected);
    for (BlockPos seed : alternateSeeds) {
        FloorGeometry.Cell cell = inspectSurfaceCell(world, seed, ceilings).orElse(null);
        if (cell == null) continue;
        FloorGeometry alternate = scanStorey(world, cell, maxSize, maxRadius, ceilings).floor();
        if (alternate.cells().isEmpty()) continue;
        boolean duplicateSameStorey = result.stream().anyMatch(existing ->
                StructureFloor.sameSemanticBand(existing.anchorY(), alternate.anchorY())
                        && existing.sameFootprint(alternate));
        if (!duplicateSameStorey) {
            result.add(alternate);
        }
    }
    return List.copyOf(result);
}
```

This is deliberately one-hop evidence: do not recursively enumerate an entire building. `StructureExpansionPolicy` only needs the directly connected alternative storey evidence to prove stair attachment. Never deduplicate by footprint alone because vertically stacked Floors are allowed to have identical X/Z projections.

- [ ] **Step 7: Add a real ordinary-stair selected-storey GameTest**

Build a lower room at Y=88, a walkable stair chain through Y=89/Y=90 with a sparse top transition at Y=91, and a broad upper room at Y=91. No ladder/trapdoor is used.

Assert:

```java
SelectedFloorScanner.Result lower = SelectedFloorScanner.scan(world, lowerSeed, 256, 32);
SelectedFloorScanner.Result upper = SelectedFloorScanner.scan(world, upperSeed, 256, 32);

helper.assertTrue(lower.floor().anchorY() == 88, "lower scan selected wrong storey");
helper.assertTrue(upper.floor().anchorY() == 91, "upper scan selected wrong storey");
helper.assertTrue(lower.floor().cellAt(topTransition).isPresent(),
        "sparse top stair was not retained by lower storey");
helper.assertTrue(upper.floor().cellAt(topTransition).isEmpty(),
        "upper storey reclaimed lower-owned stair transition");
helper.assertTrue(lower.connectedFloors().stream().anyMatch(floor -> floor.anchorY() == 91),
        "lower scan lost directly connected upper-storey evidence");
```

- [ ] **Step 8: Run pure scanner tests, the stair GameTest, and downstream attachment test**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.SelectedFloorScannerTest' --tests 'net.conczin.mca.server.world.data.VillageFloorSystemTest' --rerun-tasks
.\gradlew.bat :neoforge:runGameTestServer --rerun-tasks
```

Expected: selected-storey tests pass and `walkableStoreyEvidenceCanProveStairFloorAttachment()` stays green.

- [ ] **Step 9: Delete the superseded reconstruction model**

Delete from `SelectedFloorScanner`:

```text
MIN_MEANINGFUL_HEIGHT_SLICE_AREA
floorSelection(...)
semanticBands(...)
sliceComponentsByHeight(...)
cellsByColumn(...) used only by semantic reconstruction
sliceComponents(...)
adjacentWalkableCells(...)
cellsByHeight(...)
heightBands(...)
selectHeightBand(...)
meaningfulHeightSlice(...)
HeightBand
SemanticBands
FloorSelection
```

Then remove imports made dead by that deletion (`Comparator`, `TreeMap`, etc.) only when no longer used elsewhere.

- [ ] **Step 10: Run `git diff --check` and focused tests again**

```powershell
git diff --check
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.SelectedFloorScannerTest' --tests 'net.conczin.mca.server.world.data.VillageFloorSystemTest' --rerun-tasks
```

- [ ] **Step 11: Commit Task 4**

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
git add common/src/test/java/net/conczin/mca/server/world/data/SelectedFloorScannerTest.java
git add neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java
git commit -m "refactor: select floor storeys during exact traversal"
```

---

### Task 5: Make exterior and vertical-connector discovery use selected exact-cell semantics

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`

**Interfaces:**
- `reachesExterior(...)` consumes the same `StoreyContext`/`storeyRole(...)` decision as `scanStorey(...)`.
- `collectVerticalConnectors(...)` consumes an exact `FloorGeometry.Cell`, not `seedY`.

- [ ] **Step 1: Add an exterior-near-stair regression**

Create a closed lower storey linked by stairs to an upper area where only the upper storey reaches exterior. Scanning the closed lower Floor must remain valid; exterior probing must not climb through `OTHER`/alternate-storey cells and reject the lower Floor.

- [ ] **Step 2: Remove `seedY`/`sameSemanticBand` from exterior physical traversal**

Change `reachesExterior(...)` to accept `StoreyContext` and classify every returned `HorizontalStep.landing()` with the same `storeyRole(...)` used by `scanStorey(...)`:

```java
StoreyRole role = storeyRole(context, currentCell, nextCell,
        transitionEvidence(world, currentCell, nextCell, ceilings));
if (role != StoreyRole.OWNED) continue;
```

Exterior search must not traverse `EDGE` or `OTHER` cells. It still returns exterior when an owned landing has no physical ceiling or reaches the radius boundary.

- [ ] **Step 3: Collect vertical connectors from exact cell intervals**

Replace:

```java
collectVerticalConnectors(world, floorSeed.getY(), current.feet(), connectors);
```

with:

```java
collectVerticalConnectors(world, current, connectors);
```

and implement:

```java
private static void collectVerticalConnectors(
        Level world, FloorGeometry.Cell cell, Set<BlockPos> connectors) {
    for (int y = cell.feet().getY() - 1; y < cell.ceilingY(); y++) {
        collectVerticalConnectorAt(world,
                new BlockPos(cell.feet().getX(), y, cell.feet().getZ()), connectors);
        for (Direction direction : HORIZONTAL) {
            collectVerticalConnectorAt(world,
                    new BlockPos(cell.feet().getX() + direction.getStepX(), y,
                            cell.feet().getZ() + direction.getStepZ()), connectors);
        }
    }
}
```

`collectVerticalConnectorAt(...)` uses `StructureConnector.verticalInteractionConnector(...)` / `isVertical(...)`; it does not consult a semantic seed-Y band.

- [ ] **Step 4: Add ladder/trapdoor GameTest**

Build two exact storeys linked by a ladder column (with a trapdoor only when it belongs to that contiguous ladder column). Assert connector markers are discovered, `StructureConnector.connectsFloors(...)` can prove the relationship, and `RoomPartitioner.partition(...)` does not merge the two storeys/Rooms.

- [ ] **Step 5: Verify runtime and focused tests**

```powershell
.\gradlew.bat :neoforge:runGameTestServer --rerun-tasks
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.SelectedFloorScannerTest' --tests 'net.conczin.mca.server.world.data.RoomPartitionerTest' --rerun-tasks
```

- [ ] **Step 6: Commit Task 5**

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
git add common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java
git add neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java
git commit -m "refactor: share selected-storey exterior semantics"
```

---

### Task 6: Lock cave, POI/wall, projection, and persistence boundaries

**Files:**
- Modify: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`
- Test: `common/src/test/java/net/conczin/mca/server/world/data/RoomPoiEvidenceTest.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/FloorGeometryTest.java`
- Test: `common/src/test/java/net/conczin/mca/server/world/data/StructureFloorTest.java`

**Interfaces:**
- No new production subsystem.
- Confirms derived projection and persistence do not influence selected-storey topology.

- [ ] **Step 1: Add uneven cave GameTest**

Build a roofed cave-like Room whose exact floor cells occupy three feet heights (for example Y=64,65,66) connected only by valid step deltas, with no four-cell same-height requirement. Assert one selected Floor and one Room component contain all expected exact cells.

- [ ] **Step 2: Keep the existing deterministic wall-POI unit test green**

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.RoomPoiEvidenceTest' --rerun-tasks
```

`sharedPerimeterColumnBelongsToOneDeterministicRoom()` already proves that one shared wall candidate belongs to one Room only. Do not rewrite `RoomPoiEvidence` unless this test fails after connector changes.

- [ ] **Step 3: Add a wall-POI runtime assertion**

In a two-Room GameTest, place a configured/representative POI block in the shared wall/perimeter column. Use `RoomPoiEvidence.candidates(...)` on the resulting components and assert exactly one component receives that wall position while no `FloorGeometry.Cell` exists at the wall coordinate.

- [ ] **Step 4: Verify projection remains derived**

Extend a pure geometry test to construct stacked same-X/Z cells and assert:

```java
assertEquals(2, geometry.cellsAtColumn(x, z).size());
assertEquals(1, geometry.projection().area());
```

Then prove selected-storey behavior in `SelectedFloorScannerTest` uses the exact cells, not `projection()`.

- [ ] **Step 5: Re-verify exact save/load with connector markers outside cells**

Task 2 already extends `StructureFloorTest.connectorMetadataDoesNotManufactureFloorRegionMembership()` to require marker retention without connector cell creation. Re-run that exact contract and keep this representative object shape in mind when reviewing failures:

```java
StructureFloor original = new StructureFloor(3, 0,
        new FloorGeometry(Set.of(cell(0, 64, 0), cell(2, 64, 0)),
                Map.of(new BlockPos(1, 64, 0), FloorConnector.Type.DOOR)));
StructureFloor loaded = StructureFloor.load(original.save());
assertEquals(original.geometry().cells(), loaded.geometry().cells());
assertEquals(original.connectors(), loaded.connectors());
assertTrue(loaded.geometry().cellAt(new BlockPos(1, 64, 0)).isEmpty());
```

Do not make further persistence production edits in this task. The expected result is already fixed by Task 2: connector markers round-trip, connector cells do not appear, NBT field names remain unchanged, and no new migration version is introduced.

- [ ] **Step 6: Run cave/GameTests + persistence/POI tests**

```powershell
.\gradlew.bat :neoforge:runGameTestServer --rerun-tasks
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.RoomPoiEvidenceTest' --tests 'net.conczin.mca.server.world.data.FloorGeometryTest' --tests 'net.conczin.mca.server.world.data.StructureFloorTest' --rerun-tasks
```

- [ ] **Step 7: Commit Task 6**

Stage the GameTest and exact test files changed by this task, then:

```powershell
git add neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java
git add common/src/test/java/net/conczin/mca/server/world/data/FloorGeometryTest.java
git add common/src/test/java/net/conczin/mca/server/world/data/RoomPoiEvidenceTest.java
git commit -m "test: lock exact floor scanner boundaries"
```

---

### Task 7: Remove stale APIs and run the full acceptance gate

**Files:**
- Review/cleanup: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`
- Review/cleanup: `common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java`
- Review/cleanup: `common/src/main/java/net/conczin/mca/server/world/data/FloorGeometry.java`
- Review/cleanup: `common/src/main/java/net/conczin/mca/server/world/data/RoomPartitioner.java`
- Review tests under `common/src/test/java/net/conczin/mca/server/world/data/`
- Review `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`

**Interfaces:**
- Final public/package contracts should be exact-cell geometry + connector metadata + selected-storey scan only.
- No semantic-band reconstruction API survives.

- [ ] **Step 1: Search for stale reconstruction/materialization symbols**

Run:

```powershell
rg -n "MIN_MEANINGFUL_HEIGHT_SLICE_AREA|SemanticBands|HeightBand|floorSelection\(|semanticBands\(|heightBands\(|meaningfulHeightSlice|sliceComponentsByHeight|connectorBoundaryCell|withConnectorAssociations|floorMembershipCells|connectorTypesByCell" common/src/main/java common/src/test/java
```

Expected: no remaining semantic-band/materialized-connector symbols. If `connectorTypesByCell` remains only in historical docs, do not change unrelated docs in this task.

- [ ] **Step 2: Search for duplicate physical traversal policy**

```powershell
rg -n "LANDING_Y_OFFSETS|sameSemanticBand\(" common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
```

Expected:

- `LANDING_Y_OFFSETS` is used only by the shared physical landing resolver / helpers.
- `sameSemanticBand(...)` is not independently used by `reachesExterior(...)` or vertical-connector discovery.

- [ ] **Step 3: Run the complete common world-data test lane from scratch**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.*' --rerun-tasks
```

Expected: PASS.

- [ ] **Step 4: Run cross-loader compile verification**

```powershell
.\gradlew.bat :fabric:compileJava :neoforge:compileJava --rerun-tasks
```

Expected: PASS.

- [ ] **Step 5: Run the complete NeoForge GameTest server**

```powershell
.\gradlew.bat :neoforge:runGameTestServer --rerun-tasks
```

Expected: all required GameTests pass, including:

```text
bed footprint unchanged
bed-top seed resolves underlying floor
carpet/low occupancy preserves footprint
full solid block is not topology-neutral
partial-height support preserves exact surfaceY
lower/upper staircase storeys remain distinct
door positions do not become floor cells
uneven cave spans multiple exact Y values
vertical connector attaches Floors without merging Rooms
```

- [ ] **Step 6: Verify persistence/attachment compatibility specifically**

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.VillageFloorSystemTest' --tests 'net.conczin.mca.server.world.data.RoomDFUTest' --rerun-tasks
```

Expected: `walkableStoreyEvidenceCanProveStairFloorAttachment()` and all DFU/exact persistence tests pass without save-format changes.

- [ ] **Step 7: Verify diff hygiene and exact checkout state**

```powershell
git diff --check
git status --short --branch
git diff --stat HEAD~6..HEAD
```

Confirm no unrelated files were staged/committed and no untracked user work was deleted.

- [ ] **Step 8: Commit final cleanup only if Step 1 required edits**

If stale-symbol cleanup changed code/tests:

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
git add common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java
git add common/src/main/java/net/conczin/mca/server/world/data/FloorGeometry.java
git add common/src/main/java/net/conczin/mca/server/world/data/RoomPartitioner.java
git add common/src/test/java/net/conczin/mca/server/world/data/SelectedFloorScannerTest.java
git add common/src/test/java/net/conczin/mca/server/world/data/StructureConnectorTest.java
git add common/src/test/java/net/conczin/mca/server/world/data/FloorGeometryTest.java
git add common/src/test/java/net/conczin/mca/server/world/data/RoomPartitionerTest.java
git commit -m "refactor: finish exact floor scanner cleanup"
```

If Step 1 required no edits, do not create an empty commit.

---

## Acceptance Coverage Matrix

| Spec requirement | Plan gate |
| --- | --- |
| Exact 3D cells are canonical | Tasks 2, 4, 6 |
| Stacked same-X/Z cells survive | Tasks 4, 6 |
| Fractional support height preserved | Tasks 1, 3 |
| Beds/furniture do not mutate structural footprint | Task 1 |
| Full blocks are not topology-neutral | Task 1 |
| Lower/upper stairs stay distinct | Task 4 + Task 7 GameTests |
| Sparse top stair may remain lower-owned | Task 4 |
| Uneven cave spans multiple Y without slice thresholds | Tasks 4, 6 |
| Doors/gates do not manufacture geometry | Task 2 |
| Room partitioning is exact connected components | Task 2 |
| Wall POIs remain metadata with one owner | Task 6 / existing `RoomPoiEvidenceTest` |
| Ladder/trapdoor attachments do not merge Rooms | Task 5 |
| Exterior probing uses same selected-storey semantics | Task 5 |
| `connectedFloors` stair attachment evidence preserved | Task 4 + `VillageFloorSystemTest` |
| Projection remains derived | Task 6 |
| Persistence format/DFU unchanged | Tasks 6, 7 |
| Semantic-band/slice-area reconstruction removed | Tasks 4, 7 |

## Expected End State

```text
Minecraft Level
    |
    v
physical support + occupancy + horizontalStep
    |
    v
selected-storey exact traversal
    |              \
    |               -> alternate storey seeds -> one-hop connectedFloors evidence
    v
FloorGeometry(exact cells + connector markers)
    |
    v
RoomPartitioner(exact connected components)
    |
    +-> RoomPoiEvidence(perimeter/wall/connector evidence)
    |
    v
Rooms / persistence / Blueprint projection
```

There should be no post-hoc Y-slice semantic reconstruction between world discovery and `FloorGeometry`.
