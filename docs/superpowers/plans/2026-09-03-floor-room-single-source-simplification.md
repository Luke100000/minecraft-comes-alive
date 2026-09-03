# Floor / Room Single-Source Simplification Plan

**Spec:** `docs/superpowers/specs/2026-09-03-floor-room-single-source-simplification.md`

## Completed implementation slices

- [x] Add RED regressions for Main Room as the only Ground Floor anchor, missing Structure lookup,
  and deletion of logical buildings with zero Rooms.
- [x] Remove persisted `groundStructureId/groundFloorId`; derive Ground Floor from Main Room.
- [x] Make missing Structure logical-building lookup return not-found instead of guessing.
- [x] Delete empty logical buildings and their Structures during reconciliation.
- [x] Rename `RoomScanPlan.building` to `currentRoom`, remove `functionalRoom()`, and stop putting
  Main Room into Add Room plans.
- [x] Add RED/green Y-first Floor resolution regression and remove nearest-column snapping.
- [x] Remove `InteractionKind` and landing-handoff priority selection; resolve Y -> Floor, then
  X/Z -> Room, with vertical connector projection only where needed to map an occupied connector.
- [x] Keep connectors as deterministic Room footprint cells after partitioning and lock exterior /
  shared-door ownership behavior.
- [x] Add RED/green POI-under-raised-surface and shared-wall ownership regressions.
- [x] Make POI evidence use the Floor vertical band and one deterministic perimeter owner.
- [x] Add RED/green connector-based Floor attachment regression and remove the four-block gap rule.
- [x] Preserve global Blueprint floor viewing and building-local Remove Floor behavior.
- [x] Run full `:common:test` and `:neoforge:compileJava` verification.

## Final verification

- [x] Run `git diff --check`.
- [x] Inspect `git diff` and `git status --short`, preserving unrelated navigation changes.
- [x] Re-run full `:common:test` and `:neoforge:compileJava` after any final cleanup.

Do not reintroduce `InteractionKind`, duplicated Ground Floor IDs, `MAX_FLOOR_ATTACHMENT_GAP`, or
an overloaded RoomScanPlan Room field.
