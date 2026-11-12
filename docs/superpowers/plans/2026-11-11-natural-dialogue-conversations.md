# MCA Natural Dialogue Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Revised 2026-11-12 for the 45-event audit, location-only Prison/Infirmary dialogue and **separate anywhere-available Guard/Outlaw Ask topics**. Draft for review; **not authorized for execution**.
**Goal:** Make MCA 1.21.1 conversations contextually truthful, responsive and distinct, starting with the 45 Ask/Highlighted events, while retaining existing gameplay and story mechanics.

**Architecture:** Extend existing server-authored `DialogueEvent` JSON node graphs and English localization pools. **For Prison and Infirmary, keep only their existing `mca:in_building` event gates** and write truthful, role-neutral lines with a short meaningful player response and exit. **Separately add Guard-only and Outlaw-only Ask conversations eligible by `mca:profession` anywhere the villager can Talk**, each with distinct player replies and authored outcomes. Avoid new dialogue state, combat AI or UI architecture. Reuse completed history for follow-ups; keep command cleanup, pending-async and interaction lifecycle work independent.

**Tech Stack:** Java 21 project source, Minecraft 1.21.1, Fabric/NeoForge, JSON resources, JUnit 5, Gradle on Windows PowerShell.

**Spec:** `docs/superpowers/specs/2026-11-12-selectable-dialogue-content-quality-audit.md` (current focused priorities and context contract); see also `docs/superpowers/specs/2026-11-11-natural-dialogue-conversations-design.md` (earlier broader prose rules; its 87-event snapshot is stale).

## Global constraints

- Existing checkout `dev/1.21.1-dialogue-system-b`; no WSL, checkout or new worktree.
- Treat all pre-existing uncommitted files as user work. Re-read relevant diffs before every edit and stage/commit **only task-owned files** after approval.
- Reuse `DialogueEvent`, `DialogueEngine`, existing authoring rules and localization. Event JSON remains acyclic; stable event IDs, history, complete/ack semantics and topic menus remain intact.
- Ordinary conversations generally target two to four meaningful exchanges; ambient and service requests are exempt. This target is **proposed** and requires signoff.
- Current source count: **89** events, **44 ambient, 26 Ask, 19 Highlighted**. The focused triage already covers all 45 Ask/Highlighted resources; do not repeat that initial inventory. Rebaseline the old 87-event broader inventory, and separately review all changed English base/personality variants. Do not assume historic 5,605 key count.
- **Context correctness precedes writing polish.** A villager physically inside the prison or infirmary is not automatically incarcerated or a patient. Real Outlaw and Guard professions exist, but the latest user instruction is **"just keep being in building for now."** Do not add profession, workplace, prisoner, doctor or visitor gating to these events. No new role-persistence mechanism, condition, command API, menu packet or routing engine in this plan.
- **Ground every factual branch and preserve its neutral path.** Before changing a context-dependent line, identify the actual condition and what it proves: `mca:profession` (registered profession), `mca:in_building` (present location), `mca:building_assignment` (home/job-site anchor, **not** present job activity), `mca:relationship` / `mca:family` (actual relationship), or `mca:event_choice` (completed prior choice). If a fact is absent or unavailable, the villager must neither assert it nor become trapped without a valid neutral response/exit. Preserve existing fallback routing and effects.
- **Profession conversations are separate and anywhere-available.** The new Guard/Outlaw Ask topics use `mca:profession` conditions on their **own** event eligibility, **not** `mca:in_building`, and have branch-specific natural replies. Existing `social/chat` and `social/joke` remain unchanged. The current 89-event / 26-Ask baseline becomes 91 / 28 **only if both new events are implemented**.
- **Talk access and event eligibility are different.** The normal Talk menu should not reject a Guard or Outlaw **solely due to profession**, anywhere in the world; the Prison/Infirmary *events* still only appear inside their respective buildings. Existing `isConversationDangerous()` guards against panic, active attack targets and ignited creepers. Whether the user wants Talk **during** active Outlaw combat is unresolved and not implicitly approved here.
- One owner integrates shared `assets/mca_dialogue/lang/en_us.json`. Keep non-English files and unrelated UI, packets, async and command code untouched.
- Use upstream `C:\Users\mikol\.codex\skills\humanizer\SKILL.md` v4.0.0 for editorial review, without mechanically replacing every unusual phrase.
- Verify source and parser before changing engine behavior; propose another reviewed design if a missing capability appears. No generative dialogue or generated world facts.

