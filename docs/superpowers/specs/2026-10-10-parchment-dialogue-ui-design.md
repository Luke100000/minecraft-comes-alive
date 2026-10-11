# MCA parchment conversation UI design

**Status:** Initial implementation compiled on both loaders; updated animation and click-to-reveal require fresh verification in-game.

**Target:** MCA Minecraft 1.21.1, Java 21, shared client `InteractScreen` for Fabric and NeoForge.

## Intent and scope

Make the existing villager conversation interface look and behave like a finished game UI, using the reference image's **warm parchment speech area, dark brown topic area, restrained bronze borders, and amber selection**. The reference is a color/layout inspiration, **not** a source of assets. No AI-generated artwork, portrait, player/villager preview, custom icon set, ornamental sprite sheet, or imitation of its illustrated frame.

Keep the existing villager name, profession/age, mood, personality, traits, hearts and other status icons **exactly in their current upper-left presentation**, including tooltips. Keep the 3D villager visible behind the UI. Do not recreate villager metadata in the conversation panel. Do not modify the DialogueEvent engine, authored stories, packets, persistence, or rewards.

This spec **supersedes the Talk layout** in `2026-10-08-dialogue-interaction-ui-design.md` (right-side topics + separate bottom passage). It retains that document's server-authority, text-reveal, history and accessibility requirements, and the implemented Ask expansion/navigation from `2026-10-09-dialogue-navigation-experiment-design.md`.

## Current ownership and findings

- `InteractScreen.render()` already renders status through `drawIcons()` and `drawTextPopups()`, then the dialogue overlay through `drawDialogueEventState()`.
- `drawDialogueRows()` anchors the topics list on the right; `drawDialogueNode()` places speech independently at the bottom or left. The differing geometry is the immediate source of disconnected panels and inconsistent hit/scroll regions.
- `ClientHandlerImpl.DialoguePresentation` owns resolved text, preview, timed reveal, server-provided options, current node, read-only Previous/Next review and offer tokens. The UI must consume this state, not manufacture options.
- `InteractScreen.keyPressed()` already intercepts Escape. Vanilla Minecraft 1.21.1 `Screen.keyPressed()` closes a screen on Escape only if subclasses allow the default path; our screen can handle Q and Escape itself. **A Mixin is not justified** unless actual runtime evidence shows an external input layer intercepting these keys first.

## Selected visual layout

A single **bottom-centered conversation assembly**, with a light paper-colored speech surface directly attached to a dark topic/reply surface. The surfaces have one shared outer alignment and no floating, competing columns. Unlike the reference art, all surfaces and borders can be drawn with `GuiGraphics` primitives and existing Minecraft font; any later hand-authored texture would require separate approval.

```text
  [existing MCA metadata and icons, unchanged]

                 [3D villager in the world]

          +-----------------------------------+
          | warm paper: spoken dialogue       |
          | I passed the workshop earlier...  |
          +-----------------------------------+
          | dark topic / reply area           |
          | > Talk                            |
          |   Introduce yourself              |
          |   Ask about...                    |
          +-----------------------------------+
          | Q Back                 Esc Close   |
          +-----------------------------------+
```

This is an arrangement of regions, not a requirement to show all elements in every state. **No portrait or extra identity/name banner** belongs in the paper area. If there is no speech or preview, omit the paper region entirely and shrink the panel to its topics and footer; do not replace speech with a `Talk` heading. When a story is actively advancing, the topic list is replaced by **only the current eligible replies** and any relevant navigation actions. When there are no replies, do not reserve an empty list box; show the passage plus its appropriate action(s). Existing ambient previews may show speech and browseable topics together, inside this same assembly. Vertically center text in rows and action buttons while preserving left-aligned labels throughout.

### Colors and rendering

Approximate reference-inspired palette (initial design tokens, tune by in-game contrast checks):

