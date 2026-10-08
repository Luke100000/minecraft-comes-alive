# MCA Personality Dialogue Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give each of MCA's 14 assignable built-in personalities one substantial branching story and two distinct everyday conversations, with credible context and a small number of remembered-choice follow-ups.

**Architecture:** Add/revise `DialogueEvent` JSON and English localization only; the existing Java codec, selection engine, history and UI remain authoritative and unchanged. Use a focused resource-contract test for the new library and the existing shipped-resource test for every translation and graph. Four independently reviewed content batches feed a single localization integrator.

**Tech Stack:** Minecraft 1.21.1, MCA `DialogueEvent` JSON, English Minecraft language JSON, Java 21, JUnit 5, Gradle, Fabric and NeoForge.

**Spec:** `docs/superpowers/specs/2026-10-09-personality-dialogue-library-design.md`

## Global Constraints

- Work only in the existing `dev/1.21.1-dialogue-system` isolated worktree; do not touch the unrelated main checkout or push without a new request.
- Cover `friendly`, `flirty`, `playful`, `gloomy`, `sensitive`, `greedy`, `odd`, `crabby`, `extroverted`, `introverted`, `relaxed`, `anxious`, `peaceful` and `upbeat`; exclude `unassigned` and addon personalities.
- Balance humor, ordinary village life, friendship and occasional emotion. Every story requires at least two consequential, natural-sounding replies, 4–7 short lead-in passages and 2–4 passages per ending; everyday entries need 1–3 concise passages.
- A default main story requires only `mca:personality` plus `mca:hearts` minimum **20**; optional real-context routing must include an unconditional fallback. Never make a child's access to a personality depend on adult romance: `flirty` has a nonromantic default, with adult-only romantic wording behind `mca:age_group`.
- Keep one or two spoken sentences per passage so the existing **40 ms/grapheme** reveal remains usable; rely on existing final `Back to topics` acknowledgement for committed history/effects.
- Use `history: "story"` for main stories and `history: "scheduling"` for everyday entries. Preserve old `mca:personal/gloomy_reflection` and `mca:personal/gloomy_reflection_followup` IDs and existing `comfort`/`give_space` choice IDs.
- Use `{"type":"cooldown","min_seconds":300,"max_seconds":900}` for main stories and choice-dependent follow-ups; use `min_seconds:60,max_seconds:180` for everyday content. Do not change the engine's **2400-tick** pause window or test/showcase-specific cooldowns.
- All ordinary personality ambient events, including revised `crabby_night` and `rain_relaxed`, use **priority 0, weight 10**: this lets them share the existing priority-0 tier with greeting/root conversations. The unconditional baseline remains at `Integer.MIN_VALUE`. Main stories use **priority 40**, below exceptional contextual content; do not introduce permanently higher ambient tiers.
- Keep original event definitions and translation keys unless deliberately revised; never reinterpret existing `/1` and `/2` alternate-phrasing suffixes as ordered lines.
- Context must be verifiable. Do not assert real crop failure, children, scheduled gatherings, previous romantic relationships, or raid survival without the correct real gameplay condition. `mca:family` refers to family relation **to the player**, not proof of children. No new gameplay facts, Java APIs, action types, jobs, UI or procedural ambitions.
- For authored optional context, use an initial line-less `outcomes` routing node with an eligible weighted route and an **unconditional** route; a real-condition variant is never necessary for the default story to appear. Distinct branch prose must justify the route.
- Exactly **four selected choice-dependent follow-ups in total**, counting existing Gloomy; use `mca:event_completed` plus `mca:event_choice` and stable semantic choice IDs. Never claim a conversation caused an untracked game-world change.
- **Parallel ownership:** subagents may independently author personality event files and supply locale key/value drafts; only **one integrator** edits `common/src/main/resources/assets/mca_dialogue/lang/en_us.json`. Review voices, translation conflicts, priority, truthfulness and rewards before proceeding to the next batch.
- After every batch run the focused new test and `McaDialogueEventResourcesTest`; at completion run `:common:test :fabric:build :neoforge:build` and `git diff --check`. Client playtesting is unverified until performed.

