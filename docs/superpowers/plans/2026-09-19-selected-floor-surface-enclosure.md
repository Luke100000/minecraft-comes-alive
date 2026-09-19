# Selected Floor Surface Enclosure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Do not use subagents. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the redundant pre-traversal 3D `EnclosedVolume` flood fill so selected-Floor size and enclosure are decided by the existing supported-surface traversal and exterior proof.

**Architecture:** `SelectedFloorScanner.Observation` resolves one physical selected-Floor seed and sends it directly through the cached physical `StepProvider`. `traverseSelectedFloor(...)` remains the only Floor topology traversal, `retainEnclosedRegions(...)` / `reachesExterior(...)` remain the enclosure proof, and `maxSize` applies only to actual selected-Floor cells plus connectors. Do not restore generic interior-membership expansion or introduce a replacement volume abstraction.

**Tech Stack:** Java 21, Minecraft 1.21.1, Architectury, Fabric, NeoForge GameTests, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-19-selected-floor-surface-enclosure-design.md`

## Global Constraints

- No subagents. Execute natively with `superpowers:executing-plans`.
- Preserve all unrelated dirty/untracked work in the worktree; never use `git add .`, broad reset, or clean.
- Production scope is `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java` only unless a newly observed supported regression proves another production owner must change.
- Prefer no test edits: the three current failures are already RED regression tests.
- `FloorGeometry` remains the only canonical/persisted exact Floor-cell model.
- `WalkNodeEvaluator.getFloorLevel(...)` and Minecraft collision remain the physical floor-height source of truth.
- `FloorBandClassifier`, `retainEnclosedRegions(...)`, `reachesExterior(...)`, connector handoff, and selected-only scanning retain their current behavior.
- Do not restore `includeInteriorMembership(...)` or any neighbour-based Floor-cell expansion.
- Do not add a replacement 3D volume cache, volume budget, topology graph, or public abstraction.
- Do not special-case blocks, coordinates, copied-house fixtures, or fixed Y values.
- `maxSize` means selected Floor cells plus connectors, not transient air cells.
- Production code must not grow. `SelectedFloorScanner.java` additions must be less than or equal to deletions, and the expected result is materially net-negative.
- Run the final Java diff locally through reuse, quality, correctness, and efficiency review lenses from `java-code-review-cleanup`.
- Minecraft local source checkout is not currently available at either `local-sources/` or the earlier external `mc_source_code/local-source` path. Do not invent vanilla behavior; this change leaves the existing collision/`WalkNodeEvaluator` layer unchanged.

## Historical Basis

The implementation is intentionally not a blind revert.

- `329e54327 refactor: derive floors from enclosed volume` introduced `EnclosedVolume`, the six-direction volume flood fill, `withinVolume(...)`, and `VOLUME_CELL_LIMIT_MULTIPLIER`.
- The parent of `329e54327` already had the surface traversal followed by `retainEnclosedRegions(...)` / `reachesExterior(...)`.
- `329e54327` also removed generic `includeInteriorMembership(...)`; that removal remains correct and must stay.
- `67c7f4267 fix: keep floor enclosure storey local` narrowed the volume vertically but retained the duplicate flood fill and its independent budget.
- `0bdc4193a refactor: scan only selected floor` later removed recursive connected-storey discovery; do not reintroduce it.
- `bd1364f17 refactor: clarify selected floor boundary semantics` was a behavior-preserving naming cleanup and is not the source of these failures.

## Review Focus

These are the most important failure classes for review, each already covered by an existing GameTest:

1. One-block-higher doorway — `oneBlockHigherDoorwayKeepsFacingOwner` must return SUCCESS, include the doorway cell, and preserve FACING-side Room ownership.
2. Roofed open bridge — `openRoofBridgeDoesNotMergeTwoHouses` must keep both houses valid while pruning the canopy/bridge from both Floors.
3. Uneven exterior descent — `highSourceStillFindsExteriorThroughUnevenDescent` must return NOT_IN_BUILDING rather than BLOCK_LIMIT.
4. Supported-cell-only geometry — partial obstacles, high ceilings, carpets, and unsupported ladder exits must not manufacture Floor cells.
5. Vertical/uneven Floor boundaries — stair, open-upper-storey, uneven-cave, and source-independence regressions must retain current selected-Floor ownership.

---

### Task 1: Reconfirm the RED baseline

**Files:**
- Read only: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`
- Generated evidence only: `neoforge/run-gametest/logs/latest.log`
- Optional copied evidence: `.codex-selected-floor-surface-red.log`

