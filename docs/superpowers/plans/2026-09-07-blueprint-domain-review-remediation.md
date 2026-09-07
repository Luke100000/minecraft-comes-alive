# Blueprint and Domain Review Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix every actionable defect from the latest Blueprint/client and server/domain reviews while preserving the converged architecture and the user's preference for the smallest no-thread solution.

**Architecture:** Keep terrain lifecycle state inside `BlueprintTerrainRenderer`, make sampling and texture invalidation slice-granular, and reuse one texture per tile. Keep UI fixes local to renderer/screen/tooltip ownership. Harden the existing `Village`/`RoomDFU` aggregate boundaries rather than introducing new layers.

**Tech Stack:** Java 21, Minecraft 1.21.1 Mojang mappings, Architectury common client/server code, JUnit 5, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-07-blueprint-domain-review-remediation-design.md`

## Global Constraints

- Work only in `C:\Users\Mik\Downloads\MCA\minecraft-comes-alive-1.21.1-floor-clean-squash` on `feature/1.21.1-floor-clean-squash`.
- Preserve unrelated dirty work; do not reset or discard user changes.
- No terrain worker threads or new scheduler subsystem.
- Never force-load client chunks.
- Preserve the current water/substrate appearance while correcting dry-height source semantics.
- Follow RED -> GREEN TDD for every production behavior change.
- Commit logical slices separately; do not combine Blueprint, server/domain, Dialogue, AI-walk, SpawnQueue, and documentation work into one commit.

---

### Task 1: Terrain slice lifecycle and persistent texture

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintTerrainRenderer.java`
- Modify: `common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java`

**Interfaces:**
- `nearestReadySlice(...)` chooses only pending/stale slices whose own 16x16 chunk is loaded, camera-nearest first.
- Each tile owns per-slice sampled/complete/retry/stale state and primitive terrain arrays.
- Each tile owns at most one registered `DynamicTexture`; dirty sampled slices update an expanded dirty region of its existing `NativeImage` and upload that region.
- `sampleClientDryTerrainHeight(...)` derives the no-leaves dry surface from `MOTION_BLOCKING` upper-bound data and loaded block states.

- [ ] Add RED tests proving an unloaded nearest slice is skipped, an unavailable slice remains pending, retry does not reset successful slices, completed slices become stale, dirty texture bounds are slice-bounded, and dry height skips leaves.
- [ ] Run `:common:test --tests net.conczin.mca.client.gui.BlueprintScreenMapInteractionTest` and confirm the new tests fail for the intended missing behavior.
- [ ] Implement slice-granular readiness/retry/staleness, primitive cell storage, and the `MOTION_BLOCKING` + leaf-correction helper.
- [ ] Rework texture ownership to allocate/register once per tile, update dirty pixels in place, and upload only the dirty region expanded by one pixel for neighbor shading/contours.
- [ ] Remove the whole-tile reset/retry path and the six-argument production constructor used only by reflective tests.
- [ ] Rerun the focused test class and `:common:compileJava`; require GREEN.
- [ ] Commit as `perf: harden blueprint terrain streaming`.

### Task 2: Blueprint hover, tooltip, viewport, and view-state regressions

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintMapRenderer.java`
- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintTooltipFactory.java`
- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintScreen.java`
- Modify: `common/src/test/java/net/conczin/mca/client/gui/BlueprintScreenMapInteractionTest.java`
- Modify: `common/src/test/java/net/conczin/mca/client/gui/BlueprintTooltipHierarchyTest.java`

**Interfaces:**
- Grouped icon hover uses the icon center and the legacy six-block map radius.
- Tooltip generation receives/uses the `logicalBuildingId` already resolved by map geometry so aggregate membership includes all registered visible Rooms.
- `currentViewport()` is the one operational viewport constructor for render, containment, and scroll.
- Selected floor and player-centering are instance lifecycle state; persistent scale/head preferences remain explicit static preferences.
- Automatic Fit center tracks same-village bounds updates only while the center remains automatic.

- [ ] Add RED tests for grouped-icon hover, incomplete registered Room membership in aggregate tooltips, same-village Fit recentering, and instance-local selected-floor/player-centered state where testable through behavior.
- [ ] Run the focused Blueprint test classes and confirm RED.
- [ ] Implement the minimal renderer/factory/screen changes above.
- [ ] Rerun `BlueprintScreenMapInteractionTest`, `BlueprintTooltipHierarchyTest`, and `BlueprintMapGeometryTest`; require GREEN.
- [ ] Commit as `fix: restore blueprint interaction semantics`.

### Task 3: Canonical Room/Structure and RoomDFU invariants

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Village.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomDFU.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureFloor.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/FloorGeometry.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/VillageFloorSystemTest.java`
- Modify: `common/src/test/java/net/conczin/mca/server/world/data/RoomDFUTest.java`