## Ownership map

- Content: `common/src/main/resources/data/mca/dialogue_events/{social,personal,ambient,location,relationship,gameplay}/*.json` including `ambient/root/*.json`.
- Locale: `common/src/main/resources/assets/mca_dialogue/lang/en_us.json`; personality `common/src/main/resources/assets/mca_dialogue_*/lang/en_us.json`.
- Validation: `common/src/test/java/net/conczin/mca/dialogue/McaDialogueEventResourcesTest.java`, `DialogueSelectionTest.java`, `DialogueSessionTest.java`, `PersonalityDialogueLibraryTest.java`; focused additional tests only when existing cases do not cover a real regression.
- Relevant existing coverage to extend, **not recreate**: `common/src/test/java/net/conczin/mca/dialogue/NaturalDialogueContentTest.java`, `neoforge/src/main/java/net/conczin/mca/dialogue/DialogueConditionGameTests.java` (already distinguishes in-building visitors and workplace assignment) and `DialogueReferenceGameTests.java` (already checks infirmary eligibility/completion/cooldown).
- Editorial evidence: create `docs/dialogue-review-inventory.md` (summary, categories and decisions) and a machine-readable `docs/dialogue-review-inventory.tsv` only if the 5k-plus key audit needs per-key tracking. Keep this out of runtime resources.
- Authoring documentation: `docs/dialogue-authoring.md`, `docs/dialogue-datapack-guide.md`, `docs/dialogue-system-guide.md`, updating only rules actually changed.

## Review focus

The following high-risk cases must have an owning task test:
1. **In building but wrong role:** a villager physically in Prison/Infirmary never talks as a prisoner/patient, guard of this prison, or medical employee just because of location; profession and workplace make no difference to authored location lines in this batch. Test in Task 4A.
2. **No inn/no home/no event:** an authored reply falls back without pretending a building, biography or event exists. Test in Tasks 1 and 4B.
3. **Ordinary player question:** no random offensive reply or friendship loss without a provocative choice. Test in Task 1.
4. **Abandoned follow-up:** unfinished or interrupted conversations cannot unlock choice-dependent subsequent events or apply effects twice. Test in Tasks 5 and 9.
5. **Age/relationship boundary:** no romantic dialogue with children and no arbitrary established-partner rejection. Test in Task 7.
6. **Dynamic translation pool:** alternatives, personality overrides, placeholders and missing keys resolve safely across selected contexts. Test in Tasks 8 and 9.
7. **Profession context versus location:** Guard and Outlaw each see their own dedicated Ask topic **inside and outside prisons**; an ordinary villager sees neither. All three can see the same role-neutral Prison/Infirmary event when physically inside its building. Test in Tasks 4A and 4C.

---

## Priority and execution order

**P1a first:** Task 1 (reuse baseline) then **Task 4A Prison/Infirmary context correctness**. **P1b next:** **Task 4C Guard/Outlaw profession-specific Ask dialogue anywhere**, then Task 7 (Hire wording), Task 4B (Rumors), and the focused follow-up and listening work of Task 5. **P2:** the remainder of Task 5 (personality voice) and small targeted wording issues. **P3:** Tasks 8 and 9 (variant review and runtime acceptance). Tasks 2, 3 and 6 are older broad-plan candidates; existing First/Chat/Joke/Story and ambient work must not be rewritten without a current, specific gap. Do not equate task numbering with required execution order.

Each batch is an independent review gate. A passing existing test is useful baseline evidence: add a failing assertion only for a new, real content contract that current resources violate. No blanket line-count checks or forced two-to-four-exchange rule.

