# Floor Scanner Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the coupled floor/Room scanner with a single-floor, exact-3D pipeline that handles uneven Minecraft geometry, tall enclosures, generic connector ownership, wall POIs, manual floor/basement attachments, and selected-Room-lineage `Update Room` reconciliation that handles genuine splits without rewriting unrelated Rooms.

**Architecture:** Every action scans exactly one semantic floor selected by its interaction/scan seed. Minecraft world geometry is translated into an immutable transient `FloorSurface` that preserves each cell's real Y, collision-derived surface height, local ceiling, and connector associations; Room topology and POI evidence consume that transient model before it is projected to the existing compact persisted `BuildingFloorRegion`. Persistence matching and mutation remain outside physical traversal, with `VillageManager` keeping detached analysis and atomic commit boundaries.

**Tech Stack:** Java 21, Minecraft 1.21.1/Parchment mappings, Architectury multiloader, JUnit 5 in `:common`, NeoForge 21.1.234 GameTest integration.

**Spec:** `docs/superpowers/specs/2026-08-30-floor-scanner-simplification-spec.md`

## Global Constraints

- Every scan is scoped to **one semantic floor** selected by the interaction/action; ordinary scans never auto-register upper floors or basements.
- `ADD_FLOOR` and `ADD_BASEMENT` explicitly scan only their selected destination floor and then validate attachment to the target logical building.
- Fresh topology must preserve real per-cell Y until Room topology and POI evidence are complete; persisted X/Z projection happens afterwards.
- Use Minecraft 1.21.1 collision semantics. Prefer `WalkNodeEvaluator.getFloorLevel(BlockGetter, BlockPos)` rather than duplicating vanilla's floor-height formula.
- No fixed `ROOF_SEARCH` building-height limit. Ceiling probing is bounded by `Level#getMaxBuildHeight()` and real scanner radius/work limits.
- `Config.maxBuildingSize` counts meaningful discovered floor/footprint geometry, not empty vertical air.
- Doors, fence gates, trapdoors, and ladders remain semantic connectors across open/closed state; connector ownership never makes a connector a Room topology bridge.
- Doors contribute one footprint/shading cell to one adjacent Room. Interior and exterior doors use the same deterministic owner rule.
- `BuildingTypes`/POI relevance must not influence physical floor discovery or Room connectivity.
- Wall/perimeter blocks are POI candidates after topology; `Building.recordBuildingBlock(...)` remains the relevance filter.
- `Update Room` reconciles only the selected registered Room's lineage. Fresh components that still
  descend from the selected Room may become split-off Rooms; other pre-existing Rooms on the same
  floor are immutable blockers and are never auto-merged/re-IDed/retyped by that update.
- No NBT/DFU migration in this implementation. Existing `BuildingFloorRegion` persistence remains the compact projection.
- Analysis remains detached; persisted Village/Structure/Room state changes only after all validation succeeds.
- Do not implement a complete fake Minecraft `Level`.
- Do not reset, clean, stage, commit, or delete unrelated user work. `common/logs/` is generated runtime output and must not be committed.

---

## File Structure

### New common production files

- `common/src/main/java/net/conczin/mca/server/world/data/FloorSurface.java`
  - Immutable exact transient representation of one selected semantic floor.
  - Owns exact surface cells, deterministic persistence anchor, connector-to-floor associations, and X/Z projection helpers.
- `common/src/main/java/net/conczin/mca/server/world/data/FloorSurfacePartitioner.java`
  - Pure Room-component partitioning, source selection, and deterministic connector owner selection from exact surface geometry.
- `common/src/main/java/net/conczin/mca/server/world/data/FloorCeilingResolver.java`
  - Minecraft-facing cached ceiling/roof probe for exact selected-floor cells. It searches to the dimension build-height bound, ignores leaves as roof evidence without treating them as traversable air, and never flood-fills another storey's air volume.
- `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`
  - Minecraft-facing adapter that discovers one exact physical floor from a seed, checks enclosure/ceiling/collision, records connectors, and never climbs to another storey.
- `common/src/main/java/net/conczin/mca/server/world/data/RoomPoiEvidence.java`
  - Pure geometry for interior/support/perimeter/connector POI candidate positions after Room topology is known.

### Common production files modified

- `common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java`
  - Seed resolution and Structure-level orchestration around `SelectedFloorScanner`; remove `PersistedFloorBoundary`, fixed roof depth, vertical-air volume accounting, and multi-storey traversal.
- `common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java`
  - Connector classification, door-half normalization, and association of connectors with actual `FloorSurface` cells; remove `ownsFloorCell(BlockState)`.
- `common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java`
  - Consume exact `FloorSurface`; remove BuildingType-aware topology, stale gap reconstruction, and functional-POI footprint attachment.
- `common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java`
  - Route add/update/manual attachment actions through fresh selected-floor scans; reconcile only the selected Room lineage during `Update Room`; keep neighboring registered Rooms immutable; keep atomic commit.
- `common/src/main/java/net/conczin/mca/server/world/data/RegisteredRoomUpdate.java`
  - Carry detached selected-lineage reconciliation assignments plus any minimal detached Structure-envelope growth required to contain them.
- `common/src/main/java/net/conczin/mca/server/world/data/Structure.java`
  - Grow one persisted floor envelope when selected-lineage Rooms expand without deleting untouched floor geometry; recompute Structure bounds deterministically.
- `common/src/main/java/net/conczin/mca/server/world/data/RegisteredRoomReconciler.java`
  - Keep deterministic identity assignment, but narrow its caller contract from whole-floor reconciliation to an explicit selected-Room lineage.
- `common/src/main/java/net/conczin/mca/server/world/data/BuildingDiagnostics.java`
  - Report selected-floor physical/matching/topology failures with the new scanner API.
- `common/src/main/java/net/conczin/mca/server/world/data/BuildingFloorRegionDetector.java`
  - Stop acting as fresh multi-storey discovery; retain only compatibility helpers/constants still used by persistence/legacy interaction code, then delete unused detection code.

### New/modified common tests

- Create `common/src/test/java/net/conczin/mca/server/world/data/FloorSurfaceTest.java`
- Create `common/src/test/java/net/conczin/mca/server/world/data/FloorSurfacePartitionerTest.java`
- Create `common/src/test/java/net/conczin/mca/server/world/data/RoomPoiEvidenceTest.java`
- Create `common/src/test/java/net/conczin/mca/server/world/data/RegisteredRoomUpdateLineageTest.java`
- Modify `common/src/test/java/net/conczin/mca/server/world/data/BuildingRoomScannerOwnerTest.java`
- Replace `common/src/test/java/net/conczin/mca/server/world/data/StructureScannerAttachmentBoundaryTest.java` with selected-floor/manual-attachment matching tests; `PersistedFloorBoundary` itself is intentionally deleted.
- Extend `common/src/test/java/net/conczin/mca/server/world/data/VillageFloorSystemTest.java` for untouched-storey identity and manual floor-number preservation.

### NeoForge integration tests

- Create `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`
  - Keep the GameTest class in the scanner package so it can exercise package-private scanner APIs without widening production visibility.
- Modify `neoforge/build.gradle` to add a dedicated `gameTestServer` run and enable the known-working vanilla `minecraft:bastion/blocks/air` template namespace. Do not add a Fabric duplicate solely for symmetry.

---

### Task 0: Checkpoint the current dirty work before redesign

**Files:**
- Checkpoint: `common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java`
- Checkpoint: `common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java`
- Checkpoint: `common/src/test/java/net/conczin/mca/server/world/data/BuildingRoomScannerOwnerTest.java`
- Checkpoint: `docs/superpowers/specs/2026-08-30-blueprint-map-and-floor-scan-corrections-design.md`
- Checkpoint: `docs/superpowers/plans/2026-08-30-blueprint-map-and-floor-scan-corrections.md`
- Checkpoint: `docs/superpowers/specs/2026-08-30-floor-scanner-simplification-design.md`
- Checkpoint: `docs/superpowers/specs/2026-08-30-floor-scanner-simplification-spec.md`
- Checkpoint: `docs/superpowers/plans/2026-08-30-floor-scanner-simplification.md`
- Preserve untracked: `common/logs/`

**Interfaces:**
- Consumes: current dirty working tree exactly as found before scanner implementation begins.
- Produces: two explicit historical checkpoints and a status where generated logs are not accidentally staged.

- [ ] **Step 1: Verify the exact dirty set before staging anything**

Run:

```powershell
git.exe status --short
git.exe diff --check
```

Expected tracked Java diff: only `BuildingRoomScanner.java`, `StructureConnector.java`, and `BuildingRoomScannerOwnerTest.java`. Expected untracked roots: `docs/` and `common/logs/`.

- [ ] **Step 2: Keep generated logs out of all commits without deleting them**

Add this local-only line to `.git/info/exclude` using the file-edit tool, not a shell redirect:

```text
common/logs/
```

Then run:

```powershell
git.exe status --short
```

Expected: `common/logs/` no longer appears; its files remain on disk.

- [ ] **Step 3: Run the narrow baseline tests for the experimental Java checkpoint**

Run:

```powershell
./gradlew.bat :common:test --tests net.conczin.mca.server.world.data.BuildingRoomScannerOwnerTest --tests net.conczin.mca.server.world.data.StructureScannerAttachmentBoundaryTest
```

Expected: PASS. This proves the checkpoint is internally coherent; it does **not** mean the experiment matches the approved spec.

- [ ] **Step 4: Commit the pre-redesign scanner experiment exactly as historical work**

Run:

```powershell
git.exe add common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java common/src/test/java/net/conczin/mca/server/world/data/BuildingRoomScannerOwnerTest.java
git.exe diff --cached --check
git.exe commit -m "chore: checkpoint pre-redesign scanner experiment"
```

The commit intentionally contains the now-rejected `ownsFloorCell` door rule. Do not amend it into the redesign later; replace it in a new tested commit so the checkpoint remains useful.

- [ ] **Step 5: Commit the accepted design/spec/plan separately from code**

Run:

```powershell
git.exe add docs/superpowers
git.exe diff --cached --check
git.exe commit -m "docs: specify floor scanner simplification"
```

- [ ] **Step 6: Verify the implementation starts from a clean tracked worktree**

