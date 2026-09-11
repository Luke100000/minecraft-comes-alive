# Archer Combat Movement Simplification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current permanent/oscillating archer strafe loop with deliberate Brain-owned ranged positioning plus short skeleton-style strafe bursts that never ping-pong direction.

**Architecture:** `ArcherMovementTask` remains the sole tactical-state writer and publishes path movement through `WALK_TARGET`; vanilla `MoveToTargetSink`, MCA navigation, and `MCAMoveControl` own path execution. A new stateless `RangedCombatPositioning` helper performs bounded geometric candidate checks only. Direct `MoveControl.strafe(...)` remains only for the short `STRAFE` state, and bow/crossbow behavior reads the same runtime Brain state for emergency suppression.

**Tech Stack:** Java 21, Minecraft 1.21.1, Gradle multi-loader, NeoForge 21.1.234, Fabric API 0.116.13+1.21.1, JUnit 5.10.2, NeoForge GameTest.

**Spec:** `docs/superpowers/specs/2026-09-07-archer-combat-movement-simplification-design.md`

## Global Constraints

- Preserve emergency hysteresis exactly: enter below `12.25` squared (3.5 blocks), exit at `25.0` squared (5 blocks), vertical threat distance `<= 2.5`, desired retreat distance `6`, speed `0.9`.
- `EMERGENCY_FLEE` evaluates the nearby visible valid hostile set together and chooses a bounded geometric escape that improves the minimum distance to that group; it must not flee from one close mob directly toward another.
- Preserve kite hysteresis exactly: enter below `36.0` squared (6 blocks), exit at `81.0` squared (9 blocks), vertical threat distance `<= 2.5`, desired retreat distance `9`, speed `0.85`.
- `KITE` remains single-threat-oriented and retreats from the physically nearest close hostile. The 9-block value is desired/exit spacing; the first bounded random candidate is only required to open useful distance.
- Preserve approach speed `0.5` and 10-tick sustained LOS-loss grace before `REPOSITION`.
- Reposition samples at most 8 geometric candidates inside 8 horizontal / 4 vertical blocks; candidate evaluation must not call `PathNavigation.createPath(...)`.
- Stable `HOLD` must last at least 40 ticks before a strafe may begin.
- `STRAFE` uses forward `0.0F`, lateral magnitude `0.35F`, lasts 8-14 ticks, then has a randomized 40-80 tick cooldown.
- A strafe direction is chosen once. Never reverse inside the burst and never immediately launch the opposite direction after collision/stall.
- The primary regression is the one-block left/right shimmy: do not permit repeated alternating lateral input while the archer makes little net lateral progress inside roughly the same one-block envelope.
- Path-oriented combat movement publishes `WALK_TARGET`; do not call `navigation.moveTo(...)`, do not mutate `PATH`, and do not erase `CANT_REACH_WALK_TARGET_SINCE` every tick.
- `WALK_TARGET` does not replace target look/aim ownership. `HOLD`, `STRAFE`, `APPROACH`, and `REPOSITION` retain the attack target as `LOOK_TARGET`; `KITE` and `EMERGENCY_FLEE` may orient the body toward the retreat route.
- Do not add a custom per-tick body-yaw override to fight vanilla pathing. During path movement vanilla `MoveControl`/`BodyRotationControl` may align the torso to travel while `LookControl` independently tracks/clamps the target. `STRAFE` is the non-path exception and must establish target-facing yaw before applying lateral input.
- `ArcherMoveControl` must be removed rather than extended with another result/collision state machine.
- Keep `RangedWeaponHelper` as MCA's selected-hand/range abstraction; do not substitute vanilla main-hand-only range helpers.
- Keep physically-nearest visible guard enemy logic for close-range movement; do not substitute priority-ranked `NEAREST_GUARD_ENEMY` for kiting distance decisions.
- Do not modify unrelated current work in `MCAClient`, Blueprint files, `FloorGeometry`, or `RoomDFUTest`.
- Automated success does not prove visible motion quality; final verification must state whether a live/GameTest movement run occurred.

---

## File Structure

- Create `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/RangedCombatState.java` — canonical runtime tactical enum plus read-only helper for consumers.
- Create `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/RangedCombatPositioning.java` — stateless world-query/candidate-selection helper; no timers, Brain writes, navigation starts, or path creation.
- Modify `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementTask.java` — sole state writer, hysteresis, `WALK_TARGET` publication, bounded strafe timing/input.
- Modify `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/BowTask.java` — read canonical state for emergency attack suppression.
- Modify `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ExtendedCrossbowAttackTask.java` — read canonical state and cancel charge/fire during emergency flee.
- Modify `common/src/main/java/net/conczin/mca/entity/ai/MemoryModuleTypeMCA.java` — add runtime-only ranged-combat memory.
- Modify `common/src/main/java/net/conczin/mca/entity/ai/brain/VillagerTasksMCA.java` — register ranged-combat memory with villager Brain provider.
- Modify `common/src/main/java/net/conczin/mca/entity/ai/MCAMoveControl.java` — make directly constructible by `VillagerEntityMCA`; do not change movement semantics.
- Delete `common/src/main/java/net/conczin/mca/entity/ai/ArcherMoveControl.java`.
- Modify `common/src/main/java/net/conczin/mca/entity/VillagerEntityMCA.java` — install `MCAMoveControl` directly and remove archer-control field/getter.
- Create `common/src/test/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementStateTest.java` — deterministic state/hysteresis/strafe timing tests.
- Create `common/src/test/java/net/conczin/mca/entity/ai/brain/tasks/RangedCombatStateTest.java` — memory registration and attack-suppression contract.
- Modify `neoforge/build.gradle` — add the NeoForge `gameTestServer` run used by runtime combat tests and keep development `*GameTests*.class` files out of the shipped jar.
- Create `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherCombatMovementGameTests.java` — live movement/LOS/collision/weapon integration tests using the existing local NeoForge GameTest pattern.

