# Dialogue overhaul: master coordination design

**Status:** Roadmap/design for review, not authorization to implement any feature. **Target:** Minecraft 1.21.1, Java 21, Fabric and NeoForge.

## Intent and player contract

Deliver one coherent villager interaction experience without parallel implementations fighting over interaction ownership, dialogue state, networking, or screen geometry. **Right-clicking a villager opens the entire Interact menu** and starts the interaction/gaze ownership. While that menu is open, the villager should face and naturally track the player; **leaving or closing the entire Interact menu ends that ownership and gaze**. Switching pages within Interact, including entering/leaving Talk, must not itself end it. Danger may terminate the interaction and close the correct screen. Ordinary dialogue may still have its own distinct pause/resume semantics.

This is an intended contract, **not a claim that gaze or closing is currently broken**. The existing `EntityCommandHandler.interactAt` activates `ActivitiesMCA.INTERACT`, and `ClientHandlerImpl` already has a `CLOSE` branch. Verify actual gameplay and stale-owner behavior before proposing additional lifecycle code.

## Approaches

1. **One giant feature spec/patch:** centralizes every detail but obscures independently testable changes and encourages unrelated edits.
2. **Independent feature threads editing shared files concurrently:** permits fast local progress but risks incompatible transitions and accidental overwrites.
3. **Chosen: one master roadmap with separate feature specs/plans:** a small dependency/ownership map controls integration while existing focused plans remain the implementation authorities. Read-only reviews, isolated tests and draft docs can run in parallel.

## Priorities, dependencies and completion criteria

