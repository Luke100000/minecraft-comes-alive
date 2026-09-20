# Floor Ownership Consistency — Simplification Design

Date: 2026-09-16. Status: amended design.

This version supersedes the earlier September 16 floor-ownership design. The earlier document treated the work as a broad lifecycle/confirmation redesign. The current goal is narrower: preserve the floor behavior that now works, remove competing decisions, and make each layer answer one question.

## Decision

Use one owner for each kind of truth:

```text
Minecraft world
      ↓
SelectedFloorScanner          fresh physical storey discovery + storey links
      ↓
FloorGeometry                 exact 3D membership for that discovered storey
      ↓
RoomPartitioner               room components inside that FloorGeometry
      ↓
RoomScanPlanner               action selection from canonical geometry + saved identity
      ↓
Village / StructureFloor      persisted IDs, numbering, building identity and matching
```

The scanner decides which cells belong to the freshly observed storey. Downstream code may match that geometry to saved state or choose an action, but it must not rediscover the storey by raw interaction Y, nearest-column fallback, display outline, room area, or another traversal.

The user's house remains the acceptance case: one logical building with an open main storey, stair-connected upper/lower storeys, successive lower rooms and the turning staircase into the basement. Stairs connect canonical storeys; they do not become pseudo-floors or borrow room ownership merely because the player is standing on them.

## Current baseline

- Checkout: `C:/Users/Mik/Downloads/MCA/minecraft-comes-alive-1.21.1-floor-clean-squash`.
- Branch: `dev/1.21.1`.
- Preserve all staged and unstaged peer work.
- `FloorGeometry` is already the exact integer-cell representation.
- `SelectedFloorScanner.Observation` already shares scan-local ceiling/step caches.
- Selected-storey and connected-storey scans are intentionally separate requests.
- The planner direction regression has already been corrected to use canonical fresh floor anchors instead of raw interaction Y.
- Ladder top-exit membership is already covered by regression tests.
- The unused `SelectedFloorScanner.usesInteriorMembershipSeed()` compatibility helper has already been removed and must stay removed.

This spec does not authorize restoring an older branch wholesale or replaying the earlier broad plan.

## Ownership contract

| Question | Sole authority | Allowed downstream use |
| --- | --- | --- |
| Is this a physically supported/traversable surface? | Vanilla collision/path APIs as sampled by `SelectedFloorScanner` | Cache for the current observation. |
| Which cells belong to this freshly observed storey? | `SelectedFloorScanner` | Consume the resulting `FloorGeometry`; do not add/remove cells downstream. |
| Which other canonical storeys are directly connected? | `SelectedFloorScanner` | Consume explicit links for attachment and validation. |
| Which room component owns a floor cell? | `RoomPartitioner` | Choose/register a component; do not invent another floor. |
| Which persisted floor corresponds to fresh geometry? | `StructureFloor` matching policy | Match geometry to stable IDs; never change fresh membership. |
| Which user action should be offered? | `RoomScanPlanner` | Combine canonical observation with persisted identity. |
| Which logical building owns a saved floor/room? | `Village`, `Structure`, `LogicalBuilding` | Maintain IDs, ordinals and persistence. |
| May a candidate coexist with existing saved structures? | `StructureScanner` validation | Consume canonical geometry and explicit scanner links. |
| What shape is rendered in Blueprint/debug UI? | Derived projection only | Display/select; never decide membership. |

Saved identity and fresh physical geometry are different kinds of truth. Keeping both is not duplication. Duplication exists only when two layers answer the same question.

## Explicit simplification rules

### S1. Scanner owns fresh storey identity

`SelectedFloorScanner` may use local topology, stair state, collision support, enclosure and transition evidence to discover one storey. Once it returns `FloorGeometry`, no planner, village or persistence class may independently decide that a returned cell belongs to a different storey.

### S2. `FloorGeometry` is the spatial contract

Exact cells and their vertical intervals are authoritative for fresh and persisted membership. `BuildingFloorRegion`, bounds, outline projections and X/Z columns are derived views.

Same-column or nearest-Y fallbacks may not manufacture physical room/storey membership outside exact geometry.

### S3. Room partitioning never discovers floors

`RoomPartitioner` splits one canonical `FloorGeometry` using room boundaries. It may assign deterministic boundary ownership, but every component remains a subset of the supplied floor.

### S4. Planner chooses actions, not geometry

`RoomScanPlanner` may:

- recognize an already registered room from persisted exact membership;
- match a fresh canonical floor to one persisted `StructureFloor`;
- use scanner-provided connected floors to choose `ADD_FLOOR`/`ADD_BASEMENT`;
- choose the fresh room component to register.

It may not derive storey direction from the raw clicked/player Y after a canonical fresh floor exists. Direction must come from canonical floor anchors or explicit scanner links.

### S5. Matching tolerance is not scanner topology

`StructureFloor.BAND_TOLERANCE` belongs to persisted matching/attachment/legacy numbering policy. It must not be imported into `SelectedFloorScanner` as the definition of physical storey ownership.

The scanner may retain a scanner-owned value of `2` initially to preserve behavior. The important change is ownership: later tuning of persisted matching must not silently change fresh world discovery.

### S6. Selected and connected scans remain distinct

`scanSelected`/`Observation.selected` answer “what storey owns this interaction?”

`Observation.connected` and direct-storey links answer “what canonical storeys are connected to it?”

These are different queries and should not be collapsed merely to reduce method count. Connected discovery must not replace the selected floor's cells.

