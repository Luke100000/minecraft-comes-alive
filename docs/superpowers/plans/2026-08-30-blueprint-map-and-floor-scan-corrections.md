# Blueprint Map and Floor-Scan Corrections Implementation Plan

> **Scanner plan superseded (2026-08-30):** Do not continue Tasks 5-6 or the scanner-specific
> runtime steps in Task 7 from this plan. Those tasks encode the older persisted-storey boundary
> and POI/connector ownership architecture. The replacement design is
> `docs/superpowers/specs/2026-08-30-floor-scanner-simplification-design.md`. A new scanner
> implementation plan must be written only after that design is reviewed. Map Tasks 1-4 remain
> valid historical/implementation guidance.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Blueprint map pan/zoom smooth and layer-coherent, simplify its labels and building outlines, and correct the two reported floor-scanner ownership failures.

**Architecture:** `BlueprintMapViewport` remains the only camera/projection owner, while every map layer renders under one unrounded map pose and constant-width outline quads use inverse scale. `BlueprintMapGeometry` derives logical-building shells only from registered Room footprints. Server-side, `BuildingRoomScanner` uses one deterministic largest adjacent component-owner policy and `StructureScanner` adds a planned-scan-only persisted-storey boundary without weakening final overlap validation.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge/Fabric multiloader Gradle build, Mojang `GuiGraphics`/`PoseStack`/Blaze3D buffers, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-08-30-blueprint-map-and-floor-scan-corrections-design.md`

## Global Constraints

- Work only in `C:\Users\Mik\Downloads\MCA\minecraft-comes-alive-1.21.1-floor-clean-squash` on `feature/1.21.1-floor-clean-squash`; verify both before each completion claim.
- Preserve the existing untracked `common/logs/` directory and all unrelated user changes. Never use `git add .`, reset, clean, checkout-discard, or broad restore commands.
- Compare behavior against `origin/feature/1.21.1-floor-clean-squash` at `80bfe7d0e`, but do not restore the latent scanner or outline defects present there.
- Do not add dependencies, an off-screen framebuffer, a second camera/projection pipeline, or mutable global formatter state.
- Keep `BlueprintTerrainRenderer` as the terrain cache/texture owner and preserve `DynamicTexture#setFilter(false, false)`.
- Keep `VillageManager` as attachment orchestration/commit owner; analysis must remain detached and Village mutation atomic.
- Preserve ordinary same-storey traversal and `candidate.intersects(existing)` so real overlaps still return `OVERLAP`.
- Every production change follows observable RED -> GREEN: add the focused test, run it and confirm the expected failure, implement the minimum behavior, then rerun the focused test.
- Visual and scanner completion require the runtime scenarios in Task 7; a successful Gradle build alone is not completion evidence.
- Stage only the exact files listed in each commit step. The spec and this plan may be included in the first commit only if the user has approved committing the documentation.

## File and Responsibility Map

| File | Responsibility in this plan |
| --- | --- |
| `common/src/main/java/net/conczin/mca/client/gui/BlueprintMapViewport.java` | Exact double camera state, forward/inverse projection, pointer-anchored zoom, fractional marker clamp. |
| `common/src/main/java/net/conczin/mca/client/gui/BlueprintScreen.java` | Apply pointer-anchored wheel zoom and format scale labels. |
| `common/src/main/java/net/conczin/mca/client/gui/BlueprintMapRenderer.java` | Keep all map layers under one pose; batch one-pixel map-space outline quads; exact hover/marker projection. |
| `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java` | No intended logic change; verify it still renders under the caller pose with nearest filtering. |
| `common/src/main/java/net/conczin/mca/client/gui/BlueprintMapGeometry.java` | Cache all-Room logical-building shapes and derive one-cell shells/edges. |
| `common/src/main/java/net/conczin/mca/client/gui/BlueprintTooltipFactory.java` | Suppress redundant type headings; label residents; format POI counts with `×`. |
| `common/src/main/resources/assets/mca/lang/en_us.json` | Add singular/plural resident tooltip strings. |
| `common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java` | Shared largest-component connector/POI owner rule. |
| `common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java` | Planned-scan-only persisted-storey boundary for connector traversal and associated cells. |
| `common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java` | Camera, zoom, and scale-copy behavior. |
| `common/src/test/java/net/conczin/mca/client/gui/BlueprintMapRendererTest.java` | Constant-width outline geometry and exact edge-hit bounds. |
| `common/src/test/java/net/conczin/mca/client/gui/BlueprintMapGeometryTest.java` | All-Room union, one-cell expansion, and stable building shell. |
| `common/src/test/java/net/conczin/mca/client/gui/BlueprintTooltipHierarchyTest.java` | Non-repeating hierarchy, resident labels, and POI copy. |
| `common/src/test/java/net/conczin/mca/server/world/data/BuildingRoomScannerOwnerTest.java` | Interior owner selection and deterministic equal-area ties. |
| `common/src/test/java/net/conczin/mca/server/world/data/StructureScannerAttachmentBoundaryTest.java` | Other-storey blocking, same-storey preservation, and associated-cell filtering. |

---

### Task 1: Preserve exact camera state and anchor wheel zoom to the pointer

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintMapViewport.java:3-99`
- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintScreen.java:619-645, 1216-1227`
- Modify: `common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java`

**Interfaces:**
- Consumes: existing `BlueprintMapViewport.create(...)`, `screenX(...)`, `screenY(...)`, and Blueprint screen camera fields.
- Produces: `double worldX(double screenX)`, `double worldZ(double screenY)`, and `BlueprintMapViewport zoomedAround(double screenX, double screenY, float newScale)`.
- Produces for Task 2: an exact, unrounded viewport whose map center is never replaced by a pixel-snapped value.

- [ ] **Step 1: Add failing exact-camera and pointer-zoom tests**

Add these tests to `BlueprintScreenMapInteractionTest`:

```java
@Test
void viewportPreservesFractionalRequestedCenter() {
    BlueprintMapViewport viewport = BlueprintMapViewport.create(
            100, 120, 80, -305.25D, -1653.75D, 1.37F);

    assertEquals(-305.25D, viewport.mapCenterX(), 0.0000001D);
    assertEquals(-1653.75D, viewport.mapCenterZ(), 0.0000001D);
    assertEquals(100.3425D, viewport.screenX(-305.0D), 0.0001D);
}

@Test
void zoomAroundPointerKeepsCursorWorldPositionInvariant() {
    BlueprintMapViewport before = BlueprintMapViewport.create(
            100, 120, 80, -305.25D, -1653.75D, 1.37F);
    double mouseX = 137.5D;
    double mouseY = 91.25D;
    double worldX = before.worldX(mouseX);
    double worldZ = before.worldZ(mouseY);

    BlueprintMapViewport after = before.zoomedAround(mouseX, mouseY, 2.36F);

    assertEquals(worldX, after.worldX(mouseX), 0.0000001D);
    assertEquals(worldZ, after.worldZ(mouseY), 0.0000001D);
}
```

