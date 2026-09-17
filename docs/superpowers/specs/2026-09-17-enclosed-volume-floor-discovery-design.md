# Enclosed Volume Floor Discovery — Design

Date: 2026-09-17. Status: proposed design for `dev/1.21.1`.

## Goal

Simplify floor discovery around one clear physical model:

1. Discover the enclosed interior space with a bounded 3D flood fill.
2. From that interior, keep the supported cells that act as the floor.
3. Split those supported cells into storeys using the existing storey boundary rules.
4. Treat stairs, ladders and trapdoors as movement/connection evidence rather than alternate floor geometry.

This brings back the useful part of `origin/1.21.1` building scanning — a bounded interior flood fill with roof/enclosure checks — while retaining the newer exact `FloorGeometry` and separate-storey model.

The design must stay simple. There is one world scan, one floor geometry result, and no later pass that invents extra ordinary floor cells.

## Core model

Conceptually a room has a 3D interior volume, while a Floor contains the supported positions a player can stand in.

```text
############ roof
............ interior volume
............ interior volume
FFFFFFFFFFFF floor cells
############ support blocks
```

The flood fill sees the whole enclosed interior. `FloorGeometry` contains only the supported `F` cells.

The full interior volume is transient scan data. It is not persisted and it does not become a second canonical geometry model.

`FloorGeometry` remains the exact persisted floor-cell representation.

## One source of truth

```text
Minecraft world
      ↓
bounded enclosed-volume flood fill
      ↓
supported floor-cell extraction
      ↓
SelectedFloorScanner storey selection
      ↓
FloorGeometry
      ↓
RoomPartitioner
      ↓
RoomScanPlanner / persistence
```

`SelectedFloorScanner` owns fresh world discovery. Once it returns `FloorGeometry`, downstream code may partition, match or persist it, but it may not add ordinary floor cells that the scan did not discover.

## 1. Enclosed interior flood fill

Start from the interaction position after resolving it to a valid interior cell.

Flood in all six block directions:

- north;
- south;
- east;
- west;
- up;
- down.

The fill is bounded by the existing maximum scan size and radius limits.

An interior candidate is accepted when Minecraft collision/fluid state says the position can belong to occupied room space. Ordinary air is the simplest case. Sub-full interior obstacles may still belong to the volume when there is usable room around/above them. Full solid blocks, walls and other structural boundaries stop the fill.

Do not special-case beds, shelves, hoppers, stairs or slabs by block class merely to make them room cells. Use collision shape, support, headroom and connector semantics.

### Enclosure

The fill must not silently turn outside space into a room.

Use cached ceiling/roof checks and bounded exterior detection. A region is enclosed only when the reachable interior cannot escape through a passable route to unroofed/out-of-bounds space.

Leaves alone do not count as a roof, matching the useful behavior from `origin/1.21.1`.

Roof/ceiling is evidence that the space is enclosed. A roof does not create Floor cells by itself.

## 2. Derive Floor cells from the interior

For every interior position, test whether it has a valid standing/support surface below or within the cell using Minecraft collision/path information.

If so, that integer interior position is a Floor-cell candidate.

The integer `BlockPos` is the cell identity. Fractional collision heights from slabs, stairs and other shapes are temporary movement data only.

```text
interior cell (x, y, z)  ← canonical Floor cell when supported
support/collision below  ← physical evidence only
```

The support block itself is not the Floor cell.

### Furniture

Furniture occupies room space; it does not create another floor above itself.

```text
      bed / shelf / carpet
      same room cell
############ support
```

A low or partial obstacle may leave the existing supported cell owned by the Floor when collision/headroom allow it.

A full solid obstruction is not an interior Floor cell.

There must be no generic rule that adds a neighbouring cell merely because it is beside an already discovered Floor cell.

## 3. Storeys and stairs

The flood fill may discover one connected interior volume spanning several storeys. That is expected.

Storey identity is decided from the supported floor candidates, not from the raw air volume.

Keep the current selected-storey boundary behavior initially so this redesign does not also become a staircase-classifier rewrite. The important change is that every candidate comes from the same enclosed-volume/support scan.

Stairs and slabs use their real collision surface to decide whether neighbouring supported cells are physically connected.

```text
upper stable floor  F F F F
                  /
                F
              F
            F
lower stable floor  F F F F
```

The stair cells provide movement between the stable floor regions. They must not cause both storeys to become one Floor simply because a player can walk between them.

No `StairBlock`-specific topology rule is required.

## 4. Doors, trapdoors and ladders

Horizontal doors/gates remain Room boundaries. They may connect two enclosed areas without making both sides one Room component.

Trapdoors and ladders are vertical connector evidence.

They do not manufacture unsupported structural Floor cells. Instead they link the supported landing/storey on one side to the supported landing/storey on the other side.

If interaction occurs on a ladder/trapdoor exit where the exact interaction cell has no normal support, the scanner may resolve that interaction to the linked canonical Floor. This is an interaction handoff, not a second way to create Floor geometry.

