# Enclosed Volume Floor Discovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace generic post-traversal Floor membership expansion with one bounded enclosed-volume observation that directly supplies the supported cells used by `SelectedFloorScanner`.

**Architecture:** `SelectedFloorScanner.Observation` discovers one transient roof-bounded 3D interior candidate volume from the live world, derives supported `SurfaceCell`s from that volume, and then runs the existing storey classifier only over those supported candidates. Existing selected-storey exterior validation remains the final enclosure proof so an open upper storey or exterior canopy cannot poison an enclosed lower/inner region. `FloorGeometry` remains the only canonical/persisted spatial model; unsupported ladder/trapdoor exits resolve interactions without becoming ordinary Floor cells.

**Tech Stack:** Java 21, Minecraft 1.21.1, Architectury/Fabric/NeoForge multi-loader Gradle project, NeoForge GameTests.

**Spec:** `docs/superpowers/specs/2026-09-17-enclosed-volume-floor-discovery-design.md`

## Global Constraints

- Work first in `C:/Users/Mik/Downloads/MCA/minecraft-comes-alive-1.21.1-floor-clean-squash` on `dev/1.21.1`.
- Treat `C:/Users/Mik/Downloads/MCA/mc_source_code/local-source/src/main/java` as the Minecraft 1.21.1 behavior/API oracle and never modify it.
- Preserve unrelated untracked docs/logs and any peer work; stage only files named by the current task.
- Keep `FloorGeometry` as the only canonical/persisted exact Floor-cell representation.
- The enclosed 3D volume is scan-local transient data only.
- Reuse `FloorCeilingResolver`, `WalkNodeEvaluator.getFloorLevel(...)`, `StructureConnector`, `Transition`, `StoreyClassifier`, and the existing scan-local step cache.
- Do not special-case `ShelfBlock`, `HopperBlock`, `BedBlock`, copied-house coordinates, or a specific Y value.
- Do not add a public topology/graph abstraction or another persisted geometry type.
- Reject fluids for Floor/interior discovery initially, matching the current scanner, unless a focused existing regression proves otherwise.
- Horizontal doors/gates remain Room boundaries. Roofed candidates beyond a boundary may be observed, but the existing selected-storey exterior validation must prune an open canopy/exterior region without invalidating the enclosed room behind the boundary.
- Stairs/slabs use Minecraft collision/floor-height data. Keep current storey-boundary policy until a focused regression proves a separate storey-classifier change is needed.
- Ordinary Floor cells must come from the enclosed-volume observation. Semantic connector handoff may select a Floor but must not manufacture an unsupported ordinary `FloorGeometry.Cell`.
- Apply `java-code-review-cleanup` to one fixed final Java diff across reuse, quality, correctness, and efficiency lenses before completion.

## Local-source findings that constrain the implementation

Minecraft 1.21.1 `WalkNodeEvaluator` already separates these concerns:

```java
public static double getFloorLevel(BlockGetter blockgetter, BlockPos blockpos) {
    BlockPos below = blockpos.below();
    VoxelShape shape = blockgetter.getBlockState(below).getCollisionShape(blockgetter, below);
    return below.getY() + (shape.isEmpty() ? 0.0 : shape.max(Direction.Axis.Y));
}
```

Its neighbour search independently applies path type, collision and upward-step checks. MCA should therefore reuse the physical floor height and its own existing local transition logic rather than invent block-name-based movement rules.

The current scanner has two competing Floor-cell producers:

1. `traverseStorey(...)`, which adds physically supported traversal cells.
2. `includeInteriorMembership(...)`, which later adds neighbouring cells by a different rule.

This plan removes the second producer.

The local regression review also proves that enclosure must remain selected-storey aware. `lowerExteriorCheckDoesNotClimbOpenUpperStorey` requires an open upper storey to leave the lower storey valid, while `roofedExteriorAcrossDoorIsNotOwnedFloorGeometry` requires a roofed-but-open canopy to be pruned behind an exterior door. Therefore this plan does **not** delete `retainEnclosedRegions(...)`/`reachesExterior(...)`; those methods remove exterior candidates and do not manufacture Floor membership.

## File map