- [ ] **Step 2: Run the focused test and confirm RED**

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.client.gui.BlueprintScreenMapInteractionTest'
```

Expected: compilation fails because `worldX`, `worldZ`, and `zoomedAround` do not exist; after temporarily adding only the method declarations, `viewportPreservesFractionalRequestedCenter` fails because `Math.rint` changes the requested center.

- [ ] **Step 3: Remove camera quantization and add exact inverse/zoom methods**

Replace the rounded-origin block in `BlueprintMapViewport.create` and add the inverse/zoom methods:

```java
static BlueprintMapViewport create(int centerX,
                                   int centerY,
                                   int halfSize,
                                   double requestedMapCenterX,
                                   double requestedMapCenterZ,
                                   float scale) {
    if (scale <= 0.0F) throw new IllegalArgumentException("scale must be positive");
    return new BlueprintMapViewport(
            centerX, centerY,
            centerX - halfSize, centerY - halfSize,
            centerX + halfSize, centerY + halfSize,
            requestedMapCenterX, requestedMapCenterZ, scale);
}

double worldX(double screenX) {
    return mapCenterX + (screenX - centerX) / scale;
}

double worldZ(double screenY) {
    return mapCenterZ + (screenY - centerY) / scale;
}

BlueprintMapViewport zoomedAround(double screenX, double screenY, float newScale) {
    double cursorWorldX = worldX(screenX);
    double cursorWorldZ = worldZ(screenY);
    double newCenterX = cursorWorldX - (screenX - centerX) / newScale;
    double newCenterZ = cursorWorldZ - (screenY - centerY) / newScale;
    return create(centerX, centerY, halfSize(), newCenterX, newCenterZ, newScale);
}
```

Refactor `screenToCell` to delegate to the inverse methods so forward/inverse math has one owner:

```java
BlueprintMapFootprint.Cell screenToCell(double screenX, double screenY) {
    return new BlueprintMapFootprint.Cell(
            (int) Math.floor(worldX(screenX)),
            (int) Math.floor(worldZ(screenY)));
}
```

- [ ] **Step 4: Apply pointer-anchored zoom in `BlueprintScreen`**

Replace the wheel body with:

```java
float currentScale = getMapScale();
float newScale = zoomMapScale(currentScale, scrollY);
BlueprintMapViewport currentViewport = BlueprintMapViewport.create(
        width / 2, height / 2 + 8, MAP_HALF_SIZE,
        mapCenterX, mapCenterZ, currentScale);
BlueprintMapViewport zoomedViewport = currentViewport.zoomedAround(mouseX, mouseY, newScale);

mapScaleFit = false;
mapScale = newScale;
mapCenterX = zoomedViewport.mapCenterX();
mapCenterZ = zoomedViewport.mapCenterZ();
playerCentered = false;
rememberedPlayerCentered = false;
updatePlayerCenteredControl();
rememberMapScale();
updateMapScaleControl();
return true;
```

Do not change drag math: `mapCenterX -= dragX / scale` and `mapCenterZ -= dragY / scale` are already continuous doubles once viewport quantization is removed.

- [ ] **Step 5: Run focused tests and confirm GREEN**

Run the same focused Gradle command. Expected: every `BlueprintScreenMapInteractionTest` test passes.

- [ ] **Step 6: Review and commit the exact-camera slice**

Run:

```powershell
git diff --check
git diff -- common/src/main/java/net/conczin/mca/client/gui/BlueprintMapViewport.java common/src/main/java/net/conczin/mca/client/gui/BlueprintScreen.java common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java
```

Confirm no `Math.rint` remains in `BlueprintMapViewport` and no unrelated screen behavior changed. Then stage only the three files (plus approved plan/spec documents if requested) and commit:

```powershell
git add common/src/main/java/net/conczin/mca/client/gui/BlueprintMapViewport.java common/src/main/java/net/conczin/mca/client/gui/BlueprintScreen.java common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java
git commit -m "fix: preserve exact blueprint map camera"
```

---

### Task 2: Render every map layer under one transform with one-pixel outlines

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintMapRenderer.java:55-277, 326-493`
- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintMapViewport.java:69-100`
- Create: `common/src/test/java/net/conczin/mca/client/gui/BlueprintMapRendererTest.java`
- Verify unchanged: `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java:29-58, 118-122`

**Interfaces:**
- Consumes: Task 1's exact `BlueprintMapViewport`, existing `RowSpan`/`Edge` geometry, existing map pose, and existing color selection.
- Produces: package-visible `OutlineQuad outlineQuad(BlueprintMapFootprint.Edge edge, float scale)` used by both the renderer and focused geometry tests.
- Produces: fractional `BlueprintMapViewport.ScreenPoint(double x, double y)` for the fixed-size player marker.

- [ ] **Step 1: Add failing constant-width outline tests**

Create `BlueprintMapRendererTest`:

```java
package net.conczin.mca.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlueprintMapRendererTest {
    @Test
    void horizontalOutlineIsOneScreenPixelAtEveryScale() {
        BlueprintMapFootprint.Edge edge = new BlueprintMapFootprint.Edge(10, 20, 14, 20);

        for (float scale : new float[]{0.5F, 1.0F, 1.37F, 2.36F, 4.0F}) {
            BlueprintMapRenderer.OutlineQuad quad = BlueprintMapRenderer.outlineQuad(edge, scale);
            assertEquals(1.0F, (quad.maxZ() - quad.minZ()) * scale, 0.0001F);
            assertTrue(quad.minX() < edge.x0());
            assertTrue(quad.maxX() > edge.x1());
        }
    }

    @Test
    void verticalOutlineIsOneScreenPixelAtEveryScale() {
        BlueprintMapFootprint.Edge edge = new BlueprintMapFootprint.Edge(10, 20, 10, 24);

        for (float scale : new float[]{0.5F, 1.0F, 1.37F, 2.36F, 4.0F}) {
            BlueprintMapRenderer.OutlineQuad quad = BlueprintMapRenderer.outlineQuad(edge, scale);
            assertEquals(1.0F, (quad.maxX() - quad.minX()) * scale, 0.0001F);
            assertTrue(quad.minZ() < edge.z0());
            assertTrue(quad.maxZ() > edge.z1());
        }
    }
}
```

- [ ] **Step 2: Run the focused renderer test and confirm RED**

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.client.gui.BlueprintMapRendererTest'
```

Expected: compilation fails because `OutlineQuad` and `outlineQuad` do not exist.

- [ ] **Step 3: Add the actual outline-quad geometry used by rendering**

Add to `BlueprintMapRenderer`:

```java
static OutlineQuad outlineQuad(BlueprintMapFootprint.Edge edge, float scale) {
    float halfWidth = 0.5F / scale;
    if (edge.z0() == edge.z1()) {
        float minX = Math.min(edge.x0(), edge.x1()) - halfWidth;
        float maxX = Math.max(edge.x0(), edge.x1()) + halfWidth;
        return new OutlineQuad(minX, edge.z0() - halfWidth, maxX, edge.z0() + halfWidth);
    }
    float minZ = Math.min(edge.z0(), edge.z1()) - halfWidth;
    float maxZ = Math.max(edge.z0(), edge.z1()) + halfWidth;
    return new OutlineQuad(edge.x0() - halfWidth, minZ, edge.x0() + halfWidth, maxZ);
}

record OutlineQuad(float minX, float minZ, float maxX, float maxZ) {
}

private record OutlineLayer(List<BlueprintMapFootprint.Edge> edges, int color) {
}
```

Endpoint expansion by `halfWidth` makes perpendicular segments overlap at corners rather than leaving subpixel gaps.

- [ ] **Step 4: Replace independently rounded fills with map-space span rendering**

Replace `renderCellSpansScreenSpace` with a method called only while the map pose is active:

```java
private static void renderCellSpansMapSpace(GuiGraphics context,
                                            List<BlueprintMapFootprint.RowSpan> spans,
                                            int color) {
    for (BlueprintMapFootprint.RowSpan span : spans) {
        context.fill(span.minX(), span.z(), span.maxX() + 1, span.z() + 1, color);
    }
}
```

Change `renderRoomFootprint` and `renderStructureShade` to delegate to this method and remove their viewport parameter. Do not project or round span endpoints.

- [ ] **Step 5: Add a renderer-local batched `POSITION_COLOR` outline path**

Use the existing local Blaze3D pattern and the current map pose:

```java
private static void renderOutlineBatch(GuiGraphics context,
                                       List<OutlineLayer> layers,
                                       float scale) {
    if (layers.stream().allMatch(layer -> layer.edges().isEmpty())) return;

    context.flush();
    RenderSystem.enableBlend();
    RenderSystem.defaultBlendFunc();
    RenderSystem.setShader(GameRenderer::getPositionColorShader);
    BufferBuilder builder = Tesselator.getInstance().begin(
            VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
    Matrix4f matrix = context.pose().last().pose();

    for (OutlineLayer layer : layers) {
        for (BlueprintMapFootprint.Edge edge : layer.edges()) {
            OutlineQuad quad = outlineQuad(edge, scale);
            builder.addVertex(matrix, quad.minX(), quad.maxZ(), 0.0F).setColor(layer.color());
            builder.addVertex(matrix, quad.maxX(), quad.maxZ(), 0.0F).setColor(layer.color());
            builder.addVertex(matrix, quad.maxX(), quad.minZ(), 0.0F).setColor(layer.color());
            builder.addVertex(matrix, quad.minX(), quad.minZ(), 0.0F).setColor(layer.color());
        }
    }

    BufferUploader.drawWithShader(builder.buildOrThrow());
    RenderSystem.disableBlend();
}
```

Add the required Blaze3D/JOML imports. Build one `OutlineLayer` list for structure borders and one for Room borders so the existing order stays: shell fill -> structure border -> Room fill -> Room border. Do not begin/upload a buffer per edge.

- [ ] **Step 6: Keep the complete map content under one pushed map pose**

Restructure `render` so the map pose spans these operations in order. The surrounding hover-target calculations remain unchanged:

```java
PoseStack matrices = context.pose();
pushWorldTransform(matrices, viewport);
if (showTerrain) terrainRenderer.render(context, viewport);

// Render grouped legacy regions with their existing world-coordinate method.
// Then render every structure shell span before its border batch.
renderOutlineBatch(context, structureOutlineLayers, viewport.scale());

// Render Room span fills in existing back-to-front order.
renderOutlineBatch(context, roomOutlineLayers, viewport.scale());

// Render grouped and Room icons at their existing world anchors.
matrices.popPose();
```

Retain the current hover ordering and colors. Move only drawing calls; keep hover-target collection outside or alongside the same loops without creating a second projection. The fixed map border and scissor remain in GUI coordinates.

- [ ] **Step 7: Make edge hover use the same unrounded quad bounds**

Replace rounded-pixel equality in `isOutlineHovered` with inverse-projected map coordinates and the actual quad:

```java
double worldX = viewport.worldX(mouseScreenX);
double worldZ = viewport.worldZ(mouseScreenY);
for (BlueprintMapFootprint.Edge edge : edges) {
    OutlineQuad quad = outlineQuad(edge, viewport.scale());
    if (worldX >= quad.minX() && worldX <= quad.maxX()
            && worldZ >= quad.minZ() && worldZ <= quad.maxZ()) {
        return true;
    }
}
return false;
```

This makes visual and interactive edge bounds share one geometry owner.

- [ ] **Step 8: Preserve fractional player-marker placement**

Change `ScreenPoint` to doubles and remove final rounding in `clampMarker`:

```java
double x = centerX + dx * factor;
double y = centerY + dy * factor;
x = Math.max(minCenterX, Math.min(maxCenterX, x));
y = Math.max(minCenterY, Math.min(maxCenterY, y));
return new ScreenPoint(x, y);

record ScreenPoint(double x, double y) {
}
```

Clamp doubles directly. In `renderPlayerMarker`, push a local pose translated by the fractional marker top-left, render the fixed-size background/face at local integer coordinates, then pop:

```java
double markerX = markerCenter.x() - PLAYER_MARKER_SIZE / 2.0D;
double markerY = markerCenter.y() - PLAYER_MARKER_SIZE / 2.0D;
context.pose().pushPose();
context.pose().translate(markerX, markerY, 0.0D);
context.fill(-1, -1, PLAYER_MARKER_SIZE + 1, PLAYER_MARKER_SIZE + 1, 0xc0000000);
renderCurrentPlayerFace(context, player, 0, 0, PLAYER_MARKER_SIZE);
context.pose().popPose();
```

- [ ] **Step 9: Run focused and client GUI tests**

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.client.gui.BlueprintMapRendererTest' --tests 'net.conczin.mca.client.gui.BlueprintScreenMapInteractionTest'
```

Expected: both classes pass. Confirm `BlueprintTerrainRenderer` still contains `texture.setFilter(false, false)` and has no new camera fields.

- [ ] **Step 10: Review and commit the unified-rendering slice**

Inspect the renderer diff for balanced `pushPose`/`popPose`, one scissor enable/disable pair, and explicit `GuiGraphics.flush()` before raw buffer uploads. Stage only Task 2 files and commit:

```powershell
git add common/src/main/java/net/conczin/mca/client/gui/BlueprintMapViewport.java common/src/main/java/net/conczin/mca/client/gui/BlueprintMapRenderer.java common/src/test/java/net/conczin/mca/client/gui/BlueprintMapRendererTest.java
git commit -m "fix: align blueprint map render layers"
```

---

### Task 3: Derive a stable one-cell building shell from all registered Rooms

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintMapGeometry.java:21-177`
- Create: `common/src/test/java/net/conczin/mca/client/gui/BlueprintMapGeometryTest.java`

