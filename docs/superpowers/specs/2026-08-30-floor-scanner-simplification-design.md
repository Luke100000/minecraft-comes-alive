# Floor Scanner Simplification

> **Spatial model superseded:** use
> `2026-09-08-exact-cell-floor-scanner-simplification-design.md` as the
> canonical Floor/Room spatial contract. Older door-cell ownership and scanner
> topology rules below are retained only as historical design context.

Date: 2026-08-30

Status: Superseded by `2026-08-30-floor-scanner-simplification-spec.md`

Supersedes the scanner architecture in sections 5-6 of
`2026-08-30-blueprint-map-and-floor-scan-corrections-design.md` and scanner Tasks 5-6 of the
matching implementation plan. The Blueprint map/camera/tooltip work in that design remains
unchanged.

## Purpose

Replace the increasingly coupled floor/Room scanner rules with a small pipeline where each
stage has one meaning:

```text
Minecraft world
    -> physical 3D discovery
    -> persisted Structure matching
    -> Room topology
    -> Room POI evidence
    -> persisted Room / logical-building presentation
```

The redesign must directly support these behaviors:

1. Natural caves and uneven floors are valid interiors. Fresh scanning must retain the real Y
   coordinate of each usable surface cell instead of flattening the world to one `anchorY`
   before Room topology is decided.
2. Blocks in a Room boundary wall can be POIs. A bookshelf embedded in a cave wall must be
   discoverable just like a bookshelf against a constructed house wall.
3. `Update Room` re-reads the world, updates Room geometry, rechecks POIs, and lets the
   building outline follow the resulting registered Room footprints.
4. Doors use one rule everywhere. There is no exterior-door classification and no separate
   outer-door behavior.
5. Door columns do contribute one floor cell to one adjacent Room. This prevents holes / odd
   shading in Room and Blueprint footprints. The door is still a topology boundary between
   components; floor-cell ownership must not make it connect those components.
6. Adding an upper floor or basement discovers physical world truth first and then decides
   whether discovered floors are already persisted. Persisted ownership must not alter the
   physical traversal itself.
7. Existing ambiguity, overlap, identity, and atomic-commit safeguards remain intact.

## Core invariant

These concepts are related but not interchangeable:

- **Surface cells** describe real usable physical floor positions in the world at `(x, y, z)`.
- **Portals/connectors** describe possible transitions or boundaries between surface regions.
- **Room footprint cells** describe the X/Z presentation and ownership of one logical Room.
- **POI evidence** describes relevant blocks associated with a Room, including blocks that are
  not part of its usable floor.
- **Persisted Structures** describe saved physical ownership/identity.

No stage may use POI relevance to decide physical geometry, and no persistence rule may hide
physical geometry from discovery.

## 1. Physical 3D discovery

### Transient model

Fresh world discovery should produce a transient model that keeps the real surface height:

```java
record SurfaceCell(int x, int y, int z) {}

record FloorSurface(
        Set<SurfaceCell> cells,
        Set<Portal> portals) {}
```

The exact names are not important; the separation is. `StructureScanner` may continue using
Minecraft `BlockPos` / `BlockState` internally, but the result consumed by floor grouping and
Room topology must preserve each discovered cell's actual Y.

### Cave behavior

There is no special `CaveBlock`, wall-material list, sea-level rule, or terrain-height rule.
A cave floor is accepted for the same reason as a house floor: it is a supported interior
surface with sufficient enclosed/open volume above according to the existing physical scanner
rules.

For example, all of these can belong to one semantic storey:

```text
(10, 64, 10)
(11, 64, 10)
(12, 65, 10)
(13, 65, 10)
(14, 66, 10)
```

Storey grouping may still use a tolerance, but grouping must not rewrite those cells to a
single anchor Y before Room connectivity and POI collection have consumed them.

### Persistence compatibility

Do not require an NBT/DFU migration in the first implementation. The current compact
`BuildingFloorRegion` X/Z representation may remain the persisted form initially.

