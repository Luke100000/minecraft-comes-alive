# Room Boundary POI Ownership Design

Date: 2026-09-21

Status: proposed contract hardening for the current exact Floor/Room model.

## Goal

Lock down and preserve the Room model's existing support for configured POIs that physically occupy a wall or other immediate Room-boundary block, without turning that wall block into `FloorGeometry` or allowing POIs to redefine Floor topology.

The intended mental model is:

```text
Minecraft world
    -> SelectedFloorScanner discovers one enclosed structural Floor
    -> FloorGeometry stores exact owned Floor cells
    -> RoomPartitioner divides that Floor into Room components
    -> RoomPoiEvidence derives interior + immediate boundary positions that may contain POIs
    -> Building.applyRoomScan records only configured POI blocks found at those candidate positions
```

A POI may belong to a Room without being a Floor cell.

## User-facing definition

A Floor is the canonical enclosed floor area owned by the Room/storey. It represents structural spatial ownership, not merely positions that are currently easy to walk through and not every block that belongs visually to the Room.

A POI is evidence associated with that Room. It may be:

- on an owned Floor column;
- on the support level immediately below an owned Floor cell where existing scanning semantics require it;
- on the first boundary/wall column immediately adjacent to an owned Floor cell, within that cell's physical vertical interval.

Therefore a bookshelf, lectern, barrel, smoker, bed, or other configured POI may be embedded in or attached to the Room wall and still count for the Room even though the wall block itself is not part of `FloorGeometry`.

## Current baseline

The repository already implements most of this separation.

`FloorGeometry` is the exact structural Floor-cell model. `BuildingRoomScanner` partitions that geometry and asks `RoomPoiEvidence.candidates(...)` for possible POI positions. `RoomPoiEvidence` currently includes each Room-owned Floor column plus one horizontally adjacent perimeter column, while preventing one Room from borrowing another Room's actual Floor column. Shared perimeter columns receive one deterministic Room owner through `RoomPartitioner.owner(...)`. `Building.applyRoomScan(...)` then checks those positions against configured `BuildingType` block requirements and persists only relevant blocks.

This is existing production behavior, not a new wall-POI subsystem. Boundary POI collection was introduced by `617868cec` (`fix: collect room POIs from boundary evidence`), and the current pure `RoomPoiEvidenceTest` suite is green. Later changes added the deterministic shared-perimeter owner and overlapping-Floor protections. Those semantics are the compatibility baseline for this work.

This is the architecture to preserve and make explicit. The work is not permission to redesign `SelectedFloorScanner`, expand `FloorGeometry`, or introduce a second Room-volume model.

## Canonical ownership rules

### Floor topology remains independent of POIs

`SelectedFloorScanner` and `FloorGeometry` answer where the Floor structurally exists. They must not consult configured building types or discovered POIs when deciding Floor cells.

Adding, removing, rotating, or replacing a POI must not expand, shrink, or move canonical Floor geometry.

A wall block containing a POI remains a wall/boundary block. It is POI evidence for a Room, not a Floor cell.

### POI evidence is derived after Room topology

`RoomPoiEvidence` remains the sole geometry-to-POI-candidate adapter for ordinary functional Rooms.

For every exact Room cell with feet position `(x, y, z)` and `ceilingY`:

- the Room's own X/Z column is eligible from `y - 1` up to but excluding `ceilingY`;
- each cardinally adjacent X/Z column may be eligible over the same physical interval when that adjacent column is not already occupied by a Room Floor cell in an overlapping vertical interval;
- perimeter evidence extends one horizontal block only;
- the candidate set contains positions to inspect, not automatic POIs.

`Building.applyRoomScan(...)` remains responsible for reading the world and recording only blocks that match configured `BuildingType` requirements.

### Shared walls have one Room owner

A perimeter/wall column between two Rooms must never be counted by both Rooms.

Keep the existing deterministic ownership policy unless a concrete gameplay fixture proves physical attachment/facing must choose a different Room. Do not add a generic block-facing framework speculatively: not every configured POI has a meaningful horizontal attachment direction, and the existing component ownership order gives stable persistence.

If future evidence requires directional ownership for a specific class of boundary object, add that as a focused extension with its own regression instead of moving block semantics into Floor topology.

### Stacked Rooms remain vertically independent

A Room on another storey must not suppress lower-Room perimeter POI evidence merely because it overlaps the same or adjacent X/Z at a non-overlapping Y interval.