**Interfaces:**
- Canonical functional Room ownership requires a non-empty exact cell set wholly contained by its referenced Floor.
- Current-format load rejects duplicate IDs before insertion and rejects malformed floor-cell fields/invariants explicitly.
- `registerStructure()` validates all linked IDs/Floor ownership and conflicts before mutating any aggregate map.

- [ ] Add RED tests for empty Room registration/load validation, duplicate Room/external/Structure/LogicalBuilding IDs, missing `surfaceY`/`ceilingY`, non-finite/physically invalid cells, and invalid `registerStructure()` input leaving state unchanged.
- [ ] Run `VillageFloorSystemTest` and `RoomDFUTest`; confirm RED.
- [ ] Implement boundary validation in the four production files without adding a second validation subsystem.
- [ ] Rerun the focused server tests; require GREEN.
- [ ] Commit as `fix: enforce canonical building invariants`.

### Task 4: Aggregate encapsulation, exception atomicity, and migration contract

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Building.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Village.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomDFU.java`
- Modify: existing tests that currently mutate `getBlocks()` directly.

**Interfaces:**
- `getBlocks()` returns a deep unmodifiable view/copy; writes go through existing Building mutation methods.
- `publishFloorRefresh()` restores the pre-publication building-domain snapshot before rethrowing an unexpected post-validation exception.
- Origin migration is explicitly documented and regression-tested as rectangular approximation pending rescan.

- [ ] Add RED tests proving callers cannot mutate internal POI state through `getBlocks()` and that a forced post-publication failure restores prior aggregate state using a package-private test seam only if a real production boundary cannot otherwise be exercised.
- [ ] Run the focused tests and confirm RED.
- [ ] Implement deep read-only POI exposure and exception rollback using the existing aggregate snapshot/restore mechanism.
- [ ] Add/rename migration characterization coverage so rectangular origin migration is explicitly treated as approximation.
- [ ] Rerun server/domain tests; require GREEN.
- [ ] Commit as `fix: protect village aggregate boundaries`.

### Task 5: Preserve and commit pre-existing unrelated work

**Files:**
- Existing dirty Dialogue test/source pair.
- Existing dirty `ExtendedWalkTowardsTask.java`.
- Existing dirty `SpawnQueue.java`.
- Existing Sept-4/Sept-6 terrain design/plan documents.

- [ ] Review each dirty group independently and run its focused tests/compile where available.
- [ ] Mark the superseded Sept-6 worker-thread terrain plan/spec clearly as superseded by this plan/spec; preserve the historical document contents.
- [ ] Commit each unrelated code group independently with an accurate message; commit documentation separately.

### Task 6: Full verification and final review

**Files:** no planned production changes except fixes required by verification/review.

- [ ] Run `.\gradlew.bat :common:test :fabric:compileJava :neoforge:compileJava` from the verified worktree.
- [ ] Run `git diff --check` and inspect `git status --short`/recent commits.
- [ ] Run a whole-change code review against the task spec and resolve any Critical/Important findings.
- [ ] Do not claim the remaining terrain load-in/hitch is visually eliminated unless an in-game check is actually performed.