The rule is:

- fresh scans use exact transient 3D surfaces;
- persistence may project the accepted surface to the existing compact representation;
- operations that need current cave geometry (`Update Room`, Structure rescan, additions)
  refresh from the world rather than pretending the saved anchor-Y projection is exact 3D
  truth.

If later runtime evidence proves exact per-column Y must survive without a rescan, that is a
separate save-format design and migration.

## 2. Structure matching happens after discovery

The scanner should discover the complete physically connected candidate without consulting
persisted Structure cells as traversal barriers.

After discovery, a matching layer compares the fresh result with persisted Structures and
classifies ownership:

```text
fresh physical floors:    {74, 77}
persisted target floors:  {77}
new attachment delta:     {74}
```

The persisted `77` storey is evidence that the fresh scan reached the existing building, not a
reason to truncate traversal while scanning.

This replaces the conceptual need for `PersistedFloorBoundary`. The final implementation may
reuse small helper code temporarily, but persistence-aware traversal must not remain the
architectural contract.

### Matching safeguards to preserve

Matching must retain the existing safety rules:

- a fresh same-storey scan overlapping exactly one persisted Structure can refresh/match that
  Structure rather than creating a duplicate;
- overlap across multiple persisted Structures is ambiguous and must refuse to guess;
- a real new candidate that conflicts with another persisted owner still returns `OVERLAP`;
- attachment must still resolve to the requested logical building, nearest/unique target,
  valid vertical gap, and correct floor direction;
- analysis remains detached until commit.

## 3. Room topology is surface connectivity only

`BuildingRoomScanner` should stop deciding whether furniture or POIs are part of topology.

Its job is:

1. consume one fresh semantic `FloorSurface`;
2. identify usable surface cells;
3. split them into connected Room components using boundary/portal rules;
4. choose the component addressed by the interaction source;
5. produce the Room's owned footprint cells.

### What is not an ordinary topology cell

These must not connect two Room components:

- walls;
- beds / furniture / functional POI blocks;
- doors;
- fence gates;
- ladders;
- trapdoors.

Low collision geometry can still be represented by physical surface discovery where Minecraft
movement semantics make it a usable floor. The Room partitioner should not contain a second
BuildingType-aware furniture model.

### Door and connector floor-cell ownership

Doors need one owned floor cell for footprint continuity and Blueprint shading.

This is a post-partition ownership rule, not a passage rule:

1. remove portal/connector columns from ordinary component connectivity;
2. partition the usable surface into Room components;
3. for each connector floor cell, find adjacent Room components;
4. assign the connector cell to exactly one adjacent component;
5. use the same deterministic owner rule for every door, regardless of whether one side is
   outside the Structure.

The preferred deterministic owner rule remains:

1. largest adjacent Room component first;
2. `minX`, `minZ`, `maxX`, `maxZ` as stable equal-area ties.

Thus an exterior door adjacent to a large interior and a tiny exterior apron naturally belongs
to the interior Room without ever asking whether the door is "outer". An interior door between
two meaningful Rooms also has exactly one footprint owner, while still acting as a boundary
between them.

`StructureConnector.ownsFloorCell(BlockState)` should not encode "doors are different". If a
helper remains, it must describe generic connector floor projection/ownership, not
interior-vs-exterior semantics.

## 4. POI evidence is collected after topology

POIs must never be inserted into Room topology merely so they can later be discovered.

Once one Room component is known, build its POI candidate set from world positions around the
component:

1. each actual interior surface column;
2. the supporting block below each surface cell;
3. the one-block horizontal perimeter around the Room footprint;
4. blocks vertically occupying interior/perimeter columns through the Room's local enclosed
   height / ceiling range;
5. connector/portal blocks associated with those boundary columns where relevant.

