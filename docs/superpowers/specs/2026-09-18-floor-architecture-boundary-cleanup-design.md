# Selected Floor Boundary Semantics Cleanup — Design

Date: 2026-09-19. Amendment to the selected-Floor simplification landed in `0bdc4193a`, `c022cbffa`, and `67c7f4267`.

## Goal

Make the remaining scanner terminology match the architecture that now exists without deleting behavior that still owns a real invariant and without creating another abstraction layer.

Production code must not grow as part of this cleanup. For every scoped production Java file, the final diff must add no more lines than it deletes.

The selected-Floor model is already the architectural boundary:

```text
interaction
  -> scan one selected Floor
  -> partition that Floor
  -> mutate one selected Room
  -> atomically validate/persist the refreshed Floor + complete Room set
```

This cleanup is therefore a **semantic ownership refactor**, not another Floor-system redesign.

## Decision

Keep the local vertical classifier. It is not recursive storey discovery: it decides whether a physically reachable cell belongs to the selected Floor, is a terminal boundary cell owned by that Floor, or belongs beyond the selected Floor.

Do not introduce new public `BoundaryEvidence` / `TransitionEvidence` objects. The current sets already have narrow consumers and different invariants; wrapping them would add ceremony without deleting logic.

Rename the remaining graph-era vocabulary around what it actually means:

| Current | Target | Invariant |
| --- | --- | --- |
| `StoreyContext` | `FloorBand` | Vertical band used only while resolving one selected Floor. |
| `StoreyRole` | `FloorBandRole` | `OWNED`, `EDGE`, or `OTHER` relative to the selected Floor band. |
| `StoreyClassifier` | `FloorBandClassifier` | Memoized local classifier for one selected scan. |
| `StoreyScan` | `SelectedFloorScan` | Transient result before the public `Result` is built. |
| `traverseStorey(...)` | `traverseSelectedFloor(...)` | Traverses only the selected Floor. |
| `resolveStoreyAnchor(...)` | `resolveSelectedFloorAnchor(...)` | Canonicalizes an interaction on/near a descent to the selected Floor anchor. |
| `storeyEdgeCells` | `verticalBoundaryCells` | Cells retained in selected `FloorGeometry` but treated as terminal Room-partition boundaries. |
| `transitionSeeds` | `adjacentFloorSeeds` | Positions outside the selected Floor that may match an already-persisted adjacent Floor. |

The word `storey` may remain in test descriptions where it describes the physical scenario. The cleanup target is code ownership vocabulary, not deleting the ordinary English concept of a storey.

## Exact invariants

### `FloorGeometry`

`FloorGeometry` remains the only canonical persisted exact Floor-cell model.

Physical standing height continues to come from Minecraft collision through `WalkNodeEvaluator.getFloorLevel(...)`. Slabs, stairs, low obstacles, and uneven terrain must not get block-specific persisted height rules.

### `verticalBoundaryCells`

The current `storeyEdgeCells` are not generic Room boundaries and should not be merged with doors/gates.

They mean:

1. the cell is still part of the selected `FloorGeometry`;
2. traversal must not continue through it into another stable Floor region;
3. `RoomPartitioner` temporarily excludes it from ordinary component flood-fill;
4. `assignStoreyBoundaryClusters(...)` then assigns the boundary cluster back to a deterministic adjacent component so the transition cell remains owned without merging Rooms across the vertical boundary.

Rename the partitioner helper to match the same invariant, for example `assignVerticalBoundaryClusters(...)`.

Door/gate boundaries continue to come from connector metadata and `doorOwnerSides(...)`. They remain a separate ownership rule.

### `adjacentFloorSeeds`

The current `transitionSeeds` are not alternate Floors and must never trigger a fresh remote scan.

They mean only:

- a reachable position beyond the selected Floor boundary;
- evidence that may match an **already-persisted** adjacent `StructureFloor`;
- evidence consumed by `RoomScanPlanner` / `Village.selectAttachmentTarget(...)` and by `StructureScanner` overlap validation.

Zero persisted matches means no attachment. More than one logical target remains ambiguous and must fail rather than guess.

### Local floor-band classifier

`OWNED`, `EDGE`, and `OTHER` remain useful internal states:

- `OWNED`: traverse normally inside the selected Floor;
- `EDGE`: include the cell in selected geometry, mark it as a vertical partition boundary, and stop traversal beyond it;
- `OTHER`: exclude it from selected geometry and expose it only as adjacent-Floor evidence where appropriate.

