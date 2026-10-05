# Floor regions and transitions

Status: approved by the user on 2026-10-05; source implementation verified; floor-only ports follow.

## Intent and scope

Generalise MCA's selected-floor discovery so a room surface, a stair, and a
turning landing select the same resolved floor ownership. Resolve membership
before choosing geometry; replace the special stair-start rescan with a common
selection model. Work starts on `dev/1.21.1`, Minecraft 1.21.1 and Java 21.

The user wants the small Y=76 turning-landing room in Ore The Highway to count
as part of the upper storey containing the Y=78 rooms. Preserve the established
exclusive staircase halves: an odd middle row belongs upstairs.

Explicit exclusions:

- No migration, automatic repair, or new automatic renumbering of existing saves.
- No edits to the user's world or mods as part of implementation or testing.
- No changes to unrelated navigation, villagers, beds, or GameTest registration.
- No new persisted region graph, background scanning, retained cache, or setting.
- No changes to the current door/ladder boundary contracts or room inheritance.

The prior floor-only forward-port scope remains separate from the source change:
once the source is verified, port only this subsystem through `dev/26.1.2`,
`dev/26.2`, and `dev/26.3`. Target verification remains Java compilation only,
as requested. No push or publication is part of this design.

## Evidence in the current implementation

`SelectedFloorScanner.Observation` already owns operation-local surface probes,
ceiling evidence, movement edges, and flight ownership. `resolveSelectedFloor`
chooses a traversal seed, scans with its height band, then sometimes scans again
from the dominant materialised height when the requested cell is a stair. This
couples the interaction point, flight endpoint, and floor classification.

`StairFlightOwnership` currently maps cells to individual landing surface cells.
`FloorBandClassifier` compares a landing's walking height to the selected band's
anchor. A landing joining ascending and descending flights is excluded from a
combined flight, which is a useful separation but does not itself give that
landing a canonical floor-region identity.

Numbering has a separate inconsistency: `RoomScanPlanner.adjacentFloorNumber`
uses the referenced floor's number plus the sign of the height difference.
`StructureFloor.floorNumbers` instead groups bounded height bands. These two
policies can assign different numbers to rooms in the same storey.

The inspected saved building 313 contains main surface heights 67, 72, 76, and
78. Its Y=78 rooms have both floor numbers 2 and 3, while its Y=76 landing is
numbered 2. No two of those stored structures share an exact feet position.
This is evidence for the new model, not authority to rewrite those records.

## Proposed model

Use three distinct concepts within the existing owners:

1. A **surface region** is locally connected room or landing surface evidence.
   It has a deterministic representative height and position. It is discovery
   evidence, not a persisted room and not the final structural footprint.
2. A **floor group** associates nearby eligible surface regions with one semantic
   storey. Separate rooms and physical `StructureFloor` records may share a group
   and number without merging geometry or identities.
3. A **transition** connects surface regions. Cells along a proven inter-floor
   flight receive ownership from its endpoint groups. Movement evidence remains
   separate from structural cells and persisted connector markers.

Keep these results local to `SelectedFloorScanner.Observation`. Use small private
immutable carriers and existing collections where sufficient. A separate helper
is justified only if it owns the shared, pure grouping policy needed by both
scanner discovery and prospective numbering. Do not add an independently mutable
storey registry or public model API.

## Discovery, grouping, and selection

### Surface evidence

Reuse `floorSurfaceLevel`, `findLandings`, `FloorCeilingResolver`, and the existing
headroom, connector, and enclosure predicates. Minecraft collision shapes remain
the source of walking-height evidence. Block metadata can prove a stair but must
not select its semantic floor by direction.

Discover stable room/landing regions and the transitions touching them. Retain
the existing same-height landing evidence and full-block-stair recognition
contracts as the starting point. Cells with no stable region must not acquire
an arbitrary neighbouring room identity.

For a connected stable surface region, derive its representative height from
its ordinary landing/room surface cells; select the most frequent feet height,
breaking ties toward the lower height, consistent with the current geometry
anchor convention. Transition rows must not outvote the region's main surface.
Use existing deterministic cell ordering to select its representative position.