### Task 1: Reuse the existing inventory and record baseline

**Files:** Update existing `docs/dialogue-review-inventory.md` and `docs/dialogue-review-inventory.tsv` if the current source invalidates their lists. Reuse `NaturalDialogueContentTest.java`, `McaDialogueEventResourcesTest.java` and relevant selection tests.

**Interfaces:** Reads all event files and current English locale packs; produces an exact review matrix with event path, available choices, terminal paths, text key groups, quality category and keep/revise decision.

- [ ] **Step 1:** Confirm the current 89-file count and reconcile the existing 45-event triage against shipped Ask/Highlighted files. Update the stale 87-event inventory without discarding its historical findings. Separate actually presented, dynamically selected and apparently unused English keys.
- [ ] **Step 1A:** For each **flagged context-dependent path**, inventory its asserted game fact, supporting `DialogueCondition`, outcome if the requirement does not match, and existing fallback/exit. Record unsupported claims as revision targets; avoid adding a blanket test for every condition or rewriting already truthful paths.
- [ ] **Step 2:** Inspect the **already implemented** `NaturalDialogueContentTest.firstEncounterOffersCoherentFollowups` and `neutralSocialQuestionsNeverReachPunitiveReaction`; check they still assert useful behaviour. Reuse instead of re-creating them. Check earlier work against current graphs before proposing any additional test.
- [ ] **Step 3:** Run `.\gradlew.bat :common:test --tests net.conczin.mca.dialogue.NaturalDialogueContentTest --tests net.conczin.mca.dialogue.McaDialogueEventResourcesTest --tests net.conczin.mca.dialogue.DialogueSelectionTest --console=plain` as a fresh baseline when implementing. Record the **actual** results; they are not expected to fail without a new requirement.
- [ ] **Step 4:** Preserve already passing social-conversation tests; record which original recommendations are already implemented. Do not change social content here without a proven issue. Stage/commit only after separate permission.

### Task 2 (verify-only unless a new defect appears): Natural first meetings and Chat

**Files:** `common/src/main/resources/data/mca/dialogue_events/social/first.json`, `social/chat.json`; corresponding base `mca_dialogue/lang/en_us.json`; existing Task 1 tests.

**Interfaces:** Retain event IDs, existing `first` seen-memory behavior, completed history and existing selection priority.

- [ ] **Step 1:** Inspect existing First/Chat choices and their already-authored follow-ups; retain current inn/no-inn and neutral-heart tests, and check the separate missing-inn design has not been displaced.
- [ ] **Step 2:** Only if a new, specific mismatch is found, add the failing path assertion, run it RED, then revise the minimum affected node/key and rerun GREEN.
- [ ] **Step 3:** Read the affected English variants aloud and update the review inventory. Do not repeat already completed First/Chat improvements or invent another conversation system.

### Task 3 (verify-only unless a new defect appears): Jokes and Stories that actually respond

**Files:** `social/joke.json`, `social/story.json`; base and applicable personality English packs; `McaDialogueEventResourcesTest.java`, `DialogueSessionTest.java`.

**Interfaces:** Preserve existing advancement conditions, subject identities, completion, cooldown/history and valid heart actions.

- [ ] **Step 1:** Read the existing three Joke subject branches, five Story subjects, their choice replies and outcome variants; check already-written follow-ups, achievement-weighted skepticism and topic memories before assuming they are missing.
- [ ] **Step 2:** If a real unresponsive choice or unearned claim is found, write the smallest failing resource/history test, confirm RED, revise the affected node/key, and verify GREEN.
- [ ] **Step 3:** Keep `/N` keys as alternate phrasings rather than additional spoken turns; preserve topic memory, effect timing, history and the existing story skepticism design. Record only remaining content defects in the inventory.

### Task 4A (P1a): Truthful Prison and Infirmary location conversations