## File map and fixed content inventory

Event paths below are relative to `common/src/main/resources/data/mca/dialogue_events/`. Every new story is `personal/personality_<name>.json`, except the preserved Gloomy file. Every new ambient is `ambient/personality_<name>_<subject>.json`; these IDs and subject distinctions are the authored API, not placeholders.

| Batch | Personality | Main story event | Everyday ambient A | Everyday ambient B | Optional context in main story |
| --- | --- | --- | --- | --- | --- |
| A | Gloomy | `personal/gloomy_reflection` **revise** | `ambient/personality_gloomy_tomorrow` | `ambient/personality_gloomy_bright_spot` | `minecraft:farmer` profession / ordinary worry fallback |
| A | Friendly | `personal/personality_friendly` | `ambient/personality_friendly_newcomer` | `ambient/personality_friendly_chores` | `minecraft:farmer` profession / hypothetical neighbor dispute fallback |
| A | Playful | `personal/personality_playful` | `ambient/personality_playful_contest` | `ambient/personality_playful_rain_game` | rain / ordinary joke fallback |
| A | Sensitive | `personal/personality_sensitive` | `ambient/personality_sensitive_kindness` | `ambient/personality_sensitive_tone` | night / ordinary misunderstood remark fallback |
| B | Flirty | `personal/personality_flirty` | `ambient/personality_flirty_compliments` | `ambient/personality_flirty_gesture` | `mca:age_group` adult romantic wording / nonromantic compliments fallback |
| B | Odd | `personal/personality_odd` | `ambient/personality_odd_observations` | `ambient/personality_odd_objects` | thunder / ordinary unconventional idea fallback |
| B | Introverted | `personal/personality_introverted` | `ambient/personality_introverted_hobbies` | `ambient/personality_introverted_quiet_spot` | night / ordinary boundary-setting fallback |
| C | Greedy | `personal/personality_greedy` | `ambient/personality_greedy_bargain` | `ambient/personality_greedy_fairness` | `minecraft:farmer` profession / imagined windfall fallback |
| C | Crabby | `personal/personality_crabby` | `ambient/crabby_night` **revise** | `ambient/personality_crabby_good_work` | night / ordinary reluctant friendship fallback |
| C | Relaxed | `personal/personality_relaxed` | `ambient/rain_relaxed` **revise** | `ambient/personality_relaxed_afternoon` | rain / ordinary calm-vs-avoidance fallback |
| D | Anxious | `personal/personality_anxious` | `ambient/personality_anxious_supplies` | `ambient/personality_anxious_sound` | recorded `mca:attacked` within 24000 ticks / hypothetical worry fallback |
| D | Peaceful | `personal/personality_peaceful` | `ambient/personality_peaceful_compromise` | `ambient/personality_peaceful_morning` | `mca:guard` profession / ordinary mediation fallback |
| D | Upbeat | `personal/personality_upbeat` | `ambient/personality_upbeat_small_win` | `ambient/personality_upbeat_prediction` | rain / ordinary setback fallback |
| D | Extroverted | `personal/personality_extroverted` | `ambient/personality_extroverted_square` | `ambient/personality_extroverted_neighbors` | daytime / hypothetical gathering fallback |

**Totals:** 14 main stories (13 new, one revised), 28 everyday events (26 new, two revised). Four follow-ups: existing `personal/gloomy_reflection_followup` (choice `comfort`), plus new `personal/personality_playful_followup` (choice `apologize`), `personal/personality_greedy_followup` (choice `share`) and `personal/personality_anxious_followup` (choice `make_plan`). New follow-ups use `presentation.mode: "ask"`; prior stories have `history: "story"` and corresponding choice IDs.

