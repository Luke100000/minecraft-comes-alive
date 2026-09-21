# Archer Threat-Aware Movement and Aiming Design

Date: 2026-09-20

Status: approved for specification after the user accepted the bounded combat design and requested a written spec/plan.

Target: current `1.21.1-floor-clean-squash` worktree.

## Goal

Make MCA archers behave coherently around multiple hostiles, corners, and low-height targets without replacing vanilla/MCA navigation.

The observed regressions are separate but related:

- a distant attack target and a different nearby hostile can make the archer alternate `APPROACH -> EMERGENCY_FLEE -> APPROACH`;
- ordinary nearby hostiles currently have enough authority to steer retreat even when they are not engaging the archer;
- direct `APPROACH` can path toward the target through another hostile's personal space because the pathfinder knows physical collision, not tactical enemy clearance;
- strafe-side validation can approve a side at a corner and then immediately stop for collision;
- the current bow trajectory uses a custom quadratic gravity approximation that is not the vanilla/MineColonies aiming model and can visibly drop shots under targets.

The desired behavior is:

`dangerously close / actively engaging threat -> create separation`

`distant target with other enemies nearby -> advance through a safe local waypoint around them`

`good firing position -> hold / short safe strafe / fire`

`low or elevated target -> aim at the target body and use a predictable distance lift`

## Source-grounded conclusions

### Vanilla avoidance is a reference, not the implementation

Vanilla 1.21.1 `AvoidEntityGoal` and `SetWalkTargetAwayFrom` each reason about one entity at a time. They use `DefaultRandomPos` / `LandRandomPos` to choose a reachable point away from that entity and then let normal navigation own the path.

That ownership model is correct for MCA, but the single-threat model is insufficient for the logged multi-skeleton case. MCA already has a bounded reachable-space search that can reason about several hostiles and cave topology; retain it instead of replacing it with vanilla random-away logic.

### MineColonies confirms target-aware retreat and body-height-aware aiming

MineColonies `RangeCombatAI` retreats from its current combat target rather than treating every nearby hostile as an equal flee trigger. Its `CombatUtils.shootArrow` aims at the target bounding-box midpoint and adds a linear distance lift. It also scales projectile speed with distance.

MCA should reuse the useful principles, not port MineColonies combat/pathfinding:

- distinguish the enemy being fought from incidental nearby hostiles;
- aim using the target's body height;
- preserve body-height-aware aiming while using a deterministic trajectory correction appropriate to MCA's fixed projectile speed;
- leave route execution to the existing MCA/vanilla Brain navigation stack.

Do not copy MineColonies' variable arrow speed. MCA keeps its existing bow speed `1.6F` and existing inaccuracy `3` so this change corrects aim without changing weapon feel or balance.

### Vanilla skeleton trajectory is a reference, not a drop-in midpoint formula

Vanilla 1.21.1 `AbstractSkeleton.performRangedAttack` fires at speed `1.6F`, aims at roughly one third of target height, and adds `horizontalDistance * 0.2` to its vertical component. Live MCA validation showed that reusing the same `0.2` lift while moving the aim point up to the target midpoint over-lifts shots, especially against low-bodied targets.

MCA therefore keeps the useful vanilla invariants—speed `1.6F` and the target's actual body height—but solves the vertical launch component against the same arrow flight model that executes in 1.21.1: `0.99` air drag and `0.05` gravity per tick. The target point remains the body midpoint and inaccuracy remains `3.0F`.

The helper stays pure and deterministic: it receives projectile position, target base position, and target height, then finds the vertical aim component whose simulated flight intersects the target midpoint at the requested horizontal distance. This removes the previous ad-hoc quadratic compensation without introducing mob-specific branches.

## Threat model: escape drivers versus movement hazards

Nearby hostiles have two different tactical roles. Do not collapse them back into one list.

### Movement hazards

A movement hazard is a nearby valid guard enemy whose position should influence where the archer walks.

Build the hazard set from `NEAREST_LIVING_ENTITIES` using the existing valid-attack-target and guard-enemy predicates, the existing `2.5`-block vertical band, and line of sight. Include the current attack target as a fallback when it is within the existing 16-block nearby-threat range even if it is temporarily occluded.

Movement hazards do **not** automatically force `KITE` or `EMERGENCY_FLEE`. Their purpose is spatial: approach, reposition, retreat destination selection, and strafing should avoid walking unnecessarily close to them.

### Escape drivers

An escape driver is a movement hazard that is allowed to control the close-range tactical state.

A valid hostile is an escape driver when any of these are true:

- it is the current `ATTACK_TARGET`;
- it is a `Mob` whose current target is this archer;
- it is `archer.getLastHurtByMob()`; vanilla already expires this reference after 100 ticks, so do not add another recent-hit timer;
- it is inside the emergency-enter distance, squared distance `< 12.25`, even if it has not yet selected the archer.

The last rule prevents an archer from blindly walking through a hostile that is already within roughly 3.5 blocks just because that mob has not updated its own target field yet.

