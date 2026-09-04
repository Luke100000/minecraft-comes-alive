# Floor / Room 3D Geometry Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the one-cell-per-X/Z Floor model with one exact 3D Floor geometry source of truth, make Rooms own exact Floor cell keys, and migrate only released `origin/1.21.1` plus the upstream unversioned `origin/feature/1.21.1-floor-clean-squash` format into the new canonical save format.

**Architecture:** Physical scan output becomes `FloorGeometry`, keyed by full `BlockPos` and permitting multiple cells in one X/Z column. `StructureFloor` owns one `FloorGeometry`; Room partitioning returns exact-cell components and persisted Rooms store only the exact cell keys they own. `BuildingFloorRegion` survives only as a derived 2D projection. `RoomDFU` is the sole loading boundary: it migrates released origin and upstream unversioned floor-clean-squash, and directly loads the new canonical format.

**Tech Stack:** Java 21, Minecraft/NeoForge 1.21.1 common code, JUnit 5, Gradle, Minecraft NBT (`CompoundTag`, `ListTag`)

**Spec:** `docs/superpowers/specs/2026-09-04-floor-room-3d-geometry-architecture-spec.md`

## Global Constraints

- Do not reset, discard, or overwrite the existing uncommitted floor-system work in the current tree; implement from it.
- A physical Floor cell is identified by full `BlockPos`; multiple Y values in one X/Z column are valid.
- Do not special-case `StairBlock`, the test-world coordinates, or Y=88/Y=91.
- `FloorGeometry` is the only authoritative Floor spatial model; all X/Z regions, bounds, areas, and outlines are derived views.
- `BuildingFloorRegion` must not remain persisted canonical Floor or Room geometry.
- `RoomScanPlan` remains action/identity only and must not cache reusable scan geometry.
- Room identity/Main Room policy stays separate from topology.
- Non-top semantic ceiling is derived from the next semantic Floor anchor; top semantic ceiling comes from physical ceiling data.
- Floor refresh and Room reconciliation are published atomically.
- Canonical persistence starts at `buildingDataVersion = 1`.
- `RoomDFU` supports migration only from released `origin/1.21.1` and the upstream unversioned `origin/feature/1.21.1-floor-clean-squash` save shape.
- Historical field interpretation (`floorRegions`, old inheritance fields, legacy BlockPos compounds, old Structure main-room fields) belongs only in `RoomDFU` and migration tests.
- Keep the repository compilable after each task; delete transitional wrappers only after their callers have moved.

---

## File Structure

### New production files

- `common/src/main/java/net/conczin/mca/server/world/data/FloorGeometry.java`
  - Exact physical cells, connector ownership, multi-valued X/Z index, derived projection/bounds.
- `common/src/main/java/net/conczin/mca/server/world/data/RoomPartitioner.java`
  - Pure exact-cell Room topology over one `FloorGeometry`.

### Production files that change responsibility

- `SelectedFloorScanner.java`
  - Discovers exact cells and assigns each cell to one semantic Floor; outputs `FloorGeometry` values.
- `StructureConnector.java`
  - Associates connectors with exact Floor cells and resolves exact handoffs.
- `StructureFloor.java`
  - Stable Floor identity around `FloorGeometry`; no persisted 2D region or ceiling-boundary exception.
- `Structure.java`
  - Owns Floors, derives semantic ceilings, bounds, exact physical position resolution.
- `StructureScanner.java`
  - Converts semantic scan results into Structure/Floor identities without creating a second geometry DTO.
- `BuildingRoomScanner.java`
  - Materializes Room scan results from exact `RoomPartitioner.Component` cell keys.
- `RoomPoiEvidence.java`
  - Derives POI candidate intervals from exact Room-owned cells.
- `Building.java`
  - Persists exact `floorCells`; derives 2D footprint/bounds instead of persisting `floorRegion`.
- `StructureExpansionPolicy.java`
  - Reduced to identity matching, or removed if the matcher becomes a direct Structure/Village operation.
- `RoomWorkflow.java`, `Village.java`, `VillageManager.java`
  - Exact-cell resolution and atomic StructureFloor + Room publication.
- `RoomDFU.java`
  - Sole current loader and sole migration boundary.

### Tests to create/rename/expand

- Create `FloorGeometryTest.java`.
- Replace `FloorSurfacePartitionerTest.java` with `RoomPartitionerTest.java`.
- Expand `SelectedFloorScannerTest.java`, `StructureFloorTest.java`, `StructureFloorResolutionTest.java`, `BuildingRoomScannerOwnerTest.java`, `RoomPoiEvidenceTest.java`, `RoomDFUTest.java`, `VillageFloorSystemTest.java`, `VillageManagerExpandedRoomCommitTest.java`.
- Remove `ScannedFloorTest.java` after `ScannedFloor` is deleted.

---

### Task 1: Introduce exact multi-height `FloorGeometry`

**Files:**
- Create: `common/src/main/java/net/conczin/mca/server/world/data/FloorGeometry.java`
- Create: `common/src/test/java/net/conczin/mca/server/world/data/FloorGeometryTest.java`
- Reference only for behavior parity: `common/src/main/java/net/conczin/mca/server/world/data/FloorSurface.java`

**Interfaces:**
- Consumes: `StructureFloor.ConnectorType`, `BuildingFloorRegion.fromFootprint(...)`.
- Produces:
  - `FloorGeometry(Collection<Cell> cells, Map<BlockPos, StructureFloor.ConnectorType> connectorTypesByCell)`
  - `Set<Cell> cells()`
  - `Map<BlockPos, StructureFloor.ConnectorType> connectorTypesByCell()`
  - `List<Cell> cellsAtColumn(int x, int z)` sorted by `feet().getY()`
  - `Optional<Cell> cellAt(BlockPos feet)`
  - `int anchorY()`
  - `int maxPhysicalCeilingY()`
  - `BuildingFloorRegion projection()`
  - `FloorGeometry withConnectorTypes(Map<BlockPos, StructureFloor.ConnectorType>)`
  - `List<StructureFloor.ConnectorMarker> connectorMarkers()` derived from exact connector-cell keys
  - `static boolean canStep(double fromSurfaceY, double toSurfaceY)`
  - `static long columnKey(int x, int z)` for derived indexes only
  - `record Cell(BlockPos feet, double surfaceY, int ceilingY)`