**Other shared files:** `common/src/main/resources/assets/mca_dialogue/lang/en_us.json` (single integrator for all prompts, resume prompts, lines and replies); `common/src/test/java/net/conczin/mca/dialogue/PersonalityDialogueLibraryTest.java` (new focused coverage and content-contract checks); `common/src/test/java/net/conczin/mca/dialogue/McaDialogueEventResourcesTest.java` (existing strict decode, locale and history-reference checks); `common/src/test/java/net/conczin/mca/dialogue/DialogueSelectionTest.java` (existing selection tests, extend only where needed); `docs/dialogue-authoring.md` (brief newly shipped content examples) and `docs/dialogue-system-guide.md` (correct any stale ambient-priority description).

## Review Focus

1. **No optional context:** an ordinary villager with the matching personality and 20 hearts must still qualify for the main story; the fallback branch must be authored and reachable. Pin with `defaultStoryEligibilityIsNotRarelyGated` in Task 1.
2. **Age mismatch:** nonadult `flirty` villagers must never see romantic adult-only prose while still having ordinary coverage. Pin with `flirtyRomanceIsOnlyAnOptionalAdultRoute` in Task 3.
3. **Ambient starvation:** two ordinary personality ambient choices should share the highest eligible priority tier with greetings when no special mode takes over; baseline must still be last resort. Pin with `ambientPersonalitiesShareTheOrdinarySelectionTier` in Task 5.
4. **False follow-up unlock:** a `make_plan` story left unfinished (or completed with the other choice) must not enable an anxious follow-up. Pin against existing history/selection behavior in Task 6.
5. **Localization collisions:** all new prompts, lines and choice keys must be present and unique even when an integration batch supplies overlapping keys. Pin with existing `duplicateLocalizationSourceKeysAreRejectedBeforeGsonCollapse` and shipped-resource tests, plus `personalityKeysAreUniqueAndResolve` in Task 7.

---

### Task 1: Establish test contract and first two reference personalities (Batch A, part 1)

**Files:** Create `common/src/test/java/net/conczin/mca/dialogue/PersonalityDialogueLibraryTest.java`; modify `personal/gloomy_reflection.json`, `personal/gloomy_reflection_followup.json`; create `personal/personality_friendly.json`, and the four Gloomy/Friendly ambient paths in the inventory; modify `common/src/main/resources/assets/mca_dialogue/lang/en_us.json` through the sole integrator.

**Interfaces:** The test helper `private static DialogueEvent event(String relativeId)` loads `data/mca/dialogue_events/<relativeId>.json` from test resources and decodes with `DialogueEvent.decode(ResourceLocation.fromNamespaceAndPath("mca", relativeId), JsonObject)`. Helper `private static void assertPersonalityCoverage(String personality, String story, String ambientA, String ambientB)` asserts story highlighted, `history:story`, matching `mca:personality` and `mca:hearts(min=20)` at event level, initial optional route with unconditional fallback, >=2 distinct choice IDs, >=2 completing endings, and both ambient definitions' personality condition, `history:scheduling`, priority/weight and cooldown bounds. Later tasks add coverage cases without duplicating helpers.

