# Nighttime Shelter Distribution Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans for sequential inline implementation. Checkboxes record verified work; documentation reconciliation is not gameplay completion.

**Goal:** Incoming homeless REST villagers prefer the nearest reachable whole house below `bedCount + 5`, with safe overflow and at most 64 additional route blocks.

**Architecture:** Reuse `IndoorRoomCache` for room destinations, `SHELTER_BED` for selection, and existing navigation for movement. Establish read-only whole-house membership through the canonical floor/connector owner, then derive admission from existing claims and live intent on the server thread. No independent scanner, cache, persistent reservations, or movement owner.

**Tech Stack:** Minecraft 1.21.1, Java 21 source, current Fabric/NeoForge Gradle lanes. The daemon JDK does not change the source language level.

**Spec:** [Nighttime shelter distribution design](../specs/2026-10-05-nighttime-shelter-distribution-design.md).

## Revision and execution status

Revised 2026-10-05 using
[Inspect pathfinding task context](thread://01a10d25-2949-7020-9c55-27bd9f739771?hostId=local)
and the current source. That chat's completed change removes the stale registered
cache shortcut and doorway idle targets. Prior saved logs show **17 shelter tests
and both loader builds passed**. Fresh implementation checks are recorded below.

Execution started inline on `dev/1.21.1` at the user's explicit request.
The fresh baseline passed all 17 shelter tests. New admission regressions failed
for the intended behavior before the change, then passed after implementation.
The earlier documentation-only scope is superseded by the inline implementation.

The old Task 2 proposed a geometry adapter as if room discovery still needed a
new implementation. Room discovery, shared caching, remembered selection, and
room-local wandering now exist and are reused. Whole-house grouping now uses fresh
floor evidence in that same cache; a room is not treated as a guessed capacity pool.

The user subsequently authorized inline implementation. The source now supplies
whole-house admission. Client appearance and larger-village profiling remain open.

## Global constraints

- Capacity `bedCount + 5`; additional route allowance **64.0 blocks inclusive**.
- Preserve anchor radius **48 blocks**, shelter retry **20–39 ticks**, local wandering retry **40 ticks**, and current room-cache limits/invalidation.
- Group before limiting navigation to **ten distinct houses**; separately establish a bounded geometry-discovery budget.
- Guide incoming homeless REST villagers only. Already sheltered villagers stay; HOME owners return independently.
- PANIC/HIDE/fleeing/combat movement retains priority. Danger and unavailable/too-distant alternatives permit overflow.
- Preserve HOME/tickets, registered geometry, and existing movement ownership.
- Use immutable local data where useful; mutable Minecraft access and count/publish stay on the server thread.
- No threads, locks, executors, parallel streams, new reservation map, new long-lived cache, or automatic registration.
- Support ordinary unregistered houses. Unknown whole-house membership permits entry without a guessed cap and is reported as a limitation.
- Work in the current checkout, preserving other edits. No WSL, new checkout/worktree, staging, commit, or push.
- Disposable worlds only. Do not modify or copy `neoforge/run/saves/New World`.
- Run Gradle jobs serially; use only this checkout's supported GameTest filter.

## Review focus

1. Rooms/floors in one house must share capacity without merging adjacent houses.
2. Returning, sleeping, forced-HOME, vanilla, and unloaded owners must not be counted twice or omitted.
3. A remembered bed without active arrival must not reserve indefinitely; distant active arrivals must count.
4. A reached path node must be usable floor even when navigation normalizes the requested target.
5. Cache expiry, removed partitions, sensor differences, and emergency transitions must not revive stale membership or obstruct escape.

## File and responsibility map

| File | Responsibility |
| --- | --- |
| `common/.../entity/ai/brain/tasks/SeekIndoorShelterTask.java` | Ordinary admission, grouped candidates, route comparison, chosen anchor. |
| `common/.../server/world/data/IndoorRoomCache.java` | Existing room resolution/reuse/invalidation; extend only a proven necessary read-only seam. |
| `common/.../entity/ai/brain/tasks/EnterBuildingTask.java` | Existing `isUsableFloor(Level, PathfinderMob, BlockPos)` and movement publication. |
| `common/.../entity/ai/brain/tasks/LocalInsideBrownianWalk.java` | Existing room-local wandering; no capacity policy. |
| `common/.../entity/ai/MemoryModuleTypeMCA.java` and `brain/VillagerTasksMCA.java` | Existing selection lifecycle and activity guards; change only for a reproduced lifecycle defect. |
| `common/.../server/world/data/Village.java` | Canonical registered identity and, only if needed, immutable valid HOME snapshot access. |
| `common/.../server/world/data/ShelterHouseGeometry.java` | Conditional narrow read-only whole-house adapter; create only if Task 1 proves it necessary. |
| `common/src/test/java/net/conczin/mca/server/world/data/ShelterHouseGeometryTest.java` | Pure immutable membership/grouping assertions when a separable algorithm exists. |
| `common/src/test/java/net/conczin/mca/entity/ai/brain/tasks/ShelterRouteDistanceTest.java` | Actual Path/Node geometric distance. |
| `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/ShelterDistributionGameTests.java` | New admission, lifecycle, route, safety, and arrival regressions. |
| `neoforge/src/main/java/net/conczin/mca/gametest/McaGameTestsRegistration.java` | Register the new focused test class. |
| `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/HomelessShelterGameTests.java` | Existing 17 regressions; consume without overlapping another agent's edits. |

The common ellipses expand to `common/src/main/java/net/conczin/mca`.
Do not add all proposed files automatically. Follow the current owning APIs.

### Task 1: Refresh the baseline and settle membership interfaces

**Files:** Read the owners above plus `SelectedFloorScanner`, `BuildingRoomScanner`,
`RoomPartitioner`, `StructureScanner`, `StructureConnector`, `FloorGeometry`,
`Structure`, `StructureFloor`, and persistent movement lifecycle.

**Consumes:** `IndoorRoomCache.resolve(BlockPos): Optional<Room>`,
`Room.floorCells(): Set<BlockPos>`, `EnterBuildingTask.isUsableFloor`,
and `SHELTER_BED: GlobalPos`.

**Produces:** An evidence-backed whole-house query contract and bounded-work decision
for Task 2, plus the arrival lifecycle that Task 3 will count.

- [x] Read the referenced chat and current shared-code changes; confirm fresh discovery, shared room reuse, selected-bed cleanup, room-local wandering, and doorway idle exclusion. Existing performance savings are unmeasured.
- [x] Run `.\gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=HomelessShelterGameTests --console=plain --no-daemon --no-parallel`.
  **Expected:** all 17 current required tests actually execute and pass, including partition removal after expiry and real movement. Record exit status and count; old logs are baseline evidence only.
- [x] Trace registered logical identity separately from fresh physical membership. Trace unregistered internal-door/vertical connector discovery without registering or marking village data dirty. Demonstrate neighboring houses and connected floors using current fixtures.
- [x] Trace `moveTowardsPersistent`, WALK_TARGET, path completion/retry, REST cleanup, and HOME acquisition. Define when an arrival is active; selected-bed memory alone is insufficient. Verify current-room detection when a retained selected bed points elsewhere.
- [ ] Measure cold/warm discovery, shared-bed reuse, and the boundary/failure cases. Record visited work, elapsed time, loaded chunk scope, and a total per-attempt geometry bound. Room-cache scan limits alone do not bound repeated house queries.
- [x] Write the final read-only membership interface into this plan before Task 2. It must distinguish complete membership from unknown, expose compatible bed heads and exact physical membership, and supply existing cached rooms for standing destinations. If the owning API cannot prove whole-house membership at bounded cost, record the exact gap; do not replace it with a per-room cap or blindly copy scanners.

**Expected:** a verified interface, not an assumed `Room == House` conversion.
This prerequisite is implemented by the recorded contract below.

**Recorded implementation contract:** Reuse `IndoorRoomCache.resolveHouse(BlockPos)`
and `resolveHouses(List<BlockPos>, int)`. Immutable House exposes compatible bed
heads, room destinations, exact physical-column membership, and known/unknown
completeness. Retain fresh full-floor evidence and components in the existing
cache entry; follow `SelectedFloorScanner.Result.adjacentFloorSeeds()` and existing
vertical connector handoffs. Registered logical identity supplies additional floor
seeds, not trusted persisted geometry. Discovery permits 20 new floor scans per
operation and at most eight floors per house (4096 materialized cells); existing
per-floor and observed-block/cache limits remain. Unknown membership bypasses capacity.
The connected-room bed-count regression went RED to GREEN using this owner.

### Task 2: Implement proven whole-house membership

**Files:** Existing geometry owner from Task 1; conditional
`ShelterHouseGeometry.java` and pure common test; new
`ShelterDistributionGameTests.java` plus its registrar entry for live-world geometry.

**Consumes:** Task 1's recorded interface and current room cache. **Produces:**
an operation-local immutable house view with complete bed heads, physical membership,
canonical identity when present, and known/unknown status. No second mutable store.

- [x] Register the new GameTest class using the existing disposable-world lane.
  Write `adjacentUnregisteredHousesRemainDistinct`,
  `connectedFloorsShareCapacityIdentity`,
  `wholeHouseBedsExtendBeyondAnchorSearchRadius`,
  `staleRegisteredRoomDoesNotImposePartialCapacity`, and
  `registeredDiscoveryDoesNotMutateVillage`.
  **Assertions:** neighboring houses are separate; internal rooms/floors share one house;
  occupied/subclass bed heads count once; physical membership includes sleeping and
  raised standing positions within their exact bands; serialized village state is unchanged.
- [x] Write unloaded and incomplete membership cases; review oversized discovery bounds.
  **Assertions:** unknown is explicit, no partial bed count is enforced, no chunk is force-loaded, and known reachable room access is retained. Oversized structures without a resolvable room remain unsupported; no oversized-runtime guarantee is claimed.
- [x] Run `:neoforge:runGameTestServer -PmcaGameTest=ShelterDistributionGameTests`
  with the Gradle flags from Task 1. Keep live scanning, changed partitions, bed
  states, chunk-loading, and village immutability in GameTests; existing common
  geometry tests exercise immutable cells/topology without a running ServerLevel.
  Add and run the focused common class only for a separable pure grouping algorithm.
  **Expected RED:** an observable grouping/membership failure with a valid fixture;
  missing types or setup failures do not reproduce the requirement.
- [x] Implement the smallest read-only seam established in Task 1. Scope registered IDs to village/dimension; do not trust persisted topology as current. Reuse existing scanner/connector semantics and cache validation where they fit.
- [x] Deduplicate all known house beds, including those outside the anchor radius. Group anchors before the ten-house path budget. Resolve repeated beds/rooms once per attempt; enforce the separately recorded geometry bound.
- [x] Rerun the focused geometry GameTests and any added pure common class.
  **Expected GREEN:** all membership and immutability assertions pass within the recorded bound. Remove any provisional adapter that an existing owner can replace.

### Task 3: Add admission and final route/safety policy together

**Files:** `SeekIndoorShelterTask.java`, conditional narrow `Village.java` HOME
accessor, `ShelterDistributionGameTests.java`, registrar, and route-distance test.

**Consumes:** Task 2 complete/unknown house views; Task 1 active-arrival lifecycle.
**Produces:** final ordinary selection in existing
`protected Optional<BlockPos> getNextPosition(VillagerEntityMCA villager)`.
Keep route comparison internal to this task; do not ship an intermediate policy
that sends villagers farther merely because a house has space.

Implemented package-private seam:
`static double routeLength(VillagerEntityMCA villager, Path path)`.
Numeric route cases run in GameTests because native node positions depend on
the actual entity width/scale. No separate common route test or geometry adapter
was needed.
Keep admission/danger helpers private unless a real owning seam needs wider access.

- [x] Extend the registered class using nonoverlapping disposable arenas and established cleanup.
  Add `lastPlaceIncludesPendingArrival`, `distantIncomingTargetCounts`,
  `rememberedBedWithoutArrivalDoesNotReserve`, `clearedOrRedirectedArrivalReleasesPlace`,
  `returningOwnerIsCountedOnce`, `claimedBedReservesUnloadedOwner`,
  `vanillaOwnerChildAndSleeperCountOnce`, and `forcedHomeOwnerIsReservedOnce`.
  **Assertions:** one bed gives six accounted places; the next guest chooses the alternative;
  consecutive selections see the published arrival before either villager moves;
  stale/invalid assignments do not reserve and HOME/ticket state does not change.
- [x] Add `fullNearestHouseUsesNearbyAlternative`, `multipleBedsDoNotHideNextHouse`,
  `exactlyThirtyExtraRouteBlocksAreAllowed`, `moreThanThirtyExtraRouteBlocksUsesOverflow`,
  `longDetourUsesOverflow`, `allHousesFullStillSelectsShelter`, and `unreachableAlternativeUsesOverflow`.
  **Assertions:** decisions use measured routes; an 8-block baseline admits a 38-block alternative,
  but not 39; reachable overflow remains available.
- [x] Add `liveThreatBeforePanicUsesOverflow`, `ignitedCreeperUsesOverflow`,
  `staleOrDeadThreatDoesNotForceOverflow`, the existing sheltered-idle regressions,
  and `arrivedElsewhereDoesNotFollowStaleSelection`.
  Test active PANIC/HIDE/combat transition precedence and an endpoint normalized toward a bed or door.
- [x] Run the new GameTest class before implementing policy; verify native route arithmetic in that runtime lane.
  **Expected RED:** valid selection/lifecycle failures. Verify straight, diagonal,
  and vertical route lengths with real Minecraft Path/Node values; diagonal distance is sqrt(2).
- [x] Take one server-thread snapshot of alive loaded MCA/vanilla villagers and active intents per selection. It must include distant incoming destinations.
  Reserve claimed HOME beds and reconcile valid persisted/live owners by bed and UUID;
  count extra occupants/arrivals once, excluding the selector. Add an immutable HOME accessor only if existing APIs cannot expose the needed evidence.
- [x] Use existing usable-floor checks and at most ten grouped-house path requests.
  Reject unreachable paths or invalid actual endpoints; measure each accepted route once.
  Select the shortest under-capacity route within baseline + 64.0, preserving discovery-order ties;
   unknown capacity allows entry. With no qualifying under-capacity alternative,
   prefer the house least over capacity within the same route allowance; break
   ties by route length, then discovery order. Detected danger uses the baseline.
- [x] Reuse threat memories and current sensor/lifecycle semantics. NEAREST_HOSTILE includes
  MCA's ignited creepers, not just vanilla's hostile-distance table. HURT_BY_ENTITY must be alive,
  same-level and within squared distance 36; historical damage alone is insufficient.
  Preserve activity guards and valid arrivals, then publish through the existing movement owner.
- [x] Rerun the new distribution class and the existing shelter class.
  **Expected GREEN:** all accounting, route, endpoint, lifecycle, safety, and HOME/ticket assertions pass.
  Review for duplicate reservations, guessed membership, and unnecessary abstractions.

### Task 4: Verify actual arrivals, bounded cost, and final integration

**Files:** New distribution GameTests; existing shelter tests and final shared-code diff.

**Consumes:** completed Tasks 1–3. **Produces:** runtime evidence and a candid delivery report.

- [x] Add `consecutiveArrivalsEnterDifferentHousesAtCapacity` with real ticking entities,
  and `emergencyMovementIsNotBlockedByCapacity`. Include connected floors and
  villagers initially on a bed. **Expected:** usable-floor arrival, local room wandering,
  no capacity-induced eviction, no new HOME/ticket claim, emergency movement retains priority.
- [x] Run the new class and require actual ticks/arrivals. Repair fixture setup failures
  at their owner; do not change unrelated navigation or the user's saved world.
- [ ] Measure equivalent cold/warm small and larger loaded-village attempts.
  Record loaded entity count, discovered houses, scanned/validated blocks, path requests,
  and elapsed time. **Expected:** at most ten house path requests, unchanged producer cadence,
  bounded geometry, shared reuse, no forced chunks. Do not claim CPU/MSPT savings from code inspection.
- [x] Run serially with `--console=plain --no-daemon --no-parallel`:
  `:common:test` (native route and live geometry cases use GameTests);
  `:neoforge:runGameTestServer -PmcaGameTest=ShelterDistributionGameTests`;
  `:neoforge:runGameTestServer -PmcaGameTest=HomelessShelterGameTests`;
  and `:fabric:build :neoforge:build`.
  **Expected:** actual required counts, zero failed required tests, exit code 0. Record exact results.
- [ ] Observe a disposable client village at night: nearest full house, nearby alternative,
  monsters, already sheltered villagers, and furnishings. Record the observed result;
  if unavailable, report client appearance/naturalness as unverified.
- [x] Review the focused diff and `git diff --check`; use one fresh read-only reviewer.
  Review all five focus cases, fix material defects with regression evidence, and
  report any remaining unknown-house or performance limitation. Preserve unrelated edits.

## Execution preflight and handoff

Dependencies are sequential: Task 1 fixes the membership/arrival contracts;
Task 2 supplies complete house views; Task 3 publishes the final policy;
Task 4 verifies actual movement and cost. Each later task reads recorded rulings.

Use the current checkout and a plan-scoped ignored ledger at
`.superpowers/sdd/2026-10-05-nighttime-shelter-distribution/progress.md`.
Native PowerShell bookkeeping replaces the skill's Bash helper scripts; this
does not authorize WSL or a new worktree. Keep evidence while changes remain
uncommitted. Do not ask again for permission for work already authorized.

Implementation is present inline and uncommitted. Fresh baseline: 17/17 shelter
tests passed. New admission RED/GREEN: 16/16 passed after the implementation.
Expanded runtime checks verified connected floors, exact 30/31 route boundaries,
actual two-house arrivals, unloaded POI handling, and stale registered membership.
Two fresh review findings (cached connector reads and wide native endpoints) were
reproduced and fixed; both regressions pass in the 35-test GREEN run, with successful process exit.
Fresh integration verification: all 17 existing shelter GameTests passed, all
533 common JUnit tests passed with no failures/errors/skips, and both Fabric and
NeoForge builds passed. The 35 distribution cases include actual arrivals and an
explicit incomplete-membership admission assertion. These focused runs do not
prove the full GameTest suite. Initial missing-class discovery output was resolved
by the successful serialized retry; its cause was not conclusively established.

The small two-house fixture measures cold/warm lookup and selection and verifies
shared room reuse and two house path requests. Larger-village MSPT profiling and
total ceiling/connector block-read measurements above remain unchecked.

The existing cache remains the single geometry owner. Each house query permits
20 new floor scans and at most eight materialized floors; current-room lookup
and destination selection are separate queries, so a cold producer attempt may
permit up to 40 scans before shared reuse. Ceiling/connector inspection additionally
depends on the dimension height. Observation storage retains its 8192-block entry
and 65536-block total limits. This is a bounded discovery policy, not evidence
of a village-scale CPU/MSPT improvement.

Unknown membership preserves known room access and bypasses the soft cap.
Oversized structures without a resolvable room remain outside existing discovery
limits. The whole-house grouping, native route arithmetic, and danger cases use
the 35-test distribution class; conditional common classes and the standalone
geometry adapter listed above were not needed or created.
Client appearance/naturalness remains unverified. Evidence and rulings stay in
the plan-scoped ignored ledger because no commit or cleanup was authorized.

## 2026-10-06 shelter limit revision

The pre-fix client log showed useful houses omitted by the five-house cutoff and
reachable alternatives rejected by the 30-block additional-route allowance.
The current policy considers up to ten houses and permits 64 extra route blocks,
inclusive. Bed acquisition retains its bounded five-candidate state validation;
the 48-block anchor radius, retry cadence, geometry limits, and ownership remain.

The pre-change 48-test distribution run failed five expected admission cases.
After the change, all 48 passed, covering an available sixth house, real routes
beyond the old allowance, exact 64/65 boundaries, and less-crowded overflow.
The real detour fixture was extended to exceed 64 extra blocks and still rejects
the distant alternative. All 17 existing shelter GameTests and 552 common JUnit
tests passed, with no JUnit failures/errors/skips. Fabric and NeoForge builds and
the final diff check passed. Checks ran serially in disposable GameTest worlds.
The larger house budget permits more route searches per selection; village-scale
MSPT and client appearance remain unverified.
