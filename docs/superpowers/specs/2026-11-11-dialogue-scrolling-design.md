# Dialogue speech scrolling and sizing design

**Status:** Draft for review, 2026-11-11. This document authorizes no implementation by itself.

## Intent

Make villager speech readable and controllable at the player's GUI scale. Speech should follow the typewriter reveal automatically, allow mouse-wheel and draggable scrolling, and display two wrapped lines without a needless scroll prompt whenever screen space allows. Make the choices scrollbar slimmer while retaining Minecraft's native gestures. Both speech placements must behave consistently.

## Current ownership and cause

- Minecraft version is 1.21.1 (`gradle.properties`). `InteractScreen` owns speech rendering, wrapping, text hit targets, wheel routing, and the current integer `dialoguePassageScroll`.
- `DialogueMenuScrollWidget` extends the vanilla `AbstractScrollWidget`. It already owns native wheel, drag and scrollbar interactions for the choices menu.
- Speech currently scrolls by wheel in 20-pixel increments and renders up/down arrows. It has no draggable thumb or follow-on-reveal behavior.
- `InteractScreen.dialoguePaperHeight` estimates height from wrapped line count plus mode-specific padding. In the docked two-line case it can request 60 pixels, while rendering reserves 23 + 8 + 12 pixels above and below text. Only 17 pixels remain for two 10-pixel lines at scale 1. This is a reproducible calculation from the current code, although the exact reported resolution has not been tested in-game.
- `DialogueOverlayLayout.calculate` can further cap the bubble when the choices menu occupies the available vertical space. The speech history controls and pending-progress strip also consume vertical space.
- The 1.21.1 vanilla scrollbar renders an 8-pixel-wide sprite and uses an 8-pixel click strip. Its `renderScrollBar` implementation is private, so narrowing its actual rendering requires a visual transform or custom sprite painting.

## Approaches

1. Extend the integer speech offset with a bespoke draggable thumb. This duplicates Minecraft's drag, wheel and thumb behavior.
2. Introduce a second, fully independent speech scroll widget. This gives speech a separate owner but repeats the existing choices widget's native subclass behavior.
3. **Proposed:** Reuse the existing `DialogueMenuScrollWidget` as a second instance for speech. Let vanilla manage scroll amounts, wheel input and dragging. Keep the dialogue screen responsible for speech clipping, text, tooltips and click targets. Narrow both displayed thumbs with a small horizontal render transform while retaining the native 8-pixel hit area. This is the smallest route that keeps the current input behavior.

## Proposed behavior

### Speech height and layout

- Measure the minimum speech height from the same pixel-scaled metrics used to draw it. Reserve speaker/divider space, text top inset, bottom inset, history controls and, while shown, the pending-progress strip. Make the normal minimum text viewport accommodate **at least two complete line steps**, even for one-line or two-line speech.
- Use `dialoguePixels(10)` as the rendered line step. Do not calculate the desired height from an unscaled `lines * 10` if that disagrees with the rendered increments.
- Before laying out the menu, cap its settled height where doing so opens enough space for the two-line speech minimum. Overflowing choices remain available through their existing scrollbar. Keep the menu footer above the HUD and retain the current upper-right or docked speech placement.
- On an extremely small screen where the footer, safe margins and two-line speech minimum cannot all fit, keep controls accessible and allow speech scrolling. Do not overlap the HUD or send the bubble off-screen.
- Both placements use the same content-height rule. No new user setting or change to dialogue packets, history data or text formatting is needed.

### Speech scrolling

- Show a slim, warm-tinted draggable scrollbar only when speech exceeds its actual visible text viewport. Replace speech overflow arrows with this thumb. Wheel and drag should scroll the same content, using the vanilla scroll widget.
- Follow newly revealed text downward until its last visible line fits in the viewport. The thumb's full scroll range represents the currently rendered speech content. This applies while typewriter text is appearing and after `revealFully()`.
- A manual upward wheel gesture or drag pauses automatic following. A manual return to the bottom resumes it. A new dialogue node/offer or a deliberate history-entry change resets speech to the top and restores automatic following. Progress and history transitions must clamp offsets to valid bounds.
- Speech scrolling never selects a choice, advances dialogue or invokes the click-to-copy action. A copy phrase is clickable as soon as its entire phrase is revealed, even when other text is still animating.
- Keep text clipping, hovered glyph lookup and copy hit testing tied to the same scroll offset and viewport. Exclude the scrollbar strip from speech text hit targets.

### Scrollbar appearance

- Propose a **5-pixel displayed thumb/track** for speech and choices, placed within the existing **8-pixel native input strip**. Keep the vanilla sprite, hit geometry, wheel behavior and drag semantics. The native 8-pixel sprite is narrowed only in rendering, using a scoped pose transform.
- Respect scrolling within the correct panel. Preserve touchpad fractional wheel steps and the choices menu's animated-height scroll safeguards.
- Preserve colour contrast on the parchment and dark menu, including at GUI scales 1, 1.5 and 2.

## Scope and compatibility

Modify only the current 1.21.1 dialogue worktree's client UI and focused tests. Preserve the existing uncommitted dialogue feature work. Do not create another checkout, merge branches, alter networking or datapack schemas, or rewrite the speech typewriter/copy pipeline. No product code is part of the current design task.

## Acceptance checks

1. One and two wrapped speech lines fit without scrolling when the available screen height allows, at multiple GUI scales and in both positions.
2. Long speech has a visible draggable thumb. Wheel, dragging, clamping, and returning to the bottom work without moving the choices menu.
3. Typewriter reveal follows the latest visible text; scrolling up preserves reading position until the user returns to the bottom. Advancing nodes and navigating history reset predictably.
4. The choices scrollbar looks narrower but remains easy to drag in its full native hit area, including with touchpad scrolling.
5. Pending progress and history controls remain visible. Tiny viewports preserve footer/HUD separation and provide speech overflow instead of clipped controls.
6. Styled, wrapped and scrolled copy spans continue to show correct hover/copy behavior and do not accidentally advance dialogue.

## Choices for review

The 5-pixel visual width, 8-pixel native hit strip, pause-while-reading behavior and speech-first height allocation are design choices proposed here. They have not yet been approved through in-game visual review.
