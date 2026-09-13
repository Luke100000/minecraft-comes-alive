# Villager Fishing Bobber Design

## Goal

Make the MCA fishing chore visibly behave like player fishing without forcing vanilla `FishingHook` to support non-player owners.

The first implementation targets MCA 1.21.1. The design must remain simple to merge forward into 26.1.2 and then 26.2.

Success means a fishing villager:

- visibly holds a fishing rod while the fishing chore is active;
- casts a visible bobber toward the selected water;
- has a visible fishing line from the bobber to the villager's rod hand;
- keeps the rod and bobber active across the current 400-tick chore behavior boundary;
- reels in the bobber when MCA awards fishing loot;
- casts again for the next fishing cycle;
- removes the bobber and clears the temporary held rod when the fishing chore actually ends;
- preserves the existing MCA fishing loot and mod-compatibility behavior.

## Current behavior

`FishingTask` currently simulates fishing without creating a fishing entity.

Once the villager reaches water it:

1. equips a fishing rod from the villager inventory;
2. swings once and sets `hasCastRod`;
3. waits for the existing MCA timer;
4. generates loot directly from `BuiltInLootTables.FISHING` using the held rod as `LootContextParams.TOOL`;
5. adds the resulting item to the villager inventory;
6. damages the rod;
7. repeats the timer.

There is no fishing bobber and therefore no fishing line.

There is also a separate lifecycle problem. `AbstractChoreTask` constructs every chore behavior with a 400-tick timeout. Vanilla `Behavior` stops the task when that duration expires. `FishingTask.stop()` clears the held item, so a long-running fishing chore periodically drops the visible rod and then equips it again when the behavior restarts.

## Why not mix vanilla `FishingHook`

Do not generalize vanilla fishing through mixins for this feature.

Minecraft 1.21.1 hard-codes `Player` across the fishing-hook lifecycle:

- `FishingHook(Player, Level, int, int)` accepts a player owner;
- `tick()` calls `getPlayerOwner()` and discards the hook when no player exists;
- `shouldStopFishing(...)` accepts `Player`;
- `retrieve(...)` assumes a `Player` and casts it to `ServerPlayer` for criteria triggers;
- owner bookkeeping writes to `Player.fishing`;
- client packet recreation rejects a non-player owner;
- `FishingHookRenderer` obtains a `Player` from the hook before rendering;
- line positioning and first-person behavior are player-specific.

A mixin solution would therefore need coordinated server and client rewrites across construction, ticking, ownership validation, retrieval, owner bookkeeping, packet recreation, and rendering. That is more invasive and more version-fragile than a small MCA-owned visual bobber.

Subclassing `FishingHook` is also not useful because the important fishing state and helper methods are private and the inherited lifecycle still assumes a player owner.

## Design

Add a small MCA projectile entity representing the villager's fishing bobber. It is responsible only for the visible cast, basic bobber movement, owner synchronization, and cleanup. `FishingTask` remains responsible for deciding when a catch happens and what loot is awarded.

This deliberately separates:

- **presentation and cast state**: `MCAFishingBobberEntity`;
- **client rendering**: `MCAFishingBobberRenderer`;
- **chore timing, loot, rod durability, and job lifecycle**: `FishingTask`.

Do not copy vanilla's complete fishing simulation, lure timers, open-water checks, advancements, XP, statistics, hooked-entity behavior, or retrieval logic. MCA already has working chore-specific fishing semantics and AquaCulture-compatible loot generation.

## `MCAFishingBobberEntity`

Create:

`common/src/main/java/net/conczin/mca/entity/MCAFishingBobberEntity.java`

The entity extends `ThrowableProjectile` rather than `FishingHook`.

In the 1.21.1 mappings used by this branch, the direct `Projectile(EntityType, Level)` constructor is package-private, while `ThrowableProjectile` exposes the protected constructors MCA needs. `ThrowableProjectile` still supplies normal `Projectile` ownership, spawn-packet owner synchronization, `shoot(...)`, gravity, and basic collision/movement behavior, so no access widener or mixin is required.

### Registration

Register it in `EntitiesMCA` as `FISHING_BOBBER` using the existing generic non-living entity registration helper.

Mirror the useful vanilla fishing-bobber entity settings:

```java
EntityType.Builder.<MCAFishingBobberEntity>of(MCAFishingBobberEntity::new, MobCategory.MISC)
        .noSave()
        .noSummon()
        .sized(0.25F, 0.25F)
        .clientTrackingRange(4)
        .updateInterval(5)
```

Exact builder API spelling may differ during forward ports, but the behavior should remain equivalent.

