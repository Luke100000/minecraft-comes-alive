# Selected Floor Surface Enclosure — Design

Date: 2026-09-19. Corrective design for the selected-Floor scanner on `dev/1.21.1`.

## Goal

Remove the pre-traversal 3D enclosed-volume flood fill that can reject a small valid Floor because surrounding air volume exceeds an unrelated budget, while preserving the enclosure, furniture, connector, stair, uneven-floor, and selected-Floor invariants established by the September Floor work.

The scanner should have one topology pipeline:

```text
interaction
  -> resolve a physically supported selected-Floor seed
  -> traverse physically reachable supported surface cells
  -> classify selected-Floor vertical boundaries
  -> prune regions that can reach exterior
  -> validate actual Floor cells + connectors against maxBuildingSize
  -> FloorGeometry
```

Production code must shrink. No production Java file may have more added lines than deleted lines for this change; `SelectedFloorScanner.java` should be materially net-negative.

## Historical finding

Commit `329e54327` (`refactor: derive floors from enclosed volume`) inserted a second topology stage before the existing selected-Floor traversal:

```text
3D EnclosedVolume flood fill
  -> supportedCellsInVolume
  -> withinVolume StepProvider
  -> selected-Floor traversal
  -> retainEnclosedRegions / reachesExterior
```

That commit also introduced `VOLUME_CELL_LIMIT_MULTIPLIER = 16` and returns `BLOCK_LIMIT` when transient 3D volume cells exceed `maxSize * 16`.

The actual selected-Floor traversal already has its own `maxSize` check based on Floor cells plus connectors, and the final result performs the same real-geometry safety check again after exterior pruning and connector collection.

Commit `67c7f4267` narrowed the 3D fill to the selected Floor band, but it did not remove the independent air-volume budget or the duplicate enclosure responsibility.

Before `329e54327`, enclosure was decided after surface traversal by `retainEnclosedRegions(...)` and `reachesExterior(...)`. That surface-oriented model is the architectural boundary to restore, without restoring the old generic membership expansion that `329e54327` correctly removed.

## Failure evidence

The current full NeoForge GameTest run has three required failures:

- `oneblockhigherdoorwaykeepsfacingowner` — `BLOCK_LIMIT`;
- `openroofbridgedoesnotmergetwohouses` — `BLOCK_LIMIT`;
- `highsourcestillfindsexteriorthroughunevendescent` — `BLOCK_LIMIT`.

All three also fail before the later semantic rename commit `bd1364f17`. They are therefore behavior failures in the current scanner architecture, not regressions introduced by the terminology cleanup.

The common symptom is significant: each fixture asks the scanner to reason about a small Floor or an exterior route, but the scan terminates in the transient 3D volume budget before the selected-Floor/exterior logic can decide the requested behavior.

## Decision

Delete the pre-traversal `EnclosedVolume` pipeline.

`SelectedFloorScanner.Observation.resolve(...)` will:

1. resolve the interaction to a supported `SurfaceCell`;
2. canonicalize it with `resolveSelectedFloorAnchor(...)` using the cached physical `StepProvider`;
3. pass that `SurfaceCell` directly to `resolveSelectedFloor(...)`.

`resolveSelectedFloor(...)` will run `traverseSelectedFloor(...)` with the existing physical `StepProvider`, then run the existing `retainEnclosedRegions(...)` / `reachesExterior(...)` proof, then validate actual selected-Floor geometry.

Delete:

- `VOLUME_CELL_LIMIT_MULTIPLIER`;
- `VOLUME_DIRECTIONS`;
- `Observation.volumesBySeed`;
- `Observation.enclosedVolume(...)`;
- `discoverEnclosedVolume(...)`;
- `supportedCellsInVolume(...)`;
- `withinVolume(...)`;
- `isInteriorVolumeCell(...)`;
- the `EnclosedVolume` record.

Do not replace any of them with another volume cache, another flood fill, or another topology abstraction.

## Why the remaining pipeline is sufficient

### Physical Floor-cell production

`worldSteps(...)` already derives candidates from Minecraft collision/support behavior:

- `supportedSurfaceY(...)` requires traversal occupancy and headroom;
- `supportedFloorLevel(...)` uses `WalkNodeEvaluator.getFloorLevel(...)`;
- `FloorCeilingResolver.ceilingY(...)` requires roof/ceiling evidence for an ordinary traversed Floor cell;
- `canStep(...)` bounds physical movement between surfaces.

Therefore ordinary `FloorGeometry.Cell` membership continues to come from supported physical positions, not from neighbouring-air membership rules.

### Exterior proof

`retainEnclosedRegions(...)` already separates selected Floor regions around Room boundaries and calls `reachesExterior(...)`.

