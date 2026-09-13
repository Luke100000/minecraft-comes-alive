# Village-Scoped Mourning Specification

> **Current design (2026-09-13):** This supersedes the multi-burst session design described below. The older text is retained as implementation history only.
>
> Ambient mourning is now one self-contained village burst at a time:
>
> - `Village` persists only `nextMourningTime`.
> - After an ambient burst executes, the next one is randomized 4,000-9,000 ticks later.
> - A burst selects 2-4 currently eligible loaded residents, independent of village population.
> - Ambient bursts execute only during the daytime window 1,000-11,000 day ticks. If a burst becomes due at night, it remains due and runs after daytime returns; it is not consumed or rescheduled at night.
> - Each occurrence discovers the currently loaded, valid occupied graves once. There is no persisted session, remaining budget, later-burst timestamp, or grave cache.
> - `LAST_AMBIENT_MOURNING` remains a soft fairness timestamp so small villages do not repeatedly choose the same residents.
> - Personal/family mourning remains event-driven and separate from the ambient scheduler. Close family members are assigned the deceased's exact grave by the tragedy propagation path, so a death can produce a natural family group rather than affecting the ambient burst budget.
> - Existing FOLLOW/STAY/work/rest/chore/danger guards, exact-grave retry behaviour, loaded-chunk filtering, resurrection cleanup, and the master mourning toggle remain unchanged.

## Goal

Replace MCA's per-villager periodic mourning schedule with a village-owned ambient mourning schedule while preserving the July 2026 mourning rework's exact-grave targeting, standing-position logic, pathfinding, dialogue, and relationship-driven mourning.

The result should scale to large villages without turning every resident into an independent graveyard visitor.

## Current problem

Today every villager independently evaluates periodic mourning from `GrieveTask`:

1. `GrieveTask` checks whether the villager's home village has any mournable grave.
2. `VillagerBrain.shouldGrieve()` gives that villager an independent seven-day cadence.
3. When due, the villager starts `ActivitiesMCA.GRIEVE` and chooses a grave.

The initial offsets are randomized, but population still determines traffic. A 200-villager village produces roughly `200 / 7 ≈ 29` periodic mourning opportunities per Minecraft day. Travel and dialogue duration make those visits overlap, so the graveyard can remain continuously busy.

The eligibility order is also unnecessarily expensive: `hasPeriodicMourningCandidate()` traverses complete graveyard buildings before `shouldGrieve()` discovers that most villagers are not due.

## Existing behaviour to preserve

Do not undo the July mourning rework. Keep:

- exact `MOURNING_SITE` targeting
- exact `MOURNING_POSITION` standing targets
- occupied tombstone validation
- exclusion of resurrecting tombstones
- nearby safe standing-position selection
- reservation-based spreading around a grave
- existing long-distance/path timeout behaviour
- flower holding and mourning dialogue
- cleanup when a grave becomes invalid

The scheduling change must not alter tombstone creation, family-tree updates, marriage state, tragedy mood penalties, or murder heart penalties.

## Personal mourning

Death-triggered mourning remains immediate and relationship-driven.

The existing `EntityRelationship.onTragedy(...)` propagation must remain unchanged in scope:

- parents of the deceased are notified with `RelationshipType.CHILD`
- siblings are notified with `RelationshipType.SIBLING`
- spouse is notified with `RelationshipType.SPOUSE`
- nearby strangers receive `RelationshipType.STRANGER` tragedy effects but do not mourn

The current code does not propagate the tragedy to children of the deceased; this feature must not silently expand that relationship graph.

Related villagers target the deceased villager's exact burial site. Personal mourning is independent of the village ambient schedule and does not consume or postpone the next ambient session.

## Ambient mourning sessions

Periodic remembrance becomes owned by `Village`, which already has a server-side `tick(ServerLevel, long)` lifecycle and NBT persistence.

Instead of sending one large group at once, a village runs a bounded mourning **session** made of staggered bursts. A large village can therefore keep its graveyard visibly active across part of a Minecraft day without launching a large pathfinding wave on one tick.

Each village stores:

```java
private long nextMourningTime;
private int mourningRemaining;
private long nextMourningBurstTime;
```