### S7. Persisted matching may compare geometry, never rewrite it

`StructureFloor.overlapsSemanticStorey(FloorGeometry)` remains the persisted matching primitive for same-band + footprint overlap. `Village` and `StructureScanner` may use it to associate scanner output with saved identities or explicit connected floors.

If matching is ambiguous, return no unique target rather than choosing by nearest room, largest area or arbitrary iteration order.

### S8. Connector-exit membership is semantic, not fabricated support

Air at a verified ladder/vertical-connector top exit may belong to the adjacent storey for interaction membership. It must remain distinguishable from a physically supported `SurfaceCell`.

The current implementation may express this as a handoff height. Prefer clear naming over introducing another persisted or public geometry type.

### S9. Do not add another transition framework

The current stair logic has multiple helpers because strict descent, non-ascending descent, landing stability and stair occupancy are not identical predicates. Consolidate duplicated traversal only where it produces less code and the same behavior.

Do not introduce a new generic topology graph, public `TransitionClassifier`, region hierarchy or second geometry model as part of this cleanup.

### S10. Behavior changes need a concrete failing fixture

This cleanup is behavior-preserving. Existing heuristics may be renamed, decoupled or deduplicated, but changing their result requires a focused red regression first.

## Current competing-source audit

The following are the concrete places to simplify or constrain:

1. `SelectedFloorScanner.StoreyContext` currently reads `StructureFloor.BAND_TOLERANCE`, and `descendsFullStoreyFromStair` uses the same persistence constant. This is a real ownership leak: fresh scanner policy depends on persisted matching policy.
2. `RoomScanPlanner.connectedTransitionAttachmentPlan` is legitimate only as an action selector over already canonical floors. Its direction must stay derived from `observation.scan().floor().anchorY()` and persisted floor anchors, never `source.getY()`.
3. `RoomScanPlanner.selectSameStoreyTarget`, `Village.attachmentConnections`, and `StructureScanner.hasDirectStoreyConnection` may all call the persisted `StructureFloor` matching primitive. They must not reproduce its formula independently.
4. `Village.selectAttachmentTarget` owns persisted building attachment. It may use exact vertical evidence, explicit scanner connections and persisted matching. It must not add/remove cells from the candidate geometry.
5. `Village.resolveInteractionPosition` is valid for registered exact membership and `UPDATE_ROOM`. It must not cause an unowned stair/air interaction to borrow the nearest persisted room.

## Required behavior

R1. The main storey, successive lower storeys and basement remain one logical building when connected by the actual staircase chain.

R2. A broad flat landing remains part of its storey; a descending stair run does not absorb the next room/storey.

R3. Ladder top-exit air can resolve to the upper storey only when a verified vertical connector supplies the handoff. Generic unsupported air remains outside.

R4. A registered room updates only from exact persisted membership. An unowned stair cell or vertical gap must not borrow room identity from another storey.

R5. Adding a room on an already discovered floor uses the scanner's canonical floor and `RoomPartitioner` component; it does not create a second building because the clicked cell has a different raw Y.

R6. Adding a connected floor/basement uses scanner-provided connected-storey evidence and persisted building matching. Floor numbers remain adjacent ordinals and are not inferred from raw world Y alone.

R7. Exterior basement-door attachment remains supported. `BAND_TOLERANCE` may remain in persisted matching/attachment policy for this case.

R8. Existing save fields, floor IDs, room IDs, logical-building IDs, `floorNumber`, `RoomDFU`, connector metadata and copied-house NBT remain unchanged.

R9. Selected-only discovery must not eagerly scan the entire connected building. Operation-local caches remain local; no persistent/global geometry cache is added.

R10. Blueprint/debug rendering stays derived from persisted/fresh geometry and cannot override ownership.

## Non-goals

- No confirmation protocol redesign.
- No packet-field changes.
- No new NBT fields or data-version bump.
- No rewrite of `RoomWorkflow`, `BuildingDiagnostics` or confirmation tests unless a focused ownership regression proves they are involved.
- No wholesale scanner replacement.
- No restoration of old Y-band/area-based floor discovery.
- No new generic topology framework.
- No Blueprint rendering redesign.
- No Destiny changes.
- No branch rewrite, reset, stash, broad clean or commit as part of this plan.

## Testing strategy

Use the smallest useful loop:

1. JUnit for planner/matching decisions.
2. A running dev client/server plus `/test run <testName>` for one world fixture while iterating.
3. `/test runall FloorScannerGameTests` when scanner behavior changes substantially.
4. Full `:neoforge:runGameTestServer` only at integration/completion checkpoints.

The full suite remains required before claiming merge-ready behavior, but it is not the inner-loop debugger.

## Acceptance

The cleanup is complete when:

- `SelectedFloorScanner` no longer depends on `StructureFloor.BAND_TOLERANCE` for fresh ownership;
- no raw interaction Y determines storey identity after canonical geometry exists;
- no downstream layer changes the scanner's selected cells;
- persisted storey matching has one reusable primitive rather than copied formulas;
- connector-exit membership is named/handled as semantic handoff rather than physical collision support;
- dead compatibility helpers are removed rather than replaced;
- the focused house, stair, room-boundary, ladder and exterior-basement regressions pass;
- common tests and both loader compiles pass;
- one final full NeoForge GameTest run passes, with unrelated flaky failures reported separately if they occur;
- live copied-world verification is reported separately from automated GameTests.

See the amended implementation plan: `docs/superpowers/plans/2026-09-16-floor-ownership-consistency.md`.