**Interfaces:**
- Consumes: existing `BlueprintMapFootprint.shape`, `expand`, Room footprint layers, and logical-building IDs.
- Produces: package-visible `BuildingShape buildBuildingShape(Collection<? extends Collection<BlueprintMapFootprint.Cell>> roomFootprints)`.
- Produces: one cached all-floor `MapStructureLayer` shape per logical building, reused by every selected-floor geometry.

- [ ] **Step 1: Add failing all-Room padding tests**

Create `BlueprintMapGeometryTest`:

```java
package net.conczin.mca.client.gui;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlueprintMapGeometryTest {
    @Test
    void buildingShapeIsOneCellPaddingAroundRoomsFromEveryFloor() {
        Set<BlueprintMapFootprint.Cell> ground = Set.of(
                new BlueprintMapFootprint.Cell(0, 0),
                new BlueprintMapFootprint.Cell(1, 0),
                new BlueprintMapFootprint.Cell(0, 1));
        Set<BlueprintMapFootprint.Cell> upper = Set.of(
                new BlueprintMapFootprint.Cell(2, 0),
                new BlueprintMapFootprint.Cell(2, 1));
        LinkedHashSet<BlueprintMapFootprint.Cell> union = new LinkedHashSet<>(ground);
        union.addAll(upper);

        BlueprintMapGeometry.BuildingShape shape =
                BlueprintMapGeometry.buildBuildingShape(List.of(ground, upper));
        Set<BlueprintMapFootprint.Cell> expectedOutline = BlueprintMapFootprint.expand(union, 1);
        LinkedHashSet<BlueprintMapFootprint.Cell> expectedShell = new LinkedHashSet<>(expectedOutline);
        expectedShell.removeAll(union);

        assertEquals(expectedOutline, shape.outline().cells());
        assertEquals(expectedShell, shape.shell().cells());
    }

    @Test
    void buildingShapeIsEmptyWithoutRegisteredRooms() {
        BlueprintMapGeometry.BuildingShape shape = BlueprintMapGeometry.buildBuildingShape(List.of());

        assertEquals(Set.of(), shape.outline().cells());
        assertEquals(Set.of(), shape.shell().cells());
    }
}
```

- [ ] **Step 2: Run the geometry test and confirm RED**

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.client.gui.BlueprintMapGeometryTest'
```

Expected: compilation fails because `BuildingShape` and `buildBuildingShape` do not exist.

- [ ] **Step 3: Add the canonical all-Room shape helper**

Add to `BlueprintMapGeometry`:

```java
static BuildingShape buildBuildingShape(
        Collection<? extends Collection<BlueprintMapFootprint.Cell>> roomFootprints) {
    LinkedHashSet<BlueprintMapFootprint.Cell> roomCells = new LinkedHashSet<>();
    roomFootprints.forEach(roomCells::addAll);
    if (roomCells.isEmpty()) {
        BlueprintMapFootprint.Shape empty = BlueprintMapFootprint.shape(Set.of());
        return new BuildingShape(empty, empty);
    }

    Set<BlueprintMapFootprint.Cell> outlineCells =
            BlueprintMapFootprint.expand(roomCells, BUILDING_OUTLINE_WIDTH);
    LinkedHashSet<BlueprintMapFootprint.Cell> shellCells = new LinkedHashSet<>(outlineCells);
    shellCells.removeAll(roomCells);
    return new BuildingShape(
            BlueprintMapFootprint.shape(outlineCells),
            BlueprintMapFootprint.shape(shellCells));
}

record BuildingShape(BlueprintMapFootprint.Shape outline,
                     BlueprintMapFootprint.Shape shell) {
}
```

- [ ] **Step 4: Build Room layers once, then filter views without changing the building shape**

Add lazy immutable all-Room and all-building-layer caches:

```java
private List<MapFootprintLayer> allRoomLayers;
private List<MapStructureLayer> buildingLayers;

private List<MapFootprintLayer> allRoomLayers() {
    if (allRoomLayers == null) allRoomLayers = buildRoomLayers(null);
    return allRoomLayers;
}

private List<MapStructureLayer> buildingLayers() {
    if (buildingLayers == null) {
        buildingLayers = buildStructureLayers(groupRoomLayers(allRoomLayers()));
    }
    return buildingLayers;
}
```

In `get(Integer selectedFloor)`, derive visible rooms from that canonical list:

```java
List<MapFootprintLayer> allRooms = allRoomLayers();
List<MapFootprintLayer> visibleRooms = selectedFloor == null
        ? allRooms
        : allRooms.stream()
                .filter(layer -> Objects.equals(layer.floorOrdinal(), selectedFloor))
                .toList();
Map<Integer, List<MapFootprintLayer>> visibleRoomsByBuilding = groupRoomLayers(visibleRooms);
List<MapStructureLayer> structures = buildingLayers();
List<MapIconLayer> icons = buildIconLayers(visibleRoomsByBuilding, selectedFloor);
```

Use `visibleRooms` in the returned `MapGeometry`; `buildingLayers()` is the only owner of stable all-Room logical-building shells.

In `buildRoomLayers`, add `.filter(Building::isComplete)` immediately after `village.getRooms()` so incomplete candidates never contribute to the canonical shape.

- [ ] **Step 5: Replace physical-floor shell construction**

Rewrite `buildStructureLayers` to iterate the all-Room groups. For each logical-building entry:

```java
List<MapFootprintLayer> rooms = entry.getValue();
BuildingShape shape = buildBuildingShape(
        rooms.stream().map(MapFootprintLayer::footprintCells).toList());
if (shape.outline().cells().isEmpty()) continue;

MapFootprintLayer mainLayer = rooms.stream()
        .filter(layer -> village.isMainRoom(layer.building()))
        .findFirst()
        .orElse(rooms.getFirst());
int anchorY = rooms.stream().mapToInt(MapFootprintLayer::anchorY).min().orElse(mainLayer.anchorY());

layers.add(new MapStructureLayer(
        entry.getKey(), mainLayer.building(), anchorY,
        shape.outline().cells(), shape.shell().cells(),
        shape.shell().spans(), shape.outline().edges()));
```

Delete `outlineBaseWithoutEntranceProtrusions`, `cardinalNeighborCount`, and the physical `StructureFloor` aggregation imports. Do not derive the outline from selected-floor rooms.

- [ ] **Step 6: Run focused map geometry and GUI tests**

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.client.gui.BlueprintMapGeometryTest' --tests 'net.conczin.mca.client.gui.BlueprintMapRendererTest'
```

Expected: both classes pass.

- [ ] **Step 7: Review and commit the Room-owned outline slice**

