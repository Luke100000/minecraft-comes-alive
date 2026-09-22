# Vanilla 26.3 Pathfinding Backport Design

Date: 2026-09-22

Status: approved for implementation planning.

Target sequence: MCA `dev/1.21.1` -> `dev/26.1.2` -> `dev/26.2` -> `dev/26.3`.

Reference sources:

- canonical design oracle: `C:\Users\Mik\Downloads\MCA\mc_source_code\local-source-26.3\src`
- implementation baseline: `C:\Users\Mik\Downloads\MCA\mc_source_code\local-source-1.21.1\src`
- intermediate ownership/API check for the first forward port: `C:\Users\Mik\Downloads\MCA\mc_source_code\minecraft-patched-26.1.2.76-sources\net\minecraft`
- the current dirty `dev/1.21.1` worktree, which remains the authority for MCA-specific behavior already under development.

## Goal

Use Minecraft 26.3 as the canonical pathfinding design reference, backport every behaviorally meaningful improvement that benefits MCA 1.21.1, then forward-port that behavior through 26.1.2 and 26.2 to 26.3 while deleting compatibility code as soon as the target vanilla version owns it.

This is not a source-file transplant. Every relevant vanilla change must receive one explicit disposition:

- **Ported**: the 26.3 behavior can be expressed directly on 1.21.1.
- **Adapted**: the 26.3 behavior is valuable but must be expressed through the 1.21.1 API or MCA owner class.
- **Already present**: current MCA behavior already provides the same or stronger semantics.
- **Not ported**: the change is version/debug/mapping infrastructure or would regress MCA behavior. The reason must be recorded.

No meaningful behavior is silently omitted.

## Canonical Oracle And Port Sequence

Minecraft 26.3 is the design oracle for the whole effort. Intermediate versions are not alternative design targets; they are ownership/API checkpoints on the way to 26.3.

The required sequence is:

1. implement missing 26.3 semantics on `dev/1.21.1` using the smallest 1.21.1-compatible adaptation;
2. merge that work into `dev/26.1.2`, remove any 1.21.1 compatibility shim whose behavior is now vanilla-owned, and keep only MCA-specific policy;
3. merge the cleaned 26.1.2 result into `dev/26.2` and repeat the ownership/deduplication review;
4. merge into `dev/26.3` and converge on the native 26.3 owner/API wherever MCA no longer needs a customization.

The governing rule is:

> **Behavior carries forward; compatibility shims do not.**

Do not preserve an MCA implementation merely because it existed on the previous branch. If the new target's vanilla owner already supplies the same semantics, delete the compatibility layer and let the vanilla owner become the source of truth.

## Success Criteria

The finished backport must:

1. separate sensing/follow range from ordinary navigation range, matching the intent of newer vanilla;
2. preserve MCA's explicit long-distance navigation contract and 160-block configured horizon;
3. retain ordinary villager navigation at a 48-block baseline even if sensing range later changes;
4. preserve or improve path correctness around doors, fence gates, stairs, raised starts, partial collision, climbing, falling, beds, and building targets;
5. avoid globally making every path a 160-block / high-node-budget search;
6. avoid synchronous chunk loading or generation as a side effect of path requests;
7. preserve vanilla ownership where vanilla already has the correct state/lifecycle;
8. remove duplicated or obsolete MCA workarounds only when the source comparison proves the newer vanilla behavior replaces them;
9. keep the code simpler than an equivalent parallel MCA state machine;
10. pass focused unit/GameTests plus the relevant full verification lane before completion is claimed.

## Non-Goals

- Do not port 26.3 mappings, nullable annotations, profiler/debug subscription plumbing, logging infrastructure, or naming churn solely for parity.
- Do not copy entire 26.3 Minecraft classes into MCA.
- Do not lower `villagerFollowRange` from 48 to 16 on `dev/1.21.1`.
- Do not make `villagerPathfindingDistance = 160` the ordinary path length or ordinary node budget.
- Do not restore vanilla/MCA random intermediate waypoint behavior as the primary long-distance route planner.
- Do not redesign unrelated combat, floor scanning, residency, rendering, or builder behavior.
- Do not reset, clean, overwrite, or fold unrelated dirty work into this backport.

## Existing MCA Invariants

These are treated as intentional behavior and must survive the backport unless a specific regression test and source comparison justify replacing them.

### Range model