`nextMourningTime` is the next session start. `mourningRemaining` is the unspent mourner budget for the active session. `nextMourningBurstTime` is the next time that budget may be released.

When no session timestamp exists (old save/new village), the first enabled tick schedules a future session and returns. It must not immediately send villagers to the graveyard.

### Starting a session

When `nextMourningTime` is reached and no session is active:

1. Schedule the next session immediately so failure/no-grave cases cannot retry every tick.
2. Discover the current mournable graves once and keep that list only as transient session state.
3. If no mournable graves exist, finish without opening a session; the already-scheduled future session remains intact.
4. Count the currently loaded residents.
5. Compute the session budget as `clamp(3 + residentCount / 22, 3, 12)`.
6. Store that budget in `mourningRemaining`.
7. Make the first burst due immediately.

This gives approximately:

- 20 residents -> 3 mourners per session
- 50 residents -> 5
- 100 residents -> 7
- 150 residents -> 9
- 200+ residents -> 12 (hard cap)

The session interval is random 1-2 Minecraft days (`24_000`-`48_000` ticks).

### Releasing a burst

When an active session reaches `nextMourningBurstTime`:

1. Choose a burst budget of 2-4 villagers, capped by `mourningRemaining`.
2. Reuse the session's transient grave cache. Validate only those cached positions against the canonical tombstone predicate and drop graves that became invalid.
3. If this is an active session restored from NBT and the transient cache does not exist, rebuild it once before the burst. Do not rescan complete graveyards again during that loaded session.
4. If no cached mournable graves remain, end the active session immediately; the already-scheduled future session remains intact.
5. Build the eligible resident list fresh at burst time, so chores, danger, death, unloading, daily activity, and already-running mourning are respected.
6. Shuffle candidates using the server world's `RandomSource`, then stably order them by `LAST_AMBIENT_MOURNING` ascending, treating no value as oldest. This randomizes ties while preferring villagers who have not been selected recently.
7. Select up to the burst budget from that ordered list.
8. Shuffle the valid cached graves and prefer different graves while multiple graves exist; reuse graves only when selected mourners outnumber graves.
9. Start the existing mourning activity for the selected residents and write `LAST_AMBIENT_MOURNING = currentGameTime` for each newly selected ambient mourner.
10. Consume the full burst budget from `mourningRemaining` even if fewer residents were eligible. This guarantees that a session cannot remain active indefinitely because the village was temporarily busy.
11. If budget remains, schedule the next burst 2-4 Minecraft hours later (`2_000`-`4_000` ticks). Otherwise clear `nextMourningBurstTime`, clear the transient grave cache, and finish the session.

At most four ambient villagers therefore start pathfinding on one burst, while a large village may send up to twelve across the whole session.

Do not add interval/count/burst config fields in this change. They can be added later if players actually need them.

## Ambient mourner eligibility

An ambient candidate must:

- be alive
- be a loaded resident of the scheduling `Village`
- currently be in `Activity.IDLE` or `Activity.MEET`
- not currently be in `Activity.WORK`, `Activity.REST`, or `ActivitiesMCA.CHORE`
- not have `PLAYER_FOLLOWING` or `STAYING`; these player-directed modes run as CORE behavior while the non-core activity can still remain `IDLE`
- not already have `MOURNING_SITE`
- not already be in `ActivitiesMCA.GRIEVE`
- not be in danger according to `VillagerTasksMCA.isInDanger(...)`
- not be performing a player-assigned chore (`Chore.NONE`)

Ambient mourning must not pull a villager out of work or sleep. Personal death-triggered mourning remains immediate and may interrupt the normal schedule. Do not perform navigation/path construction during ambient candidate selection; the existing mourning activity remains responsible for pathing.

## Shared mourning domain helper

Create one focused helper at:

`common/src/main/java/net/conczin/mca/entity/ai/Mourning.java`

It owns the behaviour shared by `Relationship`, `Village`, and `EnterGraveyardTask`:

```java
public static void start(VillagerEntityMCA villager, BlockPos grave)
public static void clear(VillagerEntityMCA villager)
public static boolean isMournableTombstone(Level level, BlockPos position)
public static List<BlockPos> getMournableGraves(Village village, Level level)
public static boolean canMournAmbiently(VillagerEntityMCA villager)
```

