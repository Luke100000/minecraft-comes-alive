# Villager Fishing Bobber Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give MCA 1.21.1 fishing villagers a visible, water-targeted fishing bobber and line that survive past the inherited 400-tick chore timeout while preserving MCA's existing loot, durability, and chore semantics.

**Architecture:** Add one lightweight `ThrowableProjectile` owned by `VillagerEntityMCA` for casting/bobbing presentation and one vanilla-style client renderer for the bobber and line. `FishingTask` remains authoritative for finding water, timing catches, awarding loot, damaging rods, recasting, and cleanup. The implementation deliberately does not mix into or subclass vanilla `FishingHook`, and loader-specific code is limited to renderer registration.

**Tech Stack:** Java 21, Minecraft 1.21.1, MCA common/Fabric/NeoForge modules, vanilla `ThrowableProjectile`, Fabric `EntityRendererRegistry`, NeoForge `EntityRenderersEvent.RegisterRenderers`, NeoForge GameTest, Gradle.

**Spec:** `docs/superpowers/specs/2026-09-13-villager-fishing-bobber-design.md`

## Global Constraints

- Implement and validate 1.21.1 first; only then forward-port to 26.1.2 and 26.2.
- Do not modify `local-source`; it is reference-only.
- Do not mix into or subclass vanilla `FishingHook`.
- Do not add fake players, fishing XP, player advancements/statistics, hooked-mob behavior, or vanilla `FishingHook.retrieve(...)` semantics.
- Preserve `FishingTask.getFishingLoot(...)`, the existing 35% miss / 65% successful-catch behavior, rod durability loss, and AquaCulture-compatible loot-table context.
- Aim casts from the already-selected `targetWater` water-surface coordinates; `villager.lookAt(targetWater)` is visual only.
- Use `ThrowableProjectile` because 1.21.1 `Projectile(EntityType, Level)` is package-private while `ThrowableProjectile` exposes protected constructors and keeps generic owner spawn synchronization.
- The bobber is transient: `.noSave()`, `.noSummon()`, 0.25 x 0.25, tracking range 4, update interval 5.
- A failed cast self-discards if it hits terrain before water, remains airborne for 40 ticks, loses a valid fishing owner/rod/chore, or moves farther than 32 blocks from its owner.
- Fishing must not stop solely because `AbstractChoreTask` supplied a 400-tick duration; override only `FishingTask`, not all chores.
- Real task stop must discard the bobber, reset target/timer state, and clear the temporary held rod.
- Preserve unrelated dirty worktree changes. Stage and commit only files belonging to the fishing feature in each task.

---

## File Map

### Create

- `common/src/main/java/net/conczin/mca/entity/MCAFishingBobberEntity.java`
  - transient villager-owned projectile
  - exact-water cast targeting
  - flying-to-bobbing transition
  - owner/range/failed-cast cleanup
- `common/src/main/java/net/conczin/mca/client/render/MCAFishingBobberRenderer.java`
  - vanilla fishing-hook billboard
  - curved fishing line anchored to the villager's physical main arm
- `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/FishingTaskGameTests.java`
  - server-side entity/lifecycle regression coverage

### Modify

- `common/src/main/java/net/conczin/mca/registry/EntitiesMCA.java`
  - register `FISHING_BOBBER`
- `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/FishingTask.java`
  - replace `hasCastRod` with bobber state
  - cast at `targetWater`
  - prevent 400-tick timeout
  - reel/recast and cleanup
- `fabric/src/main/java/net/conczin/mca/fabric/MCAFabricClient.java`
  - register `MCAFishingBobberRenderer`
- `neoforge/src/main/java/net/conczin/mca/neoforge/ClientNeoForge.java`
  - register `MCAFishingBobberRenderer`

### Explicitly unchanged

- `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/AbstractChoreTask.java`
- vanilla classes under `C:/Users/Mik/Downloads/MCA/local-source`
- fishing loot tables / AquaCulture compatibility code
- other chore equipment handling

---

### Task 1: Add the transient villager-owned bobber and prove it reaches water

**Files:**
- Create: `common/src/main/java/net/conczin/mca/entity/MCAFishingBobberEntity.java`
- Modify: `common/src/main/java/net/conczin/mca/registry/EntitiesMCA.java`
- Create: `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/FishingTaskGameTests.java`