This should replace the need for generic `includeInteriorMembership()` expansion.

## 5. `includeInteriorMembership()`

The current post-pass is a competing source of floor ownership because it can add cells after the main traversal has already decided the storey.

The target design removes generic neighbour-based membership expansion.

After the enclosed-volume scan and supported-cell extraction, an ordinary Floor cell can only exist because the world scan discovered it directly.

Any remaining connector interaction handoff must be narrow, named explicitly, and must not add ordinary furniture/obstacle cells to `FloorGeometry`.

The full-height partial-obstacle regression added in `c4101f240` remains a required contract. A shelf/hopper or similar partial block must not create an overlapping cell in another storey merely because it is adjacent to an existing cell.

## 6. Rooms

Conceptually, a Room owns an enclosed 3D interior volume.

We do not need to persist every air block to represent that idea. The flood-filled volume is used during discovery, while persisted Room ownership continues to use exact Floor cells plus existing vertical/ceiling information.

`RoomPartitioner` partitions one canonical Floor. It does not run a second world scan and it does not discover another storey.

POIs/furniture can be associated with a Room by checking whether they occupy that Room's discovered/persisted vertical space. POI presence never creates Floor topology.

## 7. Persistence

This design does not require a save-format change.

Keep:

- `FloorGeometry` exact integer cells;
- `StructureFloor` IDs and floor numbers;
- Room IDs and exact floor-cell ownership;
- logical building IDs;
- connector metadata required for persisted interaction/attachment behavior;
- existing `RoomDFU` behavior.

The enclosed interior flood fill is recomputed from the live world when scanning.

## 8. Relationship to earlier designs

This design keeps the strongest rules from:

- `2026-09-08-exact-cell-floor-scanner-simplification-design.md`: exact integer Floor cells, transient collision heights, one scanner owner;
- `2026-09-16-floor-ownership-consistency-design.md`: `SelectedFloorScanner` owns fresh storey membership and downstream layers consume it;
- `origin/1.21.1` `Building.validateBuilding`: bounded flood fill with cached roof checks.

Where this design differs is the order of discovery: first establish the enclosed 3D interior, then derive supported floor candidates from that same observation.

This document supersedes the generic post-traversal interior-membership expansion described/implemented after the September 8 model. It does not supersede persistence, action-planning or logical-building rules from the September 16 ownership spec.

## 9. Required behavior

The implementation must preserve or prove all of these cases:

1. A plain closed room flood-fills its complete interior and produces the expected supported Floor cells.
2. An opening to outside is not accepted as enclosed interior.
3. A high ceiling produces one Floor layer, not a Floor cell at every Y.
4. Beds and carpets do not change the room Floor footprint.
5. Full-height partial obstacles such as the hopper regression do not manufacture another storey's Floor cell.
6. The copied-house cherry shelf does not create overlap between the basement/lower storey and the main storey.
7. Stairs connect neighbouring storeys without merging their stable Floor regions.
8. Slabs and ordinary one-block terrain steps use collision/step rules and keep the correct integer Floor cells.
9. A ladder/trapdoor links the correct storeys without inventing unsupported ordinary Floor geometry.
10. Interaction from a valid connector exit resolves to the linked Floor.
11. Doors remain Room boundaries while valid doorway floor space remains owned deterministically where appropriate.
12. Successive lower storeys and the basement in `CopiedOpenHouseGameTests` remain one logical building through the real staircase chain.
13. Caves/uneven enclosed terrain are not split solely because supported cells use nearby Y values.
14. Scan size/radius limits still fail safely rather than allowing an unbounded flood fill.

## 10. Implementation constraints

- Work on `dev/1.21.1` first; merge forward only after the canonical branch is correct.
- Start with focused red GameTests before changing scanner behavior.
- Do not special-case `ShelfBlock`, `HopperBlock`, `BedBlock` or a particular copied-house coordinate.
- Do not add a second persisted geometry type.
- Do not add a generic graph framework or public topology abstraction.
- Reuse `FloorCeilingResolver`, collision/path sampling and the existing scan-local caches where they still fit.
- Remove obsolete membership helpers rather than preserving two ways to add Floor cells.
- Keep the implementation smaller/easier to explain than the current competing traversal + membership-expansion model.

## Acceptance

The redesign is ready to merge when:

- one bounded world observation discovers the enclosed interior;
- all ordinary Floor cells are derived from supported positions in that observation;
- `includeInteriorMembership()` no longer adds generic neighbouring Floor cells;
- stairs/slabs retain correct movement behavior and separate storeys;
- ladders/trapdoors connect storeys through explicit connector handoff;
- furniture does not manufacture topology;
- the copied-house shelf overlap is gone without a block-specific exception;
- existing floor/room persistence remains compatible;
- focused floor tests pass;
- common tests and Fabric/NeoForge compilation pass;
- one final NeoForge GameTest run reaches `All N required tests passed :)`, with any unrelated flaky failure recorded separately rather than hidden.
