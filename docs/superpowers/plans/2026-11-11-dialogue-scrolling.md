# Dialogue Scrolling and Sizing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Status:** Draft awaiting design review. Do not execute product-code steps until the linked design is approved.

**Goal:** Give speech native-style draggable and automatic scrolling, make both dialogue scrollbars visually slimmer, and prevent unnecessary scrolling for two wrapped speech lines.

**Architecture:** Reuse the existing `DialogueMenuScrollWidget` for speech as a second independent instance, with native gestures and a narrow visual treatment. Keep one screen owner for scroll routing, text clipping, rich-text hit testing and follow state. Compute bubble requirements from actual drawn speech metrics and give the two-line minimum priority over choices menu height where space permits.

**Tech Stack:** Java 21, Minecraft 1.21.1 client GUI, vanilla `AbstractScrollWidget`, existing MCA Fabric/NeoForge common UI, JUnit 5 and Gradle.

**Spec:** `docs/superpowers/specs/2026-11-11-dialogue-scrolling-design.md`

## Global Constraints

- Work in the existing `dev/1.21.1-dialogue-system-b` worktree. Do not create checkouts or worktrees, switch branches, merge or push.
- Preserve all unrelated tracked and untracked changes. Review the relevant diff before each code edit and stage only explicit task paths if committing.
- Keep Minecraft's 8-pixel scrollbar hit geometry. Target a 5-pixel displayed thumb and track for both panels.
- Speech text should accommodate two complete `dialoguePixels(10)` line steps when there is sufficient room above the menu.
- Follow new text until the player scrolls up. Resume at the bottom, on a new offer/node, or after choosing a different history entry.
- Keep pending progress, history controls, text styles, click-to-copy, and both speech positions working. Do not change dialogue network messages or datapack schema.
- No new dependency or large dialogue screen rewrite.

## Review Focus

1. A docked two-line utterance at scale 1 previously had only 17 pixels of text viewport. The layout test in Task 1 must guarantee at least 20 visible pixels when enough room exists.
2. A menu that is too tall could still clip a correctly sized speech bubble. Task 1 tests speech-first height allocation and the tiny-screen fallback.
3. A typewriter reveal while the user has manually scrolled upward could jump the reading position. Task 2's state regression and in-game check must keep it stable.
4. A scrolled, wrapped copy phrase could hit the wrong glyph or the scroll strip could trigger `revealFully()`. Task 2 tests and in-game checks cover the shared clip/offset and input precedence.
5. A visually narrowed thumb could lose the native 8-pixel click target or fractional wheel behavior. Task 3 extends the existing widget interaction tests.

---

### Task 1. Fix speech's two-line minimum and menu space allocation

**Files**
- Modify `common/src/main/java/net/conczin/mca/client/gui/DialogueOverlayLayout.java`
- Modify `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java`
- Modify `common/src/test/java/net/conczin/mca/client/gui/DialogueOverlayLayoutTest.java`

**Interfaces**
- `DialogueOverlayLayout.minimumSpeechHeight(int textLineCount, int historyHeight, boolean progress, float scale)` returns the minimum frame height that fits the selected number of rendered line steps and the actual header/footer/progress reservations.
- `DialogueOverlayLayout.maximumMenuHeightForSpeech(int width, int height, int minimumSpeechHeight)` calculates the maximum menu height that leaves the bubble its reserved height, using the same HUD bottom margin, bubble-top position and 8-pixel gap as `calculate`.
- `InteractScreen.dialoguePaperHeight` uses the minimum-height result for at least two line steps and for the actual wrapped line count. The settled and animated layout paths apply the permitted menu cap where enough space exists. If the cap falls below the menu's existing footer-plus-content minimum, use the existing screen-safe layout and allow speech overflow.

