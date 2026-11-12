# Restore remembered stories in MCA dialogue

**Status:** Approach C (tiered datapack memory) selected; revised design awaiting review. No production implementation authorized.
**Target:** Existing Minecraft 1.21.1 dialogue-event overhaul, shared Fabric/NeoForge datapack resources.

## Intent and boundaries

Restore the old behavior where a villager remembers that a particular player has recently told them the same story and is more likely to respond skeptically when it is repeated. This should be authored and tunable through datapacks. Preserve the current conversation branches, heart effects, and advancement-based credibility bonuses. Interaction fatigue is a separate design.

All five selectable stories—generic, exploring, Nether, Ender Dragon, and Wither—remain available at all times. No advancement or memory hides a choice. The generic story retains its existing behavior and does not get a new memory penalty. Achievements can make stories more convincing but never unlock or gate them.

## Existing owner and constraints

- Current event: `common/src/main/resources/data/mca/dialogue_events/social/story.json`. Its choices use weighted outcomes and branch into follow-ups.
- The existing format accepts `mca:remember` actions with `id`, `var: "player"` and optional `time` duration in game ticks. It accepts `mca:memory` outcome requirements with `id`, `var: "player"` and `present`.
- `DialogueEngine` queues outcome actions as pending effects. `commit` applies them after successful conversation completion, using villager `LongTermMemory`. The stored player-scoped key includes the player's UUID. An abandoned conversation must not record a telling.
- Outcome selection filters eligible outcomes by requirements and draws by weight within priority. The memory condition belongs on the extra outcome, never on the parent story choice.
- Legacy `dev/1.21.1` `data/mca/dialogues/story.json` remembered four stories. Exploring memory lasted 24,000 ticks; Nether, Ender Dragon and Wither lasted 2,400,000. Repeat skeptical weights were 3, 10, 10 and 10. Exploring's old penalty used remaining duration to decline in integer steps.
- `LongTermMemory.remember(id, time)` extends expiry from `max(now, oldExpiry) + time`, rather than resetting to `now + time`. Repeated completed tellings accumulate memory duration independently for each ID. The proposed design preserves that, while capping the additional exploring failure weight at +3.

## Approaches

**A. Binary JSON memory:** One memory-conditional skeptical outcome per story; no Java changes, but exploring stays at full +3 and then abruptly drops to zero.

**B. Smooth engine-driven decay:** A datapack-configurable time-decay weight operator can smoothly reduce the penalty but needs Java/schema changes.

**C. Tiered JSON memories (chosen):** Three separately expiring exploring memories, each worth +1 skeptical weight, and one long memory for each major achievement story. It approximates declining skepticism with extra JSON and no Java changes.

The user selected **C** for the concrete design below. Porting the legacy interpreter was ruled out because it duplicates the current selection engine and adds unrelated behaviour.

## Datapack contract

| Story | Memory ID(s) | Duration (ticks) | Extra repeat-fail weight | Existing failure hearts |
|---|---|---|---|---:|
| Exploring | `story.exploring.short`, `story.exploring.medium`, `story.exploring` | 8,000 / 16,000 / 24,000 | +1 per active tier (max +3) | -4 |
| Nether | `story.nether` | 2,400,000 | 10 | -4 |
| Ender Dragon | `story.enderdragon` | 2,400,000 | 10 | -5 |
| Wither | `story.wither` | 2,400,000 | 10 | -6 |

For **exploring**:

1. Append three `mca:remember` actions to **every** existing outcome, successful or skeptical, using the three exploring IDs and durations. This includes the friendly-personality and advancement-bonus success entries.
2. Add **three** separately gated skeptical outcomes, each with exactly one matching `mca:memory` requirement (`var: "player"`, `present: true`) and `weight: 1`. Each uses the existing `exploring_fail` destination, `mca:hearts` `amount: -4`, and writes **all three** memories on successful completion.
3. After one completed telling and no retelling, the bonus declines from **+3 to +2 at 8,000 ticks, +1 at 16,000 ticks and zero at 24,000 ticks**. With a repeated telling, all three expiry times extend independently; the penalty cannot exceed +3, though recovery may take longer.
4. Preserve legacy `story.exploring` as the longest-tier key. On an existing world with only legacy exploring memory, that old key activates +1, not all three tiers. No data migration is required.

For **Nether, Ender Dragon and Wither**:

1. Append the corresponding single `mca:remember` action to every existing outcome using the ID and 2,400,000 ticks from the table.
2. Add one extra skeptical outcome per topic, gated by matching `mca:memory`, with `weight: 10`, the original topic-specific failure hearts and destination, and the same remember action.

For **all four stories**, preserve all existing base outcomes, weights, advancement/personality requirements, heart effects and dialogue follow-ups. Keep memory conditions on added *outcomes*, never on the story *choices*; all five choices stay visible. Repeats increase the probability of skepticism but do not guarantee failure, and achievements still raise success weights. Memories belong to a specific player-villager pair and are committed only when the dialogue completes.

Example additional **exploring short-tier** outcome; medium and long have corresponding matching conditions:

~~~json
{
  "requirements": [
    { "type": "mca:memory", "id": "story.exploring.short", "var": "player", "present": true }
  ],
  "weight": 1,
  "actions": [
    { "type": "mca:hearts", "amount": -4 },
    { "type": "mca:remember", "id": "story.exploring.short", "var": "player", "time": 8000 },
    { "type": "mca:remember", "id": "story.exploring.medium", "var": "player", "time": 16000 },
    { "type": "mca:remember", "id": "story.exploring", "var": "player", "time": 24000 }
  ],
  "next": "exploring_fail"
}
~~~

Example new Nether outcome:

~~~json
{
  "requirements": [
    { "type": "mca:memory", "id": "story.nether", "var": "player", "present": true }
  ],
  "weight": 10,
  "actions": [
    { "type": "mca:hearts", "amount": -4 },
    { "type": "mca:remember", "id": "story.nether", "var": "player", "time": 2400000 }
  ],
  "next": "nether_fail"
}
~~~

All changes reside in the overrideable story event datapack resource. `mca:memory` checks presence, not remaining ticks: three independent expiry durations produce the stepwise decay.

## Acceptance

- A single mass audit of the parsed event covers all four stories: every exploring outcome has exactly three correctly scoped/timed remember actions; the other three stories each use one. Exactly three added exploring skeptical outcomes have weight 1 each, and each major story has exactly one added skeptical outcome with weight 10.
- Evaluate exploring with zero, one, two or three matching tier memories: added skeptical weights are 0, +1, +2, +3. Unrelated topic memories must not change eligibility; success weights remain unchanged.
- All five choices remain eligible without achievements. Existing advancement bonuses still raise success weight.
- Dialogue graph remains valid; follow-ups are preserved. Existing deferred commit and long-term-memory expiry own abandonment, scope and expiration behavior; expiry extensions on retellings must not be described as resets. Any regression there requires a targeted runtime test.
- No extra Java code, storage, fatigue, localization or UI changes.

**Decision for review:** Confirm the three exploring tiers and cumulative memory duration in the revised C design before implementing.
