# Dialogue UI Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make MCA's parchment speech panel and dark Talk menu visually consistent without changing dialogue behavior or replacing the selected pixel-art design.

**Architecture:** Keep `InteractScreen` as the dialogue/input owner, `DialogueOverlayLayout` as the panel geometry owner, and `DialogueSkin` plus the existing generator as the artwork owners. Add a small pure row-metrics helper for a consistent icon/text grid, wrapping and row advance; do not introduce a new screen or state pipeline.

**Tech Stack:** Minecraft 1.21.1, Java 21, common client `GuiGraphics`, JUnit 5, existing Python/Pillow sprite generator, Fabric and NeoForge.

**Spec:** `docs/superpowers/specs/2026-11-08-kai-dialogue-ui-design.md` and `docs/superpowers/specs/2026-11-09-dialogue-row-styling-design.md`; this plan proposes only visual refinements to those already-selected designs. The proposed text, density and tail treatments need user approval via plan review before implementation.

## Global Constraints

- Preserve the parchment bubble, bottom-centred dark Talk menu, featured gold row, initially collapsed categories, meaningful icons and villager/world visibility.
- Preserve category resting fill `#362318`, ordinary choice fill `#422B1C`, hover fill `#805032`, category-only borders, and no borders on ordinary choices.
- Keep the menu's 44 unscaled GUI-pixel bottom reserve for hearts and hunger, including docked speech; retain the existing footer's alignment to the visible bottom stroke.
- Keep the optional persistent speech-position toggle; only upper-right speech has a pointer.
- Preserve `DialoguePresentation`, server offer/session checks, highlighted/ambient options, async pending/loading indicators, history review, scroll tween, keyboard and mouse hitboxes, Back/Topics/Close actions, and all unrelated screens.
- Keep renderer and existing textures; do not add dependencies, protocol changes, a new worktree, or an unrelated screen rewrite.
- Respect existing uncommitted work: `InteractScreen.java`, `DialogueOverlayLayout.java` and their tests currently contain unrelated async-loading changes. Re-read their live diffs before editing; never stage or overwrite those unrelated changes.
- For commits touching already-dirty files, use `git add -p` to stage only this task's hunks, inspect `git diff --cached`, and do not commit if independent changes cannot be separated safely.

## File Responsibilities

- Create `common/src/main/java/net/conczin/mca/client/gui/DialogueRowLayout.java`: pure per-row geometry/metrics; no rendering, network, or dialogue state.
- Create `common/src/test/java/net/conczin/mca/client/gui/DialogueRowLayoutTest.java`: row grid, wrapping width, vertical rhythm and grouping invariants.
- Modify `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java`: build structured nested rows, consume row metrics, paint text/icons/header and retain identical input actions.
- Modify `common/src/main/java/net/conczin/mca/client/gui/DialogueSkin.java`: optical icon sizing and the parchment frame/tail rendering boundary.
- Modify `common/src/main/java/net/conczin/mca/client/gui/DialogueOverlayLayout.java`: speech-header and tail attachment geometry only, preserving existing pending-progress methods.
- Modify `tools/generate-dialogue-skin.py` and regenerate `common/src/main/resources/assets/mca/textures/gui/dialogue/paper_frame.png`, `paper_tail.png` (and `icons.png` only if the optical-size pass proves necessary).
- Extend `common/src/test/java/net/conczin/mca/client/gui/DialogueOverlayLayoutTest.java` and `DialogueSkinResourcesTest.java`: screen geometry and deterministic resource checks.
- Edit only demonstrably inconsistent displayed keys in `common/src/main/resources/assets/mca/lang/en_us.json` or `common/src/main/resources/assets/mca_dialogue/lang/en_us.json`; leave dialogue prose and other translations untouched.

## Review Focus

The following five visual/input risks require explicit verification in their owning tasks:

1. Long localized category and nested labels: text wraps at the intended text-slot origin, never into the icon, disclosure arrow, or scrollbar (Task 1 test).
2. Partly scrolled, keyboard-focused and disabled rows: surface, highlight, hitbox and glyph remain aligned and clipped, with async placeholders non-clickable (Task 2 test and client check).
3. Mixed-source icon artwork: equal nominal `12 * contentScale` slots yield similar visible sizes without distortion, including conversation and relationship icons (Task 3 resource/client check).
4. Long villager names, narrow bubbles and docked speech: header never overlaps the position toggle; the pointer is joined only in upper-right mode (Task 4 layout test and client check).
5. Single-line versus multi-line content at GUI scales 1.0, 1.5 and 2.0: controls remain legible, panel within HUD clearance and interactions correct during menu-height tween (Task 2 metrics test and Task 5 client check).

---

### Task 1: Replace Whitespace Indentation with a Shared Row Grid

**Files:**
- Create: `common/src/main/java/net/conczin/mca/client/gui/DialogueRowLayout.java`
- Create: `common/src/test/java/net/conczin/mca/client/gui/DialogueRowLayoutTest.java`
- Modify: `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java` (`dialogueRows`, `dialogueRowTextWidth`, `drawDialogueRow`, nested `DialogueRow`)

**Interfaces:**
- Introduce `DialogueRowLayout.Kind` values `FEATURED`, `CATEGORY`, `CHILD`, `ORDINARY`; loading placeholder keeps its existing separate path.
- `static DialogueRowLayout.Insets insets(int panelWidth, float scale, Kind kind)`, with record `Insets(int iconX, int textX, int textWidth, int trailingX)`; x offsets are relative to menu left. For scale 1, `iconX = 16`, `textX = 36` for featured/category, `textX = 44` for child, and `textX = 16` for ordinary; reserve the existing right disclosure/scrollbar space from `textWidth`.
- Add `boolean nested` as the last `InteractScreen.DialogueRow` field with backwards-compatible constructors; use it only for options under expanded categories, not ordinary dialogue choices or placeholders.
- Both `dialogueLines` and row draw positions consume the *same* insets; preserve scrolled row bounds and existing selection actions.

- [ ] **Step 1: Write failing `DialogueRowLayoutTest` cases** asserting relative x offsets at scale 1, consistent `CHILD` text width after subtracting its inset, and no overlap of text width with the trailing arrow; include widths `250` and `350` with scales `1.0` and `2.0`.
- [ ] **Step 2: Verify RED.** Run `.\gradlew.bat :common:test --tests 'net.conczin.mca.client.gui.DialogueRowLayoutTest' --console=plain --no-configuration-cache --max-workers=1`; expect compilation failure for missing helper before implementation.
- [ ] **Step 3: Implement `insets` and update `InteractScreen`** to use structured child rows instead of `Component.literal("    ")`. Account for the separate 8-pixel scrollbar reservation in the passed `panelWidth`. Keep `Component` content exactly as localized.
- [ ] **Step 4: Verify GREEN** with the focused command, then inspect expanded and collapsed categories with long translations; confirm the rendered line starts coincide with test coordinates.
- [ ] **Step 5: Commit only Task 1 changes** with explicit-path staging for new files and `git add -p` for `InteractScreen`; review `git diff --cached` and exclude pre-existing unrelated hunks.