Candidate ownership is determined using the exact physical interval associated with each Floor cell. Same-height or vertically overlapping actual Floor ownership takes precedence over perimeter evidence; a non-overlapping upper/lower Room does not.

### POIs do not manufacture Room identity

Boundary POI evidence may affect Room type matching after the Room has been partitioned, but it must not:

- create Floor cells;
- create a new Room component;
- merge two Room components;
- choose a Floor/storey;
- create a logical building attachment;
- override persisted Room identity.

This preserves the existing ownership chain: scanner -> Floor geometry -> Room partition -> POI evidence -> type matching.

## Vertical boundary semantics

The existing physical interval remains the contract: candidate Y positions start at `cell.feet().getY() - 1` and stop before `cell.ceilingY()`.

This deliberately includes the structural support level used by existing POI scanning while excluding the ceiling boundary itself. Do not broaden this to arbitrary blocks above, below, or through a thick wall without a failing real-world fixture.

## No new geometry model

Do not add `RoomVolume`, `WallGeometry`, `PoiGeometry`, a persisted perimeter set, or a scanner-side POI graph.

The derived perimeter candidate set is intentionally ephemeral. Persisted state remains:

- exact Floor cells in `FloorGeometry` / Room `floorCells`;
- actual detected configured POI positions in `Building.blocks`.

Everything between those two is recomputable observation state.

## Implementation direction

The current production implementation already contains wall-POI support, so no behavioral production change is expected. First add an end-to-end GameTest proving that a real configured block placed in the wall is detected by the Room while the wall position remains absent from `FloorGeometry` and Room `floorCells`.

Use an existing configured block such as `minecraft:bookshelf` so the test exercises the actual `BuildingTypes` filtering path rather than only `RoomPoiEvidence.candidates(...)`.

If that GameTest passes against the current code, make no production-code change for this feature. The value of the work is the end-to-end regression and the explicit architectural contract.

If it fails, fix the narrowest owner in `RoomPoiEvidence`; do not modify `SelectedFloorScanner` or manufacture wall Floor cells.

## Required regressions

The implementation must preserve or add proof for all of the following:

1. A configured POI in an immediate wall column is detected by the Room.
2. The same wall position is absent from Room `floorCells` and canonical `FloorGeometry`.
3. An ordinary perimeter position remains only a candidate; irrelevant blocks are not persisted as POIs.
4. A shared wall/perimeter column is owned by only one Room.
5. Another Room's actual overlapping Floor column cannot be borrowed as perimeter POI evidence.
6. A stacked non-overlapping Room does not hide lower-Room wall POI evidence.
7. Uneven/raised Floor cells use their own vertical interval for boundary POI candidates.
8. POI changes do not alter selected Floor geometry.

Existing `RoomPoiEvidenceTest` coverage already proves several of these at the pure-geometry level; the new end-to-end GameTest closes the gap between candidate generation and actual configured POI persistence.

## Files in scope

Primary production owner:

- `common/src/main/java/net/conczin/mca/server/world/data/RoomPoiEvidence.java`

Integration consumers to verify, not redesign:

- `common/src/main/java/net/conczin/mca/server/world/data/BuildingRoomScanner.java`
- `common/src/main/java/net/conczin/mca/server/world/data/Building.java`
- `common/src/main/java/net/conczin/mca/server/world/data/RoomTypeResolver.java`

Tests:

- `common/src/test/java/net/conczin/mca/server/world/data/RoomPoiEvidenceTest.java`
- `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java`

Explicitly out of scope:

- `SelectedFloorScanner` behavior or Floor-band classification;
- `FloorGeometry` persistence shape;
- Room partition topology;
- connector/stair/trapdoor behavior;
- Room inheritance semantics;
- save-format migration;
- generic wall-volume persistence;
- block-facing ownership heuristics without a concrete failing fixture.

## Acceptance

The change is complete when:

- the end-to-end wall-POI GameTest proves a configured wall block is persisted as Room POI evidence while remaining outside Floor geometry;
- existing pure `RoomPoiEvidenceTest` contracts remain green;
- if the end-to-end regression already passes on the baseline, no behavioral production diff is introduced;
- no scanner/Floor topology production file changes are needed unless an independently demonstrated regression requires them;
- common tests and NeoForge compilation pass;
- the full NeoForge GameTest server has no new required failure;
- final review confirms there is still one spatial source of truth for Floor ownership and one derived POI-evidence layer.