**Interfaces:**
- Produces: `EntitiesMCA.FISHING_BOBBER`
- Produces: `MCAFishingBobberEntity.cast(ServerLevel, VillagerEntityMCA, BlockPos)`
- Produces: `MCAFishingBobberEntity.getVillagerOwner()`
- Produces: `MCAFishingBobberEntity.isBobbing()`
- Consumed later by: `FishingTask`, `MCAFishingBobberRenderer`

- [ ] **Step 1: Write the failing GameTest for an exact-water cast**

Create `FishingTaskGameTests.java` with one initial test that prepares a short stone bank and a 3 x 3 source-water pool, spawns an MCA villager roughly two blocks from the nearest source block, gives it a fishing rod, assigns `Chore.FISH`, and asks the bobber factory to cast at that exact block:

```java
package net.conczin.mca.entity.ai.brain.tasks.chore;

import net.conczin.mca.entity.MCAFishingBobberEntity;
import net.conczin.mca.entity.VillagerEntityMCA;
import net.conczin.mca.entity.VillagerFactory;
import net.conczin.mca.entity.ai.Chore;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("minecraft")
@PrefixGameTestTemplate(false)
public final class FishingTaskGameTests {
    private FishingTaskGameTests() {
    }

    @GameTest(batch = "mca_fishing_bobber", templateNamespace = "minecraft", template = "bastion/blocks/air", timeoutTicks = 80)
    public static void bobberCastAtSelectedWaterStartsBobbing(GameTestHelper helper) {
        BlockPos villagerPos = helper.absolutePos(new BlockPos(3, 2, 3));
        BlockPos water = villagerPos.east(2);
        prepareWater(helper, water);

        VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
        MCAFishingBobberEntity bobber = MCAFishingBobberEntity.cast(helper.getLevel(), villager, water);

        helper.assertTrue(!bobber.isRemoved(), "new fishing bobber was removed immediately");
        helper.succeedWhen(() -> helper.assertTrue(
                bobber.isBobbing(),
                "bobber never reached the selected water block"
        ));
    }

    private static VillagerEntityMCA spawnFisher(GameTestHelper helper, BlockPos pos) {
        VillagerEntityMCA villager = VillagerFactory.newVillager(helper.getLevel())
                .withAge(0)
                .withPosition(Vec3.atBottomCenterOf(pos))
                .withName("Fishing Probe")
                .spawn(MobSpawnType.STRUCTURE);
        villager.refreshBrain(helper.getLevel());
        villager.getInventory().addItem(new ItemStack(Items.FISHING_ROD));
        villager.setItemInHand(villager.getDominantHand(), new ItemStack(Items.FISHING_ROD));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        villager.getVillagerBrain().assignJob(Chore.FISH, player);
        return villager;
    }

    private static void prepareWater(GameTestHelper helper, BlockPos center) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                BlockPos water = center.offset(x, 0, z);
                helper.getLevel().setBlock(water.below(), Blocks.STONE.defaultBlockState(), 3);
                helper.getLevel().setBlock(water, Blocks.WATER.defaultBlockState(), 3);
                helper.getLevel().setBlock(water.above(), Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }
}
```

- [ ] **Step 2: Run compilation and verify RED**

Run:

```powershell
./gradlew :neoforge:compileJava
```

Expected: compilation fails because `MCAFishingBobberEntity` and its `cast(...)` / `isBobbing()` API do not exist yet.

- [ ] **Step 3: Register the MCA bobber entity type**

In `EntitiesMCA.java`, import `MCAFishingBobberEntity` and add beside `CRIB`:

```java
EntityType<MCAFishingBobberEntity> FISHING_BOBBER = register("fishing_bobber", EntityType.Builder
        .<MCAFishingBobberEntity>of(MCAFishingBobberEntity::new, MobCategory.MISC)
        .noSave()
        .noSummon()
        .sized(0.25F, 0.25F)
        .clientTrackingRange(4)
        .updateInterval(5)
);
```

Do not add attributes; this is not a living entity.

- [ ] **Step 4: Implement the minimal bobber entity**

Create `MCAFishingBobberEntity` extending `ThrowableProjectile` with these fields and public API:

```java
public final class MCAFishingBobberEntity extends ThrowableProjectile {
    private static final int MAX_FLYING_TICKS = 40;
    private static final double MAX_OWNER_DISTANCE_SQR = 32.0 * 32.0;

    private boolean bobbing;
    private int flyingTicks;

    public MCAFishingBobberEntity(EntityType<? extends MCAFishingBobberEntity> type, Level level) {
        super(type, level);
        noCulling = true;
    }

    public static MCAFishingBobberEntity cast(ServerLevel world, VillagerEntityMCA owner, BlockPos targetWater) {
        MCAFishingBobberEntity bobber = new MCAFishingBobberEntity(EntitiesMCA.FISHING_BOBBER, world);
        bobber.setOwner(owner);
        bobber.launchAt(owner, targetWater);
        world.addFreshEntity(bobber);
        return bobber;
    }

    @Nullable
    public VillagerEntityMCA getVillagerOwner() {
        return getOwner() instanceof VillagerEntityMCA villager ? villager : null;
    }

    public boolean isBobbing() {
        return bobbing && !isRemoved();
    }
}
```

Also implement an empty `defineSynchedData(...)`; the bobber needs no custom synchronized payload because inherited projectile spawning synchronizes the owner and normal entity movement synchronization supplies position.

- [ ] **Step 5: Aim the cast at the selected water surface**

Implement `launchAt(...)` using `targetWater` as the authority, not owner pitch:

```java
private void launchAt(VillagerEntityMCA owner, BlockPos targetWater) {
    int handSide = owner.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
    float bodyYaw = owner.yBodyRot * Mth.DEG_TO_RAD;
    double sin = Mth.sin(bodyYaw);
    double cos = Mth.cos(bodyYaw);

    Vec3 origin = owner.getEyePosition()
            .add(-cos * handSide * 0.20 - sin * 0.20,
                    -0.35,
                    -sin * handSide * 0.20 + cos * 0.20);

    FluidState fluid = level().getFluidState(targetWater);
    double surfaceY = targetWater.getY() + fluid.getHeight(level(), targetWater);
    Vec3 destination = new Vec3(
            targetWater.getX() + 0.5,
            surfaceY,
            targetWater.getZ() + 0.5
    );
    Vec3 direction = destination.subtract(origin);
    double horizontal = direction.horizontalDistance();

    setPos(origin.x, origin.y, origin.z);
    shoot(direction.x, direction.y + horizontal * 0.25, direction.z, 0.6F, 0.5F);
}
```

Keep these values as the first implementation. Only tune them if live 1.21.1 verification shows the bobber visibly overshooting/undershooting ordinary two-block casts.

- [ ] **Step 6: Implement flying, bobbing, and self-cleanup**

Override `tick()` so server validity checks happen before movement, then use `ThrowableProjectile.tick()` for the actual cast. Keep the behavior small:

```java
@Override
public void tick() {
    VillagerEntityMCA owner = getVillagerOwner();
    if (!level().isClientSide && !canRemain(owner)) {
        discard();
        return;
    }

    super.tick();
    if (isRemoved()) {
        return;
    }

    FluidState fluid = level().getFluidState(blockPosition());
    if (fluid.is(FluidTags.WATER)) {
        bobbing = true;
        flyingTicks = 0;
        stabilizeOnWater(fluid);
        return;
    }

    if (!bobbing && (++flyingTicks > MAX_FLYING_TICKS || onGround() || horizontalCollision)) {
        discard();
    }
}

private boolean canRemain(@Nullable VillagerEntityMCA owner) {
    return owner != null
            && owner.isAlive()
            && owner.getVillagerBrain().getCurrentJob() == Chore.FISH
            && owner.getItemInHand(owner.getDominantHand()).getItem() instanceof FishingRodItem
            && distanceToSqr(owner) <= MAX_OWNER_DISTANCE_SQR;
}

private void stabilizeOnWater(FluidState fluid) {
    BlockPos pos = blockPosition();
    double surfaceY = pos.getY() + fluid.getHeight(level(), pos);
    Vec3 motion = getDeltaMovement();
    double offset = getY() - surfaceY;
    setDeltaMovement(motion.x * 0.9, motion.y - offset * 0.2, motion.z * 0.9);
}
```

