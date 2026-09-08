# Exact-Cell Floor Scanner Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Floor/Room membership a set of canonical integer 3D cells, keep fractional Minecraft surface height transient during fresh discovery only, remove connector-manufactured cells and post-hoc semantic-band reconstruction, and migrate the previous exact-cell save shape cleanly to `buildingDataVersion = 2`.

**Architecture:** `SelectedFloorScanner` owns live Minecraft collision probing and records accepted integer cell-to-cell transitions only for the current observation. `FloorGeometry` persists integer membership cells plus retained `ceilingY` metadata and connector markers; it does not persist `surfaceY`. `RoomPartitioner` partitions a fresh Floor using the accepted transient transitions, while Rooms persist only the exact cell keys they own.

**Tech Stack:** Java 21, Minecraft 1.21.1, Architectury common code, NeoForge GameTest, JUnit 5, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-08-exact-cell-floor-scanner-simplification-design.md`

## Global Constraints

- `FloorGeometry.Cell.feet` is the only canonical Floor/Room membership identity.
- Canonical cells are integer `BlockPos` values. Supporting blocks and fractional collision surfaces are discovery evidence only.
- `surfaceY` must not remain in `FloorGeometry.Cell`, version-2 `StructureFloor` NBT, Room persistence, projection, or Room partitioning.
- `ceilingY` may remain per-cell vertical-interval metadata; it is not identity and must not decide Room connectivity.
- Multiple exact cells in the same X/Z column remain valid and independently addressable.
- Fresh physical reachability is represented only by bounded transient accepted transitions propagated with the current scan/observation. Do not create a second persisted geometry model.
- Projection remains derived-only and must not drive discovery, storey ownership, Room partitioning, or exact lookup.
- Do not restore `MIN_MEANINGFUL_HEIGHT_SLICE_AREA`, equal-Y slice reconstruction, or a flattened floor model under another name.
- Preserve semantic Floors: stairs may physically connect two storeys without merging them into one Floor.
- Doors/gates are connector gaps/metadata and never canonical Floor cells.
- Ladders/trapdoors attach semantic Floors but never merge Room components.
- POI relevance never manufactures Floor cells. Beds/carpet/low furniture may occupy an already valid interior membership cell; full cubes remain obstructions.
- Physical world discovery is independent of persisted Floor identity. Persistence may classify an already-discovered transition semantically; it must never decide whether Minecraft considers the transition physically valid.
- `RoomDFU` remains the only compatibility boundary. Version-1 canonical exact-cell saves migrate to version 2 by discarding persisted `surfaceY`; released-origin and upstream-unversioned floor-clean-squash migrations remain supported.
- Prefer existing owners and nested/private helpers; do not add a new top-level traversal subsystem.
- Current branch is `dev/1.21.1`. Verify branch/status before implementation tasks and preserve unrelated dirty work.
- `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java` is pre-existing untracked user evidence. Inspect and modify it as needed, but never stage its parent directory wholesale.

---

## File Structure / Responsibility Map

- `common/src/main/java/net/conczin/mca/server/world/data/FloorGeometry.java`
  - Canonical exact integer membership cells, per-cell `ceilingY`, column index, derived projection, connector marker metadata.
  - No `surfaceY`, no physical step calculation, no requirement that connector positions are cells.
- `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`
  - Live support/collision probing, transient surface height, accepted neighbour transitions, selected-storey traversal, exterior probing, alternate-storey evidence.
  - Owns nested transient `SurfaceCell` and `Transition` records.
- `common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java`
  - Propagates fresh Floor plus transient transition evidence through existing `AttachmentSeed`, `FloorObservation`, and `Result` records.
- `common/src/main/java/net/conczin/mca/server/world/data/RoomPartitioner.java`
  - Connected components over canonical Floor cells using accepted fresh transitions only.
- `common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java`
  - Materializes Room geometry from one fresh Floor and its accepted transitions.
- `common/src/main/java/net/conczin/mca/server/world/data/RoomScanPlanner.java`
  - Passes fresh observation transitions into expansion/reconciliation selection.
- `common/src/main/java/net/conczin/mca/server/world/data/RoomWorkflow.java`
  - Passes fresh scan transitions when repartitioning refreshed Floors.
- `common/src/main/java/net/conczin/mca/server/world/data/StructureExpansionPolicy.java`
  - Matches persisted Rooms against components partitioned from the fresh observation; does not reconstruct physical adjacency from persisted geometry.
- `common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java`
  - Connector classification/normalization and attachment evidence; marker-only for doors/gates.
- `common/src/main/java/net/conczin/mca/server/world/data/StructureFloor.java`
  - Version-2 canonical exact cell persistence: `pos` plus `ceilingY`; connector markers may live outside cells.
- `common/src/main/java/net/conczin/mca/server/world/data/RoomDFU.java`
  - Sole compatibility boundary, including canonical v1 -> v2 migration.
- `common/src/main/java/net/conczin/mca/server/world/data/Village.java`
  - Owns `BUILDING_DATA_VERSION = 2`.
- `common/src/main/java/net/conczin/mca/server/world/data/RoomPoiEvidence.java`
  - POI/perimeter evidence derived after Room topology; adapts to surface-free cells.
- `common/src/test/java/net/conczin/mca/server/world/data/`
  - Pure geometry, transition, partition, persistence, migration, attachment, projection, and POI contracts.
- `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`
  - Real Minecraft support/collision cases: beds, carpet, full cubes, slabs/stairs, doors, caves, exterior/storey boundaries, ladders/trapdoors.

---

### Task 1: Lock integer membership versus occupancy

**Files:**
- Modify: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`