| Use | Color |
| --- | --- |
| Paper surface | `#E6C995` (warm parchment) |
| Speech text | `#292018` (near-black brown) |
| Dark menu surface | `#201917` (warm charcoal) |
| Topic text | `#EEE2D2` (soft cream) |
| Muted frame / divider | `#67452F` (brown) |
| Accent / selected row | `#D7A34D` (amber) |
| Disabled / secondary text | `#AD9A86` |

Use a readable opaque or nearly opaque paper field for dark lettering, restrained 1-2 scaled-pixel outlines, and dark menu backing with controlled translucency so bright and dark worlds remain legible. Draw rectangular edges and subtle layered fills, not AI-style fake carved wood, bolts, grime or noisy texture. Prefer the current Minecraft font, no bespoke font. Hover/keyboard focus should be visible through both highlight and border/position cue, not color alone. Contrast is more important than a literal screenshot color sample.

Rows are left aligned and have consistent padding, click targets and wrapping. Keep the existing Ask section expandable and its entries indented. Reuse existing icons only where already meaningful; do not invent icons for every dialogue type. A subtle divider and stable footer distinguish passage, replies and navigation. The panel should not obscure the upper-left information by default.

## Layout and automatic height

All geometry is in **scaled Minecraft GUI pixels**. Derive a common panel rectangle from `Screen.width`/`height` and remeasure on resolution/GUI-scale changes. Center the assembly at the bottom with room for the visible Minecraft HUD; choose a comfortable reading width with a bounded maximum and narrow-screen margins. Keep paper and dark list the same width. Use actual measured line heights and padding for wrapping and interactive rectangles, not independent magic offsets in each renderer.

Calculate a **target height** from the complete localized passage (`fullLine()`, not the per-frame revealed substring), displayed choices/topics, optional controls, and footer. Do not resize for every newly revealed character. Use a compact minimum height and cap growth to approximately three-fifths of screen height; overflow gets a clearly signaled internal scroll viewport. On especially small GUI sizes, preserve readable font size and reachable controls rather than fitting every item onscreen at once.

During an active story, preserve the largest height needed by any passage/reply state **seen in that story**; it may grow but should not jump smaller when a shorter line appears. Reset this hold on return to the Talk menu or when the dialogue session changes. Topic-menu expansion/collapse is allowed to grow/shrink smoothly. Do not prefetch future nodes from the server to predict height.

### Short frame-time tween

- Layout computes a deterministic target. On a target change, `InteractScreen` starts a 120 ms cubic ease-out from the currently displayed height to the new target. Render frames sample elapsed monotonic time; resizing is no longer gated by the 20 Hz client tick.
- The panel's lower edge stays anchored while its top edge moves. Target changes mid-animation begin from the interpolated current height instead of snapping or restarting from an old frame.
- Rendered bounds, clipping and hit testing must correspond to the **same interpolated rectangle**. During the tween, do not expose invisible or clipped rows as click targets; do not let scroll values exceed the newly measured limit. On resize, screen reopen or loss of the animation owner, snap or reinitialize predictably rather than animating from stale dimensions.
- Layout changes are visual only. Speech reveal timing remains independent; no new packets are sent during a tween.

## Interaction and key contract

**Q = Back**, **Escape = Close** while `InteractScreen` is open. Both are fixed screen-local keys in this proposal; no new configurable key-binding surface is required. Consume Q so it cannot trigger the normal Minecraft drop-item action while the GUI is active. Treat one physical press as one navigation action; holding Q must not repeatedly back out through multiple pages.

| State | Q / on-screen Back | Escape |
| --- | --- | --- |
| Normal MCA root interaction | No previous page; leave Q unused (or omit Back hint) | Close interaction with the existing close packet |
| Nested MCA page (including `locations`) | Same destination as the existing back-arrow button | Close interaction |
| Gift mode | Cancel gifting, return to its previous interaction page | Close interaction |
| Browsing Talk topics, including expanded Ask | Leave Talk for the normal MCA interaction page, preserving existing leave/pause semantics | Close interaction |
| Active dialogue or read-only speech review | Return to Topics via existing request/pause flow; **do not** acknowledge/complete an unfinished story | Close interaction |
| Ambient preview alongside available topics | Back out of Talk; preview does not auto-start or complete | Close interaction |

