# Restore dialogue interaction fatigue

**Status:** Approved for implementation on 2026-11-11 with simple story memory (Option A).
**Target:** MCA 1.21.1 common dialogue-event system (Fabric and NeoForge).
**Related:** [Remembered stories](2026-11-11-story-memory-design.md).

## Player-visible contract and legacy source

Repeated interactions that change hearts make selected reactions more likely when the **same player** talks to the **same villager**. Datapacks choose which outcome receives fatigue and how strongly; ordinary outcome requirements, priority, advancement bonuses and story memory still apply.

In legacy `dev/1.21.1`, `Result.java` opted individual results in using `applyFatigue`. It added the **positive** value `(int) (memory.getInteractionFatigue() * Config.interactionChanceFatigue)` to the marked result if that value was greater than zero. `Dialogues.java` chose results with `max(0, total chance)`. The old chance-analysis UI could display that contribution with a reversed sign for a negative-hearts result, but the actual random weight increased.

For generic success base 4 and skeptical failure base 2, at the default global multiplier of 1.0 and without other modifiers:

| Stored fatigue | Success weight | Skeptical weight | Success chance |
| ---: | ---: | ---: | ---: |
| 0 | 4 | 2 | 4/6 |
| 1 | 4 | 3 | 4/7 |
| 5 | 4 | 7 | 4/11 |

The existing `Memories` NBT stores fatigue per player within a villager. `VillagerBrain.rewardHearts` increments it once per **nonzero** heart change, regardless of sign. The brain decays positive fatigue by one every `max(1, interactionFatigueCooldown)` entity ticks, with a default of 4,800. `DialogueEngine.commit` already calls `rewardHearts` for committed `mca:hearts` actions. Abandoned or `end:true` conversations leave pending heart changes uncommitted. Reuse this state and lifecycle unchanged.

## Minimal datapack extension

Add one optional number to `DialogueEvent.Outcome` for both choices and hidden routing nodes:

~~~json
{
  "weight": 2,
  "fatigue": 1.0,
  "actions": [{ "type": "mca:hearts", "amount": -2 }],
  "next": "generic_fail"
}
~~~

- Omitted `fatigue` defaults to `0.0`. Explicit zero disables it, `1.0` reproduces the original bonus, and values such as `0.5` and `2.0` tune strength per outcome.
- Decode only finite, nonnegative numbers. Extend both strict outcome allowlists to recognize `fatigue`, retaining rejection of unknown fields and malformed values.
- The field changes **only** the marked outcome weight. It may be used on positive reactions deliberately; do not infer opt-in from heart sign, node name or memory conditions.

At each outcome draw, read one authoritative server-side pair-fatigue snapshot and the current config:

~~~text
stored = max(0, Memories.getInteractionFatigue())
multiplier = interactionChanceFatigue if finite and positive; otherwise 0
legacyPoints = (int) (stored * multiplier)
bonus = (int) (legacyPoints * outcome.fatigue)
effectiveWeight = outcome.weight + bonus
~~~

These Java casts truncate toward zero and saturate at `Integer.MAX_VALUE` on huge positive inputs. Scaling the **already truncated** legacy points reproduces its exact float-multiplication behavior at a rate of `1.0`. Fractional datapack rates truncate again. This rate interpretation is a proposal, subject to review. Invalid runtime config contributes zero without rewriting config. No nested codec, arbitrary rate cap or second probability implementation is required.

Apply the effective weight only **after** requirements and highest-priority filtering. Feed it to the existing `weightedPick` with its single random draw; no new RNG, session state, cached weights or client-side decision. An ineligible or lower-priority outcome remains excluded. Unmarked datapacks retain their existing selection and RNG behavior. The integer-bounded bonus cannot materially expand the pre-existing `double` overflow case for extreme authored base weights; changing the general picker is outside this restoration.

Selecting a choice or automatic transition reads current fatigue at server resolution time. Pending hearts are still committed only through the existing completion process, and a chosen outcome is never rerolled.

## Approaches considered

1. **Proposed numeric scalar:** One optional field permits per-outcome opt-in and datapack strength, reusing the current codec/picker.
2. **Boolean `apply_fatigue`:** Slightly less authoring flexibility; only the global config can change its strength.
3. **Nested fatigue object:** A record, codec and extra validation for capabilities that are not requested.
4. **Existing requirements or story-memory actions:** They gate outcomes or remember stories, but cannot continuously adjust a weight based on stored fatigue.

The only necessary Java seams are the outcome codec and the context-bound selection weight. Current memory, commit, reload generation and networking owners already supply everything else.

## Shipped story and separate story memory

Annotate exactly one **existing baseline skeptical outcome** with `"fatigue": 1.0` in each of the five stories in `common/src/main/resources/data/mca/dialogue_events/social/story.json`. Preserve IDs, requirement tests, static weights, actions, destinations and follow-up graph.

The independent [story-memory spec](2026-11-11-story-memory-design.md) adds **one** memory-gated skeptical outcome for exploring (weight 3), plus one for Nether, Ender Dragon and Wither (weight 10 each). These extra outcomes remain **unmarked**. Thus total eligible skeptical weight is `baseline failure + fatigue bonus + eligible memory bonus`, with exploring memory contributing 0 or 3 based on presence. Generic receives no remembered-story bonus. Story memory uses `LongTermMemory`, while fatigue uses `Memories`; neither needs changes to `DialogueEventHistory`.

**Concurrent-file conflict:** Both designs affect `social/story.json`. Assign one integration editor when implementing, and reconcile with live changes. Both features are approved for coordinated implementation.

Existing datapacks without the field require no migration. Old MCA versions with strict codecs reject the new field, so authors must target a supporting release. Existing worlds reuse saved fatigue immediately, without a datafix. Server `/reload` replaces the event snapshot and invalidates existing offers via current generation checks; new valid sessions use the new rate. Stored fatigue, completed history and previously selected outcomes stay as they were.

## Acceptance and open decisions

- Generic weights are 4:7 at fatigue 5 and default rate/config; with zero fatigue, absent/zero rate, or zero multiplier they are 4:2.
- Choice outcomes and hidden routing outcomes use the same weights. Requirements, highest priority and player/villager isolation hold.
- Codec accepts numbers 0, 0.5, 1 and 2, rejecting negative, nonfinite and wrong-typed input. Existing Java callers using the five-argument `Outcome` constructor remain supported.
- Committed nonzero hearts increment existing fatigue once. Abandoned, `end:true`, stale or rejected interactions do not; existing decay/NBT remain unchanged.
- Exactly five baseline skeptical story outcomes are marked. All four added memory-gated skeptical outcomes remain unmarked.
- Reload affects new valid resolutions, without a new cache, packet, save field or chance-analysis UI.

**D1 (accepted):** Use numeric `"fatigue": 1.0` instead of a boolean or nested object (recommended).

**D2 (accepted):** Use scaling previously truncated legacy points for other authored rates, as specified above (recommended).

**D3 (accepted):** One editor integrates `social/story.json` with the separate remembered-story changes.