**Interfaces:**
- Consumes: existing `SelectedFloorScanner.scan(Level, BlockPos, int, int)` and `SelectedFloorScanner.inspectSurfaceCell(Level, BlockPos, FloorCeilingResolver)` world probing.
- Produces: one strict private predicate for interior occupancy that distinguishes genuinely sub-full furniture from full cubes.

- [ ] **Step 1: Preserve the existing bed regressions and add full-cube RED**

Keep the existing bed-footprint and bed-top-source GameTests. Add:

```java
@GameTest(batch = "mca_floor_full_block", templateNamespace = "minecraft",
        template = "bastion/blocks/air", timeoutTicks = 80)
public static void fullBlockIsNotOwnedInteriorCell(GameTestHelper helper) {
    BlockPos roomMin = helper.absolutePos(new BlockPos(4, 2, 4));
    buildClosedRoom(helper, roomMin, 5, 4);
    BlockPos blocked = roomMin.offset(1, 0, 1);
    helper.getLevel().setBlock(blocked, Blocks.STONE.defaultBlockState(), 3);

    SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(
            helper.getLevel(), roomMin.offset(3, 0, 2), 128, 16);

    helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
            "room scan failed: " + scan.result());
    helper.assertTrue(scan.floor().cellAt(blocked).isEmpty(),
            "full cube became an owned Room cell");
    helper.succeed();
}
```

- [ ] **Step 2: Add representative low-occupancy coverage**

Add a carpet fixture and compare exact membership positions, not projection only:

```java
Set<BlockPos> before = SelectedFloorScanner.scan(level, seed, 128, 16).floor()
        .cells().stream().map(FloorGeometry.Cell::feet).collect(Collectors.toSet());
level.setBlock(carpetCell, Blocks.RED_CARPET.defaultBlockState(), 3);
Set<BlockPos> after = SelectedFloorScanner.scan(level, seed, 128, 16).floor()
        .cells().stream().map(FloorGeometry.Cell::feet).collect(Collectors.toSet());
helper.assertTrue(before.equals(after), "carpet changed integer Room membership");
```

- [ ] **Step 3: Run GameTests and confirm the current broad predicate is exposed**

```powershell
.\gradlew.bat :neoforge:runGameTestServer --rerun-tasks
```

Expected before the implementation change: the full-cube case fails because current `collisionShape.max(Y) <= 1.0D` logic accepts a full cube as topology-neutral.

- [ ] **Step 4: Make sub-full occupancy strict**

Use one physical predicate:

```java
private static boolean isInteriorOccupancyAllowed(Level world, BlockPos pos) {
    BlockState state = world.getBlockState(pos);
    if (!state.getFluidState().isEmpty()) return false;
    var shape = state.getCollisionShape(world, pos);
    return shape.isEmpty() || shape.max(Direction.Axis.Y) < 1.0D;
}
```

`inspectSurfaceCell` still requires valid support and usable headroom. Do not consult POI relevance here; a wall bookshelf must not become a Floor cell merely because it is a POI.

- [ ] **Step 5: Re-run GameTests and focused common tests**

```powershell
.\gradlew.bat :neoforge:runGameTestServer --rerun-tasks
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.SelectedFloorScannerTest' --rerun-tasks
```

Expected: bed, bed-source, carpet, and full-cube regressions pass.

- [ ] **Step 6: Commit Task 1**

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
git add neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java
git commit -m "fix: separate room membership from full occupancy"
```

---

### Task 2: Remove `surfaceY` from canonical cells and retain transient transitions

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/FloorGeometry.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomPoiEvidence.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomDFU.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/FloorGeometryTest.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/SelectedFloorScannerTest.java`

**Interfaces:**
- Produces canonical `FloorGeometry.Cell(BlockPos feet, int ceilingY)`.
- Produces nested transient `SelectedFloorScanner.SurfaceCell(BlockPos feet, double surfaceY, int ceilingY)`.
- Produces nested transient `SelectedFloorScanner.Transition(BlockPos first, BlockPos second)`.
- Removes `FloorGeometry.canStep(double, double)`; physical step checks stay inside `SelectedFloorScanner`.

- [ ] **Step 1: Add a reflection RED proving canonical Cell still carries `surfaceY`**

