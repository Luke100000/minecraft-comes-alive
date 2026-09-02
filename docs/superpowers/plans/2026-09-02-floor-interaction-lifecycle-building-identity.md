# Floor Interaction, Lifecycle, and Building Identity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix exact-boundary Floor interaction, make Room/Floor/external deletion APIs explicit, and lock the existing Add Building vs Add Floor/inheritance UX with durable tests without changing save shape or Structure cardinality.

**Architecture:** Keep `physicalFloorAt` half-open and add an explicit interaction-kind contract for physical, connector, and one-block landing handoff candidates. Keep `LogicalBuilding` as the semantic building/inheritance boundary selected by Add Building vs Add Floor/Add Basement. Refactor deletion names only at the domain boundary; do not rewrite scanning or persistence.

**Tech Stack:** Java 21, Minecraft/NeoForge 1.21.1, JUnit 5, NeoForge GameTest, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-02-floor-interaction-lifecycle-invariants-design.md`

## Global Constraints

- `Structure.physicalFloorAt(...)` remains `anchorY <= y < ceilingY`; interaction must not make `ceilingY` inclusive.
- Add Building creates a separate `LogicalBuilding`; Add Floor/Add Basement extends the explicitly selected existing `LogicalBuilding`.
- Room inheritance remains scoped by `LogicalBuilding`; no Floor-level inheritance state is introduced.
- No `buildingDataVersion` bump, Structure migration, or Structure-cardinality invariant is introduced.
- Remove Room never removes a Floor; Remove Floor only peels an empty outermost upper/basement Floor and never Ground Floor.
- Preserve unrelated dirty navigation work in the current linked worktree.

---

### Task 1: Explicit Floor interaction kinds and exact-ceiling handoff

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Structure.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Village.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`

**Interfaces:**
- Consumes: `StructureFloor`, `StructureConnector`, `StructureScanner.isWalkableAnchor(...)`, `Village.getRoomScanPlan(...)`.
- Produces: package-private `Structure.InteractionKind`, `Structure.InteractionPosition.kind()`, `physical()`, `verticalConnector()`, and deterministic candidate ranking in `Village.resolveInteractionPosition(...)`.

- [ ] **Step 1: Add a failing exact-ceiling basement GameTest**

Add a focused GameTest next to the existing ladder/interaction tests. Build a persisted lower floor whose vertical band ends at the player's walkable feet Y, register its Room, and assert that `Village.getRoomScanPlan(...)` resolves that lower Room instead of `ADD_BUILDING`.

```java
@GameTest(batch = "mca_floor_exact_ceiling_handoff", templateNamespace = "minecraft",
        template = "bastion/blocks/air", timeoutTicks = 100)
public static void walkableExactCeilingHandsOffToPersistedLowerFloor(GameTestHelper helper) {
    resetFixtureArea(helper);
    buildSealedBox(helper, 0, 0, 6, 6, 2, 6);

    StructureScanner.Result scan = StructureScanner.scanNewStructure(
            helper.getLevel(), absolute(helper, 3, 3, 3), List.of());
    helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
            "lower-floor fixture should scan successfully");

    Structure structure = scan.toStructure(10);
    Building room = materializeRoom(helper, scan, structure, 20, absolute(helper, 3, 3, 3));
    Village village = new Village(1, helper.getLevel());
    village.registerStructure(structure, room);

    StructureFloor floor = structure.getFloor(room.getFloorId()).orElseThrow();
    BlockPos landing = new BlockPos(room.getSourceBlock().getX(), floor.ceilingY(), room.getSourceBlock().getZ());
    helper.setBlock(landing.below(), Blocks.STONE);
    helper.setBlock(landing, Blocks.AIR);
    helper.setBlock(landing.above(), Blocks.AIR);

    RoomScanPlan plan = village.getRoomScanPlan(helper.getLevel(), landing);
    helper.assertTrue(plan.mode() == Village.RoomScanMode.UPDATE_ROOM,
            "walkable exact-ceiling landing should keep persisted lower-floor Room context; got " + plan.mode());
    helper.assertTrue(plan.functionalRoom().map(Building::getId).orElse(-1) == 20,
            "exact-ceiling handoff resolved the wrong Room");
    helper.succeed();
}
```