`nearestMovementThreat(...)` becomes the nearest visible/relevant **escape driver**, with the current attack target retained as the fallback. Group retreat uses the full nearby escape-driver set.

## State precedence and hysteresis

Keep the existing thresholds:

- emergency enter: squared distance `< 12.25` (3.5 blocks);
- emergency exit: squared distance `< 25.0` (remain emergency until 5 blocks);
- kite enter: squared distance `< 36.0` (6 blocks);
- kite exit: squared distance `< 81.0` (remain kite until 9 blocks);
- close-threat vertical band: `<= 2.5` blocks;
- emergency desired separation: 6 blocks;
- kite desired separation: 9 blocks;
- emergency speed: `1.2`;
- ordinary combat movement speed: `0.5`.

Change state precedence to:

1. emergency hysteresis / emergency entry;
2. kite hysteresis / kite entry for a relevant escape driver;
3. out-of-range `APPROACH`;
4. sustained lost-LOS `REPOSITION`;
5. approach hysteresis;
6. `HOLD`.

The important correction is that a close relevant threat must be resolved before `APPROACH`. An archer leaving emergency range at about 5 blocks must remain in `KITE` until roughly 9 blocks instead of immediately walking back toward a distant target.

Close-range safety also beats LOS repositioning. If a relevant threat is inside the kite band, creating spacing is more important than finding a new firing angle.

## Retreat destination selection

Keep the existing bounded reachable-space search and its onward-space preference. Do not return to radial random candidates and do not add a second pathfinder.

Retreat selection receives two collections:

- **escape drivers** determine whether a candidate actually opens useful separation;
- **movement hazards** constrain the route so the archer does not flee directly into another hostile that is not itself driving the flee state.

For each hazard, preserve a tactical clearance floor:

```text
required candidate distance = min(current hazard distance, 6 blocks)
```

Therefore:

- if the archer is already at least 6 blocks from a passive hostile, a retreat candidate must not enter that hostile's 6-block bubble;
- if the archer is already inside 6 blocks, the candidate must not move closer to that hazard;
- escape-driver distance still determines emergency/kite improvement and desired-distance success.

For normal `KITE`, prefer a candidate that improves separation from all escape drivers while preserving hazard clearance. If no such candidate exists, hold/retry rather than selecting a route that obviously cuts through a passive hostile.

For `EMERGENCY_FLEE`, survival has priority. Prefer hazard-safe candidates first; if none exists, choose the reachable candidate with the greatest improvement from the escape drivers, using minimum hazard distance as a tie-breaker rather than refusing to move.

The prior cave-pocket/onward-space behavior remains intact.

## Threat-aware approach steering

Normal Minecraft pathfinding avoids physical collisions, not tactical proximity to enemies. A direct `WalkTarget(new EntityTracker(target, false), ...)` can therefore send the archer through another skeleton's danger zone.

Do not replace `MoveToTargetSink` or `PathNavigation`. Add only a short-range tactical steering layer:

- when no secondary movement hazard is relevant, preserve the ordinary direct `EntityTracker` approach;
- when one or more secondary hazards are nearby, probe a small fixed set of short forward/lateral waypoints around the archer rather than running the retreat graph again;
- candidate waypoints must be stable, walkable, collision-clear, and preserve the 6-block hazard clearance rule above;
- the straight segment to a candidate must also preserve hazard clearance so a locally safe endpoint cannot imply cutting through a hostile;
- allow at most 0.5 block of temporary backtracking relative to the attack target so a lateral detour around a hostile is possible;
- among valid candidates, prefer smaller target distance, then greater minimum hazard distance;
- publish the chosen position as `WALK_TARGET`; vanilla/MCA navigation still computes and executes the path;
- recalculate only through the existing combat walk-target lifecycle/retry cadence, not every tick.

If secondary hazards exist but no tactically safe waypoint is available, do not fall back to a direct path through them. Clear the combat walk target, hold, and use the existing bounded retry cadence. A later enemy movement or path-state change can make a safe waypoint available.

This is local steering, not squad AI, influence-map pathfinding, or a custom node evaluator.

## Reposition safety

`REPOSITION` remains a firing-position search, but validate candidates against the full movement-hazard set rather than only one `movementThreat`.

A firing position must:

- satisfy the existing stable/walkable/standing-space checks;
- remain in weapon range and satisfy LOS;
- not move into the 6-block bubble of a hazard that is currently outside it;
- not move closer to a hazard when already inside that bubble;
- retain the existing bounded candidate count and path-ownership rules.

If no safe firing position exists, use the same hazard-aware approach waypoint logic rather than an unconditional direct approach fallback.

## Corner-safe strafing

The current one-block endpoint check can approve a strafe that immediately collides because the mob's body sweeps through a corner before reaching the sampled endpoint.

Replace random-first side selection with evaluation of both sides.

For each lateral direction:

- probe the actual entity bounding box every `0.5` block out to `2.5` blocks along the local lateral vector;
- every probe must pass `level.noCollision(entity, movedBoundingBox)` plus the existing walkable-destination / standing-space validation;
- stop scoring that side at the first failed probe;
- reject a side that cannot provide at least `1.5` blocks of clear lateral travel, so a burst is not started toward a wall that sits just beyond the old one-block endpoint probe;
- score the side first by clear lateral distance, then by minimum distance to movement hazards at the furthest safe probe;
- if both sides are exactly tied, use a stable UUID-bit tie-breaker rather than a new random retry loop.

No valid side means remain in `HOLD` and begin the normal strafe cooldown. Collision during an active burst still ends the burst; it never reverses direction in-place.

## Bow trajectory

Replace only the bow vertical-vector calculation. Crossbow shooting is unchanged.

Keep:

- target body midpoint: `target.getY(0.5D)`;
- projectile speed: `1.6F`;
- inaccuracy: `3.0F`;
- existing arrow creation, enchantment handling, sound, and spawn behavior.

Replace:

```java
flightTicks = horizontalDistance / 1.6
gravityCompensation = 0.025 * flightTicks * max(0, flightTicks - 1)
```

with a pure midpoint trajectory solve using the retained `1.6` projectile speed and Minecraft 1.21.1 arrow flight constants (`0.99` air drag, `0.05` gravity).

Expose the vector calculation as one small pure helper on `RangedWeaponHelper` so the level/uphill/downhill/low-body cases can be tested without relying on random projectile inaccuracy.

## File responsibilities

Expected production changes stay narrow:

- `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/RangedCombatPositioning.java` — hazard/escape-driver classification and stateless tactical candidate scoring;
- `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementTask.java` — state precedence, threat-aware `WALK_TARGET` publication, and bounded strafe state;
- `common/src/main/java/net/conczin/mca/entity/ai/RangedWeaponHelper.java` — pure bow shot-vector calculation;
- `common/src/main/java/net/conczin/mca/entity/VillagerEntityMCA.java` — consume the helper for bow firing.

Expected tests:

- `common/src/test/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementStateTest.java` — state precedence/hysteresis;
- `common/src/test/java/net/conczin/mca/entity/ai/RangedWeaponHelperTest.java` — deterministic bow-vector math;
- `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherCombatMovementGameTests.java` — multi-enemy threat classification, approach steering, and corner strafe integration;
- `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherSpiderCombatGameTests.java` — preserve spider/cave-spider and cave-escape regressions;
- optionally create `ArcherBowTrajectoryGameTests.java` only if pure-vector coverage plus the existing spider tests do not adequately exercise live projectile behavior.

Do not modify the generic navigation stack, `MCAGroundPathNavigation`, `MoveToTargetSink`, or the existing cave reachable-space semantics as part of this feature.

## Required regression scenarios

1. A distant attack target plus a nearby **non-engaging** skeleton does not cause `APPROACH <-> EMERGENCY_FLEE` oscillation when the secondary skeleton remains outside emergency distance.
2. The same secondary skeleton, after `setTarget(archer)`, becomes an escape driver and causes kite/emergency spacing at the existing thresholds.
3. Any valid hostile inside 3.5 blocks can trigger emergency spacing even if it has not selected the archer yet.
4. An archer leaving emergency range does not resume `APPROACH` until the relevant close threat exits the 9-block kite hysteresis band.
5. An out-of-range archer routes a short approach waypoint around a secondary hostile instead of publishing a direct route through its 6-block bubble.
6. Two hostiles on different sides do not make ordinary retreat choose a destination that closes dangerously on the passive hazard.
7. An emergency escape still makes progress when no candidate can satisfy every passive-hazard clearance constraint.
8. A corner fixture where the old endpoint probe appeared open but the body sweep clips the corner rejects that strafe side before movement starts.
9. When both strafe sides are clear, the side with more lateral clearance / hostile spacing is selected; an exact tie is stable rather than randomly retrying.
10. Bow vector math uses the target midpoint and the 1.21.1 arrow flight model at level, uphill, downhill, spider-height, and cave-spider-height targets; deterministic live arrows also hit level / +2 / -2-block skeleton fixtures.
11. Existing spider and cave-spider bow-kill tests remain green.
12. Existing cave-corner, blocked-far-side, and one-entry-pocket escape tests remain green.

## Out of scope

- custom A* costs or a hostile-aware replacement node evaluator;
- squad formations, cover selection, focus-fire coordination, or predictive dodging;
- changing guard target priority or attack-target selection;
- changing bow damage, cooldown, projectile speed, or inaccuracy;
- changing crossbow trajectory;
- replacing the reachable cave-escape graph added by prior work;
- generic navigation/climbing/door/pathfinding refactors;
- unrelated cleanup in the already-dirty worktree.

The intended result is still a small tactical layer over existing Brain navigation: threats decide **why** the archer should move, hazard-aware local candidates decide **where the next short waypoint should be**, and the existing navigation stack decides **how to get there**.