- [ ] **Step 1. Add failing layout tests.** Assert that the docked scale-1 two-line requirement is at least 63 pixels without history/progress, the resulting text viewport fits two 10-pixel steps, scales 1.5 and 2 fit two scaled steps, and progress/history reserve their strips. Verify that a 640×360 or 1024×610 layout can reduce menu height to fit speech, while a 320×150 view keeps menu/HUD within bounds and may scroll speech.
- [ ] **Step 2. Run the focused tests and confirm the new cases fail for the current layout.** Run `./gradlew.bat :common:test --tests 'net.conczin.mca.client.gui.DialogueOverlayLayoutTest'` from PowerShell. Record the failing assertions.
- [ ] **Step 3. Implement the two geometry helpers and replace the old speech minimum/padding calculation.** Compute text content height using `Math.round(10 * scale)` per line and reserve the exact on-screen top, bottom and progress metrics. Share layout inputs between the settled and animated menu paths so they do not disagree about available room.
- [ ] **Step 4. Rerun `DialogueOverlayLayoutTest`.** All layout cases, including the existing speaker, pointer, progress and HUD tests, must pass.
- [ ] **Step 5. Review the Task 1 diff.** Verify no regression to menu footer position, pointer separation or the selectable choices area. Commit only Task 1 paths if committing is appropriate to the current dirty worktree.

### Task 2. Add draggable speech scrolling and reveal following

**Files**
- Modify `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java`
- Modify `common/src/main/java/net/conczin/mca/client/gui/DialogueMenuScrollWidget.java`
- Modify `common/src/test/java/net/conczin/mca/client/gui/DialogueMenuScrollWidgetTest.java`
- Modify `common/src/test/java/net/conczin/mca/client/gui/DialogueTextHitTestTest.java` only if a distinct scroll/clip regression is missing

**Interfaces**
- Add `private final DialogueMenuScrollWidget dialogueSpeechScroller` and `private boolean dialogueSpeechAutoFollow` in `InteractScreen`. Replace `dialoguePassageScroll` reads/writes with the speech widget's offset.
- Add `DialogueMenuScrollWidget.maxOffset()` to expose the native, clamped max offset. Keep `layout(...)`, `scrollToSection(...)`, `offset()`, and existing menu behavior intact.
- Use the speech widget's visible area as the scroll range and render its native thumb after speech. A separate screen-level `DialogueViewport` identifies the speech scroll strip versus text clip.

- [ ] **Step 1. Extend the widget tests.** Verify the second widget instance has independent position and scroll state, `maxOffset()` reflects a changing viewport, and reset restores offset zero. Add a focused text hit-test assertion only if the existing `DialogueTextHitTestTest` lacks a scrolled and clipped phrase case.
- [ ] **Step 2. Run the focused regression tests and confirm the new cases fail.** Use `./gradlew.bat :common:test --tests 'net.conczin.mca.client.gui.DialogueMenuScrollWidgetTest' --tests 'net.conczin.mca.client.gui.DialogueTextHitTestTest'`.
- [ ] **Step 3. Wire speech scroll rendering.** Derive the text top/bottom and speech scroller viewport once. Lay out the widget against the actual visible text height, align its thumb at the existing right inset, and draw the speech text and text-target clip using `dialogueSpeechScroller.offset()`. Remove the speech `▲` and `▼` markers. Keep the scrollbar strip outside copy-glyph hit targets.
- [ ] **Step 4. Route speech wheel, left-click, drag and release.** Give its native scrollbar clicks priority over copy or `revealFully()`. Keep the choices widget routing intact. Reset speech scroll on offer/node change and on explicit history navigation. A manual upward scroll/drag pauses following. Reaching `maxOffset()` restores following.
- [ ] **Step 5. Follow the visible typewriter content.** Each frame, while following is active, calculate the bottom of the currently visible wrapped speech and scroll only as far as needed to show it. Use the same wrapped text and line step as rendering. Preserve a user's manual offset as additional characters appear and clamp after content/viewport changes. Avoid introducing a competing text-reveal timer.
- [ ] **Step 6. Rerun both focused tests.** Verify wheel increments, drag/release, independent state, clamping, and scroll-aware hit testing. Test the in-game sequence with a long styled line, a copy phrase completing before the rest of the line, wheel-up while reveal continues, wheel-down to resume, and Next/Previous history.
- [ ] **Step 7. Inspect the Task 2 diff.** Check that a scrollbar click cannot also copy or advance dialogue, and that both scroll widgets receive drag release even when the pointer leaves their original area.

