# Floor Regions Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Native execution is recommended for this plan, subject to the user's choice.

**Goal:** Resolve selected floor ownership through bounded surface regions and transitions, eliminating the special stair-start rescan and inconsistent prospective storey numbering.

**Architecture:** Keep physical discovery in `SelectedFloorScanner.Observation`, with immutable canonical regions built from existing surface probes. `FloorGrouping` owns bounded semantic label bands and prospective numbering. Shared labels do not merge physical regions. Keep persisted identities and final server validation authoritative.

**Tech Stack:** Java 21, Minecraft 1.21.1, common shared logic, NeoForge GameTests, configured common JUnit tests, Windows PowerShell and the Gradle wrapper.

**Spec:** `docs/superpowers/specs/2026-10-05-floor-regions-design.md` (user-approved on 2026-10-05).

## Global Constraints

- Work starts on `dev/1.21.1`, Minecraft 1.21.1 and Java 21.
- No migration, automatic repair, or new automatic renumbering of existing saves.
- No edits to the user's world or mods as part of implementation or testing.
- No changes to unrelated navigation, villagers, beds, or GameTest registration.
- No new persisted region graph, background scanning, retained cache, or setting.
- No changes to the current door/ladder boundary contracts or room inheritance.
- Preserve exclusive staircase halves: an odd middle row belongs upstairs.
- A group's full representative-height range must fit the existing two-block tolerance.
- Ground remains 0 and basements remain negative.
- Packet/NBT schemas remain unchanged.
- Port only this subsystem through `dev/26.1.2`, `dev/26.2`, and `dev/26.3`; target verification is Java compilation only.
- No push or publication is part of this design.
- Follow `AGENTS.md`; preserve unrelated staged and unstaged work. Do not commit entire dirty files without isolating owned hunks. Use one coherent source implementation commit after verification, rather than automatic RED/GREEN commits.
- Serialize Gradle work in each checkout with `--no-parallel --no-daemon`. Keep diagnostic logs and copied-world fixtures in ignored `build/diagnostics/floor-regions/`.

## Review Focus

1. A tiny landing with no broad plateau must retain ownership without a minimum-area threshold (Task 2).
2. Nearby unroofed or wall-pocket surfaces must not become enclosed floor or attachment evidence (Task 2).
3. A dense neighbouring storey must not consume the selected floor's block budget or trigger whole-building discovery (Task 2).
4. Inserting a new level between occupied saved ordinals must fail explicitly rather than renumber existing rooms (Tasks 1 and 3).
5. A world or registration change after preview must be revalidated before commit, preserving the prior building on rejection (Task 3).

---

## File Map

| File | Responsibility |
| --- | --- |
| Create `common/src/main/java/net/conczin/mca/server/world/data/FloorGrouping.java` | Pure bounded grouping and prospective-number decisions. Package-private; no world reads. |
| Modify `common/src/main/java/net/conczin/mca/server/world/data/StructureFloor.java` | Delegate existing band grouping to that owner; retain persistence and identity APIs. |
| Modify `common/src/main/java/net/conczin/mca/server/world/data/SelectedFloorScanner.java` | Local region discovery, transition allocation, canonical selection and materialisation. |
| Modify `common/src/main/java/net/conczin/mca/server/world/data/RoomScanPlanner.java` | Use common numbering; preserve a proven attachment action when analysis fails. |
| Modify `common/src/main/java/net/conczin/mca/server/world/data/RoomScanPlan.java` | Represent an attachment with an unavailable prospective number using the existing sentinel. |
| Modify `common/src/main/java/net/conczin/mca/server/world/data/RoomWorkflow.java` | Propagate authoritative analysis failure before scanning or committing. |
| Inspect `common/src/main/java/net/conczin/mca/server/world/data/StructureScanner.java` | Keep existing interaction normalisation, attachment evidence, and validation; adapt only if its caller contract needs it. |
| Modify `common/src/main/java/net/conczin/mca/client/gui/BlueprintScreen.java` | Guard the basement label with actual prospective-number availability. |
| Inspect `common/src/main/java/net/conczin/mca/server/world/data/Village.java` / `VillageManager.java` | Preserve existing mutation boundaries; do not add load-time repair or numbering rewrites. |
| Create `common/src/test/java/net/conczin/mca/server/world/data/FloorGroupingTest.java` | Pure grouping and numbering regression assertions. |
| Modify existing `StructureFloorTest`, `RoomScanPlannerTest`, `RoomScanPlanTest`, `RoomDFUTest`, and `BlueprintScreenMapInteractionTest` | Delegation, ambiguity, stored-data preservation, and sentinel projection. |
| Modify `neoforge/src/main/java/net/conczin/mca/server/world/data/FloorScannerGameTests.java` | Runtime region, staircase, workflow, bounds, and save/reload regressions. |