Run:

```powershell
git.exe status --short
git.exe log --oneline -3
```

Expected: no tracked/untracked implementation files are listed; `common/logs/` remains physically present but locally excluded. The two newest commits are the docs checkpoint and the pre-redesign scanner checkpoint.

---

### Task 1: Introduce the exact transient `FloorSurface` model

**Files:**
- Create: `common/src/main/java/net/conczin/mca/server/world/data/FloorSurface.java`
- Create: `common/src/test/java/net/conczin/mca/server/world/data/FloorSurfaceTest.java`

**Interfaces:**
- Consumes: raw exact discovered surface cells and connector associations from later Minecraft-facing scanning.
- Produces:
  - `FloorSurface.Cell(BlockPos feet, double surfaceY, int ceilingY)`
  - `FloorSurface(Set<Cell> cells, Map<BlockPos, BlockPos> connectorByFloorCell)`
  - `int anchorY()` deterministic persistence anchor
  - `Optional<Cell> cellAtColumn(int x, int z)`
  - `Set<BlockPos> projectedCells()`
  - `BuildingFloorRegion persistedRegion()`
  - `int maxCeilingY()`

- [ ] **Step 1: Write failing tests for exact-Y retention and deterministic projection**

Create `FloorSurfaceTest.java` with tests equivalent to:

```java
@Test
void keepsExactCellHeightsWhileProjectingOnePersistedFootprint() {
    FloorSurface surface = new FloorSurface(Set.of(
            cell(0, 64, 0),
            cell(1, 64, 0),
            cell(2, 65, 0),
            cell(3, 66, 0)), Map.of());

    assertEquals(Set.of(64, 65, 66), surface.cells().stream()
            .map(cell -> cell.feet().getY()).collect(Collectors.toSet()));
    assertEquals(64, surface.anchorY());
    assertEquals(4, surface.persistedRegion().area());
    assertTrue(surface.persistedRegion().cells().stream().allMatch(pos -> pos.getY() == 64));
}

@Test
void anchorUsesLargestHeightSliceThenLowerYForEqualSlices() {
    FloorSurface surface = new FloorSurface(Set.of(
            cell(0, 65, 0), cell(1, 65, 0),
            cell(0, 64, 1), cell(1, 64, 1)), Map.of());

    assertEquals(64, surface.anchorY());
}
```

Use this local helper in the test:

```java
private static FloorSurface.Cell cell(int x, int y, int z) {
    return new FloorSurface.Cell(new BlockPos(x, y, z), y, y + 4);
}
```

- [ ] **Step 2: Run the tests and verify RED**

Run:

```powershell
./gradlew.bat :common:test --tests net.conczin.mca.server.world.data.FloorSurfaceTest
```

Expected: FAIL because `FloorSurface` does not exist.

- [ ] **Step 3: Implement the minimal immutable model**

Create `FloorSurface.java` with this API shape:

```java
record FloorSurface(Set<Cell> cells, Map<BlockPos, BlockPos> connectorByFloorCell) {
    FloorSurface {
        cells = Set.copyOf(cells);
        connectorByFloorCell = Map.copyOf(connectorByFloorCell);
    }

    int anchorY() {
        Map<Integer, Long> counts = cells.stream().collect(Collectors.groupingBy(
                cell -> cell.feet().getY(), Collectors.counting()));
        return counts.entrySet().stream()
                .max(Comparator.<Map.Entry<Integer, Long>>comparingLong(Map.Entry::getValue)
                        .thenComparing(Comparator.comparingInt(Map.Entry<Integer, Long>::getKey).reversed()))
                .map(Map.Entry::getKey)
                .orElse(0);
    }

    Optional<Cell> cellAtColumn(int x, int z) {
        return cells.stream()
                .filter(cell -> cell.feet().getX() == x && cell.feet().getZ() == z)
                .findFirst();
    }

    Set<BlockPos> projectedCells() {
        int y = anchorY();
        return cells.stream()
                .map(cell -> new BlockPos(cell.feet().getX(), y, cell.feet().getZ()))
                .collect(Collectors.toUnmodifiableSet());
    }

    BuildingFloorRegion persistedRegion() {
        return BuildingFloorRegion.fromFootprint(anchorY(), projectedCells());
    }

    int maxCeilingY() {
        return cells.stream().mapToInt(Cell::ceilingY).max().orElse(anchorY() + 2);
    }

    record Cell(BlockPos feet, double surfaceY, int ceilingY) {
        Cell {
            feet = feet.immutable();
        }
    }
}
```

Keep it package-private like the existing scanner helpers.

- [ ] **Step 4: Run the exact model tests**

Run:

```powershell
./gradlew.bat :common:test --tests net.conczin.mca.server.world.data.FloorSurfaceTest
```

Expected: PASS.

- [ ] **Step 5: Commit the exact transient model**

Run:

```powershell
git.exe add common/src/main/java/net/conczin/mca/server/world/data/FloorSurface.java common/src/test/java/net/conczin/mca/server/world/data/FloorSurfaceTest.java
git.exe diff --cached --check
git.exe commit -m "refactor: add exact floor surface model"
```

---

### Task 2: Add pure exact-Y Room partitioning and deterministic ownership

**Files:**
- Create: `common/src/main/java/net/conczin/mca/server/world/data/FloorSurfacePartitioner.java`
- Create: `common/src/test/java/net/conczin/mca/server/world/data/FloorSurfacePartitionerTest.java`

**Interfaces:**
- Consumes: `FloorSurface` from Task 1.
- Produces:
  - `List<FloorSurfacePartitioner.Component> partition(FloorSurface surface)`
  - `Component select(BlockPos source, FloorSurface surface, List<Component> components)`
  - `Component owner(Collection<Component> adjacent)`
  - `List<Component> adjacent(BlockPos floorCell, Collection<Component> components)`
  - `Component.projectedCells(int anchorY)` for persistence/render footprint only.

- [ ] **Step 1: Write RED tests for uneven connectivity, connector boundaries, and owner ordering**

Use tests equivalent to:

```java
@Test
void gradualUnevenSurfaceRemainsOneComponentWithoutFlattening() {
    FloorSurface surface = surface(Set.of(
            cell(0, 64, 0), cell(1, 64, 0), cell(2, 65, 0), cell(3, 66, 0)), Map.of());

    List<FloorSurfacePartitioner.Component> components = FloorSurfacePartitioner.partition(surface);

    assertEquals(1, components.size());
    assertEquals(Set.of(64, 65, 66), components.getFirst().cells().stream()
            .map(cell -> cell.feet().getY()).collect(Collectors.toSet()));
}

@Test
void connectorCellSeparatesTwoRoomComponents() {
    BlockPos connectorCell = new BlockPos(1, 64, 0);
    FloorSurface surface = surface(Set.of(
            cell(0, 64, 0), cell(1, 64, 0), cell(2, 64, 0)),
            Map.of(connectorCell, connectorCell));

    assertEquals(2, FloorSurfacePartitioner.partition(surface).size());
}

@Test
void connectorOwnerUsesLargestComponentThenStableBounds() {
    var small = component(Set.of(cell(0, 64, 0)));
    var large = component(Set.of(cell(2, 64, 0), cell(3, 64, 0)));

    assertEquals(large, FloorSurfacePartitioner.owner(List.of(small, large)));
}

private static FloorSurface surface(Set<FloorSurface.Cell> cells,
                                    Map<BlockPos, BlockPos> connectors) {
    return new FloorSurface(cells, connectors);
}

private static FloorSurface.Cell cell(int x, int y, int z) {
    return new FloorSurface.Cell(new BlockPos(x, y, z), y, y + 4);
}

private static FloorSurfacePartitioner.Component component(Set<FloorSurface.Cell> cells) {
    return new FloorSurfacePartitioner.Component(cells);
}
```

- [ ] **Step 2: Verify RED**

Run:

```powershell
./gradlew.bat :common:test --tests net.conczin.mca.server.world.data.FloorSurfacePartitionerTest
```

Expected: FAIL because `FloorSurfacePartitioner` does not exist.

- [ ] **Step 3: Implement the pure partitioner without `Level` or `BuildingTypes`**

Use this concrete shape:

```java
final class FloorSurfacePartitioner {
    private static final double MAX_STEP_HEIGHT = 1.125D;
    private static final Direction[] HORIZONTAL = {
            Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST
    };
    private static final Comparator<Component> OWNER_ORDER =
            Comparator.comparingInt(Component::area).reversed()
                    .thenComparingInt(Component::minX)
                    .thenComparingInt(Component::minZ)
                    .thenComparingInt(Component::maxX)
                    .thenComparingInt(Component::maxZ);

    static List<Component> partition(FloorSurface surface) {
        Set<BlockPos> connectorCells = surface.connectorByFloorCell().keySet();
        Set<BlockPos> visited = new HashSet<>();
        List<Component> result = new ArrayList<>();

        List<FloorSurface.Cell> seeds = surface.cells().stream()
                .sorted(Comparator.comparingInt((FloorSurface.Cell cell) -> cell.feet().getX())
                        .thenComparingInt(cell -> cell.feet().getZ())
                        .thenComparingInt(cell -> cell.feet().getY()))
                .toList();
        for (FloorSurface.Cell seed : seeds) {
            if (connectorCells.contains(seed.feet()) || !visited.add(seed.feet())) continue;
            LinkedHashSet<FloorSurface.Cell> componentCells = new LinkedHashSet<>();
            ArrayDeque<FloorSurface.Cell> queue = new ArrayDeque<>();
            queue.addLast(seed);

            while (!queue.isEmpty()) {
                FloorSurface.Cell current = queue.removeFirst();
                componentCells.add(current);
                for (Direction direction : HORIZONTAL) {
                    int x = current.feet().getX() + direction.getStepX();
                    int z = current.feet().getZ() + direction.getStepZ();
                    FloorSurface.Cell next = surface.cellAtColumn(x, z).orElse(null);
                    if (next == null || connectorCells.contains(next.feet())
                            || visited.contains(next.feet()) || !connected(current, next)) {
                        continue;
                    }
                    visited.add(next.feet());
                    queue.addLast(next);
                }
            }
            result.add(new Component(componentCells));
        }

        result.sort(Comparator.comparingInt(Component::minX)
                .thenComparingInt(Component::minZ)
                .thenComparingInt(Component::maxX)
                .thenComparingInt(Component::maxZ));
        return List.copyOf(result);
    }

    private static boolean connected(FloorSurface.Cell first, FloorSurface.Cell second) {
        int dx = Math.abs(first.feet().getX() - second.feet().getX());
        int dz = Math.abs(first.feet().getZ() - second.feet().getZ());
        return dx + dz == 1 && Math.abs(first.surfaceY() - second.surfaceY()) <= MAX_STEP_HEIGHT;
    }

    static Component owner(Collection<Component> adjacent) {
        return adjacent.stream().min(OWNER_ORDER).orElse(null);
    }

    static List<Component> adjacent(BlockPos floorCell, Collection<Component> components) {
        List<Component> result = new ArrayList<>();
        for (Component component : components) {
            for (Direction direction : HORIZONTAL) {
                if (component.containsColumn(
                        floorCell.getX() + direction.getStepX(),
                        floorCell.getZ() + direction.getStepZ())) {
                    result.add(component);
                    break;
                }
            }
        }
        return List.copyOf(result);
    }

    static Component select(BlockPos source, FloorSurface surface, List<Component> components) {
        for (Component component : components) {
            if (component.containsColumn(source.getX(), source.getZ())) return component;
        }

        BlockPos sourceCell = surface.cellAtColumn(source.getX(), source.getZ())
                .map(FloorSurface.Cell::feet)
                .orElse(new BlockPos(source.getX(), surface.anchorY(), source.getZ()));
        List<Component> adjacent = adjacent(sourceCell, components);
        boolean connectorColumn = surface.connectorByFloorCell().keySet().stream()
                .anyMatch(cell -> cell.getX() == source.getX() && cell.getZ() == source.getZ());
        if (connectorColumn) return owner(adjacent);
        return adjacent.stream()
                .min(Comparator.comparingInt(Component::minX).thenComparingInt(Component::minZ))
                .orElse(null);
    }

    record Component(Set<FloorSurface.Cell> cells) {
        Component { cells = Set.copyOf(cells); }
        int area() { return cells.size(); }
        int minX() { return cells.stream().mapToInt(cell -> cell.feet().getX()).min().orElse(0); }
        int minZ() { return cells.stream().mapToInt(cell -> cell.feet().getZ()).min().orElse(0); }
        int maxX() { return cells.stream().mapToInt(cell -> cell.feet().getX()).max().orElse(0); }
        int maxZ() { return cells.stream().mapToInt(cell -> cell.feet().getZ()).max().orElse(0); }
        boolean containsColumn(int x, int z) {
            return cells.stream().anyMatch(cell -> cell.feet().getX() == x && cell.feet().getZ() == z);
        }
        Set<BlockPos> projectedCells(int anchorY) {
            return cells.stream().map(cell -> new BlockPos(
                    cell.feet().getX(), anchorY, cell.feet().getZ()))
                    .collect(Collectors.toUnmodifiableSet());
        }
    }
}
```

Keep traversal loops imperative as shown; streams are limited to deterministic sorting/projection, not graph expansion.

- [ ] **Step 4: Run the partitioner tests**

Run:

```powershell
./gradlew.bat :common:test --tests net.conczin.mca.server.world.data.FloorSurfacePartitionerTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git.exe add common/src/main/java/net/conczin/mca/server/world/data/FloorSurfacePartitioner.java common/src/test/java/net/conczin/mca/server/world/data/FloorSurfacePartitionerTest.java
git.exe diff --cached --check
git.exe commit -m "refactor: partition rooms from exact floor surfaces"
```

---

### Task 3: Replace connector block-property ownership with floor-surface association

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/BuildingRoomScannerOwnerTest.java`

**Interfaces:**
- Consumes: connector `BlockState`/position and exact `FloorSurface.Cell` positions.
- Produces:
  - `BlockPos normalize(BlockPos pos, BlockState state)`; an upper door half normalizes to `pos.below()`.
  - `Map<BlockPos, BlockPos> associatedFloorCells(Level world, Collection<BlockPos> connectorPositions, Collection<FloorSurface.Cell> surfaceCells)`; key is the actual connector floor-cell coordinate, value is normalized connector block position.
- Deletes: `boolean ownsFloorCell(BlockState state)` and the dirty checkpoint test asserting doors never own cells.

- [ ] **Step 1: Replace the rejected door test with RED normalization/association tests**

Use:

```java
@Test
void upperDoorHalfNormalizesToOneLowerConnector() {
    BlockState upper = Blocks.OAK_DOOR.defaultBlockState()
            .setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);

    assertEquals(new BlockPos(4, 64, 7),
            StructureConnector.normalize(new BlockPos(4, 65, 7), upper));
}

@Test
void ownerComparatorStillChoosesLargeInteriorForDoorCell() {
    var outside = new FloorSurfacePartitioner.Component(Set.of(
            new FloorSurface.Cell(new BlockPos(-1, 64, 0), 64.0D, 68)));
    var inside = new FloorSurfacePartitioner.Component(Set.of(
            new FloorSurface.Cell(new BlockPos(1, 64, 0), 64.0D, 68),
            new FloorSurface.Cell(new BlockPos(2, 64, 0), 64.0D, 68)));
    assertEquals(inside, FloorSurfacePartitioner.owner(List.of(outside, inside)));
}
```

Delete `doorsAreTraversalOnlyAndNeverOwnFloorCells()`.

- [ ] **Step 2: Verify RED**

Run:

```powershell
./gradlew.bat :common:test --tests net.conczin.mca.server.world.data.BuildingRoomScannerOwnerTest
```

Expected: FAIL because `normalize(...)` is missing and the test no longer accepts the old door exception.

- [ ] **Step 3: Implement connector normalization and exact-surface projection**

Implement:

```java
static BlockPos normalize(BlockPos pos, BlockState state) {
    if (state.getBlock() instanceof DoorBlock
            && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
        return pos.below();
    }
    return pos;
}
```

Rewrite `associatedFloorCells(...)` so it iterates exact `FloorSurface.Cell` landings instead of `BuildingFloorRegionDetector.FloorCell`. A horizontal door/gate at `(cx, *, cz)` associates `(cx, landingY, cz)` whenever an adjacent surface cell is at the same/one-step Y. Vertical connector association continues to use `handoffs(...)`, but returns only cells belonging to the selected `FloorSurface`.

Remove `ownsFloorCell`; doors are no longer excluded. The later Room owner stage decides which adjacent Room receives the connector shading cell.

- [ ] **Step 4: Run connector/owner tests**

```powershell
./gradlew.bat :common:test --tests net.conczin.mca.server.world.data.BuildingRoomScannerOwnerTest --tests net.conczin.mca.server.world.data.FloorSurfacePartitionerTest
```

Expected: PASS.

- [ ] **Step 5: Commit**

```powershell
git.exe add common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java common/src/test/java/net/conczin/mca/server/world/data/BuildingRoomScannerOwnerTest.java
git.exe diff --cached --check
git.exe commit -m "fix: associate connectors with exact floor surfaces"
```

---

### Task 4: Build the Minecraft-facing selected-floor scanner

**Files:**
- Create: `common/src/main/java/net/conczin/mca/server/world/data/FloorCeilingResolver.java`
- Create: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`
- Create: `common/src/test/java/net/conczin/mca/server/world/data/SelectedFloorScannerTest.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java`

**Interfaces:**
- Consumes: `Level`, exact selected feet/scan seed, `maxBuildingSize`, `maxBuildingRadius`.
- Produces:
  - `new FloorCeilingResolver(Level world).ceilingY(BlockPos feet)` returning the first valid physical roof/ceiling above that exact column, or empty when the column is uncovered to build height.
  - `SelectedFloorScanner.Result(Building.validationResult result, FloorSurface surface, BlockPos min, BlockPos max)`
  - `Optional<FloorSurface.Cell> inspectSurfaceCell(Level world, BlockPos feet, FloorCeilingResolver ceilings)`
  - single-floor traversal only; vertical connectors are recorded but never followed to another storey.

- [ ] **Step 1: Write RED tests for world-independent selected-floor decisions without faking `Level`**

Put the movement/band decisions in package-private static helpers and test:

```java
@Test
void stepDecisionUsesVanillaJumpThreshold() {
    assertTrue(SelectedFloorScanner.canStep(64.0D, 65.0D));
    assertFalse(SelectedFloorScanner.canStep(64.0D, 65.25D));
}

@Test
void selectedFloorBandDoesNotClimbIntoAnotherStorey() {
    assertTrue(SelectedFloorScanner.withinSelectedFloorBand(64, 66));
    assertFalse(SelectedFloorScanner.withinSelectedFloorBand(64, 67));
}
```

These tests intentionally cover only pure decisions. Roofs, leaves, stairs/slabs, shifted/overhanging roofs, doors, and exterior exposure are covered by real Minecraft GameTests in Task 10 rather than a fake `Level`.

- [ ] **Step 2: Verify RED**

```powershell
./gradlew.bat :common:test --tests net.conczin.mca.server.world.data.SelectedFloorScannerTest
```

Expected: FAIL because `SelectedFloorScanner` does not exist.

- [ ] **Step 3: Implement the cached roof/ceiling resolver**

Create `FloorCeilingResolver.java` with this contract:

```java
final class FloorCeilingResolver {
    private final Level world;
    private final Map<BlockPos, OptionalInt> cache = new HashMap<>();

    FloorCeilingResolver(Level world) {
        this.world = world;
    }

    OptionalInt ceilingY(BlockPos feet) {
        return cache.computeIfAbsent(feet.immutable(), this::resolve);
    }

    private OptionalInt resolve(BlockPos feet) {
        for (int y = feet.getY() + 1; y < world.getMaxBuildHeight(); y++) {
            BlockPos probe = new BlockPos(feet.getX(), y, feet.getZ());
            BlockState state = world.getBlockState(probe);
            if (isRoofBlock(world, probe, state)) {
                return OptionalInt.of(y);
            }
        }
        return OptionalInt.empty();
    }

    private static boolean isRoofBlock(Level world, BlockPos pos, BlockState state) {
        return state.getFluidState().isEmpty()
                && !state.is(BlockTags.LEAVES)
                && !state.getCollisionShape(world, pos).isEmpty();
    }
}
```