**Files:**
- Modify: `common/src/main/resources/data/mca/dialogue_events/location/prison.json` and `location/infirmary.json`.
- Modify: `common/src/main/resources/assets/mca_dialogue/lang/en_us.json` for the matching location conversation keys.
- Test: `common/src/test/java/net/conczin/mca/dialogue/NaturalDialogueContentTest.java`, `neoforge/src/main/java/net/conczin/mca/dialogue/DialogueReferenceGameTests.java`.
- Reuse existing coverage: `neoforge/src/main/java/net/conczin/mca/dialogue/DialogueConditionGameTests.java`.

**Interfaces:** Keep `mca:in_building` as the **only** location eligibility condition; Prison uses `value: "prison"` and Infirmary uses `value: "infirmary"`. Do **not** add `mca:profession`, `mca:building_assignment`, conditional outcomes, role state or an AI/interaction change for these events. Keep original event IDs, Ask mode and prompts, five-second repeat policy, history policy, priority and completion/ack semantics.

- [ ] **Step 1:** In `NaturalDialogueContentTest` add `locationConversationsDescribePlaceWithoutAssigningRoles`. Decode both resources; assert unchanged `mca:in_building` gates, absence of new profession/workplace role requirements, a reachable optional question/reaction with a meaningful answer, and a polite complete exit. Do **not** enforce arbitrary passage counts or a new outcome router.
- [ ] **Step 2:** Run `.\gradlew.bat :common:test --tests net.conczin.mca.dialogue.NaturalDialogueContentTest --console=plain`. Expected: the new content test fails because the current location events are three-line monologues without player responses.
- [ ] **Step 3:** Rewrite the Prison and Infirmary lines as observations that any villager inside could plausibly make; add the optional question/reply and an exit. Do not claim to be a prisoner, guard of this building, patient, clinician, assigned worker or definite visitor. Preserve old translation keys until checked for other references.
- [ ] **Step 4:** Rerun the common test to GREEN. Check locale keys, choice IDs, accessible reply/exit paths, valid completion and unchanged cooldown. Read each line in the player's and villager's voices.
- [ ] **Step 5:** Extend `DialogueReferenceGameTests` with `buildingConversationsAreLocationOnly` if existing tests do not already cover the distinction: ordinary villagers inside the corresponding room see the Ask topic, outside do not. Profession/workplace status must not change the event graph; preserve `infirmaryEventUsesCurrentBuildingAndCooldownExpires` and reuse existing `DialogueConditionGameTests` coverage.
- [ ] **Step 6:** Run `.\gradlew.bat :neoforge:runGameTestServer -PmcaGameTest=DialogueReferenceGameTests --console=plain` and focused existing building-condition tests. Expected: location eligibility, conversation completion and cooldown still pass.
- [ ] **Step 7:** Manually try both location conversations with regular villagers inside/outside; check optional replies and exits. Also try Guards/Outlaws where safely reachable. Keep these location events **free of profession branches**; any new profession-specific Ask topics belong to Task 4C.

### Task 4C (P1b): Guard and Outlaw profession-specific Ask dialogue anywhere

**Files:**
- Create: `common/src/main/resources/data/mca/dialogue_events/social/guard_duties.json`, `social/outlaw_reputation.json`.
- Modify: `common/src/main/resources/assets/mca_dialogue/lang/en_us.json` for both prompts, villager lines and player-choice replies.
- Test: `common/src/test/java/net/conczin/mca/dialogue/NaturalDialogueContentTest.java`, `McaDialogueEventResourcesTest.java`; extend `neoforge/src/main/java/net/conczin/mca/dialogue/DialogueReferenceGameTests.java` only if existing engine tests do not exercise profession eligibility in Talk.

**Interfaces:** Each new event uses `trigger: "talk"`, `presentation.mode: "ask"`, a distinct topic-appropriate prompt, normal authoring cooldown and `requirements: [{"type":"mca:profession","value":"mca:guard"}]` or `mca:outlaw` respectively. These are **event eligibility conditions**, not a condition on the player's location or the existing `social/chat` graph. Do **not** require `mca:in_building` or `mca:building_assignment`. `social/joke.json` already uses `mca:profession = mca:guard` as a proof of existing authoring support. Leave `social/chat`, joke hearts, history and all current 89 event IDs intact. Names above are proposed new IDs; they do not exist yet.

