# Layered snow movement targets on 1.21.1

Based on MCA8c20758a4. Implementation/builds/tests are1.21.1 only;26.3 source comparison
is read-only. Builder selects work/destinations; native MCA owns physical navigation.

## Cause and references

The Builder full Snowy408 run20261015-194025292-1ec35a2d removes49/49supports, then
holds two standby bodies at the eastern project edge until the unchanged90000tick
outer limit. Their first raised target is snow4 over a full snowblock. Start snow5
supports feetY+0.5; next support isY+1.375; native move control is toldY+1.0. The
understated0.5rise does not trigger the jump required for the actual0.875rise.

MineColonies local checkout targets1.21.1. SurfaceType treats snow layers>1 as walkable
support; AbstractPathJob treats the single layer as passable. Its decisive navigation
method getSmartGroundY checks collision inside the next cell, then below, excluding
interactive panels. Vanilla PathNavigation.getGroundY checks only below. MCA1.21.1
and read-only26.3 MCAGroundPathNavigation inherit that below-only fallback.

MCAWalkNodeEvaluator already models a pathfindable partial block's collision surface,
requiring support under the complete body footprint. Native MoveControl uses the target
height to decide whether to jump. Baritone MovementHelper separately uses layer depth
for passability/headroom; its custom movement executor is not appropriate to copy into MCA.

## Regression and correction

New layeredSnowStepUsesItsPhysicalSurface builds a narrow actual snow corridor in an
entity-ticking fixture, admits a native complete path and requires physical ascent within
80ticks. mca-layered-snow-red.log is genuine physical RED, with actualY+0.5/wantedY+1.
No Builder scheduling, forced velocity, tick acceleration, timeout change or fake world.

Share the existing supported-surface computation between MCA evaluator and navigator.
Keep native below-floor fallback, pathfindability, full footprint support, climb ownership
and collision checks. No snow type branch, new state/cache/timer/controller is needed.

The first context-free shared calculation made horizontalpathwalksthroughscaffolding
fail in the49navigation controls despite passing all10safety tests. That experiment
was reverted before refinement. ScaffoldingBlock.getCollisionShape explicitly depends
on CollisionContext.isAbove/isDescending. The shared helper now queries with the actual
entity collision context, retaining native pass-through scaffolding behavior. Preserve
mca-surface-context-free-rejected.patch and mca-layered-snow-navigation-controls.log as
rejected evidence; do not count that run as green.

Current corrected-context verification passes589commonJUnit, both loader builds,
all49ground-navigation controls and all10physical-safety tests including actual snow
ascent and ordinary scaffolding traversal. Logs mca-layered-snow-context-navigation.log
and mca-layered-snow-context-safety.log. Fixed three-Java-file review SHA256
61DD6C05202173ECB31407B70B54E82C5A25357B769BC2EB160BE8CBCB1D52EC.

Reuse: one existing supported-floor calculation serves search and movement. Quality:
no new lifecycle state or helper class. Correctness: actual entity collision context,
native fallbacks, full footprint and climb ownership are retained. Efficiency: bounded
shape queries on the owner thread, no added searches/caches. Native owners were checked
again after the context-free attempt failed. No Java code changed after this fixed review.

Frozen rebuilt MCA jars under Builder mca-layered-snow/ differ from preceding bed-ceiling
pins in exactly MCAWalkNodeEvaluator.class and MCAGroundPathNavigation.class. Resources
and archive entry sets match. NeoForgeSHA256
f9fdeb9f4abe462d043e872b1ad68b0b41bc8cee2ebae0a24ea0a2e1b0a9ffa3;
FabricSHA256 087c719fcbf05833a320740f0548aa2a1bd86f827061018368df7a501d8180f4.
Builder integration remains pending. Logs/patches live
under Builder .superpowers/sdd/2026-10-15-access-execution/postrepair-live/. Preserve unrelated
staged/dirty MCA files. Review covers only evaluator, navigator, new regression and this
report; use explicit-path commits. No production-readiness claim from isolated tests.