- [ ] **Step 1: Write failing exact-geometry tests**

Create `FloorGeometryTest` with the same anchor/projection expectations as `FloorSurfaceTest`, but make the formerly-invalid stacked column explicitly valid:

```java
@Test
void sameColumnCellsAtDifferentHeightsAreBothCanonical() {
    FloorGeometry.Cell lower = cell(2, 88, 3);
    FloorGeometry.Cell upper = cell(2, 91, 3);

    FloorGeometry geometry = new FloorGeometry(Set.of(lower, upper), Map.of());

    assertEquals(List.of(lower, upper), geometry.cellsAtColumn(2, 3));
    assertEquals(Set.of(lower, upper), geometry.cells());
    assertEquals(1, geometry.projection().area());
}

@Test
void cellLookupUsesFullBlockPosition() {
    FloorGeometry.Cell lower = cell(2, 88, 3);
    FloorGeometry.Cell upper = cell(2, 91, 3);
    FloorGeometry geometry = new FloorGeometry(Set.of(lower, upper), Map.of());

    assertEquals(lower, geometry.cellAt(lower.feet()).orElseThrow());
    assertEquals(upper, geometry.cellAt(upper.feet()).orElseThrow());
}
```

Also port tests for dominant-anchor tie-breaking, connector-cell inclusion, and deterministic column ordering.
Add one invariant test proving a connector key must reference an existing exact
Floor cell; `FloorGeometry` must not manufacture synthetic cells from connector
metadata.

- [ ] **Step 2: Run the new test to verify RED**

Run:

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.server.world.data.FloorGeometryTest --no-daemon --console=plain
```

Expected: compilation failure because `FloorGeometry` does not exist.

- [ ] **Step 3: Implement `FloorGeometry` without a single-cell column API**

Use full-position identity and a derived multi-valued column index:

```java
final class FloorGeometry {
    static final double MAX_STEP_HEIGHT = 1.125D;

    private final Set<Cell> cells;
    private final Map<BlockPos, StructureFloor.ConnectorType> connectorTypesByCell;
    private final Map<Long, List<Cell>> cellsByColumn;
    private final Map<BlockPos, Cell> cellsByPosition;

    FloorGeometry(Collection<Cell> cells,
                  Map<BlockPos, StructureFloor.ConnectorType> connectorTypesByCell) {
        LinkedHashMap<BlockPos, Cell> positions = new LinkedHashMap<>();
        for (Cell cell : cells) positions.putIfAbsent(cell.feet(), cell);
        this.cellsByPosition = Map.copyOf(positions);
        this.cells = Set.copyOf(positions.values());
        if (!this.cellsByPosition.keySet().containsAll(connectorTypesByCell.keySet())) {
            throw new IllegalArgumentException("Connector cell is not part of FloorGeometry");
        }
        this.connectorTypesByCell = Map.copyOf(connectorTypesByCell);
        this.cellsByColumn = indexColumns(this.cells);
    }

    List<Cell> cellsAtColumn(int x, int z) {
        return cellsByColumn.getOrDefault(columnKey(x, z), List.of());
    }

    Optional<Cell> cellAt(BlockPos feet) {
        return Optional.ofNullable(cellsByPosition.get(feet));
    }
}
```

`indexColumns` must preserve every distinct full-position cell and sort each list by Y. Do not add `cellAtColumn(x,z)` returning one arbitrary cell.

- [ ] **Step 4: Run `FloorGeometryTest` GREEN**

Run the Task 1 test command again. Expected: PASS.

- [ ] **Step 5: Commit the isolated geometry primitive**

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/FloorGeometry.java common/src/test/java/net/conczin/mca/server/world/data/FloorGeometryTest.java
git commit -m "refactor: add exact floor geometry"
```

---

### Task 2: Port semantic scanning to exact geometry and reproduce the real stacked-column world

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/ScannedFloor.java` (temporary wrapper only in this task)
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/SelectedFloorScannerTest.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/StructureConnectorTest.java`

**Interfaces:**
- Consumes: Task 1 `FloorGeometry`.
- Produces during this transitional task:
  - `ScannedFloor(FloorGeometry geometry, int semanticCeilingY)` instead of wrapping `FloorSurface`.
  - `SelectedFloorScanner.FloorSelection(ScannedFloor selected, List<ScannedFloor> connected)` with exact geometry preserved.
  - `StructureConnector.associatedFloorCells(..., FloorGeometry geometry)` returning connector ownership keyed by exact Floor-cell `BlockPos`.
- `ScannedFloor` is deliberately temporary and is deleted in Task 4.

- [ ] **Step 1: Add the missing real-world regression before changing scanner code**

Add a test where the lower semantic Floor contains both Y88 and Y91 at the same X/Z:

```java
@Test
void stackedTopStairColumnStaysOnLowerFloorWithoutLosingUpperRoom() {
    BlockPos stackedColumn = new BlockPos(6, 91, 0);
    BlockPos upperRoom = new BlockPos(8, 91, 0);
    Set<FloorGeometry.Cell> cells = Set.of(
            cell(0, 88, 0), cell(1, 88, 0), cell(2, 88, 0), cell(3, 88, 0),
            cell(6, 88, 0),
            cell(4, 89, 0),
            cell(5, 90, 0),
            cell(6, 91, 0),
            cell(8, 91, 0), cell(9, 91, 0), cell(10, 91, 0), cell(11, 91, 0));

    ScannedFloor lower = SelectedFloorScanner.floorSelection(cells, stackedColumn).selected();
    ScannedFloor upper = SelectedFloorScanner.floorSelection(cells, upperRoom).selected();

    assertEquals(List.of(88, 91), lower.geometry().cellsAtColumn(6, 0).stream()
            .map(cell -> cell.feet().getY()).toList());
    assertEquals(88, lower.geometry().anchorY());
    assertEquals(91, lower.semanticCeilingY());
    assertTrue(upper.geometry().cellAt(upperRoom).isPresent());
    assertTrue(upper.geometry().cellAt(stackedColumn).isEmpty());
}
```

The helper must create `FloorGeometry.Cell`, not `FloorSurface.Cell`.

- [ ] **Step 2: Run the scanner regression and verify it is RED**

Run:

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.server.world.data.SelectedFloorScannerTest --no-daemon --console=plain
```

Expected: compilation/runtime failure until the scanner uses `FloorGeometry` and no longer reconstructs a single-valued `FloorSurface`.

- [ ] **Step 3: Port scanner discovery and semantic ownership to `FloorGeometry.Cell`**

Replace all `FloorSurface.Cell` references in `SelectedFloorScanner` with `FloorGeometry.Cell`. Keep the existing component-aware semantic owner map keyed by full `feet` positions:

```java
Map<BlockPos, HeightBand> owners = new LinkedHashMap<>();
Map<Long, List<FloorGeometry.Cell>> byColumn = cellsByColumn(discovered);
```

When one semantic band is materialized, construct:

```java
FloorGeometry geometry = new FloorGeometry(selectedCells, Map.of());
```

Never collapse `selectedCells` by X/Z.

- [ ] **Step 4: Make connector association exact-cell aware**

Change connector membership so each handoff compares the full candidate Y against every cell in that X/Z column:

```java
for (BlockPos handoff : handoffs(connector)) {
    geometry.cellsAtColumn(handoff.getX(), handoff.getZ()).stream()
            .filter(cell -> cell.feet().getY() == handoff.getY())
            .forEach(cell -> cells.add(new BlockPos(
                    connector.getX(), cell.feet().getY(), connector.getZ())));
}
```

Do not choose first/lowest/highest before matching the handoff height.

- [ ] **Step 5: Update temporary `ScannedFloor` to wrap `FloorGeometry`**

Keep only the transient semantic-ceiling bridge needed by untouched callers:

```java
record ScannedFloor(FloorGeometry geometry, int semanticCeilingY) {
    int anchorY() { return geometry.anchorY(); }
    BuildingFloorRegion region() { return geometry.projection(); }
}
```

Remove its old responsibility for manufacturing `StructureFloor`; that conversion moves to Task 4.

- [ ] **Step 6: Run scanner and connector tests GREEN**

Run:

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.server.world.data.SelectedFloorScannerTest --tests net.conczin.mca.server.world.data.StructureConnectorTest --no-daemon --console=plain
```

Expected: PASS, including the stacked Y88/Y91 same-column case.

- [ ] **Step 7: Commit exact semantic scanning**

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java common/src/main/java/net/conczin/mca/server/world/data/ScannedFloor.java common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java common/src/test/java/net/conczin/mca/server/world/data/SelectedFloorScannerTest.java common/src/test/java/net/conczin/mca/server/world/data/StructureConnectorTest.java
git commit -m "fix: preserve stacked floor cells during scanning"
```

---

### Task 3: Replace flattened Room topology with exact-cell `RoomPartitioner`

**Files:**
- Create: `common/src/main/java/net/conczin/mca/server/world/data/RoomPartitioner.java`
- Create: `common/src/test/java/net/conczin/mca/server/world/data/RoomPartitionerTest.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomPoiEvidence.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/BuildingRoomScannerOwnerTest.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/RoomPoiEvidenceTest.java`
- Delete after callers move: `common/src/main/java/net/conczin/mca/server/world/data/FloorSurfacePartitioner.java`
- Delete after replacement tests pass: `common/src/test/java/net/conczin/mca/server/world/data/FloorSurfacePartitionerTest.java`

**Interfaces:**
- Consumes: `FloorGeometry`.
- Produces:
  - `RoomPartitioner.partition(FloorGeometry geometry)`
  - `RoomPartitioner.select(BlockPos source, FloorGeometry geometry, List<Component> components)`
  - `RoomPartitioner.owner(Collection<Component> adjacent)`
  - `RoomPartitioner.adjacent(FloorGeometry.Cell cell, Collection<Component> components)`
  - `record Component(Set<FloorGeometry.Cell> cells)` with `contains(BlockPos feet)` and derived X/Z bounds.

- [ ] **Step 1: Write RED tests for multi-valued adjacent columns**

Add tests proving adjacency evaluates every candidate in an adjacent column and never links same-column cells directly:

```java
@Test
void adjacentColumnChoosesAllStepCompatibleCellsInsteadOfOneColumnRepresentative() {
    FloorGeometry.Cell start = cell(0, 90, 0);
    FloorGeometry.Cell tooLow = cell(1, 88, 0);
    FloorGeometry.Cell step = cell(1, 91, 0);
    FloorGeometry geometry = geometry(Set.of(start, tooLow, step), Map.of());

    List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry);

    RoomPartitioner.Component startComponent = components.stream()
            .filter(component -> component.contains(start.feet())).findFirst().orElseThrow();
    assertTrue(startComponent.contains(step.feet()));
    assertFalse(startComponent.contains(tooLow.feet()));
}

@Test
void sameColumnCellsDoNotConnectByThemselves() {
    FloorGeometry.Cell lower = cell(0, 88, 0);
    FloorGeometry.Cell upper = cell(0, 91, 0);
    assertEquals(2, RoomPartitioner.partition(
            geometry(Set.of(lower, upper), Map.of())).size());
}