- [ ] **Step 1:** In `NaturalDialogueContentTest` add `guardAndOutlawAskTopicsAreProfessionScopedAnywhere`: parse each new event and assert only its matching profession requirement, an Ask prompt, at least **two player-choice routes with distinct reply keys and a polite exit**, and valid complete terminals. Check no `mca:in_building` or invented role condition appears in either new event. Review the actual lines editorially in Step 4 for meaning and responsiveness.
- [ ] **Step 2:** Run `.\gradlew.bat :common:test --tests net.conczin.mca.dialogue.NaturalDialogueContentTest --console=plain`. Expected: new content test RED because the two event resources are absent.
- [ ] **Step 3:** Author `guard_duties.json` about protecting others, difficult safety decisions and fatigue or vigilance, and `outlaw_reputation.json` about how the Outlaw designation affects reputation and trust. Each player option must lead to a **specific** answer: for Guard, "What do you look out for?" vs "Does it weigh on you?"; for Outlaw, "Can people trust you again?" vs "How do they treat you?" Include a short exit. Don't assert a witnessed attack, committed crime, detention, prisoner status, prison posting or personal history. No new hearts/rewards, history memory or command effects in this batch.
- [ ] **Step 4:** Rerun targeted common tests to GREEN, check all authored locale keys/variants and verify both new graphs are acyclic and complete. Preserve existing generic Chat/Joke routes and outcome weights.
- [ ] **Step 5:** Add focused eligible/ineligible GameTest coverage only if current selection tests cannot prove it: Guard sees Guard Ask (not Outlaw), Outlaw sees Outlaw Ask (not Guard), ordinary villager sees neither, and the eligible topic appears at **two distinct world locations including outside any prison**. Verify `location/prison` still depends only on being inside a prison. Run the scoped GameTest suite, retaining all unrelated dialogue events.
- [ ] **Step 6:** Manually play every reply path for calm Guard and calm Outlaw inside/outside a prison on an actual client and confirm normal Talk can open. `VillagerEntityMCA.isConversationDangerous()` blocks panic/attack-target situations; document an active-combat Outlaw limitation rather than silently altering hostile AI, GUI access or interaction command routing. If a **calm** Outlaw cannot access Talk, report a separate interaction defect needing approval, not an excuse to skip content verification.

### Task 4B (P1b/P2): Rumors and truthful area commentary

**Files:** `common/src/main/resources/data/mca/dialogue_events/social/rumors.json` and selectively `social/local_area.json`; matching English locale; `NaturalDialogueContentTest.java` and existing selection/resource tests. Do not modify `DialogueAsyncResolver` or the structure-location async protocol.

**Interfaces:** Reuse `mca:structure_location`, `configured_rumors` and the existing failure line. Keep its real coordinates, ordering/acknowledgement and current heart-weight eligibility. Preserve current/home biome lookup and generic fallback for `local_area`.

- [ ] **Step 1:** Add a failing `rumorsPositiveResultsOfferOptionalReply` content test: the `location` and `lore` results offer a short topic-relevant player reply or a clear close option, while the `failed` outcome may finish immediately. A resolved node must retain its **single** resolved `line` and existing `mca:structure_location` configuration; `DialogueEvent.Node` allows a resolved single-line node to have choices **instead of** `complete`, not both.
- [ ] **Step 2:** Run the targeted common content test. Expected: RED for the current `location` and `lore` nodes, which complete without choices.
- [ ] **Step 3:** Give location/lore results optional reply branches and terminal acknowledgements using existing JSON graph primitives. Do not alter the weighted route, async resolver, success/failure coordinate formatting or literal legacy lore canon without a separate editorial decision. Change local-area fallback text only if a fresh full-path reading shows a factually wrong claim.
- [ ] **Step 4:** Run focused common tests to GREEN and add a resolver lifecycle regression only if the new choices expose a meaningful pending/acknowledgement risk. In-game, verify a real resolved-coordinate result and failed lookup display correctly **before** player replies become actionable. Record locale/personalities and any remaining runtime limitation.

