# Shared floor observation without behavior or code-size growth

Date: 2026-10-17. Original scope approved for inline implementation on 2026-10-18.
The 2026-10-20 orchestration addendum below is a document revision for review,
not a claim that the additional implementation has started.

## Intent and success criteria

Reduce floor-scanner complexity while keeping the current behavior and keeping
production Java LOC at or below the implementation baseline. The user approved
writing this specification and identified shared observation as the likely first
cleanup. On 2026-10-18 the user approved checkpointing the floor fixes, updating
this specification and its plan, and implementing the cleanup immediately.

The review confirmed the distinction to retain: fresh discovery does not use
saved membership as traversal walls; persisted membership identifies existing
Rooms; ceiling, surface and transition caches live only within one operation.
Do not remove those caches or rescan registered Rooms merely to choose a UI action.
The remaining duplication is physical attachment probing outside that observation,
not a requirement to eliminate persistence or all caching.

Success means fewer independent physical probes and repeated attachment checks,
with unchanged geometry, ownership, action selection, validation and persistence.
A transfer of methods between files alone does not meet the complexity goal.

Work is scoped to `dev/1.21.1`, Minecraft 1.21.1, Java 21. The checkout is
`C:/Users/Mik/Downloads/MCA/minecraft-comes-alive-1.21.1-floor-clean-squash`.
No WSL or new worktrees. Preserve all existing tracked and untracked work.

## Source baseline and historical decisions

At drafting time HEAD is `3c89a0f1b` (Scanner fixes). The working tree also contains
ongoing scanner corrections, house reconstructions and unrelated navigation
work. HEAD alone is not the baseline for this refactor. Before implementation,
establish that the other work has settled, capture the exact relevant source
contents and hashes, and verify the affected regression suites on that state.
Do not overwrite or absorb another agent's changes. If that state changes during
implementation or verification, reconcile ownership and establish which source
was actually tested before making equivalence or LOC claims.

Implementation baseline: `f9de24136ea7f3a6c6515c2da93b185865b87243`, the
floor-only checkpoint. Its production sources include the slab-tower, furniture
and bounded-exterior corrections. Unrelated dirty AI/navigation work is excluded.

The September 16 ownership design, September 17 selected-floor simplification,
September 19 boundary-semantics amendment (in the September 18-named file), and
October 5 floor-regions design provide context. Later amendments and the current
verified behavior take precedence over superseded drafts. Do not combine all
historical designs into a new set of requirements.

Preserve the current distinctions between exact structural membership, walking
clearance, transition ownership, semantic floor numbering and saved identity.
The furniture, staircase and stale-ceiling corrections are baseline behaviors,
not candidates for reversal merely to reduce lines.

## Concrete duplication to remove

`SelectedFloorScanner.Observation` owns operation-local ceiling and surface
evidence. `StructureScanner.resolveAttachmentSeed` creates that observation,
but returns only the selected result. Some handoff checks create separate
`FloorCeilingResolver` instances rather than sharing the existing observation.

`StructureScanner.FloorObservation` currently carries the selected result and
vertical connector connections. `RoomScanPlanner` passes those to
`Village.selectAttachmentTarget`, where a new ceiling resolver and calls to
`SelectedFloorScanner.inspectSurfaceCell` gather additional live-world evidence.
The same live-contact relationship can then be checked during overlap rejection
and target selection through separate loops.

This creates a useful, narrow consolidation target. It does not establish that
the current behavior is wrong, or that all handoff branches are redundant.

## Proposed ownership and data flow

One selected-floor operation owns the physical observation used for selection,
necessary handoff probes and live attachment-contact evidence. Reuse the existing
observation and caches; do not add a parallel scanner or retained world snapshot.

`StructureScanner.observeFloor` retains that operation's observation while
resolving the selected result. Seed/handoff helpers consume the same observation
where they currently sample the same physical predicates. Their candidate
ordering, precedence, ambiguity checks and acceptance predicates stay unchanged.
Entry points with intentionally different policies, such as new-structure and
reported-structure scans, retain their existing ordering and validation.