Feed those candidates through the existing `Building.recordBuildingBlock(...)` /
`BuildingTypes` relevance filtering. Stone, deepslate, quartz, dirt, etc. are harmless candidate
evidence when no configured BuildingType matches them.

### Wall POIs

A wall is therefore not a special Minecraft block category. It is simply a boundary/perimeter
column adjacent to the Room.

```text
####B##       B = bookshelf embedded in wall
#.....#
#.....D       D = door
#######
```

The bookshelf is a POI because its block position is in the Room's perimeter evidence set.
The wall cell is not added to the Room footprint.

The same rule works for a cave carved into stone and for a constructed building.

## 5. `Update Room` semantics

`Update Room` means "make the registered floor agree with the world again", not merely
"recount blocks inside the old footprint".

The operation should remain floor-level because topology changes can split or merge Rooms:

```text
fresh physical Structure scan
    -> match/refresh the existing Structure identity
    -> obtain the fresh exact 3D surface for the affected floor
    -> repartition the complete affected floor into Room components
    -> collect POIs for every resulting registered component
    -> reconcile components with existing Room IDs
    -> atomically commit Structure + Room geometry + POIs
```

This preserves the useful intent of the current `partitionRegistered` /
`RegisteredRoomReconciler` path while removing its dependency on stale flattened floor cells.

### Expected update behavior

- Excavate two additional valid interior cells in a cave, then `Update Room`: the Room footprint
  can expand into them.
- Seal part of the Room: the footprint can shrink or split according to topology.
- Add/remove a bookshelf in an adjacent wall: Room footprint stays unchanged, POIs update.
- Change a door: topology and connector ownership are recalculated with the same generic door
  rule.
- If reconciliation is ambiguous, fail without partially modifying Structure/Room state.

### Building outline

The Blueprint building outline needs no direct scanner mutation. It is already defined as the
one-cell expansion of the union of registered Room footprints. Once an Update Room commit
changes those footprints, refreshed map geometry automatically produces the new outline.

Server-side Village dimensions should continue to refresh through the existing final mutation
boundary.

## 6. Small 3D fixture tests, not a fake Minecraft Level

Do not implement a large fake `Level` that attempts to reproduce Minecraft collision and
BlockState behavior. That would duplicate Minecraft and make tests trustworthy only against the
fake.

Instead split testing at the world-discovery boundary.

### Pure geometry fixtures

Represent discovered world truth using small coordinate fixtures. For example:

```text
y ~= 64

#######
#.....#
#.....D
####B##
```

or an uneven cave surface:

```text
(1,64,1) (2,64,1) (3,65,1) (4,66,1)
```

These tests exercise pure sets/vectors/graphs and should prove:

- connected 3D surfaces become the intended Room components;
- changing Y within one storey does not lose connectivity;
- doors separate topology but contribute one owned floor cell;
- exterior and interior doors use identical ownership logic;
- connector footprint cells remain disjoint between Rooms;
- perimeter evidence includes wall POIs without adding walls to footprint;
- POI evidence does not connect two components;
- updates can expand/shrink/split a floor and reconcile deterministically.

### Minecraft-facing tests

Keep a much smaller integration/GameTest layer for the adapter from Minecraft blocks to physical
surface/portal facts. Representative worlds should include:

1. normal constructed Room with wall bookshelf;
2. cave with uneven surface and bookshelf embedded in a natural wall;
3. two Rooms separated by a door;
4. a door opening to an exterior apron, proving no special outer-door behavior;
5. ladder/trapdoor connecting upper/lower storeys;
6. short collision geometry such as slabs/stairs where Minecraft collision semantics matter.

The pure tests should carry most combinatorial coverage; Minecraft-facing tests prove that real
BlockState/collision behavior is translated correctly.

## 7. Responsibility changes

### `StructureScanner`

Owns only physical discovery:

- real 3D usable surface cells;
- roofs/enclosure/collision checks;
- connectors/portal relationships;
- semantic floor grouping without destroying per-cell Y in the transient result.

