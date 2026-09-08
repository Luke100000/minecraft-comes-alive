# Floor Scanner Simplification Specification

> **Spatial model superseded:** use
> `2026-09-08-exact-cell-floor-scanner-simplification-design.md` as the
> canonical Floor/Room spatial contract. Older transient-surface and owned-door
> rules below are historical requirements, not current implementation guidance.

Date: 2026-08-30

Status: Approved implementation specification

Supersedes:

- scanner sections 5-6 of `2026-08-30-blueprint-map-and-floor-scan-corrections-design.md`;
- scanner Tasks 5-6 of `2026-08-30-blueprint-map-and-floor-scan-corrections.md`;
- the architectural draft in `2026-08-30-floor-scanner-simplification-design.md` where this
  specification is more precise.

The already implemented Blueprint camera/rendering/tooltip/Room-derived-outline work is not
redesigned here.

## 1. Problem statement

The current floor system mixes five different concerns inside scanner code:

1. discovering enclosed physical Structure geometry;
2. deciding which persisted Structure owns discovered geometry;
3. partitioning that geometry into Rooms;
4. assigning connector and furniture cells to Room footprints;
5. finding configured POIs.

That coupling has produced special-case behavior and several user-visible failures:

- a bookshelf embedded in a cave or constructed boundary wall is not registered because POI
  discovery is limited to existing Room footprint columns;
- naturally uneven cave floors are flattened to a single floor `anchorY` before Room topology is
  decided;
- `Update Room` can re-partition stale persisted geometry instead of making the Room agree with
  the current world;
- planned upper-floor/basement scans need persistence-aware traversal rules to avoid rediscovering
  an existing storey;
- doors have accumulated ownership exceptions even though exterior and interior doors should
  follow one rule;
- physical discovery requires a roof within `ROOF_SEARCH = 16`, so a fully enclosed tall hall can
  be rejected solely because the ceiling is too high;
- enclosure checks currently flatten too much context into fixed assumptions: every selected-floor
  cell needs its own real ceiling/exposure result, so tall, stepped, sloped, overhanging, or vaulted
  roofs are not forced through one global ceiling Y or a fixed-height probe;
- `maxBuildingSize` is currently charged against interior air volume, so a tall but otherwise
  ordinary building can hit the block limit simply because it has more empty headroom.

The replacement must simplify the conceptual model rather than adding more exceptions.

## 2. Required architecture

The scanner pipeline is:

```text
Minecraft world
    -> selected-floor exact 3D surface discovery + local ceiling/exposure evidence
    -> persisted Structure matching
    -> Room topology
    -> connector footprint ownership
    -> Room POI evidence
    -> persisted Room / logical-building presentation
```

Data flows only downward through these stages.

Every scan is scoped to **one semantic floor** selected by the interaction/action. A normal
building scan, Room scan, or `Update Room` never auto-discovers or registers upper floors or
basements. Additional storeys are added only through the explicit `ADD_FLOOR` / `ADD_BASEMENT`
flows, which perform the same pipeline again for the manually selected destination floor.

### 2.1 Core invariant

The following sets have different meanings and must never be used interchangeably:

- **enclosure evidence**: local roof/ceiling and exterior-exposure facts used to validate the
  selected floor without registering or flood-filling another storey;
- **surface cells**: real supported usable floor positions on the selected semantic floor at their
  actual world height;
- **portals/connectors**: transitions or boundaries associated with the enclosure/surfaces;
- **Room topology cells**: usable surface cells participating in Room connectivity;
- **Room footprint cells**: X/Z cells owned for Room persistence/presentation;
- **POI evidence**: world blocks associated with a Room that may lie on its floor, inside it, or
  in its boundary/perimeter;
- **persisted Structures**: saved physical identity/ownership.

Consequences:

- POI relevance never changes physical discovery or Room connectivity.
- Persistence never hides physical geometry from fresh discovery.
- Connector footprint ownership never turns a connector into a topology bridge.
- Empty interior air does not count as Room/Structure floor area.

## 3. Physical discovery

