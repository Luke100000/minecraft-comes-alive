# Floor Ownership Consistency Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan inline. The user explicitly does not want subagents or new worktrees for this cleanup. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve the now-working floor/storey behavior while removing competing sources of truth so fresh storey discovery, room partitioning, action planning and persisted identity each have one owner.

**Architecture:** `SelectedFloorScanner` owns fresh physical storey membership and direct storey links. `FloorGeometry` carries exact 3D membership into `RoomPartitioner`; `RoomScanPlanner` chooses actions from that canonical geometry; `StructureFloor`/`Village` only match and persist identity. The cleanup removes cross-layer policy leaks and dead compatibility logic without adding another topology framework.

**Tech Stack:** Java 21, Minecraft 1.21.1, NeoForge 21.1.250, Fabric, Gradle, JUnit and Minecraft GameTest.

**Spec:** `docs/superpowers/specs/2026-09-16-floor-ownership-consistency-design.md`.

## Global Constraints

- Work in `C:/Users/Mik/Downloads/MCA/minecraft-comes-alive-1.21.1-floor-clean-squash` on `dev/1.21.1`.
- Preserve all staged and unstaged peer work. Do not reset, stash, clean or broadly rewrite files.
- Do not create another worktree or subagent task.
- Do not commit unless the user explicitly asks after verification.
- Add no dependencies, NBT fields, packet fields or data-version migration.
- Keep `FloorGeometry` as the exact integer-cell membership representation.
- Reuse the existing copied-house NBT unchanged.
- Keep selected-storey discovery separate from connected-storey discovery.
- A behavior change requires a focused failing regression first.
- Run one Gradle/GameTest process at a time in the shared checkout.

## File map

| File | Responsibility in this cleanup |
| --- | --- |
| `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java` | Fresh storey membership, transition topology, semantic connector handoff. |
| `common/src/main/java/net/conczin/mca/server/world/data/StructureFloor.java` | Persisted storey matching and floor-number tolerance. |
| `common/src/main/java/net/conczin/mca/server/world/data/RoomScanPlanner.java` | Action selection from canonical fresh geometry plus persisted identity. |
| `common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java` | Candidate validation using canonical geometry and explicit scanner links. |
| `common/src/main/java/net/conczin/mca/server/world/data/Village.java` | Persisted building attachment and exact registered interaction lookup. |
| `common/src/test/java/net/conczin/mca/server/world/data/RoomScanPlannerTest.java` | Planner ownership regressions. |
| `common/src/test/java/net/conczin/mca/server/world/data/StructureFloorTest.java` | Persisted matching/ordinal semantics. |
| `common/src/test/java/net/conczin/mca/server/world/data/VillageFloorSystemTest.java` | Persisted floor/building identity behavior. |
| `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java` | Physical storey/landing/ladder regressions. |
| `neoforge/src/main/java/net/conczin/mca/server/world/data/CopiedOpenHouseGameTests.java` | Actual house and multi-storey attachment regressions. |

---

### Task 1: Freeze the working ownership behavior before cleanup

**Files:**
- Read: the production files in the file map.
- Test: `RoomScanPlannerTest.java`, `StructureFloorTest.java`, `VillageFloorSystemTest.java`, `FloorScannerGameTests.java`, `CopiedOpenHouseGameTests.java`.

**Interfaces:**
- Consumes the current dirty implementation exactly as it stands.
- Produces a named baseline for the behavior-preserving cleanup.

- [ ] **Step 1: Record the dirty baseline without modifying it**

Run:

```powershell
git status --short
git rev-parse HEAD
git diff --check
```

Keep the status output with the task notes. Do not stage anything.

- [ ] **Step 2: Run focused deterministic common tests**

Run:

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.server.world.data.RoomScanPlannerTest --tests net.conczin.mca.server.world.data.StructureFloorTest --tests net.conczin.mca.server.world.data.VillageFloorSystemTest --no-daemon
```

Expected: all selected tests pass. A failure is a baseline defect and must be diagnosed before cleanup.

- [ ] **Step 3: Confirm the ownership regressions exist**

Search for these exact test methods and preserve them:

```powershell
rg -n "interactionBelowAnchorStaysOnCanonicalUpperStorey|ladderTopExitStaysOnUpperStorey|successiveLowerStairRoomsAttachOneLevelAtATime|eachLowerStaircaseRegistersThePlannedRoomUnderTheHouse|unownedStairCellDoesNotBorrowRegisteredRoomIdentity" common/src/test neoforge/src/main
```

Do not add duplicate tests when an existing test already expresses the requirement.

- [ ] **Step 4: Use focused GameTests during later edits**

When a dev client/server is already running, use Minecraft's built-in selector rather than restarting all tests:

```text
/test run laddertopexitstaysonupperstorey
/test run successivelowerstairroomsattachonelevelatatime
/test run eachlowerstaircaseregisterstheplannedroomunderthehouse
```

The full GameTest server is deferred to Task 6.

---

### Task 2: Decouple fresh scanner ownership from persisted matching tolerance

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java`.
- Test: `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java` only if a missing regression is discovered.

