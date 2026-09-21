# Archer Threat-Aware Movement and Aiming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stop multi-enemy approach/flee oscillation, steer archer movement around nearby hostiles, reject corner-doomed strafes, and make bow elevation reliable for skeletons and low-height mobs.

**Architecture:** `ArcherMovementTask` remains the sole tactical-state owner and publishes only `WALK_TARGET` intent for path movement. `RangedCombatPositioning` classifies nearby hostiles into escape drivers versus spatial hazards and selects short local waypoints from the existing reachable-space graph; vanilla/MCA navigation still owns route computation/execution. Bow trajectory math becomes one pure helper in `RangedWeaponHelper`, consumed by `VillagerEntityMCA`.

**Tech Stack:** Java 21, Minecraft 1.21.1, Gradle multi-loader, NeoForge GameTest, JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-20-archer-threat-aware-movement-and-aiming-design.md`

## Global Constraints

- Keep emergency enter squared distance `< 12.25` (3.5 blocks), emergency exit squared distance `< 25.0` (5 blocks), kite enter squared distance `< 36.0` (6 blocks), kite exit squared distance `< 81.0` (9 blocks), and close-threat vertical band `<= 2.5` blocks.
- Keep emergency desired separation `6` blocks, kite desired separation `9` blocks, emergency speed `1.2`, and ordinary combat movement speed `0.5`.
- A current attack target, a mob targeting the archer, `archer.getLastHurtByMob()`, or any valid hostile inside squared distance `< 12.25` may be an escape driver; passive nearby hostiles remain movement hazards without automatically forcing kite/flee.
- Vanilla already expires `lastHurtByMob` after 100 ticks; do not add a duplicate recent-hit timer.
- Hazard clearance uses the 6-block kite-enter distance: a candidate outside a hazard's 6-block bubble must remain outside it; a candidate starting inside the bubble must not move closer.
- Close relevant threat spacing is evaluated before `APPROACH` and sustained lost-LOS `REPOSITION`.
- Keep the existing bounded reachable-space/cave-pocket search. Do not replace it with `DefaultRandomPos`, `LandRandomPos`, a custom A*, or a custom node evaluator.
- Threat-aware approach is a short waypoint layer only. `MoveToTargetSink`, `PathNavigation`, and MCA navigation still own paths and movement execution.
- Approach may backtrack at most `0.5` block relative to the attack target to permit lateral detours.
- If secondary hazards exist and no safe approach waypoint exists, hold/retry; do not fall back to a direct target route through the hazards.
- Strafe probes use the actual entity bounding box every `0.5` block out to `2.5` blocks and require at least `1.5` blocks of clear lateral travel before a burst may start.
- No strafe reversal inside a burst. An exact side-score tie uses a stable UUID-bit tie-breaker rather than random retry behavior.
- Bow aiming keeps target midpoint `0.5`, speed `1.6F`, and inaccuracy `3.0F`. Live validation showed midpoint + `horizontalDistance * 0.2D` over-lifts MCA shots, so the final helper solves the vertical component against 1.21.1 arrow drag (`0.99`) and gravity (`0.05`) while retaining those speed/inaccuracy values.
- Crossbow trajectory, bow damage/cooldown, guard target priority, generic navigation, doors/climbing, and the existing cave escape graph are out of scope.
- Preserve unrelated dirty-worktree changes. Do not reset, clean, or fold unrelated files into task commits.

## Review Focus

- **Secondary hazard disappears while an approach waypoint is active:** after the combat walk target is consumed/retried, the archer should resume an ordinary direct approach instead of remaining permanently detoured. Task 2 adds `removedSecondaryHazardReturnsToDirectApproach`.
- **Close attack target is temporarily occluded:** it must remain an escape driver through the attack-target fallback, so LOS loss cannot make the archer walk into it. Task 1 adds `occludedAttackTargetStillDrivesCloseRetreat`.
- **Archer starts inside a passive hazard's 6-block bubble:** local steering may move laterally/away but must never choose a candidate closer to that hazard. Task 2 adds `approachCandidateInsideHazardBubbleDoesNotMoveCloser`.
- **No tactically safe approach waypoint exists:** the archer must hold/retry rather than publish the direct target tracker through the hazard ring. Task 2 adds `blockedByHazardRingDoesNotFallBackToDirectApproach`.
- **Zero horizontal bow distance:** the pure aim helper must remain finite and apply only midpoint vertical delta, with no NaN/division behavior. Task 4 adds `zeroHorizontalDistanceProducesFiniteMidpointVector`.

---

## File Structure

- Modify `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/RangedCombatPositioning.java` — classify hazards/escape drivers; score retreat, approach, reposition, and strafe candidates; remain stateless and path-free.
- Modify `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementTask.java` — close-threat state precedence, hazard-aware movement publication, and strafe-side selection.
- Modify `common/src/main/java/net/conczin/mca/entity/ai/RangedWeaponHelper.java` — add the pure bow shot-vector calculation.
- Modify `common/src/main/java/net/conczin/mca/entity/VillagerEntityMCA.java` — use the bow shot-vector helper instead of quadratic compensation.
- Modify `common/src/test/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementStateTest.java` — pin close-threat precedence.
- Create `common/src/test/java/net/conczin/mca/entity/ai/RangedWeaponHelperTest.java` — deterministic trajectory-vector coverage.
- Modify `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherCombatMovementGameTests.java` — threat-role, approach-steering, retreat, and strafe integration coverage.
- Modify `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherSpiderCombatGameTests.java` only if helper reuse is required; preserve all existing spider/cave regression tests.
- Create `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherBowTrajectoryGameTests.java` — live level/uphill/downhill skeleton shooting coverage.

---

### Task 1: Split Escape Drivers from Passive Hazards and Fix State Precedence

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/RangedCombatPositioning.java:49-72,408-413`
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementTask.java:66-118,189-210`
- Modify: `common/src/test/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementStateTest.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherCombatMovementGameTests.java`

**Interfaces:**
- Produces: `static List<LivingEntity> nearbyMovementHazards(Mob entity, LivingEntity attackTarget)`
- Produces: `static List<LivingEntity> nearbyEscapeThreats(Mob entity, LivingEntity attackTarget)`
- Keeps: `static LivingEntity nearestMovementThreat(Mob entity, LivingEntity fallback)` but changes it to choose from escape drivers.
- Private helper: `isEscapeDriver(Mob entity, LivingEntity candidate, LivingEntity attackTarget)`.
- Consumers: Task 2 retreat/approach/reposition and Task 3 strafe scoring.

- [ ] **Step 1: Write the failing close-threat precedence tests**

Add these cases to `ArcherMovementStateTest`:

```java
@Test
void kiteHysteresisBeatsApproachToDistantTarget() {
    assertEquals(KITE, selectBaseState(EMERGENCY_FLEE, 400.0D, 27.0D, 0.0D, 225.0D, 20));
    assertEquals(KITE, selectBaseState(KITE, 400.0D, 80.0D, 0.0D, 225.0D, 20));
}

