# Blueprint Lazy Terrain Streaming Implementation Plan

> **Superseded 2026-09-07:** This worker-thread/scheduler plan is retained as historical design work but must not be implemented. The approved simpler no-thread remediation is `docs/superpowers/plans/2026-09-07-blueprint-domain-review-remediation.md`.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace synchronous whole-tile Blueprint terrain sampling with viewport-prioritized, one-ring lazy streaming that snapshots already-loaded client terrain under a frame budget, rasterizes on one background worker, and uploads bounded cached textures on the render thread.

**Architecture:** `BlueprintTerrainRenderer` will become the client/render-thread coordinator. `BlueprintTerrainSampler` incrementally snapshots live 1.21.1 client chunk data into primitive arrays; `BlueprintTerrainScheduler` prioritizes/deduplicates visible and padding requests and serializes raster work; `BlueprintTerrainRasterizer` turns immutable snapshots into 128x128 CPU pixel buffers without any world or render-system access. Ready textures remain renderer-owned and capped at 96 entries.

**Tech Stack:** Java 21, Minecraft 1.21.1 Mojang mappings, Architectury common client code, JUnit 5, `ClientLevel`/`ClientChunkCache`, `LevelChunkSection.maybeHas`, `DynamicTexture`, `NativeImage`, single-thread `ExecutorService`.

**Spec:** `docs/superpowers/specs/2026-09-06-blueprint-lazy-terrain-streaming-design.md`

## Global Constraints

- Target branch: `feature/1.21.1-floor-clean-squash`; baseline behavior includes `ea5e3c751 fix: restore client seabed rendering`.
- Keep `TILE_BLOCK_SIZE = 128`, `SAMPLE_STEP = 1`, `MAX_CACHED_TILES = 96`, and `INCOMPLETE_TILE_RETRY_TICKS = 20L` unless runtime profiling demonstrates a measured need to tune them.
- Initial client-thread terrain snapshot budget: `750_000L` nanoseconds per rendered frame, with a secondary cap of one 16x16 chunk slice per frame.
- Maintain exactly one tile ring of padding around the visible tile rectangle, but admit visible loaded candidates nearest-first and padding only with remaining capacity under the 96-tile hard cap.
- Use at most one dedicated raster worker and at most two fully snapshotted tiles waiting for rasterization.
- Never call a chunk lookup with creation/force-load enabled for Blueprint terrain; use `ClientChunkCache.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false)`.
- Never read live `ClientLevel`, mutable `LevelChunk`, biome state, `TextureManager`, or `RenderSystem` state from the raster worker.
- Do not rely on client-unsent `Heightmap.Types.OCEAN_FLOOR` or `MOTION_BLOCKING_NO_LEAVES`; derive water floor and dry contour height from loaded client chunk sections.
- Preserve the validated underwater order: actual seabed block color -> water blend -> hillshade -> contour.
- Keep actual sand/stone/gravel differences visible below identical water.
- Do not retain a Java object per terrain column after a tile is installed; snapshots use primitive arrays and are discarded after rasterization.
- No disk map cache, JourneyMap region database, texture LOD chain, multiple terrain workers, or unrelated room/floor/pathfinding changes.
- Every behavior change follows RED -> GREEN TDD. Before each commit run the focused tests for that task; before completion run `./gradlew :common:test :fabric:compileJava :neoforge:compileJava --no-daemon --console=plain` (Windows: `.\gradlew.bat ...`).

---

## File Structure

- Create `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainTileKey.java`
  - World-anchored 128x128 tile identity shared by renderer, sampler and scheduler.
- Create `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainSnapshot.java`
  - Immutable primitive terrain data owned by one raster job; no world references.
- Create `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRasterizer.java`
  - Pure CPU color/water/hillshade/contour conversion to a 128x128 ARGB buffer.
- Create `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainSampler.java`
  - Client-thread incremental 16x16-slice sampler using only already-loaded chunks.
- Create `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainScheduler.java`
  - Desired-request admission, priority/deduplication, snapshot progress, one raster worker, stale/lifecycle handling.
- Create `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainMetrics.java`
  - Lightweight counters and fixed-size timing samples used for runtime verification.
- Modify `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java`
  - Viewport demand calculation, scheduler pump, texture upload/draw/LRU lifecycle; remove synchronous `TerrainTile.sample` / `Cell[][]` architecture.
- Create `common/src/test/java/net/conczin/mca/client/gui/BlueprintTerrainRasterizerTest.java`
- Create `common/src/test/java/net/conczin/mca/client/gui/BlueprintTerrainSamplerTest.java`
- Create `common/src/test/java/net/conczin/mca/client/gui/BlueprintTerrainSchedulerTest.java`
- Create `common/src/test/java/net/conczin/mca/client/gui/BlueprintTerrainMetricsTest.java`
- Modify `common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java`
  - Keep screen/viewport behavior tests; move terrain implementation tests to the focused classes above.

---

### Task 1: Primitive Snapshot and Pure Rasterizer

**Files:**
- Create: `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainTileKey.java`
- Create: `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainSnapshot.java`
- Create: `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRasterizer.java`
- Create: `common/src/test/java/net/conczin/mca/client/gui/BlueprintTerrainRasterizerTest.java`
- Modify: `common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java:145-205`
- Modify later in this task only as needed for helper delegation: `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java:160-240`

**Interfaces:**
- Produces: `record BlueprintTerrainTileKey(int minX, int minZ)` with `maxX()`, `maxZ()`, `centerX()`, `centerZ()`.
- Produces: immutable `BlueprintTerrainSnapshot` with `short[] heights`, four 128-entry edge-height arrays, `int[] terrainColors`, `int[] waterTints`, a `BitSet valid`, `long sampledAtGameTime`, and `boolean complete`.
- Produces: `BlueprintTerrainRasterizer.rasterize(BlueprintTerrainSnapshot snapshot) -> int[16384]` in ARGB order.
- Later tasks consume all three types; no later task may give the rasterizer a `ClientLevel`, `LevelChunk`, `NativeImage`, `DynamicTexture` or `TextureManager`.

- [ ] **Step 1: Write failing rasterizer tests for the visual contract and primitive data shape**

Create `BlueprintTerrainRasterizerTest` with literal expected colors. The production change these tests catch is moving water after hillshade or collapsing underwater terrain to one water color.