If testing shows `ThrowableProjectile`'s collision callback leaves a terrain-hit bobber alive for one extra tick, override `onHitBlock(...)` narrowly to `discard()` when the hit block is not water. Do not add vanilla hooking behavior.

- [ ] **Step 7: Run the focused GameTest server and verify GREEN**

Run:

```powershell
./gradlew :neoforge:runGameTestServer
```

Expected: the new `mca_fishing_bobber` test passes, and existing GameTests remain green.

- [ ] **Step 8: Commit only the bobber domain seam and its first test**

```powershell
git add common/src/main/java/net/conczin/mca/entity/MCAFishingBobberEntity.java `
        common/src/main/java/net/conczin/mca/registry/EntitiesMCA.java `
        neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/FishingTaskGameTests.java
git diff --cached --name-only
git commit -m "feat: add villager fishing bobber"
```

Before committing, confirm the staged list contains exactly those three paths.

---

### Task 2: Integrate casting, timeout prevention, reel/recast, and cleanup into `FishingTask`

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/FishingTask.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/FishingTaskGameTests.java`

**Interfaces:**
- Consumes: `MCAFishingBobberEntity.cast(ServerLevel, VillagerEntityMCA, BlockPos)`
- Consumes: `MCAFishingBobberEntity.isBobbing()`
- Produces: a fishing task that remains `Behavior.Status.RUNNING` after 400 ticks while `Chore.FISH` remains valid
- Produces: exactly one active bobber per running fishing task

- [ ] **Step 1: Add failing lifecycle tests before changing `FishingTask`**

Extend `FishingTaskGameTests` with three focused tests.

First, prove the inherited 400-tick stop is a bug:

```java
@GameTest(batch = "mca_fishing_lifecycle", templateNamespace = "minecraft", template = "bastion/blocks/air")
public static void fishingDoesNotStopAtFourHundredTicks(GameTestHelper helper) {
    BlockPos villagerPos = helper.absolutePos(new BlockPos(3, 2, 3));
    prepareWater(helper, villagerPos.east(2));
    VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
    villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

    FishingTask task = new FishingTask();
    long start = helper.getLevel().getGameTime();
    helper.assertTrue(task.tryStart(helper.getLevel(), villager, start), "fishing task did not start");
    task.tickOrStop(helper.getLevel(), villager, start + 401L);

    helper.assertTrue(task.getStatus() == Behavior.Status.RUNNING, "fishing task stopped at the inherited 400-tick boundary");
    helper.assertTrue(villager.getMainHandItem().is(Items.FISHING_ROD), "fishing timeout cleared the visible rod");
    helper.succeed();
}
```

Second, prove repeated task ticks cannot create duplicate bobbers:

```java
@GameTest(batch = "mca_fishing_lifecycle", templateNamespace = "minecraft", template = "bastion/blocks/air")
public static void repeatedFishingTicksKeepOneBobber(GameTestHelper helper) {
    BlockPos villagerPos = helper.absolutePos(new BlockPos(3, 2, 3));
    prepareWater(helper, villagerPos.east(2));
    VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
    villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

    FishingTask task = new FishingTask();
    long time = helper.getLevel().getGameTime();
    task.start(helper.getLevel(), villager, time);
    task.tick(helper.getLevel(), villager, time);
    task.tick(helper.getLevel(), villager, time + 1);
    task.tick(helper.getLevel(), villager, time + 2);

    long bobbers = helper.getLevel()
            .getEntitiesOfClass(MCAFishingBobberEntity.class, villager.getBoundingBox().inflate(32.0D))
            .stream()
            .filter(entity -> !entity.isRemoved())
            .count();
    helper.assertTrue(bobbers == 1L, "fishing task created " + bobbers + " active bobbers");
    helper.succeed();
}
```

Third, prove real stop cleans both the bobber and rod:

```java
@GameTest(batch = "mca_fishing_lifecycle", templateNamespace = "minecraft", template = "bastion/blocks/air")
public static void stoppingFishingClearsBobberAndRod(GameTestHelper helper) {
    BlockPos villagerPos = helper.absolutePos(new BlockPos(3, 2, 3));
    prepareWater(helper, villagerPos.east(2));
    VillagerEntityMCA villager = spawnFisher(helper, villagerPos);
    villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);

    FishingTask task = new FishingTask();
    long time = helper.getLevel().getGameTime();
    task.start(helper.getLevel(), villager, time);
    task.tick(helper.getLevel(), villager, time);
    task.doStop(helper.getLevel(), villager, time + 1);

    helper.assertTrue(villager.getMainHandItem().isEmpty(), "stopped fishing task left the chore rod equipped");
    helper.assertTrue(
            helper.getLevel()
                    .getEntitiesOfClass(MCAFishingBobberEntity.class, villager.getBoundingBox().inflate(32.0D))
                    .stream()
                    .noneMatch(entity -> !entity.isRemoved()),
            "stopped fishing task left an active bobber"
    );
    helper.succeed();
}
```

Add imports for `Behavior`, `MemoryModuleType`, and `MCAFishingBobberEntity`.

- [ ] **Step 2: Run GameTests and verify RED against the old fishing lifecycle**

Run:

```powershell
./gradlew :neoforge:runGameTestServer
```

Expected before the task change: the 400-tick test fails because `FishingTask` is stopped by the inherited duration; bobber integration tests cannot pass until `FishingTask` owns a bobber.

- [ ] **Step 3: Replace boolean cast state with the bobber reference**

In `FishingTask.java`, change:

```java
private BlockPos targetWater;
private boolean hasCastRod;
private int ticks;
```

to:

```java
private BlockPos targetWater;
private MCAFishingBobberEntity bobber;
private int ticks;
```

Remove the now-unused `EquipmentSlot` import if it remains unused.

- [ ] **Step 4: Prevent only fishing from timing out at 400 ticks**

Add:

```java
@Override
protected boolean timedOut(long time) {
    return false;
}
```

Do not alter `AbstractChoreTask`.

- [ ] **Step 5: Validate the chosen water before using or recasting it**

Before the near-water branch, add a narrow stale-target check:

```java
if (targetWater != null && !world.getBlockState(targetWater).is(Blocks.WATER)) {
    discardBobber();
    targetWater = null;
    ticks = 0;
}
```

Then allow the existing `targetWater == null` search branch to find a replacement on the following tick.

- [ ] **Step 6: Cast one bobber when the villager reaches water**

Replace the `hasCastRod` block with:

```java
if (bobber == null || bobber.isRemoved()) {
    villager.swing(villager.getDominantHand());
    bobber = MCAFishingBobberEntity.cast(world, villager, targetWater);
    ticks = 0;
}

