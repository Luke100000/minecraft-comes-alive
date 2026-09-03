# Floor/Room Model Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove ambiguous lookup semantics and obsolete inferred connector relationships so Floor cells remain the canonical geometry source and compatibility is limited to the two approved 1.21.1 sources.

**Architecture:** Keep the existing FloorSurface → StructureFloor → Room partitioning model. Persist physical Floor/Room geometry and connector metadata independently; derive interaction semantics, floor numbers, and live vertical attachments from dedicated code paths rather than overloaded methods or marker coincidence. Migration accepts only released `origin/1.21.1` and `origin/feature/1.21.1-floor-clean-squash` unversioned saves.

**Tech Stack:** Java 21, Minecraft 1.21.1, Gradle, JUnit 5.

**Spec:** Current conversation decisions and the existing floor-clean model in this branch.

## Global Constraints

- Preserve all unrelated dirty worktree changes.
- Migration source 1 is exactly `origin/1.21.1` (`575691bd6e09d4be2f828340683247dc2a2c4fdb`).
- Migration source 2 is exactly `origin/feature/1.21.1-floor-clean-squash` (`80bfe7d0edc06d6e6cb7363321aace316752ad65`).
- Do not add compatibility for other local, fork, 26.x, security, or intermediate save formats.
- Floor cells are geometry truth; connector markers are metadata and must not manufacture current geometry.
- Vertical attachment is proven from live connector topology, never inferred from matching persisted marker columns.
- Use TDD for behavioral/API changes and keep implementation minimal.

---

### Task 1: Make room lookup semantics explicit

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Village.java`
- Modify callers under `common/src/main/java/net/conczin/mca/server/world/data/`
- Test: `common/src/test/java/net/conczin/mca/server/world/data/VillageFloorSystemTest.java`

**Interfaces:**
- Produce `findPhysicalRoomAt(Vec3i)` for exact persisted physical lookup.
- Produce `findInteractionRoomAt(BlockPos)` for interaction/support-band lookup.

- [x] **Step 1:** Change tests to call the explicit APIs and add a test proving physical lookup does not use the interaction support band.
- [x] **Step 2:** Run focused Village tests and verify RED because the new API names do not exist.
- [x] **Step 3:** Rename implementations and update callers according to intent; remove overloaded `getFunctionalRoomAt` methods.
- [x] **Step 4:** Run focused tests and verify GREEN.

### Task 2: Stop Blueprint from inferring persisted vertical destinations

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintMapGeometry.java`
- Modify: `common/src/main/java/net/conczin/mca/client/gui/BlueprintMapRenderer.java`
- Test: `common/src/test/java/net/conczin/mca/client/gui/BlueprintMapGeometryTest.java`
- Test: `common/src/test/java/net/conczin/mca/client/gui/BlueprintMapRendererTest.java`

**Interfaces:**
- `MapConnectorLayer` contains only logical building id and connector marker.
- Ladder and trapdoor render as `↕`; door and gate keep their existing symbols.

- [x] **Step 1:** Update tests to require directionless vertical connector layers and `↕` for both ladder and trapdoor.
- [x] **Step 2:** Run focused Blueprint tests and verify RED.
- [x] **Step 3:** Delete `verticalDirection(...)`, `VerticalDirection`, and same-column cross-floor inference; simplify renderer glyph selection.
- [x] **Step 4:** Run focused tests and verify GREEN.

### Task 3: Keep proven vertical connections minimal

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureConnector.java`
- Test: `common/src/test/java/net/conczin/mca/server/world/data/VillageFloorSystemTest.java`

**Interfaces:**
- `VerticalConnection(Structure structure, StructureFloor floor)` contains only the proven attachment target.

- [x] **Step 1:** Change tests to construct a two-field `VerticalConnection`.
- [x] **Step 2:** Run the focused attachment tests and verify RED.
- [x] **Step 3:** Remove connector position from the record and deduplicate connections by Structure/Floor after physical proof.
- [x] **Step 4:** Run focused attachment/connector tests and verify GREEN.

### Task 4: Restrict migration to the two approved branches and remove runtime geometry repair

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Village.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/RoomDFU.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/StructureFloor.java`
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Building.java`
- Test: `common/src/test/java/net/conczin/mca/server/world/data/RoomDFUTest.java`
- Test: `common/src/test/java/net/conczin/mca/server/world/data/StructureFloorResolutionTest.java`

**Interfaces:**
- Current save version is `2`.
- Versioned current saves load directly and must match version `2`.
- Unversioned saves migrate only as either released origin format (no `structures`) or upstream floor-clean-squash format (`structures` present, no current version tag).
- Current `StructureFloor` constructor/load never adds connector columns to `region` and never restores derived `floorNumber` from NBT.

- [x] **Step 1:** Add/adjust migration tests for the exact origin/upstream shapes and a test that version `1` is rejected rather than silently migrated.
- [x] **Step 2:** Replace legacy-load tests that expected runtime connector ownership repair with tests asserting connector metadata cannot mutate current Floor/Room geometry.
- [x] **Step 3:** Run focused migration/StructureFloor tests and verify RED.
- [x] **Step 4:** Bump current data version, keep unversioned `RoomDFU` as the two-source boundary, and delete runtime connector ownership repair plus `Building.includeFloorColumn`.
- [x] **Step 5:** Remove connector→region mutation and persisted `floorNumber` compatibility from `StructureFloor`.
- [x] **Step 6:** Run focused migration/StructureFloor tests and verify GREEN.

### Task 5: Split registered-room update orchestration without adding layers

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java`
- Existing tests: registered-room reconciliation/update test suite.

**Interfaces:**
- Keep all behavior and public APIs unchanged.
- Extract private helpers for assignment validation/identity preparation, type resolution, and applying the already-validated mutation.

- [x] **Step 1:** Run the focused registered-room update tests to establish the GREEN baseline.
- [x] **Step 2:** Extract cohesive private helpers with no behavior change and no new service/repository abstractions.
- [x] **Step 3:** Re-run the same tests and verify GREEN.

### Task 6: Final verification

**Files:** None beyond prior tasks.

- [x] **Step 1:** Search for removed ambiguous/inference symbols (`getFunctionalRoomAt`, Blueprint `VerticalDirection`, runtime connector room repair, connector-bearing `VerticalConnection`).
- [x] **Step 2:** Run `gradlew.bat :common:test` fresh and require exit code 0.
- [x] **Step 3:** Run `git diff --check` and inspect `git status --short` without reverting unrelated dirty work.