- [ ] **Step 2: Run the GameTest server and verify RED**

Run:

```text
./gradlew :neoforge:runGameTestServer --no-daemon
```

Expected: the new `mca_floor_exact_ceiling_handoff` test fails because the current interaction path rejects the `y == ceilingY` landing and falls back away from the persisted Room. Record any unrelated pre-existing GameTest failures separately; do not fix navigation in this task.

- [ ] **Step 3: Introduce explicit interaction kinds and one-block landing handoff**

In `Structure`, replace semantic booleans as the source of truth with:

```java
enum InteractionKind {
    PHYSICAL(0),
    HORIZONTAL_CONNECTOR(1),
    VERTICAL_CONNECTOR(2),
    LANDING_HANDOFF(3);

    private final int priority;

    InteractionKind(int priority) {
        this.priority = priority;
    }

    int priority() {
        return priority;
    }
}
```

Keep convenience methods on `InteractionPosition` so callers do not duplicate kind checks:

```java
record InteractionPosition(StructureFloor floor,
                           Building room,
                           InteractionKind kind,
                           int verticalDistance) {
    boolean physical() {
        return kind == InteractionKind.PHYSICAL;
    }

    boolean verticalConnector() {
        return kind == InteractionKind.VERTICAL_CONNECTOR;
    }
}
```

Resolve normal positions in this order inside one Structure:

1. vertical connector via existing connector geometry;
2. exact physical Floor prism (`floorAtHeight` + footprint) -> `PHYSICAL`;
3. horizontal door/gate owner -> `HORIZONTAL_CONNECTOR`;
4. a same-footprint candidate exactly one block outside the vertical band, only when `StructureScanner.isWalkableAnchor(world, pos)` -> `LANDING_HANDOFF`;
5. existing adjacent connector/passage-cell resolution for non-physical horizontal interaction.

Do not change `physicalFloorAt(...)` or `floorAtHeight(...)` inclusivity.

In `Village.resolveInteractionPosition(...)`, rank by `kind.priority()`, then `verticalDistance`, then the existing stable connector/floor/Room/Structure tie breakers. A physical upper owner must beat a lower landing handoff at the same position.

- [ ] **Step 4: Run the GameTest server and verify GREEN**

Run:

```text
./gradlew :neoforge:runGameTestServer --no-daemon
```

Expected: the exact-ceiling test passes and existing connector/floor GameTests remain green; if an unrelated navigation GameTest is already failing from dirty navigation work, confirm the floor batch passes and preserve that unrelated failure unchanged.

- [ ] **Step 5: Add physical-owner precedence regression**

Add a second focused GameTest with a lower Floor offering a landing handoff and an upper persisted Floor physically owning the same coordinate. Assert the upper Floor/Room wins. This test must fail if candidate ranking is changed to prefer a generic handoff over physical ownership.

- [ ] **Step 6: Run the GameTest server and verify GREEN again**

Run `./gradlew :neoforge:runGameTestServer --no-daemon` and confirm both interaction regressions pass.

- [ ] **Step 7: Commit the interaction slice**

```text
git add common/src/main/java/net/conczin/mca/server/world/data/Structure.java
git add common/src/main/java/net/conczin/mca/server/world/data/Village.java
git add neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java
git commit -m "fix: resolve floor landing handoffs explicitly"
```