Confirm the production diff no longer reads `StructureFloor.region()` for Blueprint shell construction and no entrance-protrusion heuristic remains. Stage exact files and commit:

```powershell
git add common/src/main/java/net/conczin/mca/client/gui/BlueprintMapGeometry.java common/src/test/java/net/conczin/mca/client/gui/BlueprintMapGeometryTest.java
git commit -m "fix: derive blueprint outlines from rooms"
```

---

### Task 4: Remove scale trailing zeroes and simplify tooltip hierarchy

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintScreen.java:812-814`
- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintTooltipFactory.java:38-242`
- Modify: `common/src/main/resources/assets/mca/lang/en_us.json:505-510`
- Modify: `common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java:31-36`
- Modify: `common/src/test/java/net/conczin/mca/client/gui/BlueprintTooltipHierarchyTest.java`

**Interfaces:**
- Consumes: existing `formatMapScale`, tooltip grouping, `typeLabel`, `appendPoi`, and localized block names.
- Produces: `Resident: name`/`Residents: names`, no redundant sole title-matching Room heading, and `count × block` item lines.
- Produces: scale text with zero required and at most two optional fractional digits.

- [ ] **Step 1: Change scale expectations to the requested copy and confirm RED**

Replace the current scale-label test with:

```java
@Test
void numericScaleLabelUsesAtMostTwoDecimalsWithoutTrailingZeroes() {
    assertEquals("1.37:1", BlueprintScreen.formatMapScale(1.37F));
    assertEquals("2:1", BlueprintScreen.formatMapScale(2.0F));
    assertEquals("0.5:1", BlueprintScreen.formatMapScale(0.5F));
    assertEquals("2.36:1", BlueprintScreen.formatMapScale(2.356F));
}
```

Run `BlueprintScreenMapInteractionTest`. Expected: RED because current output is `2.00:1` and `0.50:1`.

- [ ] **Step 2: Implement an isolated root-locale formatter**

Add imports for `DecimalFormat` and `DecimalFormatSymbols`, then implement:

```java
static String formatMapScale(float scale) {
    DecimalFormat formatter = new DecimalFormat(
            "0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
    formatter.setGroupingUsed(false);
    return formatter.format(scale) + ":1";
}
```

Do not store `DecimalFormat` in a mutable static field.

- [ ] **Step 3: Add failing tooltip-copy tests**

Extend `BlueprintTooltipHierarchyTest` with a floor-specific sole-group assertion:

```java
@Test
void soleTitleMatchingRoomDoesNotRepeatBuildingType() throws Exception {
    Fixture fixture = fixture();
    fixture.groundRoom().addBlock(Blocks.YELLOW_BED, BlockPos.ZERO);
    BlueprintTooltipFactory factory = BlueprintTooltipFactory.create(
            fixture.village(), RoomTypeResolver.create(fixture.village()));

    List<String> lines = factory.tooltip(fixture.groundRoom(), 0, true).stream()
            .map(component -> component.getString())
            .toList();

    assertEquals(1L, lines.stream().filter("House"::equals).count());
    assertEquals(List.of(0, 2, 2, 4), lines.stream()
            .map(BlueprintTooltipHierarchyTest::leadingSpaces)
            .toList());
    assertTrue(lines.stream().anyMatch(line -> line.contains("1 × Yellow Bed")));
}
```

Add a package-visible resident label helper test:

```java
@Test
void residentLabelsAreExplicitAndPluralized() {
    assertEquals("Resident: Hye-Sook",
            BlueprintTooltipFactory.residentLabel(List.of("Hye-Sook")).getString());
    assertEquals("Residents: Hye-Sook, Alex",
            BlueprintTooltipFactory.residentLabel(List.of("Hye-Sook", "Alex")).getString());
}
```

Add `assertTrue` to the test imports. Run `BlueprintTooltipHierarchyTest`. Expected: compilation fails because `residentLabel` does not exist; after adding only its declaration, the sole-group assertion fails because `House` is repeated and POI copy uses `- 1 x`.

- [ ] **Step 4: Add localized resident labels**

Add to `en_us.json` alongside the existing Room tooltip keys:

```json
"gui.blueprint.roomTooltip.resident": "Resident: %1$s",
"gui.blueprint.roomTooltip.residents": "Residents: %1$s",
```

Implement one ordered, de-duplicated label owner:

```java
static Component residentLabel(Collection<String> names) {
    List<String> residents = new ArrayList<>(new LinkedHashSet<>(names));
    String key = residents.size() == 1
            ? "gui.blueprint.roomTooltip.resident"
            : "gui.blueprint.roomTooltip.residents";
    return Component.translatable(key, String.join(", ", residents))
            .withStyle(ChatFormatting.GRAY);
}
```

Call it only for non-empty resident collections. Use it in both direct Room details and aggregate Room details so names are never emitted unlabeled.

- [ ] **Step 5: Suppress only the redundant sole aggregate heading**

Pass the title type into `appendAggregateRooms`:

```java
private void appendAggregateRooms(List<Component> lines,
                                  List<Building> rooms,
                                  BuildingType titleType) {
    List<RoomTypeResolver.Context> resolvedRooms = rooms.stream()
            .map(roomTypeResolver::resolve)
            .toList();
    Map<BuildingType, List<RoomTypeResolver.Context>> grouped = new LinkedHashMap<>();
    for (RoomTypeResolver.Context resolved : resolvedRooms) {
        BuildingType type = roomTypeResolver.presentationType(resolved);
        if (type == null) type = resolved.room().getBuildingType();
        grouped.computeIfAbsent(type, ignored -> new ArrayList<>()).add(resolved);
    }

    boolean suppressOnlyHeading = grouped.size() == 1 && grouped.containsKey(titleType);
    for (Map.Entry<BuildingType, List<RoomTypeResolver.Context>> entry : grouped.entrySet()) {
        int detailLevel = suppressOnlyHeading ? 1 : 3;
        if (!suppressOnlyHeading) lines.add(indent(typeLabel(entry.getKey()), 2));
        appendAggregateRoomDetails(lines, entry.getValue(), detailLevel);
    }
}

private void appendAggregateRoomDetails(List<Component> lines,
                                        List<RoomTypeResolver.Context> rooms,
                                        int detailLevel) {
    LinkedHashSet<String> residents = new LinkedHashSet<>();
    rooms.forEach(context -> village.getResidents(context.room().getId())
            .forEach(residents::add));
    if (!residents.isEmpty()) lines.add(indent(residentLabel(residents), detailLevel));

    Map<ResourceLocation, List<BlockPos>> combinedPoi = new LinkedHashMap<>();
    rooms.forEach(context -> context.ownPoi().forEach((id, positions) ->
            combinedPoi.computeIfAbsent(id, ignored -> new ArrayList<>()).addAll(positions)));
    appendPoi(lines, combinedPoi,
            Component.translatable("gui.blueprint.roomTooltip.roomPoi")
                    .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC),
            detailLevel);
}
```

