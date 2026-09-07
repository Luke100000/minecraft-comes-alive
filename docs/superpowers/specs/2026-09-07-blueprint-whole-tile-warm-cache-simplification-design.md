# Blueprint Whole-Tile Warm Cache Simplification Design

Date: 2026-09-07

Status: approved design direction; implementation not started

Target: `feature/1.21.1-floor-clean-squash`

## Goal

Simplify `BlueprintTerrainRenderer` by replacing the current 64-slice-per-tile streaming state machine with whole-tile sampling, while keeping the warm session cache that makes repeated Blueprint opens immediate.

The intended trade-off is explicit: accept a small bounded amount of synchronous terrain work on a cold cache in exchange for substantially less renderer state and control flow. The user has observed that the earlier whole-tile hitch was not materially disruptive; the more noticeable problem was seeing terrain progressively paint in on every open.

## Why change the current design

The current renderer divides each 128x128 terrain tile into sixty-four 16x16 slices and maintains per-slice sampling, retry, staleness, dependency-halo, texture-dirty, and nearest-ready scheduling state. This successfully bounds client-thread work, but it also:

- exposes progressive map construction visually on a cold open;
- creates several parallel state arrays per tile;
- requires slice-selection and dependency-halo helpers;
- requires a nanosecond frame budget;
- requires partial texture refresh bookkeeping;
- makes retry and staleness behavior harder to reason about than the visible result warrants.

The new warm cache already removes the most important repeated-open cost. Once a tile has been rendered in the current `ClientLevel`, closing and reopening Blueprint can reuse the same ready texture. That changes the performance trade-off enough that the slice state machine is no longer justified by the observed UX.

## Architecture constraints

- Keep `BlueprintTerrainRenderer` as the single terrain sampling, cache, texture, and draw owner.
- Keep terrain work on the client/render thread. Do not add workers, executor queues, scheduler classes, or a second terrain representation.
- Keep world-coordinate 128x128 tile identity.
- Keep `MAX_CACHED_TILES = 96` and access-order LRU eviction unless later profiling demonstrates a need to tune it.
- Keep the cache session-scoped to one `ClientLevel` identity. Reopening Blueprint in the same level reuses ready tiles; disconnecting or changing level/dimension releases all cached textures.
- Keep one tile of prefetch padding around the visible viewport.
- Never request, generate, or force-load a Minecraft chunk for Blueprint terrain. All chunk access must use the existing nullable client-cache lookup path.
- Keep `SAMPLE_STEP = 1` and nearest texture filtering.
- Preserve the currently approved terrain color, water/substrate, hillshade, contour, and dry-height semantics. This simplification changes scheduling/cache mechanics only; it must not silently resolve or revert a separate terrain-appearance/source-correctness change.

## Proposed tile model

Replace per-slice lifecycle state with one lifecycle per 128x128 `TerrainTile`.

Each cached tile needs only:

- its world-space tile coordinates;
- primitive terrain sample arrays needed to render the tile;
- one `DynamicTexture`/`NativeImage` and texture location;
- `sampledAtGameTime` for the last successful whole-tile sample attempt;
- `retryAfterGameTime` when the last sample was incomplete because client chunks were unavailable;
- a boolean or equivalent completeness marker;
- no per-slice timestamps, retry arrays, dirty arrays, bitmasks, or dependency state.

The exact primitive sample arrays may remain as they are if they are still useful to the existing raster path. Do not reintroduce per-block object graphs solely as part of this simplification.

## Render and scheduling flow

Every Blueprint render should follow one straightforward sequence.

1. Compute the visible tile rectangle from `BlueprintMapViewport`.
2. Compute one additional tile ring for prefetch.
3. Draw every ready cached tile that intersects the visible rectangle immediately, including stale tiles awaiting refresh.
4. Build a small candidate set for terrain work:
   - missing visible tiles first;
   - incomplete visible tiles whose retry time has elapsed;
   - stale visible tiles;
   - missing prefetch tiles;
   - incomplete/stale prefetch tiles last.
5. Within the same priority class, choose the candidate nearest the viewport center.
6. Sample at most **one whole 128x128 tile per rendered frame**.
7. Update or create that tile's texture, then return to normal rendering.
8. Trim the access-order cache to 96 tiles, releasing evicted textures.