The bobber is transient and must not survive world saves or be summonable as normal content.

### Owner

The owner is the fishing `VillagerEntityMCA`.

Use normal inherited `Projectile.setOwner(...)` ownership. In 1.21.1 `Projectile` already includes the owner entity ID in its spawn packet and resolves that owner on the client in `recreateFromPacket(...)`, so the MCA bobber does not need custom owner synchronization.

The bobber should expose a focused helper such as:

```java
@Nullable
public VillagerEntityMCA getVillagerOwner()
```

It returns the owner only when the current owner is an MCA villager.

If the owner disappears, dies, changes away from the fishing chore, or no longer has the fishing rod equipped, the server-side bobber should discard itself.

Do not store the bobber on `VillagerEntityMCA` globally. `FishingTask` owns the chore-local reference.

### Cast trajectory

The cast should look like player fishing but must aim from MCA's already-known `targetWater`, not trust the villager's current pitch/yaw to land the bobber.

`FishingTask` already chooses an exact water block before casting and only casts once the villager is within the existing `distanceToSqr(...) < 5.0D` threshold. Use that target as the authoritative destination.

When creating the bobber:

1. spawn it near the villager's eye/rod-hand side using a small vanilla-like rear/side offset;
2. compute the destination at the center of `targetWater` horizontally;
3. compute destination Y from the target block's actual fluid surface with `world.getFluidState(targetWater).getHeight(world, targetWater)`;
4. form a direction vector from the spawn position to that water-surface point;
5. add a small upward arc component so the cast visibly rises before landing instead of travelling like a straight projectile;
6. call inherited `shoot(...)` with vanilla-like speed and only small inaccuracy;
7. let `shoot(...)` derive bobber yaw/pitch from the resulting motion.

`villager.lookAt(targetWater)` remains because it makes the villager face the cast naturally, but it is presentation only. Projectile targeting comes from the exact water coordinates.

This makes the normal cast deterministic enough to land in the selected nearby water while preserving a fishing-rod-like arc.

### Bobber movement

Implement only the visual subset required by the chore:

- while flying, use `ThrowableProjectile`'s normal projectile movement/gravity and collision handling so the cast travels visibly;
- when the bobber reaches water, transition to a bobbing state;
- while bobbing, remain near the water surface with small vanilla-like vertical stabilization rather than sinking;
- do not run vanilla fish-attraction, nibble, open-water, hooked-entity, loot, XP, statistic, or advancement logic;
- if it lands on ground or collides before reaching water, discard it so `FishingTask` can replace/recast it;
- if it has not reached water within 40 ticks of being cast, discard it so a bad trajectory cannot leave the chore stuck;
- if it moves more than 32 blocks from its owner, discard it as invalid.

The implementation should copy only the minimum movement behavior needed from vanilla `FishingHook`. Keep this entity much smaller than vanilla `FishingHook`.

### Removal

The entity should be discarded when:

- `FishingTask` reels in a catch;
- the task performs a deliberate recast;
- the fishing chore stops;
- the owner becomes invalid;
- the owner moves far enough away that the current cast is no longer meaningful.

No persistent cleanup mechanism is required because the entity is `noSave()` and validates its owner while ticking.

## `MCAFishingBobberRenderer`

Create:

`common/src/main/java/net/conczin/mca/client/render/MCAFishingBobberRenderer.java`

The renderer should closely follow vanilla 1.21.1 `FishingHookRenderer`, but operate on `MCAFishingBobberEntity` and `VillagerEntityMCA`.

### Bobber texture

Use the vanilla fishing-hook texture rather than adding an MCA texture:

```text
minecraft:textures/entity/fishing_hook.png
```

Render the bobber billboard using the same scale, UVs, light handling, and cutout render type as vanilla where practical.

### Fishing line

Render the same curved segmented line used by vanilla fishing.

The line origin is the villager's rod hand, not a generic center-of-body point.

Use `VillagerEntityMCA.getMainArm()` to respect MCA's left-handed trait. MCA's `getDominantHand()` is the main interaction hand, while `getMainArm()` determines whether that hand is physically left or right.

For third-person villager rendering, adapt vanilla's third-person hand-position calculation:

- interpolate the owner's body yaw;
- offset horizontally according to `getMainArm()`;
- scale offsets by owner scale;
- account for crouching if applicable;
- originate near the hand/eye-relative position used by vanilla.

Do not copy vanilla's first-person camera branch. The bobber owner is an NPC and can never be the local first-person player.

The renderer should render only while `getVillagerOwner()` returns a valid villager.

### Loader registration

Register the renderer on both loaders:

- Fabric: `MCAFabricClient` through `EntityRendererRegistry.register(...)`;
- NeoForge: `ClientNeoForge.onRegisterRenderers(...)` through `event.registerEntityRenderer(...)`.

Keep loader-specific code limited to renderer registration. All bobber behavior remains in `common`.

## `FishingTask` lifecycle

`FishingTask` remains the single owner of fishing-chore state.

Replace the boolean-only cast state with an explicit bobber reference/state that can answer whether a cast is currently active.

Suggested state:

```java
private BlockPos targetWater;
private MCAFishingBobberEntity bobber;
private int ticks;
```

`hasCastRod` is no longer necessary when an active bobber represents the cast.

### Starting

On `start(...)`:

1. call `super.start(...)`;
2. equip the fishing rod;
3. do not cast until a valid water target is found and the villager is within the existing fishing distance.

### Approaching water

Keep the existing target-water search and navigation behavior.

When the villager is close enough:

1. stop navigation;
2. look at `targetWater`;
3. if there is no live owned bobber, swing the dominant hand and spawn one aimed directly at the selected water surface;
4. increment the existing catch timer while the cast remains active.

Do not spawn multiple bobbers for one villager.

### Catch and recast

Keep the existing MCA catch probability and loot-generation method unless testing demonstrates a pre-existing bug unrelated to the visual bobber work.

When the current timer produces a catch:

1. swing the villager's dominant hand;
2. remove the current bobber;
3. generate fishing loot through the existing `getFishingLoot(...)` path;
4. add the loot to the villager inventory;
5. damage the held fishing rod by one;
6. reset the catch timer;
7. allow the next task tick to cast a fresh bobber.

If the random catch attempt produces no loot event under the current 35% rule, retain the existing semantics: reset the timer without swinging, removing, or recasting the bobber. Only a successful catch visibly reels and recasts.

### Rod breakage

If rod durability reaches zero during a catch:

- the bobber must be removed;
- the next `equipFishingRod(...)` call may select another fishing rod from inventory;
- if no fishing rod remains, preserve the existing `chore.fishing.norod` abandonment behavior.

Do not create replacement rods or special-case durability outside the existing inventory semantics.

## Preventing periodic de-equipping

Do not change the timeout for every chore in `AbstractChoreTask` as part of this feature.

Fishing is a continuous cast/wait activity and is uniquely harmed by the inherited 400-tick behavior timeout. Override the timeout in `FishingTask` so the running fishing behavior remains active while `canStillUse(...)` remains true.

For example, use the narrowest available 1.21.1 mechanism:

```java
@Override
protected boolean timedOut(long time) {
    return false;
}
```

If the mapped method differs in a forward-port target, preserve the semantic rule rather than the exact method name: fishing must not stop solely because 400 ticks elapsed.

The chore must still stop when `canStillUse(...)` becomes false, including when `Chore.FISH` is no longer the current job or the inherited chore conditions reject continued execution.

## Task cleanup

`FishingTask.stop(...)` becomes the authoritative cleanup path.

It must:

1. discard the current bobber if present;
2. clear the bobber reference;
3. reset `targetWater`;
4. reset the catch timer;
5. clear the temporary held fishing rod when combat is not taking precedence, matching the existing chore-item cleanup semantics.

On 1.21.1, use the local equivalent of `clearChoreItem(...)` or retain the existing direct hand clear if that helper does not yet exist on the branch. Do not broaden this change into unrelated chore cleanup refactoring.

Cleanup must be idempotent so task stop, owner invalidation, and bobber self-removal can occur in either order safely.

## Inventory and equipment behavior

Keep fishing rods as villager inventory-owned equipment.

`equipFishingRod(...)` should continue to:

- accept an already-held fishing rod;
- find a `FishingRodItem` in the villager inventory otherwise;
- abandon the chore with `chore.fishing.norod` when none exists.

This feature is not an inventory-system rewrite. Do not move the rod permanently out of the villager inventory or add fishing-specific inventory slots.

The visible hand item exists only for the active fishing chore and should be cleared when that chore genuinely ends.

## Error and edge-case behavior

### No water

Preserve the existing `FAILED_COOLDOWN` behavior when no water target is found. No bobber is spawned.

### Water becomes invalid

If the selected water disappears or becomes unreachable after casting, discard the stale bobber, clear `targetWater`, and let the existing target search choose again. Do not leave a floating permanent bobber.

### Villager moves away

If another higher-priority behavior moves the villager far enough from its bobber, discard the bobber. The fishing task may recast when it regains a valid fishing position.

### Combat or panic