This classifier must remain operation-local. It must not grow a list/graph of connected Floors or recursively invoke another selected scan.

## Stair semantics

The previous 2026-09-17 design said not to add block-specific `StairBlock` rules. Before this cleanup, the code used `StairBlock` narrowly in `isStairOccupancy(...)` and `descendsFullStoreyFromStair(...)`; the cleanup renames those helpers to `isExplicitStairTransitionCell(...)` and `stairTransitionReachesBelowBand(...)` without changing their predicates.

This amendment narrows that rule instead of pretending the code is fully block-agnostic:

- **physical standing height remains block-agnostic** and collision-owned;
- a narrow `StairBlock` check may remain as a **transition disambiguation hint** for normal Minecraft stair interactions/boundaries;
- full-block staircases must continue to work from topology alone (`fullBlockStaircaseDoesNotMergeLowerRooms` proves that path exists);
- do not add per-shape, per-block, copied-house, or fixed-Y exceptions.

Rename the stair helpers so the scope is obvious, e.g. `isExplicitStairTransitionCell(...)` and `stairTransitionReachesBelowBand(...)`.

Removing the `StairBlock` hint is explicitly **not** required by this cleanup. It should only be removed later if a focused behavior change proves generic topology gives identical interaction and boundary ownership for the normal staircase fixtures.

## Room and persistence ownership

Do not reopen the Room-persistence architecture.

The following remains accepted:

- Add Room materializes/persists only the selected component.
- Update Room replaces only the selected registered Room and preserves its ID when unambiguous.
- Sibling Rooms are carried through unchanged.
- Existing Room-cell loss or identity overlap fails rather than globally reconciling.
- `RegisteredRoomReconciler`, `RoomIdentityPolicy`, recursive connected-storey discovery, and lineage assignment stay deleted.
- `Village.replaceStructureAndRegisterRoom(...)` / `publishFloorRefresh(...)` remain the atomic validation/persistence boundary.

## Interaction handoff

Ladder/trapdoor interaction handoff remains separate from canonical geometry. Unsupported exits may resolve an interaction to an existing Floor, but they do not become ordinary `FloorGeometry.Cell`s.

Current uncommitted ladder/trapdoor follow-up work in `RoomScanPlanner`, `Structure`, and related tests is outside this cleanup. Accessor renames must preserve those edits rather than absorbing/reworking them.

## Required behavior / regression coverage

The cleanup must preserve the existing contracts already covered by tests, especially:

- `selectedFloorScanDoesNotRecursivelyDiscoverDeepStairChain`;
- `staircaseKeepsUpperRoomOutOfLowerStorey`;
- `twoBlockStaircaseSeparatesBroadStoreys`;
- `fullBlockStaircaseDoesNotMergeLowerRooms`;
- `flatUpperLandingBesideDescentRemainsFloorCell`;
- `slabAndStairUseTransientSurfaceEvidence`;
- `unevenThreeArmRoomIsSourceIndependent` and the uneven-cave/plateau tests;
- `roofedExteriorAcrossDoorIsNotOwnedFloorGeometry`;
- ladder/trapdoor attachment and unsupported-exit tests;
- selected-only Add Room, moved-wall ID preservation, selected split behavior, sibling preservation, and sibling-overlap rejection.

No new gameplay behavior is required for this refactor. Existing tests are characterization/acceptance tests for the rename and ownership cleanup.

## Non-goals

- No new Floor graph or recursive discovery API.
- No new persisted topology model.
- No generic `BoundaryEvidence` wrapper merely to rename two existing sets.
- No splitting `SelectedFloorScanner` into multiple classes solely because it is large; extract only if a later change establishes a stable independent interface.
- No Room reconciliation or sibling auto-registration.
- No changes to save format.
- No changes to physical step-height semantics.

## Acceptance

After the cleanup, the architecture should be explainable directly from names:

```text
SelectedFloorScanner
  -> selected Floor geometry
  -> verticalBoundaryCells: retained terminal cells for Room partitioning
  -> adjacentFloorSeeds: local evidence for matching persisted adjacent Floors

BuildingRoomScanner / RoomPartitioner
  -> partition one selected Floor

RoomWorkflow
  -> mutate one selected Room/component

Village
  -> atomically validate and publish the refreshed Floor + complete Room set
```

There must be no API or helper whose job is to recursively discover another Floor from `adjacentFloorSeeds`.

The final production diff must also satisfy the no-growth budget: each scoped production Java file has additions less than or equal to deletions relative to the pre-cleanup baseline.