- [ ] **Step 1: Write failing `gloomyAndFriendlyCoverage` and `defaultStoryEligibilityIsNotRarelyGated` tests.** The latter verifies exactly the two ordinary event-level prerequisites (`personality`, hearts >=20) with no age, weather, mood, profession or event hard gate; verify the main-story routing node includes a real optional-context outcome **and** an unconditional one. Assert `comfort` and `give_space` survive intact.
- [ ] **Step 2: Run** `.\gradlew.bat :common:test --tests net.conczin.mca.dialogue.PersonalityDialogueLibraryTest --offline --console=plain` **and observe FAIL** from missing Friendly/ambient resources or Gloomy's old restrictive requirements.
- [ ] **Step 3: Revise Gloomy and author Friendly plus four everyday subjects.** Gloomy removes hard `sad`/night requirements, retains both durable choices and follow-up ID, gains a farmer-context route, and uses 300–900-second story cooldown. Friendly centers on a *hypothetical* disagreement, not an asserted tracked quarrel. Add 4–7 short lead-in passages and 2–4 lines in each choice outcome; author context fallbacks and repeat/priority/history exactly as in Global Constraints. Align Gloomy follow-up production cooldown; keep its completed-choice requirement.
- [ ] **Step 4: The single locale integrator inserts all keys, then run** `.\gradlew.bat :common:test --tests net.conczin.mca.dialogue.PersonalityDialogueLibraryTest --tests net.conczin.mca.dialogue.McaDialogueEventResourcesTest --offline --console=plain` **and observe PASS**.
- [ ] **Step 5: Review prose and `git diff --check`, then commit** only Task 1 files: `git commit -m "feat(dialogue): establish personality story references"`.

### Task 2: Complete Batch A with Playful and Sensitive

**Files:** Create `personal/personality_playful.json`, `personal/personality_sensitive.json` and their four ambient JSON paths from the inventory; modify `PersonalityDialogueLibraryTest.java` and the shared locale via integrator.

**Interfaces:** Reuse `event` and `assertPersonalityCoverage` from Task 1; main-story choice IDs: Playful `apologize` / `try_another_joke`; Sensitive `acknowledge_hurt` / `ask_intent`. Do not create the Playful follow-up until Task 6.

- [ ] **Step 1: Add failing `playfulAndSensitiveCoverage` test** checking all six IDs, exact default gates, contextual rain/night routes and distinct complete endings.
- [ ] **Step 2: Run** `.\gradlew.bat :common:test --tests net.conczin.mca.dialogue.PersonalityDialogueLibraryTest --offline --console=plain` **and observe FAIL** because these resources are absent.
- [ ] **Step 3: Author the two stories and four different everyday topics** using the fixed inventory. Avoid claiming real prank consequences or a recorded insulting neighbor; distinguish compassionate curiosity from agreement. Retain fixed cooldown/priority/weight contracts.
- [ ] **Step 4: Integrate English keys in the sole locale writer; run both focused resource tests** with the command from Task 1 and observe PASS.
- [ ] **Step 5: Review, `git diff --check`, commit** `feat(dialogue): add playful and sensitive conversations`.

### Task 3: Batch B — Flirty, Odd and Introverted

**Files:** Create `personal/personality_{flirty,odd,introverted}.json`, each personality's two inventory-listed ambient events; modify `PersonalityDialogueLibraryTest.java` and sole-integrator English locale.

**Interfaces:** Fixed story choice IDs: Flirty `speak_sincerely` / `take_it_slow`; Odd `hear_it_out` / `question_practicality`; Introverted `set_boundary` / `quiet_alternative`.

- [ ] **Step 1: Write failing `batchBCoverage` and `flirtyRomanceIsOnlyAnOptionalAdultRoute` tests.** Confirm each event's personality-and-hearts-only default eligibility, a conditional route with unconditional fallback, and the `flirty` romance variant gated by `mca:age_group`=`adult` **only on a branch**. Ensure nonromantic alternate text keys and no romance-specific unguarded default lines.
- [ ] **Step 2: Run the focused personality test and observe FAIL** for missing resources.
- [ ] **Step 3: Author nine JSON resources** using the distinct topics; for Flirty, default to sincerity of ordinary compliments and reserve romance-themed passages for its adult-gated branch. Odd is imaginative without being mocked; Introverted can assert boundaries without being antisocial.
- [ ] **Step 4: Integrate locale once and run both focused resource tests; expect PASS** with no duplicate keys.
- [ ] **Step 5: Review, `git diff --check`, commit** `feat(dialogue): add flirty odd and introverted conversations`.

### Task 4: Batch C — Greedy, Crabby and Relaxed

