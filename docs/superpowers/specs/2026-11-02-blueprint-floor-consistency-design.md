# Blueprint and Floor Consistency — Specification

Date: 2026-11-02. Target: `dev/1.21.1`, Minecraft 1.21.1, Java 21.

Status: approved for native implementation and related commits on 2026-11-02. Source baseline: `37a2a9fde`.

## Purpose

Make Blueprint and server operations use one implementation of floor/room selection and preserve its explicit decision through saving. Reduce duplicated orchestration inside the existing classes. The player should get the same result from equivalent valid positions in a room, rather than finding one tile where the action happens to work.

The earlier diagnostics established position-dependent server analysis failures. They did not establish a client/server world divergence. This work must not grow into a synchronization redesign on that assumption.

This is a focused follow-up to the September 17 selected-floor workflow and September 25 single-state publication designs. Preserve their selected-room scope and Village-owned publication boundary. Historical connected-storey APIs in older documents are not instructions to restore removed code.

## Agreed constraints

- Work in the existing `dev/1.21.1` checkout; no WSL or new worktrees without consent.
- Preserve unrelated dirty work. User authorized native implementation and related commits on 2026-11-02; no push is authorized.
- Use existing production classes; no class extraction or new top-level production classes.
- Keep the existing Blueprint controls, packet formats, type-choice flow, and save format.
- Add no server-preview protocol, per-player plan store, version/token system, synchronization framework, asynchronous scan executor, or new cache layer.
- Keep scanning and mutation on their existing threads; live server world access remains on the server thread.
- Keep current scan budgets, loaded-column guards, and bounded hole tolerance.
- Production Java should shrink overall. Report scoped production additions/deletions separately from tests; do not delete required behavior to meet a line target.

## Approaches considered

1. **Consolidate the existing shared planner and execution flow — selected.** Keep `RoomScanPlanner`/`RoomScanPlan`, pass their decision through the workflow, and share remaining room reconstruction. This reduces competing decisions without changing network or persistence architecture.
2. **Server-provided previews — deferred.** This would remove local preview calculation but introduce request/reply timing and refresh state. There is no demonstrated need for it in this fix.
3. **Replace the building/floor/room model or extract a framework — rejected.** This would expand scope, require migration or extensive adaptation, and obscure the existing behavior we want to preserve.

## One owner for each decision

| Responsibility | Existing owner | Contract |
| --- | --- | --- |
| Fresh supported surfaces, selected storey, enclosure, bounded holes | `SelectedFloorScanner` and `FloorCeilingResolver` | Produce canonical geometry; downstream code does not add cells or infer another storey. |
| Exact floor membership and vertical intervals | `FloorGeometry` | Bounds and map outlines are derived views. |
| Room components and boundary ownership | `RoomPartitioner`, with world-aware boundary evidence supplied by `BuildingRoomScanner.components` | Partition the selected floor; do not discover another floor. |
| Action, selected seed, and persistent target | `RoomScanPlanner` → `RoomScanPlan` | One shared implementation used by both sides. |
| Request checks, evidence reuse, type choice | `RoomWorkflow` | Consume the plan; do not select a different room during materialization. |
| IDs, save publication, rollback, dirtiness | `VillageManager` and the existing `Village` publication boundary | Validate payloads and publish changes; do not infer a different user action from sentinel IDs. |
| Labels, buttons, map rendering, packet projection | `BlueprintScreen` and existing renderers | Display/project the shared plan; do not implement independent floor/room rules. |

The client has a server-supplied village snapshot and a local world view. The server has authoritative data and recomputes through the same planner for each request. One source of rules does not mean trusting a client plan or retaining a preview scan for a later confirmation.

## Required behavior

### R1. Preserve selection and provenance

Keep these roles distinct: interaction position, canonical selected cell, traversal anchor, and persisted structure/floor/room IDs. Do not replace them all with raw player Y, a nearby cell, or a floor-number label.

Under unchanged valid geometry and saved identity:

- ordinary supported positions in one unregistered enclosed room select the same canonical floor/component and compatible Add action/target;
- positions already owned by the same registered room resolve Update for that same room;
- supported slab/bed positions and accepted shelf-obstructed positions resolve through canonical ownership rather than borrowing a neighbor's room;
- scanning from opposite orders does not change geometry or target.

This does not authorize treating every connected position as the same registered room. Preserve the current distinctions for newly expanded unregistered cells, stair rows, unsupported connector handoffs, shared door cells, overlapping rooms, and different heights in the same column. In particular, keep the expectations of `freshSameFloorOverlapDoesNotInventRegisteredRoomIdentity`, `freshCellDoesNotBorrowRoomOwnershipFromAnotherHeight`, and `staircaseTransitionOverlapDoesNotInventRegisteredRoomIdentity`. A merge/split or ambiguous ownership must not be silently resolved by proximity or component overlap alone.

### R2. Blueprint is a projection of the plan

Remove the redundant independent `RoomScanMode` argument from `BlueprintScreen.structureScanMessage` and its sending helper. The supplied plan alone determines the packet action and expected target. Preserve the current mapping:

| Plan mode | Existing packet action | Expected target ID |
| --- | --- | --- |
| `ADD_BUILDING` | `ADD_BUILDING` | `-1` |
| `ADD_ROOM` | `SCAN_ROOM` | `-1` |
| `UPDATE_ROOM` | `SCAN_ROOM` | Selected room ID |
| `ADD_ATTACHMENT` | `ADD_ATTACHMENT` | Logical building ID |

Keep floor removal, inheritance, main-room controls, labels, and selected map floor behavior. Do not move client rendering/projection code into the server planner.

Keep the existing position/village-response preview invalidation for this pass. A stationary block edit may leave a cached preview stale until it refreshes; the server must still reject an invalid operation without mutation. A new preview refresh mechanism requires a demonstrated need and separate scope, rather than being introduced as part of this consolidation.

### R3. Carry explicit intent to commit

Carry `RoomScanPlan` in the existing `BuildingScanResult`. Successful workflow results must carry the actual plan; update constructor call sites and tests instead of retaining a constructor that guesses it from `pendingStructure`, negative IDs, or room geometry. Existing result source information remains feedback/provenance metadata and must not become an alternative target-selection rule.

`VillageManager.commitRoomAddition` dispatches from that plan:

- `ADD_BUILDING` and `ADD_ATTACHMENT` use the existing initial-registration path with their distinct target/numbering validation;
- `ADD_ROOM` uses the existing structure-refresh-and-room-addition path for the plan's exact persisted structure/floor;
- `UPDATE_ROOM` is handled by the existing registered-room update path, never accepted as a room addition.

All successful production addition paths already prepare a detached pending structure. Require that payload at the commit boundary, validate it against the explicit target, and remove the unused addition branch that accepts a bare room without a prepared structure. Do not retain a compatibility path that guesses the mode. Missing or inconsistent payloads fail without mutation.

Keep `targetBuildingId()` feedback semantics: an attachment yields the plan's logical-building target; ordinary additions yield `-1`. `withSource`/`withPendingStructure`, where still needed, preserve the plan rather than derive a new one.

### R4. Reuse evidence within one operation

Use the existing `RoomScanPlanner.Analysis` and `SelectedFloorScanner.Observation`; add no parallel analysis context.

When planning already produced a valid selected-floor observation and world-aware partition, materialization/update consumes that same evidence after the existing target-specific validation. An update must not discard a recovered canonical seed and rediscover selection from the raw interaction position.

Keep the cheap registered-room lookup: it need not run an expensive fresh scan merely to display Update. Execution may then obtain the one fresh scan it needs. Existing bounded candidate/handoff probes remain legitimate; “one operation” does not mean one seed probe regardless of geometry.

Type confirmation is a new operation. Replan/revalidate current world state and the transmitted expected target, preserve the existing continuation-position check, and reject incompatible actions or changed expected identities. Never reuse world evidence across the type-choice round trip.

