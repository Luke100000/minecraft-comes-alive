# Shared Floor Observation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove repeated attachment/handoff physical probes without changing floor behavior or growing production Java LOC.

**Architecture:** Keep one SelectedFloorScanner.Observation per operation. Capture immutable live-contact relations separately from explicit vertical/stair connections; Village consumes those facts and retains identity, overlap and ranking policy.

**Tech Stack:** Minecraft 1.21.1, Java 21, Gradle, common JUnit and NeoForge GameTests.

**Spec:** docs/superpowers/specs/2026-10-17-shared-floor-observation-simplification-design.md

## Global Constraints

- Work on dev/1.21.1 in the existing linked checkout; no WSL or new worktrees.
- Preserve unrelated dirty work; no pushes, ports, user-world changes or save migration.
- Baseline f9de24136; aggregate physical production Java lines must not increase. Report nonblank deltas too.
- Preserve physical geometry, ownership, source/seed/anchor, action precedence, validation, IDs and floor numbers.
- Fresh discovery uses current blocks; persisted membership preserves identity; caches are operation-local.
- Keep live contact distinct from explicit connections, especially for overlap permissions.
- Use the normal Gradle test tasks; the user declined further test isolation on 2026-10-18. Verify actual registered selectors because shared launch files can be overwritten by concurrent runs.

## Review Focus

- Explicit connection to one building plus live contact to another must not weaken overlap rejection.
- World changes after observation must not trigger additional world reads during planning; a new operation must observe the change.
- No-world consumers retain geometry-only outcomes with absent live evidence.
- Handoff ordering and ambiguity remain unchanged for irregular/slab/ladder sources.
- Registered-room planning remains lazy and update/confirmation performs a fresh scan.

## Task 1: Capture and consume shared physical evidence

**Files:** SelectedFloorScanner.java (shared physical queries), StructureScanner.java (handoffs and captured contacts), Village.java (policy only), RoomScanPlanner.java (pass contacts); existing RoomScanPlannerTest.java, VillageFloorSystemTest.java and FloorScannerGameTests.java.

**Interfaces:** Observation exposes `surfaceCell(BlockPos)` and `shellsTouch(FloorGeometry.Cell, FloorGeometry.Cell)`. FloorObservation adds immutable `Set<StructureConnector.VerticalConnection> liveContacts`; this carrier is not merged into verticalConnections. Village's production attachment overload receives liveContacts as its fourth argument; geometry-only test entry uses empty evidence.

- [x] Run baseline common tests and FloorScannerGameTests; read results before production edits.
- [x] Add `attachmentPlanningUsesCapturedContactAndNextOperationRefreshesIt`: existing tall registered lower room, later upper deck, observe upper, obstruct lower supports, assert captured plan still targets building 51 and fresh planning rejects attachment. Run and witness RED on old re-probing planner.
- [x] Add explicit/live conflicting-owner policy coverage and immutable observation coverage; preserve all existing no-world tests.
- [x] Reuse the existing observation's ceiling/surface queries for handoff and standing checks. Capture each relevant candidate-to-saved-floor live contact once. Delete Village's physical probes and duplicate live-contact loops; pass immutable evidence through planner.
- [x] Address Franklin's review finding: capture explicit connector evidence first; relocate the existing pure explicit/stair resolver to StructureScanner for reuse by capture and Village policy, and skip live contact when either kind of explicit connection exists. Pinned with the real ladder/trapdoor fixture before implementation.
- [x] Run focused common tests and new GameTest; expected PASS. Final normal-task runs: 595 common + 1 Fabric ordinary tests; 99 FloorScannerGameTests and 7 ReportedStairHouseGameTests passed, including the explicit-contact guard regression.

## Task 2: Integration verification and handoff

**Interfaces:** Consumes Task 1's immutable observation and policy-only attachment selection; no new production interface.

- [ ] Compile common, Fabric and NeoForge; run ordinary project tests and full NeoForge GameTest integration suite. Report any remaining failures by name and compare unrelated failures with checkpoint behavior.
- [x] Measure physical/nonblank production LOC against f9de24136 and verify aggregate delta <= 0. Physical 3497 -> 3496 (-1); nonblank 3153 -> 3152 (-1).
- [x] Obtain one fresh-context review of the refactor only, fix important findings with focused regression proof, preserve unrelated changes. Franklin found one unnecessary-probe P2; its specific RED was witnessed and final 99-test GREEN includes the corrected ladder fixture. No deferred minor findings or declined behaviors.
- [x] Update this plan/spec with results and checkpoint only refactor sources, tests and documentation. No push; full-suite readiness remains explicitly unverified.

