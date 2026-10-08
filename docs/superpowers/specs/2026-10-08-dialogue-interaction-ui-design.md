# MCA DialogueEvent Interaction UI — Hybrid Layout Design

**Status:** Proposed for review; specification only, not implementation approval.

**Target:** The existing `project/dialogue-event-overhaul` worktree on Minecraft 1.21.1. The core screen lives in `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java` and inherits the existing MCA interaction widgets from `AbstractDynamicScreen`. The design should remain implementable in shared client code for Fabric and NeoForge.

## Intent and constraints

Make extended conversations feel like an intentional part of MCA's in-world interaction screen instead of a stack of floating, centrally aligned lines. Preserve the existing Minecraft/MCA aesthetic, the villager as the focus of the scene, and everything the dialogue engine already knows how to do.

The agreed **hybrid** layout has three distinct responsibilities:

1. **Left:** existing villager details, relationship icons and their tooltips; keep their current ownership and interactions. Do not add a second identity/status card.
2. **Right:** browsable conversation topics **or** the player's available reply choices, depending on dialogue state. These are mutually exclusive lists.
3. **Bottom:** the current villager passage, its gradual character reveal, and a clearly named progression/finish action when one is permitted.

Prefer a clear view of the villager's head and torso, but **never sacrifice legibility, usable targets, or a valid conversation flow to avoid overlaying the villager**. It is an in-game GUI: controlled overlap is acceptable on small displays or when text is long. Keep the world visible rather than adding an opaque full-screen backdrop.

The approved prior design chose the existing left-hand status presentation, the right-hand topics/replies swap, and a bottom dialogue panel. This document makes their sizing, states and acceptance rules concrete. It does not reopen the event-system architecture.

## Existing behavior and integration points

- `InteractScreen.render(...)` currently draws regular MCA icons/status and then calls `drawDialogueEventState(...)`; `requestDialogueMenu()` enters Talk after the player presses the existing Talk button, and `leaveDialogueMode()` returns to the normal interaction layout.
- `drawDialogueOptions`, `drawDialogueNode`, `drawDialogueRows`, `beginDialogueViewport` and `drawDialogueRow` currently share a centered ~230 GUI-pixel-wide viewport with centered lines/rows and one `dialogueScroll` value. The view has scissoring and token-bound click targets already; preserve their safety properties while changing geometry.
- `AbstractDynamicScreen` already owns standard MCA pages, buttons, icons and constraints. Its left status tooltips are drawn through `drawTextPopups` and its icons through `drawIcons`. The new panels exist **only in Talk mode**, not on command, gift or other pages.
- `ClientHandlerImpl.DialoguePresentation` is the client display state. It exposes server-provided options or a visible line, visible choices, `canAdvance()` and `advanceKind()`. Do not infer choices, branch outcome, completion, or availability from the UI.
- `resolveDialogueLine(...)` currently uses `villager.sendChatMessage(...)` for non-silent lines; a spoken passage can therefore also appear in ordinary Minecraft chat. Changing duplication must preserve any real chat/voice semantics, translation variation and authored `silent` behavior. Audit the call path before changing it.
- The existing dialogue system specifies Unicode-grapheme reveal every **40 ms**, no skip/instant reveal, token-bound actions, topic-specific continuation after returning via Talk, and server-owned completion/reward acknowledgement. This spec preserves those decisions.

## Layout

All dimensions below are **Minecraft scaled GUI pixels**, not raw monitor pixels. Calculate bounds from the current `Screen.width`/`height` at layout time; never assume a fixed resolution, and recompute on resize or GUI-scale change. Reuse MCA's font, button hover language, icon textures and subdued translucent dark surfaces; do not introduce a new HUD, external GUI library or texture atlas solely for this screen.

### Spacious layout (preferred)

At approximately `width >= 640` and `height >= 360`:

```text
┌─────────────────────────────────────────────────────────────────────┐
│ Name / profession                         ┌─ Conversation topics ─┐ │
│ Mood / personality                       │ Continue earlier ...  │ │
│ Traits                                   │ Featured conversation │ │
│                                         │ Ask about...          │ │
│                                         │ • Old bridge          │ │
│       [villager remains visible]         │ • Village life        │ │
│                                         │ What's on your mind? │ │
│                                         │ Back                  │ │
│  [existing relationship/action icons]   └───────────────────────┘ │
│             ┌─────────────────────────────────────────────┐       │
│             │ Villager's current spoken passage          │       │
│             │                                             │       │
│             │                                    Next ›   │       │
│             └─────────────────────────────────────────────┘       │
└─────────────────────────────────────────────────────────────────────┘
```

