# Partial-floor headroom and waypoint arrival on 1.21.1

Builder's captured Snowy408 restoration failure held its repair actor on three snow layers, with feet0.25 above the lower doorway floor and body height1.8366. Native search reported a complete route under a two-block lintel. Horizontal motion collided before the actor could leave its support and descend; four peers waited for the repair. No Builder-specific movement behavior is needed to reproduce the defect.

## Two causal boundaries

- Native RED80ec87dff: future snow nodes offered an obstructed outgoing edge. The existing raised-body sweep ran only at the search's start node. Extending it to planned partial surfaces rejects the edge while retaining a clear side departure.
- The first edge-only experiment still failed physical travel and was reverted. A trace showed a valid detour selected, followed by width-based waypoint advancement while the body's footprint still overlapped snow. An injected copy of the observed route isolates this following defect, retaining actual native ticking/movement and asserting physical arrival.
- Checkpointeb02c114b applies the existing partial-floor resolver/sweep at planned nodes and retains the lower waypoint when turning early would collide with the following lower space. Existing native movement completes the descent; no velocity/controller, retry timer, centering rule or new state is introduced.
- Review negativea3b151407 adds snow2 beneath the doorway. Its floor0.125 is above the detour floor0 but below actual feet0.25. This physically fails while a genuinely higher following-step positive passes. Correctionaba69ea96 compares the following surface with actual feet, preserving the positive.

All original assertions/timeouts remain. Temporary route logging was removed after diagnosis. Native following still owns climb precedence, final-node behavior and stuck detection. The bounded endpoint collision check is not a complete swept-transition guarantee.

## Owners and review

Minecraft1.21.1 `WalkNodeEvaluator` labels integer nodes; `PathNavigation.followThePath` advances with width tolerance or direction-based corner cutting. MCA owns supported partial-floor resolution, its existing raised-start sweep, and the native navigator override. MineColonies `getSmartGroundY` also resolves the occupied cell's collision surface before the lower support. Baritone1.21.4 accounts for snow depth/overhead clearance in `MovementHelper`; its player executor is not appropriate for MCA villagers. Builder retains work, reservations and temporary-world decisions.

Four-lens read-only review found the actual-height boundary gap, then no further actionable finding after its regression/correction. Existing APIs and state were reused. Ordinary level movement short-circuits before projected-body collision checking, but the planner now performs additional floor reads; no measured performance improvement is claimed.

## Verification and integration

On unchanged final sourceaba69ea96, JDK25 with release21:589commonJUnit,16physical-safety GameTests,8evaluator-lookup GameTests,49ground-navigation GameTests and both1.21.1 loader builds pass. Evidence under Builder's ignored `.superpowers/sdd/2026-10-16-live-scaffold/`: `mca-partial-floor-final-physical.log`, `mca-partial-floor-final-lookup.log`, `mca-partial-floor-final-navigation.log`. Earlier RED/rejected experiments remain there.

Frozen integration jars in `mca-partial-floor/`: NeoForgeSHA256D3F46C127155407DA9A3A7B62C02DFC239FF8055999A770BF5394554C10A55D4; FabricSHA256A69E24CADC9879B1C70E1324384F1F499DFE4B780D23C8D43E75648948704D13. Source SHA256: evaluator039113D68477F07541026DA02EA7D33F84421D7739C64C954D56AB0BC7EF9D30; navigator5A32E5E7DB87927EB6C8A46D1800661AFCC578005A3A3F4B9691876B426CC1A8.

Builder productiona317b698 plus this pair passes979Neoordinary(1existing skip)/17Minecraft-aware/979Fabricordinary(1skip), then all85construction controls in run182831358-4159eaf7. Same gate completes Snowy408/15524ticks/32supports/maxgap32; Butcher382/13831ticks/22supports/maxgap27; Plains334/11522ticks/27supports/maxgap42. Each includes repair, evacuation, temporary removal, registration and final-world assertions. Remaining-house/repeat/visual acceptance are not established by those passes. Only owned navigation/test/report paths were committed; unrelated sibling staged and unstaged work remains untouched.