### 3.1 Selected-floor discovery retains local 3D enclosure evidence

Fresh Structure discovery starts from the semantic floor selected by the interaction/action and
must answer both:

> Which supported surface cells belong to the selected floor?

and:

> What local ceiling and exterior-exposure evidence validates those cells as enclosed?

The first answer defines the floor that may be registered. The second supplies local ceiling,
exposure, wall/perimeter, and POI evidence for that floor. Enclosure evidence must never cause
another storey or vertically separated supported surface to be registered automatically.

This distinction matters for irregular cross-sections. For example:

```text
       /
      /
     |
_____|   selected floor
```

The enclosing wall/roof can move horizontally as Y increases. The scanner must therefore preserve
the selected floor's real per-cell Y and resolve ceiling/exposure independently for each surface
cell instead of assuming one global roof height or one flattened floor plane. A genuinely enclosed
interior surface column will eventually meet a physical boundary when probed upward; irregular
geometry changes that boundary Y from column to column, not the fact that the selected floor is
scanned independently.

A suspended platform inside the same enclosure is a useful secondary regression case: whichever
surface the action selects may be scanned, but the existence of another supported surface must not
change or steal the selected floor.

The implementation may inspect open interior cells for local exposure decisions, but the Structure
scan must not materialize the complete 3D interior-air volume as its primary data model. Minecraft
`Level#getBlockState` resolves through full chunks, and a literal air-volume flood would make tall
halls disproportionately expensive and could let an unfinished/open storey above affect whether the
current floor exists. Prefer the exact selected-floor surface plus cached per-column ceiling and
selected-floor exposure evidence.

Solid slabs/floors that genuinely separate enclosed volumes still separate physical space unless a
real opening/connector joins them.

### 3.2 Floor selection and manual storey additions

Vertical connectors identify relationships between storeys; they do not make an ordinary scan
traverse and register every connected storey.

Required behavior:

- `ADD_BUILDING`, `ADD_ROOM`, and `UPDATE_ROOM` scan only the semantic floor containing their
  selected interaction/scan seed;
- ladders and trapdoors may be recorded as connectors, but ordinary Room/Structure traversal stops
  at the floor boundary instead of climbing to another storey;
- `ADD_FLOOR` explicitly selects/scans the intended upper destination floor;
- `ADD_BASEMENT` explicitly selects/scans the intended lower destination floor;
- the newly scanned floor is then matched/attached to the existing logical building;
- no action implicitly registers an unrequested floor merely because it is vertically reachable.

### 3.3 No fixed maximum ceiling height

Delete the semantic dependency on `ROOF_SEARCH = 16`.

A position is not `NO_ROOF` merely because the first enclosing solid block is more than 16 blocks
above it. Roof/enclosure probing must terminate using Minecraft's dimension-specific
`LevelHeightAccessor#getMinBuildHeight()` / `getMaxBuildHeight()` bounds and scanner bounds, not an
arbitrary building-height constant.

Required behavior:

- a sealed cobblestone box 10 blocks tall and the same box 30 blocks tall are both physically
  discoverable, assuming all other configured Structure limits are satisfied;
- changing the roof height alone must not change whether the ground floor is considered inside;
- open sky still proves an uncovered/exterior column;
- leaves remain excluded from roof evidence according to the current rule unless separately
  redesigned later.

Roof/ceiling results should be cached by column/position during one scan so tall buildings do not
cause repeated full-height probes for every neighbor.

Do not use a Minecraft heightmap or `canSeeSky(...)` as authoritative enclosure/roof truth:

- heightmaps expose the topmost qualifying block in a column, not the nearest ceiling above an
  interior/cave surface;
- `canSeeSky(...)` is a skylight query, not a collision/enclosure query, and therefore does not
  express the MCA rule that a collidable transparent glass roof is still a physical roof.

Heightmaps may be used only as a safe optimization hint when their limitations do not change the
result. Actual roof/enclosure acceptance is based on the world `BlockState`/collision geometry at
the relevant position.

### 3.4 Semantic size is surface geometry, not air volume