| File | Responsibility in this change |
| --- | --- |
| `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java` | Enclosed-volume discovery, supported-cell extraction, volume-scoped storey traversal, connector interaction handoff. |
| `common/src/main/java/net/conczin/mca/server/world/data/FloorCeilingResolver.java` | Reused unchanged unless a focused test proves a cache/API adjustment is necessary. |
| `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java` | Focused physical-volume, furniture, door, stair and connector regressions. |
| `neoforge/src/main/java/net/conczin/mca/server/world/data/CopiedOpenHouseGameTests.java` | Real copied-house acceptance; no fixture rewrite on 1.21.1. |

---

### Task 1: Lock the new Floor-cell contract with focused red GameTests

**Files:**
- Modify: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`

**Interfaces:**
- Consumes: existing `SelectedFloorScanner.scan(Level, BlockPos, int, int)` and `FloorGeometry.cellAt(BlockPos)`.
- Produces: regression contracts for same-level full-height partial obstacles, high ceilings, and semantic ladder-top interaction.

- [ ] **Step 1: Add the same-level full-height partial-obstacle regression**

Add next to `raisedFullHeightPartialObstacleDoesNotBecomeFloorMembership`:

```java
@GameTest(batch = "mca_floor_partial_obstacle", templateNamespace = "minecraft",
        template = "bastion/blocks/air", timeoutTicks = 80)
public static void fullHeightPartialObstacleDoesNotBecomeFloorCell(GameTestHelper helper) {
    BlockPos roomMin = helper.absolutePos(new BlockPos(4, 2, 4));
    buildClosedRoom(helper, roomMin, 5, 4);
    BlockPos blocked = roomMin.offset(1, 0, 1);
    helper.getLevel().setBlock(blocked, Blocks.HOPPER.defaultBlockState(), 3);

    SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(
            helper.getLevel(), roomMin.offset(3, 0, 2), 128, 16);

    helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
            "room scan failed: " + scan.result());
    helper.assertTrue(scan.floor().cellAt(blocked).isEmpty(),
            "full-height partial obstacle became ordinary Floor geometry");
    helper.succeed();
}
```

This is the direct 1.21.1 reproduction of the same-Y ownership path that the 26.3 cherry shelf exposed. It must fail before production code changes.

- [ ] **Step 2: Add a high-ceiling regression that checks exact cells, not projection**

Add a helper that raises the roof without changing the footprint:

```java
private static void raiseClosedRoomRoof(
        GameTestHelper helper, BlockPos min, int width, int depth, int roofOffset) {
    var level = helper.getLevel();
    for (int x = -1; x <= width; x++) {
        for (int z = -1; z <= depth; z++) {
            BlockPos column = min.offset(x, 0, z);
            boolean wall = x == -1 || x == width || z == -1 || z == depth;
            for (int y = 2; y < roofOffset; y++) {
                level.setBlock(column.above(y),
                        wall ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(), 3);
            }
            level.setBlock(column.above(roofOffset), Blocks.STONE.defaultBlockState(), 3);
        }
    }
}
```

Then add:

```java
@GameTest(batch = "mca_floor_high_ceiling", templateNamespace = "minecraft",
        template = "bastion/blocks/air", timeoutTicks = 80)
public static void highCeilingDoesNotCreateExtraFloorLayers(GameTestHelper helper) {
    BlockPos roomMin = helper.absolutePos(new BlockPos(4, 2, 4));
    buildClosedRoom(helper, roomMin, 5, 4);
    raiseClosedRoomRoof(helper, roomMin, 5, 4, 5);

    SelectedFloorScanner.Result scan = SelectedFloorScanner.scan(
            helper.getLevel(), roomMin.offset(2, 0, 2), 256, 16);

    helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
            "high-ceiling room scan failed: " + scan.result());
    helper.assertTrue(scan.floor().cells().stream()
                    .allMatch(cell -> cell.feet().getY() == roomMin.getY()),
            "high ceiling created Floor cells above the supported room layer");
    helper.succeed();
}
```

- [ ] **Step 3: Change the ladder-top regression to test handoff instead of unsupported geometry**

Rename `airAboveLadderIsFloorMembershipCell` to `airAboveLadderResolvesFloorWithoutInventingSupport` and change its final assertions to:

```java
helper.assertTrue(scan.result() == Building.validationResult.SUCCESS,
        "air above ladder did not resolve to its room Floor: " + scan.result());