`start(...)` must:

- set `MOURNING_SITE`
- clear stale `MOURNING_POSITION`
- clear stale `MOURNING_RETRY_AT`
- clear `PATH`
- clear `WALK_TARGET`
- point `LOOK_TARGET` at the grave
- activate `ActivitiesMCA.GRIEVE`

`clear(...)` must erase `MOURNING_SITE`, `MOURNING_POSITION`, and `MOURNING_RETRY_AT`.

`isMournableTombstone(...)` is the single canonical grave predicate. `EnterGraveyardTask` must delegate to it instead of keeping a duplicate private predicate.

`getMournableGraves(...)` must use complete `graveyard` buildings and the canonical predicate.

## Retry behaviour

Do not remove `GrieveTask` entirely. Repurpose it from periodic scheduling into a cheap retry trigger for an already-assigned personal/ambient grave.

Add a new persisted brain memory:

```java
MemoryModuleType<Long> MOURNING_RETRY_AT
```

The retry flow becomes:

1. A mourning attempt fails while its assigned grave is still mournable.
2. Keep `MOURNING_SITE` so the retry remains tied to the same grave.
3. Clear only `MOURNING_POSITION` and transient walk/path state.
4. Set `MOURNING_RETRY_AT = gameTime + 1200`.
5. Return the villager to its scheduled activity.
6. Idle `GrieveTask` checks only whether an assigned site exists, is still valid, and its retry timestamp is due.
7. When due, reactivate `ActivitiesMCA.GRIEVE` for that same grave.

`GrieveTask` must never scan village graveyards or create ambient mourning opportunities.

On successful mourning or an invalid/removed grave, clear the mourning memories completely.

When a villager is recreated by tombstone resurrection, the existing resurrection cleanup must also erase `MOURNING_RETRY_AT` and `LAST_AMBIENT_MOURNING` so a resurrected villager inherits neither a stale retry deadline nor the dead entity's ambient-selection recency.

This keeps retry behaviour while removing the expensive periodic polling path.

## Legacy `LAST_GRIEVE` and ambient fairness

The old per-villager seven-day scheduler must stop using:

- `GRIEVE_COOLDOWN`
- `setGrieving()`
- `shouldGrieve()`
- `justGrieved()`
- old cooldown-based `retryGrievingLater()` semantics

Keep the `LAST_GRIEVE` memory registration and Brain profile entry only for legacy decode compatibility. Do not reuse its saved values for the new system: historical saves may contain a true completion timestamp, a randomized initial offset, a negative forced-grief value, or a retry-derived synthetic timestamp.

Add a separate persisted memory:

```java
MemoryModuleType<Long> LAST_AMBIENT_MOURNING
```

Only the village burst selector reads/writes `LAST_AMBIENT_MOURNING`. Missing/oldest timestamps are preferred; recently selected villagers remain fallback candidates when too few other residents are eligible, so this is a soft fairness rule rather than a hard cooldown.

`LAST_GRIEVE` must never trigger mourning, participate in fairness, or be used as a retry deadline after this change.

`MOURNING_RETRY_AT` remains the only retry deadline.

## Master config

Add to `Config`:

```java
public boolean enableMourning = true;
```

When `false`:

- `Village` does not schedule or start ambient mourning
- `Relationship` does not start death-triggered grave mourning
- `GrieveTask` does not restart failed mourning attempts
- already-running mourning may finish naturally
- tragedy mood/heart effects continue
- relationship/family state changes continue
- tombstone placement/filling continues

Because `Config` has a no-argument constructor and Gson deserializes into initialized fields, an existing version-2 config that lacks `enableMourning` must retain the field initializer `true` rather than silently disabling mourning.

## Village time semantics

`Village.tick(ServerLevel, long)` currently offsets its local `time` by village id for older periodic systems. Mourning timestamps must use absolute server game time.

Call the mourning scheduler with the unmodified `time` before:

```java
time += getId();
```

Persist `nextMourningTime` as that absolute game-time value.

## Performance requirements

Normal idle villagers must no longer perform graveyard discovery.

Complete-graveyard discovery occurs once when a session opens. If an active session is loaded from NBT, its transient cache may be rebuilt once on the first due burst after load. Later bursts validate only cached grave positions; they do not traverse graveyard buildings again. A maximum-size session can create at most six bursts because the minimum burst budget is two.