- On `dev/1.21.1`, keep `villagerFollowRange = 48`; changing sensing semantics is not required to backport the navigation split.
- `villagerPathfindingDistance = 160`.
- Ordinary nearby paths must remain bounded.
- `LongDistancePathTarget` marks explicit destinations that may use the larger configured geometric horizon.
- The decision that a producer needs long-distance handling uses geometric distance, not Manhattan distance.
- That geometric comparison is against ordinary **navigation capability**, not blindly against sensing `FOLLOW_RANGE`.
- On 1.21.1 the ordinary navigation floor is 48. On newer vanilla this corresponds to the villager's native `requiredPathLength = 48`.
- If a future branch uses `FOLLOW_RANGE = 16` while ordinary required path length remains 48, a target 30 blocks away is still an ordinary navigation target.
- Vanilla 26.1.2 and 26.3 use the Mob default `FOLLOW_RANGE = 16` while villagers independently require 48 blocks of navigation. On the forward-port branches, prefer convergence to that split unless a concrete MCA sensing/targeting requirement justifies retaining the configurable 48 override. Any retained 48 must be documented as an MCA gameplay divergence, not as a pathfinding requirement.

### Long-distance execution

- The logical destination remains the real bed/building/grave/etc. block.
- MCA does not create random intermediate logical destinations for the normal long-distance route.
- Vanilla/MCA pathfinding may return a useful partial path and continue progressively.
- `CANT_REACH_WALK_TARGET_SINCE` remains the canonical failure timer rather than introducing a parallel retry state machine.
- For a nearby static target whose direct geometric distance fits the ordinary range but whose actual route is longer, MCA may run the cheap ordinary search first and retry with the configured extended horizon only when the ordinary result cannot reach the destination.
- This ordinary-first fallback is intentional: it solves long detours around walls without making every nearby path a 160-block search.

### MCA navigation improvements

- `MCAGroundPathNavigation` remains the MCA ground-navigation owner.
- `MCAWalkNodeEvaluator` remains the evaluator owner for MCA-specific collision/path-type fixes.
- `BedApproachTarget` and multi-target bed logic remain the final HOME approach abstraction.
- MCA door/fence-gate handling, raised-start handling, barrier/collision checks, climb traversal, and fall resynchronization are preserved when they are stronger than vanilla 26.3.

## Vanilla Source Audit Boundary

The audit is exhaustive for behavior that can affect an MCA ground villager.

### Core shared path search

Compare all behaviorally meaningful changes between 1.21.1 and 26.3 in:

- `net.minecraft.world.entity.ai.navigation.PathNavigation`
- `net.minecraft.world.entity.ai.navigation.GroundPathNavigation`
- `net.minecraft.world.level.pathfinder.PathFinder`
- `net.minecraft.world.level.pathfinder.NodeEvaluator`
- `net.minecraft.world.level.pathfinder.WalkNodeEvaluator`
- shared path model/types used by those classes when a changed contract requires inspection (`Path`, `Node`, `Target`, `PathType`, `PathfindingContext`).

### Villager movement owners

Compare:

- villager constructor/navigation setup and attribute construction;
- `MoveToTargetSink`;
- `SetWalkTargetFromBlockMemory`;
- `InteractWithDoor`;
- `SleepInBed`;
- the villager activity packages that wire HOME / meeting / movement behaviors.

### MCA overlap

For every useful vanilla change, inspect the current MCA owner before implementation:

- `MCAGroundPathNavigation`
- `MCAWalkNodeEvaluator`
- `LongDistancePathTarget`
- `BedApproachTarget`
- `MultiTargetPositionTracker`
- `ClimbTraversal`
- `PathfindingBlacklist`
- `ExtendedWalkTowardsTask`
- `WanderOrTeleportToTargetTask`
- `EnterBuildingTask`
- `SmarterOpenDoorsTask`
- `MixinMoveToTargetSink`
- `VillagerEntityMCA`
- `Config`

If a vanilla change is already implemented elsewhere in MCA, reuse that owner instead of creating a second implementation.

## Known 26.3 Change: Independent Navigation Length

This behavior is approved for backport.

Newer vanilla decouples navigation range from `FOLLOW_RANGE`:

```java
private float requiredPathLength = 16.0F;

public void setRequiredPathLength(float length) {
    this.requiredPathLength = length;
    this.updatePathfinderMaxVisitedNodes();
}

private float getMaxPathLength() {
    return Math.max((float)this.mob.getAttributeValue(Attributes.FOLLOW_RANGE), this.requiredPathLength);
}
```

Villagers then set:

```java
this.getNavigation().setRequiredPathLength(48.0F);
```

The 26.3 `PathFinder` node budget is mutable and is updated from:

```text
getMaxPathLength() * 16
```

### 1.21.1 backport semantics

