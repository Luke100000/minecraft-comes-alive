# Selected Floor Boundary Semantics Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the remaining selected-Floor boundary machinery around its actual local invariants without changing Floor geometry, Room partitioning, attachment behavior, or persistence.

**Architecture:** Keep `SelectedFloorScanner` as the owner of one selected Floor scan. Preserve its local three-state vertical-band classifier, expose retained vertical boundary cells separately from adjacent-Floor match seeds, and keep the narrow `StairBlock` recognition limited to transition disambiguation while Minecraft collision remains the physical-height authority. Do not add new evidence wrapper types or restore recursive discovery.

**Tech Stack:** Java 21, Minecraft 1.21.1, Architectury, Fabric, NeoForge GameTests, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-18-floor-architecture-boundary-cleanup-design.md`

## Global Constraints

- `FloorGeometry` remains the canonical persisted Floor model.
- `WalkNodeEvaluator.getFloorLevel(...)` remains the physical standing-height authority.
- One fresh scan resolves one selected Floor only; adjacent-floor evidence may match persisted Floors but may not trigger another world scan.
- Keep `OWNED` / `EDGE` / `OTHER` behavior; rename the local classifier rather than deleting it.
- Keep door/gate boundaries separate from vertical Floor-boundary cells.
- Do not restore recursive Floor discovery, Room reconciliation, sibling auto-registration, or lineage assignment.
- Do not introduce new public `BoundaryEvidence` / `TransitionEvidence` records in this cleanup.
- Production code must not grow: for each scoped production Java file, added lines must be less than or equal to deleted lines relative to the pre-cleanup baseline.
- Preserve unrelated dirty work. `RoomScanPlanner.java` and `FloorScannerGameTests.java` already contain unrelated uncommitted vertical-connector follow-up changes; edit/stage only the accessor/name hunks required by this plan.
- No save-format or gameplay-behavior changes are part of this plan.

---

## Task 1: Rename the selected-Floor classifier and transient scan model

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java:240-540, 838-935`

**Interfaces:**
- Consumes: existing `SelectedFloorScanner.scan(Level, BlockPos, int, int)` behavior and current `Result` data.
- Produces: `Result.verticalBoundaryCells()` and `Result.adjacentFloorSeeds()` with unchanged set contents; private `FloorBand`, `FloorBandRole`, `FloorBandClassifier`, and `SelectedFloorScan` names.

- [ ] **Step 1: Record a green compile baseline before renaming**

Run:

```powershell
.\gradlew.bat :common:compileJava :fabric:compileJava :neoforge:compileJava --no-daemon --no-build-cache
```

Expected: all three compile tasks pass. If the dirty worktree already fails, stop and preserve that exact pre-existing failure instead of attributing it to this refactor.

- [ ] **Step 2: Rename the private selected-Floor band types**

Change the private model to this shape without altering conditions:

```java
private enum FloorBandRole {
    OWNED, EDGE, OTHER
}

private record FloorBand(int anchorY) {
    int minOwnedY() {
        return anchorY - FLOOR_BAND_RADIUS;
    }

    int maxOwnedY() {
        return anchorY + FLOOR_BAND_RADIUS;
    }
}

private static final class FloorBandClassifier {
    private final Level world;
    private final FloorBand band;
    private final StepProvider provider;
    private final Map<BlockPos, FloorBandRole> roles = new HashMap<>();
    private final Map<BlockPos, Boolean> exterior = new HashMap<>();

    private FloorBandClassifier(Level world, FloorBand band, StepProvider provider) {
        this.world = world;
        this.band = band;
        this.provider = provider;
    }

    private FloorBandRole role(SurfaceCell cell) {
        return roles.computeIfAbsent(
                cell.feet(), ignored -> floorBandRole(world, band, cell, provider));
    }
}
```

Rename all call sites mechanically:

```text
StoreyContext      -> FloorBand
StoreyRole         -> FloorBandRole
StoreyClassifier   -> FloorBandClassifier
storeyRole(...)    -> floorBandRole(...)
```

