# Selected Floor and Room Workflow Simplification — Design

Date: 2026-09-17. Target: `dev/1.21.1` after `329e54327`.

## Goal

Simplify MCA structure discovery around what the player is actually editing:

1. scan one selected Floor at a time;
2. keep normal Minecraft uneven-floor movement inside that Floor;
3. use stairs/ladders as explicit attachment evidence, not a reason to recursively discover every storey;
4. keep doors as Room boundaries;
5. add/update only the selected Room instead of reconciling every Room on the Floor;
6. preserve Room identity when the player explicitly updates that Room;
7. retain deterministic external-basement attachment because it is user-visible behavior MCA needs;
8. keep interaction handoff convenient without allowing interaction code to invent geometry.

The governing rule is:

```text
World scan decides geometry.
Interaction logic only decides which geometry the player meant.
```

This design deliberately gives up automatic whole-building discovery and exotic staircase inference in exchange for a smaller, more predictable source of truth.

## Product decisions

The accepted behavior choices are:

| Topic | Decision | Meaning |
| --- | --- | --- |
| Uneven Floors | 1A | One Floor may contain ordinary slabs, one-block terrain changes, raised areas, and similar walkable variation. |
| Automatic storey discovery | 2B | Scanning one Floor does not recursively discover basement/upper Floors. Floors are added explicitly. |
| Stair complexity | 3B | Support normal obvious Minecraft staircases/landings through generic collision rules; exotic constructions may fail. |
| Ladders | 4A | Ladders may connect Floors, but unsupported ladder exits are interaction handoffs, not `FloorGeometry.Cell`s. |
| Indoor vs covered exterior | 5A | Keep enclosure/exterior pruning so roofed porches/canopies do not become indoor Floor geometry. |
| Doors | 6A | Doors/gates remain automatic Room boundaries on a Floor. |
| Add Room reconciliation | 7B | Add/update only the selected Room; do not rebuild unrelated registered Rooms on the Floor. |
| Stable Room IDs | 8A | An explicitly updated Room keeps its ID/type where the update is unambiguous. |
| External basement | 9A | An external basement may attach to the same logical Building when strict geometry proves the relationship. |
| Interaction tolerance | 10A | Common stair/slab/doorway/ladder-boundary interactions resolve to canonical geometry without creating geometry. |

## Core model

```text
Minecraft world
      ↓
selected enclosed-volume scan
      ↓
supported cells for ONE selected storey
      ↓
FloorGeometry
      ↓
door/gate partition of that Floor
      ↓
selected Room component
      ↓
add/update only that Room
```

`FloorGeometry` remains the only canonical persisted exact Floor-cell model.

The transient enclosed 3D volume remains useful because it separates indoor room space from covered exterior space. It does not imply automatic multi-storey discovery.

## 1. Scan one selected Floor only

`SelectedFloorScanner` already has both `selected(...)` and recursive `connected(...)` behavior. The selected path is the behavior this design wants as the default source of fresh geometry.

Fresh scanning should return exactly one canonical Floor:

- the Floor containing/resolved from the interaction;
- exact supported integer cells only;
- connector markers and local transition evidence needed by that Floor;
- no recursively discovered list of upper/lower `DiscoveredStorey` objects.

Remove normal workflow dependence on:

- `discoverConnectedStoreys(...)`;
- `ConnectedStoreys` as a recursive discovery result;
- `Result.connectedStoreys`;
- `Result.storeyLinks`;
- `Result.storeyScan(...)` as a way to retrieve recursively pre-scanned storeys;
- `directlyConnectedFloors(...)` derived from that recursive graph.

The implementation may retain narrow local attachment evidence that proves an attachment to an already-registered Floor, but it must not recursively world-scan another storey merely because a stair/ladder is reachable.

There are two different kinds of local vertical evidence and they must not be conflated:

- ladders/trapdoors use persisted `FloorConnector` markers and physical connector columns;
- ordinary stairs have no `FloorConnector.Type`, so the selected scan's local `transitionSeeds` (or an equivalent selected-storey boundary position) may be matched directly against already-persisted Floor geometry.

A stair transition position may identify an existing registered Floor; it must not be used as a request to scan/discover that other Floor. If the local evidence matches more than one logical target, attachment is ambiguous and must fail rather than guess.

### Why

Adding a downstairs Room should not silently scan the first floor, second floor and basement. The player can explicitly add another Floor from that Floor. This removes a substantial graph/discovery layer while making UI actions easier to explain.

## 2. Uneven Floors remain supported

Choice 1A does not mean retaining arbitrary topology heuristics.

Use Minecraft collision/floor-height behavior as the physical rule. Minecraft 1.21.1 `WalkNodeEvaluator.getFloorLevel(...)` already derives the standing height from the support block collision shape, and its neighbour search independently handles upward steps/collision constraints.

MCA should therefore keep:

- `WalkNodeEvaluator.getFloorLevel(...)` for transient physical height;
- integer `BlockPos` as canonical Floor-cell identity;
- local horizontal transitions between physically reachable supported cells;
- ordinary slabs, stairs, terrain steps and small level variation.