helper.assertTrue(scan.floor().cellAt(interaction).isEmpty(),
        "unsupported ladder exit became ordinary Floor geometry");
helper.assertTrue(scan.floor().cellAt(roomMin.offset(1, 0, 2)).isPresent(),
        "ladder exit did not resolve to the supported room Floor");
helper.succeed();
```

In `ladderTopExitStaysOnUpperStorey`, change the geometry expectation from `isPresent()` to `isEmpty()`. Keep the later planner assertion proving the interaction still targets the upper registered Floor.

- [ ] **Step 4: Run the focused tests and record the red result**

Use a running NeoForge GameTest environment and run:

```text
/test run fullheightpartialobstacledoesnotbecomefloorcell
/test run highceilingdoesnotcreateextrafloorlayers
/test run airaboveladderresolvesfloorwithoutinventingsupport
/test run laddertopexitstaysonupperstorey
```

Expected before implementation:

- the same-level hopper test fails because `includeInteriorMembership()` adds the hopper cell;
- the updated ladder geometry assertions fail because the old post-pass adds unsupported exit membership;
- the high-ceiling test should pass and serves as a guard against deriving a Floor at every interior Y.

- [ ] **Step 5: Commit only the red/contract tests**

```powershell
git add neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java
git commit -m "test: define enclosed volume floor membership"
```

---

### Task 2: Discover and cache the bounded enclosed interior volume

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`

**Interfaces:**
- Consumes: `FloorCeilingResolver.ceilingY(BlockPos)`, `StructureConnector.isHorizontalBoundary(BlockState)`, `StructureConnector.isVertical(Level, BlockPos)`, existing `maxSize`/`maxRadius` limits.
- Produces: private `EnclosedVolume` with exact transient interior cells and directly derived supported `SurfaceCell`s.

- [ ] **Step 1: Add one private transient observation type**

Near the existing private records, add:

```java
private record EnclosedVolume(
        Building.validationResult result,
        Set<BlockPos> cells,
        Map<BlockPos, SurfaceCell> supportedCells) {

    private static EnclosedVolume failure(Building.validationResult result) {
        return new EnclosedVolume(result, Set.of(), Map.of());
    }

    private static EnclosedVolume success(
            Set<BlockPos> cells, Map<BlockPos, SurfaceCell> supportedCells) {
        return new EnclosedVolume(
                Building.validationResult.SUCCESS,
                Set.copyOf(cells),
                Map.copyOf(supportedCells));
    }
}
```

Keep it private and scan-local. Do not expose it through `Result` or persistence.

- [ ] **Step 2: Give `Observation` one cache for discovered volumes**

Add:

```java
private final Map<BlockPos, EnclosedVolume> volumesByCell = new HashMap<>();
```

and:

```java
private EnclosedVolume enclosedVolume(BlockPos seed) {
    EnclosedVolume cached = volumesByCell.get(seed);
    if (cached != null) return cached;

    EnclosedVolume discovered = discoverEnclosedVolume(
            world, seed, ceilings, maxSize, maxRadius);
    if (discovered.result() == Building.validationResult.SUCCESS) {
        discovered.cells().forEach(cell -> volumesByCell.putIfAbsent(cell, discovered));
    }
    return discovered;
}
```

This avoids repeating the same 3D flood fill when connected storeys resolve from seeds inside the same building volume.

- [ ] **Step 3: Add the interior occupancy predicate**

Use collision facts and existing connector semantics:

```java
private static boolean isInteriorVolumeCell(Level world, BlockPos pos) {
    BlockState state = world.getBlockState(pos);
    if (!state.getFluidState().isEmpty()) return false;
    if (StructureConnector.isHorizontalBoundary(state)
            || StructureConnector.isVertical(world, pos)) return true;

    var shape = state.getCollisionShape(world, pos);
    return shape.isEmpty() || shape.max(Direction.Axis.Y) < 1.0D;
}
```

