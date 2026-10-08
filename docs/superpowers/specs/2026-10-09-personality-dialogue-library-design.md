# MCA Personality Dialogue Library — Design

**Status:** Proposed for review; content design only, not implementation approval.

**Target:** Minecraft Comes Alive 1.21.1, branch `dev/1.21.1-dialogue-system`, building on the shipped `DialogueEvent` engine and the current authoring guides. The target is **all 14 assignable built-in personalities**, not the `unassigned` fallback or addon-defined personalities.

## Purpose and agreed direction

Make conversations feel like they belong to a particular villager, instead of repeating generic lines with a personality label attached. Use a **balanced tone**: everyday life and humor most of the time, with friendship, disagreements, vulnerability, and occasional emotional stakes. A gloomy villager may laugh; a crabby villager may care; a greedy villager can make a generous choice. Personality shapes perspective, not an unbreakable stereotype.

Each personality should have **one substantial branching story and two or three everyday conversations**. Existing stories count toward that goal *if upgraded where necessary*, not duplicated. Basic personality stories must be findable without a rare profession, building, weather, or one-off gameplay event. Optional contextual routes make familiar subjects feel personal when real state is available.

Success is a library with genuine differences in topic, voice, responses, and consequences—not simply a large count of JSON files or longer monologues.

## Scope and approach

This is a **dialogue-content expansion**, not a new dialogue subsystem. Author or revise events under `common/src/main/resources/data/mca/dialogue_events/`, with their lines, prompts and replies in `common/src/main/resources/assets/mca_dialogue/lang/en_us.json`. Use `docs/dialogue-authoring.md`, `docs/dialogue-datapack-guide.md`, and `docs/dialogue-system-guide.md` as the schema authorities.

- Reuse `mca:personality`, hearts, mood, relationship to the player, age, profession, home/workplace building assignment, current building, time, weather, and recorded `mca:recent_event` conditions. Use line-less conditional/weighted routing with a guaranteed unconditional fallback for scene variations.
- Use existing `DialogueEvent` `lines`, `choices`, `next`, `outcomes`, `complete`, `history`, repeat/cooldown, and follow-up history conditions. Do not add one Java handler per story.
- **Distinguish fiction from gameplay facts.** A guard can fear a raid, but must not say they *survived* one without `mca:recent_event` for `mca:raid_survived`. A farmer can worry about crops, but cannot claim a crop failure was detected. A villager's `mca:family` condition describes their relationship **to the talking player**, not proof that the villager has children. Use only recorded information and conditions that mean what the line claims.
- New detected gameplay facts (e.g., a failed harvest, a specific friendship dispute, an individual ambition) are **not part of this content batch**. If authors discover a compelling missing fact, record it as a separate prospective Java feature with its real gameplay producer, persistence, and tests; do not simulate it with invented dialogue memories or silently add event trackers.
- Keep existing protocol, UI, authority, speech reveal, pause deadline, reward-commit boundary, base social interactions, and legacy migrations untouched. Do not add a personality preference/ambition registry merely for prose.

## Planned coverage: fourteen distinct voices

The entries below give the primary branching story and **two everyday subjects** per personality. These are content briefs, not literal dialogue lines. A third everyday subject is optional if it adds genuine variety.