**Files:** Create `personal/personality_{greedy,crabby,relaxed}.json`, four new ambient paths in the inventory; **modify** existing `ambient/crabby_night.json` and `ambient/rain_relaxed.json` instead of replacing their IDs; modify `PersonalityDialogueLibraryTest.java` and locale.

**Interfaces:** Greedy choice IDs `share` / `keep`; Crabby `offer_company` / `respect_space`; Relaxed `take_action` / `wait_patiently`.

- [ ] **Step 1: Add failing `batchCCoverage`**, ensuring each personality has exactly the inventory-listed two distinct subjects, with reused ambient paths, story choices/conditional fallback, and no hard event gates.
- [ ] **Step 2: Run the personality test; observe FAIL** for new stories and for old ambient priority/cooldown contracts.
- [ ] **Step 3: Author three stories/four new ambient entries; revise the two existing ambient entries** to 1–3 meaningful passages, priority 0 / weight 10 and 60–180-second cooldown. Greedy's windfall is hypothetical; Crabby does not magically become cheerful; Relaxed recognizes limits of avoidance.
- [ ] **Step 4: Integrate locale in one writer and run both focused resource tests; expect PASS**.
- [ ] **Step 5: Review, `git diff --check`, commit** `feat(dialogue): add greedy crabby and relaxed conversations`.

### Task 5: Batch D — Anxious, Peaceful, Upbeat and Extroverted

**Files:** Create `personal/personality_{anxious,peaceful,upbeat,extroverted}.json` plus eight inventory-listed ambient JSON resources; modify `PersonalityDialogueLibraryTest.java`, `DialogueSelectionTest.java` (if fixture needed) and shared English locale.

**Interfaces:** Anxious choice IDs `make_plan` / `offer_reassurance`; Peaceful `mediate` / `hold_boundary`; Upbeat `name_disappointment` / `next_small_step`; Extroverted `small_gathering` / `open_invitation`.

- [ ] **Step 1: Add failing `batchDCoverage` and `ambientPersonalitiesShareTheOrdinarySelectionTier` tests.** The latter calls existing `DialogueEngine.planSelection` on eligible loaded ambient events and checks at least two personality options coexist at priority 0 alongside `ambient/greet` and `ambient/root/generic`; verify `ambient/baseline` remains the last-resort tier. Include low/high-heart metadata and no-profession fallback assertions across all 14.
- [ ] **Step 2: Run focused personality and `DialogueSelectionTest`; observe FAIL** on missing resources/coverage.
- [ ] **Step 3: Author four stories and eight ambient events.** Anxious's actual-attack branch requires `mca:recent_event`=`mca:attacked` with `within_ticks:24000`; unrecorded village danger remains a *worry*. Peaceful's guard variant cannot assert a historical fight; Extroverted does not falsely claim a gathering occurred.
- [ ] **Step 4: Merge localization as sole writer; run personality, selection and shipped-resource tests, expect PASS**.
- [ ] **Step 5: Review, `git diff --check`, commit** `feat(dialogue): complete all personality coverage`.

### Task 6: Three new completed-choice follow-ups

**Files:** Create `personal/personality_playful_followup.json`, `personal/personality_greedy_followup.json`, `personal/personality_anxious_followup.json`; modify `PersonalityDialogueLibraryTest.java`, possibly focused `DialogueEventHistoryTest.java` / `DialogueSelectionTest.java` only if an existing behavior lacks a regression; sole integrator updates locale.

**Interfaces:** Exact prior event/choice pairs: `mca:personal/personality_playful#apologize`, `mca:personal/personality_greedy#share`, `mca:personal/personality_anxious#make_plan`. All follow-ups have `presentation.mode:ask`, distinctive prompt/resume prompt, requirements for both `mca:event_completed` and `mca:event_choice`, `history:scheduling`, and 300–900-second cooldown; keep Gloomy's pre-existing `#comfort` reference.

