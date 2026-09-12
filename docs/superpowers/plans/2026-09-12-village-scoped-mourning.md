# Village-Scoped Mourning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace per-villager periodic mourning with persisted village-owned mourning sessions that scale to 3-12 total mourners but release only 2-4 villagers per staggered burst, while preserving immediate relationship-driven mourning and the July 2026 exact-grave/pathfinding behaviour.

**Architecture:** `Village.tick(ServerLevel, long)` owns the only ambient cadence and persists the next session timestamp plus active-session burst state. A due session computes one population-scaled budget, discovers mournable graves once into transient session state, then releases that budget in 2-4-villager bursts 2-4 Minecraft hours apart. Each burst re-evaluates current resident eligibility, respects the villager's IDLE/MEET daily activity, revalidates only cached graves, and uses `LAST_AMBIENT_MOURNING` as a fairness/recency marker. A new `Mourning` domain helper owns canonical grave validation/discovery plus shared mourning start/clear/eligibility logic. `GrieveTask` remains registered in idle AI only as a cheap retry trigger for an already assigned grave; it never discovers graveyards or creates ambient opportunities.

**Tech Stack:** Java 21, Minecraft 1.21.1, Gradle, MCA Brain/Behavior APIs, MCA village NBT persistence, JUnit 5, NeoForge GameTest.

**Spec:** `docs/superpowers/specs/2026-09-12-village-scoped-mourning.md`

## Global Constraints

- Preserve `MOURNING_SITE`, `MOURNING_POSITION`, exact occupied-grave validation, resurrection exclusion, safe standing-position selection, reservation spreading, path timeout behaviour, flowers/dialogue, and invalid-grave cleanup.
- Ambient session cadence is randomized between `24_000L` and `48_000L` ticks and is owned by `Village`.
- Session size is `clamp(3 + loadedResidentCount / 22, 3, 12)`.
- An active session releases only `2-4` villagers per burst, with `2_000L-4_000L` ticks between bursts.
- The full burst budget is consumed even when fewer residents are eligible, so sessions cannot remain active indefinitely.
- Do not add session interval/size or burst size/delay config fields.
- `enableMourning = false` blocks new ambient mourning, new death-triggered grave mourning, and retry restarts, while tragedy mood/heart/family effects and tombstone handling continue.
- Personal tragedy propagation remains parents/siblings/spouse exactly as it is today; do not add children-of-deceased propagation.
- `LAST_GRIEVE` remains registered/profiled only for legacy brain decode compatibility and has no runtime mourning use.
- `LAST_AMBIENT_MOURNING` is a persisted `MemoryModuleType<Long>` read/written only by ambient burst selection as a soft fairness marker.
- `MOURNING_RETRY_AT` is a persisted `MemoryModuleType<Long>` and is the only retry deadline.
- Ambient selection may use residents currently in `Activity.IDLE` or `Activity.MEET`; it must not pull villagers out of `WORK`, `REST`, panic, hide, raid, chores, danger handling, or player-directed FOLLOW/STAY modes (`PLAYER_FOLLOWING` / `STAYING`).
- Complete graveyard discovery happens once per session. A restored active session may rebuild its transient cache once on its first due burst after load; later bursts only revalidate cached positions.
- `Village.tick` must evaluate mourning with the unmodified absolute `time` before the existing `time += getId()` offset.
- Do not modify `VillageManager` for mourning scheduling.
- Preserve unrelated dirty worktree changes.

---

## File Map

### Create

- `common/src/main/java/net/conczin/mca/entity/ai/Mourning.java`
  - canonical mournable-tombstone predicate
  - village grave discovery
  - shared mourning start/clear operations
  - ambient candidate predicate
- `common/src/test/java/net/conczin/mca/ConfigMourningTest.java`
  - config default/explicit-disable deserialization
- `common/src/test/java/net/conczin/mca/server/world/data/VillageMourningScheduleTest.java`
  - timestamp default, interval bounds, and NBT round-trip
- `neoforge/src/main/java/net/conczin/mca/server/world/data/VillageMourningGameTests.java`
  - world/entity/tombstone/relationship/retry integration coverage

### Modify

- `common/src/main/java/net/conczin/mca/Config.java`
  - add `public boolean enableMourning = true`
- `common/src/main/java/net/conczin/mca/entity/ai/MemoryModuleTypeMCA.java`
  - add persisted `LAST_AMBIENT_MOURNING`
  - add persisted `MOURNING_RETRY_AT`
  - retain `LAST_GRIEVE` for decode compatibility only
- `common/src/main/java/net/conczin/mca/entity/ai/Relationship.java`
  - delegate personal grave mourning to `Mourning.start(...)` behind the config flag
- `common/src/main/java/net/conczin/mca/entity/ai/brain/VillagerTasksMCA.java`
  - register `LAST_AMBIENT_MOURNING` in the Brain profile
  - register `MOURNING_RETRY_AT` in the Brain profile
  - replace old completion/cooldown cleanup with success/invalid clear versus delayed retry
  - keep `GrieveTask` in the idle package
- `common/src/main/java/net/conczin/mca/entity/ai/brain/VillagerBrain.java`
  - remove `GRIEVE_COOLDOWN`, `GRIEVE_RETRY_DELAY`, `setGrieving()`, `retryGrievingLater()`, `justGrieved()`, `shouldGrieve()`
- `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/GrieveTask.java`
  - become assigned-site retry-only logic
- `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/EnterGraveyardTask.java`
  - require an already assigned grave and delegate grave validation to `Mourning`
  - remove periodic graveyard discovery
- `common/src/main/java/net/conczin/mca/block/TombstoneBlock.java`
  - erase `MOURNING_RETRY_AT` and `LAST_AMBIENT_MOURNING` during resurrection cleanup
- `common/src/main/java/net/conczin/mca/server/world/data/Village.java`
  - persist `nextMourningTime`, `mourningRemaining`, and `nextMourningBurstTime`
  - schedule sessions and staggered ambient bursts in `tick(...)`
  - keep a transient grave cache plus initialized flag for the active session
  - use `LAST_AMBIENT_MOURNING` ordering for ambient fairness

### Explicitly unchanged

- `common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java`
- `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/MournAtGraveTask.java`

---