@Test
void selectionUsesSourceHeightWhenTwoComponentsShareOneColumn() {
    FloorGeometry.Cell lower = cell(0, 88, 0);
    FloorGeometry.Cell upper = cell(0, 91, 0);
    FloorGeometry geometry = geometry(Set.of(lower, upper), Map.of());
    List<RoomPartitioner.Component> components = RoomPartitioner.partition(geometry);

    assertTrue(RoomPartitioner.select(new BlockPos(0, 91, 0), geometry, components)
            .contains(upper.feet()));
}
```

- [ ] **Step 2: Run `RoomPartitionerTest` RED**

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.server.world.data.RoomPartitionerTest --no-daemon --console=plain
```

Expected: compilation failure because `RoomPartitioner` does not exist.

- [ ] **Step 3: Implement exact graph traversal**

For every horizontal neighbor column, examine every candidate:

```java
for (Direction direction : HORIZONTAL) {
    int x = current.feet().getX() + direction.getStepX();
    int z = current.feet().getZ() + direction.getStepZ();
    for (FloorGeometry.Cell next : geometry.cellsAtColumn(x, z)) {
        if (boundaryCells.contains(next.feet())
                || visited.contains(next.feet())
                || !FloorGeometry.canStep(current.surfaceY(), next.surfaceY())) {
            continue;
        }
        visited.add(next.feet());
        queue.addLast(next);
    }
}
```

Boundary ownership remains deterministic, but boundary clusters are also keyed by full `BlockPos` and use height-compatible adjacency.

`select(...)` must first resolve an exact candidate cell from
`geometry.cellsAtColumn(source.getX(), source.getZ())` using source Y, then select
the component containing that full cell position. It must never return the first
component that merely contains the same X/Z column.

- [ ] **Step 4: Port `BuildingRoomScanner` to exact components**

`BuildingRoomScanner.Result` must carry exact component cell keys rather than anchor-Y projections:

```java
record Result(Building.validationResult status,
              BlockPos seed,
              int floorId,
              Set<BlockPos> floorCells,
              Set<BlockPos> poiCells,
              BlockPos min,
              BlockPos max) { }
```

Materialization uses:

```java
Set<BlockPos> floorCells = component.cells().stream()
        .map(FloorGeometry.Cell::feet)
        .collect(Collectors.toUnmodifiableSet());
```

The Room minimum/maximum Y comes from exact component cells and their physical ceilings, not from the semantic Floor anchor extrusion.

- [ ] **Step 5: Make POI evidence use each exact cell's physical interval**

Replace Floor-wide `anchorY..maxCeilingY` column sweeps with per-cell intervals:

```java
for (FloorGeometry.Cell cell : component.cells()) {
    addColumn(result,
            cell.feet().getX(), cell.feet().getZ(),
            cell.feet().getY() - 1, cell.ceilingY());
}
```

Perimeter ownership stays deterministic, but tests must prove perimeter POIs do not become Room floor cells.

- [ ] **Step 6: Run topology/owner/POI tests GREEN**

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.server.world.data.RoomPartitionerTest --tests net.conczin.mca.server.world.data.BuildingRoomScannerOwnerTest --tests net.conczin.mca.server.world.data.RoomPoiEvidenceTest --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 7: Delete the old partitioner only after all callers compile**

Remove `FloorSurfacePartitioner.java` and its test after `rg` shows no production reference:

```powershell
rg -n "FloorSurfacePartitioner" common/src/main common/src/test
```

Expected after cleanup: no matches.

- [ ] **Step 8: Commit exact Room topology**

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/RoomPartitioner.java common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java common/src/main/java/net/conczin/mca/server/world/data/RoomPoiEvidence.java common/src/test/java/net/conczin/mca/server/world/data/RoomPartitionerTest.java common/src/test/java/net/conczin/mca/server/world/data/BuildingRoomScannerOwnerTest.java common/src/test/java/net/conczin/mca/server/world/data/RoomPoiEvidenceTest.java
git add -u common/src/main/java/net/conczin/mca/server/world/data/FloorSurfacePartitioner.java common/src/test/java/net/conczin/mca/server/world/data/FloorSurfacePartitionerTest.java
git commit -m "refactor: partition rooms by exact floor cells"
```

---

### Task 4: Make `StructureFloor` own exact geometry and derive semantic ceilings in `Structure`

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureFloor.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Structure.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`
- Delete: `common/src/main/java/net/conczin/mca/server/world/data/ScannedFloor.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/StructureFloorTest.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/StructureFloorResolutionTest.java`
- Delete/update: `common/src/test/java/net/conczin/mca/server/world/data/ScannedFloorTest.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/StructureScannerAttachmentBoundaryTest.java`

**Interfaces:**
- Consumes: exact `FloorGeometry`, Task 2 semantic scan grouping.
- Produces target canonical Floor API:

```java
public record StructureFloor(int id, int floorNumber, FloorGeometry geometry) {
    public int anchorY() { return geometry.anchorY(); }
    public BuildingFloorRegion region() { return geometry.projection(); } // derived only
    public int area() { return geometry.projection().area(); }
    public List<ConnectorMarker> connectors() { return geometry.connectorMarkers(); }
}
```

- `Structure.semanticCeilingY(StructureFloor floor)` derives the old `518102886` invariant.
- `Structure.resolvePhysicalFloorCell(BlockPos pos)` returns both Floor and exact Cell.

- [ ] **Step 1: Write RED tests for derived semantic ceilings**

Add tests with exact lower and upper Floor geometry:

```java
@Test
void nonTopSemanticCeilingIsNextFloorAnchorEvenWhenLowerOwnsBoundaryHeightCell() {
    StructureFloor lower = floor(0, Set.of(
            cell(0, 88, 0, 90),
            cell(1, 91, 0, 93)));
    StructureFloor upper = floor(1, Set.of(
            cell(3, 91, 0, 94),
            cell(4, 91, 0, 94)));
    Structure structure = new Structure(7, new BlockPos(0, 88, 0), List.of(lower, upper));

    assertEquals(91, structure.semanticCeilingY(lower));
    assertEquals(94, structure.semanticCeilingY(upper));
}
```

Also assert exact physical resolution chooses the correct stacked cell based on query Y rather than an extruded X/Z region.

- [ ] **Step 2: Run StructureFloor/Resolution tests RED**

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.server.world.data.StructureFloorTest --tests net.conczin.mca.server.world.data.StructureFloorResolutionTest --no-daemon --console=plain
```

Expected: FAIL against the old `anchorY/ceilingY/region/ceilingBoundaryRegion` record.

- [ ] **Step 3: Replace persistent Floor fields with exact geometry**

Remove authoritative `anchorY`, `ceilingY`, `region`, `ceilingBoundaryRegion`, and stored connector list from the record. Derive anchor, projection, and connector markers from `geometry`.

Do not keep `ceilingBoundaryRegion` as a compatibility field in `StructureFloor.load`; old-format reading belongs in `RoomDFU` Task 7.

- [ ] **Step 4: Derive semantic ceiling at Structure level**

Implement deterministic sorted-Floor lookup:

```java
int semanticCeilingY(StructureFloor floor) {
    List<StructureFloor> sorted = getFloors();
    int index = sorted.indexOf(floor);
    if (index >= 0 && index + 1 < sorted.size()) {
        return sorted.get(index + 1).anchorY();
    }
    return floor.geometry().maxPhysicalCeilingY();
}
```

Replace direct production calls to `floor.ceilingY()` with either exact cell ceilings or `structure.semanticCeilingY(floor)`, depending on whether the caller is physical or semantic.

- [ ] **Step 5: Implement exact physical position resolution**

Add a structure-level result:

```java
record FloorCell(StructureFloor floor, FloorGeometry.Cell cell) { }
```

For a query `(x,y,z)`, inspect all cells at that X/Z in every candidate Floor, keep cells where `feetY <= y < ceilingY`, and choose the highest feet Y. Do not infer membership from `region` extrusion.

- [ ] **Step 6: Change `SelectedFloorScanner`/`StructureScanner` output to `FloorGeometry` and delete `ScannedFloor`**

Target records:

```java
record FloorSelection(FloorGeometry selected, List<FloorGeometry> connected) { }

record Result(Building.validationResult result,
              FloorGeometry floor,
              BlockPos min,
              BlockPos max,
              List<FloorGeometry> connectedFloors) { }
```

`StructureScanner` assigns stable Floor IDs and lets `Structure` derive semantic ceilings from the resulting ordered `StructureFloor`s.

- [ ] **Step 7: Run Floor/Structure/attachment tests GREEN**

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.server.world.data.SelectedFloorScannerTest --tests net.conczin.mca.server.world.data.StructureFloorTest --tests net.conczin.mca.server.world.data.StructureFloorResolutionTest --tests net.conczin.mca.server.world.data.StructureScannerAttachmentBoundaryTest --no-daemon --console=plain
```

Expected: PASS.

- [ ] **Step 8: Confirm removed representations have no production references**

```powershell
rg -n "ScannedFloor|ceilingBoundaryRegion" common/src/main/java/net/conczin/mca/server/world/data
```

Expected: no production matches.

- [ ] **Step 9: Commit canonical StructureFloor geometry**

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/StructureFloor.java common/src/main/java/net/conczin/mca/server/world/data/Structure.java common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java common/src/test/java/net/conczin/mca/server/world/data/StructureFloorTest.java common/src/test/java/net/conczin/mca/server/world/data/StructureFloorResolutionTest.java common/src/test/java/net/conczin/mca/server/world/data/StructureScannerAttachmentBoundaryTest.java
git add -u common/src/main/java/net/conczin/mca/server/world/data/ScannedFloor.java common/src/test/java/net/conczin/mca/server/world/data/ScannedFloorTest.java
git commit -m "refactor: make structure floors own exact geometry"
```

---

### Task 5: Persist exact Room cell ownership and resolve Rooms by exact Floor cell

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Building.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Structure.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/BuildingDiagnostics.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/StructureFloorResolutionTest.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/VillageFloorSystemTest.java`
- Add/modify any existing Building round-trip test under `common/src/test/java/net/conczin/mca/server/world/data/`.

**Interfaces:**
- Consumes: `BuildingRoomScanner.Result.floorCells()` and `Structure.resolvePhysicalFloorCell(...)`.
- Produces:
  - `Building.getFloorCells(): Set<BlockPos>`
  - `Building.ownsFloorCell(BlockPos): boolean`
  - `Building.projectedFloorRegion(): BuildingFloorRegion` as a derived view only
  - `Building.applyRoomScan(...)` stores exact cell keys.

- [ ] **Step 1: Write RED tests for exact Room ownership**

Use two cells in the same X/Z at different Y and assign them to different Rooms:

```java
@Test
void roomsCanOwnDifferentExactCellsInTheSameColumn() {
    Building lower = room(10, 20, 0, Set.of(new BlockPos(2, 88, 3)));
    Building upperTransition = room(11, 20, 0, Set.of(new BlockPos(2, 91, 3)));

    assertTrue(lower.ownsFloorCell(new BlockPos(2, 88, 3)));
    assertFalse(lower.ownsFloorCell(new BlockPos(2, 91, 3)));
    assertTrue(upperTransition.ownsFloorCell(new BlockPos(2, 91, 3)));
}
```

Also assert `projectedFloorRegion().area()` collapses both heights only for the derived view.