```java
@Test
void canonicalCellContainsOnlyMembershipAndCeilingMetadata() {
    assertEquals(List.of("feet", "ceilingY"),
            Arrays.stream(FloorGeometry.Cell.class.getRecordComponents())
                    .map(RecordComponent::getName)
                    .toList());
}
```

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.FloorGeometryTest.canonicalCellContainsOnlyMembershipAndCeilingMetadata' --rerun-tasks
```

Expected: FAIL because the current record components are `feet`, `surfaceY`, `ceilingY`.

- [ ] **Step 2: Introduce transient world-probe types inside `SelectedFloorScanner`**

Add:

```java
record SurfaceCell(BlockPos feet, double surfaceY, int ceilingY) {
    SurfaceCell {
        feet = feet.immutable();
    }

    FloorGeometry.Cell canonical() {
        return new FloorGeometry.Cell(feet, ceilingY);
    }
}

record Transition(BlockPos first, BlockPos second) {
    Transition {
        first = first.immutable();
        second = second.immutable();
    }

    boolean connects(BlockPos a, BlockPos b) {
        return (first.equals(a) && second.equals(b))
                || (first.equals(b) && second.equals(a));
    }
}
```

Keep `surfaceY` only on `SurfaceCell`/surface probes used during a live scan. Add a scanner-local `canStep(double fromSurfaceY, double toSurfaceY)` using the existing `1.125D` threshold.

- [ ] **Step 3: Simplify `FloorGeometry.Cell`**

Target:

```java
record Cell(BlockPos feet, int ceilingY) {
    Cell {
        Objects.requireNonNull(feet, "feet");
        if (ceilingY <= feet.getY()) {
            throw new IllegalArgumentException("FloorGeometry cell ceiling must be above feet");
        }
        feet = feet.immutable();
    }
}
```

Delete `MAX_STEP_HEIGHT`, `canStep`, and all `surfaceY` validation from `FloorGeometry`.

- [ ] **Step 4: Convert the scanner internally from canonical Cells to `SurfaceCell` probes**

`inspectSurfaceCell`, `findLanding`, horizontal neighbour probing, storey-policy evidence, and exterior probing use `SurfaceCell`. Only accepted Floor membership is converted with `surface.canonical()`.

When a physical neighbour transition is accepted for the selected Floor, add:

```java
transitions.add(new Transition(current.feet(), candidate.feet()));
```

Do not store the numeric surface height after that decision.

- [ ] **Step 5: Update non-scanner Cell construction**

Replace old three-argument cell construction with the two-argument canonical form. Representative targets are:

```java
new FloorGeometry.Cell(pos, ceilingY)
new FloorGeometry.Cell(sourceCell.feet().relative(direction), sourceCell.ceilingY())
```

Update `RoomPoiEvidence`, `StructureConnector`, legacy migration constructors, and tests. No replacement code may synthesize a fake `surfaceY = feet.getY()` merely to satisfy an old signature.

- [ ] **Step 6: Add pure transition semantics coverage**

```java
@Test
void acceptedTransitionIsUndirectedForRoomConnectivity() {
    BlockPos a = new BlockPos(0, 64, 0);
    BlockPos b = new BlockPos(1, 64, 0);
    SelectedFloorScanner.Transition edge = new SelectedFloorScanner.Transition(a, b);
    assertTrue(edge.connects(a, b));
    assertTrue(edge.connects(b, a));
}
```

- [ ] **Step 7: Run focused compile/tests**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.FloorGeometryTest' --tests 'net.conczin.mca.server.world.data.SelectedFloorScannerTest' --rerun-tasks
.\gradlew.bat :neoforge:compileJava --rerun-tasks
```

Expected: focused tests and NeoForge common-source compilation pass with no `surfaceY` access on `FloorGeometry.Cell`.

- [ ] **Step 8: Commit Task 2**

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/FloorGeometry.java
git add common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
git add common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java
git add common/src/main/java/net/conczin/mca/server/world/data/RoomPoiEvidence.java
git add common/src/main/java/net/conczin/mca/server/world/data/RoomDFU.java
git add common/src/test/java/net/conczin/mca/server/world/data/FloorGeometryTest.java
git add common/src/test/java/net/conczin/mca/server/world/data/SelectedFloorScannerTest.java
git commit -m "refactor: keep floor surface height transient"
```

---

### Task 3: Partition Rooms from fresh transitions and make connectors gaps

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/FloorGeometry.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomPartitioner.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomWorkflow.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomScanPlanner.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureExpansionPolicy.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureFloor.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/RoomPartitionerTest.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/StructureConnectorTest.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/StructureFloorTest.java`
- Test: `common/src/test/java/net/conczin/mca/server/world/data/VillageFloorSystemTest.java`