### Task 1: Add the master config without breaking existing version-2 configs

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/Config.java`
- Create: `common/src/test/java/net/conczin/mca/ConfigMourningTest.java`

**Interfaces:**
- Produces: `public boolean enableMourning = true`
- Consumed later by: `Village`, `Relationship`, `GrieveTask`

- [ ] **Step 1: Write the failing config tests**

Create `ConfigMourningTest` with direct Gson deserialization so the test exercises the same reflective construction used by `Config.loadOrCreate()`:

```java
package net.conczin.mca;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigMourningTest {
    private static final Gson GSON = new Gson();

    @Test
    void missingMourningFlagKeepsDefaultEnabled() {
        Config config = GSON.fromJson("{\"version\":2}", Config.class);

        assertTrue(config.enableMourning);
    }

    @Test
    void explicitMourningDisableIsLoaded() {
        Config config = GSON.fromJson("{\"version\":2,\"enableMourning\":false}", Config.class);

        assertFalse(config.enableMourning);
    }
}
```

- [ ] **Step 2: Run the focused test and verify RED**

Run:

```powershell
./gradlew :common:test --tests 'net.conczin.mca.ConfigMourningTest'
```

Expected: compilation fails because `Config.enableMourning` does not exist.

- [ ] **Step 3: Add the config field beside the existing tombstone settings**

In `Config.java`, immediately after `defaultHeadstoneType`, add:

```java
/** Enables personal and ambient villager mourning at occupied graves. */
public boolean enableMourning = true;
```

Do not bump `Config.VERSION`; the initializer is deliberately how existing version-2 files gain the new default.

- [ ] **Step 4: Run the focused test and verify GREEN**

Run the same command from Step 2.

Expected: both tests pass.

- [ ] **Step 5: Commit the config seam**

```powershell
git add common/src/main/java/net/conczin/mca/Config.java common/src/test/java/net/conczin/mca/ConfigMourningTest.java
git commit -m "feat: add mourning enable config"
```

---

### Task 2: Centralize grave validity and assigned mourning state

**Files:**
- Create: `common/src/main/java/net/conczin/mca/entity/ai/Mourning.java`
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/MemoryModuleTypeMCA.java`
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/brain/VillagerTasksMCA.java`
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/EnterGraveyardTask.java`
- Create: `neoforge/src/main/java/net/conczin/mca/server/world/data/VillageMourningGameTests.java`

**Interfaces:**
- Produces: `MemoryModuleType<Long> LAST_AMBIENT_MOURNING`
- Produces: `MemoryModuleType<Long> MOURNING_RETRY_AT`
- Produces: `Mourning.start(VillagerEntityMCA, BlockPos)`
- Produces: `Mourning.clear(VillagerEntityMCA)`
- Produces: `Mourning.isMournableTombstone(Level, BlockPos)`
- Produces: `Mourning.getMournableGraves(Village, Level)`
- Produces: `Mourning.canMournAmbiently(VillagerEntityMCA)`
- `EnterGraveyardTask` consumes only an already assigned `MOURNING_SITE`; it no longer chooses graves itself.

- [ ] **Step 1: Register dedicated ambient-fairness and retry memories**

Add the declaration next to the existing mourning memories in `MemoryModuleTypeMCA`:

```java
MemoryModuleType<Long> LAST_GRIEVE = register("last_grieve", Optional.of(Codec.LONG));
MemoryModuleType<BlockPos> MOURNING_SITE = register("mourning_site", Optional.of(BlockPos.CODEC));
MemoryModuleType<GlobalPos> MOURNING_POSITION = register("mourning_position", Optional.of(GlobalPos.CODEC));
MemoryModuleType<Long> LAST_AMBIENT_MOURNING = register("last_ambient_mourning", Optional.of(Codec.LONG));
MemoryModuleType<Long> MOURNING_RETRY_AT = register("mourning_retry_at", Optional.of(Codec.LONG));
```

Add both `MemoryModuleTypeMCA.LAST_AMBIENT_MOURNING` and `MemoryModuleTypeMCA.MOURNING_RETRY_AT` to `VillagerTasksMCA.MEMORY_TYPES`. Keep `LAST_GRIEVE` registered and in the profile only for legacy decode compatibility.

- [ ] **Step 2: Add failing GameTests for the canonical tombstone predicate**

Create `VillageMourningGameTests` in package `net.conczin.mca.server.world.data` with these three tests:

```java
@GameTestHolder("minecraft")
public final class VillageMourningGameTests {
    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void emptyTombstoneIsNotMournable(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);

        helper.assertTrue(!Mourning.isMournableTombstone(helper.getLevel(), grave),
                "empty tombstone must not be mournable");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void occupiedTombstoneIsMournable(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(3, 1, 1), "Mourning Probe");
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow().setEntity(deceased);

        helper.assertTrue(Mourning.isMournableTombstone(helper.getLevel(), grave),
                "occupied tombstone must be mournable");
        helper.succeed();
    }

    @GameTest(templateNamespace = "minecraft", template = "bastion/blocks/air")
    public static void resurrectingTombstoneIsNotMournable(GameTestHelper helper) {
        BlockPos grave = helper.absolutePos(new BlockPos(1, 1, 1));
        VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(3, 1, 1), "Resurrection Probe");
        helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
        TombstoneBlock.Data data = TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow();
        data.setEntity(deceased);
        data.startResurrecting(false);

        helper.assertTrue(!Mourning.isMournableTombstone(helper.getLevel(), grave),
                "resurrecting tombstone must not be mournable");
        helper.succeed();
    }
}
```

Use one local `spawnVillager(...)` helper built on the repository's existing pattern:

```java
private static VillagerEntityMCA spawnVillager(GameTestHelper helper, BlockPos relativePos, String name) {
    BlockPos pos = helper.absolutePos(relativePos);
    helper.getLevel().getChunk(pos);
    VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
            .withAge(0)
            .withPosition(Vec3.atBottomCenterOf(pos))
            .withName(name)
            .spawn(MobSpawnType.STRUCTURE);
    villager.refreshBrain(helper.getLevel());
    return villager;
}
```

- [ ] **Step 3: Run GameTests and verify RED**

Run:

```powershell
./gradlew :neoforge:runGameTestServer
```

Expected: compilation fails because `Mourning` does not exist yet.

- [ ] **Step 4: Implement `Mourning` as the single domain helper**

Create `common/src/main/java/net/conczin/mca/entity/ai/Mourning.java` with this surface and behaviour:

```java
public final class Mourning {
    private Mourning() {
    }

    public static void start(VillagerEntityMCA villager, BlockPos grave) {
        villager.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_SITE, grave);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_POSITION);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_RETRY_AT);
        villager.getBrain().eraseMemory(MemoryModuleType.PATH);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(grave));
        villager.getBrain().setActiveActivityIfPossible(ActivitiesMCA.GRIEVE);
    }

    public static void clear(VillagerEntityMCA villager) {
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_SITE);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_POSITION);
        villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_RETRY_AT);
    }

    public static boolean isMournableTombstone(Level level, BlockPos position) {
        return level.getBlockState(position).is(TagsMCA.Blocks.TOMBSTONES)
                && TombstoneBlock.Data.of(level.getBlockEntity(position))
                .filter(TombstoneBlock.Data::hasEntity)
                .filter(data -> !data.isResurrecting())
                .isPresent();
    }

    public static List<BlockPos> getMournableGraves(Village village, Level level) {
        return village.getBuildingsOfType("graveyard")
                .filter(Building::isComplete)
                .flatMap(Building::getBlockPosStream)
                .distinct()
                .filter(position -> isMournableTombstone(level, position))
                .toList();
    }

    public static boolean canMournAmbiently(VillagerEntityMCA villager) {
        Brain<Villager> brain = villager.getBrain();
        boolean ambientActivity = brain.isActive(Activity.IDLE) || brain.isActive(Activity.MEET);
        return villager.isAlive()
                && ambientActivity
                && !villager.getBrain().isActive(ActivitiesMCA.GRIEVE)
                && villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isEmpty()
                && !VillagerTasksMCA.isInDanger(villager)
                && villager.getVillagerBrain().getCurrentJob() == Chore.NONE;
    }
}
```

