# Dialogue fatigue parity with dev/1.21.1

**Status:** Implemented in the dialogue worktree with passing automated verification; live client acceptance pending. Supersedes the five-story-only shipped scope of the November 11 fatigue spec.
**Reference:** Local `dev/1.21.1` at `a111ada3378a0c6adb2c59fae68967c36054f88d`, checked 2026-11-12. Implementation target is the existing `dev/1.21.1-dialogue-system-b` worktree, Minecraft 1.21.1; do not switch or edit the reference branch.

## Goal and approach

Restore the old fatigue contributions to social reactions and conversation openings through the new datapack system, with an explicit default-on MCA config switch. Reuse the existing pair memory, numeric outcome `fatigue`, picker, completion lifecycle and personality translations. No new fatigue counter, introvert multiplier, mandatory cooldown or legacy engine.

Approaches considered: annotating reactions alone is smaller but misses the old refusal at the door; restoring the legacy engine conflicts with the new conversation flow. Proposed approach: annotate reactions correctly and add the smallest event-weight/terminal-close support for the two missing root responses.

## What the reference actually does

`resources/data/dialogue/Result.java` adds `F = (int)(pairFatigue * interactionChanceFatigue)` when positive; `resources/Dialogues.java` clamps total weights at zero. `VillagerBrain.rewardHearts` increments fatigue once per nonzero heart action; default decay is one per 4,800 ticking-entity ticks. Neither ambient speech nor introversion inherently increases it.

Old `data/mca/dialogues/` has 18 marked results: Chat 1, Greet 2, Joke 3, Flirt 2, Hug 1, Kiss 2, Root 2, Story 5. These are semantic groups, not the number of flags to add: migration split additive conditions into several current outcomes.

| Current path under `data/mca/dialogue_events/` | Required fatigue contribution |
| --- | --- |
| `social/chat.json` | Add F once to baseline failure in each of `day_reaction`, `news_reaction`, `reaction` |
| `social/joke.json` | Add F once to baseline failure for creative, animal and monster |
| `ambient/greet.json` | Kind: add an unconditional weight-0/rate-1 failure, retaining the odd-only weight-5 failure; handshake: mark only baseline weight-3 failure. Short greeting stays unmarked, as in the reference |
| `relationship/flirt.json` | Mark baseline fail and slap once in each nonpartner priority tier, including tier 0; personality/heart bonus entries stay unmarked |
| `relationship/hug.json` | Mark only baseline failure |
| `relationship/kiss.json` | Mark only baseline fail and slap |
| `social/story.json` | Keep five existing baseline flags; memory-conditioned extra failures stay unmarked |
| New `ambient/root/busy.json`, `ambient/root/goaway.json` | Compete with ordinary ambient openings at weights max(0,F-3) and max(0,F-5), respectively |

Never mark every negative outcome: that would multiply F for villagers satisfying additional conditions. Busy first becomes possible at F=4, go-away at F=6. Both are probabilistic; neither guarantees refusal.

## Configurable fatigue semantics

Add `public boolean interactionFatigueEnabled = true` beside the existing `Config.interactionChanceFatigue = 1.0f` and `Config.interactionFatigueCooldown = 4800`. These remain the sole multiplier and decay interval; no new datapack setting, memory key or fatigue counter is needed. Missing the new field in an existing config uses the enabled default. The server reads the current config for authoritative selections and committed heart actions (not only when a conversation is opened).

- **Enabled (default):** preserve existing per-player/villager saved fatigue; each committed, nonzero `rewardHearts` invocation adds one fatigue point regardless of heart sign; a ticking villager decays positive stored fatigue by one every `max(1, interactionFatigueCooldown)` ticks. Effective outcome and ambient-event weights use the existing sanitised, truncated `pairFatigue * interactionChanceFatigue` contribution multiplied by each datapack `fatigue` rate. Busy/go-away enter only when their computed weights become positive.
- **Disabled:** a committed heart action still applies its hearts and all other dialogue effects but **does not add fatigue**. Ignore fatigue contributions when calculating *both* outcome and ambient-event weights, so zero/negative-base fatigue-only results and busy/go-away cannot be selected even if saved fatigue is positive. Existing pair fatigue is **not erased** or migrated: its normal tick-based decay continues using `interactionFatigueCooldown`, and any amount remaining when re-enabled takes effect again. Disabled is not a freeze or reset switch.
- `interactionChanceFatigue = 0` while **enabled** removes weight contributions (including refusals) but does **not** stop per-heart accumulation or decay. The boolean, not the multiplier, controls new gain. Switching either setting must not rewrite saved NBT or invalidate otherwise valid datapack definitions; new selections respect it without requiring `/reload`.

