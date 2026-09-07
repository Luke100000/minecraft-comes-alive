# Archer Combat Movement Simplification Design

Date: 2026-09-07

Status: approved for specification by the user's agreement with the proposed MineColonies-style movement simplification.

Target: `feature/1.21.1-floor-clean-squash`

## Goal

Make MCA archers move deliberately instead of continuously oscillating left and right. Preserve the useful close-range kite/flee hysteresis, weapon-range support, guard targeting, and MCA navigation behavior while removing the duplicated strafe controller and direct path ownership from archer combat code.

The desired combat loop is:

`too close -> move away`

`too far -> approach`

`in range but sustained bad line of sight -> reposition to a firing position`

`good range + line of sight -> hold, aim, fire`

There is no normal continuous orbit/side-strafe state.

## Source-grounded review conclusions

This design applies the Java cleanup review lenses to the fixed review scope of `ArcherMovementTask` and `ArcherMoveControl`, with nearby MCA, vanilla 1.21.1, and MineColonies code used only as owner/reference context.

### Reuse

- Vanilla 1.21.1 `RangedBowAttackGoal` already contains the 20-visible-tick transition into strafing and the 20-tick random clockwise/backwards toggles that MCA resembles. That code is useful evidence for where the current pattern came from, but it is not the behavior MCA wants to preserve.
- Vanilla/MCA Brain movement already has the correct locomotion ownership chain: combat behavior publishes `WALK_TARGET`; `MoveToTargetSink` computes/owns `PATH`; `PathNavigation` and `MoveControl` execute locomotion. Archer combat must reuse that chain instead of calling `navigation.moveTo(...)`, `createPath(...)`, or `moveControl.strafe(...)` itself.
- Keep `RangedWeaponHelper` as MCA's canonical ranged-weapon/range abstraction. Vanilla `BehaviorUtils.isWithinAttackRange` and `SetWalkTargetFromAttackTargetIfTargetOutOfReach` are not used wholesale because vanilla's predicate is main-hand based while MCA intentionally supports its selected bow/crossbow hand and MCA weapon semantics.
- Keep the existing nearest-visible-enemy search for close-range movement threats. `MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY` is intentionally not reused for this decision because `GuardEnemiesSensor` ranks target priority before distance; kiting needs the physically nearest valid visible danger.
- MineColonies is the behavioral reference for positioning: ranged guards move to obtain attack range/line of sight, hold once positioned, and move away when dangerously close. MCA should adapt that principle to its Brain/vanilla navigation architecture rather than port MineColonies pathfinding.

### Quality

- `ArcherMovementTask` currently owns tactical state, path candidate search, path creation, navigation start/stop, strafe timing, collision reversal, look control, and debug output. The redesigned task owns tactical intent only.
- `ArcherMoveControl` duplicates vanilla `MoveControl` strafe math and adds archer-only request/result state. Remove that parallel controller rather than tuning more timers into it.
- Use one canonical ranged-combat state shared by movement and weapon behaviors instead of storing emergency tactical state inside `MoveControl`.
- Keep candidate selection in one small stateless positioning helper so `ArcherMovementTask` remains readable and navigation remains owned by the Brain pipeline.

### Correctness

- Current sideways movement has competing direction owners: `ArcherMovementTask` flips `strafingClockwise`, while `ArcherMoveControl` can silently reverse `strafeRight`. The redesign has no duplicated lateral-direction state.
- A horizontal collision can currently reverse the task direction independently of blocked-strafe accounting, creating rapid left/right feedback. Removing continuous strafing removes this failure mode instead of adding another hysteresis timer.
- Archer combat currently erases `WALK_TARGET`/`CANT_REACH_WALK_TARGET_SINCE` and starts/stops navigation itself. The redesign leaves `CANT_REACH_WALK_TARGET_SINCE`, `PATH`, path retries, obstacle traversal, doors, jumping, climbing, and ordinary navigation lifecycle with vanilla/MCA owners.
- Bow emergency suppression and crossbow behavior must read the same canonical combat state. They must not infer tactical state independently or depend on the move-control implementation.

### Efficiency

- Remove the hot combat loop that can call `createPath(...)` for up to ten away candidates every 10-16 ticks per archer.
- Position selection may sample bounded geometric candidates, but it does not create paths. It publishes one chosen `WalkTarget`; `MoveToTargetSink` performs the path computation once.
- Reposition/retreat candidates are recomputed on state entry, target/candidate invalidation, or bounded retry—not every tick while the current `WALK_TARGET` remains useful.

## Canonical ranged-combat state

Add a runtime-only MCA Brain memory for the current ranged tactical state. It has no codec and is not persisted.

States:

- `HOLD`
- `APPROACH`
- `REPOSITION`
- `KITE`
- `EMERGENCY_FLEE`

