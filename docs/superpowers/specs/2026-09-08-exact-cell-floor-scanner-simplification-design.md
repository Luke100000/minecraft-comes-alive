# Exact-Cell Floor Scanner Simplification

Date: 2026-09-08

Status: Canonical source of truth for Floor/Room spatial semantics on this
branch.

This document supersedes the spatial cell identity, traversal, connector, and
cell-persistence details in the older Floor/Room scanner and exact-geometry
specs. `2026-09-04-floor-room-3d-geometry-architecture-spec.md` remains the
historical foundation for exact 3D ownership, identity, atomic workflow, and
compatibility boundaries where this document does not override it.

The purpose of this revision is deliberately small: a Room/Floor is ultimately
made from integer 3D membership cells. Fractional collision heights are useful
while reading Minecraft, but they are not canonical Room state.

The historical fixes `fix: keep staircase scans within one floor` and
`fix: preserve stacked floor cells during scanning` remain regression contracts,
not implementation constraints. Their original mechanisms were already adapted
by the exact-geometry rewrite.

## Problem

The core model is now simpler than the scanner feeding it:

- `FloorGeometry` already stores exact 3D cells and permits multiple cells in
  the same X/Z column;
- Room ownership is already exact-cell ownership;
- the scanner already has the live Minecraft collision information needed to
  decide whether one candidate can be reached from another;
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

The scanner should instead decide whether a newly reached integer cell belongs
to the selected storey while traversing the world. It should retain the accepted
neighbour transitions only for that fresh observation, then discard fractional
surface heights rather than persisting them in `FloorGeometry`.

## Design principle

The canonical mental model is:

> A Floor is a set of integer 3D membership cells. A Room is a connected subset
> of those cells inside one semantic Floor, after Room boundaries are applied.

Everything else is either discovery input, semantic metadata, or a derived
view.

```text
Minecraft world
    -> resolve exact source cell
    -> sample local collision + accepted neighbour transitions
    -> traverse integer cells for the selected storey
    -> FloorGeometry
    -> RoomPartitioner using the fresh accepted transitions
    -> exact-cell Rooms
    -> POI evidence / projection / persistence views
```

There is no second spatial truth between world discovery and `FloorGeometry`.

## Goals

1. Make one selected-storey graph traversal the source of fresh Floor geometry.
2. Remove post-hoc Y-slice / area-based semantic reconstruction.
3. Preserve exact stacked cells, stairs, slabs, uneven terrain, and caves while
   keeping fractional collision height transient.
4. Preserve the distinction between semantic storeys; a staircase must not make
   two storeys one Floor merely because a player can walk between them.
5. Keep Room partitioning simple: connected exact cells minus Room boundaries.
6. Keep doors, gates, ladders, trapdoors, walls, furniture, and POIs from
   manufacturing alternate floor geometry.
7. Use Minecraft movement/collision semantics at the world adapter boundary,
   not block-class special cases in topology.
8. Remove `surfaceY` from canonical Floor persistence with one explicit,
   deterministic compatibility migration rather than supporting two shapes
   under the same data version.

## Non-goals

- Do not collapse the concept of a semantic Floor into one whole-building
  walkable component.
- Do not classify `StairBlock`, slab classes, cave materials, or specific Y
  values as architectural categories.
- Do not reintroduce one-cell-per-X/Z assumptions.
- Do not make doors or wall blocks part of Floor geometry merely to make POI or
  Blueprint behavior convenient.
- Do not redesign `RoomIdentityPolicy`, Main Room selection, inheritance, or
  logical-building identity.
- Change `RoomDFU` only as required to migrate the previous canonical exact-cell
  save shape to the new surface-height-free shape.
- Do not add another persisted or long-lived scan DTO beside `FloorGeometry`.
- Do not use unrestricted vanilla pathfinding as the Floor definition. A
  walkable route can legitimately cross semantic storeys.

## Canonical invariants

### Exact cells are spatial truth

`FloorGeometry.Cell.feet` is the canonical identity of a floor cell: the
integer `BlockPos` whose grid cell is considered part of that Floor/Room.

```text
(x, y, z) != (x, y + 3, z)
```

Both may belong to one semantic Floor, and both may coexist in the same X/Z
column.

The domain rule is deliberately simple: a Floor is a set of supported integer
membership cells. A slab, stair, carpet, bed, or other partial collision shape
does not create a fractional or support-block identity for the Room.

`surfaceY` is not a `FloorGeometry.Cell` field and is not persisted. Minecraft
surface height may be sampled transiently while deciding whether a candidate is
supported and whether a specific neighbour transition is physically valid.
After that transition has been classified, the numeric surface height is thrown
away.

