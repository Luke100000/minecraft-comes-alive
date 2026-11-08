# Kai-style MCA dialogue UI

**Status:** Selected for implementation, 8 November 2026.

## Intent

Reproduce the supplied **Kai concept screenshot** as the MCA Talk interface. The screenshot is a proposed design, not a picture of the running client. Players should see the villager between a parchment speech bubble at the upper right and a dark, bottom-centred choice panel, with MCA's existing villager information/icons at the upper left.

## Scope and invariants

- Minecraft 1.21.1, shared `InteractScreen` client code, Fabric and NeoForge. Reuse the current Minecraft font and existing entity/world background. No new screen or portrait.
- Use original, small, pixel-art **texture resources** for the parchment speech bubble (including its pointed tail), dark conversation panel, outlined featured row, and meaningful category icons; nine-slice the frames for arbitrary GUI sizes. Do not paste a crop of the concept screenshot into the game.
- Reproduce the arrangement: left-side tooltips/icons; upper-right parchment with villager name, separator and current speech; bottom-centred dark topic/reply list; gold outlined star-featured conversation; category headers; Q Back / Esc Close footer. Screens with no speech show only the bottom panel.
- Keep existing `DialogueEngine`, `DialoguePresentation`, network packets, history/cooldowns/effects, ambient previews, eligibility, current localized topic/category names and non-dialogue screens. The highlighted **server-selected** option moves to the first row, outside its category; other choices remain under their categories. A featured option is never duplicated. A currently active exchange shows only its actual eligible player choices/actions, not stale topic rows.
- Start categories collapsed to resemble the screenshot; opening a category reveals its existing items. Expansion is purely client-side. No removal of the ambient option or category renaming in this visual pass.
- Keep server offer/session token checks, Q one-level Back/Topics without completion, Escape close, click-to-reveal, read-only history and terminal Finish conversation. Preserve mouse and keyboard input and existing left-side tooltips.
- Adapt positions to scaled GUI size. At normal aspect ratios the bubble must avoid the character and menu; at narrow/small GUI sizes prioritise readable text, visible controls, clipping and scrolling instead of forced screenshot pixel coordinates. All clicks must match visible clipped rows throughout the existing 120 ms menu height tween.

## Selected approach

**B: bespoke pixel-art textures, rendered by the existing GUI.** Small deterministic sprites provide crisp repeatable corners and shading, while code controls size/positions and live text. This can look closer to the screenshot than flat `fill` rectangles without introducing a UI framework. The previously considered **A: flat procedural rectangles** is simpler but loses the screenshot's material treatment and pointed bubble. A single prerendered full-screen background was rejected because it would fail on changing GUI scales, long dialogue and localization.

## Visual ownership

`InteractScreen` owns dialogue state and interactions. A small `DialogueOverlayLayout` calculates menu and bubble rectangles in GUI pixels from screen size and content heights; no server state is stored there. A small `DialogueSkin` draws the reusable textures/nine-slice edges/icons. Texture PNGs live under `assets/mca/textures/gui/dialogue/`, with a deterministic art-source generator so binary assets remain maintainable. The left-hand villager information and world renderer remain untouched.

For topics, the menu begins with a single featured event when one exists, followed by collapsible categories for remaining offered events, then a footer. For an active node, the same dark panel draws only actual replies, history/navigation and the footer, while the upper-right bubble shows the active speech. On a preview, bubble and topic list can coexist. The menu grows to fit short lists and scrolls longer ones; the bubble clips and scrolls its own wrapped passage independently.

## Verification

Use focused tests for geometry bounds, feature-first/no-duplicate ordering, and the declared sprite dimensions; run common tests and both loader builds. Manually inspect 1.21.1 clients at multiple GUI scales, long translated text, daylight/night, large lists, active/review/preview/paused stories, mouse/keyboard scrolling, tween hit boxes and Q/Escape. Compilation alone does not establish visual fidelity. The reference screenshot remains the acceptance target.
