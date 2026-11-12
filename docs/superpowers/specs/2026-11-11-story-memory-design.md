# Restore remembered stories in MCA dialogue

**Status:** Approach A selected and approved for implementation on 2026-11-11.
**Target:** MCA 1.21.1 shared dialogue-event JSON. The prior three-tier Option C design and plan are preserved in `docs/superpowers/archive/`.

## Goal

A villager remembers when a particular player has completed telling an exploring, Nether, Ender Dragon or Wither story. Repeating that topic increases skeptical outcome weight, using existing datapack `mca:remember` actions and `mca:memory` conditions. Preserve the five visible story choices, achievement-based success bonuses, heart effects and full conversation graph. Generic stories have no topic-memory penalty.

## Approaches

- **A — one timed memory per topic (selected):** one conditionally eligible skeptical outcome per topic. Low authoring overhead and only existing datapack primitives.
- **B — engine-driven decay:** smooth recovery, requires new Java/schema logic.
- **C — three exploration tiers:** stepped recovery via three memories and outcomes, archived as `2026-11-11-story-memory-option-c-design.md` with its plan in the same archive directory.

## Datapack contract

| Story | Player-scoped memory | Duration (ticks) | Additional skeptical weight | Negative hearts | Failure node |
| --- | --- | ---: | ---: | ---: | --- |
| Exploring | `story.exploring` | 24000 | 3 | -4 | `exploring_fail` |
| Nether | `story.nether` | 2400000 | 10 | -4 | `nether_fail` |
| Ender Dragon | `story.enderdragon` | 2400000 | 10 | -5 | `enderdragon_fail` |
| Wither | `story.wither` | 2400000 | 10 | -6 | `wither_fail` |

Add exactly one `mca:remember` action for the topic to **every** existing success/failure outcome and the new conditional skeptical outcome. The action has `var: "player"` and `time` from the table. Each of the four extra skeptical outcomes requires matching `mca:memory` with `var: "player"` and `present: true`, has the additional weight in the table, applies the existing failure hearts and targets the existing failure node.

Effects commit only after successful dialogue completion. Player-scoped memory keys are owned by each villager and include the player's UUID. Existing `LongTermMemory.remember` **extends** an unexpired memory by duration rather than resetting expiry. Preserve that implementation, including compatibility with old `story.exploring` saves.

## Integration with interaction fatigue

The separate approved fatigue design adds a numeric `"fatigue": 1.0` on **only the five pre-existing baseline skeptical outcomes**. None of the four new memory-conditional skeptical outcomes receives fatigue, so eligible skeptical weight increases by baseline + fatigue bonus + memory bonus without double-counting. Both features must be edited into the same live `social/story.json`.

## Acceptance

- Every outcome for each of the four stories writes exactly one correctly scoped/timed topic memory.
- Each story has exactly one added `mca:memory`-gated skeptical outcome with the weight, hearts and destination in the table. Existing outcomes' static weights, requirements, destinations, hearts and follow-ups remain intact.
- Without topic memory the extra failure outcome is ineligible. With matching memory the skeptical weight increases by 3 or 10. Unrelated memories never affect eligibility.
- No story choice is gated. Advancement evidence still boosts successful reactions. Generic has no remembered-story penalty.
- The content uses existing datapack codecs and deferred persistence, with no Java memory change or new save state.
