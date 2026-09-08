# Exact-Cell Floor Scanner Simplification

Date: 2026-09-08

Status: Proposed simplification of the scanner built on top of the exact 3D
Floor/Room model from `2026-09-04-floor-room-3d-geometry-architecture-spec.md`.

This document does **not** replace the exact-geometry model. It simplifies the
remaining world-scanning logic that still reconstructs semantic floors after
discovery using height slices, slice-area thresholds, and global height bands.

The historical fixes `fix: keep staircase scans within one floor` and
`fix: preserve stacked floor cells during scanning` remain regression contracts,
not implementation constraints. Their original mechanisms were already adapted
by the exact-geometry rewrite.

## Problem

The core model is now simpler than the scanner feeding it:

- `FloorGeometry` already stores exact 3D cells and permits multiple cells in
  the same X/Z column;
- `RoomPartitioner` already treats those exact cells as graph nodes and checks
  every step-compatible candidate in an adjacent column;
- Room ownership is already exact-cell ownership;
- X/Z projection is already derived presentation data.

`SelectedFloorScanner`, however, still discovers a broad connected surface and
then reconstructs storeys through a second model:

```text
exact discovered cells
    -> group by feet Y
    -> connected components per Y slice
    -> "meaningful" slice if area >= 4
    -> HeightBand groups using BAND_TOLERANCE
    -> reassign sparse components to a lower band
    -> rebuild one FloorGeometry per band
```

That is the remaining architectural mismatch. Exact cells are discovered first,
but their semantic ownership is then inferred by flattening them back into Y
slices.

The scanner should instead decide whether a newly reached exact cell belongs to
the selected storey while traversing the exact-cell graph. It should not flood a
whole building and then invent storeys afterward.

## Design principle

The canonical mental model is:

> A Room is a connected set of exact 3D floor cells inside one selected semantic
> Floor, after Room-boundary connectors are applied.

Everything else is either discovery input, semantic metadata, or a derived
view.

```text
Minecraft world
    -> resolve exact source cell
    -> traverse exact cells for the selected storey
    -> FloorGeometry
    -> RoomPartitioner
    -> exact-cell Rooms
    -> POI evidence / projection / persistence views
```

There is no second spatial truth between world discovery and `FloorGeometry`.

## Goals

1. Make one selected-storey graph traversal the source of fresh Floor geometry.
2. Remove post-hoc Y-slice / area-based semantic reconstruction.
3. Preserve exact stacked cells, stairs, slabs, uneven terrain, and caves.
4. Preserve the distinction between semantic storeys; a staircase must not make
   two storeys one Floor merely because a player can walk between them.
5. Keep Room partitioning simple: connected exact cells minus Room boundaries.
6. Keep doors, gates, ladders, trapdoors, walls, furniture, and POIs from
   manufacturing alternate floor geometry.
7. Use Minecraft movement/collision semantics at the world adapter boundary,
   not block-class special cases in topology.
8. Keep persistence and Room identity rules unchanged unless implementation
   proves a change is required.

## Non-goals

- Do not collapse the concept of a semantic Floor into one whole-building
  walkable component.
- Do not classify `StairBlock`, slab classes, cave materials, or specific Y
  values as architectural categories.
- Do not reintroduce one-cell-per-X/Z assumptions.
- Do not make doors or wall blocks part of Floor geometry merely to make POI or
  Blueprint behavior convenient.
- Do not redesign `RoomIdentityPolicy`, Main Room selection, inheritance,
  logical-building identity, or `RoomDFU` in this change.
- Do not add another persisted or long-lived scan DTO beside `FloorGeometry`.
- Do not use unrestricted vanilla pathfinding as the Floor definition. A
  walkable route can legitimately cross semantic storeys.

## Canonical invariants

### Exact cells are spatial truth

`FloorGeometry.Cell.feet` remains the physical identity of a floor cell.

```text
(x, y, z) != (x, y + 3, z)
```

Both may belong to one semantic Floor, and both may coexist in the same X/Z
column.

`surfaceY` describes the physical walkable surface. `ceilingY` describes the
physical vertical interval above that cell. Neither field is a substitute for
semantic Floor identity.

### Projection is only a view

`FloorGeometry.projection()` may collapse several exact cells into one X/Z
column for rendering, bounds, coarse overlap, or legacy-facing APIs. Projection
must never decide discovery, storey ownership, Room connectivity, or exact
position lookup.

### Semantic Floor ownership is decided once

