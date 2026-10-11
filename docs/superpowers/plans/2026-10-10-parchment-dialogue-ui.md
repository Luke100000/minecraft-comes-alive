# Parchment Conversation UI Implementation Plan

> **For agentic workers:** Implement directly in the current linked worktree. Keep checks proportional to this client-side visual change; verify both loaders and inspect the result in a client before declaring visual completion.

**Goal:** Replace MCA's detached Talk overlays with one bottom-centered parchment-and-dark-menu panel, smoothly sized to content and navigated using Q Back / Esc Close.

**Architecture:** `InteractScreen` owns the layout, rendering, short frame-time tween, hit regions and keyboard routing. `DialoguePresentation` owns speech reveal, including click-to-reveal. Server packets and event semantics remain unchanged.

**Tech Stack:** Java 21, Minecraft 1.21.1, existing `GuiGraphics`/GLFW, Fabric and NeoForge.

**Spec:** `docs/superpowers/specs/2026-10-10-parchment-dialogue-ui-design.md`

## Constraints

- Preserve upper-left villager information and icons without new portraits or AI art.
- Use procedural warm-paper colors, dark topics, amber focus and existing font.
- Existing dialogue eligibility, history, timed reveal, tokens, actions and packets remain authoritative; a parchment click may reveal the remainder of the current line without advancing.
- No new Mixin unless the existing screen key handling demonstrably fails.
- Keep the current worktree and all existing staged/uncommitted changes intact.
- Minimize new tests as requested; prioritize two-loader compile, diff check and manual screen-scale verification at the end.

## Tasks

### 1. Unified presentation

**Modify:** `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java`

- [x] Replace separate right-side topics and bottom speech placement with one measured, bottom-centered panel.
- [x] Preserve server-offered rows, selectable Ask, previews/replies, previous/next history and final acknowledgement.
- [x] Draw paper speech, attached charcoal choices and a clear footer using primitive fills/borders; keep wrapped lines, clip and scroll aligned with click areas.
- [x] Hide the paper region when no speech or preview exists and vertically center row/action text without changing ambient previews or horizontal alignment.

### 2. Smooth sizing and navigation

**Modify:** `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java`

- [x] Measure target from complete text and current rows; cap by available GUI height, hold height across active story, reset at topic/session changes.
- [x] Tween from the current displayed height over 120 ms using monotonic render-frame time; keep all viewport/hit bounds within the animated surface.
- [x] Reveal the current passage or preview instantly on a single parchment click, without accepting/advancing any reply.
- [x] Route one Q press to the existing nearest Back/Topics action; Escape closes; preserve non-Talk back-arrow destinations. No custom key mixin or binding.

### 3. Focused verification and review

- [x] Compile `:fabric:compileJava :neoforge:compileJava` using Gradle wrapper, inspect failure status.
- [x] Run `git diff --check`, review scoped diff and preserve preexisting staging.
- [ ] In an actual client, check scale, daylight/night readability, long localized passages/options, menu scroll, Ask, preview, history, Q/Esc and final Done. Report this as unverified if client runtime cannot be exercised.

## Review focus

- Very short GUIs: no invisible clickable items, unusable footer or negative-height scissor.
- Opening a new server offer: never reuse stale click targets after token changes.
- Expansion while a line reveals: measure `fullLine`, never per-character text; clicking parchment only reveals text, never commits a choice.
- Q back on an unfinished terminal line: pause; never commit queued effects.
- Holding Q: one navigation action until release; Escape always takes the close packet path.