Do not alter the `OWNED` / `EDGE` / `OTHER` decision tree.

- [ ] **Step 3: Rename selected-scan methods/record without changing data flow**

Rename:

```text
resolveStoreyAnchor(...) -> resolveSelectedFloorAnchor(...)
traverseStorey(...)      -> traverseSelectedFloor(...)
StoreyScan               -> SelectedFloorScan
```

The record remains structurally equivalent:

```java
private record SelectedFloorScan(
        Building.validationResult result,
        FloorGeometry floor,
        Set<Transition> transitions,
        Set<BlockPos> verticalBoundaryCells,
        Set<BlockPos> adjacentFloorSeeds,
        Set<BlockPos> connectors) {
}
```

- [ ] **Step 4: Rename the two package-visible result sets by invariant**

Change `SelectedFloorScanner.Result` to:

```java
record Result(Building.validationResult result,
              FloorGeometry floor,
              BlockPos min,
              BlockPos max,
              Set<Transition> transitions,
              Set<BlockPos> verticalBoundaryCells,
              Set<BlockPos> adjacentFloorSeeds) {
    Result {
        transitions = transitions == null ? Set.of() : Set.copyOf(transitions);
        verticalBoundaryCells = verticalBoundaryCells == null
                ? Set.of() : Set.copyOf(verticalBoundaryCells);
        adjacentFloorSeeds = adjacentFloorSeeds == null
                ? Set.of() : Set.copyOf(adjacentFloorSeeds);
    }
}
```

Rename local variables in `traverseSelectedFloor(...)`, `retainEnclosedRegions(...)`, and the success builder so the old names do not survive as aliases.

- [ ] **Step 5: Compile `common` to expose every stale accessor/type reference**

Run:

```powershell
.\gradlew.bat :common:compileJava --no-daemon --no-build-cache
```

Expected: FAIL at downstream references to `storeyEdgeCells()` / `transitionSeeds()` until Task 2 updates their consumers. Do not add compatibility accessors just to make this intermediate state compile.

---

## Task 2: Update Room partitioning and adjacent-Floor consumers

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java:29-38`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomPartitioner.java:45-115`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomScanPlanner.java:104-116`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java:250-262`

**Interfaces:**
- Consumes: `SelectedFloorScanner.Result.verticalBoundaryCells()` and `.adjacentFloorSeeds()` from Task 1.
- Produces: unchanged Room components and attachment/overlap decisions; no new wrapper types.

- [ ] **Step 1: Update the single world-aware Room partition entry point**

Change `BuildingRoomScanner.components(...)` to pass the renamed vertical boundary set:

```java
return RoomPartitioner.partition(
        floor,
        scan.transitions(),
        StructureConnector.doorOwnerSides(world, floor),
        scan.verticalBoundaryCells());
```

- [ ] **Step 2: Rename the RoomPartitioner parameter/helper around vertical boundaries**

Use this signature:

```java
static List<Component> partition(FloorGeometry geometry,
                                 Collection<SelectedFloorScanner.Transition> transitions,
                                 Map<BlockPos, Direction> doorOwnerSides,
                                 Collection<BlockPos> verticalBoundaryCells)
```

Keep connector/door boundaries separate. Rename only the vertical-boundary locals/helper:

```text
storeyBoundaryCells                -> acceptedVerticalBoundaries
assignStoreyBoundaryClusters(...)  -> assignVerticalBoundaryClusters(...)
```

Do not merge these cells into `connectorBoundaryCells`; their assignment semantics are intentionally different.

- [ ] **Step 3: Update persisted-adjacent-Floor matching callers**

In `RoomScanPlanner.attachmentPlan(...)`, pass only the renamed evidence:

```java
Village.AttachmentTarget target = village.selectAttachmentTarget(
        candidateFloor,
        observation.verticalConnections(),
        observation.scan().adjacentFloorSeeds()).orElse(null);
```

