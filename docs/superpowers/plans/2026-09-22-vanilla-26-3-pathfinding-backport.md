# Vanilla 26.3 Pathfinding Backport And Sequential Forward-Port Implementation Plan

> **Implementation rule:** Minecraft 26.3 is the canonical design oracle. Implement missing semantics on 1.21.1, then forward-port behavior through 26.1.2 and 26.2 to 26.3 while deleting compatibility shims as soon as vanilla owns them.

**Goal:** Bring MCA ground/villager pathfinding into semantic parity with the useful parts of vanilla 26.3 without losing MCA's stronger navigation features, without coupling sensing range to path-search range, and without carrying duplicated compatibility logic into newer branches.

**Architecture:** `MCAGroundPathNavigation` remains MCA's integration owner. On 1.21.1 it adapts the missing 26.3 required-path-length concept with a 48-block ordinary floor and a 768-node minimum PathFinder budget. MCA's configured 160-block horizon remains exceptional: long-distance producers keep the real logical destination, and nearby static targets may retry at the extended horizon only after the ordinary search cannot reach them. On 26.1.2+ vanilla owns ordinary required path length and mutable PathFinder budget, so the 1.21.1 compatibility implementation is removed and only MCA-specific policy remains.

**Tech stack:** Java 21 on 1.21.1; Java 25 on 26.1.2/26.2/26.3; Gradle multi-loader; NeoForge GameTest; JUnit 5.

**Spec:** `docs/superpowers/specs/2026-09-22-vanilla-26-3-pathfinding-backport-design.md`

## Canonical Sources

- 26.3 design oracle: `C:\Users\Mik\Downloads\MCA\mc_source_code\local-source-26.3\src`
- 1.21.1 implementation baseline: `C:\Users\Mik\Downloads\MCA\mc_source_code\local-source-1.21.1\src`
- 26.1.2 ownership/API checkpoint: `C:\Users\Mik\Downloads\MCA\mc_source_code\minecraft-patched-26.1.2.76-sources\net\minecraft`
- 26.2 ownership/API checkpoint: `C:\Users\Mik\Downloads\MCA\mc_source_code\local-source-26.2\src`

Intermediate sources answer “who owns this behavior on this branch?” They do not replace 26.3 as the design target.

## Version Contract

| Branch | Sensing/default `FOLLOW_RANGE` | Ordinary navigation | Baseline node budget | MCA extended horizon |
| --- | --- | --- | --- | --- |
| `dev/1.21.1` | keep MCA 48 | adapt 26.3 semantics to minimum 48 | minimum 768 | 160 |
| `dev/26.1.2` | default 16; retain config override surface | vanilla native 48 | vanilla native ~768 | 160 |
| `dev/26.2` | carry audited 26.1.2 decision | vanilla native 48 | vanilla native ~768 | 160 |
| `dev/26.3` | default 16 unless a regression proves documented MCA divergence | vanilla native 48 | vanilla native ~768 | 160 |

Do not use `villagerPathfindingDistance` to set ordinary `requiredPathLength` on any forward branch.

## Global Constraints

- Preserve all unrelated dirty-worktree changes. Do not reset, clean, checkout-over, or blanket-stash.
- Do not switch the current dirty worktree between version branches. Forward-port in a clean worktree after the 1.21.1 task-owned changes are committed, or create separate Git worktrees for the target branches.
- Keep 26.3 as the source oracle even while compiling against older APIs.
- Keep the logical destination equal to the real bed/building/grave/etc. target.
- Keep `CANT_REACH_WALK_TARGET_SINCE` as the canonical movement-failure timer.
- Keep `maxVisitedNodesMultiplier = 1.0` unless a failing regression plus source evidence requires otherwise.
- Do not globally make every path a 160-block / 2560-node search.
- Do not add a second path-budget manager, route planner, retry state machine, or reachability cache.
- Do not force-load chunks to satisfy a path request.
- Preserve MCA's `BedApproachTarget`, multi-target bed logic, fence-gate/door behavior, raised-start/barrier collision checks, climb traversal, fall resync, and selective exact collision checks unless a 26.3 owner demonstrably replaces them.
- Apply the `java-code-review-cleanup` lenses—reuse, quality, correctness, efficiency—to every implementation slice and the final diff.