All paths are relative to `C:\Users\mikol\IdeaProjects\minecraft-comes-alive-dev-1.21.1`.

### Task 1: One Pure Grouping and Numbering Policy

**Files:** Create `FloorGrouping.java` and `FloorGroupingTest.java`; modify `StructureFloor.java` and `StructureFloorTest.java` from the file map.

**Interfaces:**

- Produces `static List<FloorGrouping.Band> FloorGrouping.bands(Collection<Integer> heights)`.
- Produces package-private immutable `record Band(int minY, int maxY)` with `boolean contains(int height)`.
- Produces `static FloorGrouping.NumberDecision FloorGrouping.prospectiveNumber(Collection<StructureFloor> registered, StructureFloor ground, int candidateY)`.
- Produces immutable `record NumberDecision(Building.validationResult result, OptionalInt number)`; SUCCESS requires a present number, and failure requires an empty number.
- Retains `StructureFloor.floorNumbers(Collection<StructureFloor>, StructureFloor)` and its callers, delegating band formation to `FloorGrouping.bands`.

- [x] **Step 1: Add the grouping and numbering regression assertions.**

In `FloorGroupingTest`, define `groupRangeDoesNotChain`, `groupingIgnoresInputOrder`, `candidateReusesLandingStorey`, `conflictingSavedNumbersAreAmbiguous`, `occupiedOrdinalGapIsAmbiguous`, and `basementUsesNegativeNumber`.

The exact assertions are:

```java
assertEquals(List.of(new Band(76, 78), new Band(80, 80)),
        FloorGrouping.bands(List.of(78, 80, 76, 78)));
assertEquals(FloorGrouping.bands(List.of(67, 72, 76, 78)),
        FloorGrouping.bands(List.of(78, 76, 72, 67)));
// Registered anchors/numbers: 67/0, 72/1, 76/2; candidate anchor 78.
assertEquals(OptionalInt.of(2), decision.number());
assertEquals(Building.validationResult.SUCCESS, decision.result());
// Registered anchors/numbers: 67/0, 76/2, 78/3; candidate anchor 78.
assertEquals(Building.validationResult.AMBIGUOUS_STRUCTURE, conflict.result());
assertTrue(conflict.number().isEmpty());
// Registered anchors/numbers: 67/0, 78/1; candidate anchor 72.
assertEquals(Building.validationResult.AMBIGUOUS_STRUCTURE, insertion.result());
// Registered anchor/number: 67/0; candidate anchor 62.
assertEquals(OptionalInt.of(-1), basement.number());
```

Build registered floors with the existing `TestStructureFloors` helpers. Assert that every input floor's number and geometry are unchanged after each decision.

- [x] **Step 2: Run the existing bounded-band test and record the semantic RED case.**

Run `:common:test --tests 'net.conczin.mca.server.world.data.StructureFloorTest'`. Before adding the new helper, extend an existing `RoomScanPlannerTest` fixture so a proven attachment at Y=78 references the Y=76 saved Floor 2, then assert prospective Floor 2. Run that named method; expected current failure is actual Floor 3. An undefined-helper compile failure is not the behavioural reproduction.

- [x] **Step 3: Implement the pure interfaces and delegate existing band formation.**