In `StructureScanner.hasDirectFloorConnection(...)`, keep the existing persisted-floor lookup but use:

```java
return selected.adjacentFloorSeeds().stream().anyMatch(seed ->
        structure.getFloors().stream().anyMatch(floor ->
                floor.geometry().interactionCellAt(
                        seed.getX(), seed.getY(), seed.getZ()).isPresent()));
```

Do not call `SelectedFloorScanner.scan(...)` from either consumer.

- [ ] **Step 4: Compile all production source sets**

Run:

```powershell
.\gradlew.bat :common:compileJava :fabric:compileJava :neoforge:compileJava --no-daemon --no-build-cache
```

Expected: PASS.

- [ ] **Step 5: Commit the ownership-name migration without staging unrelated dirty hunks**

Stage clean files normally and use patch staging for the already-dirty planner:

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java `
        common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java `
        common/src/main/java/net/conczin/mca/server/world/data/RoomPartitioner.java `
        common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java
git add -p common/src/main/java/net/conczin/mca/server/world/data/RoomScanPlanner.java
git diff --cached --check
git commit -m "refactor: clarify selected floor boundary semantics"
```

When staging `RoomScanPlanner.java`, include only the `transitionSeeds()` -> `adjacentFloorSeeds()` hunk; leave the pre-existing vertical-connector handoff hunk unstaged.

---

## Task 3: Make stair-specific semantics narrow and explicit

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java:342-359, 498-540`
- Modify: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java:681-850, 977-1000`

**Interfaces:**
- Consumes: `FloorBand` / `FloorBandClassifier` from Task 1.
- Produces: unchanged stair/landing ownership behavior with helper names that state this is transition disambiguation, not physical floor-height calculation.

- [ ] **Step 1: Rename the generic descending helpers around the selected Floor band**

Rename mechanically:

```text
isDescendingStoreyTransition(...) -> isDescendingFloorBoundary(...)
descendsBelowOwnedBand(...)        -> descendsBelowFloorBand(...)
```

Keep all predicates and thresholds unchanged.

- [ ] **Step 2: Rename and document the explicit stair hint**

Replace the old naming with:

```java
/**
 * Stair identity is only a transition-disambiguation hint. Physical standing height is still
 * derived from collision through WalkNodeEvaluator.getFloorLevel(...).
 */
private static boolean isExplicitStairTransitionCell(Level world, BlockPos feet) {
    return world.getBlockState(feet).getBlock() instanceof StairBlock
            || world.getBlockState(feet.below()).getBlock() instanceof StairBlock;
}
```

Rename:

```text
isStairOccupancy(...)            -> isExplicitStairTransitionCell(...)
descendsFullStoreyFromStair(...) -> stairTransitionReachesBelowBand(...)
```

Do not remove `StairBlock` in this task. The existing full-block staircase test remains the proof that topology-only transitions also work where no StairBlock identity exists.

- [ ] **Step 3: Update test accessor names/messages, preserving existing behavior assertions**

In `FloorScannerGameTests`, update accessor assertions such as:

```java
helper.assertTrue(lower.verticalBoundaryCells().contains(topStair),
        "lower Floor did not retain the top stair as a vertical Room-partition boundary");
helper.assertTrue(!lower.adjacentFloorSeeds().isEmpty(),
        "stair attachment evidence was lost");
```

and:

```java
helper.assertTrue(lower.verticalBoundaryCells().contains(topTransition),
        "full-block descent did not retain its terminal vertical boundary cell");
```

Do not rename physical-scenario test methods merely to remove the word `storey`; those names still describe the fixtures accurately.

- [ ] **Step 4: Run the NeoForge GameTest server as the behavioral gate**

Run:

```powershell
.\gradlew.bat :neoforge:runGameTestServer --no-daemon --no-build-cache
```

Required Floor regressions include:

```text
staircaseKeepsUpperRoomOutOfLowerStorey
flatUpperLandingBesideDescentRemainsFloorCell
twoBlockStaircaseSeparatesBroadStoreys
selectedFloorScanDoesNotRecursivelyDiscoverDeepStairChain
fullBlockStaircaseDoesNotMergeLowerRooms
slabAndStairUseTransientSurfaceEvidence
```

Expected for this refactor: Floor/Room tests remain green. If the complete server run hits a known unrelated nondeterministic test, record the exact failing test and do not weaken Floor assertions.

- [ ] **Step 5: Stage only the accessor/message hunks in the already-dirty GameTest file and commit**

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
git add -p neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java
git diff --cached --check
git commit -m "refactor: name floor transition semantics explicitly"
```

Leave the pre-existing `verticalConnectorWithoutPersistedMarkerHandsOffToExistingRoom` addition unstaged unless it has already been committed separately.

---

## Task 4: Final architecture and regression verification

**Files:**
- Review: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`
- Review: `common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java`
- Review: `common/src/main/java/net/conczin/mca/server/world/data/RoomPartitioner.java`
- Review: `common/src/main/java/net/conczin/mca/server/world/data/RoomScanPlanner.java`
- Review: `common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java`
- Review: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`

**Interfaces:**
- Consumes: final names from Tasks 1-3.
- Produces: verified selected-Floor architecture with no compatibility aliases or recursive-discovery vocabulary in production ownership code.

- [ ] **Step 1: Search for stale ownership symbols**

Run:

```powershell
rg -n "StoreyContext|StoreyRole|StoreyClassifier|StoreyScan|storeyEdgeCells|transitionSeeds|isStairOccupancy|descendsFullStoreyFromStair|isDescendingStoreyTransition|descendsBelowOwnedBand" `
  common/src/main/java/net/conczin/mca/server/world/data `
  neoforge/src/main/java/net/conczin/mca/server/world/data
```

Expected: no production references. Test method/batch names containing the ordinary word `storey` are allowed and are not part of this symbol list.

- [ ] **Step 2: Prove adjacent-Floor evidence cannot recursively scan**

Run:

```powershell
rg -n "adjacentFloorSeeds|SelectedFloorScanner\.scan" `
  common/src/main/java/net/conczin/mca/server/world/data/RoomScanPlanner.java `
  common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java
```

Inspect the matches. `adjacentFloorSeeds` consumers must only compare against persisted geometry / attachment state; there must be no `scan(...)` call driven by one of those seeds.

- [ ] **Step 3: Run unit tests and loader compiles**

Run:

```powershell
.\gradlew.bat :common:test :fabric:compileJava :neoforge:compileJava --no-daemon --no-build-cache
```

Expected: PASS.

- [ ] **Step 4: Run the final NeoForge GameTest server**

Run:

```powershell
.\gradlew.bat :neoforge:runGameTestServer --no-daemon --no-build-cache
```

Expected: all Floor/Room GameTests pass. A complete-suite success claim requires the server's own final success output; otherwise report any unrelated nondeterministic failure separately.

- [ ] **Step 5: Review the final diff for accidental behavior changes**

Run:

```powershell
git diff --check
git diff --numstat -- common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java `
                    common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java `
                    common/src/main/java/net/conczin/mca/server/world/data/RoomPartitioner.java `
                    common/src/main/java/net/conczin/mca/server/world/data/RoomScanPlanner.java `
                    common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java
git diff -- common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java `
           common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java `
           common/src/main/java/net/conczin/mca/server/world/data/RoomPartitioner.java `
           common/src/main/java/net/conczin/mca/server/world/data/RoomScanPlanner.java `
           common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java `
           neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java
```

The production diff should be semantic renaming/comments only, and every listed production file must have additions less than or equal to deletions. Any changed branch condition, threshold, Floor-cell membership rule, partition algorithm, or persistence logic is outside this plan and must be removed or split into a separately designed behavior change.