- [ ] **Step 2: Run affected tests RED**

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.server.world.data.StructureFloorResolutionTest --tests net.conczin.mca.server.world.data.VillageFloorSystemTest --no-daemon --console=plain
```

- [ ] **Step 3: Replace `Building.floorRegion` with exact `floorCells`**

Canonical state:

```java
private Set<BlockPos> floorCells = Set.of();

public Set<BlockPos> getFloorCells() {
    return floorCells;
}

boolean ownsFloorCell(BlockPos feet) {
    return floorCells.contains(feet);
}

BuildingFloorRegion projectedFloorRegion() {
    int projectionY = floorCells.stream().mapToInt(BlockPos::getY).min().orElse(posY);
    return BuildingFloorRegion.fromFootprint(projectionY, floorCells);
}
```

Do not let this derived projection participate in exact position ownership.

- [ ] **Step 4: Apply scan results as exact cell sets**

`applyRoomScan` takes `scan.floorCells()` directly. Bounds remain cached display/POI bounds, but are derived from the exact component during scan.

Remove persistence writes of `floorRegions` from `Building.save()`; old `floorRegions` parsing is moved to `RoomDFU` in Task 7.

- [ ] **Step 5: Resolve a Room only after resolving an exact Floor cell**

In `Structure.resolveInteractionPosition`, first resolve `FloorCell`, then select the Room by exact key:

```java
FloorCell resolved = resolvePhysicalFloorCell(pos).orElse(null);
if (resolved == null) return Optional.empty();
Building room = rooms.stream()
        .filter(candidate -> candidate.getFloorId() == resolved.floor().id())
        .filter(candidate -> candidate.ownsFloorCell(resolved.cell().feet()))
        .min(Comparator.comparingInt(Building::getId))
        .orElse(null);
```

Connector handoff resolution may provide a precise `FloorGeometry.Cell`, but it must use the same Room ownership lookup.

- [ ] **Step 6: Run exact ownership and Village Floor tests GREEN**

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.server.world.data.StructureFloorResolutionTest --tests net.conczin.mca.server.world.data.VillageFloorSystemTest --no-daemon --console=plain
```

- [ ] **Step 7: Commit exact Room ownership**

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/Building.java common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java common/src/main/java/net/conczin/mca/server/world/data/Structure.java common/src/main/java/net/conczin/mca/server/world/data/BuildingDiagnostics.java common/src/test/java/net/conczin/mca/server/world/data/StructureFloorResolutionTest.java common/src/test/java/net/conczin/mca/server/world/data/VillageFloorSystemTest.java
git commit -m "refactor: persist exact room floor cells"
```

---

### Task 6: Simplify Floor refresh/add-Room workflow around one exact geometry truth

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureExpansionPolicy.java` or delete if it becomes a thin wrapper.
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomWorkflow.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Village.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/StructureExpansionPolicyTest.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/VillageManagerExpandedRoomCommitTest.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/VillageFloorSystemTest.java`

**Interfaces:**
- Consumes: fresh `FloorGeometry`, exact Room components, stable Floor/Room identity policies.
- Produces one atomic operation that publishes a refreshed `StructureFloor` plus reconciled Rooms together.

- [ ] **Step 1: Add RED atomicity tests using exact geometry**

Preserve the existing stale-outline scenario but assert exact cell state, not only projected area:

```java
assertTrue(village.getStructure(structureId).orElseThrow()
        .getFloor(floorId).orElseThrow().geometry().cellAt(newCell).isPresent());
assertTrue(village.getBuilding(newRoomId).orElseThrow().getFloorCells().contains(newCell));
```

Add a failed revalidation test that snapshots the StructureFloor geometry and Room cell sets before the operation and asserts both are unchanged after failure.

- [ ] **Step 2: Run atomic workflow tests RED**

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.server.world.data.VillageManagerExpandedRoomCommitTest --tests net.conczin.mca.server.world.data.StructureExpansionPolicyTest --no-daemon --console=plain
```

- [ ] **Step 3: Reduce expansion matching to identity only**

Fresh Floor matching may use semantic band + derived projection overlap to find a unique persisted Floor, but the match result must carry only identity:

```java
record FloorTarget(int structureId, int floorId) { }
```

Do not return or cache a second geometry copy from `StructureExpansionPolicy`. If its only remaining method delegates to one matcher, inline it into `Village`/`RoomWorkflow` and delete the class/test.

- [ ] **Step 4: Build the complete replacement state before mutating Village maps**

The operation order is:

```text
fresh FloorGeometry
 -> exact RoomPartitioner components
 -> RoomIdentityPolicy assignment
 -> refreshed Structure copy with replaced FloorGeometry
 -> reconciled Room copies with exact floorCells
 -> validate all references/disjoint ownership
 -> publish Structure + Rooms together
```

Use the existing snapshot/copy pattern in `Village`/`VillageManager`; do not update the Floor first and Rooms later.

- [ ] **Step 5: Keep `RoomScanPlan` geometry-free**

Verify no `FloorGeometry`, component set, or scan result field is added to `RoomScanPlan`. Execution rescans live world and revalidates the stored identities/action.

- [ ] **Step 6: Run workflow tests GREEN**

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.server.world.data.VillageManagerExpandedRoomCommitTest --tests net.conczin.mca.server.world.data.VillageFloorSystemTest --no-daemon --console=plain
```

If `StructureExpansionPolicy` remains, include its test; if deleted, `rg -n "StructureExpansionPolicy" common/src` must return no matches.

- [ ] **Step 7: Commit atomic workflow simplification**

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/RoomWorkflow.java common/src/main/java/net/conczin/mca/server/world/data/Village.java common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java common/src/test/java/net/conczin/mca/server/world/data/VillageManagerExpandedRoomCommitTest.java common/src/test/java/net/conczin/mca/server/world/data/VillageFloorSystemTest.java
git add -u common/src/main/java/net/conczin/mca/server/world/data/StructureExpansionPolicy.java common/src/test/java/net/conczin/mca/server/world/data/StructureExpansionPolicyTest.java
git commit -m "refactor: publish floor and room refresh atomically"
```