---

### Task 1: Add the Canonical Runtime Ranged-Combat State

**Files:**
- Create: `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/RangedCombatState.java`
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/MemoryModuleTypeMCA.java:16-30`
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/brain/VillagerTasksMCA.java:54-96`
- Create: `common/src/test/java/net/conczin/mca/entity/ai/brain/tasks/RangedCombatStateTest.java`

**Interfaces:**
- Produces: `MemoryModuleTypeMCA.RANGED_COMBAT_STATE : MemoryModuleType<RangedCombatState>`
- Produces: `RangedCombatState.current(LivingEntity) : Optional<RangedCombatState>`
- Produces: `RangedCombatState.suppressesRangedAttack() : boolean`
- Consumers: `ArcherMovementTask`, `BowTask`, `ExtendedCrossbowAttackTask`, GameTests.

- [ ] **Step 1: Write the failing state/registration test**

```java
package net.conczin.mca.entity.ai.brain.tasks;

import net.conczin.mca.entity.ai.MemoryModuleTypeMCA;
import net.conczin.mca.entity.ai.brain.VillagerTasksMCA;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RangedCombatStateTest {
    @Test
    void rangedCombatMemoryIsRegisteredWithVillagerBrain() {
        assertTrue(MemoryModuleTypeMCA.MEMORY_MODULES.containsValue(MemoryModuleTypeMCA.RANGED_COMBAT_STATE));
        assertTrue(VillagerTasksMCA.MEMORY_TYPES.contains(MemoryModuleTypeMCA.RANGED_COMBAT_STATE));
    }

    @Test
    void onlyEmergencyFleeSuppressesRangedAttack() {
        for (RangedCombatState state : RangedCombatState.values()) {
            if (state == RangedCombatState.EMERGENCY_FLEE) {
                assertTrue(state.suppressesRangedAttack());
            } else {
                assertFalse(state.suppressesRangedAttack(), state.name());
            }
        }
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.entity.ai.brain.tasks.RangedCombatStateTest'
```

Expected: compilation/test failure because `RangedCombatState` and `RANGED_COMBAT_STATE` do not exist.

- [ ] **Step 3: Add the enum and runtime-only memory**

Create the enum with exactly these states and consumer helper:

```java
public enum RangedCombatState {
    HOLD,
    STRAFE,
    APPROACH,
    REPOSITION,
    KITE,
    EMERGENCY_FLEE;

    public boolean suppressesRangedAttack() {
        return this == EMERGENCY_FLEE;
    }

    public static Optional<RangedCombatState> current(LivingEntity entity) {
        Optional<RangedCombatState> state =
                entity.getBrain().getMemoryInternal(MemoryModuleTypeMCA.RANGED_COMBAT_STATE);
        return state == null ? Optional.empty() : state;
    }
}
```

Use the null-safe read deliberately: vanilla 1.21.1 `Brain.getMemoryInternal(...)` returns `null` when a memory type is not registered, and `BowTask` / `ExtendedCrossbowAttackTask` retain their generic `Mob` bounds. Registered MCA villagers still return the normal `Optional` value.

Register it without a codec so it is runtime-only:

```java
MemoryModuleType<RangedCombatState> RANGED_COMBAT_STATE =
        register("ranged_combat_state", Optional.empty());
```

Append `MemoryModuleTypeMCA.RANGED_COMBAT_STATE` to `VillagerTasksMCA.MEMORY_TYPES`.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the same `RangedCombatStateTest` command. Expected: PASS.

- [ ] **Step 5: Commit only this task**

```powershell
git add common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/RangedCombatState.java common/src/main/java/net/conczin/mca/entity/ai/MemoryModuleTypeMCA.java common/src/main/java/net/conczin/mca/entity/ai/brain/VillagerTasksMCA.java common/src/test/java/net/conczin/mca/entity/ai/brain/tasks/RangedCombatStateTest.java
git commit -m "refactor: add canonical archer combat state"
```

---

### Task 2: Make Base State Selection Deterministic and Preserve Hysteresis

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementTask.java:22-185`
- Create: `common/src/test/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementStateTest.java`

**Interfaces:**
- Consumes: `RangedCombatState` from Task 1.
- Produces package-private static base-state selector. `STRAFE` is deliberately not selected here; Task 5 layers the bounded micro-state on top of a valid `HOLD`:

```java
static RangedCombatState selectBaseState(
        RangedCombatState currentState,
        double targetDistanceSquared,
        double threatDistanceSquared,
        double threatVerticalDistance,
        double attackRangeSquared,
        int seeTime
)
```

- Produces the package-private start predicate used by Task 5:

```java
static boolean shouldStartStrafe(int holdTicks, int strafeCooldown)
```

- [ ] **Step 1: Write failing threshold/hysteresis tests**

Cover these exact cases in `ArcherMovementStateTest`:

```java
@Test
void emergencyUsesSeparateEnterAndExitThresholds() {
    assertEquals(EMERGENCY_FLEE, selectBaseState(HOLD, 100, 12.24, 0, 225, 20));
    assertEquals(EMERGENCY_FLEE, selectBaseState(EMERGENCY_FLEE, 100, 24.99, 0, 225, 20));
    assertEquals(KITE, selectBaseState(EMERGENCY_FLEE, 100, 25.0, 0, 225, 20));
}