```java
@Test
void identicalWaterStillShowsDifferentSeabedBlocks() {
    BlueprintTerrainSnapshot sand = snapshotWithCenter(64, 0xffdbd3a0, 0x3f76e4);
    BlueprintTerrainSnapshot stone = snapshotWithCenter(64, 0xff7f7f7f, 0x3f76e4);

    int sandPixel = BlueprintTerrainRasterizer.rasterize(sand)[centerIndex()];
    int stonePixel = BlueprintTerrainRasterizer.rasterize(stone)[centerIndex()];

    assertNotEquals(sandPixel, stonePixel);
}

@Test
void waterBlendsBeforeHillshade() {
    assertEquals(0xff505050,
            BlueprintTerrainRasterizer.composeTerrainAndWater(
                    0xff000000, 0xffffff, 0.5F, false));
}

@Test
void missingNeighborHeightFallsBackToCurrentHeight() {
    BlueprintTerrainSnapshot snapshot = snapshotWithMissingNorthBorder(72, 0xff808080, -1);
    assertDoesNotThrow(() -> BlueprintTerrainRasterizer.rasterize(snapshot));
}
```

Use test-only builders in the test class; do not add production mutators just for tests.

- [ ] **Step 2: Run the new rasterizer tests and verify RED**

Run:

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.client.gui.BlueprintTerrainRasterizerTest --no-daemon --console=plain
```

Expected: FAIL because `BlueprintTerrainSnapshot` / `BlueprintTerrainRasterizer` do not exist.

- [ ] **Step 3: Add the tile key and immutable primitive snapshot**

Create `BlueprintTerrainTileKey.java`:

```java
package net.conczin.mca.client.gui;

record BlueprintTerrainTileKey(int minX, int minZ) {
    static final int SIZE = 128;

    int maxX() { return minX + SIZE; }
    int maxZ() { return minZ + SIZE; }
    double centerX() { return minX + SIZE / 2.0; }
    double centerZ() { return minZ + SIZE / 2.0; }
}
```

Create `BlueprintTerrainSnapshot.java`. Keep center columns at exactly 128x128. Preserve current edge hillshade without a 130x130 object graph by storing only four primitive neighbor-height strips; use `Short.MIN_VALUE` for an unavailable edge height.

```java
final class BlueprintTerrainSnapshot {
    static final int SIZE = 128;
    static final int COLUMN_COUNT = SIZE * SIZE;
    static final short MISSING_HEIGHT = Short.MIN_VALUE;
    static final int NO_WATER_TINT = -1;

    private final BlueprintTerrainTileKey key;
    private final long sampledAtGameTime;
    private final boolean complete;
    private final short[] heights;
    private final short[] northHeights;
    private final short[] southHeights;
    private final short[] westHeights;
    private final short[] eastHeights;
    private final int[] terrainColors;
    private final int[] waterTints;
    private final BitSet valid;

    int index(int localX, int localZ) { return localZ * SIZE + localX; }
    boolean valid(int localX, int localZ) { return valid.get(index(localX, localZ)); }
    int height(int localX, int localZ) { return heights[index(localX, localZ)]; }
    int terrainColor(int localX, int localZ) { return terrainColors[index(localX, localZ)]; }
    int waterTint(int localX, int localZ) { return waterTints[index(localX, localZ)]; }
    int validColumnCount() { return valid.cardinality(); }
}
```

The constructor accepts ownership of arrays built by the sampler and must validate exact lengths. Do not expose the mutable arrays.

- [ ] **Step 4: Move the pure visual math into `BlueprintTerrainRasterizer`**

Move the current validated constants and pure helpers from `BlueprintTerrainRenderer`: `hillshadeBrightness`, `multiplyTint`, `waterColor`, `composeTerrainAndWater`, `blendContour`, `blendOpaque`, `shadeColor`.

Implement:

```java
static int[] rasterize(BlueprintTerrainSnapshot snapshot) {
    int[] pixels = new int[BlueprintTerrainSnapshot.COLUMN_COUNT];
    for (int z = 0; z < BlueprintTerrainSnapshot.SIZE; z++) {
        for (int x = 0; x < BlueprintTerrainSnapshot.SIZE; x++) {
            int index = snapshot.index(x, z);
            if (!snapshot.valid(x, z)) {
                pixels[index] = 0x00000000;
                continue;
            }

            int height = snapshot.height(x, z);
            int north = snapshot.northHeight(x, z, height);
            int south = snapshot.southHeight(x, z, height);
            int west = snapshot.westHeight(x, z, height);
            int east = snapshot.eastHeight(x, z, height);
            float brightness = hillshadeBrightness(west, east, north, south);
            boolean contour = isNorthOrWestContour(snapshot, x, z, height, north, west);
            pixels[index] = composeTerrainAndWater(
                    snapshot.terrainColor(x, z),
                    snapshot.waterTint(x, z),
                    brightness,
                    contour);
        }
    }
    return pixels;
}
```

For interior neighbors, read `heights`; for the four outer edges, read the corresponding edge strip and fall back to the current height when the strip contains `MISSING_HEIGHT`.

- [ ] **Step 5: Run rasterizer tests and the existing Blueprint test class**

Run:

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.client.gui.BlueprintTerrainRasterizerTest --tests net.conczin.mca.client.gui.BlueprintScreenMapInteractionTest --no-daemon --console=plain
```

Expected: PASS. Update existing tests to call `BlueprintTerrainRasterizer` instead of renderer helpers; do not duplicate the same color-math tests in both classes.

- [ ] **Step 6: Commit Task 1**

```powershell
git add common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainTileKey.java common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainSnapshot.java common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRasterizer.java common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java common/src/test/java/net/conczin/mca/client/gui/BlueprintTerrainRasterizerTest.java common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java
git diff --cached --check
git commit -m "refactor: isolate blueprint terrain rasterization"
```

---

### Task 2: Client-Safe Incremental Terrain Sampler

**Files:**
- Create: `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainSampler.java`
- Create: `common/src/test/java/net/conczin/mca/client/gui/BlueprintTerrainSamplerTest.java`
- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainSnapshot.java`
- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java:200-480` only to delegate existing sampling helpers during transition; renderer wiring remains Task 4.