It should not own persisted-floor traversal boundaries.

### Structure matching / `VillageManager`

Owns fresh-vs-persisted identity decisions:

- match/refresh a known Structure;
- calculate new-storey delta;
- reject ambiguous ownership;
- enforce attachment target/gap/direction;
- retain detached analysis and atomic commit.

This can initially live as focused helpers in `VillageManager` / `StructureScanner.Result`; a new
class is warranted only if those helpers become a coherent reusable matching unit.

### `BuildingRoomScanner`

Owns:

- Room partitioning from a fresh surface;
- source component selection;
- post-partition connector floor-cell ownership;
- POI candidate geometry construction after topology.

It must not use `BuildingTypes` to decide Room connectivity.

### `Building`

Continues to own configured POI relevance through existing block-recording/type matching logic.
The scanner supplies candidate world positions; `Building` decides which candidates matter.

### `RegisteredRoomReconciler`

Keeps Room identity stable across a whole-floor update and remains responsible for deterministic
split/merge reconciliation rather than moving that logic into the scanner.

## 8. What should be deleted or simplified

The redesign should aim to remove conceptual mechanisms, not layer new behavior on top.

Likely deletions/simplifications after RED tests exist:

- `PersistedFloorBoundary` as a discovery-time ownership mechanism;
- the door exception in `StructureConnector.ownsFloorCell`;
- `FUNCTIONAL_POI` as a Room topology category;
- `attachFunctionalPoiCells(...)` and the rule that POI obstacles become footprint cells;
- BuildingType-aware `hasFunctionalPoiObstacle(...)` from Room partitioning;
- using Room footprint as the only POI scan mask;
- any outer-door/exterior-door classification or heuristic;
- gap reconstruction whose only purpose is repairing geometry already lost by anchor-Y
  flattening, once fresh `FloorSurface` is available to the partitioner.

Some short-gap handling may still be justified by real collision semantics. Keep it only if a
world-facing RED test demonstrates the physical scanner cannot represent the case cleanly.

## 9. Acceptance scenarios

Before implementation is considered complete, reproduce these behaviors in addition to unit
tests:

1. **Cave wall POI:** a bookshelf embedded in the natural wall of a valid cave Room is counted.
2. **Uneven cave floor:** one Room crosses several Y values within one storey without holes or
   anchor-Y misclassification.
3. **Update expansion:** excavating valid floor space then choosing Update Room expands the Room
   and changes the Blueprint outline accordingly.
4. **Update POI-only change:** adding/removing a wall bookshelf updates POIs without changing
   Room floor area.
5. **Exterior door:** the door contributes one floor cell to the interior Room and produces no
   shading hole, without any outer-door branch.
6. **Interior door:** the same ownership rule gives the connector cell to one Room while the
   door still separates Room topology.
7. **Upper/basement attachment:** discovery may see existing + new storeys; matching commits only
   the valid new attachment and preserves the existing target identity.
8. **Ambiguous attachment/overlap:** geometry touching two persisted owners still refuses to
   guess and leaves Village state unchanged.
9. **Reload:** accepted Room footprints, POIs, Structure identity, floor numbers, and logical
   building identity survive save/reload.

## Alternatives rejected

- **Continue patching the existing scanner:** adding wall POI exceptions, door exceptions, and
  more persistence-aware traversal keeps one method responsible for geometry, ownership,
  topology, and evidence.
- **Fake the complete Minecraft `Level`:** expensive mock behavior would duplicate collision
  semantics and can pass while the real game fails.
- **Treat walls/furniture as Room footprint:** fixes POI visibility by corrupting Room area and
  connectivity.
- **Special-case exterior doors:** outside/inside is emergent from adjacent components; a door
  should not need a semantic classification to render correctly.
- **Immediately persist exact 3D floor surfaces:** technically clean but creates an NBT/DFU
  migration before runtime evidence shows it is necessary. Preserve exact Y transiently first.