@Test
void kiteUsesSeparateEnterAndExitThresholds() {
    assertEquals(KITE, selectBaseState(HOLD, 100, 35.99, 0, 225, 20));
    assertEquals(KITE, selectBaseState(KITE, 100, 80.99, 0, 225, 20));
    assertEquals(HOLD, selectBaseState(KITE, 100, 81.0, 0, 225, 20));
}

@Test
void sustainedLosLossRepositionsButBriefLossHolds() {
    assertEquals(HOLD, selectBaseState(HOLD, 100, 100, 0, 225, -10));
    assertEquals(REPOSITION, selectBaseState(HOLD, 100, 100, 0, 225, -11));
}

@Test
void outOfRangeApproachesBeforeOrdinaryHold() {
    assertEquals(APPROACH, selectBaseState(HOLD, 226, 100, 0, 225, 40));
}

@Test
void strafeRequiresStableHoldAndExpiredCooldown() {
    assertFalse(shouldStartStrafe(39, 0));
    assertFalse(shouldStartStrafe(40, 1));
    assertTrue(shouldStartStrafe(40, 0));
}

@Test
void verticallySeparatedThreatDoesNotTriggerCloseRangeStates() {
    assertEquals(HOLD, selectBaseState(HOLD, 100, 4, 2.51, 225, 20));
    assertEquals(HOLD, selectBaseState(KITE, 100, 4, 2.51, 225, 20));
}
```

- [ ] **Step 2: Run the state test and verify RED**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.entity.ai.brain.tasks.ArcherMovementStateTest'
```

Expected: FAIL because the selector/timing methods do not yet expose `RangedCombatState` semantics.

- [ ] **Step 3: Replace the private `MovementState` selector with explicit canonical hysteresis**

Implement the close-range exit bands explicitly rather than deriving one threshold and accidentally losing the `EMERGENCY_FLEE -> KITE` transition:

```java
if (closeRangeThreat) {
    if (currentState == EMERGENCY_FLEE && threatDistanceSquared < EMERGENCY_EXIT_DISTANCE_SQUARED) {
        return EMERGENCY_FLEE;
    }
    if (threatDistanceSquared < EMERGENCY_ENTER_DISTANCE_SQUARED) {
        return EMERGENCY_FLEE;
    }
    if ((currentState == EMERGENCY_FLEE || currentState == KITE)
            && threatDistanceSquared < KITE_EXIT_DISTANCE_SQUARED) {
        return KITE;
    }
    if (threatDistanceSquared < KITE_ENTER_DISTANCE_SQUARED) {
        return KITE;
    }
}
if (targetDistanceSquared > attackRangeSquared) return APPROACH;
if (seeTime < -LOST_SIGHT_BEFORE_APPROACH) return REPOSITION;
return HOLD;
```

Do not change movement execution yet. This task is only base-state semantics and canonical-memory adoption. `ArcherMovementTask` writes `MemoryModuleTypeMCA.RANGED_COMBAT_STATE` whenever its selected base tactical state changes and erases it in `stop()`. Task 5 is the only place that may temporarily replace `HOLD` with canonical `STRAFE` while a burst is active.

- [ ] **Step 4: Run both state tests and verify GREEN**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.entity.ai.brain.tasks.RangedCombatStateTest' --tests 'net.conczin.mca.entity.ai.brain.tasks.ArcherMovementStateTest'
```

- [ ] **Step 5: Commit**

```powershell
git add common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementTask.java common/src/test/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementStateTest.java
git commit -m "refactor: centralize archer movement state selection"
```

---

### Task 3: Add Stateless Ranged Position Selection Without Path Creation

**Files:**
- Create: `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/RangedCombatPositioning.java`
- Modify: `neoforge/build.gradle:23-51`
- Create: `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherCombatMovementGameTests.java`

**Interfaces:**
- Produces:

```java
static LivingEntity nearestMovementThreat(Mob entity, LivingEntity fallback)
static Optional<Vec3> findAwayPosition(PathfinderMob entity, LivingEntity threat, double desiredDistance)
static Optional<Vec3> findFiringPosition(PathfinderMob entity, LivingEntity target, LivingEntity movementThreat, double attackRangeSquared)
static boolean isStrafeSideWalkable(PathfinderMob entity, float lateralDirection)
```

- `findAwayPosition` makes one `LandRandomPos.getPosAway(entity, 12, 5, threat.position())` request, validates that returned candidate is stable and farther from the threat, and never creates a path. Do not preserve the old outer 10-attempt path-search loop; `LandRandomPos` already owns its own bounded random-position search.
- `findFiringPosition` examines at most 8 nearby candidates inside 8 horizontal / 4 vertical blocks, requires node/path type `WALKABLE`, collision-free standing space, LOS from candidate eye to target eye, ranged distance, and kite safety.
- `isStrafeSideWalkable` probes one full block to the chosen local lateral side so the pre-check does not merely re-sample the entity's current block.

- [ ] **Step 1: Add the executable NeoForge GameTest run configuration**

Reuse the already-proven 1.21.1 MCA run shape from the local navigation GameTest checkout:

```groovy
gameTestServer {
    type.set('gameTestServer')
    gameDirectory = file('run-gametest/')
    ideName = "NeoForge GameTest Server (${project.path})"
    systemProperty('neoforge.enabledGameTestNamespaces', "${mod_id},minecraft")
}
```

Add it inside `neoForge.runs`, after the ordinary `server` run. Keep development GameTests out of the production jar:

```groovy
tasks.named('jar') {
    exclude '**/*GameTests*.class'
}
```

Verify Gradle exposes the runtime task:

```powershell
.\gradlew.bat :neoforge:tasks --all
```

Expected: the task list contains `runGameTestServer - Runs the gameTestServer Minecraft run configuration.`

- [ ] **Step 2: Add RED GameTests for geometric ownership**

Use the locally established 1.21.1 pattern:

```java
@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class ArcherCombatMovementGameTests {
    @GameTest(
            batch = "mca_archer_positioning",
            templateNamespace = "minecraft",
            template = "bastion/blocks/air",
            timeoutTicks = 120
    )
    public static void blockedSideIsRejectedBeforeStrafe(GameTestHelper helper) {
        // Build flat stone floor, spawn MCA villager at center, place a stone wall
        // exactly one block to the tested lateral side, face the entity toward a fixed target,
        // then assert isStrafeSideWalkable(...) is false for the wall side and true for the clear side.
    }
}
```

Add a second GameTest that builds a wall between archer and target with a clear offset firing lane, calls `findFiringPosition`, and asserts the returned position has LOS and lies within attack range.

Keep the helper result-only: the test consumes returned `Vec3` positions, and production code in `RangedCombatPositioning` must contain no `navigation.createPath(...)` or `navigation.moveTo(...)` call.

- [ ] **Step 3: Compile NeoForge to verify RED**

```powershell
.\gradlew.bat :neoforge:compileJava
```

Expected: FAIL because `RangedCombatPositioning` does not exist.

- [ ] **Step 4: Implement `RangedCombatPositioning`**

Implementation rules:

```java
final class RangedCombatPositioning {
    private static final int FIRING_CANDIDATE_ATTEMPTS = 8;
    private static final int FIRING_HORIZONTAL_RANGE = 8;
    private static final int FIRING_VERTICAL_RANGE = 4;

