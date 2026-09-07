# Blueprint JourneyMap-Style Underwater Rendering Design

## Goal

Make Blueprint terrain water behave like JourneyMap's bathymetric/transparency rendering in the way that matters visually: the real block below the water must contribute to the final map pixel.

For equal biome water color and comparable terrain shading, sand, gravel, stone, clay, grass, and other seabed blocks must produce visibly different final water pixels.

## Reference behavior in `C:\Users\Mik\Downloads\journey-src`

The JourneyMap source uses a vertical transparency-compositing model rather than drawing a detached translucent GUI overlay:

- `journeymap/client/mod/vanilla/VanillaBlockHandler.java` first assigns `MapColor.WATER` alpha `0.25F`, then its later generic `LiquidBlock` branch sets liquid alpha to `0.7F`. Vanilla water therefore ends initialization at `0.7F` in this source tree.
- `journeymap/client/model/block/BlockMD.java` treats any block with alpha below `1.0F` as transparent.
- `journeymap/client/cartography/render/SurfaceRenderer.java` keeps cached terrain `heights` and fluid `waterHeights` separately. With bathymetry enabled it records the top fluid height, continues downward to the terrain height, and later builds the visible strata from both values.
- `SurfaceRenderer` also has a bounded generic transparency depth (`maxDepth = 8.0F`) when building transparent strata.
- `journeymap/client/cartography/Strata.java` retains the fluid color and passes whether water exists above each stratum into the renderer.
- `Strata.nextUp(..., true)` explicitly skips middle fluid strata when another fluid stratum remains above, so JourneyMap does not repeatedly alpha-composite every water block in the column.
- `journeymap/client/cartography/render/BaseRenderer.java` blends the submerged block color with the water color before the stack is painted. Its default `tweakWaterColorBlend` is `0.5F`.
- `SurfaceRenderer.paintStrata(...)` then composites the remaining top water stratum using that block's initialized alpha (`0.7F` for vanilla water in this source tree).
- `BaseRenderer.paintBlock(...)` writes the resulting color as an opaque image pixel.

Ignoring JourneyMap's separate lighting adjustments, those current vanilla defaults would algebraically collapse to:

```text
submerged = ground * 0.50 + water * 0.50
final     = submerged * 0.30 + water * 0.70
          = ground * 0.15 + water * 0.85
```

The important architectural property is therefore: **water and the block beneath it are resolved into one final opaque terrain pixel before the texture is displayed**. Water depth affects JourneyMap's bounded stratum/light traversal, but it is not implemented as MCA's current "increase GUI overlay alpha with depth" model.

This MCA change adopts that property. It does not port JourneyMap's generic arbitrary-transparent-block stack, night-light model, palette system, or configurable bathymetry subsystem.

## Root cause in Minecraft 1.21.1 client chunks

The failed intermediate implementation removed the detached GUI overlay, but still sampled
`Heightmap.Types.OCEAN_FLOOR` and `MOTION_BLOCKING_NO_LEAVES` from `ClientLevel`.
That is not reliable on the client in 1.21.1:

- `WORLD_SURFACE` and `MOTION_BLOCKING` have `Heightmap.Usage.CLIENT`.
- `OCEAN_FLOOR` and `MOTION_BLOCKING_NO_LEAVES` have `Heightmap.Usage.LIVE_WORLD`.
- `ClientboundLevelChunkPacketData` serializes only heightmaps for which `sendToClient()` is true.
- `LevelChunk` nevertheless pre-creates every final heightmap, including the two `LIVE_WORLD` maps.
- `ChunkAccess.getHeight(...)` primes a heightmap only when its object is absent. The pre-created
  `LIVE_WORLD` maps therefore look present even though the initial chunk packet never populated
  their data.

On a freshly received client chunk those maps consequently read at the minimum build height.
Later client-side block updates can update individual columns, so relying on them can also produce
incoherent per-column sampling rather than one consistently wrong value.