The visible footer always tells players the current **Q Back** and **Esc Close** semantics, and usable mouse controls remain available. `Topics`, `Previous`, `Next`, and `Done` remain distinct: Previous/Next history is client-only read-only speech review; advancing and terminal acknowledgement remain explicit server-token-validated actions. Do **not** relabel a final acknowledgement `Next` or let Q substitute for `Done`.

Start with `InteractScreen.keyPressed` / `keyReleased` and existing page transition methods. Vanilla `Screen.keyPressed` handles Escape by calling `onClose()`; `InteractScreen` already overrides it. An input Mixin, client-wide key listener or new loader adapter is **out of scope unless proven necessary** by an observed conflict in both loader clients. Preserve unrelated key behavior and vanilla focus handling by delegating unhandled keys appropriately.

## State transitions and input consistency

- Preserve the complete server-offered sequence: continuation, highlighted events, expandable Ask topics, ambient Talk, and authored player choices. No guessed choices or visible heart/reaction previews.
- Read-only history cannot activate old choices; switch back to the live line without reenacting text reveal or completing an event. Reveal normally proceeds at 40 ms/grapheme, but a single left click inside the parchment shows the remainder of the current line or ambient preview immediately. That click never advances the node, chooses a reply, or acknowledges completion.
- Server offer/session tokens still gate clicks; reject stale click areas after response changes. Keyboard and mouse activate the same actions.
- Route wheel scrolling only to the hovered currently active viewport, not to the hotbar. Scrolling and focus must remain valid while the panel animates or changes content. Use existing focus/narration practices or narrow widgets rather than inventing a second input framework.
- ESC uses the existing `onClose()` close-packet path, including session/token information. Q must not send that packet when merely navigating back; use the appropriate existing Talk leave/pause or topics request. No client-side reward, completion or cooldown changes.

## Implementation boundaries and validation

**Likely production owner:** `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java`. Reuse `ClientHandlerImpl.DialoguePresentation`, `AbstractDynamicScreen` page names and MCA's existing translation/tooltip infrastructure. Introduce a small package-private layout calculator/record only if it materially simplifies measurement and testability; avoid speculative UI frameworks, generalized animations and new network messages. No new art resources are required.

**Candidate work sequence after spec and plan approval:**

1. Characterize current Talk/preview/review/back states and add low-complexity layout/key regression coverage where possible.
2. Unify measured paper/menu/footer geometry; render color-only parchment and dark rows, preserving existing left status and authoritative hit targets.
3. Add target-height calculation and a short monotonic-time eased height tween; keep scissor/hit bounds in sync with animation.
4. Route Q Back and Esc Close through the existing screen lifecycle; confirm there is no need for a Mixin.
5. Run focused tests and both-loader compilation, then manually verify both clients at multiple GUI scales with short/long text, expanding Ask, ambient preview, history, terminal acknowledgement, hover, scrolling, keyboard repeat and dim/bright worlds.

**Acceptance:** The villager remains visible behind a single coherent bottom conversation UI; left status/info is unchanged; parchment colors, contrasting dark replies and gold selection match the intended mood without any generated artwork; short text uses little space, long text grows the panel smoothly and scrolls when capped; Q backs out one level without completing dialogue; Esc reliably closes; all states preserve existing server authority and history. Build success alone is insufficient for layout or keyboard-behavior acceptance.

## Alternatives

- **Chosen: procedural parchment + attached dark options.** Closest to the requested color/paper idea without external artwork, simple to theme and resize.
- **Vanilla book texture reuse.** Potentially more textured, but its existing page proportions and corners are not guaranteed to nine-slice cleanly and could require special-case art layout. Do not assume a suitable sprite without inspecting the exact 1.21.1 asset.
- **Custom hand-painted art.** Could better match the illustration but requires an artist and an explicit asset-review cycle, and is not needed for this scope.
