# Supported stair starts on 1.21.1

Base016b291b4 with the pre-existing unrelated staged work retained. Builder's
Snowy285 trace contains a partial path starting inside a stair, one below the
grounded actor. A fresh final-pose reload does not reproduce its transient history.

The lower-surface regression isolates the first wrong decision: vanilla selects
the supported air node above a stair, while MCA's same-column raised-start guard
forces it down into the stair's BLOCKED cell. The MCA-only native regression fails
at that assertion without Builder scheduling. It also requires real native descent
after selection, rather than only checking coordinates.

`correctRaisedStartNode` now treats a same-column node as unreached climbing only
when that node is climbable. The existing modeled-floor, maximum-step and physical
sweep checks remain. This retains the head-level-vine protection while allowing
ordinary supported starts. No block-specific stair exception, controller, timer,
cache, physics change or Builder-owned movement was added.26.3 was compared only.

Owner comparison: native WalkNodeEvaluator start selection and PathNavigation
waypoint admission, MCA floor/sweep and climb ownership; MineColonies start-node
setup and Baritone stance-based pathStart. All keep this responsibility below
construction scheduling. The four-lens review reuses the existing climbability
and clearance predicates, adds no state, leaves world reads on the server thread,
and adds one narrowly reached climbability query.

Evidence lives in Builder's ignored
`.superpowers/sdd/2026-10-15-access-execution/postrepair-live/`:
`stair-start-mca-red.log` and `stair-start-builder-red.log`. The initial green-build
attempt was invalid because concurrent publication temporarily stashed the patch;
only `stair-start-mca-green-stable.log` can verify the restored correction.

Stable verification:589commonJUnit with no failures/skips, both1.21.1loader builds,
all8NavigationPhysicalSafetyGameTests (including real stair descent and the old
head-level-vine protection), then all49MCAGroundPathNavigationGameTests pass.
Logs: `stair-start-mca-green-stable.log`, `stair-start-mca-navigation-controls.log`.

Builder pins under `access-execution/mca-stair-start/`:
NeoForge SHA256f3f5adb2f5b4c851f4fc6d636a5176659a047bcb51dfd49f5b699fb0c8067dd0;
Fabric058e8ab562149b752b06140cba557d1bd1a65243dcdf3e248ef590502139a8b3.
The new evaluator entry equals the compiled common owner byte-for-byte. Compared
with the preceding door pin, it is the only changed navigation class;14other
changed entries belong to the separately authorized family-tree commit062781254.
Resources and archive entry sets match. This is documented rather than presented
as a whole-artifact one-variable comparison. Builder integration/house results
belong in its `2026-10-15-121-stair-start-node.md` report. Full live-stall reliability
remains separate from the demonstrated start-selection regression.