A fresh scan assigns each accepted exact cell to the selected semantic Floor
during traversal. Once `FloorGeometry` is produced, downstream code must not
rediscover which storey its cells belong to.

### Rooms are graph components of one Floor

`RoomPartitioner` consumes one `FloorGeometry`. Its ordinary nodes are exact
cells. Its ordinary edges are cardinal neighboring-column transitions allowed
by the shared Minecraft step rule. Room-boundary connectors remove/own boundary
cells according to the existing deterministic policy.

Room topology must not contain a second storey classifier.

## Selected-storey traversal

### Source resolution

The interaction source first resolves to one exact physical cell.

Resolution must account for low furniture such as beds: interacting on top of a
low obstacle resolves to the structural floor cell underneath when that floor
cell is physically valid. This is source resolution only; furniture does not
become topology.

The resolved cell becomes the selected-storey seed.

### World adapter

The Minecraft-facing adapter answers only local physical questions:

- does this exact candidate have a supported walkable surface?
- what is its `surfaceY`?
- what is its physical `ceilingY`?
- can the player-sized traversal move between these two neighboring surfaces
  under the shared step rule?
- is a connector present at or beside this cell?

It may use collision shapes, `WalkNodeEvaluator`, and a small bounded landing
probe. It must not decide Room type, POI ownership, persisted Room identity, or
global height bands.

### Storey boundary policy

The scan is one traversal with an explicit selected-storey boundary policy.

Conceptually:

```text
queue exact selected-storey cells

while queue not empty:
    inspect each cardinal neighboring column
    enumerate every physically valid step-compatible exact candidate
    ask selected-storey policy whether candidate is:
        OWNED      -> retain and continue traversal
        EDGE       -> retain as selected-storey transition/boundary cell,
                      but do not let it flood another storey
        OTHER      -> do not add to selected Floor
```

The policy is semantic, but it operates on exact graph transitions as they are
encountered. It must not first bucket all discovered cells by Y, calculate
slice areas, or reconstruct `HeightBand`s afterward.

The first implementation should preserve the current storey-separation
behavior through a small local policy rather than a new global model. It may
use existing persisted neighboring Floor identity and the existing semantic
height tolerance as boundary evidence, but scanner correctness must not depend
on `MIN_MEANINGFUL_HEIGHT_SLICE_AREA`, connected components of equal-Y slices,
or a whole-building `semanticBands(...)` pass.

### Ambiguous geometry

There is no universally correct way to infer human storeys from arbitrary
walkable geometry alone: a long cave ramp and a staircase can be physically
similar graphs. Therefore the boundary policy may use semantic context already
owned by the floor system:

- the selected source cell;
- persisted neighboring `StructureFloor` identities during refresh/addition;
- proven vertical connector attachment relationships;
- deterministic local transition rules covered by regression tests.

It must not hide this ambiguity behind an arbitrary area threshold. If a world
shape is genuinely ambiguous under the supported rules, scanning should fail or
remain on the selected storey rather than silently merging persisted storeys.

## Stairs, slabs, stacked cells, and caves

### Stairs

Stairs are ordinary exact walkable cells linked by valid step edges. No
`StairBlock` branch is required.

A staircase may contribute several exact cells to the lower Floor, including a
sparse transition cell whose Y equals the upper Floor's anchor. Reaching that
cell does not authorize broad traversal across the upper storey.

Conversely, scanning from a source in the upper room selects the upper storey
and its own exact cells.

### Stacked same-column cells

All valid exact cells survive discovery and ownership independently. No scanner
helper may collapse a column before storey selection or Room partitioning.

When an adjacent X/Z column contains multiple candidates, traversal evaluates
all candidates against the current cell and the storey boundary policy.

### Slabs and partial-height surfaces

Slabs and similar geometry are represented by their physical `surfaceY`.
Connectivity follows the same step rule as any other supported surface. There
is no slab topology category.

### Uneven caves

A cave Floor is not required to be flat or made from a particular material.
Exact cells may span several Y values while remaining one selected storey and
one Room when local walkability and the storey boundary policy allow it.

The scanner must not split a cave merely because its cells occupy several Y
slices, and it must not require four same-height cells to prove that part of the
cave is meaningful.

## Doors and horizontal connectors

Doors and other Room-boundary connectors affect traversal/partition metadata;
they do not manufacture a parallel Floor footprint.

The selected-storey scan discovers the real floor cell on each valid side from
world collision semantics. Connector association then attaches boundary
metadata to exact Floor cells that already exist.