Extract the existing resident/POI aggregation into `appendAggregateRoomDetails`. Update both `structureFloorTooltip` and `allFloorsTooltip` to pass `titleType`. Keep headings for multiple groups and for a sole group whose type differs from the title.

- [ ] **Step 6: Change POI item copy without changing block localization**

Implement:

```java
private static List<Component> poiLines(Map<ResourceLocation, List<BlockPos>> poi) {
    return poi.entrySet().stream()
            .filter(entry -> !entry.getValue().isEmpty())
            .<Component>map(entry -> Component.literal(entry.getValue().size() + " × ")
                    .append(blockName(entry.getKey())).withStyle(ChatFormatting.DARK_GRAY))
            .toList();
}
```

In `appendPoi`, remove the literal `"- "` prefix and indent each item exactly one level below its heading.

- [ ] **Step 7: Update existing hierarchy expectations and run focused tests**

Update indentation expectations only where the intentionally removed redundant heading changes them. Preserve the assertions that direct Room details remain one level below the root and `Also here` entries remain siblings.

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.client.gui.BlueprintScreenMapInteractionTest' --tests 'net.conczin.mca.client.gui.BlueprintTooltipHierarchyTest'
```

Expected: both classes pass.

- [ ] **Step 8: Review and commit the copy/hierarchy slice**

Validate `en_us.json` by running the tests, inspect the exact tooltip lines, stage only Task 4 files, and commit:

```powershell
git add common/src/main/java/net/conczin/mca/client/gui/BlueprintScreen.java common/src/main/java/net/conczin/mca/client/gui/BlueprintTooltipFactory.java common/src/main/resources/assets/mca/lang/en_us.json common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java common/src/test/java/net/conczin/mca/client/gui/BlueprintTooltipHierarchyTest.java
git commit -m "fix: simplify blueprint map labels"
```

---

### Task 5: Assign door and connector cells to the enclosed Room

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java:10-98, 194-265`
- Create: `common/src/test/java/net/conczin/mca/server/world/data/BuildingRoomScannerOwnerTest.java`

**Interfaces:**
- Consumes: `BuildingFloorRegion.Component.area()` and existing component bounds.
- Produces: package-visible `componentOwner(Collection<BuildingFloorRegion.Component>)` and `selectComponent(...)` so the actual owner policy is directly testable in the same package.
- Preserves: direct source-inside-component selection and at-most-one owner per connector/functional POI cell.

- [ ] **Step 1: Add failing interior-owner and tie-break tests**

Create `BuildingRoomScannerOwnerTest`:

```java
package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildingRoomScannerOwnerTest {
    @Test
    void connectorSourceChoosesLargestAdjacentInterior() {
        BuildingFloorRegion.Component outside = component(-2, 0, -1, 0);
        BuildingFloorRegion.Component interior = component(1, 0, 5, 3);
        BlockPos doorCell = new BlockPos(0, 88, 0);
        StructureFloor floor = new StructureFloor(0, 88, 94, 0, null);

        BuildingFloorRegion.Component selected = BuildingRoomScanner.selectComponent(
                doorCell, floor, Set.of(doorCell), List.of(outside, interior));

        assertEquals(interior, selected);
        assertEquals(interior, BuildingRoomScanner.componentOwner(List.of(outside, interior)));
    }

    @Test
    void equalAreaComponentsUseStableBoundsTieBreak() {
        BuildingFloorRegion.Component first = component(-4, 0, -3, 1);
        BuildingFloorRegion.Component second = component(1, 0, 2, 1);

        assertEquals(first, BuildingRoomScanner.componentOwner(List.of(second, first)));
    }

    private static BuildingFloorRegion.Component component(
            int minX, int minZ, int maxX, int maxZ) {
        int area = (maxX - minX + 1) * (maxZ - minZ + 1);
        return new BuildingFloorRegion.Component(minX, minZ, maxX, maxZ, area, List.of());
    }
}
```

- [ ] **Step 2: Run the owner test and confirm RED**

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.BuildingRoomScannerOwnerTest'
```

Expected: compilation fails because the methods are private/renamed; after exposing the current methods, the first test fails because coordinate ordering chooses `outside`.

- [ ] **Step 3: Introduce one area-first owner comparator**

Add:

```java
private static final Comparator<BuildingFloorRegion.Component> COMPONENT_OWNER_ORDER =
        Comparator.comparingInt(BuildingFloorRegion.Component::area).reversed()
                .thenComparingInt(BuildingFloorRegion.Component::minX)
                .thenComparingInt(BuildingFloorRegion.Component::minZ)
                .thenComparingInt(BuildingFloorRegion.Component::maxX)
                .thenComparingInt(BuildingFloorRegion.Component::maxZ);

static BuildingFloorRegion.Component componentOwner(
        Collection<BuildingFloorRegion.Component> adjacent) {
    return adjacent.stream().min(COMPONENT_OWNER_ORDER).orElse(null);
}
```

Replace all `connectorOwner(...)` uses in connector materialization and functional POI attachment with `componentOwner(...)`.

- [ ] **Step 4: Make connector-source selection use the same owner**

Keep direct component selection first. Then implement:

```java
BlockPos floorCell = new BlockPos(source.getX(), floor.anchorY(), source.getZ());
List<BuildingFloorRegion.Component> adjacent = adjacentComponents(floorCell, components);
if (connectorCells.contains(floorCell)) return componentOwner(adjacent);
return adjacent.stream()
        .min(Comparator.comparingInt(BuildingFloorRegion.Component::minX)
                .thenComparingInt(BuildingFloorRegion.Component::minZ))
        .orElse(null);
```

Give `selectComponent` package visibility for the focused test; do not broaden it beyond the package.

- [ ] **Step 5: Run focused and floor-system tests**

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.BuildingRoomScannerOwnerTest' --tests 'net.conczin.mca.server.world.data.VillageFloorSystemTest'
```

Expected: both classes pass.

- [ ] **Step 6: Review and commit the connector-owner slice**

Confirm all three owner sites—source selection, connector footprint assignment, functional POI attachment—delegate to `componentOwner`. Stage exact files and commit:

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java common/src/test/java/net/conczin/mca/server/world/data/BuildingRoomScannerOwnerTest.java
git commit -m "fix: assign connector cells to interior rooms"
```

---

### Task 6: Stop planned attachment scans at persisted storeys

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java:22-178, 305-314, 448-471`
- Create: `common/src/test/java/net/conczin/mca/server/world/data/StructureScannerAttachmentBoundaryTest.java`
- Verify unchanged except signature wiring if necessary: `common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java:241-286`

**Interfaces:**
- Consumes: planned scan seed Y, all existing `StructureFloor.region().cells()`, connector transitions, and `BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE`.
- Produces: `StructureScanner.PersistedFloorBoundary` with `forAttachment(...)`, `blocks(BlockPos)`, and `addPermittedAssociated(...)`.
- Preserves: empty boundary for new/reported/rescan paths, ordinary same-storey traversal, `ignoredStructureId`, `validAttachment`, and final candidate intersection.