`ceilingY` may remain attached to the cell as physical vertical-interval
metadata because position lookup, POI evidence, and bounds consume it. It does
not participate in cell identity or Room connectivity.

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

`RoomPartitioner` consumes one `FloorGeometry` plus the accepted physical
neighbour transitions from the same fresh observation. Its nodes are integer
cells. It must not recreate movement by reading persisted fractional heights.
Room-boundary connector positions are gaps/metadata, not manufactured nodes.

Room topology must not contain a second storey classifier.

## Selected-storey traversal

### Source resolution

The interaction source first resolves to one exact physical cell.

Resolution must account for low furniture such as beds: interacting on or above
the furniture resolves to the integer interior cell that the Room owns when
that cell has valid support and enclosure. The support block below is evidence,
not the canonical cell.

The resolved cell becomes the selected-storey seed.

### World adapter

The Minecraft-facing adapter answers only local physical questions:

- can this integer candidate be an interior Floor membership cell?
- what is the temporary standing/support surface height for this probe?
- what is its physical `ceilingY`?
- can the player-sized traversal move between these two neighboring surfaces
  under the shared step rule?
- is a connector present at or beside this cell?

It may use collision shapes, `WalkNodeEvaluator`, and a small bounded landing
probe. It must not decide Room type, POI ownership, persisted Room identity, or
global height bands.

Physical neighbor discovery is independent of persistence. Persisted
`StructureFloor` state may later help classify an already-discovered physical
transition as belonging to this storey or another one, but persistence must not
change whether the Minecraft adapter considers a local surface physically
valid/reachable in the first place.

### Structural support versus occupancy

Room/Floor topology represents ownership of interior integer cells, not a
requirement that the cell be literal air at scan time. Low interior occupancy
such as a bed or carpet may occupy an owned cell without deleting that cell
from the Room.

This must not become another broad heuristic. In particular, a generic rule
equivalent to `collisionShape.max(Y) <= 1.0` is insufficient because an ordinary
full cube also has a maximum collision height of `1.0`. A full-height solid
block must not become topology-neutral merely because the block above it is
open.

The Minecraft adapter must distinguish ownership from traversability. A
candidate can be owned while being inconvenient or temporarily impossible to
walk through. The local checks use physical facts:

- valid structural support below the logical cell;
- actual occupied collision height/shape in the logical cell;
- available headroom;
- whether collision fills/touches the whole logical cell versus remaining a
  genuinely sub-full interior obstacle;
- the shared Minecraft step/traversal rule for each neighbour transition.

The intended behavior is not "every POI is a floor cell" and not a `BedBlock`
special case. POI relevance alone never manufactures topology. The useful
invariant is: a supported enclosed interior cell can stay owned when occupied
by genuinely sub-full furniture, while a full solid obstruction remains a
separator/obstruction.

### Storey boundary policy

The scan is one traversal with an explicit selected-storey boundary policy.

Conceptually:

```text
queue exact selected-storey cells

while queue not empty:
    inspect each cardinal neighboring column
    sample every physically valid candidate and neighbour transition
    ask selected-storey policy whether candidate is:
        OWNED      -> retain cell + accepted transition, continue traversal
        EDGE       -> retain cell + accepted transition as selected-storey edge,
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

Persisted identity is evidence for semantic classification only. It must not be
implemented as a second persistence-aware physical flood fill or as a hidden
`PersistedFloorBoundary`-style world-discovery rule.

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

Slabs and similar geometry still use their real Minecraft collision surface
while the scanner evaluates a neighbour transition. The resulting canonical
cell remains an integer `BlockPos`; the fractional height is not stored in
`FloorGeometry` and is not written to NBT. There is no slab topology category.

### Uneven caves

A cave Floor is not required to be flat or made from a particular material.
Exact cells may span several Y values while remaining one selected storey and
one Room when local walkability and the storey boundary policy allow it.

The scanner must not split a cave merely because its cells occupy several Y
slices, and it must not require four same-height cells to prove that part of the
cave is meaningful.

## Doors and horizontal connectors

Doors and other Room-boundary connectors affect traversal/partition metadata;
they do not manufacture Floor cells. The door/gate position is a gap between
owned cells on its valid sides.

`RoomPartitioner` partitions only owned cells and omits connector-gap
transitions. A door block therefore belongs to neither Room floor-cell set.
Metadata may still associate that connector position with one deterministic
Room for POI/interaction purposes when needed.

An interior door and an exterior door use the same rule. There is no owned-door
cell or separate "outer door" geometry path.

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

It should:

- use exact `FloorGeometry.Cell` nodes;
- connect nodes only through accepted neighbour transitions from the fresh
  scanner observation;
- keep same-column cells distinct;
- omit Room-boundary connector-gap transitions;
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

If one perimeter/wall POI candidate borders more than one Room component, it
must have one deterministic owner rather than being counted by every adjacent
Room. Reuse the existing stable Room-owner ordering used elsewhere in Room
partitioning/reconciliation unless a focused regression proves a narrower rule
is required.

Low furniture such as beds may occupy an owned interior cell. Furniture neither
creates nor deletes Floor geometry solely because it is a POI, and a wall POI
does not become an owned cell merely because it is relevant.

## Persistence and identity

Keep the exact-geometry ownership established by the 2026-09-04 architecture:

- `StructureFloor` owns one canonical `FloorGeometry`;
- Rooms persist exact floor-cell keys referencing that geometry;
- `BuildingFloorRegion` remains derived projection only;
- `RoomScanPlan` remains action/identity only;
- `RoomIdentityPolicy` / reconciliation remain separate from topology;
- Floor + Room refresh remains atomic;
- `RoomDFU` remains the compatibility boundary for historical formats.

This revision intentionally changes the canonical cell encoding because
`surfaceY` is no longer domain state:

```text
buildingDataVersion = 2