The diagram shows **regions**, not simultaneous menu and spoken states: when browsing topics the bottom passage panel may be absent; while a story is active, the right region changes to player replies, and an empty reply area must not remain as a useless oversized box.

- **Left information:** retain existing `drawTextPopups`, icons, trait/mood hover interactions and the current left screen anchoring. Do not move them merely to align with a hypothetical new card. Account for their measured visible extent when computing the right and bottom panels.
- **Right panel:** anchor to the right edge with a margin of approximately 12 scaled pixels. Aim for a width in the **190–270 px** range when there is room, based on available width rather than hardcoded centered positions. Use a compact heading, section spacing and **left-aligned** wrapped selectable rows with full-row hover/focus treatment. Do not create a row that looks clickable for a section heading. Long content is clipped to an internal scroll viewport; the Back/Leave option remains accessible.
- **Bottom passage panel:** anchor near the bottom with approximately 12 px outer margin and enough space for two to four wrapped lines in typical dialogue. Allow its width to use otherwise free horizontal space, prioritizing a comfortable readable line length and keeping the left-hand icons unobstructed where possible. The passage is **left-aligned**, padded, and distinct from the action/footer. A small speaker label is optional only if it improves context; do not duplicate name/profession/mood information in a second status card.
- The dialogue panel and choice panel have **separate measured rectangles**. Compute text wrapping, padding, clipping, hover areas and scroll offsets from those actual rectangles, not from the old constant `210` wrap width or `width / 2` centers. Panels may use a subtle MCA-style border/highlight and translucent dark backing for world contrast.
- Reserve the center of the viewport primarily for the villager. If a right panel or a bottom panel intersects the villager on narrow configurations, prefer that to text extending off-screen or control collisions.

### Compact layout

For roughly `width < 640` or `height < 360`, or whenever measured available space cannot accommodate both panels:

- Keep the existing left villager information. Do **not** force a narrow, unreadable side column. Display the **active** Talk region as a compact panel that can extend into the center, with the passage panel below or in place of it when necessary.
- Topics and replies never compete simultaneously for the same space: topics take the primary list viewport while browsing; active replies replace topics while conversing. The passage remains clearly associated with the replies; if the screen cannot show both regions comfortably, stack them vertically with independently measured scrolling.
- Scroll long lists, wrap translated strings, keep all controls within the visible screen, and preserve a usable minimum clickable row height (approximately 20 scaled px, taller for wrapped choices).
- Avoid shrinking Minecraft text below the game's configured font/GUI scale. Do not hide the last available response or final acknowledgement behind the hotbar, screen edge, left icons, or a clipping rectangle.

## State and interaction contract

| State | Right/primary interactive region | Bottom passage region |
| --- | --- | --- |
| Normal MCA interaction | Existing buttons/pages (unchanged) | None |
| Talk menu | Authored continuation first, highlighted event, Ask section/events, ambient option, Back | None (unless design polish adds a non-interactive hint) |
| Beginning/continuing a story line (reveal incomplete) | No active replies or premature buttons; no stale topic list | One gradually revealed passage; no skip action |
| Passage fully revealed, another authored passage follows | No player-choice menu yet | Clearly labeled `Next` |
| Passage fully revealed, node offers choices | Only the server-provided natural player replies | Current completed passage remains readable |
| Completing terminal passage | No stale reply/topic options | Explicit `Back to topics` acknowledgement |
| Rejected/stale response or content reload | Clear stale hit areas; use refreshed authoritative state | Never render stale interactive controls |

An empty right-hand box is not required when there are no selectable replies; keep the bottom passage dominant. Use a consistent hover state, click target, spacing and focus order when replies appear. Do not show positive/neutral/negative ratings, heart deltas or hidden checks on replies.

**Do not turn `Next` into a catch-all label.** `Next` advances to another authored passage or explicitly connected node; `Back to topics` acknowledges an ending and lets the server commit queued effects. The Talk-menu continuation is a separate **authored event-specific** option and is presented only after the player reopens the normal interaction screen and presses Talk. Returning to the menu must not automatically start the next event.

Preserve existing server tokens, offered-choice checks, pending effects, timing, cooldowns, repeat rules, history and pause/expiry behavior. GUI transformations are not authorization; clicks during reveal are ignored without queueing future actions. No new packets, client-owned dialogue graph, or server-state duplication is part of the UI work.

## Text, sound, chat and accessibility