This activity gate is part of ambient eligibility. It preserves normal daily scheduling: ambient remembrance may borrow IDLE/MEET time, while WORK/REST, emergency activities, and player-directed FOLLOW/STAY CORE behavior remain untouched. Personal tragedy does not use this predicate and may still start mourning immediately.

- [ ] **Step 5: Make `EnterGraveyardTask` assigned-site only**

Replace `findTarget(...)` with:

```java
private Optional<MourningTarget> findTarget(VillagerEntityMCA villager) {
    Level level = villager.level();
    return villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)
            .filter(grave -> Mourning.isMournableTombstone(level, grave))
            .flatMap(grave -> findStandingPosition(level, villager, grave)
                    .map(position -> new MourningTarget(grave, position)));
}
```

Then replace the remaining local grave-validation calls with `Mourning.isMournableTombstone(...)` and delete:

```java
hasPeriodicMourningCandidate(...)
getCompleteGraveyards(...)
isMournableTombstone(...)
```

Keep the standing-position generation, safety checks, distance ordering, and 256-block reservation scan unchanged.

- [ ] **Step 6: Run GameTests and common compilation**

Run:

```powershell
./gradlew :common:compileJava
./gradlew :neoforge:runGameTestServer
```

Expected: the three tombstone tests pass and `EnterGraveyardTask` contains no village-wide grave discovery path.

- [ ] **Step 7: Commit the mourning domain seam**

```powershell
git add common/src/main/java/net/conczin/mca/entity/ai/Mourning.java common/src/main/java/net/conczin/mca/entity/ai/MemoryModuleTypeMCA.java common/src/main/java/net/conczin/mca/entity/ai/brain/VillagerTasksMCA.java common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/EnterGraveyardTask.java neoforge/src/main/java/net/conczin/mca/server/world/data/VillageMourningGameTests.java
git commit -m "refactor: centralize mourning state"
```

---

### Task 3: Persist and execute village-owned mourning sessions and bursts

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/server/world/data/Village.java`
- Create: `common/src/test/java/net/conczin/mca/server/world/data/VillageMourningScheduleTest.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/server/world/data/VillageMourningGameTests.java`

**Interfaces:**
- Produces: `private long nextMourningTime`
- Produces: `private int mourningRemaining`
- Produces: `private long nextMourningBurstTime`
- Produces: transient `List<BlockPos> mourningGraveCache`
- Produces: transient `boolean mourningGraveCacheInitialized`
- Produces: package-private getters for all three persisted scheduling fields
- Produces: package-private static `long calculateNextMourningTime(long now, RandomSource random)`
- Produces: package-private static `long calculateNextMourningBurstTime(long now, RandomSource random)`
- Produces: package-private static `int calculateMourningSessionSize(int residentCount)`
- Produces: package-private static `int calculateMourningBurstSize(int remaining, RandomSource random)`
- Produces: private `tickMourning(ServerLevel world, long time)`
- Produces: private `refreshMourningGraveCache(ServerLevel world)` and `endMourningSession()`
- Uses: `Mourning.getMournableGraves(...)`, `Mourning.canMournAmbiently(...)`, `Mourning.start(...)`
- Uses: `MemoryModuleTypeMCA.LAST_AMBIENT_MOURNING` only for burst-selection fairness

- [ ] **Step 1: Write the failing common scheduling tests**

Create `VillageMourningScheduleTest`:

```java
package net.conczin.mca.server.world.data;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VillageMourningScheduleTest {
    @Test
    void legacyVillageLoadsWithoutScheduledMourning() {
        CompoundTag tag = new Village(1, null).save();
        tag.remove("nextMourningTime");
        tag.remove("mourningRemaining");
        tag.remove("nextMourningBurstTime");

        Village loaded = new Village(tag, null);

        assertEquals(0L, loaded.getNextMourningTime());
        assertEquals(0, loaded.getMourningRemaining());
        assertEquals(0L, loaded.getNextMourningBurstTime());
    }

    @Test
    void nextMourningFallsBetweenOneAndTwoMinecraftDays() {
        long now = 200_000L;
        long next = Village.calculateNextMourningTime(now, RandomSource.create(1234L));

        assertTrue(next >= now + 24_000L);
        assertTrue(next <= now + 48_000L);
    }

    @Test
    void nextBurstFallsBetweenTwoAndFourMinecraftHours() {
        long now = 200_000L;
        long next = Village.calculateNextMourningBurstTime(now, RandomSource.create(1234L));

        assertTrue(next >= now + 2_000L);
        assertTrue(next <= now + 4_000L);
    }

    @Test
    void sessionSizeScalesAndCaps() {
        assertEquals(3, Village.calculateMourningSessionSize(20));
        assertEquals(5, Village.calculateMourningSessionSize(50));
        assertEquals(7, Village.calculateMourningSessionSize(100));
        assertEquals(9, Village.calculateMourningSessionSize(150));
        assertEquals(12, Village.calculateMourningSessionSize(200));
        assertEquals(12, Village.calculateMourningSessionSize(1_000));
    }

    @Test
    void mourningSessionStateRoundTripsThroughVillageNbt() {
        CompoundTag tag = new Village(1, null).save();
        tag.putLong("nextMourningTime", 345_678L);
        tag.putInt("mourningRemaining", 8);
        tag.putLong("nextMourningBurstTime", 346_789L);

        Village loaded = new Village(tag, null);
        Village reloaded = new Village(loaded.save(), null);

        assertEquals(345_678L, reloaded.getNextMourningTime());
        assertEquals(8, reloaded.getMourningRemaining());
        assertEquals(346_789L, reloaded.getNextMourningBurstTime());
        assertTrue(!reloaded.save().contains("mourningGraveCache"));
        assertTrue(!reloaded.save().contains("mourningGraveCacheInitialized"));
    }
}
```

- [ ] **Step 2: Run the common scheduling test and verify RED**

Run:

```powershell
./gradlew :common:test --tests 'net.conczin.mca.server.world.data.VillageMourningScheduleTest'
```

Expected: compilation fails because the mourning timestamp API does not exist.

- [ ] **Step 3: Add persisted schedule state to `Village`**

Add:

```java
private static final long MIN_MOURNING_INTERVAL = 24_000L;
private static final long MAX_MOURNING_INTERVAL = 48_000L;
private static final long MIN_MOURNING_BURST_DELAY = 2_000L;
private static final long MAX_MOURNING_BURST_DELAY = 4_000L;
private static final int MIN_MOURNING_BURST_SIZE = 2;
private static final int MAX_MOURNING_BURST_SIZE = 4;
private static final int MIN_MOURNING_SESSION_SIZE = 3;
private static final int MAX_MOURNING_SESSION_SIZE = 12;
private static final int MOURNING_SESSION_SCALE_DIVISOR = 22;