### Task 2: Explicit Room, Floor, and external-site mutation APIs

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Village.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/VillageFloorSystemTest.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/server/world/data/ExternalBuildingGameTests.java`

**Interfaces:**
- Consumes: existing `Village.canRemoveFloor(...)`, `removeFloor(...)`, `removeLogicalBuilding(...)`.
- Produces: `boolean removeRoom(int roomId)` and `boolean removeExternalBuilding(int buildingId)`; removes ambiguous `Village.removeBuilding(int)` from normal domain mutation.

- [ ] **Step 1: Write failing API/lifecycle unit tests**

Change existing Room-removal tests to call `removeRoom(...)` and assert the boolean result. Add explicit no-op coverage for a Main Room and for an unknown Room ID:

```java
assertFalse(village.removeRoom(main.getId()));
assertTrue(village.getBuilding(main.getId()).isPresent());
assertFalse(village.removeRoom(999));
```

Add an external-site test at the domain level or adapt the existing GameTest so the manager path proves it delegates to external-site removal without affecting functional Rooms.

- [ ] **Step 2: Run focused common tests and verify RED**

Run:

```text
./gradlew :common:test --tests net.conczin.mca.server.world.data.VillageFloorSystemTest --no-daemon
```

Expected: compilation/test failure because `removeRoom(int)`/`removeExternalBuilding(int)` do not exist yet.

- [ ] **Step 3: Implement the minimal explicit domain methods**

Replace `Village.removeBuilding(int)` with narrowly named operations:

```java
public boolean removeRoom(int roomId) {
    Building room = buildings.get(roomId);
    if (room == null || isMainRoom(room)) return false;
    buildings.remove(roomId);
    refreshLogicalBuildings();
    calculateDimensions();
    markDirty();
    return true;
}

public boolean removeExternalBuilding(int buildingId) {
    if (externalBuildings.remove(buildingId) == null) return false;
    calculateDimensions();
    markDirty();
    return true;
}
```

Update `VillageManager.removeRoom(...)` to call `village.removeRoom(...)`. Update `VillageManager.removeBuilding(...)` so external sites call `removeExternalBuilding(...)`, orphan cleanup calls `removeRoom(...)`, and whole logical-building deletion remains `removeLogicalBuilding(...)`. Do not make Room removal call Floor removal.

- [ ] **Step 4: Run focused tests and verify GREEN**

Run:

```text
./gradlew :common:test --tests net.conczin.mca.server.world.data.VillageFloorSystemTest :neoforge:compileJava --no-daemon
```

Expected: unit tests pass and all manager/GameTest source compiles.

- [ ] **Step 5: Run the GameTest server for external-site/removal integration**

Run `./gradlew :neoforge:runGameTestServer --no-daemon` and confirm the external removal and floor removal batches remain green.

- [ ] **Step 6: Commit the mutation slice**

```text
git add common/src/main/java/net/conczin/mca/server/world/data/Village.java
git add common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java
git add common/src/test/java/net/conczin/mca/server/world/data/VillageFloorSystemTest.java
git add neoforge/src/main/java/net/conczin/mca/server/world/data/ExternalBuildingGameTests.java
git commit -m "refactor: make building mutations explicit"
```

### Task 3: Lock building intent and inheritance UX without changing persistence

**Files:**
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/VillageFloorSystemTest.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/RoomTypeResolverTest.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`
- Modify only if a failing invariant proves a production correction is necessary: `common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java`
- Modify only if a failing invariant proves a production correction is necessary: `common/src/main/java/net/conczin/mca/server/world/data/RoomTypeResolver.java`

**Interfaces:**
- Consumes: current Add Building/Add Floor/Add Basement flows, `logicalBuildingId`, Main Room, `contributesToMain`, `RoomTypeResolver`.
- Produces: regression coverage proving the current UX rather than a new persistence model.

- [ ] **Step 1: Add multi-floor inheritance unit coverage**

Construct one `LogicalBuilding` using two Structure owners at different Y values with the same logical-building ID. Put the Main Room on Ground and a contributing Room upstairs with a POI. Assert `RoomTypeResolver.create(village).resolve(main).contributors()` contains the upstairs Room and its POI appears in the Main Room's `effectivePoi()`.

The production change that would make this test fail is scoping contributor lookup by Structure/Floor instead of `logicalBuildingId`.

- [ ] **Step 2: Add stacked-independent-building unit coverage**

Construct a second Structure at a higher Y with a different logical-building ID and a Restaurant/Cashier Room containing a POI. Assert it is absent from the Inn Main Room's contributors/effective POIs and has its own Main Room through its own `LogicalBuilding`.

The production change that would make this test fail is merging inheritance by physical stacking/proximity rather than explicit logical-building identity.

- [ ] **Step 3: Run focused unit tests**

Run:

```text
./gradlew :common:test --tests net.conczin.mca.server.world.data.VillageFloorSystemTest --tests net.conczin.mca.server.world.data.RoomTypeResolverTest --no-daemon
```

Expected: tests should already pass if the current implementation matches the intended UX. If either fails, make only the minimal production correction required by the invariant and rerun RED/GREEN for that specific test.

- [ ] **Step 4: Add a GameTest for explicit Add Building vs Add Floor intent**

Build two vertically stacked enclosed storeys. Register the lower storey as LogicalBuilding `10`, then analyze the upper storey twice using the two explicit actions:

```java
VillageManager manager = new VillageManager(helper.getLevel());
RoomScanPlan addFloor = RoomScanPlan.attachment(10, 1, upperSeed, upperSeed);
BuildingScanResult attached = manager.analyzeAttachedRoom(
        village, addFloor, Village.RoomScanMode.ADD_FLOOR, 10);

helper.assertTrue(attached.result() == Building.validationResult.SUCCESS,
        "Add Floor should analyze the upper storey as an attachment");
helper.assertTrue(attached.pendingStructure() != null
                && attached.pendingStructure().getLogicalBuildingId() == 10,
        "Add Floor must retain the explicitly selected logical building id");

BuildingScanResult independent = new VillageManager(helper.getLevel()).analyzeBuildingAddition(upperSeed);
helper.assertTrue(independent.result() == Building.validationResult.SUCCESS,
        "Add Building should independently analyze the same upper storey");
helper.assertTrue(independent.targetBuildingId() < 0,
        "Add Building must not inherit the lower building solely because it is vertically stacked");
```

The fixture should use the same upper scan seed for both analyses. The difference must come from the explicit action/plan, not geometry or Y inference.

- [ ] **Step 5: Commit the invariant-coverage slice**

```text
git add common/src/test/java/net/conczin/mca/server/world/data/VillageFloorSystemTest.java
git add common/src/test/java/net/conczin/mca/server/world/data/RoomTypeResolverTest.java
git add neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java
git add common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java
git add common/src/main/java/net/conczin/mca/server/world/data/RoomTypeResolver.java
git commit -m "test: lock multi-floor building identity invariants"
```

Only stage production files in this commit if Step 3/4 actually required a production correction.

### Task 4: Diagnostics and full verification

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/BuildingDiagnostics.java`
- Test/verify: existing common tests and NeoForge GameTests.

**Interfaces:**
- Consumes: `Structure.InteractionPosition.kind()` and existing Room/Floor/logical-building diagnostics.
- Produces: diagnostics that say whether interaction resolved as physical, connector, or landing handoff without introducing a new public result hierarchy.

- [ ] **Step 1: Update diagnostics to print interaction kind**

Resolve the inspected Structure's `InteractionPosition` once and include at least:

```text
interactionKind=LANDING_HANDOFF
floorId=...
floorNumber=...
verticalDistance=1
```

Keep the diagnostic read-only. Do not add persistence or mutation behavior.

- [ ] **Step 2: Run focused compilation/tests**

Run:

```text
./gradlew :common:test --tests net.conczin.mca.server.world.data.VillageFloorSystemTest --tests net.conczin.mca.server.world.data.RoomTypeResolverTest :neoforge:compileJava --no-daemon
```

- [ ] **Step 3: Run the complete common test suite**

Run:

```text
./gradlew :common:test --no-daemon
```

- [ ] **Step 4: Run the NeoForge GameTest server**

Run:

```text
./gradlew :neoforge:runGameTestServer --no-daemon
```

Record the exact pass/failure summary. Do not claim unrelated dirty navigation GameTests pass unless this run proves it.

- [ ] **Step 5: Run whitespace and repository-state checks**

Run:

```text
git diff --check
git status --short
```

Confirm only intentionally preserved unrelated navigation changes remain dirty after the floor/building work is committed.

- [ ] **Step 6: Commit diagnostics if changed**

```text
git add common/src/main/java/net/conczin/mca/server/world/data/BuildingDiagnostics.java
git commit -m "chore: expose floor interaction diagnostics"
```

Skip this commit if diagnostics required no source change.
