# Blueprint Lazy Terrain Streaming Design

> **Superseded 2026-09-07:** Retained for history. The approved implementation direction is the smaller no-thread design in `docs/superpowers/specs/2026-09-07-blueprint-domain-review-remediation-design.md`.

Date: 2026-09-06

Status: approved for implementation

Target: `feature/1.21.1-floor-clean-squash`

Baseline: `ea5e3c751 fix: restore client seabed rendering`

## Problem

`BlueprintTerrainRenderer` currently samples world-derived terrain while the Blueprint screen is rendering. Commit `0e187ea4d` reduced the worst first-open stall by allowing at most one missing tile sample per frame, but a tile sample is still synchronous work on the client/render path. Panning into uncached terrain can therefore produce repeated frame hitches, and zoomed-out views can discover many missing tiles at once.

The underwater performance optimization also exposed a Minecraft 1.21.1 client-data constraint: `Heightmap.Types.OCEAN_FLOOR` is a live-world heightmap and is not transmitted to the client. It therefore cannot be used as a reliable client-side shortcut for the seabed. The correct client-safe fallback is a downward search over already-loaded chunk data, accelerated by skipping whole 16-block sections whose palette cannot contain a motion-blocking floor.

The desired result is a small, Blueprint-specific version of JourneyMap's lazy map loading rather than a second full mapping system.

## Goals

1. Opening or panning the Blueprint map must not synchronously sample an entire terrain tile in `render()`.
2. Visible missing terrain has higher priority than prefetch terrain.
3. Keep one tile of prefetch padding around the visible viewport so ordinary panning usually reveals an already-prepared tile.
4. Perform expensive pure CPU terrain rasterization off the render/client thread.
5. Keep all live Minecraft world/chunk reads bounded and on the client thread unless a future optimization first creates an immutable copy.
6. Never request, generate, or force-load a Minecraft chunk solely for Blueprint terrain.
7. Preserve actual block colors below water, including sand/stone/gravel differences.
8. Preserve existing dry-terrain visuals unless a separately tested source constraint requires a replacement path.
9. Bound CPU memory, GPU texture memory, queue size, and stale work.
10. Make performance measurable so later optimizations are evidence-driven rather than inferred from compilation success.

## Non-goals

- No persistent disk map cache.
- No JourneyMap-style region database.
- No multi-level texture LOD chain in this change.
- No Minecraft chunk generation/loading outside the client's normal view distance.
- No pool of multiple terrain workers.
- No background reads from a live `ClientLevel` or mutable `LevelChunk`.
- No unrelated Blueprint room/floor/structure rendering changes.

## Source-grounded constraints

### Minecraft 1.21.1 local sources

The local patched 1.21.1 sources under `C:\Users\Mik\Downloads\MCA\local-source` establish the following:

1. `Heightmap.Types.WORLD_SURFACE` and `MOTION_BLOCKING` use `Heightmap.Usage.CLIENT`.
2. `OCEAN_FLOOR` and `MOTION_BLOCKING_NO_LEAVES` use `Heightmap.Usage.LIVE_WORLD`.
3. `ClientboundLevelChunkPacketData` serializes only heightmaps for which `sendToClient()` is true.
4. `LevelChunk` creates heightmap objects for the final heightmap set, so the mere presence of an `OCEAN_FLOOR` object on the client does not prove that it contains server data.
5. `LevelChunkSection.maybeHas(Predicate<BlockState>)` delegates to the paletted container's palette-level predicate. This can reject a whole 16x16x16 section without scanning each block in that section.
6. `PalettedContainer.copy()` creates copied palette/storage data and is available as a future escalation path if profiling justifies copying section state for worker-side scanning.
7. `ClientChunkCache.getChunk(x, z, ChunkStatus, false)` returns `null` when the requested client chunk is not available; this is the preferred no-force-load lookup.
8. `DynamicTexture(NativeImage)` queues its GPU preparation/upload through `RenderSystem.recordRenderCall` when constructed off the render thread.
9. `TextureManager` itself owns mutable registration state. Its asynchronous preload path marshals registration through `Minecraft.execute(...)` and `RenderSystem.recordRenderCall(...)`. Blueprint should therefore keep texture registration/release on the client/render thread.