private long nextMourningTime;
private int mourningRemaining;
private long nextMourningBurstTime;
private final List<BlockPos> mourningGraveCache = new ArrayList<>();
private boolean mourningGraveCacheInitialized;
```

The cache and initialized flag are transient runtime state. Do not read or write either one in NBT. The boolean distinguishes “restored active session has not rebuilt its cache yet” from “this session already scanned and no valid graves remain.”

Load and save three top-level NBT keys:

```java
nextMourningTime = tag.getLong("nextMourningTime");
mourningRemaining = tag.getInt("mourningRemaining");
nextMourningBurstTime = tag.getLong("nextMourningBurstTime");
```

```java
tag.putLong("nextMourningTime", nextMourningTime);
tag.putInt("mourningRemaining", mourningRemaining);
tag.putLong("nextMourningBurstTime", nextMourningBurstTime);
```

Add:

```java
long getNextMourningTime() {
    return nextMourningTime;
}

int getMourningRemaining() {
    return mourningRemaining;
}

long getNextMourningBurstTime() {
    return nextMourningBurstTime;
}

static long calculateNextMourningTime(long now, RandomSource random) {
    int range = (int) (MAX_MOURNING_INTERVAL - MIN_MOURNING_INTERVAL + 1L);
    return now + MIN_MOURNING_INTERVAL + random.nextInt(range);
}

static long calculateNextMourningBurstTime(long now, RandomSource random) {
    int range = (int) (MAX_MOURNING_BURST_DELAY - MIN_MOURNING_BURST_DELAY + 1L);
    return now + MIN_MOURNING_BURST_DELAY + random.nextInt(range);
}

static int calculateMourningSessionSize(int residentCount) {
    int scaled = MIN_MOURNING_SESSION_SIZE + residentCount / MOURNING_SESSION_SCALE_DIVISOR;
    return Math.max(MIN_MOURNING_SESSION_SIZE, Math.min(MAX_MOURNING_SESSION_SIZE, scaled));
}

static int calculateMourningBurstSize(int remaining, RandomSource random) {
    int requested = MIN_MOURNING_BURST_SIZE
            + random.nextInt(MAX_MOURNING_BURST_SIZE - MIN_MOURNING_BURST_SIZE + 1);
    return Math.min(remaining, requested);
}
```

- [ ] **Step 4: Call mourning before the village-id time offset**

Change the start of `Village.tick(...)` to:

```java
public void tick(ServerLevel world, long time) {
    tickMourning(world, time);
    time += getId();
    boolean taxSeason = time % Config.getInstance().taxSeason == 0;
    // existing tick body continues unchanged
}
```

- [ ] **Step 5: Implement session start plus staggered burst release in `Village`**

Add a private Fisher-Yates helper that uses Minecraft's `RandomSource`:

```java
private static <T> void shuffle(List<T> values, RandomSource random) {
    for (int index = values.size() - 1; index > 0; index--) {
        Collections.swap(values, index, random.nextInt(index + 1));
    }
}
```

Implement:

```java
private void tickMourning(ServerLevel world, long time) {
    if (!Config.getInstance().enableMourning) {
        return;
    }

    if (mourningRemaining > 0) {
        if (nextMourningBurstTime == 0L || time >= nextMourningBurstTime) {
            if (!mourningGraveCacheInitialized) {
                refreshMourningGraveCache(world);
            }
            releaseMourningBurst(world, time);
        }
        return;
    }

    if (nextMourningTime == 0L) {
        nextMourningTime = calculateNextMourningTime(time, world.random);
        markDirty();
        return;
    }

    if (time < nextMourningTime) {
        return;
    }

    nextMourningTime = calculateNextMourningTime(time, world.random);
    refreshMourningGraveCache(world);
    if (mourningGraveCache.isEmpty()) {
        endMourningSession();
        markDirty();
        return;
    }

    mourningRemaining = calculateMourningSessionSize(getResidents(world).size());
    nextMourningBurstTime = time;
    markDirty();

    releaseMourningBurst(world, time);
}

private void refreshMourningGraveCache(ServerLevel world) {
    mourningGraveCache.clear();
    mourningGraveCache.addAll(Mourning.getMournableGraves(this, world));
    mourningGraveCacheInitialized = true;
}

private void endMourningSession() {
    mourningRemaining = 0;
    nextMourningBurstTime = 0L;
    mourningGraveCache.clear();
    mourningGraveCacheInitialized = false;
}

private void releaseMourningBurst(ServerLevel world, long time) {
    mourningGraveCache.removeIf(grave -> !Mourning.isMournableTombstone(world, grave));
    if (mourningGraveCache.isEmpty()) {
        endMourningSession();
        markDirty();
        return;
    }

    int burstBudget = calculateMourningBurstSize(mourningRemaining, world.random);
    mourningRemaining -= burstBudget;

    List<VillagerEntityMCA> candidates = getResidents(world).stream()
            .filter(Mourning::canMournAmbiently)
            .collect(Collectors.toCollection(ArrayList::new));

    shuffle(mourningGraveCache, world.random);
    shuffle(candidates, world.random);
    candidates.sort(Comparator.comparingLong(villager -> villager.getBrain()
            .getMemoryInternal(MemoryModuleTypeMCA.LAST_AMBIENT_MOURNING)
            .orElse(Long.MIN_VALUE)));

    int count = Math.min(burstBudget, candidates.size());
    for (int index = 0; index < count; index++) {
        VillagerEntityMCA villager = candidates.get(index);
        villager.getBrain().setMemory(MemoryModuleTypeMCA.LAST_AMBIENT_MOURNING, time);
        Mourning.start(villager, mourningGraveCache.get(index % mourningGraveCache.size()));
    }

    if (mourningRemaining > 0) {
        nextMourningBurstTime = calculateNextMourningBurstTime(time, world.random);
    } else {
        endMourningSession();
    }
    markDirty();
}
```

Important semantics:

- a new session schedules the following session before grave discovery, then performs one complete graveyard scan to initialize the transient cache
- an active session always takes priority over starting another session
- candidates are rebuilt for every burst; do not persist or preselect a queue of villagers
- ambient candidates must currently be in `Activity.IDLE` or `Activity.MEET`; WORK/REST and emergency activities are not interrupted
- shuffling before the stable `LAST_AMBIENT_MOURNING` sort makes equal/missing timestamps random while still preferring oldest timestamps
- `LAST_AMBIENT_MOURNING` is written only when a villager is newly selected for an ambient burst; `LAST_GRIEVE` remains legacy-only
- later bursts remove invalid positions from `mourningGraveCache` and never traverse complete graveyard buildings again
- a restored active session starts with `mourningGraveCacheInitialized == false`; its first due burst rebuilds the cache exactly once, then uses the normal validation-only path
- subtract the full burst budget even if fewer candidates are available, so temporary lack of eligible villagers cannot stall the session forever
- if no cached valid graves remain, end only the current session; keep the already-scheduled `nextMourningTime`

- [ ] **Step 6: Add GameTests for session start, staggered bursts, fairness, and activation**

Add this package-local fixture to `VillageMourningGameTests`:

```java
private static Village villageWithGraveyard(GameTestHelper helper, BlockPos grave) {
    Village village = new Village(1, helper.getLevel());
    FloorGeometry geometry = new FloorGeometry(
            List.of(new FloorGeometry.Cell(grave, grave.getY() + 2)), Map.of());
    StructureFloor floor = new StructureFloor(0, 0, geometry);
    Structure structure = new Structure(10, grave, List.of(floor));
    Building room = new Building(grave);
    room.setId(100);
    room.setStructureId(10);
    room.setFloorId(0);
    room.setGeometry(grave, grave, List.of(grave));
    room.setType("graveyard");
    room.setTypeForced(true);
    room.addBlock(BlocksMCA.CROSS_HEADSTONE, grave);
    village.registerStructure(structure, room);
    return village;
}

