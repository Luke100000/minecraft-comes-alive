# Blueprint JourneyMap-Style Underwater Rendering Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the Blueprint map show the actual block beneath water by CPU-compositing biome water tint over the sampled seabed into the single opaque terrain texture.

**Architecture:** Use only the client-sent `WORLD_SURFACE` heightmap as the top-of-column anchor. Scan the actual client `BlockState`s downward once to derive two layers: the first motion-blocking terrain Y/state and the highest water Y above it. Keep the resolved terrain color/height and optional biome water tint separate in each CPU-side cell. Apply hillshade and contours to the terrain layer first, then composite the water tint with MCA's fixed seabed-visible contribution (`0.625F`) into one final opaque `DynamicTexture` pixel.

**Tech Stack:** Java 21, Minecraft 1.21.1 common client code, `GuiGraphics`, `NativeImage`, `DynamicTexture`, JUnit 5, Gradle

**Spec:** `docs/superpowers/specs/2026-09-04-blueprint-journeymap-underwater-rendering-design.md`

## Global Constraints

- The tree is actively dirty from unrelated/concurrent work. Capture `git status --short` immediately before implementation and preserve every unrelated dirty path; do not reset, overwrite, stage, or commit those changes as part of this renderer change. Stage only the exact Blueprint terrain renderer/test paths named by each task.
- Keep `SAMPLE_STEP = 1`, `TILE_BLOCK_SIZE = 128`, nearest texture filtering, tile cache behavior, retry timing, hillshade constants, and contour interval unchanged.
- Do not read `OCEAN_FLOOR` or `MOTION_BLOCKING_NO_LEAVES` on the client. They are `LIVE_WORLD` heightmaps, are pre-created by `LevelChunk`, but are not sent in the initial chunk packet; their unprimed/partially updated columns caused the incorrect flat-water sampling in the failed intermediate implementation.
- Keep `WORLD_SURFACE` as the reliable client-sent start height. Derive terrain and water layers from actual block states down to the world's minimum build height.
- Match Minecraft's `OCEAN_FLOOR` substrate predicate with the first `BlockState.blocksMotion()` result, without mutating or priming chunk heightmap state.
- Keep the downward `MapColor.NONE` fallback after selecting the terrain block.
- Replace MCA's current depth-opacity curve with one fixed `WATER_BLEND = 0.625F`. This deliberately leaves `37.5%` of the unshaded output color to the seabed; it follows JourneyMap's single-substrate-plus-water composition model without claiming to reproduce JourneyMap's current vanilla-water alpha exactly.
- Final terrain pixels are opaque. There must be no second translucent water texture or GUI blend pass.
- The final pixel must retain underlying block identity: changing only the seabed block at identical biome water tint must change the output pixel.
- Apply hillshade and contours to the terrain layer first, then composite the water tint. This prevents hard seabed contours from appearing as dark stripes on top of water.
- Do not add depth-driven water opacity. JourneyMap's `Strata.nextUp(..., true)` skips middle fluid strata rather than repeatedly compositing every water block.
- Do not port JourneyMap's generic `Strata`, palette, night-light, cave-light, per-block light attenuation, `maxDepth = 8.0F` cutoff, or arbitrary-transparent-block systems.

---

## File Structure

### Production file

- Modify `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java`
  - Own the pure water/seabed composition helper.
  - Store terrain color plus optional water tint as separate CPU-side layers per terrain cell.
  - Upload and render one terrain texture per tile.

### Test file

- Modify `common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java`
  - Replace the detached-overlay test with final-pixel behavior tests.
  - Lock the fixed seabed-visible blend and opaque-output contract.

---