The 256-block standing-position reservation scan remains execution-only: it runs only for villagers who were actually selected to mourn.

At most four new ambient mourners may be started by one burst, and at most twelve may be budgeted for one session regardless of population size.

## Persistence and compatibility

- `Village(CompoundTag, ServerLevel)` reads `nextMourningTime`, `mourningRemaining`, and `nextMourningBurstTime`, defaulting each to zero when absent.
- `Village.save()` writes all three values.
- the active session's grave list is transient and is never serialized
- A loaded `nextMourningTime == 0L` with no active session means “unscheduled”; the next enabled village tick schedules a future session and returns.
- `mourningRemaining > 0` means a session is active. If its burst timestamp is missing/zero, treat that burst as due on the next enabled tick rather than discarding the session.
- when an active session is restored without its transient grave cache, rebuild the cache exactly once before the next due burst
- When mourning is disabled, scheduling/session state is frozen. Re-enabling resumes any overdue session burst before starting another session.
- No Room DFU change is needed because the new field belongs to top-level village state rather than room/building schema.
- No tombstone NBT or family-tree format changes are allowed.
- `LAST_GRIEVE` remains registered/profiled only for legacy brain decode compatibility.
- `LAST_AMBIENT_MOURNING` is the only persisted ambient fairness timestamp.

## Required tests

### Common JUnit

Create:

- `common/src/test/java/net/conczin/mca/ConfigMourningTest.java`
- `common/src/test/java/net/conczin/mca/server/world/data/VillageMourningScheduleTest.java`

Cover:

- missing `enableMourning` JSON keeps the default `true`
- explicit `enableMourning: false` deserializes as `false`
- legacy village NBT without `nextMourningTime` loads as unscheduled (`0L`)
- session scheduling produces a timestamp within 1-2 Minecraft days
- burst scheduling produces a timestamp 2-4 Minecraft hours later
- session-size calculation is population-scaled and hard-capped at 12
- `nextMourningTime`, `mourningRemaining`, and `nextMourningBurstTime` round-trip through `Village.save()`
- the grave cache is not written to village NBT

### NeoForge GameTests

Create:

`neoforge/src/main/java/net/conczin/mca/server/world/data/VillageMourningGameTests.java`

Cover:

- empty tombstone is not mournable
- occupied tombstone is mournable
- resurrecting occupied tombstone is not mournable
- due village session with no grave assigns nobody and does not spin/retry every tick
- due village session with an occupied grave starts only one 2-4-villager burst, not the whole session budget
- an active session does not release another burst before `nextMourningBurstTime`
- a due later burst re-evaluates current eligibility instead of using a preselected queue
- ambient selection accepts `IDLE`/`MEET` residents but does not pull `WORK`/`REST` residents into `GRIEVE`
- `LAST_AMBIENT_MOURNING` fairness prefers never/older-selected residents when enough are available, but recently selected residents remain valid fallback candidates
- a 200+ resident session budget never exceeds 12 and no individual burst exceeds 4
- selected resident receives `MOURNING_SITE` and enters `GRIEVE`
- selected ambient resident receives the current game time in `LAST_AMBIENT_MOURNING`
- multiple bursts in one uninterrupted session perform one complete graveyard discovery; an NBT-restored active session performs at most one rebuild before continuing
- unrelated `STRANGER` tragedy does not assign a mourning site
- a relationship tragedy (`SPOUSE` is sufficient to test this local branch) assigns the exact burial site when mourning is enabled
- the same relationship tragedy does not assign a site when `enableMourning` is false, while the tragedy mood effect still occurs
- failed mourning retry keeps the same `MOURNING_SITE` and waits until `MOURNING_RETRY_AT`
- tombstone resurrection clears both `MOURNING_RETRY_AT` and `LAST_AMBIENT_MOURNING`

## Non-goals

- changing who counts as family for tragedy propagation
- adding child-of-deceased propagation
- redesigning graves/tombstones
- changing general villager navigation
- changing tragedy mood values
- configurable session interval, session size, burst size, or burst delay in this change
- global/static mourning scheduler
- new event-bus subscriber for village mourning