This deliberately preserves the old leaf rule correctly: a leaf encountered above the player is **not** accepted as the roof, but the probe continues above it and may still find a real roof later. Leaves remain collision blocks for movement; the scanner never pretends the leaf itself is air.

There is no fixed `ROOF_SEARCH`. A 30-block or 80-block ceiling is valid as long as a real roof is found before `getMaxBuildHeight()`.

- [ ] **Step 4: Implement selected-floor BFS over exact surface cells**

Use this shape:

```java
static Result scan(Level world, BlockPos seed, int maxSize, int maxRadius) {
    FloorCeilingResolver ceilings = new FloorCeilingResolver(world);
    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    Set<BlockPos> visited = new HashSet<>();
    LinkedHashMap<BlockPos, FloorSurface.Cell> cells = new LinkedHashMap<>();
    LinkedHashSet<BlockPos> connectors = new LinkedHashSet<>();

    FloorSurface.Cell seedCell = inspectSurfaceCell(world, seed, ceilings).orElse(null);
    if (seedCell == null || reachesExterior(world, seed, ceilings, maxRadius, null)) {
        return Result.failure(Building.validationResult.NOT_IN_BUILDING, seed);
    }

    queue.addLast(seed.immutable());
    visited.add(seed.immutable());

    while (!queue.isEmpty()) {
        BlockPos current = queue.removeFirst();
        FloorSurface.Cell currentCell = inspectSurfaceCell(world, current, ceilings).orElse(null);
        if (currentCell == null) continue;
        cells.put(currentCell.feet(), currentCell);

        for (Direction direction : HORIZONTAL) {
            enqueueHorizontalLanding(world, seed, currentCell, direction, maxRadius,
                    visited, queue, connectors, ceilings);
        }
        collectConnectorsAtSelectedFloor(world, seed.getY(), current, connectors);
        if (cells.size() + connectors.size() > maxSize) {
            return Result.failure(Building.validationResult.BLOCK_LIMIT, seed);
        }
    }

    Map<BlockPos, BlockPos> associated = StructureConnector.associatedFloorCells(
            world, connectors, cells.values());
    FloorSurface surface = new FloorSurface(Set.copyOf(cells.values()), associated);
    return success(seed, surface);
}
```

The helper contracts used above are part of this task:

```java
static boolean canStep(double fromSurfaceY, double toSurfaceY) {
    return Math.abs(toSurfaceY - fromSurfaceY) <= 1.125D;
}

static boolean withinSelectedFloorBand(int seedY, int candidateY) {
    return Math.abs(candidateY - seedY) <= BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE;
}

private static Optional<FloorSurface.Cell> inspectSurfaceCell(
        Level world, BlockPos feet, FloorCeilingResolver ceilings) {
    if (!isFeetAndHeadOpen(world, feet) || !isSupported(world, feet)) return Optional.empty();
    OptionalInt ceiling = ceilings.ceilingY(feet);
    if (ceiling.isEmpty()) return Optional.empty();
    double surfaceY = WalkNodeEvaluator.getFloorLevel(world, feet);
    return Optional.of(new FloorSurface.Cell(feet, surfaceY, ceiling.getAsInt()));
}
```

`enqueueHorizontalLanding(...)` checks candidate `dy` in `0, +1, -1`, requires `withinSelectedFloorBand(seed.getY(), candidate.getY())`, requires `canStep(current.surfaceY(), candidate.surfaceY())`, and never queues a vertical handoff. If the horizontal boundary is a door/gate, normalize and record it, inspect the landing beyond it, and cross only when that far side is itself a valid roofed selected-floor landing that does not reach exterior when the connector is treated as the blocked boundary. `collectConnectorsAtSelectedFloor(...)` records doors/gates in the local floor band plus ladders/trapdoors touching that band without adding vertical destinations to the queue.

`reachesExterior(...)` is selected-floor scoped. It performs a horizontal/step BFS over supported feet positions only, uses the same `FloorCeilingResolver`, treats doors/gates as semantic boundaries, and returns true when an ordinary reachable path either finds an uncovered candidate or reaches `maxRadius - 1`. It never floods vertical air and never follows ladders/trapdoors to another storey.

For each ordinary surface candidate:

1. Feet/head must have empty fluid and empty collision.
2. Support below must keep the existing minimum horizontal support footprint (`width * depth >= 0.25D`).
3. `surfaceY` comes from `WalkNodeEvaluator.getFloorLevel(world, feet)`.
4. Candidate Y must satisfy `withinSelectedFloorBand(seed.getY(), candidate.getY())`; this prevents a staircase from silently turning an ordinary scan into `ADD_FLOOR`, while retaining the existing ±2 uneven-floor tolerance.
5. The exact column must have a real non-leaf physical roof somewhere below Minecraft's maximum build height; that Y becomes `Cell.ceilingY`.
6. Horizontal neighbors may resolve at `dy = 0, +1, -1` when their vanilla floor-height delta is at most `1.125D` and they remain inside the selected floor band.
7. Horizontal doors/gates are recorded as connector boundaries. Traverse to their far-side landing only when that side is a valid enclosed selected-floor side; an open exterior door therefore does not cause the outside to join the Structure.
8. Ladders/trapdoors are recorded as vertical connectors but never enqueued vertically and never trigger discovery of the other storey.
9. If an ordinary reachable side (not through a connector) reaches an uncovered supported candidate or escapes the configured radius, fail as `NOT_IN_BUILDING`/`SIZE_LIMIT` rather than silently truncating the floor.
10. `maxSize` checks discovered surface/connector footprint count, not enclosure span height or air volume.
11. Structure `min/max` derives from surface X/Z, minimum feet/support Y, and maximum resolved local ceiling Y; do not materialize a vertical `volume` set.

Why this still handles irregular walls/roofs: the **floor itself** is discovered from real supported 3D cells, while every selected-floor column resolves its own ceiling independently. A stepped, vaulted, inward-sloped, or overhanging roof can therefore move horizontally with height without requiring one global `anchorY` ceiling or a fixed 16-block search. We deliberately do not model the whole air volume because that would let an open stairwell or unfinished upper storey influence whether the current floor exists.

Keep the current small-building compatibility rule by computing the old-style examined envelope from surface/connector positions plus immediate neighbors, not from every vertical air block.

- [ ] **Step 5: Run pure scanner decision tests and compile common**

```powershell
./gradlew.bat :common:test --tests net.conczin.mca.server.world.data.SelectedFloorScannerTest
./gradlew.bat :common:compileJava
```

Expected: PASS.

- [ ] **Step 6: Commit the world adapter before wiring it into production orchestration**

```powershell
git.exe add common/src/main/java/net/conczin/mca/server/world/data/FloorCeilingResolver.java common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java common/src/test/java/net/conczin/mca/server/world/data/SelectedFloorScannerTest.java common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java
git.exe diff --cached --check
git.exe commit -m "refactor: discover one exact physical floor"
```

---

### Task 5: Route `StructureScanner` through the selected-floor model and delete persistence-aware traversal

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java`
- Replace: `common/src/test/java/net/conczin/mca/server/world/data/StructureScannerAttachmentBoundaryTest.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/BuildingFloorRegionDetector.java`

**Interfaces:**
- Consumes: `SelectedFloorScanner.Result` from Task 4.
- Produces:
  - `StructureScanner.Result(..., List<StructureFloor> floors, FloorSurface surface)` where successful scans contain exactly one fresh floor.
  - `scanNewStructure(...)`, `scanReportedStructure(...)`, and `scanPlannedStructure(...)` remain public package-facing entry points.
  - Add `scanExistingFloor(Level world, Structure structure, StructureFloor floor, BlockPos source, Collection<Structure> existing)` for `ADD_ROOM`, `UPDATE_ROOM`, diagnostics, and full rescans.
- Deletes: `ROOF_SEARCH`, `PersistedFloorBoundary`, multi-storey vertical traversal, vertical-air `volume`, `toFloors(...)`, and fresh `BuildingFloorRegionDetector.detect(...)` use.

- [ ] **Step 1: Replace boundary tests with RED single-floor result tests**

Delete assertions against `PersistedFloorBoundary` and add tests around the pure result/projection helper:

```java
@Test
void oneSelectedSurfaceProducesExactlyOnePersistedFloor() {
    FloorSurface surface = surfaceAt(74, Set.of(
            new BlockPos(0, 74, 0), new BlockPos(1, 74, 0),
            new BlockPos(0, 74, 1), new BlockPos(1, 74, 1)));

    StructureFloor floor = StructureScanner.persistedFloor(surface);

    assertEquals(74, floor.anchorY());
    assertEquals(4, floor.area());
}

@Test
void persistedOtherStoreyIsNotAWorldTraversalBoundaryConcept() {
    assertFalse(Arrays.stream(StructureScanner.class.getDeclaredClasses())
            .anyMatch(type -> type.getSimpleName().equals("PersistedFloorBoundary")));
}

