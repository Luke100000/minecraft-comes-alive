# Kai-style MCA Dialogue UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Render MCA Talk like the Kai reference, with independent upper-right parchment speech and bottom-centred pixel-art conversation panel.

**Architecture:** Keep the existing dialogue client/server state and interaction routes. Add one pure layout calculator, one sprite renderer, and small resources. Adapt `InteractScreen` to the new layout and featured-first row presentation without changing event eligibility.

**Tech Stack:** Java 21, Minecraft 1.21.1 `GuiGraphics`, Gradle, JUnit Jupiter, Pillow for deterministic PNG artwork generation.

**Spec:** [Kai-style MCA dialogue UI design](../specs/2026-11-08-kai-dialogue-ui-design.md)

## Global Constraints

- Existing pre-created `dev/1.21.1-dialogue-system-b` worktree only; no WSL, new checkout, merge or push.
- Shared client-only 1.21.1 rendering; do not modify `DialogueEngine`, packets, villager status, authored dialogue JSON or other screens.
- 120 ms menu-height tween, visible hit targets, hover/keyboard behavior and token validation must survive.
- Use small original pixel-art textures (not a flattened screenshot) and preserve localizable text.

## Review Focus

1. Narrow 320×240 GUI: bubble and menu stay in bounds, footer remains accessible. Test `DialogueOverlayLayoutTest` with the small size.
2. Long passage: speech height is bounded, its own scroll clipping works. Test bounded geometry and inspect in client.
3. Featured event topic happens to be collapsed: render exactly once outside sections. Test `DialogueMenuOrderingTest` and inspect in client.
4. Empty/ambient-only menu: no fictional star or empty category. Test helper with no highlighted option, inspect in client.
5. Active story/history: rows stay authentic and navigation cannot complete dialogue on Back. Exercise in live client and existing dialogue tests.

---

### Task 1: Layout geometry and ordering

**Files:**
- Create `common/src/main/java/net/conczin/mca/client/gui/DialogueOverlayLayout.java`
- Create `common/src/main/java/net/conczin/mca/client/gui/DialogueMenuOrdering.java`
- Create `common/src/test/java/net/conczin/mca/client/gui/DialogueOverlayLayoutTest.java`
- Create `common/src/test/java/net/conczin/mca/client/gui/DialogueMenuOrderingTest.java`

**Interfaces:** `DialogueOverlayLayout.calculate(int width, int height, int menuHeight, int bubbleHeight)` returns `Geometry(menu: Rect, bubble: Rect)`; `DialogueMenuOrdering.ordered(List<EventOption>)` returns featured-first followed by all remaining options, with exactly one instance of each ID.

- [ ] **Step 1: RED tests.** Assert in-bounds/nonoverlapping rectangles for 1672×941, 640×360 and 320×240; absent bubble if height=0. Assert highlighted-first, no duplicate and empty-list order.
- [ ] **Step 2: Run** `./gradlew.bat :common:test --tests '*DialogueOverlayLayoutTest' --tests '*DialogueMenuOrderingTest'` and observe expected initial compilation failure.
- [ ] **Step 3: Implement** pure layout and ordering helpers with the above signatures; no network or rendering dependencies in layout.
- [ ] **Step 4: Run** same command expecting PASS; commit just these four files (`feat(dialogue): add Kai overlay layout and featured ordering`).

### Task 2: Pixel-art skin

**Files:**
- Create `tools/generate-dialogue-skin.py`
- Create generated `common/src/main/resources/assets/mca/textures/gui/dialogue/{paper_frame,paper_tail,menu_frame,featured_frame,icons}.png`
- Create `common/src/main/java/net/conczin/mca/client/gui/DialogueSkin.java`
- Create `common/src/test/java/net/conczin/mca/client/gui/DialogueSkinResourcesTest.java`

**Interfaces:** `DialogueSkin.drawFrame(GuiGraphics, ResourceLocation, int x, int y, int w, int h)` nine-slices a 24×24 resource; `DialogueSkin.drawIcon(GuiGraphics, int iconIndex, int x, int y)` blits one 16×16 icon.

- [ ] **Step 1: RED test.** Use `ImageIO` to require the five named resources and precise 24×24 frame / 16×20 tail / 96×16 icon atlas dimensions and PNG alpha.
- [ ] **Step 2: Run** `./gradlew.bat :common:test --tests '*DialogueSkinResourcesTest'` expecting a missing-resource failure.
- [ ] **Step 3: Implement** deterministic hand-authored texture generator, run it to create the PNGs, and add nine-slice blitting. Use colours and borders from the reference, no text baked into images.
- [ ] **Step 4: Run** test and `:common:compileJava` expecting PASS; commit these files (`feat(dialogue): add pixel-art parchment and menu skin`).

