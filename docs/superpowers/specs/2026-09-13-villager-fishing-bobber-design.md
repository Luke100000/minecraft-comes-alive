# Villager Fishing Player-Parity Design

## Goal

Make MCA 1.21.1 fishing villagers visibly and mechanically fish like a player at the bobber level, while keeping MCA ownership, water targeting, loot-table integration, chore lifecycle, and forward-portability.

This design supersedes the earlier presentation-only version of this spec. The existing implementation plan at `docs/superpowers/plans/2026-09-13-villager-fishing-bobber.md` is stale after this design change and must be rewritten after this spec is approved.

Success means a fishing villager:

- visibly holds its fishing rod for the full active chore;
- casts a real MCA-owned bobber toward MCA's selected water target;
- has a fishing line that visually attaches to the rendered rod/hand instead of the neck or upper torso;
- shows the vanilla-style waiting splash, approaching-fish trail, bubbles, bite splash/sound, and bobber dip;
- reels immediately when a real bite occurs;
- produces a catch on every bite that the villager reels;
- visibly launches the caught loot from the bobber toward the villager;
- delivers that real loot into MCA inventory without duplicating it;
- damages the rod once per catch and recasts for the next cycle;
- cleans up bobber, reel item, and temporary held equipment when fishing genuinely ends;
- remains implementable on 1.21.1 first, then mergeable to 26.1.2 and 26.2.

## Source-grounded baseline

Minecraft 1.21.1 source under `C:/Users/Mik/Downloads/MCA/local-source/src` is the design oracle for player-visible fishing behavior.

Relevant vanilla owners:

- `net.minecraft.world.entity.projectile.FishingHook`
- `net.minecraft.client.renderer.entity.FishingHookRenderer`
- `net.minecraft.client.renderer.entity.layers.ItemInHandLayer`
- `net.minecraft.client.model.HumanoidModel`

Vanilla `FishingHook` has a broad outer lifecycle (`FLYING`, `HOOKED_IN_ENTITY`, `BOBBING`). The player-visible fish cycle is not a separate enum state machine. While `BOBBING`, vanilla uses:

- `timeUntilLured`
- `timeUntilHooked`
- `nibble`
- `fishAngle`
- synchronized `biting`

`catchingFish(...)` owns the approach particles, bubbles, splash, splash sound, bite timing, and transition into the nibble window. The synchronized biting update gives the bobber its sharp downward motion.

Vanilla retrieval creates an `ItemEntity` at the hook and gives it velocity toward the fisher. Player XP, statistics, criteria, hooked-entity pulling, and `Player.fishing` bookkeeping are separate concerns and are not required for MCA villagers.

Vanilla `FishingHookRenderer` computes a player-specific line origin from player eye/body offsets. That formula is the cause of the current MCA visual bug: the MCA renderer copied the player approximation even though MCA renders its rod through `HumanoidMobRenderer` -> `ItemInHandLayer` -> `HumanoidModel.translateToHand(...)` plus MCA's own entity scaling. The screenshot shows the resulting line originating around the villager's upper torso rather than intersecting the held fishing rod.

## Architectural choice

Keep the existing MCA-owned `MCAFishingBobberEntity`. Do not mix into or subclass vanilla `FishingHook`.

Vanilla `FishingHook` is still player-owned throughout construction, owner validation, packet recreation, retrieval, criteria, stats, and renderer assumptions. Generalizing it for villagers would require coordinated mixins across multiple server and client responsibilities. The custom MCA bobber remains the smaller and more portable boundary.

The design changes ownership inside MCA:

- `MCAFishingBobberEntity` owns cast movement plus the vanilla-shaped bobbing/lure/bite cycle.
- `FishingTask` owns water selection, chore orchestration, loot-table generation, rod durability, catch delivery, and cleanup.
- `MCAFishingBobberRenderer` owns only the bobber billboard.
- a villager render layer owns the fishing line so the line can use the already-posed MCA humanoid arm and held-item transform rather than reconstructing a player hand position.

This keeps one source of truth for each concern and avoids a parallel `FishingTask` timer that can disagree with the bobber animation.

## Bobber lifecycle

### Outer state

Preserve the current MCA projectile's simple outer lifecycle:

1. `FLYING`: the projectile travels toward the selected water.
2. `BOBBING`: it has reached water and runs the fish cycle.
3. removal: reel, invalid owner, invalid cast, chore stop, or stale range removes it.

Do not add an MCA `WAITING/APPROACHING/BITE` enum. Mirror vanilla's existing field-shaped state instead so the implementation remains easy to compare against `FishingHook.catchingFish(...)` during code review and forward ports.

### Cast targeting

Keep MCA's exact-water targeting rather than copying player aim.

Players aim manually; villagers cannot. `FishingTask` already chooses `targetWater`, so the bobber should continue to launch from the villager toward the center/surface of that exact water block with the existing small cast arc.

The target remains authoritative for destination. `villager.lookAt(targetWater)` is presentation only.

The projectile should still self-discard if it hits invalid terrain, fails to reach water within the existing short failed-cast window, loses its fishing owner/rod/chore, or exceeds the existing owner-distance bound.

### Bobbing and fish approach

Once the bobber reaches water, `MCAFishingBobberEntity` should closely reproduce the visible portion of vanilla 1.21.1 `FishingHook.catchingFish(...)`.

Use the same core fields and base timing ranges:

- `timeUntilLured`: random 100-600 ticks
- `timeUntilHooked`: random 20-80 ticks
- `nibble`: random 20-40 ticks
- `fishAngle`: random approach heading, then vanilla-style drift while approaching

The wait/approach sequence should reproduce vanilla particle behavior:

- while `timeUntilLured` counts down, occasionally emit `SPLASH` at a randomized point around the hook with the same increasing probability as the vanilla timer nears zero;
- when lure time reaches zero, choose `fishAngle` and enter `timeUntilHooked`;
- while `timeUntilHooked` counts down, move the particle origin toward the bobber using the vanilla distance formula;
- emit the paired `FISHING` wake particles and occasional `BUBBLE` particle over valid water;
- when the approach reaches zero, play `FISHING_BOBBER_SPLASH`, emit the vanilla-style burst of `BUBBLE` and `FISHING` particles around the hook, set `nibble` to 20-40 ticks, and set synchronized biting state true.

This hybrid deliberately omits vanilla `openWater` classification, rain/sky timing modifiers, Lure-enchantment timing reduction, hooked-entity behavior, player luck calculation, XP, criteria, and statistics. Those belong to literal vanilla fishing (scope B), not the approved hybrid.

The base visible timing therefore matches vanilla ranges, while MCA still controls cast placement and loot integration.

### Biting synchronization and bobber dip

Add synchronized biting state equivalent in purpose to vanilla `DATA_BITING`.

When biting becomes true:

- server state enters the nibble window;
- the bobber receives the sharp vanilla-style negative Y movement;
- clients react to the synchronized transition so the dip is visible immediately instead of waiting only for coarse entity-position updates.

While biting, retain the smaller downward bobbing perturbation used by vanilla so the hook visibly stays under tension.

If no reel occurs before `nibble` expires, clear biting and restart the lure cycle. Under normal MCA orchestration the villager should reel immediately, so this is a robustness fallback rather than the expected path.

### Water stabilization

Copy the 1.21.1 vanilla `FishingHook` water-surface stabilization math for the `BOBBING` branch: damp horizontal motion by `0.9` and adjust vertical motion from the hook-to-fluid-surface offset using the same randomized `0.2` factor. Keep the vanilla biting downward perturbation on top of that stabilization.

Do not copy open-water scanning or entity-hooking code. The bobber needs only enough movement logic to remain convincing at the water surface and support the bite dip.

## Catch semantics

A genuine bite always produces a catch.

Remove `FishingTask`'s independent `ticks` catch timer and the current hidden post-wait random miss. The current code succeeds when `random.nextFloat() >= 0.35F`, so it has a 35% miss / 65% catch roll. That roll must disappear from the new design.

The bobber's biting state is the only catch trigger:

1. `FishingTask` sees its owned bobber biting.
2. The villager swings/reels immediately.
3. `FishingTask` calls the existing MCA fishing-loot path exactly once.
4. One real caught item is spawned at the bobber.
5. The rod takes one point of durability damage.
6. The bobber is removed.
7. The caught item visibly flies toward the villager.
8. After delivery, the next fishing cycle may cast again.

Do not introduce a second success roll after the bite. Do not fake a bite that can silently produce no catch.

