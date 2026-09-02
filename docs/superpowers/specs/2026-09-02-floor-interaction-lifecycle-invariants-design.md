# Floor Interaction, Lifecycle, and Building Identity Design

## Scope

This design implements the selected follow-up priorities from the floor-system review:

1. make Floor interaction semantics explicit and fix boundary handoff without weakening exact
   physical membership;
2. preserve the existing player-facing building identity model: Add Building creates a separate
   building, while Add Floor/Add Basement extend an existing building even when another building
   could occupy the same X/Z at a different Y;
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

## 1. Canonical player model

The player-facing model remains:

```text
LogicalBuilding
  -> one or more Floors
       -> zero or more Rooms
```

`Structure` remains an internal scan/persistence ownership detail beneath that model. This design
does **not** impose a new one-Floor-per-Structure or multi-Floor-per-Structure invariant. Existing
saves and the current single-selected-Floor scanner remain valid without a save-format migration.

The user-visible meanings are deliberately narrow:

- `LogicalBuilding` owns identity shared by a whole building, the canonical Ground Floor, Main
  Room, and inheritance state.
- `StructureFloor` is persisted geometry and the logical floor number used by that building.
- `Room` is functional topology on that persisted Floor and can be added/removed without changing
  the Floor itself.
- `Structure` is an internal physical scan/persistence owner used to keep floor geometry and Room
  references stable. Its exact floor cardinality is not a player concept and is not changed here.

### 1.1 Explicit player intent defines building identity

Physical stacking must never silently decide semantic building identity. The Blueprint actions are
the contract:

```text
Add Building
  -> create a new LogicalBuilding
  -> create its own Main Room/inheritance scope

Add Room
  -> add another Room to the current Floor/building
  -> contributes to the Main Room by default

Add Floor / Add Basement
  -> scan exactly one selected physical storey
  -> attach that Floor to the explicitly selected existing LogicalBuilding
  -> the first/new Rooms on that Floor contribute to the same Main Room by default
```

This supports both common and mixed-use constructions without extra prompts:

```text
Inn
  Ground: Reception [Main], Kitchen
  Floor 1: Guest Rooms
  Floor 2: Guest Rooms

Restaurant directly above the Inn
  created with Add Building, therefore a separate LogicalBuilding
  Dining [Main], Kitchen, Cashier
```

The Inn's upstairs Rooms inherit/contribute across floors because inheritance is scoped to the
LogicalBuilding, not to a Floor or Structure. The Restaurant does not inherit from the Inn even if
its geometry is directly stacked above it.

### 1.2 Inheritance is orthogonal to geometry

Rooms contribute to their LogicalBuilding's Main Room according to the existing
`contributesToMain`/building inheritance rules. A single Floor may contain both inherited and
independent Rooms. No Floor-level inheritance state is introduced.

This means a multi-floor Inn works by default, while an unusual Room can opt out without splitting
the physical floor or changing building geometry.

### 1.3 Compatibility boundary

No building-data version bump or Structure migration is part of this work. Existing released and
branch save compatibility stays exactly at the current boundary. The scanner continues to discover
one selected physical Floor at a time; this design changes interaction/lifecycle rules, not save
shape.

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
removeFloor(structureId, floorId)
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
- removing a Floor removes only that persisted Floor; an internal Structure may disappear only if
  it becomes empty as an implementation detail;
- after removing the outer Floor, the next inward empty Floor becomes eligible;
- no Room mutation invokes Floor removal implicitly.

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
| Building intent | Add Building creates a separate LogicalBuilding even when stacked above/below another building. |
| Floor intent | Add Floor/Add Basement extends the selected LogicalBuilding instead of creating a new inheritance scope. |
| References | every Room references an existing persisted Floor/Structure owner. |
| Ground Floor | every LogicalBuilding has exactly one valid canonical Ground Floor. |
| Numbering | Floor numbers are stable around the canonical Ground Floor and ordered by height bands. |
| Multi-floor inheritance | contributing Rooms on upper/lower Floors participate in the same Main Room classification. |
| Stacked independence | a separate stacked building has its own Main Room and does not contribute POIs across the building boundary. |
| Room removal | removing the last Room leaves the Floor/Structure intact. |
| Upper removal | only the highest empty positive Floor is removable. |
| Basement removal | only the lowest empty negative Floor is removable. |
| Ground removal | Ground Floor is never removable. |
| Cascade | only explicit logical-building removal removes all storeys. |
| Save round-trip | building identity, Main Room, inheritance, Floors, Rooms, and removal eligibility survive reload. |

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
- a multi-floor Inn keeps one Main Room/inheritance scope across Ground/upper/basement Rooms;
- a Restaurant created with Add Building directly above that Inn remains a separate logical
  building and inheritance scope;
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
3. add the complete Room/Floor removal lifecycle matrix and consolidate mutation APIs without
   changing save shape;
4. add building-intent/inheritance coverage for multi-floor Inns and independently stacked
   buildings;
5. update diagnostics;
6. run focused tests, all common tests, NeoForge compilation, and the applicable GameTest suite.

The work should be committed in coherent slices rather than one catch-all commit: interaction
contract, explicit mutations/tests, then building-identity/inheritance and diagnostics/integration
coverage.

## 7. Non-goals

- no second rewrite of `StructureScanner`;
- no navigation/climb refactor in this work;
- no Blueprint visual redesign;
- no generic service/repository/event-sourcing architecture;
- no inclusive `ceilingY` physical membership workaround;
- no new one-Floor-per-Structure or multi-Floor-per-Structure invariant;
- no building-data version bump or Structure migration;
- no inference that vertically stacked geometry must belong to one building;
- no deletion of released-world compatibility;
- no speculative generalized spatial-query framework.

## 8. Acceptance criteria

The work is complete only when all of the following are true:

1. the known Basement exact-ceiling position resolves to the intended persisted Floor through an
   explicit interaction handoff while `physicalFloorAt` remains half-open;
2. physical/connector owners deterministically outrank generic landing handoffs;
3. Add Floor/Add Basement preserves the selected LogicalBuilding/Main Room/inheritance scope;
4. Add Building can create an independent logical building directly above/below another one;
5. multi-floor inherited Rooms contribute across Floors while independent stacked buildings never
   contribute across their logical-building boundary;
6. Remove Room never removes a Floor;
7. Remove Floor peels only the empty outermost upper/basement storey and never Ground Floor;
8. explicit whole-building removal is the only all-storey cascade;
9. no save-format migration or Structure-cardinality rewrite is introduced;
10. invariant unit tests and Minecraft GameTests cover the matrix above;
11. all existing floor/Room/Blueprint regression tests remain green;
12. unrelated dirty navigation work in the current worktree is preserved.