There is no nanosecond budget and no inner loop that keeps sampling until a deadline. The hard scheduling budget is one tile attempt per rendered frame.

## Cold-open behavior

On the first Blueprint open in a level with an empty terrain cache, visible tiles are populated one whole tile at a time, nearest to the viewport center first.

This may create a small client-frame hitch when a tile is sampled. That is accepted by this design because:

- the previous whole-tile behavior was not observed as materially disruptive;
- only one tile is sampled in a frame;
- repeat opens are served from the warm cache;
- nearby panning is usually covered by the prefetch ring;
- the implementation becomes substantially simpler and easier to verify.

Do not restore the oldest behavior of synchronously sampling every missing visible tile in one render call.

## Warm-cache behavior

The session cache is the primary UX optimization.

- `BlueprintScreen.removed()` may close its renderer instance, but closing an individual screen must not release the shared terrain cache.
- Opening another Blueprint screen in the same `ClientLevel` must find and draw already-ready tile textures immediately.
- `MCAClient` or the existing client lifecycle hook continues to notify terrain cache ownership of the current level identity.
- A different `ClientLevel` object or `null` invalidates the entire terrain cache and releases all registered textures.
- No background sampling occurs while Blueprint is closed.
- No disk cache is introduced.

## Incomplete tile behavior

Whole-tile sampling must remain safe when only part of a 128x128 tile is present in the client's chunk cache.

For each sample attempt:

- read only chunks already present in the client cache;
- sample all available columns in the tile;
- if a required chunk is unavailable, do not force-load it;
- if the tile already has valid cached values for unavailable columns, preserve those prior values;
- if no prior value exists, leave those columns transparent/unavailable rather than inventing terrain;
- mark the tile incomplete and set a tile-level retry time using the existing retry interval;
- continue drawing the best cached texture while the retry is pending.

An incomplete retry resamples the whole tile when eligible. It does not maintain sixty-four independent retry clocks.

## Staleness and world updates

Ready terrain should remain stale-while-revalidate.

- A completed tile receives one tile-level stale timestamp/age.
- When stale, the existing texture remains drawable immediately.
- The tile enters the same one-tile-per-frame candidate queue as other work, below missing visible terrain but above low-priority prefetch.
- A refresh samples the whole tile and updates the existing texture.
- Missing chunks during refresh preserve old valid pixels as described above.

The exact stale interval should initially retain the existing effective value unless runtime testing gives a concrete reason to change it.

## Texture lifecycle

Simplify texture updates together with sampling state.

- Keep one `DynamicTexture`/`NativeImage` per cached tile.
- When a tile has a new whole-tile sample, rewrite the required pixels in its existing `NativeImage` and perform one full-tile upload.
- Do not maintain `dirtySlices` or dirty-region bounds.
- Do not allocate/register a replacement texture for every refresh.
- LRU eviction and level invalidation remain the only normal reasons to release a tile texture.

A 128x128 full upload is small enough that, with the one-tile-per-frame sampling limit, the simpler lifecycle is preferred over partial-upload bookkeeping.

## Code to remove

The implementation should delete slice-only machinery rather than leaving compatibility wrappers around it. Expected removals include the equivalents of:

- `SAMPLE_SLICE_BLOCK_SIZE`;
- `SAMPLE_SLICES_PER_AXIS`;
- `SAMPLE_SLICE_COUNT`;
- `TERRAIN_SAMPLE_BUDGET_NANOS`;
- `TileSamplingBudget`;
- `sampleSliceBounds(...)`;
- `sampleDependencyBounds(...)`;
- `dirtyTextureBounds(...)`;
- `sliceNeedsSampling(...)`;
- `recordSliceSampleResult(...)`;
- `nearestReadySlice(...)`;
- per-slice `sampledAtGameTimes`;
- per-slice `retryAfterGameTimes`;
- per-slice `dirtySlices`;
- `sampledSliceCount` and progressive texture checkpoint rules.

Tests whose sole purpose is to enforce those deleted implementation seams should be removed or replaced with behavior-level whole-tile tests. Do not keep obsolete helpers only to satisfy old reflection-based tests.

## What remains from the current renderer

Retain the behavior that still provides clear value:

- world-anchored tile coordinates;
- 128x128 tile cache identity independent of viewport state;
- 96-entry bounded LRU;
- visible-before-prefetch priority;
- one-ring prefetch;
- nearest-visible-first selection;
- no-force-load chunk lookup;
- cached terrain reuse across Blueprint screen instances;
- level-identity invalidation;
- stale refresh;
- primitive sample storage;
- persistent texture reuse;
- current terrain appearance algorithms.

## Relationship to earlier specs

This document supersedes the terrain-streaming mechanics in:

- `docs/superpowers/specs/2026-09-06-blueprint-lazy-terrain-streaming-design.md`;
- the slice-granular terrain constraints in `docs/superpowers/specs/2026-09-07-blueprint-domain-review-remediation-design.md`.

It does **not** supersede the non-terrain Blueprint fixes or server/domain fixes in the September 7 remediation design. Those remain independent.

## Out of scope

- Sampling all visible missing tiles synchronously in one frame.
- Background workers or asynchronous world reads.
- JourneyMap-style region databases.
- Persistent disk caching.
- Multi-resolution/LOD terrain textures.
- Sampling while Blueprint is closed.
- Increasing the client chunk/view distance.
- Changing room, floor, tooltip, viewport, or building geometry behavior.
- Changing water blend, terrain colors, contours, hillshade, or dry-height semantics as part of this scheduling simplification.

## Test strategy

Implementation follows RED -> GREEN behavior tests.

### Whole-tile scheduling

- A cold render selects at most one terrain tile for sampling.
- A missing visible tile outranks every prefetch tile.
- Between visible candidates, the tile nearest the viewport center is selected first.
- A ready visible cached tile draws without requiring a new sample.
- One-ring prefetch remains classified separately from visible work.

### Warm cache

- Closing one Blueprint renderer keeps ready terrain for the next screen instance.
- Reopening within the same level sees the same cached tile.
- Changing the `ClientLevel` identity releases and clears the cache.
- Disconnecting (`level == null`) clears the cache.

### Retry and staleness

- An incomplete tile cannot retry before its tile-level retry deadline.
- An incomplete tile retries as one whole tile after the deadline.
- Missing columns retain previous valid cached values where available.
- A completed stale tile remains drawable while queued for refresh.
- A stale visible tile outranks stale prefetch work but does not outrank missing visible terrain.

### Texture lifecycle

- Repeated refreshes reuse the same tile texture object/resource registration.
- A completed whole-tile update performs one texture refresh path rather than slice checkpoints.
- LRU eviction releases the evicted texture.
- Cache invalidation releases every retained texture.

### Regression coverage

- Preserve current water/substrate rendering tests.
- Preserve dry terrain, leaf, hillshade, and contour tests that assert user-visible behavior rather than slice implementation details.
- Preserve tile anchoring/cache-identity tests.
- Delete or rewrite tests for nearest-ready-slice, slice bounds, dirty-slice bounds, dependency halos, and sampling-budget internals.

## Verification

Automated gates:

1. focused `BlueprintScreenMapInteractionTest` RED/GREEN runs during implementation;
2. full `:common:test`;
3. `:fabric:compileJava`;
4. `:neoforge:compileJava`;
5. `git diff --check`;
6. final diff audit proving the slice-only machinery was actually removed rather than bypassed.

Runtime validation is required separately from automated tests:

1. cold-open Blueprint in a terrain-heavy area and confirm any one-tile frame hitch is acceptable;
2. close and immediately reopen Blueprint and confirm terrain is already visible from the warm cache;
3. pan across the prefetch boundary and confirm new terrain does not cause a long freeze;
4. leave Blueprint open long enough for staleness and confirm refresh does not blank the existing tile;
5. change dimension or disconnect/reconnect and confirm terrain from the old level is not reused.

Do not claim the visual UX is improved from Gradle alone.

## Success criteria

The simplification is successful when:

- repeated Blueprint opens in the same level render already-cached terrain immediately;
- a cold cache never samples more than one whole tile per render frame;
- terrain reads never force-load chunks;
- incomplete and stale tiles retain usable prior pixels while waiting for refresh;
- the 96-tile cache and level invalidation remain bounded and correct;
- the 64-slice lifecycle, per-slice arrays, nanosecond budget, dependency-halo bookkeeping, and partial texture-upload state are gone;
- current approved terrain visuals remain unchanged;
- focused and full automated gates pass;
- in-game cold-open/reopen behavior is visually validated.
