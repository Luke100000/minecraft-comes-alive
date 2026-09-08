# Floor / Room 3D Geometry Architecture

## Status

Proposed replacement for the current floor/room geometry model on
`feature/1.21.1-floor-clean-squash`.

> **Spatial semantics superseded 2026-09-08:**
> `2026-09-08-exact-cell-floor-scanner-simplification-design.md` is now the
> canonical source of truth for Floor/Room cell identity, traversal evidence,
> connector gaps, and canonical cell persistence. In particular, persisted
> `surfaceY` and owned door cells described below are historical. This document
> remains authoritative for the exact-3D ownership direction, identity model,
> atomic workflow, and compatibility architecture where the 2026-09-08 spec
> does not override it.

This spec supersedes the spatial parts of
`2026-09-03-floor-room-single-source-simplification.md`, in particular the old
rule that Room ownership is X/Z column ownership. The real staircase regression
proves that one semantic Floor may legitimately own more than one physical
surface in the same X/Z column.

The identity rules from the previous work remain valid unless this document
explicitly changes them.

## Why this is the right correction

The first commits of this branch establish useful boundaries that should be
preserved rather than replaced with another parallel model.

### `518102886` — `1.21.1 Floor Update`

The original branch architecture introduced:

- `Structure` as persistent physical-building ownership;
- `StructureFloor` as stable persistent storey identity;
- `BuildingFloorRegion` as a compact X/Z projection for a semantic Floor;
- `BuildingRoomScanner` as the component partitioner inside one Floor;
- `RoomScanPlan` as the action/identity boundary between Blueprint preview and
  server execution;
- `RoomDFU` as the compatibility boundary from released `origin/1.21.1` data.

The original semantic Floor ceiling rule was also correct:

```text
non-top Floor ceiling = next Floor anchor
top Floor ceiling     = detected physical roof
```

That rule describes semantic storeys. It should not be replaced by the maximum
physical ceiling observed in one scan.

The weakness of the initial implementation was not those ownership boundaries.
It was that the persistent Floor geometry was only a 2D `BuildingFloorRegion`,
and Room partitioning therefore projected everything to the Floor anchor Y.

### `22d75f0ed` / `2de880b4c`

These commits simplified Room reconciliation so Main Room identity remains
stable through merge/update operations. That is an identity concern and should
remain separate from physical geometry.

### `4182d7302` — `Improvements to floor system`

`RoomIdentityPolicy` extracted identity assignment from the larger workflow.
This is the correct direction: geometry discovers components; a separate policy
decides which persisted Room IDs those components inherit.

### `92fbce2d5` — `Improve Architecture`

`RoomScanPlan` was tightened into a canonical action/identity plan shared by
client preview and server execution. It should stay an action/identity object;
it must not become another holder of Floor geometry.

## Regression that invalidates the current model

The current transient `FloorSurface` introduced a single-value index:

```text
(x, z) -> one Cell
```

The test world contains this valid lower-storey geometry in one column:

```text
(-302, 91, -1623)  top staircase transition cell
(-302, 88, -1623)  lower flat-room cell
```

Both cells can belong to the same semantic lower Floor. The meaningful upper
room also starts at Y=91 nearby. Component-aware semantic assignment can
correctly distinguish the sparse transition cell from the meaningful upper
storey, but construction then fails because `FloorSurface` rejects two cells in
one X/Z column.

This is not a staircase special case. It demonstrates that a semantic Floor is
not a heightmap.

## Goals

1. Make exact 3D physical cells the single spatial source of truth.
2. Allow any number of physical Floor cells in one X/Z column.
3. Keep Floor identity, Room identity, Main Room identity, and logical-building
   identity separate from geometry discovery.
4. Derive 2D footprints, bounds, area, map outlines, and semantic vertical bands
   from the canonical 3D data instead of persisting competing geometry models.
5. Keep `RoomDFU` as the only compatibility boundary for old building/floor
   formats.
6. Preserve atomic Floor + Room publication during rescans.
7. Remove special representations that only exist because the old persistence
   model could not express exact geometry, especially `ceilingBoundaryRegion`.

## Non-goals

- Do not revert to the old whole-volume `origin/1.21.1` building scanner.
- Do not special-case `StairBlock` or the Y values from the test world.
- Do not physically nest mutable `Building` objects inside `StructureFloor` in
  this change. Existing Room APIs can continue to use `Building` while its
  spatial state is simplified.
