# Nighttime shelter distribution

Status: implemented inline on `dev/1.21.1`, 2026-10-06; server tests and loader builds verified.
The server tests cover whole-house discovery, admission, route limits, threats,
and actual arrivals. Client appearance and village-scale performance remain unverified.
Fresh results: 48 distribution GameTests, 17 existing shelter GameTests, and
552 common JUnit tests passed; Fabric and NeoForge builds passed. The focused
GameTest runs do not establish full GameTest-suite health.

## Intended outcome

Incoming homeless REST villagers prefer the nearest reachable house with space.
Normal capacity is the number of compatible bed heads in that house plus five
villagers. They may choose an alternative up to **64 additional walking-route
blocks**, inclusive, compared with the nearest reachable shelter considered.

This is a soft admission preference. When no under-capacity alternative qualifies,
overflow prefers the reachable house least over its normal capacity within the
same 64 additional route blocks, breaking ties by route length and then discovery
order. Detected danger retains the nearest reachable shelter.
Already-sheltered villagers stay; returning HOME owners and PANIC/HIDE/fleeing
movement keep their existing owners. Temporary shelter never claims HOME or a POI
ticket. Use disposable test worlds; do not modify or copy the user's saved world.

## Current implementation and evidence

The current shared-code owners are:

- `SeekIndoorShelterTask`: homeless REST entry; searches compatible HOME anchors
  within 48 blocks with occupancy ANY and retries after 20–39 ticks. It groups
  anchors before the ten-house path-request limit, compares actual native route
  length, and publishes the selected usable floor through existing navigation.
- `IndoorRoomCache`, owned by `VillageManager`: one transient server-thread cache
  per level. `resolve(BlockPos bed)` returns one immutable `Room.floorCells()`.
  Cache misses use fresh `SelectedFloorScanner` and `BuildingRoomScanner`
  partitioning. Persisted registered room cells are no longer a discovery shortcut.
  `resolveHouse` and `resolveHouses` retain full-floor evidence and follow supported
  connector handoffs in that same cache. Registered identity supplies required
  coverage evidence; stale or unavailable membership bypasses capacity rather
  than imposing a guessed partial cap.
- `MemoryModuleTypeMCA.SHELTER_BED`: a selected anchor shared by entry and
  wandering, without bed ownership. Brain activity cleanup and the core behavior
  clear it when REST ends or HOME is acquired.
- `EnterBuildingTask.isUsableFloor`: supported collision-free standing space,
  excluding doors, bed blocks, and positions immediately above beds. Doorways
  remain navigation passages; they are excluded as idle destinations.
- `LocalInsideBrownianWalk`: chooses nearby usable floor in that same resolved
  room. It now has its own room-based selection rather than delegating to vanilla
  Brownian selection. It prefers steps of 2–4 blocks when available and retains
  its 40-tick retry gate.