With fatigue disabled, unmarked positive-weight reactions and ordinary ambient openings retain their datapack weights, requirements and priority; the switch does not disable the dialogue system itself. Disabled fatigue-only branches must not interfere with eligible unaffected alternatives. Saved per-pair values remain isolated across players and villagers.

## Minimal runtime additions

1. Allow finite nonpositive outcome base weight only when its `fatigue` rate is positive. Apply requirements and compute effective weights with the config switch, then **exclude weights <=0 before selecting the highest eligible priority** and calling the unchanged positive-only picker. An inactive high-priority outcome must not mask an effective-positive lower-priority one. `choiceEligible` must apply the same effective-positive rule: do not offer choices whose outcomes all have zero-effective weight, and reject stale selections without effects. Automatic outcome routing must use the same eligibility rule; if no positive candidate survives, take a bounded, safe no-eligible route with no action/heart commit, throw or re-entry loop, rather than a zero-weight picker error.
2. Add optional event-level `fatigue` (finite nonnegative number, default 0), restricted to ambient events. Permit nonpositive event base weight only with positive fatigue. Reuse the existing truncation/config sanitization formula; exclude zero-effective events before event-priority and recent-preview filtering. Apply computed weights in both opening `pickPreview` and AMBIENT selection. Old unmarked packs preserve weights and RNG behaviour.
3. Add optional terminal-node `close_interaction` (boolean, default false), valid only on complete terminals. Busy/go-away are one-line scheduling events, priority 0, repeat cooldown 0 seconds, no heart/memory actions, with this flag. Reuse `dialogue.busy` and `dialogue.goaway` translation pools.
4. A selected refusal opening exposes its line and acknowledgement only. Server rejects topic/ambient/resume bypass against that offer; acknowledgement completes it and releases the matching interaction using the existing ID-scoped stop and CLOSE packet. No new packet or timed lockout. Closing early also releases ownership normally. Ordinary `end:true` alone is insufficient: it neither supplies this opening path nor the required owner release.

## Explicit preservation boundaries

This restores fatigue behaviour within the new flow, not identical overall legacy probabilities: new opening candidates, anti-repeat scheduling, relationship tiers and story memory remain. `interactionFatigueEnabled` is a new explicit control rather than a claim that the reference checkout already provided this toggle.

Preserve the approved neutral-chat rule (failure awards no negative hearts) and priority-100 positive partner affection. Consequently Chat failures do not build fatigue, and partner affection still succeeds. Do not silently restore old penalties or bypass priorities. Pure ambient introvert lines still neither build nor consume fatigue; repeated completed heart-changing interactions are needed to build it. Preserve deferred commits, story advancement bonuses, saved fatigue, reload invalidation, danger handling and stale-token protection.

Datapack and save compatibility: absent outcome/event `fatigue` means 0, absent terminal `close_interaction` means false, and legacy positive-weight JSON, priority, requirements and resource IDs retain their meaning. Weight-0/negative-base fatigue entries are accepted only with a positive finite rate, then skipped whenever their effective weight is nonpositive. Existing saves retain the same per-player/villager fatigue NBT layout. No resource or packet schema changes are required solely for the toggle.

## Acceptance

Batch-audit every table row for exactly one additive F per eligible legacy result group. With the default enabled switch and multiplier 1, check F=0/3/4/5/6; at F=6 refusal weights are 3 and 1. With the boolean disabled and preexisting F=6, check both refusals have zero effective weight, baseline reactions remain unmodified, heart changes still commit without incrementing fatigue, and saved fatigue continues to decay. Re-enable before and after a decay tick: remaining saved fatigue resumes affecting both weight paths, with no save migration or reset. Distinguish enabled/multiplier-0 (gain continues, weights unaffected) from disabled (gain suppressed). Verify a missing config field defaults to enabled and old datapacks/resources/NBT still load with unchanged unmarked weights.

Prove a zero-effective high-priority outcome cannot hide a lower-priority usable outcome; an all-zero choice is not offered and a stale selection has no effects; an all-zero automatic route exits safely without throwing, looping or committing actions. Prove refusal selection, readable acknowledgement and interaction release with a real server pair, plus another player/villager remaining unaffected. Check disabled zero-weight events cannot suppress normal candidates, stale offers cannot close a newer interaction, and reload applies new data. Source inspection or builds alone do not establish the reported in-game symptom is fixed.