Set `FloorGrouping.MAX_HEIGHT_SPAN = 2`. Sort distinct heights ascending, then start a new band only when the candidate exceeds the band's lowest height by more than 2. Return immutable lists. Existing `StructureFloor.floorNumbers` still derives ordinals relative to the supplied ground band.

For prospective numbers, group all supplied anchors plus `candidateY`. Reuse a unanimous saved number in the candidate band. For a new band above the ground, use the nearest lower band's number plus one when it fits below the nearest upper band's number; reverse this for basements. If there is only an upper neighbour on the basement side, use its number minus one. Reject conflicting relevant labels, an occupied ordinal, invalid ground context, or inconsistent neighbour ordering without mutating inputs. Missing ground context returns NOT_IN_BUILDING; numerical conflicts return AMBIGUOUS_STRUCTURE.

- [x] **Step 4: Run the pure grouping tests and original band tests.**

Run `:common:test --tests 'net.conczin.mca.server.world.data.FloorGroupingTest' --tests 'net.conczin.mca.server.world.data.StructureFloorTest'`. Expected: every selected test executes and passes. The planner integration RED remains recorded for Task 3.

### Task 2: Canonical Local Regions and Transition Ownership

**Files:** Modify `SelectedFloorScanner.java` and `FloorScannerGameTests.java`; consume the Task 1 grouping policy.

**Interfaces:**

- Retains `SelectedFloorScanner.scan(Level, BlockPos, int maxSize, int maxRadius)`, `Observation.selected(BlockPos)`, and `Result`'s externally consumed geometry/provenance fields.
- Adds private immutable `record SurfaceRegion(BlockPos representative, int anchorY, Set<SurfaceCell> cells)` inside `SelectedFloorScanner`.
- Adds private immutable `record FloorGroup(FloorGrouping.Band band, List<SurfaceRegion> regions)` inside `SelectedFloorScanner`.
- Adds private immutable `record FloorSelection(SurfaceCell traversalSeed, FloorGroup group)` inside `SelectedFloorScanner`.
- Produces operation-local `RegionDiscovery.region(SurfaceCell surface): Optional<SurfaceRegion>` and `RegionDiscovery.select(SurfaceCell requested): Optional<FloorSelection>`; the owner captures the existing world, ceiling resolver, step provider, and scan bounds.
- Replaces the private landing-cell ownership mapping with `TransitionOwnership.owner(SurfaceCell surface): Optional<FloorGroup>`. It consumes canonical endpoint regions from `RegionDiscovery` and the Task 1 bands; it does not discover another endpoint's outgoing floor chain.

- [x] **Step 1: Add a minimal turning-landing runtime regression.**

Add `turningLandingSharesUpperStoreyRegardlessOfSeed` to the existing test holder. Build a roofed shell using its existing helpers: a lower room at relative Y=0, a five-row flight to a small flat 2x2 landing at Y=5, and a perpendicular short flight from that landing to an adjoining upper room at Y=7. Include another upper room reachable through a door, retaining separate components. The main lower-to-upper groups are separated; the Y=5 landing and Y=7 rooms share a bounded group.

Observe from the lower flight's upper-owned rows, both interaction heights for those rows, all four landing cells, the short connecting flight, and the upper room interiors. Compare only equivalent seeds within the same selected structural component; compare group number rather than geometry across a door-partitioned component. Assertions: SUCCESS, identical feet-position sets for equivalent seeds, equal exact geometry on repeated unchanged scans, and no lower-room cells owned upstairs. Preserve the odd middle-row upstairs allocation.

- [x] **Step 2: Run the focused regression and record the current semantic result.**

Run `:neoforge:runGameTestServer -PmcaGameTest=FloorScannerGameTests#turningLandingSharesUpperStoreyRegardlessOfSeed`. Expected RED is differing owned geometry or missing landing/transition cells. If current geometry already satisfies that fixture, retain it as a passing invariant and use the recorded Task 1 planner discrepancy as the feature's RED; do not distort the fixture to manufacture failure.

