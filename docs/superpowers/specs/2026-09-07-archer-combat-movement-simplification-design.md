# Archer Combat Movement Simplification Design

Date: 2026-09-07

Status: approved for specification by the user's agreement with the proposed MineColonies-style movement simplification.

Target: `feature/1.21.1-floor-clean-squash`

## Goal

Make MCA archers move deliberately instead of continuously oscillating left and right. Preserve a restrained amount of skeleton-style strafing, the useful close-range kite/flee hysteresis, weapon-range support, guard targeting, and MCA navigation behavior while removing the duplicated strafe controller and direct path ownership from archer combat code.

The reported regression is specifically the old one-block shimmy: an archer can alternate left/right movement repeatedly while making little or no net lateral progress, effectively chattering around the same block instead of committing to a movement decision. This is the primary anti-oscillation behavior the implementation must eliminate.

The desired combat loop is:

`too close -> move away`

`too far -> approach`

`in range but sustained bad line of sight -> reposition to a firing position`

`good range + line of sight -> hold, aim, fire, with occasional short lateral strafe bursts`

There is no continuous orbit. Strafing is a bounded tactical micro-movement between stable firing periods.

## Source-grounded review conclusions

This design applies the Java cleanup review lenses to the fixed review scope of `ArcherMovementTask` and `ArcherMoveControl`, with nearby MCA, vanilla 1.21.1, and MineColonies code used only as owner/reference context.

### Reuse

- Vanilla 1.21.1 `RangedBowAttackGoal` already contains the 20-visible-tick transition into strafing and the 20-tick random clockwise/backwards toggles that MCA resembles. MCA deliberately keeps the recognizable short lateral-strafe feel, but not vanilla's effectively continuous orbit once the target is visible and in range.
- Vanilla/MCA Brain movement already has the correct locomotion ownership chain: combat behavior publishes `WALK_TARGET`; `MoveToTargetSink` computes/owns `PATH`; `PathNavigation` and `MoveControl` execute locomotion. Archer combat must reuse that chain for approach, kite, flee, and reposition. The only direct `MoveControl` command retained is a bounded `STRAFE` burst, matching vanilla's ownership for non-path lateral movement.
- Keep `RangedWeaponHelper` as MCA's canonical ranged-weapon/range abstraction. Vanilla `BehaviorUtils.isWithinAttackRange` and `SetWalkTargetFromAttackTargetIfTargetOutOfReach` are not used wholesale because vanilla's predicate is main-hand based while MCA intentionally supports its selected bow/crossbow hand and MCA weapon semantics.
- Keep the existing nearest-visible-enemy search for close-range movement threats. `MemoryModuleTypeMCA.NEAREST_GUARD_ENEMY` is intentionally not reused for this decision because `GuardEnemiesSensor` ranks target priority before distance; kiting needs the physically nearest valid visible danger.
- MineColonies is the behavioral reference for positioning: ranged guards move to obtain attack range/line of sight, hold once positioned, and move away when dangerously close. MCA should adapt that principle to its Brain/vanilla navigation architecture rather than port MineColonies pathfinding.

### Quality

- `ArcherMovementTask` currently owns tactical state, path candidate search, path creation, navigation start/stop, strafe timing, collision reversal, look control, and debug output. The redesigned task owns tactical intent plus the bounded vanilla strafe micro-input; it does not own path computation or path execution.
- `ArcherMoveControl` duplicates vanilla `MoveControl` strafe math and adds archer-only request/result state. Remove that parallel controller. Short strafing uses ordinary `MCAMoveControl`/vanilla `MoveControl.strafe(...)` with direction and timing owned only by `ArcherMovementTask`. The active-use `KITE` exception delegates to that same vanilla strafe path but restores the canonical `0.85` kite speed modifier afterward, because vanilla `MoveControl.strafe(...)` hard-codes `0.25`.
- Use one canonical ranged-combat state shared by movement and weapon behaviors instead of storing emergency tactical state inside `MoveControl`.
- Keep candidate selection in one small stateless positioning helper so `ArcherMovementTask` remains readable and navigation remains owned by the Brain pipeline.

