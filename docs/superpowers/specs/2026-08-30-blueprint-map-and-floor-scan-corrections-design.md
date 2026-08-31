# Blueprint Map and Floor-Scan Corrections

Date: 2026-08-30

Status: Map design implemented/in progress; scanner sections 5-6 superseded

> **Scanner architecture revision (2026-08-30):** Sections 5-6 below are retained as historical
> context only and must not drive further implementation. The approved simplification direction
> is documented in `2026-08-30-floor-scanner-simplification-design.md`. That revision separates
> physical 3D discovery, persisted Structure matching, Room topology, connector floor-cell
> ownership, and POI evidence; it also defines wall POIs, real cave surfaces, generic door
> behavior, and fresh-world `Update Room` semantics.

## Purpose

Correct the Blueprint map's continuous pan/zoom presentation and several floor-system edge cases without weakening the physical Structure/registered Room separation introduced by the floor-system work.

This design covers every reported symptom:

1. Dragging the map feels stepped, laggy, and visually uncomfortable.
2. Room/building graphics can shift relative to terrain at different zoom levels.
3. The map scale label shows unnecessary trailing zeroes such as `2.00:1`.
4. Structure tooltips repeat the building type and leave resident names unlabeled.
5. Building outlines are derived from complicated physical geometry instead of being a simple one-cell padding around all registered Rooms.
6. Adding a basement or upper floor can report `OVERLAP` after a diagnostic rescan of the same world succeeds.
7. A door/connector floor cell can be assigned to the small exterior component instead of the enclosed Room, producing `TOO_SMALL` and omitting the inside door cell from the Room footprint.

## Baseline and origin comparison

The comparison baseline is the refreshed `origin/feature/1.21.1-floor-clean-squash` at `80bfe7d0e`. The reviewed worktree HEAD is `fd30cb458`.

| Concern | Origin behavior | Current branch behavior | Conclusion |
| --- | --- | --- | --- |
| Map camera | `Math.rint` pixel-locking with fixed scale/center behavior | Same pixel-locking plus continuous drag and wheel zoom | Continuous interaction exposed origin's camera quantization as visible stutter. |
| Terrain and structural layers | Terrain/icons use a map transform; exact Room fills/outlines are projected and rounded separately | Same split ownership | Independent rasterization is the source of zoom-dependent visual disagreement. |
| Building shell | Union of physical non-basement Structure floors, entrance-protrusion filtering, one-cell expansion | Same | This complexity is inherited from origin and does not match the requested Room-owned outline. |
| Attachment scan | Planned scan may traverse a vertical connector into the persisted target floor, then reject the rediscovered target as overlap | Same | This is a latent origin bug, not a regression caused by the current simplification. |
| Door component ownership | Coordinate-order tie-breaking chooses the connector owner | Same | This is also a latent origin bug. |
| Tooltip hierarchy | Simpler baseline presentation | Current aggregate hierarchy can print the title type twice and prints resident names without labels | The hierarchy needs a compact, non-repeating presentation rule. |

Origin remains useful as behavioral evidence, but it must not be copied blindly where the reported defect is already present there.

## Design principles

- One owner per concern: `BlueprintMapViewport` owns camera conversion; `BlueprintMapGeometry` owns immutable map shapes; `StructureScanner` owns physical discovery; `BuildingRoomScanner` owns Room partitioning.
- Keep exact camera state in doubles. Quantization belongs only at an unavoidable final raster or clip boundary, never in persistent camera state.
- Keep terrain, Rooms, building shells, icons, hit testing, and the player marker on one mathematical transform.
- Preserve constant one-GUI-pixel outlines without forcing the camera onto integer pixels.
- Treat persisted Structure floors as owned geometry during vertical attachment discovery, while retaining the final overlap check for genuine overlaps.
- Prefer existing footprint, span, edge, cache, and rendering primitives over a new framebuffer or parallel map renderer.
- Keep analysis detached and commits atomic: a successful scan may produce a pending Structure/Room, but it must not mutate Village state until the existing commit boundary accepts it.

