# Unified Dialogue Flow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make MCA Talk coherent: existing romantic partners welcome flirting, player actions read as exchanges, and categories/navigation make sense.

**Architecture:** Keep the current server-owned `DialogueEvent` engine, pending effects and parchment `InteractScreen`. Fix authored data, carry existing `presentation.topic` metadata to the client, and improve the existing Talk list and controls. No new dialogue engine, UI framework or compatibility layer.

**Tech Stack:** Minecraft 1.21.1, Java 21, Fabric and NeoForge, Gson datapacks, Mojang codecs, JUnit, Gradle.

**Spec:** `docs/superpowers/specs/2026-10-12-unified-dialogue-flow-design.md`

## Global Constraints

- Work only in the existing isolated `dev/1.21.1-dialogue-system` worktree: `C:\Users\mikol\.codex\worktrees\dialogue-event-overhaul\minecraft-comes-alive-dev-1.21.1`. Never edit the unrelated dirty main checkout. No push/merge.
- Flirt is **always positive** for a valid adult romantic partner (spouse/engaged/promised). Partner Hug/Kiss cannot give punitive rejections; nonpartner Flirt odds improve at **30** and **60** hearts. Preserve adult, blood-family and other eligibility rules.
- Keep existing server authority, session/token validation, datapack extension points, cooldowns, reward/history semantics, parchment UI, upper-left villager information, localized text and both loaders.
- **Minimal TDD:** One targeted RED→GREEN regression per genuinely changed behavior, preferably in an **existing** test class. Tests hold fixtures and assertions, **never production algorithms or implementation**. Do not introduce production functions/classes just for tests; no large mock framework or redundant suites. Run `:common:test` before calling any task done.
- Screenshots from previous discussions are stale; assess UI using current source and actual in-game behavior where possible.

## Review Focus

- Married/promised/engaged villager with low hearts or an odd personality: Flirt must not reject or slap.
- Nonpartner at 29/30/59/60 hearts: outcome chance increases at thresholds without making strangers guaranteed.
- Datapack `topic` missing/unknown: option remains available as **Other topics**, and does not break the bounded packet.
- Back/Escape or history review before Finish: no premature/duplicate hearts, commands or stale choices.
- Small GUI scale with long text: selectable rows, history controls and Finish must remain reachable.

---

### Task 1: Fix Affection Outcomes and Show the Player's Words

**Files:** Modify `common/src/main/resources/data/mca/dialogue_events/relationship/flirt.json`, `hug.json`, `kiss.json` (same directory), relevant English text in `common/src/main/resources/assets/mca_dialogue/lang/en_us.json` and existing personality English packs as needed; test in `common/src/test/java/net/conczin/mca/dialogue/McaDialogueEventResourcesTest.java`.

**Interfaces:** Use existing `mca:relationship` value `romantic_partner`, `mca:hearts` and `DialogueEvent.Outcome.priority`; each event begins with a visible `line` and genuine first-person `choices` whose accepted route uses authored weighted outcomes.

- [ ] **RED:** Add `affectionStartsWithPlayerChoiceAndPartnerCannotReject()` to the existing resource test. Decode the real three events; assert each starts with a visible villager line and selectable player utterance, a partner-eligible highest-priority positive outcome overrides rejection/slap, and Flirt's nonpartner success weighting rises at hearts 30 and 60. Test the real decoded event/outcome behavior, not a reimplementation of selection.
- [ ] Run `.\gradlew.bat :common:test --tests net.conczin.mca.dialogue.McaDialogueEventResourcesTest --console=plain`. **Expected:** new regression fails on current automatic reaction/partner weights.
- [ ] **GREEN:** Change three JSON event graphs and minimal English keys. Make the visible offered line an invitation, then the actual player dialogue a choice before reaction. Use partner priority **100** positive-only, nonpartner heart tiers **10** (30+) and **20** (60+), retaining normal low-heart/personality failure paths. Do not give partner slap/negative actions. Keep server-only completion and existing restrictions.
- [ ] Rerun targeted test and `.\gradlew.bat :common:test --console=plain`. **Expected:** all pass. Stage only edited JSON/locales/existing test and commit `fix(dialogue): make affection exchanges relationship-aware`.

### Task 2: Send Event Topic Metadata to Talk

**Files:** Modify `common/src/main/java/net/conczin/mca/dialogue/DialogueEngine.java`, `common/src/main/java/net/conczin/mca/network/s2c/InteractionDialogueOptionsResponse.java`, existing callers, and `common/src/test/java/net/conczin/mca/network/DialogueEventCodecTest.java`.