### Correctness

- Current sideways movement has competing direction owners: `ArcherMovementTask` flips `strafingClockwise`, while `ArcherMoveControl` can silently reverse `strafeRight`. The redesign keeps one lateral-direction owner: `ArcherMovementTask` chooses the burst direction once and never has the move controller silently reverse it.
- A horizontal collision can currently reverse the task direction independently of blocked-strafe accounting, creating rapid left/right feedback. In the redesign, collision or a rejected strafe ends the burst and returns to `HOLD`; it never causes an immediate opposite-direction burst.
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
- `STRAFE`
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

`EMERGENCY_FLEE` suppresses bow use and crossbow charging/firing until the state exits. It is the higher-priority escape state: it prioritizes physically opening distance over aiming, and the body may fully face the escape route. On leaving emergency range while still inside the kite exit band, transition to `KITE` before returning to ordinary ranged positioning.

Emergency escape direction is group-aware. Build the threat set from nearby visible valid guard enemies on the archer's reachable vertical band, including the active attack target when it is nearby. Choose a bounded geometric escape candidate that improves the minimum distance to that threat set, preferring the shortest candidate radius that reaches the 6-block desired safety distance without closing on any member of the group. If no candidate can satisfy every threat, use the valid candidate with the greatest improvement in minimum distance. Candidate evaluation must not create paths.

### Kite

Preserve the current hysteresis:

- enter below squared distance `36.0` (6 blocks)
- remain until squared distance reaches `81.0` (9 blocks)
- an archer leaving `EMERGENCY_FLEE` inside the kite exit band transitions to `KITE`, not directly to `HOLD`
- apply only when vertical threat distance is at most `2.5` blocks
- desired retreat distance remains 9 blocks
- speed modifier remains `0.85`

Archers may continue aiming/firing during `KITE` when their weapon behavior's normal range and visibility rules permit it. While actively drawing/charging a ranged weapon, the archer keeps its body facing the attack target and expresses the immediate retreat direction as player-like forward/lateral strafe input. That local input is validated before use and supplements, rather than replaces, the existing Brain-owned retreat destination.

`KITE` is normal combat spacing, not panic. It begins when a close threat enters the 6-block band and persists until the threat reaches the 9-block exit distance. Outside an active ranged-use cycle, ordinary path movement may turn the body toward the retreat route. During an active bow draw/crossbow charge, target-facing body yaw takes precedence and the immediate retreat step is converted to target-relative strafe input so movement and aiming do not fight over entity yaw.

Kite movement remains single-threat-oriented: retreat from the physically nearest visible movement threat. The 9-block value is a desired spacing/exit distance, not a requirement that the first random `WalkTarget` already land at least 9 blocks from the threat. A valid bounded candidate only needs to open useful distance; state hysteresis keeps `KITE` active until the actual threat distance reaches the exit band.

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

`HOLD` is the normal stable firing state. The archer faces the target and does not immediately begin orbiting merely because it has seen the target for 20 ticks.

After at least 40 consecutive ticks in a valid stable `HOLD`, and only when the strafe cooldown has expired, the archer may begin one short `STRAFE` burst. The next cooldown is randomized between 40 and 80 ticks so groups of archers do not synchronize.

### Strafe

`STRAFE` preserves a small amount of the skeleton-style combat feel without allowing a permanent left/right control loop.

Rules:

- choose left or right once when the burst begins;
- begin the burst only if the positioning helper confirms the chosen lateral side is locally walkable for the current navigation/path type; if the first side is unsafe, try the opposite side once, otherwise remain in `HOLD` and start the cooldown;
- use lateral input only: forward component `0.0`, lateral magnitude `0.35`;
- keep the chosen direction for 8-14 ticks;
- continue facing/aiming at the attack target while strafing;
- bow/crossbow attack behavior may continue normally during the burst;
- immediately end the burst and return to `HOLD` if line of sight is lost, the target leaves effective range, a close threat requires `KITE`/`EMERGENCY_FLEE`, the entity collides horizontally/minor-horizontally, or horizontal motion stalls after the burst has started;
- after any early cancellation, start the normal cooldown before another strafe is allowed;
- never reverse direction inside a burst and never start an opposite-direction burst as a collision response.