This guarantee is bounded by the unchanged packets: Update carries a room ID; attachment carries a logical building ID; Add Room carries `-1`. Confirmation does not carry the original Add Room component/structure/floor identity or attachment floor number. Recompute those from current geometry and numbering, and validate the chosen type against the current result. Do not claim that every change since the earlier prompt can be detected. Stronger cross-round-trip identity pinning would require the extra protocol/state explicitly excluded here.

Keep intentionally different reported-building neighbor discovery and manual selected-position behavior; do not merge their fallback policies merely to reduce method count.

### R5. Share reconstruction, preserve update scope

Single-room and full-floor refresh already share low-level scanning, partitioning, and detached structure refresh helpers. Consolidate their remaining repeated component materialization and copying of stable room identity/type/inheritance metadata into one small method inside `RoomWorkflow`.

Preserve the policy differences:

- an individual update replaces only the selected room and keeps unrelated rooms unchanged;
- full-floor refresh matches all registered rooms one-to-one, rejects merges/splits/lost identity, and does not register fresh unregistered components;
- forced types remain forced; automatic type resolution continues to use the correct individual or prospective batch context;
- preparation never publishes live state;
- failed publication retains existing rollback and ID-allocation behavior.

Do not unify these policies through boolean flag combinations, a generic transaction engine, or a second copy of building state.

### R6. Retain the fixes already made

Retain partial-head-obstruction ownership checks, equal-height bed/surface deduplication, floating-bottom-slab support, and per-operation rejected-region deduplication.

Preserve the current wall-opening proof: at most two cells wide, at most four blocks high, continuous physical frame evidence including thin jambs. Preserve one/two-cell AIR roof-gap proof with physical cardinal borders and no chaining through inferred gaps. Genuine exterior breaches, larger gaps, fluids/leaves as invalid roof evidence, solid/flooded source cells, and unavailable columns must still fail appropriately.

NPC discovery retains 512 floor cells, radius 16 with one radius-32 retry after `SIZE_LIMIT`, 8,192 observed dependencies per entry, 65,536 total observed dependencies, and a shared 20-attempt admission budget. Manual and NPC resource limits remain intentionally different; do not demand universal success parity for oversized floors.

## Acceptance and evidence

1. The mode cannot be supplied independently of the plan at the Blueprint scan-message boundary.
2. Equal village identity/world evidence produces matching preview and server-plan action/target. Test the village save/load snapshot path; do not claim this alone proves a live client/server session.
3. Equivalent supported positions in unchanged representative rooms agree on exact floor geometry, anchor, selected component, and permitted target. Cover ordinary floor, uneven slab, bed, shelf, bounded wall/roof opening, and normal stairs with their existing ownership rules.
4. Expected-room mismatch still yields `TARGET_CHANGED`; physical rejection retains its physical failure result. Wrong actions and invalid payloads do not fall through into another operation or mutate state.
5. Type-choice deferral/confirmation, forced types, floor numbers, sibling rooms, and rollback remain covered by existing suites plus focused regressions at changed seams.
6. Existing common tests, focused floor/cache/refresh GameTests, and both loader compilations pass for the implemented source state. Record actual counts; the earlier 684/116 results are historical baseline evidence, not completion evidence for this plan.
7. Scoped production Java is net-negative overall, with no new top-level production classes or packet/save changes. Report any required exception rather than hiding growth in another file.

The original `jayhouse.schem` and supplied positions remain useful acceptance material. Any mod-substitution replay must state its limitations. Do not alter the original schematic, user instance, or saves. A normal client/server retest must be reported separately from JVM/GameTest proof.

## Out of scope

Changing building/floor/room persistence identity; renaming the domain model wholesale; automatic multi-storey discovery; terrain/map rendering redesign; new preview networking; relaxed exterior validation; broad cache redesign; unrelated AI/navigation work.

## Builder discretion

Choose private helper names and local collection forms where they preserve the contracts above and reduce code. Keep established packet/result error categories. Do not rewrite already-correct geometry algorithms or invalidate passing boundary tests to make the consolidation easier. Expose no new public API solely to inspect internal counters in tests.