- Do not redesign Main Room, inheritance, logical-building grouping, or
  Blueprint UX unless required by the new geometry contract.
- Do not add another long-lived scan DTO that duplicates the canonical Floor
  geometry.

## Canonical ownership model

```text
world blocks
   |
   v
physical cell discovery
   |
   v
semantic Floor assignment
   |
   v
FloorGeometry  <------------------------------+
   |                                           |
   +--> StructureFloor (stable Floor ID)       |
   |                                           |
   +--> Room component partition               |
            |                                  |
            v                                  |
          Building/Room -- owns cell keys -----+
```

Persistent identity remains:

```text
LogicalBuilding
   |
   +--> one or more Structures
            |
            +--> StructureFloors

Building/Room
   +--> structureId
   +--> floorId
```

The key spatial invariant is:

> A physical cell is identified by its full 3D position. X/Z is only a derived
> projection and is never sufficient to identify Floor or Room membership.

## `FloorGeometry`: the single physical Floor representation

Replace `FloorSurface` + `ScannedFloor` with one exact geometry type for one
semantic Floor.

Conceptually:

```java
record FloorGeometry(
        Set<Cell> cells,
        Map<BlockPos, StructureFloor.ConnectorType> connectorTypesByCell) {

    record Cell(BlockPos feet, double surfaceY, int ceilingY) {}
}
```

The exact API may differ during implementation, but the invariants are fixed:

- `Cell.feet` is the physical identity.
- Two cells with the same X/Z and different Y are valid.
- Two different cells with the same full `BlockPos` are not valid.
- `surfaceY` is physical walkability data, not semantic Floor identity.
- `ceilingY` is the physical ceiling for that cell/column observation.
- connector ownership is attached to an exact Floor cell, not to an anchor-Y
  projection of the column.

Derived indexes are allowed for performance, but they are not authoritative.
The normal column index is:

```text
(x, z) -> List<Cell> sorted by feet Y
```

There must be no API whose semantics silently mean "give me the one Cell in
this column". Queries choose a cell explicitly by source Y and/or topology.

### Derived views

`FloorGeometry` derives:

- `anchorY`: dominant meaningful flat height, using the same deterministic
  policy as scanning;
- `maxPhysicalCeilingY`;
- `BuildingFloorRegion projection()`: X/Z union for map, outline, area, and
  coarse overlap operations;
- bounds;
- `cellsAtColumn(x, z)`;
- exact physical lookup by full `BlockPos`.

`BuildingFloorRegion` therefore remains a useful compact **view**, but it is no
longer persisted or consulted as the authoritative Floor topology.

## Semantic Floor assignment

Discovery may encounter several walkable surfaces in the same X/Z column and
must retain all of them until semantic assignment is complete.

The current component-aware direction is retained:

1. Discover exact walkable cells using Minecraft collision/walkability rules.
2. Find meaningful connected surface components. A meaningful upper-room
   component can establish a new semantic storey.
3. Treat sparse transition components as connectors between meaningful
   components rather than automatically creating a Floor.
4. Assign a sparse transition component to the semantic Floor it is physically
   connected to. A sparse top stair at the same Y as an upper room may therefore
   remain part of the lower Floor while the meaningful upper-room component
   starts the upper Floor.
5. Never classify by block class (`StairBlock`) and never assume one physical
   height per X/Z column.

The output of semantic assignment is one `FloorGeometry` per semantic Floor.
No later layer should have to rediscover which semantic Floor a cell belongs to.

## `StructureFloor`: identity around canonical geometry

`StructureFloor` remains the stable persisted Floor identity, but it should stop
carrying a second geometry model.

Target responsibility:

```text
StructureFloor
  id                 stable persistence identity
  geometry           exact FloorGeometry
  floorNumber        derived runtime presentation value
```

`anchorY`, projected region, bounds, and physical membership are derived from
`geometry`.

`ceilingBoundaryRegion` is removed. Its only purpose is to encode exceptional
physical cells at the semantic ceiling in a representation that otherwise
collapses the Floor to 2D. Exact cells make it unnecessary.

### Semantic ceiling

The original `518102886` rule is restored as a derived Structure-level concept:

```text
if there is a next semantic Floor:
    semanticCeilingY = nextFloor.anchorY
else:
    semanticCeilingY = floor.geometry.maxPhysicalCeilingY
```

This value is useful for storey ordering and compatibility queries, but it does
not decide whether an exact physical cell belongs to the Floor. A lower Floor
may validly own a transition cell whose feet Y equals the next Floor anchor.