[Inspect pathfinding task context](thread://01a10d25-2949-7020-9c55-27bd9f739771?hostId=local)
reports the partition-cache regression failing before its correction, then all
17 shelter GameTests and both loader builds passing. The saved completion logs
were inspected for this revision. These are prior-run results, not a fresh rerun
or proof of distribution, whole-house geometry, performance, or client appearance.

Retain that architecture and its regressions. Add admission before shelter
selection; do not rewrite local wandering or create another room scanner/cache.

## Selection and incoming lifecycle

1. Ordinary selection runs only during homeless REST, with existing combat and
   walking-target guards. Sleeping or properly sheltered villagers do not relocate.
2. Discover compatible bed anchors within the existing 48-block radius. Resolve
   house identity before limiting navigation to ten distinct houses. Multiple
   beds or rooms in one house must not consume the house budget.
3. Get standing endpoints from the existing room cache and usable-floor predicate.
   Capacity membership and movement clearance are separate: occupied beds count
   toward capacity although they cannot be standing destinations.
4. Measure each inspected reachable route once. In ordinary conditions choose the
   shortest under-capacity route at most 64 blocks longer than the shortest
   reachable route. Discovery order breaks ties. Unknown capacity permits entry.
5. With no qualifying under-capacity alternative, choose the house with the fewest
   occupants over its `bedCount + 5` capacity within the same route allowance.
   Count reserved beds, physical guests and active arrivals, excluding the selector.
   Equal overflow counts prefer the shorter route, then discovery order. Detected
   danger selects the shortest reachable route. No reachable shelter retains the existing village-seeking
   behavior; no new teleport or movement fallback is introduced.
6. Publish through `EnterBuildingTask.start` and the existing persistent movement
   owner. Set `SHELTER_BED` to the chosen anchor. Navigation owns movement.

Preserve a valid arrival already in progress; do not rerun admission every tick or
redirect it merely because the house fills later. An expired, cleared, redirected,
or completed arrival no longer consumes an incoming place. `SHELTER_BED` alone
is **not** a reservation: it can outlive WALK_TARGET while the villager idles.

Trace how `moveTowardsPersistent`, WALK_TARGET, and any retained movement intent
represent a still-active arrival. Reuse that authoritative lifecycle rather than
add a shelter reservation map. If a villager has arrived in another suitable room,
identify its current shelter rather than send it back solely because the selected
anchor still resolves elsewhere.

Route length is the sum of geometric distances from the villager's current
position through the actual entity node positions to the reached endpoint.
Validate the last reached node as well as the nominal Path target; bed/door target
normalization must not turn a valid requested floor into an invalid endpoint.
Node count and straight-line bed distance are insufficient.

## House identity and implemented geometry

A `Room.floorCells()` set is one selected physical room, not a whole-house
identity or a vertical-band membership query. Do not implement beds-plus-five
per room and describe it as the agreed house policy.

For registered houses, logical building identity is scoped to the village and
dimension. `Village.findPhysicalRoomAt`, `getStructureFor`, and
`getLogicalBuildingId` supply canonical association. Rooms/floors of one logical
building share capacity. Persisted identity does not prove current physical
geometry, especially when autoScan is disabled.

Unregistered houses use the existing floor scanner and connector owners through
`IndoorRoomCache.resolveHouse` and `resolveHouses`. Runtime tests cover internal
rooms, connected floors, and separate neighboring houses. Each query permits up
to 20 new floor scans and eight materialized floors per house. A producer's
current-room check and destination selection can perform two queries, permitting
up to 40 cold scans; warm evidence is shared. Ceiling and connector inspection
also depends on dimension height. These limits are not an MSPT measurement.

The existing cache retains fresh floor evidence and room components. Registered
identity supplies additional discovery seeds and required coverage, not trusted
saved geometry. Loaded HOME POI anchors are queried without reading bed blocks in
the task's predicate; the cache owns guarded physical bed validation. Cached
connector reads and destinations also require loaded chunks. No independent
scanner, automatic registration, second cache, persistent house IDs, or
radius-based grouping was added. Unknown or incomplete whole-house membership
permits entry to a resolved room without enforcing a partial bed count. This can
still crowd; an oversized structure that cannot resolve even a room remains
outside the existing discovery limits.

Resolve bed heads across the whole known house, including beds outside the initial
anchor radius when already within resolved geometry. Membership includes sleeping
and standing occupants in exact physical vertical bands, not only entities whose
block position equals a floor-cell coordinate.

## Admission accounting

Normal capacity is `bedCount + 5`; compatible bed heads count once, occupied or
not. Count children, adult MCA villagers, and vanilla villagers; players and
monsters do not consume places.

Account for valid bed-owner reservations, additional physical occupants, and
active incoming villagers. Known UUIDs count once per house. A guest standing in
one house while walking into another occupies the former and reserves arrival in
the latter. Exclude the selecting villager from the pre-admission total.

A claimed HOME POI reserves one place even if its owner is unloaded or unidentified.
Reconcile loaded HOME brains and valid persisted MCA HOME assignments with those
claims. Do not count a known owner again when present or incoming, or count the
same bed through both persisted and live assignments. A valid forced HOME must be
handled even if its normal ticket state differs. Never infer an owner UUID from a
POI claim alone or reserve from an invalid/stale bed assignment.

Use one operation-local view of alive loaded villagers and active destinations,
covering distant arrivals outside a house bounding box and the selecting villager's
48-block search area. Keep physical membership separate from incoming membership.
Count and publish sequentially on the server thread so a second selection sees
the first destination immediately. Preserve HOME and ticket counts.

## Safety, threading, and work bounds

Ordinary admission must not run during active PANIC/HIDE/fleeing/combat ownership,
including activity-transition ticks. Detected threats before PANIC may bypass the
capacity preference. Use the existing memories and all their actual sensor owners:
vanilla hostiles and MCA's ignited-creeper sensor both write NEAREST_HOSTILE.
Do not assume every remembered hostile appears in vanilla's type-distance table.
Historical HURT_BY damage alone must not cause permanent overflow. A live,
same-level nearby HURT_BY_ENTITY uses the vanilla calm-down distance, squared 36.

Minecraft world, entity, brain, POI, navigation, and room-cache access stays on the
server thread. An immutable local view is useful for clear accounting, not license
to access the live world asynchronously. No locks, atomics, concurrent reservation
maps, parallel streams, or executors are needed.

Retain current cadence and cache limits: 512 scan cells, radius 16, 128 entries,
8192 observed blocks per entry, 65536 total observed blocks, validation every
40 ticks, failed retry after 200 ticks, and idle expiry after 1200 ticks.
These are room-cache limits, not proof that repeated whole-house work is cheap.
Measure cold discovery, warm reuse, many-bed grouping, and loaded-entity accounting;
bound geometry separately from the ten-house navigation budget. Reuse within an
operation and avoid forced chunk loading or increased scan defaults without evidence.

## Acceptance and delivery

The plan must verify whole-house membership first, then admission and route/safety
selection together. Cover adjacent unregistered houses, connected floors, stale
registered partitions, whole-house bed enumeration, owner de-duplication, consecutive
arrivals, cleared/redirected intent, exactly 64 extra route blocks, excessive detours,
danger including ignited creepers, and all-full/unreachable overflow.

Retain the 17 existing shelter regressions for real entry/wandering, bed and leaf
surfaces, doors, cache sharing, cold discovery, topology invalidation/expiry, and
memory cleanup. Add real arrival and emergency-transition tests rather than rely
only on manually produced destinations. Check both loaders after runtime changes.
A disposable client check is required to claim natural-looking movement.

The inline implementation supplies whole-house discovery and admission in the
existing shared owners. Delivery records fresh runtime tests and loader builds
separately from the still-unverified client appearance and village-scale cost.