Relevant local files:

- `net/minecraft/world/level/levelgen/Heightmap.java`
- `net/minecraft/network/protocol/game/ClientboundLevelChunkPacketData.java`
- `net/minecraft/client/multiplayer/ClientChunkCache.java`
- `net/minecraft/world/level/chunk/LevelChunkSection.java`
- `net/minecraft/world/level/chunk/PalettedContainer.java`
- `net/minecraft/client/renderer/texture/DynamicTexture.java`
- `net/minecraft/client/renderer/texture/TextureManager.java`
- `com/mojang/blaze3d/systems/RenderSystem.java`

### Public references checked

Public Yarn 1.21.1 documentation confirms the same useful APIs:

- `ChunkSection.hasAny(Predicate<BlockState>)` / the equivalent Mojang-mapped `LevelChunkSection.maybeHas(...)` performs a palette predicate rather than a full block scan.
- `PalettedContainer.copy()` exists and copies paletted storage.
- `ClientChunkManager.getChunk(..., ChunkStatus, boolean)` exposes a nullable no-create/no-force-load path.
- `NativeImageBackedTexture` / dynamic texture APIs separate CPU image data from upload.
- `RenderSystem` exposes render-thread checks and `recordRenderCall(...)`.

JourneyMap source at `C:\Users\Mik\Downloads\journey-src` provides the architectural precedent rather than code to copy directly:

- `MapRenderer` maintains a tile grid around the current center.
- grid changes use a `SingleFlightGate` so rapid viewport changes coalesce rather than launch overlapping rebuilds.
- map-grid work is scheduled with `CompletableFuture` on a background executor.
- `TaskController` deliberately serializes map task execution through a one-thread executor / one active queued future.

## Approaches considered

### A. Read live `ClientLevel` and chunks directly from a worker

This would move the most work off the client thread and is superficially closest to "fully async".

Rejected for the initial design. Client chunks, palettes, biome state, and texture manager state are mutable. The local sources do not provide a general contract that arbitrary world reads are safe while network/chunk updates mutate that state. Avoiding a frame hitch is not worth introducing intermittent data races or corrupted visual snapshots.

### B. Copy entire relevant chunk-section palettes, then scan them on a worker

`PalettedContainer.copy()` makes this technically feasible. The client thread could copy section block-state containers, and the worker could perform vertical floor searches against the immutable copies.

This is an optional second-stage optimization, not the initial implementation. Copying many 4096-entry section containers creates additional allocation/copy pressure and memory spikes. Biome tint data would also need a separate immutable snapshot. Use this only if profiling shows the bounded client-thread snapshot stage remains materially expensive after palette section skipping.

### C. Recommended: bounded client snapshot -> single CPU worker -> render-thread upload

This keeps the unsafe/live part small and makes the expensive deterministic image work asynchronous.

The client thread incrementally resolves already-loaded terrain columns into compact primitive arrays under a strict per-frame time budget. The worker sees only immutable primitive data, computes hillshade/contours/water composition and the final 128x128 pixel buffer, then returns that buffer. The client/render thread performs only texture creation, registration, drawing and release.

This is the best initial balance of performance, memory, correctness and implementation complexity.

## Architecture

Split terrain streaming into four package-private responsibilities.

### `BlueprintTerrainRenderer`

Responsibilities:

- calculate the visible tile rectangle from `BlueprintMapViewport`;
- ask the scheduler for visible + padding tiles;
- upload completed CPU pixel buffers on the client/render thread;
- draw ready textures;
- release evicted textures;
- own the ready GPU texture cache lifecycle.

It must not perform vertical terrain scans or rasterize a whole tile inside `render()`.

### `BlueprintTerrainScheduler`

Responsibilities:

- maintain desired tile requests;
- classify requests as `VISIBLE` or `PADDING`;
- deduplicate one request per `TileKey`;
- order visible requests before padding, then nearest tile-center first;
- maintain a viewport request epoch so superseded queued work can be dropped;
- allow exactly one CPU raster worker;
- maintain bounded pending and completed queues;
- prevent duplicate concurrent builds for the same tile.

