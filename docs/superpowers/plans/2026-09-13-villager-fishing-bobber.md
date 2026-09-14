# Villager Fishing Player-Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade MCA 1.21.1 villager fishing so the bobber uses vanilla-shaped lure/bite feedback, origin/1.21.1's 65% catch chance is applied once per real bite, successful catches visibly reel one protected item to the villager, and the fishing line attaches to the actually rendered rod/hand.

**Architecture:** Keep the existing MCA-owned `MCAFishingBobberEntity` because vanilla `FishingHook` is player-bound, but reuse vanilla APIs and algorithms everywhere the ownership boundary allows it. The bobber owns vanilla-shaped bobbing/lure/bite state; `FishingTask` only orchestrates water, loot, reel delivery, durability, and cleanup; a villager render layer derives the line anchor from `HumanoidModel.translateToHand(...)` and vanilla `ItemInHandLayer` transforms; the bobber renderer becomes billboard-only.

**Tech Stack:** Java 21, Minecraft 1.21.1, MCA common/Fabric/NeoForge modules, vanilla `ThrowableProjectile`, `SynchedEntityData`, `ItemEntity`, `ParticleTypes`, `SoundEvents`, humanoid render layers, NeoForge GameTest, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-13-villager-fishing-bobber-design.md`

## Global Constraints

- Implement and validate 1.21.1 first; port to 26.1.2 and 26.2 only after live 1.21.1 proof.
- Preserve the existing MCA bobber entity and exact-water targeting; do not mix into or subclass vanilla `FishingHook`.
- Treat `C:/Users/Mik/Downloads/MCA/local-source/src` as the 1.21.1 design oracle and never modify it.
- Reuse vanilla generic APIs directly: inherited projectile ownership/movement, `SynchedEntityData`, `ParticleTypes`, `SoundEvents.FISHING_BOBBER_SPLASH`, `ItemEntity`, `ItemEntity.setNeverPickUp()`, `SimpleContainer.addItem(...)`, `HumanoidModel.translateToHand(...)`, vanilla held-item transforms, hook texture, and line geometry.
- Adapt only the narrow vanilla logic that is private or player-bound. Do not add mixins/invokers solely to reach private fishing helpers.
- Use vanilla base fishing timing ranges: lure wait 100-600 ticks, approach 20-80 ticks, bite window 20-40 ticks. Do not add vanilla open-water checks, rain/sky timing modifiers, Lure/Luck mechanics, hooked-entity behavior, player XP/stats/criteria, or fake players.
- Use vanilla catch timing for the real bite window, but preserve origin/1.21.1's catch chance: on the first tick of each bite, roll exactly once with `random.nextFloat() >= 0.35F`. Success reels during the nibble window; failure lets that bite expire without loot or rod damage. Remove only the independent 200-399 tick catch timer.
- The in-flight caught stack exists in exactly one real `ItemEntity`; do not insert a duplicate into inventory while it is flying.
- Call `ItemEntity.setNeverPickUp()` immediately when the reel item is created. While MCA owns that reel, player/mob pickup and normal item-entity merging must remain disabled by vanilla pickup-delay behavior.
- Release pickup protection only when the reel has ended: inventory remainder, owner death/removal, or lost task ownership. Use `setNoPickUpDelay()` then leave the one real remainder as a normal world drop.
- Fishing must remain active past the inherited 400-tick chore timeout; keep the fishing-specific `timedOut(...) == false` behavior.
- The fishing line must derive from the rendered MCA humanoid arm/held-item transform. Do not restore an eye/body offset approximation.
- Client visual verification is mandatory for the line, particles, dip, item flight, and handedness; server tests and compilation are insufficient proof.
- Preserve unrelated dirty worktree changes. Each commit stages only the fishing files changed by that task.
- Before completion, follow `java-code-review-cleanup`: freeze one fishing-only diff, compare against local vanilla owners, run reuse/quality/correctness/efficiency review lenses, fix worthwhile findings, and rerun verification.

---

## File Map

### Modify

- `common/src/main/java/net/conczin/mca/entity/MCAFishingBobberEntity.java`
  - add synchronized biting state
  - port the visible vanilla lure/approach/bite cycle
  - use vanilla-shaped bobbing stabilization and bite dip
- `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/FishingTask.java`
  - remove the independent timer and move origin's 65%/35% roll to real bites
  - roll once per `bobber.isBiting()` window and reel successful attempts
  - create/protect/deliver one vanilla `ItemEntity`
  - keep catch cleanup and rod durability single-owned
- `common/src/main/java/net/conczin/mca/client/render/MCAFishingBobberRenderer.java`
  - retain vanilla hook billboard only
  - remove the guessed hand-position and line code
- `common/src/main/java/net/conczin/mca/client/render/VillagerEntityMCARenderer.java`
  - install the fishing-line render layer
- `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/FishingTaskGameTests.java`
  - add bite, reel, anti-pickup, and remainder regressions

### Create

- `common/src/main/java/net/conczin/mca/client/render/layer/VillagerFishingLineLayer.java`
  - locate the villager-owned MCA bobber on the client
  - derive the fishing-line origin from the posed humanoid hand/item transform
  - render vanilla-shaped 16-segment line geometry

### Intentionally unchanged unless compilation proves an API mismatch

- `common/src/main/java/net/conczin/mca/registry/EntitiesMCA.java`
- `fabric/src/main/java/net/conczin/mca/fabric/MCAFabricClient.java`
- `neoforge/src/main/java/net/conczin/mca/neoforge/ClientNeoForge.java`
- `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/AbstractChoreTask.java`
- files under `C:/Users/Mik/Downloads/MCA/local-source`

---

### Task 1: Make the MCA bobber own the vanilla-shaped lure and bite cycle

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/entity/MCAFishingBobberEntity.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/FishingTaskGameTests.java`