**Interfaces:**
- Keeps `StructureFloor.BAND_TOLERANCE` unchanged for persistence/matching.
- Produces scanner-local vertical ownership policy with the same initial numeric behavior.

- [ ] **Step 1: Prove the coupling is limited to the scanner references**

Run:

```powershell
rg -n "StructureFloor\.BAND_TOLERANCE" common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java common/src/main/java/net/conczin/mca/server/world/data/StructureFloor.java common/src/main/java/net/conczin/mca/server/world/data/RoomScanPlanner.java
```

Expected scanner references are `StoreyContext.minOwnedY/maxOwnedY` and the full-storey descent boundary.

- [ ] **Step 2: Introduce one scanner-owned constant with unchanged value**

Add near the other `SelectedFloorScanner` policy constants:

```java
private static final int STOREY_HEIGHT_RADIUS = 2;
```

Change `StoreyContext` to:

```java
private record StoreyContext(int anchorY) {
    int minOwnedY() {
        return anchorY - STOREY_HEIGHT_RADIUS;
    }

    int maxOwnedY() {
        return anchorY + STOREY_HEIGHT_RADIUS;
    }
}
```

Change the boundary in `descendsFullStoreyFromStair` from:

```java
int boundaryY = start.feet().getY() - StructureFloor.BAND_TOLERANCE;
```

to:

```java
int boundaryY = start.feet().getY() - STOREY_HEIGHT_RADIUS;
```

- [ ] **Step 3: Verify the ownership boundary**

Run:

```powershell
rg -n "StructureFloor\.BAND_TOLERANCE" common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
```

Expected: no matches.

Do not change `StructureFloor.BAND_TOLERANCE` or its tests in this task.

- [ ] **Step 4: Run focused tests**

Run:

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.server.world.data.StructureFloorTest --tests net.conczin.mca.server.world.data.RoomScanPlannerTest :neoforge:compileJava --no-daemon
```

Then, in the running GameTest environment, run the flat-landing, full-stair, narrow-stair and copied-house lower-chain tests affected by the scanner. If any result changes, stop and diagnose; this task is intended to be behavior-neutral.

---

### Task 3: Audit planner and attachment code for re-discovery after canonical geometry exists

**Files:**
- Modify only if justified: `RoomScanPlanner.java`, `StructureScanner.java`, `Village.java`, `StructureFloor.java`.
- Test: `RoomScanPlannerTest.java`, `VillageFloorSystemTest.java`.

**Interfaces:**
- Consumes `StructureScanner.FloorObservation` and its canonical `scan().floor()` / directly connected floors.
- Preserves `StructureFloor.overlapsSemanticStorey(FloorGeometry)` as the persisted matching primitive.

- [ ] **Step 1: Locate every remaining storey-choice heuristic outside the scanner**

Run:

```powershell
rg -n "source\.getY\(\)|nearestFloor|sameSemanticBand|overlapsSemanticStorey|BAND_TOLERANCE|directlyConnectedFloors|selectAttachmentTarget" common/src/main/java/net/conczin/mca/server/world/data/RoomScanPlanner.java common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java common/src/main/java/net/conczin/mca/server/world/data/Village.java common/src/main/java/net/conczin/mca/server/world/data/StructureFloor.java
```

Classify each hit as either persisted matching/action selection or fresh membership. Only the latter is a violation.

- [ ] **Step 2: Preserve the canonical planner direction regression**

`connectedTransitionAttachmentPlan` must continue to derive direction from canonical floor anchors:

```java
int direction = Integer.compare(
        observation.scan().floor().anchorY(), referenceFloor.anchorY());
```

Do not replace this with `source.getY()` or player Y.

Run:

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.server.world.data.RoomScanPlannerTest.interactionBelowAnchorStaysOnCanonicalUpperStorey --no-daemon
```

Expected: pass.