### Task 1: Make the single-texture renderer sample client-safe terrain + water layers

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java`
- Modify: `common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java`

**Interfaces:**
- Consumes: the client-sent `WORLD_SURFACE`, actual block states in that column, and biome colors.
- Produces: `ColumnLayers(terrainY, waterY, terrainState)`, opaque `waterColor(ground, water)`, `TerrainTile.Cell(int height, int terrainColor, int waterTint)`, and exactly one `terrainTextureLocation` per tile.

- [ ] **Step 1: Capture the dirty-tree baseline**

Run:

```powershell
git status --short
```

Keep this output as the implementation baseline. Do not reset, overwrite, stage, or commit any unrelated path from it.

- [ ] **Step 2: Add a failing client-safe layer-sampling regression**

Keep the existing final-pixel tests for substrate-dependent opaque water composition. Add one
behavioral regression that provides synthetic columns as real `BlockState`s:

```java
@Test
void waterColumnResolvesActualSubstrateBeforeComposition() {
    IntFunction<BlockState> sandColumn = y -> {
        if (y == 62) return Blocks.SAND.defaultBlockState();
        if (y <= 64 && y > 62) return Blocks.WATER.defaultBlockState();
        return Blocks.AIR.defaultBlockState();
    };
    ColumnLayers sand = BlueprintTerrainRenderer.sampleColumnLayers(sandColumn, 64, -64);
    assertEquals(62, sand.terrainY());
    assertEquals(64, sand.waterY());
    assertEquals(Blocks.SAND.defaultBlockState(), sand.terrainState());

    IntFunction<BlockState> deepStoneColumn = y -> {
        if (y == 40) return Blocks.STONE.defaultBlockState();
        if (y <= 64 && y > 40) return Blocks.WATER.defaultBlockState();
        return Blocks.AIR.defaultBlockState();
    };
    ColumnLayers stone = BlueprintTerrainRenderer.sampleColumnLayers(deepStoneColumn, 64, -64);
    assertEquals(40, stone.terrainY());
    assertEquals(64, stone.waterY());
    assertEquals(Blocks.STONE.defaultBlockState(), stone.terrainState());
}
```

This catches the actual regression: replacing the scan with client `OCEAN_FLOOR`/`MOTION_BLOCKING_NO_LEAVES`
or adding JourneyMap's generic eight-block transparency cutoff loses the real substrate.

- [ ] **Step 3: Run the focused test class and verify RED**

Run:

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.client.gui.BlueprintScreenMapInteractionTest --no-daemon --console=plain
```

Expected: the new regression fails because the client-safe `sampleColumnLayers(...)` contract does not exist yet.

- [ ] **Step 4: Preserve the existing fixed seabed-visible color blend**

Keep:

```java
private static final float WATER_BLEND = 0.625f;
```

```java
static int waterColor(int groundColor, int biomeWaterColor) {
    return blendOpaque(groundColor, biomeWaterColor, WATER_BLEND);
}

private static int blendOpaque(int baseColor, int overlayColor, float opacity) {
    float inverse = 1.0f - opacity;
    int red = Math.round(((baseColor >> 16) & 0xff) * inverse
            + ((overlayColor >> 16) & 0xff) * opacity);
    int green = Math.round(((baseColor >> 8) & 0xff) * inverse
            + ((overlayColor >> 8) & 0xff) * opacity);
    int blue = Math.round((baseColor & 0xff) * inverse
            + (overlayColor & 0xff) * opacity);
    return 0xff000000 | (red << 16) | (green << 8) | blue;
}
```

`0.625F` is the fixed MCA readability blend already chosen for this renderer: it leaves `37.5%` of the unshaded output color to the substrate. JourneyMap supplies the structural model—terrain height and fluid height are tracked separately, middle fluid strata are skipped, and water is composed with the real block below—but this source tree later overwrites vanilla water's initial `0.25F` material alpha with the generic `LiquidBlock` alpha `0.7F`, so `0.625F` must not be described as JourneyMap's exact current effective default.

- [ ] **Step 5: Derive terrain + water layers from client block states**

Starting at `WORLD_SURFACE - 1`, scan actual block states downward. Remember the highest water Y and stop at the first motion-blocking block. This creates the two JourneyMap-style sampling layers without relying on client-unreliable `LIVE_WORLD` heightmaps:

```java
static ColumnLayers sampleColumnLayers(IntFunction<BlockState> stateAtY, int topY, int minY) {
    int waterY = Integer.MIN_VALUE;
    for (int y = topY; y >= minY; y--) {
        BlockState state = stateAtY.apply(y);
        if (state.blocksMotion()) {
            return new ColumnLayers(y, waterY, state);
        }
        if (waterY == Integer.MIN_VALUE && isWater(state)) {
            waterY = y;
        }
    }
    return null;
}
```

When the returned layer has water, resolve `terrainState` at `terrainY`, preserve the existing `MapColor.NONE` fallback and biome tinting, and resolve biome water tint at `waterY`. Store `terrainY + 1` as the cell height, the resolved seabed color as `terrainColor`, and the biome water tint as `waterTint`. Do not flatten those two colors during sampling.

Do not add JourneyMap's `maxDepth = 8.0F` traversal limit. It is part of JourneyMap's generic transparency renderer; Blueprint must continue to show the substrate under deep water.

Add a RED regression before this production change: a water column over sand must resolve sand, and a deep water column over stone must still resolve stone while reporting the top water Y separately.

- [ ] **Step 6: Delete the separate water texture lifecycle**