    private RangedCombatPositioning() {}
}
```

For LOS, raycast from candidate eye position to `target.getEyePosition()` with block colliders and no fluid collision. For walkability, reuse the entity navigation's `NodeEvaluator`/`PathType.WALKABLE`; do not add a second pathfinder. For nearest threat, move the existing `NEAREST_VISIBLE_LIVING_ENTITIES` + `GuardEnemiesSensor.isGuardEnemy(...)` search out of `ArcherMovementTask` unchanged in meaning.

- [ ] **Step 5: Compile and run the positioning GameTests**

First compile:

```powershell
.\gradlew.bat :common:compileJava :neoforge:compileJava
.\gradlew.bat :neoforge:runGameTestServer
```

`runGameTestServer` executes the registered GameTests in a real NeoForge server. Confirm the `mca_archer_positioning` tests report PASS. Use the same `@GameTestHolder("minecraft")`, `@PrefixGameTestTemplate(false)`, and `bastion/blocks/air` pattern already present in the local MCA navigation GameTests.

Also verify the helper has no path-execution ownership:

```powershell
rg -n "createPath\(|moveTo\(" common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/RangedCombatPositioning.java
```

Expected: no matches.

- [ ] **Step 6: Commit**

```powershell
git add common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/RangedCombatPositioning.java neoforge/build.gradle neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherCombatMovementGameTests.java
git commit -m "refactor: add archer ranged positioning helper"
```

---

### Task 4: Move Approach, Reposition, Kite, and Flee Onto Brain `WALK_TARGET`

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementTask.java:82-297`
- Extend: `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherCombatMovementGameTests.java`

**Interfaces:**
- Consumes Task 3 positioning methods.
- `ArcherMovementTask` publishes `WalkTarget` only; `MoveToTargetSink` owns `PATH` and calls navigation.
- `ArcherMovementTask` retains combat `LOOK_TARGET`/`LookControl` ownership during `HOLD`, `STRAFE`, `APPROACH`, and `REPOSITION`. `KITE`/`EMERGENCY_FLEE` do not require target-facing body yaw.

- [ ] **Step 1: Add RED GameTests for movement ownership**

Add tests with a real archer created via `VillagerFactory.newVillager(helper.getLevel()).withProfession(ProfessionsMCA.ARCHER)...spawn(...)`, equip a bow, and write `MemoryModuleType.ATTACK_TARGET` directly.

Required cases:

1. `outOfRangeArcherPublishesApproachWalkTarget` — target beyond attack range; assert `WALK_TARGET` is present and tracks the target.
2. `closeThreatPublishesAwayWalkTarget` — threat inside emergency range; assert state is `EMERGENCY_FLEE` and `WALK_TARGET` points farther from the threat than the archer's starting position.
3. `sustainedBlockedLosPublishesRepositionTarget` — target in range but wall blocks LOS for >10 ticks; assert `REPOSITION` and a positional `WALK_TARGET` appears.
4. `holdClearsCombatWalkTarget` — after entering valid visible in-range `HOLD`, assert stale combat `WALK_TARGET` is erased and remains absent.
5. `pathMovementRetainsCombatLookTarget` — exercise `APPROACH` and `REPOSITION`; while each state owns movement, assert `LOOK_TARGET` remains present and its `PositionTracker.currentPosition()` follows the attack target rather than being cleared/replaced by path publication. Do not assert literal torso yaw while `MOVE_TO` is active.

- [ ] **Step 2: Run/compile and verify RED against current direct-navigation code**