- [ ] **Step 1: Add failing boundary behavior tests**

Create `StructureScannerAttachmentBoundaryTest`:

```java
package net.conczin.mca.server.world.data;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureScannerAttachmentBoundaryTest {
    @Test
    void attachmentBoundaryBlocksPersistedOtherStoreyButNotSameBand() {
        Structure persisted = structureWithFloor(3, 77, Set.of(
                new BlockPos(0, 77, 0), new BlockPos(1, 77, 0),
                new BlockPos(0, 77, 1), new BlockPos(1, 77, 1)));
        StructureScanner.PersistedFloorBoundary boundary =
                StructureScanner.PersistedFloorBoundary.forAttachment(74, List.of(persisted));

        assertTrue(boundary.blocks(new BlockPos(0, 77, 0)));
        assertFalse(boundary.blocks(new BlockPos(0, 76, 0)));
        assertFalse(boundary.blocks(new BlockPos(9, 77, 9)));
    }

    @Test
    void associatedCellsKeepSameStoreyOverlapEvidence() {
        Structure persisted = structureWithFloor(3, 74, Set.of(
                new BlockPos(0, 74, 0), new BlockPos(1, 74, 0),
                new BlockPos(0, 74, 1), new BlockPos(1, 74, 1)));
        StructureScanner.PersistedFloorBoundary boundary =
                StructureScanner.PersistedFloorBoundary.forAttachment(74, List.of(persisted));
        Set<BuildingFloorRegionDetector.FloorCell> discovered = new HashSet<>();
        BuildingFloorRegionDetector.FloorCell overlap =
                new BuildingFloorRegionDetector.FloorCell(0, 74, 0);

        boundary.addPermittedAssociated(discovered, Set.of(overlap));

        assertTrue(discovered.contains(overlap));
    }

    @Test
    void associatedCellsDoNotReintroducePersistedOtherStorey() {
        Structure persisted = structureWithFloor(3, 77, Set.of(
                new BlockPos(0, 77, 0), new BlockPos(1, 77, 0),
                new BlockPos(0, 77, 1), new BlockPos(1, 77, 1)));
        StructureScanner.PersistedFloorBoundary boundary =
                StructureScanner.PersistedFloorBoundary.forAttachment(74, List.of(persisted));
        Set<BuildingFloorRegionDetector.FloorCell> discovered = new HashSet<>();
        BuildingFloorRegionDetector.FloorCell targetFloor =
                new BuildingFloorRegionDetector.FloorCell(0, 77, 0);

        boundary.addPermittedAssociated(discovered, Set.of(targetFloor));

        assertFalse(discovered.contains(targetFloor));
    }

    private static Structure structureWithFloor(int id, int y, Set<BlockPos> cells) {
        BuildingFloorRegion region = BuildingFloorRegion.fromFootprint(y, cells);
        StructureFloor floor = new StructureFloor(0, y, y + 5, 0, region);
        return new Structure(id, cells.iterator().next(),
                new BlockPos(0, y, 0), new BlockPos(1, y + 4, 1), List.of(floor));
    }
}
```

- [ ] **Step 2: Run the boundary test and confirm RED**

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.StructureScannerAttachmentBoundaryTest'
```

Expected: compilation fails because `PersistedFloorBoundary` does not exist.

- [ ] **Step 3: Implement the typed persisted-storey boundary**

Add this nested package-visible record to `StructureScanner`:

```java
record PersistedFloorBoundary(
        int seedY,
        Set<BuildingFloorRegionDetector.FloorCell> persistedFloorCells) {
    private static PersistedFloorBoundary none() {
        return new PersistedFloorBoundary(0, Set.of());
    }

    static PersistedFloorBoundary forAttachment(
            int seedY, Collection<Structure> existing) {
        LinkedHashSet<BuildingFloorRegionDetector.FloorCell> cells = new LinkedHashSet<>();
        for (Structure structure : existing) {
            for (StructureFloor floor : structure.getFloors()) {
                if (floor.region() == null) continue;
                for (BlockPos cell : floor.region().cells()) {
                    cells.add(new BuildingFloorRegionDetector.FloorCell(
                            cell.getX(), cell.getY(), cell.getZ()));
                }
            }
        }
        return new PersistedFloorBoundary(seedY, Set.copyOf(cells));
    }

    boolean blocks(BlockPos destination) {
        return isOtherStorey(destination.getY()) && persistedFloorCells.contains(
                new BuildingFloorRegionDetector.FloorCell(
                        destination.getX(), destination.getY(), destination.getZ()));
    }

    void addPermittedAssociated(
            Set<BuildingFloorRegionDetector.FloorCell> discovered,
            Collection<BuildingFloorRegionDetector.FloorCell> associated) {
        for (BuildingFloorRegionDetector.FloorCell cell : associated) {
            if (!isOtherStorey(cell.y()) || !persistedFloorCells.contains(cell)) {
                discovered.add(cell);
            }
        }
    }

    private boolean isOtherStorey(int y) {
        return Math.abs(y - seedY) > BuildingFloorRegionDetector.FLOOR_CLUSTER_TOLERANCE;
    }
}
```

The floor-cluster tolerance is essential: claimed cells in the candidate's own storey stay observable so the final overlap check can reject them.

- [ ] **Step 4: Wire the boundary only into planned scans**

Pass `PersistedFloorBoundary.none()` from `scanNewStructure`, `scanReportedStructure`, and `rescanStructure`. In `scanPlannedStructure`, build it from `plan.scanSeed().getY()` and `existing`.

Extend the private `scan(...)` signature with `PersistedFloorBoundary boundary`; keep `ignoredStructureId` as a separate identity concern.

- [ ] **Step 5: Stop exits from vertical connectors into owned other-storey floor cells**

Apply `boundary.blocks(destination)` at each connector-owned transition:

- In the horizontal-neighbor loop, when `currentState` is a vertical connector, do not enqueue a claimed walkable destination from another storey.
- In `enqueueConnectorHandoffs`, check the boundary before enqueuing a walkable candidate reached from a vertical connector.
- In the explicit UP/DOWN connector loop, check the boundary before enqueuing a walkable destination.

Pass the boundary into `enqueueConnectorHandoffs`; do not add a boolean scan-mode parameter. Leave `enqueueStep` and ordinary nonconnector horizontal traversal unchanged.

The horizontal-loop condition should have this form:

```java
boolean exitsVerticalConnector = StructureConnector.isVertical(currentState);
boolean entersOwnedStorey = exitsVerticalConnector && boundary.blocks(next);
if (!entersOwnedStorey && (StructureConnector.isConnector(nextState)
        || isWalkableAnchor(world, next, nextState, roof)
        || nextFloorObstacle)) {
    enqueueTraversal(seed, next, visited, queue, maxRadius);
}
```

- [ ] **Step 6: Filter only connector-derived other-storey cells**

Replace the unconditional associated-cell merge:

```java
Set<BuildingFloorRegionDetector.FloorCell> associated =
        StructureConnector.associatedFloorCells(world, connectorCells, floorCells);