A small room or landing is eligible regardless of its area. Do not introduce a
minimum-area heuristic that would discard the user's small turning room.

### Floor groups

Group eligible regions within the same proven building context using the existing
two-block height tolerance. A group's full representative-height range must fit
that tolerance: 76 and 78 may group, but 76, 78, and 80 must not become one group
through pairwise chaining. Sort by height and deterministic position, then compare
each candidate with the lowest representative height of the group.

Height tolerance is a semantic convention, not physical membership. Preserve
the existing exact-column separation and overlap validation so grouping labels
cannot merge room footprints or erase physical boundaries. A group may contain
door-separated regions; the door still partitions rooms. Proximity alone does
not attach another logical building.

The Y=76 turning region and eligible Y=78 regions therefore share a group. A
90-degree staircase is represented as a flight, a stable region, and another
flight. Resolve each flight independently around that stable region.

### Transition ownership

Resolve both endpoint surface regions before assigning a proven flight's rows.
Retain the tested physical allocation by distinct ascending step heights,
independent of flight width or compass direction; the odd middle row belongs to
the upper region. Sharing a semantic floor number does not merge those regions
or change this ownership. Ordinary uneven surfaces retain their local traversal
rules.
Descending interaction uses that same allocation, not a second rule.

Do not replace proven odd-row semantics with distance-to-player or raw block-count
allocation. Slabs and partial blocks retain their actual collision heights.
Preserve full-block flights, ordinary uneven floor surfaces, and slab transitions
using the same region/transition ownership decision, with their existing evidence
requirements. Do not assume every ascending surface is an inter-storey staircase.

### Selection and materialisation

Resolve the requested supported surface or existing connector handoff to its
physical region. Traverse and materialise the selected floor using that region's
canonical classification. Semantic groups determine labels separately. Preserve the original interaction position and supported-source
provenance for subsequent Room lookup and server target validation.

All equivalent seeds in the same physical ownership component must produce the same
feet positions. Ceiling and connector metadata must also be deterministic for an
unchanged world. Selection of a lower stair half and its upper walking cell must
use the same physical transition ownership when both refer to that half.

Retain structural materialisation for furniture, solid interior columns, and
valid climbable openings; stable surface discovery does not authorise a flood
through walls. Preserve the one-air-pocket wall regression.

Remove the stair-specific second scan only when equivalent-seed tests pass through
this model. Remove obsolete landing-to-height ownership decisions and duplicate
anchor selection within the affected scanner path. Do not leave both old and new
classification systems active for the same proven transition.

## Numbering and saved-data boundaries

Use the shared bounded-group policy for prospective floor numbers. A candidate
joining an unambiguous existing group reuses that group's number, even if its
surface is above the directly attached landing. A distinct group gets the next
appropriate available number relative to the fixed ground reference and relevant
neighbours. Ground remains 0 and basements remain negative.

Stored Structure, Floor, and Room identities remain authoritative for existing
registrations. Loading or viewing a save must not invoke a new repair pass. An
explicit room rescan can update its geometry under normal validation but must not
use that operation to silently reclassify all saved floor numbers.

For a candidate group whose saved members disagree about their number, or a new
intermediate group requiring renumbering occupied ordinals, use the existing
`AMBIGUOUS_STRUCTURE` analysis failure. Do not silently choose a label or fall
through to Add Building when a building attachment was proven but ambiguous.
An existing registered Room remains selectable and updateable; the numbering
conflict must not block unrelated operations on it.

The registration-order guarantee covers room registration within a storey and
equivalent scans against the same captured building state and ground reference.
It does not promise contiguous numbers after inserting arbitrarily many new
levels between already saved floor numbers. That would require the renumbering
the user excluded.

## Bounds, lifecycle, and threading

Discovery is lazy and bounded to the selected ownership neighbourhood plus the
endpoint evidence of directly relevant transitions. Do not recursively resolve
every adjacent floor in a connected staircase chain. Resolve a landing identity
without expanding all flights leaving a different floor group.

Preserve the configured horizontal radius, cycle protection, and failure results.
Apply the floor block limit to the selected materialised floor and its connectors,
not the sum of all neighbouring endpoint regions. Bound local transition evidence
with the existing flight size and radius budgets; there is no unbounded building
graph traversal or new global discovery budget. Boundary evidence is not part of
the selected floor's geometry unless assigned to it.