## 1. Smooth, coherent map camera

### Root cause

`BlueprintMapViewport.create` currently rounds the screen-space map origin with `Math.rint`, then derives a replacement map center from that rounded origin. A drag may update `mapCenterX/Z` by a fraction, but the viewport discards that movement until it crosses a whole GUI pixel. This is the visible stepping.

The rounding was originally compensating for a second problem: terrain and icons render under a `PoseStack` map transform, while Room fills, Room outlines, and building shells independently call `Math.round(viewport.screenX/screenY(...))`. At fractional zoom, those paths can choose different raster boundaries. Removing `Math.rint` without unifying the layer transform would restore smooth camera state but expose the old distortion/shift.

### Required behavior

- Dragging by a fractional GUI pixel moves the complete map by that fractional amount in the same frame.
- Terrain, Room fills, building shell, Room/building outlines, icons, hover targets, and the player marker remain registered to the same world coordinates throughout pan and zoom.
- Nearest-neighbor terrain sampling remains enabled.
- Room and building outlines remain one GUI pixel wide at every supported zoom.
- The map remains clipped to the current integer GUI scissor rectangle.

### Camera state

`BlueprintMapViewport.create` must preserve `requestedMapCenterX` and `requestedMapCenterZ` exactly. Remove `mapOriginX`, `mapOriginZ`, and both `Math.rint` calls. The viewport's existing forward and inverse formulas become the canonical projection:

```text
screenX = centerX + (worldX - mapCenterX) * scale
screenY = centerY + (worldZ - mapCenterZ) * scale

worldX = mapCenterX + (screenX - centerX) / scale
worldZ = mapCenterZ + (screenY - centerY) / scale
```

The scissor bounds can remain integers because they describe the fixed widget boundary, not camera state.

### Render ownership

Render all map-space content inside one pushed map pose:

```text
translate(screenCenter)
scale(mapScale)
translate(-mapCenter)
```

Within that pose:

- Render the cached terrain texture at its world-space bounds.
- Render Room row spans directly in map coordinates rather than projecting and rounding each span.
- Render the building shell row spans in the same way.
- Anchor building icons in map coordinates; retain the existing inverse scale for icons that must stay a fixed GUI size.
- Render Room and building boundary edges as colored quads with a map-space thickness of `1 / scale`, producing a constant one-GUI-pixel stroke after transformation.

The outline batch should be renderer-local and use the project's existing Blaze3D pattern (`BufferBuilder`, `DefaultVertexFormat.POSITION_COLOR`, and the active pose matrix). Flush pending `GuiGraphics` work at the explicit ordering boundary, then emit one ordered outline batch. Do not add an off-screen framebuffer, a second camera, or one buffer upload per edge.

This approach preserves the reason screen-space borders existed—constant visual width—without pixel-locking the camera. Official rendering guidance describes the pose matrix as the owner of vertex translation/scaling and `POSITION_COLOR` as the standard colored-vertex format; the local Minecraft 1.21.1 classes and existing `HorizontalGradientWidget` confirm those APIs in this exact worktree.

### Hover and marker projection

- Cell hover continues to use the exact inverse viewport projection and `floor` to select the world cell.
- Edge hover must test against the same projected one-pixel edge rectangle (or the equivalent `0.5 / scale` map-space tolerance). Remove equality tests against independently rounded edge coordinates.
- Keep the player marker a fixed GUI size, but position its center from the exact viewport projection. A final local pose translation may carry the fractional screen offset; do not round the world anchor before clamping.

### Pointer-anchored zoom

Wheel zoom should preserve the world point under the mouse. Before changing scale, calculate the cursor's world position with the old viewport. After selecting the new scale, set the map center so that world position projects back to the same cursor coordinate:

```text
cursorWorldX = oldCenterX + (mouseX - screenCenterX) / oldScale
newCenterX = cursorWorldX - (mouseX - screenCenterX) / newScale
```