**Interfaces:**
- Consumes: current `SelectedFloorScanner.scan(Level, BlockPos, int, int)`.
- Produces: exact pre-change failure evidence for the three Floor regressions.

- [ ] **Step 1: Record the current production source state**

Run:

```powershell
git status --short
git diff -- common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
```

Expected: no uncommitted production diff in `SelectedFloorScanner.java` before this fix. Do not touch unrelated dirty files.

- [ ] **Step 2: Run the full NeoForge GameTest server before implementation**

Run:

```powershell
.\gradlew.bat :neoforge:runGameTestServer --no-daemon --no-build-cache --max-workers=1
```

Expected RED evidence: the command may fail because the current scanner is known red.

- [ ] **Step 3: Copy and inspect the RED log**

Run:

```powershell
Copy-Item neoforge/run-gametest/logs/latest.log .codex-selected-floor-surface-red.log -Force
rg -n "GAME TESTS COMPLETE|required tests failed|oneblockhigherdoorwaykeepsfacingowner|openroofbridgedoesnotmergetwohouses|highsourcestillfindsexteriorthroughunevendescent|BLOCK_LIMIT" .codex-selected-floor-surface-red.log
```

Expected:

```text
oneblockhigherdoorwaykeepsfacingowner ... BLOCK_LIMIT
openroofbridgedoesnotmergetwohouses ... BLOCK_LIMIT
highsourcestillfindsexteriorthroughunevendescent ... BLOCK_LIMIT
```

Record any additional required failure by exact name. Do not hide an unrelated failure and do not modify tests to obtain the expected RED state.

---