- [x] **Step 3: Implement bounded region discovery and endpoint identity.**

Within `Observation`, reuse the current cached surface steps and ceilings. Discover ordinary supported surfaces until a connector boundary or proven transition is reached; resolve directly touching transition endpoints to stable surface regions. Detect stable landing evidence through existing same-height peer rules. A supported terminal cell with no transition is eligible as a one-cell region; do not introduce an area threshold.

Keep transition rows out of the ordinary-region height vote. Use the existing lower-height tie rule and deterministic cell comparator. Group the relevant endpoint regions with `FloorGrouping.bands`; discover enough ordinary surface evidence to establish the group's identity before classifying the requested component. Do not expand outgoing flights belonging to an adjacent floor group. Copy region members and groups before publishing them inside the observation.

- [x] **Step 4: Replace flight allocation and selection with the canonical group identity.**

Resolve endpoint groups once. Internal transitions retain one owner group. For inter-group proven flights, sort distinct transition step heights and assign heights below the middle index downstairs and the remainder upstairs. Preserve the existing full-block-flight proof requirement, partial collision heights, boundary behaviour, and cycle/radius/flight budgets.

`resolveSelectedFloor` obtains one `FloorSelection` and calls `scanSelectedFloor` once. `FloorBandClassifier` consumes the selected group and transition ownership, rather than comparing arbitrary landing surfaces with `canStep`. Ordinary cell ownership remains bounded by the resolved regions and existing enclosure rules. Preserve `supportedSource`, selected seed, structural materialisation, ceilings, and adjacent-floor evidence.

Remove the special dominant-height stair rescan, the old `StairFlightOwnership`, and obsolete seed-anchor logic after their call sites use canonical ownership. Do not leave parallel proven-flight classifiers. Retain only the ordinary uneven-surface predicates still required by the model.

- [x] **Step 5: Add direction, small-region, and discovery-budget assertions.**

Add `rotatedTurningLandingKeepsOwnership`, `tinyLandingDoesNotBorrowAnotherRoom`, `unroofedBranchDoesNotBecomeRegionEvidence`, and `adjacentRegionDoesNotConsumeSelectedFloorBudget` to the same holder. Rotate the first fixture 90 degrees and compare its ownership after transforming positions. For the tiny supported region, assert its cells remain represented but a nearby registered room is not borrowed. For an unroofed branch or a one-air wall pocket, assert no new ownership beyond the enclosed component. For budget accounting, the selected floor at its exact block/connector limit must still succeed with a larger neighbouring region; lowering that selected limit by one must return BLOCK_LIMIT.

- [x] **Step 6: Run the scanner holder and inspect executed results.**

Run `:neoforge:runGameTestServer -PmcaGameTest=FloorScannerGameTests`. Expected: all required tests execute and pass, including existing even/odd/full-block/long-wide flights, uneven landing repair, deep-chain isolation, slabs, wall pockets, and door/ladder boundaries. Do not weaken assertions or suppress failing fixtures.

### Task 3: Shared Prospective Numbering and Authoritative Ambiguity

**Files:** Modify `RoomScanPlanner.java`, `RoomScanPlan.java`, `RoomWorkflow.java`, and the scoped Blueprint label guard; extend the mapped common tests and `FloorScannerGameTests.java`.

**Interfaces:**

- Consumes `FloorGrouping.prospectiveNumber` and canonical selected geometry from Task 2.
- Adds `Building.validationResult result` to package-private `RoomScanPlanner.Analysis`; preserve its existing convenience constructors with SUCCESS defaults.
- Produces package-private `static Analysis RoomScanPlanner.analyzeFresh(Village, BlockPos source, StructureScanner.FloorObservation observation)`; existing `planFresh` delegates to `.plan()` for current callers. The private component-aware path has the same result semantics.
- Produces package-private `static RoomScanPlan RoomScanPlan.attachmentWithoutNumber(int buildingId, BlockPos source, BlockPos scanSeed, StructureFloor selectedFloor)` using the existing Integer.MIN_VALUE prospective-number sentinel.
- Retains the public `RoomScanPlan` record fields and packet/NBT schemas. ADD_ATTACHMENT may have an unavailable number; it must still have a proven building target and selected candidate geometry. All other mode invariants remain unchanged.