**Interfaces:**
- Keeps: `MCAFishingBobberEntity.cast(ServerLevel, VillagerEntityMCA, BlockPos)`
- Keeps: `MCAFishingBobberEntity.isBobbing()`
- Produces: `public boolean isBiting()`
- Consumed by Task 2: `FishingTask` reels only when `isBiting()` is true

- [ ] **Step 1: Add a natural-cycle GameTest that fails before bite state exists**

Add this test beside the existing bobber test. It deliberately waits through the real maximum 600 + 80 tick lure/approach window instead of adding a test-only timer setter:

```java
@GameTest(
        batch = "mca_fishing_bite",
        templateNamespace = "minecraft",
        template = "bastion/blocks/air",
        timeoutTicks = 800
)
public static void bobberEventuallyEntersRealBiteWindow(GameTestHelper helper) {
    BlockPos villagerPos = helper.absolutePos(new BlockPos(3, 2, 3));
    BlockPos water = villagerPos.east(2);
    prepareWater(helper, water);

    VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
    MCAFishingBobberEntity bobber = MCAFishingBobberEntity.cast(helper.getLevel(), villager, water);

    helper.succeedWhen(() -> helper.assertTrue(
            bobber.isBobbing() && bobber.isBiting(),
            "bobber never reached the vanilla-shaped bite window"
    ));
}
```

- [ ] **Step 2: Run compilation and verify RED**

Run:

```powershell
./gradlew :neoforge:compileJava
```

Expected: compilation fails because `MCAFishingBobberEntity.isBiting()` does not exist yet.

- [ ] **Step 3: Add vanilla-shaped synchronized bite state without duplicate booleans**

In `MCAFishingBobberEntity`, add the vanilla-equivalent synchronized field plus only the state needed for the visible fish cycle:

```java
private static final EntityDataAccessor<Boolean> DATA_BITING =
        SynchedEntityData.defineId(MCAFishingBobberEntity.class, EntityDataSerializers.BOOLEAN);

private final RandomSource synchronizedRandom = RandomSource.create();
private int nibble;
private int timeUntilLured;
private int timeUntilHooked;
private float fishAngle;

public boolean isBiting() {
    return entityData.get(DATA_BITING);
}

@Override
protected void defineSynchedData(SynchedEntityData.Builder builder) {
    builder.define(DATA_BITING, false);
}
```

Do not add a second `boolean biting`; `DATA_BITING` is the one source of truth.

Add the client/server transition response using vanilla `FishingHook.onSyncedDataUpdated(...)` as the source:

```java
@Override
public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
    if (DATA_BITING.equals(key) && isBiting()) {
        setDeltaMovement(
                getDeltaMovement().x,
                -0.4F * Mth.nextFloat(synchronizedRandom, 0.6F, 1.0F),
                getDeltaMovement().z
        );
    }
    super.onSyncedDataUpdated(key);
}
```

- [ ] **Step 4: Replace MCA's independent bobbing approximation with the narrow vanilla water behavior we need**

At the start of `tick()`, seed the synchronized random the same way vanilla does:

```java
synchronizedRandom.setSeed(getUUID().getLeastSignificantBits() ^ level().getGameTime());
```

When the bobber is in water, keep MCA's outer `bobbing` flag but use the local 1.21.1 `FishingHook` surface correction formula:

```java
private void stabilizeOnWater(FluidState fluid) {
    BlockPos pos = blockPosition();
    double surfaceHeight = fluid.getHeight(level(), pos);
    Vec3 motion = getDeltaMovement();
    double surfaceOffset = getY() + motion.y - pos.getY() - surfaceHeight;
    if (Math.abs(surfaceOffset) < 0.01) {
        surfaceOffset += Math.signum(surfaceOffset) * 0.1;
    }

    setDeltaMovement(
            motion.x * 0.9,
            motion.y - surfaceOffset * random.nextFloat() * 0.2,
            motion.z * 0.9
    );

    if (isBiting()) {
        setDeltaMovement(getDeltaMovement().add(
                0.0,
                -0.1 * synchronizedRandom.nextFloat() * synchronizedRandom.nextFloat(),
                0.0
        ));
    }
}
```

After stabilization, call `catchingFish()` only on the server. Leave MCA's working exact-target cast, failed-cast timeout, owner/rod/chore validation, and 32-block owner-distance cleanup intact.

- [ ] **Step 5: Port only vanilla's visible `catchingFish(...)` branch**

Add this method based on local 1.21.1 `FishingHook.catchingFish(...)`. Keep vanilla timer ranges, particle formulas, and splash sound; intentionally omit open-water, rain/sky timing, lure enchantment, and player-only logic:

```java
private void catchingFish() {
    ServerLevel world = (ServerLevel) level();

    if (nibble > 0) {
        nibble--;
        if (nibble <= 0) {
            timeUntilLured = 0;
            timeUntilHooked = 0;
            entityData.set(DATA_BITING, false);
        }
        return;
    }

    if (timeUntilHooked > 0) {
        timeUntilHooked--;
        if (timeUntilHooked > 0) {
            fishAngle += (float) random.triangle(0.0, 9.188);
            float angle = fishAngle * Mth.DEG_TO_RAD;
            float sin = Mth.sin(angle);
            float cos = Mth.cos(angle);
            double x = getX() + sin * timeUntilHooked * 0.1F;
            double y = Mth.floor(getY()) + 1.0F;
            double z = getZ() + cos * timeUntilHooked * 0.1F;

            if (world.getBlockState(BlockPos.containing(x, y - 1.0, z)).is(Blocks.WATER)) {
                if (random.nextFloat() < 0.15F) {
                    world.sendParticles(ParticleTypes.BUBBLE, x, y - 0.1F, z, 1, sin, 0.1, cos, 0.0);
                }
                float wakeX = sin * 0.04F;
                float wakeZ = cos * 0.04F;
                world.sendParticles(ParticleTypes.FISHING, x, y, z, 0, wakeZ, 0.01, -wakeX, 1.0);
                world.sendParticles(ParticleTypes.FISHING, x, y, z, 0, -wakeZ, 0.01, wakeX, 1.0);
            }
        } else {
            playSound(
                    SoundEvents.FISHING_BOBBER_SPLASH,
                    0.25F,
                    1.0F + (random.nextFloat() - random.nextFloat()) * 0.4F
            );
            double y = getY() + 0.5;
            int count = (int) (1.0F + getBbWidth() * 20.0F);
            world.sendParticles(ParticleTypes.BUBBLE, getX(), y, getZ(), count, getBbWidth(), 0.0, getBbWidth(), 0.2F);
            world.sendParticles(ParticleTypes.FISHING, getX(), y, getZ(), count, getBbWidth(), 0.0, getBbWidth(), 0.2F);
            nibble = Mth.nextInt(random, 20, 40);
            entityData.set(DATA_BITING, true);
        }
        return;
    }

    if (timeUntilLured > 0) {
        timeUntilLured--;
        float splashChance = 0.15F;
        if (timeUntilLured < 20) {
            splashChance += (20 - timeUntilLured) * 0.05F;
        } else if (timeUntilLured < 40) {
            splashChance += (40 - timeUntilLured) * 0.02F;
        } else if (timeUntilLured < 60) {
            splashChance += (60 - timeUntilLured) * 0.01F;
        }

        if (random.nextFloat() < splashChance) {
            float angle = Mth.nextFloat(random, 0.0F, 360.0F) * Mth.DEG_TO_RAD;
            float distance = Mth.nextFloat(random, 25.0F, 60.0F);
            double x = getX() + Mth.sin(angle) * distance * 0.1;
            double y = Mth.floor(getY()) + 1.0F;
            double z = getZ() + Mth.cos(angle) * distance * 0.1F;
            if (world.getBlockState(BlockPos.containing(x, y - 1.0, z)).is(Blocks.WATER)) {
                world.sendParticles(ParticleTypes.SPLASH, x, y, z, 2 + random.nextInt(2), 0.1F, 0.0, 0.1F, 0.0);
            }
        }

        if (timeUntilLured <= 0) {
            fishAngle = Mth.nextFloat(random, 0.0F, 360.0F);
            timeUntilHooked = Mth.nextInt(random, 20, 80);
        }
        return;
    }

    timeUntilLured = Mth.nextInt(random, 100, 600);
}
```

Keep this method close to vanilla naming/structure so later source review and forward ports can diff it mechanically.

- [ ] **Step 6: Run the fishing GameTests and verify GREEN**

Run:

```powershell
./gradlew :neoforge:runGameTestServer --no-configuration-cache
```

Expected: all registered NeoForge GameTests pass, including `mca_fishing_bite`; the existing cast/timeout/single-bobber/stop/rod-loss tests remain green.

- [ ] **Step 7: Commit only Task 1 files**

```powershell
git add common/src/main/java/net/conczin/mca/entity/MCAFishingBobberEntity.java neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/FishingTaskGameTests.java
git commit -m "feat: add vanilla-style villager fishing bites"
```

---

### Task 2: Apply origin catch chance to real bites and protect successful reel items

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/FishingTask.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/FishingTaskGameTests.java`

**Interfaces:**
- Consumes: `MCAFishingBobberEntity.isBiting()` from Task 1
- Produces task state: `ItemEntity reelItem`, `int reelTicks`, `boolean biteAttempted`
- Preserves: `getFishingLoot(ServerLevel, VillagerEntityMCA)` and MCA loot context
- Uses vanilla API: `ItemEntity.setNeverPickUp()`, `ItemEntity.setNoPickUpDelay()`, `SimpleContainer.addItem(...)`

- [ ] **Step 1: Add an end-to-end regression for vanilla bite-window catch and protected reel flight**

Add imports for `ItemEntity` and `AtomicBoolean`, then add:

```java
@GameTest(
        batch = "mca_fishing_reel",
        templateNamespace = "minecraft",
        template = "bastion/blocks/air",
        timeoutTicks = 900
)
public static void biteReelsOneProtectedItemIntoInventory(GameTestHelper helper) {
    BlockPos villagerPos = helper.absolutePos(new BlockPos(3, 2, 3));
    prepareWater(helper, villagerPos.east(2));
    VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
    TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL), true);
    Player thief = helper.makeMockPlayer(GameType.SURVIVAL);
    AtomicBoolean sawProtectedReel = new AtomicBoolean();
    int startingLoot = countCaughtItems(villager);
    int startingRodDamage = villager.getItemInHand(villager.getDominantHand()).getDamageValue();

    task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());
    helper.onEachTick(() -> {
        task.tick(helper.getLevel(), villager, helper.getLevel().getGameTime());
        for (ItemEntity item : activeReelItems(helper, villager)) {
            sawProtectedReel.set(true);
            helper.assertTrue(item.hasPickUpDelay(), "reel item became naturally pickup-eligible in flight");
            item.playerTouch(thief);
            helper.assertTrue(!item.isRemoved(), "another player stole the protected reel item");
        }
    });

    helper.succeedWhen(() -> {
        helper.assertTrue(sawProtectedReel.get(), "no real reel ItemEntity was observed");
        helper.assertTrue(thief.getInventory().isEmpty(), "protected reel item entered another player's inventory");
        helper.assertTrue(countCaughtItems(villager) > startingLoot, "a real bite did not deliver a catch");
        helper.assertTrue(activeReelItems(helper, villager).isEmpty(), "delivered reel item was left in the world");
        helper.assertTrue(
                villager.getItemInHand(villager.getDominantHand()).getDamageValue() == startingRodDamage + 1,
                "one delivered bite did not damage the fishing rod exactly once"
        );
    });
}