---

### Task 7: Introduce canonical persistence and narrow `RoomDFU` migration to exactly two upstream sources

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomDFU.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Village.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Building.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Structure.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureFloor.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/FloorGeometry.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/RoomDFUTest.java`

**Interfaces:**
- `Village.BUILDING_DATA_VERSION = 1`.
- `RoomDFU.load(CompoundTag villageTag)` is the only building-state load entry point.
- Supported inputs:
  1. no `buildingDataVersion`, no `structures` -> released `origin/1.21.1` migration;
  2. no `buildingDataVersion`, `structures` present -> upstream `origin/feature/1.21.1-floor-clean-squash` migration;
  3. `buildingDataVersion == Village.BUILDING_DATA_VERSION` -> direct canonical load.

- [ ] **Step 1: Rewrite migration tests first to state the exact support contract**

Replace generic `previousBranchVillage` naming with `upstreamFloorCleanSquashVillage`, matching fields emitted by the upstream branch. Add:

```java
@Test
void canonicalVillageSaveUsesVersionOne() {
    Village village = new Village(1, null);
    assertEquals(1, village.save().getInt("buildingDataVersion"));
}
```

Keep released-origin tests for ID preservation, grouped/external migration, and legacy `blocks2` compound positions.
Keep the upstream floor-clean-squash Main Room/inheritance assertions too:
the old Structure `mainRoomId` and old Room `inheritanceEnabled` values must be
translated into current `LogicalBuilding` + `contributesToMain` state without
teaching current-domain classes those legacy fields.

- [ ] **Step 2: Add RED upstream floor-clean-squash migration assertions for exact geometry**

The test fixture must be unversioned and contain `structures`. Assert:

```java
RoomDFU.Result migrated = RoomDFU.load(upstreamFloorCleanSquashVillage(true));
StructureFloor floor = migrated.structures().get(20).getFloor(0).orElseThrow();

assertEquals(Set.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0)),
        floor.geometry().cells().stream().map(FloorGeometry.Cell::feet).collect(Collectors.toSet()));
assertEquals(Set.of(new BlockPos(0, 64, 0), new BlockPos(1, 64, 0)),
        migrated.buildings().get(10).getFloorCells());
```

This migration synthesizes flat exact cells from old `region(anchorY)` because upstream never persisted exact physical heights.

- [ ] **Step 3: Run `RoomDFUTest` RED**

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.server.world.data.RoomDFUTest --no-daemon --console=plain
```

- [ ] **Step 4: Make canonical classes read only canonical fields**

Canonical `StructureFloor.save()` writes exact cells and connector types; canonical `Building.save()` writes exact `floorCells`.

Example Floor cell NBT shape:

```text
floors: [
  {
    id: 0,
    cells: [
      { pos: <BlockPos>, surfaceY: 88.0d, ceilingY: 90 }
    ],
    connectors: [
      { pos: <exact Floor-cell BlockPos>, type: "door" }
    ]
  }
]
```

`Building`, `Structure`, and `StructureFloor` constructors/loaders must not inspect `floorRegions`, `ceilingBoundaryRegion`, `inheritanceEnabled`, `rootRoomId`, `mainRoomAutomatic`, or legacy BlockPos `{x,y,z}` compounds.

- [ ] **Step 5: Centralize every load path in `RoomDFU.load`**

Use this exact routing shape:

```java
static Result load(CompoundTag villageTag) {
    if (villageTag.contains("buildingDataVersion")) {
        int version = villageTag.getInt("buildingDataVersion");
        if (version != Village.BUILDING_DATA_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported MCA buildingDataVersion: " + version);
        }
        return loadCurrent(villageTag);
    }

    return villageTag.contains("structures", Tag.TAG_LIST)
            ? migrateUpstreamFloorCleanSquash(villageTag)
            : migrateOrigin(villageTag.getList("buildings", Tag.TAG_COMPOUND));
}
```

`Village(CompoundTag, ServerLevel)` becomes a consumer of `RoomDFU.Result`; remove its separate version switch.

- [ ] **Step 6: Implement released-origin migration directly to canonical state**

For each functional legacy Building, synthesize one exact flat Floor from its stored bounds and create one Room whose exact cell keys match that Floor. Preserve old grouped Building -> `ExternalBuilding` behavior and normalize legacy POI BlockPos compounds in `RoomDFU` before constructing current `Building` objects.

- [ ] **Step 7: Implement only the upstream floor-clean-squash migration**

For every upstream old `StructureFloor`:

```java
BuildingFloorRegion region = BuildingFloorRegion.load(oldFloor.getCompound("region"));
int oldCeilingY = oldFloor.getInt("ceilingY");
Set<FloorGeometry.Cell> cells = region.cells().stream()
        .map(pos -> new FloorGeometry.Cell(pos, pos.getY(), oldCeilingY))
        .collect(Collectors.toUnmodifiableSet());
```

Read old connectors only because they are part of the upstream format, associate them with these synthesized exact cells, and construct current `StructureFloor` directly. Do not parse `ceilingBoundaryRegion`; it is not part of the upstream migration source.

For each old Room `floorRegion`, assign every exact cell in its owning migrated Floor whose X/Z is in that old Region. This is the only place where old column ownership is interpreted.

Build current `LogicalBuilding` objects in `RoomDFU` during both migrations.
For upstream floor-clean-squash, preserve the old Main Room and inheritance
meaning already covered by `RoomDFUTest`; for released origin, the sole
functional Room becomes the Main Room of its synthesized Structure.