Because the non-top ceiling depends on a sibling Floor, it should not become a
second authoritative field inside `FloorGeometry`.

## Room topology and ownership

Room partitioning operates on the exact `FloorGeometry` cell graph.

### Graph nodes

Each `FloorGeometry.Cell` is one node, identified by full `feet` `BlockPos`.

### Graph edges

Ordinary horizontal adjacency exists when:

- X/Z differ by exactly one cardinal block;
- the two physical surfaces satisfy the same walkable step-height rule used by
  discovery; and
- no Room-boundary connector separates the cells.

Cells merely sharing the same X/Z column are **not** adjacent.

When an adjacent X/Z column contains several cells, partitioning evaluates every
candidate whose physical height is step-compatible with the current cell. It
must never select an arbitrary first/lowest/highest cell before applying the
connectivity rule.

### Door/gate boundaries

Door and gate Floor cells remain deterministic Room boundaries. Boundary cells
are assigned to exactly one adjacent component after ordinary components are
formed, using the existing stable owner ordering unless tests require a narrower
rule.

Vertical connectors are Floor-attachment evidence, not implicit Room merging.

### Room persistence

A functional Room persists a set of exact Floor cell keys:

```text
Set<BlockPos> floorCells
```

Those positions are references into its owning `StructureFloor.geometry`.
The Room does not duplicate `surfaceY` or `ceilingY`.

Required invariant:

```text
room.floorCells subset-of owningFloor.geometry.cellPositions
```

Two Rooms on one Floor may not own the same exact cell. They may contain cells
with the same X/Z at different Y if the topology genuinely produces that result.

Room X/Z footprint, bounds, and area are derived from these exact cell keys.
The old persisted `Building.floorRegion` is removed from the canonical save
format; a projected region may still be created on demand for rendering and
overlap calculations.

## Position and Room resolution

Position resolution becomes explicitly height-aware.

### Resolve a physical Floor cell

For a query position `(x, y, z)`:

1. inspect `geometry.cellsAtColumn(x, z)`;
2. keep cells whose vertical interior contains the query position, using the
   cell's physical feet/ceiling data;
3. if several candidates remain, choose the highest physical feet Y not above
   the query Y, then deterministic full-position ordering;
4. connector interactions may supply an exact handoff cell, but they use the
   same geometry lookup rather than a separate Floor-priority system.

This replaces Floor-wide X/Z extrusion and column-only Room ownership.

### Resolve a Room

Once an exact Floor cell is selected, resolve the Room that owns that exact cell
key. If no registered Room owns it, the position is on an unregistered component
of a known Floor and may produce `ADD_ROOM`.

## POI ownership

POI ownership is derived from Room cell ownership, not from a flattened Room
footprint.

For each Room-owned Floor cell, inspect its physical vertical interval for
functional POIs. Existing adjacent perimeter/wall POI behavior may remain, but
adjacency must be evaluated against exact nearby cells and deterministic Room
ownership.

Perimeter POIs do not become Room Floor cells merely because they are assigned
to a Room.

## Structure bounds and intersection

`Structure` continues to own Floors and logical-building identity.

Its bounds are derived from exact Floor geometry. They are a coarse acceleration
structure only; exact containment uses `FloorGeometry`.

Structure intersection must not treat overlapping X/Z projections as physical
overlap when the exact storeys are vertically disjoint. Coarse bounds/projection
may reject impossible matches quickly, followed by exact/semantic Floor checks.

## Scan and publication workflow

`RoomScanPlan` remains the canonical action + persisted identity plan. It does
not carry reusable geometry.

Preview and execution may both scan the live world. Execution is authoritative
and revalidates the planned identity/action before publication.

### Update existing Room

```text
RoomScanPlan(identity/action)
        |
        v
fresh exact FloorGeometry scan
        |
        v
match existing StructureFloor identity
        |
        v
partition fresh FloorGeometry into Room components
        |
        v
RoomIdentityPolicy assigns persisted Room IDs
        |
        v
publish refreshed StructureFloor + reconciled Rooms atomically
```

The persisted Floor and its Rooms must never be published in separate steps.

### Add Room on a stale/expanded Floor

Fresh geometry may extend beyond the old persisted projection. If the fresh
Floor uniquely matches an existing semantic Floor, refresh that `StructureFloor`
and add/reconcile the new Room component in the same atomic publication.

This replaces the need for stale persisted Floor geometry to gate the fresh Room
footprint. `StructureExpansionPolicy` may be reduced to identity matching or
removed if its remaining behavior becomes a direct part of the Floor matcher.