```powershell
.\gradlew.bat :common:compileJava :neoforge:compileJava
.\gradlew.bat :neoforge:runGameTestServer
```

Expected: at least the new Brain-ownership GameTest assertions fail before the refactor.

- [ ] **Step 3: Replace direct path/navigation ownership in `ArcherMovementTask`**

Delete:

- `findPathAway(...)`
- `getPathEndDistanceSquared(...)`
- combat-owned `Path` creation
- `navigation.moveTo(...)` in approach/flee/kite
- per-tick `navigation.stop()`
- per-tick erase of `CANT_REACH_WALK_TARGET_SINCE`
- path-blocked counters whose only purpose was forcing custom repaths.

Publish intents instead:

```java
private void publishApproach(E entity, LivingEntity target) {
    entity.getBrain().setMemory(
            MemoryModuleType.WALK_TARGET,
            new WalkTarget(new EntityTracker(target, false), (float) SPEED_MODIFIER, 0)
    );
}
```

For `KITE`, use `RangedCombatPositioning.findAwayPosition(...)` and publish `new WalkTarget(position, speed, 0)`. For `EMERGENCY_FLEE`, use the group-aware emergency escape helper over nearby valid threats and publish one Brain-owned `WalkTarget`; candidate evaluation remains geometric and never creates a path.

For `REPOSITION`, use `findFiringPosition(...)`; if absent, publish ordinary approach intent as specified.

For `HOLD`, `STRAFE`, `APPROACH`, and `REPOSITION`, keep using the attack target for `MemoryModuleType.LOOK_TARGET` plus `LookControl.setLookAt(...)`. Do not clear look intent merely because path movement is delegated through `WALK_TARGET`. For `KITE` and `EMERGENCY_FLEE`, allow the path/escape direction to own body orientation; ranged weapon tasks may still publish their own look target during `KITE` when they are actively aiming/firing.

Do not add direct `setYRot`/`setYBodyRot` correction inside path-oriented states. Local vanilla 1.21.1 runs `MoveControl.tick()` before `LookControl.tick()`, and moving mobs use `BodyRotationControl` to align body yaw with travel. The implementation preserves target aim via the look pipeline instead of creating a second yaw controller.

Use one transient `walkTargetRetryCooldown` in `ArcherMovementTask` for Brain-owned movement retries. Set it to `10 + entity.getRandom().nextInt(10)` (10-19 ticks) whenever a combat `WALK_TARGET` is published, preserving the old ordinary repath cadence without preserving custom path ownership. Decrement it once per behavior tick. Republish only on movement-state/target change, or when `WALK_TARGET` has been erased by the Brain/navigation lifecycle and this cooldown has reached zero. Never erase `CANT_REACH_WALK_TARGET_SINCE` to force another attempt.

- [ ] **Step 4: Run focused JUnit plus movement GameTests**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.entity.ai.brain.tasks.*' :common:compileJava :neoforge:compileJava
.\gradlew.bat :neoforge:runGameTestServer
```

Expected: all five movement/look-ownership cases PASS in the NeoForge GameTest server.

- [ ] **Step 5: Commit**

```powershell
git add common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementTask.java neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherCombatMovementGameTests.java
git commit -m "refactor: route archer combat movement through brain"
```

---

### Task 5: Replace Permanent Side-Strafe With Bounded Strafe Bursts

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementTask.java`
- Modify: `common/src/test/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementStateTest.java`
- Extend: `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherCombatMovementGameTests.java`

**Interfaces:**
- Uses ordinary `entity.getMoveControl().strafe(0.0F, lateral)` only while canonical state is `STRAFE`.
- Uses `RangedCombatPositioning.isStrafeSideWalkable(entity, lateral)` before a burst.
- Calls `selectBaseState(...)` every tick first. Any base state other than `HOLD` preempts/cancels a burst immediately; a valid `HOLD` either remains `HOLD`, starts a burst when eligible, or keeps the current burst until its duration/cancellation rule ends it.
- Produces package-private `static boolean shouldCancelStrafe(boolean visible, boolean inRange, boolean closeThreat, boolean collided, boolean stalled)` for deterministic cancellation tests.

- [ ] **Step 1: Add RED timing/cancellation unit tests**

Add assertions that:

```java
assertFalse(shouldStartStrafe(39, 0));
assertTrue(shouldStartStrafe(40, 0));
assertFalse(shouldStartStrafe(80, 1));

assertTrue(shouldCancelStrafe(true, true, false, true, false));   // collision
assertTrue(shouldCancelStrafe(true, true, false, false, true));   // stall
assertTrue(shouldCancelStrafe(false, true, false, false, false)); // LOS lost
assertTrue(shouldCancelStrafe(true, false, false, false, false)); // range lost
assertTrue(shouldCancelStrafe(true, true, true, false, false));   // close threat
assertFalse(shouldCancelStrafe(true, true, false, false, false));
```

- [ ] **Step 2: Add RED live anti-oscillation tests**

Add three GameTests:

1. `stableTargetUsesBoundedStrafeBursts` — run a clear-lane archer against a stationary target for at least 240 ticks, sample canonical state and lateral displacement each tick, assert:
   - most ticks are `HOLD` rather than `STRAFE`;
   - each contiguous `STRAFE` run is between 8 and 14 ticks unless cancelled early;
   - two strafe runs are separated by at least 40 non-strafe ticks;
   - direction sign does not change inside one run.