**Interfaces:**
- Produces `RoomPartitioner.partition(FloorGeometry, Collection<SelectedFloorScanner.Transition>)`.
- `BuildingRoomScanner.scan(Level, BlockPos, Set<BlockPos>, int, int, FloorGeometry, Collection<SelectedFloorScanner.Transition>)` and `BuildingRoomScanner.partition(Level, BlockPos, int, int, FloorGeometry, Collection<SelectedFloorScanner.Transition>)` receive the same transition collection as the Floor they materialize.
- Existing `SelectedFloorScanner.Result`, `StructureScanner.AttachmentSeed`, `FloorObservation`, and `StructureScanner.Result` propagate immutable transition sets.
- Connector markers are stored by physical position; marker positions need not be Floor cells.

- [ ] **Step 1: Write RoomPartitioner RED for explicit transition connectivity**

```java
@Test
void partitionUsesOnlyAcceptedFreshTransitions() {
    FloorGeometry geometry = geometry(
            cell(0, 64, 0), cell(1, 64, 0), cell(2, 64, 0));
    Set<SelectedFloorScanner.Transition> transitions = Set.of(
            new SelectedFloorScanner.Transition(
                    new BlockPos(0, 64, 0), new BlockPos(1, 64, 0)));

    List<RoomPartitioner.Component> parts = RoomPartitioner.partition(geometry, transitions);

    assertEquals(2, parts.size());
    assertTrue(parts.stream().anyMatch(part -> part.area() == 2));
    assertTrue(parts.stream().anyMatch(part -> part.area() == 1));
}
```

Expected before implementation: compile/test RED because current partitioning reconstructs adjacency from canonical `surfaceY`.

- [ ] **Step 2: Make `RoomPartitioner` transition-driven**

Change BFS adjacency to require a supplied accepted edge:

```java
private static boolean connected(BlockPos a, BlockPos b,
                                 Collection<SelectedFloorScanner.Transition> transitions) {
    return transitions.stream().anyMatch(edge -> edge.connects(a, b));
}
```

Do not add a persistent edge map. If later measurement shows repeated linear lookup is material, add an ephemeral endpoint index inside `RoomPartitioner.partition` without changing the domain model.

- [ ] **Step 3: Propagate transition evidence through existing result records**

Add immutable `Set<SelectedFloorScanner.Transition> transitions` fields to:

```text
SelectedFloorScanner.Result
StructureScanner.AttachmentSeed
StructureScanner.FloorObservation
StructureScanner.Result
```

Every constructor uses `Set.copyOf(transitions)`; failure results use `Set.of()`.

- [ ] **Step 4: Pass transitions into every fresh Room partition site**

Update:

```text
BuildingRoomScanner.scan
BuildingRoomScanner.partition
RoomWorkflow fresh refresh/repartition calls
StructureExpansionPolicy.registeredRoomForFreshComponent
RoomScanPlanner.planFresh
```

No fresh-observation path may call `RoomPartitioner.partition(freshFloor)` without the transitions produced by that same observation.

- [ ] **Step 5: Write connector-gap RED**

```java
@Test
void doorMarkerDoesNotRequireOrOwnFloorCell() {
    BlockPos door = new BlockPos(1, 64, 0);
    FloorGeometry geometry = new FloorGeometry(
            Set.of(cell(0, 64, 0), cell(2, 64, 0)),
            Map.of(door, FloorConnector.Type.DOOR));

    assertTrue(geometry.cellAt(door).isEmpty());
    assertEquals(FloorConnector.Type.DOOR,
            geometry.connectorTypesByPosition().get(door));
    assertEquals(2, RoomPartitioner.partition(geometry, Set.of()).size());
}
```

Expected before connector changes: constructor/accessor or membership validation fails because connectors currently assume cell ownership.

- [ ] **Step 6: Remove connector-cell materialization**

In `FloorGeometry`, rename connector storage/access to `connectorTypesByPosition` and remove the constructor requirement that marker keys are cell keys. In `FloorGeometry.flat` and `StructureFloor.load`, retain valid markers without filtering them through cell membership.

Replace connector materialization with marker-only merge:

```java
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

Door/gate transitions are omitted from the accepted transition set. Do not synthesize a replacement door cell.

- [ ] **Step 7: Preserve deterministic connector/POI ownership as metadata only**

Keep `RoomPartitioner.owner` or the existing stable owner comparator for shared perimeter/connector POI evidence. Delete only boundary-cell ownership code whose purpose was to insert the connector position into a Room floor-cell set.

- [ ] **Step 8: Run focused tests and GameTests**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.RoomPartitionerTest' --tests 'net.conczin.mca.server.world.data.StructureConnectorTest' --tests 'net.conczin.mca.server.world.data.StructureFloorTest' --tests 'net.conczin.mca.server.world.data.VillageFloorSystemTest' --rerun-tasks
.\gradlew.bat :neoforge:runGameTestServer --rerun-tasks
```

Expected: Room components follow supplied transitions, connector positions remain outside cell sets, attachment tests remain green.

- [ ] **Step 9: Commit Task 3**

Stage exact changed files only, then:

```powershell
git commit -m "refactor: partition rooms from fresh cell transitions"
```

---