The scheduler is conceptually similar to JourneyMap's `SingleFlightGate`, but simpler: completed cached tile content is independent of viewport epoch, while pending work may become stale when the viewport changes.

### `BlueprintTerrainSampler`

Runs only on the client thread.

Responsibilities:

- use `ClientChunkCache.getChunk(chunkX, chunkZ, FULL, false)` or an equivalent no-force-load lookup;
- incrementally snapshot one tile in 16x16 chunk slices;
- stop when the client-thread sampling time budget is exhausted;
- mark unloaded columns as absent rather than loading their chunk;
- preserve cached values for an incomplete refresh where possible;
- produce a compact immutable `TerrainSnapshot` containing only the data needed for pure CPU rendering.

The sampler must not rely on client-unsent heightmaps.

For a water column:

1. use client-valid `WORLD_SURFACE` only to find the upper bound and water surface;
2. walk downward by section;
3. call `LevelChunkSection.maybeHas(state -> state.blocksMotion())` before scanning any block in that section;
4. skip the entire section when the palette contains no motion-blocking state;
5. scan individual blocks only inside candidate sections until the actual solid floor is found;
6. if the selected block has `MapColor.NONE`, continue downward until a drawable block color is found;
7. snapshot the actual terrain color and the biome water tint separately so the current seabed-visible water blend is preserved.

For dry columns, retain existing visual semantics, but do not treat `MOTION_BLOCKING_NO_LEAVES` as trustworthy client-transmitted data. If that height is still required for contours, derive the equivalent result with a client-safe section-aware search and protect it with a dedicated regression test.

### `BlueprintTerrainRasterizer`

Runs on the single background worker and must be a pure function of the immutable snapshot.

Responsibilities:

- derive neighbor heights for hillshade;
- apply terrain tint, water blend, hillshade and contours in the validated order;
- produce exactly one 128x128 ARGB pixel buffer;
- perform no Minecraft world access;
- perform no texture-manager or render-system calls.

Prefer an `int[]` pixel buffer over worker-owned `DynamicTexture` or long-lived `NativeImage` ownership. This keeps resource lifetime simple and makes stale worker results cheap to discard.

## Data model and memory

Do not retain one Java `Cell` object per block after a tile is built.

A snapshot should use primitive arrays, for example:

- `short[] height` for 128x128 terrain heights;
- `int[] terrainColor`;
- `int[] waterTint`, with `-1` for no water;
- a compact validity mask if incomplete columns need to be represented explicitly.

At 16,384 columns, the three primary arrays are about 160 KiB of raw data per in-flight tile. Keep only a very small number of snapshots in flight and discard them immediately after rasterization.

The worker output is a 128x128 ARGB buffer, about 64 KiB.

A 128x128 RGBA dynamic texture is about 64 KiB of CPU image memory plus approximately 64 KiB of GPU pixel storage before driver/texture overhead. With the existing hard cap of 96 ready textures, the raw CPU+GPU pixel footprint is roughly 12 MiB. That is acceptable as an initial ceiling and is far preferable to retaining 96 full `Cell[][]` graphs as well.

Initial cache policy:

- keep `MAX_CACHED_TILES = 96` unless profiling demonstrates a reason to change it;
- discard snapshot/column arrays once a GPU-ready tile is installed;
- never exceed the hard cap: admit visible desired tiles nearest-first, then use remaining capacity for padding;
- LRU-evict unneeded ready tiles first; if visible desired candidates alone exceed the cap, the farthest visible candidates are not newly scheduled/retained until capacity is available;
- never let the request queue itself retain dozens of 160 KiB snapshots.

## Viewport and padding policy

Desired terrain consists of:

1. all tiles intersecting the current visible world rectangle;
2. one additional tile ring around that rectangle.

However, a tile should be scheduled only if it intersects at least one currently loaded client chunk or already has cached data worth retaining. This matters for heavily zoomed-out views: the screen may mathematically cover hundreds of tiles, but Blueprint must not queue terrain for chunks the client does not possess.

The 96-tile cache cap remains authoritative even in extreme views. Visible loaded candidates are admitted nearest-first. Padding is opportunistic and must never displace a visible admitted tile. This prevents the "one padding ring" rule from becoming an unbounded memory promise.