In `BlueprintTerrainRenderer.java`:

1. Remove `import com.mojang.blaze3d.systems.RenderSystem;`.
2. Rename `createTextures(...)` to `createTexture(...)`.
3. Make `renderTile(...)` perform only the terrain blit.
4. In `createTexture(...)`, create only `terrainImage`; delete `waterImage`, `hasWater`, `nativeWaterColor`, all water-image writes, and the `mca_blueprint_water` registration.
5. Style the terrain layer first, then composite the water layer for both normal and contour pixels:

```java
int color = composeTerrainAndWater(
        cell.terrainColor(), cell.waterTint(), brightness, false);
int contourColor = composeTerrainAndWater(
        cell.terrainColor(), cell.waterTint(), brightness, true);
```

6. Change the record to:

```java
private record Cell(int height, int terrainColor, int waterTint) {
}
```

7. Delete `TerrainTile.waterTextureLocation` and its release branch. `releaseTexture(...)` releases only `terrainTextureLocation`.

8. Add a RED regression for the layer-ordering bug seen in the live Blueprint screen. With a known terrain color, water tint, brightness, and contour, assert the literal pixel produced when the contour is part of the terrain layer and water is composited last. The old order (water first, contour last) must fail this test because it creates the hard dark water stripes.

- [ ] **Step 7: Run the focused tests and verify GREEN**

Run:

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.client.gui.BlueprintScreenMapInteractionTest --no-daemon --console=plain
```

Expected: PASS, including substrate-dependent output, fixed blend, water-after-terrain ordering, and opaque final pixels.

- [ ] **Step 8: Verify the old overlay/depth path is completely gone**

Run:

```powershell
rg -n "waterTextureLocation|waterOverlayColor|mca_blueprint_water|RenderSystem|WATER_BASE_OPACITY|WATER_DEPTH_OPACITY_PER_BLOCK|WATER_MAX_OPACITY|waterDepth" common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java
```

Expected: no matches.

Run:

```powershell
rg -n "WATER_BLEND|waterColor\(|composeTerrainAndWater|terrainColor|waterTint" common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java
```

Expected: one fixed blend constant, the pure composition helper, the sampling call, separate CPU-side terrain/water fields, and the behavioral tests are present.

- [ ] **Step 9: Commit the complete renderer slice**

```powershell
git add common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java
git commit -m "fix: show blueprint seabed through water"
```

Do not add any unrelated dirty path from the implementation-start `git status --short` baseline.

---

### Task 2: Regression and runtime verification

**Files:**
- Verify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java`
- Verify: `common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java`
- Preserve untouched: every unrelated dirty path captured in the implementation-start `git status --short` baseline.

**Interfaces:**
- Consumes: completed single-texture renderer from Task 1.
- Produces: verified 1.21.1 common build plus runtime visual evidence.

- [ ] **Step 1: Run the full common test suite**

```powershell
.\gradlew.bat :common:test --no-daemon --console=plain
```

Expected: PASS. If an unrelated pre-existing failure remains in the concurrent floor/map work, record its exact failure separately; do not modify those files as part of this renderer task.

- [ ] **Step 2: Compile common production code explicitly**

```powershell
.\gradlew.bat :common:compileJava --no-daemon --console=plain
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Check patch hygiene and scope**

Run these separately:

```powershell
git diff --check
```

```powershell
git status --short
```

```powershell
git diff -- common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java
```

Expected:

- no whitespace errors,
- only the renderer/test changes belong to this feature,
- every unrelated dirty path from the implementation-start baseline is still preserved and not folded into this work.

- [ ] **Step 4: Run the Fabric 1.21.1 client and perform visual acceptance**

Run:

```powershell
.\gradlew.bat :fabric:runClient --no-daemon --console=plain
```

In a world with Blueprint map access, inspect:

1. shallow water with sand adjacent to gravel or stone,
2. deeper water over the same substrates,
3. a tile boundary while panning,
4. multiple Blueprint zoom levels.

Pass only if all of these are observable:

- sand-under-water and stone/gravel-under-water are visibly different at equal depth,
- the underwater block remains visibly contributory in both shallow and deep water,
- there is no artificial color-alpha ramp caused only by water depth,
- there is no detached blue overlay, texture offset, or seam while panning,
- hillshade/contours remain aligned with underwater terrain,
- ordinary land rendering remains unchanged.

- [ ] **Step 5: Record verification evidence before declaring completion**

Capture the exact Gradle results and the runtime locations/substrates checked. Do not treat a green unit test alone as proof of the visual requirement.