This admits air and genuinely sub-full room occupancy such as carpet/bed cells. Full cubes and full-height partial collision such as the hopper/shelf case remain boundaries.

- [ ] **Step 4: Add bounded six-direction volume discovery**

Add a `VOLUME_DIRECTIONS` constant using all six directions:

```java
private static final Direction[] VOLUME_DIRECTIONS = Direction.values();
```

Then add:

```java
private static EnclosedVolume discoverEnclosedVolume(
        Level world,
        BlockPos seed,
        FloorCeilingResolver ceilings,
        int maxSize,
        int maxRadius) {
    if (!isInteriorVolumeCell(world, seed)) {
        return EnclosedVolume.failure(Building.validationResult.NOT_IN_BUILDING);
    }

    ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    LinkedHashSet<BlockPos> cells = new LinkedHashSet<>();
    queue.addLast(seed.immutable());
    cells.add(seed.immutable());

    while (!queue.isEmpty()) {
        BlockPos current = queue.removeFirst();
        if (horizontalDistance(current, seed) >= maxRadius) {
            return EnclosedVolume.failure(Building.validationResult.SIZE_LIMIT);
        }

        for (Direction direction : VOLUME_DIRECTIONS) {
            BlockPos next = current.relative(direction);
            if (cells.contains(next) || !isInteriorVolumeCell(world, next)) continue;

            if (ceilings.ceilingY(next).isEmpty()) {
                continue;
            }

            cells.add(next.immutable());
            queue.addLast(next.immutable());
            if (cells.size() > maxSize) {
                return EnclosedVolume.failure(Building.validationResult.BLOCK_LIMIT);
            }
        }
    }

    return EnclosedVolume.success(cells, supportedCellsInVolume(world, cells, ceilings));
}
```

The volume pass only collects roof-bounded candidates. It does not decide the final exterior verdict. `retainEnclosedRegions(...)`/`reachesExterior(...)` remain responsible for proving that the selected storey region is enclosed and for pruning roofed-but-open canopy branches. This preserves `lowerExteriorCheckDoesNotClimbOpenUpperStorey`, `roofedExteriorAcrossDoorIsNotOwnedFloorGeometry`, `openRoofBridgeDoesNotMergeTwoHouses`, and `enclosedRoofedCorridorRemainsIndoorGeometry`.

- [ ] **Step 5: Derive supported candidates exactly once from the volume**

Add:

```java
private static Map<BlockPos, SurfaceCell> supportedCellsInVolume(
        Level world, Set<BlockPos> volume, FloorCeilingResolver ceilings) {
    LinkedHashMap<BlockPos, SurfaceCell> supported = new LinkedHashMap<>();
    for (BlockPos cell : volume.stream().sorted(CELL_ORDER).toList()) {
        BlockPos below = cell.below();
        if (volume.contains(below) && isLowObstacle(world, below)) continue;

        SurfaceCell surface = inspectSurfaceCell(world, cell, ceilings).orElse(null);
        if (surface != null) supported.put(cell.immutable(), surface);
    }
    return Map.copyOf(supported);
}
```

The `volume.contains(below) && isLowObstacle(...)` normalization is required for beds/carpet: the occupied lower integer cell remains the Floor cell, and air immediately above it does not become a second canonical cell merely because the furniture has a collision top.

- [ ] **Step 6: Compile before changing storey traversal**

Run:

```powershell
.\gradlew.bat :common:compileJava :neoforge:compileJava --no-daemon
```

Expected: compile succeeds. No behavior is claimed yet because the new observation is not wired into storey traversal.

- [ ] **Step 7: Commit the isolated volume-discovery code**

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
git commit -m "refactor: discover enclosed room volume"
```

---

### Task 3: Make the enclosed observation the only source of ordinary Floor candidates

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`

**Interfaces:**
- Consumes: `EnclosedVolume.supportedCells()`, existing physical `StepProvider`, `StoreyClassifier`, `traverseStorey(...)`.
- Produces: volume-scoped `StepProvider`; `traverseStorey` can only visit supported cells from the current enclosed observation.

- [ ] **Step 1: Add a volume-scoped wrapper around the existing physical step provider**

Add:

```java
private static StepProvider withinVolume(StepProvider physical, EnclosedVolume volume) {
    return cell -> physical.steps(cell).stream()
            .filter(step -> volume.supportedCells().containsKey(step.landing().feet()))
            .map(step -> new HorizontalStep(
                    volume.supportedCells().get(step.landing().feet()), step.connector()))
            .sorted(HorizontalStep.ORDER)
            .toList();
}
```

This reuses the current physical movement sampling and scan-local cache, while preventing that sampler from creating membership outside the already-discovered volume.

- [ ] **Step 2: Resolve the seed first, then discover its enclosed volume**

Move seed resolution into `Observation.resolve(...)` so one resolved seed drives both volume discovery and storey discovery:

```java
private StoreyResolution resolve(BlockPos seed) {
    return resolutions.computeIfAbsent(seed.immutable(), requested -> {
        SeedResolution seedResolution = resolveScanSeed(world, requested, ceilings).orElse(null);
        if (seedResolution == null) {
            return StoreyResolution.failure(Building.validationResult.NOT_IN_BUILDING);
        }

        EnclosedVolume volume = enclosedVolume(seedResolution.traversalSeed().feet());
        if (volume.result() != Building.validationResult.SUCCESS) {
            return StoreyResolution.failure(volume.result());
        }

        return resolveCanonicalStorey(
                world, seedResolution, volume, ceilings, provider, maxSize, maxRadius);
    });
}
```

Change `resolveCanonicalStorey(...)` to accept `SeedResolution` and `EnclosedVolume`; remove `requestedSeed`, but keep `maxRadius` because the existing selected-storey exterior validation still uses it.

- [ ] **Step 3: Use only the volume-scoped provider for anchor and storey classification**

At the start of `resolveCanonicalStorey(...)`:

```java
StepProvider enclosedSteps = withinVolume(provider, volume);
SurfaceCell traversalSeed = volume.supportedCells()
        .get(seedResolution.traversalSeed().feet());
if (traversalSeed == null) {
    return StoreyResolution.failure(Building.validationResult.NOT_IN_BUILDING);
}

traversalSeed = resolveStoreyAnchor(world, traversalSeed, enclosedSteps);
StoreyClassifier classifier = new StoreyClassifier(
        world, new StoreyContext(traversalSeed.feet().getY()), provider);
StoreyScan scan = traverseStorey(
        world, traversalSeed, ceilings, maxSize, maxRadius, enclosedSteps, classifier);
```

The exact `SurfaceCell` objects used by storey traversal now come from `volume.supportedCells()`, including their cached `surfaceY`/`ceilingY`. The classifier keeps the physical provider so storey-edge/exterior decisions can inspect real neighbouring movement without allowing those neighbours to become Floor cells.

- [ ] **Step 4: Keep selected-storey enclosure validation after candidate traversal**

Immediately after `traverseStorey(...)`, keep the current enclosure pass and give it the physical provider:

```java
scan = retainEnclosedRegions(
        world, traversalSeed, scan, ceilings, maxRadius, provider, classifier);
if (scan.result() != Building.validationResult.SUCCESS || scan.floor() == null) {
    return StoreyResolution.failure(scan.result());
}
```

Do not delete `retainEnclosedRegions(...)`, `reachesExterior(...)`, or the boundary continuation behavior in this implementation unless a separate focused red/green experiment proves them redundant. They only remove exterior candidates; they are not a competing Floor-cell producer.

- [ ] **Step 5: Stop requiring a semantic connector seed to exist as a Floor cell**

Delete:

```java
BlockPos membershipSeed = seedResolution.membershipSeed();
if (membershipSeed != null && scan.floor().cellAt(membershipSeed).isEmpty()) {
    return StoreyResolution.failure(Building.validationResult.NOT_IN_BUILDING);
}
```

`SeedResolution.membershipSeed` is no longer geometry validation. Rename it to `interactionSeed` if still useful to document the handoff, or remove that component entirely if no remaining caller consumes it.

- [ ] **Step 6: Run the focused new tests plus existing bed/carpet/door tests**

In the running GameTest environment:

```text
/test run fullheightpartialobstacledoesnotbecomefloorcell
/test run raisedfullheightpartialobstacledoesnotbecomefloormembership
/test run bedsdonotchangeroomfootprint
/test run scanfrombedtopusesunderlyingroomfloor
/test run carpetdoesnotchangeintegerroommembership
/test run roofedexterioracrossdoorisnotownedfloorgeometry
/test run openroofbridgedoesnotmergetwohouses
/test run enclosedroofedcorridorremainsindoorgeometry
```

If the GameTest command normalizes method names differently, use the exact names printed by `/test runall FloorScannerGameTests` rather than changing test code to satisfy the command parser.

Expected: all pass; the new same-level hopper test must now be green.

- [ ] **Step 7: Commit the canonical candidate-source change**

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
git commit -m "refactor: derive floors from enclosed volume"
```

---

### Task 4: Delete the competing membership path and keep connector handoff narrow

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java` only for the already-approved ladder expectation from Task 1 if it was kept red until this task.

**Interfaces:**
- Consumes: volume-scoped storey traversal from Task 3.
- Produces: one ordinary Floor-cell producer (`traverseStorey` over `EnclosedVolume.supportedCells`) and one semantic interaction handoff for vertical connectors.

- [ ] **Step 1: Remove generic post-traversal membership expansion**

Delete `includeInteriorMembership(...)` completely and return the retained/direct `StoreyScan` without a second membership pass.

Delete private helpers that become unused:

```text
isInteriorMembershipOccupancy
isSingleOpenLayerMembership
isInteriorMembershipCandidatePair
isInteriorMembershipBoundary
```

Do not replace them with a renamed generic neighbour-membership helper.

- [ ] **Step 2: Keep the storey-aware enclosure path and verify it only removes cells**

Keep `retainEnclosedRegions(...)`, `enclosedBoundaryContinuations(...)`, `reachesExterior(...)`, `connectedRegions(...)`, and their exterior cache for this change. Verify by inspection that they only retain/remove cells from the storey produced by `traverseStorey(...)` and never add an ordinary `FloorGeometry.Cell`.

If the new volume-scoped traversal makes one of these helpers obviously dead, leave that deletion for a separate focused cleanup after the full storey/canopy suite is green. YAGNI is preferable to combining two topology changes.

- [ ] **Step 3: Narrow interaction handoff to verified vertical connector exits**

Replace the old broad handoff predicate with:

```java
private static OptionalDouble interactionHandoffY(
        Level world, BlockPos pos, FloorCeilingResolver ceilings) {
    if (!isVerticalConnectorTopExit(world, pos)) return OptionalDouble.empty();
    if (inspectSurfaceCell(world, pos.above(), ceilings).isPresent()) return OptionalDouble.empty();
    return OptionalDouble.of(pos.getY());
}
```

Update `adjacentTraversalSeed(...)` to call `interactionHandoffY(...)`.

This helper selects a nearby supported traversal seed. It never inserts `pos` into `FloorGeometry`.

- [ ] **Step 4: Simplify `SeedResolution` if possible**

If `membershipSeed`/`interactionSeed` is no longer read after Task 3, replace:

```java
private record SeedResolution(SurfaceCell traversalSeed, BlockPos membershipSeed) {
}
```

with direct `Optional<SurfaceCell>` seed resolution and remove the record. Prefer deleting the wrapper over keeping dead semantic state.

If the record remains necessary to distinguish direct support from connector handoff in the planner path, keep exactly two fields with descriptive names and no boolean flags.

- [ ] **Step 5: Check for dead imports and special-case residue**

Run:

```powershell
rg -n "includeInteriorMembership|isInteriorMembership|membershipHandoffY|interactionMembershipHandoffY" common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
```

Expected: no hits.

Then confirm the enclosure-only helpers still exist:

```powershell
rg -n "retainEnclosedRegions|reachesExterior|enclosedBoundaryContinuations" common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
```

Expected: hits remain.

Also inspect `StairBlock` usage:

```powershell
rg -n "StairBlock|isStairOccupancy" common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
```

Do **not** remove the existing stair-storey compatibility rule in this task unless the focused stair suite proves it is redundant. The volume redesign and storey-classifier cleanup are separate behavior changes.

- [ ] **Step 6: Run focused connector and enclosure regressions**