## Execution approval

User requested immediate implementation; no further approval pause. Inline execution with a final independent review. Windows-native ledger and commands replace the skill's Bash scripts; no WSL.

## Verification record

- Checkpoint baseline: 593 common tests and 98 floor-scanner GameTests passed.
- Focused contact regression: RED against checkpoint (planner re-probed world), then GREEN after refactor; 595 common tests passed, including separate live/explicit overlap policy and immutable captured contacts.
- Common, Fabric and NeoForge compile; all 596 ordinary tests pass.
- Final normal Gradle tasks (no init script): FloorScannerGameTests 99/99, ReportedStairHouseGameTests 7/7. Test selectors confirmed from actual runtime registration.
- Full NeoForge integration ran 656 tests: 628 passed, 28 failed. This is NOT a globally green suite. Do not claim global integration readiness or live modded 26.3 verification. The full-suite failures are not all established as pre-existing; source/baseline comparison and suite-fixture diagnosis remain follow-up work.
- The full-suite airborne floor assertion passed in the final focused scanner class. Missing runtime inner-class errors also occurred in the full run; do not infer a product fix from those errors alone.
- Logs: `%TEMP%/mca-shared-observation-full.log`, `mca-shared-observation-final-scanner.log`, `mca-shared-observation-final-houses.log`, `mca-shared-observation-explicit-red.log`.

### Production size (physical / nonblank)

| File | Checkpoint | Refactor | Delta |
| --- | --- | --- | --- |
| SelectedFloorScanner.java | 1533 / 1417 | 1549 / 1431 | +16 / +14 |
| StructureScanner.java | 361 / 319 | 389 / 345 | +28 / +26 |
| Village.java | 1359 / 1199 | 1313 / 1157 | -46 / -42 |
| RoomScanPlanner.java | 244 / 218 | 245 / 219 | +1 / +1 |
| Total | 3497 / 3153 | 3496 / 3152 | -1 / -1 |

Final SHA256: SelectedFloorScanner `A105C296E8107446AD7F1B318BDDDCD43B919ADC6366C326E841B3F53E893197`; StructureScanner `9C713E4EA938A740A3EF995D9CAB97FDA2D2730B929EF4246213E40C1289C7DE`; Village `ADE3CB925F0C391DC44D799EE48380A6D084E4F4F0A1E46F4E72DB165C20B68D`; RoomScanPlanner `3B427CD840D6713FECAB9265B3A128666E3D8B5E40CD3362BAAA2DFCBB5AC09C`.

### Full-suite failures requiring separate diagnosis

- registeredroomcachedoesnotrestoreinvalidatedgeometry
- villagerescapesenclosedyardthroughonlygateonslab
- sharedroomcachereusesbedsandinvalidatesroofchanges
- personalmourningwaitsfornearbymonster
- unreachableroofdoesnotleavevillagersuspendedondecorativevine
- airborneselectionresolvesthefloorinitsowncolumn (final focused class passes)
- panicinterruptsandmourningcanresume
- combatinterruptsmourningapproach
- mourningflowerrestoresonlywhileitownsthehand
- personalmourningwaitsforplayerdirectedmovement
- playerretaliationdamagedrivesactualemergencyescape
- wetandcoveredsearchrespectfootprintbudget
- flowersdonothidesupportedbank
- guardsreusescheduledactivitywithintickbutnotacrossticksorschedules
- lazypoiacquisitionrespectscooldownandbothmemoryguards
- villagerignoresunnecessaryunroofedglowberryclimb
- resurrectionclearsexactgravelocation
- escapenativepathrejectedwhentacticaltrackerfindshazard
- teleportprobechecksactualcenteredlandingbox
- coldhousebesidenarrowsteppedporchdoesnotrequireneighborchunks
- longdetourusesoverflow
- lesscrowdedoverflowrespectsadditionaltravellimit
- coldhousebesideunevenporchdoesnotrequireneighborchunks
- compareshouseroutesinsteadoffirstbatchedhit
- morethansixtyfourextrarouteblocksusesoverflow
- widenativeendpointcannotstandindoorway
- exactlysixtyfourextrarouteblocksareallowed
- colddiscoveryrejectsanunloadedboundarywithoutloadingit