2. `blockedStrafeCancelsWithoutDirectionFlip` — wall off the selected lateral side; when collision/stall occurs, assert state returns to `HOLD` and no opposite-direction `STRAFE` begins before cooldown expiry.
3. `sameBlockLeftRightShimmyDoesNotRecur` — against a stationary visible target on clear flat ground, sample the archer's horizontal delta projected onto the target-relative lateral axis every tick. Track a rolling 20-tick window and the lateral displacement envelope. Fail if the lateral sign alternates more than once while total lateral excursion stays below 1.0 block. This is the explicit regression for the historical left/right chatter around one block, independent of tactical-state labels.

- [ ] **Step 3: Run tests and verify RED**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.entity.ai.brain.tasks.ArcherMovementStateTest' :neoforge:compileJava
.\gradlew.bat :neoforge:runGameTestServer
```

Expected: the current continuous-strafe behavior fails the new duration/cooldown/direction assertions and/or the explicit same-block shimmy regression.

- [ ] **Step 4: Implement bounded strafe fields and lifecycle**

Use transient fields only inside `ArcherMovementTask`:

```java
private int holdTicks;
private int strafeCooldown;
private int strafeTicksRemaining;
private int strafeTicksElapsed;
private float strafeDirection;
```

Rules:

- compute `baseState = selectBaseState(...)` before strafe timing; if it is not `HOLD`, end any burst and enter that base state immediately;
- increment `holdTicks` only during valid `HOLD`;
- decrement positive `strafeCooldown` once per behavior tick;
- after `holdTicks >= 40 && strafeCooldown == 0`, randomly try one side; if unsafe, try the opposite once; if both fail, remain `HOLD` and assign cooldown 40-80;
- duration is random inclusive 8-14 ticks;
- set canonical Brain state to `STRAFE` when the burst starts and back to `HOLD` when it completes/cancels while the base state remains `HOLD`;
- call `moveControl.strafe(0.0F, strafeDirection * 0.35F)` each active strafe tick;
- before each active strafe input, keep the target as `LOOK_TARGET` and establish target-facing yaw with the existing target-facing path (`lookAt`/equivalent) so the lateral vector is relative to the opponent, not the previous navigation heading;
- reset `holdTicks` when the burst begins;
- cancel to `HOLD` on LOS/range/close-threat invalidation, horizontal/minor-horizontal collision, or post-start horizontal stall;
- set cooldown 40-80 after normal completion or early cancellation;
- never mutate `strafeDirection` during an active burst.

- [ ] **Step 5: Run JUnit and GameTests until GREEN**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.entity.ai.brain.tasks.ArcherMovementStateTest'
.\gradlew.bat :neoforge:runGameTestServer
```

Confirm the observed contiguous strafe runs satisfy duration/cooldown rules and no sign flip occurs inside a run.

- [ ] **Step 6: Commit**

```powershell
git add common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementTask.java common/src/test/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementStateTest.java neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherCombatMovementGameTests.java
git commit -m "fix: bound archer strafing into deliberate bursts"
```

---

### Task 6: Move Emergency Weapon Suppression Onto the Canonical Brain State

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/BowTask.java:60-80,174-176`
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ExtendedCrossbowAttackTask.java`
- Extend: `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherCombatMovementGameTests.java`

**Interfaces:**
- Consumes: `RangedCombatState.current(entity)` / `suppressesRangedAttack()`.
- Removes all dependency on `ArcherMoveControl.isEmergencyFleeing()`.

- [ ] **Step 1: Add RED live tests for bow and crossbow emergency cancellation**

Bow case:

- spawn/equip archer with bow and valid target;
- allow bow draw to begin;
- move/spawn close threat inside 3.5 blocks;
- assert canonical state becomes `EMERGENCY_FLEE` and `entity.isUsingItem()` becomes false before another shot is released.

Crossbow case:

- equip crossbow and begin charge;
- trigger `EMERGENCY_FLEE`;
- assert using-item and charging flags are cleared and no shot occurs while emergency state remains.

- [ ] **Step 2: Run GameTests and verify RED for crossbow coordination / old control dependency**

```powershell
.\gradlew.bat :neoforge:compileJava
.\gradlew.bat :neoforge:runGameTestServer
```

Expected: the new emergency bow/crossbow assertions fail before both weapon tasks read the canonical state.

- [ ] **Step 3: Update `BowTask`**

Replace the old move-control type check with canonical-state read:

```java
private static boolean isEmergencyFleeing(Mob entity) {
    return RangedCombatState.current(entity)
            .map(RangedCombatState::suppressesRangedAttack)
            .orElse(false);
}
```

Keep existing bow draw/cooldown/range/LOS logic unchanged.

- [ ] **Step 4: Update `ExtendedCrossbowAttackTask`**

Reject starting during emergency flee in `checkExtraStartConditions`.

At the top of `crossbowAttack(...)`, add:

```java
if (RangedCombatState.current(entity)
        .map(RangedCombatState::suppressesRangedAttack)
        .orElse(false)) {
    if (entity.isUsingItem()) {
        entity.stopUsingItem();
    }
    entity.setChargingCrossbow(false);
    this.crossbowState = CrossbowState.UNCHARGED;
    return;
}
```

Do not add duplicate distance hysteresis to either weapon task.