`ArcherMovementTask` owns the strafe timer, cooldown, and chosen direction. These are transient execution details, not separate tactical state sources. The canonical Brain state remains `STRAFE` for the duration of the burst.

There is no permanent `SIDE_STRAFE`/orbit mode and no periodic in-burst direction toggle.

The one-block shimmy is explicitly forbidden. Once a strafe burst begins, it has one lateral sign for the whole burst. A collision or stall ends the burst; it does not produce a corrective opposite input. Repeated alternating lateral inputs while the archer remains inside roughly the same one-block lateral envelope are a regression even if the tactical state itself does not change.

## Movement ownership

`ArcherMovementTask` publishes intent through Brain memories and does not execute locomotion.

Rules:

- no `navigation.moveTo(...)` from ranged-combat movement;
- no combat-owned `navigation.createPath(...)`;
- no per-tick `navigation.stop()`;
- `moveControl.strafe(...)` is allowed while the canonical state is `STRAFE`, and as a narrow `KITE` exception while a ranged weapon is actively being used. The `KITE` exception is only a locally validated immediate retreat input that keeps target-facing aim coherent; `WALK_TARGET` remains the owner of the retreat destination and route lifecycle;
- no archer-specific strafe request API, silent redirection, or second strafe controller;
- no per-tick erase of `CANT_REACH_WALK_TARGET_SINCE`;
- do not mutate `PATH` directly;
- publish/replace `WALK_TARGET` only when entering a movement state, changing target/destination, or retrying after the existing Brain/navigation path lifecycle invalidates the prior target;
- entering `HOLD` clears stale combat walking intent once, then remains stationary without repeatedly erasing Brain movement state every tick;
- `WALK_TARGET` owns destination/path intent only; it does not own ranged target look/aim intent;
- during `HOLD`, `STRAFE`, `APPROACH`, and `REPOSITION`, keep the attack target as `LOOK_TARGET` and continue target-directed `LookControl` updates while the movement state is active;
- during `EMERGENCY_FLEE`, movement may fully turn the body toward the escape route. During `KITE`, ordinary path travel may also face the route, but an active ranged-use cycle keeps the body facing the attack target and uses target-relative backward/diagonal movement instead;
- do not fight vanilla path locomotion with a custom per-tick body-yaw override. Vanilla 1.21.1 `MoveControl` rotates entity yaw toward `MOVE_TO`, `BodyRotationControl` aligns the moving body to that yaw, and `LookControl` independently tracks/clamps head aim. Therefore target-facing during path-oriented `APPROACH`/`REPOSITION` means retained look/aim ownership, not forcing the torso to ignore its travel direction;
- while `HOLD`/`STRAFE` are not path-driven, preserve the existing target-facing combat presentation; in particular, `STRAFE` must establish target-facing yaw before applying lateral input so `MoveControl.strafe(0.0F, lateral)` remains lateral relative to the opponent;
- existing panic, safety, swimming, interaction, and higher-priority activity ownership must continue to preempt guard combat movement through the existing Brain scheduling model.

`MoveToTargetSink`, MCA's existing mixin/extensions, `PathNavigation`, and `MCAMoveControl` remain responsible for route computation and physical movement.

## Positioning helper

Introduce one small stateless helper, `RangedCombatPositioning`, for candidate selection only.

Responsibilities:

- select the physically nearest visible valid movement threat using the existing guard-enemy predicate;
- choose an away candidate using `LandRandomPos.getPosAway(...)` without path creation;
- choose a bounded nearby firing candidate for `REPOSITION` using world collision/line-of-sight checks;
- validate the immediate left/right lateral step before a `STRAFE` burst using the current navigation node/path-type rules, without owning strafe timing or direction state;
- return positions/results only; it owns no timers, navigation, Brain memories, or entity mutation.