- [ ] **Step 1: Write failing `fourChoiceDependentFollowupsHaveValidReferences` and `unfinishedOrDifferentChoiceDoesNotUnlockFollowup` tests.** Assert each of four exact event/choice references, required story history, and absence of unsupported statements about altered village state. For the latter, reuse existing `DialogueEventHistoryTest` latest-completed-choice/aborted-run fixtures, testing `mca:event_choice` gating only if not already covered.
- [ ] **Step 2: Run focused resources/history tests; observe FAIL** for the three missing follow-up resources.
- [ ] **Step 3: Author three follow-up events** with 2–4 meaningful spoken passages referencing the player's earlier advice but not asserting any untracked NPC/world change.
- [ ] **Step 4: Integrate locale once and rerun focused resource/history tests; expect PASS**.
- [ ] **Step 5: Review, `git diff --check`, commit** `feat(dialogue): add remembered personality followups`.

### Task 7: Library-wide audit, documentation and release verification

**Files:** Modify `PersonalityDialogueLibraryTest.java` for complete 14-row final table; update `docs/dialogue-authoring.md` and `docs/dialogue-system-guide.md` to reference new examples and equal-tier ambient selection; correct only affected content/translation files found by review.

**Interfaces:** Exhaustive static expectations are the inventory table above, **14** accessible highlighted stories, **28** personality everyday entries, **4** choice-dependent follow-ups. Existing `McaDialogueEventResourcesTest` remains the sole strict full-locale/codec scanner; do not copy it into a new test helper.

- [ ] **Step 1: Add failing `completeFourteenPersonalityMatrix` and `personalityKeysAreUniqueAndResolve` tests** asserting every row, stable choices, valid graph, unique locale keys and meaningful subject separation. Reuse existing `McaDialogueEventResourcesTest` for strict full-file duplicate-key parsing and prompt/line/choice translation resolution; add assertions only for gaps, not a second scanner.
- [ ] **Step 2: Run** `.\gradlew.bat :common:test --tests net.conczin.mca.dialogue.PersonalityDialogueLibraryTest --tests net.conczin.mca.dialogue.McaDialogueEventResourcesTest --offline --console=plain`; **observe FAIL** for any unmet contract; if it already passes, record that explicitly instead of manufacturing a failure.
- [ ] **Step 3: Audit and correct** all 14 personalities for voice, age appropriateness, non-duplicated subjects, real-condition truthful wording, distinct branches, natural reply labels, 40-ms reveal readability, heart/mood effect duplication, unique keys, contextual fallback, cooldowns and real ambient priority competition. Update two relevant guides only where shipped examples or priorities changed. Do not introduce new APIs for content concerns.
- [ ] **Step 4: Rerun targeted tests, then** `.\gradlew.bat :common:test :fabric:build :neoforge:build --offline --console=plain --no-parallel` **and** `git diff --check`; require `BUILD SUCCESSFUL` and no whitespace errors. If offline dependencies prevent the build, report the exact failure and rerun online only when permitted.
- [ ] **Step 5: In-game smoke test if a client is available:** ordinary low/high hearts, at least one child-eligible (including nonadult Flirty), four different voices, both ambient subjects after repeat/cooldown, real-attack variant, reply reveal, completed follow-up and abandoned-run behavior, and language at different UI scales. Record outcomes and **mark unrun scenarios unverified**; no passing claim from static tests alone.
- [ ] **Step 6: Final review of branch diff, commit** `test(dialogue): verify personality conversation library` **if task files remain,** and provide user-facing counts, test/build results, unverified client checks, and branch status. Do not push unless asked.

## Execution and handoff

Implement in task order, using a fail → minimal resource/content edit → green cycle for each batch; review each batch before advancing. Authors may write disjoint event JSON in parallel, but they must give translation additions to the **single** integrator and never edit `en_us.json` themselves. Any change requiring a Java gameplay producer is a separate proposed feature, not an invitation to widen this plan. Preserve all unrelated worktree changes and include only reviewed task files in commits.