| Personality | Main story and meaningful decision | Everyday subjects |
| --- | --- | --- |
| **Friendly** | Tries to bring two neighbors together; player can encourage patient listening or suggest backing off. No claim of an actual recorded quarrel. | Introducing a newcomer; helping someone with chores |
| **Flirty** | Unsure whether affection is sincere or merely playful; player can encourage honesty or a slower approach. Adult-appropriate material only. | Awkward compliments; joking about a romantic gesture |
| **Playful** | A harmless joke went wrong; player can encourage an apology or help plan a better way to make someone smile. | A silly contest; an invented game for a rainy day |
| **Gloomy** | **Reuse and deepen `personal/gloomy_reflection`**, with an optional real-context thread about work or a recent experience and the existing `comfort`/`give_space` outcomes. | Worrying about tomorrow; spotting an unexpectedly pleasant moment |
| **Sensitive** | An offhand remark stuck with them; player can acknowledge the hurt or help them ask what was meant. | Remembering a kindness; noticing a small change in someone's tone |
| **Greedy** | Must decide between keeping a windfall and sharing it; dialogue discusses motives without asserting inventory transfers. | Bargaining stories; what counts as a fair exchange |
| **Odd** | Has an unusual idea for village life; player can hear it out or challenge the practicality without ridiculing them. | Strange observations; unconventional uses for ordinary things |
| **Crabby** | Reluctantly admits missing someone's company; player can give respectful space or invite them into company. | Complaining about noise; grudgingly praising good work |
| **Extroverted** | Wants to host a gathering but worries nobody will enjoy it; player can encourage an intimate event or a larger get-together. No claim that an actual event was scheduled. | News from the square; meeting new neighbors |
| **Introverted** | Wants to set a boundary without rejecting a friend; player can help phrase it or offer a quieter alternative. | Quiet hobbies; finding a peaceful spot |
| **Relaxed** | Is asked to help someone under pressure and considers when calm becomes avoidance; player can encourage action or patience. | Enjoying an ordinary afternoon; appreciating rain |
| **Anxious** | Prepares for dangers that may never arrive; player can make a practical plan or provide reassurance. An *actual* attack/raid variant requires the real recorded event. | Checking doors and supplies; overthinking a harmless sound |
| **Peaceful** | Tries to defuse a disagreement without ignoring the underlying problem; player can suggest mediation or a firm boundary. | Finding compromises; enjoying a calm village morning |
| **Upbeat** | Tries to stay hopeful after a setback while acknowledging disappointment; player can encourage honesty or an achievable next step. | Celebrating tiny successes; sharing an optimistic prediction |

**Existing content ownership:** Keep the identities and stable choice IDs of `personal/gloomy_reflection` and `personal/gloomy_reflection_followup`. Preserve `ambient/crabby_night` and `ambient/rain_relaxed`, improving or counting them as one everyday entry only if they meet the final quality bar. Do not copy the same subject into both a new story and a current personal or social event.

## Conversation shapes and discoverability

### Main stories

One per personality, preferably `presentation.mode: "highlighted"` where eligible, with an authored prompt and specific resume prompt. A normal friendship threshold such as **20 hearts** may unlock the story; personality and basic friendship should be sufficient for the default path. Use adult eligibility only when the subject requires it, and provide age-appropriate coverage for personalities also held by children instead of making the entire personality inaccessible. Special existing events (death, cure, raid, etc.) can retain their higher contextual priority. Other eligible highlighted conversations remain reachable through Ask under existing selection behavior.

Aim for **4–7 reasonably short spoken passages before an important decision**, at least **two distinct player replies**, and **2–4 passages per meaningful ending**. More than one decision is warranted only if it changes the interaction. Player replies should sound like something a person would say, not like a visible scoring menu. Every route must conclude or continue validly; no unreachable choice, fake action, or dummy confirmation.

Stories generally use `history: "story"` and stable semantic choice IDs so later content can refer to the latest completed outcome. Use modest, differentiated heart changes only when justified by the response; no automatic heart + mood double reward and no penalty for choosing respectful personal space. The final passage still needs the existing explicit `Back to topics` acknowledgement before completion is committed.

### Everyday conversations

Provide **at least two distinct subjects for each personality**, generally through `presentation.mode: "ambient"` and `history: "scheduling"`, with one to three concise spoken passages. Occasional player replies are useful; not every greeting needs a choice. Use normal short prompts or memorable observations rather than generic stock affirmations. An `ask` event is appropriate for a deliberately selectable everyday topic, but must not overwhelm the Ask menu with dozens of near-identical options.

Ambient selection considers **only the highest currently eligible priority tier**, then weights candidates within that tier. Give comparable personality-specific ordinary entries balanced priorities/weights so one permanently suppresses the other; keep the unconditional baseline available for when contextual entries are not eligible. Review competition with existing time-of-day, greeting, crabby-night and relaxed-rain ambient events. Do not assign all new ambient events arbitrarily high priority.

Cooldowns belong in JSON and are **per player/villager/event**. Avoid the five-second demonstration cooldown for finished production stories: use intentionally longer, possibly randomized cooldowns (initial tuning proposal: everyday **60–180 seconds**, main stories **300–900 seconds**), subject to playtesting. These are authoring values, not new game clocks. Existing tests/showcase may retain fast cooldowns. Do not change the fixed 2400-tick pause deadline.

### Contextual variants and follow-ups