@Test
void closeThreatSafetyBeatsLostSightReposition() {
    assertEquals(KITE, selectBaseState(KITE, 100.0D, 25.0D, 0.0D, 225.0D, -75));
    assertEquals(KITE, selectBaseState(HOLD, 100.0D, 35.0D, 0.0D, 225.0D, -75));
}
```

Replace the old `sustainedLosLossBeatsKiteRangeAfterTargetIsOccluded` expectation because this feature deliberately makes close relevant threat safety higher priority than LOS repositioning.

- [ ] **Step 2: Run the focused unit test and verify RED**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.entity.ai.brain.tasks.ArcherMovementStateTest' --no-daemon
```

Expected: the new precedence assertions fail because `APPROACH` / `REPOSITION` currently run before ordinary kite hysteresis/entry.

- [ ] **Step 3: Add failing threat-role GameTests**

Add these methods to `ArcherCombatMovementGameTests` using the class's existing `spawnArcher`, `spawnTarget`, and Brain-memory fixture helpers:

```java
@GameTest(batch = "mca_archer_passive_secondary", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
public static void passiveSecondaryOutsideEmergencyDoesNotDriveRetreat(GameTestHelper helper) {
    cleanupTestEntities();
    BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
    prepareFlatArea(helper, start, 20);
    VillagerEntityMCA archer = spawnArcher(helper, start);
    Zombie target = spawnTarget(helper, start.east(18));
    Zombie passive = spawnTarget(helper, start.north(5));
    archer.setNoAi(true);
    archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
    seedNearbyEnemies(archer, List.of(target, passive));

    ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
    long now = helper.getLevel().getGameTime();
    movement.start(helper.getLevel(), archer, now);
    movement.tick(helper.getLevel(), archer, now + 1);

    helper.assertTrue(RangedCombatState.current(archer).orElse(null) == RangedCombatState.APPROACH,
            "passive secondary hostile incorrectly drove retreat");
    helper.succeed();
}

@GameTest(batch = "mca_archer_engaging_secondary", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
public static void engagingSecondaryDoesDriveRetreat(GameTestHelper helper) {
    cleanupTestEntities();
    BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
    prepareFlatArea(helper, start, 20);
    VillagerEntityMCA archer = spawnArcher(helper, start);
    Zombie target = spawnTarget(helper, start.east(18));
    Zombie engaging = spawnTarget(helper, start.north(5));
    engaging.setTarget(archer);
    archer.setNoAi(true);
    archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
    seedNearbyEnemies(archer, List.of(target, engaging));

    ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
    long now = helper.getLevel().getGameTime();
    movement.start(helper.getLevel(), archer, now);
    movement.tick(helper.getLevel(), archer, now + 1);

    helper.assertTrue(RangedCombatState.current(archer).orElse(null) == RangedCombatState.KITE,
            "engaging secondary hostile did not drive kite spacing");
    helper.succeed();
}

@GameTest(batch = "mca_archer_very_close_secondary", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
public static void veryClosePassiveSecondaryTriggersEmergency(GameTestHelper helper) {
    cleanupTestEntities();
    BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
    prepareFlatArea(helper, start, 20);
    VillagerEntityMCA archer = spawnArcher(helper, start);
    Zombie target = spawnTarget(helper, start.east(18));
    Zombie passive = spawnTarget(helper, start.north(3));
    archer.setNoAi(true);
    archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
    seedNearbyEnemies(archer, List.of(target, passive));

    ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
    long now = helper.getLevel().getGameTime();
    movement.start(helper.getLevel(), archer, now);
    movement.tick(helper.getLevel(), archer, now + 1);

    helper.assertTrue(RangedCombatState.current(archer).orElse(null) == RangedCombatState.EMERGENCY_FLEE,
            "hostile inside emergency range was ignored because it was passive");
    helper.succeed();
}
```

Add the Review Focus case with a two-block wall between archer and attack target; omit the target from visible enemies but keep `ATTACK_TARGET` and `NEAREST_LIVING_ENTITIES`:

```java
@GameTest(batch = "mca_archer_occluded_attack_threat", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
public static void occludedAttackTargetStillDrivesCloseRetreat(GameTestHelper helper) {
    cleanupTestEntities();
    BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
    prepareFlatArea(helper, start, 10);
    setWallColumn(helper, start.east());
    VillagerEntityMCA archer = spawnArcher(helper, start);
    Zombie target = spawnTarget(helper, start.east(2));
    archer.setNoAi(true);
    archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
    archer.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, List.of(target));
    seedVisibleEnemies(archer, List.of());

    ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
    long now = helper.getLevel().getGameTime();
    movement.start(helper.getLevel(), archer, now);
    movement.tick(helper.getLevel(), archer, now + 1);

    helper.assertTrue(RangedCombatState.current(archer).orElse(null) == RangedCombatState.EMERGENCY_FLEE,
            "occluded ATTACK_TARGET stopped being a close escape driver");
    helper.succeed();
}
```

Use one helper for deterministic Brain fixtures:

```java
private static void seedNearbyEnemies(VillagerEntityMCA archer, List<? extends LivingEntity> enemies) {
    archer.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, List.copyOf(enemies));
    seedVisibleEnemies(archer, enemies);
}

private static void seedVisibleEnemies(VillagerEntityMCA archer, List<? extends LivingEntity> enemies) {
    archer.getBrain().setMemory(
            MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
            new NearestVisibleLivingEntities(archer, List.copyOf(enemies))
    );
}
```

- [ ] **Step 4: Run GameTests and verify the new role tests are RED**

```powershell
.\gradlew.bat :neoforge:runGameTestServer --no-daemon --no-build-cache --max-workers=1
```

Expected before implementation: at least the passive-secondary / engaging-secondary distinction fails because current movement threat selection treats all nearby valid guard enemies equivalently.

- [ ] **Step 5: Implement the minimal threat split and precedence change**

In `RangedCombatPositioning`, keep one common hostile predicate and derive the two roles:

```java
static List<LivingEntity> nearbyMovementHazards(Mob entity, LivingEntity attackTarget) {
    List<LivingEntity> hazards = new ArrayList<>();
    entity.getBrain().getMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES).ifPresent(nearby -> nearby.stream()
            .filter(candidate -> isNearbyMovementThreat(entity, candidate))
            .filter(entity.getSensing()::hasLineOfSight)
            .forEach(hazards::add));
    if (attackTarget != null
            && !hazards.contains(attackTarget)
            && entity.distanceToSqr(attackTarget) <= NEARBY_THREAT_RANGE_SQUARED
            && isNearbyMovementThreat(entity, attackTarget)) {
        hazards.add(attackTarget);
    }
    return hazards;
}

static List<LivingEntity> nearbyEscapeThreats(Mob entity, LivingEntity attackTarget) {
    return nearbyMovementHazards(entity, attackTarget).stream()
            .filter(candidate -> isEscapeDriver(entity, candidate, attackTarget))
            .toList();
}

private static boolean isEscapeDriver(Mob entity, LivingEntity candidate, LivingEntity attackTarget) {
    return candidate == attackTarget
            || entity.getLastHurtByMob() == candidate
            || candidate instanceof Mob mob && mob.getTarget() == entity
            || entity.distanceToSqr(candidate) < ArcherMovementTask.EMERGENCY_ENTER_DISTANCE_SQUARED;
}
```

Expose `EMERGENCY_ENTER_DISTANCE_SQUARED` package-private so the positioning helper uses the canonical threshold rather than copying `12.25`.

Make `nearestMovementThreat(...)` choose the closest entity from `nearbyEscapeThreats(...)`, with `fallback` retained if the list is empty.

In `selectBaseDecision(...)`, move the kite block directly after emergency handling:

```java
if (closeRangeThreat) {
    if ((currentState == RangedCombatState.EMERGENCY_FLEE || currentState == RangedCombatState.KITE)
            && threatDistanceSquared < KITE_EXIT_DISTANCE_SQUARED) {
        return new BaseStateDecision(RangedCombatState.KITE, "kite_hysteresis");
    }
    if (threatDistanceSquared < KITE_ENTER_DISTANCE_SQUARED) {
        return new BaseStateDecision(RangedCombatState.KITE, "kite_close_threat");
    }
}

if (targetDistanceSquared > attackRangeSquared) {
    return new BaseStateDecision(RangedCombatState.APPROACH, "out_of_range");
}
if (seeTime < -LOST_SIGHT_BEFORE_REPOSITION) {
    return new BaseStateDecision(RangedCombatState.REPOSITION, "lost_los");
}
```

Do not add another state, timer, or memory.