After successful selection, gather the live structural-contact relationships
needed to compare the candidate with relevant persisted floors. Reuse exact
overlapping columns and the observation's ceiling/surface predicates. Record each
candidate-to-persisted-floor live-contact result once for reuse by attachment
validation and selection. Do not scan those persisted floors again, recursively
discover adjacent storeys, or build a whole-building graph.

Extend the existing `FloorObservation` with the minimum immutable contact data
required by its consumer. Prefer an existing structure/floor relation carrier
where it expresses the relationship without adding another public model. The
data identifies observed contact with a particular saved floor; it does not
grant ownership or select a winning building. It must not contain a world-reading
callback or expose mutable caches. Treat the result as operation-local evidence,
never as authority across a later confirmation or mutation.

`Village` retains saved identity, overlap policy, candidate ranking and target
selection. Its attachment path consumes the captured live-contact facts instead
of performing fresh physical ceiling/support probes. Pure comparisons of stored
geometry remain legitimate there. `RoomScanPlanner` projects the result into the
existing action and prospective floor number.

Fresh confirmation/commit validation remains authoritative. Sharing an observation
within an operation must not reuse stale evidence from an earlier analysis after
the world or registered structures change. Preserve current world-thread ownership.

## Evidence distinctions that must survive

There are three different inputs to attachment:

| Input | Meaning | Required boundary |
| --- | --- | --- |
| Vertical connectors and scanner stair-transition matches | Explicit connection to a saved floor | Preserve the existing connection set and its precedence. |
| Contact inferred from stored exact geometry | Existing interval/shell evidence | Preserve current formulas, ranking and rejection behavior. |
| Contact verified in the current world | A live ceiling/support relationship, including stale saved ceilings | Keep separate from explicit connections. |

Do not append live-contact relationships to `verticalConnections` or the merged
explicit connection set. Currently live contact can excuse otherwise unproven
overlap only when the explicit connection set is empty. Explicit connection to
one floor can also permit certain distinct-band overlap within the same logical
building. Treating all contact as one generic connection would broaden those
permissions and violate behavior preservation.

Review amendment: determine explicit vertical and saved stair-transition evidence
before collecting live contact. If either supplies an explicit connection, skip
live ceiling/support probes entirely: neither target selection nor overlap policy
can consume live contact on that path. Reuse the same pure explicit-evidence
resolver for this guard and Village policy rather than duplicating matching rules.

Preserve the precise live-contact predicate: choose lower/upper cells in the same
column, require the current lower ceiling to equal the upper supporting block's
Y, and require the existing lower surface inspection to succeed. Stored interval
meeting and stored shell touching are separate predicates with different formulas;
do not normalize them into an assumed universal touching rule.

With no world, live contact is absent. Existing geometry-only/JUnit paths must
retain their results without a fabricated world or changed inference rules.
Update callers together rather than leaving a second implementation as a
compatibility path. Immutable absent evidence is sufficient where appropriate.

## Preserved behavior and exclusions

- Preserve exact floor cells and ceilings, selected seed, supported-source
  meaning, canonical anchors, transitions, vertical boundaries and adjacent seeds.
- Preserve structural membership below furniture separately from clearance over
  its actual top. Retain the chest/eaves correction and bed regressions.
- Preserve stair halves, turning landings, short storeys, bounded label grouping,
  door facing/ownership, trapdoor halves and ladder-exit contracts.
- Preserve all existing room/building/floor identities and floor numbers, saved
  fields, inheritance, overlap checks and atomic publication boundaries.
- Preserve accepted and rejected attachment targets, ranking, tie rejection,
  existing validation results and fallback ordering.
- Preserve radius/size limits, operation-local caching and lazy selected-floor
  discovery. Contact collection is limited to relevant overlapping saved floors;
  do not inspect unrelated floors or do work on an early-return path that needs
  no fresh observation. Do not claim a performance gain without measurements.

Manual building selection, new ambiguity UI, new failure/result types, changes
to existing Add Building fallthroughs, save repair/migration, renumbering,
navigation changes and forward ports are outside this spec. A mismatch between
old prose and current behavior is a separate decision, not permission to change
the outcome during this refactor. A newly exposed bug needs its own reproduction
and scope decision.