### Task 5: Personal stories and recorded-history follow-ups

**Files:** In P1b, `personal/gloomy_reflection_followup.json`, `personal/personality_{anxious,greedy,playful}_followup.json`, `personal/mourning_depressed.json`, and their base English translations; in P2, only flagged `personal/personality_*.json`. Test `NaturalDialogueContentTest.java`, `PersonalityDialogueLibraryTest.java`, `DialogueSessionTest.java` and existing history tests.

**Interfaces:** Preserve existing personality IDs, event/choice IDs (especially `make_plan`, `share`, `apologize` and `comfort`), prerequisites, pause/resume and per-player **completed** choice history. The four follow-ups already exist; no new memory registry or second event engine. Any change to once-only/cooldown behaviour requires explicit review, not an incidental editorial rewrite.

- [ ] **Step 1:** Write focused tests verifying that each P1b follow-up still requires completion and the specific earlier choice, offers a readable topic-specific prompt, and ends through a useful, naturally responsive branch. For `mourning_depressed`, assert the listen path offers a meaningful continuation, while dismissal/space endings remain safe and short.
- [ ] **Step 2:** Run `.\gradlew.bat :common:test --tests net.conczin.mca.dialogue.NaturalDialogueContentTest --tests net.conczin.mca.dialogue.PersonalityDialogueLibraryTest --console=plain` and confirm new tests fail for the specific missing response/prompt, not existing valid history behaviour.
- [ ] **Step 3:** Author only P1b revisions first: a relevant optional player reaction and matching villager answer, accurate Ask labels, and a meaningful listening response. Keep every old history dependency, identity and eligible outcome intact; no fabricated passage of time or relationship.
- [ ] **Step 4:** Rerun focused tests, walk all edited paths, and check repeat/cooldown presentation. Later, review overlapping Friendly versus Peaceful and other interchangeable advice-and-agreement personality stories as **P2 editorial batches**, preserving stronger exceptions (e.g. Odd, Crabby, Cured Identity). Update the review inventory for changed paths.

### Task 6 (deferred unless a new problem is found): Ambient and root greeting quality

**Files:** `ambient/*.json` and `ambient/root/*.json` (**44 current files**, including Busy and Go away); matching base English packs; `DialogueSelectionTest.java`. The current user-approved audit priority is Ask/Highlighted, so do not initiate a blanket ambient pass.

**Interfaces:** Keep ambient selection, weighted priority, cooldowns and fallback baseline. Most lines remain short observations with no choice.

- [ ] **Step 1:** Write one focused test for a possible starvation/fallback regression identified by the inventory, including a villager with no applicable personality/context event.
- [ ] **Step 2:** Run RED, then edit overlapping generic greetings, incongruous weather/biome remarks and repetitive personality messages only where required.
- [ ] **Step 3:** Verify representative personalities, all root times, fallback and cooldowns; run tests and record decisions for all 44 entries if the broader audit is separately approved.

### Task 7 (P1b for Hire, otherwise conditional): Relationships and consequential gameplay

**Files:** `relationship/{adopt,apologize,divorce,flirt,hug,kiss,procreate,procreate_engaged}.json`; `gameplay/{divorce_papers,hire,stay}.json`; English pack; `McaDialogueEventResourcesTest.java`, `DialogueSelectionTest.java`.

**Interfaces:** Existing command actions, romantic age/family restrictions, partner-positive tier and slap handling are immutable unless separately approved.