### Add Floor / Basement

Attachment still requires proven physical vertical connectivity through a real
vertical connector. Candidate matching uses exact Floor geometry and connector
handoff cells. Vertical distance may break ties only after connectivity is
established.

## Persistence format

Introduce `buildingDataVersion = 1` for the new canonical exact-geometry format.

The canonical save contains only the current object model. Legacy field names
must not be interpreted by `Village`, `Building`, `Structure`, or scanners.

Conceptually:

```text
Village
  buildingDataVersion: 1
  buildings: [Room...]
  externalBuildings: [...]
  structures: [Structure...]
  logicalBuildings: [...]

Structure
  id
  buildingId
  source
  floors:
    - id
      cells: exact 3D FloorGeometry cells
      connectors: exact Floor-cell connector ownership

Room / Building
  id
  structureId
  floorId
  source
  floorCells: exact cell keys owned by the Room
  type / forced / contributesToMain
  POI block state needed by existing Room type logic
```

The first implementation should prefer a clear deterministic encoding over a
complex compression scheme. If save-size measurement later shows that cell NBT
is a real problem, compression can change without changing the in-memory model.

## `RoomDFU` is the only compatibility boundary

All building-state loading goes through `RoomDFU`, including direct loading of
the current canonical format.

`Village` should conceptually do:

```java
RoomDFU.Result data = RoomDFU.load(tag);
```

and then install the returned canonical state. `Village` should not contain a
second historical-format switch.

`RoomDFU` recognizes exactly these supported inputs:

1. **Released `origin/1.21.1`** — no Structures. Preserve the existing legacy
   Building migration behavior.
2. **Upstream `origin/feature/1.21.1-floor-clean-squash`** — Structures/Floors
   exist but no `buildingDataVersion`. This is the only Floor-system migration
   source that must be supported.
3. **Current canonical save** — `buildingDataVersion == 1`; this is not a
   migration path.

Historical tag interpretation and normalization lives only in `RoomDFU`.

### Migrating old 2D Floor geometry

Old formats cannot contain physical information they never persisted. Migration
therefore creates a conservative exact approximation and lets later world scans
replace it with real geometry.

For an upstream floor-clean-squash `StructureFloor` with `region(anchorY)` and
`ceilingY`:

- create one `FloorGeometry.Cell` at `(x, anchorY, z)` for each region cell;
- use the persisted semantic/physical ceiling as the conservative cell ceiling;
- preserve connector markers;
- do not invent support for later local-only fields such as
  `ceilingBoundaryRegion`; they are not part of the upstream migration contract.

### Migrating old Room footprints

For an old Room `floorRegion`, assign every exact cell in its owning migrated
Floor whose X/Z lies in that Room region. This reproduces the old column-based
ownership as faithfully as the old data permits while converting immediately to
the new exact-cell representation.

Released `origin/1.21.1` Rooms with no Floor geometry continue to receive a
single conservative flat Floor synthesized from their stored building bounds,
as the existing `RoomDFU` already does conceptually.

## Classes to remove or reduce

Target cleanup after migration:

- **Remove `ScannedFloor`** — semantic Floor scan output is `FloorGeometry`.
- **Replace `FloorSurface` with `FloorGeometry`** — no one-cell-per-column
  invariant.
- **Remove `StructureFloor.ceilingBoundaryRegion`**.
- **Stop persisting authoritative `BuildingFloorRegion` in `StructureFloor` and
  `Building`**; retain projection helpers only.
- **Change `FloorSurfacePartitioner` into an exact-cell Room partitioner**; it
  may be renamed once the final responsibility is clear.
- **Reduce/remove `StructureExpansionPolicy`** if exact fresh Floor matching
  makes it a thin wrapper.
- **Keep `RoomIdentityPolicy`/reconciliation separate from topology**.
- **Keep `RoomScanPlan` geometry-free**.
- **Keep `LogicalBuilding` focused on logical identity/Main Room/inheritance**.

## Runtime failure policy

Valid Minecraft geometry must not cause unchecked exceptions merely because it
contains stacked surfaces.

Scanner/topology failures caused by ordinary world geometry return the existing
validation/diagnostic result path. Exceptions are reserved for impossible
program-state invariants or malformed current-version persistence.

Legacy data migration should repair/filter representational inconsistencies when
the intended ownership can be determined. After migration, canonical state is
validated strictly:

- every Room references an existing Structure and Floor;
- every Room Floor cell exists in that Floor's geometry;
- one exact Floor cell has at most one registered Room owner;
- Floor geometry contains no duplicate full `BlockPos` cell identities;
- logical-building and Main Room references are valid under the existing repair
  policy.

## Tests that define the new contract

### Real regression geometry

Add a pure scanner/topology regression containing all of these at once:

```text
lower meaningful surface at Y=88
stair transition at Y=89
stair transition at Y=90
sparse top transition cell at Y=91
meaningful upper-room surface at Y=91

at least one lower Y=88 cell shares the same X/Z as the sparse Y=91 transition
cell
```

Required outcome:

- no exception;
- both same-column exact cells survive discovery;
- sparse top transition cell belongs to the lower semantic Floor;
- meaningful Y=91 room belongs to the upper semantic Floor;
- lower Room partition remains connected through the transition where physical
  step connectivity says it should;
- upper Room does not merge with the lower Room merely because it shares Y or
  X/Z projection.

### Floor geometry

- multiple cells at one X/Z are legal;
- duplicate identical full-position cells are de-duplicated/rejected
  deterministically;
- `cellsAtColumn` returns every cell sorted by Y;
- projected footprint collapses stacked cells only for the derived 2D view;
- exact lookup never depends on iteration order.

### Room partition

- adjacency considers every height-compatible candidate in an adjacent column;
- same-column cells do not create an edge by themselves;
- door/gate boundary cells get one deterministic Room owner;
- exact Room cell sets are disjoint;
- every Room cell is a member of the owning Floor geometry.

### Semantic Floors

- non-top semantic ceiling is the next Floor anchor;
- top semantic ceiling comes from physical ceiling data;
- a lower Floor may own an exact transition cell at the next Floor's anchor Y;
- no StairBlock-specific behavior is required.

### Migration

Keep fixture tests for:

- released `origin/1.21.1` save;
- the actual unversioned upstream `origin/feature/1.21.1-floor-clean-squash`
  save shape;
- current canonical round trip.

Add a source-level guard/test if practical that historical field normalization
(`floorRegions`, old inheritance fields, legacy BlockPos compounds, old Ground
Floor fields) is referenced only from `RoomDFU` and migration tests.

### Atomic workflow

- updating a Room publishes refreshed Floor geometry and reconciled Rooms
  atomically;
- adding a Room to stale same-storey geometry refreshes the owning Floor in the
  same publication;
- failed revalidation leaves both persisted Floor and Rooms unchanged;
- Room ID/Main Room stability follows the existing identity policy.

## Implementation order

Implementation should be test-driven and keep the repository runnable between
steps.

1. Add the stacked same-column regression and make it fail for the current
   single-cell `FloorSurface` assumption.
2. Introduce `FloorGeometry` with full-position identity and multi-valued column
   lookup; port discovery/semantic assignment without changing persistence yet.
3. Port Room partition/connector association to exact-cell topology.
4. Replace `ScannedFloor` and route scan results through `FloorGeometry`.
5. Move `StructureFloor` to exact geometry and remove
   `ceilingBoundaryRegion`/authoritative persisted region.
6. Move Room membership from projected `floorRegion` to exact Floor cell keys.
7. Make Floor + Room refresh publication atomic against the new geometry model;
   simplify/remove stale expansion wrappers that are no longer needed.
8. Implement canonical persistence and move all load routing into `RoomDFU`.
9. Add migration fixtures only for released origin and the actual upstream
   unversioned floor-clean-squash format, plus a canonical round-trip test.
10. Delete dead compatibility/projection code only after migration and full
    regression tests prove it is unused.

## Acceptance criteria

The architecture is complete when all of the following are true:

- the real test-world stacked column cannot trigger a geometry exception;
- no production Floor topology assumes one cell per X/Z;
- there is one authoritative exact 3D geometry representation per Floor;
- Rooms persist exact Floor-cell membership, not an independent 2D geometry
  truth;
- `BuildingFloorRegion` is derived projection only;
- `ceilingBoundaryRegion` and `ScannedFloor` are gone;
- semantic ceiling behavior matches the original branch invariant;
- Main Room/Room identity behavior remains stable;
- `RoomScanPlan` remains action/identity only;
- `RoomDFU` is the only code that understands legacy building save formats;
- released origin and upstream floor-clean-squash saves load into valid
  canonical state;
- focused regressions, full `:common:test`, NeoForge compilation, and
  `git diff --check` pass.
