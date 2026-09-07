# Blueprint Whole-Tile Warm Cache Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Blueprint's 64-slice terrain streaming state machine with one-whole-tile-per-frame sampling while preserving the warm session cache, bounded LRU, no-force-load reads, and current terrain visuals.

**Architecture:** `BlueprintTerrainRenderer` remains the only terrain sampling/cache/texture owner. Each 128x128 `TerrainTile` has one lifecycle (`sampledAtGameTime`, `retryAfterGameTime`, `complete`), one persistent texture, and the existing primitive terrain arrays; `render()` selects at most one nearest eligible tile per frame, samples it synchronously from already-loaded client chunks, uploads the full 128x128 texture once, and draws every ready visible cached tile. Warm cache ownership remains keyed to the current `ClientLevel` identity through the existing `MCAClient -> BlueprintScreen -> BlueprintTerrainRenderer` lifecycle hook.

**Tech Stack:** Java 21, Minecraft 1.21.1 Mojang mappings, Architectury common client code, `ClientLevel`/`LevelChunk`, `DynamicTexture`/`NativeImage`, JUnit 5, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-07-blueprint-whole-tile-warm-cache-simplification-design.md`

## Global Constraints

- Work only on `feature/1.21.1-floor-clean-squash` in the existing checkout.
- The worktree already contains approved/uncommitted Blueprint warm-cache and terrain-correctness edits. Do **not** reset, stash, checkout, or overwrite them.
- The user is also working concurrently on unrelated files. Treat every dirty/untracked path outside the four Blueprint implementation files named in this plan as off-limits: do not edit, stage, restore, clean, or otherwise mutate it.
- Keep `TILE_BLOCK_SIZE = 128`, `SAMPLE_STEP = 1`, `MAX_CACHED_TILES = 96`, and `INCOMPLETE_TILE_RETRY_TICKS = 20L`.
- Replace slice staleness with `TERRAIN_TILE_STALE_TICKS = 600L` (30 seconds at 20 TPS).
- Sample at most one whole terrain tile per rendered frame.
- Preserve one tile of prefetch padding; visible work always outranks prefetch work, and equal-priority work is nearest-to-camera first.
- Keep terrain sampling on the client/render thread. Do not add workers, futures, queues, schedulers, disk caches, or another terrain representation.
- Never generate or force-load terrain chunks. Use `level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false)` for nullable client-cache reads.
- A missing **core** chunk makes a tile incomplete. Missing one-cell hillshade-halo data is opportunistic and does not make an otherwise complete tile incomplete.
- Unavailable cells retain their previous valid primitive values; cells with no previous value remain invalid/transparent.
- Preserve the current worktree's dry-height, leaf, water/substrate, biome tint, hillshade, contour, and color-composition code. This plan changes scheduling/cache mechanics only.
- Keep one `DynamicTexture`/`NativeImage` registration per cached tile and reuse it on refresh; eviction or level invalidation are the normal release points.
- Closing a Blueprint screen must retain the warm cache. Changing `ClientLevel` identity or disconnecting (`level == null`) must release and clear it.
- Automated tests may prove scheduling/lifecycle contracts but must not be used to claim the in-game visual hitch is acceptable.

---

### Task 1: Replace slice scheduling with one whole-tile lifecycle

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java:35-800`
- Modify: `common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java:250-560`

**Interfaces:**
- Consumes: existing `tileMin`, `tileDistanceSq`, `terrainSamplingBand`, `tileTouchesLoadedChunk`, `sampleClientOceanFloorColumn`, terrain color helpers, static warm `tiles` cache, and `onClientLevelChanged(Object)`.
- Produces: package-private `terrainWorkPriority` and `isCoreSampleCell`; `TerrainTile.sampleWholeTile(ClientLevel,long)`; tile-level `sampledAtGameTime`, `retryAfterGameTime`, and `complete`; a full-tile `updateTexture(TerrainTile)` path.

- [ ] **Step 1: Record the dirty-worktree baseline before editing**

Run:

```powershell
git status --short
git diff -- common/src/main/java/net/conczin/mca/MCAClient.java common/src/main/java/net/conczin/mca/client/gui/BlueprintScreen.java common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java
```

