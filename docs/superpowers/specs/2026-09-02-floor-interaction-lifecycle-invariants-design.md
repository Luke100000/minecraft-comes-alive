# Floor Interaction, Lifecycle, and Structure Invariants Design

## Scope

This design implements the selected follow-up priorities from the floor-system review:

1. make Floor interaction semantics explicit and fix boundary handoff without weakening exact
   physical membership;
2. finish the `Structure` model so one runtime `Structure` means one independently rescannable
   physical storey/section;
3. keep Room, Floor, external-site, and logical-building deletion as explicit independent
   mutations;
4. turn the regression suite into an invariant matrix that covers complete Floor lifecycles, not
   only individual bugs.

The goal is to simplify the model while preserving released-world compatibility and the branch's
new features. This is not a new scanner rewrite and it does not change navigation.

## Research basis

Implementation should use the local patched Minecraft 1.21.1 sources as the primary behavioral
reference. Web documentation is supporting evidence rather than a substitute for the actual target
sources.

The following sources were checked while preparing this design:

- NeoForge 1.21.1 Saved Data documentation: mutations of persisted data must mark the owning
  `SavedData` dirty, so all successful Village topology mutations must continue to reach the
  manager's persistence boundary.
- NeoForge GameTest documentation: GameTests are the appropriate integration layer for real-world
  block/collision/interaction behavior, while pure domain invariants should remain ordinary unit
  tests where possible.
- Fabric automated-testing documentation: the same split is recommended conceptually—unit tests
  for isolated logic and GameTests for behavior that requires a Minecraft world.
- mappings.dev for Minecraft 1.21.1: `BlockState` exposes collision, support, and interaction shapes;
  therefore walkability/landing decisions should derive from Minecraft collision semantics rather
  than block-name special cases.

## 1. Canonical model

The runtime model becomes:

```text
LogicalBuilding
  -> one or more Structures
       -> exactly one StructureFloor each
            -> zero or more Rooms
```

The meanings are deliberately narrow:

- `LogicalBuilding` owns identity shared by a whole building, the canonical Ground Floor, Main
  Room, and inheritance state.
- `Structure` is one independently discovered/rescannable physical storey or detached physical
  section. A normal runtime Structure contains exactly one `StructureFloor`.
- `StructureFloor` is the persisted geometry and floor number for that Structure.
- `Room` is functional topology on that persisted Floor and can be added/removed without changing
  the Floor itself.

New scans already naturally produce this shape, so the change removes a residual compatibility
model rather than introducing a new scanning concept.

### 1.1 Compatibility boundary

Old branch saves may contain a single `Structure` with multiple `StructureFloor`s. They remain
loadable, but are normalized during migration before entering the normal runtime model.

The current persisted `buildingDataVersion` is bumped. Loading the previous version performs a
deterministic split:

1. group each multi-floor Structure's floors by their existing floor record;
2. retain the original Structure ID for the canonical Ground Floor when that Structure owns it;
   otherwise retain it for the first floor by anchor-Y/id order;
3. create one new Structure ID for every other floor;
4. each normalized Structure receives one Floor, normalized to local floor ID `0`, while preserving
   anchor, ceiling, logical floor number, exact region, and connector cells;
5. rewrite Room `structureId`/`floorId` references to the normalized owner;
6. rewrite `LogicalBuilding.groundStructureId`/`groundFloorId` if necessary while preserving the
   logical-building ID and Main Room ID;
7. recompute Structure bounds from the single Floor;
8. validate that every Room references an existing Structure/Floor and every LogicalBuilding has a
   valid Ground Floor.

ID allocation during migration must never collide with Rooms, Structures, or external Buildings.
After villages load, `VillageManager.lastBuildingId` is clamped to at least one greater than every
persisted/generated building-domain ID. The saved counter remains a lower bound, not an assumption.

No compatibility-only multi-floor Structure may be saved back in the new format.

## 2. Physical membership versus interaction ownership

The current exact Floor geometry remains half-open vertically:

```text
anchorY <= y < ceilingY
```

`Structure.physicalFloorAt(...)` keeps that rule. `ceilingY` must not become inclusive merely to
repair UI/player interaction, because doing so would create overlapping ownership when another
storey begins at the boundary.

Interaction answers a different question: **which persisted Floor/Room is this player position
operating on?**

### 2.1 Explicit interaction candidate kinds

`Structure.InteractionPosition` gains an explicit interaction kind instead of encoding semantics in
multiple booleans. The intended kinds, strongest first, are:

1. `PHYSICAL` — position is inside the exact persisted Floor prism;
2. `HORIZONTAL_CONNECTOR` — door/gate-style boundary resolves to a Room/Floor owner;
3. `VERTICAL_CONNECTOR` — ladder/trapdoor/climb connector resolves to its persisted landing Floor;
4. `LANDING_HANDOFF` — a walkable position immediately outside the exact vertical band resolves to
   the adjacent persisted Floor because it is the usable landing for that Floor.

