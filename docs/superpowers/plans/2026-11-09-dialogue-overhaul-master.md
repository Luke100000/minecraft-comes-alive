# Dialogue Overhaul Master Coordination Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans on an **approved individual feature plan**. This file coordinates feature work; it is **not** authorization to implement all rows at once.

**Goal:** Integrate interaction ownership, slap exit, row styling and datapack-compatible inline async Rumours into one verified villager experience.

**Architecture:** Keep one server-side interaction owner and one `DialogueEngine` session/transition pipeline. Implement approved focused plans in dependency order with one writer for shared Java/UI files; perform independent research, docs preparation and focused tests in parallel.

**Tech Stack:** Minecraft 1.21.1, Java 21, shared Fabric/NeoForge, existing dialogue events, codecs and GUI.

**Spec:** [Master coordination design](../specs/2026-11-09-dialogue-overhaul-master-design.md).

## Global Constraints

- Right-click **opens entire Interact menu** and starts gaze ownership; leaving/closing that menu ends it. Talk page navigation is not a separate interaction lifetime.
- Preserve all staged row-styling changes and unrelated untracked drafts. No WSL, new worktree, dependencies, or product edits under this planning request.
- Audit and reproduce before security/lifecycle fixes. P0 source review does not establish a working exploit; existing lifecycle code may already cover some P1 tasks.
- Follow each feature's own approval gate, exact signatures, RED/GREEN tests, file ownership and scoped commit policy. Never combine two competing Rumours implementations.
- Ensure ordinary dialogue commits on final acknowledgment; only the approved slap-ending path closes/commits immediately. Pending async choices cannot commit early.

## Review Focus

1. Unowned/foreign/replayed/distant legacy commands do not mutate state or close a different player's interaction (P0 security plan/GameTests).
2. Leaving any Interact page, reopening the same villager, switching villagers or danger does not strand or release the wrong owner; gaze follows **whole-menu** lifetime (P1 owner + client tests).
3. Slap transition closes the matching UI only once, applies one delayed hit, then delivers localized reaction; ordinary choice remains unchanged (P2 GameTests/client).
4. Late async results after close, reload, timeout, owner replacement or shutdown are discarded; no extra chat or premature history effects (P4 tests).
5. Unknown datapack resolver, `%2$s` location translation, loader-specific networking, and HUD geometry remain correct across both loaders (P4/P5 data/codec/client/server tests).

---

## Shared-file ownership and sequencing

| Owner / phase | Exclusive edit responsibility | Can proceed alongside |
| --- | --- | --- |
| **P0 authorization integrator** | `network/c2s/InteractionVillagerMessage.java`, related command validation, matching-owner close, permitted command tests | Read-only P1 analysis, P3 visual QA |
| **P1 lifecycle integrator** | `entity/interaction/EntityCommandHandler.java`, villager Brain/tasks, lifecycle/close handlers and tests | P0 security audit only; avoid editing common ownership contract simultaneously |
| **P2 slap integrator** | `dialogue/DialogueEngine.java`, `DialogueActionGameTests.java`, scoped slap resources and guides | P3 visual QA, P4 read-only protocol review |
| **P3 styling owner (existing staged work)** | `client/gui/InteractScreen.java`, `DialogueOverlayLayout.java`, their tests | P0/P1 server-side review; no P4 UI changes until visual checkpoint |
| **P4 async integrator** | `DialogueEngine.java`, `DialogueEvent.java`, async resolver API, networking payloads, `ClientHandlerImpl.java`, `InteractScreen.java`, Rumours event data, translations and tests | Independent P5 docs draft/review only; no parallel edit of files above |
| **P5 verification/doc integrator** | Merge/review all three guides, coordinate GameTest/loader/client results and final regression matrix | Documentation outline before P4; final validation after P4 |

An ownership row names the **one active writer**, not a permanent team assignment. Coordinate even when changes occur in different Java methods; serialization and packet/session interfaces must be reviewed together. Screen styling QA can be performed early, but any geometry correction must land before P4 screen integration.

## Ordered execution checklist

### Gate P0 — Command authorization (separate approval)