Expected: the four Blueprint-related files already contain the approved warm-cache/current terrain edits. Any other dirty/untracked paths shown by `git status` are concurrent user work and must remain untouched.

- [ ] **Step 2: Run the focused Blueprint baseline from rebuilt classes**

Run:

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.client.gui.BlueprintScreenMapInteractionTest --rerun-tasks
```

Expected: PASS before the simplification. If it fails, diagnose that baseline first rather than attributing it to this plan.

- [ ] **Step 3: Add RED tests for whole-tile scheduling policy**

In `BlueprintScreenMapInteractionTest`, add these behavior tests near the existing terrain scheduling tests:

```java
@Test
void wholeTileSchedulingOrdersVisibleBeforePrefetchAndHonorsLifecycleDeadlines() {
    long never = Long.MIN_VALUE;
    long now = 200L;

    assertEquals(0, BlueprintTerrainRenderer.terrainWorkPriority(
            0, false, false, never, never, now));
    assertEquals(1, BlueprintTerrainRenderer.terrainWorkPriority(
            0, true, false, 150L, 200L, now));
    assertEquals(2, BlueprintTerrainRenderer.terrainWorkPriority(
            0, true, true, -400L, never, now));
    assertEquals(3, BlueprintTerrainRenderer.terrainWorkPriority(
            1, false, false, never, never, now));
    assertEquals(4, BlueprintTerrainRenderer.terrainWorkPriority(
            1, true, false, 150L, 200L, now));
    assertEquals(4, BlueprintTerrainRenderer.terrainWorkPriority(
            1, true, true, -400L, never, now));

    assertEquals(Integer.MAX_VALUE, BlueprintTerrainRenderer.terrainWorkPriority(
            0, true, false, 150L, 201L, now),
            "incomplete tiles must wait for their retry deadline");
    assertEquals(Integer.MAX_VALUE, BlueprintTerrainRenderer.terrainWorkPriority(
            0, true, true, -399L, never, now),
            "a complete tile younger than 600 ticks must stay fresh");
}

@Test
void wholeTileCompletenessCountsCoreCellsButNotHillshadeHalo() {
    int cellCount = 130;

    assertFalse(BlueprintTerrainRenderer.isCoreSampleCell(0, 64, cellCount, cellCount));
    assertFalse(BlueprintTerrainRenderer.isCoreSampleCell(129, 64, cellCount, cellCount));
    assertFalse(BlueprintTerrainRenderer.isCoreSampleCell(64, 0, cellCount, cellCount));
    assertFalse(BlueprintTerrainRenderer.isCoreSampleCell(64, 129, cellCount, cellCount));
    assertTrue(BlueprintTerrainRenderer.isCoreSampleCell(1, 1, cellCount, cellCount));
    assertTrue(BlueprintTerrainRenderer.isCoreSampleCell(128, 128, cellCount, cellCount));
}
```

These expected values encode the approved priority order:

1. missing visible;
2. retry-ready incomplete visible;
3. stale visible;
4. missing prefetch;
5. retry-ready incomplete or stale prefetch;
6. no work for fresh or retry-blocked tiles.

- [ ] **Step 4: Run only the two new tests and verify RED**

Run:

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.client.gui.BlueprintScreenMapInteractionTest.wholeTileSchedulingOrdersVisibleBeforePrefetchAndHonorsLifecycleDeadlines --tests net.conczin.mca.client.gui.BlueprintScreenMapInteractionTest.wholeTileCompletenessCountsCoreCellsButNotHillshadeHalo --rerun-tasks
```

Expected: compilation/test failure because `terrainWorkPriority` and `isCoreSampleCell` do not yet exist.

- [ ] **Step 5: Add the tile-level constants and pure scheduling helpers**

In `BlueprintTerrainRenderer`, remove the slice budget/stale constants and add:

```java
private static final int MAX_CACHED_TILES = 96;
private static final long INCOMPLETE_TILE_RETRY_TICKS = 20L;
private static final long TERRAIN_TILE_STALE_TICKS = 600L;
private static final int NO_TERRAIN_WORK = Integer.MAX_VALUE;
```