private static Village withMourningState(
        Village village,
        long nextSession,
        int remaining,
        long nextBurst,
        ServerLevel level) {
    CompoundTag tag = village.save();
    tag.putLong("nextMourningTime", nextSession);
    tag.putInt("mourningRemaining", remaining);
    tag.putLong("nextMourningBurstTime", nextBurst);
    return new Village(tag, level);
}

private static void occupyGrave(GameTestHelper helper, BlockPos grave) {
    VillagerEntityMCA deceased = spawnVillager(helper, new BlockPos(7, 1, 1), "Ambient Grave Probe");
    helper.getLevel().setBlock(grave, BlocksMCA.CROSS_HEADSTONE.defaultBlockState(), 3);
    TombstoneBlock.Data.of(helper.getLevel().getBlockEntity(grave)).orElseThrow().setEntity(deceased);
}

private static List<BlockPos> mourningSites(List<VillagerEntityMCA> residents) {
    return residents.stream()
            .flatMap(villager -> villager.getBrain()
                    .getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).stream())
            .toList();
}
```

Add these assertions as separate GameTests:

```java
long now = helper.getLevel().getGameTime();
village.tick(helper.getLevel(), now);
helper.assertTrue(village.getNextMourningTime() > now, "first tick should only schedule");
helper.assertTrue(mourningSites(residents).isEmpty(), "first tick must not assign mourners");
```

```java
Village due = withMourningState(new Village(1, helper.getLevel()), now, 0, 0L, helper.getLevel());
due.tick(helper.getLevel(), now);
helper.assertTrue(due.getNextMourningTime() > now, "empty session must still reschedule");
helper.assertTrue(due.getMourningRemaining() == 0, "empty graveyard must end the active session");
helper.assertTrue(due.getNextMourningBurstTime() == 0L, "empty graveyard must clear the burst time");
helper.assertTrue(mourningSites(residents).isEmpty(), "no grave means no mourners");
```

For burst behaviour, create an already-active session directly so the test does not need to spawn 50+ villagers merely to obtain a budget larger than one burst:

```java
occupyGrave(helper, grave);
Village due = withMourningState(
        villageWithGraveyard(helper, grave),
        now + 24_000L,
        8,
        now,
        helper.getLevel());
for (VillagerEntityMCA resident : residents) {
    due.updateResident(resident);
}
due.tick(helper.getLevel(), now);
List<VillagerEntityMCA> mourners = residents.stream()
        .filter(v -> v.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isPresent())
        .toList();
helper.assertTrue(mourners.size() >= 2 && mourners.size() <= 4,
        "one due burst must start only two to four mourners");
helper.assertTrue(due.getMourningRemaining() >= 4 && due.getMourningRemaining() <= 6,
        "first burst must consume only its two-to-four-person budget");
helper.assertTrue(due.getNextMourningBurstTime() > now,
        "remaining session budget must schedule a later burst");
helper.assertTrue(mourners.stream().allMatch(v ->
                v.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).filter(grave::equals).isPresent()),
        "selected residents must receive the occupied grave");
helper.assertTrue(mourners.stream().allMatch(v -> v.getBrain().isActive(ActivitiesMCA.GRIEVE)),
        "selected residents must enter GRIEVE");
helper.assertTrue(mourners.stream().allMatch(v ->
                v.getBrain().getMemoryInternal(MemoryModuleTypeMCA.LAST_AMBIENT_MOURNING).filter(last -> last == now).isPresent()),
        "new ambient mourners must record LAST_AMBIENT_MOURNING for fairness");

int beforeEarlyTick = mourningSites(residents).size();
long nextBurst = due.getNextMourningBurstTime();
due.tick(helper.getLevel(), nextBurst - 1L);
helper.assertTrue(mourningSites(residents).size() == beforeEarlyTick,
        "session must not release another burst early");

due.tick(helper.getLevel(), nextBurst);
int afterSecondBurst = mourningSites(residents).size();
helper.assertTrue(afterSecondBurst > beforeEarlyTick && afterSecondBurst <= beforeEarlyTick + 4,
        "due second burst must re-evaluate residents and release at most four more");
```

Spawn at least ten eligible residents for the active-session case so the second burst has fresh candidates after the first group enters `GRIEVE`.

Add a deterministic fairness case by giving four residents `LAST_AMBIENT_MOURNING = now`, leaving at least four others without the memory, and forcing an active session with exactly `mourningRemaining = 2`. Because the burst budget is then exactly two, both selected residents must come from the never/older-mourned group:

```java
for (VillagerEntityMCA recent : residents.subList(0, 4)) {
    recent.getBrain().setMemory(MemoryModuleTypeMCA.LAST_AMBIENT_MOURNING, now);
}

Village fair = withMourningState(
        villageWithGraveyard(helper, grave),
        now + 24_000L,
        2,
        now,
        helper.getLevel());
for (VillagerEntityMCA resident : residents) {
    fair.updateResident(resident);
}
fair.tick(helper.getLevel(), now);

List<VillagerEntityMCA> selected = residents.stream()
        .filter(v -> v.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isPresent())
        .toList();
helper.assertTrue(selected.size() == 2, "two-person remaining budget must release exactly two when eligible");
helper.assertTrue(selected.stream().noneMatch(residents.subList(0, 4)::contains),
        "LAST_AMBIENT_MOURNING fairness must prefer older/never-selected villagers when enough exist");