- [ ] **Step 5: Run focused tests and GameTests GREEN**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.entity.ai.brain.tasks.*' :common:compileJava :neoforge:compileJava
.\gradlew.bat :neoforge:runGameTestServer
```

Expected: the bow/crossbow emergency GameTests PASS.

- [ ] **Step 6: Commit**

```powershell
git add common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/BowTask.java common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ExtendedCrossbowAttackTask.java neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherCombatMovementGameTests.java
git commit -m "fix: share archer emergency state with ranged weapons"
```

---

### Task 7: Delete `ArcherMoveControl` and Restore One Movement Controller

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/MCAMoveControl.java:9-17`
- Delete: `common/src/main/java/net/conczin/mca/entity/ai/ArcherMoveControl.java`
- Modify: `common/src/main/java/net/conczin/mca/entity/VillagerEntityMCA.java:121-153`
- Extend: `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherCombatMovementGameTests.java`

**Interfaces:**
- `MCAMoveControl` becomes `public` with a `public MCAMoveControl(Mob mob)` constructor.
- `VillagerEntityMCA` no longer exposes `getArcherMoveControl()`.

- [ ] **Step 1: Add a RED integration assertion for controller ownership**

In the common archer GameTest fixture, assert after spawn:

```java
helper.assertTrue(
        archer.getMoveControl().getClass() == MCAMoveControl.class,
        "MCA villager should use the shared MCAMoveControl directly"
);
```

This fails before deletion because the runtime type is `ArcherMoveControl`.

- [ ] **Step 2: Run compile/GameTest and verify RED**

```powershell
.\gradlew.bat :common:compileJava :neoforge:compileJava
.\gradlew.bat :neoforge:runGameTestServer
```

Expected: the controller-ownership assertion fails because the runtime type is still `ArcherMoveControl`.

- [ ] **Step 3: Simplify controller construction**

In `MCAMoveControl`:

```java
public class MCAMoveControl extends MoveControl {
    public MCAMoveControl(Mob mob) {
        super(mob);
    }
    // existing climb/jump behavior unchanged
}
```

Remove the Javadoc statement about specialized controls delegating to it.

In `VillagerEntityMCA`, replace:

```java
private final ArcherMoveControl archerMoveControl;
...
this.archerMoveControl = new ArcherMoveControl(this);
this.moveControl = this.archerMoveControl;
```

with:

```java
this.moveControl = new MCAMoveControl(this);
```

Delete `getArcherMoveControl()` and delete `ArcherMoveControl.java`.

- [ ] **Step 4: Search for stale references**

```powershell
rg -n "ArcherMoveControl|getArcherMoveControl|strafeForArcher|StrafeResult|emergencyFleeing" common neoforge fabric
```

Expected: no production references.

- [ ] **Step 5: Compile all production modules and run controller GameTest**

```powershell
.\gradlew.bat :common:compileJava :fabric:compileJava :neoforge:compileJava
.\gradlew.bat :neoforge:runGameTestServer
```

Expected: production compilation passes; the controller assertion and bounded-strafe GameTests both PASS.

- [ ] **Step 6: Commit**

```powershell
git add common/src/main/java/net/conczin/mca/entity/ai/MCAMoveControl.java common/src/main/java/net/conczin/mca/entity/VillagerEntityMCA.java common/src/main/java/net/conczin/mca/entity/ai/ArcherMoveControl.java neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherCombatMovementGameTests.java
git commit -m "refactor: remove archer-specific move controller"
```

---

### Task 8: Complete Runtime Coverage for Threat Choice, Retry, and Cleanup

**Files:**
- Modify: `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherCombatMovementGameTests.java`
- Modify only if tests expose a defect: `ArcherMovementTask.java`, `RangedCombatPositioning.java`, `BowTask.java`, or `ExtendedCrossbowAttackTask.java`.

**Interfaces:**
- No new production abstraction is allowed in this task unless a failing required scenario proves one is necessary.

- [ ] **Step 1: Add the remaining required GameTests**

Add these scenarios from the spec:

1. `nearestVisibleThreatControlsRetreat` — attack target is farther/higher priority, second valid hostile is physically closer; assert the away target increases distance from the closer threat.
2. `unreachableRepositionDoesNotPathSpam` — construct blocked firing candidates, let `MoveToTargetSink` clear/mark unreachable intent, and assert archer logic does not republish during the first 9 ticks after publication, then permits one bounded retry once the 10-19 tick `walkTargetRetryCooldown` expires. Do not add instrumentation that calls `createPath` from archer code.
3. `targetLossAndWeaponRemovalClearCombatState` — clear `ATTACK_TARGET` or remove bow/crossbow, tick behavior, assert `RANGED_COMBAT_STATE` and stale combat `WALK_TARGET` are absent.
4. `panicPreemptsCombatMovement` — give the archer a valid attack target, damage it below `25%` health so `guardTooHurt(...)` applies, and ensure the normal hurt path populates `HURT_BY`; tick until `Activity.PANIC` is active. Assert `RANGED_COMBAT_STATE` is erased and the old combat `WALK_TARGET` is not republished while panic owns movement.
5. `kiteBandDoesNotPingPong` — move target/threat through 6/9-block band over multiple ticks and assert the canonical state remains `KITE` until the exit threshold is actually reached.
6. `approachUsesExistingNavigationThroughObstacle` — place an out-of-range target behind a short wall with a usable doorway/one-block step route. Assert the archer publishes `WALK_TARGET`, vanilla/MCA navigation produces movement through the route, and the archer reaches the far side without any combat-owned path API. This is the runtime regression proving ordinary obstacle/door/step navigation is still delegated to `MoveToTargetSink`/`PathNavigation`/`MCAMoveControl`.
7. `normalRangedPathingKeepsLookOwnership` — while an archer is in `APPROACH` and then `REPOSITION`, assert `LOOK_TARGET.currentPosition()` continues to track the attack target while `WALK_TARGET`/navigation changes independently. In `KITE`/`EMERGENCY_FLEE`, assert only that retreat movement remains valid; do not require torso yaw toward the attack target.