private static List<ItemEntity> activeReelItems(GameTestHelper helper, VillagerEntityMCA villager) {
    return helper.getLevel().getEntitiesOfClass(
            ItemEntity.class,
            villager.getBoundingBox().inflate(32.0D),
            item -> !item.isRemoved()
    );
}

private static int countCaughtItems(VillagerEntityMCA villager) {
    return villager.getInventory().getItems().stream()
            .filter(stack -> !stack.isEmpty())
            .filter(stack -> !(stack.getItem() instanceof FishingRodItem))
            .mapToInt(ItemStack::getCount)
            .sum();
}
```

This test exercises the real task/bobber path with the origin chance forced to its successful branch and directly calls vanilla `playerTouch(...)` on the in-flight item to prove the pickup delay protects it from a competing player.

Keep existing one-argument `TestFishingTask(...)` callers working, and add an optional deterministic catch-result override for the new success/miss regressions:

```java
private static final class TestFishingTask extends FishingTask {
    private final Player assigningPlayer;
    private final Boolean forcedBiteResult;

    private TestFishingTask(Player assigningPlayer) {
        this(assigningPlayer, null);
    }

    private TestFishingTask(Player assigningPlayer, Boolean forcedBiteResult) {
        this.assigningPlayer = assigningPlayer;
        this.forcedBiteResult = forcedBiteResult;
    }

    @Override
    boolean shouldReelBite(VillagerEntityMCA villager) {
        return forcedBiteResult != null ? forcedBiteResult : super.shouldReelBite(villager);
    }

    // Keep the existing getAssigningPlayer(), abandonJobWithMessage(...), and isTimedOut(...) overrides.
}
```

The production method still uses the real origin predicate; this override exists only so GameTests can prove both sides without probabilistic flakes.

Add a second regression for the 35% miss branch:

```java
@GameTest(
        batch = "mca_fishing_miss",
        templateNamespace = "minecraft",
        template = "bastion/blocks/air",
        timeoutTicks = 900
)
public static void failedOriginCatchRollLetsBiteExpireWithoutLoot(GameTestHelper helper) {
    BlockPos villagerPos = helper.absolutePos(new BlockPos(3, 2, 3));
    prepareWater(helper, villagerPos.east(2));
    VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
    TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL), false);
    AtomicBoolean sawBite = new AtomicBoolean();
    int startingLoot = countCaughtItems(villager);
    int startingRodDamage = villager.getItemInHand(villager.getDominantHand()).getDamageValue();

    task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());
    helper.onEachTick(() -> {
        task.tick(helper.getLevel(), villager, helper.getLevel().getGameTime());
        MCAFishingBobberEntity active = activeBobber(helper, villager);
        if (active != null && active.isBiting()) {
            sawBite.set(true);
        }
    });

    helper.succeedWhen(() -> {
        MCAFishingBobberEntity active = activeBobber(helper, villager);
        helper.assertTrue(sawBite.get(), "forced miss never reached a real bite");
        helper.assertTrue(active != null && !active.isBiting(), "failed bite did not expire back to waiting");
        helper.assertTrue(countCaughtItems(villager) == startingLoot, "failed origin catch roll produced loot");
        helper.assertTrue(activeReelItems(helper, villager).isEmpty(), "failed origin catch roll spawned a reel item");
        helper.assertTrue(
                villager.getItemInHand(villager.getDominantHand()).getDamageValue() == startingRodDamage,
                "failed origin catch roll damaged the fishing rod"
        );
    });
}
```

Add a small test helper that resolves the one owned active bobber without introducing production state:

```java
private static MCAFishingBobberEntity activeBobber(GameTestHelper helper, VillagerEntityMCA villager) {
    return helper.getLevel()
            .getEntitiesOfClass(MCAFishingBobberEntity.class, villager.getBoundingBox().inflate(32.0D))
            .stream()
            .filter(entity -> !entity.isRemoved() && entity.getVillagerOwner() == villager)
            .findFirst()
            .orElse(null);
}
```

- [ ] **Step 2: Run the GameTest server and verify RED**

Run:

```powershell
./gradlew :neoforge:runGameTestServer --no-configuration-cache
```

Expected: `mca_fishing_reel` fails because the current task has no reel `ItemEntity`; it still uses its independent timer/miss roll and inserts successful loot directly into inventory.

- [ ] **Step 3: Replace the independent catch timer with reel state**

In `FishingTask`, remove:

```java
private int ticks;
```

Add:

```java
private static final int MAX_REEL_TICKS = 40;
private static final double REEL_DELIVERY_DISTANCE_SQR = 1.5 * 1.5;