```

Use fresh villagers for this fairness test so `MOURNING_SITE` from the burst-sequencing test cannot leak into it. Also assert `Village.calculateMourningSessionSize(200) == 12` and `Village.calculateMourningSessionSize(10_000) == 12` in either the common test or GameTest so the hard cap is explicit.

Add an activity-gating GameTest using fresh residents. Leave the IDLE resident as initialized. For the MEET and WORK residents, satisfy the activity requirement before switching:

```java
BlockPos poi = helper.absolutePos(new BlockPos(2, 1, 2));
GlobalPos globalPoi = GlobalPos.of(helper.getLevel().dimension(), poi);

meet.getBrain().setMemory(MemoryModuleType.MEETING_POINT, globalPoi);
meet.getBrain().setActiveActivityIfPossible(Activity.MEET);

work.getBrain().setMemory(MemoryModuleType.JOB_SITE, globalPoi);
work.getBrain().setActiveActivityIfPossible(Activity.WORK);

rest.getBrain().setActiveActivityIfPossible(Activity.REST);
```

Assert `Mourning.canMournAmbiently(...)` is true for IDLE/MEET and false for WORK/REST, then run a due burst and assert the WORK/REST residents never receive `MOURNING_SITE`.

Add a cache-reuse GameTest with a fixture that retains the registered `Building` reference. Start a session with a valid occupied grave so the first burst initializes `mourningGraveCache`; after that burst, call `graveyard.setType("house")` and keep it forced while leaving the cached tombstone occupied and valid. When the second burst becomes due, it must still select fresh mourners from the cached grave. A second full `getMournableGraves(...)` traversal would see no graveyard and fail this assertion.

Add a restored-session GameTest by saving/reloading a village with `mourningRemaining > 0`, a due `nextMourningBurstTime`, and a valid persisted graveyard. Because the transient cache is empty after construction, the due burst must rebuild it once and successfully select mourners. After that burst, use the same building-type mutation as above to prove later bursts use the rebuilt cache rather than scanning again.

- [ ] **Step 7: Run focused common tests and GameTests**

Run:

```powershell
./gradlew :common:test --tests 'net.conczin.mca.server.world.data.VillageMourningScheduleTest'
./gradlew :neoforge:runGameTestServer
```

Expected: scheduling tests pass; the first tick only schedules; empty graveyards end the current session without spinning; an active session releases only 2-4 residents per due burst; no second burst starts early; later bursts use fresh eligibility; IDLE/MEET are eligible while WORK/REST are preserved; `LAST_AMBIENT_MOURNING` prefers older/never-selected villagers; complete grave discovery occurs once per uninterrupted session and once after restoring an active session; session size caps at 12.

- [ ] **Step 8: Commit village-owned scheduling**

```powershell
git add common/src/main/java/net/conczin/mca/server/world/data/Village.java common/src/test/java/net/conczin/mca/server/world/data/VillageMourningScheduleTest.java neoforge/src/main/java/net/conczin/mca/server/world/data/VillageMourningGameTests.java
git commit -m "feat: stagger village mourning sessions"
```

---

### Task 4: Route death-triggered personal mourning through the shared helper

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/Relationship.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/server/world/data/VillageMourningGameTests.java`

**Interfaces:**
- Consumes: `Config.enableMourning`, `Mourning.start(...)`
- Preserves: existing `EntityRelationship.super.onTragedy(...)` propagation and all mood/heart effects

- [ ] **Step 1: Add failing tragedy GameTests**

Use fresh villagers per case and exercise the existing local relationship branches directly:

```java
relative.getRelationships().onTragedy(
        helper.getLevel().damageSources().generic(), grave, RelationshipType.SPOUSE, deceased);
helper.assertTrue(
        relative.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).filter(grave::equals).isPresent(),
        "spouse tragedy should target the exact burial site");
```

```java
stranger.getRelationships().onTragedy(
        helper.getLevel().damageSources().generic(), grave, RelationshipType.STRANGER, deceased);
helper.assertTrue(
        stranger.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isEmpty(),
        "stranger tragedy must not start grave mourning");
```

For disabled mourning, preserve and restore the singleton config in `finally`:

```java
int moodBefore = relative.getVillagerBrain().getMoodValue();
boolean previous = Config.getInstance().enableMourning;
try {
    Config.getInstance().enableMourning = false;
    relative.getRelationships().onTragedy(
            helper.getLevel().damageSources().generic(), grave, RelationshipType.SPOUSE, deceased);

    helper.assertTrue(
            relative.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).isEmpty(),
            "disabled mourning must not assign a grave");
    helper.assertTrue(relative.getVillagerBrain().getMoodValue() < moodBefore,
            "tragedy mood penalty must remain when mourning is disabled");
} finally {
    Config.getInstance().enableMourning = previous;
}
```

- [ ] **Step 2: Run GameTests and verify the disabled case is RED**

Run:

```powershell
./gradlew :neoforge:runGameTestServer
```

Expected before production edit: the disabled spouse case still assigns `MOURNING_SITE`.

- [ ] **Step 3: Replace only the grave-mourning branch in `Relationship.onTragedy(...)`**

Replace the direct memory/activity setup with:

```java
if (Config.getInstance().enableMourning && burialSite != null && type != RelationshipType.STRANGER) {
    Mourning.start(entity, burialSite);
}
```

Leave the preceding mood/murder-heart code and the trailing `EntityRelationship.super.onTragedy(...)` call in their current order. Remove imports that become unused after the direct Brain setup disappears.

- [ ] **Step 4: Run GameTests and verify GREEN**

Run the same command from Step 2.

Expected: spouse exact-site, stranger exclusion, and disabled-mourning mood preservation all pass.

- [ ] **Step 5: Commit personal mourning migration**

```powershell
git add common/src/main/java/net/conczin/mca/entity/ai/Relationship.java neoforge/src/main/java/net/conczin/mca/server/world/data/VillageMourningGameTests.java
git commit -m "refactor: route personal mourning through helper"
```

---

### Task 5: Replace the old seven-day villager timer with assigned-site retry state

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/GrieveTask.java`
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/brain/VillagerTasksMCA.java`
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/brain/VillagerBrain.java`
- Modify: `common/src/main/java/net/conczin/mca/block/TombstoneBlock.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/server/world/data/VillageMourningGameTests.java`

**Interfaces:**
- `GrieveTask` consumes only `MOURNING_SITE` plus `MOURNING_RETRY_AT`.
- Failed attempts produce `MOURNING_RETRY_AT = gameTime + 1200L` while preserving the same site.
- Successful/invalid attempts call `Mourning.clear(...)`.
- `LAST_GRIEVE` remains registered/profiled only for legacy decode compatibility; this task and `Village` do not read or write it.
- `LAST_AMBIENT_MOURNING` remains separate from retry state and is used only by `Village` ambient selection.

- [ ] **Step 1: Add a failing natural retry GameTest**

Create a villager with an assigned occupied grave and a future retry timestamp, force the Brain back to idle, and let normal AI ticks evaluate the existing idle package:

```java
long retryAt = helper.getLevel().getGameTime() + 20L;
villager.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_SITE, grave);
villager.getBrain().setMemory(MemoryModuleTypeMCA.MOURNING_RETRY_AT, retryAt);
villager.getBrain().setActiveActivityIfPossible(Activity.IDLE);