**Interfaces:**
- Consumes: `BlueprintTerrainTileKey`, `BlueprintTerrainSnapshot` from Task 1.
- Produces: `BlueprintTerrainSampler.Progress begin(BlueprintTerrainTileKey key, long gameTime)`.
- Produces: `BlueprintTerrainSampler.StepResult sampleNextSlice(ClientLevel level, Progress progress)` where one call processes at most one 16x16 tile/chunk intersection.
- Produces: `BlueprintTerrainSnapshot finish(Progress progress)` after all 64 core slices have been visited; unavailable chunks produce invalid columns and `complete=false`.
- Produces package-private pure helpers `findWaterFloor(...)` and `findDryTerrainHeight(...)` so section skipping can be RED/GREEN tested without a live Minecraft client.

- [ ] **Step 1: Write RED tests for water floor, dry height and unloaded chunk behavior**

Create tests that name the regressions we need to catch:

```java
@Test
void waterFloorSkipsWaterOnlySectionsBeforeReadingBlocks() {
    int[] reads = {0};
    IntFunction<BlockState> states = y -> {
        reads[0]++;
        return y > 40 ? Blocks.WATER.defaultBlockState() : Blocks.STONE.defaultBlockState();
    };
    IntPredicate sectionMayContainFloor = y -> Math.floorDiv(y, 16) <= 2;

    BlueprintTerrainSampler.ColumnSample floor = BlueprintTerrainSampler.findWaterFloor(
            states, sectionMayContainFloor, 64, -64);

    assertEquals(8, reads[0]);
    assertEquals(40, floor.y());
    assertEquals(Blocks.STONE.defaultBlockState(), floor.state());
}

@Test
void dryHeightIgnoresLeavesWithoutUsingMotionBlockingNoLeavesHeightmap() {
    IntFunction<BlockState> states = y -> switch (y) {
        case 75 -> Blocks.OAK_LEAVES.defaultBlockState();
        case 70 -> Blocks.GRASS_BLOCK.defaultBlockState();
        default -> Blocks.AIR.defaultBlockState();
    };

    assertEquals(71, BlueprintTerrainSampler.findDryTerrainHeight(
            states, y -> true, 76, -64));
}

@Test
void missingChunkMarksSliceIncompleteWithoutSamplingColumns() {
    BlueprintTerrainSampler.Progress progress = BlueprintTerrainSampler.begin(
            new BlueprintTerrainTileKey(0, 0), 100L);
    BlueprintTerrainSampler.StepResult result = BlueprintTerrainSampler.sampleSlice(
            progress, 0, 0, null, null);

    assertFalse(result.tileComplete());
    assertEquals(0, result.sampledColumns());
}
```

The test fixture for `sampleSlice` should pass a package-private `SliceAccess` adapter, not mock `ClientLevel`.

- [ ] **Step 2: Run sampler tests and verify RED**

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.client.gui.BlueprintTerrainSamplerTest --no-daemon --console=plain
```

Expected: FAIL because sampler interfaces do not exist.

- [ ] **Step 3: Implement `Progress` as primitive owned arrays plus slice cursor**

Use 64 core slices because a 128x128 aligned tile is exactly 8x8 chunks. `Progress` owns the mutable arrays until `finish` transfers them to `BlueprintTerrainSnapshot`.

```java
static final class Progress {
    final BlueprintTerrainTileKey key;
    final long sampledAtGameTime;
    final short[] heights = new short[BlueprintTerrainSnapshot.COLUMN_COUNT];
    final int[] terrainColors = new int[BlueprintTerrainSnapshot.COLUMN_COUNT];
    final int[] waterTints = new int[BlueprintTerrainSnapshot.COLUMN_COUNT];
    final BitSet valid = new BitSet(BlueprintTerrainSnapshot.COLUMN_COUNT);
    final short[] northHeights = missingBorder();
    final short[] southHeights = missingBorder();
    final short[] westHeights = missingBorder();
    final short[] eastHeights = missingBorder();
    int nextSlice;
    boolean complete = true;
}
```

Initialize `waterTints` to `BlueprintTerrainSnapshot.NO_WATER_TINT` and all border arrays to `MISSING_HEIGHT`.

- [ ] **Step 4: Implement the no-force loaded-chunk boundary**

All production chunk acquisition must go through one method:

```java
@Nullable
private static LevelChunk loadedChunk(ClientLevel level, int chunkX, int chunkZ) {
    return level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
}
```

Never fall back to `level.getChunk(chunkX, chunkZ)` because that API requests a chunk with creation semantics.

- [ ] **Step 5: Implement section-aware water and dry vertical searches**

For water, preserve the already-proven section skip:

```java
while (y >= minY) {
    int sectionMinY = Math.floorDiv(y, 16) * 16;
    if (!sectionMayContainFloor.test(y)) {
        y = sectionMinY - 1;
        continue;
    }
    for (; y >= Math.max(minY, sectionMinY); y--) {
        BlockState state = stateAtY.apply(y);
        if (state.blocksMotion()) return new ColumnSample(y, state);
    }
}
```

For dry terrain, use the exact 1.21.1 `MOTION_BLOCKING_NO_LEAVES` predicate semantics without the heightmap:

```java
private static boolean isDryHeightCandidate(BlockState state) {
    return (state.blocksMotion() || !state.getFluidState().isEmpty())
            && !(state.getBlock() instanceof LeavesBlock);
}
```

Use `LevelChunkSection.maybeHas(BlueprintTerrainSampler::isDryHeightCandidate)` before reading blocks in a section.

- [ ] **Step 6: Snapshot one 16x16 core slice and its relevant border heights**

For a loaded chunk slice:

1. use `chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ) + 1` as the client-valid upper bound;
2. get block/map/biome tint on the client thread;
3. for water, find the solid floor with section skipping and continue below `MapColor.NONE` until a drawable block is found;
4. for dry terrain, derive the no-leaves contour height with section skipping;
5. write `height`, `terrainColor`, `waterTint`, `valid` for each of 256 core columns;
6. when processing a tile-edge slice, also sample the adjacent one-block north/south/west/east height strip with no-force chunk lookup; missing neighbor chunks leave `MISSING_HEIGHT` so rasterizer falls back to the current cell height.

Do not rasterize or allocate `NativeImage` here.

Do not retain an old ready tile's primitive snapshot just to support incomplete refreshes. Missing columns remain transparent in the fresh snapshot; Task 5 preserves previously valid cached columns by merging those transparent pixels from the old registered texture on the client thread. This keeps the spec's incomplete-refresh behavior without reintroducing long-lived per-column snapshot memory.

- [ ] **Step 7: Finish by transferring primitive ownership to the immutable snapshot**

```java
BlueprintTerrainSnapshot finish(Progress progress) {
    if (progress.nextSlice != 64) throw new IllegalStateException("snapshot not complete");
    return new BlueprintTerrainSnapshot(
            progress.key,
            progress.sampledAtGameTime,
            progress.complete,
            progress.heights,
            progress.northHeights,
            progress.southHeights,
            progress.westHeights,
            progress.eastHeights,
            progress.terrainColors,
            progress.waterTints,
            progress.valid);
}
```

Do not clone these arrays; ownership transfers once and `Progress` is no longer used.

- [ ] **Step 8: Run sampler + rasterizer tests and commit**

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.client.gui.BlueprintTerrainSamplerTest --tests net.conczin.mca.client.gui.BlueprintTerrainRasterizerTest --no-daemon --console=plain
git add common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainSampler.java common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainSnapshot.java common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java common/src/test/java/net/conczin/mca/client/gui/BlueprintTerrainSamplerTest.java
git diff --cached --check
git commit -m "feat: snapshot blueprint terrain incrementally"
```