if (!bobber.isBobbing()) {
    return;
}

ticks++;
```

The timer starts only once the cast has actually reached water. If a failed cast self-discards, the next task tick creates a fresh cast rather than accumulating a second live bobber.

- [ ] **Step 7: Reel only successful catches and recast on the next tick**

Keep the current random threshold/catch chance expression. Inside the successful catch branch, reel first:

```java
if (ticks >= villager.level().random.nextInt(200) + 200) {
    if (villager.level().random.nextFloat() >= 0.35F) {
        ItemStack stack = getFishingLoot(world, villager);

        villager.swing(villager.getDominantHand());
        discardBobber();
        villager.getInventory().addItem(stack);
        villager.getItemInHand(villager.getDominantHand())
                .hurtAndBreak(1, villager, villager.getDominantSlot());
    }
    ticks = 0;
}
```

On the existing 35% miss case, do not discard/recast: just reset the timer as before.

- [ ] **Step 8: Make stop cleanup idempotent**

Add:

```java
private void discardBobber() {
    if (bobber != null && !bobber.isRemoved()) {
        bobber.discard();
    }
    bobber = null;
}
```

Replace `stop(...)` with:

```java
@Override
protected void stop(ServerLevel world, VillagerEntityMCA villager, long time) {
    discardBobber();
    targetWater = null;
    ticks = 0;

    ItemStack stack = villager.getItemInHand(villager.getDominantHand());
    if (!stack.isEmpty()) {
        villager.setItemInHand(villager.getDominantHand(), ItemStack.EMPTY);
    }
}
```

Do not refactor other chores or inventory ownership in this task.

- [ ] **Step 9: Add rod-loss cleanup coverage**

Add one GameTest which starts/ticks fishing, removes every fishing rod from the held hand/inventory, ticks again, and asserts no active bobber remains after the chore abandons. Use the existing `chore.fishing.norod` path; do not expose private fields solely for testing.

- [ ] **Step 10: Run the full NeoForge GameTest server and verify GREEN**

Run:

```powershell
./gradlew :neoforge:runGameTestServer
```

Expected: all fishing tests and pre-existing GameTests pass.

- [ ] **Step 11: Commit only fishing task + fishing tests**

```powershell
git add common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/FishingTask.java `
        neoforge/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/FishingTaskGameTests.java
git diff --cached --name-only
git commit -m "fix: keep villager fishing cast active"
```