- Display the current server-resolved, localized `Component`, keeping style/color, formatting, Unicode grapheme boundaries and pooled translation choice stable during the current line/pause. The agreed timing is 40 ms per grapheme; there is no instant-reveal shortcut.
- Avoid echoing the exact same in-panel dialogue into the Minecraft chat HUD by default. **Before implementing**, inspect `resolveDialogueLine` / `sendChatMessage` for side effects and distinguish genuinely intentional ambient chat from UI-only story speech; do not just replace the call with literal text and lose translation/presentation semantics.
- Show all text against enough translucent contrast for daylight, darkness and busy interiors. Use visible hover/selected/disabled states instead of relying only on text color. Preserve MCA's overall visual palette.
- Mouse wheel scrolls the hovered Talk panel and not the hotbar. When there is only one active list viewport, scrolling goes there as before. Scroll state resets on a new server view/token but does not jump unexpectedly during reveal.
- Keyboard focus/tab navigation, click handling and narration should follow the version-matched Minecraft screen/widget conventions wherever practical. Where a custom-drawn row remains necessary, its focus/activation and hit region must correspond to its visible row.
- Escape and Back retain the existing distinction: leave Talk for MCA's normal interaction page when the Talk Back control is chosen; close the interaction via Escape/normal close. Do not silently complete a story when closing or walking away.

## Implementation boundaries and candidate touchpoints

The intended first implementation is a **client presentation change** primarily in `InteractScreen.java`: centralize measured Talk panel geometry, separate `options` and `node` rendering, reuse existing `DialoguePresentation` state, and keep clickable regions token-bound. A small client-only panel/layout helper is justified only if its code has one clear owner and materially simplifies clipping/hit-testing. Preserve the normal `AbstractDynamicScreen` pages and their dynamic buttons.

`ClientHandlerImpl.DialoguePresentation` should not need a network or event-model redesign. Inspect it only for a demonstrable UI issue such as duplicate chat emission or rendered-line state. The source spec and plan for the full DialogueEvent overhaul remain authoritative for progression, rewards and persistence; this new file specifies only the human-facing layout and accessibility rules.

Out of scope: rebuilding the MCA status sidebar, changing villagers' data or personalities, changing dialogue JSON, adding portrait images, cinematics, camera steering, custom voice audio, new triggers, or redesigning normal command/gift screens.

## Verification and acceptance

1. In the existing 1.21.1 NeoForge client, the current normal interaction page still shows exactly the existing left information/icons and normal Talk button. Entering Talk shows a readable topics panel with no Back-only regression when ordinary content is eligible.
2. Use the opt-in `testpacks/dialogue-showcase` pack and its current `neoforge/run/saves/New World` setup to inspect highlighted, Ask, ambient and authored continuation items. Topics render in a readable right/compact list without overlapping each other or clipping beyond the screen.
3. Play the full bridge story: ordered passages are readable at the bottom with 40 ms/grapheme reveal; `Next` only advances authored passages; after the last passage, natural replies replace topics; the terminal displays `Back to topics`, with no duplicate active choices.
4. Verify that the final acknowledgement still gates effects and history, incomplete dialogue returns via a **selected** personalized continuation, and stale/duplicate requests cannot trigger a different UI action. These are regression checks for preserved behavior, not new engine work.
5. Test GUI scales and window sizes including a roomy screen, a short screen and the smallest usable compact configuration; also long localized lines, long replies, many options, scroll/focus, tooltips and status labels. Inspect daylight and dim interior readability. Prioritize avoiding cut-off buttons and incorrect click regions over keeping the villager entirely unobscured.
6. Verify both Fabric and NeoForge client rendering after code changes; common tests/loader builds alone do **not** constitute visual verification. Confirm that normal screens still work and that opening/closing Talk does not create duplicate chat lines or incorrect rewards.

**Done means** the three responsibilities are visually distinct, the existing sidebar is untouched, every offered response stays readable and reachable, long text and small GUIs work, the server remains authoritative, and the dialogue-specific UI is visibly more coherent without hiding the villager unnecessarily.

## Alternatives considered

- **Existing centered overlay with minor spacing changes:** smallest patch, but leaves topics and spoken text competing in the same centered viewport and gives little improvement to long conversation flow.
- **Full opaque conversation screen / cinematic portrait:** strongest spatial separation, but obscures the world and recreates existing MCA information; contrary to the preferred in-world feel.
- **Selected — hybrid in-world panels:** retains existing MCA status and world context while clearly distinguishing topics, replies and passages, with a compact fallback when the ideal composition cannot fit.