| Priority | Work and linked authority | Required outcome / gate |
| --- | --- | --- |
| **P0** | **Legacy interaction command authorization.** Source-level finding recorded under [slap-plan security audit](../plans/2026-11-09-immediate-slap-dialogue-exit.md#general-legacy-handling-audit--separate-follow-up-workstreams). | Separate, approved security spec/plan; reject absent, foreign, stale, distant or unauthorized command packets and never close another player's/newer interaction. Preserve valid commands. **Not a confirmed exploit.** Audit first; only implement after separate approval. |
| **P1** | **Whole-menu interaction lifecycle and natural gaze.** [Existing lifecycle plan](../plans/2026-11-08-authoritative-villager-interaction-lifecycle.md). | Right-click establishes a single owner through **all** Interact pages; natural head/body motion and safe approach; leaving, danger, distance, death or logout releases the correct owner and screen. Reconcile that plan's phrase “dialogue screen” with **entire Interact menu** before execution. Check source already present and reproduce any actual gaze snaps before changing controls. |
| **P2** | **Immediate exit on slap.** [Design](2026-11-09-immediate-slap-dialogue-exit-design.md), [plan](../plans/2026-11-09-immediate-slap-dialogue-exit.md). | Provoking choice immediately closes the matching entire Interact UI; a short delayed hit occurs once, then the existing localized final reaction appears in chat. Normal non-slap final acknowledgements remain unchanged. Integrate after P1 owner/close semantics and P0 authorization contract are stable. |
| **P3** | **Dialogue row styling.** [Design](2026-11-09-dialogue-row-styling-design.md), [plan](../plans/2026-11-09-dialogue-row-styling.md). | Preserve category outlines, soft rows, footer and HUD clearance. Staged changes have documented focused test results; **in-game visual verification remains outstanding**. This QA can proceed in parallel, but freeze screen geometry before P4 merges. |
| **P4** | **Per-character reveal, genuine async loading, inline location Rumours.** [Preferred generic async design](2026-11-09-dialogue-pending-and-async-design.md), [plan](../plans/2026-11-09-dialogue-pending-and-async.md). | Grey three-dot row while choices are unavailable, 40 ms/Unicode-grapheme text reveal; parchment progress bar **only** for genuine server async work; localized location results inline rather than chat; one typed, namespaced and datapack-configurable **server resolver** path. Gate on completed P1/P2 state and stable P3 geometry. Requires review/approval of the generic async spec. |
| **P5** | **Docs, cross-feature verification and loader parity.** The three existing dialogue guides plus each feature's tests. | `docs/dialogue-authoring.md`, `docs/dialogue-datapack-guide.md`, `docs/dialogue-system-guide.md` accurately explain actions, async `resolve`, packs and addon registration. Test the combined lifecycle, security, stale callbacks, visible UI and both loaders. Draft documentation can run earlier; final accuracy/parity gate runs after integration. |

P0 and P1 are **stabilization gates**, not assertions that their implementation is wholly absent. The lifecycle code already has some owner/activity/close plumbing; progress must be established from tests and a client run. The security concern is a concrete packet-level source finding, **not runtime exploit confirmation**.

## Integration decisions and conflicts

- **Only one Rumours async architecture:** the older [inline Rumours design](2026-11-09-inline-location-rumors-design.md) and [plan](../plans/2026-11-09-inline-location-rumors.md) propose a dedicated `mca:rumor_location` action. Their **player requirements** survive (localized results, exact coordinates, search failures, cooldown/history, no duplicate chat, cancellation, legacy command compatibility), but their implementation approach is **superseded for planning** by optional node `resolve` with registered `mca:structure_location`. Do not execute both paths. Reconcile the generic documents before P4 code.
- **Fix translation design before P4:** `Messenger.getTranslatable(player, key, coordinates)` supplies player name and personality/profession/gender variants. Existing location strings frequently refer to coordinates as **`%2$s`**, not `%1$s`. The generic async proposal's `Component.translatable(node.line, argument)` / `%1$s` rule is incompatible; resolve using the established villager translation path or an equivalent parameter contract. Preserve localized lookup failures and non-dialogue `location` chat calls.
- **Shared state:** `DialogueEngine` is the single authority for event transitions, final acknowledgements, interrupted slap completion, pending async sessions and stale-token rejection. Legacy command authorization uses the interaction owner, not a parallel conversation owner. Slap exits immediately; pending Rumours must stay open until the answer is ready.
- **Shared UI:** finish/review P3's `InteractScreen` and `DialogueOverlayLayout` changes before P4 alters them; the screen owner implements dots/parchment bar against the final geometry. Keep the server authoritative for async readiness and gameplay effects, the client authoritative only for animation/rendering.
- **Boundaries:** no new worktree, dependencies, chat duplication for dialogue Rumours, fabricated loading percentages, or speculative rewrite of villager rotations. Unapproved security, lifecycle and async designs require their own review; do not treat approval of this roadmap as approval of their code.

## Cross-feature acceptance

1. Right-click opens Interact, page navigation preserves gaze, and leaving closes the owner's interaction. Reopening/switching villagers and stale close packets never release a newer or foreign owner. Head/body motion remains natural under navigation and danger.
2. A slap route closes the whole menu before a single physical strike and subsequent localized chat line, even under duplicate messages; ordinary dialogue commits only on its normal final acknowledgement.
3. Ordinary typing has no fake server progress bar. Genuine location search shows a parchment pending bar and one disabled dots row; result or failure appears inline, with no extra vanilla chat, stale reply, or double history commit.
4. Existing Rumours selection rules, personalities/translations, external legacy location commands, client HUD spacing, and non-async datapacks remain compatible.
5. Security negative tests, session/async GameTests, focused UI checks, common tests, Fabric and NeoForge compilation and client/dedicated-server checks are reported separately; tests and compilation alone do not prove visible gameplay.

## Decisions still requiring feature-level approval

The P0 security policy for legacy commands; any P1 lifecycle design updates following runtime reproduction; P4 generic resolver protocol and timeout; the `%2$s` localization correction and superseding of the older Rumours-only implementation plan. This document fixes **ordering and integration ownership**, not those detailed designs.
