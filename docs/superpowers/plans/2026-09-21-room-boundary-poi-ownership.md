# Room Boundary POI Ownership Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Do not use subagents. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lock down the existing wall-POI architecture with end-to-end regression coverage, without adding the wall block to canonical Floor geometry or replacing the current ownership model.

**Architecture:** `FloorGeometry` remains the only exact structural Floor model. `RoomPoiEvidence` already derives ephemeral interior/perimeter candidate positions after Room partitioning, and `Building.applyRoomScan(...)` persists only configured POI blocks found at those positions. Start with an end-to-end regression; passing on the current implementation means the intended result is no production-code change.

**Tech Stack:** Java 21, Minecraft 1.21.1, Architectury, NeoForge GameTests, JUnit 5, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-21-room-boundary-poi-ownership-design.md`

## Global Constraints

- Preserve all unrelated staged, unstaged, and untracked work in the dirty `dev/1.21.1` checkout.
- `FloorGeometry` remains the canonical exact structural Floor-cell model.
- A wall POI may belong to a Room without becoming a Floor cell.
- Existing wall-POI behavior is the compatibility baseline; this task is not a redesign of that mechanism.
- `RoomPoiEvidence` remains the only ordinary Room geometry-to-POI-candidate adapter.
- POI evidence is derived only after Floor discovery and Room partitioning.
- Perimeter evidence extends exactly one horizontal block from an owned Room Floor cell.
- Candidate Y range remains `feetY - 1 <= y < ceilingY`.
- An overlapping actual Room Floor column takes precedence over perimeter evidence.
- Shared perimeter columns must have one deterministic Room owner.
- Do not add a second persisted Room/wall/POI geometry model.
- Do not modify `SelectedFloorScanner`, Floor-band classification, stair/trapdoor behavior, persistence format, or Room inheritance unless a new focused failing regression independently proves that owner is wrong.
- Do not add generic facing/attachment heuristics without a concrete failing gameplay fixture.
- Use existing configured `minecraft:bookshelf` data for end-to-end POI detection rather than introducing test-only building type configuration.
- Run only one NeoForge GameTest server at a time.

## Review Focus

1. Wall POI candidate versus Floor ownership — the bookshelf must be recorded as POI evidence while its wall position remains outside `FloorGeometry` and Room `floorCells`.
2. Shared wall between Rooms — the same boundary position must never be counted by both Rooms.
3. Stacked Rooms — a non-overlapping upper Room must not suppress lower-Room perimeter evidence.
4. Vertical interval edges — perimeter candidates must not leak below `feetY - 1` or into/above `ceilingY`.
5. Irrelevant wall blocks — perimeter candidacy must not cause arbitrary wall blocks to be persisted as POIs.

---

### Task 1: Add end-to-end wall POI regression

**Files:**
- Modify: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`
- Read only: `common/src/main/resources/data/mca/building_types/library.json`

**Interfaces:**
- Consumes: `SelectedFloorScanner.scan(Level, BlockPos, int, int)`, `BuildingRoomScanner.partition(...)`, `Building.applyRoomScan(...)`, and configured `BuildingTypes` matching.
- Produces: one GameTest proving actual wall POI persistence is independent of Floor geometry.

- [ ] **Step 1: Add the integration regression**

Near the existing basic closed-Room scanner tests, add:

```java
@GameTest(batch = "mca_room_wall_poi", templateNamespace = "minecraft",
        template = "bastion/blocks/air", timeoutTicks = 100)
public static void wallPoiCountsWithoutBecomingFloorGeometry(GameTestHelper helper) {
    BlockPos roomMin = helper.absolutePos(new BlockPos(4, 2, 4));
    buildClosedRoom(helper, roomMin, 5, 4);
    BlockPos seed = roomMin.offset(2, 0, 2);
    BlockPos wallPoi = roomMin.offset(-1, 1, 2);
    var level = helper.getLevel();
    level.setBlock(wallPoi, Blocks.BOOKSHELF.defaultBlockState(), 3);

    SelectedFloorScanner.Result floorScan = SelectedFloorScanner.scan(level, seed, 128, 16);
    helper.assertTrue(floorScan.result() == Building.validationResult.SUCCESS,
            "wall-POI room scan failed: " + floorScan.result());
    helper.assertTrue(floorScan.floor().cellsAtColumn(wallPoi.getX(), wallPoi.getZ()).isEmpty(),
            "wall POI column was manufactured into canonical FloorGeometry");

    BuildingRoomScanner.Result roomScan = BuildingRoomScanner.partition(
            level, seed, 128, 0, floorScan).stream()
            .filter(result -> result.status() == Building.validationResult.SUCCESS)
            .findFirst().orElseThrow();
    Building room = new Building(seed);
    helper.assertTrue(room.applyRoomScan(level, roomScan) == Building.validationResult.SUCCESS,
            "Room materialization failed");

    ResourceLocation bookshelf = BuiltInRegistries.BLOCK.getKey(Blocks.BOOKSHELF);
    helper.assertTrue(room.getBlocks().getOrDefault(bookshelf, List.of()).contains(wallPoi),
            "configured bookshelf in Room wall was not recorded as POI evidence");
    helper.assertTrue(room.getFloorCells().stream().noneMatch(cell ->
                    cell.getX() == wallPoi.getX() && cell.getZ() == wallPoi.getZ()),
            "Room persisted wall POI column as owned Floor geometry");
    helper.succeed();
}
```

