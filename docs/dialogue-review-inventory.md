# MCA dialogue editorial inventory

Generated from the current worktree on 2026-11-11. The resource and placeholder checks are mechanical.
Candidate flags indicate where a writer should look; they are not findings. Editorial status stays pending until a full contextual reading and playtest.

**Events:** 87. Families: ambient 42, gameplay 3, location 2, personal 25, relationship 8, social 7.
**English packs:** 10. **English entries:** 5650.
**Question-ending terminal candidates:** 24.
**Missing graph targets:** 0; **unreachable nodes:** 0; **non-completing graph sinks:** 0; **unresolved direct English keys:** 0.
**Duplicate JSON keys:** 0; **missing required positional arguments in personality overrides:** 0.
**Authored social voice coverage:** 28 keys × 9 personality packs; **missing overrides:** 0.
**Story adventure choices:** 3 checked; **advancement-gated choices:** 0; **missing evidence-based outcome bonuses:** 0.

Each English key is listed in [dialogue-review-inventory.tsv](dialogue-review-inventory.tsv), including potential dynamic or legacy keys not visible in direct event references. An unreferenced key is not necessarily unused.
Review text against the [conversation writing guide](dialogue-conversation-writing.md), retaining the gameplay conditions and actual player choices.

## Event paths and triage

| Event | Mode | Nodes | Choices | Candidate question-ended terminals | Review |
| --- | --- | ---: | ---: | --- | --- |
| `ambient/baseline.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/biome_dry.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/biome_forest.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/biome_plains.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/biome_snowy.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/crabby_night.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/greet.json` | ambient | 5 | 3 | - | review_pending |
| `ambient/personality_anxious_sound.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_anxious_supplies.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_crabby_good_work.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_extroverted_neighbors.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_extroverted_square.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_flirty_compliments.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_flirty_gesture.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_friendly_chores.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_friendly_newcomer.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_gloomy_bright_spot.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_gloomy_tomorrow.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_greedy_bargain.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_greedy_fairness.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_introverted_hobbies.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_introverted_quiet_spot.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_odd_objects.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_odd_observations.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_peaceful_compromise.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_peaceful_morning.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_playful_contest.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_playful_rain_game.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_relaxed_afternoon.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_sensitive_kindness.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_sensitive_tone.json` | ambient | 1 | 0 | main | review_pending |
| `ambient/personality_upbeat_prediction.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/personality_upbeat_small_win.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/rain_relaxed.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/root/evening.json` | ambient | 1 | 0 | greeting | review_pending |
| `ambient/root/generic.json` | ambient | 1 | 0 | - | review_pending |
| `ambient/root/mayor.json` | ambient | 1 | 0 | greeting | review_pending |
| `ambient/root/monarch.json` | ambient | 1 | 0 | greeting | review_pending |
| `ambient/root/morning_early.json` | ambient | 1 | 0 | greeting | review_pending |
| `ambient/root/morning_late.json` | ambient | 1 | 0 | greeting | review_pending |
| `ambient/root/negative.json` | ambient | 1 | 0 | greeting | review_pending |
| `ambient/root/night.json` | ambient | 1 | 0 | greeting | review_pending |
| `gameplay/divorce_papers.json` | ask | 2 | 0 | - | review_pending |
| `gameplay/hire.json` | ask | 4 | 3 | - | review_pending |
| `gameplay/stay.json` | ask | 5 | 0 | fail,repeat,no_space | review_pending |
| `location/infirmary.json` | ask | 1 | 0 | - | review_pending |
| `location/prison.json` | ask | 1 | 0 | - | review_pending |
| `personal/cured_identity.json` | highlighted | 4 | 3 | - | review_pending |
| `personal/cured_zombies.json` | ask | 5 | 3 | - | review_pending |
| `personal/cured_zombies_followup.json` | ask | 3 | 2 | - | review_pending |
| `personal/gloomy_reflection.json` | highlighted | 6 | 2 | - | review_pending |
| `personal/gloomy_reflection_followup.json` | ask | 1 | 0 | - | review_pending |
| `personal/lactose_intolerance.json` | ask | 1 | 0 | - | review_pending |
| `personal/mourning.json` | highlighted | 3 | 2 | - | review_pending |
| `personal/mourning_depressed.json` | highlighted | 6 | 3 | - | review_pending |
| `personal/personality_anxious.json` | highlighted | 6 | 2 | - | review_pending |
| `personal/personality_anxious_followup.json` | ask | 1 | 0 | - | review_pending |
| `personal/personality_crabby.json` | highlighted | 6 | 2 | - | review_pending |
| `personal/personality_extroverted.json` | highlighted | 6 | 2 | - | review_pending |
| `personal/personality_flirty.json` | highlighted | 6 | 2 | - | review_pending |
| `personal/personality_friendly.json` | highlighted | 6 | 2 | - | review_pending |
| `personal/personality_greedy.json` | highlighted | 6 | 2 | - | review_pending |
| `personal/personality_greedy_followup.json` | ask | 1 | 0 | - | review_pending |
| `personal/personality_introverted.json` | highlighted | 6 | 2 | - | review_pending |
| `personal/personality_odd.json` | highlighted | 6 | 2 | - | review_pending |
| `personal/personality_peaceful.json` | highlighted | 6 | 2 | - | review_pending |
| `personal/personality_playful.json` | highlighted | 6 | 2 | - | review_pending |
| `personal/personality_playful_followup.json` | ask | 1 | 0 | - | review_pending |
| `personal/personality_relaxed.json` | highlighted | 6 | 2 | - | review_pending |
| `personal/personality_sensitive.json` | highlighted | 6 | 2 | - | review_pending |
| `personal/personality_upbeat.json` | highlighted | 6 | 2 | - | review_pending |
| `personal/revived.json` | highlighted | 3 | 2 | - | review_pending |
| `relationship/adopt.json` | ask | 3 | 2 | - | review_pending |
| `relationship/apologize.json` | ask | 3 | 1 | success,fail | review_pending |
| `relationship/divorce.json` | ask | 3 | 2 | - | review_pending |
| `relationship/flirt.json` | ask | 4 | 1 | fail,slap | review_pending |
| `relationship/hug.json` | ask | 3 | 1 | success,fail | review_pending |
| `relationship/kiss.json` | ask | 4 | 1 | success,fail | review_pending |
| `relationship/procreate.json` | ask | 3 | 2 | - | review_pending |
| `relationship/procreate_engaged.json` | ask | 1 | 0 | response | review_pending |
| `social/chat.json` | ask | 23 | 12 | - | edited_under_review |
| `social/first.json` | highlighted | 18 | 12 | - | edited_under_review |
| `social/joke.json` | ask | 19 | 11 | fail | edited_under_review |
| `social/local_area.json` | ask | 29 | 8 | - | edited_under_review |
| `social/rock_paper_scissor.json` | ask | 7 | 3 | win_paper,lose_paper | review_pending |
| `social/rumors.json` | ask | 4 | 0 | lore | review_pending |
| `social/story.json` | ask | 29 | 17 | - | edited_under_review |