MCA should not add block-name rules for special staircase shapes.

## 3. Normal stairs, not recursive staircase intelligence

Stairs serve two separate purposes:

1. within one selected Floor, collision/step rules can connect nearby uneven supported cells;
2. between storeys, a normal staircase/landing can provide attachment evidence when the player explicitly adds the next Floor.

The scanner does not need to walk an arbitrarily deep stair chain looking for every storey.

Remove or simplify code whose only purpose is discovering/canonicalizing recursively connected storeys. Keep the local storey-boundary behavior required to stop one selected Floor absorbing the stable floor above/below, and keep enough local transition positions to prove a normal staircase reaches an already-registered adjacent Floor.

Tests for exotic staircase inference should be removed or rewritten when they no longer represent supported product behavior. Normal straight/L/U stairs and sensible landings remain required.

## 4. Ladders are connectors, not Floor cells

The current enclosed-volume redesign established the desired invariant:

```text
if a position is an ordinary FloorGeometry.Cell,
the world scan found real standing support for it.
```

Keep that invariant.

At the top of a ladder/trapdoor:

- the unsupported exit position may resolve the interaction to a nearby supported Floor;
- the connector may prove that a newly selected Floor attaches above/below an existing Floor;
- the unsupported exit itself does not enter `FloorGeometry`.

This avoids misleading geometry while still allowing UI/source-of-truth logic to offer Add Floor/Add Basement correctly.

## 5. Keep enclosure/exterior pruning

Choice 5A is intentional even though it costs some scanner code.

Keep the selected-storey enclosure proof, including the role currently served by:

- `retainEnclosedRegions(...)`;
- `reachesExterior(...)`;
- boundary continuation logic needed for doors/canopies.

A bedroom opening through a door to a roofed but open-sided porch must not absorb the porch into the indoor Floor.

The simplification target is multi-storey recursion and global Room reconciliation, not enclosure correctness.

## 6. Doors continue to partition Rooms

`RoomPartitioner` remains the owner of Room components within one canonical Floor.

Doors/gates remain boundaries. One Floor may contain many Rooms:

```text
bedroom ─ door ─ hallway ─ door ─ kitchen
   Room A          Room B          Room C
             one Floor
```

The partitioner may be simplified after recursive storey-edge state is removed, but door ownership must remain deterministic.

## 7. Add Room selects one component only

The current `RoomWorkflow.analyzeRoom(...)` does substantially more work than the selected action requires:

1. fresh-scan the Floor;
2. materialize every partition component;
3. materialize all previous Rooms;
4. run `RegisteredRoomReconciler.reconcileAddition(...)` across the whole Floor;
5. publish replacements for unrelated Rooms.

Replace that with a local operation:

1. scan the selected Floor once;
2. partition it once;
3. select the component containing the canonical scan seed/interaction handoff;
4. materialize only that component;
5. verify that it does not conflict with registered Rooms;
6. persist the selected Room and refreshed Floor only if existing Room ownership remains valid.

Choice 7B is about persistence/reconciliation, not about inventing a second selected-only topology algorithm. `RoomPartitioner` may still compute the transient component list once when that shared view is needed for deterministic door/boundary or POI-perimeter ownership. What must disappear is materializing sibling components as replacement Rooms and reconciling their persistent identities merely because one Room was selected.

If `BuildingRoomScanner.materialize(...)` still needs the full transient component list for `RoomPoiEvidence`, pass that one partition through. Do not duplicate partitioning or add a parallel selected-component flood fill merely to avoid constructing a transient list.

### Safety rule

Add Room must not silently mutate existing Rooms.

If the selected component overlaps the owned identity cells of an already registered Room, or if refreshing the Floor would invalidate existing registered Room cells, fail with an existing suitable validation result such as `OVERLAP` rather than guessing/reassigning identities.

The player can then explicitly Update Room on the affected Room.

This is intentionally stricter and easier to reason about than global automatic reconciliation.

## 8. Update Room preserves exactly the selected identity

Updating registered Room `#12` should preserve `#12` when the fresh geometry is unambiguous.

The update path should:

1. fresh-scan the selected Floor;
2. partition it;
3. select the one component identified by the interaction position/canonical handoff;
4. require that component to overlap the previous selected Room's stable identity cells;
5. reject overlap with any other registered Room;
6. copy the selected Room's ID/type/forced-type/main-contribution metadata onto the replacement;
7. replace only that Room plus the Floor geometry.

Do not create new sibling Rooms merely because an edited Room split into several fresh components. Those components can be added explicitly later.

Do not globally reassign unrelated Room IDs.

This makes `RoomIdentityPolicy` and most/all of `RegisteredRoomReconciler` unnecessary. Delete them if no supported workflow still needs them after the focused tests are green.

`RegisteredRoomUpdate` and `VillageManager` should shrink to one selected replacement instead of carrying lists of lineage assignments.

## 9. External basement attachment stays, but remains strict

Choice 9A is retained because external basement entrances are a real user expectation.

The existing `Village.selectAttachmentTarget(...)` already has the right direction for connector-less external attachment:

- require overlapping exact columns;
- require vertical intervals/structural shells to physically meet;
- reject ambiguous equally-near logical Buildings;
- reject unproven overlap.

Keep deterministic structural evidence. Do not replace it with distance-only or "nearest house" heuristics.

After recursive connected-storey discovery is removed, attachment evidence should come from one of:

1. a selected-scan stair transition position that uniquely falls on an already-registered adjacent Floor;
2. an explicit ladder/trapdoor connector column that uniquely connects to an already-registered Floor; or
3. strict direct vertical structural evidence for an external Floor.

The stair case is deliberately persisted-geometry matching, not a scan of the adjacent storey. This preserves normal stair attachment while still honoring choice 2B.

## 10. Interaction handoff remains narrow and deterministic

Choice 10A does not authorize fuzzy guessing.

Supported interaction normalization includes common Minecraft positions such as:

- standing on/inside a stair or slab interaction cell;
- a doorway/boundary;
- a ladder/trapdoor exit;
- the exact physical vertical interval of a persisted Floor.

`StructureScanner` may map such an interaction to a canonical supported scan seed. It must not add the interaction position to `FloorGeometry` merely to make the action work.

If multiple equally plausible handoffs lead to different Floors, return no handoff/ambiguous result instead of choosing by arbitrary iteration order.

## One source of truth

The intended owners after simplification are:

| Concern | Owner |
| --- | --- |
| Fresh world Floor geometry | `SelectedFloorScanner` |
| Exact persisted Floor geometry | `FloorGeometry` / `StructureFloor` |
| Interaction → canonical scan seed | `StructureScanner` / `StructureConnector` |
| Room partition on one Floor | `RoomPartitioner` / `BuildingRoomScanner` |
| Add/update selected Room | `RoomWorkflow` |
| Persist selected Room/Floor mutation | `VillageManager` / `Village` |
| Logical Building attachment | `Village.selectAttachmentTarget(...)` |

No second owner may manufacture ordinary Floor cells or globally redistribute Room identities.

## Expected deletions/simplifications

Subject to red/green tests, the implementation should aim to delete or collapse:

- recursive connected-storey discovery state in `SelectedFloorScanner`;
- `RoomScanPlanner.connectedTransitionAttachmentPlan(...)` if explicit local attachment makes it redundant;
- `StructureScanner.FloorObservation.directlyConnectedFloors()` if it only feeds recursive discovery;
- whole-Floor Add Room materialization/reconciliation;
- `RegisteredRoomReconciler.reconcileAddition(...)`;
- lineage-based registered Room update;
- `RoomIdentityPolicy` if no supported path remains;
- assignment-list machinery in `RegisteredRoomUpdate` and `VillageManager` that exists only for lineage/global reconciliation.

Do not pre-commit to deleting a helper if a focused supported-behavior test proves it still owns necessary behavior.

## Non-goals

- No save-format redesign.
- No new public graph/topology abstraction.
- No generic pathfinding framework.
- No block-specific `StairBlock`, hopper, shelf, bed, copied-house-coordinate or Y-level exceptions.
- No automatic recursive discovery of every storey.
- No distance-only external-building inference.
- No global "best effort" Room-ID reassignment.

## Required behavior

The simplified workflow must prove:

1. ordinary uneven indoor Floors still scan correctly across slabs/one-block variation;
2. scanning one Floor does not recursively return upper/lower Floors;
3. a normal staircase can be used to explicitly add the next Floor;
4. a ladder/trapdoor can attach Floors without creating unsupported Floor cells;
5. high ceilings still produce one supported Floor layer;
6. full-height partial obstacles do not become Floor geometry;
7. roofed outdoor porches/canopies remain excluded from indoor geometry;
8. doors still split one Floor into separate Room components;
9. Add Room persists only the selected component and leaves unrelated registered Rooms unchanged;
10. Add Room rejects ambiguous overlap instead of rewriting another Room;
11. Update Room keeps the selected Room ID/type when its new component is unambiguous;
12. Update Room does not create/reassign unrelated sibling Rooms;
13. an external basement can attach to the same logical Building through strict structural evidence;
14. ambiguous external attachment remains rejected;
15. common stair/slab/doorway/ladder interaction positions resolve deterministically;
16. persisted Floor/Room data remains compatible with existing saves.

## Engineering constraints

Apply the project coding/review rules throughout implementation:

- KISS: prefer selected-operation logic over recursive/global reconciliation;
- DRY: one Floor scanner, one Room partitioner, one interaction handoff path;
- YAGNI: do not preserve unsupported exotic staircase or auto-discovery machinery "just in case";
- descriptive names and immutable records/collections where appropriate;
- use guard clauses instead of deep nesting;
- compare gameplay behavior against Minecraft 1.21.1 local sources before inventing collision/path rules;
- run the same fixed Java diff through reuse, quality, correctness and efficiency review lenses before completion;
- preserve unrelated user work and untracked files;
- add focused red GameTests before changing each supported behavior.

## Acceptance

This redesign is ready when the implementation is materially smaller/easier to explain than the baseline while all required behavior above is green, Java/common verification passes, Fabric/NeoForge compile, and the final NeoForge GameTest run reports all required tests passed.