Add imports only if absent:

```java
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
```

`java.util.List` is already used in this test class.

- [ ] **Step 2: Run the NeoForge GameTest server to establish behavior**

Run:

```powershell
.\gradlew.bat :neoforge:runGameTestServer --no-daemon --no-build-cache --max-workers=1
```

Expected outcome for `wallpoicountswithoutbecomingfloorgeometry`:

- PASS means the architecture already implements the desired behavior. Proceed without adding production behavior.
- FAIL because the bookshelf is absent from `room.getBlocks()` means continue to Task 2 and fix only `RoomPoiEvidence`/candidate ownership.
- FAIL because `wallPoi` appears in `FloorGeometry` means stop and investigate independently; do not hide that topology regression inside the POI change.

Record any unrelated required failure by exact test name.

---

### Task 2: Harden pure boundary-candidate contracts

**Files:**
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/RoomPoiEvidenceTest.java`
- Modify only if Task 1 proves a production gap: `common/src/main/java/net/conczin/mca/server/world/data/RoomPoiEvidence.java`

**Interfaces:**
- Consumes: `RoomPoiEvidence.candidates(Collection<Component>, Component)`.
- Produces: exact one-block perimeter and vertical-interval contract.

- [ ] **Step 1: Add the vertical upper-bound regression**

Add:

```java
@Test
void perimeterEvidenceStopsBeforePhysicalCeiling() {
    FloorGeometry.Cell cell = new FloorGeometry.Cell(new BlockPos(1, 64, 1), 68);
    var room = new RoomPartitioner.Component(Set.of(cell));

    Set<BlockPos> candidates = RoomPoiEvidence.candidates(Set.of(room), room);

    assertTrue(candidates.contains(new BlockPos(0, 67, 1)));
    assertFalse(candidates.contains(new BlockPos(0, 68, 1)));
}
```

- [ ] **Step 2: Add the one-block perimeter regression**

Add:

```java
@Test
void perimeterEvidenceDoesNotExpandThroughThickWall() {
    FloorGeometry.Cell cell = new FloorGeometry.Cell(new BlockPos(1, 64, 1), 68);
    var room = new RoomPartitioner.Component(Set.of(cell));

    Set<BlockPos> candidates = RoomPoiEvidence.candidates(Set.of(room), room);

    assertTrue(candidates.contains(new BlockPos(0, 65, 1)));
    assertFalse(candidates.contains(new BlockPos(-1, 65, 1)));
}
```

- [ ] **Step 3: Run the focused common test**

Run:

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.server.world.data.RoomPoiEvidenceTest --no-daemon --no-build-cache --max-workers=1
```

Expected: PASS.

If these tests pass and Task 1 also passed, do not add behavioral production code.

- [ ] **Step 4: If Task 1 exposed a production gap, make the smallest candidate-generation fix**

Keep the current shape:

```java
static Set<BlockPos> candidates(Collection<RoomPartitioner.Component> components,
                                RoomPartitioner.Component component)
```

The corrected algorithm must remain:

```text
for each exact Room Floor cell:
    include its own physical POI column
    inspect each cardinal adjacent column
    reject the adjacent column when another Room owns an overlapping exact Floor interval there
    otherwise include it only when this Room is the deterministic owner of that perimeter column
```