Add these package-private helpers and use them from production scheduling/sampling:

```java
static int terrainWorkPriority(int samplingBand,
                               boolean tilePresent,
                               boolean complete,
                               long sampledAtGameTime,
                               long retryAfterGameTime,
                               long gameTime) {
    if (samplingBand < 0 || samplingBand > 1) return NO_TERRAIN_WORK;
    if (!tilePresent) return samplingBand == 0 ? 0 : 3;

    if (!complete) {
        if (retryAfterGameTime != Long.MIN_VALUE && gameTime < retryAfterGameTime) {
            return NO_TERRAIN_WORK;
        }
        return samplingBand == 0 ? 1 : 4;
    }

    if (sampledAtGameTime != Long.MIN_VALUE
            && gameTime - sampledAtGameTime >= TERRAIN_TILE_STALE_TICKS) {
        return samplingBand == 0 ? 2 : 4;
    }
    return NO_TERRAIN_WORK;
}

static boolean isCoreSampleCell(int cellX,
                                int cellZ,
                                int xCellCount,
                                int zCellCount) {
    return cellX > 0 && cellX < xCellCount - 1
            && cellZ > 0 && cellZ < zCellCount - 1;
}
```

Do not retain `TERRAIN_SLICE_STALE_TICKS`, `TERRAIN_SAMPLE_BUDGET_NANOS`, or `TEXTURE_REFRESH_SLICE_INTERVAL` beside these replacements.

- [ ] **Step 6: Collapse `TerrainTile` from 64 slice states to one tile lifecycle**

Keep the current primitive arrays and texture fields. Replace:

```java
private final long[] sampledAtGameTimes = new long[SAMPLE_SLICE_COUNT];
private final long[] retryAfterGameTimes = new long[SAMPLE_SLICE_COUNT];
private final boolean[] dirtySlices = new boolean[SAMPLE_SLICE_COUNT];
private int sampledSliceCount;
private boolean textureDirty;
```

with:

```java
private long sampledAtGameTime = Long.MIN_VALUE;
private long retryAfterGameTime = Long.MIN_VALUE;
private boolean complete;
private DynamicTexture terrainTexture;
private ResourceLocation terrainTextureLocation;
```

Keep the constructor signature `TerrainTile(int minX, int minZ, int sampleStep)` so the existing warm-cache regression tests continue to construct the real tile type without a test-only constructor.

- [ ] **Step 7: Replace `sampleNextSlice` with one mutating whole-tile sample**

Delete `sampleNextSlice`. Add:

```java
private void sampleWholeTile(ClientLevel level, long gameTime) {
    int minBuildHeight = level.getMinBuildHeight();
    int firstCellX = firstCell(minX, sampleStep);
    int firstCellZ = firstCell(minZ, sampleStep);
    BlockPos.MutableBlockPos surfacePos = new BlockPos.MutableBlockPos();
    boolean sampledCompletely = true;

    for (int cellX = 0; cellX < xCellCount; cellX++) {
        for (int cellZ = 0; cellZ < zCellCount; cellZ++) {
            boolean sampled = sampleCell(
                    level, firstCellX, firstCellZ, cellX, cellZ,
                    minBuildHeight, surfacePos);
            if (!sampled && isCoreSampleCell(cellX, cellZ, xCellCount, zCellCount)) {
                sampledCompletely = false;
            }
        }
    }

    sampledAtGameTime = gameTime;
    complete = sampledCompletely;
    retryAfterGameTime = sampledCompletely
            ? Long.MIN_VALUE
            : gameTime + INCOMPLETE_TILE_RETRY_TICKS;
}
```

The 130x130 primitive grid already contains the 128x128 core plus its one-cell neighbor halo. This loop intentionally treats missing halo samples as non-fatal while missing core samples make the tile incomplete.

- [ ] **Step 8: Simplify `sampleCell` to a direct no-force-load client chunk lookup**

Change the signature to remove slice-specific chunk parameters:

```java
private boolean sampleCell(ClientLevel level,
                           int firstCellX,
                           int firstCellZ,
                           int cellX,
                           int cellZ,
                           int minBuildHeight,
                           BlockPos.MutableBlockPos surfacePos)
```

At the start of the method use:

```java
int x = firstCellX + cellX * sampleStep;
int z = firstCellZ + cellZ * sampleStep;
int sampleX = x + sampleStep / 2;
int sampleZ = z + sampleStep / 2;

LevelChunk chunk = level.getChunkSource().getChunk(
        SectionPos.blockToSectionCoord(sampleX),
        SectionPos.blockToSectionCoord(sampleZ),
        ChunkStatus.FULL,
        false);
if (chunk == null) {
    return false;
}
```

When `chunk == null`, **do not call `clearCell`**. Returning without mutation is what preserves a prior valid cached value. Keep the existing current-worktree body from `surfaceHeight` through dry terrain, biome tint, water/substrate scan, and `setCell` unchanged apart from the removed slice parameters.

- [ ] **Step 9: Rewrite `render()` to select exactly one whole-tile candidate**

Retain the current visible bounds and one-ring sampling bounds. Replace visible/prefetch slice selection and both sampling `while` loops with one candidate accumulator:

```java
List<TileKey> visibleKeys = new ArrayList<>();
TileKey workKey = null;
TerrainTile workTile = null;
int bestPriority = NO_TERRAIN_WORK;
double bestDistanceSq = Double.POSITIVE_INFINITY;

for (int minX = tileMin(samplingMinX); minX <= samplingMaxX; minX += TILE_BLOCK_SIZE) {
    for (int minZ = tileMin(samplingMinZ); minZ <= samplingMaxZ; minZ += TILE_BLOCK_SIZE) {
        int samplingBand = terrainSamplingBand(
                minX, minZ, visibleMinX, visibleMaxX, visibleMinZ, visibleMaxZ);
        if (samplingBand > 1) continue;

        TileKey key = new TileKey(minX, minZ);
        TerrainTile tile = tiles.get(key);
        if (samplingBand == 0) {
            visibleKeys.add(key);
        }

        if (!tileTouchesLoadedChunk(minX, minZ, loadedChunks)) continue;

        int priority = terrainWorkPriority(
                samplingBand,
                tile != null,
                tile != null && tile.complete,
                tile == null ? Long.MIN_VALUE : tile.sampledAtGameTime,
                tile == null ? Long.MIN_VALUE : tile.retryAfterGameTime,
                gameTime);
        if (priority == NO_TERRAIN_WORK) continue;

        double distanceSq = tileDistanceSq(
                minX, minZ, viewport.mapCenterX(), viewport.mapCenterZ());
        if (priority < bestPriority
                || (priority == bestPriority && distanceSq < bestDistanceSq)) {
            workKey = key;
            workTile = tile;
            bestPriority = priority;
            bestDistanceSq = distanceSq;
        }
    }
}

if (workKey != null) {
    if (workTile == null) {
        workTile = new TerrainTile(workKey.minX(), workKey.minZ(), sampleStep);
        tiles.put(workKey, workTile);
    }
    workTile.sampleWholeTile(minecraft.level, gameTime);
    updateTexture(workTile);
}

for (TileKey key : visibleKeys) {
    TerrainTile tile = tiles.get(key);
    if (tile != null) {
        renderTile(context, tile);
    }
}
trimCache();
```

There must be one `sampleWholeTile` call site in `render()` and no loop around it. This is the hard one-tile-per-frame budget.

- [ ] **Step 10: Replace dirty-slice texture uploads with one full-tile upload**

Make `renderTile` draw-only:

```java
private void renderTile(GuiGraphics context, TerrainTile tile) {
    if (tile.terrainTextureLocation == null) return;
    context.blit(tile.terrainTextureLocation, tile.minX, tile.minZ,
            TILE_BLOCK_SIZE, TILE_BLOCK_SIZE, 0.0F, 0.0F,
            TILE_BLOCK_SIZE, TILE_BLOCK_SIZE, TILE_BLOCK_SIZE, TILE_BLOCK_SIZE);
}
```

Replace `updateTexture` and the `updatePixels` overload that accepts `SliceBounds` with:

```java
private void updateTexture(TerrainTile tile) {
    if (tile.terrainTexture == null) {
        NativeImage terrainImage = new NativeImage(TILE_BLOCK_SIZE, TILE_BLOCK_SIZE, true);
        updatePixels(tile, terrainImage);

        DynamicTexture terrainTexture = new DynamicTexture(terrainImage);
        terrainTexture.setFilter(false, false);
        tile.terrainTexture = terrainTexture;
        tile.terrainTextureLocation = Minecraft.getInstance().getTextureManager()
                .register("mca_blueprint_terrain", terrainTexture);
        return;
    }

    NativeImage terrainImage = tile.terrainTexture.getPixels();
    if (terrainImage == null) return;

    updatePixels(tile, terrainImage);
    tile.terrainTexture.upload();
}

private static void updatePixels(TerrainTile tile, NativeImage terrainImage) {
    int firstCellX = TerrainTile.firstCell(tile.minX, tile.sampleStep);
    int firstCellZ = TerrainTile.firstCell(tile.minZ, tile.sampleStep);

    for (int pixelX = 0; pixelX < TILE_BLOCK_SIZE; pixelX++) {
        for (int pixelZ = 0; pixelZ < TILE_BLOCK_SIZE; pixelZ++) {
            int blockX = tile.minX + pixelX;
            int blockZ = tile.minZ + pixelZ;
            int cellX = Math.floorDiv(blockX - firstCellX, tile.sampleStep);
            int cellZ = Math.floorDiv(blockZ - firstCellZ, tile.sampleStep);

            if (!tile.isCellValid(cellX, cellZ)) {
                terrainImage.setPixelRGBA(pixelX, pixelZ, 0);
                continue;
            }

            int height = tile.height(cellX, cellZ);
            int northHeight = tile.heightAt(cellX, cellZ - 1, height);
            int southHeight = tile.heightAt(cellX, cellZ + 1, height);
            int westHeight = tile.heightAt(cellX - 1, cellZ, height);
            int eastHeight = tile.heightAt(cellX + 1, cellZ, height);
            float brightness = hillshadeBrightness(westHeight, eastHeight, northHeight, southHeight);
            boolean northContour = Math.floorDiv(height, CONTOUR_INTERVAL)
                    != Math.floorDiv(northHeight, CONTOUR_INTERVAL);
            boolean westContour = Math.floorDiv(height, CONTOUR_INTERVAL)
                    != Math.floorDiv(westHeight, CONTOUR_INTERVAL);
            int color = composeTerrainAndWater(
                    tile.terrainColor(cellX, cellZ),
                    tile.waterTint(cellX, cellZ),
                    brightness,
                    northContour || westContour);
            terrainImage.setPixelRGBA(pixelX, pixelZ, FastColor.ABGR32.fromArgb32(color));
        }
    }
}
```

Keep this existing contour computation inline. The behavioral requirement is one full 128x128 rewrite followed by `DynamicTexture.upload()` on refresh.

- [ ] **Step 11: Delete the obsolete slice-only machinery rather than bypassing it**

Remove from production:

```text
SAMPLE_SLICE_BLOCK_SIZE
SAMPLE_SLICES_PER_AXIS
SAMPLE_SLICE_COUNT
TERRAIN_SAMPLE_BUDGET_NANOS
TEXTURE_REFRESH_SLICE_INTERVAL
TERRAIN_SLICE_STALE_TICKS
TileSamplingBudget
SliceBounds
hasSampledTerrain
shouldRefreshTexture
sampleSliceBounds
sliceNeedsSampling
recordSliceSampleResult
nearestReadySlice
dirtyTextureBounds
sampleDependencyBounds
sampleNextSlice
sampledAtGameTimes
retryAfterGameTimes
dirtySlices
sampledSliceCount
textureDirty
```

Also remove now-unused imports such as `Arrays` and `LongSupplier` if `rg` confirms they have no remaining use. Keep `SectionPos` because tile/chunk coordinate conversion still uses it.

- [ ] **Step 12: Replace obsolete slice tests with whole-tile behavior tests**