---

### Task 3: Render the vanilla-style bobber and fishing line on both loaders

**Files:**
- Create: `common/src/main/java/net/conczin/mca/client/render/MCAFishingBobberRenderer.java`
- Modify: `fabric/src/main/java/net/conczin/mca/fabric/MCAFabricClient.java`
- Modify: `neoforge/src/main/java/net/conczin/mca/neoforge/ClientNeoForge.java`

**Interfaces:**
- Consumes: `MCAFishingBobberEntity.getVillagerOwner()`
- Produces: client-visible vanilla fishing hook texture and 16-segment curved line anchored to the MCA villager's main arm

- [ ] **Step 1: Implement the renderer from the 1.21.1 vanilla renderer, omitting player-only code**

Create `MCAFishingBobberRenderer extends EntityRenderer<MCAFishingBobberEntity>`.

Use:

```java
private static final ResourceLocation TEXTURE_LOCATION =
        ResourceLocation.withDefaultNamespace("textures/entity/fishing_hook.png");
private static final RenderType RENDER_TYPE = RenderType.entityCutout(TEXTURE_LOCATION);
```

In `render(...)`:

1. obtain `VillagerEntityMCA owner = bobber.getVillagerOwner()`;
2. return without rendering if owner is null;
3. render the same 0.5-scale four-vertex camera-facing hook billboard as vanilla 1.21.1;
4. compute the villager hand position;
5. subtract `bobber.getPosition(partialTicks).add(0.0, 0.25, 0.0)`;
6. render the same 16-segment curved black line using `RenderType.lineStrip()`;
7. call `super.render(...)`.

Copy the vanilla `vertex(...)`, `fraction(...)`, and `stringVertex(...)` geometry helpers directly in behavior, adapting naming/style to MCA. Do not copy the first-person camera branch.

- [ ] **Step 2: Anchor the line to the MCA villager's physical main arm**

Implement the third-person hand calculation as:

```java
private Vec3 getVillagerHandPos(VillagerEntityMCA owner, float partialTicks) {
    int side = owner.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
    float bodyYaw = Mth.lerp(partialTicks, owner.yBodyRotO, owner.yBodyRot) * Mth.DEG_TO_RAD;
    double sin = Mth.sin(bodyYaw);
    double cos = Mth.cos(bodyYaw);
    float scale = owner.getScale();
    double sideOffset = side * 0.35 * scale;
    double forwardOffset = 0.8 * scale;
    float crouchOffset = owner.isCrouching() ? -0.1875F : 0.0F;

    return owner.getEyePosition(partialTicks).add(
            -cos * sideOffset - sin * forwardOffset,
            crouchOffset - 0.45 * scale,
            -sin * sideOffset + cos * forwardOffset
    );
}
```

This intentionally uses `getMainArm()`, not `getDominantHand()`, because MCA's left-handed trait controls the physical arm.

- [ ] **Step 3: Register the renderer on Fabric**

In `MCAFabricClient.onInitializeClient()`, alongside `CRIB` and `GRIM_REAPER`, add:

```java
EntityRendererRegistry.register(EntitiesMCA.FISHING_BOBBER, MCAFishingBobberRenderer::new);
```

The existing wildcard `net.conczin.mca.client.render.*` import should already cover the renderer.

- [ ] **Step 4: Register the renderer on NeoForge**

In `ClientNeoForge.onRegisterRenderers(...)`, add:

```java
event.registerEntityRenderer(EntitiesMCA.FISHING_BOBBER, MCAFishingBobberRenderer::new);
```

- [ ] **Step 5: Compile both loader clients**

Run:

```powershell
./gradlew :fabric:compileJava :neoforge:compileJava
```

Expected: both loader modules compile with the shared renderer.

- [ ] **Step 6: Commit renderer + registrations only**

```powershell
git add common/src/main/java/net/conczin/mca/client/render/MCAFishingBobberRenderer.java `
        fabric/src/main/java/net/conczin/mca/fabric/MCAFabricClient.java `
        neoforge/src/main/java/net/conczin/mca/neoforge/ClientNeoForge.java
git diff --cached --name-only
git commit -m "feat: render villager fishing line"
```

Because `fabric/src/main/java/net/conczin/mca/fabric/MCAFabric.java` is already dirty for unrelated work, do not stage it accidentally; this task touches `MCAFabricClient.java`, not `MCAFabric.java`.

---

### Task 4: Verify server lifecycle, build artifacts, and live 1.21.1 behavior

**Files:**
- No new production files expected unless verification finds a defect directly attributable to this feature.

**Interfaces:**
- Validates all interfaces from Tasks 1-3 together.

- [ ] **Step 1: Run common unit tests**

```powershell
./gradlew :common:test
```

Expected: existing JUnit tests pass.

- [ ] **Step 2: Run the NeoForge GameTest server**

```powershell
./gradlew :neoforge:runGameTestServer
```

Expected: all GameTests pass, including `mca_fishing_bobber` and `mca_fishing_lifecycle`.

- [ ] **Step 3: Build both loaders**

```powershell
./gradlew :fabric:build :neoforge:build
```

Expected: both distributions build successfully.

- [ ] **Step 4: Launch Fabric 1.21.1 and verify the full visual loop**

Run:

```powershell
./gradlew :fabric:runClient
```

In a test world:

1. assign `FISH` to a villager with a fishing rod;
2. watch it choose and approach nearby water;
3. confirm the villager faces the selected water;
4. confirm the bobber launches in a visible arc and lands in that water rather than following stale pitch;
5. confirm the hook texture is visible;
6. confirm the black line runs from the bobber to the villager's physical rod arm;
7. test a naturally right-handed and left-handed MCA villager;
8. leave fishing active for at least 500 ticks and confirm rod/bobber do not disappear at 400 ticks;
9. wait for a successful catch and confirm swing -> bobber removal -> loot/durability -> next cast;
10. confirm a 35% miss resets the wait without an artificial reel animation;
11. remove the target water and confirm stale bobber cleanup/reselection;
12. remove/break the last rod and confirm the chore abandons without an orphan bobber.

- [ ] **Step 5: Launch NeoForge 1.21.1 and repeat the loader-sensitive checks**

Run:

```powershell
./gradlew :neoforge:runClient
```

Recheck renderer registration, bobber visibility, line visibility, catch/recast, and >400-tick persistence. The gameplay logic is shared, so there is no need to repeat every server-only assertion manually.

- [ ] **Step 6: Recheck the original compatibility scenario**

With AquaCulture (or the exact mod setup that motivated the July fishing compatibility change), confirm that catches still come through `FishingTask.getFishingLoot(...)` with the held fishing rod as `LootContextParams.TOOL`. Do not route catches through vanilla `FishingHook.retrieve(...)` to fix any visual issue.

- [ ] **Step 7: Inspect the final fishing-only diff**

Run:

```powershell
git log --oneline --decorate -6
git show --stat --oneline HEAD~2..HEAD
git status --short
```

Confirm unrelated dirty files remain untouched and unstaged.

If live verification required tuning only the bobber launch offsets/speed or renderer hand offset, make the smallest adjustment, rerun the focused loader/client check, and commit it separately as:

```powershell
git commit -m "fix: tune villager fishing cast visuals"
```

Do not use verification as an excuse for unrelated chore refactors.

---

### Task 5: Forward-port the verified feature to 26.1.2

**Files:**
- Port the same six production paths into the 26.1.2 checkout.
- Port `FishingTaskGameTests.java` if that branch has the same NeoForge GameTest setup; otherwise preserve the same server assertions in the nearest existing GameTest location.

**Interfaces:**
- Consumes: the completed 1.21.1 commits from Tasks 1-3 plus any visual-tuning commit from Task 4.
- Produces: behaviorally identical 26.1.2 fishing.

