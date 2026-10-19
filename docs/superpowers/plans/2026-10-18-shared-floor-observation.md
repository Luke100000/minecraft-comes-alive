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

## Item 4: subsequently approved NPC cache dependency fix

The user approved the previously deferred item 4 after the architecture follow-ups.
The earlier deferral and verification counts above remain historical evidence.

Implemented within IndoorRoomCache's existing LoadedBlocks view: record normalized
discovery/partition block reads and loaded/unloaded column facts, merge them with
the final floor footprint, and validate them with non-loading lookups. Include
column facts in the unchanged per-entry/global observation budgets. When a complete
proof cannot fit, discard partial maps and rediscover at the normal 40-tick boundary;
do not change enclosure acceptance or reject valid room geometry due to this limit.
Preserve unchanged complete-proof reuse, room-local fallback and saved ownership.

Three focused runtime regressions were reproduced RED, then passed with the fix:
exterior support extension, exterior roof/membership changes and oversized dependency
proofs. After an external source overwrite, restored the exact tested patch and
reran all three successfully. Independent read-only review found no actionable
P0/P1/P2 issues. Chunk availability transitions/global eviction accounting were
source-reviewed but are not directly exercised by these three regressions.

Verification: normal `test :fabric:compileJava :neoforge:runGameTestServer --continue`
completed ordinary common/Fabric testing (600 tests, zero failures/errors), Fabric
compilation and the full runtime run (664 tests, 648 passed, 16 failed). All three
new cache regressions passed both focused and in full integration. Global release
readiness is NOT established. Full log: `%TEMP%/mca-cache-dependencies-final.log`.

Important source-state caveat: another actor repeatedly replaced IndoorRoomCache
with the HEAD baseline, including during final integration after the cache batch
had executed. The tested patch was restored once the run finished, and the source
hash again matches the independently reviewed/tested version:
`21EBE5B8937FE5CDEB1DDBBD0E9F27CF89C369384CB37129774612EA339F8639`.
Compiled common/NeoForge Entry classes were also rewritten afterward and no longer
contained completeDependencies. Tests prove the implementation's regression behavior,
not that the current compiled outputs or running client contain it. Do not rebuild
over a running client; resolve concurrent source/output ownership first. No writer
was identified from read-only checks, and no other chat/client was stopped or messaged.

The source fix and tests remain uncommitted on dev/1.21.1. No port/push or live 26.3
verification was performed. Remaining failures are recorded without asserting all
are unrelated to floor work:

- registeredroomcachedoesnotrestoreinvalidatedgeometry
- villagerescapesenclosedyardthroughonlygateonslab
- sharedroomcachereusesbedsandinvalidatesroofchanges
- personalmourningwaitsfornearbymonster
- airborneselectionresolvesthefloorinitsowncolumn
- panicinterruptsandmourningcanresume
- combatinterruptsmourningapproach
- mourningflowerrestoresonlywhileitownsthehand
- personalmourningwaitsforplayerdirectedmovement
- moosavedhillsidewalksdowntoriverandfishes
- villagerwalksunderbridgewithhangingglowberries
- guardsreusescheduledactivitywithintickbutnotacrossticksorschedules
- lazypoiacquisitionrespectscooldownandbothmemoryguards
- villagerignoresunnecessaryunroofedglowberryclimb
- resurrectionclearsexactgravelocation
- longdetourusesoverflow

Ledger: `.superpowers/sdd/2026-10-18-indoor-cache-dependencies/progress.md`.
- lesscrowdedoverflowrespectsadditionaltravellimit
- coldhousebesideunevenporchdoesnotrequireneighborchunks
- compareshouseroutesinsteadoffirstbatchedhit
- morethansixtyfourextrarouteblocksusesoverflow
- widenativeendpointcannotstandindoorway
- exactlysixtyfourextrarouteblocksareallowed
- colddiscoveryrejectsanunloadedboundarywithoutloadingit

## Architecture follow-ups (2026-10-18)

User approved items 1, 2, 3 and 5; item 4 (NPC cache invalidation dependencies)
remains deferred. Implemented on dev/1.21.1, left uncommitted, no port or push.

- Diagnostics no longer equate a refreshed persisted Floor's modal height change
  with discovery of the player's upper Floor.
- FloorObservation captures merged explicit attachment matches once, separately
  from live shell contact. Village consumes these immutable facts.
- Add Building shares its operation-local discovery and identical-result room
  partition. Attachment/new-building fallback precedence and fresh later operations
  are preserved. A mutation restoring independent discovery made the reuse test fail.
- Loaded-only NPC block views expose column availability to the canonical scanner.
  An unknown seed cannot fall through to adjacent air inspection. The cold porch
  regression and actual unloaded-boundary rejection both pass.
- Corrected the unloaded-boundary GameTest: its old corridor ended at local X=8
  rather than the chunk edge. The corrected fixture asserts that it reaches X=0
  and suppresses construction-time neighbor updates that would load the missing
  chunk. Original rejection/no-loading assertions are unchanged.

No saved ownership, numbering, NPC invalidation footprint, cache lifetime or
observation limit was changed. Skipping item 4 can leave NPC shelter/wandering
geometry stale after exterior edits until another invalidation/eviction; it does
not rewrite saved Room identities. Seven production files changed, net +37 lines;
the old no-growth simplification result above describes the completed 44e3fdcbe
refactor, not this separately approved set of fixes.

Verification:

- Normal `test :fabric:compileJava`: 600 common/Fabric tests, no failures/errors;
  Fabric compilation passed. All common tests passed again after the reuse mutation
  was removed. NeoForge compiled and launched through normal tasks.
- 99 focused FloorScannerGameTests and 7 ReportedStairHouseGameTests passed.
- Cold uneven-porch GameTest: reproduced RED, then GREEN with loaded-column parity.
- Corrected cold unloaded-boundary GameTest: GREEN, and passed in the final full run.
- Independent read-only review found no actionable P0/P1/P2 issues in the main patch.
- Final full NeoForge run: **658 tests, 642 passed, 16 failed**. This is not a
  globally green release. Do not label every remaining failure unrelated: in
  particular, airborne floor selection passes focused but fails in the full suite;
  its integration failure remains unresolved.

Final runtime log: `%TEMP%/mca-architecture-final-gametest.log`.
Detailed execution ledger: `.superpowers/sdd/2026-10-18-floor-architecture-followups/progress.md`.
No live 26.3 verification was performed.

Remaining full-suite failures:

- registeredroomcachedoesnotrestoreinvalidatedgeometry
- villagerescapesenclosedyardthroughonlygateonslab
- sharedroomcachereusesbedsandinvalidatesroofchanges
- personalmourningwaitsfornearbymonster
- airborneselectionresolvesthefloorinitsowncolumn
- archerescapescavecornerandkillspursuingspider
- panicinterruptsandmourningcanresume
- combatinterruptsmourningapproach
- mourningflowerrestoresonlywhileitownsthehand
- personalmourningwaitsforplayerdirectedmovement
- playerretaliationdamagedrivesactualemergencyescape
- failedreassignmentpreservesexistingforcedhome
- guardsreusescheduledactivitywithintickbutnotacrossticksorschedules
- lazypoiacquisitionrespectscooldownandbothmemoryguards
- villagerignoresunnecessaryunroofedglowberryclimb
- longdetourusesoverflow