### Task 4: Introduce canonical persistence version 2

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Village.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureFloor.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomDFU.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/StructureFloorTest.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/RoomDFUTest.java`

**Interfaces:**
- Produces `Village.BUILDING_DATA_VERSION = 2`.
- Version-2 `StructureFloor` cell NBT contains `pos` plus `ceilingY`, never `surfaceY`.
- `RoomDFU` recognizes current v2, canonical v1 migration, upstream unversioned floor-clean-squash, and released origin.

- [ ] **Step 1: Write version-2 save RED**

Update the current version assertion and add the cell-shape assertion:

```java
assertEquals(2, village.save().getInt("buildingDataVersion"));
CompoundTag cell = firstCanonicalFloorCell(village.save());
assertTrue(cell.contains("pos"));
assertTrue(cell.contains("ceilingY", Tag.TAG_INT));
assertFalse(cell.contains("surfaceY"));
```

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.RoomDFUTest' --rerun-tasks
```

Expected: RED on version and/or persisted `surfaceY`.

- [ ] **Step 2: Change canonical `StructureFloor` serialization**

Target:

```java
private static CompoundTag saveCell(FloorGeometry.Cell cell) {
    CompoundTag tag = new CompoundTag();
    tag.put("pos", NbtHelper.encodeBlockPos(cell.feet()));
    tag.putInt("ceilingY", cell.ceilingY());
    return tag;
}

private static FloorGeometry.Cell loadCell(CompoundTag tag) {
    BlockPos pos = NbtHelper.decodeBlockPos(tag.get("pos"));
    if (pos == null) throw new IllegalArgumentException("FloorGeometry cell is missing pos");
    if (!tag.contains("ceilingY", Tag.TAG_INT)) {
        throw new IllegalArgumentException("FloorGeometry cell is missing ceilingY");
    }
    return new FloorGeometry.Cell(pos, tag.getInt("ceilingY"));
}
```

Canonical v2 load must not interpret optional `surfaceY`.

- [ ] **Step 3: Add canonical-v1 migration fixture**

Construct a canonical-v1 tag from a valid current fixture by setting `buildingDataVersion` to `1` and adding an old `surfaceY` double to every Floor cell. Use a fractional value on at least one cell. Assert `RoomDFU.load(CompoundTag)` preserves exact `feet` and `ceilingY` ownership while the next v2 save contains no `surfaceY`.

- [ ] **Step 4: Route versions explicitly in `RoomDFU.load(CompoundTag)`**

Use:

```java
if (villageTag.contains("buildingDataVersion")) {
    int version = villageTag.getInt("buildingDataVersion");
    return switch (version) {
        case 1 -> loadCurrent(migrateCanonicalV1(villageTag));
        case Village.BUILDING_DATA_VERSION -> loadCurrent(villageTag);
        default -> throw new IllegalArgumentException(
                "Unsupported MCA buildingDataVersion: " + version);
    };
}
```

`migrateCanonicalV1(CompoundTag)` works on `villageTag.copy()`, validates the v1 cell fields it depends on, removes `surfaceY` from every canonical Floor cell, sets the copy's `buildingDataVersion` to 2, and returns the copy. It must not mutate the caller's tag.

- [ ] **Step 5: Update old corruption tests deliberately**

Required test outcomes:

- Missing/invalid `pos` or `ceilingY` remains invalid.
- Version-1 malformed/non-finite `surfaceY` is validated only inside the v1 migration path if the historical-shape validator still requires it.
- Version 3 becomes the unsupported-version case after v2 is current.
- Fractional v1 `surfaceY = 63.5D` migrates successfully but does not survive in v2 state/save output.

- [ ] **Step 6: Re-run persistence tests**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.RoomDFUTest' --tests 'net.conczin.mca.server.world.data.StructureFloorTest' --rerun-tasks
```

Expected: current v2 round-trip, canonical-v1 migration, released-origin migration, and upstream-unversioned migration pass.

- [ ] **Step 7: Commit Task 4**

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/Village.java
git add common/src/main/java/net/conczin/mca/server/world/data/StructureFloor.java
git add common/src/main/java/net/conczin/mca/server/world/data/RoomDFU.java
git add common/src/test/java/net/conczin/mca/server/world/data/RoomDFUTest.java
git add common/src/test/java/net/conczin/mca/server/world/data/StructureFloorTest.java
git commit -m "refactor: persist integer floor cells in version two"
```

---

### Task 5: Replace semantic-band reconstruction with direct selected-storey traversal

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/SelectedFloorScannerTest.java`
- Test: `common/src/test/java/net/conczin/mca/server/world/data/VillageFloorSystemTest.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`

**Interfaces:**
- Produces nested `StoreyRole { OWNED, EDGE, OTHER }`.
- Produces nested `StoreyScan(FloorGeometry floor, Set<Transition> transitions, Set<BlockPos> alternateSeeds, Set<BlockPos> connectors)`.
- Removes `FloorSelection`, `SemanticBands`, `HeightBand`, equal-Y slice components, and `MIN_MEANINGFUL_HEIGHT_SLICE_AREA` after replacement tests are green.

- [ ] **Step 1: Replace band-oriented pure tests with behavior contracts**

Add tests with these exact expectations:

```text
lowerStoreyOwnsSparseUpwardTransitionButNotUpperRoom
  lower selected cell set contains the sparse top transition
  lower selected cell set excludes broad upper-room cells
  transition set contains the last lower stair edge