Priority order:

1. missing visible tile with loaded chunk data;
2. incomplete visible tile refresh;
3. missing padding tile;
4. incomplete padding refresh.

Within the same class, prefer the tile whose center is closest to the viewport center.

Panning behavior:

- a tile moving from padding to visible should normally already be ready;
- newly exposed outer padding is queued at low priority;
- queued work that is no longer visible/padding is removed before snapshotting;
- work already running may finish, but its result is installed only if useful or admitted to the normal LRU cache;
- viewport changes do not invalidate a completed terrain texture because tile pixels are world-coordinate based, not viewport based.

## Client-thread budget

Replace the current "one whole tile sample per frame" budget with a time-bounded incremental snapshot budget.

Initial target:

- approximately 0.75 ms of terrain snapshot work per rendered frame;
- never start an unbounded tile scan because budget remains;
- snapshot in chunk-sized 16x16 slices so work can stop at a stable boundary;
- if timing overhead or low-end hardware makes a time-only budget noisy, also enforce a maximum of one chunk slice per frame as a conservative secondary cap.

The exact millisecond value is a tuning constant, not a correctness requirement. Profiling should decide whether 0.5, 0.75 or 1.0 ms is the best default.

## Worker and queue policy

Use one dedicated terrain worker rather than the common fork-join pool.

Recommended shape:

- single-thread executor with a named daemon thread;
- at most one raster job running;
- bounded queue of at most two fully snapshotted tiles waiting for rasterization;
- a separate lightweight request set may contain more `TileKey`s because keys are cheap, but snapshots are produced only when worker capacity exists;
- closing the Blueprint terrain renderer cancels pending work and shuts down the executor;
- stale/cancelled jobs must release/discard their pixel arrays without touching the texture manager.

One worker is intentional. Rasterizing more tiles in parallel competes with Minecraft's own rendering, networking, chunk meshing and integrated-server work and provides little benefit when uploads and visible demand are already serialized.

## Texture handoff

Worker completion enqueues a CPU pixel result.

On the client/render thread:

1. verify the tile is still admissible and not superseded;
2. create/fill `NativeImage` from the 128x128 pixel buffer;
3. create/register `DynamicTexture`;
4. set nearest filtering as today;
5. atomically replace the old ready texture for the tile;
6. release the replaced texture through `TextureManager`;
7. discard CPU snapshot and worker pixel arrays.

Do not mutate `TextureManager.byPath` or release textures from the background worker.

## Invalidation and incomplete chunks

Do not force-load missing chunks.

If a tile is only partially backed by loaded client chunks:

- retain any prior valid cached columns where available;
- represent unavailable columns as missing/transparent;
- retry incomplete tiles after the current existing retry interval or when they become desired again;
- never block waiting for a chunk packet.

Completed terrain tiles may retain the existing refresh semantics for this change. Real-time terrain-edit invalidation is a separate feature unless implementation uncovers an existing invalidation hook that can be adopted without expanding scope.

## Failure handling

- Worker exceptions fail only that tile request, log once with its `TileKey`, and leave any older ready texture intact.
- A closed screen drops all pending completions using a lifecycle/generation token.
- Texture upload failure must not remove the previous working texture until replacement succeeds.
- Invalid/unloaded chunks are normal absence, not errors.
- Queue saturation drops/reprioritizes padding before visible work.

## Testing

### Unit tests

Add behavior tests for:

- visible requests outrank padding;
- nearest visible tile wins within the same priority;
- duplicate requests coalesce;
- a stale viewport request is removed before snapshot allocation;
- padding promoted to visible keeps the same tile/cache identity;
- unloaded chunks are skipped without requesting creation;
- water-only sections are skipped without per-block reads;
- candidate sections find the actual solid seabed;
- `MapColor.NONE` fallback still finds a drawable block below;
- sand and stone below identical water produce different final pixels;
- rasterizer performs the established seabed/water/hillshade ordering;
- a completed tile does not retain `Cell[][]`-style per-block objects;
- cache admission never exceeds the hard cap, visible candidates outrank padding, and eviction releases the eldest unneeded texture first.