- [ ] **Step 1: Merge/cherry-pick the verified 1.21.1 feature commits into 26.1.2**

Use the actual commit IDs produced during Tasks 1-4. Resolve only mapping/API differences; do not redesign the feature.

- [ ] **Step 2: Reconcile mapping differences while preserving these invariants**

- custom MCA bobber, not vanilla `FishingHook`;
- generic villager owner sync;
- exact `targetWater` surface aiming;
- one bobber per task;
- fishing-specific timeout suppression;
- same existing MCA loot code;
- idempotent stop cleanup;
- vanilla-style texture/line anchored to `getMainArm()`.

- [ ] **Step 3: Run 26.1.2 tests/build and a short live client check**

Use that checkout's Gradle tasks equivalent to:

```powershell
./gradlew :common:test :fabric:build :neoforge:build
```

Then live-check one loader for cast -> bob -> catch -> recast and >400 ticks, plus the second loader for renderer registration.

- [ ] **Step 4: Commit only mapping/port changes**

Use a port-specific commit such as:

```powershell
git commit -m "feat: port villager fishing bobber"
```

---

### Task 6: Forward-port the verified feature to 26.2 and adapt only the renderer API

**Files:**
- Port `MCAFishingBobberEntity`, `EntitiesMCA`, and `FishingTask` conceptually unchanged.
- Port Fabric/NeoForge renderer registrations using the 26.2 loader APIs.
- Rewrite only `MCAFishingBobberRenderer` to the 26.2 render-state API.

**Interfaces:**
- Consumes: verified 26.1.2 behavior.
- Produces: behaviorally identical 26.2 fishing with the newer renderer infrastructure.

- [ ] **Step 1: Merge/cherry-pick the feature into the 26.2 checkout**

Keep the generic projectile-owner design; 26.2 still supports generic owner IDs in projectile spawn packets.

- [ ] **Step 2: Use the 26.2 shared chore cleanup helper**

In 26.2 `FishingTask.stop(...)`, use the branch's existing:

```java
clearChoreItem(villager);
```

instead of carrying forward 1.21.1's direct hand clear.

- [ ] **Step 3: Port the renderer to 26.2 render state**

Follow 26.2 vanilla `FishingHookRenderer` geometry but replace player extraction with the MCA villager owner. Preserve:

- vanilla 26.2 fishing-hook texture;
- hook billboard;
- 16-segment line;
- hand-origin offset from `VillagerEntityMCA.getMainArm()`;
- no first-person branch.

Do not mix into 26.2 vanilla `FishingHookRenderer`.

- [ ] **Step 4: Run 26.2 tests/build and live verification**

Run the branch-equivalent common tests, GameTests, Fabric build, and NeoForge build, then verify visible casting/line/recast and >400-tick persistence in client.

- [ ] **Step 5: Commit only the 26.2 port**

```powershell
git commit -m "feat: port villager fishing bobber to 26.2"
```

---

## Final Acceptance Checklist

- [ ] 1.21.1 villager casts a real MCA bobber entity at the exact selected water surface.
- [ ] A failed cast self-recovers; no permanent terrain-stuck hook.
- [ ] Bobber reaches water and visibly bobs rather than sinking away.
- [ ] Vanilla hook texture renders on Fabric and NeoForge.
- [ ] Fishing line attaches to the correct physical villager arm, including left-handed villagers.
- [ ] Fishing remains active past 400 ticks with no periodic rod/hook disappearance.
- [ ] One villager never accumulates multiple active bobbers.
- [ ] Successful catch visibly reels, awards the existing MCA loot, damages the rod, and recasts.
- [ ] Existing 35% miss behavior does not fake a reel/catch.
- [ ] Missing/broken last rod abandons fishing and leaves no orphan bobber.
- [ ] Changing/cancelling the chore clears bobber, timer/target state, and temporary held rod.
- [ ] Removing target water causes cleanup and reselection.
- [ ] Existing AquaCulture-compatible loot behavior remains intact.
- [ ] No `FishingHook`/`FishingHookRenderer` mixins were added.
- [ ] Unrelated dirty worktree changes were not staged or committed.
- [ ] 26.1.2 and 26.2 ports preserve behavior and only adapt version APIs.