- [x] **Step 1: Add planner, plan, and projection regression assertions.**

Finish `attachmentAboveLandingReusesExistingStorey` from Task 1: Y=76 Floor 2 plus candidate Y=78 yields ADD_ATTACHMENT and prospective 2. Add `conflictingStoreyNumbersPreserveAttachmentFailure`: conflicting Y=76/2 and Y=78/3 labels yield analysis AMBIGUOUS_STRUCTURE, plan ADD_ATTACHMENT targeting the proven building, `hasProspectiveFloor() == false`, and unchanged registered numbers. Add `unnumberedAttachmentRetainsIdentity` to `RoomScanPlanTest`; assert its target building and selected geometry are retained with no prospective number.

In `BlueprintScreenMapInteractionTest`, assert an unnumbered attachment uses the ordinary Add Floor label, while known -1/-2 labels remain Add Basement. Reuse the existing button test seam; do not create a second rendering state model.

- [x] **Step 2: Run the relevant common tests and record RED.**

Run `:common:test --tests 'net.conczin.mca.server.world.data.RoomScanPlannerTest' --tests 'net.conczin.mca.server.world.data.RoomScanPlanTest' --tests 'net.conczin.mca.client.gui.BlueprintScreenMapInteractionTest'`. Expected semantic failure for the current planner is prospective 3 rather than 2; compile failures for new interfaces are not additional behavioural evidence.

- [x] **Step 3: Implement the authoritative analysis result and shared number projection.**

Replace `adjacentFloorNumber` arithmetic with the Task 1 pure decision over the proven building's floors and fixed ground floor. On an ambiguous decision, retain the attachment target and geometry with no prospective number and return the analysis failure. Do not fall through to Add Building. `analyze` preserves its early registered-room selection and persisted-floor fallback; ambiguity must not block existing room updates.

Allow the sentinel only for the attachment's unavailable-number state. Guard `BlueprintScreen.updateStructureScanControl`'s basement label with `scanContext.hasProspectiveFloor()` before testing whether its number is negative. The existing action and wire fields remain sufficient; the server provides the existing ambiguity error after an attempted action.

- [x] **Step 4: Propagate analysis failures through workflow entry points.**

At `RoomWorkflow.scanRoom` and its attached-room analysis, return the analysis result before materialising or committing a candidate whose analysis failed. Preserve expected target/Room checks and the existing fresh analysis before commit. Generic building registration paths that resolve a proven attachment must use the same authoritative failure; they must not bypass it through new-building fallback. Do not alter unrelated saved-data lifecycle or add a repair call to `Village`/`VillageManager`.

- [x] **Step 5: Add runtime registration, reload, and stale-preview regressions.**

Add `turningLandingRegistrationOrderKeepsUpperNumber`: using the Task 2 fixture and a fixed registered ground reference, register the eligible upper room components in opposite orders in isolated manager states; all upper/landing components receive the same expected group number, and preview/commit agree. Add `turningLandingRemoveReaddKeepsRoomSelection`: remove a non-main Room, re-add through ADD_ROOM, then save/reload; upper stair interactions recover that Room's identity.

Add `staleRegionAttachmentDoesNotCommit` and `conflictingSavedStoreysRejectOnlyNewAttachments`. Capture a preview, change a relevant support or referenced registration, and assert the old preview fails the existing validation with no new room/structure or modified unrelated floor. With conflicting labels, assert fresh attachment returns AMBIGUOUS_STRUCTURE but an existing registered Room still selects UPDATE_ROOM. Add a `RoomDFUTest` round-trip assertion for the 0/1/2/3/2 saved labels without adding a migration or repairing them in the fixture.