- Keep MCA `FOLLOW_RANGE = 48`.
- Add an independent ordinary navigation requirement of 48.
- The ordinary baseline node budget therefore remains at least 768 nodes.
- Keep `maxVisitedNodesMultiplier = 1.0` by default.
- On 1.21.1, where `PathFinder.maxVisitedNodes` is constructor-fixed and `PathNavigation` has no `requiredPathLength` API, adapt the 26.3 model in `MCAGroundPathNavigation`: floor the ordinary max path length at 48 and floor the created PathFinder budget at `48 * 16`.
- Explicit long-distance targets may use the configured 160 geometric horizon without permanently changing the entity's ordinary navigation requirement.
- A nearby static target may retry at the extended horizon only after the ordinary 48-range search fails to reach it.
- Do not globally turn the ordinary node budget into `160 * 16`.

The implementation should follow the 26.3 owner model as closely as 1.21.1 permits. MCA code should not maintain a parallel copy of the same state in the entity, Brain, or config.

### Forward-port semantics

By 26.1.2 vanilla already has the structural behavior being emulated on 1.21.1:

- `PathNavigation.requiredPathLength`;
- `setRequiredPathLength(...)`;
- `getMaxPathLength() = max(FOLLOW_RANGE, requiredPathLength)`;
- mutable `PathFinder.maxVisitedNodes`;
- node-budget refresh derived from effective path length;
- vanilla villager constructor setup with `setRequiredPathLength(48.0F)`;
- `PathNavigation.recomputePath()` deferral through `canUpdatePath()`.

Therefore the 1.21.1 compatibility implementation is temporary and must not be copied forward wholesale.

On `dev/26.1.2` and later:

- remove MCA code that sets `requiredPathLength` from `villagerPathfindingDistance`;
- let vanilla's villager-required path length remain 48 for ordinary navigation;
- audit MCA consumers of `FOLLOW_RANGE`; if no concrete MCA sensing/targeting behavior requires the override, stop overriding vanilla's 16 sensing default on these branches;
- keep `villagerPathfindingDistance = 160` as MCA's extended-horizon policy only;
- use vanilla's mutable PathFinder/required-path machinery rather than retaining a second MCA budget implementation;
- keep MCA's climb-aware `canUpdatePath()` override;
- remove the 1.21.1 `recomputePath()` compatibility override because vanilla now owns that deferral and calls the MCA `canUpdatePath()` polymorphically;
- remove redundant `setCanPassDoors(true)` once the target vanilla `NodeEvaluator` already defaults it to true, while preserving intentional `setCanOpenDoors(true)`;
- retain `LongDistancePathTarget`, progressive real-destination routing, nearby-static extended fallback, and other MCA-specific behavior that vanilla does not provide.

The same ownership review is repeated at the 26.2 and 26.3 hops. A newer vanilla implementation replaces the compatibility layer only when source comparison plus regression coverage proves equivalent or stronger behavior.

## Other 26.3 Changes

No other behavior is pre-approved merely because it exists in 26.3. Each change must be classified by source comparison.

Examples that require explicit audit include:

- surface-position and below-surface target handling in `GroundPathNavigation`;
- vertical waypoint tolerances and ground-navigation capability APIs;
- mutable `PathFinder` search-budget behavior beyond the required-path-length split;
- meaningful algorithm changes in `WalkNodeEvaluator`, including start-node, collision, partial-collision, water, jump, fall, diagonal, and path-type handling;
- changes in `MoveToTargetSink` path creation, fallback, retry, stuck, or failure-memory behavior;
- HOME/meeting movement changes in `SetWalkTargetFromBlockMemory`;
- door interaction and bed lifecycle changes that alter actual villager behavior.

Pure signature renames, local-variable renames, annotation migrations, profiler/debug capture, debug subscriber support, and equivalent refactors are recorded as **Not ported: no gameplay/pathfinding semantic change**.

## Architecture

### One navigation owner

`MCAGroundPathNavigation` is the MCA integration point. The 1.21.1 vanilla navigation object should gain only the state/API needed to represent newer vanilla semantics. MCA-specific routing decisions remain in `MCAGroundPathNavigation`.

Do not create a separate path-budget manager, service, cache, or Brain memory.

### One evaluator owner

`MCAWalkNodeEvaluator` should extend/adapt the 1.21.1 vanilla evaluator and carry only MCA behavior that is not supplied by the backported vanilla semantics.

When a 26.3 evaluator change makes an MCA override redundant, remove or simplify the MCA code only after a regression proves parity or improvement.

### One failure source of truth