**Interfaces:** Extend `DialogueEngine.EventOption(ResourceLocation id, Component prompt, String topic)` and `InteractionDialogueOptionsResponse.EventOption(Mode mode, ResourceLocation id, Component prompt, String topic)`. Server maps absent, blank or too-long topics to `other`; packet string max **64** chars. Keep 64-option cap, ordering, tokens and event IDs unchanged.

- [ ] **RED:** Extend existing packet round-trip test with a `relationship` topic and missing/unknown topic cases, asserting unchanged option identity and decoded topic. **Expected:** current option protocol cannot retain topic.
- [ ] Run `.\gradlew.bat :common:test --tests net.conczin.mca.network.DialogueEventCodecTest --console=plain`. **Expected:** new regression fails for missing topic support.
- [ ] **GREEN:** Plumb authored semantic topic through server option and existing options codec; normalize unsafe/missing values before encoding. No new packet type, filename-based category inference or event selection changes.
- [ ] Rerun targeted test and `.\gradlew.bat :common:test --console=plain`. **Expected:** pass. Commit `feat(dialogue): send semantic topic in menu options`.

### Task 3: Fix Existing Talk Menu and Navigation

**Files:** Modify `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java` and `common/src/main/resources/assets/mca/lang/en_us.json`; extend existing `common/src/test/java/net/conczin/mca/network/DialoguePresentationTest.java` only for a real state regression.

**Interfaces:** Consume Task 2's `EventOption.topic()`. In **one scrollable existing panel**, group `small_talk/location/game` as Conversations (include ambient), `relationship` as Relationships, `personal/life_event/introduction` as Personal Stories, `service` as Practical Matters and absent/unknown as Other Topics. Each section is collapsible without a server request. Continuation stays prominent; highlighted events remain accessible.

- [ ] **RED:** Add one narrow `DialoguePresentationTest` regression demonstrating a stale offer/history review cannot expose old response choices or trigger progression. If section grouping genuinely needs an extracted production helper to keep the GUI readable, write **one** grouping-behavior test first; otherwise use the existing UI code directly. **Expected:** new behavior fails where absent; do not manufacture a failing test for guards already working.
- [ ] Run `.\gradlew.bat :common:test --tests net.conczin.mca.network.DialoguePresentationTest --console=plain` (and the one grouping test only if needed). **Expected:** intended new failure, not unrelated errors.
- [ ] **GREEN:** Replace mixed `Ask about...` with topic sections in `InteractScreen`. Keep player replies distinct from read-only earlier/latest history controls. Label ordinary advance **Continue**, terminal acknowledgment **Finish conversation**, active Q **Topics**, menu Q **Back**, Esc **Close**. Maintain existing tokened `InteractionDialogueAdvanceMessage` and server `ENDED` response → topics. Do **not** commit effects on Back/Q/Escape, change conversation engine or introduce a second client state machine.
- [ ] Rerun targeted and `.\gradlew.bat :common:test --console=plain`; compile `.\gradlew.bat :common:compileJava --console=plain`. **Expected:** all pass. Commit `feat(dialogue): clarify Talk categories and navigation`.

### Task 4: Repair Remaining Real Mismatches and Verify

**Files:** Only actually broken events under `common/src/main/resources/data/mca/dialogue_events/`, their English locale keys, `docs/dialogue-authoring.md` and optionally `docs/dialogue-system-guide.md`. Reuse existing `McaDialogueEventResourcesTest` if an additional content bug needs regression coverage.

**Interfaces:** Keep genuine conversations distinct from consequential gameplay actions (hire, stay, divorce, adoption, procreation); retain authored requirements, explicit confirmations and server-authoritative commands.

- [ ] Inspect remaining offered Talk events for automatic reactions to unspoken player requests or mismatched replies. For each **real** bug found, add a single focused regression to the existing resource test and run it RED. If none, do not add speculative tests.
- [ ] Fix only confirmed content/translation errors and rerun affected tests GREEN. Update authoring guide explaining `mode` vs `topic`, relationship priority, player choices and Finish semantics. No tests for human prose.
- [ ] Run `.\gradlew.bat :common:test :fabric:build :neoforge:build --console=plain` and `git diff --check`. **Expected:** builds and tests pass or exact blockers are reported. If possible check actual 1.21.1 GUI at small/large scales, partner/nonpartner heart/personality combinations, Back/resume/history/Finish, stale selection, and unknown topic. Never claim in-game checks that did not run.
- [ ] Commit only actual changes (skip empty commit), report hashes, verified checks and remaining limitations. Never push/merge.

## Execution Handoff

User selected **superpowers:executing-plans** (inline/native), not subagent-driven execution. **Before production changes**, ask for review of this written plan per `superpowers:writing-plans`. After approval, read `superpowers:executing-plans`, initialize its ledger and execute Tasks 1–4 in order using minimal RED→GREEN tests and per-task commits.
