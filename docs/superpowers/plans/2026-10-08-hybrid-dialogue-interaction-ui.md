# Hybrid Dialogue UI — Fast Implementation Plan

**Goal:** Make MCA Talk readable and comfortable: existing villager details left, selectable topics/replies to the right, dialogue at the bottom. Keep the world visible where practical. Approved design: `docs/superpowers/specs/2026-10-08-dialogue-interaction-ui-design.md`.

**Approach:** Update existing `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java` directly. Reuse its offered actions, scissor, hit areas and `ClientHandlerImpl.DialoguePresentation`; extract a helper only if genuinely needed. Do not touch the server dialogue engine, network protocol, history, cooldowns, or normal MCA controls. Work in `project/dialogue-event-overhaul`, preserving all existing dirty work. No mandatory per-step commits.

## Pass 1 — Layout

- [x] Replace the centered Talk menu with a translucent, readable right-side list of server-provided topics. While a story runs, show spoken text in a bottom panel and swap topic choices for player replies. Put `Next`/`Back to topics` visibly in the passage panel, only when `presentation.canAdvance()` permits it.
- [x] Derive positions and text widths from the current scaled GUI dimensions, allowing the side panel to extend toward the center on compact displays. Existing villager labels/icons stay unchanged. Favor simplicity and reuse current methods over inventing a layout framework.
- [x] Compile/test `:common:compileJava` and correct immediate issues before polishing.

## Pass 2 — Usability

- [x] Implement left-aligned wrapped text, visible hovered row backgrounds, larger hit targets, scissor-matched hit areas, separate text/list scrolling and token-based resets. The Talk wheel does not change the hotbar.
- [x] Preserve current token-bound/server-authoritative click handling, existing reveal-gated replies/advancement, final acknowledgement and pause/continuation behavior; covered by existing common tests. In-game appearance/input remains to be checked.

## Pass 3 — Polish and verify

- [x] Stop showing identical in-panel story dialogue in the chat HUD. Inspected `Messenger.sendChatMessage`; retained text transformations and speech effect for accepted non-silent lines. Unrelated world/ambient chat is unchanged.
- [x] `:common:test :fabric:build :neoforge:build` passed, `git diff --check` was clean and source reviewed.
- [ ] Visually test long text and scrolling at multiple GUI scales in the actual NeoForge and Fabric clients; use the opt-in `testpacks/dialogue-showcase` and the existing NeoForge `New World`. This remains **unverified**, including speaker sound/absence of duplicate chat, until checked in-game.

**Done:** Three visually distinct regions (only when needed), existing left details unchanged, every offered action readable/reachable, no duplicate story chat, server authority preserved. One final review and optional commit, not a commit for every small change.