The exact enum names may change during implementation, but the distinction and ordering must be
explicit and testable.

`Village.resolveInteractionPosition(...)` ranks candidates by semantic strength first, then the
smallest vertical distance, then stable IDs. It must not infer priority from unrelated booleans.

### 2.2 Top-boundary handoff

The known Basement 1 failure has a persisted floor such as `anchor=84`, `ceiling=87`, while the
player interaction position is at `y=87`. That position is correctly outside `physicalFloorAt`, but
it can still be the basement landing.

`LANDING_HANDOFF` is allowed only when all of these are true:

- the X/Z column belongs to the persisted Floor footprint (or an exact connector-owned landing
  cell);
- the query is immediately adjacent to the Floor's vertical band, with a maximum one-block
  handoff;
- the queried player cell is actually walkable according to existing Minecraft collision/support
  semantics;
- there is no stronger physical or connector claim from another Floor/Structure at the position;
- the handoff does not cross a solid ceiling, wall, or unrelated enclosed section.

This fixes exact-ceiling interaction without declaring the ceiling coordinate physically part of
the lower Floor.

### 2.3 Lower-boundary behavior

Existing intentional interaction support around `anchorY - 1` is retained only where the queried
cell is a real walkable approach/landing. The same `LANDING_HANDOFF` contract should replace ad-hoc
vertical tolerance where possible so upper and lower edges use one rule.

### 2.4 Connectors

Vertical connectors continue to use exact connector geometry and landing cells. Standing beside a
ladder is not a vertical-connector interaction. A top exit or trapdoor handoff may resolve to the
adjacent Floor even though the player's feet have left the connector block, but only through the
explicit connector/landing contract.

Horizontal doors continue to be boundaries rather than Floor topology. Their Room ownership is
resolved from the interior Floor cell/Room topology, not from a special BuildingType rule.

## 3. Explicit mutation model

Domain mutations must state what kind of object they remove.

The desired domain operations are conceptually:

```text
removeRoom(roomId)
removeFloor(structureId)            // one Structure == one Floor after normalization
removeExternalBuilding(id)
removeLogicalBuilding(buildingId)
```

The player-facing `VillageManager.removeBuilding(pos)` command may remain as a convenience action,
but it must resolve the target and delegate to one explicit domain mutation. `Village.removeBuilding`
must not remain an ambiguous method that sometimes means Room and sometimes external site.

### 3.1 Room removal

- refuses removal of the Main Room under the current rule;
- removes only the Room;
- never deletes its Structure/Floor as a side effect;
- refreshes logical state and persistence metadata after success.

### 3.2 Floor removal

- only an empty Floor can be removed;
- Ground Floor is never removable;
- an upper Floor is removable only when no higher positive Floor exists in the LogicalBuilding;
- a basement is removable only when no lower/more-negative Floor exists;
- removing a Floor removes its now-empty one-floor Structure;
- after removing the outer Floor, the next inward empty Floor becomes eligible;
- no Room mutation invokes Floor removal implicitly.

With the one-Floor Structure invariant, `Structure.removeFloor(...)` is no longer normal runtime
behavior. Floor deletion becomes Structure deletion after eligibility validation. Compatibility
code may temporarily need helpers while old saves are normalized, but that path must not leak into
normal mutations.

### 3.3 Logical-building removal

Removing the whole building explicitly removes all member Structures and Rooms and then its
`LogicalBuilding`. This is the only operation that intentionally cascades across all physical
storeys.

### 3.4 Persistence

Every successful mutation continues to recalculate dimensions where required, reconcile the
logical building, and mark the owning SavedData dirty through the existing manager/Village
boundary. Failed/no-op mutations must not partially modify topology.

## 4. Invariant test matrix

Tests should describe durable rules rather than mirror implementation helpers.

### 4.1 Pure/unit invariants

Add or retain focused tests for:

| Area | Required invariant |
| --- | --- |
| Physical band | `anchorY` is inclusive and `ceilingY` is exclusive. |
| Structure shape | every new-format runtime Structure has exactly one Floor. |
| References | every Room references an existing Structure and its sole Floor. |
| Ground Floor | every LogicalBuilding has exactly one valid canonical Ground Floor. |
| Numbering | Floor numbers are stable around the canonical Ground Floor and ordered by height bands. |
| Room removal | removing the last Room leaves the Floor/Structure intact. |
| Upper removal | only the highest empty positive Floor is removable. |
| Basement removal | only the lowest empty negative Floor is removable. |
| Ground removal | Ground Floor is never removable. |
| Cascade | only explicit logical-building removal removes all storeys. |
| Migration | previous multi-floor Structures normalize to one-Floor Structures without losing Room identity, floor geometry, connector geometry, Main Room, or Ground Floor. |
| ID safety | migration cannot cause `lastBuildingId` reuse/collision. |
| Save round-trip | normalized data reloads with the same topology and no compatibility-only multi-floor Structure. |

