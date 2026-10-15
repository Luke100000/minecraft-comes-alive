# 1.21.1 nearby native detours

Status2026-10-15: focused owner regressions and both captured Builder journeys pass. Full-house reliability remains OPEN: the latest fresh Plains run fails separately at228/334 during acquisition. Publication verification is recorded below. No26.3 implementation or runs.

The captured Plains232 opening has an unfinished spruce support on an upper bridge. The crew occupies a nearby but disconnected platform. Native reachability from the bridge ground start succeeds; from the elevated crew it returns a two-node climb or a one-node terminal partial. Doubling a test-only finder multiplier reveals a full43-node route on unchanged terrain. This is a search-boundary finding, not evidence for Builder-owned steering.

Original MCA navigation treats an actually completed one-block climb as stationary because it requires squared distance4 from the first node. Its chaining branch records failure after an unusable continuation without using the existing detour owner. Correcting only those two decisions passed primitive tests, but physical repeats were unreliable: one flank completed in413ticks; another returned to the original platform and stalled. That hypothesis was reverted before the second regression/change. Rejected artifact names contain short-partial.

The configured persistent search also capped nearby approachable goals as if they were distant progressive goals. The revised owner reuses requiresExtendedPath and hasWalkableApproach: nearby goals with an existing walkable approach receive the configured bounded detour search; distant or sealed goals retain the smaller progressive cap. Existing useful ordinary paths return before escalation, and failed-search backoff remains. Chaining accepts logical one-cell movement and uses the existing native flank only after its continuation fails. No new state, timer, route executor or movement controls.

Source comparison covered vanilla PathFinder/PathNavigation, MCA persistent search/sink/producer, MineColonies workFrom invalidation/navigation lifecycle, and Baritone completed-segment chaining. MCA26.3 retains the corresponding gate and approach owner; comparison was read only.

RED/GREEN: the three owning NavigationRecoveryGameTests each failed on original production. All17 recovery tests,49 MCAGroundPathNavigationGameTests and12 native movement-sink tests pass on the revised source. The49-test suite preserves its original sealed-goal <=1024 expansions, ordinary-budget, failed-retry and no-physical-progress assertions. Common JUnit587 passes; both loader jars build.

The autonomous suite's vine-only exit class failure reproduced on unchanged owner and the first experiment (7/8 each), and remains7/8 on the final overhead artifact. It is an existing suite limitation; no fixture/assertion/deadline was weakened.

The apparently frozen captured232 stair was initially an unticking fixture chunk: entity tick153 stayed fixed while helper tick213 advanced, despite loaded/forced chunks. Native GameTest readiness covered only its template. Builder's archived fixture now waits for native entity-ticking readiness before spawning the saved body; this independent fixture regression was RED/GREEN. The original artifact still failed the actual short-climb journey after readiness was corrected. Do not infer a locomotion defect from the superseded frozen-entity capture.

A distinct fresh276 opening failed with advancing entity ticks and actual horizontal collision. Its route admitted a raised diagonal beneath an orthogonal overhead spruce plank. `MCAWalkNodeEvaluator.canApproachDiagonalJump` checked the two low approach cells but omitted the cells occupied by the raised body's head. Reuse `blocksJumpApproach` through the native body height: either overhead collision rejects the transition, while the original low-edge sliding check remains. No Builder steering or new movement state. Minecraft/MCA1.21.1, Baritone's diagonal head-bonk checks and MineColonies' jump/departure clearance were compared; MCA26.3 comparison was read-only.

`raisedDiagonalRejectsOverheadCornerBeforeJump` genuinely failed before this seven-line correction, after proving clear admission and actual collision controls. All8 fence/planter,49 navigation and17 recovery controls pass;587 common JUnit and both loader builds pass. Builder's saved276 journey now reaches the upper checkpoint through a native10-node route in roughly110ticks, with its80-tick inactivity and no-teleport checks intact. The saved232 journey and all18 focused Builder controls also pass. The subsequent fresh-house228 failure does not accept the entire lifecycle.

Current immutable artifacts in Builder `.superpowers/sdd/2026-10-14-plains-roof-live/`:
- NeoForge: mca-neoforge-1.21.1-overhead-diagonal.jar, SHA256 F5B6F5A41F6F64B96415A5949B3D67808C3A5482EF55860742FC3ED19A52FD4D.
- Fabric: mca-fabric-1.21.1-overhead-diagonal.jar, SHA2560488825199BC49A7B2AC4DEFAE5CCD54B36AF5AEF7DD00EBE2CA22DC4EE62D94.
Class-content comparison against nearby-detour changes only MCAWalkNodeEvaluator; nearby-detour versus swimming changes only MCAGroundPathNavigation and its finder inner class.

Fixed diff/four-lens reviews and raw logs: Builder scratch mca-nearby-detour-reviewed.patch / cleanup-review.md and mca-overhead-reviewed.patch / cleanup-review.md. Preserve earlier immutable artifacts and rejected-experiment evidence. Use only the current artifact for its next acceptance; do not combine results across states.

## Publication verification, 15 October 2026

The selected family-tree and navigation changes were verified with the unrelated
Brain-cache experiment, benchmark tooling and other staged work temporarily set
aside. The vine and Spanish-moss tests remain preserved in their separate stash.

- Common JUnit: 589 tests, zero failures, errors or skips. The generated-parent
  regression first failed against the unchanged family-tree production code.
- NeoForge GameTests, run sequentially: navigation recovery 17/17, ground
  navigation 49/49, fence/overhead clearance 8/8, autonomous movement 6/6,
  movement sink 12/12 and doors 12/12. All 104 required tests passed.
- Both `:fabric:build` and `:neoforge:build` completed successfully.

Concurrent unstaged raised-start edits appeared during the first verification
attempt, alongside a missing-common-classes compile failure. Those edits were
preserved separately; common outputs were rebuilt and the selected suites above
were rerun on the agreed source state. The concurrent edits are excluded from
this publication.

These checks do not establish full Builder-house acceptance, a new CPU/TPS
improvement, client visual correctness or other-version parity.