StructureFloor cell:
  pos       exact integer membership key
  ceilingY  retained vertical-interval metadata
  # no surfaceY
```

`RoomDFU` must migrate `buildingDataVersion == 1` by preserving each cell's
`pos` and `ceilingY` and discarding its persisted `surfaceY`. The released
`origin/1.21.1` and upstream unversioned floor-clean-squash migrations remain
supported. Version 2 is the only direct canonical load shape after this change;
do not make version 1 accept both encodings.

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
feeding exact cells, accepted transient transitions, and connector metadata to
the selected-storey policy and Room partitioner.

Required cases:

1. **Stacked column:** two valid cells with identical X/Z and different Y both
   survive; iteration order does not change ownership.
2. **Staircase split:** lower room + staircase + meaningful upper room remain
   two semantic Floors even though valid step edges connect them.
3. **Sparse top stair:** the top transition cell may remain owned by the lower
   Floor while the broad upper room at the same Y belongs to the upper Floor.
4. **Uneven cave:** a connected cave Room spanning several Y values remains one
   selected Floor/Room without same-height area thresholds.
5. **Slab transition:** live collision height controls whether the transition
   is accepted, but both canonical cells remain integer positions.
6. **Room door:** a boundary connector prevents Room merging without creating
   or owning a door floor cell.
7. **Vertical connector:** ladder/trapdoor attachment relates Floors but does
   not merge their Rooms.

### Minecraft-facing tests

Use GameTests for facts that depend on real collision/block behavior:

1. beds/furniture do not change the room floor footprint;
2. scanning from the top of a bed resolves the same owned integer Room cell;
3. a full-height solid block is not treated as topology-neutral merely because
   its collision shape reaches exactly `1.0` and the block above is open;
4. carpet/representative low furniture preserves integer Room membership;
5. stairs/slabs produce the expected integer cells and accepted physical
   transitions without persisting fractional surface height;
6. uneven cave terrain scans without flat-floor assumptions;
7. interior and exterior doors do not manufacture Floor cells;
8. wall POIs are counted once without wall blocks entering the Room footprint;
9. ladders/trapdoors produce attachment evidence without cross-floor Room
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
10. **Persistence:** version-2 saves contain exact membership cells without
    `surfaceY`; version-1 canonical saves migrate to the same Floor/Room
    ownership and identity.

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

`FloorGeometry` is already the correct long-lived membership representation. A
second persisted `ScannedFloor`, `FloorSurface`, heightmap, or band model would
recreate the duplication this branch has been removing. A bounded transient set
of accepted neighbour transitions in the existing scan result is discovery
evidence, not another canonical geometry model.

## Implementation boundary

This spec approves a scanner simplification only. The implementation plan must
be test-driven and should start by locking the acceptance scenarios above
before deleting semantic-band code.

Prefer moving behavior into the existing owners (`FloorGeometry`,
`SelectedFloorScanner`, `RoomPartitioner`, connector/POI helpers) and private
helpers over adding new top-level classes. A new class is justified only when a
responsibility is genuinely reusable/independent and keeping it inside an
existing owner would make that owner less clear.

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