`RoomPartitioner` remains responsible for:

1. partitioning ordinary exact cells without crossing Room-boundary cells;
2. clustering boundary cells;
3. assigning each boundary cluster to one deterministic adjacent Room owner.

An interior door and an exterior door use the same rule. There is no need for
an "outer door" geometry path.

## Vertical connectors

Ladders and trapdoors are attachment evidence between semantic Floors, not
ordinary Room edges and not implicit Room merging.

Connector discovery should be local to exact selected Floor cells and record
the normalized connector plus its associated exact cell(s). It should not use a
second seed-Y band traversal to decide whether a connector exists.

When adding a Floor or basement, proven connector relationships and persisted
Floor identity constrain which semantic storey is being attached. A vertical
connector may connect Floors without making their Rooms one component.

## Exterior detection

Exterior validation and Floor discovery must use the same physical-cell and
selected-storey traversal semantics.

The current independent `reachesExterior(...)` walk should not retain a
different semantic-band rule from the main scanner. Otherwise one path can call
a position "same Floor" while the other rejects it.

The target design is either:

- reuse the same local traversal primitive with a mode that asks whether an
  unroofed/out-of-bounds route exists; or
- share one exact neighbor enumerator and one selected-storey boundary policy
  between discovery and exterior probing.

There must be one answer to "which exact neighboring cells are reachable on
this selected storey?"

## Room partitioning

`RoomPartitioner` is already close to the target and should remain small.

It should continue to:

- use exact `FloorGeometry.Cell` nodes;
- inspect every candidate in neighboring X/Z columns;
- connect candidates only when `FloorGeometry.canStep(...)` succeeds;
- keep same-column cells distinct;
- treat Room-boundary connector cells as boundaries;
- assign boundary cells deterministically after open components are formed;
- resolve the source against an exact cell rather than a flattened footprint.

It should **not** gain semantic-storey logic removed from
`SelectedFloorScanner`.

## POIs, walls, and furniture

POI collection happens after Room topology.

For each exact Room component, candidate POI evidence may include:

- blocks occupying the Room's exact vertical intervals;
- supporting blocks where relevant;
- adjacent perimeter/wall blocks;
- connector blocks associated with the Room boundary.

`BuildingTypes` or equivalent relevance filtering decides whether those blocks
matter semantically.

Wall blocks can therefore be POI evidence without entering the Room's floor
cell set. A bookshelf embedded in a constructed wall or a cave wall is handled
the same way.

Low furniture such as beds may occupy the interior while the structural floor
underneath remains the Room cell. Furniture neither creates nor deletes Floor
geometry solely because it is a POI.

## Persistence and identity

This simplification does not introduce a new persistence format.

Keep the exact-geometry ownership established by the 2026-09-04 architecture:

- `StructureFloor` owns one canonical `FloorGeometry`;
- Rooms persist exact floor-cell keys referencing that geometry;
- `BuildingFloorRegion` remains derived projection only;
- `RoomScanPlan` remains action/identity only;
- `RoomIdentityPolicy` / reconciliation remain separate from topology;
- Floor + Room refresh remains atomic;
- `RoomDFU` remains the compatibility boundary for historical formats.

Any persistence change discovered during implementation requires a separate
explicit design decision; it is not implied by scanner cleanup.

## Expected simplifications

Once equivalent RED/GREEN coverage exists, `SelectedFloorScanner` should no
longer need the post-hoc semantic reconstruction model:

- remove `MIN_MEANINGFUL_HEIGHT_SLICE_AREA`;
- remove `SemanticBands`;
- remove `HeightBand` as a discovered-geometry reconstruction type;
- remove `cellsByHeight(...)` used only for semantic reconstruction;
- remove `sliceComponentsByHeight(...)`;
- remove equal-Y `sliceComponents(...)` used only for floor selection;
- remove `heightBands(...)`;
- remove `meaningfulHeightSlice(...)`;
- replace `floorSelection(...)` with direct selected-storey traversal output;
- remove lower-owner reassignment whose only job is repairing the Y-slice model.

`StructureFloor.BAND_TOLERANCE` may remain where semantic Floor ordering,
matching, or deterministic local storey-boundary evidence still needs it. It
must not remain as a hidden second geometry model.

`LANDING_Y_OFFSETS` may remain as a small Minecraft probing implementation
detail if tests prove it is sufficient for all physically valid step
candidates. It is not semantic Floor ownership.

## Testing strategy

### Pure exact-graph tests