private ItemEntity reelItem;
private int reelTicks;
private boolean biteAttempted;
```

At the top of `tick(...)`, process an already-earned reel before checking for another rod/cast:

```java
if (tickReelItem(villager)) {
    return;
}
```

This ordering lets a bite that already earned loot finish even if that catch broke the villager's last rod. After the reel finishes, the normal next tick can equip a spare rod or abandon fishing.

Delete all `ticks` resets and the current timer block containing:

```java
if (ticks >= villager.level().random.nextInt(200) + 200) {
    if (villager.level().random.nextFloat() >= 0.35F) {
        // direct catch
    }
    ticks = 0;
}
```

Add the origin/1.21.1 catch predicate as one narrow method so GameTests can force each branch deterministically:

```java
boolean shouldReelBite(VillagerEntityMCA villager) {
    return villager.getRandom().nextFloat() >= 0.35F;
}
```

Replace the bobbing decision with one roll per nibble window:

```java
if (!bobber.isBiting()) {
    biteAttempted = false;
} else if (!biteAttempted) {
    biteAttempted = true;
    if (shouldReelBite(villager)) {
        beginReel(world, villager);
    }
}
```

This preserves origin's exact 65% success / 35% miss predicate while tying it to a real vanilla-style bite. A failed attempt leaves the hook in its current nibble window; `biteAttempted` prevents repeated rolls until biting clears and a later bite begins. Reset `biteAttempted` whenever the bobber is discarded/recast or task state is cleared.

Update `discardBobber()` so ownership reset is explicit:

```java
private void discardBobber() {
    if (bobber != null && !bobber.isRemoved()) {
        bobber.discard();
    }
    bobber = null;
    biteAttempted = false;
}
```

- [ ] **Step 4: Spawn the caught item using vanilla `FishingHook.retrieve(...)` velocity and vanilla pickup protection**

Add:

```java
private void beginReel(ServerLevel world, VillagerEntityMCA villager) {
    ItemStack caught = getFishingLoot(world, villager);
    villager.swing(villager.getDominantHand());

    ItemEntity item = new ItemEntity(world, bobber.getX(), bobber.getY(), bobber.getZ(), caught);
    item.setNeverPickUp();

    double dx = villager.getX() - bobber.getX();
    double dy = villager.getY() - bobber.getY();
    double dz = villager.getZ() - bobber.getZ();
    double distanceSqr = dx * dx + dy * dy + dz * dz;
    item.setDeltaMovement(
            dx * 0.1,
            dy * 0.1 + Math.sqrt(Math.sqrt(distanceSqr)) * 0.08,
            dz * 0.1
    );

    world.addFreshEntity(item);
    reelItem = item;
    reelTicks = 0;

    discardBobber();
    villager.getItemInHand(villager.getDominantHand())
            .hurtAndBreak(1, villager, villager.getDominantSlot());
}
```

The velocity is the vanilla 1.21.1 retrieval formula with only the destination changed from `Player` to `VillagerEntityMCA`.

- [ ] **Step 5: Deliver exactly the real in-flight stack and preserve inventory remainder**

Add:

```java
private boolean tickReelItem(VillagerEntityMCA villager) {
    if (reelItem == null) {
        return false;
    }
    if (reelItem.isRemoved()) {
        clearReelReference();
        return false;
    }

    reelTicks++;
    if (reelItem.distanceToSqr(villager) <= REEL_DELIVERY_DISTANCE_SQR || reelTicks >= MAX_REEL_TICKS) {
        finishReelItem(villager);
    }
    return true;
}

private void finishReelItem(VillagerEntityMCA villager) {
    if (reelItem == null || reelItem.isRemoved()) {
        clearReelReference();
        return;
    }

    ItemStack remainder = villager.getInventory().addItem(reelItem.getItem());
    if (remainder.isEmpty()) {
        reelItem.discard();
    } else {
        reelItem.setItem(remainder);
        reelItem.setNoPickUpDelay();
    }
    clearReelReference();
}

private void releaseReelItem() {
    if (reelItem != null && !reelItem.isRemoved()) {
        reelItem.setNoPickUpDelay();
    }
    clearReelReference();
}

private void clearReelReference() {
    reelItem = null;
    reelTicks = 0;
}
```

Do not call `setNoPickUpDelay()` while the task still owns a healthy in-flight reel. The only normal successful path is explicit MCA delivery into inventory.

- [ ] **Step 6: Make task stop preserve an earned catch without creating a duplicate**

Keep `discardBobber()` idempotent. In `stop(...)`, handle a live reel before clearing the temporary rod:

```java
if (reelItem != null) {
    if (villager.isAlive() && !villager.isRemoved()) {
        finishReelItem(villager);
    } else {
        releaseReelItem();
    }
}