`Config.maxBuildingSize` remains the semantic limit for accepted building/floor geometry. It must
not be consumed once per empty interior air block between floor and ceiling.

Therefore:

- Room/Structure floor area is based on discovered supported surface/footprint cells;
- a tall atrium does not become "larger" simply because it contains more vertical air;
- implementation work-memory used to establish enclosure is not persisted as building area;
- any separate traversal-safety budget introduced for pathological worlds must be explicitly named
  as a scan-work limit and must not masquerade as `maxBuildingSize`.

Do not fix the existing bug by merely increasing `ROOF_SEARCH` or multiplying
`maxBuildingSize` by an arbitrary height factor.

### 3.5 Exact transient surface model

Fresh discovery must retain actual per-cell vertical geometry until Room topology and POI evidence
have consumed it.

The implementation should expose one immutable transient model equivalent to:

```java
record SurfaceCell(
        int x,
        int y,
        int z,
        double surfaceY,
        int ceilingY) {
}

record FloorSurface(
        Set<SurfaceCell> cells,
        Set<Portal> portals) {
}
```

Exact names/record layout may differ, but the result must preserve:

- the real feet/support column `(x, y, z)`;
- enough collision-derived height information for slab/stair transitions;
- a usable local ceiling/enclosed-height bound for POI scanning;
- connector/portal relationships without converting those connectors into ordinary topology cells.

Collections leaving discovery should be immutable snapshots (`Set.copyOf`, `List.copyOf`, or
equivalent).

For the collision-derived floor height, prefer Minecraft's existing public
`WalkNodeEvaluator.getFloorLevel(BlockGetter, BlockPos)` semantics rather than maintaining a second
copy of the formula. In 1.21.1 vanilla this resolves the supporting block's collision shape and
returns `supportY + collisionShape.max(Y)`. Vanilla ground pathfinding also uses `1.125` as its
default jump/step height threshold, so an MCA surface-transition threshold with that value is
Minecraft-derived behavior rather than an arbitrary scanner constant.

### 3.6 Cave and irregular-floor behavior

No material or biome concept distinguishes a cave from a house.

A cave surface is accepted using the same support, collision, enclosure, and traversal rules as a
constructed floor. There must be no special `CaveBlock`, stone whitelist, sea-level check, or
terrain-height heuristic.

These cells may all belong to one semantic storey while keeping their original Y values:

```text
(10,64,10)
(11,64,10)
(12,65,10)
(13,65,10)
(14,66,10)
```

Storey grouping may use the existing small vertical tolerance, but grouping must not project every
cell onto one `anchorY` until all fresh topology/POI operations that require exact height are done.

### 3.7 Minecraft collision semantics remain authoritative

Physical discovery continues to use real `BlockState` collision/fluid/support behavior. Do not
replace Minecraft collision with a hand-maintained block whitelist.

Stairs, slabs, modded collision shapes, ladders, trapdoors, doors, and fence gates are handled from
their world state and connector semantics.

Connector semantics deliberately take precedence over "empty collision means generic air". In
Minecraft 1.21.1, open doors, trapdoors, and fence gates change collision/pathfindability state;
vanilla pathfinding still classifies doors explicitly (`DOOR_OPEN`, closed-door types,
`WALKABLE_DOOR`). MCA must likewise preserve connector identity across open/closed state so opening
an exterior door or gate cannot make a registered enclosure leak into the outside world or merge
two Rooms.

## 4. Persisted Structure matching

### 4.1 Matching happens after complete target-floor discovery

Fresh discovery must not consult persisted floor cells as traversal walls.

Example:

```text
manual ADD_BASEMENT target: fresh floor 74
persisted attached floor:  floor 77
matching result:            attach 74 below logical building containing 77
```

Floor `77` is matching/attachment evidence; it is not rediscovered as part of the floor-74 scan.
Likewise an `UPDATE_ROOM` on floor `77` fresh-scans floor `77` only.

This removes `PersistedFloorBoundary` as an architectural concept. It may exist temporarily during
incremental implementation, but final scanner behavior must not depend on persistence-aware world
discovery. Ordinary scanning is already floor-scoped, and explicit floor/basement actions select
their destination floor directly.