- [ ] Review the legacy packet/authentication inventory in [slap-plan audit](2026-11-09-immediate-slap-dialogue-exit.md#general-legacy-handling-audit--separate-follow-up-workstreams), trace authorized UI callers, interaction ID and allowed actions; verify actual security behavior through negative tests before asserting exploitability.
- [ ] Write and obtain approval for a focused P0 security spec + TDD plan. Include same-player/foreign/stale/out-of-range/disallowed commands, zombie-villager gift path, and qualified stop semantics; keep valid commands working.
- [ ] After approval, execute/verify P0 under one owner and publish the new server-side authorization/interaction contract to P1/P2 implementers. **Gate:** relevant negative tests and legitimate interactions pass.

### Gate P1 — Whole-menu lifecycle (reconcile existing work)

- [ ] Review current behavior against the [lifecycle plan](2026-11-08-authoritative-villager-interaction-lifecycle.md): right-click enters Interact owner; **all** Interact pages retain it; leaving any page/closing the entire menu releases it. Existing code already enters `ActivitiesMCA.INTERACT`; evaluate completeness rather than rewrite it blindly.
- [ ] Reproduce gaze/head snap, movement, guard, sleep, danger and stale-close scenarios in a client/controlled GameTest. Update the focused plan's “dialogue screen” language to the user-approved **whole Interact menu** contract and obtain necessary approval for any new behavior.
- [ ] After approval, complete only confirmed gaps and verify owner/ID-qualified open/close. **Gate:** stable ownership, natural gaze and cleanup on normal exit/danger, no cross-player close.

### Gate P2 — Immediate slap completion

- [ ] Implement only after P0/P1 interaction/close contract is stable, using [slap design](../specs/2026-11-09-immediate-slap-dialogue-exit-design.md) and [slap plan](2026-11-09-immediate-slap-dialogue-exit.md).
- [ ] Verify click → owner-matched entire-UI close → single delayed strike → localized chat, without invalidating a newer interaction or ordinary final acknowledgements. **Gate:** focused GameTests and a client timing check.

### Parallel QA P3 — Row styling checkpoint

- [ ] Review existing staged [styling plan](2026-11-09-dialogue-row-styling.md), focused test evidence and an in-game screenshot across HUD, resize and menu states. Do not overwrite current staged work.
- [ ] Resolve any visual defects with the styling owner, and then record/freeze `InteractScreen`/`DialogueOverlayLayout` layout assumptions for P4. **Gate:** validated screen geometry and documented visual result.

### Gate P4 — One generic async flow + Rumours migration

- [ ] Review/approve [generic async design](../specs/2026-11-09-dialogue-pending-and-async-design.md) and [plan](2026-11-09-dialogue-pending-and-async.md); mark [Rumours-only design](../specs/2026-11-09-inline-location-rumors-design.md) and [plan](2026-11-09-inline-location-rumors.md) **superseded as implementations** after porting their unique acceptance criteria. Do **not** add dedicated `mca:rumor_location` alongside node `resolve`.
- [ ] Correct async translation contract before code: preserve `villager.getTranslatable(player, key, coordinates)` or equivalent, with player name/personality/profession variants and existing `%2$s` coordinate keys. Keep standalone legacy `location` command/chat behavior.
- [ ] Follow generic plan's own steps in order: optional resolver schema/codec → pending ownership and generation/token/timeout → S2C protocol/client state → dots and parchment bar atop P3 geometry → built-in structure resolver and Rumours migration. **Gate:** pending/ready/stale/fallback, no chat duplication, addon/datapack, client and both loader tests.

### Gate P5 — Documentation and combined verification

- [ ] Integrate updates to `docs/dialogue-authoring.md`, `docs/dialogue-datapack-guide.md`, `docs/dialogue-system-guide.md` with final P0/P2/P4 behavior; document distinction between reveal dots and true asynchronous pending bar.
- [ ] Run each focused project's common/NeoForge GameTests and Fabric/NeoForge compilation, then combined lifecycle/security/slap/async regressions; check relevant dedicated-server loading and actual client visuals. Record which tests executed, passed, failed or were not run; compilation is not gameplay proof.
- [ ] Request final cross-feature review, resolve overlapping changes with the named owner, and confirm the master acceptance criteria. **Gate:** no known unverified requirement described as passed.

## Handoff

The roadmap is **planning only**. Review the [master design](../specs/2026-11-09-dialogue-overhaul-master-design.md), confirm the full-menu gaze semantics and integration order, then approve each unapproved focused design before its product code is changed. Independent audits/QA can proceed without authorizing other feature implementations.