---

### Task 3: Viewport Priority Scheduler and Single Raster Worker

**Files:**
- Create: `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainScheduler.java`
- Create: `common/src/test/java/net/conczin/mca/client/gui/BlueprintTerrainSchedulerTest.java`
- Test utility remains nested inside the test class; do not create production executor controls only for tests.

**Interfaces:**
- Consumes: `BlueprintTerrainTileKey`, `BlueprintTerrainSnapshot`, `BlueprintTerrainSampler`, `BlueprintTerrainRasterizer`.
- Produces: `enum DemandClass { VISIBLE, PADDING }`.
- Produces: `record Demand(BlueprintTerrainTileKey key, DemandClass demandClass, double distanceSq, boolean needsBuild)`.
- Produces: `record RasterResult(BlueprintTerrainTileKey key, long requestGeneration, long sampledAtGameTime, boolean complete, int validColumnCount, int[] pixels, long rasterNanos)`.
- Produces scheduler methods `updateDemand(Collection<Demand>)`, `advanceClientSampling(ClientLevel, long budgetNanos, int maxSlices)`, package-private `submitSnapshot(BlueprintTerrainSnapshot)`, `drainCompleted()`, `isDesired(key)`, `desiredClass(key)`, `close()`.
- Produces pure/package-private scheduler observations used by behavior tests and renderer admission: `static List<Demand> admit(Collection<Demand>, int cap)`, `Request peekNextRequest()`, and `int requestCount()`.
- Production constructor creates a one-thread named daemon `ThreadPoolExecutor` with an `ArrayBlockingQueue` capacity of two; package-private constructor accepts an `ExecutorService` and `LongSupplier nanoTime` for deterministic tests.
- Client-thread request/snapshot state remains client-thread-owned. The worker communicates back only through a `ConcurrentLinkedQueue<RasterResult>` plus atomic lifecycle/outstanding counters; it never mutates the desired-request map or renderer cache.

- [ ] **Step 1: Write RED tests for ordering, dedupe, admission and stale demand**

```java
@Test
void visibleOutranksCloserPadding() {
    scheduler.updateDemand(List.of(
            demand(tile(0, 0), PADDING, 1.0, true),
            demand(tile(128, 0), VISIBLE, 100.0, true)));

    assertEquals(tile(128, 0), scheduler.peekNextRequest().key());
}

@Test
void nearestVisibleWinsWithinSameClass() {
    scheduler.updateDemand(List.of(
            demand(tile(0, 0), VISIBLE, 100.0, true),
            demand(tile(128, 0), VISIBLE, 25.0, true)));

    assertEquals(tile(128, 0), scheduler.peekNextRequest().key());
}

@Test
void duplicateDemandCoalescesAndPaddingPromotionKeepsIdentity() {
    BlueprintTerrainTileKey key = tile(0, 0);
    scheduler.updateDemand(List.of(demand(key, PADDING, 10.0, true)));
    scheduler.updateDemand(List.of(demand(key, VISIBLE, 10.0, true)));

    assertEquals(1, scheduler.requestCount());
    assertEquals(VISIBLE, scheduler.desiredClass(key));
}

@Test
void staleQueuedRequestIsRemovedBeforeSnapshotAllocation() {
    scheduler.updateDemand(List.of(demand(tile(0, 0), PADDING, 1.0, true)));
    scheduler.updateDemand(List.of());
    assertNull(scheduler.peekNextRequest());
}
```

- [ ] **Step 2: Add RED tests for the bounded one-worker configuration and lifecycle behavior**

Use a nested `ManualExecutorService` for stale/close tests, and expose a package-private production executor factory so its actual one-thread/two-waiter bounds can be asserted without timing races.

```java
@Test
void productionRasterExecutorHasOneWorkerAndTwoWaitingSlots() {
    ThreadPoolExecutor executor = BlueprintTerrainScheduler.newRasterExecutor();
    try {
        assertEquals(1, executor.getCorePoolSize());
        assertEquals(1, executor.getMaximumPoolSize());
        assertEquals(2, executor.getQueue().remainingCapacity());
    } finally {
        executor.shutdownNow();
    }
}

@Test
void closingDropsLateCompletion() {
    scheduler.enqueueSnapshot(snapshot(tile(0, 0)));
    scheduler.close();
    executor.runNextIgnoringShutdown();

    assertTrue(scheduler.drainCompleted().isEmpty());
}

@Test
void staleCompletionCannotOverwriteNewerGeneration() {
    BlueprintTerrainTileKey key = tile(0, 0);
    scheduler.updateDemand(List.of(demand(key, VISIBLE, 0.0, true)));
    scheduler.submitSnapshot(snapshot(key));

    scheduler.updateDemand(List.of(demand(key, VISIBLE, 0.0, false)));
    scheduler.updateDemand(List.of(demand(key, VISIBLE, 0.0, true)));
    executor.runNext();

    assertTrue(scheduler.drainCompleted().isEmpty());
}
```