### Task 3. Narrow the scrollbar appearance without changing native input

**Files**
- Modify `common/src/main/java/net/conczin/mca/client/gui/DialogueMenuScrollWidget.java`
- Modify `common/src/main/java/net/conczin/mca/client/gui/InteractScreen.java`
- Modify `common/src/test/java/net/conczin/mca/client/gui/DialogueMenuScrollWidgetTest.java`

**Interfaces**
- Keep `scrollbarWidth()` and the hit/drag strip at the inherited 8 pixels.
- In `DialogueMenuScrollWidget.renderDecorations`, wrap `super.renderDecorations` in a scoped horizontal pose transform that displays the native thumb at 5 pixels wide while keeping its y position, height and tint. Restore graphics pose and colour immediately.
- Draw matching 5-pixel background tracks inside the original 8-pixel strip for the choices and speech panels.

- [ ] **Step 1. Add a regression for the native hit region.** Click and drag at both edges of the 8-pixel scrollbar region and assert scrolling still works. Preserve the existing fractional touchpad-wheel test.
- [ ] **Step 2. Run `DialogueMenuScrollWidgetTest` and verify baseline/failure of the new interaction assertion as appropriate.** The width itself must be validated visually because graphics pose transforms are not represented by this unit test.
- [ ] **Step 3. Apply the visual-only narrowing and matching track styling.** Use one widget-level transform for both instances. Do not override the private vanilla thumb renderer or replace the native gesture implementation.
- [ ] **Step 4. Rerun `DialogueMenuScrollWidgetTest` and check in-game screenshots.** Inspect parchment and dark menu at Minecraft GUI scale settings that produce MCA content scales 1, 1.5 and 2. Check thumb visibility, edge hitbox, track alignment and hover contrast.

### Task 4. Integrated verification and review

**Files:** Only the paths above, plus this plan's checkboxes if tracking completion.

- [x] **Step 1. Run focused tests together.** `./gradlew.bat :common:test --tests 'net.conczin.mca.client.gui.DialogueOverlayLayoutTest' --tests 'net.conczin.mca.client.gui.DialogueMenuScrollWidgetTest' --tests 'net.conczin.mca.client.gui.DialogueTextHitTestTest'`.
- [x] **Step 2. Run the common regression suite.** `./gradlew.bat :common:test`.
- [x] **Step 3. Build both loaders.** `./gradlew.bat :fabric:build :neoforge:build`. Compilation/build success is only wiring evidence.
- [ ] **Step 4. Check in the actual game.** Use two-line and long speech in both bubble positions, pending progress, history navigation, full/partial styled copy spans, GUI-scale changes, mouse-wheel and drag, touchpad if available, and a tiny logical viewport. Capture the resolution/GUI scale that originally showed the two-line defect when available.
- [x] **Step 5. Review only the scoped changes.** Run `git diff --check` for tracked paths and examine all untracked touched files. Record any unverified visual checks, and preserve unrelated working-tree state. Do not push without direction.

## Self-review against the spec

- The two-line height and menu-space priority are covered by Task 1.
- Speech thumb, wheel, drag, auto-follow and manual pause are covered by Task 2.
- Native input width and slimmer visual bar in both panels are covered by Task 3.
- Wrapping, scroll-aware copy, history, progress, GUI scales, HUD and both layout modes are covered by Tasks 1, 2 and 4.
- Approval is still required before any implementation work begins.