## Deletion and LOC budget

Expected production files are `SelectedFloorScanner.java`, `StructureScanner.java`,
`Village.java` and `RoomScanPlanner.java`. Touch `FloorCeilingResolver.java` only
if a smaller shared query requires it. Avoid new production files, generic
evidence frameworks, pass-through service layers and broad renames.

Required deletion targets are the attachment-local ceiling resolver and physical
world-probe implementation in `Village`, duplicate live-contact iteration, and
separate resolver allocations for handoff checks covered by the shared operation.
Keep one implementation of the live-contact predicate. Delete helpers that become
unused; do not retain old and new paths behind a switch.

The hard size gate is aggregate physical production Java lines across every file
changed by this refactor: final minus baseline must be at most zero. Count every
new production Java file in full. Record per-file and aggregate deltas against the
captured source baseline, including the already-present fixes; do not compare
only against HEAD. A receiving file may grow while the former owner shrinks.
This aggregate rule is the budget for this task; the historical per-file budget
belonged to its earlier cleanup.

Report nonblank production lines as an additional check. Do not meet the physical
line budget by compressing statements, deleting useful explanations, moving logic
into resources, or hiding complexity in abstractions. Tests and this document are
reported separately and cannot offset production growth. Do not delete behavioral
coverage to make the totals look smaller.

If shared observation cannot eliminate enough duplicated work/code to meet the
budget cleanly, stop at the measured proposal and discuss a smaller cleanup.
Changing gameplay or adding a replacement framework is not an allowed escape.

## Verification and acceptance

Establish a passing baseline for the affected tests after concurrent fixes settle.
The primary proof is unchanged behavioral assertions before and after the refactor.
For representative fixtures, compare exact cells/ceilings, anchors, seeds,
transition/boundary evidence, actions, target IDs, prospective numbers and commit
results. Use deterministic ordering for comparisons; do not assert hash iteration
order. Do not keep a second production implementation for differential testing.

Required coverage includes:

- Chest/eaves enclosure, bed-top selection, furniture footprint and low clearance.
- Reported staircase houses, including downstairs registration, upper selection,
  successful attachment/commit and unchanged downstairs room identity.
- Stale lower ceilings after adding an upstairs deck and irregular/ladder-adjacent
  selection, using the existing regressions.
- Explicit connector and stair attachments, external basement contact, detached
  floors, unproven overlap, already-registered copies and equally plausible targets.
- An explicit connection combined with live contact to another floor/building,
  verifying that the existing overlap restrictions are not relaxed. Add a focused
  characterization fixture if the baseline does not already cover this distinction.
- Geometry-only/no-world attachment paths and revalidation after analysis when
  relevant world evidence or saved ownership changes.

Use the existing common JUnit lane for geometry/policy and NeoForge GameTests for
world collision and registration. Run relevant common tests first, then the floor
scanner and affected house classes. The existing selector is
`-PmcaGameTest=ClassName` or `-PmcaGameTest=ClassName#methodName` with
`:neoforge:runGameTestServer`. Compile common and both loaders, run the full common
tests and one final full NeoForge GameTest integration run before calling the
implementation ready. Diagnose failures against the captured baseline; do not
rewrite assertions to accommodate changed outcomes.

Completion requires all of the following: the affected behavior remains equal,
`Village` no longer gathers live physical evidence during attachment selection,
the selected operation shares its relevant physical observation, obsolete probes
and helpers are removed, production LOC does not increase, and unrelated work is
preserved. Report source state and executed tests with the LOC comparison.

Source-level equivalence and 1.21.1 GameTests do not establish live behavior on the
modded 26.3 server. That remains a separately reported verification boundary.

## Review and handoff

The user explicitly authorized immediate inline implementation on 2026-10-18.
Plan: `docs/superpowers/plans/2026-10-18-shared-floor-observation.md`.
Forward ports, user-world changes and pushes remain excluded. The user subsequently
declined further test isolation: use normal Gradle tasks and confirm actual test
registration, avoiding overlapping standard GameTest launches when practical.