private static FloorSurface surfaceAt(int y, Set<BlockPos> positions) {
    LinkedHashSet<FloorSurface.Cell> cells = new LinkedHashSet<>();
    for (BlockPos pos : positions) {
        cells.add(new FloorSurface.Cell(pos, pos.getY(), pos.getY() + 4));
    }
    return new FloorSurface(cells, Map.of());
}
```

- [ ] **Step 2: Verify RED**

```powershell
./gradlew.bat :common:test --tests net.conczin.mca.server.world.data.StructureScannerAttachmentBoundaryTest
```

Expected: FAIL while `PersistedFloorBoundary` still exists and `persistedFloor(...)` is absent.

- [ ] **Step 3: Replace the old scanner core with thin orchestration**

Successful surface conversion is exactly one floor:

```java
static StructureFloor persistedFloor(FloorSurface surface) {
    return new StructureFloor(0, surface.anchorY(), surface.maxCeilingY(), surface.persistedRegion());
}
```

Extend `StructureScanner.Result`:

```java
record Result(Building.validationResult result,
              BlockPos source,
              BlockPos min,
              BlockPos max,
              List<StructureFloor> floors,
              FloorSurface surface) {
    Result {
        floors = List.copyOf(floors);
    }
}
```

Failure results use an empty `FloorSurface(Set.of(), Map.of())`; successful results use `List.of(persistedFloor(surface))`.

`scanPlannedStructure(...)` calls the same selected-floor scanner at `plan.scanSeed()` with **no** persisted-floor boundary. `ADD_FLOOR`/`ADD_BASEMENT` therefore cannot climb to another storey because `SelectedFloorScanner` never traverses vertically.

Overlap checks happen only after the fresh candidate floor is built. Ignore the current Structure ID only for `scanExistingFloor`/rescan.

- [ ] **Step 4: Remove obsolete fresh multi-storey detection code**

Delete from `StructureScanner`:

```text
ROOF_SEARCH
PersistedFloorBoundary
addVerticalInteriorColumn
addObstacleInteriorColumn
toFloors
resolveTopFloorCeiling
enqueueConnectorHandoffs vertical traversal
vertical UP/DOWN queue traversal
vertical-air volume size accounting
```

In `BuildingFloorRegionDetector`, keep `FLOOR_CLUSTER_TOLERANCE` only while legacy persistence/interaction callers still reference it. Delete `detect(...)`, `HeightSlice`, and `MutableBand` once `rg "BuildingFloorRegionDetector.detect"` returns no production callers.

- [ ] **Step 5: Run scanner and floor-system tests**

```powershell
./gradlew.bat :common:test --tests net.conczin.mca.server.world.data.StructureScannerAttachmentBoundaryTest --tests net.conczin.mca.server.world.data.VillageFloorSystemTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git.exe add common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java common/src/main/java/net/conczin/mca/server/world/data/BuildingFloorRegionDetector.java common/src/test/java/net/conczin/mca/server/world/data/StructureScannerAttachmentBoundaryTest.java
git.exe diff --cached --check
git.exe commit -m "refactor: make structure scans single-floor"
```

---

### Task 6: Make `BuildingRoomScanner` consume exact `FloorSurface` topology

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/BuildingRoomScannerOwnerTest.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/FloorSurfacePartitionerTest.java`

**Interfaces:**
- Consumes: `FloorSurface` and `FloorSurfacePartitioner.Component`.
- Produces:
  - `BuildingRoomScanner.scan(Level, BlockPos, Set<BlockPos>, int, StructureFloor, FloorSurface)`
  - `BuildingRoomScanner.partition(Level, BlockPos, int, StructureFloor, FloorSurface)` materializes
    every fresh topology component with no persistence identity assumptions; `Update Room` filters
    this list to the selected Room lineage before reconciliation.
  - `BuildingRoomScanner.Result` remains the adapter consumed by `Building.applyRoomScan(...)`.
- Deletes: `PassageDecision.FUNCTIONAL_POI`, `hasFunctionalPoiObstacle`, `attachFunctionalPoiCells`, BuildingTypes topology import, stale `partitionCells`/gap repair once no tests require it.

- [ ] **Step 1: Add RED tests proving POI/furniture classification no longer participates in partitioning**

Update pure partition tests so topology depends only on exact surface/connector geometry. Add:

```java
@Test
void unrelatedPoiMetadataCannotConnectComponents() {
    FloorSurface surface = surface(Set.of(cell(0, 64, 0), cell(2, 64, 0)), Map.of());
    assertEquals(2, FloorSurfacePartitioner.partition(surface).size());
}
```

In `BuildingRoomScannerOwnerTest`, assert a door connector cell is assigned to the deterministic Room owner rather than rejected by block type.

- [ ] **Step 2: Verify RED against the old `BuildingRoomScanner` path**

```powershell
./gradlew.bat :common:test --tests net.conczin.mca.server.world.data.BuildingRoomScannerOwnerTest --tests net.conczin.mca.server.world.data.FloorSurfacePartitionerTest
```

Expected: FAIL until `BuildingRoomScanner` stops calling `ownsFloorCell`/BuildingTypes topology.

- [ ] **Step 3: Rewrite `BuildingRoomScanner` as a thin materialization adapter**

The main path becomes:

```java
static Result scan(Level world, BlockPos source, Set<BlockPos> blocked,
                   int maxSize, StructureFloor floor, FloorSurface surface) {
    List<FloorSurfacePartitioner.Component> components = FloorSurfacePartitioner.partition(surface);
    FloorSurfacePartitioner.Component selected = FloorSurfacePartitioner.select(source, surface, components);
    return selected == null
            ? Result.failure(Status.TOO_SMALL, source)
            : materializeComponent(world, source, blocked, maxSize, floor, surface, components, selected);
}
```

For materialization:

1. Start footprint from `selected.projectedCells(floor.anchorY())`.
2. For each `surface.connectorByFloorCell()` entry, find adjacent components by X/Z.
3. If `selected.equals(FloorSurfacePartitioner.owner(adjacent))`, project that connector floor cell to `floor.anchorY()` and add it to footprint.
4. Connector cells never participate in `partition(...)`, so ownership cannot merge Rooms.
5. Apply existing blocked-overlap, `MIN_INTERIOR_AREA`, and `maxSize` validation to the final footprint.

`partition(...)` uses the same `FloorSurfacePartitioner.partition(surface)` result and materializes
each component with an empty blocked set. It does not decide which existing Room owns a component;
that is persistence/reconciliation policy owned by `VillageManager`/`RegisteredRoomReconciler`.

Remove `BuildingTypes`, `FUNCTIONAL_POI`, `attachFunctionalPoiCells`, and `hasFunctionalPoiObstacle` from this class.

- [ ] **Step 4: Route fresh surfaces through `VillageManager` for add-building/add-room/manual attachment**

For a new/pending Structure, use `StructureScanner.Result.surface()` when calling `BuildingRoomScanner.scan(...)`.

For `analyzeRoom(...)` inside an existing Structure:

```java
StructureFloor floor = resolveRoomFloor(village, structure, pos, -1);
StructureScanner.Result fresh = StructureScanner.scanExistingFloor(
        world, structure, floor, pos, village.getStructures().values());
if (fresh.result() != Building.validationResult.SUCCESS) return failedRoom(fresh.result(), pos, village);
return scanResolvedRoom(village, structure, pos, -1, floor,
        fresh.surface(), registeredRoomCells(village, structure.getId(), floor.id(), -1));
```

Do not persist the Structure refresh merely for an `ADD_ROOM` analysis. Normal `Update Room` also
does not replace the whole floor; Task 8 only grows the persisted floor envelope when the selected
Room itself expands outside it.

- [ ] **Step 5: Run Room-scanner and common tests**

```powershell
./gradlew.bat :common:test --tests net.conczin.mca.server.world.data.BuildingRoomScannerOwnerTest --tests net.conczin.mca.server.world.data.FloorSurfacePartitionerTest
./gradlew.bat :common:test
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git.exe add common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java common/src/test/java/net/conczin/mca/server/world/data/BuildingRoomScannerOwnerTest.java common/src/test/java/net/conczin/mca/server/world/data/FloorSurfacePartitionerTest.java
git.exe diff --cached --check
git.exe commit -m "refactor: partition rooms from fresh floor surfaces"
```

---

### Task 7: Collect Room POI evidence from exact interior and perimeter geometry

**Files:**
- Create: `common/src/main/java/net/conczin/mca/server/world/data/RoomPoiEvidence.java`
- Create: `common/src/test/java/net/conczin/mca/server/world/data/RoomPoiEvidenceTest.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java`

**Interfaces:**
- Consumes: one exact `FloorSurface`, one selected `FloorSurfacePartitioner.Component`, and connector footprint ownership.
- Produces: `Set<BlockPos> RoomPoiEvidence.candidates(FloorSurface surface, FloorSurfacePartitioner.Component component, Set<BlockPos> ownedConnectorCells)`.
- `Building.applyRoomScan(...)` continues filtering returned candidates through `recordBuildingBlock(world, pos)`; no BuildingType matching moves into the geometry layer.

- [ ] **Step 1: Write RED perimeter evidence tests**

Use:

```java
@Test
void perimeterIncludesWallColumnWithoutAddingItToRoomFootprint() {
    FloorSurface.Cell interior = new FloorSurface.Cell(new BlockPos(1, 64, 1), 64.0D, 68);
    FloorSurface surface = new FloorSurface(Set.of(interior), Map.of());
    var component = new FloorSurfacePartitioner.Component(Set.of(interior));

    Set<BlockPos> candidates = RoomPoiEvidence.candidates(surface, component, Set.of());

    assertTrue(candidates.contains(new BlockPos(0, 65, 1)));
    assertTrue(candidates.contains(new BlockPos(1, 63, 1)));
    assertFalse(component.projectedCells(surface.anchorY()).contains(new BlockPos(0, 64, 1)));
}

@Test
void unevenCellsUseTheirOwnLocalVerticalEvidenceRange() {
    FloorSurface.Cell low = new FloorSurface.Cell(new BlockPos(0, 64, 0), 64.0D, 68);
    FloorSurface.Cell high = new FloorSurface.Cell(new BlockPos(1, 66, 0), 66.0D, 72);
    var surface = new FloorSurface(Set.of(low, high), Map.of());
    var component = new FloorSurfacePartitioner.Component(Set.of(low, high));

    Set<BlockPos> candidates = RoomPoiEvidence.candidates(surface, component, Set.of());
    assertTrue(candidates.contains(new BlockPos(1, 71, 1)));
}
```

- [ ] **Step 2: Verify RED**

```powershell
./gradlew.bat :common:test --tests net.conczin.mca.server.world.data.RoomPoiEvidenceTest
```

Expected: FAIL because `RoomPoiEvidence` does not exist.

- [ ] **Step 3: Implement evidence geometry only**

Implement:

```java
static Set<BlockPos> candidates(FloorSurface surface,
                                FloorSurfacePartitioner.Component component,
                                Set<BlockPos> ownedConnectorCells) {
    LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
    Set<Long> componentColumns = component.cells().stream()
            .map(cell -> columnKey(cell.feet().getX(), cell.feet().getZ()))
            .collect(Collectors.toSet());
    for (FloorSurface.Cell cell : component.cells()) {
        addColumn(result, cell.feet().getX(), cell.feet().getZ(),
                cell.feet().getY() - 1, cell.ceilingY());
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            int x = cell.feet().getX() + direction.getStepX();
            int z = cell.feet().getZ() + direction.getStepZ();
            if (!componentColumns.contains(columnKey(x, z))) {
                addColumn(result, x, z, cell.feet().getY() - 1, cell.ceilingY());
            }
        }
    }
    result.addAll(ownedConnectorCells);
    return Set.copyOf(result);
}

private static long columnKey(int x, int z) {
    return ((long) x << 32) ^ (z & 0xffffffffL);
}

private static void addColumn(Set<BlockPos> result, int x, int z, int minY, int ceilingY) {
    for (int y = minY; y < ceilingY; y++) {
        result.add(new BlockPos(x, y, z));
    }
}
```

`addColumn` adds every block position from inclusive `minY` through exclusive `ceilingY`. Do not inspect `BlockState` here.

- [ ] **Step 4: Replace `BuildingRoomScanner.collectPoiCells(...)` with `RoomPoiEvidence`**

`BuildingRoomScanner` passes the candidate set unchanged in its `Result`. `Building.applyRoomScan(...)` remains:

```java
blocks.clear();
for (BlockPos pos : scan.poiCells()) {
    recordBuildingBlock(world, pos);
}
```

This is the only BuildingType relevance filter.

- [ ] **Step 5: Run POI and Room tests**

```powershell
./gradlew.bat :common:test --tests net.conczin.mca.server.world.data.RoomPoiEvidenceTest --tests net.conczin.mca.server.world.data.BuildingRoomScannerOwnerTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git.exe add common/src/main/java/net/conczin/mca/server/world/data/RoomPoiEvidence.java common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java common/src/test/java/net/conczin/mca/server/world/data/RoomPoiEvidenceTest.java
git.exe diff --cached --check
git.exe commit -m "fix: collect room POIs from boundary evidence"
```

---

### Task 8: Make `Update Room` reconcile only the selected Room lineage

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Structure.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RegisteredRoomUpdate.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RegisteredRoomReconciler.java`
- Create: `common/src/test/java/net/conczin/mca/server/world/data/RegisteredRoomUpdateLineageTest.java`

**Interfaces:**
- Consumes: fresh exact selected-floor surface from `StructureScanner.scanExistingFloor(...)`, the
  selected registered Room, and every other registered Room on that floor as immutable blockers.
- Produces:
  - all fresh topology components that descend from the selected Room's old footprint;
  - `RegisteredRoomReconciler.reconcile(...)` called with `previousRooms = List.of(selectedRoom)` and
    only those lineage components;
  - the source/best-matching lineage component keeps the selected Room ID;
  - additional lineage components created by a genuine split receive new Room IDs;
  - optional minimal `Structure` floor-envelope growth when any lineage component expands beyond
    the persisted floor region; shrinking/splitting does not erase unrelated Structure geometry;
  - no pre-existing neighboring Room creation/deletion/re-ID/retyping/re-POI mutation;
  - any lineage component overlapping another pre-existing registered Room is rejected rather than
    merged into that neighbor.

- [ ] **Step 1: Write RED tests for selected-Room lineage selection and split reconciliation**

Create `RegisteredRoomUpdateLineageTest.java` with pure helpers around the detached update scope:

```java
@Test
void splitIncludesEveryFreshComponentDescendingFromSelectedRoomButNotUnrelatedComponents() {
    Building selected = room(12, 0, 4);
    Building left = room(-1, 0, 1);
    Building right = room(-1, 3, 4);
    Building unrelated = room(-1, 10, 11);

    List<Building> lineage = VillageManager.updateLineage(
            selected, List.of(left, right, unrelated), List.of()).orElseThrow();

    assertEquals(List.of(left, right), lineage);
}

@Test
void lineageRejectsFreshComponentThatAlsoConsumesRegisteredNeighbor() {
    Building selected = room(12, 0, 4);
    Building mergedFresh = room(-1, 0, 7);
    Building neighbor = room(20, 5, 7);

    assertTrue(VillageManager.updateLineage(
            selected, List.of(mergedFresh), List.of(neighbor)).isEmpty());
}

@Test
void splitKeepsOldIdOnSourceComponentAndCreatesAnotherRoom() {
    Building previous = room(12, 0, 4);
    Building left = room(-1, 0, 1);
    Building right = room(-1, 3, 4);

    RegisteredRoomReconciler.Result result = RegisteredRoomReconciler.reconcile(
            new BlockPos(0, 64, 0), 12, -1,
            List.of(previous), List.of(left, right)).orElseThrow();

    assertEquals(12, result.playerComponent().getId() < 0
            ? result.assignments().stream()
                    .filter(assignment -> assignment.component() == result.playerComponent())
                    .findFirst().orElseThrow().roomId()
            : result.playerComponent().getId());
    assertEquals(1, result.assignments().stream()
            .filter(RegisteredRoomReconciler.Assignment::createsRoom).count());
}

private static Building room(int id, int minX, int maxX) {
    BuildingFloorRegion footprint = BuildingFloorRegion.fromFootprint(64,
            java.util.stream.IntStream.rangeClosed(minX, maxX)
                    .mapToObj(x -> new BlockPos(x, 64, 0))
                    .toList());
    Building room = new Building(new BlockPos(minX, 64, 0));
    room.setId(id);
    room.setStructureId(1);
    room.setFloorId(0);
    room.setGeometry(new BlockPos(minX, 64, 0), new BlockPos(maxX, 68, 0),
            maxX - minX + 1, footprint);
    return room;
}
```

- [ ] **Step 2: Verify RED**

```powershell
./gradlew.bat :common:test --tests net.conczin.mca.server.world.data.RegisteredRoomUpdateLineageTest
```

Expected: lineage tests FAIL until `updateLineage(...)` exists. The reconciler split assertion may
already pass; it locks in the useful existing identity behavior that this redesign must preserve.

- [ ] **Step 3: Add minimal Structure-floor containment growth**

Do **not** replace the whole persisted floor during `Update Room`. Add a helper that only grows the
existing floor region when a reconciled selected-lineage Room now contains cells outside it:

```java
boolean ensureFloorContains(int floorId, BuildingFloorRegion roomRegion, int roomCeilingY) {
    StructureFloor existing = floors.get(floorId);
    if (existing == null || existing.region() == null || roomRegion == null) return false;
    LinkedHashSet<BlockPos> union = new LinkedHashSet<>(existing.region().cells());
    union.addAll(roomRegion.cells());
    BuildingFloorRegion expanded = BuildingFloorRegion.fromFootprint(existing.anchorY(), union);
    floors.put(floorId, new StructureFloor(floorId, existing.anchorY(),
            Math.max(existing.ceilingY(), roomCeilingY), existing.floorNumber(), expanded));
    recomputeBoundsFromFloors();
    return true;
}
```

This is deliberately monotonic for `Update Room`: shrinking/sealing/splitting the selected Room
changes the Room footprints, but does not erase Structure-floor cells that may belong to
unregistered space or another Room. Explicit Structure maintenance can later tighten the physical
envelope.

- [ ] **Step 4: Keep reconciliation assignments but scope them to one previous Room**

Extend `RegisteredRoomUpdate` with the detached Structure snapshot:

```java
Structure refreshedStructure,
```

Keep `previousRoomIds`, `assignments`, `playerComponent`, `expectedPlayerRoomId`, and
`playerMatchingTypes`, but change their normal-update contract:

```text
previousRoomIds == List.of(expectedPlayerRoomId)
assignments == reconciliation results for selected Room lineage only
```

This is what permits a split to create additional Rooms while preventing unrelated registered
Rooms from entering the reconciliation transaction.

- [ ] **Step 5: Fresh-scan the floor, then filter to the selected Room lineage**

In `analyzeRegisteredRoomUpdate(...)`:

```java
StructureFloor persistedFloor = structure.getFloor(expected.getFloorId()).orElse(null);
StructureScanner.Result fresh = StructureScanner.scanExistingFloor(
        world, structure, persistedFloor, pos, village.getStructures().values());
if (fresh.result() != Building.validationResult.SUCCESS) {
    return RegisteredRoomUpdate.failure(fresh.result(), pos, village);
}

List<Building> freshComponents = BuildingRoomScanner.partition(
                world, pos, Config.getInstance().maxBuildingSize,
                persistedFloor, fresh.surface()).stream()
        .map(geometry -> roomResultFromGeometry(
                village, structure, persistedFloor, geometry, -1))
        .filter(scan -> scan.result() == Building.validationResult.SUCCESS)
        .map(BuildingScanResult::building)
        .toList();

List<Building> otherRooms = village.getRooms()
        .filter(room -> room.getStructureId() == structure.getId())
        .filter(room -> room.getFloorId() == persistedFloor.id())
        .filter(room -> room.getId() != expected.getId())
        .toList();

List<Building> lineage = updateLineage(expected, freshComponents, otherRooms).orElse(null);
if (lineage == null || lineage.isEmpty()) {
    return RegisteredRoomUpdate.failure(Building.validationResult.OVERLAP, pos, village);
}
```

Implement the pure scope helper as:

```java
static Optional<List<Building>> updateLineage(Building selected,
                                              Collection<Building> freshComponents,
                                              Collection<Building> otherRooms) {
    List<Building> lineage = freshComponents.stream()
            .filter(component -> component.getFloorFootprintIntersectionArea(selected) > 0)
            .sorted(Comparator.comparingInt((Building room) -> room.getRawPos0().getX())
                    .thenComparingInt(room -> room.getRawPos0().getZ())
                    .thenComparingInt(room -> room.getRawPos1().getX())
                    .thenComparingInt(room -> room.getRawPos1().getZ()))
            .toList();
    if (lineage.isEmpty()) return Optional.empty();
    for (Building component : lineage) {
        for (Building other : otherRooms) {
            if (component.getFloorFootprintIntersectionArea(other) > 0) return Optional.empty();
        }
    }
    return Optional.of(lineage);
}
```

This is the key scope rule:

```text
fresh component overlaps selected old Room -> lineage candidate
fresh component overlaps another old registered Room -> reject selected update
fresh component overlaps neither -> ignore it for this update
```

Then reconcile only that lineage:

```java
int mainRoomId = village.getMainRoom(structure).map(Building::getId).orElse(-1);
RegisteredRoomReconciler.Result reconciled = RegisteredRoomReconciler.reconcile(
        pos, expected.getId(), mainRoomId, List.of(expected), lineage).orElse(null);