---

## Task 1: Finish The 26.3 Source-Comparison Ledger

**Files:**
- Modify: `docs/superpowers/plans/2026-09-22-vanilla-26-3-pathfinding-backport.md` only to record final dispositions
- Read only: vanilla 1.21.1, 26.1.2, 26.2, and canonical 26.3 source owners
- Read only: current MCA navigation/brain owners

- [ ] **Step 1: Freeze the source boundary**

Audit these vanilla owners against 26.3:

```text
net.minecraft.world.entity.ai.navigation.PathNavigation
net.minecraft.world.entity.ai.navigation.GroundPathNavigation
net.minecraft.world.level.pathfinder.PathFinder
net.minecraft.world.level.pathfinder.NodeEvaluator
net.minecraft.world.level.pathfinder.WalkNodeEvaluator
net.minecraft.world.level.pathfinder.Path
net.minecraft.world.level.pathfinder.PathType
net.minecraft.world.level.pathfinder.PathfindingContext
net.minecraft.world.entity.npc.villager.Villager
net.minecraft.world.entity.ai.behavior.MoveToTargetSink
net.minecraft.world.entity.ai.behavior.SetWalkTargetFromBlockMemory
net.minecraft.world.entity.ai.behavior.InteractWithDoor
net.minecraft.world.entity.ai.behavior.SleepInBed
net.minecraft.world.entity.ai.behavior.VillagerGoalPackages
```

For each meaningful 26.3 behavior, inspect the corresponding MCA owner before assigning a disposition.

- [ ] **Step 2: Record one disposition for every meaningful change**

Use:

| Change | 1.21.1 | 26.3 | MCA owner/overlap | Disposition | Proof |
| --- | --- | --- | --- | --- | --- |
| independent required path length | tied to `FOLLOW_RANGE` | independent, villager=48 | `MCAGroundPathNavigation` | Adapt on 1.21.1; vanilla-owned on 26.1.2+ | Task 2/3 regressions |
| mutable PathFinder budget | constructor-final | mutable + refreshed from effective path length | none | Adapt floor on 1.21.1; delete shim on 26.1.2+ | Task 2/3 regressions |
| recompute deferral | absent in base | base checks `canUpdatePath()` | MCA has 1.21.1 override | Keep on 1.21.1; delete on 26.1.2+ | airborne recompute test |
| sleep-start memory cleanup | `Villager.startSleeping()` | `SleepInBed` | already present | Already present / ownership moved | source comparison |
| `getSurfaceY()` ownership | private WATER helper | public floatable-fluid helper | MCA shadows private helper without callers | Remove shadow on <=26.2; use native 26.3 owner | source visibility / compile / fluid regression if needed |
| large-mob danger malus | old width handling | `BIG_MOBS_CLOSE_TO_DANGER` | likely irrelevant to normal MCA villager width | Decide from evidence | focused geometry only if relevant |

- [ ] **Step 3: Explicitly classify 26.3-only/version plumbing**

Mark profiler/debug subscriptions, annotations, local renames, mapping churn, and loader-only hook differences as **Not ported** unless they change MCA runtime behavior.

- [ ] **Step 4: Check the ledger before touching production**

No production change starts from “26.3 has this method.” It starts from a ledger row showing behavior, owner, and test.

---

## Task 2: Write RED Regressions For The 1.21.1 Navigation Split

**Files:**
- Modify: `common/src/test/java/net/conczin/mca/ConfigPathfindingTest.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/entity/ai/navigation/MCAGroundPathNavigationGameTests.java`
- Modify as needed: `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ExtendedWalkTowardsTaskGameTests.java`

- [ ] **Step 1: Pin 1.21.1 config defaults**

Extend `ConfigPathfindingTest` to assert:

```java
assertEquals(160, config.getVillagerPathfindingDistance());
assertEquals(48, config.getVillagerFollowRange());
```

Keep existing clamp behavior unchanged.

- [ ] **Step 2: Add a RED ordinary-navigation-floor GameTest**

In `MCAGroundPathNavigationGameTests`, add:

```text
ordinaryNavigationRemains48WhenFollowRangeIsLowered
```

Fixture:

1. spawn an MCA villager;
2. lower its `FOLLOW_RANGE` to 8;
3. request an ordinary unmarked path to an open target about 30 blocks away;
4. assert the path reaches the target;
5. assert `FOLLOW_RANGE` remains 8.

Before the production change, the 1.21.1 base path horizon is still derived from 8, so this must fail for the right reason.

- [ ] **Step 3: Rewrite the long-distance horizon regression around the new ordinary floor**

The existing `blockWalkTargetExtendsOnlyItsPathHorizon` uses a 30-block target and therefore becomes an ordinary-path case once navigation has a 48 floor.

Change it to:

- target beyond 48 but comfortably inside 160;
- `WALK_TARGET` owned by `LongDistancePathTarget`;
- ordinary request cannot reach the far destination;
- marked/static extended request reaches or produces useful progress toward the real target;
- `FOLLOW_RANGE` is unchanged.

- [ ] **Step 4: Strengthen the nearby-detour regression**

The existing wall detour only proves a route longer than the temporary `FOLLOW_RANGE = 8`.

Make the detour longer than the 48-block ordinary navigation floor while keeping the destination itself geometrically nearby. Assert:

- ordinary first search cannot reach within 48;
- the static-target extended retry reaches through the longer route;
- the logical target never changes.

This pins the intentional “cheap ordinary first, extended only when needed” behavior.

- [ ] **Step 5: Pin classification against navigation capability, not sensing**

Update/add an `ExtendedWalkTowardsTaskGameTests` case:

- set `FOLLOW_RANGE = 8`;
- place the final target roughly 30 blocks away;
- run the producer;
- assert it is treated as ordinary/final-target movement rather than a `LongDistancePathTarget`, because ordinary navigation capability is 48.

Retain the existing diagonal/geometric-distance regression.

- [ ] **Step 6: Run RED verification**

```powershell
.\gradlew.bat common:test --no-daemon
.\gradlew.bat common:compileJava neoforge:compileJava --no-daemon
.\gradlew.bat neoforge:runGameTestServer --no-daemon --no-build-cache --max-workers=1
```

Record the expected new failures. Do not weaken existing pathfinding tests to make the suite green.

---

## Task 3: Implement The Minimum 1.21.1 Compatibility Layer

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/navigation/MCAGroundPathNavigation.java`
- Keep shallow unless evidence requires otherwise: `common/src/main/java/net/conczin/mca/entity/ai/navigation/LongDistancePathTarget.java`
- Modify only if call-site semantics require it: `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ExtendedWalkTowardsTask.java`

- [ ] **Step 1: Name the two vanilla-derived constants**

In `MCAGroundPathNavigation`, introduce narrowly scoped constants for:

```java
private static final float REQUIRED_PATH_LENGTH = 48.0F;
private static final int VISITED_NODES_PER_BLOCK = 16;
```

Do not put the 160 long-distance setting into either constant; it remains config-owned.

- [ ] **Step 2: Centralize ordinary navigation capability**

Add one helper used by both normal path creation and long-distance classification:

```java
private static float getOrdinaryPathLength(Mob mob) {
    return Math.max(
            (float)mob.getAttributeValue(Attributes.FOLLOW_RANGE),
            REQUIRED_PATH_LENGTH
    );
}
```

If producer code needs the classification helper, keep the existing public `requiresExtendedPath(...)` and make it delegate to this ordinary path length.

`requiresExtendedPath` must remain geometric:

```java
return mob.blockPosition().distSqr(target)
        > ordinaryPathLength * ordinaryPathLength;