The switch from a 65% post-wait success roll to guaranteed bite success intentionally changes catch semantics. That is part of the approved player-parity behavior.

## Visible caught-item reel

Use a real `ItemEntity` for the caught loot so the visible return path matches vanilla rather than teleporting loot invisibly into MCA inventory.

Create the `ItemEntity` at the bobber's current position and use vanilla 1.21.1 retrieval velocity as the reference:

- horizontal velocity toward the villager at `delta * 0.1`;
- vertical velocity toward the villager plus the vanilla distance-based upward term.

The reel item is the authoritative caught stack during flight. Do not simultaneously put a duplicate copy into villager inventory.

`FishingTask` holds a short-lived reference to the reel `ItemEntity` while it travels. This is orchestration state, not a second copy of the loot.

Set the reel item pickup delay to 60 ticks. Deliver when it comes within 1.5 blocks of the villager, or after 40 reel ticks as a fallback. On delivery, transfer the entity's current remaining stack into the villager inventory and discard the item entity.

Correctness rules:

- if the reel entity was already removed before MCA delivery, do not generate or insert a replacement copy;
- if fishing stops while the villager is still alive and the tracked reel item still exists, complete the transfer before cleanup so an earned catch is not lost;
- if the villager dies or is removed during the reel, clear the reel item pickup delay and leave the real item in the world rather than duplicating it into an invalid inventory;
- use a finite pickup delay so a reel item cannot become a permanently uncollectable world entity if task tracking is lost after unload/reload.

The 40-tick reel timeout is a delivery safety bound, not fishing gameplay timing. Live verification may justify a later spec change, but implementation of this spec uses 40 ticks and a 60-tick pickup delay.

## `FishingTask` orchestration

`FishingTask` should become thinner after the bobber owns the bite cycle.

Task state is:

```java
private BlockPos targetWater;
private MCAFishingBobberEntity bobber;
private ItemEntity reelItem;
private int reelTicks;
```

There is no independent catch timer.

### Active cycle

While fishing:

1. ensure a valid rod is equipped;
2. find/retain a valid water target using the existing search;
3. approach the target as today;
4. when in cast range, stop navigation, face the target, and cast one bobber if none is active;
5. let the bobber own waiting, approach particles, and biting;
6. when `bobber.isBiting()` becomes true, reel exactly once;
7. while `reelItem` exists, do not cast a second bobber;
8. after the reel item is delivered/cleared, allow the next cast.

One villager must never own multiple active bobbers or multiple in-flight reel items.

### Continuous chore lifecycle

Keep the existing fishing-specific override that prevents the inherited 400-tick behavior timeout from periodically stopping fishing. The rod should not de-equip simply because the behavior duration elapsed.

The chore must still stop normally when `canStillUse(...)` fails, the job changes, combat/activity precedence takes over, or required equipment is lost.

### Rod durability

Damage the rod once when a biting bobber is reeled and the caught item is created, matching the semantic point at which vanilla rod retrieval consumes durability.

If the rod breaks, the earned reel item still completes its flight/delivery. The next cycle may equip another rod from inventory. If none remains, preserve the existing `chore.fishing.norod` abandonment behavior.

## Fishing-line attachment

The fishing line must no longer be anchored from `MCAFishingBobberRenderer` using a player eye/body offset.

The line belongs in a render layer attached to `VillagerEntityMCARenderer`, because that layer runs after MCA/vanilla have prepared the actual humanoid model pose and after the living renderer has applied entity rotation, MCA horizontal/vertical scaling, baby translation, and other model transforms.

### Authoritative attachment transform

For the dominant physical arm:

1. start from the current villager render-layer `PoseStack` and the already-posed parent `VillagerEntityModelMCA`;
2. call the model's `translateToHand(owner.getMainArm(), poseStack)` so arm rotation/swing is included;
3. follow the same third-person held-item transform used by vanilla `ItemInHandLayer` (`-90` X rotation, `180` Y rotation, handed X translation, Y/Z grip translation) to reach the rendered fishing-rod item origin;
4. use that transformed held-item origin as the line attachment; do not add another eye/body approximation or speculative rod-tip offset;
5. convert the interpolated bobber world position into the current layer/model space and draw the same 16-segment curved line shape as vanilla.

