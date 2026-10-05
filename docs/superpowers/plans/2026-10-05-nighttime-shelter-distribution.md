# Nighttime Shelter Distribution Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Native execution in this chat is recommended because these changes share live entity state and geometry interfaces.

**Goal:** Prefer the nearest reachable house below its `bedCount + 5` capacity, allowing overflow for danger or alternatives more than 30 route blocks farther away.

**Architecture:** Resolve house membership through existing MCA geometry, derive admission from bed claims and live villager state, and select a destination in `SeekIndoorShelterTask`. Keep observation, admission, and WALK_TARGET publication on the server thread. Use operation-local geometry snapshots; do not persist shelter reservations or introduce a second movement owner.

**Tech Stack:** Minecraft 1.21.1, Java 21 mod source, existing Fabric/NeoForge integration, Gradle wrapper, common JUnit and NeoForge GameTests. Re-read current build metadata before execution; the configured Gradle daemon JDK does not change the source language level.

**Spec:** [Nighttime shelter distribution design](../specs/2026-10-05-nighttime-shelter-distribution-design.md).

## Global Constraints

- Normal capacity is `bedCount + 5`; compatible bed heads count once, occupied or not.
- Maximum additional walking-route distance is **30 blocks**, inclusive, relative to the nearest reachable shelter in the inspected candidate set.
- Preserve the current **48-block** anchor search radius and **20–39 tick** staggered shelter retry cadence. The additional travel allowance does not expand the search radius.
- Group anchors before limiting path requests to **five distinct houses** per attempt. Several beds must not use up the house budget.
- Guide incoming homeless REST villagers; already-sheltered villagers stay. Normal HOME return is not subject to this admission rule.
- PANIC, fleeing, and emergency hiding retain their existing movement owners. Detected danger permits overflow during REST before PANIC begins.
- Preserve HOME, bed tickets, floor/room registration, and dimension ownership. Read-only geometry must not mark village data dirty or register houses.
- Keep mutable world, entity, brain, POI, and navigation access on the server thread. No locks, atomics, parallel streams, executors, or asynchronous scans are required.
- Ordinary unregistered village houses are required. Unknown membership preserves shelter access, but is a reported limitation rather than proof that distribution works.
- Preserve unrelated staged/unstaged changes. Do not create checkouts/worktrees, use WSL, commit, push, or stage all files. The user requested a plan; those actions are not authorized by an imported workflow.
- Use disposable worlds only. Do not modify or copy the user's `neoforge/run/saves/New World`.

## Review Focus

1. Two adjacent unregistered houses sharing a roof must not become one capacity pool: Task 2 geometry regression.
2. A claimed bed with an unloaded/vanilla owner must still reserve a place without counting a loaded owner twice: Task 3 admission regression.
3. An incoming villager beyond the local house bounding box must count, and a cleared target must stop counting: Task 3 lifecycle regression.
4. A house straddling the discovery radius must count its whole known geometry, not just anchors within 48 blocks: Task 2 boundary regression.
5. A spatially close house behind a long wall must not defeat the 30-route-block safety fallback: Task 4 route regression.

## File and responsibility map

- Existing `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/SeekIndoorShelterTask.java`: candidate grouping, operation-local admission, route comparison, and existing destination publication.
- Existing `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/EnterBuildingTask.java`: consume the parallel agent's `static boolean hasStandingSpace(Level, PathfinderMob, BlockPos)`; do not create another clearance predicate.
- Proposed `common/src/main/java/net/conczin/mca/server/world/data/ShelterHouseGeometry.java`: narrow read-only adapter to package-private canonical geometry, needed because registered and unregistered membership cannot be inferred from a bed radius. Task 1 must establish feasibility before this class is kept.
- Existing `common/src/main/java/net/conczin/mca/server/world/data/Village.java`: only a narrow HOME snapshot accessor if existing APIs cannot provide owner UUIDs and HOME positions. No new persisted fields.
- Proposed `common/src/test/java/net/conczin/mca/server/world/data/ShelterHouseGeometryTest.java`: Minecraft-aware geometry fixtures and registration immutability.
- Proposed `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ShelterDistributionGameTests.java`: new distribution tests, separate from the other agent's active indoor-movement tests.
- Existing `neoforge/src/main/java/net/conczin/mca/gametest/McaGameTestsRegistration.java`: register the new class through the established focused test lane.
- Existing `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/HomelessShelterGameTests.java`: reuse the other agent's tests without concurrent edits.