```

- [ ] **Step 3: Floor the constructor-time PathFinder budget**

1.21.1 `PathFinder.maxVisitedNodes` is final and has no setter. Adapt the 26.3 model at the existing owner:

```java
int ordinaryBudget = Mth.floor(REQUIRED_PATH_LENGTH * VISITED_NODES_PER_BLOCK);
int effectiveBudget = Math.max(maxVisitedNodes, ordinaryBudget);
return new PathFinder(this.nodeEvaluator, effectiveBudget);
```

Do not set it from `villagerPathfindingDistance`.

- [ ] **Step 4: Make every ordinary path use the 48 floor**

Refactor the current `createPath(Set<BlockPos>, ...)` override:

1. calculate `ordinaryPathLength = getOrdinaryPathLength(mob)`;
2. calculate `extendedPathLength = max(configured 160 horizon, ordinaryPathLength)`;
3. for non-static/non-current targets, call the protected vanilla overload with `ordinaryPathLength`;
4. for a static current walk target beyond ordinary geometric range, use the extended horizon;
5. for a static target inside ordinary range, run the ordinary search first;
6. only if the ordinary path is non-null/non-reaching and an extended horizon exists, retry with the extended horizon;
7. keep whichever result reaches or makes strictly better progress.

Do not introduce an additional state flag or cache.

- [ ] **Step 5: Keep `LongDistancePathTarget` as intent metadata**

Do not move navigation logic into the marker. Its job is still to identify producer-owned long-distance intent/handoff. The navigation owner remains `MCAGroundPathNavigation`.

- [ ] **Step 6: Preserve the 1.21.1 recompute compatibility override**

Keep the current `recomputePath()` deferral on this branch. Vanilla 1.21.1 lacks the newer base-class `canUpdatePath()` guard.

Do not copy this override to 26.1.2+.

- [ ] **Step 7: Remove the non-overriding `getSurfaceY()` shadow**

Vanilla 1.21.1 declares `GroundPathNavigation.getSurfaceY()` private. The current MCA method with the same name has no MCA call sites and is not an override of the vanilla helper.

Remove the MCA shadow method and its now-unused WATER/fluid imports rather than treating it as a 26.3 backport seam. Let vanilla 1.21.1 continue owning its private WATER surface calculation.

- [ ] **Step 8: Add/retain unloaded-chunk proof**

Add a focused GameTest if the current suite does not already prove this: a 160-horizon request must not synchronously load/generate a previously unloaded far chunk.

- [ ] **Step 9: Run GREEN verification**

```powershell
.\gradlew.bat common:test --no-daemon
.\gradlew.bat common:compileJava neoforge:compileJava --no-daemon
.\gradlew.bat neoforge:runGameTestServer --no-daemon --no-build-cache --max-workers=1
```

All Task 2 regressions must now pass.

---

## Task 4: Clean The 1.21.1 Diff Before Forward-Porting

**Review scope:** only the pathfinding backport plus nearby code required to prove it.

- [ ] **Step 1: Reuse review**

Confirm there is:

- one ordinary-path-length helper;
- one geometric long-distance predicate;
- one partial-path quality predicate;
- no duplicate node-budget calculation;
- no second long-distance target abstraction.

- [ ] **Step 2: Quality review**

Check that:

- `LongDistancePathTarget` remains a shallow immutable marker;
- `MCAGroundPathNavigation.createPath` uses guard clauses and named concepts rather than repeated magic values;
- comments explain 1.21.1 compatibility or MCA divergence, not obvious statements.

- [ ] **Step 3: Correctness review**

Confirm:

- lowering sensing range cannot lower the ordinary 48 path capability;
- ordinary target classification uses navigation capability;
- nearby long-detour fallback retains the real target;
- extended searches do not mutate `FOLLOW_RANGE`;
- failure/stuck state still flows through vanilla/MCA existing owners;
- recompute behavior is correct while airborne/climbing.

- [ ] **Step 4: Efficiency review**

Confirm:

- ordinary path requests do one bounded search;
- only qualifying static failures trigger a second extended search;
- no path globally receives a 160-derived node budget;
- no new chunk loads, streams, or hot-path allocations were added unnecessarily.

- [ ] **Step 5: Verify patch hygiene**

```powershell
git diff --check
git status --short
git diff -- common/src/main/java/net/conczin/mca/entity/ai/navigation common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ExtendedWalkTowardsTask.java common/src/test/java/net/conczin/mca/ConfigPathfindingTest.java neoforge/src/main/java/net/conczin/mca/entity/ai/navigation neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ExtendedWalkTowardsTaskGameTests.java
```

Do not stage unrelated dirty files.

---

## Task 5: Forward-Port To `dev/26.1.2` And Delete Superseded Compatibility Logic

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/entity/VillagerEntityMCA.java`
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/navigation/MCAGroundPathNavigation.java`
- Modify: `common/src/main/java/net/conczin/mca/Config.java`
- Forward-port: `LongDistancePathTarget.java`, producer/sink changes, and their tests
- Compare: patched 26.1.2 vanilla owners against canonical 26.3

- [ ] **Step 1: Port in a clean branch/worktree**

Do not switch the current dirty 1.21.1 workspace. After task-owned 1.21.1 changes are safely committed, use the project's normal sequential merge or a separate worktree rooted at `dev/26.1.2`.

- [ ] **Step 2: Add/port regressions before resolving production conflicts**

Port the ordinary-vs-sensing, long-distance, nearby-detour, partial-progress, recompute, and no-forced-chunk-load regressions first.

Expect API adaptations for Java 25 / 26.1.2, but preserve test semantics.

- [ ] **Step 3: Remove the incorrect required-path-length override**

Delete from `VillagerEntityMCA`:

```java
this.getNavigation().setRequiredPathLength(
        (float)Config.getInstance().getVillagerPathfindingDistance()
);
```

Vanilla 26.1.2 already sets the villager ordinary requirement to 48.

- [ ] **Step 4: Delete the 1.21.1 PathFinder-budget compatibility shim**

Do not carry the 1.21.1 “minimum 768 in `createPathFinder`” implementation forward. Vanilla 26.1.2 has:

- mutable `PathFinder.maxVisitedNodes`;
- `setRequiredPathLength(48)`;
- automatic budget refresh from effective path length.

Let vanilla own that state.

- [ ] **Step 5: Retain only MCA's routing policy**

Keep:

- ordinary-vs-extended geometric classification;
- real final destination;
- `LongDistancePathTarget` ownership marker;
- ordinary-first retry for nearby static long detours;
- partial-progress chaining;
- bed/building/MCA final-target handoff;
- climb/fall/collision improvements.

For ordinary searches, prefer vanilla's normal `super.createPath(...)` so its native `getMaxPathLength()` owns the 48 ordinary horizon.

- [ ] **Step 6: Remove the 1.21.1 recompute override**

Delete MCA's compatibility `recomputePath()` override. Keep MCA's climb-aware `canUpdatePath()`.

Vanilla 26.1.2 base `recomputePath()` now defers through `canUpdatePath()`, so it will call the MCA override correctly.

- [ ] **Step 7: Remove redundant door-pass initialization**

If the target source still confirms `NodeEvaluator.canPassDoors = true` by default, remove:

```java
this.nodeEvaluator.setCanPassDoors(true);
```

Keep intentional:

```java
this.nodeEvaluator.setCanOpenDoors(true);
```

- [ ] **Step 8: Do not recreate the private surface-helper shadow**

Vanilla 26.1.2 still owns `getSurfaceY()` privately. Do not forward-port MCA's 1.21.1 same-name method or `@Override`; it is not a valid owner seam.

- [ ] **Step 9: Separate sensing migration from navigation**

Reconfirm the source audit before editing. The current forward branches only use `villagerFollowRange` to construct the villager attribute:

```powershell
git grep -n "FOLLOW_RANGE\|villagerFollowRange\|getVillagerFollowRange" -- common/src
```

Unless a new focused regression demonstrates a concrete MCA sensing/targeting requirement for 48:

- change the forward-branch default to vanilla 16;
- retain the existing config field/getter during this pathfinding work so existing configs remain compatible and can intentionally override sensing;
- do not change ordinary navigation, which remains vanilla 48.

If 48 is retained, document the exact non-pathfinding reason in the ledger.

- [ ] **Step 10: Carry the 160 long-distance default forward**

Change the old forward-branch `villagerPathfindingDistance = 80` default to 160 and keep it independent of ordinary `requiredPathLength`.

- [ ] **Step 11: Verify no legacy coupling remains**

```powershell
rg -n -U '(?s)setRequiredPathLength\(.{0,200}getVillagerPathfindingDistance' common/src/main/java
```

Expected: no matches. This multiline check is intentional because the current bad call is formatted across multiple lines.

- [ ] **Step 12: Run the 26.1.2 verification lane**

```powershell
.\gradlew.bat common:test --no-daemon
.\gradlew.bat common:compileJava neoforge:compileJava --no-daemon
.\gradlew.bat neoforge:runGameTestServer --no-daemon --no-build-cache --max-workers=1
.\gradlew.bat :fabric:build :neoforge:build --no-daemon
```

---

## Task 6: Forward-Port To `dev/26.2`

**Files:** same MCA pathfinding/brain/test owners as Task 5, adapted only where 26.2 APIs require it.

- [ ] **Step 1: Merge the cleaned 26.1.2 result, not the raw 1.21.1 compatibility shape**

The 26.2 branch must inherit:

- native vanilla ordinary required-path ownership;
- MCA 160 extended policy;
- no `requiredPathLength = villagerPathfindingDistance` coupling;
- no 1.21.1 recompute compatibility override.

- [ ] **Step 2: Compare target vanilla ownership with canonical 26.3**

Use:

```text
C:\Users\Mik\Downloads\MCA\mc_source_code\local-source-26.2\src
C:\Users\Mik\Downloads\MCA\mc_source_code\local-source-26.3\src
```

Classify differences as behavior vs API/version churn before editing MCA.

- [ ] **Step 3: Re-run the duplicate-owner search**

```powershell
rg -n -U '(?s)setRequiredPathLength\(.{0,200}getVillagerPathfindingDistance' common/src/main/java
git grep -n "recomputePath" -- common/src/main/java/net/conczin/mca/entity/ai/navigation
```

Expected: no long-distance-config coupling and no stale 1.21.1 recompute shim.

- [ ] **Step 4: Keep the private surface helper vanilla-owned**

Vanilla 26.2 still declares `getSurfaceY()` private. Do not add an MCA same-name pseudo-override.

- [ ] **Step 5: Preserve the sensing decision independently**

Carry the audited 26.1.2 `FOLLOW_RANGE` decision forward. Do not reintroduce 48 merely because an older branch had it.

- [ ] **Step 6: Run the full focused lane**

```powershell
.\gradlew.bat common:test --no-daemon
.\gradlew.bat common:compileJava neoforge:compileJava --no-daemon
.\gradlew.bat neoforge:runGameTestServer --no-daemon --no-build-cache --max-workers=1
.\gradlew.bat :fabric:build :neoforge:build --no-daemon
```

---

## Task 7: Converge `dev/26.3` On Native 26.3 Ownership

**Files:**
- Modify as required: `MCAGroundPathNavigation.java`
- Modify as required: `MCAWalkNodeEvaluator.java`
- Modify as required: `VillagerEntityMCA.java`
- Modify as required: `Config.java`
- Forward-port all focused navigation/brain GameTests

- [ ] **Step 1: Merge the cleaned 26.2 result**

Do not resurrect any compatibility code already deleted at earlier hops.

- [ ] **Step 2: Diff every MCA override against its 26.3 vanilla owner**

Pay special attention to:

- `PathNavigation` required path length / waypoint behavior;
- `GroundPathNavigation.getSurfaceY()` and `FluidTags.ENTITY_FLOATABLE`;
- `WalkNodeEvaluator` floatable-fluid handling;
- `BlockTags.SPELEOTHEMS`;
- large-mob danger malus;
- path-type/loader hook differences;
- default door-pass behavior.

- [ ] **Step 3: Remove stale overrides when vanilla is stronger**

Vanilla 26.3 makes `getSurfaceY()` public and generalizes it to `isInFloatableFluid()` / `FluidTags.ENTITY_FLOATABLE`. Do not introduce MCA's older WATER-only implementation on this branch; use the native 26.3 method unless a focused regression proves a separate MCA requirement.

Do not remove climb/collision behavior just because the surrounding vanilla method changed; prove equivalence with focused regression first.

- [ ] **Step 4: Finalize sensing/navigation separation**

Target state:

```text
vanilla sensing default        16   (unless documented MCA gameplay override)
vanilla ordinary navigation   48
vanilla ordinary node budget ~768
MCA extended horizon         160
```

No one value may be reused as another concept for convenience.

- [ ] **Step 5: Re-run the complete source-comparison ledger**

Every meaningful 26.3 hunk in the audit boundary must now be one of:

- native vanilla owner;
- MCA-specific retained improvement;
- intentionally adapted behavior;
- explicitly rejected change with reason.

- [ ] **Step 6: Run 26.3 verification**

```powershell
.\gradlew.bat common:test --no-daemon
.\gradlew.bat common:compileJava neoforge:compileJava --no-daemon
.\gradlew.bat neoforge:runGameTestServer --no-daemon --no-build-cache --max-workers=1
.\gradlew.bat :fabric:build :neoforge:build --no-daemon
```

---

## Task 8: Final Four-Lens Review And Cross-Version Acceptance

- [ ] **Step 1: Reuse**

Across all four branches verify there is only one owner for:

- ordinary navigation requirement;
- PathFinder budget;
- long-distance classification;
- partial-progress classification;
- failure timing;
- bed final-approach logic.

- [ ] **Step 2: Quality**

Remove:

- stale compatibility comments;
- redundant setters;
- old constants;
- duplicate helper seams;
- version branches that are unnecessary because each Minecraft line has its own source branch.

- [ ] **Step 3: Correctness**

On each branch prove:

- sensing changes do not silently shrink ordinary navigation;
- a target inside 48 is not long-distance merely because sensing is lower;
- a target beyond 48 can use the 160 policy;
- a nearby destination with a >48 detour can use ordinary-first extended retry;
- unreachable/stuck behavior still records failure;
- no path request synchronously force-loads chunks;
- doors/fence gates, partial blocks, stairs, raised starts, climbing, falling, and beds retain their focused regressions.

- [ ] **Step 4: Efficiency**

Verify:

- baseline ordinary budget is ~768, not ~2560;
- the 160 horizon does not permanently mutate vanilla required path length;
- nearby paths do not run the extended search unless the ordinary search fails;
- no duplicate path computation was introduced outside the intentional ordinary-then-extended fallback.

- [ ] **Step 5: Final mechanical checks per branch**

```powershell
git diff --check
git status --short
rg -n -U '(?s)setRequiredPathLength\(.{0,200}getVillagerPathfindingDistance' common/src/main/java
```

The multiline search must be empty on 26.1.2, 26.2, and 26.3.

## Final Acceptance

The work is complete only when:

1. the 26.3 source-comparison ledger is exhaustive for the defined pathfinding boundary;
2. 1.21.1 has the minimum compatibility layer needed to express ordinary 48 vs extended 160 semantics;
3. 26.1.2, 26.2, and 26.3 use native required-path/budget ownership and contain no copied 1.21.1 compatibility state;
4. the forward branches no longer feed `villagerPathfindingDistance` into ordinary `setRequiredPathLength`;
5. the sensing-range decision is documented independently of navigation range;
6. MCA-specific improvements survive each merge exactly once;
7. focused unit/GameTests and loader builds pass on every touched branch, or unrelated pre-existing failures are named precisely;
8. the final report distinguishes canonical 26.3 parity, MCA intentional divergences, automated proof, and any remaining manual gameplay proof.