World reads and registration mutations stay on their current owning thread.
No executors, parallel streams, new locks, or concurrent caches are needed.
Discovery state is discarded after the operation, and final results are immutable
or copied before publication. Existing server-side target recomputation and
validation remain authoritative.

## Ownership and affected code

| Owner | Responsibility |
| --- | --- |
| `SelectedFloorScanner` | Discover bounded regions/transitions, resolve groups, allocate exact selected geometry. |
| `StructureFloor` or a single pure grouping helper | Shared deterministic band/group policy; no world mutation or migration. |
| `StructureScanner` | Consume selected geometry, preserve handoffs and candidate validation. |
| `RoomScanPlanner` | Project the authoritative selection into an action and prospective number. |
| `RoomWorkflow` / `VillageManager` | Revalidate and commit through existing mutation boundaries. |
| `Village` | Existing persisted identities and logical-building ownership; no new repair pass. |

Loader-specific code should need only regression fixtures. No client-only model
is introduced, and packet/NBT schemas remain unchanged.

## Verification and acceptance

Add focused GameTests in the existing floor scanner lane. First reproduce the
turning-landing numbering or equivalent-seed discrepancy with a minimal vanilla
fixture; an unrelated compile or fixture failure is not RED evidence.

Required scenarios:

- Two flights meeting at a small flat 90-degree landing, with an adjoining room
  two blocks below the upper room surface; both belong to the upper storey.
- The same construction rotated, and observations from both directions, each
  stair half, landing, and room interior yield identical owner geometry.
- Different room registration orders within that storey agree on its number;
  prospective action and committed result agree.
- Even, odd, long/wide, and full-block flights retain exclusive ownership halves.
- Uneven upper landings and landing-edge stairs retain canonical membership.
- Genuine separate stacked floors remain separate; bounded bands do not chain.
- Door partitions, slabs, furniture, wall pockets, and climbable exits retain
  current geometry and Room identity behaviour.
- Deep stair chains do not recursively merge or discover all storeys.
- Save/reload and explicit remove/re-add retain valid new registrations.
- Existing inconsistent saved numbers round-trip without a new repair; ambiguous
  fresh attachments fail without rewriting other registrations.

Run the same focused regression after the implementation, the floor scanner
GameTest class, and the relevant copied-house classes. Use focused common JUnit
tests for pure band grouping, numbering decisions, and saved-data preservation.
Compile common and both loader modules. Expand checks only for newly changed
seams or unresolved failures. Port verification is compilation only.

Review the final diff for obsolete scan branches, duplicate ownership policies,
schema changes, automatic repair, and unrelated staged work. Passing source tests
does not establish target-version gameplay or client visual correctness.

## Alternatives considered

1. Fix only attachment arithmetic. Smaller, but leaves seed-dependent ownership
   and the special second scan; insufficient for the approved simplification.
2. Operation-local region/group resolution using existing probes. Recommended:
   gives selection and transitions one resolved identity without new saved state.
3. Persist a whole-building topology graph or add user floor overrides. More
   lifecycle, migration, and UI responsibilities than this task requires.

## Review conclusion

This design preserves exact geometry, validated stair split behaviour, existing
saved identities, and loader boundaries. Its main implementation risks are stable
region recognition and accidental expansion through deep transition chains.
The targeted tests above are required before removing the old special rescan.
Implementation planning starts after the user reviews this written specification.

## Implementation ruling: geometry and label ownership

The existing `twoBlockStaircaseSeparatesBroadStoreys` regression exposed an
incorrect draft interpretation: grouping nearby heights must not combine the
endpoint footprints of a proven flight. The scanner therefore needs only
`SurfaceRegion` and transition ownership, while the pure `FloorGrouping` helper
owns semantic label bands. No separate scanner FloorGroup carrier is needed.
Turning-landing regressions compare equivalent seeds within each physical
region and separately assert shared numbering and disjoint geometry. This
preserves genuine short storeys and avoids a rule specific to upward stairs.