### Task 2: Tighten Vertical Rhythm Without Losing Click Targets

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/client/gui/DialogueRowLayout.java`
- Modify: `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java` (`dialogueRowHeight`, `drawDialogueRow`)
- Test: `common/src/test/java/net/conczin/mca/client/gui/DialogueRowLayoutTest.java`

**Interfaces:**
- Produce `static int height(float scale, int wrappedLines, Kind kind)`; at scale 1, ordinary/child single-line rows are `22` pixels, category `24`, featured `28`; multiline height is at least `wrappedLines * 10 + 10` pixels. Apply existing scaling/rounding consistently.
- No additional empty spacer rows. Category borders and an explicit child indent communicate grouping; row advance, fill and hit-test bounds derive from `height`.

- [ ] **Step 1: Write failing tests** for single-line `22/24/28`, two-line height `30`, and scale `1.5` spacing; assert monotonic height with wrapped lines and that icon slot `12 * scale` fits within each row.
- [ ] **Step 2: Verify RED** with the focused `DialogueRowLayoutTest` Gradle command; expect height-specific assertion failures or missing method.
- [ ] **Step 3: Implement `height` and consume it** in target-menu sizing, scrollbar content height, row rendering and hit registration; preserve existing special `26`-pixel non-interactive async loading placeholder.
- [ ] **Step 4: Verify GREEN**, then visually check clipped rows, hover/focus, first/last category and a tweening menu for gaps or mismatched hit rectangles.
- [ ] **Step 5: Commit only Task 2 hunks** after reviewing staged diff, excluding unrelated async changes.

### Task 3: Normalize Visible Icon Size

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/client/gui/DialogueSkin.java` (`drawIcon`)
- Modify: `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java` (featured and relationship icon drawing)
- Test: `common/src/test/java/net/conczin/mca/client/gui/DialogueSkinResourcesTest.java`

**Interfaces:** Keep the currently selected vanilla Social Interactions speech icon, MCA heart, custom category artwork and featured star. Expose `static DialogueSkin.IconCrop cropFor(int iconIndex)` with `record IconCrop(int left, int top, int width, int height)` for generated-atlas categories; retain a dedicated branch for native speech and MCA heart artwork. All occupy the row-grid's `12 * contentScale` slot, preserve source aspect ratio and centre their *opaque bounds* rather than assuming each texture's declared rectangle is its visible size.

- [ ] **Step 1: Add a failing resource test** that calls `cropFor` for generated-atlas categories and checks that their `IconCrop` dimensions contain the nonempty opaque bounds inside the corresponding `16x16` cell, with no crop crossing its cell boundary. Record which mixed-source icon needs optical adjustment.
- [ ] **Step 2: Verify RED** against `DialogueSkinResourcesTest`; expect missing `cropFor` rather than an unrelated source or classpath failure.
- [ ] **Step 3: Centralize optical slot positioning** in `DialogueSkin`; use one explicit rendering contract for native, MCA and generated icons, without replacing their selected source art or stretching non-square symbols.
- [ ] **Step 4: Verify GREEN**, then compare screenshot crops of all expanded category rows and the featured icon at two GUI scales. Verify pixel-art sharpness and consistent perceived size by eye.
- [ ] **Step 5: Commit only Task 3 files/hunks** after staged-diff review.

### Task 4: Make Parchment Speech Hierarchy and Pointer Join Coherent

**Files:**
- Modify: `common/src/main/java/net/conczin/mca/client/gui/DialogueOverlayLayout.java`
- Modify: `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java` (speech header and pointer draw order)
- Modify: `common/src/main/java/net/conczin/mca/client/gui/DialogueSkin.java` (paper rendering where required)
- Modify: `tools/generate-dialogue-skin.py`
- Regenerate: `common/src/main/resources/assets/mca/textures/gui/dialogue/paper_frame.png`, `paper_tail.png`
- Test: `common/src/test/java/net/conczin/mca/client/gui/DialogueOverlayLayoutTest.java` and `DialogueSkinResourcesTest.java`

**Interfaces:**
- `static Rect speakerHeader(Rect bubble, float scale)` in `DialogueOverlayLayout`: reserve the current top header band and the upper-right toggle's control space; clipped name stays within it.
- `static Rect tailBounds(Rect bubble)` returns the `16x20` pointer rectangle where the generated outline joins the panel's left edge; `Geometry.canShowTail()` remains the visibility gate.
- Use a modest contrast treatment for the speaker label (e.g. darker header tint or nameplate), while preserving divider and speech scroll area. Remove isolated bright scuff pixels from the parchment source and soften the conspicuously pale edge shine; keep a warm textured pixel-art border and a subtle interior gradient.