- [ ] **Step 8: Run migration and round-trip tests GREEN**

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.server.world.data.RoomDFUTest --no-daemon --console=plain
```

Expected: origin migration PASS, upstream floor-clean-squash migration PASS, canonical round-trip PASS.

- [ ] **Step 9: Verify historical fields are isolated**

Run these searches after migration is complete:

```powershell
rg -n 'floorRegions|ceilingBoundaryRegion|inheritanceEnabled|rootRoomId|mainRoomAutomatic|groundStructureId|groundFloorId' common/src/main/java/net/conczin/mca/server/world/data
```

Expected: historical parsing references occur only in `RoomDFU.java`; current-domain names that are still legitimate for `LogicalBuilding` behavior must not be old-format parsing branches.

- [ ] **Step 10: Commit the canonical compatibility boundary**

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/RoomDFU.java common/src/main/java/net/conczin/mca/server/world/data/Village.java common/src/main/java/net/conczin/mca/server/world/data/Building.java common/src/main/java/net/conczin/mca/server/world/data/Structure.java common/src/main/java/net/conczin/mca/server/world/data/StructureFloor.java common/src/main/java/net/conczin/mca/server/world/data/FloorGeometry.java common/src/test/java/net/conczin/mca/server/world/data/RoomDFUTest.java
git commit -m "refactor: version exact floor geometry persistence"
```

---

### Task 8: Remove obsolete flattened geometry and run the complete regression gate

**Files:**
- Delete: `common/src/main/java/net/conczin/mca/server/world/data/FloorSurface.java`
- Delete/update: `common/src/test/java/net/conczin/mca/server/world/data/FloorSurfaceTest.java`
- Modify as needed: remaining floor/room tests and diagnostics only to remove dead APIs.
- Update: `docs/superpowers/specs/2026-09-04-floor-room-3d-geometry-architecture-spec.md` only if implementation revealed a factual interface-name difference; do not weaken invariants.

**Interfaces:**
- Consumes: completed Tasks 1-7.
- Produces: no transitional geometry types, no flattened authoritative persistence, clean full verification.

- [ ] **Step 1: Search for forbidden old spatial assumptions**

Run:

```powershell
rg -n "FloorSurface|ScannedFloor|cellAtColumn|ceilingBoundaryRegion" common/src/main common/src/test
```

Expected: no production matches; tests may mention old names only if explicitly testing rejection/migration text, otherwise remove them.

- [ ] **Step 2: Search for authoritative Room/Floor `BuildingFloorRegion` persistence**

```powershell
rg -n 'put\("floorRegions"|put\("region"|getCompound\("region"|getList\("floorRegions"' common/src/main/java/net/conczin/mca/server/world/data
```

Expected: old-format reads only in `RoomDFU`; `BuildingFloorRegion` usage elsewhere is derivation/projection only.

- [ ] **Step 3: Run the focused floor/room regression set**

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.server.world.data.FloorGeometryTest --tests net.conczin.mca.server.world.data.SelectedFloorScannerTest --tests net.conczin.mca.server.world.data.RoomPartitionerTest --tests net.conczin.mca.server.world.data.BuildingRoomScannerOwnerTest --tests net.conczin.mca.server.world.data.RoomPoiEvidenceTest --tests net.conczin.mca.server.world.data.StructureFloorTest --tests net.conczin.mca.server.world.data.StructureFloorResolutionTest --tests net.conczin.mca.server.world.data.StructureScannerAttachmentBoundaryTest --tests net.conczin.mca.server.world.data.VillageFloorSystemTest --tests net.conczin.mca.server.world.data.VillageManagerExpandedRoomCommitTest --tests net.conczin.mca.server.world.data.RoomDFUTest --no-daemon --console=plain
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run all common tests**

```powershell
.\gradlew.bat :common:test --rerun-tasks --no-daemon --console=plain
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Compile NeoForge integration**

```powershell
.\gradlew.bat :neoforge:compileJava --rerun-tasks --no-daemon --console=plain
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Run whitespace/diff validation**

```powershell
git diff --check
```

Expected: no output, exit code 0.

- [ ] **Step 7: Inspect the final working tree without discarding pre-existing work**

```powershell
git status --short --branch
```

Confirm only intentional floor/room architecture changes and the spec/plan are present. Do not reset unrelated existing modifications.

- [ ] **Step 8: Commit final dead-code cleanup**

```powershell
git add -A common/src/main/java/net/conczin/mca/server/world/data common/src/test/java/net/conczin/mca/server/world/data docs/superpowers/specs/2026-09-04-floor-room-3d-geometry-architecture-spec.md docs/superpowers/plans/2026-09-04-floor-room-3d-geometry.md
git commit -m "refactor: remove flattened floor geometry model"
```

---

## Final Contract Checklist

- The Y88/Y91 same-X/Z staircase regression is represented in a pure test and does not throw.
- `FloorGeometry` preserves both exact cells in that column.
- No production API returns an arbitrary single cell for an X/Z column.
- Semantic assignment still places the sparse Y91 transition on the lower Floor and the meaningful Y91 room on the upper Floor.
- Room traversal examines every height-compatible adjacent-column cell and never connects cells merely because they share X/Z.
- `StructureFloor` owns exact geometry; semantic ceiling is Structure-derived.
- Rooms persist exact Floor-cell keys and are subsets of the owning Floor geometry.
- Room ownership resolution is exact-cell based, not X/Z-column based.
- `BuildingFloorRegion` is only a derived 2D view.
- `ceilingBoundaryRegion`, `FloorSurface`, `FloorSurfacePartitioner`, and `ScannedFloor` are gone.
- Floor + Room refresh is atomic.
- `RoomScanPlan` remains action/identity only.
- `buildingDataVersion` starts at 1 for the new canonical exact-geometry format.
- `RoomDFU` directly loads the current canonical version and migrates only released `origin/1.21.1` plus upstream unversioned `origin/feature/1.21.1-floor-clean-squash`.
- Full common tests, NeoForge compile, and `git diff --check` pass.