### 4.2 Matching outcomes

The matching/orchestration layer must be able to classify discovered geometry as:

- refresh of exactly one known Structure floor;
- one genuinely new manually requested floor attached to exactly one logical building;
- a new standalone Structure/floor;
- real overlap with another persisted owner;
- ambiguous ownership touching multiple plausible persisted Structures.

### 4.3 Safeguards that must remain

Preserve the existing safety boundaries:

- a same-storey fresh scan overlapping exactly one persisted Structure may refresh that Structure
  instead of duplicating it;
- fresh geometry overlapping/touching multiple persisted Structures ambiguously must refuse to
  guess;
- a true conflict with another persisted owner still returns `OVERLAP`;
- upper/basement attachment still validates requested logical building, nearest/unique target,
  vertical gap, and requested direction;
- upper/basement attachment does not auto-register any additional unrequested storey encountered
  through connectors or shared enclosure;
- analysis produces detached candidate data only;
- Village/Structure/Room state mutates only at the existing atomic commit boundary.

## 5. Room topology

### 5.1 Input

`BuildingRoomScanner` consumes the fresh exact `FloorSurface` for one semantic storey. It must not
reconstruct fresh topology from `BuildingFloorRegion.cells()` flattened to `anchorY` when the world
is available.

### 5.2 Responsibility

The Room topology stage only:

1. determines usable surface cells;
2. partitions them into connected components;
3. treats connector/boundary columns according to portal semantics;
4. resolves the interaction source to one component;
5. returns component geometry for later footprint ownership and POI collection.

It does not query `BuildingTypes` to decide connectivity.

### 5.3 Non-topology cells

The following do not connect otherwise separate Room components:

- walls;
- functional POI/furniture blocks;
- doors;
- fence gates;
- ladders;
- trapdoors.

Low collision geometry may still yield a usable surface through the physical discovery stage. That
is collision semantics, not BuildingType semantics.

## 6. Connector footprint ownership

### 6.1 Doors own one floor cell

Doors must contribute one Room footprint cell so map/Room fills do not contain a visual hole.

That ownership occurs **after** ordinary Room components have been partitioned.

The ownership algorithm is:

1. identify the connector floor cell associated with the semantic storey;
2. find adjacent Room components;
3. assign the cell to at most one component;
4. do not use the assigned cell to merge/connect those components.

Minecraft doors are two-block structures (`DoorBlock.HALF`). Normalize a door connector to its
lower half before deriving connector identity/footprint association so one placed door cannot be
treated as two independent connectors or contribute two Room cells.

### 6.2 One rule for interior and exterior doors

There is no `isOuterDoor`, `isExteriorDoor`, or equivalent special path.

For any connector floor cell with multiple adjacent components, owner selection is deterministic:

1. largest adjacent component area first;
2. `minX`;
3. `minZ`;
4. `maxX`;
5. `maxZ`.

Thus a door between a large enclosed interior and a tiny exterior apron naturally shades as part of
the interior Room without classifying the door itself as exterior.

An interior door between two valid Rooms uses exactly the same rule: one Room owns the footprint
cell, while the door remains a topology boundary between both Rooms.

### 6.3 Connector helper semantics

`StructureConnector.ownsFloorCell(BlockState)` must not encode the current "all connectors except
doors" exception. Prefer removing the boolean entirely in favor of a relation such as
`associatedFootprintCells(connector, floorSurface)`: whether a connector contributes a Room cell is
defined by how that connector projects onto an actual semantic surface, not by a context-free block
property. This matters for Minecraft's different connector shapes: a two-block door, one-block
fence gate, top/bottom trapdoor, and wall-mounted ladder do not occupy floor geometry in the same
way.

If a simpler helper remains, its name and contract must describe generic connector
projection/footprint eligibility. Interior/exterior classification is prohibited.

## 7. POI evidence

### 7.1 POIs are collected after topology

POI blocks are evidence associated with a Room; they are not Room topology cells.