`reachesExterior(...)` explores physical passable surface routes and treats either of these as exterior evidence:

- reaching the scan-radius boundary;
- reaching a physically passable position with no ceiling.

This is the correct place to decide whether roofed candidates are truly indoor. In particular, a roofed but open-sided canopy can be traversed as a candidate and then pruned because it reaches unroofed exterior.

### Door boundaries

Door/gate cells remain canonical Floor cells where physically supported. `RoomPartitioner` and `StructureConnector` remain responsible for deterministic door ownership.

The fix must not special-case the one-block-higher doorway fixture.

### Floor-band boundaries

`FloorBandClassifier` remains operation-local and unchanged in behavior. `OWNED`, `EDGE`, and `OTHER` still decide which physically reachable supported cells belong to the selected Floor, are terminal vertical boundary cells, or belong beyond it.

This change does not restore recursive storey discovery.

## Required preserved semantics from the enclosed-volume redesign

Removing the volume flood fill is not a rollback of the valid invariants introduced around `329e54327`.

The following remain required:

- a full-height partial obstacle such as a hopper is not manufactured into ordinary Floor geometry;
- a high ceiling does not create extra Floor layers;
- high ceiling air volume does not consume the Floor block limit;
- beds and carpets do not redefine exact Floor membership;
- unsupported ladder/trapdoor exits may resolve an interaction but do not become ordinary `FloorGeometry.Cell` entries;
- open upper Floors do not invalidate enclosed lower Floors;
- roofed exterior canopies are excluded from indoor Floor geometry;
- uneven indoor Floors remain source-independent within the supported vertical band;
- normal stairs separate stable Floors while retaining transition evidence;
- no generic `includeInteriorMembership(...)` or equivalent neighbour-expansion pass returns.

## Size and radius semantics

`maxSize` measures selected Floor topology, not transient air.

Keep these actual topology checks:

- during `traverseSelectedFloor(...)`: selected cells plus observed connectors must not exceed `maxSize`;
- after exterior pruning and vertical connector collection: final selected Floor cells plus connectors must not exceed `maxSize`.

Keep the existing horizontal `maxRadius` guard in selected-Floor traversal and `reachesExterior(...)`.

Do not add another independent size multiplier.

## Coding and review constraints

Apply the requested coding standards:

- KISS: one selected-Floor topology pipeline;
- DRY: one physical `StepProvider`, one exterior proof, one Floor-size meaning;
- YAGNI: no replacement volume object or speculative enclosure abstraction;
- descriptive names, immutable result collections, and guard clauses;
- comments only where they explain a non-obvious invariant.

Apply `java-code-review-cleanup` to the final fixed Java diff through all four lenses:

### Reuse

Reuse `StepProvider`, `worldSteps(...)`, `FloorCeilingResolver`, `FloorBandClassifier`, `retainEnclosedRegions(...)`, and `reachesExterior(...)`. Do not create new equivalents.

### Quality

Remove the redundant volume cache/model and make the control flow readable directly from `Observation.resolve(...)`.

### Correctness

Do not let air-volume size determine `BLOCK_LIMIT`. Preserve actual Floor/connectors limits, exterior pruning, connector handoff, and all selected-Floor boundary rules.

### Efficiency

Removing the 3D fill eliminates potentially thousands of transient air-cell visits and ceiling lookups before the real supported-surface traversal.

## Files in scope

Production:

- `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`

Tests:

- Prefer no test edits: the three existing failing GameTests are already the RED regressions.
- Modify `FloorScannerGameTests.java` only if investigation proves an uncovered invariant needs an additional focused assertion; do not weaken existing tests.

Documentation:

- this spec;
- its implementation plan.

No persistence, Room workflow, save format, or other production files are in scope unless a failing supported regression proves this assumption wrong.

## Acceptance

The fix is complete when:

1. the three current `BLOCK_LIMIT` regressions reach their intended assertions rather than failing in transient volume discovery;
2. the enclosed-volume-era protection tests remain green;
3. common tests and Fabric/NeoForge compilation pass;
4. the full NeoForge GameTest server runs all registered tests and no new required failure is introduced;
5. any remaining failure is compared by exact test name against the pre-change baseline rather than hidden;
6. the production diff is net-negative and contains no replacement enclosure abstraction;
7. a final four-lens Java review finds no worthwhile in-scope cleanup left.

## Non-goals

- No restoration of `includeInteriorMembership(...)`.
- No generic 3D Room-volume persistence.
- No save-format change.
- No recursive Floor/storey discovery.
- No FloorBand redesign.
- No stair-classifier redesign.
- No copied-house, coordinate, block-class, or fixed-Y workaround.
- No increase to the old volume multiplier as a workaround.