### 4.2 Interaction unit tests

Where world behavior can be represented without faking Minecraft collision, test ranking/selection
logic directly:

- physical candidate beats landing handoff;
- connector candidate beats generic landing handoff;
- stable tie-breaking does not depend on HashMap iteration order;
- an inner storey is not accidentally selected when a stronger adjacent-storey candidate exists;
- exact ceiling is not physical membership.

Do not create a large fake `Level` merely to force collision behavior into JUnit.

### 4.3 Minecraft GameTests

Use real GameTests for behavior requiring actual blocks/collision:

- persisted basement `anchor=84`, `ceiling=87`, walkable player position at `y=87` resolves to the
  basement interaction Floor when no stronger owner exists;
- the same coordinate does **not** hand off through a solid roof/non-walkable cell;
- when an upper Floor physically owns the boundary position, the upper Floor wins;
- top and bottom ladder exits resolve to the expected Floor;
- standing beside a ladder is not treated as a connector;
- valid trapdoor/ladder hatch handoff works while a decorative trapdoor does not create a vertical
  Floor connection;
- door boundary ownership remains on the correct interior Room;
- slabs/stairs/partial-height walkable cells obey Minecraft collision semantics;
- irregular/cave Floors keep exact footprint ownership;
- full lifecycle: add upper floor(s), add basement(s), remove a Room, verify Floor persists, verify
  inner Remove Floor is unavailable, peel outer Floors in order, and confirm the Ground Floor
  survives;
- repeat the lifecycle with save/load coverage in unit/NBT tests so runtime behavior and persistence
  agree.

GameTests should be small scenes with one behavior each. A broader lifecycle GameTest is useful as a
single integration scenario, but it must not replace focused failures that identify which invariant
broke.

## 5. Diagnostics

`BuildingDiagnostics` should expose interaction resolution in terms of the new contract. A useful
trace includes:

```text
interactionKind=LANDING_HANDOFF
structureId=...
floorId=...
floorNumber=...
physical=false
verticalDistance=1
walkable=true
rejectedStrongerCandidate=none
```

When no interaction resolves, diagnostics should report the closest rejected candidate and reason
(`OUTSIDE_FOOTPRINT`, `NOT_WALKABLE`, `SOLID_BOUNDARY`, `STRONGER_OWNER`, `TOO_FAR`) rather than only
falling through to `ADD_BUILDING`.

These reason labels are diagnostic-only. They should not become another public domain result
hierarchy unless production behavior needs them.

## 6. Implementation order

Implementation follows TDD and keeps each stage independently green:

1. lock the exact-ceiling Basement regression and candidate-priority rules with failing tests;
2. introduce explicit interaction kinds and the one-block landing-handoff rule without changing
   physical membership;
3. add the complete Room/Floor removal lifecycle matrix and consolidate mutation APIs;
4. add new-format single-Floor Structure invariant tests;
5. implement previous-version migration/splitting and ID-counter repair;
6. update callers to rely on one-Floor Structures and delete normal-runtime multi-floor mutation
   helpers;
7. update diagnostics;
8. run focused tests, all common tests, NeoForge compilation, and the applicable GameTest suite.

The work should be committed in coherent slices rather than one catch-all commit: interaction
contract, explicit mutations/tests, Structure normalization/migration, then diagnostics/integration
coverage.

## 7. Non-goals

- no second rewrite of `StructureScanner`;
- no navigation/climb refactor in this work;
- no Blueprint visual redesign;
- no generic service/repository/event-sourcing architecture;
- no inclusive `ceilingY` physical membership workaround;
- no deletion of released-world compatibility;
- no speculative generalized spatial-query framework.

## 8. Acceptance criteria

The work is complete only when all of the following are true:

1. the known Basement exact-ceiling position resolves to the intended persisted Floor through an
   explicit interaction handoff while `physicalFloorAt` remains half-open;
2. physical/connector owners deterministically outrank generic landing handoffs;
3. normal runtime data contains exactly one Floor per Structure;
4. previous branch multi-floor saves load, normalize, and save in the new one-Floor format without
   losing Room/LogicalBuilding identity or geometry;
5. Remove Room never removes a Floor;
6. Remove Floor peels only the empty outermost upper/basement storey and never Ground Floor;
7. explicit whole-building removal is the only all-storey cascade;
8. invariant unit tests and Minecraft GameTests cover the matrix above;
9. all existing floor/Room/Blueprint regression tests remain green;
10. unrelated dirty navigation/UI work in the current worktree is preserved.