Delete the requirement that a functional POI/furniture column must be inserted into the Room
footprint merely so it can later be counted.

### 7.2 Candidate evidence set

For each accepted Room component, build POI candidate world positions from:

1. each real interior surface column;
2. the supporting block below each surface cell;
3. the one-block horizontal perimeter around the Room component;
4. blocks occupying interior and perimeter columns from the local surface height through the local
   enclosed/ceiling range;
5. associated connector/portal blocks where they lie in those boundary columns.

Then pass candidates through the existing `Building.recordBuildingBlock(...)` /
`BuildingTypes` relevance filter.

The scanner must not duplicate BuildingType matching logic.

### 7.3 Wall POIs

A wall is not a special block category. A wall is simply perimeter evidence adjacent to a Room.

```text
####B##       B = bookshelf embedded in boundary wall
#.....#
#.....D       D = door
#######
```

Required result:

- `B` is available to BuildingType/POI matching;
- the wall column is not added to Room topology;
- irrelevant wall stone/brick is discarded by the existing relevance filter;
- this behaves identically for a cave wall and a constructed wall.

## 8. `Update Room`

### 8.1 Meaning

`Update Room` means:

> Make the selected registered Room's current topology agree with the world again, including any
> genuine split of that Room into multiple Rooms.

It is not limited to recounting POIs inside the old Room footprint, but it is also not a
whole-floor mutation. Reconciliation is scoped to the **selected Room lineage**: the selected old
Room plus fresh components descended from its old footprint. Neighboring registered Rooms are
read-only constraints and never join that reconciliation set.

### 8.2 Required update pipeline

```text
fresh physical Structure discovery
    -> match/refresh existing Structure identity
    -> obtain exact fresh 3D surface for affected storey
    -> partition fresh topology around the selected Room
    -> keep only fresh components that overlap the selected old Room footprint
    -> reject any lineage component that consumes another registered Room's footprint
    -> assign connector footprint cells + collect POI evidence for the lineage components
    -> reconcile those components against the selected old Room identity
    -> atomically commit the reconciled Room lineage + required Structure geometry/POIs
```

`Update Room` never discovers or registers an upper floor/basement. It refreshes only the
selected registered Room and any Rooms created by a genuine split of that Room. Other already
registered Rooms on the same floor and all other storeys remain untouched unless the player
explicitly updates/adds those Rooms through their own action.

The scanner may inspect adjacent/current-floor geometry to determine where the selected Room now
ends. That inspection does not authorize mutation of the other registered Rooms it observes.

### 8.3 Expected behavior

- Excavating valid cave floor space and choosing `Update Room` may expand the Room.
- Sealing part of a Room may shrink it.
- If the old Room has become disconnected, every fresh component that still overlaps the old Room
  footprint participates in reconciliation. The interaction/source component keeps the selected
  Room identity; additional valid split-off components become newly registered Rooms with new IDs.
- A split-off Room is created only from geometry descended from the selected old Room. The update
  must not discover an arbitrary unrelated component elsewhere on the floor and register it merely
  because the whole floor was inspected.
- If removing a separating wall would make the selected Room consume cells already owned by
  another registered Room, reject the update with overlap/ambiguity instead of merging or rewriting
  that neighboring Room.
- Adding/removing a bookshelf in a boundary wall updates POIs without changing floor area.
- Changing a door recalculates topology and connector ownership for the selected Room lineage, but
  may not steal a footprint cell already persisted by another registered Room; such a conflict is
  rejected.
- The source-selected/best matching component keeps the selected Room's existing ID/type/forced
  state/contribution state. Newly created split Rooms receive new IDs and resolve their type through
  the normal Room type rules, inheriting contribution semantics from the selected Room as today.
- Any ambiguity or overlap fails with no partial Structure/Room mutation.

### 8.4 Building outline

No scanner-specific outline update code is required.

The Blueprint building outline is already defined from the union of registered Room footprints.
After an accepted Room update changes those footprints, refreshed Blueprint geometry naturally
changes the building outline.

Village dimensions continue to refresh through the existing final mutation boundary.