### Task 3: Integrate independent bubble and featured menu

**Files:**
- Modify `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java`
- Modify `common/src/main/resources/assets/mca/lang/en_us.json` only if a missing visible label is needed.
- Modify tests from Task 1 if the exact rendering geometry exposes a necessary contract refinement.

**Interfaces:** `dialogueRows(...)` consumes the ordering helper and creates one `FEATURED` row before expandable categories; `drawDialogueEventState(GuiGraphics)` renders bubble and menu from layout `Geometry`, preserving offer-token and hit ownership.

- [ ] **Step 1: RED regression.** Add/extend a helper test for collapsed categories with one highlighted event; no featured duplication; assert that `DialogOverlayLayout` keeps its menu bounds within the screen while bubble height changes.
- [ ] **Step 2: Run** focused tests expecting RED for new expectations.
- [ ] **Step 3: Implement** menu and bubble positioning, sprite borders/tail, villager name/divider, icons and section/featured row treatments; begin all categories folded. Keep existing `dialogueHit` clipping, footer input routes, scrolling and 120 ms tween consistent with the new menu bounds.
- [ ] **Step 4: Run** focused tests, `:common:test`, `:fabric:compileJava`, `:neoforge:compileJava` and `git diff --check`; commit feature change (`feat(dialogue): render Kai-style split Talk UI`).

### Task 4: Runtime acceptance and docs

Refinement after checkpoint `ec3dd60e1` (approved concept A):
- [ ] Add regression for menu height staying fixed throughout scrollbar dragging and wheel gestures.
- [ ] Put animation ownership in DialogueMenuAnimation; preserve existing native scroller behavior.
- [ ] Move history to Previous/Next and Return to current under speech, with no counter/status.
- [ ] Reuse vanilla toast/social_interactions, increase colour contrast, raise the panel and add a lightweight position toggle.
- [ ] Run common tests and both loader compiles; inspect live click/history/scroll behavior if the client is available.

Position clarification: the lightweight toggle moves speech above the fixed bottom
choices pane (same width, separate frame, no pointer); it does not move choices below
the upper-right bubble. Scrollbar need and wrapping width use the settled viewport so
expansion cannot flash a transient scrollbar. Cover this and normal pointer retention
in DialogueOverlayLayoutTest and DialogueMenuScrollWidgetTest.

Polish after checkpoint `c7a4b0ddd`: centre each Q/Esc footer group in its half,
round keycaps with symmetric pixel steps, generate matching frame silhouettes,
render the native gold star in a centred icon-size slot and give its featured row
more height. On expansion, reveal the clicked heading and first option with minimal
scroll using settled viewport height. Reuse native scrolling and current panel width.
Verify revealRange and symmetric PNG alpha silhouettes, then common tests and both
loader compiles. Leave this new polish uncommitted after the requested checkpoint.

Approved visual correction (2026-11-08): restore the featured star as inline text;
centre visible icon artwork and text together in each padded row; put section arrows
at the right edge; use the preferred right-hand parchment width even for short lines;
align Back left and Close right inside the footer. Scale text and icon spacing together
on large GUI canvases so the overlay keeps the reference's readable proportions.
Implement in InteractScreen, DialogueSkin and DialogueOverlayLayout; preserve native
scroll gestures and derive hitboxes from the same row bounds. Verify existing common
tests and both loader compiles, then inspect the actual client. Leave changes uncommitted.

**Files:**
- Modify `docs/dialogue-system-guide.md` to describe the actual split visuals and featured row (no engine changes).
- Test and optionally amend the files from Task 3 for witnessed visual issues only.

**Interfaces:** End-to-end `Talk` screen; no new public Java API.

- [ ] **Step 1: Run** both loader builds and focused/common tests; record actual results.
- [ ] **Step 2: Launch** at least one 1.21.1 development client; inspect screenshots for parchment placement, frame/tail, star, left information, open/closed topic row, active reply, and scroll behavior. If full gameplay inspection is unavailable, record that limitation explicitly.
- [ ] **Step 3: Update** the developer guide with verified UI description; run `git diff --check` and `git status --short`; commit docs/runtime refinements on the existing branch. Do not push.
- [ ] **Step 4: Hand off** measured verification, what could not be visually checked, and any remaining screenshot differences.