- [ ] **Step 1: Write failing layout tests** asserting header stays inside `320x240` and `640x360` bubble bounds at scales `1.0`/`2.0`, ends left of the existing position-toggle slot, and tail bounds meet the bubble edge only when `canShowTail()` is true; docked speech must never show a pointer.
- [ ] **Step 2: Write failing resource assertions** verifying that the parchment contains no authored isolated scuff points at former locations and that the tail PNG remains `16x20` with a visible border/fill and transparent outside pixels.
- [ ] **Step 3: Verify RED** with both focused Gradle test classes; expect missing geometry helpers / existing scuff pixels.
- [ ] **Step 4: Implement the smallest header and pointer changes**, update the generator, then run `python tools/generate-dialogue-skin.py`. Check that nine-slice corners and tail meet without doubled outlines, hidden segments or light bands in screenshot, not just in source PNGs.
- [ ] **Step 5: Verify GREEN** with both focused tests, `:common:compileJava`, and in-game screenshots for upper-right/docked speech, long names, small windows and light/dark worlds.
- [ ] **Step 6: Commit only Task 4 files and regenerated PNGs**, carefully excluding unrelated async-progress additions.

### Task 5: Visible Copy Audit and End-to-End UI Verification

**Files:**
- Edit if needed: `common/src/main/resources/assets/mca/lang/en_us.json`, `common/src/main/resources/assets/mca_dialogue/lang/en_us.json`
- No other production files.

**Interfaces:** Retain all translation keys and dialogue meaning. Match capitalization within category/header/action tiers; do not title-case spoken dialogue, alter proper names, or rewrite unrelated button strings.

- [ ] **Step 1: Audit the rendered labels** for Conversations/Relationships, Hug/Kiss, featured entries, Q Interaction/Esc Close, Next/Previous and dynamically loaded prompts. Compare current English values to the screenshots; log mismatches and leave already-consistent labels untouched.
- [ ] **Step 2: If a real inconsistency exists, add a failing localization assertion** in a matching existing resource test, verify RED, make the smallest translation-value edit, then verify GREEN. If none exists, document the no-change outcome instead of inventing a text change.
- [ ] **Step 3: Run `.\gradlew.bat :common:test :common:compileJava --console=plain --no-configuration-cache --max-workers=1`, then `.\gradlew.bat :fabric:build :neoforge:build --console=plain --no-configuration-cache --max-workers=1`; report every failure by test/task name, including unrelated pre-existing failures.
- [ ] **Step 4: Manually verify Minecraft 1.21.1** in both speech positions, at scales `1.0`, `1.5`, `2.0`, with narrow viewport, long translated text, collapsed/expanded categories, featured/ordinary events, history review, async pending/placeholder, scrollbar wheel/drag, partial clipping, Q/Escape, keyboard focus, menu tween and survival HUD clearance; capture before/after screenshots. If runtime is not available, explicitly leave visual acceptance pending.
- [ ] **Step 5: Run `git diff --check`**, inspect working tree and staged hunks, and commit only any Task 5 localization/test changes. Do not include unrelated work-in-progress changes.

## Self-Review and Completion Gate

- Scope maps to all reported issues: text alignment/grouping (Tasks 1–2), icons (Task 3), name/tail/highlight/dots (Task 4), capitalization (Task 5).
- Geometric tests protect wrapping and hitboxes; PNG tests protect generated art; manual client QA is mandatory for perceived size, border joins and contrast.
- If the selected icon/pointer styling conflicts with the existing Kai spec, pause to resolve it with the user rather than overriding an earlier design choice.
- **This is a plan only.** Do not begin source changes until the user reviews and approves this proposed visual treatment and the execution method.