- [ ] **Step 3: Run scheduler tests and verify RED**

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.client.gui.BlueprintTerrainSchedulerTest --no-daemon --console=plain
```

Expected: FAIL because scheduler does not exist.

- [ ] **Step 4: Implement admitted desired requests with the 96-tile hard cap**

`updateDemand` must sort by class then distance and retain at most 96 desired keys:

```java
Comparator<Demand> ORDER = Comparator
        .comparing(Demand::demandClass)
        .thenComparingDouble(Demand::distanceSq)
        .thenComparingInt(d -> d.key().minX())
        .thenComparingInt(d -> d.key().minZ());
```

Declare `VISIBLE` before `PADDING` so enum order matches priority. For duplicate keys keep the stronger class and minimum distance. Remove requests no longer admitted before creating a `BlueprintTerrainSampler.Progress`.

- [ ] **Step 5: Implement client-thread snapshot advancement with a time and slice cap**

```java
void advanceClientSampling(ClientLevel level, long budgetNanos, int maxSlices) {
    long deadline = nanoTime.getAsLong() + budgetNanos;
    int slices = 0;
    while (slices < maxSlices && nanoTime.getAsLong() < deadline) {
        Request request = activeOrNextRequest();
        if (request == null || rasterOutstanding.get() >= MAX_RASTER_OUTSTANDING) return;
        BlueprintTerrainSampler.StepResult step = sampler.sampleNextSlice(level, request.progress());
        slices++;
        if (step.finished()) submitSnapshot(sampler.finish(request.progress()));
    }
}
```

The initial production call will pass `750_000L` and `1`. Never start a second slice in the same frame even if the timer has room.

- [ ] **Step 6: Implement the bounded single-flight raster queue**

Do not share an `ArrayDeque` or plain `boolean rasterRunning` between the client and worker threads. Let the JDK executor own the waiting queue and use a thread-safe completion handoff:

```java
private static final int MAX_PENDING_SNAPSHOTS = 2;
private static final int MAX_RASTER_OUTSTANDING = 1 + MAX_PENDING_SNAPSHOTS;
private final AtomicInteger rasterOutstanding = new AtomicInteger();
private final ConcurrentLinkedQueue<RasterResult> completed = new ConcurrentLinkedQueue<>();
private final AtomicBoolean closed = new AtomicBoolean();
private final AtomicLong lifecycleGeneration = new AtomicLong();

static ThreadPoolExecutor newRasterExecutor() {
    return new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_PENDING_SNAPSHOTS),
            runnable -> {
                Thread thread = new Thread(runnable, "MCA Blueprint Terrain Raster");
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());
}
```

`submitSnapshot(...)` increments `rasterOutstanding` only after reserving one of the three running/waiting slots, then calls `executor.execute(...)`. The worker calls only `BlueprintTerrainRasterizer.rasterize(snapshot)` and measures its own duration; it must not capture `ClientLevel`, `LevelChunk`, renderer state, request maps or texture state. It offers a successful `RasterResult` to `completed` only when the lifecycle token still matches and `closed` is false, then decrements `rasterOutstanding` in `finally`.

Catch `RejectedExecutionException` only for the close/race boundary: release the reserved outstanding slot and leave the request eligible for a later rebuild if the scheduler is still open. Catch rasterization exceptions inside the worker, log once with the tile key, drop that tile result, and leave any previous ready texture untouched.

- [ ] **Step 7: Add per-key request generation and lifecycle generation checks**

Increment a client-thread-owned per-key `requestGeneration` whenever a new build replaces an older pending build. Capture both `requestGeneration` and `lifecycleGeneration.get()` when submitting work. The worker may check only the atomic lifecycle/closed state before offering a completion. When the client thread drains `completed`, compare the result's request generation with the current per-key generation; stale results are discarded and counted, never installed. `close()` sets `closed`, increments the lifecycle generation, calls `shutdownNow()`, and clears queued completions so late workers cannot retain pixel arrays after the screen closes.

- [ ] **Step 8: Run scheduler tests and commit**

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.client.gui.BlueprintTerrainSchedulerTest --tests net.conczin.mca.client.gui.BlueprintTerrainSamplerTest --tests net.conczin.mca.client.gui.BlueprintTerrainRasterizerTest --no-daemon --console=plain
git add common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainScheduler.java common/src/test/java/net/conczin/mca/client/gui/BlueprintTerrainSchedulerTest.java
git diff --cached --check
git commit -m "feat: schedule blueprint terrain streaming"
```

---

### Task 4: Renderer Viewport Demand, One-Ring Padding and Async Handoff

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java:26-480`
- Modify: `common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java`
- Modify: `common/src/test/java/net/conczin/mca/client/gui/BlueprintTerrainSchedulerTest.java`

**Interfaces:**
- Consumes scheduler/sampler/rasterizer APIs from Tasks 1-3.
- Produces renderer-owned `record ReadyTile(ResourceLocation textureLocation, DynamicTexture texture, long sampledAtGameTime, boolean complete, int validColumnCount)`.
- Produces pure package-private `static List<BlueprintTerrainScheduler.Demand> demandsForViewport(...)` for behavior tests.
- Produces `uploadCompleted(Minecraft minecraft)` and `renderReadyTiles(GuiGraphics context, visible bounds)`; neither method performs world scanning.

- [ ] **Step 1: Write RED tests for visible rectangle, padding and extreme-view admission**

```java
@Test
void viewportAddsExactlyOneTilePaddingRing() {
    BlueprintMapViewport viewport = BlueprintMapViewport.create(0, 0, 256, 64.0, 64.0, 1.0F);
    List<Demand> demands = BlueprintTerrainRenderer.demandsForViewport(
            viewport, key -> true, Set.of(), Map.of());

    assertTrue(demands.stream().anyMatch(d -> d.demandClass() == VISIBLE));
    assertTrue(demands.stream().anyMatch(d -> d.demandClass() == PADDING));
    assertEquals(1, paddingRingThicknessInTiles(demands));
}

@Test
void unloadedPaddingIsNotQueued() {
    BlueprintTerrainTileKey unloaded = new BlueprintTerrainTileKey(256, 256);
    List<Demand> demands = BlueprintTerrainRenderer.demandsForViewport(
            viewportAroundOrigin(), key -> !key.equals(unloaded), Set.of(), Map.of());

    assertFalse(demands.stream().anyMatch(d -> d.key().equals(unloaded) && d.needsBuild()));
}

