# Dialogue Fatigue Parity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement task-by-task after approval. Track steps with checkboxes.

**Status:** Implemented in the dialogue worktree; automated verification passed. Live client acceptance remains pending. See `.superpowers/sdd/2026-11-12-dialogue-fatigue-parity/progress.md` for evidence and limitations.
**Goal:** Restore the `dev/1.21.1` fatigue contributions and busy/go-away exits in the new dialogue system, with an explicit default-enabled MCA fatigue option.
**Architecture:** Reuse pair memory and outcome fatigue; add the config boolean, ambient event fatigue and a terminal close flag. Keep one weighted picker and the existing interaction owner/ID-scoped close protocol.
**Tech Stack:** Minecraft 1.21.1, Java 21, Minecraft codecs, JUnit and existing NeoForge dialogue GameTests; Fabric and NeoForge.
**Spec:** [fatigue parity design](../specs/2026-11-12-dialogue-fatigue-parity-design.md).

## Global constraints

- Reference `dev/1.21.1` at `a111ada3378a0c6adb2c59fae68967c36054f88d`; implement in the current dialogue worktree. No new checkout, branch switch, WSL, staging, commit or push.
- Preserve all concurrent edits, neutral-chat no-penalty behaviour, priority-100 partner success and existing story memory/advancement bonuses.
- No new fatigue counter, introvert multiplier, mandatory cooldown or legacy engine. Continue using committed nonzero hearts and existing 4,800-tick default decay.
- Event/outcome rates are finite nonnegative doubles defaulting to 0; preserve the November 11 two-cast fatigue formula and global-config sanitization.
- Add only `Config.interactionFatigueEnabled = true` beside existing `interactionChanceFatigue = 1.0f` and `interactionFatigueCooldown = 4800`. When enabled, keep existing gain/decay/weighting. When disabled, suppress fatigue gain and all fatigue-based outcome/ambient weight contributions but **continue decay** of preexisting per-pair fatigue. Never wipe, freeze or migrate saved fatigue. Re-enabling uses whatever saved fatigue remains. Multiplier 0 suppresses weighting, **not gain**, while the boolean is enabled.
- Preserve backwards compatibility: missing new config entry defaults to enabled; omitted datapack `fatigue` and `close_interaction` keep defaults 0/false; legacy positive-weight event/outcome packs, resources, save NBT and protocols still work. Only finite nonpositive-base entries with positive fatigue rate may be introduced as new data.

## Review focus

1. Split conditional failures must not receive F several times (Task 1 grouped audit).
2. Filter nonpositive effective outcomes **before** highest-priority selection, otherwise disabled high-priority outcomes mask lower-priority usable ones. Use the same rule for `choiceEligible`, selection revalidation and automatic routing, with safe all-zero handling (Tasks 1-2).
3. Opening preview and explicit AMBIENT selection must use the same current pair/config (Task 2).
4. Refusal cannot be bypassed through topic/resume selection; old acknowledgements cannot close a newer interaction (Task 2 server checks).
5. Pure ambient speech still does not build fatigue; a successful test must use committed heart actions and the actual current jar (Task 3).
6. Switching off with accumulated fatigue immediately suppresses weighted effects and further gain, while normal cooldown decay continues and re-enabling restores the remaining saved influence (Tasks 0-3).

### Task 0: Add the explicit default-on config gate

**Modify:** `common/src/main/java/net/conczin/mca/Config.java`, the existing per-player/villager fatigue gain owner in `VillagerBrain.java`, and the shared fatigue-weight calculation in `DialogueEngine.java` when Tasks 1-2 wire it. Keep config and memory serialization otherwise unchanged.
**Tests:** focused config-default/heart-commit/decay checks plus the existing dialogue selection tests; cover same-pair and other-pair isolation.