- [ ] **Step 6: Run unit + GameTests and verify GREEN**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.entity.ai.brain.tasks.ArcherMovementStateTest' --no-daemon
.\gradlew.bat :neoforge:runGameTestServer --no-daemon --no-build-cache --max-workers=1
```

Expected: the new threat-role/state tests pass and existing archer close-range tests remain green.

- [ ] **Step 7: Commit Task 1 only**

```powershell
git add common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/RangedCombatPositioning.java common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementTask.java common/src/test/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementStateTest.java neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherCombatMovementGameTests.java
git commit -m "fix: separate archer threats from movement hazards"
```

---

### Task 2: Add Hazard-Aware Retreat, Approach, and Reposition Waypoints

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/RangedCombatPositioning.java:74-384`
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementTask.java:395-479`
- Modify: `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherCombatMovementGameTests.java`

**Interfaces:**
- Change: `findGroupEscapePosition(PathfinderMob entity, List<? extends LivingEntity> escapeThreats, List<? extends LivingEntity> hazards, double desiredDistance)`
- Change: `findEmergencyEscapePosition(PathfinderMob entity, List<? extends LivingEntity> escapeThreats, List<? extends LivingEntity> hazards, double desiredDistance)`
- Add: `static Optional<Vec3> findApproachPosition(PathfinderMob entity, LivingEntity target, List<? extends LivingEntity> secondaryHazards)`
- Change: `findFiringPosition(PathfinderMob entity, LivingEntity target, List<? extends LivingEntity> hazards, double attackRangeSquared)`
- Private shared predicate: `preservesHazardClearance(Vec3 origin, Vec3 candidate, List<? extends LivingEntity> hazards)`.

- [ ] **Step 1: Write failing retreat and approach candidate tests**

Add direct positioning tests to `ArcherCombatMovementGameTests`:

```java
@GameTest(batch = "mca_archer_passive_hazard_escape", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
public static void retreatOpensFromDriverWithoutCuttingTowardPassiveHazard(GameTestHelper helper) {
    cleanupTestEntities();
    BlockPos start = helper.absolutePos(new BlockPos(12, 2, 12));
    prepareFlatArea(helper, start, 16);
    VillagerEntityMCA archer = spawnArcher(helper, start);
    Zombie driver = spawnTarget(helper, start.east(2));
    Zombie passive = spawnTarget(helper, start.west(6));
    double passiveStart = archer.distanceTo(passive);

    Vec3 destination = RangedCombatPositioning.findEmergencyEscapePosition(
            archer,
            List.of(driver),
            List.of(driver, passive),
            6.0D
    ).orElseThrow();

    helper.assertTrue(destination.distanceTo(driver.position()) > archer.distanceTo(driver),
            "escape did not open distance from the driver");
    helper.assertTrue(destination.distanceTo(passive.position()) >= Math.min(passiveStart, 6.0D),
            "escape cut into passive hazard clearance");
    helper.succeed();
}

@GameTest(batch = "mca_archer_hazard_approach", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
public static void approachCandidateRoutesAroundSecondaryHazard(GameTestHelper helper) {
    cleanupTestEntities();
    BlockPos start = helper.absolutePos(new BlockPos(12, 2, 12));
    prepareFlatArea(helper, start, 20);
    VillagerEntityMCA archer = spawnArcher(helper, start);
    Zombie target = spawnTarget(helper, start.east(18));
    Zombie passive = spawnTarget(helper, start.east(6));

    Vec3 destination = RangedCombatPositioning.findApproachPosition(
            archer,
            target,
            List.of(passive)
    ).orElseThrow(() -> new AssertionError("no detour waypoint found on open terrain"));

    helper.assertTrue(destination.distanceTo(passive.position()) >= archer.distanceTo(passive),
            "approach waypoint entered the secondary hostile's safety bubble");
    helper.assertTrue(Math.abs(destination.z - archer.getZ()) >= 1.0D,
            "approach waypoint did not route laterally around the hostile");
    helper.succeed();
}
```

Add the inside-bubble Review Focus case:

```java
@GameTest(batch = "mca_archer_inside_hazard", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
public static void approachCandidateInsideHazardBubbleDoesNotMoveCloser(GameTestHelper helper) {
    cleanupTestEntities();
    BlockPos start = helper.absolutePos(new BlockPos(12, 2, 12));
    prepareFlatArea(helper, start, 20);
    VillagerEntityMCA archer = spawnArcher(helper, start);
    Zombie target = spawnTarget(helper, start.east(18));
    Zombie passive = spawnTarget(helper, start.north(4));
    double current = archer.distanceTo(passive);

    Vec3 destination = RangedCombatPositioning.findApproachPosition(archer, target, List.of(passive)).orElseThrow();
    helper.assertTrue(destination.distanceTo(passive.position()) >= current,
            "approach moved closer while already inside a passive hazard bubble");
    helper.succeed();
}
```

- [ ] **Step 2: Add failing integration tests for blocked/direct fallback and hazard removal**

Use a four-way passive hazard ring at exactly 6 blocks so every immediate direction violates one clearance floor:

```java
@GameTest(batch = "mca_archer_hazard_ring", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
public static void blockedByHazardRingDoesNotFallBackToDirectApproach(GameTestHelper helper) {
    cleanupTestEntities();
    BlockPos start = helper.absolutePos(new BlockPos(14, 2, 14));
    prepareFlatArea(helper, start, 22);
    VillagerEntityMCA archer = spawnArcher(helper, start);
    Zombie target = spawnTarget(helper, start.east(18));
    List<Zombie> hazards = List.of(
            spawnTarget(helper, start.north(6)),
            spawnTarget(helper, start.south(6)),
            spawnTarget(helper, start.east(6)),
            spawnTarget(helper, start.west(6))
    );
    archer.setNoAi(true);
    archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
    List<LivingEntity> all = new ArrayList<>(hazards);
    all.add(target);
    seedNearbyEnemies(archer, all);

    ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
    long now = helper.getLevel().getGameTime();
    movement.start(helper.getLevel(), archer, now);
    movement.tick(helper.getLevel(), archer, now + 1);

    helper.assertTrue(RangedCombatState.current(archer).orElse(null) == RangedCombatState.APPROACH,
            "hazard ring unexpectedly changed tactical state");
    helper.assertTrue(archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).isEmpty(),
            "no-safe-waypoint case fell back to a direct target WALK_TARGET");
    helper.succeed();
}
```

For hazard removal, first seed one secondary hazard, tick once to publish a positional detour, erase that secondary from both nearby memories, clear the consumed combat walk target, advance beyond the existing retry cooldown, and tick again:

```java
@GameTest(batch = "mca_archer_hazard_removed", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 160)
public static void removedSecondaryHazardReturnsToDirectApproach(GameTestHelper helper) {
    cleanupTestEntities();
    BlockPos start = helper.absolutePos(new BlockPos(12, 2, 12));
    prepareFlatArea(helper, start, 20);
    VillagerEntityMCA archer = spawnArcher(helper, start);
    Zombie target = spawnTarget(helper, start.east(18));
    Zombie passive = spawnTarget(helper, start.east(6));
    archer.setNoAi(true);
    archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
    seedNearbyEnemies(archer, List.of(target, passive));

    ArcherMovementTask<VillagerEntityMCA> movement = new ArcherMovementTask<>(15);
    long now = helper.getLevel().getGameTime();
    movement.start(helper.getLevel(), archer, now);
    movement.tick(helper.getLevel(), archer, now + 1);

    WalkTarget detour = archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
    helper.assertTrue(detour.getTarget().currentPosition().distanceToSqr(target.position()) > 0.01D,
            "secondary hazard did not produce a positional detour waypoint");

    passive.discard();
    seedNearbyEnemies(archer, List.of(target));
    archer.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

    for (int tick = 2; tick <= 25; tick++) {
        movement.tick(helper.getLevel(), archer, now + tick);
    }

    WalkTarget direct = archer.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElseThrow();
    helper.assertTrue(direct.getTarget().currentPosition().distanceToSqr(target.position()) < 0.01D,
            "archer did not resume direct approach after the secondary hazard disappeared");
    helper.succeed();
}
```

When implementing this method, use the existing concrete `WalkTarget.getTarget().currentPosition()` API; do not inspect private tracker implementation types.

- [ ] **Step 3: Run GameTests and verify RED**

```powershell
.\gradlew.bat :neoforge:runGameTestServer --no-daemon --no-build-cache --max-workers=1
```

Expected: new method signatures do not exist yet and/or direct approach still cuts through hazards.

- [ ] **Step 4: Implement shared hazard-clearance logic**

Use actual distance, not squared-distance arithmetic, because the requirement is `min(currentDistance, 6)`:

```java
private static boolean preservesHazardClearance(
        Vec3 origin,
        Vec3 candidate,
        List<? extends LivingEntity> hazards
) {
    double desiredClearance = Math.sqrt(ArcherMovementTask.KITE_ENTER_DISTANCE_SQUARED);
    for (LivingEntity hazard : hazards) {
        double required = Math.min(origin.distanceTo(hazard.position()), desiredClearance);
        if (candidate.distanceTo(hazard.position()) + 1.0E-6D < required) {
            return false;
        }
    }
    return true;
}
```

Rename `collectReachableEscapePositions(...)` to `collectReachablePositions(...)` and keep its current graph radius, vertical range, stable-destination, walkability, and standing-space behavior unchanged. Reuse it for retreat and approach instead of adding another flood-fill.

- [ ] **Step 5: Implement hazard-aware retreat scoring**

Change the retreat entry points to accept both collections. Candidate validity is:

```java
double currentDriverDistanceSquared = minimumDistanceSquared(origin, escapeThreats);
double candidateDriverDistanceSquared = minimumDistanceSquared(candidatePosition, escapeThreats);
boolean improvesDrivers = candidateDriverDistanceSquared > currentDriverDistanceSquared + MIN_USEFUL_DISTANCE_GAIN;
boolean preservesHazards = preservesHazardClearance(origin, candidatePosition, hazards);
```

For `findGroupEscapePosition`, only consider driver-improving candidates that preserve hazard clearance; retain existing onward-space preference and non-closing-driver rules.

For `findEmergencyEscapePosition`, return the best hazard-safe candidate first. If none exists, permit an emergency fallback ranked in this exact order:

```text
1. larger minimum distance to escape drivers
2. larger minimum distance to all hazards
3. larger onward reachable space
4. fewer travel steps
```

Do not weaken the existing one-entry-pocket/onward-space checks for the normal safe path.

- [ ] **Step 6: Implement short hazard-aware approach waypoints**

Add:

```java
private static final double MAX_APPROACH_BACKTRACK = 0.5D;

static Optional<Vec3> findApproachPosition(
        PathfinderMob entity,
        LivingEntity target,
        List<? extends LivingEntity> secondaryHazards
) {
    Vec3 origin = entity.position();
    double currentTargetDistance = origin.distanceTo(target.position());
    // Probe only the bounded forward/lateral offsets used by combat steering.
    // Reject candidates that are unstable, collide, violate endpoint hazard
    // clearance, or cross a hazard on the straight segment from the archer.
    // Rank the survivors by target distance, then minimum hazard distance.
    ...
}
```

Approach deliberately does **not** reuse the retreat reachable-space graph: topology search is reserved for escape decisions, while approach remains a cheap local steering probe. If `secondaryHazards` is empty, `ArcherMovementTask` does not call this helper and preserves its current direct `EntityTracker` approach.

In `publishApproach(...)`:

```java
List<LivingEntity> hazards = RangedCombatPositioning.nearbyMovementHazards(entity, target);
List<LivingEntity> secondaryHazards = hazards.stream().filter(hazard -> hazard != target).toList();
if (secondaryHazards.isEmpty()) {
    publishDirectApproach(entity, target, force);
    return;
}

Optional<Vec3> waypoint = RangedCombatPositioning.findApproachPosition(entity, target, secondaryHazards);
if (waypoint.isEmpty()) {
    clearCombatWalkTarget(entity);
    scheduleWalkTargetRetry(entity);
    logMovementIntent(entity, "hold", "approach_blocked_by_hazard", null);
    return;
}
publishCombatWalkTarget(entity, new WalkTarget(waypoint.orElseThrow(), (float) SPEED_MODIFIER, 0), force);
```

Extract `publishDirectApproach(...)` only to avoid duplicating the existing `EntityTracker` construction between normal approach and fallback-free callers.

- [ ] **Step 7: Make reposition consume all hazards and use safe approach fallback**

Change `findFiringPosition(...)` to receive `List<? extends LivingEntity> hazards`. Replace the current single `movementThreat` distance check with `preservesHazardClearance(entity.position(), candidate, hazards)`.

If no firing position is found, call the same secondary-hazard approach selection. Only use the old direct `EntityTracker` fallback when no secondary hazards are present.

- [ ] **Step 8: Run GameTests and verify GREEN**

```powershell
.\gradlew.bat :neoforge:runGameTestServer --no-daemon --no-build-cache --max-workers=1
```

Expected: approach detours around passive hostiles; hazard-ring case holds rather than cutting through; retreat still escapes cave/crowd fixtures; removed hazard resumes direct approach.

- [ ] **Step 9: Commit Task 2 only**

```powershell
git add common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/RangedCombatPositioning.java common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementTask.java neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherCombatMovementGameTests.java
git commit -m "fix: steer archer paths around nearby hostiles"
```

---

### Task 3: Reject Corner-Doomed Strafes and Prefer the Safer Side

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/RangedCombatPositioning.java:386-406`
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementTask.java:326-389`
- Modify: `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherCombatMovementGameTests.java`

**Interfaces:**
- Add: `static float findBestStrafeDirection(PathfinderMob entity, List<? extends LivingEntity> hazards)` returning `-1.0F`, `0.0F`, or `1.0F`.
- Keep: `isStrafeSideWalkable(...)` for existing tests, but redefine it as meeting the minimum `1.5`-block body-sweep clearance.
- Add private `StrafeCandidate(float direction, double clearance, double hazardDistanceSquared)` record.

- [ ] **Step 1: Write the failing beyond-one-block corner test**

At yaw `0`, positive lateral direction is south. Put a two-block-high wall two blocks south: the old one-block endpoint probe is clear, but a 1.5-block body sweep reaches the wall.

```java
@GameTest(batch = "mca_archer_strafe_sweep", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
public static void strafeRejectsSideBlockedJustBeyondOldEndpointProbe(GameTestHelper helper) {
    cleanupTestEntities();
    BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
    prepareFlatArea(helper, start, 6);
    VillagerEntityMCA archer = spawnArcher(helper, start);
    archer.setYRot(0.0F);
    setWallColumn(helper, start.south(2));

    helper.assertTrue(!RangedCombatPositioning.isStrafeSideWalkable(archer, 1.0F),
            "strafe side with less than 1.5 blocks of body clearance was accepted");
    helper.assertTrue(RangedCombatPositioning.isStrafeSideWalkable(archer, -1.0F),
            "open opposite strafe side was rejected");
    helper.succeed();
}
```

- [ ] **Step 2: Add failing side-selection tests**

```java
@GameTest(batch = "mca_archer_strafe_hazard_side", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
public static void strafePrefersSideFartherFromPassiveHazard(GameTestHelper helper) {
    cleanupTestEntities();
    BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
    prepareFlatArea(helper, start, 10);
    VillagerEntityMCA archer = spawnArcher(helper, start);
    archer.setYRot(0.0F);
    Zombie southHazard = spawnTarget(helper, start.south(7));

    float direction = RangedCombatPositioning.findBestStrafeDirection(archer, List.of(southHazard));
    helper.assertTrue(direction == -1.0F, "strafe did not prefer the side farther from the hazard: " + direction);
    helper.succeed();
}

@GameTest(batch = "mca_archer_strafe_stable_tie", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 120)
public static void equalStrafeSidesUseStableTieBreak(GameTestHelper helper) {
    cleanupTestEntities();
    BlockPos start = helper.absolutePos(new BlockPos(8, 2, 8));
    prepareFlatArea(helper, start, 10);
    VillagerEntityMCA archer = spawnArcher(helper, start);
    archer.setYRot(0.0F);

    float first = RangedCombatPositioning.findBestStrafeDirection(archer, List.of());
    float second = RangedCombatPositioning.findBestStrafeDirection(archer, List.of());
    helper.assertTrue(first != 0.0F && first == second, "equal strafe sides did not produce a stable direction");
    helper.succeed();
}
```

- [ ] **Step 3: Run GameTests and verify RED**

```powershell
.\gradlew.bat :neoforge:runGameTestServer --no-daemon --no-build-cache --max-workers=1
```

Expected: old one-block probe accepts the south side and `findBestStrafeDirection` does not exist.

- [ ] **Step 4: Implement body-sweep scoring**

Add constants:

```java
private static final double STRAFE_PROBE_STEP = 0.5D;
private static final double MIN_STRAFE_CLEARANCE = 1.5D;
private static final double MAX_STRAFE_CLEARANCE = 2.5D;
```

Score a side without mutating the entity:

```java
private static StrafeCandidate scoreStrafeSide(
        PathfinderMob entity,
        float direction,
        List<? extends LivingEntity> hazards
) {
    Vec3 lateral = strafeDirectionVector(entity, direction);
    double clearance = 0.0D;
    Vec3 furthest = entity.position();
    for (double distance = STRAFE_PROBE_STEP; distance <= MAX_STRAFE_CLEARANCE; distance += STRAFE_PROBE_STEP) {
        Vec3 candidate = entity.position().add(lateral.scale(distance));
        AABB movedBox = entity.getBoundingBox().move(lateral.scale(distance));
        if (!entity.level().noCollision(entity, movedBox)
                || !isWalkableDestination(entity, candidate)
                || !hasStandingSpace(entity, candidate)) {
            break;
        }
        clearance = distance;
        furthest = candidate;
    }
    if (clearance < MIN_STRAFE_CLEARANCE) {
        return null;
    }
    return new StrafeCandidate(direction, clearance, minimumDistanceSquared(furthest, hazards));
}
```

`findBestStrafeDirection(...)` scores `-1` and `+1`, then compares: larger clearance, larger hazard distance, stable UUID-bit tie-break. `isStrafeSideWalkable(...)` delegates to the same geometry with an empty hazard list and checks non-null.

- [ ] **Step 5: Replace random-first strafe startup**

In `tryStartStrafe(...)`, replace `getRandom().nextBoolean()` / preferred-opposite probing with:

```java
List<LivingEntity> hazards = RangedCombatPositioning.nearbyMovementHazards(entity, target);
float direction = RangedCombatPositioning.findBestStrafeDirection(entity, hazards);
if (direction == 0.0F) {
    this.strafeCooldown = nextStrafeCooldown(entity);
    logMovementIntent(entity, "strafe_skip", "no_safe_side", null);
    return;
}
this.strafeDirection = direction;
```

During an active burst, retain the existing collision/stall cancellation. Recheck only whether the current side still has minimum immediate clearance and hazard safety; do not switch to the other side mid-burst.

- [ ] **Step 6: Run GameTests and verify GREEN**

```powershell
.\gradlew.bat :neoforge:runGameTestServer --no-daemon --no-build-cache --max-workers=1
```

Expected: corner/beyond-endpoint side is rejected before a strafe starts; passive-hazard side loses; exact ties stay stable; existing blocked-side/collision tests remain green.

- [ ] **Step 7: Commit Task 3 only**

```powershell
git add common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/RangedCombatPositioning.java common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementTask.java neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherCombatMovementGameTests.java
git commit -m "fix: make archer strafing corner aware"
```

---

### Task 4: Replace Quadratic Bow Compensation with Height-Aware Trajectory Solving

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/RangedWeaponHelper.java`
- Modify: `common/src/main/java/net/conczin/mca/entity/VillagerEntityMCA.java:1603-1635`
- Create: `common/src/test/java/net/conczin/mca/entity/ai/RangedWeaponHelperTest.java`
- Create: `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherBowTrajectoryGameTests.java`
- Verify unchanged: `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherSpiderCombatGameTests.java`

**Interfaces:**
- Produces: `public static Vec3 calculateBowShotVector(Vec3 projectilePosition, Vec3 targetBasePosition, double targetHeight)`.
- Consumer: `VillagerEntityMCA.performRangedAttack(...)` bow branch only.

- [ ] **Step 1: Write deterministic failing vector tests**

Create `RangedWeaponHelperTest`:

```java
package net.conczin.mca.entity.ai;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RangedWeaponHelperTest {
    private static final double EPSILON = 1.0E-9D;

    @Test
    void levelSkeletonUsesMidpointAndArrowPhysics() {
        Vec3 shot = RangedWeaponHelper.calculateBowShotVector(
                new Vec3(0.0D, 1.5D, 0.0D),
                new Vec3(10.0D, 0.0D, 0.0D),
                2.0D
        );
        assertVector(shot, 10.0D, 0.365D, 0.0D);
    }

    @Test
    void uphillAndDownhillCompensateForActualTrajectory() {
        Vec3 uphill = RangedWeaponHelper.calculateBowShotVector(new Vec3(0, 1.5, 0), new Vec3(10, 3, 0), 2.0D);
        Vec3 downhill = RangedWeaponHelper.calculateBowShotVector(new Vec3(0, 1.5, 0), new Vec3(10, -2, 0), 2.0D);
        assertVector(uphill, 10.0D, 3.478D, 0.0D);
        assertVector(downhill, 10.0D, -1.611D, 0.0D);
    }

    @Test
    void spiderAndCaveSpiderUseTheirActualBodyMidpoints() {
        Vec3 spider = RangedWeaponHelper.calculateBowShotVector(new Vec3(0, 1.5, 0), new Vec3(10, 0, 0), 0.9D);
        Vec3 caveSpider = RangedWeaponHelper.calculateBowShotVector(new Vec3(0, 1.5, 0), new Vec3(10, 0, 0), 0.5D);
        assertVector(spider, 10.0D, -0.186D, 0.0D);
        assertVector(caveSpider, 10.0D, -0.385D, 0.0D);
    }

    @Test
    void zeroHorizontalDistanceProducesFiniteMidpointVector() {
        Vec3 shot = RangedWeaponHelper.calculateBowShotVector(new Vec3(0, 1.5, 0), new Vec3(0, 3, 0), 2.0D);
        assertTrue(Double.isFinite(shot.y));
        assertVector(shot, 0.0D, 2.5D, 0.0D);
    }

    private static void assertVector(Vec3 actual, double x, double y, double z) {
        assertEquals(x, actual.x, EPSILON);
        assertEquals(y, actual.y, EPSILON);
        assertEquals(z, actual.z, EPSILON);
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.entity.ai.RangedWeaponHelperTest' --no-daemon
```

Expected: compilation fails because `calculateBowShotVector(...)` does not exist.

- [ ] **Step 3: Implement the pure bow-vector helper**

In `RangedWeaponHelper`:

```java
private static final double BOW_TARGET_HEIGHT_FRACTION = 0.5D;
private static final double BOW_PROJECTILE_SPEED = 1.6D;
private static final double ARROW_AIR_DRAG = 0.99D;
private static final double ARROW_GRAVITY = 0.05D;

public static Vec3 calculateBowShotVector(
        Vec3 projectilePosition,
        Vec3 targetBasePosition,
        double targetHeight
) {
    // Aim at the target midpoint, then solve the vertical component against
    // Minecraft 1.21.1 arrow drag/gravity at the retained 1.6 launch speed.
    // See the implementation and RangedWeaponHelperTest for the bounded solver.
}
```

Do not clamp by mob type and do not add spider-specific branches; bounding-box height already handles low entities generically.

- [ ] **Step 4: Make `VillagerEntityMCA` consume the helper**

Replace the bow branch's `flightTicks` / `gravityCompensation` code with:

```java
Vec3 shot = RangedWeaponHelper.calculateBowShotVector(
        persistentProjectileEntity.position(),
        target.position(),
        target.getBbHeight()
);
persistentProjectileEntity.shoot(shot.x, shot.y, shot.z, 1.6F, 3.0F);
```

Leave crossbow shooting, arrow creation, sounds, and spawning unchanged.

- [ ] **Step 5: Run the vector tests and verify GREEN**

```powershell
.\gradlew.bat :common:test --tests 'net.conczin.mca.entity.ai.RangedWeaponHelperTest' --no-daemon
```

- [ ] **Step 6: Add live skeleton elevation GameTests**

Create `ArcherBowTrajectoryGameTests` with these three entry points:

Add three tests:

```java
@GameTest(batch = "mca_archer_bow_level_skeleton", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 360)
public static void archerHitsLevelSkeleton(GameTestHelper helper) {
    runSkeletonTrajectoryCase(helper, new BlockPos(8, 22, 8), new BlockPos(18, 22, 8), "level");
}

@GameTest(batch = "mca_archer_bow_uphill_skeleton", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 360)
public static void archerHitsSkeletonTwoBlocksHigher(GameTestHelper helper) {
    runSkeletonTrajectoryCase(helper, new BlockPos(8, 22, 8), new BlockPos(18, 24, 8), "uphill");
}

@GameTest(batch = "mca_archer_bow_downhill_skeleton", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 360)
public static void archerHitsSkeletonTwoBlocksLower(GameTestHelper helper) {
    runSkeletonTrajectoryCase(helper, new BlockPos(8, 24, 8), new BlockPos(18, 22, 8), "downhill");
}
```

Use these concrete helpers in the same class:

```java
private static final List<Entity> TEST_ENTITIES = new ArrayList<>();

private static void runSkeletonTrajectoryCase(
        GameTestHelper helper,
        BlockPos relativeArcherPos,
        BlockPos relativeSkeletonPos,
        String label
) {
    cleanupTestEntities();
    BlockPos archerPos = helper.absolutePos(relativeArcherPos);
    BlockPos skeletonPos = helper.absolutePos(relativeSkeletonPos);
    preparePlatform(helper, archerPos);
    preparePlatform(helper, skeletonPos);

    VillagerEntityMCA archer = VillagerFactory.newVillager(helper.getLevel())
            .withAge(0)
            .withProfession(ProfessionsMCA.ARCHER)
            .withPosition(Vec3.atBottomCenterOf(archerPos))
            .withName("Bow Trajectory Archer")
            .spawn(MobSpawnType.STRUCTURE);
    archer.setItemSlot(EquipmentSlot.MAINHAND, Items.BOW.getDefaultInstance());
    archer.setOnGround(true);
    archer.setNoAi(true);
    archer.refreshBrain(helper.getLevel());

    Skeleton skeleton = EntityType.SKELETON.create(helper.getLevel());
    if (skeleton == null) {
        throw new IllegalStateException("failed to create skeleton target");
    }
    skeleton.absMoveTo(skeletonPos.getX() + 0.5D, skeletonPos.getY(), skeletonPos.getZ() + 0.5D);
    skeleton.setNoAi(true);
    helper.getLevel().addFreshEntity(skeleton);

    TEST_ENTITIES.add(archer);
    TEST_ENTITIES.add(skeleton);
    archer.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, skeleton);
    archer.getBrain().setMemory(MemoryModuleType.NEAREST_LIVING_ENTITIES, List.of(skeleton));

    float initialHealth = skeleton.getHealth();
    BowTask<VillagerEntityMCA> bowTask = new BowTask<>(20, 15);
    long startTime = helper.getLevel().getGameTime();
    bowTask.start(helper.getLevel(), archer, startTime);
    int[] ticks = {0};
    helper.onEachTick(() -> {
        ticks[0]++;
        bowTask.tick(helper.getLevel(), archer, startTime + ticks[0]);
        if (skeleton.getHealth() < initialHealth || !skeleton.isAlive()) {
            helper.succeed();
            return;
        }
        if (ticks[0] >= 300) {
            helper.fail("archer never hit " + label + " skeleton; health=" + skeleton.getHealth()
                    + ", state=" + RangedCombatState.current(archer).orElse(null));
        }
    });
}

private static void preparePlatform(GameTestHelper helper, BlockPos center) {
    helper.getLevel().getChunk(center);
    for (int x = -2; x <= 2; x++) {
        for (int z = -2; z <= 2; z++) {
            helper.getLevel().setBlock(center.offset(x, -1, z), Blocks.STONE.defaultBlockState(), 3);
            for (int y = 0; y <= 3; y++) {
                helper.getLevel().setBlock(center.offset(x, y, z), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }
}

private static void cleanupTestEntities() {
    TEST_ENTITIES.removeIf(entity -> {
        if (entity.isAlive()) {
            entity.discard();
        }
        return true;
    });
}
```

This succeeds on the first observed health reduction rather than requiring every random-inaccuracy shot to hit.

- [ ] **Step 7: Run live trajectory + existing spider GameTests**

```powershell
.\gradlew.bat :neoforge:runGameTestServer --no-daemon --no-build-cache --max-workers=1
```

Expected: level/uphill/downhill skeleton tests pass, `archerKillsSpiderWithBow` passes, and `archerKillsCaveSpiderWithBow` passes.

- [ ] **Step 8: Commit Task 4 only**

```powershell
git add common/src/main/java/net/conczin/mca/entity/ai/RangedWeaponHelper.java common/src/main/java/net/conczin/mca/entity/VillagerEntityMCA.java common/src/test/java/net/conczin/mca/entity/ai/RangedWeaponHelperTest.java neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherBowTrajectoryGameTests.java
git commit -m "fix: use height-aware linear archer aim"
```

---

## Final Verification

After all four task commits:

- [ ] Run all common unit tests and loader compilation:

```powershell
.\gradlew.bat :common:test :common:compileJava :fabric:compileJava :neoforge:compileJava --no-daemon
```

- [ ] Run the full NeoForge GameTest suite once, serially:

```powershell
.\gradlew.bat :neoforge:runGameTestServer --no-daemon --no-build-cache --max-workers=1
```

- [ ] Explicitly confirm these archer regressions in the output: passive secondary does not drive retreat, engaging secondary does, hazard-aware approach detours, hazard ring does not direct-fallback, emergency/kite does not ping-pong into approach, strafe sweep rejects the near corner, level/uphill/downhill skeleton trajectory passes, spider/cave-spider bow kills pass, cave-corner escape passes, blocked-far-side escape passes, one-entry-pocket escape passes.

- [ ] Check formatting/whitespace only in the relevant diff:

```powershell
git diff --check -- common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/RangedCombatPositioning.java common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementTask.java common/src/main/java/net/conczin/mca/entity/ai/RangedWeaponHelper.java common/src/main/java/net/conczin/mca/entity/VillagerEntityMCA.java common/src/test/java/net/conczin/mca/entity/ai/brain/tasks/ArcherMovementStateTest.java common/src/test/java/net/conczin/mca/entity/ai/RangedWeaponHelperTest.java neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherCombatMovementGameTests.java neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ArcherBowTrajectoryGameTests.java
```

- [ ] Inspect `git status --short` and verify no unrelated dirty files were staged or changed by this implementation.

- [ ] If the full GameTest run contains pre-existing unrelated failures, record them separately with their exact test names. Do not claim the full suite is green unless it is actually green.

## Self-Review Notes

- **Spec coverage:** threat roles, state coherence, hazard-aware retreat/approach/reposition, corner-safe strafe, low-height/elevation-aware bow aim, and preservation of cave escape are each owned by a concrete task and test.
- **Placeholder scan:** no `TBD`, `TODO`, “similar to Task N”, or unspecified implementation/error-handling steps are required by the plan.
- **Type consistency:** `nearbyMovementHazards`, `nearbyEscapeThreats`, `findApproachPosition`, the changed retreat signatures, `findBestStrafeDirection`, and `calculateBowShotVector` are defined once and consumed by later steps with matching types.
- **Review Focus:** all five high-risk conditions listed above have an owning test in Task 1, Task 2, or Task 4.
- **Scope discipline:** no generic navigation rewrite, custom pathfinder, target-priority rewrite, projectile-speed change, crossbow change, or unrelated worktree cleanup is included.