@Test
void hugeViewportNeverAdmitsMoreThanCacheCap() {
    List<Demand> admitted = BlueprintTerrainScheduler.admit(demands(300), 96);
    assertEquals(96, admitted.size());
    assertTrue(admitted.stream().noneMatch(d -> d.demandClass() == PADDING)
            || visibleCount(admitted) < 96);
}
```

- [ ] **Step 2: Run renderer/scheduler tests and verify RED**

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.client.gui.BlueprintScreenMapInteractionTest --tests net.conczin.mca.client.gui.BlueprintTerrainSchedulerTest --no-daemon --console=plain
```

Expected: FAIL because viewport demand helpers and async renderer path do not exist.

- [ ] **Step 3: Replace synchronous `TerrainTile.sample` calls in `render()` with demand update + scheduler pump**

The render-loop shape becomes:

```java
void render(GuiGraphics graphics, BlueprintMapViewport viewport) {
    Minecraft minecraft = Minecraft.getInstance();
    ClientLevel level = minecraft.level;
    if (level == null) return;

    List<Demand> demands = demandsForViewport(
            viewport,
            key -> tileTouchesLoadedChunk(level, key),
            readyTiles.keySet(),
            readyStateByKey());
    scheduler.updateDemand(demands);
    scheduler.advanceClientSampling(level, SNAPSHOT_BUDGET_NANOS, MAX_SLICES_PER_FRAME);
    uploadCompleted(minecraft);
    renderReadyTiles(graphics, viewport);
    trimCache();
}
```

Delete `TileSamplingBudget`; no call from `render()` may invoke a whole 128x128 tile sampler.

- [ ] **Step 4: Implement candidate generation without force-loading**

Calculate the visible tile min/max exactly as today. Generate one additional tile in each direction for padding. Before a missing tile becomes a build demand, test whether any of its 8x8 chunk positions is present using `level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false) != null`. A ready cached tile remains drawable even when its chunks later leave the client cache.

Set `needsBuild` when:

```java
ready == null || (!ready.complete()
        && gameTime - ready.sampledAtGameTime() >= INCOMPLETE_TILE_RETRY_TICKS)
```

- [ ] **Step 5: Preserve visible-only drawing while padding stays prefetch-only**

Iterate only visible tile keys for `GuiGraphics.blit`. Padding tiles may be ready in the cache but are not drawn outside the current viewport demand. This keeps prefetch invisible and prevents an expanded map draw area.

- [ ] **Step 6: Run focused tests and commit renderer wiring**

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.client.gui.BlueprintScreenMapInteractionTest --tests net.conczin.mca.client.gui.BlueprintTerrainSchedulerTest --tests net.conczin.mca.client.gui.BlueprintTerrainSamplerTest --no-daemon --console=plain
git add common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java common/src/test/java/net/conczin/mca/client/gui/BlueprintTerrainSchedulerTest.java
git diff --cached --check
git commit -m "feat: stream blueprint terrain around viewport"
```

---

### Task 5: Render-Thread Texture Installation, LRU Eviction and Legacy Cell Removal

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java`
- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainScheduler.java`
- Modify: `common/src/test/java/net/conczin/mca/client/gui/BlueprintTerrainSchedulerTest.java`
- Modify: `common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java`

**Interfaces:**
- Consumes `RasterResult` from Task 3.
- Renderer ready cache becomes `LinkedHashMap<BlueprintTerrainTileKey, ReadyTile>` in access-order.
- `ReadyTile` owns both the registered `ResourceLocation` and its `DynamicTexture` reference so an incomplete refresh can copy already-valid pixels from `DynamicTexture.getPixels()` before atomically replacing the texture.
- Renderer never stores `BlueprintTerrainSnapshot`, `Cell[][]`, or worker `int[]` pixel buffers in a ready entry.

- [ ] **Step 1: Write RED tests for stale completion, incomplete replacement and cache cap**

```java
@Test
void incompleteRefreshNeverReplacesMoreCompleteReadyTile() {
    ReadyTile old = readyTile(true, 16384);
    RasterResult refresh = rasterResult(false, 12000);
    assertFalse(BlueprintTerrainRenderer.shouldReplace(old, refresh));
}

@Test
void incompleteRefreshPreservesPreviouslyValidPixels() {
    int oldNativePixel = FastColor.ABGR32.fromArgb32(0xff486a3d);
    int freshMissingArgb = 0x00000000;

    assertEquals(oldNativePixel,
            BlueprintTerrainRenderer.selectNativeUploadPixel(freshMissingArgb, oldNativePixel));
}

@Test
void freshValidPixelWinsOverPreviousPartialPixel() {
    int oldNativePixel = FastColor.ABGR32.fromArgb32(0xff486a3d);
    int freshArgb = 0xff7f7f7f;

    assertEquals(FastColor.ABGR32.fromArgb32(freshArgb),
            BlueprintTerrainRenderer.selectNativeUploadPixel(freshArgb, oldNativePixel));
}

@Test
void cacheEvictionPrefersEldestUnneededTile() {
    List<BlueprintTerrainTileKey> lruOrder = List.of(unneededKey, visibleA, visibleB);
    assertEquals(unneededKey,
            BlueprintTerrainRenderer.selectEldestEvictable(lruOrder, Set.of(visibleA, visibleB)));
}
```

- [ ] **Step 2: Run focused tests and verify RED**

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.client.gui.BlueprintScreenMapInteractionTest --tests net.conczin.mca.client.gui.BlueprintTerrainSchedulerTest --no-daemon --console=plain
```

- [ ] **Step 3: Implement render-thread pixel upload with incomplete-refresh merging**

Drain worker results only from `render()`/client thread. Valid rasterized terrain pixels are always opaque; `0x00000000` means the fresh snapshot had no loaded data for that column. For an incomplete refresh, copy the previous registered texture's native pixel into those transparent positions so a newly loaded region can improve a partial tile without erasing older cached coverage.