## 9. Persistence compatibility

No NBT/DFU migration is required for the first implementation of this specification.

The current compact `BuildingFloorRegion` representation may remain the persisted X/Z storey
projection.

Rules:

- fresh world operations use exact transient 3D surfaces;
- accepted Rooms may still persist through the existing footprint representation;
- operations needing current vertical geometry (`Update Room`, Structure rescan, additions) rescan
  the selected floor in the world instead of treating the saved anchor-Y projection as exact 3D
  truth;
- exact per-column Y persistence is a separate future migration only if runtime evidence shows it
  is required without a world rescan.

## 10. Responsibility map

### `StructureScanner`

Owns physical discovery only:

- enclosure/open-space discovery;
- real supported 3D surface cells for one selected semantic floor;
- local ceiling/enclosure information;
- Minecraft collision/support checks;
- portal/connector relationships;
- selected-floor grouping while retaining per-cell height in the transient result.

It does not own persisted-floor traversal barriers or BuildingType-aware Room rules.

### Structure matching / `VillageManager`

Owns:

- fresh-vs-persisted Structure identity;
- refresh vs explicitly requested new-floor decisions;
- ambiguity and overlap validation;
- target logical-building validation;
- attachment gap/direction validation;
- detached analysis and atomic commit orchestration.

Do not create a new abstraction solely for layering aesthetics. Extract a dedicated matcher class
only if the resulting matching helpers form a coherent reusable unit.

### `BuildingRoomScanner`

Owns:

- Room partitioning from exact fresh surfaces;
- source component selection;
- post-partition connector footprint ownership;
- construction of POI candidate geometry after topology.

It does not own BuildingType matching.

### `Building`

Continues to own configured POI relevance through existing block/type matching.

### `RegisteredRoomReconciler`

Keep this helper, but narrow its contract from whole-floor reconciliation to a caller-supplied
reconciliation scope. Normal `Update Room` passes exactly one previous Room (the selected Room) and
the fresh components in that Room's lineage. This preserves stable identity and split handling
without giving the reconciler authority over neighboring registered Rooms.

## 11. Required simplifications/deletions

The implementation should reduce concepts rather than preserve old mechanisms behind new names.

Once equivalent RED tests exist, remove or simplify:

- `PersistedFloorBoundary` as a discovery-time ownership rule;
- fixed-height `ROOF_SEARCH` semantics;
- counting vertical interior air toward `maxBuildingSize`;
- the door-specific negative `ownsFloorCell` exception;
- `FUNCTIONAL_POI` as a Room topology category;
- `attachFunctionalPoiCells(...)`;
- BuildingType-aware `hasFunctionalPoiObstacle(...)` in Room partitioning;
- Room footprint as the sole POI scan mask;
- outer/exterior-door classification or heuristics;
- gap reconstruction whose only purpose is repairing geometry lost by early anchor-Y flattening.

Do not remove a collision/short-gap rule merely because it looks old. Keep it if a focused real
Minecraft behavior test demonstrates that it represents actual collision semantics rather than a
workaround for flattened geometry.

## 12. Coding constraints

The implementation must follow the applicable shared rules from the reviewed coding standards:

- favor clear, descriptive names over mode booleans and overloaded sets;
- use immutable records/snapshots at stage boundaries;
- keep scanner methods focused; do not grow one larger all-purpose `scan(...)` method;
- prefer straightforward loops over deeply nested stream pipelines in traversal code;
- use named limits only when they represent real product/configuration semantics;
- do not introduce speculative generic frameworks, repository/service layers, or a complete fake
  Minecraft `Level`;
- preserve explicit validation results instead of swallowing failures;
- preserve detached analysis and atomic mutation as the transaction boundary.

Framework-specific Spring/Quarkus and Node/Next conventions are not applicable to this Minecraft
codebase.

## 13. Test strategy

### 13.1 Pure geometry/topology fixtures

Most combinatorial behavior should be tested below the Minecraft-world adapter using compact 3D
coordinate fixtures, not a fake `Level` implementation.

Fixtures must cover:

- one uneven cave Room across several Y values;
- two Room components separated by a door;
- exterior-apron and interior-door cases using the same owner rule;
- connector footprint cells remaining disjoint between Rooms;
- perimeter POI evidence including a wall bookshelf;
- POI evidence not connecting Room components;
- selected-Room expansion/shrink without mutating neighboring registered Rooms;
- selected-Room split behavior: source-selected component keeps the old ID and additional components
  overlapping the old selected footprint are created/reconciled as new Rooms;
- unrelated fresh components elsewhere on the floor are excluded from the selected Room lineage;
- attempted merge/overlap into another registered Room is rejected instead of rewriting it;
- persisted matching of existing vs new storeys;
- explicit manual floor/basement attachment without auto-discovering neighboring storeys;
- ambiguous fresh geometry touching two owners.

### 13.2 Minecraft-facing tests

Use a small number of real block/collision integration tests or GameTests for behavior that depends
on Minecraft itself:

1. ordinary constructed Room with a boundary-wall bookshelf;
2. cave with uneven supported surface and bookshelf in natural wall;
3. two Rooms separated by a door;
4. door opening to a tiny exterior apron;
5. ladder/trapdoor marking an upper/lower relationship while an ordinary scan remains on the
   selected floor, followed by an explicit `ADD_FLOOR` / `ADD_BASEMENT` scan of the destination;
6. slabs/stairs or another non-full collision surface;
7. sealed tall hall whose roof is more than 16 blocks above the ground floor;
8. stepped/sloped/overhanging wall or roof whose boundary shifts horizontally with Y, proving that
   selected-floor cells keep independent local ceiling heights instead of one flattened roof Y;
9. small floating platform inside a tall enclosure as a secondary floor-selection regression: it
   must not steal or merge with the selected floor;
10. same hall with an actual opening to sky, which must fail enclosure where appropriate.

Do not build a large mock Minecraft world that reimplements collision/roof behavior.

The NeoForge development run already enables MCA's GameTest namespace through
`neoforge.enabledGameTestNamespaces`, so NeoForge GameTests are a natural place for these real-world
fixtures. Do not require a duplicate Fabric GameTest harness merely for symmetry; common pure tests
plus one real Minecraft integration lane are sufficient unless loader-specific behavior is being
tested.

## 13.3 Minecraft 1.21.1 source audit

This specification was checked against the local mapped Minecraft 1.21.1 source. Relevant engine
behavior supports the design:

- `LevelHeightAccessor` exposes dynamic min/max build height; no 16-block roof assumption exists in
  vanilla world bounds.
- `WalkNodeEvaluator.getFloorLevel(BlockGetter, BlockPos)` already implements collision-derived
  surface height, and vanilla ground navigation uses a `1.125` default jump-height threshold.
- `DoorBlock`, `TrapDoorBlock`, and `FenceGateBlock` all encode open/closed state explicitly, while
  vanilla pathfinding retains semantic door/path node types instead of reducing doors to generic
  air.
- `DoorBlock` stores upper/lower halves, requiring connector normalization for one logical door.
- `Heightmap` stores only topmost qualifying column height, so it cannot represent a cave's nearest
  interior ceiling.
- `BlockAndTintGetter.canSeeSky` is derived from sky-light brightness, so it is not equivalent to a
  collidable roof test.
- `Level#getBlockState` resolves through a `LevelChunk`, reinforcing the requirement to avoid a
  needlessly cubic full-air-volume scan for tall buildings.

These findings refine implementation choices but do not change the architecture in section 2.

## 14. Mandatory acceptance scenarios

Implementation is not complete until these are reproduced against real runtime behavior where
applicable:

1. **Tall sealed hall**: a fully enclosed building remains discoverable with a roof >16 blocks
   above the ground floor.
2. **Irregular 3D enclosure**: a valid selected floor remains correctly enclosed when walls/roof
   step, slope, overhang, or vault so local ceiling heights differ across the floor. The scanner
   must not flatten those cells to one ceiling Y or impose a fixed roof-search distance.