## Implementation status (2026-10-18)

Implemented against checkpoint `f9de24136`: handoff and live-contact probes share
the operation's existing surface/ceiling observation; Village attachment selection
consumes immutable facts rather than reading blocks. Explicit vertical/stair
evidence is resolved first and suppresses unused live-contact probing, addressing
Franklin's review finding. Persistent identity and per-operation caching remain.

Production size decreased by one physical and one nonblank line overall. All 596
ordinary tests, 99 focused scanner GameTests and 7 saved-house/tower GameTests pass.
The full 656-test NeoForge integration run had 28 failures, recorded by name in
the plan; global readiness is therefore not established. Do not treat all those
failures as unrelated without baseline/suite diagnosis. No forward port or live
26.3 verification was performed, and existing saved floor ownership is unchanged.

## Approved architecture follow-ups (2026-10-18)

The user approved review items 1, 2, 3 and 5, explicitly deferring item 4
(NPC cache observation/invalidation dependencies). These follow-ups extend the
completed simplification; they are not a claim that every tower or live 26.3
scenario is fixed.

- A refresh of a persisted Floor is not discovery of the player's upper Floor.
  Diagnostics report a changed modal cell height without asserting a new storey.
- FloorObservation captures the merged explicit connector/stair matches once.
  Live shell contact remains separate and never grants explicit-connector permissions.
- Add Building shares discovery within that server operation. Attachment and new
  structure selection retain their different fallback precedence. Room partitions
  are reused only for the identical selected observation result. Later UI confirmation
  and later operations still observe the world afresh.
- Loaded-only block views expose non-loading column availability to the same scanner
  guards used by LevelReader. Unknown columns cannot prove a finite exterior edge
  or be read as air during seed fallback. The NPC cache's invalidation footprint,
  lifetime and observation limit are unchanged.

Execution/evidence ledger:
`.superpowers/sdd/2026-10-18-floor-architecture-followups/progress.md`.

## NPC cache dependency follow-up (item 4, subsequently approved)

The user subsequently approved item 4. The earlier deferral above records the
previous scope, not the current decision.

IndoorRoomCache must validate the evidence used to accept a room, not only the
canonical geometry it returned. Its existing loaded-only discovery view now
captures normalized block states read during discovery/partition, including
exterior supports, natural edges and ceiling rays, plus queried loaded/unloaded
column availability. The entry combines those facts with the existing final
floor/room observation. Validation remains non-loading and uses the existing
40-tick interval; bed occupancy and open/closed toggleable state remain normalized.

Both block and column facts count toward the existing 8192-entry dependency cap
and 65536-fact shared limit. If a full proof does not fit, discard the partial
dependency snapshot and rediscover on the next validation boundary. Keep room
geometry usable until then rather than rejecting an otherwise valid room or
allowing a truncated proof to certify it indefinitely. Unchanged complete proofs
still reuse their Room objects across validation; this is not blanket cache expiry.

No enclosure-policy, saved ownership/numbering, TTL, global hook or alternate
geometry pipeline is introduced. Auxiliary connector-read views remain nonrecording.
This trades periodic rediscovery for bounded memory only on proofs too large to retain.

Runtime regressions cover exterior support extension, a new exterior ceiling above
the old physical band, and a large proof that exceeds the dependency budget. Each
compares cached outcomes with independent fresh discovery; unchanged small-proof
identity reuse guards against solving the problem by disabling the cache.

Execution ledger: `.superpowers/sdd/2026-10-18-indoor-cache-dependencies/progress.md`.

## Unchanged tower refresh regression (2026-10-19)

Registering and repeatedly refreshing an unchanged Floor must preserve its exact
cells, ceilings, connectors, Room membership and saved identities. Refresh still
targets the registered Floor rather than rediscovering whichever storey contains
the player's current step. Prefer a supported ordinary cell near its saved anchor;
explicit stair rows can extend below that anchor and must not select a different
seed-relative discovery band. Reuse the operation's stair classification, with
the existing supported-cell fallback when no ordinary support remains.