### Task 2: Delete the pre-traversal volume pipeline

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java:25-240, 710-725, 900-925`

**Interfaces:**
- Consumes: existing `SurfaceCell`, cached physical `StepProvider`, `resolveSelectedFloorAnchor(...)`, `traverseSelectedFloor(...)`, `retainEnclosedRegions(...)`.
- Produces: unchanged package-visible `SelectedFloorScanner.Result` API and unchanged `FloorGeometry` semantics, without `EnclosedVolume`.

- [ ] **Step 1: Remove volume-only constants and Observation state**

Delete:

```java
private static final int VOLUME_CELL_LIMIT_MULTIPLIER = 16;
private static final Direction[] VOLUME_DIRECTIONS = Direction.values();
```

Delete from `Observation`:

```java
private final Map<BlockPos, EnclosedVolume> volumesBySeed = new HashMap<>();
```

Delete the entire `enclosedVolume(BlockPos seed)` method.

Keep `resolutions`, `ceilings`, and the cached physical `provider`.

- [ ] **Step 2: Make Observation resolve directly into selected-Floor traversal**

Replace the volume branch in `Observation.resolve(...)` with:

```java
private Result resolve(BlockPos seed) {
    return resolutions.computeIfAbsent(seed.immutable(), requested -> {
        SurfaceCell traversalSeed = resolveScanSeed(world, requested, ceilings).orElse(null);
        if (traversalSeed == null) {
            return Result.failure(Building.validationResult.NOT_IN_BUILDING, requested);
        }

        traversalSeed = resolveSelectedFloorAnchor(world, traversalSeed, provider);
        return resolveSelectedFloor(
                world, traversalSeed, ceilings, provider, maxSize, maxRadius);
    });
}
```

Do not add a second cache or fallback scan.

- [ ] **Step 3: Simplify resolveSelectedFloor to consume the physical provider directly**

Change the signature to:

```java
private static Result resolveSelectedFloor(
        Level world,
        SurfaceCell traversalSeed,
        FloorCeilingResolver ceilings,
        StepProvider provider,
        int maxSize,
        int maxRadius) {
```

Delete:

```java
StepProvider enclosedSteps = withinVolume(provider, volume);
SurfaceCell traversalSeed = volume.supportedCells().get(requestedSeed.feet());
if (traversalSeed == null) {
    return Result.failure(Building.validationResult.NOT_IN_BUILDING, requestedSeed.feet());
}
```

Create the classifier from `traversalSeed` exactly as today, then call:

```java
SelectedFloorScan scan = traverseSelectedFloor(
        world, traversalSeed, ceilings, maxSize, maxRadius, provider, classifier);
```

Keep the existing `retainEnclosedRegions(...)` call using `provider`.

Change failure/success source references in this method from `requestedSeed.feet()` to `traversalSeed.feet()`.

Do not alter the FloorBand predicates, exterior pruning, connector collection, or actual Floor-size checks.

- [ ] **Step 4: Delete all volume-only helpers**

Delete the entire methods:

```text
discoverEnclosedVolume(...)
supportedCellsInVolume(...)
withinVolume(...)
isInteriorVolumeCell(...)
```

Delete the entire `EnclosedVolume` record.

Do not recreate any equivalent helper under another name.

- [ ] **Step 5: Prove the duplicate architecture is gone**

Run:

```powershell
rg -n "VOLUME_CELL_LIMIT_MULTIPLIER|VOLUME_DIRECTIONS|volumesBySeed|enclosedVolume\(|discoverEnclosedVolume|supportedCellsInVolume|withinVolume|isInteriorVolumeCell|EnclosedVolume" common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
```

Expected: no matches.

Then run:

```powershell
rg -n "traverseSelectedFloor|retainEnclosedRegions|reachesExterior|cells\.size\(\) \+ connectors\.size\(\) > maxSize" common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
```

Expected: the supported-surface traversal, exterior proof, and actual topology size checks remain.

- [ ] **Step 6: Compile the changed production code**

Run:

```powershell
.\gradlew.bat :common:compileJava --no-daemon --no-build-cache --rerun-tasks --max-workers=1
```

Expected: PASS.

---

### Task 3: Verify the regressions and all preserved Floor semantics

**Files:**
- No source edits expected.
- Read generated `neoforge/run-gametest/logs/latest.log`.

**Interfaces:**
- Consumes: simplified selected-Floor traversal from Task 2.
- Produces: GameTest evidence that the architectural deletion fixes the failures without reopening old geometry bugs.

- [ ] **Step 1: Run the NeoForge GameTest server after the fix**

Run:

```powershell
.\gradlew.bat :neoforge:runGameTestServer --no-daemon --no-build-cache --max-workers=1
```

Expected for the three RED tests:

```text
oneBlockHigherDoorwayKeepsFacingOwner -> PASS
openRoofBridgeDoesNotMergeTwoHouses -> PASS
highSourceStillFindsExteriorThroughUnevenDescent -> PASS
```

The third test's behavior is specifically `NOT_IN_BUILDING`; do not change it to SUCCESS merely to get green.

- [ ] **Step 2: Check the exact final GameTest summary and regression names**

Run:

```powershell
rg -n "GAME TESTS COMPLETE|required tests failed|oneblockhigherdoorwaykeepsfacingowner|openroofbridgedoesnotmergetwohouses|highsourcestillfindsexteriorthroughunevendescent" neoforge/run-gametest/logs/latest.log
```

Expected: none of the three names appear as failures. If the suite is not fully green, compare every remaining required failure by exact name with `.codex-selected-floor-surface-red.log` before deciding whether it is caused by this change.

- [ ] **Step 3: Assert that the enclosed-volume-era protection tests did not fail**

Run:

```powershell
$tests = @(
    'raisedFullHeightPartialObstacleDoesNotBecomeFloorMembership',
    'fullHeightPartialObstacleDoesNotBecomeFloorCell',
    'highCeilingDoesNotCreateExtraFloorLayers',
    'highCeilingDoesNotConsumeFloorBlockLimit',
    'carpetDoesNotChangeIntegerRoomMembership',
    'staircaseKeepsUpperRoomOutOfLowerStorey',
    'twoBlockStaircaseSeparatesBroadStoreys',
    'fullBlockStaircaseDoesNotMergeLowerRooms',
    'lowerExteriorCheckDoesNotClimbOpenUpperStorey',
    'airAboveLadderResolvesFloorWithoutInventingSupport',
    'ladderTopExitStaysOnUpperStorey',
    'unevenCaveRemainsOneStoreyAcrossThreeIntegerHeights',
    'roofedExteriorAcrossDoorIsNotOwnedFloorGeometry',
    'disconnectedRoofDoesNotChangeHouseFloor',
    'enclosedRoofedCorridorRemainsIndoorGeometry',
    'unevenThreeArmRoomIsSourceIndependent',
    'interiorFloorHoleDoesNotInvalidateRemainingRoom'
)
foreach ($test in $tests) {
    if (Select-String -Path neoforge/run-gametest/logs/latest.log -Pattern "$test failed" -Quiet) {
        throw "Protection GameTest failed: $test"
    }
}
```

PowerShell matching is case-insensitive by default, so these canonical Java method names match the lower-cased names in the GameTest log.

- [ ] **Step 4: Do not modify existing tests to accommodate the implementation**

If any protection test fails, invoke `superpowers:systematic-debugging` against that exact behavior. Identify the root cause before another production edit. Do not weaken Floor assertions, enlarge limits, or add fixture-specific exceptions.

---

### Task 4: Full verification, four-lens Java cleanup, and scoped commit

**Files:**
- Review/modify only if justified: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`
- Documentation already created by the planning phase.
- Do not stage unrelated dirty files.

**Interfaces:**
- Consumes: final Java diff after green GameTests.
- Produces: verified, deletion-heavy selected-Floor scanner with no redundant volume topology owner.

- [ ] **Step 1: Run common unit tests and both loader compiles**

Run:

```powershell
.\gradlew.bat :common:test :fabric:compileJava :neoforge:compileJava --no-daemon --no-build-cache --rerun-tasks --max-workers=1
```

Expected: PASS.

- [ ] **Step 2: Freeze the final Java diff for cleanup review**

Run:

```powershell
git diff -- common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
```

Use this exact diff for all four local review lenses below.

- [ ] **Step 3: Reuse review**

Required conclusion:

```text
The implementation reuses the existing physical StepProvider, FloorCeilingResolver,
FloorBandClassifier, retainEnclosedRegions, and reachesExterior pipeline.
No replacement volume/enclosure helper was added.
```

If that statement is false, simplify the implementation before continuing.

- [ ] **Step 4: Quality review**

Check all of these:

- `Observation.resolve(...)` is one linear seed -> anchor -> selected-Floor path;
- no dead volume helper/constant/record remains;
- no compatibility alias preserves removed architecture;
- no new parameter wrapper or boolean flag was introduced;
- comments explain invariants rather than deleted implementation history.

Fix any worthwhile in-scope issue and rerun Task 3 plus Task 4 Step 1 after a production edit.

- [ ] **Step 5: Correctness review**

Check all of these:

- ordinary Floor cells still require physical support/headroom/ceiling evidence;
- `retainEnclosedRegions(...)` and `reachesExterior(...)` retain their current logic unless a test-driven correction was required;
- `FloorBandClassifier` predicates are unchanged;
- the real `maxSize` checks still guard selected cells/connectors;
- `maxRadius` remains enforced;
- unsupported connector interaction cells still do not become ordinary Floor geometry.

Fix only test-proven correctness issues.

- [ ] **Step 6: Efficiency review**

Confirm all of these:

- no 3D all-direction air-cell queue remains;
- no volume-sized `Set<BlockPos>` or supported-cell map is built before surface traversal;
- no replacement per-cell ceiling scan was added;
- the existing step cache in `Observation` remains the only world-step cache required by this path.

- [ ] **Step 7: Enforce the production no-growth budget**

Run:

```powershell
$file = 'common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java'
$stat = git diff --numstat -- $file
if (-not $stat) { throw 'Expected a SelectedFloorScanner production diff' }
$parts = $stat -split '\s+'
$added = [int]$parts[0]
$deleted = [int]$parts[1]
Write-Output "SelectedFloorScanner additions=$added deletions=$deleted"
if ($added -gt $deleted) {
    throw 'Production no-growth constraint violated'
}
```

Expected: deletions exceed additions.

- [ ] **Step 8: Final diff hygiene**

Run:

```powershell
git diff --check
git status --short
```

Inspect status carefully. Unrelated dirty/untracked work must remain untouched.

- [ ] **Step 9: Stage only the scoped implementation**

Run:

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
git diff --cached --check
git diff --cached --stat
```

If a new focused GameTest was added for an uncovered invariant, stage only that file after reviewing its diff. The planning spec/plan are already committed before execution.

Do not stage any `.codex-*.log`, run directory, Archer, Destiny, navigation, UI, or other unrelated file.

- [ ] **Step 10: Commit the verified fix**

Run:

```powershell
git commit -m "fix: remove redundant floor volume flood fill"
```

Then run:

```powershell
git status --short
git show --stat --oneline HEAD
```

Expected: the commit contains only the selected-Floor scanner, plus a focused test file only if Task 3 proved a new test was required. The planning documents remain in their earlier documentation commit. All unrelated user work remains dirty/untracked exactly as before.