- [ ] **Step 1:** In `NaturalDialogueContentTest` add `hireOfferDoesNotUseContractAcceptedSpeech`. Assert the initial `offer` node and successful `hired` node no longer use the same locale key. Verify the existing 3-day/5-emerald and 7-day/10-emerald actions remain attached to the same choices with the same affordability requirements.
- [ ] **Step 2:** Run the targeted common test and confirm RED for the shared initial/success text key, then introduce a distinct **pre-agreement** offer key and accurate English copy. Keep success, no-money, cancel, server command dispatch and interaction closure exactly as before.
- [ ] **Step 3:** Rerun to GREEN and inspect the live Hire confirmation through the UI. Defer changes to the other relationship/service events unless a specific client defect is reproduced; in particular, silent Confirm/Cancel echoes are a **verify-only** concern, not yet a proven bug. Stage/commit only after separate authorization.

### Task 8: Full English dialogue pool audit and editorial integration

**Files:** Every current `assets/mca_dialogue/lang/en_us.json` and `assets/mca_dialogue_*/lang/en_us.json`; inventory; `McaDialogueEventResourcesTest.java`, `PersonalityDialogueLibraryTest.java`.

**Interfaces:** Single shared-base-locale integrator. Preserve dynamic personality/gender pools, translatable formatting, key identities and non-English packs.

- [ ] **Step 1:** Start with every **changed** English key and its numbered `/1..N` variants and personality overrides, reviewing each in its actual path and checking positional arguments. Only broaden to the full base/personality locale inventory if the independent 89-event editorial completion pass is approved; track keep/revise/unused decisions.
- [ ] **Step 2:** Add a failing test for any discovered missing key, placeholder incompatibility or personality-fallback breakage, not thousands of static assertions duplicating the file.
- [ ] **Step 3:** Fix only confirmed naturalness/consistency defects in manageable locale batches, retaining better original jokes, idiosyncrasies and contextual voice.
- [ ] **Step 4:** Run translation/resource tests after each batch; compare key sets/placeholders; verify changed texts with the associated path, and preserve old dynamically used keys until verified unused. Record the changed-key inventory and report whether the broader key audit remains pending.

### Task 9: Integrated verification and authoring guidance

**Files:** Existing dialogue tests, `docs/dialogue-authoring.md`, `docs/dialogue-datapack-guide.md`, `docs/dialogue-system-guide.md`, review inventory.

**Interfaces:** No new UI, protocol or engine owner; coordinate with the existing master design and pending async/lifecycle plans.

- [ ] **Step 1:** Add or extend a real session regression test for the full sequence: choose player line, reveal villager reply, acknowledge, history/effect commits once; interrupted/abandoned runs do not unlock later follow-ups.
- [ ] **Step 2:** Run targeted test RED for any actual behavior gap; make minimal approved fix only if required, then GREEN.
- [ ] **Step 3:** Update authoring docs to explain natural dialogue structure, when to use separate follow-up events, non-cyclic graphs and player-speech keys.
- [ ] **Step 4:** Run `.\gradlew.bat :common:test :fabric:build :neoforge:build --console=plain` and `git diff --check`. Report unrelated/pre-existing failures exactly. Check English JSON parsing and event inventory complete against the filesystem.
- [ ] **Step 5:** Separately playtest (or mark unverified) **Prison/Infirmary** with ordinary villagers inside and outside, confirming meaningful replies and no false role claims. Then test **Guard-only and Outlaw-only Ask prompts and their distinct player branches inside and outside prisons**, and record active-combat limitations separately. Test Rumors resolver success/failure, follow-up choice history, Hire acceptance and failure, and the relevant edited personalities. Check first meeting, Chat/Joke/Story, child/adult and partner cases, long text reveal, choice scrolling and exit/resume only to the extent touched by the work. Test both loaders where practical.
- [ ] **Step 6:** Review against every spec criterion, record remaining editorial limitations, and stage/commit only owned changes.

## Handoff

**Approval required before Task 1:** The revised focused spec and this draft plan must be reviewed. The user has expressed interest in context-aware authored dialogue but **has not approved product edits**. Recommend **native execution with a fresh final reviewer** because most tasks share the same locale and event contracts. The user may instead choose subagent-driven delivery with one integrator controlling the base English locale. No work starts until the design and execution method are agreed. The separate interaction legacy-retirement/`InteractionCommands` work is already implemented and should not be reopened by this plan.