The helper must not become a MineColonies-style custom pathfinder. MineColonies `PathJobCanSee` is a behavioral reference; MCA's actual path ownership remains vanilla/MCA `MoveToTargetSink`.

## Move-control simplification

Remove archer tactical behavior from `ArcherMoveControl`:

- remove `archerStrafeRequested`;
- remove `StrafeResult` and its debug/result API;
- remove the overridden archer strafe execution and duplicated walkability test;
- remove `emergencyFleeing` from move control.

After those responsibilities are removed, `ArcherMoveControl` is an unnecessary wrapper. Delete it and make `MCAMoveControl` directly usable by `VillagerEntityMCA` (adjust visibility/constructor visibility as narrowly as required). Update `VillagerEntityMCA` to install `MCAMoveControl` directly and remove `getArcherMoveControl()`.

Do not alter `MCAMoveControl`'s existing navigation/climb/jump behavior as part of this work. Keep the inherited vanilla strafe implementation and add only a narrow speed-aware entry point for active-use `KITE`: delegate to `super.strafe(...)`, then restore the requested speed modifier. Do not copy vanilla strafe math or recreate an archer-specific movement controller.

## Weapon behavior coordination

`BowTask` and `ExtendedCrossbowAttackTask` read the canonical ranged-combat state.

- In `EMERGENCY_FLEE`, stop any active bow draw or crossbow charge and do not begin another attack cycle.
- In `KITE`, `HOLD`, `STRAFE`, `REPOSITION`, or `APPROACH`, weapon behavior continues to use its existing target validity, line-of-sight, draw/charge, cooldown, and effective-range checks. `REPOSITION`/`APPROACH` naturally cannot fire when visibility/range conditions fail.
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

1. An archer with a stationary visible target in a clear firing lane spends most combat time in `HOLD`, may perform bounded 8-14 tick lateral `STRAFE` bursts, returns to `HOLD`, and never enters rapid left/right ping-pong. The regression test must specifically detect repeated alternating lateral motion while the archer remains within the same roughly one-block lateral envelope.
2. Brief line-of-sight loss below the grace period does not start movement; sustained in-range LOS loss enters `REPOSITION` and produces one Brain-owned walking intent toward a firing candidate/fallback.
3. A target moving across the 6/9-block kite band does not cause per-tick state ping-pong; the current hysteresis is preserved.
4. A target entering the 3.5/5-block emergency band causes group-aware escape movement that does not flee from one nearby threat directly toward another, and both bow and crossbow attack cycles remain suppressed until emergency exit.
5. With multiple valid visible enemies outside emergency range, KITE responds to the physically nearest close threat rather than a farther higher-priority attack target.
6. Combat movement publishes `WALK_TARGET` and allows `MoveToTargetSink`/navigation to own `PATH`; obstacle traversal, doors, jumping, and MCA climb behavior are not replaced by custom archer locomotion.
7. A blocked/unreachable reposition candidate does not trigger repeated multi-path searches in the archer task; Brain navigation invalidation/retry leads to a bounded new candidate or approach fallback.
8. Target loss, weapon removal, panic/safety preemption, and combat behavior stop cleanly release ranged-combat state and stale combat movement intent.
9. A blocked strafe ends the current burst without reversing direction; another strafe cannot begin until the cooldown expires.
10. `WALK_TARGET` pathing does not erase combat look ownership: `HOLD`, `STRAFE`, `APPROACH`, and `REPOSITION` retain target look/aim intent; `EMERGENCY_FLEE` may orient the body toward escape movement; and a moving `KITE` archer observed during an active bow draw keeps its body facing the attack target while still opening distance. No custom post-movement body-yaw loop is added to fight vanilla `MoveControl`/`BodyRotationControl`.

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
- Adding squad tactics, cover systems, formations, predictive dodging, suppression, or continuous player-like dodge/orbit strafing.
- Tuning unrelated melee guard movement.

The intended result is deliberately smaller than the current archer movement implementation: one tactical state owner, one stateless position selector, the existing Brain/navigation stack doing path movement, and a short vanilla-style `MoveControl` strafe burst as the only non-path locomotion exception.