boundary.addPermittedAssociated(floorCells, associated);
```

Do not remove cells already present in `floorCells`; those are direct traversal evidence and must still reach `candidate.intersects(existing)`.

- [ ] **Step 7: Run boundary and existing floor-system tests**

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.StructureScannerAttachmentBoundaryTest' --tests 'net.conczin.mca.server.world.data.VillageFloorSystemTest'
```

Expected: both classes pass.

- [ ] **Step 8: Audit the final overlap and commit boundary**

Confirm these production statements are still present and behaviorally unchanged:

```java
if (other.getId() != ignoredStructureId && candidate.intersects(other)) {
    return Result.failure(Building.validationResult.OVERLAP, source);
}
```

and in `VillageManager.analyzeAttachedRoom`:

```java
candidate.setLogicalBuildingId(plan.targetBuildingId());
return scanRoom(village, candidate, plan.scanSeed(), -1)
        .withSource(source)
        .withPendingStructure(candidate);
```

Stage only Task 6 files and commit:

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java common/src/test/java/net/conczin/mca/server/world/data/StructureScannerAttachmentBoundaryTest.java
git commit -m "fix: stop attachment scans at persisted storeys"
```

---

### Task 7: Full verification and exact runtime reproductions

**Files:**
- Verify all files changed in Tasks 1-6.
- Do not modify or stage `common/logs/`.
- Modify production code only if a runtime failure produces a traced, in-scope root cause; repeat the affected task's RED -> GREEN cycle before continuing.

**Interfaces:**
- Consumes: all prior task deliverables.
- Produces: fresh unit/build evidence plus direct evidence for the original visual, basement, and door-cell symptoms.

- [ ] **Step 1: Run the complete focused regression set**

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.client.gui.BlueprintScreenMapInteractionTest' --tests 'net.conczin.mca.client.gui.BlueprintMapRendererTest' --tests 'net.conczin.mca.client.gui.BlueprintMapGeometryTest' --tests 'net.conczin.mca.client.gui.BlueprintTooltipHierarchyTest' --tests 'net.conczin.mca.server.world.data.BuildingRoomScannerOwnerTest' --tests 'net.conczin.mca.server.world.data.StructureScannerAttachmentBoundaryTest' --tests 'net.conczin.mca.server.world.data.VillageFloorSystemTest'
```

Expected: Gradle exits `0`; every listed class passes with zero failed tests.

- [ ] **Step 2: Run the full common test and multiloader compile gates**

Run:

```powershell
.\gradlew.bat :common:test :fabric:compileJava :neoforge:compileJava
```

Expected: `BUILD SUCCESSFUL` with no failed tests or Java compilation errors.

- [ ] **Step 3: Inspect the complete branch diff and worktree ownership**

Run:

```powershell
git branch --show-current
git status --short
git diff --check
git diff origin/feature/1.21.1-floor-clean-squash...HEAD -- common/src/main/java common/src/test/java common/src/main/resources/assets/mca/lang/en_us.json
```

Expected branch: `feature/1.21.1-floor-clean-squash`. Expected unrelated entry: the pre-existing untracked `common/logs/`. Confirm there are no conflict markers, debug prints, source-shape-only tests, or unrelated refactors.

- [ ] **Step 4: Verify smooth pan and coherent zoom in the client**

Launch the normal development client:

```powershell
.\gradlew.bat :neoforge:runClient
```

In the Blueprint map:

1. Select scales `0.5`, `1`, `1.37` via wheel, `2.36` via wheel, and `4`.
2. Drag slowly by less than one GUI pixel per rendered frame; terrain, Room fills, shell, borders, icons, and marker must move continuously with no one-pixel pauses.
3. Place the cursor over a recognizable building corner and zoom both directions; that world corner must remain under the pointer.
4. Pan and zoom around the reported building; Room/building geometry must not shift relative to terrain.
5. Confirm outline width remains one GUI pixel and terrain remains nearest-filtered rather than blurred.

Record the exact scales and whether each layer stayed registered. Do not mark this gate complete from a still screenshot alone; observe motion.

- [ ] **Step 5: Verify outline, scale, and tooltip acceptance**

Using an irregular building with multiple Rooms/floors:

1. Compare All Floors, Ground Floor, basement, and upper-floor filters.
2. Confirm the building boundary remains the same union of all registered Room cells plus exactly one cell of padding.
3. Confirm there are no physical-floor entrance spikes or heuristic notches.
4. Confirm scale labels read `2:1`, `0.5:1`, and no label contains unnecessary `.00`.
5. Hover the reported House and confirm the sole-group tooltip reads once as `House`, then `Ground Floor · Main Room`, labeled resident text, and `1 × Yellow Bed` under the POI heading.
6. Hover a building/floor with genuinely different Room types and confirm their subheadings remain.

- [ ] **Step 6: Reproduce the basement and upper-floor attachment flow**

With verbose building diagnostics enabled, recreate the reported vertical attachment:

```text
new basement floor anchor: 74
persisted target ground floor anchor: 77
vertical connector joins the storeys
```

Run analysis and commit for `ADD_BASEMENT`. Required evidence:

- analysis returns `SUCCESS`, not `OVERLAP`;
- pending candidate contains the new `74` storey and does not duplicate the persisted `77` floor;
- commit creates a new Structure/Room under logical building `3`;
- reload preserves both Structures, floor numbers, and Main Room identity.

Mirror the scenario above the target for `ADD_FLOOR`. Also attempt a real same-storey intersecting addition and confirm it still returns `OVERLAP`.

- [ ] **Step 7: Reproduce the inside-door Room flow**

Use the reported layout where the door floor cell is adjacent to a large enclosed Room and a one-to-three-cell exterior apron. Run `ADD_ROOM` from the inside door cell. Required evidence:

- analysis returns `SUCCESS`, not `TOO_SMALL`;
- the selected footprint is the enclosed component;
- the door floor cell belongs to that Room;
- the exterior apron is not selected;
- a second Room scan cannot claim the same door cell;
- reload preserves the Room footprint.

- [ ] **Step 8: Run fresh final gates after any runtime-driven correction**

If Steps 4-7 required a code change, rerun Steps 1-3 in full after the final edit. A previous green run is stale after production changes.

When all gates are green, run:

```powershell
git status --short
git log -7 --oneline
```

Report the exact commits, focused/full Gradle results, runtime scenarios exercised, and any intentionally uncommitted documentation. Do not claim the bugs fixed if either runtime scanner reproduction or visual motion check was skipped.