- [x] **Step 6: Run the same common regression selection and the scanner holder.**

Include `FloorGroupingTest`, `StructureFloorTest`, `RoomDFUTest`, and all Task 3 test classes in the focused common invocation. Run `:neoforge:runGameTestServer -PmcaGameTest=FloorScannerGameTests` after the workflow changes. Expected: actual tests execute, all pass, and source gameplay evidence remains separate from target compilation.

### Task 4: Source Verification, Owned Commit, and Floor-Only Ports

**Files:** Only implementation/test files above and the approved documentation; target-version equivalents in the existing three checkouts.

**Interfaces:** Consumes the verified source change; produces a focused source commit, adapted floor-only target changes, and per-target Java compilation evidence. No publishing.

- [x] **Step 1: Run copied-house regressions and source Java compilation.**

Run `:neoforge:runGameTestServer -PmcaGameTest=ReportedStairHouseGameTests`, then `:neoforge:runGameTestServer -PmcaGameTest=ReportedFloorInteractionGameTests`. Use existing checked-in disposable fixtures; do not depend on the user's live world or add temporary diagnostic methods to committed tests. Run `:common:compileJava :fabric:compileJava :neoforge:compileJava`. Every invocation uses `--console=plain --no-parallel --no-daemon` and must exit 0 with its expected test execution or compilation output.

- [x] **Step 2: Review the owned diff against the spec and record evidence.**

Check for one canonical ownership path, removed special rescan, retained odd-row policy, unchanged serialization, no new save repairs, bounded deep-chain behaviour, and preserved unrelated staged entries. Run `git diff --check` on changed tracked paths. Record source RED/GREEN and actual test counts in an ignored diagnostic note. For native execution, obtain one fresh read-only whole-change review through available native review/subagent tools; do not create another user-facing chat. If unavailable, report that independent-review gap rather than claiming a review occurred.

- [ ] **Step 3: Commit only owned floor changes.**

Inspect full file diffs and the index. Preserve the pending GameTestHolder/import removals and unrelated staged work. Isolate this task's hunks using the already established focused-commit workflow; verify other index entries before/after. Create one source implementation commit only after all required source checks pass. Do not commit diagnostic exports, user worlds, or unrelated staged hunks.

- [ ] **Step 4: Inspect and port through the existing target checkouts.**

Confirm each current branch, dirty state, and ancestry before modifying it. Inspect every source hunk against the version-matched target owner. Use a floor-only merge when the incoming range contains only authorised floor changes and their docs; otherwise transplant the focused implementation commit without importing unrelated history. Resolve adaptations explicitly; do not use wholesale ours/theirs. Preserve target logs and untracked files.

Target roots are `C:\Users\mikol\IdeaProjects\minecraft-comes-alive-dev-26.1.2`, `C:\Users\mikol\IdeaProjects\minecraft-comes-alive-dev-26.2`, and `C:\Users\mikol\IdeaProjects\minecraft-comes-alive-dev-26.3`.

- [ ] **Step 5: Compile Java once in each target after its port.**

Run `.\gradlew.bat :common:compileJava :fabric:compileJava :neoforge:compileJava --console=plain --no-parallel --no-daemon` sequentially in those roots. Expected: BUILD SUCCESSFUL and process exit 0. Fix relevant compile/API adaptation failures, then rerun that target. Do not run target GameTests, build jars, or start clients without a new user request.

- [ ] **Step 6: Review the final branch states and hand off bounded evidence.**

Confirm every owned source hunk was ported/adapted or already present, verify final branch states, and report exact commits and checks actually run. State the target runtime/client gaps. Do not push, install jars, modify the original instance, or claim runtime behaviour from compilation.

## Plan Self-Review