```java
private ReadyTile upload(Minecraft minecraft, RasterResult result, @Nullable ReadyTile previous) {
    NativeImage image = new NativeImage(128, 128, true);
    NativeImage previousPixels = previous == null ? null : previous.texture().getPixels();
    int mergedValidColumns = 0;
    for (int z = 0; z < 128; z++) {
        for (int x = 0; x < 128; x++) {
        int index = z * 128 + x;
            int oldNative = previousPixels == null ? 0 : previousPixels.getPixelRGBA(x, z);
            int nativePixel = selectNativeUploadPixel(result.pixels()[index], oldNative);
            image.setPixelRGBA(x, z, nativePixel);
            if (FastColor.ABGR32.alpha(nativePixel) != 0) mergedValidColumns++;
        }
    }

    if (!result.complete() && previous != null
            && mergedValidColumns <= previous.validColumnCount()) {
        image.close();
        return previous;
    }

    DynamicTexture texture = new DynamicTexture(image);
    texture.setFilter(false, false);
    try {
        ResourceLocation location = minecraft.getTextureManager().register("mca_blueprint_terrain", texture);
        return new ReadyTile(location, texture, result.sampledAtGameTime(),
                result.complete() || mergedValidColumns == 16384, mergedValidColumns);
    } catch (RuntimeException exception) {
        texture.close();
        throw exception;
    }
}

static int selectNativeUploadPixel(int freshArgb, int previousNativePixel) {
    return FastColor.ARGB32.alpha(freshArgb) == 0
            ? previousNativePixel
            : FastColor.ABGR32.fromArgb32(freshArgb);
}
```

Do not release the previous texture until the replacement texture has been successfully registered. If an incomplete refresh adds no valid coverage and the old tile is already present, keep the old tile and discard the staged replacement; real-time terrain-edit invalidation remains out of scope.

- [ ] **Step 4: Implement replacement and cache admission rules**

Before upload, reject an incomplete result when an existing tile is already complete. Otherwise merge it as above. `upload(...)` returns the previous `ReadyTile` unchanged when an incomplete refresh adds no coverage, so no new texture is registered. Install a newly created tile only if the result is complete or the merged valid-column count is greater than the previous count:

```java
if (old == null) return result.validColumnCount() > 0;
if (old.complete() && !result.complete()) return false;
if (result.complete()) return true;
return merged.validColumnCount() > old.validColumnCount();
```

If a completion is no longer desired and the cache is already full, discard its pixels without uploading. If admitted, install it and then release any replaced texture.

- [ ] **Step 5: Enforce LRU size 96 without evicting admitted visible demand for padding**

Before installing padding when the cache is full, drop the padding result. For visible installs, evict the eldest cache entry not in the scheduler's admitted visible set; if every cache entry is admitted visible, discard the farther/new candidate rather than exceeding 96.

Implement package-private pure `selectEldestEvictable(List<BlueprintTerrainTileKey> lruOrder, Set<BlueprintTerrainTileKey> admittedVisible)` and use the access-order `LinkedHashMap` key iteration as its production input. This is the helper exercised by the RED cache-eviction test above.

- [ ] **Step 6: Remove the legacy synchronous terrain object graph**

Delete from `BlueprintTerrainRenderer`:

- nested `TerrainTile`;
- nested `Cell`;
- private `TileKey` in favor of `BlueprintTerrainTileKey`;
- `createTexture(TerrainTile)`;
- `TerrainTile.sample(...)`;
- old vertical/biome helpers now owned by sampler/rasterizer.

After this step the ready cache contains only `ReadyTile` metadata + the already-required registered `DynamicTexture`/`ResourceLocation`; snapshots and worker raster pixel arrays are dropped after upload. The texture's existing `NativeImage` is the only prior-pixel source used for incomplete refresh merging.

- [ ] **Step 7: Verify no behavior regression and commit**

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.client.gui.BlueprintTerrainRasterizerTest --tests net.conczin.mca.client.gui.BlueprintTerrainSamplerTest --tests net.conczin.mca.client.gui.BlueprintTerrainSchedulerTest --tests net.conczin.mca.client.gui.BlueprintScreenMapInteractionTest --no-daemon --console=plain
git add common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainScheduler.java common/src/test/java/net/conczin/mca/client/gui/BlueprintTerrainSchedulerTest.java common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java
git diff --cached --check
git commit -m "refactor: bound blueprint terrain cache lifecycle"
```

---

### Task 6: Performance Metrics and Deterministic Budget Tests

**Files:**
- Create: `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainMetrics.java`
- Create: `common/src/test/java/net/conczin/mca/client/gui/BlueprintTerrainMetricsTest.java`
- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainScheduler.java`
- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java`
- Modify: `common/src/test/java/net/conczin/mca/client/gui/BlueprintTerrainSchedulerTest.java`

**Interfaces:**
- Produces `BlueprintTerrainMetrics.Snapshot snapshot()` containing requested, snapshotted, rasterized, installed, staleDropped, readyTileCount, snapshotBytesInFlight, pixelBytesInFlight, max/p95 snapshot nanos, max/p95 raster nanos, max/p95 upload nanos.
- Timing samples use fixed-size 256-entry `long[]` rings; all ring mutation occurs on client thread. Worker result carries `rasterNanos` and client thread records it when draining completions.

- [ ] **Step 1: Write RED tests for p95 and counters**

```java
@Test
void p95UsesRecordedClientThreadDurations() {
    BlueprintTerrainMetrics metrics = new BlueprintTerrainMetrics();
    for (int i = 1; i <= 100; i++) metrics.recordSnapshotNanos(i);

    assertEquals(95, metrics.snapshot().p95SnapshotNanos());
    assertEquals(100, metrics.snapshot().maxSnapshotNanos());
}

@Test
void staleAndInstalledCountersAreIndependent() {
    BlueprintTerrainMetrics metrics = new BlueprintTerrainMetrics();
    metrics.recordInstalled();
    metrics.recordStaleDropped();
    metrics.recordStaleDropped();

    assertEquals(1, metrics.snapshot().installed());
    assertEquals(2, metrics.snapshot().staleDropped());
}
```

- [ ] **Step 2: Run metrics tests and verify RED**

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.client.gui.BlueprintTerrainMetricsTest --no-daemon --console=plain
```

- [ ] **Step 3: Implement fixed-size timing rings without per-frame allocation**

Store raw nanos in preallocated arrays. Compute p95 only when `snapshot()` is requested by copying the currently populated part into a temporary array and sorting that copy. Do not sort or allocate in the render hot path.

```java
private static long percentile95(long[] values, int count) {
    if (count == 0) return 0L;
    long[] copy = Arrays.copyOf(values, count);
    Arrays.sort(copy);
    int index = Math.min(count - 1, (int) Math.ceil(count * 0.95) - 1);
    return copy[index];
}
```

- [ ] **Step 4: Instrument the three measured stages**