- [ ] Add documented `public boolean interactionFatigueEnabled = true` next to `interactionChanceFatigue` and `interactionFatigueCooldown`; old config files without the new field remain enabled. Do not add a second multiplier, datapack flag, migration, or memory counter.
- [ ] Gate only the fatigue increment on each committed nonzero `rewardHearts` when disabled; continue applying hearts, deferred actions and other effects. Keep natural positive-fatigue decay every `max(1, interactionFatigueCooldown)` ticking-villager ticks, whether the switch is on or off.
- [ ] Gate outcome and event fatigue contributions using the same current server config and pair memory. Disabled means effective F=0 for weighting regardless of stored fatigue; re-enable restores any remaining amount. `interactionChanceFatigue=0` with the boolean on keeps increment and decay active. Do not treat a disabled switch as an invitation to reset NBT.
- [ ] Verify an enabled default, disabled-before-first-interaction, disabled-with-existing-F, toggle-back-on, and multiplier-0 cases. Capture unchanged non-fatigue behaviour and ensure another player's pair memory is unaffected.

### Task 1: Restore reaction contributions

**Modify:** `common/src/main/java/net/conczin/mca/dialogue/DialogueEvent.java` (Outcome validation), `DialogueEngine.java` (effective-positive filtering), and the seven JSON files listed in the spec table.
**Tests:** existing `common/src/test/java/net/conczin/mca/dialogue/{DialogueEventParsingTest,DialogueSelectionTest,NaturalDialogueContentTest,McaDialogueEventResourcesTest}.java`.
**Interface:** retain `Outcome.fatigue()` and `pickOutcome(List<Outcome>, RandomSource, int, float)`; no new counter or action.

- [ ] Add one table-driven resource/selection audit, using the spec's legacy groups. Calculate total eligible failure weights at F=0 and F=6 for ordinary, odd, flirty, child and romantic-partner contexts. Assert each intended group gains exactly 6, bonus entries gain 0, and short greeting/partner outcomes remain protected. Kind failure contributes max(0,F), plus its existing odd bonus.
- [ ] Run the four classes with `:common:test --tests` and observe missing fatigue/weight-0 decoding failures, not fixture errors.
- [ ] Permit finite nonpositive base weight only on outcomes with positive fatigue rate. After requirements, filter nonpositive **effective** weights **before** highest-priority selection; a disabled priority-100 fatigue-only outcome must not suppress a positive priority-0 option. Apply the same predicate to `choiceEligible` and revalidate choice clicks (including stale offers), hiding/rejecting all-zero choices without effects. For all-zero automatic routing, follow a bounded no-eligible/fail-closed path without throwing, retry loops, or completing actions. Annotate only baseline entries; add the missing unconditional kind failure. Preserve the ordinary positive fallback and follow-up graph.
- [ ] Test mixed priority positive/zero effective candidates, all-zero choices and automatic nodes with the boolean enabled/disabled and multiplier 0. Confirm unmarked positive-weight packs choose with unchanged priority and RNG behaviour.
- [ ] Rerun those classes; verify unmarked packs and the existing five-story audit still pass. Do not replace existing tests with assertions copied solely from the new JSON.

### Task 2: Restore refusals at conversation opening

**Modify:** `DialogueEvent.java`, `DialogueEngine.java`; inspect/update `common/src/main/java/net/conczin/mca/network/ClientHandlerImpl.java` and `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java` only if acknowledgement presentation needs it. Existing ID-scoped `OpenGuiRequest` is the close transport.
**Create:** `common/src/main/resources/data/mca/dialogue_events/ambient/root/{busy,goaway}.json`.
**Tests:** `DialogueEventParsingTest.java`, `DialogueSelectionTest.java`, `DialogueOwnershipReleaseTest.java` and `neoforge/src/main/java/net/conczin/mca/dialogue/DialogueEngineGameTests.java`.
**Interfaces:** event `double fatigue` defaults 0; node `boolean closeInteraction` maps to optional JSON `close_interaction`, default false. Preserve current record constructors with compatibility overloads. Extend `pickPreview` with pair-fatigue/config arguments; retain the old test-facing overload with zero fatigue.