if (reconciled == null) {
    return RegisteredRoomUpdate.failure(Building.validationResult.OVERLAP, pos, village);
}
```

`previousRoomIds` in the produced update is therefore exactly `List.of(expected.getId())`.
Additional assignments with `previous == null` represent genuine split-off Rooms and are allocated
new IDs during commit using the existing Room creation/type-resolution path.

- [ ] **Step 6: Grow the detached Structure only as required by the selected lineage**

Create `Structure refreshed = new Structure(structure.save())` and grow it for every lineage
assignment before committing anything:

```java
for (RegisteredRoomReconciler.Assignment assignment : reconciled.assignments()) {
    Building component = assignment.component();
    if (!refreshed.ensureFloorContains(
            persistedFloor.id(), component.getFloorRegions().getFirst(),
            component.getRawPos1().getY() + 1)) {
        return RegisteredRoomUpdate.failure(Building.validationResult.OVERLAP, pos, village);
    }
}
```

No neighboring Room object is copied or modified.

- [ ] **Step 7: Commit only the selected Room lineage after all validation succeeds**

In `applyRegisteredRoomUpdate(...)`, validate:

```java
if (update.refreshedStructure() == null
        || update.refreshedStructure().getId() != update.structureId()
        || !update.previousRoomIds().equals(List.of(update.expectedPlayerRoomId()))
        || update.playerComponent() == null) {
    return Building.validationResult.OVERLAP;
}
```

Remove the current check that requires `currentFloorRooms` IDs to equal `previousRoomIds`; under
lineage-scoped reconciliation `previousRoomIds` intentionally contains only the selected old Room.
Instead:

1. verify the assignment whose `previous()` is non-null still points to the current selected Room
   object;
2. collect the current other registered Rooms on that floor;
3. reject if any proposed lineage component overlaps any of those current other Rooms;
4. allocate IDs only for `assignment.createsRoom()` entries;
5. preserve the selected Room's type/forced/contribution state on its assigned component;
6. resolve normal Room types for newly created split components using the existing
   `RoomTypeResolver` path and inherit `contributesToMain` from the selected Room.

After every validation/type decision succeeds, commit the detached Structure, update the selected
existing Room, and insert any new split Rooms. Reuse the existing assignment loop rather than
inventing a second identity/type system:

```java
village.getStructures().put(update.structureId(), update.refreshedStructure());
// copy scanned geometry into assignment.previous() for the selected old Room
// insert assignment.createsRoom() components with newly allocated IDs
lastBuildingId = nextRoomId;
```

There must be no return path that can fail after live Structure/Room replacements begin. No
pre-existing neighboring Room is removed, copied, re-IDed, retyped, or given new POIs.

- [ ] **Step 8: Narrow reconciliation rather than deleting it**

Delete `BuildingRoomScanner.partitionRegistered(...)` if no explicit maintenance caller remains.
Keep `RegisteredRoomReconciler.java`, but change its class comment from "one complete registered
Floor partition" to an explicit caller-provided reconciliation scope. `Update Room` passes one
previous selected Room plus its lineage components. It may therefore create split-off Rooms, but it
must never merge/rewrite a pre-existing neighboring Room after a wall is removed.

- [ ] **Step 9: Run update-lineage tests and the full common suite**

```powershell
./gradlew.bat :common:test --tests net.conczin.mca.server.world.data.RegisteredRoomUpdateLineageTest
./gradlew.bat :common:test
```

Expected: PASS.

- [ ] **Step 10: Commit**

```powershell
git.exe add common/src/main/java/net/conczin/mca/server/world/data/Structure.java common/src/main/java/net/conczin/mca/server/world/data/RegisteredRoomUpdate.java common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java common/src/main/java/net/conczin/mca/server/world/data/RegisteredRoomReconciler.java common/src/test/java/net/conczin/mca/server/world/data/RegisteredRoomUpdateLineageTest.java
git.exe diff --cached --check
git.exe commit -m "fix: reconcile splits within selected room updates"
```

---

### Task 9: Lock manual floor/basement attachment and explicit maintenance to room-local semantics

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Structure.java`
- Delete: `common/src/main/java/net/conczin/mca/server/world/data/StructureFloorMatcher.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/VillageFloorSystemTest.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/BuildingDiagnostics.java`

**Interfaces:**
- Consumes: one-floor `StructureScanner.Result` and existing target logical-building/floor metadata.
- Produces:
  - manual attachment validates exactly one fresh destination floor;
  - `fullScan` explicitly iterates registered Rooms and invokes the same selected-lineage update path
    for each Room, rather than repartitioning/reconciling an entire floor in one transaction;
  - diagnostics inspect one selected persisted floor/Room at a time;
  - untouched floor IDs/numbers survive all single-floor refreshes.

- [ ] **Step 1: Write RED floor-system tests for single-floor attachment identity**

Extend `VillageFloorSystemTest` with:

```java
@Test
void refreshingOneFloorDoesNotRenumberManualNeighboringFloors() {
    Village village = new Village(1, null);
    Structure structure = structure(10, 77,
            new StructureFloor(3, 64, 70, -1, region(64)),
            new StructureFloor(7, 74, 80, 0, region(74)),
            new StructureFloor(9, 84, 90, 1, region(84)));
    village.registerStructure(structure, room(100, 10, 7, true));

    assertTrue(structure.ensureFloorContains(7, region(75), 82));

    assertEquals(-1, structure.getFloor(3).orElseThrow().floorNumber());
    assertEquals(0, structure.getFloor(7).orElseThrow().floorNumber());
    assertEquals(1, structure.getFloor(9).orElseThrow().floorNumber());
}

@Test
void manualAttachmentRejectsAResultContainingMoreThanOneFreshFloor() {
    StructureScanner.Result scan = new StructureScanner.Result(
            Building.validationResult.SUCCESS,
            BlockPos.ZERO,
            BlockPos.ZERO,
            new BlockPos(1, 80, 1),
            List.of(
                    new StructureFloor(0, 74, 78, region(74)),
                    new StructureFloor(1, 84, 88, region(84))),
            new FloorSurface(Set.of(
                    new FloorSurface.Cell(new BlockPos(0, 74, 0), 74.0D, 78)), Map.of()));

    assertNull(VillageManager.singleScannedFloor(scan));
}

private static BuildingFloorRegion region(int y) {
    return BuildingFloorRegion.fromFootprint(y, Set.of(
            new BlockPos(0, y, 0), new BlockPos(1, y, 0),
            new BlockPos(0, y, 1), new BlockPos(1, y, 1)));
}
```

Add a package-private helper used by production code and the test:

```java
static StructureFloor singleScannedFloor(StructureScanner.Result scan) {
    return scan != null && scan.floors().size() == 1 ? scan.floors().getFirst() : null;
}
```

Test that a `StructureScanner.Result` containing two floors returns `null`, proving a manual attachment cannot silently choose one of several discovered floors.

- [ ] **Step 2: Verify RED**

```powershell
./gradlew.bat :common:test --tests net.conczin.mca.server.world.data.VillageFloorSystemTest
```

Expected: the new multi-floor-candidate rejection test fails until attachment explicitly enforces one floor.

- [ ] **Step 3: Make manual attachment reject anything except exactly one requested fresh floor**

Immediately after `scanPlannedStructure(...)` succeeds:

```java
StructureFloor attachmentFloor = singleScannedFloor(structureScan);
if (attachmentFloor == null) {
    return failedRoom(Building.validationResult.AMBIGUOUS_STRUCTURE, source, village);
}
```

Use `attachmentFloor` in the existing `validAttachment(...)` checks for target logical building, unique nearest gap, direction, and prospective floor number. Do not add an exterior/inner connector branch.

- [ ] **Step 4: Make explicit full maintenance iterate Rooms, not repartition floors**

`fullScan(village)` is an explicit request to update everything, so it may update every registered
Room. It must still do so through the same selected-lineage Room operation rather than one
whole-floor reconciler. Snapshot live state first, then iterate Room IDs deterministically:

```java
List<Integer> roomIds = village.getRooms()
        .map(Building::getId)
        .sorted()
        .toList();
for (int roomId : roomIds) {
    Building room = village.getBuilding(roomId).orElse(null);
    if (room == null) continue;
    RegisteredRoomUpdate update = analyzeRegisteredRoomUpdate(
            village, roomId, room.getSourceBlock());
    if (update.result() != Building.validationResult.SUCCESS) {
        restoreFullScanSnapshots(...);
        return update.result();
    }
    Building.validationResult committed = applyRegisteredRoomUpdate(update, null, false);
    if (committed != Building.validationResult.SUCCESS) {
        restoreFullScanSnapshots(...);
        return committed;
    }
}
```

The snapshots must include Rooms, Structures, and `lastBuildingId` before the first update. A later
Room therefore sees earlier successfully refreshed Room footprints as blockers, which is desirable:
explicit full maintenance still never auto-merges two registered Rooms.

Delete `Structure.applyScan(...)` and `StructureFloorMatcher.java` in this task. The current repository
has exactly one `StructureFloorMatcher.match(...)` caller, inside `Structure.applyScan(...)`, and the
whole-Structure rediscovery path is being removed. Room-local updates use `ensureFloorContains(...)`,
so keeping the old multi-floor matcher would leave dead architecture beside the new path.

- [ ] **Step 5: Update diagnostics to scan/report the selected persisted floor**

Where `BuildingDiagnostics` currently invokes `StructureScanner.rescanStructure(...)`, resolve the interaction/persisted `StructureFloor` and call `scanExistingFloor(...)`. Include floor ID/anchor and the scanner result in verbose output so `NOT_IN_BUILDING`, `SIZE_LIMIT`, `BLOCK_LIMIT`, and ambiguous matching are distinguishable.