The Blueprint renderer must therefore use only the client-sent `WORLD_SURFACE` as its vertical
starting point and derive its own terrain/water layers from the actual block states already present
in the client chunk.

## Design decision

Use `WORLD_SURFACE` only to find the top of the loaded column. From there, scan the actual client
block states downward and derive two values in one pass:

- `terrainY`: the first block whose state `blocksMotion()`, matching Minecraft's `OCEAN_FLOOR`
  predicate without depending on its unsent heightmap data;
- `waterY`: the highest water state encountered above that terrain block, if any.

Keep those two layers separate in the sampled terrain cell: the terrain layer owns the resolved
terrain color/height and the water layer owns the biome water tint when water is present.
`terrainY` owns hillshade/contour height, so underwater relief comes from the seabed rather than
from the flat water surface. During texture creation, style the terrain layer first and then
CPU-composite the water tint over that styled terrain into the final opaque pixel.

Do not add a JourneyMap-style `Strata` object graph. For Blueprint's current requirement, all water layers in a sampled column share one biome water tint, so the renderer only needs:

- seabed color,
- water tint,
- one deterministic blend function.

This preserves the useful JourneyMap behavior while keeping the Blueprint renderer small.

## Water color model

Use one fixed MCA blend that keeps substantially more seabed contribution than JourneyMap's current vanilla-water alpha would:

```text
final = ground * 0.375 + water * 0.625
```

The result is always opaque ARGB (`0xffRRGGBB`).

Implement this as a single `WATER_BLEND = 0.625F` constant and one opaque blend helper. Do not preserve MCA's current `WATER_BASE_OPACITY`, `WATER_DEPTH_OPACITY_PER_BLOCK`, or `WATER_MAX_OPACITY` curve; those constants belong to the detached-overlay implementation being removed.

`0.625F` is an MCA readability choice, not an exact reproduction of this JourneyMap source tree's current vanilla-water alpha. It preserves JourneyMap's important composition model—real substrate plus one water contribution, with middle water layers skipped—while leaving `37.5%` of the final unshaded color to the seabed so block boundaries remain visible. It does not copy JourneyMap's generic light attenuation, palette configuration, or `maxDepth = 8.0F` traversal limit. The client-safe block-state scan can reach the actual floor directly, so adding an artificial eight-block substrate cutoff would make the implementation more complex while hiding the block the user explicitly wants to see.

## Rendering data flow

For a non-water column:

```text
WORLD_SURFACE block
  -> MapColor
  -> biome tint when applicable
  -> Cell.terrainColor
  -> hillshade
  -> contour overlay
  -> terrain texture pixel
```

For a water column:

```text
WORLD_SURFACE water
  -> scan actual block states downward
  -> remember top water Y
  -> first blocksMotion() terrain block
  -> first meaningful seabed MapColor
  -> seabed biome tint
  -> biome water tint
  -> Cell(terrainHeight, terrainColor, waterTint)
  -> hillshade terrainColor using terrainHeight
  -> contour terrainColor using terrainHeight
  -> waterColor(styledTerrainColor, waterTint)
  -> terrain texture pixel
```

The water tint is deliberately composited after MCA's hard contour overlay. Applying contours after
the water blend exposed every four-block seabed contour as a dark stripe across otherwise smooth
water. Keeping terrain styling and water composition as two ordered layers retains seabed relief
without placing raw contour lines on top of the water.

## Tile/cache model

Keep one logical terrain layer and one optional logical water layer in each sampled cell:

```java
Cell(int height, int terrainColor, int waterTint)
```

`waterTint == NO_WATER_TINT` means the cell has no water. These are CPU-side data layers only;
there is still exactly one final texture and one blit.

Each `TerrainTile` owns exactly one texture resource:

```text
terrainTextureLocation
```

Remove:

- `waterTextureLocation`,
- the second `NativeImage`,
- `mca_blueprint_water`,
- explicit `RenderSystem` blending,
- water texture release logic.