This does not freeze saved geometry or relax overlap checks: real world edits
still receive a fresh scan. The reported slab tower must pass repeated refreshes
from multiple lower-floor/stair positions at both 2048/32 and 8192/320 scan limits,
and preserve its three registered storeys through refresh and save/reload.

## Explicit missing-storey insertion and short stair turns (2026-10-19)

This is a separately approved behavior correction, not part of the original
behavior-preserving refactor. A genuine missing storey may be inserted between
existing labels: Add Floor shifts higher positive labels upward, or lower basement
labels downward. `FloorGrouping` returns a pure insertion decision; the existing
atomic addition commit revalidates and applies its label shifts only to the target
logical Building. Preview, loading, raw registration and ordinary Room updates
must not renumber anything. Preserve all existing IDs, anchors, geometry, Room
ownership and main-Room identity. Reject stale labels and integer overflow;
failed or repeated additions must not leave shifts behind.

A short flat turn joining full-block stair flights is part of the flight, just
like an equivalent slab turn. Extend the existing through-landing proof with
explicit stair evidence and full-step rises/drops; retain its doorway and usable
level-depth guards so real intermediate Rooms keep their own storeys. The saved
two-block right-angle turn must select the same physical Floor as downstairs.
Refreshing that saved downstairs Room may adopt formerly omitted turn cells;
this is not permission to assign an unowned cell to a nearby registered Room.

Regression evidence uses the existing tower SNBT plus a 58-block delta and copied
registration state from the stopped test save. It is not live 26.3 verification.

## Incremental simplification and floor-batched refresh (2026-10-20)

The user asked to fold the architectural recommendation into this spec and the
current plan. Preserve current registration, type selection, independent Room
roles, inheritance and contribution controls. These support the user's eventual
compound-Building/subroom goal, such as a prison with a cafeteria; implementing
new compound recognition or a new hierarchy is not part of this revision.

The existing physical pipeline is already shared. Simplification should remove
repeated orchestration around it, not replace the scanner or merge intentionally
different Add Building, Add Floor, Add Room and Update Room policies. One physical
observation is shared within an operation; later operations observe the world
again. Structural membership, movement clearance, storey boundaries, attachment
permission and saved identity remain separate decisions using that evidence.

Implement in two phases in
`docs/superpowers/plans/2026-10-19-floor-system-simplification.md`:

1. Small cleanup: let Observation implement its existing StepProvider directly
   and remove the redundant StructureFloor geometry-replacement overload. Keep
   the focused ceiling, partition/materialization and inheritance owners.
2. Floor-batched whole-village refresh: observe each registered physical Floor
   once, partition once, reconcile its registered Rooms together and publish the
   complete replacement set through the existing atomic boundary.

The user's separately requested ceiling policy is also explicit: search from
feet Y + 1 through min(feet Y + 128, exclusive world maximum Y - 1), inclusive;
skip nonempty fluid states, including waterlogged collision shapes, without
extending that distance. Retain the existing leaves and collision predicates.
This intentionally excludes roofs more than 128 blocks above the feet and is
not a behavior-preserving cleanup.

### Why whole-village refresh needs a different reconciliation scope

At revision baseline `4cc3b86f7` plus the current dirty tower fixes,
`VillageManager.fullScan` iterates registered Rooms. Each
`RoomWorkflow.analyzeRegisteredRoomUpdate` scans and partitions the entire saved
Floor, then compares the selected replacement with stale sibling memberships.
If Room A is unchanged but an exterior wall shrinks Room B, A's update can reject
the missing old B cells before B is refreshed. This is source-predicted R1 in
`docs/superpowers/reviews/2026-10-19-floor-pipeline-architecture-review.md`;
runtime reproduction remains a prerequisite, not an already-passing claim.
During document revision the shared checkout advanced to `1ef6fc025`, which
checkpointed the inspected floor fixes alongside other work. Re-capture the
actual source baseline before execution; this document revision made no commit.

