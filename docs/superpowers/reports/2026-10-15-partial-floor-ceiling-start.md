# Native departure from a partial floor under a low ceiling

Scope: MCA 1.21.1, based on 0d93f6b24. Builders owns work and exit destinations;
MCA/Minecraft owns the physical path. No new controller, timer, cache, bed-specific
classification or Builder recovery state.

## Cause and correction

The completed Snowy408 capture has two grounded workers on a bed at relative
Y=1.5625 beneath a top slab. Their actual bodies fit, but vanilla rounds the
logical start up to Y=2. Its whole-cell height scan includes the slab and marks
that start BLOCKED. Every tested exterior destination receives a one-node path.
The existing straight-corridor fallback also cannot open an exit from this pose.

The MCA-only `bedUnderLowCeilingKeepsPhysicalDeparture` reproduces this without
Builder scheduling. It requires real bed contact, collision-free body geometry,
an entity-ticking chunk, a native complete path and physical descent within 80 ticks.
`bed-ceiling-mca-red-ticking.log` records the genuine BLOCKED-start RED.

MCA's existing `correctRaisedStartNode` already restores the physical feet cell
when a rounded raised start is unreachable. Include a BLOCKED selected start in
that same condition. Existing grounded/climbing, modeled-floor, rise, sweep,
native neighbor classification and clearance owners remain in place.

Compared version-matched vanilla `WalkNodeEvaluator.getStart`, its integer height
classification and native waypoint arrival; MCA start/sweep/physical navigation;
MineColonies `PathfindingUtils.prepareStart` and `AbstractPathJob`; Baritone
`PathingBehavior.pathStart`. Physical stance belongs in navigation. None requires
a second Builder movement pipeline.

## Fixture and verification evidence

An initial physical check used loaded terrain outside the tiny template but did
not ensure entity ticking. It is invalid physical verification. The corrected new
fixture produces RED on the original owner, then physical GREEN with the correction.

The combined controls exposed the same pre-existing setup issue in the stair
regression: entity age remains zero and `isPositionEntityTicking` is false for all
80 ticks. The user explicitly approved replacing its ground setup with the existing
`GameTestTerrain.prepareFlatArea`, using the identical radius/clearance and preserving
every assertion and timeout. `bed-ceiling-mca-safety-fixture-trace.log` records that
diagnosis. Diagnostic per-tick logs are removed.

Stable `bed-ceiling-mca-safety-green.log` passes 589 common JUnit tests, both loader
builds and all nine physical-safety controls, including the bed departure, stair
descent, head-level vine, overhang, fence and trapdoor controls.
`bed-ceiling-mca-navigation-controls.log` also passes all 49 ground-navigation controls.

Builders' saved five-worker Snowy408 replay fails before the change and evacuates
the complete crew afterward. The experimental NeoForge and Fabric jars differ
from the preceding frozen pins in exactly `MCAWalkNodeEvaluator.class`; no resource
or archive entry changes. `snowy-exit-native-blocked-red.log` and
`snowy-exit-native-green.log` preserve this focused comparison. It does not establish
fresh complete-house or repeated matrix acceptance.

## Fixed review

Four-lens review covers the same two Java paths: reuse the existing start owner;
no new mutable state or helper; retain native neighbor/collision checks and the
grounded/non-climbing boundaries; use one enum comparison before expensive physical
queries. The tests execute real paths and movement, rather than comparing helpers.
The fixed reviewed patch SHA256 is
ED1202E13389113C07DB2C2C1C3C6CEF82F2677B0405ABDCEDB451D77BCC085E.

Builder logs and the reviewed patch are under its ignored
`.superpowers/sdd/2026-10-15-access-execution/postrepair-live/`. Unrelated staged MCA
work is outside this correction and must remain untouched.