- [ ] **Step 3: Reuse the persisted matching primitive instead of copying its formula**

Where code needs to answer “does this canonical fresh/connected floor correspond to this saved floor?”, use:

```java
savedFloor.overlapsSemanticStorey(freshGeometry)
```

Do not duplicate `sameSemanticBand(anchorY...) && footprintIntersectionArea(...) > 0` in another class.

If the audit finds no duplicated formula, make no edit for this step.

- [ ] **Step 4: Keep building attachment separate from fresh membership**

`Village.selectAttachmentTarget` may use explicit scanner connections, exact vertical evidence and persisted matching to choose a logical building. Confirm it never constructs replacement `FloorGeometry` or mutates the candidate's cells.

`StructureScanner.validateCandidate` may reject overlaps and consume `selected.directlyConnectedFloors(...)`; it must not perform another world traversal to decide which storey the candidate is.

If both conditions already hold, make no edit.

- [ ] **Step 5: Delete only compatibility/dead paths proved unused**

Run:

```powershell
rg -n "usesInteriorMembershipSeed|nearestFloorAtColumn|floorAtHeight" common/src/main common/src/test neoforge/src/main
```

`usesInteriorMembershipSeed` should remain absent. Remove another helper only if it has no caller and is not serialization/API compatibility. Do not replace dead code with a new abstraction.

- [ ] **Step 6: Run focused planner/persistence tests**

Run:

```powershell
.\gradlew.bat :common:test --tests net.conczin.mca.server.world.data.RoomScanPlannerTest --tests net.conczin.mca.server.world.data.VillageFloorSystemTest --tests net.conczin.mca.server.world.data.StructureFloorTest --no-daemon
```

---

### Task 4: Clarify semantic connector membership without adding another geometry model

**Files:**
- Modify: `SelectedFloorScanner.java` only if the rename reduces ambiguity.
- Test: `FloorScannerGameTests.java`.

**Interfaces:**
- Keeps `inspectSurfaceCell` / `supportedFloorLevel` strictly physical.
- Keeps connector-exit interaction membership local to the scanner.

- [ ] **Step 1: Lock the physical-versus-semantic distinction**

In `ladderTopExitStaysOnUpperStorey`, retain or add the assertion that the ladder top-exit cell belongs to the upper returned `FloorGeometry` while:

```java
SelectedFloorScanner.inspectSurfaceCell(
        helper.getLevel(), topExit, new FloorCeilingResolver(helper.getLevel())).isEmpty()
```

must remain true.

This proves membership without fabricated collision support.

- [ ] **Step 2: Rename misleading private helpers instead of adding a public type**

If the current names still use “surface” for semantic membership, rename only the private helpers:

```text
membershipSurface              -> membershipHandoffY
interiorMembershipSurface      -> interiorMembershipHandoffY
interactionMembershipSurface   -> interactionMembershipHandoffY
```

Keep the return type `OptionalDouble`. The value is a local handoff height used to find an adjacent supported seed; it is not a `SurfaceProbe` and is not serialized.

- [ ] **Step 3: Verify no semantic handoff enters physical traversal**

Search:

```powershell
rg -n "membershipHandoffY|interactionMembershipHandoffY|interiorMembershipHandoffY|new SurfaceProbe|new SurfaceCell" common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
```

Confirm connector-exit fallback is consumed only by seed/membership handoff logic. Do not feed an unsupported exit directly to `physicalSteps` as physical support.

- [ ] **Step 4: Run the focused ladder and membership tests**

Use the running GameTest environment:

```text
/test run laddertopexitstaysonupperstorey
/test run airaboveladderisfloormembershipcell
/test run laddertrapdoorattachesfloorswithoutmergingrooms
```

If the rename is the only production change, these must remain behavior-identical.

---

### Task 5: Simplify duplicated scanner traversal only when it is a net reduction

**Files:**
- Potentially modify: `SelectedFloorScanner.java`.
- Test: `FloorScannerGameTests.java`, `CopiedOpenHouseGameTests.java`.

**Interfaces:**
- Preserves strict descent and non-ascending descent as distinct semantics.
- Produces fewer duplicated queue/visited implementations only if the result is clearer and smaller.

- [ ] **Step 1: Compare the two descent traversals before editing**

Read `descendsFullStoreyFromStair` and `descendsBelowOwnedBand` side by side. Write down the only semantic difference: the first follows strictly lower steps; the second permits level movement but never ascent.

- [ ] **Step 2: Refactor only if one private traversal helper removes real duplication**