Keep that sibling guard for a selected-room update. Whole-village maintenance
instead reconciles the complete registered Room set for each
`(structureId, floorId)`. Never group by displayed floor number or only by logical
Building: separate Structures can share a label without sharing scan geometry.

### Batch reconciliation and publication contract

- Enumerate only Floors referenced by registered functional Rooms, ordered by
  structure ID then floor ID. Choose the lowest-ID Room's saved source as the
  deterministic request source; retain scanExistingFloor's ordinary supported
  seed preference and fallback policy. Do not scan unregistered storeys.
- Use one top-level scanExistingFloor call and one world-aware components call
  per target key. Materialize matched Rooms from those components using existing
  Room/POI owners. No second scanner, retained world snapshot or global cache.
- Match identities using the fresh FloorGeometry.roomIdentityOverlapCount,
  retaining its boundary-cell exclusions. Every saved Room must overlap exactly
  one fresh component, and no matched component may overlap another saved Room.
  Zero matches, splits and merges return the existing OVERLAP result. Do not
  choose by nearest position, largest overlap, room ID or majority voting.
- Unmatched fresh components remain unregistered. Successful replacement has
  exactly the original Room ID set; no automatic Room creation/deletion, ID
  allocation, ownership transfer or main-Room replacement occurs.
- Preserve structure/logical-Building/floor IDs, saved floor labels and semantic
  anchors, forced direct types and flags, inheritance and contribution controls.
  Refresh exact geometry and POIs. Use the current RoomTypeResolver for automatic
  direct types with all of this Floor's replacements in the prospective set;
  inherited presentation uses the resulting published Rooms. Add no compound
  classification rules or new type-selection prompts to maintenance.
- Validate cross-Structure overlap with the current scanner policy. Unknown
  columns and exceeded budgets remain failures, not permission to invent a
  boundary or substitute saved geometry. Preserve underlying scan/materialization
  failure results rather than translating every failure to OVERLAP.
- Analyze without mutating registered objects, then call the existing
  Village.publishFloorRefresh once with the complete replacement set. For later
  Floors of the same Structure, use its current copy so earlier refreshed Floors
  cannot be overwritten by an older Structure snapshot.
- Retain the whole-village BuildingStateSnapshot and finalize the manager only
  after every Floor succeeds. On a later analysis/validation/publication failure,
  restore all earlier Floors, Rooms, POIs, types, labels and logical metadata.
  On a RuntimeException restore the same snapshot before rethrowing; do not
  swallow programming failures or leave earlier Floors partially updated.
- Keep all work synchronous on the owning server thread, with no interleaved UI
  confirmation or reuse across requests. The existing floor publication and
  settlement rollback boundaries remain the owners; no new transaction service.

### Acceptance and scope boundaries

First reproduce the sibling-shrink rejection in a focused GameTest and preserve
the selected-room rejection on that same edited fixture. Then prove the batch
succeeds with both IDs and roles intact. Cover merge/split/missing identity,
unregistered components, multiple Floors of one Structure, later failure and
publication-exception rollback, insertion-order independence, unchanged tower
refresh and save/reload. Retain the current doorway, enclosure, attachment,
missing-storey insertion and dependency-aware NPC-cache regressions.

The batch is an explicit maintenance correctness correction, not permission to
weaken selected-room validation or broadly change scan results. Any other newly
discovered behavior difference needs a focused failing reproduction and scope
decision before another exception is added. Diagnostic provenance/double-scan
changes remain deferred; a network-preview redesign remains out of scope.

Keep the non-positive aggregate production LOC gate for pure cleanup and report
physical/nonblank deltas against the captured current source. Report the ceiling
policy and batch correction separately as well as the overall delta; moving code
is not a saving. No large LOC or timing reduction is promised. If the batch needs
net production growth beyond the cleanup budget, present the measured proposal
for explicit approval rather than hiding it in the refactor tally.

This addendum records the requested design revision. Review the revised spec and
plan before implementation; the historical 2026-10-18 implementation approval is
not approval to execute newly added tasks. No source, tests, saves, index or
commits are changed by this document revision.