Delete tests whose only contract is the removed implementation:

```text
terrainSamplingBudgetCanContinueWithinFrame
terrainSamplingBudgetAlwaysAllowsFirstSliceThenStopsAtDeadline
partialTerrainBecomesRenderableAfterFirstSampledSlice
partialTerrainTextureRefreshIsThrottledBetweenFirstSliceAndCompletion
terrainSamplingUsesSixteenBlockSlices
terrainSamplingPrioritizesSliceNearestCameraPosition
nearestTerrainSliceStopsAfterFirstLoadedCandidate
terminalPartialTerrainSampleFlushesDirtyTexture
nearestPendingTerrainSliceSkipsItsOwnUnloadedChunk
failedTerrainSliceStaysPendingWithoutResettingSuccessfulSlices
completedTerrainSliceBecomesStaleAfterFixedRefreshInterval
dirtyTextureBoundsExpandOnlyOnePixelAndClipToTile
incrementalTerrainSliceSamplesOneCellNeighborHaloForImmediateHillshade
```

Remove the test-only `samplingBudget(LongSupplier,long)` helper and the `LongSupplier` import. Keep the existing tests for tile anchoring, camera-distance priority, one-ring prefetch, loaded-chunk eligibility, water/substrate, dry terrain, hillshade, cache identity, warm reopen, and level invalidation.

- [ ] **Step 13: Run the new tests and the full Blueprint test class GREEN**

Run:

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.client.gui.BlueprintScreenMapInteractionTest.wholeTileSchedulingOrdersVisibleBeforePrefetchAndHonorsLifecycleDeadlines --tests net.conczin.mca.client.gui.BlueprintScreenMapInteractionTest.wholeTileCompletenessCountsCoreCellsButNotHillshadeHalo --rerun-tasks
.\gradlew.bat :common:test --tests net.conczin.mca.client.gui.BlueprintScreenMapInteractionTest --rerun-tasks
```

Expected: both commands PASS with zero failed tests.

- [ ] **Step 14: Prove the slice state machine is actually gone**

Run:

```powershell
rg -n "SAMPLE_SLICE|TileSamplingBudget|sampleSliceBounds|sampleDependencyBounds|dirtyTextureBounds|sliceNeedsSampling|recordSliceSampleResult|nearestReadySlice|dirtySlices|sampledSliceCount|TEXTURE_REFRESH_SLICE_INTERVAL|TERRAIN_SAMPLE_BUDGET_NANOS" common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java
```

Expected: no matches.

Do **not** replace those names with renamed equivalents or another per-slice array/bitmask.

---

### Task 2: Verify warm-cache ownership and all automated gates

**Files:**
- Verify/preserve: `common/src/main/java/net/conczin/mca/MCAClient.java:76-85`
- Verify/preserve: `common/src/main/java/net/conczin/mca/client/gui/BlueprintScreen.java:47-52,1314-1317`
- Verify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java`
- Verify: `common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java`

**Interfaces:**
- Consumes: `MCAClient.tickClient(Minecraft) -> BlueprintScreen.maintainTerrainCache(Minecraft) -> BlueprintTerrainRenderer.onClientLevelChanged(Object)` and static warm tile cache.
- Produces: verified same-level reuse and different-level/disconnect invalidation without reintroducing per-screen texture destruction.

- [ ] **Step 1: Re-run the existing warm-cache regression tests from rebuilt classes**

Run:

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.client.gui.BlueprintScreenMapInteractionTest.closingBlueprintRendererKeepsWarmTerrainForNextScreen --tests net.conczin.mca.client.gui.BlueprintScreenMapInteractionTest.changingClientLevelInvalidatesWarmTerrainCache --rerun-tasks
```

Expected: PASS. `BlueprintTerrainRenderer.close()` remains non-destructive for the session cache; `onClientLevelChanged` clears on a different identity or `null`.

- [ ] **Step 2: Audit lifecycle code rather than adding another owner**

Confirm the production path remains exactly one ownership chain:

```java
// MCAClient.tickClient
BlueprintScreen.maintainTerrainCache(client);