All source paths above are relative to the current repository root. Do not modify the parallel floor-regions implementation merely to satisfy proposed interfaces; adapt the read-only adapter to its final APIs.

### Task 1: Establish the indoor-selector and geometry prerequisites

**Files:** Read the files above and `SelectedFloorScanner.java`, `StructureScanner.java`, `StructureConnector.java`, `FloorGrouping.java`, `FloorGeometry.java`, and the current `Village.java`. Record findings in this plan before implementation.

**Interfaces:** Consumes the completed indoor-selector change and existing structure/floor discovery. Produces a verified mapping for Task 2 and a bounded-cost geometry decision; no gameplay deliverable yet.

- [ ] Refresh [Backport 1.21.1 pathfinding optim (2)](thread://01a10bd3-3dd2-77e1-995d-79adce443bd9?hostId=local) with `read_thread`. Inspect the actual local diff and fresh test results. Do not message that chat without user authorization or overlap its active edits.
- [ ] Confirm `EnterBuildingTask.hasStandingSpace` and the actual feet-position selector are present. Run `.\gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=HomelessShelterGameTests --console=plain --no-daemon --no-parallel` once the other agent's Gradle work ends. Require actual movement, leaves, bed-surface, and shelter-reachability cases to pass. An earlier target-only test is insufficient.
- [ ] Trace read-only unregistered house discovery using `SelectedFloorScanner.Observation`, `StructureScanner.observeFloor`, canonical door ownership, and vertical connectors. Verify one-floor discovery is not silently treated as whole-house discovery. Include an adjacent-house fixture, a staircase, and incomplete registered geometry with autoScan disabled.
- [ ] Establish a per-operation bound using existing scanner/pathfinding limits. Default building limits are currently 8192 cells and radius 320; these defaults alone are not evidence that repeated scans every 20–39 ticks are affordable. Measure visited cells and elapsed scan time for small natural houses, the multi-floor fixture, and failure at the existing size limit. Identify reuse of observations within the operation and confirm no forced chunk loading.
- [ ] Record the chosen geometry entry points, measured work, unloaded-chunk behavior, and any required narrow extension in this plan. If exact unregistered whole-house identity cannot be proved within bounded work, report the concrete limitation before implementing Task 2. Do not substitute a radius, register buildings automatically, or silently downgrade the requirement to registered houses.

This checkpoint is intentional: current source exposes floor discovery, not a proven public read-only whole-house query. A finalized geometry algorithm cannot responsibly be claimed before this investigation.

### Task 2: Resolve distinct houses and whole-house membership

**Files:** Create `ShelterHouseGeometry.java` and `ShelterHouseGeometryTest.java`. Extend existing geometry owners only where Task 1 identified a necessary narrow read-only seam.

**Interfaces:** Proposed public API in `net.conczin.mca.server.world.data`:

```java
static List<House> discover(ServerLevel level, @Nullable Village village,
                            List<BlockPos> orderedBedAnchors);
```

`ShelterHouseGeometry` is public and its method is public static. `House` is a nested immutable public data carrier exposing `Set<BlockPos> bedHeads()`, `Set<BlockPos> floorCells()`, `boolean contains(BlockPos position)`, and `boolean membershipKnown()`. Choose record versus final class to fit the established geometry representation; do not expose mutable world state. A nullable village supports villagers without MCA residency and follows the repository's annotation convention.

- [ ] Write `adjacentUnregisteredHousesRemainDistinct`, `connectedFloorsShareCapacityIdentity`, `incompleteRegisteredHouseUsesFreshGeometry`, `bedOutsideAnchorRadiusStillCountsInResolvedHouse`, and `discoveryDoesNotRegisterOrMutateVillage` tests. Assert two nearby houses remain distinct, connected floors belong to one house, every compatible bed head is counted once, and serialized village data is unchanged. Include an occupied bed and a BedBlock subclass.

  The adjacent-house fixture must assert:
  ```java
  assertEquals(2, houses.size());
  assertTrue(firstHouse.contains(firstBedHead));
  assertFalse(firstHouse.contains(secondBedHead));
  assertEquals(Set.of(firstBedHead), firstHouse.bedHeads());
  assertEquals(villageBefore, villageAfter);
  ```
- [ ] Run `.\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.ShelterHouseGeometryTest' --console=plain --no-daemon --no-parallel`. Wire a compilable initial adapter so RED is an incorrect grouping/omitted-floor assertion, not a missing-type or fixture error.
- [ ] Implement the adapter using Task 1's verified geometry. Registered grouping uses logical building identity scoped to the passed village. Unregistered grouping uses fresh proven topology. Merge fresh evidence with incomplete registered geometry without allocating persistent IDs. Capture whole-house membership, including vertical bands, rather than equating occupied positions with exact floor-cell coordinates.
- [ ] De-duplicate compatible bed heads over the resolved whole-house geometry, beyond the initial anchor radius where geometry is already resolved. Return one House per identity in nearest-anchor order. Treat unsupported or unloaded geometry as `membershipKnown() == false`; retain that anchor's existing bounded shelter floor candidates but do not invent a capacity number.
- [ ] Re-run the focused common tests. Require all assertions to pass, and confirm Task 1's work bounds still hold. Document unknown-membership cases and keep geometry snapshots local to one discovery operation.

### Task 3: Derive admission from claims, occupants, and incoming targets

**Files:** Modify `SeekIndoorShelterTask.java`; add only if needed `Village.java` HOME accessor. Create/register `ShelterDistributionGameTests.java`.

**Interfaces:** Task 2 supplies House membership. Task 3 adds private `boolean hasCapacity(ServerLevel level, House house, List<AbstractVillager> loadedVillagers, Map<UUID, BlockPos> residentHomes)` to the task. If needed, add public `Map<UUID, BlockPos> getResidentHomePositions()` to Village returning an immutable server-thread snapshot, never the mutable map.

- [ ] Add `lastPlaceIncludesPendingArrival`, `returningOwnerIsCountedOnce`, `claimedBedReservesUnloadedOwner`, `vanillaOwnerAndChildCountOnce`, `distantIncomingTargetCounts`, and `clearedOrChangedTargetReleasesIncomingPlace`. With one bed, assert six accounted places fill the normal capacity and a seventh incoming guest uses another house. Exercise public behavior through actual WALK_TARGET production; do not weaken assertions to inspect a private counter.
- [ ] Register the test class, then run `.\gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=ShelterDistributionGameTests --console=plain --no-daemon --no-parallel`. Require a valid behavior failure before adding admission.
- [ ] Take one snapshot of alive loaded MCA and vanilla `AbstractVillager` entities per selection using the existing ServerLevel iteration API. Inspect all loaded villagers' incoming WALK_TARGETs, not only entities within a house's bounding box. This includes arrivals currently outside the original discovery radius. Discard the snapshot after publication; measure the cost in Task 5 rather than keeping an unowned cache.
- [ ] Reserve one place per valid claimed HOME bed, including unidentified/unloaded owners, and reconcile with known valid HOME owners from persisted MCA assignments and loaded brains. POI claims provide occupancy, not UUID ownership. Count each known owner once; do not double-count the same bed via persisted and live assignments. Read claims without calling `take` or `release`.
- [ ] Count additional loaded occupants and incoming villagers by UUID. Exclude already-accounted owners and the selecting villager, count each guest at most once per house, and respect HOME dimension. A guest physically in one house while heading to another occupies a place in the former and has an incoming place in the latter. Ignore cleared targets and dead/removed entities.
- [ ] Use the comparison `accountedPlaces < house.bedHeads().size() + 5`. Unknown membership bypasses the capacity preference. Observe and publish on the same server thread without an async boundary; later villagers read the WALK_TARGET immediately. Preserve owners' return behavior.
- [ ] Integrate admission into `getNextPosition` with Task 2's distinct-house discovery: provisionally try the nearest under-capacity reachable house, keeping the nearest reachable house as overflow when all are full. Task 4 replaces this provisional spatial ordering with the route-distance and danger policy. For the consecutive-arrival fixture, assert the first incoming WALK_TARGET belongs to the house's last place and the second belongs to the alternative before ticking either villager's movement.
- [ ] Re-run the class and verify consecutive arrivals, target lifecycle, unloaded reservations, and mixed vanilla/MCA occupants. No concurrent reservation map, tracked capacity property, lock, or executor should appear in the diff.

### Task 4: Select nearest shelter with route allowance and emergency overflow

**Files:** Modify `SeekIndoorShelterTask.java`; extend `ShelterDistributionGameTests.java`. Add pure path-distance tests to `common/src/test/java/net/conczin/mca/entity/ai/brain/tasks/ShelterRouteDistanceTest.java` if the package's configured Minecraft-aware lane supports constructing Path/Node.

**Interfaces:** Keep protected `Optional<BlockPos> getNextPosition(VillagerEntityMCA villager)` and existing publication intact. Change private floor selection to return `Optional<Path> findReachableFloor(ServerLevel level, VillagerEntityMCA villager, Iterable<BlockPos> candidates)` so route measurement uses the path actually selected. Add package-private `static double routeLength(Vec3 origin, Path path)` for the pure-distance test and private `boolean hasDetectedDanger(VillagerEntityMCA villager)` for existing live threat memories.

- [ ] Add `nearestHouseWithSpaceWins`, `fullNearestHouseUsesNearbyAlternative`, `multipleBedsDoNotHideNextHouse`, `exactlyThirtyExtraRouteBlocksAreAllowed`, `moreThanThirtyExtraRouteBlocksUsesOverflow`, `longDetourUsesOverflow`, `allHousesFullStillSelectsShelter`, `unreachableAlternativeUsesOverflow`, `detectedHostileAllowsImmediateOverflow`, and `alreadyShelteredDoesNotRelocate`. Include a threat remembered before PANIC, and a stale/dead threat that should not force overflow.
- [ ] Run the focused GameTest class and require behavior failures. For pure distance, assert straight, diagonal, and vertical node segments have their geometric lengths, not their node counts; an 8-block route plus 30 additional blocks is eligible and a 39-block route is not. Use fixtures with measured actual route lengths in GameTests, not assumed anchor distances.

  The route-distance oracle can use real Minecraft nodes:
  ```java
  Path diagonal = new Path(List.of(new Node(0, 0, 0), new Node(1, 0, 1)),
          new BlockPos(1, 0, 1), true);
  assertEquals(Math.sqrt(2.0D), SeekIndoorShelterTask.routeLength(
          new Vec3(0.5D, 0.0D, 0.5D), diagonal), 1.0E-6D);
  ```
- [ ] Discover nearby compatible HOME anchors with occupancy ANY and existing radius 48. Group through Task 2 before evaluating at most five distinct houses. Reuse candidate floor validation and navigation's set-target path request; reject paths that cannot reach an actual valid standing destination. Do not add per-candidate random-sample rejection or pass solid support blocks as feet targets.
- [ ] Measure a fresh route from the villager's position through the path's entity node positions using summed Euclidean segment distances. Empty paths are eligible only when the destination is already reached and valid. Capture each reachable candidate's route once, preserving the path/target association; do not call a pathfinding distance a threat-safety proof.
- [ ] Among inspected reachable candidates, find the minimum measured route as the overflow baseline. In ordinary conditions choose the shortest under-capacity route with `routeLength <= baseline + 30.0D`; ties preserve discovery order. Unknown membership allows admission without a guessed cap. If none qualifies, return the baseline destination.
- [ ] For danger, use a live same-level NEAREST_HOSTILE memory and the sensor's current hostile-distance predicate. A remembered live same-level HURT_BY_ENTITY qualifies within squared distance `36.0D`, matching 1.21.1 `VillagerCalmDown`'s nearby-attacker rule. Historical damage alone does not force permanent overflow. Re-read the final MCA/vanilla sensor and calm-down ownership before wiring this predicate; if inaccessible, compare existing API/access options before copying the hostile-distance table. Preserve PANIC/HIDE/raid packages and ensure REST cannot overwrite their escape target during an activity transition.
- [ ] Re-run the class and path-distance tests. Verify every selected endpoint has clearance and support, excludes bed surfaces, preserves HOME/tickets, and still handles the existing unreachable-floor-sample regression.

### Task 5: Prove actual arrivals, emergency precedence, and bounded work

**Files:** Extend `ShelterDistributionGameTests.java` using existing disposable-world terrain and real tick patterns; review the final shared-code diff.

**Interfaces:** Complete Task 2 geometry, Task 3 admission, Task 4 selection; existing REST behavior, navigation, door interaction, and corrected local wandering.

- [ ] Add `consecutiveArrivalsEnterDifferentHousesAtCapacity` with real ticking villagers and two furnished houses, and `emergencyMovementIsNotBlockedByCapacity` exercising active PANIC/HIDE transitions. Include multiple floors and villagers initially standing on a bed. Require arrival on interior floor and unchanged HOME; capacity need not prohibit transient collision pushes or emergency overflow.
- [ ] Run the new GameTest class. If fixtures stall in terrain setup, inspect stacks and repair the fixture owner; do not call that a production regression, alter unrelated navigation, or modify the user's world. Real ticks must execute; NoAI/manual producer tests alone do not satisfy this task.
- [ ] Measure one normal shelter attempt in the same loaded small/large village fixture before and after the change. Record loaded villager count, candidate houses, geometry cells inspected, path requests, and elapsed time. Require no more than five house path requests, unchanged producer cadence, no forced chunk loading, and no repeat full geometry scan for each occupant/bed within the same attempt. If the whole-level entity snapshot or geometry work is excessive, optimize the existing owner after documenting the evidence; do not hide the cost with an indefinite cache.
- [ ] Run, serially with other agents' jobs:

  ```powershell
  .\gradlew.bat :common:test --tests 'net.conczin.mca.server.world.data.ShelterHouseGeometryTest' --console=plain --no-daemon --no-parallel
  .\gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=ShelterDistributionGameTests --console=plain --no-daemon --no-parallel
  .\gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=HomelessShelterGameTests --console=plain --no-daemon --no-parallel
  .\gradlew.bat :fabric:build :neoforge:build --console=plain --no-daemon --no-parallel
  ```

  Run the route-distance class when added. Require actual required-test counts, fresh completion logs, and zero process exit codes; do not report merely registering/skipping tests as validation.
- [ ] Observe a disposable client village at night with incoming villagers, a full house, a nearby alternative, and detected monsters. Confirm no capacity-induced eviction, natural interior wandering, and overflow. If client validation is unavailable, state that limitation and do not claim visible crowding is fully fixed.
- [ ] Review `git diff --check` and the focused source diff. Remove duplicated predicates or admission state, confirm no world files are source artifacts, and report exact checks and unresolved runtime/performance limitations. Leave changes uncommitted unless the user requests a Git action.

## Execution handoff

This is a plan, not a gameplay change or proof that unknown house geometry is solved.
Task 1 resolves the remaining technical uncertainty before the proposed adapter is
implemented. Read the approved spec and current source together; refresh the
parallel indoor/floor work at execution time.

Recommended method: native execution in this chat, sequentially, preserving the
other agent's changes. Ask for plan review and execution-method selection before
starting implementation. Use only reviewer/subagent capabilities actually available
in the session; a fresh user-visible chat is not a substitute for an internal
review agent without explicit user authorization.