## Machine-audit findings

All graph and translation checks are static. The directly referenced English keys and personality overrides were checked for existence and positional arguments. Dynamic lookups, context-dependent eligibility and spoken meaning still need human review.

### Missing targets (0)
- None found

### Unreachable nodes (0)
- None found

### Non-completing sinks (0)
- None found

### Missing English references (0)
- None found

### Duplicate JSON keys (0)
- None found

### Missing required translation arguments (0)
- None found


## Editorial review method

Review each event as a complete player/villager exchange. Follow all choices, confirm the next line responds to the chosen meaning, validate age, relationship, biome and completed-history context, then review all visible English variants. Retain natural existing text. Use Humanizer 4.0 only as a prose-editing lens.

Open questions and flags are potential work, not automatically errors. Many legacy keys are resolved dynamically and must not be deleted because they lack a direct event reference.

## Confirmed findings (initial focused review)

- Social First previously ended after each introduction response. Follow-up choices are being authored.
- Social First, Chat, Joke, Story and Local Area have authored follow-up choices; the First endings and follow-up questions in all five have nine distinct personality variants.
- Social Chat's ordinary questions no longer apply random negative-heart outcomes; the village-doubt responses now answer the villager's uncertainty rather than an unrelated player-history question.
- Nether, Dragon and Wither stories remain selectable without advancements; matching achievements increase the weight of positive villager reactions, while skeptical replies remain possible.
- The remaining 5,000-plus variants and 24 question-ending candidates have not undergone a complete in-game editorial acceptance review.