```text
/test run airaboveladderresolvesfloorwithoutinventingsupport
/test run laddertopexitstaysonupperstorey
/test run laddertrapdoorattachesfloorswithoutmergingrooms
/test run singlesideddoorcellbelongstoitsonlyroom
/test run roofedexterioracrossdoorisnotownedfloorgeometry
/test run openroofbridgedoesnotmergetwohouses
/test run enclosedroofedcorridorremainsindoorgeometry
```

Expected: all pass, with unsupported ladder exit positions absent from ordinary Floor geometry while planner/storey targeting remains correct.

- [ ] **Step 7: Commit the deletion/simplification**

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java
git commit -m "refactor: remove secondary floor membership"
```

---

### Task 5: Prove storey separation and the copied-house regression on the canonical branch

**Files:**
- Test: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`
- Test: `neoforge/src/main/java/net/conczin/mca/server/world/data/CopiedOpenHouseGameTests.java`
- Modify production code only if one of these tests exposes a defect directly caused by the enclosed-volume change.

**Interfaces:**
- Consumes: canonical volume-derived `FloorGeometry` from Tasks 2-4.
- Produces: acceptance evidence for stairs, slabs, caves, connected storeys, and copied-house basement/lower ownership.

- [ ] **Step 1: Run the physical/storey regression group**

Run these in the GameTest environment:

```text
/test run staircasekeepsupperroomoutoflowerstorey
/test run flatupperlandingbesidedescentremainsfloorcell
/test run twoblockstaircaseseparatesbroadstoreys
/test run slabandstairusetransientsurfaceevidence
/test run connectedstoreydiscoverycanonicalizesdeepstairchain
/test run unevencaveremainsonestoreyacrossthreeintegerheights
/test run unevencavescandoesnotdependonselectedheight
/test run lowerexteriorcheckdoesnotclimbopenupperstorey
```

Expected: all pass with the same anchors/cell ownership as before the redesign.

- [ ] **Step 2: Run the copied-house lower-chain acceptance cases**

```text
/test run addingbasementpreservesregisteredupperstorey
/test run eachlowerstaircaseregisterstheplannedroomunderthehouse
/test run successivelowerstairroomsattachonelevelatatime
```

Expected: all pass on `dev/1.21.1` without fixture-coordinate changes.

- [ ] **Step 3: If a stair test fails, fix only the storey boundary that the test proves wrong**

Keep the existing `StoreyClassifier`, `StoreyRole`, `StoreyContext`, stable-landing peer rule, and transition-seed flow as the starting point. Do not introduce a second storey graph or block-class table.

Any production edit in this step requires its own red focused test or an already-red test above before changing behavior.

- [ ] **Step 4: Run the full floor GameTest class**

```text
/test runall FloorScannerGameTests
```

Expected: all required `FloorScannerGameTests` pass.

- [ ] **Step 5: Commit only if Task 5 required a production/test correction**