- Spec coverage: Tasks 1/3 own grouping and new numbering; Task 2 owns canonical regions, transition allocation and bounds; Task 3 owns workflow/serialization preservation; Task 4 owns verification and authorised ports.
- Interface consistency: `FloorGrouping` is the only pure policy helper; all scanner carriers remain private. `Analysis.result` carries failure, while the existing plan/wire fields retain action and target provenance.
- Review focus: the five additional input classes above each have named assertions in their owning task.
- Scope: no registry replacement, schema migration, UI redesign, background execution, or automatic save repair. The sentinel label guard is necessary to project an ambiguous attachment correctly.
- Execution recommendation: Native in this chat. The scanner and planner depend closely on shared grouping semantics, and keeping one implementation context reduces coordination overhead; use a fresh reviewer after source verification.

User authorised inline execution in this chat. Execution evidence is recorded in the ignored build/diagnostics/floor-regions/progress.md ledger.

## Execution rulings

- Keep semantic grouping separate from physical region ownership. The existing
  short-storey regression rejects merging proven-flight endpoints by height.
  Use canonical SurfaceRegion owners and the existing row split; FloorGrouping
  handles prospective labels. No scanner FloorGroup/FloorSelection wrapper.
- Turning tests compare geometry within each region, then verify shared labels,
  registration order, save/reload, room removal/re-addition and stale previews.
- Preserve the existing uneven-floor regressions as requested; do not duplicate
  their shapes with weaker assertions.
- Authorised coordination with the user's basement diagnosis chat shares this
  checkout; serialize Gradle and keep its fixture edits out of unrelated commits.
- The user subsequently stopped coordination. Finish directly with self-review;
  do not create another review chat or exchange further messages.
- SurfaceRegion stores only its canonical SurfaceCell. The operation-local
  member-to-region map supplies identity; retaining another member-set copy and
  scanner FloorGroup/FloorSelection carriers is unnecessary.
- Existing unroofed, uneven-exterior, wall-pocket and deep-chain tests cover
  enclosure and isolation. Extend the turning fixtures with budget/workflow
  assertions rather than introduce duplicate test shapes or helper-only tests.
- Prospective numbering also consumes candidate geometry. Overlapping stacked
  floors within two blocks retain separate numbers; disjoint nearby regions may
  reuse a unanimous label. Never extend a saved label's full height range beyond
  tolerance or accept the unavailable-number sentinel as a label.
- Transition proof is bounded to twice the selected floor budget because it
  observes both halves. Traversal/materialization charge only selected cells and
  connectors against the requested limit. Lowering the exact selected limit by
  one must fail rather than return clipped geometry.
- Use the existing attachment factory with its existing unavailable-number
  sentinel; no additional factory or persisted schema is needed.
- Verify in an ignored disposable source snapshot after shared build output was
  overwritten by concurrent unrelated builds. Compare all owned source bytes to
  the tested snapshot before committing.

## Execution evidence

- Source semantic RED: landing Y=76/Floor 2 plus Y=78 candidate produced Floor 3.
- Additional RED: extending saved 76..78/Floor 2 downward to Y=74 reused Floor 2;
  an unavailable saved sentinel was accepted. Both pure regressions now pass.
- Budget RED: limit 11 returned SUCCESS with a clipped eight-cell landing. The
  same turning GameTest passes after separating proof and materialization limits.
- Full source verification: 526 common JUnit tests, zero failures/errors/skips;
  all 84 scanner GameTests pass; common/Fabric/NeoForge Java compilation passes.
- The original turning geometry RED used an overbroad expectation across separate
  physical regions. It is not valid evidence for merging those regions. Corrected
  assertions preserve the existing short-storey behavior and compare labels.
- Diagnostic logs and the execution ledger remain under ignored
  `build/diagnostics/floor-regions/`; runtime worlds are disposable.

- Copied-house source checks pass: ReportedStairHouseGameTests 4/4 and
  ReportedFloorInteractionGameTests 2/2. The new descending basement fixture
  reproduces ADD_ATTACHMENT on the baseline and now commits ADD_ROOM on the
  existing basement, retaining its label and the room across the door.
- Source owned diff reviewed; tested implementation bytes match the snapshot.
  The remaining unchecked source-commit/port steps are executed after saving
  this plan; final branch hashes and target compile results go in the ledger.