Existing brain/activity rules retain priority. Fishing should not keep a stale bobber alive after the chore can no longer continue.

### Chunk unload or entity removal

The bobber is non-persistent. It may disappear on unload. `FishingTask` must tolerate a removed/null bobber and recreate one when the active fishing villager resumes in a loaded area.

## Testing and verification

### Automated tests

Prefer focused tests around behavior that can be tested without a renderer:

- fishing does not time out solely at 400 ticks;
- a stopped fishing task discards its bobber and resets task state;
- a missing fishing rod still abandons the chore;
- rod breakage does not leave a bobber behind;
- one villager cannot accumulate multiple active MCA bobbers.

Do not add brittle tests for copied vanilla trajectory constants unless a regression would otherwise be hard to detect.

### In-game verification

Rendering and animation require live verification. Compilation or unit tests alone cannot prove this feature is correct.

Verify on both Fabric and NeoForge 1.21.1:

1. assign a villager the fishing chore with a rod in inventory;
2. watch it walk to water;
3. confirm the rod is visible in the correct physical hand for both right- and left-handed villagers;
4. confirm the cast bobber visibly travels toward the water;
5. confirm the fishing line connects the bobber to the villager hand;
6. leave the villager fishing for longer than 400 ticks and confirm the rod/bobber do not periodically disappear;
7. wait for a catch and confirm reel/catch/recast behavior;
8. confirm loot still enters the villager inventory and rod durability decreases;
9. remove or break the last rod and confirm the chore abandons cleanly with no orphan bobber;
10. cancel/change the chore and confirm both bobber and temporary hand equipment are cleaned up;
11. test water removal or forced villager movement and confirm stale bobbers disappear;
12. test with AquaCulture or the original compatibility scenario to ensure the existing loot-table behavior remains intact.

## Files expected to change on 1.21.1

Core implementation:

- `common/src/main/java/net/conczin/mca/entity/MCAFishingBobberEntity.java` — new transient projectile entity;
- `common/src/main/java/net/conczin/mca/client/render/MCAFishingBobberRenderer.java` — new vanilla-style NPC bobber renderer;
- `common/src/main/java/net/conczin/mca/registry/EntitiesMCA.java` — bobber entity registration;
- `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/FishingTask.java` — cast/reel lifecycle and timeout behavior;
- `fabric/src/main/java/net/conczin/mca/fabric/MCAFabricClient.java` — Fabric renderer registration;
- `neoforge/src/main/java/net/conczin/mca/neoforge/ClientNeoForge.java` — NeoForge renderer registration.

Tests may add one focused test file in the existing test structure if the behavior can be exercised cheaply.

Do not modify vanilla source copies under `local-source`; they are references only.

## Forward-port strategy

Implement and validate this on 1.21.1 first.

Then merge/cherry-pick the feature into 26.1.2 and finally 26.2, resolving only mapping/API differences.

### 26.1.2

Keep the same architecture and behavior. Expected changes should mostly be imports, mappings, entity builder signatures, and renderer API details.

Do not redesign the feature during the port unless the target vanilla API makes the 1.21.1 mechanism impossible.

### 26.2

26.2 changes Minecraft's renderer infrastructure substantially, but vanilla still hard-codes fishing hooks to player owners. Preserve the custom bobber design.

Port the renderer to the 26.2 render-state API while keeping the same visual contract:

- vanilla fishing-hook texture;
- visible bobber;
- line anchored to the MCA villager's physical rod arm;
- no first-person branch.

The 26.2 `Projectile` owner packet behavior still supports generic entity owners, so the bobber ownership model remains valid.

The 26.2 branch already has a shared `clearChoreItem(...)` helper in `AbstractChoreTask`; use it instead of duplicating direct hand cleanup when forward-porting.

## Non-goals

This change does not:

- make villagers use vanilla `FishingHook` directly;
- add mixins to vanilla fishing classes;
- implement player advancements, statistics, or fishing XP for villagers;
- make villagers hook mobs or items;
- replace MCA's existing fishing loot generation with vanilla `FishingHook.retrieve(...)`;
- rebalance catch chance or timing;
- redesign all chore timeouts;
- refactor other chore equipment handling;
- change AquaCulture compatibility behavior beyond preserving it;
- make the bobber persistent across save/load.

## Implementation principle

Use vanilla as a visual and movement reference, not as a class hierarchy to force into MCA.

The smallest maintainable solution is one lightweight MCA projectile plus one lightweight renderer, while `FishingTask` stays authoritative for fishing gameplay. That keeps the implementation readable on 1.21.1 and makes the intended merge path to 26.1.2 and 26.2 explicit.