3. **Tall-space size accounting**: increasing ceiling height without changing usable surface area
   does not independently trigger `BLOCK_LIMIT`.
4. **Cave wall POI**: a bookshelf embedded in a natural cave boundary is counted.
5. **Uneven cave floor**: one Room spans several valid Y values without anchor-Y holes or false
   separate floors.
6. **Update expansion**: excavating valid interior surface then `Update Room` expands the Room and
   the Blueprint outline follows the new Room union.
7. **Update POI-only change**: adding/removing a boundary-wall bookshelf updates POIs without
   changing Room area.
8. **Update isolation**: changing the selected Room never changes a neighboring registered Room's
   footprint, ID, type, POIs, or connector ownership. If any fresh selected-lineage component would
   consume that neighbor, the update is rejected.
9. **Update split reconciliation**: placing a divider inside the selected Room and choosing
   `Update Room` keeps the old Room ID on the source/best-matching component and creates/reconciles
   the other split-off component(s) without changing unrelated registered Rooms.
10. **Exterior door**: the door contributes one floor cell to the interior Room with no shading hole
   and no outer-door branch.
11. **Interior door**: the same ownership algorithm assigns the cell to one Room while the door
   remains a topology boundary.
12. **Upper/basement attachment**: `ADD_FLOOR` / `ADD_BASEMENT` scans only the explicitly selected
    destination floor, validates it against the existing logical building, and commits that one
    attachment while preserving target identity.
13. **Ambiguous ownership**: fresh geometry that plausibly belongs to two persisted Structures is
    rejected without guessing.
14. **Reload**: accepted Structure identity, floor numbers, Room footprints, Room IDs/types, POIs,
    and logical-building identity survive save/reload.

## 15. Diagnostics requirements

Verbose building diagnostics should report failures by stage so scanner regressions are actionable.

At minimum, diagnostics should distinguish:

- source could not resolve to an enclosed physical space;
- physical scan exceeded a real configured radius/size/work limit;
- no supported surface was discovered;
- fresh-vs-persisted matching was ambiguous;
- real persisted overlap;
- Room component too small/overlapping;
- selected-Room update would consume or conflict with another registered Room.

Do not report `NO_STRUCTURE` for every upstream physical-discovery failure when a more precise
reason is already available internally.

## 16. Non-goals

This specification does not require:

- persisting exact 3D per-column floor heights;
- an NBT/DFU migration;
- arbitrary detection of disconnected buildings through solid barriers;
- automatic discovery/registration of every upper floor or basement connected to the current
  floor;
- special cave materials;
- special exterior-door classification;
- a second BuildingType matching implementation in the scanner;
- a complete fake Minecraft `Level`;
- changes to the already accepted Blueprint camera/rendering architecture.

## 17. Alternatives rejected

- **Increase `ROOF_SEARCH` to 32/64/256**: merely moves an arbitrary height failure.
- **Scale `maxBuildingSize` by height**: keeps empty air coupled to semantic building area and adds
  another magic factor.
- **Continue patching current scanner paths**: preserves one method/set for discovery, persistence,
  topology, connector ownership, and POIs.
- **Treat walls/furniture as Room footprint**: makes POIs visible by corrupting Room area and
  connectivity.
- **Special-case exterior doors**: exterior/interior ownership emerges from adjacent components;
  the door needs no semantic classification.
- **Persisted-floor traversal barriers**: persistence is identity evidence and belongs after fresh
  physical discovery.
- **Immediate exact-3D save migration**: unnecessary until runtime evidence proves transient exact
  geometry plus world rescan is insufficient.
- **Full fake Minecraft world**: duplicates collision semantics and can pass while real block
  behavior fails.

## 18. Completion rule

A green compile/test run is necessary but not sufficient.

The scanner redesign is complete only when:

- focused pure tests pass;
- Minecraft-facing collision/enclosure tests pass;
- the mandatory runtime scenarios above have been exercised;
- old special-case mechanisms superseded by this specification are actually removed rather than
  left dormant beside the new path;
- no unrelated user work in the repository is reset, cleaned, staged, or committed as part of the
  scanner work.