upperRoomDoesNotReclaimLowerOwnedTopTransition
  upper selected cell set contains broad upper-room cells
  upper selected cell set excludes the lower-owned top transition

stackedSameColumnCellsSurviveSelectedStoreyTraversal
  two canonical cells sharing X/Z but with different Y remain independently present

unevenCellsAcrossThreeIntegerHeightsRemainOneStorey
  one selected Floor contains all expected Y levels when live transitions connect them

selectedStoreyDoesNotDependOnIterationOrder
  reversing candidate iteration produces identical canonical cell and transition sets
```

These tests assert integer membership and accepted transitions. They must not inspect `surfaceY` after canonicalization.

- [ ] **Step 2: Keep the local semantic policy nested and pure**

Add:

```java
enum StoreyRole { OWNED, EDGE, OTHER }

record StoreyContext(int anchorY) {}

static StoreyRole storeyRole(StoreyContext context,
                             SurfaceCell current,
                             SurfaceCell candidate,
                             TransitionEvidence evidence) {
    int maxOwnedY = context.anchorY() + StructureFloor.BAND_TOLERANCE;
    int y = candidate.feet().getY();
    if (y < context.anchorY() || y > maxOwnedY + 1) return StoreyRole.OTHER;
    if (y == maxOwnedY + 1) return StoreyRole.EDGE;
    if (y == context.anchorY()
            && current.feet().getY() == context.anchorY()
            && evidence.currentHasSameHeightPeer()
            && evidence.candidateHasDescendingStep()) {
        return StoreyRole.OTHER;
    }
    return StoreyRole.OWNED;
}
```

Surface height is allowed inside `SurfaceCell` while evaluating evidence; it is never copied into the canonical Floor.

- [ ] **Step 3: Resolve the local anchor using physical probes only**

`resolveStoreyAnchor` may descend through physically accepted neighbours while the current probe has no same-height peer. `lowestDescendingNeighbor` compares transient `surfaceY` only during this live decision and tie-breaks by feet Y, then X, then Z.

- [ ] **Step 4: Implement direct selected-storey BFS**

Use:

```java
record StoreyScan(FloorGeometry floor,
                  Set<Transition> transitions,
                  Set<BlockPos> alternateSeeds,
                  Set<BlockPos> connectors) {
    StoreyScan {
        transitions = Set.copyOf(transitions);
        alternateSeeds = Set.copyOf(alternateSeeds);
        connectors = Set.copyOf(connectors);
    }
}
```

Traversal rules:

- `OWNED`: add `candidate.canonical()`, add the accepted `Transition`, enqueue.
- `EDGE`: add `candidate.canonical()`, add the accepted `Transition`, do not enqueue; inspect its next physical neighbours only to collect alternate-storey seeds.
- `OTHER`: do not add canonical membership; collect it as alternate-storey evidence when appropriate.
- Connector positions are metadata only and are never canonicalized.

- [ ] **Step 5: Make `scan(Level, BlockPos, int, int)` return the direct storey and its transitions**

Target flow:

```java
StoreyScan selected = scanStorey(world, seedCell, maxSize, maxRadius, ceilings);
FloorGeometry floor = StructureConnector.withConnectorMarkers(
        selected.floor(), StructureConnector.markers(world, selected.connectors()));
List<FloorGeometry> connectedFloors = connectedStoreyEvidence(
        world, floor, selected.alternateSeeds(), maxSize, maxRadius, ceilings);
return success(seed, floor, selected.transitions(), connectedFloors);
```

- [ ] **Step 6: Preserve one-hop `connectedFloors` evidence**

Alternate-storey seeds may be scanned separately for attachment evidence. Do not recursively enumerate the whole building. Never deduplicate by footprint alone because stacked Floors may share X/Z projection.

Keep `VillageFloorSystemTest.walkableStoreyEvidenceCanProveStairFloorAttachment()` green.

- [ ] **Step 7: Add real staircase GameTest**

Build lower and upper rooms connected only by ordinary stairs. Assert:

```java
helper.assertTrue(lower.floor().anchorY() == lowerY, "wrong lower storey");
helper.assertTrue(upper.floor().anchorY() == upperY, "wrong upper storey");
helper.assertTrue(lower.floor().cellAt(topTransition).isPresent(),
        "lower storey lost sparse transition cell");
helper.assertTrue(upper.floor().cellAt(topTransition).isEmpty(),
        "upper storey reclaimed lower-owned transition cell");
helper.assertTrue(lower.connectedFloors().stream().anyMatch(floor -> floor.anchorY() == upperY),
        "stair attachment evidence was lost");