discardBobber();
targetWater = null;
```

If `finishReelItem(...)` encounters a full inventory, the existing `ItemEntity` becomes the one normal world remainder. If the owner is gone, the existing item is released to normal pickup. Never generate a replacement stack in either case.

- [ ] **Step 7: Add the full-inventory remainder regression**

Add a second GameTest using the same natural fishing cycle. Fill the villager inventory after `spawnFisher(...)` while leaving the held rod intact:

```java
@GameTest(
        batch = "mca_fishing_reel_remainder",
        templateNamespace = "minecraft",
        template = "bastion/blocks/air",
        timeoutTicks = 900
)
public static void fullInventoryReleasesOnlyTheRealReelRemainder(GameTestHelper helper) {
    BlockPos villagerPos = helper.absolutePos(new BlockPos(3, 2, 3));
    prepareWater(helper, villagerPos.east(2));
    VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
    for (int slot = 0; slot < villager.getInventory().getContainerSize(); slot++) {
        villager.getInventory().setItem(slot, new ItemStack(Blocks.STONE, 64));
    }

    TestFishingTask task = new TestFishingTask(helper.makeMockPlayer(GameType.SURVIVAL), true);
    task.start(helper.getLevel(), villager, helper.getLevel().getGameTime());
    helper.onEachTick(() -> task.tick(helper.getLevel(), villager, helper.getLevel().getGameTime()));

    helper.succeedWhen(() -> {
        List<ItemEntity> drops = activeReelItems(helper, villager);
        helper.assertTrue(drops.size() == 1, "full inventory did not preserve exactly one reel remainder");
        helper.assertTrue(!drops.getFirst().hasPickUpDelay(), "finished reel remainder stayed permanently protected");
    });
}
```

Java 21 is configured on this branch, so `List.getFirst()` is available.

- [ ] **Step 8: Run focused compilation and full GameTests**

Run:

```powershell
./gradlew :common:compileJava :neoforge:compileJava
./gradlew :neoforge:runGameTestServer --no-configuration-cache
```

Expected: compilation succeeds; all GameTests pass; `mca_fishing_reel` proves the forced-success branch creates one protected real item, `mca_fishing_miss` proves a forced miss produces no loot/item/durability and does not reroll the same bite, and `mca_fishing_reel_remainder` leaves one normal remainder when inventory is full.

- [ ] **Step 9: Commit only Task 2 files**

```powershell
git add common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/FishingTask.java neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/FishingTaskGameTests.java
git commit -m "feat: reel villager fishing catches visibly"
```

---

### Task 3: Attach the fishing line to MCA's actual rendered rod/hand

**Files:**
- Create: `common/src/main/java/net/conczin/mca/client/render/layer/VillagerFishingLineLayer.java`
- Modify: `common/src/main/java/net/conczin/mca/client/render/MCAFishingBobberRenderer.java`
- Modify: `common/src/main/java/net/conczin/mca/client/render/VillagerEntityMCARenderer.java`

**Interfaces:**
- Consumes: `MCAFishingBobberEntity.getVillagerOwner()` and the existing owner relation
- Consumes vanilla model API: `VillagerEntityModelMCA.translateToHand(HumanoidArm, PoseStack)` inherited from `HumanoidModel`
- Produces: one client render layer; no synchronized bobber ID, cache, or new network state
- Leaves loader registration unchanged: both loaders already register `MCAFishingBobberRenderer`

This is visual work. Do not add a brittle unit test that merely repeats matrix constants. Verification is loader compilation plus live client proof against the supplied screenshot regression.

- [ ] **Step 1: Reduce the bobber renderer to vanilla billboard responsibility**

In `MCAFishingBobberRenderer`, keep the vanilla hook texture, `RenderType.entityCutout(...)`, billboard scale/orientation, four hook vertices, and `getTextureLocation(...)`.

Remove:

```java
getVillagerHandPos(...)
fraction(...)
stringVertex(...)
RenderType.lineStrip()
```

Also remove imports used only by the old guessed player-style hand offset (`HumanoidArm`, `Mth`, and `Vec3` if no longer used).

The bobber renderer should no longer reconstruct any villager arm position.

- [ ] **Step 2: Create `VillagerFishingLineLayer` and derive the hand origin through the same vanilla model path that renders held items**

Create `common/src/main/java/net/conczin/mca/client/render/layer/VillagerFishingLineLayer.java` with this structure:

```java
public final class VillagerFishingLineLayer
        extends RenderLayer<VillagerEntityMCA, VillagerEntityModelMCA<VillagerEntityMCA>> {
    private static final double BOBBER_SEARCH_RADIUS = 32.0;

    public VillagerFishingLineLayer(
            RenderLayerParent<VillagerEntityMCA, VillagerEntityModelMCA<VillagerEntityMCA>> parent
    ) {
        super(parent);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            VillagerEntityMCA villager,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch
    ) {
        if (!(villager.getItemInHand(villager.getDominantHand()).getItem() instanceof FishingRodItem)) {
            return;
        }

        MCAFishingBobberEntity bobber = findOwnedBobber(villager);
        if (bobber == null) {
            return;
        }

        Vector3f hand = getRenderedRodOrigin(villager);
        Vector3f hook = getHookInCurrentModelSpace(poseStack, bobber, partialTicks);
        renderLine(poseStack, bufferSource, hook, hand);
    }
}
```

Implement `getRenderedRodOrigin(...)` from local vanilla `ItemInHandLayer.render(...)` and `renderArmWithItem(...)`, reusing the already-posed parent model:

```java
private Vector3f getRenderedRodOrigin(VillagerEntityMCA villager) {
    PoseStack handPose = new PoseStack();
    if (getParentModel().young) {
        handPose.translate(0.0F, 0.75F, 0.0F);
        handPose.scale(0.5F, 0.5F, 0.5F);
    }

    HumanoidArm arm = villager.getMainArm();
    getParentModel().translateToHand(arm, handPose);
    handPose.mulPose(Axis.XP.rotationDegrees(-90.0F));
    handPose.mulPose(Axis.YP.rotationDegrees(180.0F));
    boolean left = arm == HumanoidArm.LEFT;
    handPose.translate((left ? -1 : 1) / 16.0F, 0.125F, -0.625F);

    return handPose.last().pose().transformPosition(new Vector3f());
}
```

Start with the exact `ItemInHandLayer` grip transform. Do not add a body/eye-space correction. If live client proof shows the line enters the grip rather than the visible rod/string point, tune at most one small **rod-local** offset after this transform and document the measured value next to it.

- [ ] **Step 3: Convert the interpolated bobber world position into the already-transformed villager model space**

The render layer's current `PoseStack` already contains `LivingEntityRenderer` rotation, scale, MCA body scaling, and baby translation. Convert the bobber endpoint through the inverse of that exact matrix rather than recreating those transforms:

```java
private Vector3f getHookInCurrentModelSpace(
        PoseStack poseStack,
        MCAFishingBobberEntity bobber,
        float partialTicks
) {
    Vec3 hookWorld = bobber.getPosition(partialTicks).add(0.0, 0.25, 0.0);
    Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
    Vector3f hookRenderSpace = new Vector3f(
            (float) (hookWorld.x - camera.x),
            (float) (hookWorld.y - camera.y),
            (float) (hookWorld.z - camera.z)
    );

    Matrix4f inverseVillagerPose = new Matrix4f(poseStack.last().pose()).invert();
    return inverseVillagerPose.transformPosition(hookRenderSpace);
}
```

During execution, verify this against local `EntityRenderDispatcher.render(...)`: the entity pose stack is translated by camera-relative entity coordinates before `LivingEntityRenderer` applies the model transforms. If the active mapping exposes the main camera through a slightly different accessor, use the mapped 1.21.1 equivalent while preserving this coordinate-space rule.

- [ ] **Step 4: Render the same 16-segment vanilla curve between hook and hand**

Use `RenderType.lineStrip()` and adapt vanilla `FishingHookRenderer.stringVertex(...)` so the start point is the hook already expressed in villager model space:

```java
private static void renderLine(
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        Vector3f hook,
        Vector3f hand
) {
    float dx = hand.x() - hook.x();
    float dy = hand.y() - hook.y();
    float dz = hand.z() - hook.z();
    VertexConsumer consumer = bufferSource.getBuffer(RenderType.lineStrip());
    PoseStack.Pose pose = poseStack.last();

    for (int segment = 0; segment <= 16; segment++) {
        float fraction = (float) segment / 16.0F;
        float nextFraction = (float) (segment + 1) / 16.0F;
        stringVertex(hook, dx, dy, dz, consumer, pose, fraction, nextFraction);
    }
}