Do not add source-text grep tests for forbidden heightmap names. Test the client-snapshot behavior and injected height/section data instead.

### Concurrency tests

Use deterministic fake executors where possible to prove:

- only one raster job runs at a time;
- stale completion cannot overwrite a newer tile result;
- closing cancels or ignores pending completion;
- worker code receives immutable snapshot data and has no world dependency.

### Integration / runtime verification

Compilation and unit tests are not sufficient for the performance claim.

Profile at least:

1. first Blueprint open with no terrain cache;
2. slow pan across a tile boundary;
3. fast pan across several tiles;
4. shallow water and deep water;
5. zoomed-out/Fit view;
6. normal view distance and a high client view distance.

Record:

- maximum and p95 Blueprint terrain snapshot time per frame;
- raster worker job duration;
- texture upload duration;
- number of requested, snapshotted, rasterized, installed and stale-dropped tiles;
- ready tile count;
- approximate snapshot/pixel memory in flight.

## Acceptance criteria

The implementation is complete only when all of the following are true:

1. `BlueprintTerrainRenderer.render()` does not call the whole-tile world sampler synchronously.
2. Live world reads occur only in the bounded client-thread snapshot stage.
3. No Blueprint path calls a chunk lookup with creation/force-load enabled.
4. Missing visible tiles are scheduled before padding.
5. One padding ring is maintained without queuing unloaded distant terrain in zoomed-out views.
6. At most one terrain raster worker runs.
7. Texture registration/release remains on the client/render thread.
8. Actual underwater block differences remain visible.
9. The implementation does not rely on client-unsent `OCEAN_FLOOR` data.
10. If the existing dry path uses `MOTION_BLOCKING_NO_LEAVES`, it is replaced by a client-safe equivalent with a regression test rather than silently trusted.
11. Ready tiles do not retain the old per-column `Cell[][]` object graph after upload.
12. `:common:test` and the relevant loader compile tasks pass.
13. Runtime profiling shows the client-thread terrain snapshot stage stays within the chosen frame budget in the tested scenarios; if it does not, the section-copy escalation below is evaluated before increasing the budget.

## Escalation path if snapshotting is still too expensive

Only after profiling demonstrates that section-aware client snapshotting is still the dominant hitch:

1. copy `PalettedContainer<BlockState>` only for candidate sections of one loaded chunk at a time;
2. capture the minimum biome/tint metadata needed for those columns;
3. pass the immutable copied section state to the same single worker;
4. perform vertical floor scanning on the worker;
5. keep the copied-section queue strictly bounded and discard copies immediately after processing.

This optimization should be measured against the simpler design because palette/storage copying may merely move cost from block reads to allocation and memory bandwidth.

## Public web references

- Fabric Yarn 1.21.1 `ChunkSection` API: `https://maven.fabricmc.net/docs/yarn-1.21.1%2Bbuild.1/net/minecraft/world/chunk/ChunkSection.html`
- Fabric Yarn 1.21.1 `PalettedContainer` API: `https://maven.fabricmc.net/docs/yarn-1.21.1%2Bbuild.3/net/minecraft/world/chunk/PalettedContainer.html`
- Fabric Yarn 1.21.1 `ClientChunkManager` API: `https://maven.fabricmc.net/docs/yarn-1.21.1%2Bbuild.1/net/minecraft/client/world/ClientChunkManager.html`
- Fabric Yarn 1.21.1 `NativeImageBackedTexture` API: `https://maven.fabricmc.net/docs/yarn-1.21.1%2Bbuild.1/net/minecraft/client/texture/NativeImageBackedTexture.html`
- Fabric Yarn `RenderSystem` API: `https://maven.fabricmc.net/docs/yarn-1.21%2Bbuild.1/com/mojang/blaze3d/systems/RenderSystem.html`
- NeoForged 1.21-1.21.1 texture documentation: `https://docs.neoforged.net/docs/1.21.1/resources/client/textures/`

## Implementation boundary

This document defines the design only. The next step after review is a separate implementation plan. Do not combine the async scheduler/cache work with unrelated `ExtendedWalkTowardsTask` changes or the older untracked Blueprint documents already present in the worktree.