- [ ] **Step 6: Run floor-system, diagnostics compile, and full common tests**

```powershell
./gradlew.bat :common:test --tests net.conczin.mca.server.world.data.VillageFloorSystemTest
./gradlew.bat :common:test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git.exe add common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java common/src/main/java/net/conczin/mca/server/world/data/Structure.java common/src/main/java/net/conczin/mca/server/world/data/BuildingDiagnostics.java common/src/test/java/net/conczin/mca/server/world/data/VillageFloorSystemTest.java
git.exe add -u common/src/main/java/net/conczin/mca/server/world/data/StructureFloorMatcher.java
git.exe diff --cached --check
git.exe commit -m "fix: keep floor attachments explicitly single-floor"
```

---

### Task 10: Add real Minecraft GameTests for collision/enclosure behavior

**Files:**
- Create: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`
- Modify: `neoforge/build.gradle`

**Interfaces:**
- Consumes: final `StructureScanner`/`SelectedFloorScanner` APIs.
- Produces: real Minecraft regression coverage for behavior that JUnit fixtures cannot honestly emulate.

- [ ] **Step 1: Add the dedicated NeoForge GameTest server run**

Inside `neoForge.runs` in `neoforge/build.gradle`, add:

```groovy
gameTestServer {
    type.set('gameTestServer')
    gameDirectory = file('run-gametest/')
    ideName = "NeoForge GameTest Server (${project.path})"
    systemProperty('neoforge.enabledGameTestNamespaces', 'mca,minecraft')
}
```

This creates the concrete Gradle task `:neoforge:runGameTestServer`. The `minecraft` namespace is enabled only so the tests can reuse the known vanilla `bastion/blocks/air` empty-ish template already used successfully by the local 1.21.1 builder GameTests.

- [ ] **Step 2: Add a NeoForge-discovered GameTest holder**

Use NeoForge's automatic holder scan:

```java
package net.conczin.mca.server.world.data;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class FloorScannerGameTests {
    private FloorScannerGameTests() { }
}
```

Every method in this class uses:

```java
@GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 100)
```

Keeping the class in `net.conczin.mca.server.world.data` gives it package access to `StructureScanner` without making scanner internals public.

- [ ] **Step 3: Add a tall sealed-hall RED GameTest**

Build the test world with `GameTestHelper#setBlock(...)`: solid floor/walls, a roof at least 20 blocks above the selected floor, and a scan seed on the floor. Then:

```java
StructureScanner.Result result = StructureScanner.scanNewStructure(
        helper.getLevel(), helper.absolutePos(new BlockPos(4, 1, 4)), List.of());
helper.assertTrue(result.result() == Building.validationResult.SUCCESS,
        "sealed tall hall should scan without a 16-block roof cap");
helper.succeed();
```

The test must fail on the pre-redesign `ROOF_SEARCH = 16` implementation and pass on the new path.

- [ ] **Step 4: Add real collision/enclosure GameTests**

Add separate methods for:

```text
uneven cave-like floor using slabs/stairs and real collision shapes
door between two Room components
door opening to a tiny exterior apron
ladder/trapdoor present without ordinary scan climbing storeys
stepped/overhanging boundary whose wall/roof shifts horizontally with Y
wall bookshelf on the selected Room perimeter
small suspended platform that does not merge into the selected ground floor
actual uncovered breach that returns NOT_IN_BUILDING
```

Each test constructs only the blocks needed for that behavior, invokes the production scanner, asserts the specific footprint/floor/POI result, and calls `helper.succeed()`.

- [ ] **Step 5: Run the NeoForge GameTest server**

Run:

```powershell
./gradlew.bat :neoforge:runGameTestServer
```

Expected: process exits successfully with every `FloorScannerGameTests` method passing. Do not substitute `:common:test` for this step; these tests exist specifically to exercise real `Level`/`BlockState`/collision behavior.

- [ ] **Step 6: Commit GameTests**

```powershell
git.exe add neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java neoforge/build.gradle
git.exe diff --cached --check
git.exe commit -m "test: cover floor scanning in real minecraft worlds"
```

---

### Task 11: Persistence/reload regression, dead-code removal, and final verification

**Files:**
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/RoomDFUTest.java`
- Modify/delete as indicated by `rg`: `BuildingFloorRegionDetector.java`, `StructureFloorMatcher.java`, stale scanner helpers/tests.
- No save-format production migration files should be added.

**Interfaces:**
- Consumes: final scanner/persistence implementation from Tasks 1-10.
- Produces: proof that existing save shape still round-trips Room footprints, POIs, Structure/floor identity, and logical-building identity; no superseded scanner mechanism remains live.

- [ ] **Step 1: Add a reload RED regression using existing NBT save/load APIs**

Extend the existing save/load test style to create:

```java
BuildingFloorRegion footprint = BuildingFloorRegion.fromFootprint(64, Set.of(
        new BlockPos(0, 64, 0), new BlockPos(1, 64, 0),
        new BlockPos(0, 64, 1), new BlockPos(1, 64, 1)));
StructureFloor floor = new StructureFloor(0, 64, 70, 0, footprint);
Structure structure = new Structure(20, new BlockPos(0, 64, 0),
        new BlockPos(0, 64, 0), new BlockPos(1, 69, 1), List.of(floor));
structure.setLogicalBuildingId(77);

Building room = new Building(new BlockPos(0, 64, 0));
room.setId(10);
room.setStructureId(20);
room.setFloorId(0);
room.setType("house");
room.setGeometry(new BlockPos(0, 64, 0), new BlockPos(1, 69, 1), 4, footprint);
room.addBlock(Blocks.BELL, new BlockPos(0, 65, 0));

Structure reloadedStructure = new Structure(structure.save());
Building reloadedRoom = new Building(room.save());

assertEquals(structure.getId(), reloadedStructure.getId());
assertEquals(structure.getLogicalBuildingId(), reloadedStructure.getLogicalBuildingId());
assertEquals(structure.getFloor(0).orElseThrow().floorNumber(),
        reloadedStructure.getFloor(0).orElseThrow().floorNumber());
assertEquals(room.getFloorRegions(), reloadedRoom.getFloorRegions());
assertEquals(room.getStructureId(), reloadedRoom.getStructureId());
assertEquals(room.getFloorId(), reloadedRoom.getFloorId());
assertEquals(room.getBlocks(), reloadedRoom.getBlocks());
```

Add `import net.minecraft.world.level.block.Blocks;` to `RoomDFUTest.java` for this regression.

- [ ] **Step 2: Run reload tests**

```powershell
./gradlew.bat :common:test --tests net.conczin.mca.server.world.data.RoomDFUTest
```

Expected: PASS without introducing a new DFU/NBT format.

- [ ] **Step 3: Remove superseded mechanisms proven unused**

Run these searches separately and delete only code with zero live production callers:

```powershell
rg.exe -n "PersistedFloorBoundary|ROOF_SEARCH|FUNCTIONAL_POI|attachFunctionalPoiCells|hasFunctionalPoiObstacle|ownsFloorCell|BuildingFloorRegionDetector\.detect" common/src/main/java common/src/test/java
rg.exe -n "MAX_PARTITION_GAP|bridgesCanonicalGap|partitionCells\(" common/src/main/java/net/conczin/mca/server/world/data
```

Expected final result: no references to the first group. The second group remains only if a focused real Minecraft GameTest demonstrates it is necessary for actual collision semantics; otherwise remove it.

- [ ] **Step 4: Run all verification gates**

Run:

```powershell
./gradlew.bat :common:test
./gradlew.bat :common:compileJava
./gradlew.bat :fabric:compileJava
./gradlew.bat :neoforge:compileJava
git.exe diff --check
git.exe status --short
```

Then run the NeoForge GameTest task identified in Task 10 once more.

Expected:

```text
all JUnit tests PASS
Fabric compile PASS
NeoForge compile PASS
all scanner GameTests PASS
git diff --check emits no errors
only intentional implementation/doc changes appear in status
common/logs remains uncommitted
```

- [ ] **Step 5: Exercise the mandatory runtime acceptance set**

In a dev world verify these exact user-facing scenarios:

```text
1. sealed room with roof >16 blocks scans successfully
2. stepped/sloped/overhanging enclosure scans the selected floor correctly
3. increasing only ceiling height does not cause BLOCK_LIMIT
4. cave-wall bookshelf is counted as a POI
5. one cave Room can cross several real Y values
6. Update Room expands after excavation and Blueprint outline follows
7. adding a divider then Update Room keeps the old ID on the source/best component and creates a new split-off Room
8. removing a divider into an already registered neighboring Room is rejected instead of merging/re-IDing that neighbor
9. POI-only wall change updates POIs without floor-area change
10. exterior door owns one interior shading cell with no outer-door branch
11. interior door uses the same ownership rule while splitting topology
12. ADD_FLOOR/ADD_BASEMENT adds only the explicitly selected destination floor
13. ambiguous ownership refuses without partial mutation
14. save/reload preserves identities, footprints, POIs, floor numbers, and logical building
```

- [ ] **Step 6: Commit final cleanup/regression coverage**

```powershell
git.exe add common/src/main common/src/test
git.exe diff --cached --check
git.exe commit -m "test: lock floor scanner persistence and cleanup"
```

Do not use `git add -A`; stage the scanner/common paths deliberately and review `git status --short` before committing.

---

## Implementation Order Rationale

1. Checkpoint first so none of the current experimental work or accepted design context is lost.
2. Introduce exact geometry as a pure immutable model before changing Minecraft traversal.
3. Prove Room topology/ownership in pure tests before coupling it to `Level`.
4. Build the real Minecraft adapter separately so collision/enclosure bugs are not hidden inside Room logic.
5. Switch Structure discovery to one-floor semantics and delete persisted traversal boundaries.
6. Switch Room topology to the exact fresh model.
7. Add POI perimeter evidence only after topology is stable.
8. Make `Update Room` consume the same fresh pipeline and commit Structure/Rooms atomically.
9. Lock manual floor/basement/full-rescan orchestration to explicit single-floor behavior.
10. Prove the Minecraft-dependent assumptions in actual GameTests.
11. Finish with save/reload proof, deletion of superseded mechanisms, loader compiles, and runtime acceptance.