Apply the same formula to Z/Y. This removes the impression that the building jumps away from the user's focus when zooming.

## 2. Scale label without `.00`

Format scale with zero required fractional digits and at most two optional fractional digits:

- `2.0` -> `2:1`
- `0.5` -> `0.5:1`
- `1.37` -> `1.37:1`
- `2.356` -> `2.36:1`

Use a per-call `DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT))` (or an equivalently isolated formatter), then append `:1`. Java's `#` pattern digit is optional, unlike `0`, and Oracle documents that `DecimalFormat` instances are not generally synchronized; do not introduce one shared mutable static formatter.

## 3. Building outline: one-cell padding around all Rooms

### Current problem

`BlueprintMapGeometry.buildStructureLayers` currently:

1. unions physical non-basement `StructureFloor.region` cells,
2. removes cells that look like one-neighbor entrance protrusions,
3. expands that filtered physical footprint,
4. subtracts visible Room cells to form the shell.

That makes the displayed building boundary depend on physical scanner details and a heuristic entrance filter, which creates the reported distorted/overcomplicated outline.

### New ownership rule

For each logical building:

1. Union the exact footprint cells of every complete registered Room in that logical building, across all floors.
2. Compute `outlineCells = BlueprintMapFootprint.expand(allRoomCells, 1)`.
3. Compute `shellCells = outlineCells - allRoomCells`.
4. Derive spans and outer edges through the existing `BlueprintMapFootprint.shape` pipeline.

The building outline is therefore stable while switching floor filters and is always exactly one cell outside the union of all Rooms. Individual Room layers remain floor-filtered. If a logical building has no registered Room footprint, it has no structural building outline. External/grouped legacy POIs remain on their existing path.

Remove `outlineBaseWithoutEntranceProtrusions` and its cardinal-neighbor heuristic. Cache the all-Room building shapes once per `BlueprintMapGeometry` instance; do not rebuild them every frame or once per edge.

## 4. Compact, unambiguous tooltips

### Single-type structure/floor tooltip

When the only aggregate Room type matches the building title, do not repeat the type as a nested heading. Label residents and use the multiplication sign for counts:

```text
House
  Ground Floor · Main Room
  Resident: Hye-Sook
  Blocks in this room:
    1 × Yellow Bed
```

Use `Residents:` for more than one unique resident. Add localized singular/plural resident keys and include the colon in the localized heading. Preserve insertion order while de-duplicating names.

### Multiple Room types

If a floor genuinely contains multiple presentation types, retain type subheadings because they disambiguate which residents and POIs belong to which Room group. The title-matching heading is suppressed only when it is the sole group. Preserve the existing `Also here` behavior for vertically overlapping hover targets.

The POI heading remains `Blocks in this room`, but items become `count × localized block name` rather than `- count x localized block name`.

## 5. Basement/upper-floor false `OVERLAP`

### Evidence and root cause

The supplied trace shows:

- persisted floor: anchor `77`, area `10`;
- fresh diagnostic rescan while ignoring the inspected Structure: anchors `74` and `77`, success;
- real `ADD_BASEMENT` analysis: `OVERLAP`.

`VillageManager.analyzeAttachedRoom` calls `StructureScanner.scanPlannedStructure` with every persisted Structure and no ignored ID. The planned scan starts in the new basement, follows the ladder/trapdoor handoff into the already-persisted ground floor, and creates a candidate containing both floors. The final candidate-vs-existing intersection correctly sees the rediscovered ground floor and rejects it. Diagnostics succeed only because their rescan ignores that Structure ID; they are not exercising the same ownership boundary.

### Attachment-only vertical ownership boundary

Do not ignore the target Structure and do not commit a rescan that duplicates its floors. Keep attachment analysis producing a detached new pending Structure, as the current commit path expects.

For `scanPlannedStructure` only:

1. Build one immutable set of all persisted canonical floor-anchor cells from the supplied Structures.
2. Pass that set as a named scan constraint, not a boolean mode flag.
3. During connector-mediated transitions that change Y, do not enqueue a destination owned by that persisted-floor set.
4. When `StructureConnector.associatedFloorCells` derives extra cells from scanned connectors, do not add an associated cell already owned by a persisted floor unless it was independently discovered through ordinary same-level traversal.
5. Leave ordinary horizontal traversal unchanged.
6. Leave the final `candidate.intersects(existing)` validation unchanged.

This is deliberately narrower than treating all persisted cells as generic traversal walls. A real same-height overlap is still discovered by ordinary traversal and rejected by the final overlap validation. Only the vertical connector handoff that would absorb an existing storey is stopped at the ownership boundary.

After the candidate contains only the new storey, the existing `validAttachment` logic remains responsible for checking target identity, nearest/unique attachment, gap, and prospective floor number. Commit continues to allocate a new Structure and Room under the target logical building only after successful detached analysis.

The same rule applies symmetrically to `ADD_BASEMENT` and `ADD_FLOOR`.

## 6. Interior door cell assigned to the wrong Room

### Evidence and root cause

The supplied trace shows a stable physical Structure floor (`@88..94`, area `40`) followed by `ADD_ROOM -> TOO_SMALL`. The failure is therefore in Room partition/materialization, not physical floor discovery.

`BuildingRoomScanner` removes connector cells before splitting ordinary components. When the source is the door cell and both an enclosed interior and a small exterior apron are adjacent, both `selectComponent` and `connectorOwner` currently prefer the component with the lowest coordinates. That can select the tiny exterior component; its footprint then fails `MIN_INTERIOR_AREA`, and the door cell is not owned by the intended interior Room.

### Shared owner rule

Create one deterministic adjacent-component owner comparator and use it everywhere connector-like cells need an owner:

1. largest `Component.area()` first;
2. then `minX`, `minZ`, `maxX`, `maxZ` for deterministic equal-area ties.

Apply the same helper to:

- source-on-connector component selection;
- connector floor-cell assignment during materialization;
- functional POI component attachment, which currently delegates to the same coordinate-based helper.

A source directly inside an ordinary component still selects that component immediately. Connector cells continue to have at most one Room owner, so Room footprints remain disjoint. In the reported layout, the enclosed interior wins, includes the inside door floor cell, and is evaluated against `MIN_INTERIOR_AREA` as the intended Room.

## File-level implementation scope

Expected production files:

- `common/src/main/java/net/conczin/mca/client/gui/BlueprintMapViewport.java`
- `common/src/main/java/net/conczin/mca/client/gui/BlueprintMapRenderer.java`
- `common/src/main/java/net/conczin/mca/client/gui/BlueprintMapGeometry.java`
- `common/src/main/java/net/conczin/mca/client/gui/BlueprintScreen.java`
- `common/src/main/java/net/conczin/mca/client/gui/BlueprintTooltipFactory.java`
- `common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java`
- `common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java`
- language JSON files needed for resident labels

`BlueprintTerrainRenderer` should require no new camera state; it remains the cached terrain sampler/texture owner and renders through the caller's canonical map pose. `VillageManager` should remain the attachment orchestration and detached commit owner unless a small signature change is needed to pass an already-built scan constraint.

Expected tests:

- extend `BlueprintScreenMapInteractionTest`;
- add focused viewport/projection tests if keeping them in the screen test would obscure intent;
- extend map-footprint/geometry tests for the all-Room outline;
- update `BlueprintTooltipHierarchyTest`;
- add focused `StructureScanner` attachment-boundary tests;
- add focused `BuildingRoomScanner` connector-owner tests;
- extend `VillageFloorSystemTest` only for orchestration-level assertions not already proved by scanner tests.

## Verification plan

### Automated RED -> GREEN cases

Map/camera:

- A requested center with fractional world coordinates is preserved exactly by viewport creation.
- A fractional drag changes every projected layer anchor by the corresponding fractional screen delta.
- World-to-screen and screen-to-world/cell calculations agree at minimum, maximum, and intermediate scales.
- Pointer-anchored zoom leaves the cursor's world coordinate invariant within a small floating-point tolerance.
- Constant-width edge geometry is one GUI pixel at every scale preset and wheel-generated intermediate scale.
- Scale formatting expects `2:1`, `0.5:1`, `1.37:1`, and rounded two-decimal output.

Geometry/tooltips:

- A concave/L-shaped union of multiple Room footprints produces exactly the union expanded by one cell, including corners, with no entrance filtering.
- Rooms on different floors contribute to one stable logical-building outline.
- A single title-matching Room group prints the building type once.
- Resident labels use singular/plural correctly and POIs use `×`.
- Multiple distinct Room types retain disambiguating subheadings.

Scanning:

- Reproduce the reported basement shape: new floor anchor `74`, persisted target anchor `77`, connected by a vertical connector. Planned scan succeeds and the candidate contains only the `74` floor.
- Mirror that case for an upper-floor attachment.
- A same-height candidate that truly intersects persisted floor cells still returns `OVERLAP`.
- An attachment nearest to two logical buildings remains rejected as ambiguous.
- A door cell adjacent to a large enclosed component and a one-to-three-cell exterior component selects the enclosed component, includes the door floor cell, and succeeds.
- Equal-area connector sides select deterministically.
- Materialized Room footprints remain disjoint.

### Runtime acceptance

A green Gradle build alone is not sufficient for the visual and gameplay reports. Re-run the exact in-game scenarios:

1. Slowly drag at several non-integer zoom levels and confirm there is no one-pixel stepping.
2. Zoom with the cursor over a recognizable building corner; that corner must remain under the cursor and all Room/building layers must stay fixed to terrain.
3. Inspect an irregular multi-Room building in All Floors and individual floor views; the logical-building outline must be the stable one-cell padding around the union of all registered Rooms.
4. Confirm the scale label and compact tooltip presentation in game.
5. Re-run the supplied basement and inside-door layouts with verbose diagnostics, then commit each successful operation and reload the world to verify durable Structure/Room identity.
6. Run the focused JUnit lane, then the repository's broader relevant test/check lane.

## Alternatives rejected

- **Keep `Math.rint` and interpolate visually:** preserves two camera states and can make hit testing disagree with rendering.
- **Remove `Math.rint` only:** smooths camera state but leaves independently rounded structural layers, recreating the distortion that motivated the rounding.
- **Render the entire map to an off-screen framebuffer:** adds lifecycle, scaling, memory, and filtering complexity that the existing pose/geometry pipeline does not need.
- **Scale integer GUI outlines with the map:** makes outline width vary with zoom and produces the reported chunky/distorted appearance.
- **Ignore the target Structure during attachment scan:** allows a candidate to duplicate the target's persisted floor and conflicts with the current new-pending-Structure commit model.
- **Clip every persisted floor cell out of planned scans:** can hide genuine same-height overlaps; the boundary must apply only to vertical connector ownership transfer.
- **Choose the connector side nearest the source or by coordinate alone:** the source is the connector itself and coordinate ordering has no semantic relationship to the enclosed Room.

## External references

- [NeoForged 1.21.1 screen rendering documentation](https://docs.neoforged.net/docs/1.21.1/gui/screens/) documents scoped `PoseStack` translation with push/pop for GUI rendering.
- [Fabric rendering concepts](https://docs.fabricmc.net/develop/rendering/basic-concepts) documents matrix-owned vertex transforms, `BufferBuilder`, and `POSITION_COLOR`; the exact 1.21.1 API availability was also verified from this worktree's mapped Minecraft jar.
- [Java 21 `DecimalFormat`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/text/DecimalFormat.html) defines optional `#` fraction digits, rounding, locale symbols, and its synchronization constraint.