Acceptable result: one small private helper used by both callers with explicit call-site semantics and fewer total branches/queue-management lines.

Reject the refactor if it requires a new public class, a generic graph framework, multiple new records/enums, or increases the scanner's control-flow complexity. In that case leave the two methods as-is; duplicated bookkeeping is cheaper than a new source of truth.

- [ ] **Step 3: Do not change landing policy in this cleanup**

Keep `MIN_STABLE_LANDING_PEERS`, stair occupancy checks and strict/non-ascending threshold behavior unless a focused red GameTest proves a concrete wrong classification.

- [ ] **Step 4: Run affected GameTests immediately if this task changes code**

Run the specific flat landing, full stair, narrow corridor, deep stair chain, uneven floor and copied-house lower-chain tests in the running GameTest environment. Then run `/test runall FloorScannerGameTests` once.

---

### Task 6: Final integration verification and scope review

**Files:**
- Verify all files changed by Tasks 2-5.
- Update these two planning documents only with final evidence if desired.

**Interfaces:**
- Produces the merge-ready evidence boundary; does not commit.

- [ ] **Step 1: Run common tests and both loader compiles**

Run sequentially:

```powershell
.\gradlew.bat :common:test :fabric:compileJava :neoforge:compileJava --no-daemon --no-build-cache
```

Expected: build successful.

- [ ] **Step 2: Run the complete NeoForge GameTest server once**

Run:

```powershell
.\gradlew.bat :neoforge:runGameTestServer --no-daemon --no-build-cache
```

Require the final `All N required tests passed :)` summary. If an unrelated known flaky test fails, rerun only after recording that failure; do not attribute it to the floor cleanup without evidence.

- [ ] **Step 3: Inspect the critical ownership groups explicitly**

Confirm the final log includes the floor/storey cases for:

- canonical interaction below/near a floor anchor;
- flat landing and full/narrow stair boundaries;
- successive lower copied-house rooms;
- Add Room on an existing floor;
- unowned stair cells not borrowing registered room identity;
- ladder top exit and air-above-ladder membership;
- external basement-door attachment;
- room boundary/door ownership.

- [ ] **Step 4: Verify no competing policy was reintroduced**

Run:

```powershell
rg -n "StructureFloor\.BAND_TOLERANCE" common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java
rg -n "source\.getY\(\)" common/src/main/java/net/conczin/mca/server/world/data/RoomScanPlanner.java
rg -n "usesInteriorMembershipSeed|nearestFloorAtColumn|floorAtHeight" common/src/main common/src/test neoforge/src/main
```

Expected:

- no scanner dependency on persisted band tolerance;
- no raw-source-Y storey direction in planner action selection;
- no removed compatibility membership helper or nearest-column fallback resurrected.

- [ ] **Step 5: Review scope and formatting**

Run:

```powershell
git diff --check
git status --short
git diff -- common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java common/src/main/java/net/conczin/mca/server/world/data/RoomScanPlanner.java common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java common/src/main/java/net/conczin/mca/server/world/data/StructureFloor.java common/src/main/java/net/conczin/mca/server/world/data/Village.java
```

Confirm the final edits are narrower than the starting dirty floor diff and that unrelated Destiny/peer changes are untouched.

- [ ] **Step 6: Report the evidence boundary accurately**

Report:

1. exact production files changed;
2. which competing decision was removed from each;
3. focused test results;
4. final common/Fabric/NeoForge compile result;
5. full GameTest required-test count;
6. any residual landing heuristic deliberately retained;
7. live copied-world verification as pending unless it was actually exercised.

Do not describe the cleanup as a universal staircase inference solution.

## Plan self-review

- The plan preserves the current house behavior and starts from tests rather than a scanner rewrite.
- Fresh storey membership has one owner: `SelectedFloorScanner`.
- Exact spatial membership has one representation: `FloorGeometry`.
- Room partitioning does not rediscover floors.
- Planner action selection consumes canonical anchors/links and does not use raw interaction Y as storey identity.
- `StructureFloor.BAND_TOLERANCE` remains available for persisted matching/attachment but is removed from scanner policy.
- Selected and connected discovery remain separate because they answer different questions.
- Connector-exit membership is clarified with naming rather than a new persisted/public model.
- Scanner traversal is deduplicated only when that produces a smaller implementation.
- The old confirmation/packet/lifecycle expansion is explicitly out of scope.
- No subagent, worktree, history rewrite or automatic commit is part of execution.