```

- [ ] **Step 8: Delete the superseded reconstruction model**

Remove only after the replacement tests are green:

```text
MIN_MEANINGFUL_HEIGHT_SLICE_AREA
floorSelection
semanticBands
sliceComponentsByHeight
sliceComponents used only for storey selection
cellsByHeight
heightBands
selectHeightBand
meaningfulHeightSlice
HeightBand
SemanticBands
FloorSelection
```

- [ ] **Step 9: Verify and commit Task 5**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.SelectedFloorScannerTest' --tests 'net.conczin.mca.server.world.data.VillageFloorSystemTest' --rerun-tasks
.\gradlew.bat :neoforge:runGameTestServer --rerun-tasks
git diff --check
git add common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
git add common/src/test/java/net/conczin/mca/server/world/data/SelectedFloorScannerTest.java
git add neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java
git commit -m "refactor: select integer floor cells during traversal"
```

---

### Task 6: Share physical traversal with exterior/connector discovery and lock world regressions

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomPoiEvidence.java` only if focused regressions require a production change.
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/RoomPoiEvidenceTest.java` only if new regression coverage is needed.
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/FloorGeometryTest.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`

**Interfaces:**
- `horizontalStep` is the single live neighbour resolver for selected-storey scan and exterior probing.
- Vertical connector discovery is local to exact cell intervals and connector blocks; it does not run a separate semantic-band traversal.

- [ ] **Step 1: Add slab/stair GameTest for transient collision evidence**

Build a path containing a slab and stair. Assert the scan succeeds, returns the expected integer cells, and the expected neighbour pair is present in `scan.transitions()`. Do not assert any persisted fractional field.

- [ ] **Step 2: Make `horizontalStep` the only physical neighbour resolver**

Use a transient landing type:

```java
private record HorizontalStep(SurfaceCell landing, BlockPos connector) {}
```

The helper probes `LANDING_Y_OFFSETS`, uses live collision `surfaceY` for scanner-local `canStep(double, double)`, normalizes connector positions, and never manufactures `FloorGeometry.Cell` at a connector.

- [ ] **Step 3: Make `reachesExterior` use the same resolver and storey role**

Exterior traversal follows only `StoreyRole.OWNED` physical transitions. It must not climb through EDGE/OTHER cells into an exterior upper storey and reject an enclosed lower storey.

Add a GameTest where the lower Floor is enclosed, stairs reach an upper area open to exterior, and scanning the lower Floor still succeeds.

- [ ] **Step 4: Collect vertical connectors from exact cell intervals**

Replace seed-band probing with the cell interval:

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

- [ ] **Step 5: Add ladder/trapdoor attachment GameTest**

Build two semantic Floors connected by a ladder column, with a trapdoor only when it is part of that contiguous connector. Assert connector markers and vertical attachment evidence exist while Room components remain separate.

- [ ] **Step 6: Add uneven cave GameTest**

Build one roofed cave Room whose membership spans at least three integer feet-Y values with valid live step transitions. Assert one selected Floor and one Room component contain the expected integer cells without any same-height slice threshold.

- [ ] **Step 7: Keep wall POI ownership deterministic**

Run `RoomPoiEvidenceTest.sharedPerimeterColumnBelongsToOneDeterministicRoom()`. If it already proves one-owner behavior after connector-gap changes, make no `RoomPoiEvidence` production edit. Otherwise add a focused runtime fixture asserting a wall POI is counted once while its block position is absent from `FloorGeometry.cells()`.

- [ ] **Step 8: Verify derived projection with stacked cells**

Extend `FloorGeometryTest`:

```java
assertEquals(2, geometry.cellsAtColumn(x, z).size());
assertEquals(1, geometry.projection().area());
```

The selected-storey/transition tests must continue using exact cells rather than projection.

- [ ] **Step 9: Run focused verification and commit**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.FloorGeometryTest' --tests 'net.conczin.mca.server.world.data.RoomPoiEvidenceTest' --tests 'net.conczin.mca.server.world.data.SelectedFloorScannerTest' --rerun-tasks
.\gradlew.bat :neoforge:runGameTestServer --rerun-tasks
git diff --check
git add common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
git add common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java
git add common/src/test/java/net/conczin/mca/server/world/data/FloorGeometryTest.java
git add neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java
git commit -m "test: lock integer floor scanner world semantics"
```

If `RoomPoiEvidence.java` or `RoomPoiEvidenceTest.java` changed, stage those exact files before this commit too.

---

### Task 7: Remove stale spatial APIs and run the complete acceptance gate

**Files:**
- Review all production/test files touched by Tasks 1-6.
- No unrelated cleanup.

**Interfaces:**
- Final canonical state: integer cell membership plus `ceilingY` plus connector markers.
- Final fresh-only state: transient `SurfaceCell` plus accepted `Transition` evidence.
- No global semantic-band reconstruction and no persisted fractional surface height.

- [ ] **Step 1: Prove stale surface/connector/reconstruction APIs are gone**