Movement failure remains represented by vanilla Brain/navigation state, especially `CANT_REACH_WALK_TARGET_SINCE`, path completion, and stuck detection.

Do not introduce duplicate cooldowns, failure timers, or reachability caches for a 26.3 backport.

### Marked long-distance paths remain exceptional

Ordinary path creation uses the independent ordinary navigation requirement.

Explicit MCA long-distance movement uses `LongDistancePathTarget` to retain producer ownership of the real logical destination. Navigation may also retry a nearby static target with the larger horizon when the ordinary bounded search cannot reach it. Neither path may mutate global sensing range or permanently enlarge the ordinary node budget.

## Source-Comparison Ledger

Implementation must maintain a ledger in the implementation notes or plan checklist with one row per meaningful vanilla change:

| Vanilla owner/change | 1.21.1 behavior | 26.3 behavior | MCA overlap | Disposition | Test/evidence |
| --- | --- | --- | --- | --- | --- |
| independent required path length | tied to `FOLLOW_RANGE` | separate requirement, villager=48 | partial equivalent via current 48 follow range | Adapted | ordinary-range + budget regression |
| recompute while navigation cannot update | base 1.21.1 recomputes immediately | newer vanilla defers through `canUpdatePath()` | current MCA already carries a 1.21.1 compatibility override | Adapted on 1.21.1; delete on 26.1.2+ | airborne recompute regression |
| sleep-start movement-memory cleanup | owned by `Villager.startSleeping()` | moved into `SleepInBed` | behavior already exists on 1.21.1 | Already present / ownership moved | source comparison; no duplicate hook |

The table is not limited to this known row. The audit is incomplete until every behaviorally meaningful hunk in the defined source boundary has a disposition.

## Coding And Cleanup Rules

The implementation follows the requested `coding-standards` and `java-code-review-cleanup` rules.

### Readability / KISS

- Prefer small owner-level changes over copied vanilla classes or broad mixins.
- Use descriptive names such as `requiredPathLength`, `maxPathLength`, and `maxVisitedNodes`.
- Use guard clauses to keep hot-path control flow shallow.
- Comments explain why MCA differs from vanilla, not what obvious code does.
- Name non-obvious constants; do not scatter magic values such as 16, 48, 160, 40, 1200, or search multipliers when an existing owner/config already supplies the meaning.

### DRY / reuse

- Reuse vanilla owner state and project helpers before adding new abstractions.
- Keep one long-distance target factory/predicate rather than duplicating distance checks across tasks.
- Reuse GameTest setup/helpers where existing fixtures already express the same geometry.
- Do not duplicate path-progress classification, door state, bed candidate state, or retry/failure state.

### YAGNI

- Port only behavior supported by a source diff and a concrete MCA use/correctness reason.
- Do not port debug/profiling infrastructure as speculative future work.
- Do not add compatibility branches for hypothetical versions to the 1.21.1 branch.

### Immutability / ownership

- New data carriers are immutable unless Minecraft APIs require mutation.
- Mutable search budget belongs to `PathNavigation`/`PathFinder`, matching vanilla ownership.
- MCA-specific target metadata remains a shallow immutable tracker.

## Mandatory Four-Lens Review

Every implementation slice and the final combined diff receive these reviews.

### Reuse

Check for:

- duplicated vanilla helpers or state;
- duplicate MCA distance/target helpers;
- repeated GameTest fixture setup;
- custom path-type/collision logic now supplied by the backported vanilla behavior.

### Quality

Check for:

- parallel state machines;
- unnecessary wrappers or boolean-flag APIs;
- parameter sprawl;
- stale compatibility branches;
- comments that narrate obvious code;
- overly broad visibility or mutable state.

### Correctness

Check:

- ordinary paths stay ordinary;
- long-distance policy is marker-scoped;
- node budgets update when the ordinary required path length changes;
- changing sensing range cannot silently shrink the villager's 48-block ordinary navigation requirement;
- unreachable/stuck semantics are not weakened;
- no path request force-loads chunks;
- door/bed/fence/stair/climb/fall behavior does not regress;
- MCA path targets remain the intended logical targets.

### Efficiency

Check:

- no global 160-block node budget;
- no duplicate path computations per attempt;
- no hot-path stream/collection churn without need;
- no repeated world/chunk lookups that can be reused locally;
- no pathfinding-wide debug allocation or logging added to release behavior.

## Test Strategy

Use test-driven implementation for every behavioral change that can be isolated.

### Unit tests

Pin pure/config semantics:

- follow-range default/clamp remains 48 / existing clamp;
- pathfinding-distance default/clamp remains 160 / existing clamp;
- ordinary required path length is independent of `FOLLOW_RANGE`;
- pathfinder node-budget calculation follows the effective ordinary path length.

### NeoForge GameTests

Prefer GameTests for navigation/runtime behavior. Add or extend focused tests for:

- ordinary path range with normal `FOLLOW_RANGE = 48`;
- lowering `FOLLOW_RANGE` in a fixture does not lower an explicitly configured 48 ordinary navigation requirement;
- lowering `FOLLOW_RANGE` does not cause a geometrically 30-block target to be classified as long-distance when the ordinary navigation requirement is 48;
- marked long-distance targets can use a larger geometric horizon without changing sensing range;
- diagonal targets inside geometric ordinary range are not misclassified as long-distance;
- nearby static targets can take routes longer than the ordinary navigation range by using the ordinary-first extended fallback;
- useful partial long-distance paths still progress;
- unreachable targets retain failure/stuck behavior;
- surface/below-surface target behavior for any accepted 26.3 change;
- every accepted evaluator change with representative geometry;
- doors/fence gates, stairs/partial blocks, raised starts, climbing/falling, and beds when an audited change overlaps those paths.

Tests should exercise the real owner path. Avoid test-only seams that bypass `PathNavigation`, `MCAWalkNodeEvaluator`, Brain memory, or normal movement-sink lifecycle.

### Verification order

Use the smallest lane first:

```powershell
.\gradlew.bat common:test --no-daemon
.\gradlew.bat common:compileJava neoforge:compileJava --no-daemon
.\gradlew.bat neoforge:runGameTestServer --no-daemon --no-build-cache --max-workers=1
.\gradlew.bat :fabric:build :neoforge:build --no-daemon
```

Run only the commands that exist in the project and report pre-existing/unrelated failures separately rather than weakening assertions.

Compilation is not gameplay proof. Any accepted navigation/evaluator behavior that is only observable at runtime needs a GameTest or a narrowly described manual gameplay check.

## Dirty-Worktree Safety

The current worktree contains unrelated and overlapping changes.

- Never reset, clean, checkout-over, or blanket-stash the worktree.
- Before each implementation task, inspect `git status` and the exact target-file diff.
- Preserve uncommitted MCA navigation work and adapt around it.
- Stage/commit only task-owned paths if commits are requested by the execution workflow.
- If an audited change overlaps an unclear uncommitted edit, stop that slice and resolve ownership before editing.

## Sequential Merge Safety

Each forward-port hop is a cleanup boundary, not a mechanical conflict-resolution exercise.

Before accepting a conflict resolution on 26.1.2, 26.2, or 26.3:

1. inspect the target vanilla owner in that version;
2. identify which previous-branch code was only a compatibility shim;
3. remove that shim if the target vanilla owner now supplies the behavior;
4. retain MCA-specific policy only once;
5. rerun the focused regressions before proceeding to the next branch.

In particular, do not carry this existing forward-branch pattern into the final design:

```java
this.getNavigation().setRequiredPathLength(
        (float) Config.getInstance().getVillagerPathfindingDistance()
);
```

It incorrectly turns the 160-block MCA long-distance horizon into the ordinary vanilla navigation requirement and would inflate the ordinary PathFinder budget. The forward branches must instead preserve vanilla's 48 ordinary requirement and apply 160 only to MCA's exceptional extended searches.

## Acceptance

This backport and forward-port sequence is complete only when:

1. the source-comparison ledger accounts for every behaviorally meaningful change in the defined audit boundary;
2. all **Ported** and **Adapted** entries have focused regression coverage where practical;
3. all **Already present** entries point to the exact MCA owner and are verified not to be weaker than 26.3 for the relevant behavior;
4. every **Not ported** entry has a concrete reason rather than “not needed”;
5. the independent 48-block ordinary navigation requirement is implemented without lowering 1.21.1 MCA sensing range;
6. MCA's 160-block marked long-distance horizon remains exceptional rather than global;
7. the four cleanup lenses find no worthwhile unresolved issue in the touched diff;
8. focused tests and the relevant full verification lanes pass, or unrelated pre-existing failures are named precisely;
9. `git diff --check` passes for the task-owned diff;
10. the 26.1.2, 26.2, and 26.3 forward ports contain no duplicate required-path-length/budget ownership or stale 1.21.1 recompute compatibility code;
11. `dev/26.3` uses native 26.3 ownership wherever possible while retaining MCA-specific behavior exactly once;
12. the final report distinguishes source parity, automated proof, and any remaining manual gameplay proof.