// BlueprintScreen
public static void maintainTerrainCache(Minecraft client) {
    BlueprintTerrainRenderer.onClientLevelChanged(client.level);
}
```

`BlueprintTerrainRenderer.render` may defensively call `onClientLevelChanged(minecraft.level)` too, but there must be no second cache map, timer, background preload, or screen-owned texture copy.

- [ ] **Step 3: Run the fresh full automated gate**

Run:

```powershell
.\gradlew.bat :common:test :fabric:compileJava :neoforge:compileJava --rerun-tasks
```

Expected: `BUILD SUCCESSFUL` with zero failed tests and both loader compiles successful.

- [ ] **Step 4: Run whitespace and final diff checks**

Run:

```powershell
git diff --check
git diff -- common/src/main/java/net/conczin/mca/MCAClient.java common/src/main/java/net/conczin/mca/client/gui/BlueprintScreen.java common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java
git status --short
```

Expected:

- `git diff --check` reports no whitespace errors (line-ending warnings are not whitespace errors);
- the Blueprint diff shows one whole-tile lifecycle and no slice state machine;
- current terrain appearance/source-correctness edits remain present rather than being reverted;
- every non-Blueprint dirty/untracked path from the baseline remains untouched.

- [ ] **Step 5: Commit only the coherent Blueprint implementation after all automated gates are green**

Because the renderer/test files already contain approved Blueprint work from before this plan, review the complete Blueprint diff first. If that diff is intended as one coherent Blueprint change, stage only these four files:

```powershell
git add common/src/main/java/net/conczin/mca/MCAClient.java
git add common/src/main/java/net/conczin/mca/client/gui/BlueprintScreen.java
git add common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java
git add common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java
git diff --cached --check
git diff --cached --stat
git commit -m "refactor: simplify blueprint terrain caching"
```

Do not stage any other dirty/untracked path, regardless of whether it appeared before or during execution.

If the complete Blueprint diff contains a change that is not approved for the same commit, stop before `git add` and separate that change instead of silently committing it.

---

### Task 3: Perform the required in-game UX validation

**Files:**
- No code changes expected.
- If a runtime defect is found, return to Task 1 with a RED regression test before changing production code.

**Interfaces:**
- Consumes: built client with the simplified whole-tile renderer.
- Produces: runtime evidence for cold-open hitch, repeat-open cache reuse, panning, stale refresh, and level invalidation.

- [ ] **Step 1: Cold-open Blueprint with an empty terrain cache**

Use a terrain-heavy area with several visible tiles. Open Blueprint once after joining/changing dimension.

Expected:

- terrain fills at most one 128x128 tile per rendered frame;
- a short one-tile hitch is acceptable;
- there is no long freeze caused by sampling every visible tile in one frame;
- there is no 16x16 checkerboard/progressive-slice painting pattern.

- [ ] **Step 2: Close and immediately reopen Blueprint**

Expected: previously ready terrain is visible immediately from the warm cache before any stale refresh work.

- [ ] **Step 3: Pan across the one-tile prefetch boundary**

Expected: nearby prefetched terrain is usually already ready; a genuinely cold new tile may cost one frame but does not trigger a multi-tile synchronous freeze.

- [ ] **Step 4: Validate 600-tick stale-while-revalidate**

Leave Blueprint open for more than 30 seconds, preferably after changing a nearby terrain block.

Expected:

- stale cached texture remains visible while refresh is queued;
- at most one stale tile refresh occurs per frame;
- the map does not blank during refresh;
- refresh activity does not create a repeating five-second CPU/fan burst.

- [ ] **Step 5: Validate level/dimension invalidation**

Change dimension, or disconnect and reconnect, then open Blueprint.

Expected: terrain texture content from the previous `ClientLevel` is never reused.

- [ ] **Step 6: Report automated and visual evidence separately**

The final implementation report must state:

```text
Automated: focused Blueprint tests + :common:test + Fabric compile + NeoForge compile + git diff --check.
Runtime: cold open / repeat open / pan / >30s stale refresh / dimension-disconnect validation, with any observed hitch or fan behavior described explicitly.
```

Do not infer runtime success from Gradle alone.