private static void stringVertex(
        Vector3f hook,
        float dx,
        float dy,
        float dz,
        VertexConsumer consumer,
        PoseStack.Pose pose,
        float fraction,
        float nextFraction
) {
    float x = hook.x() + dx * fraction;
    float y = hook.y() + dy * (fraction * fraction + fraction) * 0.5F;
    float z = hook.z() + dz * fraction;
    float nextX = hook.x() + dx * nextFraction;
    float nextY = hook.y() + dy * (nextFraction * nextFraction + nextFraction) * 0.5F;
    float nextZ = hook.z() + dz * nextFraction;
    float nx = nextX - x;
    float ny = nextY - y;
    float nz = nextZ - z;
    float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);

    consumer.addVertex(pose, x, y, z)
            .setColor(-16777216)
            .setNormal(pose, nx / length, ny / length, nz / length);
}
```

The `+0.25` hook-height adjustment is already included in `hookWorld`; do not add it again inside the curve.

- [ ] **Step 5: Find the client bobber through inherited projectile ownership without new sync/cache state**

Add:

```java
@Nullable
private static MCAFishingBobberEntity findOwnedBobber(VillagerEntityMCA villager) {
    return villager.level()
            .getEntitiesOfClass(
                    MCAFishingBobberEntity.class,
                    villager.getBoundingBox().inflate(BOBBER_SEARCH_RADIUS),
                    bobber -> !bobber.isRemoved() && bobber.getVillagerOwner() == villager
            )
            .stream()
            .findFirst()
            .orElse(null);
}
```

Do not add a synchronized bobber ID or persistent render cache unless profiling later proves this bounded lookup material.

- [ ] **Step 6: Install the layer on the MCA villager renderer**

In `VillagerEntityMCARenderer` after the existing skin/face/clothing/hair layers, add:

```java
addLayer(new VillagerFishingLineLayer(this));
```

Import the new layer. Do not add loader-specific registration for this layer.

- [ ] **Step 7: Compile both loaders**

Run:

```powershell
./gradlew :fabric:compileJava :neoforge:compileJava
```

Expected: both loaders compile with the common render layer and the existing bobber renderer registrations unchanged.

- [ ] **Step 8: Run live Fabric and NeoForge visual acceptance before committing**

Launch each 1.21.1 client using the repository's existing run tasks/configurations. For each loader, verify all of these in-world:

1. right-handed villager: line intersects the held rod/hand and never starts from neck/chest;
2. left-handed villager: line attaches to the physical left-side rod;
3. arm swing/cast: attachment follows the posed arm rather than remaining fixed on the torso;
4. adult and baby/custom-scale villagers: attachment remains visually connected under MCA scaling;
5. bobber wait: intermittent vanilla-style distant splash appears;
6. approach: `FISHING` wake and occasional bubbles converge on the bobber;
7. bite: splash sound/particles occur and the bobber visibly dips;
8. reel: the caught item visibly leaves the bobber and travels toward the villager;
9. leave fishing running past 400 ticks: rod/bobber do not periodically de-equip;
10. compare against the supplied screenshot: the previous upper-body line origin must be gone.
11. remove/replace the selected water while fishing and confirm the stale bobber is discarded and a new valid target can be found.
12. run the existing AquaCulture/current fishing-loot compatibility scenario and confirm the caught item still comes from MCA's existing fishing loot path.

If the grip-origin needs correction, change only a small rod-local offset after the vanilla hand/item transform, rerun both clients, and keep the offset only when it visibly improves both handedness cases.

- [ ] **Step 9: Commit only Task 3 files**

```powershell
git add common/src/main/java/net/conczin/mca/client/render/MCAFishingBobberRenderer.java common/src/main/java/net/conczin/mca/client/render/VillagerEntityMCARenderer.java common/src/main/java/net/conczin/mca/client/render/layer/VillagerFishingLineLayer.java
git commit -m "fix: attach villager fishing line to rendered rod"
```

---

### Task 4: Run the source-grounded Java cleanup gate and final verification

**Files:**
- Review/fix only the fishing Java scope created by Tasks 1-3 and the existing fishing GameTests
- Do not absorb unrelated dirty files into review fixes or commits

**Interfaces:**
- Consumes the completed Task 1-3 implementation
- Produces a reviewed fishing-only diff with one state owner per concern and verified 1.21.1 behavior

- [ ] **Step 1: Freeze the exact fishing-only diff for all four review lenses**

Use the initial committed fishing renderer checkpoint as the comparison base and restrict the diff to the fishing scope:

```powershell
git diff 4dacd1435..HEAD -- common/src/main/java/net/conczin/mca/entity/MCAFishingBobberEntity.java common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/FishingTask.java common/src/main/java/net/conczin/mca/client/render/MCAFishingBobberRenderer.java common/src/main/java/net/conczin/mca/client/render/VillagerEntityMCARenderer.java common/src/main/java/net/conczin/mca/client/render/layer/VillagerFishingLineLayer.java neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/FishingTaskGameTests.java
```

Save/reuse that same file set/diff for reuse, quality, correctness, and efficiency review. Do not silently widen the review to the unrelated archer/config/navigation worktree changes.

- [ ] **Step 2: Recompare every adapted behavior with its vanilla/MCA owner before accepting custom code**

Read side by side:

```text
C:/Users/Mik/Downloads/MCA/local-source/src/main/java/net/minecraft/world/entity/projectile/FishingHook.java
C:/Users/Mik/Downloads/MCA/local-source/src/main/java/net/minecraft/world/entity/item/ItemEntity.java
C:/Users/Mik/Downloads/MCA/local-source/src/main/java/net/minecraft/world/entity/Mob.java
C:/Users/Mik/Downloads/MCA/local-source/src/main/java/net/minecraft/client/renderer/entity/FishingHookRenderer.java
C:/Users/Mik/Downloads/MCA/local-source/src/main/java/net/minecraft/client/renderer/entity/layers/ItemInHandLayer.java
C:/Users/Mik/Downloads/MCA/local-source/src/main/java/net/minecraft/client/model/HumanoidModel.java
common/src/main/java/net/conczin/mca/entity/VillagerEntityMCA.java
common/src/main/java/net/conczin/mca/client/render/VillagerLikeEntityMCARenderer.java
```

Confirm:

- bobber timer/particle math still maps directly to `FishingHook.catchingFish(...)`;
- biting has one source of truth (`DATA_BITING`), with no shadow task boolean/timer;
- reel velocity still matches `FishingHook.retrieve(...)`;
- `setNeverPickUp()` remains active for the entire task-owned flight and uses vanilla player/mob pickup behavior;
- inventory delivery handles `SimpleContainer.addItem(...)` remainder without duplicate/loss;
- line origin still uses posed `translateToHand(...)` + `ItemInHandLayer` transform and no body-offset fallback;
- no mixin/accessor can replace custom code more cleanly without dragging in player-only state.

- [ ] **Step 3: Run the four `java-code-review-cleanup` lenses over the frozen diff**

Review the same diff for:

1. **Reuse:** duplicate helpers/constants, copied vanilla code that can call a generic vanilla API directly, repeated GameTest fixture code.
2. **Quality:** redundant timer/boolean/cache state, parameter sprawl, unnecessary wrappers, line-render state that can remain local.
3. **Correctness:** double catches, lost catches, pickup-protection gaps, full-inventory remainder, owner death/stop ordering, rod breakage while reel is active, client/server sync, line coordinate-space mistakes.
4. **Efficiency:** per-frame allocations/searches in the line layer, repeated entity lookups, unnecessary collections, hot-path math that can be safely local/reused.

Fix only high-confidence findings inside this feature scope. If the client bobber lookup is acceptable at one active fisher scale, keep the simple implementation; do not add speculative synchronized IDs/caches.

- [ ] **Step 4: If cleanup changes Java, rerun the smallest affected checks and commit the cleanup separately**

For server/gameplay changes:

```powershell
./gradlew :common:compileJava :neoforge:compileJava
./gradlew :neoforge:runGameTestServer --no-configuration-cache
```

For renderer-only cleanup:

```powershell
./gradlew :fabric:compileJava :neoforge:compileJava
```

If cleanup made changes, commit only those fishing files:

```powershell
git add common/src/main/java/net/conczin/mca/entity/MCAFishingBobberEntity.java common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/FishingTask.java common/src/main/java/net/conczin/mca/client/render/MCAFishingBobberRenderer.java common/src/main/java/net/conczin/mca/client/render/VillagerEntityMCARenderer.java common/src/main/java/net/conczin/mca/client/render/layer/VillagerFishingLineLayer.java neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/FishingTaskGameTests.java
git commit -m "refactor: clean up villager fishing parity"
```

If there are no worthwhile findings, make no cleanup commit.

- [ ] **Step 5: Run final automated verification**

Run:

```powershell
./gradlew :common:test :fabric:compileJava :neoforge:compileJava
./gradlew :neoforge:runGameTestServer --no-configuration-cache
```

Expected: common tests pass, both loader production source sets compile, and the full NeoForge GameTest suite passes including all fishing regressions.

- [ ] **Step 6: Repeat final live client proof on both loaders**

Recheck the exact visual acceptance from Task 3 after the cleanup pass. Completion requires direct client proof of:

- rod/hand line attachment for right- and left-handed villagers;
- no neck/chest origin;
- wait/approach/bite particle sequence;
- visible bite dip and splash sound;
- protected item flying from bobber to villager;
- one catch per bite with no hidden miss;
- no periodic de-equip beyond 400 ticks;
- clean bobber/item/rod state after chore cancellation and rod loss.
- water invalidation still discards/recasts instead of leaving an orphan bobber;
- AquaCulture/current fishing loot compatibility still uses MCA's existing loot path.

- [ ] **Step 7: Confirm unrelated dirty work remains untouched**

Run:

```powershell
git status --short
git log -6 --oneline
```

Expected: the fishing commits are present; pre-existing unrelated dirty files remain dirty exactly as user work and were never staged into fishing commits.