Reuse `occupiesPoiColumn(...)`, `RoomPartitioner.adjacent(...)`, and `RoomPartitioner.owner(...)`. Do not query `BuildingTypes` here and do not add world/block state to this pure geometry helper.

- [ ] **Step 5: If production code changed in Step 4, keep its comment aligned with the contract**

When Step 4 actually changes `RoomPoiEvidence.java`, update the class comment to express the contract directly:

```java
/** Derives Room-local and immediate boundary positions where configured POIs may be observed. */
```

If Task 1 and the focused tests already pass on the baseline, skip this step and leave production code untouched. Keep comments focused on why perimeter candidates are separate from Floor ownership.

---

### Task 3: Verify configured filtering and ownership remain separate

**Files:**
- Read/verify: `common/src/main/java/net/conczin/mca/server/world/data/Building.java`
- Read/verify: `common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java`
- Read/verify: `common/src/main/java/net/conczin/mca/server/world/data/RoomTypeResolver.java`
- Test: `common/src/test/java/net/conczin/mca/server/world/data/RoomPoiEvidenceTest.java`
- Test: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`

**Interfaces:**
- Consumes: candidate positions from Task 2.
- Produces: evidence that candidates and actual persisted configured POIs remain different concepts.

- [ ] **Step 1: Confirm the actual configured-block filter remains in `Building`**

Run:

```powershell
rg -n "recordBuildingBlock|matchesBlock|scan\.poiCells" common/src/main/java/net/conczin/mca/server/world/data/Building.java
```

Expected: `applyRoomScan(...)` iterates `scan.poiCells()` and `recordBuildingBlock(...)` persists a block only when a configured `BuildingType` matches its `BlockState`.

Do not move `BuildingTypes` matching into `RoomPoiEvidence`.

- [ ] **Step 2: Prove no Floor topology owner gained POI coupling**

Run:

```powershell
rg -n "RoomPoiEvidence|BuildingTypes|matchesBlock" common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java common/src/main/java/net/conczin/mca/server/world/data/FloorGeometry.java common/src/main/java/net/conczin/mca/server/world/data/RoomPartitioner.java
```

Expected: no new POI/type-matching dependency in `SelectedFloorScanner` or `FloorGeometry`. `RoomPartitioner` may remain referenced by `RoomPoiEvidence` for deterministic ownership, but must not inspect configured block types.

- [ ] **Step 3: Compile both common and NeoForge source**

Run:

```powershell
.\gradlew.bat :common:compileJava :neoforge:compileJava --no-daemon --no-build-cache --max-workers=1
```

Expected: PASS.

---

### Task 4: Full regression and final review

**Files:**
- Review only the scoped diff plus existing dirty-tree status.

**Interfaces:**
- Consumes: Tasks 1-3.
- Produces: release-quality evidence for boundary POI ownership with unchanged Floor topology.

- [ ] **Step 1: Run the full NeoForge GameTest server once**

Run:

```powershell
.\gradlew.bat :neoforge:runGameTestServer --no-daemon --no-build-cache --max-workers=1
```

Expected: all required tests pass, including `wallpoicountswithoutbecomingfloorgeometry`. If unrelated dirty archer/other tests fail, report exact names and compare them with the pre-change baseline; do not call the suite green.

- [ ] **Step 2: Run the common world-data tests**

Run:

```powershell
.\gradlew.bat :common:test --tests "net.conczin.mca.server.world.data.*" --no-daemon --no-build-cache --max-workers=1
```

Expected: PASS.

- [ ] **Step 3: Review the scoped diff through KISS/DRY/YAGNI**

Run:

```powershell
git diff --check
git diff -- common/src/main/java/net/conczin/mca/server/world/data/RoomPoiEvidence.java common/src/test/java/net/conczin/mca/server/world/data/RoomPoiEvidenceTest.java neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java
git status --short
```

Review requirements:

- no wall cell was added to `FloorGeometry`;
- no second geometry model was introduced;
- no duplicate POI filtering was added outside `Building.recordBuildingBlock(...)`;
- no generic facing framework was added;
- candidate generation remains deterministic and operation-local;
- unrelated dirty files remain untouched.

- [ ] **Step 4: Commit only if explicitly requested**

Do not commit automatically. If the user asks for a commit, stage only the scoped files and use a focused message such as:

```text
test: lock down room boundary POI ownership
```

If Task 2 required a production correction, use:

```text
fix: preserve room boundary POI ownership
```