`ArcherMovementTask` is the only writer while ranged combat is active. `BowTask`, `ExtendedCrossbowAttackTask`, debug output, and tests may read it. Do not retain a second private `MovementState` field or an `emergencyFleeing` flag in a move controller.

Register the memory through the existing `MemoryModuleTypeMCA` and `VillagerTasksMCA.MEMORY_TYPES` mechanisms.

The memory is erased when ranged movement stops or the villager no longer has a valid ranged combat target.

## State selection

State precedence is deterministic and evaluated from the attack target, the physically nearest valid visible movement threat, line of sight, the current ranged-combat state, and `RangedWeaponHelper` attack range.

### Emergency flee

Preserve the current hysteresis:

- enter below squared distance `12.25` (3.5 blocks)
- remain until squared distance reaches `25.0` (5 blocks)
- apply only when vertical threat distance is at most `2.5` blocks
- desired retreat distance remains 6 blocks
- speed modifier remains `0.9`

`EMERGENCY_FLEE` suppresses bow use and crossbow charging/firing until the state exits.

### Kite

Preserve the current hysteresis:

- enter below squared distance `36.0` (6 blocks)
- remain until squared distance reaches `81.0` (9 blocks)
- an archer leaving `EMERGENCY_FLEE` inside the kite exit band transitions to `KITE`, not directly to `HOLD`
- apply only when vertical threat distance is at most `2.5` blocks
- desired retreat distance remains 9 blocks
- speed modifier remains `0.85`

Archers may continue aiming/firing during `KITE` when their weapon behavior's normal range and visibility rules permit it.

### Approach

If the attack target is outside `RangedWeaponHelper`'s effective attack range, use `APPROACH` regardless of the ordinary firing-position state.

`APPROACH` publishes a `WalkTarget` toward the attack target at the existing `0.5` speed modifier. It does not call navigation directly.

### Reposition

Transient line-of-sight loss does not immediately move the archer. Preserve a 10-tick loss-of-sight grace period to avoid reacting to a one-frame obstruction or target edge crossing.

If the target is within weapon range but remains unseen beyond that grace period, enter `REPOSITION`.

`REPOSITION` asks the positioning helper for a nearby firing position. A valid firing position must:

- be a stable/pathfindable destination for the villager's current navigation type;
- provide unobstructed block-collider line of sight from the candidate eye position to the target eye position;
- remain within the effective ranged-weapon attack distance;
- not place the archer inside the current kite-enter distance of the nearest visible movement threat;
- be chosen by shortest travel distance among the bounded valid candidates so archers do not wander merely to find a different firing angle.

Sample at most eight nearby candidates within 8 horizontal and 4 vertical blocks of the archer. Candidate evaluation is geometric only; do not call `createPath` while evaluating candidates.

If no firing candidate is found, publish an ordinary approach `WalkTarget` toward the attack target and let close-range `KITE`/`EMERGENCY_FLEE` precedence prevent suicidal convergence. Do not add a second pathfinder, unreachable-position cache, or failure ladder.

### Hold

If the archer is in effective weapon range, has line of sight, and is outside close-range kite/flee bands, use `HOLD`.

`HOLD` is the normal stable firing state. The archer faces the target and does not move merely because it has seen the target for 20 ticks.

There is no `SIDE_STRAFE` state and no periodic random movement-direction toggle.

## Movement ownership

`ArcherMovementTask` publishes intent through Brain memories and does not execute locomotion.

Rules:

- no `navigation.moveTo(...)` from ranged-combat movement;
- no combat-owned `navigation.createPath(...)`;
- no per-tick `navigation.stop()`;
- no `moveControl.strafe(...)` or archer-specific strafe request;
- no per-tick erase of `CANT_REACH_WALK_TARGET_SINCE`;
- do not mutate `PATH` directly;
- publish/replace `WALK_TARGET` only when entering a movement state, changing target/destination, or retrying after the existing Brain/navigation path lifecycle invalidates the prior target;
- entering `HOLD` clears stale combat walking intent once, then remains stationary without repeatedly erasing Brain movement state every tick;
- existing panic, safety, swimming, interaction, and higher-priority activity ownership must continue to preempt guard combat movement through the existing Brain scheduling model.

`MoveToTargetSink`, MCA's existing mixin/extensions, `PathNavigation`, and `MCAMoveControl` remain responsible for route computation and physical movement.

## Positioning helper

Introduce one small stateless helper, `RangedCombatPositioning`, for candidate selection only.

Responsibilities:

- select the physically nearest visible valid movement threat using the existing guard-enemy predicate;
- choose an away candidate using `LandRandomPos.getPosAway(...)` without path creation;
- choose a bounded nearby firing candidate for `REPOSITION` using world collision/line-of-sight checks;
- return positions/results only; it owns no timers, navigation, Brain memories, or entity mutation.

The helper must not become a MineColonies-style custom pathfinder. MineColonies `PathJobCanSee` is a behavioral reference; MCA's actual path ownership remains vanilla/MCA `MoveToTargetSink`.

