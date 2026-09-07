# Blueprint and Domain Review Remediation Design

Date: 2026-09-07

Status: approved for implementation by the user's "commit all and fix all issues" instruction after the consolidated review findings.

Target: `feature/1.21.1-floor-clean-squash`

## Goal

Close the concrete defects found by the Aquinas, Gibbs, Hubble, and Sartre reviews without redesigning the converged floor/room architecture or adding a terrain worker subsystem.

## Architecture constraints

- Keep canonical floor ownership as `LogicalBuilding -> Structure -> StructureFloor -> FloorGeometry`, with Rooms owning exact subsets of Floor cells.
- Keep `RoomWorkflow` as orchestration, `Village` as aggregate/invariant owner, `VillageManager` as ID/finalization coordinator, and `RoomDFU` as the compatibility boundary.
- Keep the existing Blueprint split: `BlueprintMapViewport`, `BlueprintMapGeometry`, `BlueprintMapRenderer`, `BlueprintTerrainRenderer`, and `BlueprintTooltipFactory`.
- Keep terrain rendering on the client/render thread. Do not add workers, queues, scheduler classes, a second geometry representation, repositories, command buses, or generic transaction frameworks.
- Terrain remains 128x128 tiles divided into 16x16 slices, camera-nearest first, visible before one-ring prefetch, bounded to the existing cache, and never force-loads chunks.
- A terrain slice is eligible only when that slice's own client chunk is loaded. Missing slices remain pending and are retried individually; successful slices are never reset merely because another slice was unavailable.
- Reuse one `DynamicTexture`/`NativeImage` per cached tile and update dirty texture regions instead of allocating/registering a replacement texture at every progressive checkpoint.
- Completed tiles must eventually become stale and resample slice-by-slice so terrain can update while Blueprint remains open.
- For dry terrain, use client `MOTION_BLOCKING` as the packet-authoritative upper bound and explicitly correct leaves by scanning loaded block states. Preserve the current water/substrate path and current water appearance.

## Client fixes

1. Make loadedness and retry state slice-granular. An unavailable chunk cannot mark its slice successfully sampled.
2. Remove whole-tile retry resets. Retry only missing or stale slices.
3. Replace repeated full `NativeImage`/`DynamicTexture` allocation with a persistent tile texture and bounded dirty-region upload.
4. Add deliberate completed-tile staleness so local world changes can refresh.
5. Replace dry `MOTION_BLOCKING_NO_LEAVES` heightmap use with `MOTION_BLOCKING` plus explicit leaf correction from already-loaded client data.
6. Restore grouped-icon hover/tooltip targeting with the legacy six-block radius semantics.
7. Make aggregate tooltip membership follow the logical-building identity carried by map geometry/hover targets, including registered incomplete Rooms that are intentionally visible.
8. Centralize Blueprint viewport construction in `currentViewport()`.
9. Keep view preferences intentional: scale/head-visibility may persist, but selected floor and player-centering are screen-instance state.
10. Preserve automatic Fit centering across same-village bounds changes without overriding a center the user manually panned.
11. Remove production-only test seams left by the incremental terrain refactor and avoid the per-column `Cell` object graph where a primitive representation is straightforward.

## Server/domain fixes

1. Reject functional Rooms with empty exact floor-cell ownership at every canonical registration/load validation boundary.
2. Reject duplicate canonical IDs during `RoomDFU.loadCurrent()` rather than allowing `Map.put()` to collapse entries. Apply the same protection to Rooms, external buildings, Structures, and LogicalBuildings.
3. Require canonical floor-cell `surfaceY` and `ceilingY` fields and validate physical cell invariants: finite surface, surface at/above the feet block and below the ceiling, and `ceilingY > feetY`.
4. Harden `Village.registerStructure()` to enforce the same Room/Structure/Floor ownership invariants before mutation and reject conflicting IDs.
5. Make `Building.getBlocks()` unable to mutate internal persisted POI state through the returned map/lists; use existing mutation APIs for writes.
6. Make `publishFloorRefresh()` exception-atomic by restoring the aggregate snapshot if a post-validation publication/reconciliation step throws.
7. Treat origin bounding-box migration as explicitly approximate geometry until a real rescan; document this in the compatibility boundary and regression-test the behavior rather than claiming the migrated rectangle is exact observation.

## Out of scope

- Rewriting `Village` or `BlueprintScreen` merely because they are large.
- Replacing `ExternalBuilding extends Building` compatibility inheritance.
- Network delta protocols or registry/snapshot splitting.
- Git-history reconstruction of the 183-commit branch.
- Visual claims that require an in-game run. Automated verification can prove compilation/tests and lifecycle contracts, not that every hitch is visually gone.

## Verification

- Every behavior change gets a RED test before production changes and a GREEN rerun afterward.
- Focused Blueprint and server/domain tests run after their respective tasks.
- Final automated gate: `:common:test :fabric:compileJava :neoforge:compileJava` plus `git diff --check`.
- The final report must state separately whether an in-game visual check was performed; do not infer visual completion from Gradle.