- [ ] Add focused codec/selection cases for event-rate defaults/invalid values, restricted ambient use, terminal-close validation, and refusal weights with the config boolean enabled and multiplier 1: F=3 -> 0:0, F=4 -> 1:0, F=5 -> 2:0, F=6 -> 3:1. Verify disabled boolean with saved F=6, zero multiplier while enabled, and negative pair fatigue all exclude both. Include a disabled high-priority event and all-zero candidate set; regular positive ambient entries must remain eligible.
- [ ] Confirm RED, then implement event-weight calculation through the existing picker in preview and AMBIENT resolution, using the same current config gate as outcome selection. Filter zero-effective events before priority/anti-repeat logic. Revalidate when config changes between preview and choice/acknowledgement; stale selections must not revive a disabled refusal. Update strict allowlists, external-ID reconstruction and compatibility constructors alongside codecs.
- [ ] Add the two spec-defined refusal resources. For a refusal preview, expose acknowledgement rather than the current terminal-preview NONE action, suppress menu choices/continuations, and enforce refusal offer restrictions server-side. Apply terminal close on acknowledgement through the existing owner-scoped stop/CLOSE path; preserve normal completion and early-close paths.
- [ ] Add a bounded server case using real player/villager memories and deterministic selection: display refusal, reject a topic/resume bypass, acknowledge, and assert session/offer/interaction release. Extend the existing ownership test to reject a stale acknowledgement after interaction replacement. Assert another player/villager is unaffected. Run the affected tests together.
- [ ] Verify that missing `fatigue`/`close_interaction` fields preserve old datapack decoding and unmarked weights; old resource IDs/translations and existing pair-fatigue NBT need no migration. Refusal resources remain valid while globally disabled, but are never selected at nonpositive effective weight.

### Task 3: Verify integration and document limits

**Modify:** the existing fatigue section of `docs/dialogue-authoring.md` with event rate, config switch semantics, disabled weights and terminal close constraints. No locale rewrite is needed; use the existing base/personality busy/go-away keys.

- [ ] Run the targeted common classes above, then `.\gradlew.bat :common:test :fabric:build :neoforge:build --console=plain`. Run the focused dialogue server cases using the project's existing GameTest selector; read its current configuration before choosing the selector.
- [ ] In a client with the newly built jar, complete repeated heart-changing interactions with an introverted villager; inspect authoritative fatigue and observe refusal/readable exit. Compare an extrovert at equal stored fatigue, and a fresh pair. Turn fatigue off with F>=6: check no busy/go-away or fatigue-weighted reaction, no gain on subsequent nonzero heart commits, but ordinary effects and cooldown decay continue. Re-enable after a decay tick and verify residual F works; independently verify enabled/multiplier 0 still accumulates. Verify neutral ambient clicks alone do not create fatigue and `/reload` still updates datapack selection. Record results separately from source/build evidence.
- [ ] Review only owned hunks against the pinned source and the spec table. Test config missing-field default, old unmarked datapacks and save NBT, nonpositive disabled candidates, stale offers, empty automatic routes and both event selection entry points. Report intentional differences: new default-on toggle (not in reference), neutral Chat penalties, partner protection, ambient candidate pool/anti-repeat, and simple story memory. No claim of identical overall probabilities or live validation without that evidence.

## Double-check completed for this proposal

Checked current source, old `Result`, `Dialogues` and `Actions`, all 18 legacy flags, and the migrated outcome groups. Corrected the previous assumption that 13 extra flags suffice, identified the missing weight-0 kind response and verified old root refusals release interaction. Checked both event-selection paths: `begin`/`pickPreview` and `select(AMBIENT)`. Existing positive-only picker and normal ENDED response do not implement these requirements by themselves. Every spec requirement maps to Tasks 1-3; implementation tests and live play remain pending.