Most scanner semantics should be testable without a fake Minecraft `Level` by
feeding exact cells and connector metadata to the selected-storey policy and
Room partitioner.

Required cases:

1. **Stacked column:** two valid cells with identical X/Z and different Y both
   survive; iteration order does not change ownership.
2. **Staircase split:** lower room + staircase + meaningful upper room remain
   two semantic Floors even though valid step edges connect them.
3. **Sparse top stair:** the top transition cell may remain owned by the lower
   Floor while the broad upper room at the same Y belongs to the upper Floor.
4. **Uneven cave:** a connected cave Room spanning several Y values remains one
   selected Floor/Room without same-height area thresholds.
5. **Slab transition:** physical `surfaceY`, not block type or feet Y alone,
   controls walkability.
6. **Room door:** a boundary connector prevents Room merging and has one
   deterministic Room owner without creating new geometry.
7. **Vertical connector:** ladder/trapdoor attachment relates Floors but does
   not merge their Rooms.

### Minecraft-facing tests

Use GameTests for facts that depend on real collision/block behavior:

1. beds/furniture do not change the room floor footprint;
2. scanning from the top of a bed resolves the structural floor underneath;
3. stairs/slabs produce the expected exact `surfaceY` and step connectivity;
4. uneven cave terrain scans without flat-floor assumptions;
5. interior and exterior doors do not manufacture Floor cells;
6. wall POIs are counted without wall blocks entering the Room footprint;
7. ladders/trapdoors produce attachment evidence without cross-floor Room
   merging.

### Existing behavioral regressions

The behaviors introduced by these historical fixes remain mandatory:

- `fix: keep staircase scans within one floor`;
- `fix: preserve stacked floor cells during scanning`.

Tests should describe the behavior, not preserve their former helper/class
structure.

## Acceptance scenarios

The simplification is complete only when all of these hold:

1. **Lower + upper storeys linked by stairs:** scanning the lower room does not
   flood the upper room, and scanning the upper room does not collapse into the
   lower Floor.
2. **Sparse staircase transition:** a sparse top transition cell may belong to
   the lower Floor while a meaningful upper room at the same height belongs to
   the upper Floor.
3. **Stacked X/Z:** exact cells at multiple Y values in one column survive and
   can be independently resolved.
4. **Uneven cave:** one Room/storey can span several Y values without
   `MIN_MEANINGFUL_HEIGHT_SLICE_AREA` or equal-Y component reconstruction.
5. **Beds:** placing/removing a bed does not change the structural Room
   footprint; scanning from bed top finds the same Room.
6. **Doors:** interior/exterior doors remain boundaries/metadata and do not
   invent floor geometry.
7. **Wall POI:** a valid wall POI is counted while the wall remains outside the
   Room's exact floor-cell set.
8. **Vertical connectors:** ladder/trapdoor relationships attach semantic Floors
   without merging Room components.
9. **Projection:** Blueprint/coarse X/Z views remain derived from exact cells and
   do not affect scan results.
10. **Persistence:** save/reload keeps the same exact Floor/Room ownership and
    identity behavior as before this scanner-only simplification.

## Alternatives rejected

### Keep patching `semanticBands(...)`

Adding more exceptions to meaningful slice sizes, band tolerances, and
lower-owner reassignment keeps two competing models: exact cells first, Y-slice
semantics second. It makes every new staircase/cave shape another tuning case.

### Treat the whole walkable building as one Floor

This is spatially simple but semantically wrong for the existing system. Stairs
and vertical connectors would merge storeys and make Floor identity, adding
upper floors/basements, Blueprint selection, and Room persistence inconsistent.

### Detect stairs by block class

That fixes one representation of a vertical transition but fails on slabs,
custom blocks, terrain steps, and cave ramps. The world adapter already has the
physical collision/surface information needed to describe movement without a
`StairBlock` topology rule.

### Add another intermediate floor model

`FloorGeometry` is already the correct exact representation. A second
`ScannedFloor`, `FloorSurface`, heightmap, or band model would recreate the
duplication this branch has been removing.

## Implementation boundary

This spec approves a scanner simplification only. The implementation plan must
be test-driven and should start by locking the acceptance scenarios above
before deleting semantic-band code.

The intended end state is deliberately small:

```text
world adapter + selected-storey traversal
                 |
                 v
            FloorGeometry
                 |
                 v
            RoomPartitioner
                 |
                 v
               Rooms
```

No implementation work should begin until this written spec is reviewed and
approved.