helper.onEachTick(() -> {
    long now = helper.getLevel().getGameTime();
    if (now < retryAt) {
        helper.assertTrue(!villager.getBrain().isActive(ActivitiesMCA.GRIEVE),
                "retry must not start before MOURNING_RETRY_AT");
        helper.assertTrue(
                villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).filter(grave::equals).isPresent(),
                "waiting retry must preserve the assigned grave");
        return;
    }

    if (villager.getBrain().isActive(ActivitiesMCA.GRIEVE)) {
        helper.assertTrue(
                villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE).filter(grave::equals).isPresent(),
                "retry must restart the same assigned grave");
        helper.succeed();
    } else if (now > retryAt + 40L) {
        helper.fail("mourning retry did not restart after its deadline");
    }
});
```

Give this GameTest a timeout of at least 100 ticks.

- [ ] **Step 2: Run GameTests and verify RED**

Run:

```powershell
./gradlew :neoforge:runGameTestServer
```

Expected: current `GrieveTask` ignores `MOURNING_RETRY_AT` and still depends on `LAST_GRIEVE` as a per-villager timer plus periodic grave discovery.

- [ ] **Step 3: Rewrite `GrieveTask` as retry-only**

Replace its start condition with:

```java
protected boolean checkExtraStartConditions(ServerLevel world, VillagerEntityMCA entity) {
    if (!Config.getInstance().enableMourning) {
        return false;
    }

    Optional<BlockPos> site = entity.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE);
    if (site.isEmpty()) {
        return false;
    }

    if (!Mourning.isMournableTombstone(world, site.get())) {
        Mourning.clear(entity);
        return false;
    }

    return entity.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_RETRY_AT)
            .filter(retryAt -> world.getGameTime() >= retryAt)
            .isPresent();
}
```

Replace `start(...)` with:

```java
protected void start(ServerLevel world, VillagerEntityMCA villager, long time) {
    villager.getBrain().getMemoryInternal(MemoryModuleTypeMCA.MOURNING_SITE)
            .ifPresent(grave -> Mourning.start(villager, grave));
}
```

Keep `Pair.of(3, new GrieveTask())` in the idle package. The task must contain no graveyard traversal and no `LAST_GRIEVE` access.

- [ ] **Step 4: Replace grieving completion with clear-or-retry state**

Add in `VillagerTasksMCA`:

```java
private static final long GRIEVING_RETRY_DELAY = 1200L;
```

Replace the final grieving `LambdaTask` body with:

```java
new LambdaTask<>(v -> {
    boolean completed = mournAtGrave.hasCompleted();
    boolean targetStillMournable = EnterGraveyardTask.hasMournableSite(v);

    if (completed || !targetStillMournable) {
        Mourning.clear(v);
    } else {
        v.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_POSITION);
        v.getBrain().eraseMemory(MemoryModuleType.PATH);
        v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        v.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        v.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        v.getBrain().setMemory(
                MemoryModuleTypeMCA.MOURNING_RETRY_AT,
                v.level().getGameTime() + GRIEVING_RETRY_DELAY);
    }

    v.getBrain().updateActivityFromSchedule(v.level().getDayTime(), v.level().getGameTime());
})
```

This preserves `MOURNING_SITE` only for a real retry and performs no candidate/graveyard scan during cleanup.

- [ ] **Step 5: Remove the obsolete runtime cooldown APIs from `VillagerBrain`**

Delete:

```java
GRIEVE_COOLDOWN
GRIEVE_RETRY_DELAY
setGrieving()
retryGrievingLater()
justGrieved()
shouldGrieve()
```

Remove the now-unused static import of `LAST_GRIEVE` from `VillagerBrain`. Keep `MemoryModuleTypeMCA.LAST_GRIEVE` registered and keep it in `VillagerTasksMCA.MEMORY_TYPES` only for legacy decode compatibility. `Village` uses `LAST_AMBIENT_MOURNING` for recency instead.

- [ ] **Step 6: Extend tombstone resurrection cleanup**

In the existing resurrected-`VillagerEntityMCA` cleanup in `TombstoneBlock.Data.tick()`, add:

```java
villager.getBrain().eraseMemory(MemoryModuleTypeMCA.LAST_AMBIENT_MOURNING);
villager.getBrain().eraseMemory(MemoryModuleTypeMCA.MOURNING_RETRY_AT);
```

Keep the existing `LAST_GRIEVE`, site, position, path, walk, look, and can't-reach cleanup. Clearing `LAST_AMBIENT_MOURNING` means a recreated villager starts with fresh ambient fairness state rather than inheriting selection recency from the dead entity.

Extend `VillageMourningGameTests` with a resurrection case. Before `setEntity(...)`, give the stored villager both `LAST_AMBIENT_MOURNING` and `MOURNING_RETRY_AT`, place it into the tombstone, call `startResurrecting(false)`, and allow the block entity to tick through resurrection with a GameTest timeout above 500 ticks. Locate the recreated `VillagerEntityMCA` near the grave and assert both memories are absent. This exercises the real `TombstoneBlock.Data.tick()` cleanup rather than only checking source text.

- [ ] **Step 7: Run retry GameTests and compilation**

Run:

```powershell
./gradlew :common:compileJava
./gradlew :neoforge:runGameTestServer
```

Expected: retry waits until its timestamp, preserves the same site while waiting, restarts the assigned site once due, and compiles with no runtime caller of the old cooldown methods.

- [ ] **Step 8: Commit retry migration**

```powershell
git add common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/GrieveTask.java common/src/main/java/net/conczin/mca/entity/ai/brain/VillagerTasksMCA.java common/src/main/java/net/conczin/mca/entity/ai/brain/VillagerBrain.java common/src/main/java/net/conczin/mca/block/TombstoneBlock.java neoforge/src/main/java/net/conczin/mca/server/world/data/VillageMourningGameTests.java
git commit -m "refactor: make mourning retry assigned-site only"
```

---

### Task 6: Verify disabled ambient mourning, performance boundaries, and compatibility

**Files:**
- Modify tests only:
  - `neoforge/src/main/java/net/conczin/mca/server/world/data/VillageMourningGameTests.java`
  - `common/src/test/java/net/conczin/mca/ConfigMourningTest.java`
  - `common/src/test/java/net/conczin/mca/server/world/data/VillageMourningScheduleTest.java`

**Interfaces:**
- Validates the completed design; introduces no new production API.

- [ ] **Step 1: Add ambient config-off coverage**

Use a due village with an occupied grave and eligible residents, then temporarily disable mourning:

```java
boolean previous = Config.getInstance().enableMourning;
try {
    Config.getInstance().enableMourning = false;
    long beforeSession = village.getNextMourningTime();
    int beforeRemaining = village.getMourningRemaining();
    long beforeBurst = village.getNextMourningBurstTime();
    village.tick(helper.getLevel(), now);

    helper.assertTrue(mourningSites(residents).isEmpty(),
            "disabled mourning must not assign ambient mourners");
    helper.assertTrue(village.getNextMourningTime() == beforeSession,
            "disabled mourning must not reschedule the next session");
    helper.assertTrue(village.getMourningRemaining() == beforeRemaining,
            "disabled mourning must freeze the active session budget");
    helper.assertTrue(village.getNextMourningBurstTime() == beforeBurst,
            "disabled mourning must freeze the next burst timestamp");
} finally {
    Config.getInstance().enableMourning = previous;
}
```

- [ ] **Step 2: Run all focused mourning tests**

Run:

```powershell
./gradlew :common:test --tests 'net.conczin.mca.ConfigMourningTest' --tests 'net.conczin.mca.server.world.data.VillageMourningScheduleTest'
./gradlew :neoforge:runGameTestServer
```

Expected: all mourning tests pass.

- [ ] **Step 3: Run complete common verification**

Run:

```powershell
./gradlew :common:test
./gradlew :common:compileJava
```

Expected: PASS.

- [ ] **Step 4: Prove the old population-scaling path is gone**

Run:

```powershell
rg -n "hasPeriodicMourningCandidate|GRIEVE_COOLDOWN|shouldGrieve\(|setGrieving\(|justGrieved\(|retryGrievingLater\(" common/src/main/java
rg -n "LAST_GRIEVE" common/src/main/java
rg -n "LAST_AMBIENT_MOURNING" common/src/main/java
rg -n "MOURNING_RETRY_AT" common/src/main/java
```

Expected:

- the first search has no matches
- `LAST_GRIEVE` appears only in compatibility registration/profile and existing legacy resurrection cleanup; it must not appear in `Village`, `GrieveTask`, or `VillagerBrain` cooldown APIs
- `LAST_AMBIENT_MOURNING` appears in registration/profile, `Village` fairness ordering/writes, and resurrection cleanup
- `MOURNING_RETRY_AT` appears in registration/profile, shared start/clear, retry producer/consumer, and resurrection cleanup

- [ ] **Step 5: Verify village ownership and absolute-time ordering**

Run:

```powershell
rg -n -C 8 "tickMourning|releaseMourningBurst|time \+= getId\(\)" common/src/main/java/net/conczin/mca/server/world/data/Village.java
rg -n "Mourning|nextMourningTime|mourningRemaining|nextMourningBurstTime" common/src/main/java/net/conczin/mca/server/world/data/VillageManager.java
```

Expected:

- `tickMourning(world, time)` appears before `time += getId()`
- `VillageManager.java` has no mourning scheduler or mourning timestamp state

- [ ] **Step 6: Review scope and whitespace**

Run:

```powershell
git diff --check
git diff -- common/src/main/java/net/conczin/mca/Config.java common/src/main/java/net/conczin/mca/block/TombstoneBlock.java common/src/main/java/net/conczin/mca/entity/ai common/src/main/java/net/conczin/mca/server/world/data/Village.java common/src/test/java/net/conczin/mca/ConfigMourningTest.java common/src/test/java/net/conczin/mca/server/world/data/VillageMourningScheduleTest.java neoforge/src/main/java/net/conczin/mca/server/world/data/VillageMourningGameTests.java docs/superpowers/specs/2026-09-12-village-scoped-mourning.md docs/superpowers/plans/2026-09-12-village-scoped-mourning.md
```

Expected: no whitespace errors and no unrelated dirty files in the feature diff.

- [ ] **Step 7: Commit final regression coverage and planning docs**

```powershell
git add common/src/test/java/net/conczin/mca/ConfigMourningTest.java common/src/test/java/net/conczin/mca/server/world/data/VillageMourningScheduleTest.java neoforge/src/main/java/net/conczin/mca/server/world/data/VillageMourningGameTests.java docs/superpowers/specs/2026-09-12-village-scoped-mourning.md docs/superpowers/plans/2026-09-12-village-scoped-mourning.md
git commit -m "test: cover village-scoped mourning"
```

---

## Self-Review

### Spec coverage

- Master config default and disabled behaviour: Tasks 1, 4, 6.
- Village-owned randomized 1-2 day session cadence: Task 3.
- Population-scaled 3-12 session budget with a hard cap of 12: Task 3.
- Staggered 2-4-villager bursts every 2-4 Minecraft hours: Task 3.
- Complete grave discovery once per session, with one rebuild after restoring an active session and validation-only later bursts: Tasks 2 and 3.
- `LAST_AMBIENT_MOURNING` soft fairness without restoring per-villager scheduling: Tasks 2, 3, and 5.
- Ambient daily-activity gating accepts IDLE/MEET and preserves WORK/REST: Tasks 2 and 3.
- Personal parents/siblings/spouse propagation unchanged: Task 4 changes only the local grave-start branch.
- Stranger tragedy remains mood-only: Task 4.
- Exact-grave/pathfinding/reservation/dialogue mechanics preserved: Task 2 leaves standing/path execution in `EnterGraveyardTask`/`MournAtGraveTask`.
- Retry without village-wide scans: Task 5.
- `LAST_GRIEVE` decode compatibility only: Tasks 2 and 5 retain registration/profile while removing all runtime mourning use.
- Resurrection retry and ambient-recency cleanup: Task 5.
- Absolute game-time timestamp before village-id offset: Tasks 3 and 6.
- No `VillageManager` scheduler: File Map, Task 3, Task 6.
- Required JUnit and GameTest coverage: Tasks 1 through 6.

### Placeholder scan

The plan contains no `TBD`, `TODO`, “implement later”, conditional file selection, unnamed test classes, or “similar to Task N” shortcuts. Every production interface introduced by one task is named before a later task consumes it.

### Type consistency

- `nextMourningTime` is `long` everywhere.
- `mourningRemaining` is `int` everywhere and `nextMourningBurstTime` is `long` everywhere.
- Session interval bounds are `24_000L` and `48_000L` everywhere.
- Burst delay bounds are `2_000L` and `4_000L` everywhere.
- Burst size is `2-4`; session size is `3-12` using divisor `22` and hard cap `12`.
- `LAST_AMBIENT_MOURNING` is `MemoryModuleType<Long>` everywhere and is used only for ambient fairness.
- `MOURNING_RETRY_AT` is `MemoryModuleType<Long>` everywhere.
- `mourningGraveCache` and `mourningGraveCacheInitialized` are transient only and are absent from village NBT.
- Shared grave APIs consistently use `BlockPos`.
- `Mourning.start(...)`, `clear(...)`, `isMournableTombstone(...)`, `getMournableGraves(...)`, and `canMournAmbiently(...)` match the specification exactly.
- `GrieveTask` remains present and registered; only its responsibility changes.