## Move-control simplification

Remove archer tactical behavior from `ArcherMoveControl`:

- remove `archerStrafeRequested`;
- remove `StrafeResult` and its debug/result API;
- remove the overridden archer strafe execution and duplicated walkability test;
- remove `emergencyFleeing` from move control.

After those responsibilities are removed, `ArcherMoveControl` is an unnecessary wrapper. Delete it and make `MCAMoveControl` directly usable by `VillagerEntityMCA` (adjust visibility/constructor visibility as narrowly as required). Update `VillagerEntityMCA` to install `MCAMoveControl` directly and remove `getArcherMoveControl()`.

Do not alter `MCAMoveControl`'s existing navigation/climb/jump behavior as part of this work.

## Weapon behavior coordination

`BowTask` and `ExtendedCrossbowAttackTask` read the canonical ranged-combat state.

- In `EMERGENCY_FLEE`, stop any active bow draw or crossbow charge and do not begin another attack cycle.
- In `KITE`, `HOLD`, `REPOSITION`, or `APPROACH`, weapon behavior continues to use its existing target validity, line-of-sight, draw/charge, cooldown, and effective-range checks. `REPOSITION`/`APPROACH` naturally cannot fire when visibility/range conditions fail.
- Do not duplicate distance hysteresis in weapon tasks.
- Do not derive attack suppression from move-controller type/state.

This makes bow and crossbow emergency behavior consistent while keeping weapon mechanics separate from movement mechanics.

## Expected production scope

Primary files expected to change during implementation:

- `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementTask.java`
- `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/BowTask.java`
- `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ExtendedCrossbowAttackTask.java`
- `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/RangedCombatPositioning.java` (new)
- `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/RangedCombatState.java` (new runtime-state enum)
- `common/src/main/java/net/conczin/mca/entity/ai/MemoryModuleTypeMCA.java`
- `common/src/main/java/net/conczin/mca/entity/ai/brain/VillagerTasksMCA.java`
- `common/src/main/java/net/conczin/mca/entity/ai/MCAMoveControl.java`
- `common/src/main/java/net/conczin/mca/entity/ai/ArcherMoveControl.java` (delete)
- `common/src/main/java/net/conczin/mca/entity/VillagerEntityMCA.java`

Nearby code may be read for integration context, but unrelated floor, Blueprint, builder, and navigation behavior is out of scope.

## Verification

Tests must prove observable combat behavior rather than merely asserting that classes, constants, or memories exist.

Required behavior coverage:

1. An archer with a stationary visible target in a clear firing lane reaches `HOLD`, remains positionally stable through multiple attack cycles, and does not oscillate left/right.
2. Brief line-of-sight loss below the grace period does not start movement; sustained in-range LOS loss enters `REPOSITION` and produces one Brain-owned walking intent toward a firing candidate/fallback.
3. A target moving across the 6/9-block kite band does not cause per-tick state ping-pong; the current hysteresis is preserved.
4. A target entering the 3.5/5-block emergency band causes escape movement, and both bow and crossbow attack cycles remain suppressed until emergency exit.
5. With multiple valid visible enemies, retreat responds to the physically nearest close threat rather than a farther higher-priority attack target.
6. Combat movement publishes `WALK_TARGET` and allows `MoveToTargetSink`/navigation to own `PATH`; obstacle traversal, doors, jumping, and MCA climb behavior are not replaced by custom archer locomotion.
7. A blocked/unreachable reposition candidate does not trigger repeated multi-path searches in the archer task; Brain navigation invalidation/retry leads to a bounded new candidate or approach fallback.
8. Target loss, weapon removal, panic/safety preemption, and combat behavior stop cleanly release ranged-combat state and stale combat movement intent.

Where pure state selection can be tested without world behavior, use focused unit coverage. Movement ownership, LOS repositioning, obstacle traversal, and visible anti-oscillation behavior require an actual server/GameTest or equivalent live integration scenario; do not claim them from compilation alone.

Automated final gate after implementation:

`./gradlew :common:test :fabric:compileJava :neoforge:compileJava`

plus `git diff --check` and the focused combat runtime scenarios above.

The implementation report must state separately whether an in-game/live movement check was performed. Gradle success is not evidence that the visible left/right oscillation is gone.

## Out of scope

- Porting MineColonies navigation or `PathJobCanSee`.
- Replacing MCA guard target selection/threat priorities.
- Reworking bow/crossbow damage, cooldown, accuracy, equipment, or projectile logic beyond emergency-state coordination.
- Changing generic MCA climb/jump/navigation semantics.
- Adding squad tactics, cover systems, formations, predictive dodging, suppression, or player-like combat strafing.
- Tuning unrelated melee guard movement.

The intended result is deliberately smaller than the current archer movement implementation: one tactical state owner, one stateless position selector, and the existing Brain/navigation stack doing the actual movement.