- [ ] **Step 2: Run the new scenarios RED where gaps remain**

```powershell
.\gradlew.bat :neoforge:runGameTestServer
```

Any failing required scenario must be fixed in the existing owners; do not introduce caches/failure ladders simply to satisfy the test.

- [ ] **Step 3: Apply minimal fixes only**

Allowed examples:

- reset transient timers on target change/behavior stop;
- erase canonical state and stale combat `WALK_TARGET` once on stop;
- honor an existing non-combat `WALK_TARGET`/higher-priority state instead of overwriting it;
- correct/reset the single 10-19 tick `walkTargetRetryCooldown` if the failing runtime test proves its lifecycle is wrong.

Do not add custom `PATH` state, path-result mirrors, unreachable caches, or a second movement controller.

- [ ] **Step 4: Run all archer JUnit + GameTests GREEN**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.entity.ai.brain.tasks.*' :common:compileJava :fabric:compileJava :neoforge:compileJava
.\gradlew.bat :neoforge:runGameTestServer
```

Expected: the complete `ArcherCombatMovementGameTests` set and all required spec scenarios PASS.

- [ ] **Step 5: Commit**

```powershell
git add common/src/main/java/net/conczin/mca/entity/ai/brain/tasks neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherCombatMovementGameTests.java
git commit -m "test: cover archer combat movement lifecycle"
```

---

### Task 9: Final Cleanup Review and Verification

**Files:**
- Review only the archer files changed by Tasks 1-8 plus their tests.
- Do not stage unrelated pre-existing dirty files.

**Interfaces:** None; this is the completion gate.

- [ ] **Step 1: Run Java cleanup review against the fixed archer diff**

If the branch contains unrelated dirty files, construct the review diff from the archer commits/files only. Re-check reuse, quality, correctness, efficiency, and local vanilla-owner semantics.

Specifically verify:

- no `ArcherMoveControl` remains;
- no `navigation.createPath(...)` or `navigation.moveTo(...)` remains in `ArcherMovementTask`/`RangedCombatPositioning`;
- no per-tick erase of `CANT_REACH_WALK_TARGET_SINCE`;
- no second emergency flag outside canonical Brain state;
- no direction flip during `STRAFE`;
- no repeated left/right lateral sign chatter inside a one-block envelope in the stationary-target regression;
- `LOOK_TARGET` remains independent from `WALK_TARGET` in normal ranged path states, and no custom per-tick body-yaw controller was introduced;
- `RangedCombatPositioning` owns no mutable state;
- `MoveToTargetSink` still owns path computation/execution.

- [ ] **Step 2: Run the final automated gate**

```powershell
.\gradlew.bat :common:test :fabric:compileJava :neoforge:compileJava
git diff --check
```

Expected: Gradle exits 0 and `git diff --check` emits no errors.

- [ ] **Step 3: Run the full archer GameTest suite**

```powershell
.\gradlew.bat :neoforge:runGameTestServer
```

This is the runtime gate: run all tests in `ArcherCombatMovementGameTests`, not only individual batches. Record that they executed in the NeoForge GameTest server and separately record whether a manual in-game visual movement check was also performed; GameTest success alone is not a visual-quality claim.

- [ ] **Step 4: Inspect git state before final commit**

```powershell
git status --short
git diff --name-only HEAD
```

Confirm only archer implementation/test files are staged for the final cleanup commit. Leave the unrelated existing `MCAClient`, Blueprint, FloorGeometry, and RoomDFUTest modifications untouched.

- [ ] **Step 5: Commit any review-only corrections**

Only if Task 9 found and fixed concrete issues:

```powershell
git add common/src/main/java/net/conczin/mca/entity/ai common/src/main/java/net/conczin/mca/entity/VillagerEntityMCA.java common/src/test/java/net/conczin/mca/entity/ai neoforge/src/main/java/net/conczin/mca/entity/ai
git commit -m "refactor: polish archer combat movement"
```

If no correction was required, do not create an empty commit.

---

## Completion Criteria

Implementation is complete only when all of the following are true:

- stable visible in-range archers spend most time holding/aiming, not continuously orbiting;
- short 8-14 tick skeleton-style strafe bursts still occur after stable hold and obey 40-80 tick cooldown;
- blocked/colliding strafes cancel rather than reverse direction;
- the historical one-block left/right shimmy regression is covered explicitly and does not recur under a stationary clear-lane target;
- approach, kite, flee, and LOS reposition publish Brain `WALK_TARGET` instead of driving navigation directly;
- `HOLD`, `STRAFE`, `APPROACH`, and `REPOSITION` retain attack-target look/aim ownership independently of `WALK_TARGET`; `KITE`/`EMERGENCY_FLEE` may orient body movement toward retreat, and path states do not install a competing body-yaw loop;
- close-range 3.5/5 and 6/9 hysteresis remains exact;
- sustained LOS loss repositions after 10 ticks, brief loss does not;
- nearest physical visible threat controls retreat movement;
- bow and crossbow both suppress/cancel attacks during `EMERGENCY_FLEE` from the shared Brain state;
- `ArcherMoveControl` is deleted and MCA villagers use `MCAMoveControl` directly;
- JUnit, Fabric compile, NeoForge compile, `git diff --check`, and all archer GameTests pass;
- the final report explicitly says whether the live/GameTest anti-oscillation movement scenario was actually run.