```powershell
rg -n "MIN_MEANINGFUL_HEIGHT_SLICE_AREA|SemanticBands|HeightBand|floorSelection\(|semanticBands\(|heightBands\(|meaningfulHeightSlice|sliceComponentsByHeight|withConnectorAssociations|connectorBoundaryCell|floorMembershipCells|connectorTypesByCell" common/src/main/java common/src/test/java
```

Expected: no live production mechanism with those meanings.

Then:

```powershell
rg -n "surfaceY" common/src/main/java common/src/test/java
```

Expected allowed locations only:

- transient `SelectedFloorScanner.SurfaceCell` / live physical helpers;
- canonical-v1 migration/fixtures in `RoomDFU` / `RoomDFUTest`.

`FloorGeometry.java` and version-2 `StructureFloor.saveCell` must contain no `surfaceY`.

- [ ] **Step 2: Run the complete world-data test lane**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.*' --rerun-tasks
```

Expected: PASS.

- [ ] **Step 3: Run cross-loader compilation**

```powershell
.\gradlew.bat :fabric:compileJava :neoforge:compileJava --rerun-tasks
```

Expected: PASS.

- [ ] **Step 4: Run the complete NeoForge GameTest server**

```powershell
.\gradlew.bat :neoforge:runGameTestServer --rerun-tasks
```

Required evidence includes:

```text
bed does not change integer Room membership
bed-top source resolves the same Room
carpet/low occupancy preserves membership
full cube is not an owned interior cell
slab/stair traversal works without persisted surfaceY
lower/upper staircase storeys remain distinct
stacked same-X/Z cells survive
door/gate positions are not Floor cells
uneven cave spans multiple integer Y values
lower-storey exterior check does not climb into upper storey
ladder/trapdoor attaches Floors without merging Rooms
wall POI remains metadata with one deterministic owner
```

- [ ] **Step 5: Verify persistence and attachment specifically**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.RoomDFUTest' --tests 'net.conczin.mca.server.world.data.StructureFloorTest' --tests 'net.conczin.mca.server.world.data.VillageFloorSystemTest' --rerun-tasks
```

Required persistence evidence:

- current saves write `buildingDataVersion = 2`;
- v2 Floor cells contain `pos` plus `ceilingY`, not `surfaceY`;
- v1 canonical exact-cell saves migrate and preserve cell ownership;
- unsupported version 3 rejects;
- released-origin and upstream-unversioned migrations remain green;
- stair-linked `connectedFloors` attachment evidence remains green.

- [ ] **Step 6: Verify checkout hygiene**

```powershell
git diff --check
git status --short --branch
git log --oneline -10
```

Confirm the pre-existing untracked GameTest path was preserved intentionally and no unrelated user work was deleted or staged.

- [ ] **Step 7: Commit final cleanup only if the stale-symbol review required edits**

Stage exact files only, then:

```powershell
git commit -m "refactor: finish integer floor cell cleanup"
```

Do not create an empty commit.

---

## Acceptance Coverage Matrix

| Spec requirement | Plan gate |
| --- | --- |
| Integer 3D cells are canonical membership | Tasks 2, 3 |
| `surfaceY` is transient only | Tasks 2, 4, 7 |
| `ceilingY` remains metadata, not identity/connectivity | Tasks 2, 4 |
| Stacked same-X/Z cells survive | Tasks 5, 6 |
| Beds/carpet preserve interior membership | Task 1 |
| Full cubes remain obstructions | Task 1 |
| Physical slab/stair collision controls fresh transitions | Tasks 2, 6 |
| Room partitioning consumes accepted fresh transitions | Task 3 |
| Doors/gates do not manufacture/own cells | Task 3 |
| Lower/upper stairs stay distinct | Task 5 |
| Sparse top transition may remain lower-owned | Task 5 |
| Uneven cave spans several Y without slice thresholds | Tasks 5, 6 |
| Exterior probing shares selected-storey semantics | Task 6 |
| `connectedFloors` stair evidence preserved | Task 5 + `VillageFloorSystemTest` |
| Ladder/trapdoor attachments do not merge Rooms | Task 6 |
| Wall POIs remain metadata with one owner | Task 6 |
| Projection remains derived | Task 6 |
| Canonical persistence is version 2 without `surfaceY` | Task 4 |
| Canonical v1 migrates deterministically | Task 4 |
| Semantic-band/slice-area reconstruction removed | Tasks 5, 7 |

## Expected End State

```text
Minecraft Level
    |
    v
transient SurfaceCell + collision probe
    |
    +-> accepted Transition set (fresh observation only)
    |
    v
selected-storey integer membership
    |
    v
FloorGeometry(Cell{feet, ceilingY} + connector markers)
    |
    +-> RoomPartitioner + fresh transitions -> exact Room cell sets
    +-> RoomPoiEvidence / projection
    |
    v
StructureFloor v2 persistence (pos + ceilingY, no surfaceY)
```

There is one long-lived spatial truth: integer exact Floor/Room membership cells. Collision height exists only while Minecraft is being observed.