The existing one-block sample step, nearest texture filtering, tile coordinates, incomplete-tile retry behavior, LRU cache, hillshade, and land contour behavior remain unchanged. Under water, the same terrain contour is now attenuated by the later water composition instead of being drawn as an unfiltered dark stripe on top.

## Sampling behavior

For every loaded column:

1. Read `WORLD_SURFACE` and start at `surfaceHeight - 1`.
2. Walk downward through the actual `BlockState`s until the world's minimum build height or the
   first `blocksMotion()` state.
3. While walking, remember only the highest water Y. This is the water layer.
4. The first `blocksMotion()` state is the terrain layer. Do not read `OCEAN_FLOOR` or
   `MOTION_BLOCKING_NO_LEAVES` from the client.
5. If water was encountered, use the terrain block's real `MapColor`, continuing downward through
   `MapColor.NONE` only when necessary, and apply the same grass/foliage biome tint used on land.
6. Resolve biome water tint at the remembered water position.
7. Store the resolved terrain color and water tint separately in `Cell`.
8. Store the terrain Y as the cell height so underwater hillshade and contours describe the floor.
9. During texture creation, apply hillshade/contours to `terrainColor`, then call
   `BlueprintTerrainRenderer.waterColor(styledTerrainColor, waterTint)` for water cells.

The scan is intentionally bounded by the world's minimum build height rather than JourneyMap's
generic eight-block transparency limit. Deep water still needs to reveal its substrate.

## Test contract

The previous test contract—"the water overlay remains translucent"—is no longer correct because there is no water overlay after this change.

Replace it with final-pixel behavioral assertions:

1. **Underlying block identity matters**
   - Same water tint and same depth over sand vs stone produces different final pixels.

2. **Final water pixels are opaque**
   - `pixel & 0xff000000 == 0xff000000`.

3. **Fixed seabed-visible composition is preserved**
   - The pure helper uses MCA's fixed `0.625F` water contribution so the seabed retains a deliberate `37.5%` color contribution.

4. **Water is the final logical layer**
   - A styled terrain contour is blended under the water tint rather than being drawn on top of it.
   - The regression uses a literal expected pixel so reversing the layer order recreates the stripe failure.

4. **Client-safe layer sampling is preserved**
   - A water column over sand resolves sand as the terrain layer before composition.
   - A deep water column over stone still resolves stone rather than stopping at an arbitrary
     transparency depth.
   - The sampler returns the top water Y separately from terrain Y.

5. **The tile pipeline is single-texture**
   - `TerrainTile` has one texture resource and `Cell` carries terrain color plus optional water tint as CPU-side layers; there is no detached water texture.

Do not add a unit test that expects color opacity to increase with water depth; that behavior belongs to the implementation being removed and is not how JourneyMap's middle-fluid handling works.

## Runtime acceptance

In a 1.21.1 client world, inspect Blueprint map water over visibly different substrates:

- sand beside gravel,
- gravel beside stone,
- shallow shore transitioning into deeper water.

Pass conditions:

- the underwater substrate boundaries remain visible through the water tint,
- there is no artificial depth-driven alpha ramp; water color is determined by substrate, biome water tint, and the existing terrain shading pipeline,
- water pixels have no separate overlay offset or tile seam,
- panning and zooming do not detach water from terrain,
- contour and relief shading remain aligned with the seabed terrain,
- non-water terrain remains visually unchanged apart from unavoidable neighboring hillshade effects.

## Out of scope

- Porting JourneyMap's full `Strata` abstraction.
- General glass/ice/leaves transparency stacking.
- JourneyMap night/cave lighting.
- JourneyMap per-block water light attenuation and its `maxDepth = 8.0F` traversal cutoff.
- JourneyMap color palette/configuration UI.
- A user-facing bathymetry toggle.
- Changing Blueprint sampling resolution, tile size, cache policy, contours, or viewport behavior.
- Reading client `OCEAN_FLOOR` or `MOTION_BLOCKING_NO_LEAVES` heightmaps.