- Around each client-thread slice: record elapsed snapshot nanos and update snapshot bytes in flight only when a full snapshot is queued.
- Worker includes `rasterNanos` in `RasterResult`; record it on client thread while draining completion.
- Around `NativeImage` fill + `DynamicTexture` registration: record upload nanos.
- Update requested/snapshotted/rasterized/installed/staleDropped counters at state transitions, not every render loop iteration.
- Publish ready tile count from renderer when `metrics.snapshot()` is requested.

Add an opt-in runtime logging path so these numbers are actually obtainable during the required client profile. When `-Dmca.debug.blueprintTerrainMetrics=true` is present, `BlueprintTerrainRenderer` logs one `BlueprintTerrainMetrics.Snapshot` through `MCA.LOGGER` at most once every two seconds while the screen is open. Keep the default path silent and allocation-free apart from the already-recorded fixed-size timing rings.

- [ ] **Step 5: Add deterministic scheduler budget test**

Inject a fake `LongSupplier` sequence proving that even with time remaining only one slice is sampled when `maxSlices=1`, and that no slice begins once `budgetNanos` has expired.

```java
@Test
void oneSliceSecondaryCapWinsEvenWhenTimeBudgetRemains() {
    scheduler.advanceClientSampling(level, 750_000L, 1);
    assertEquals(1, sampler.sampledSliceCount());
}
```

Use a test `SliceSampler` adapter rather than a mocked `ClientLevel` for this scheduler-only test.

- [ ] **Step 6: Run metrics/scheduler tests and commit**

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.client.gui.BlueprintTerrainMetricsTest --tests net.conczin.mca.client.gui.BlueprintTerrainSchedulerTest --no-daemon --console=plain
git add common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainMetrics.java common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainScheduler.java common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java common/src/test/java/net/conczin/mca/client/gui/BlueprintTerrainMetricsTest.java common/src/test/java/net/conczin/mca/client/gui/BlueprintTerrainSchedulerTest.java
git diff --cached --check
git commit -m "perf: measure blueprint terrain streaming"
```

---

### Task 7: Full Verification and Runtime Profiling Gate

**Files:**
- Modify only files needed to fix failures discovered by verification.
- Do not add unrelated refactors during this task.

**Interfaces:**
- Consumes all prior tasks.
- Produces evidence that the implementation meets every acceptance criterion in the approved spec.

- [ ] **Step 1: Run the complete automated verification**

```powershell
.\gradlew.bat :common:test :fabric:compileJava :neoforge:compileJava --no-daemon --console=plain
git diff --check
git status --short
```

Expected: Gradle `BUILD SUCCESSFUL`; `git diff --check` exits 0. Existing unrelated `ExtendedWalkTowardsTask.java` and older untracked Sept-4 Blueprint docs may still appear in status and must not be staged.

- [ ] **Step 2: Verify scheduler/worker thread boundaries with tests and code review**

Confirm these concrete properties before runtime testing:

```text
BlueprintTerrainRenderer.render
  -> demand calculation
  -> scheduler.advanceClientSampling(ClientLevel, 750_000ns, 1 slice)
  -> worker receives BlueprintTerrainSnapshot only
  -> BlueprintTerrainRasterizer.rasterize(snapshot)
  -> client thread drains RasterResult
  -> NativeImage/DynamicTexture/TextureManager
```

There must be no worker capture of `ClientLevel`, `LevelChunk`, `TextureManager`, `GuiGraphics` or `Minecraft`.

- [ ] **Step 3: Runtime profile first Blueprint open**

Launch the 1.21.1 dev client using the repository's normal client run configuration with `-Dmca.debug.blueprintTerrainMetrics=true`. With an empty Blueprint terrain cache, open Blueprint and capture the logged `BlueprintTerrainMetrics.Snapshot` values after terrain settles.

Pass conditions:

- p95 client snapshot slice <= `750_000 ns` in the sampled window;
- max slices per rendered frame = 1;
- raster jobs run one at a time;
- ready tile count <= 96;
- no forced chunk requests/log errors.

- [ ] **Step 4: Runtime profile panning and zoomed-out/Fit behavior**

Exercise both slow and fast panning across multiple 128-block tile boundaries, then Fit/zoomed-out view.

Record and inspect:

```text
requested
snapshotted
rasterized
installed
staleDropped
readyTileCount
snapshotBytesInFlight
pixelBytesInFlight
p95/max snapshot nanos
p95/max raster nanos
p95/max upload nanos
```

Pass conditions: visible requests install before padding under contention; stale work rises during fast pans without unbounded queue growth; padding never causes ready tiles >96; unloaded distant chunks in Fit view are not queued merely because they are mathematically inside the viewport.

- [ ] **Step 5: Runtime verify shallow/deep water and dry terrain**

Inspect at least one shallow-water and one deep-water area with visibly different underwater blocks (sand vs stone/gravel). Confirm:

- seabed differences remain visible below identical biome water;
- no flat-water regression from `OCEAN_FLOOR` misuse;
- dry terrain/leaf canopy contour behavior matches the pre-streaming baseline closely enough that no tile-edge seam or leaf-height regression is visible.

If dry terrain differs, fix the section-aware `MOTION_BLOCKING_NO_LEAVES` equivalent under a new RED test before continuing.

- [ ] **Step 6: Apply the escalation gate only if measured snapshot time fails**

Do **not** increase the client-thread budget first. If p95 snapshot work exceeds `750_000 ns` materially on the tested machine, profile whether block/section reads dominate. Only then implement the spec's copied-section escalation in a separate follow-up task/commit:

```java
PalettedContainer<BlockState> immutableStates = chunk.getSection(sectionIndex).getStates().copy();
```

Bound copied candidate sections to one loaded chunk at a time, capture required biome/tint data on client thread, and pass only immutable copies to the same one raster worker. Re-run Tasks 2-7 tests and runtime measurements before accepting it.

- [ ] **Step 7: Final verification commit if runtime testing required code changes**

If no code changes were needed, do not create an empty commit. If fixes were needed:

```powershell
.\gradlew.bat :common:test :fabric:compileJava :neoforge:compileJava --no-daemon --console=plain
git diff --check
git add <only files changed for verified fixes>
git commit -m "fix: finalize blueprint terrain streaming"
```

Final completion report must state automated results and measured runtime values separately. Do not claim the hitch is fixed from compilation/tests alone.