In each main story, include **at least one optional variant** when a relevant, *verifiable* context exists: profession/workplace, time/weather, relationship to the player, or a recorded recent life event. Its unconditional fallback must still work for a villager with no such context. Context should change *what the villager specifically talks about*, not only insert an adjective into otherwise identical dialogue. Use a separate event only if the alternate situation constitutes a different story.

Provide **a small selected set of choice-dependent follow-ups (target four total, counting the existing Gloomy follow-up)** rather than an obligatory follow-up for every story. Each is a separately selectable `ask`/highlighted event with `mca:event_completed` plus `mca:event_choice` when the wording depends on a choice, `history: "story"` in the preceding event, and a distinct event ID and resume prompt. Preserve the engine's semantics: the latest successfully *completed* choice replaces older recorded choices; an abandoned run does not. Do not imply a village NPC changed the world when the only persistent state is conversation history.

## Content rules and accessibility

- **Varied voices:** Give each personality a characteristic attitude without defining it by a repeated catchphrase. Make the player able to respond kindly, disagree thoughtfully, or ask for space. Avoid every branch ending in the same canned thanks.
- **Natural pace:** Keep each spoken passage to approximately one or two short sentences. A longer scene should contain more *turns*, not an enormous single line that must slowly reveal before `Next` is available (currently 40 ms per grapheme).
- **Honest context:** No unsupported claims about children, crops, raids, marriages, scheduled gatherings, or past actions. Never infer an individual's specific history from personality alone.
- **No romance with children:** Romantic situations and flirtation must be adult-appropriate; preserve MCA's personality/age checks and explicitly gate any general-access adult-coded variants.
- **Translations:** Unique stable text keys in MCA's existing English dialogue locale, exact prompts/replies/lines, no reuse of `/1`, `/2` alternative-speech suffixes as ordered steps. Keep localization text in lang JSON rather than embedding English in data nodes.
- **Compatibility:** JSON-authored events must work on Fabric and NeoForge through the current common loader. Addon personalities are out of scope, but adding built-in stories must not break third-party event loading.

## Delivery and parallel writing

Deliver in **four small personality batches** of three or four entries. Writers/subagents, *if available during implementation*, can work on separate event JSON files and draft proposed translation key/value additions independently. **One integrator** owns the shared `mca_dialogue/lang/en_us.json` merge to avoid key clashes. Do not let parallel authors edit the same locale file concurrently. Review each batch for voice, factual claims, selection priority, repeated content and consistent rewards before merging the next.

Preferred order: establish the complete 14-personality inventory and content matrix; draft existing-Gloomy revisions and a representative new story/ambient pair as quality references; cover the remaining personalities in batches; integrate optional contextual routes and selected follow-ups; verify automated and in-game behavior. The implementation plan will choose exact filenames, IDs and ordering. No source edits or commits beyond this design are authorized by this spec alone.

## Verification and acceptance

1. Every built-in non-`unassigned` personality has **one eligible main story** and **at least two distinct everyday entries**, allowing improved existing resources to count. All 14 personalities are reachable with legitimate test setups; none relies exclusively on a rare job/life event. Track the inventory in the implementation plan, distinguishing reused, revised and newly added content.
2. Every main story has meaningful choices and distinct ending text; contextual variants have an unconditional fallback. Selected follow-ups correctly depend on the recorded completed choice and appear as separate topics; alternative choices and abandoned runs do not falsely unlock them.
3. All JSON resources decode through current codecs; event IDs and translation keys are unique, all line/prompt/choice keys resolve, graph destinations exist, and text does not refer to nonexistent game state. Use the existing `McaDialogueEventResourcesTest`, authoring tests and focused selection/history tests; extend focused tests for new failure modes, not a duplicate engine.
4. Check actual priority/weight interplay to ensure variety, baseline fallback, and non-starvation in Talk. Test low/high hearts, a child-eligible personality, a villager with no profession or applicable event, and a contextual variant with the recorded fact. Verify choices stay hidden until reveal and rewards occur only upon acknowledged completion.
5. Run `:common:test`, `:fabric:build`, `:neoforge:build`, and `git diff --check`. Validate in game across several personalities and repeated conversations; capture any client/unobserved behavior as unverified rather than claiming the tests prove visual or writing quality.

### Out of scope

No new AI-generated dialogue, autonomous villager personality progression, procedural memory/ambition tracking, new gameplay event producers, cross-villager social simulation, content for addon-defined personalities, additional localizations, new GUI layout, or automatic scheduling of events described in dialogue. Propose such features separately if later playtesting justifies them.