The transform must be derived from the model/item rendering path. Do not replace it with another hard-coded eye-position formula.

### Finding the owned bobber on the client

Do not add synchronized bobber IDs or persistent render caches solely for the line.

The line layer should return immediately unless the villager is visibly holding a fishing rod. For an active fishing villager, find the loaded `MCAFishingBobberEntity` in the existing 32-block ownership range whose resolved projectile owner is that exact villager. The server/task invariant already guarantees at most one active bobber.

This small spatial lookup keeps the bobber entity's owner relationship as the source of truth and avoids client cache invalidation state. If profiling later proves this lookup material, optimize it separately with evidence rather than adding speculative state now.

### Visual acceptance

The line attachment is not considered fixed merely because it compiles.

Live client verification must show:

- the line intersects the held fishing rod/hand area rather than the neck, shoulder, chest, or body center;
- right-handed and left-handed villagers attach on the correct side;
- the anchor follows arm swing/cast animation rather than staying fixed to the torso;
- adult and baby/custom-scale MCA villagers do not visibly detach because of MCA renderer scaling;
- the line remains attached while the bobber dips during a bite.

The supplied screenshot is the negative regression reference: that upper-body origin is unacceptable.

## Bobber renderer

After moving the line to the villager render layer, `MCAFishingBobberRenderer` should only render the vanilla fishing-hook billboard texture and no longer own a guessed hand-position helper.

Keep the vanilla hook texture, billboard scale, UVs, light behavior, and cutout render type where applicable.

Fabric and NeoForge keep their existing bobber renderer registration. The villager line layer is common client code and should be installed by the MCA villager renderer itself, not separately per loader.

## Cleanup and edge cases

`FishingTask.stop(...)` remains the authoritative chore cleanup path.

It must safely handle any point in the cycle:

- discard the current bobber if one exists;
- clear bobber and water-target references;
- complete or safely release an in-flight reel item according to the catch-delivery rules above;
- clear reel bookkeeping;
- clear the temporary held fishing rod when fishing genuinely ends, following existing combat/equipment precedence;
- remain idempotent if the bobber already self-discarded or the reel item already disappeared.

If selected water disappears before the bite, discard the stale bobber, clear the target, and search again.

If another behavior moves the villager too far from its bobber, the bobber self-discards through its owner/range validation and `FishingTask` may recast when fishing becomes valid again.

No bobber or catch-delivery entity should become a permanent orphan after chore cancellation, rod loss, or owner invalidation.

## Explicit non-goals

This design does not:

- mix into or subclass vanilla `FishingHook`;
- make villagers hook mobs or arbitrary entities;
- implement vanilla open-water classification;
- apply Lure/Luck-of-the-Sea mechanics to the bobber timing/loot beyond MCA's existing loot context;
- add player fishing XP, stats, criteria, or advancements;
- add a fake player;
- change MCA's water-target search into player-style free aiming;
- redesign all chore timeouts or all chore equipment handling;
- modify files under `C:/Users/Mik/Downloads/MCA/local-source`;
- forward-port to 26.1.2 or 26.2 before 1.21.1 behavior and visuals are validated.

## Testing strategy

### Server/GameTest coverage

Automated tests should prove behavior rather than implementation constants.

At minimum cover:

- a cast at MCA-selected water reaches bobbing state;
- fishing remains active beyond the inherited 400-tick boundary;
- one fishing villager cannot accumulate multiple bobbers;
- the bobber reaches a bite and exposes biting state;
- a bite causes exactly one loot generation/reel, with no post-bite random failure;
- reel creation removes the bobber and damages the rod once;
- a tracked reel item transfers exactly once to villager inventory and cannot duplicate if removed early;
- stopping during bobbing cleans up the bobber;
- stopping during reel delivery preserves or safely releases the earned catch according to the design;
- rod breakage leaves no stale bobber and still permits the already-earned catch to finish;
- missing rod preserves the existing chore-abandon behavior.

Particle rendering and model attachment should not be asserted through brittle server tests.

### Live client verification

Run live 1.21.1 checks on both Fabric and NeoForge:

1. right-handed fisher: cast, line attachment, approach particles, bite dip, splash/sound, reel item, recast;
2. left-handed fisher: same checks with correct physical side;
3. leave fishing active well beyond 400 ticks and confirm no periodic rod/bobber de-equip;
4. verify waiting splashes occur before the visible approach trail;
5. verify the paired fishing wake/bubble trail converges on the bobber;
6. verify the bite visibly dips the bobber and immediately leads to a catch;
7. verify the caught item visibly travels from hook toward villager and ends in MCA inventory once;
8. verify rod durability decreases once per catch;
9. test rod breakage and no-spare-rod abandonment;
10. remove/change the water target and verify stale bobber cleanup/recast;
11. cancel/change the chore during bobbing and during reel flight;
12. verify adult, baby, and visibly scaled villager line attachment;
13. verify AquaCulture/current fishing loot compatibility remains intact.

Client visual proof is mandatory. Compile success and server GameTests do not prove the line attachment or particle sequence.

## Java cleanup and vanilla-owner review gate

Implementation must follow the `java-code-review-cleanup` workflow before it is considered ready.

Because this is Minecraft gameplay/rendering code:

- compare the final bobber state/timing/particles against local 1.21.1 `FishingHook` again;
- compare line attachment against local `ItemInHandLayer`, `HumanoidModel.translateToHand(...)`, and MCA renderer scaling;
- treat those local owners as the design oracle for copied behavior;
- freeze the fishing-only Java diff/file set and run reuse, quality, correctness, and efficiency review lenses over that same scope;
- remove redundant state or helper seams when the vanilla/MCA owner already supplies the source of truth;
- keep one canonical bite state in the bobber rather than shadowing it in `FishingTask`;
- keep one authoritative caught stack in the reel `ItemEntity` until delivery;
- avoid new client caches or synchronized IDs for the line unless measured need justifies them;
- preserve unrelated dirty worktree files.

The cleanup pass should specifically look for copied vanilla logic that can be narrowed, duplicated timer/state ownership, catch duplication/loss paths, client render allocations/searches, and stale helpers left over from the current presentation-only implementation.

## Expected 1.21.1 implementation scope

Expected modifications:

- `common/src/main/java/net/conczin/mca/entity/MCAFishingBobberEntity.java`
  - vanilla-shaped bobbing/lure/bite fields and particle/sound behavior
  - synchronized biting state and bite dip
  - water-surface movement refinement
- `common/src/main/java/net/conczin/mca/entity/ai/brain/tasks/chore/FishingTask.java`
  - remove independent catch timer/miss roll
  - reel on real bite
  - real caught-item flight/delivery state
  - cleanup and durability integration
- `common/src/main/java/net/conczin/mca/client/render/MCAFishingBobberRenderer.java`
  - bobber billboard only; remove guessed line-origin calculation
- `common/src/main/java/net/conczin/mca/client/render/VillagerEntityMCARenderer.java`
  - install the fishing-line render layer
- `common/src/main/java/net/conczin/mca/client/render/layer/VillagerFishingLineLayer.java` — owns model-space hand/item attachment and curved line rendering
- existing fishing GameTest coverage or a focused new GameTest file

Existing entity registration and loader renderer registration should remain unchanged unless the implementation exposes a real API mismatch.

## Forward-port strategy

Implement, test, visually validate, and clean up 1.21.1 first.

Then port the completed behavior to 26.1.2 and finally 26.2. Preserve behavior and ownership; resolve only API/mapping/render-pipeline differences required by each version.

26.2's renderer architecture may require a different way to obtain the rendered hand/item transform. Preserve the semantic contract: the fishing line must derive from the actual MCA held-rod rendering transform rather than reverting to a player eye/body approximation.

## Acceptance criteria

The feature is complete on 1.21.1 only when all of the following are true:

- cast remains targeted at MCA-selected water;
- bobber shows vanilla-shaped wait/approach/bite feedback;
- every real bite reeled by the villager generates exactly one catch;
- there is no hidden post-bite miss roll;
- caught loot visibly flies from the bobber toward the villager as a real item and is delivered once;
- the line visibly attaches to the rendered rod/hand for both arm sides and representative MCA scaling;
- fishing no longer periodically de-equips at the inherited 400-tick boundary;
- cleanup leaves no duplicate loot, stale bobber, or stale reel state;
- MCA loot integration and rod durability remain intact;
- focused automated verification passes;
- both Fabric and NeoForge client visuals have been checked live;
- the final fishing Java diff passes the source-grounded four-lens cleanup review.