If files changed:

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java
git commit -m "fix: preserve storeys with enclosed volume scan"
```

If nothing changed, do not create an empty checkpoint commit.

---

### Task 6: Run the fixed-diff Java cleanup review and final verification

**Files:**
- Review fixed diff: `SelectedFloorScanner.java`, `FloorScannerGameTests.java`, and any directly changed floor test file from Tasks 1-5.
- Do not include unrelated docs/logs or other dirty worktree files.

**Interfaces:**
- Consumes: completed implementation diff.
- Produces: reviewed canonical `dev/1.21.1` implementation ready for forward merge.

- [ ] **Step 1: Freeze the exact Java diff for review**

Run:

```powershell
git diff origin/dev/1.21.1...HEAD -- common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java neoforge/src/main/java/net/conczin/mca/server/world/data/CopiedOpenHouseGameTests.java
```

Save/inspect that exact diff for all four cleanup lenses.

- [ ] **Step 2: Reuse lens**

Confirm the implementation reuses:

```text
FloorCeilingResolver
WalkNodeEvaluator.getFloorLevel
StructureConnector
Transition
StoreyClassifier
Observation scan-local caches
```

Flag/fix any duplicate ceiling cache, connector classifier, movement-height helper, or second cell-membership pipeline.

- [ ] **Step 3: Quality lens**

Check for:

```text
dead membership helpers
boolean-flag parameter sprawl
duplicate flood-fill loops
new public geometry types
comments that merely narrate code
mutable collections escaping private scan-local scope
```

Prefer guard clauses, private helpers, descriptive names, `Set.copyOf`/`Map.copyOf`, and deletion of obsolete code.

- [ ] **Step 4: Correctness lens against local Minecraft sources**

Re-open:

```text
C:/Users/Mik/Downloads/MCA/mc_source_code/local-source/src/main/java/net/minecraft/world/level/pathfinder/WalkNodeEvaluator.java
```

Verify that MCA still uses `WalkNodeEvaluator.getFloorLevel(...)` for physical surface height and does not copy/approximate vanilla path-type logic unnecessarily.

Re-check these failure-sensitive rules:

```text
full-height partial obstacles do not become ordinary Floor cells
low furniture keeps the lower integer Floor cell
air above low furniture does not become a duplicate Floor cell
external door/canopy does not leak into geometry
internal roofed door/corridor remains discoverable
unsupported ladder/trapdoor exit selects a Floor without becoming support
maxSize/maxRadius terminate the 3D flood fill
```

- [ ] **Step 5: Efficiency lens**

Confirm:

```text
ArrayDeque is used for BFS
visited cells use HashSet/LinkedHashSet
FloorCeilingResolver remains cached
successful EnclosedVolume is indexed by all of its cells for Observation reuse
supported SurfaceCells are derived once per volume
physical StepProvider remains scan-local/cached
no full-volume copy is created per storey traversal
```

Fix only obvious local allocation/recomputation issues; do not introduce parallel streams or a generic graph framework.

- [ ] **Step 6: Run Java/common verification**

```powershell
.\gradlew.bat :common:test :fabric:compileJava :neoforge:compileJava --no-daemon
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Run the full NeoForge GameTest server once**

```powershell
.\gradlew.bat :neoforge:runGameTestServer --no-daemon --no-build-cache
```

Required clean claim: final log contains `All N required tests passed :)` and the process exits successfully.

If a known unrelated flaky archer/mourning test fails, record its exact name/output before any rerun. Do not attribute it to floor discovery without evidence.

- [ ] **Step 8: Final diff/history hygiene**

Run:

```powershell
git diff --check
git status --short
rg -n "<<<<<<<|=======|>>>>>>>" common/src/main neoforge/src/main docs/superpowers/specs docs/superpowers/plans
git log --oneline --decorate -8
```

Confirm unrelated `.codex*.log` and unrelated untracked docs remain uncommitted.

- [ ] **Step 9: Push only after the canonical branch is fully verified**

```powershell
git push origin dev/1.21.1
```

Do not merge forward to `dev/26.1.2`, `dev/26.2`, or `dev/26.3` until this canonical `dev/1.21.1` implementation is green and pushed. Forward propagation should be normal branch merges so later versions inherit the same floor semantics rather than receiving version-specific floor patches.

## Completion checklist

- [ ] One bounded 3D observation discovers the enclosed interior.
- [ ] Supported Floor candidates are derived once from that observation.
- [ ] Ordinary Floor geometry has one producer.
- [ ] Generic `includeInteriorMembership()` and its helper family are gone.
- [ ] Beds/carpet retain the intended integer Floor cell without an extra cell above.
- [ ] Hopper/shelf-style full-height partial blocks do not become ordinary Floor membership.
- [ ] High ceilings do not create extra Floor layers.
- [ ] Internal roofed door/corridor discovery works; exterior canopy/open bridge stays outside.
- [ ] Stair/slab/cave regressions retain existing storey behavior.
- [ ] Ladder/trapdoor interaction handoff works without unsupported `FloorGeometry.Cell`s.
- [ ] Copied-house lower/basement chain passes on 1.21.1.
- [ ] `:common:test`, Fabric compile, NeoForge compile pass.
- [ ] Full NeoForge GameTests reach `All N required tests passed :)` or any unrelated flaky failure is recorded exactly and separated from floor behavior.
- [ ] Final Java diff passes reuse, quality, correctness, efficiency and local-vanilla review.
